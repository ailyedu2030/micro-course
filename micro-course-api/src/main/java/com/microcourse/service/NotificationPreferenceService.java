package com.microcourse.service;

import com.microcourse.dto.PreferenceUpdateRequest;
import com.microcourse.dto.PreferenceVO;

public interface NotificationPreferenceService {

    PreferenceVO getOrCreate(Long userId);

    PreferenceVO update(Long userId, PreferenceUpdateRequest request);
}