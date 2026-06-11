package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.entity.OperationLog;

import java.time.LocalDateTime;

/**
 * 操作日志服务接口
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
public interface OperationLogService {

    /**
     * 记录操作日志（当前同步插入，Phase 6 改为异步）
     *
     * @param operationLog 日志记录
     */
    void log(OperationLog operationLog);

    /**
     * 分页查询操作日志
     *
     * @param userId     操作用户ID（可选）
     * @param action     操作类型（可选）
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @param page       页码（从0开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<OperationLog> pageQuery(Long userId, String action,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        int page, int size);
}