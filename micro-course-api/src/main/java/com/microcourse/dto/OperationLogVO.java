package com.microcourse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 操作日志 VO
 * 用于 API 返回，过滤敏感字段，detail 从 JSON String 转为 Map
 *
 * @author Phase10-Development-Team
 * @since 2026-06-12
 */
public class OperationLogVO {

    private Long id;

    @JsonProperty("userId")
    private Long userId;

    private String username;

    private String action;

    /**
     * 功能模块（从 action/targetType 推断）
     * AUTH / USER / COURSE / GRADE / SETTING / PERMISSION
     */
    private String module;

    @JsonProperty("targetType")
    private String targetType;

    @JsonProperty("targetId")
    private Long targetId;

    /**
     * 操作详情，从 JSON String 解析为 Map
     * schema: {"field":"username","oldValue":"john","newValue":"johnny"}
     */
    private Map<String, Object> detail;

    private String ip;

    /**
     * 操作状态：1=成功, 0=失败
     */
    private Integer status;

    /**
     * 错误信息（操作失败时填充）
     */
    @JsonProperty("errorMessage")
    private String errorMessage;

    /**
     * 耗时（毫秒）
     */
    private Integer duration;

    /**
     * 请求方法（从 detail JSON 提取，如 GET/POST/PUT/DELETE）
     */
    private String method;

    /**
     * 请求路径（从 detail JSON 提取）
     */
    private String path;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public OperationLogVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public Map<String, Object> getDetail() { return detail; }
    public void setDetail(Map<String, Object> detail) { this.detail = detail; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
