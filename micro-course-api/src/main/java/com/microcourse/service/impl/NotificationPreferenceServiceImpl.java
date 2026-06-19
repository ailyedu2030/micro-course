package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.PreferenceUpdateRequest;
import com.microcourse.dto.PreferenceVO;
import com.microcourse.entity.NotificationPreference;
import com.microcourse.repository.NotificationPreferenceRepository;
import com.microcourse.service.NotificationPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceServiceImpl(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreferenceVO getOrCreate(Long userId) {
        LambdaQueryWrapper<NotificationPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationPreference::getUserId, userId);
        NotificationPreference preference = preferenceRepository.selectOne(wrapper);

        if (preference == null) {
            preference = new NotificationPreference();
            preference.setUserId(userId);
            preference.setAllowSite(true);
            preference.setAllowEmail(false);
            preference.setAllowWechat(false);
            preference.setUpdatedAt(LocalDateTime.now());
            try {
                preferenceRepository.insert(preference);
            } catch (org.springframework.dao.DuplicateKeyException dupEx) {
                // CON-NEW-6 修复:DB UNIQUE(user_id) 兜底,降级为已存在记录
                preference = preferenceRepository.selectOne(wrapper);
            }
        }

        return convertToVO(preference);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreferenceVO update(Long userId, PreferenceUpdateRequest request) {
        LambdaQueryWrapper<NotificationPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationPreference::getUserId, userId);
        NotificationPreference preference = preferenceRepository.selectOne(wrapper);

        if (preference == null) {
            preference = new NotificationPreference();
            preference.setUserId(userId);
            preference.setUpdatedAt(LocalDateTime.now());
        }

        if (request.getAllowSite() != null) {
            preference.setAllowSite(request.getAllowSite());
        }
        if (request.getAllowEmail() != null) {
            preference.setAllowEmail(request.getAllowEmail());
        }
        if (request.getAllowWechat() != null) {
            preference.setAllowWechat(request.getAllowWechat());
        }
        if (request.getQuietHoursStart() != null) {
            preference.setQuietHoursStart(request.getQuietHoursStart());
        }
        if (request.getQuietHoursEnd() != null) {
            preference.setQuietHoursEnd(request.getQuietHoursEnd());
        }
        preference.setUpdatedAt(LocalDateTime.now());

        if (preference.getId() == null) {
            preferenceRepository.insert(preference);
        } else {
            preferenceRepository.updateById(preference);
        }

        return convertToVO(preference);
    }

    private PreferenceVO convertToVO(NotificationPreference preference) {
        PreferenceVO vo = new PreferenceVO();
        vo.setId(preference.getId());
        vo.setUserId(preference.getUserId());
        vo.setAllowSite(preference.getAllowSite());
        vo.setAllowEmail(preference.getAllowEmail());
        vo.setAllowWechat(preference.getAllowWechat());
        vo.setQuietHoursStart(preference.getQuietHoursStart());
        vo.setQuietHoursEnd(preference.getQuietHoursEnd());
        vo.setUpdatedAt(preference.getUpdatedAt());
        return vo;
    }
}