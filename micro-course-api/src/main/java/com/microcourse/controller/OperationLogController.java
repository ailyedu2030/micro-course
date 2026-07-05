package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.OperationLogVO;
import com.microcourse.entity.OperationLog;
import com.microcourse.util.OperationLogAssembler;
import com.microcourse.service.OperationLogService;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 操作日志控制器
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/api/operation-logs")
@Validated
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final OperationLogAssembler operationLogAssembler;

    public OperationLogController(OperationLogService operationLogService,
                                   OperationLogAssembler operationLogAssembler) {
        this.operationLogService = operationLogService;
        this.operationLogAssembler = operationLogAssembler;
    }

    /**
     * 分页查询操作日志列表
     * GET /api/operation-logs
     * 权限: ADMIN / ACADEMIC
     * 日期转换与 username→userIds 搜索已下沉 Service 层，
     * Entity→VO 转换委托给 OperationLogAssembler。
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("查询操作日志")
    public R<PageResult<OperationLogVO>> page(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTime,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {

        PageResult<OperationLog> result = operationLogService.pageQuery(
                userId, username, action, module, targetId,
                startTime, endTime, page, size);

        // 批量查询用户名并装配 VO
        Map<Long, String> userNameMap = buildUserNameMap(result.getItems());
        List<OperationLogVO> vos = result.getItems().stream()
                .map(entity -> operationLogAssembler.toVO(entity, userNameMap))
                .collect(Collectors.toList());
        return R.ok(PageResult.of(vos, result.getTotalElements(), result.getPage(), result.getSize()));
    }

    /**
     * P1-1: 批量查询所有日志涉及的 userId → username 映射（委托 Service）
     */
    private Map<Long, String> buildUserNameMap(List<OperationLog> logs) {
        Set<Long> userIdSet = logs.stream()
                .map(OperationLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIdSet.isEmpty()) {
            return Collections.emptyMap();
        }
        return operationLogService.batchFindUsernames(userIdSet);
    }

}
