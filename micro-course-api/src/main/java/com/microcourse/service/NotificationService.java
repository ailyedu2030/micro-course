package com.microcourse.service;

import com.microcourse.dto.NotificationCreateRequest;
import com.microcourse.dto.NotificationVO;
import com.microcourse.dto.PageResult;

public interface NotificationService {

    NotificationVO send(NotificationCreateRequest request);

    PageResult<NotificationVO> getMyNotifications(Long userId, int page, int size);

    void markAsRead(Long id);

    long getUnreadCount(Long userId);

    void markAllAsRead(Long userId);
}