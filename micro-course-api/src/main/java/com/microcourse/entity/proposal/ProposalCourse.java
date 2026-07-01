package com.microcourse.entity.proposal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/**
 * Phase 15: 课程体系动态表实体
 * 对应表 proposal_courses
 */
@TableName("proposal_courses")
public class ProposalCourse {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("proposal_id")
    private Long proposalId;

    @TableField("module_name")
    private String moduleName;

    @TableField("course_name")
    private String courseName;

    private Integer hours;

    private BigDecimal credits;

    private String semester;

    @TableField("sort_order")
    private Integer sortOrder;

    public ProposalCourse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public BigDecimal getCredits() { return credits; }
    public void setCredits(BigDecimal credits) { this.credits = credits; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
