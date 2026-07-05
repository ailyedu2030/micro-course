package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.entity.OperationLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * @param userId     操作用户ID（可选，精确匹配）
     * @param userIds    操作用户ID列表（可选，IN 匹配，用于 username 模糊搜索后传入）
     * @param action     操作类型（可选）
     * @param module     功能模块（可选，AUTH 映射 action IN，其他映射 targetType）
     * @param targetId   目标对象ID（可选）
     * @param startTime  开始时间（可选）
     * @param endTime    结束时间（可选）
     * @param page       页码（从0开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<OperationLog> pageQuery(Long userId, List<Long> userIds, String action,
                                        String module, Long targetId,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        int page, int size);

    /**
     * 分页查询操作日志（LocalDate 版本，自动处理日期转换 + username→userIds 搜索）
     *
     * @param userId     操作用户ID（可选，精确匹配）
     * @param username   用户名（可选，模糊搜索后转换为 userIds）
     * @param action     操作类型（可选）
     * @param module     功能模块（可选）
     * @param targetId   目标对象ID（可选）
     * @param startDate  开始日期（可选，自动转为 00:00:00）
     * @param endDate    结束日期（可选，自动转为 23:59:59）
     * @param page       页码（从0开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<OperationLog> pageQuery(Long userId, String username, String action,
                                        String module, Long targetId,
                                        LocalDate startDate, LocalDate endDate,
                                        int page, int size);

    /**
     * 按用户名模糊搜索用户ID列表
     * @param username 用户名（支持模糊）
     * @return 用户ID列表
     */
    List<Long> findUserIdsByUsername(String username);

    /**
     * 批量查询用户 ID → 用户名映射
     * @param userIds 用户ID集合
     * @return userId → username 映射
     */
    Map<Long, String> batchFindUsernames(Set<Long> userIds);
}
