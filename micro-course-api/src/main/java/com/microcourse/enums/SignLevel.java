package com.microcourse.enums;

/**
 * Phase 15: 签字级别枚举
 *
 * <p>对齐 docs/开发规划/phase15-storage-application-spec.md §10.2</p>
 * <p>固定 3 行（LEAD/DEPT/SCHOOL）在创建草稿时自动初始化，
 * SHARED_UNIT 由模块⑤动态追加。</p>
 */
public enum SignLevel {

    /** 微专业负责人意见 */
    LEAD("微专业负责人意见"),

    /** 学院意见 */
    DEPT("学院意见"),

    /** 学校意见 */
    SCHOOL("学校意见"),

    /** 共建共享单位意见 */
    SHARED_UNIT("共建共享单位意见");

    private final String label;

    SignLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 根据 label 查找枚举
     */
    public static SignLevel fromLabel(String label) {
        if (label == null) {
            return null;
        }
        for (SignLevel level : values()) {
            if (level.label.equals(label)) {
                return level;
            }
        }
        return null;
    }

    /**
     * 判断是否为固定行（不可删除）
     */
    public boolean isFixed() {
        return this == LEAD || this == DEPT || this == SCHOOL;
    }
}
