package com.microcourse.dto;

/**
 * 班级学生名单 VO（Round 5-3 P1-10 新增）。
 *
 * <p>承载 {@code GET /api/classes/{id}/students} 的单条学生数据，字段均取自 {@code users} 表
 * （classId 关联），不引入额外联表与新字段，保持 DB schema 不变。</p>
 */
public class ClassStudentVO {

    private Long userId;
    private Long classId;
    private String username;
    private String realName;
    private String studentNo;
    private Integer status;

    public ClassStudentVO() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
