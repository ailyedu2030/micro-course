package com.microcourse.service;

import com.microcourse.dto.NotificationCreateRequest;
import com.microcourse.dto.NotificationVO;
import com.microcourse.dto.PageResult;

public interface NotificationService {

    NotificationVO send(NotificationCreateRequest request, Long senderId);

    PageResult<NotificationVO> getMyNotifications(Long userId, String type, int page, int size);

    void markAsRead(Long id, Long userId);

    long getUnreadCount(Long userId);

    void markAllAsRead(Long userId);
}