package com.microcourse.dto;

import com.microcourse.entity.PlatformShareConfig;

import java.time.LocalDateTime;

public class PlatformShareConfigDTO {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private Boolean active;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    public static PlatformShareConfigDTO fromEntity(PlatformShareConfig e) {
        PlatformShareConfigDTO d = new PlatformShareConfigDTO();
        d.id = e.getId();
        d.configKey = e.getConfigKey();
        d.configValue = e.getConfigValue();
        d.description = e.getDescription();
        d.active = e.getActive();
        d.updatedAt = e.getUpdatedAt();
        d.updatedBy = e.getUpdatedBy();
        return d;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
