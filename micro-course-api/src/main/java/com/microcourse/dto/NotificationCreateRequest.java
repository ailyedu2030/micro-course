package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class NotificationCreateRequest {

    /** 单用户模式：指定单个接收用户 */
    private Long userId;

    /** 群发模式：批量接收用户列表 */
    private List<Long> targetUserIds;

    /** 发送给所有用户（管理员公告） */
    private Boolean sendToAll;

    @NotBlank(message = "通知类型不能为空")
    @Size(max = 30, message = "通知类型最长30字符")
    private String type;

    @NotBlank(message = "通知标题不能为空")
    @Size(max = 200, message = "通知标题最长200字符")
    private String title;

    private String content;

    private Long relatedId;

    @Size(max = 20, message = "渠道最长20字符")
    private String channel;

    public NotificationCreateRequest() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public List<Long> getTargetUserIds() { return targetUserIds; }
    public void setTargetUserIds(List<Long> targetUserIds) { this.targetUserIds = targetUserIds; }
    public Boolean getSendToAll() { return sendToAll; }
    public void setSendToAll(Boolean sendToAll) { this.sendToAll = sendToAll; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getRelatedId() { return relatedId; }
    public void setRelatedId(Long relatedId) { this.relatedId = relatedId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
}