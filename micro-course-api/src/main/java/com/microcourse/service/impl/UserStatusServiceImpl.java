package com.microcourse.service.impl;

import com.microcourse.dto.TeacherStatusRequest;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.entity.OperationLog;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.security.UserStatusCheckFilter;
import com.microcourse.service.OperationLogService;
import com.microcourse.service.UserStatusService;
import com.microcourse.util.IpUtil;
import com.microcourse.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class UserStatusServiceImpl implements UserStatusService {

    private static final Logger log = LoggerFactory.getLogger(UserStatusServiceImpl.class);

    private final UserRepository userRepository;
    private final RedisUtil redisUtil;
    private final OperationLogService operationLogService;

    public UserStatusServiceImpl(UserRepository userRepository,
                                  RedisUtil redisUtil,
                                  OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, UserStatusRequest request) {
        UserStatus newStatus;
        try {
            newStatus = UserStatus.fromCode(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "无效的用户状态值: " + request.getStatus());
        }

        User user = userRepository.selectById(id);
        if (user == null) {
            user = userRepository.selectByIdIncludingDeleted(id);
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UserStatus currentStatus = UserStatus.fromCode(user.getStatus());
        Integer oldStatus = user.getStatus();
        Integer newStatusCode = request.getStatus();

        if (currentStatus == newStatus) {
            return;
        }

        if (currentStatus == null || !currentStatus.canTransitionTo(newStatus)) {
            log.warn("非法用户状态转换: userId={} {} -> {}", id, currentStatus, newStatus);
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "不允许从 " + currentStatus + " 转换到 " + newStatus);
        }

        if (currentStatus == UserStatus.DELETED && newStatus == UserStatus.ACTIVE) {
            if (user.getDeletedAt() != null) {
                long daysSinceDeleted = ChronoUnit.DAYS.between(user.getDeletedAt(), LocalDateTime.now());
                if (daysSinceDeleted > 180) {
                    throw new BusinessException(ErrorCode.DELETED_USER_RETENTION_EXPIRED);
                }
            }
            userRepository.restoreToActive(id);
            writeStatusAuditLog(user, oldStatus, newStatusCode);
            evictUserStatusCache(id);
            log.info("用户状态变更(恢复): userId={} {} -> {}", id, currentStatus, newStatus);
            return;
        }

        switch (newStatus) {
            case ACTIVE:
                user.setStatus(1);
                user.setDeletedAt(null);
                break;
            case DISABLED:
                user.setStatus(2);
                redisUtil.clearLoginFailure(user.getUsername());
                break;
            case DELETED:
                user.setStatus(3);
                user.setDeletedAt(LocalDateTime.now());
                redisUtil.clearLoginFailure(user.getUsername());
                break;
            default:
                user.setStatus(newStatusCode);
                break;
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);

        writeStatusAuditLog(user, oldStatus, newStatusCode);
        evictUserStatusCache(id);
        log.info("用户状态变更: userId={} {} -> {}", id, currentStatus, newStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus(status);
        updateStatus(id, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTeacherStatus(Long id, TeacherStatusRequest request) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (user.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "只有教师角色才能更新入驻审核状态");
        }

        Integer oldStatus = user.getTeacherStatus();
        Integer newStatus = request.getTeacherStatus();

        if (newStatus < 0 || newStatus > 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "教师审核状态值无效，应为 0/1/2");
        }

        user.setTeacherStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);

        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("TEACHER_STATUS_CHANGE");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        try {
            java.util.Map<String, Object> detailMap = new java.util.LinkedHashMap<>();
            detailMap.put("field", "teacherStatus");
            detailMap.put("old", oldStatus);
            detailMap.put("new", newStatus);
            detailMap.put("reason", request.getReason());
            logEntry.setDetail(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detailMap));
        } catch (Exception e) {
            log.warn("序列化教师状态变更详情失败: {}", e.getMessage());
            logEntry.setDetail("{\"field\":\"teacherStatus\",\"old\":" + oldStatus + ",\"new\":" + newStatus + "}");
        }
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);
    }

    private void evictUserStatusCache(Long id) {
        try {
            redisUtil.delete(UserStatusCheckFilter.STATUS_CACHE_PREFIX + id);
        } catch (Exception e) {
            log.warn("清除用户状态缓存失败（不影响状态变更主流程）: userId={}", id, e);
        }
    }

    private void writeStatusAuditLog(User user, Integer oldStatus, Integer newStatus) {
        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("STATUS_CHANGE");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        logEntry.setDetail("{\"field\":\"status\",\"old\":" + oldStatus + ",\"new\":" + newStatus + "}");
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);
    }
}
