package com.microcourse.entity.proposal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Phase 15: 签字盖章记录表实体
 * 对应表 proposal_signatures
 */
@TableName("proposal_signatures")
public class ProposalSignature {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("proposal_id")
    private Long proposalId;

    @TableField("sign_level")
    private String signLevel;

    @TableField("unit_seq")
    private Integer unitSeq;

    @TableField("opinion_text")
    private String opinionText;

    @TableField("signature_type")
    private String signatureType;

    @TableField("signature_text")
    private String signatureText;

    @TableField("signature_image_url")
    private String signatureImageUrl;

    @TableField("seal_image_url")
    private String sealImageUrl;

    @TableField("sign_date")
    private LocalDateTime signDate;

    private String remark;

    public ProposalSignature() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public String getSignLevel() { return signLevel; }
    public void setSignLevel(String signLevel) { this.signLevel = signLevel; }
    public Integer getUnitSeq() { return unitSeq; }
    public void setUnitSeq(Integer unitSeq) { this.unitSeq = unitSeq; }
    public String getOpinionText() { return opinionText; }
    public void setOpinionText(String opinionText) { this.opinionText = opinionText; }
    public String getSignatureType() { return signatureType; }
    public void setSignatureType(String signatureType) { this.signatureType = signatureType; }
    public String getSignatureText() { return signatureText; }
    public void setSignatureText(String signatureText) { this.signatureText = signatureText; }
    public String getSignatureImageUrl() { return signatureImageUrl; }
    public void setSignatureImageUrl(String signatureImageUrl) { this.signatureImageUrl = signatureImageUrl; }
    public String getSealImageUrl() { return sealImageUrl; }
    public void setSealImageUrl(String sealImageUrl) { this.sealImageUrl = sealImageUrl; }
    public LocalDateTime getSignDate() { return signDate; }
    public void setSignDate(LocalDateTime signDate) { this.signDate = signDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
