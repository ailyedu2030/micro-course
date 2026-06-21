package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.OperationLog;
import com.microcourse.entity.User;
import com.microcourse.repository.OperationLogRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;

    public OperationLogServiceImpl(OperationLogRepository operationLogRepository,
                                   UserRepository userRepository) {
        this.operationLogRepository = operationLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void log(OperationLog operationLog) {
        // Phase 6: 改为异步记录
        operationLogRepository.insert(operationLog);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OperationLog> pageQuery(Long userId, List<Long> userIds, String action,
                                             String module, Long targetId,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             int page, int size) {
        Page<OperationLog> pg = new Page<>(page + 1, size); // MyBatis-Plus 1-based
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        // userId 精确匹配
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        // userIds IN 匹配（username 模糊搜索转换后）
        if (userIds != null && !userIds.isEmpty()) {
            wrapper.in(OperationLog::getUserId, userIds);
        }
        // action 精确匹配
        if (action != null && !action.isBlank()) {
            wrapper.eq(OperationLog::getAction, action);
        }
        // module 筛选：AUTH → action IN ('LOGIN','LOGOUT')，其他 → targetType 精确匹配
        if (module != null && !module.isBlank()) {
            if ("AUTH".equals(module)) {
                wrapper.in(OperationLog::getAction, List.of("LOGIN", "LOGOUT"));
            } else {
                wrapper.eq(OperationLog::getTargetType, module);
            }
        }
        // targetId 精确匹配
        if (targetId != null) {
            wrapper.eq(OperationLog::getTargetId, targetId);
        }
        if (startTime != null) {
            wrapper.ge(OperationLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(OperationLog::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        IPage<OperationLog> result = operationLogRepository.selectPage(pg, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findUserIdsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<User> uWrapper = new LambdaQueryWrapper<>();
        uWrapper.like(User::getUsername, username);
        uWrapper.select(User::getId);
        List<User> matchedUsers = userRepository.selectList(uWrapper);
        return matchedUsers.stream().map(User::getId).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> batchFindUsernames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<User> users = userRepository.selectBatchIds(userIds);
            return users.stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
