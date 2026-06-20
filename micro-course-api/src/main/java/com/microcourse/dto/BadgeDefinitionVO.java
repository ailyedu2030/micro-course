package com.microcourse.dto;

import java.time.LocalDateTime;

public class BadgeDefinitionVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private String category;
    private String criteria;
    private LocalDateTime createdAt;

    public BadgeDefinitionVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCriteria() { return criteria; }
    public void setCriteria(String criteria) { this.criteria = criteria; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
