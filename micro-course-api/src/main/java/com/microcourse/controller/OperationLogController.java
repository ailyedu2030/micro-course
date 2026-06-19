package com.microcourse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.OperationLogVO;
import com.microcourse.entity.OperationLog;
import com.microcourse.entity.User;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.OperationLogService;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
public class OperationLogController {

    private static final Logger log = LoggerFactory.getLogger(OperationLogController.class);

    private final OperationLogService operationLogService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public OperationLogController(OperationLogService operationLogService,
                                  UserRepository userRepository,
                                  ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询操作日志列表
     * GET /api/operation-logs
     * 权限: ADMIN / ACADEMIC
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<OperationLogVO>> page(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTime,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 200) int size) {

        // P0-1: LocalDate → LocalDateTime 转换（开始日期取 00:00:00，结束日期取 23:59:59）
        LocalDateTime startDateTime = startTime != null ? startTime.atStartOfDay() : null;
        LocalDateTime endDateTime = endTime != null ? endTime.atTime(LocalTime.MAX) : null;

        // P0-3: username 模糊搜索 → 转换为 userIds
        List<Long> userIds = null;
        if (username != null && !username.isBlank()) {
            LambdaQueryWrapper<User> uWrapper = new LambdaQueryWrapper<>();
            uWrapper.like(User::getUsername, username);
            uWrapper.select(User::getId);
            List<User> matchedUsers = userRepository.selectList(uWrapper);
            userIds = matchedUsers.stream().map(User::getId).collect(Collectors.toList());
            if (userIds.isEmpty()) {
                // 没有匹配用户，直接返回空结果
                return R.ok(PageResult.of(List.of(), 0L, page, size));
            }
        }

        PageResult<OperationLog> result = operationLogService.pageQuery(
                userId, userIds, action, module, targetId,
                startDateTime, endDateTime, page, size);

        // P1-1: 批量查询用户名，避免 N+1
        Map<Long, String> userNameMap = buildUserNameMap(result.getItems());

        List<OperationLogVO> vos = result.getItems().stream()
                .map(entity -> convertToVO(entity, userNameMap))
                .collect(Collectors.toList());
        return R.ok(PageResult.of(vos, result.getTotalElements(), result.getPage(), result.getSize()));
    }

    /**
     * P1-1: 批量查询所有日志涉及的 userId → username 映射
     */
    private Map<Long, String> buildUserNameMap(List<OperationLog> logs) {
        Set<Long> userIdSet = logs.stream()
                .map(OperationLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIdSet.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<User> users = userRepository.selectBatchIds(userIdSet);
            return users.stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量查询用户名失败, userIds={}", userIdSet, e);
            return Collections.emptyMap();
        }
    }

    private OperationLogVO convertToVO(OperationLog entity, Map<Long, String> userNameMap) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setAction(entity.getAction());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setIp(entity.getIp());
        vo.setCreatedAt(entity.getCreatedAt());

        // success Boolean → status Integer (1=成功, 0=失败)
        vo.setStatus(entity.getSuccess() != null && entity.getSuccess() ? 1 : 0);

        // P0-4: duration 从 entity.durationMs 映射
        vo.setDuration(entity.getDurationMs());

        // P0-4: module 从 action/targetType 推断
        String action = entity.getAction();
        if ("LOGIN".equals(action) || "LOGOUT".equals(action)) {
            vo.setModule("AUTH");
        } else if (entity.getTargetType() != null && !entity.getTargetType().isEmpty()) {
            vo.setModule(entity.getTargetType());
        } else {
            vo.setModule(null);
        }

        // P1-1: 从批量查询的 Map 中获取 username
        if (entity.getUserId() != null) {
            vo.setUsername(userNameMap.getOrDefault(entity.getUserId(), null));
        }

        // detail JSON String → Map，同时提取 method/path/errorMessage
        if (entity.getDetail() != null && !entity.getDetail().isEmpty()) {
            try {
                Map<String, Object> detailMap = objectMapper.readValue(
                        entity.getDetail(), new TypeReference<Map<String, Object>>() {});
                vo.setDetail(detailMap);

                // P0-4: 从 detail 提取 method 和 path
                if (detailMap.containsKey("method")) {
                    vo.setMethod(String.valueOf(detailMap.get("method")));
                }
                if (detailMap.containsKey("path")) {
                    vo.setPath(String.valueOf(detailMap.get("path")));
                }

                // 如果操作失败，从 detail 中提取 errorMessage
                if (vo.getStatus() == 0 && detailMap.containsKey("errorMessage")) {
                    vo.setErrorMessage(String.valueOf(detailMap.get("errorMessage")));
                }
            } catch (JsonProcessingException e) {
                // P2: JSON 解析异常记录日志
                log.warn("解析操作日志 detail JSON 失败, logId={}, detail={}", entity.getId(), entity.getDetail(), e);
                vo.setDetail(Map.of("raw", entity.getDetail()));
            }
        }

        return vo;
    }
}
