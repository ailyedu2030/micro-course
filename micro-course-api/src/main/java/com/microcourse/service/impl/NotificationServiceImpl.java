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
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.NotificationRepository;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   CourseRepository courseRepository,
                                   EnrollmentRepository enrollmentRepository) {
        this.notificationRepository = notificationRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * P0-7：业务事件异步触发通知。
     *
     * <p>{@code @Async} 在独立线程池（AsyncConfig#taskExecutor）执行，不阻塞主业务事务；
     * 全程 try-catch 兜底，通知持久化失败仅记录日志，绝不向调用方传播异常（异常隔离）。
     */
    @Async
    @Override
    public void notifyAsync(Long userId, NotificationType type, String title, String content, Long relatedId) {
        try {
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
        } catch (Exception e) {
            // 异常隔离：通知失败不得影响主链路（选课/批改/审批）
            log.error("[Notification] 异步通知发送失败 userId={} type={} relatedId={}", userId, type, relatedId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationVO send(NotificationCreateRequest request, Long senderId) {
        // SECURITY: TEACHER 只能向自己课程中的学生发送通知
        if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC")) {
            // 查询教师所有课程
            LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<>();
            courseWrapper.eq(Course::getTeacherId, senderId).isNull(Course::getDeletedAt);
            List<Course> teacherCourses = courseRepository.selectList(courseWrapper);
            if (teacherCourses.isEmpty()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "没有可授课的课程，无法发送通知");
            }
            Set<Long> courseIds = teacherCourses.stream().map(Course::getId).collect(Collectors.toSet());
            // 查询收件人是否在这些课程中已选课
            LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
            enrollWrapper.eq(Enrollment::getUserId, request.getUserId())
                         .in(Enrollment::getCourseId, courseIds)
                         .ne(Enrollment::getEnrollmentStatus, "CANCELLED");
            if (enrollmentRepository.selectCount(enrollWrapper) == 0) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "只能向自己课程中的学生发送通知");
            }
        }

        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setType(request.getType());
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setRelatedId(request.getRelatedId());
        notification.setChannel(request.getChannel() != null ? request.getChannel() : "SITE");
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());

        notificationRepository.insert(notification);
        return convertToVO(notification);
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
}