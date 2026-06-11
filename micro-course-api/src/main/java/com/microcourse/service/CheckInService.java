package com.microcourse.service;

import com.microcourse.dto.CheckInVO;

import java.util.List;

public interface CheckInService {

    CheckInVO checkIn(Long userId);

    List<CheckInVO> getMyCheckIns(Long userId, int days);

    int getStreak(Long userId);

    void updateDuration(Long userId, int duration);
}