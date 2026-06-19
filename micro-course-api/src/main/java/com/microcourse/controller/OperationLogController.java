package com.microcourse.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.OperationLogVO;
import com.microcourse.entity.OperationLog;
import com.microcourse.dto.UserVO;
import com.microcourse.service.UserService;
import com.microcourse.service.OperationLogService;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    private final OperationLogService operationLogService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public OperationLogController(OperationLogService operationLogService,
                                  UserService userService,
                                  ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询操作日志列表
     * GET /api/operation-logs
     * 权限: ADMIN
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<OperationLogVO>> page(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 200) int size) {
        PageResult<OperationLog> result = operationLogService.pageQuery(userId, action, startTime, endTime, page, size);
        List<OperationLogVO> vos = result.getItems().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return R.ok(PageResult.of(vos, result.getTotalElements(), result.getPage(), result.getSize()));
    }

    private OperationLogVO convertToVO(OperationLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setAction(log.getAction());
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId());
        vo.setIp(log.getIp());
        vo.setCreatedAt(log.getCreatedAt());
        // success Boolean → status Integer (1=成功, 0=失败)
        vo.setStatus(log.getSuccess() != null && log.getSuccess() ? 1 : 0);
        // detail JSON String → Map
        if (log.getDetail() != null && !log.getDetail().isEmpty()) {
            try {
                Map<String, Object> detailMap = objectMapper.readValue(
                        log.getDetail(), new TypeReference<Map<String, Object>>() {});
                vo.setDetail(detailMap);
                // 如果操作失败，从 detail 中提取 errorMessage
                if (vo.getStatus() == 0 && detailMap.containsKey("errorMessage")) {
                    vo.setErrorMessage(String.valueOf(detailMap.get("errorMessage")));
                }
            } catch (JsonProcessingException e) {
                vo.setDetail(Map.of("raw", log.getDetail()));
            }
        }
        // 获取用户名
        if (log.getUserId() != null) {
            try {
                UserVO user = userService.getUserById(log.getUserId());
                if (user != null) {
                    vo.setUsername(user.getUsername());
                }
            } catch (Exception e) {
                // user may be deleted
            }
        }
        return vo;
    }
}