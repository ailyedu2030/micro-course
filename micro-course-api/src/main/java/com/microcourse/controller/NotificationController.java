package com.microcourse.controller;

import com.microcourse.dto.NotificationVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<NotificationVO>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = getCurrentUserId();
        PageResult<NotificationVO> result = notificationService.getMyNotifications(userId, page, size);
        return R.ok(result);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public R<Void> markAsRead(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        notificationService.markAsRead(id, userId);
        return R.ok();
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public R<Void> markAllAsRead() {
        Long userId = getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return R.ok();
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public R<Long> getUnreadCount() {
        Long userId = getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return R.ok(count);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}