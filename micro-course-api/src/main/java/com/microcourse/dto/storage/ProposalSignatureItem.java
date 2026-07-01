package com.microcourse.dto.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;

/**
 * P0-1 修复：接纳前端嵌套签名结构 { opinionText, signature:{type,text,imageUrl}, seal:{type,text,imageUrl}, signDate, remark }
 * 同时保留扁平字段用于预览等只读场景的向后兼容。
 */
public class ProposalSignatureItem {

    private Long id;
    @NotBlank(message = "签字级别不能为空")
    private String signLevel;
    private String opinionText;

    // 嵌套签名对象（前端 v-model 结构）
    private SignatureFile signature;
    private SignatureFile seal;

    // 扁平字段（向后兼容预览/导出）
    @JsonIgnore
    private String signatureType;
    @JsonIgnore
    private String signatureText;
    @JsonIgnore
    private String signatureImageUrl;
    @JsonIgnore
    private String sealImageUrl;

    private String signDate;
    private String remark;
    private Integer unitSeq;

    /** 签名/公章文件内部类 */
    public static class SignatureFile {
        private String type = "TEXT";
        private String text;
        private String imageUrl;

        public SignatureFile() {}
        public SignatureFile(String type, String text, String imageUrl) {
            this.type = type; this.text = text; this.imageUrl = imageUrl;
        }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    public ProposalSignatureItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSignLevel() { return signLevel; }
    public void setSignLevel(String signLevel) { this.signLevel = signLevel; }
    public String getOpinionText() { return opinionText; }
    public void setOpinionText(String opinionText) { this.opinionText = opinionText; }

    public SignatureFile getSignature() { return signature; }
    public void setSignature(SignatureFile signature) { this.signature = signature; }
    public SignatureFile getSeal() { return seal; }
    public void setSeal(SignatureFile seal) { this.seal = seal; }

    // 向后兼容的扁平 getter（用于预览/导出）
    public String getSignatureType() {
        if (signatureType != null) return signatureType;
        return signature != null ? signature.getType() : "TEXT";
    }
    public void setSignatureType(String signatureType) { this.signatureType = signatureType; }
    public String getSignatureText() {
        if (signatureText != null) return signatureText;
        return signature != null ? signature.getText() : null;
    }
    public void setSignatureText(String signatureText) { this.signatureText = signatureText; }
    public String getSignatureImageUrl() {
        if (signatureImageUrl != null) return signatureImageUrl;
        return signature != null ? signature.getImageUrl() : null;
    }
    public void setSignatureImageUrl(String signatureImageUrl) { this.signatureImageUrl = signatureImageUrl; }
    public String getSealImageUrl() {
        if (sealImageUrl != null) return sealImageUrl;
        return seal != null ? seal.getImageUrl() : null;
    }
    public void setSealImageUrl(String sealImageUrl) { this.sealImageUrl = sealImageUrl; }

    public String getSignDate() { return signDate; }
    public void setSignDate(String signDate) { this.signDate = signDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getUnitSeq() { return unitSeq; }
    public void setUnitSeq(Integer unitSeq) { this.unitSeq = unitSeq; }
}
