package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.NotificationCreateRequest;
import com.microcourse.dto.NotificationVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Notification;
import com.microcourse.repository.NotificationRepository;
import com.microcourse.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public NotificationVO send(NotificationCreateRequest request) {
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
    public PageResult<NotificationVO> getMyNotifications(Long userId, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
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
    public void markAsRead(Long id, Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getId, id)
               .eq(Notification::getUserId, userId)
               .set(Notification::getIsRead, true)
               .set(Notification::getReadAt, LocalDateTime.now());
        notificationRepository.update(null, wrapper);
    }

    @Override
    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        wrapper.eq(Notification::getIsRead, false);
        return notificationRepository.selectCount(wrapper);
    }

    @Override
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