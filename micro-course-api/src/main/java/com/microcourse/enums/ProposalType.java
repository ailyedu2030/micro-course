package com.microcourse.enums;

/**
 * Phase 15: 微专业类型枚举
 *
 * <p>对齐 docs/开发规划/phase15-storage-application-spec.md §10.1</p>
 */
public enum ProposalType {

    /** 急需紧缺型 */
    URGENT_NEED("急需紧缺型");

    private final String label;

    ProposalType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 根据 label 查找枚举
     */
    public static ProposalType fromLabel(String label) {
        if (label == null) {
            return null;
        }
        for (ProposalType type : values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        return null;
    }
}
