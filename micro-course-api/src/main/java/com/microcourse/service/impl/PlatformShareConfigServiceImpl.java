package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.PlatformShareConfigDTO;
import com.microcourse.entity.OperationLog;
import com.microcourse.entity.PlatformShareConfig;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.PlatformShareConfigRepository;
import com.microcourse.service.OperationLogService;
import com.microcourse.service.PlatformShareConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 平台分享配置服务实现
 *
 * @author Phase9-Development-Team
 * @since 2026-07-03
 */
@Service
public class PlatformShareConfigServiceImpl implements PlatformShareConfigService {

    private static final Logger log = LoggerFactory.getLogger(PlatformShareConfigServiceImpl.class);

    private final PlatformShareConfigRepository repository;
    private final OperationLogService operationLogService;

    public PlatformShareConfigServiceImpl(PlatformShareConfigRepository repository,
                                          OperationLogService operationLogService) {
        this.repository = repository;
        this.operationLogService = operationLogService;
    }

    @Override
    public List<PlatformShareConfigDTO> listAll() {
        LambdaQueryWrapper<PlatformShareConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(PlatformShareConfig::getConfigKey)
               .orderByDesc(PlatformShareConfig::getActive);
        List<PlatformShareConfig> entities = repository.selectList(wrapper);
        return entities.stream()
                .map(PlatformShareConfigDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PlatformShareConfigDTO> findByKey(String configKey) {
        LambdaQueryWrapper<PlatformShareConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformShareConfig::getConfigKey, configKey);
        PlatformShareConfig entity = repository.selectOne(wrapper);
        return entity != null
                ? Optional.of(PlatformShareConfigDTO.fromEntity(entity))
                : Optional.empty();
    }

    /** P1I-060: 分账比例/百分比类配置 key 前缀集 */
    private static final java.util.Set<String> PERCENTAGE_KEYS = java.util.Set.of(
            "platform_share_ratio", "teacher_share_ratio", "commission_rate",
            "platform_commission", "teacher_commission"
    );

    /** P1I-060: 校验配置 value 的有效性 */
    private void validateConfigValue(String configKey, String configValue) {
        // 百分比类配置：0-100 范围校验
        if (PERCENTAGE_KEYS.contains(configKey)) {
            if (configValue == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "配置值不能为空");
            }
            try {
                double val = Double.parseDouble(configValue);
                if (val < 0 || val > 100) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                            "分账比例配置值必须在 0-100 之间，当前值: " + val);
                }
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "分账比例配置值必须是数值，当前值: " + configValue);
            }
        }

        // 通用配置值非空校验
        if (configValue == null || configValue.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "配置值不能为空");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformShareConfigDTO upsert(PlatformShareConfigDTO dto) {
        // P1I-060: 配置值范围校验
        validateConfigValue(dto.getConfigKey(), dto.getConfigValue());

        dto.setUpdatedAt(LocalDateTime.now());

        LambdaQueryWrapper<PlatformShareConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformShareConfig::getConfigKey, dto.getConfigKey());
        PlatformShareConfig existing = repository.selectOne(wrapper);

        boolean isUpdate = existing != null;
        String oldValue = isUpdate ? existing.getConfigValue() : null;

        if (isUpdate) {
            dto.setId(existing.getId());
            // P1C-061: 设置乐观锁版本号，防止并发编辑覆盖
            dto.setVersion(existing.getVersion());
            PlatformShareConfig entity = convertToEntity(dto);
            repository.updateById(entity);
        } else {
            PlatformShareConfig entity = convertToEntity(dto);
            repository.insert(entity);
            dto.setId(entity.getId());
        }

        // P1-I-05: 记录分账配置变更日志
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(dto.getUpdatedBy());
            logEntry.setAction("UPDATE_SHARE_CONFIG");
            logEntry.setTargetType("PLATFORM_SHARE_CONFIG");
            logEntry.setTargetId(dto.getId());
            logEntry.setDetail("配置Key=" + dto.getConfigKey()
                    + (isUpdate ? ", 旧值=" + oldValue : ", 新建")
                    + ", 新值=" + dto.getConfigValue());
            logEntry.setSuccess(true);
            logEntry.setCreatedAt(LocalDateTime.now());
            operationLogService.log(logEntry);
        } catch (Exception e) {
            log.warn("[PlatformShareConfig] 记录变更日志失败 configKey={}", dto.getConfigKey(), e);
        }

        return dto;
    }

    private PlatformShareConfig convertToEntity(PlatformShareConfigDTO dto) {
        PlatformShareConfig entity = new PlatformShareConfig();
        entity.setId(dto.getId());
        entity.setConfigKey(dto.getConfigKey());
        entity.setConfigValue(dto.getConfigValue());
        entity.setDescription(dto.getDescription());
        entity.setActive(dto.getActive());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setUpdatedBy(dto.getUpdatedBy());
        // P1C-061: 设置乐观锁版本号（insert 时 version 为 null 或 0 均可）
        entity.setVersion(dto.getVersion());
        return entity;
    }
}
