package com.microcourse.service;

import com.microcourse.dto.TeacherStatusRequest;
import com.microcourse.dto.UserStatusRequest;

public interface UserStatusService {

    void updateStatus(Long id, UserStatusRequest request);

    void updateStatus(Long id, Integer status);

    void updateTeacherStatus(Long id, TeacherStatusRequest request);
}
