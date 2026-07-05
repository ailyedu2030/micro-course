package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.PlatformShareConfigDTO;
import com.microcourse.entity.PlatformShareConfig;
import com.microcourse.repository.PlatformShareConfigRepository;
import com.microcourse.service.PlatformShareConfigService;
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

    private final PlatformShareConfigRepository repository;

    public PlatformShareConfigServiceImpl(PlatformShareConfigRepository repository) {
        this.repository = repository;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformShareConfigDTO upsert(PlatformShareConfigDTO dto) {
        dto.setUpdatedAt(LocalDateTime.now());

        LambdaQueryWrapper<PlatformShareConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PlatformShareConfig::getConfigKey, dto.getConfigKey());
        PlatformShareConfig existing = repository.selectOne(wrapper);

        if (existing != null) {
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
