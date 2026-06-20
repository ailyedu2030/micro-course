package com.microcourse.dto.bundle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BundleVO {
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private Long creatorId;
    private String creatorName;
    private BigDecimal price;
    private Boolean isFree;
    private Integer studentCount;
    private Integer status;
    private List<BundleItemVO> items;
    private LocalDateTime createdAt;

    public BundleVO() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public List<BundleItemVO> getItems() { return items; }
    public void setItems(List<BundleItemVO> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
