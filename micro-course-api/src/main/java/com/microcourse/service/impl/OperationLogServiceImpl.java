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
    public PageResult<OperationLog> pageQuery(Long userId, String action,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             int page, int size) {
        Page<OperationLog> pg = new Page<>(page + 1, size); // MyBatis-Plus 1-based
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (action != null && !action.isBlank()) {
            wrapper.eq(OperationLog::getAction, action);
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