package com.microcourse.controller;

import com.microcourse.dto.NotificationCreateRequest;
import com.microcourse.dto.NotificationVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
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
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        Long userId = getCurrentUserId();
        PageResult<NotificationVO> result = notificationService.getMyNotifications(userId, type, page, size);
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

    /** P0-1: 通知发送允许教师和管理员 */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<NotificationVO> send(@Valid @RequestBody NotificationCreateRequest request) {
        Long userId = getCurrentUserId();
        NotificationVO vo = notificationService.send(request, userId);
        return R.ok(vo);
    }

    /** P1: getCurrentUserId 类型安全 —— 兼容 Long / String / Number 类型 principal */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Number) {
            return ((Number) principal).longValue();
        }
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.TOKEN_INVALID);
    }
}
