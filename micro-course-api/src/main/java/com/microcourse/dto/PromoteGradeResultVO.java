package com.microcourse.dto;

import java.util.List;

/**
 * 升级学生年级结果 VO
 */
public class PromoteGradeResultVO {

    /** 升级前的年级 */
    private String fromGrade;

    /** 升级后的年级（次年级） */
    private String toGrade;

    /** 实际升级的学生数 */
    private int affectedCount;

    /** 已毕业的学生数（升级后超过毕业年份的不再升） */
    private int graduatedCount;

    /** 已毕业的学生 username 列表（参考用） */
    private List<String> graduatedUsernames;

    public PromoteGradeResultVO() {}

    public String getFromGrade() { return fromGrade; }
    public void setFromGrade(String fromGrade) { this.fromGrade = fromGrade; }

    public String getToGrade() { return toGrade; }
    public void setToGrade(String toGrade) { this.toGrade = toGrade; }

    public int getAffectedCount() { return affectedCount; }
    public void setAffectedCount(int affectedCount) { this.affectedCount = affectedCount; }

    public int getGraduatedCount() { return graduatedCount; }
    public void setGraduatedCount(int graduatedCount) { this.graduatedCount = graduatedCount; }

    public List<String> getGraduatedUsernames() { return graduatedUsernames; }
    public void setGraduatedUsernames(List<String> graduatedUsernames) { this.graduatedUsernames = graduatedUsernames; }
}