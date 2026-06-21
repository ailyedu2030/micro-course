package com.microcourse.service;

import com.microcourse.dto.NotificationCreateRequest;
import com.microcourse.dto.NotificationVO;
import com.microcourse.dto.PageResult;
import com.microcourse.enums.NotificationType;

public interface NotificationService {

    NotificationVO send(NotificationCreateRequest request, Long senderId);

    /**
     * P0-7：业务事件异步触发通知（系统内部调用，无权限校验）。
     *
     * <p>实现必须标注 {@code @Async} 异步执行，绝不阻塞主业务链路；
     * 内部异常自我吞没并记录日志，保证通知失败不影响主流程（异常隔离）。
     *
     * @param userId    接收者用户 ID
     * @param type      通知类型枚举
     * @param title     通知标题
     * @param content   通知正文
     * @param relatedId 关联业务实体 ID（如 courseId / exerciseId，可为 null），前端据此拼跳转
     */
    void notifyAsync(Long userId, NotificationType type, String title, String content, Long relatedId);

    PageResult<NotificationVO> getMyNotifications(Long userId, String type, int page, int size);

    void markAsRead(Long id, Long userId);

    long getUnreadCount(Long userId);

    void markAllAsRead(Long userId);
}