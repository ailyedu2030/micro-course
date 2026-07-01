package com.microcourse.dto.storage;

import jakarta.validation.constraints.NotBlank;

/**
 * P0-2 修复：添加签字字段，使其与前端共享单位签字数据对齐。
 * 每个共享单位可携带独立签字数据，后端负责同步到 proposal_signatures 表。
 */
public class ProposalSharedUnitItem {

    private Long id;
    @NotBlank(message = "单位名称不能为空")
    private String unitName;
    @NotBlank(message = "单位类型不能为空")
    private String unitType;
    private Integer sortOrder;

    // P0-2: 签字相关字段（前端 v-model 结构）
    private String opinionText;
    private ProposalSignatureItem.SignatureFile signature;
    private ProposalSignatureItem.SignatureFile seal;
    private String signDate;
    private String remark;

    public ProposalSharedUnitItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }
    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getOpinionText() { return opinionText; }
    public void setOpinionText(String opinionText) { this.opinionText = opinionText; }
    public ProposalSignatureItem.SignatureFile getSignature() { return signature; }
    public void setSignature(ProposalSignatureItem.SignatureFile signature) { this.signature = signature; }
    public ProposalSignatureItem.SignatureFile getSeal() { return seal; }
    public void setSeal(ProposalSignatureItem.SignatureFile seal) { this.seal = seal; }
    public String getSignDate() { return signDate; }
    public void setSignDate(String signDate) { this.signDate = signDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
