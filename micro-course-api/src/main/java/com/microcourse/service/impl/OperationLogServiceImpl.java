package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.OperationLog;
import com.microcourse.repository.OperationLogRepository;
import com.microcourse.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务实现
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public OperationLogServiceImpl(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
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
}
