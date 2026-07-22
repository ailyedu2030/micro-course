package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.NotificationCreateRequest;
import com.microcourse.dto.NotificationVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Notification;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.NotificationRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   CourseRepository courseRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * P0-7：业务事件异步触发通知。
     *
     * <p>{@code @Async} 在独立线程池（AsyncConfig#taskExecutor）执行，不阻塞主业务事务；
     * {@code @Transactional(propagation = REQUIRES_NEW)} 确保每次通知写入独立提交（E2-3 修复）；
     * 全程 try-catch 兜底，通知持久化失败仅记录日志，绝不向调用方传播异常（异常隔离）。
     *
     * ERR-007 修复: @Retryable 与 @Async 不兼容（Spring AOP 代理链在异步线程中不可达）。
     * 改为在 catch 块中手动重试 2 次（线程内重试，不依赖 AOP）。
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Override
    public void notifyAsync(Long userId, NotificationType type, String title, String content, Long relatedId) {
        int maxRetries = 2;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                doNotifyAsync(userId, type, title, content, relatedId);
                return;
            } catch (org.springframework.dao.DataAccessException e) {
                if (attempt < maxRetries) {
                    log.warn("[Notification] 通知写入失败, 第{}次重试 userId={} type={}", attempt + 1, userId, type, e);
                    try { Thread.sleep(1000L * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.error("[Notification] 异步通知发送失败（已重试{}次）userId={} type={} relatedId={}", maxRetries, userId, type, relatedId, e);
                }
            } catch (Exception e) {
                log.error("[Notification] 异步通知发送失败 userId={} type={} relatedId={}", userId, type, relatedId, e);
                return;
            }
        }
    }

    private void doNotifyAsync(Long userId, NotificationType type, String title, String content, Long relatedId) {
        if (userId == null || type == null) {
            log.warn("[Notification] notifyAsync 入参非法,跳过 userId={} type={}", userId, type);
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type.getCode());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setChannel("SITE");
        notification.setIsRead(false);
        LocalDateTime now = LocalDateTime.now();
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        notificationRepository.insert(notification);
        log.debug("[Notification] 已发送 userId={} type={} relatedId={}", userId, type.getCode(), relatedId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO send(NotificationCreateRequest request, Long senderId) {
        // 解析目标用户列表
        List<Long> userIds = resolveTargetUserIds(request, senderId);

        if (userIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "没有可发送的目标用户");
        }

        Notification first = null;
        for (Long uid : userIds) {
            Notification notification = new Notification();
            notification.setUserId(uid);
            notification.setType(request.getType());
            notification.setTitle(com.microcourse.util.XssSanitizer.sanitizePlainText(request.getTitle()));
            notification.setContent(com.microcourse.util.XssSanitizer.sanitize(request.getContent()));
            notification.setRelatedId(request.getRelatedId());
            notification.setChannel(request.getChannel() != null ? request.getChannel() : "SITE");
            notification.setIsRead(false);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.insert(notification);
            if (first == null) first = notification;
        }

        log.info("[Notification] 群发完成 senderId={} count={} type={}", senderId, userIds.size(), request.getType());
        return first != null ? convertToVO(first) : null;
    }

    private List<Long> resolveTargetUserIds(NotificationCreateRequest request, Long senderId) {
        // 模式 1：sendToAll（管理员公告）— 仅 ADMIN 可用
        if (Boolean.TRUE.equals(request.getSendToAll())) {
            if (!SecurityUtil.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "仅管理员可发送全员公告");
            }
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getStatus, 1).isNull(User::getDeletedAt);
            return userRepository.selectList(wrapper).stream()
                    .map(User::getId).collect(Collectors.toList());
        }

        // 模式 2：targetUserIds（群发指定用户）
        if (request.getTargetUserIds() != null && !request.getTargetUserIds().isEmpty()) {
            // TEACHER 权限校验
            if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC")) {
                validateTeacherCanNotify(senderId, request.getTargetUserIds());
            }
            return request.getTargetUserIds();
        }

        // 模式 3：userId（单用户，向后兼容）
        if (request.getUserId() != null) {
            List<Long> singleUser = List.of(request.getUserId());
            if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC")) {
                validateTeacherCanNotify(senderId, singleUser);
            }
            return singleUser;
        }

        return List.of();
    }

    private void validateTeacherCanNotify(Long teacherId, List<Long> targetUserIds) {
        LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<>();
        courseWrapper.eq(Course::getTeacherId, teacherId).isNull(Course::getDeletedAt);
        List<Course> teacherCourses = courseRepository.selectList(courseWrapper);
        if (teacherCourses.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "没有可授课的课程，无法发送通知");
        }
        Set<Long> courseIds = teacherCourses.stream().map(Course::getId).collect(Collectors.toSet());
        LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
        enrollWrapper.in(Enrollment::getUserId, targetUserIds)
                     .in(Enrollment::getCourseId, courseIds)
                     .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
        if (enrollmentRepository.selectCount(enrollWrapper) == 0) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "只能向自己课程中的学生发送通知");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationVO> getMyNotifications(Long userId, String type, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Notification::getType, type);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);

        Page<Notification> ipage = notificationRepository.selectPage(
                new Page<>(page + 1, size),
                wrapper
        );

        PageResult<NotificationVO> result = new PageResult<>();
        result.setItems(ipage.getRecords().stream().map(this::convertToVO).toList());
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id, Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getId, id)
               .eq(Notification::getUserId, userId)
               .set(Notification::getIsRead, true)
               .set(Notification::getReadAt, LocalDateTime.now());
        notificationRepository.update(null, wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        wrapper.eq(Notification::getIsRead, false);
        return notificationRepository.selectCount(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        Notification notification = new Notification();
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        wrapper.eq(Notification::getIsRead, false);

        notificationRepository.update(notification, wrapper);
    }

    private NotificationVO convertToVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setUserId(notification.getUserId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setRelatedId(notification.getRelatedId());
        vo.setChannel(notification.getChannel());
        vo.setIsRead(notification.getIsRead());
        vo.setReadAt(notification.getReadAt());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }

    @org.springframework.retry.annotation.Recover
    public void recoverNotify(DataAccessException t, Long userId, NotificationType type,
                               String title, String content, Long relatedId) {
        log.error("[Notification] 重试3次仍失败，通知丢失 userId={} type={} title={}", userId, type, title, t);
    }
}
