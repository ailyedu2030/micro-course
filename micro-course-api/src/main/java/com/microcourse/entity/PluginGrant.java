package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("plugin_grants")
public class PluginGrant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String pluginId;
    private String grantType;
    private Long granteeId;
    private LocalDateTime createdAt;

    public PluginGrant() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getGrantType() { return grantType; }
    public void setGrantType(String grantType) { this.grantType = grantType; }
    public Long getGranteeId() { return granteeId; }
    public void setGranteeId(Long granteeId) { this.granteeId = granteeId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
