package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.SettingUpdateRequest;
import com.microcourse.entity.AdminSetting;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.AdminSettingRepository;
import com.microcourse.service.AdminSettingService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Service
public class AdminSettingServiceImpl implements AdminSettingService {

    private final AdminSettingRepository adminSettingRepository;

    public AdminSettingServiceImpl(AdminSettingRepository adminSettingRepository) {
        this.adminSettingRepository = adminSettingRepository;
    }

    @Override
    @Cacheable(value = "adminSettingsList", key = "'all'", sync = true)
    @Transactional(readOnly = true)
    public List<AdminSettingVO> getAll() {
        List<AdminSetting> settings = adminSettingRepository.selectList(null);
        return settings.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "adminSettingsByKey", key = "#key", sync = true)
    @Transactional(readOnly = true)
    public String getByKey(String key) {
        LambdaQueryWrapper<AdminSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminSetting::getSettingKey, key);
        AdminSetting setting = adminSettingRepository.selectOne(wrapper);
        return setting != null ? setting.getSettingValue() : null;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "adminSettingsList", allEntries = true),
            @CacheEvict(value = "adminSettingsByKey", allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void update(String key, String value) {
        LambdaQueryWrapper<AdminSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminSetting::getSettingKey, key);
        AdminSetting setting = adminSettingRepository.selectOne(wrapper);
        if (setting == null) {
            throw new BusinessException(ErrorCode.ADMIN_SETTING_NOT_FOUND);
        }
        setting.setSettingValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        adminSettingRepository.updateById(setting);
    }

    /**
     * P1-4: 批量 upsert，委托给 doUpsert 消除 N+1 select 查询
     * P2: 统一为 upsert 语义，与 update 行为一致
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "adminSettingsList", allEntries = true),
            @CacheEvict(value = "adminSettingsByKey", allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void updateBatch(List<SettingUpdateRequest> settings) {
        LocalDateTime now = LocalDateTime.now();
        for (SettingUpdateRequest req : settings) {
            // P1-3: 使用 PostgreSQL ON CONFLICT 原子 upsert
            adminSettingRepository.upsertByKey(req.getKey(), req.getValue(), now);
        }
    }

    /**
     * P1-3: 使用 PostgreSQL INSERT ... ON CONFLICT 原子 upsert
     * 消除 check-then-insert 竞态
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "adminSettingsList", allEntries = true),
            @CacheEvict(value = "adminSettingsByKey", allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void upsert(String key, String value) {
        adminSettingRepository.upsertByKey(key, value, LocalDateTime.now());
    }

    private AdminSettingVO convertToVO(AdminSetting setting) {
        AdminSettingVO vo = new AdminSettingVO();
        vo.setId(setting.getId());
        vo.setSettingKey(setting.getSettingKey());
        vo.setSettingValue(setting.getSettingValue());
        vo.setDescription(setting.getDescription());
        vo.setUpdatedAt(setting.getUpdatedAt());
        return vo;
    }
}