package com.microcourse.service;

import com.microcourse.entity.OperationLog;

/**
 * 操作日志服务接口
 */
public interface OperationLogService {

    /**
     * 记录操作日志（当前同步插入，Phase 6 改为异步）
     */
    void log(OperationLog operationLog);
}