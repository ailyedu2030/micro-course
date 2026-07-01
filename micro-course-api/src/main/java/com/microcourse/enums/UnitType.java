package com.microcourse.enums;

/**
 * Phase 15: 共享单位类型枚举
 *
 * <p>对齐 docs/开发规划/phase15-storage-application-spec.md §10.3</p>
 */
public enum UnitType {

    /** 共建高校 */
    CO_BUILD_UNIV("共建高校"),

    /** 合作企业 */
    ENTERPRISE("合作企业"),

    /** 拟共享高校 */
    SHARE_UNIV("拟共享高校");

    private final String label;

    UnitType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 根据 label 查找枚举
     */
    public static UnitType fromLabel(String label) {
        if (label == null) {
            return null;
        }
        for (UnitType type : values()) {
            if (type.label.equals(label)) {
                return type;
            }
        }
        return null;
    }
}
