package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("platform_share_config")
public class PlatformShareConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("config_key")
    private String configKey;

    @TableField("config_value")
    private String configValue;

    @TableField("description")
    private String description;

    @TableField("active")
    private Boolean active;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String k) { this.configKey = k; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String v) { this.configValue = v; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean a) { this.active = a; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long u) { this.updatedBy = u; }
}
