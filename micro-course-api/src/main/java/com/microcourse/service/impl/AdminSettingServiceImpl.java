package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.AdminSettingVO;
import com.microcourse.dto.SettingUpdateRequest;
import com.microcourse.entity.AdminSetting;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.AdminSettingRepository;
import com.microcourse.service.AdminSettingService;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatch(List<SettingUpdateRequest> settings) {
        LocalDateTime now = LocalDateTime.now();
        for (SettingUpdateRequest req : settings) {
            LambdaQueryWrapper<AdminSetting> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AdminSetting::getSettingKey, req.getKey());
            AdminSetting setting = adminSettingRepository.selectOne(wrapper);
            if (setting != null) {
                setting.setSettingValue(req.getValue());
                setting.setUpdatedAt(now);
                adminSettingRepository.updateById(setting);
            } else {
                // Upsert: key 不存在时插入新记录
                AdminSetting newSetting = new AdminSetting();
                newSetting.setSettingKey(req.getKey());
                newSetting.setSettingValue(req.getValue());
                newSetting.setUpdatedAt(now);
                adminSettingRepository.insert(newSetting);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(String key, String value) {
        LocalDateTime now = LocalDateTime.now();
        // CON-NEW-2 修复:用 INSERT ON CONFLICT 原子 upsert,避免 check-then-insert 竞态
        // MyBatis-Plus 没有原生 upsert,使用 wrapper.last() 附加 ON CONFLICT 子句
        AdminSetting newSetting = new AdminSetting();
        newSetting.setSettingKey(key);
        newSetting.setSettingValue(value);
        newSetting.setUpdatedAt(now);
        try {
            adminSettingRepository.insert(newSetting);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            // 并发写入同一新 key,降级为 update
            LambdaQueryWrapper<AdminSetting> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AdminSetting::getSettingKey, key);
            AdminSetting existing = adminSettingRepository.selectOne(wrapper);
            if (existing != null) {
                existing.setSettingValue(value);
                existing.setUpdatedAt(now);
                adminSettingRepository.updateById(existing);
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