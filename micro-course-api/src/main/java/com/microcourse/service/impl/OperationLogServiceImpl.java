package com.microcourse.service.impl;

import com.microcourse.entity.OperationLog;
import com.microcourse.repository.OperationLogRepository;
import com.microcourse.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogRepository operationLogRepository;

    @Autowired
    public OperationLogServiceImpl(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @Override
    public void log(OperationLog operationLog) {
        // Phase 6: 改为异步记录
        operationLogRepository.insert(operationLog);
    }
}