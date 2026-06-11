package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.AdminSettingVO;
import com.microcourse.entity.AdminSetting;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.AdminSettingRepository;
import com.microcourse.service.AdminSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    @Transactional(readOnly = true)
    public List<AdminSettingVO> getAll() {
        List<AdminSetting> settings = adminSettingRepository.selectList(null);
        return settings.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public String getByKey(String key) {
        LambdaQueryWrapper<AdminSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminSetting::getSettingKey, key);
        AdminSetting setting = adminSettingRepository.selectOne(wrapper);
        return setting != null ? setting.getSettingValue() : null;
    }

    @Override
    @Transactional
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

    @Override
    @Transactional
    public void updateBatch(Map<String, String> settings) {
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            LambdaQueryWrapper<AdminSetting> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AdminSetting::getSettingKey, entry.getKey());
            AdminSetting setting = adminSettingRepository.selectOne(wrapper);
            if (setting != null) {
                setting.setSettingValue(entry.getValue());
                setting.setUpdatedAt(now);
                adminSettingRepository.updateById(setting);
            }
        }
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