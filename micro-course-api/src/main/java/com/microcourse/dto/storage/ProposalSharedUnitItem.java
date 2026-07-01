package com.microcourse.dto.storage;

import jakarta.validation.constraints.NotBlank;

public class ProposalSharedUnitItem {

    private Long id;
    @NotBlank(message = "单位名称不能为空")
    private String unitName;
    @NotBlank(message = "单位类型不能为空")
    private String unitType;
    private Integer sortOrder;

    public ProposalSharedUnitItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
