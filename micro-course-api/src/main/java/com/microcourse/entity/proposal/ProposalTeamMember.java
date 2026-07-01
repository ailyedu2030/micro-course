package com.microcourse.entity.proposal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Phase 15: 教学团队成员动态表实体
 * 对应表 proposal_team_members
 */
@TableName("proposal_team_members")
public class ProposalTeamMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("proposal_id")
    private Long proposalId;

    @TableField("member_type")
    private String memberType;

    private Integer seq;

    private String name;

    private Integer age;

    private String title;

    private String organization;

    private String profession;

    @TableField("taught_courses")
    private String taughtCourses;

    @TableField("planned_courses")
    private String plannedCourses;

    public ProposalTeamMember() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public String getMemberType() { return memberType; }
    public void setMemberType(String memberType) { this.memberType = memberType; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }
    public String getTaughtCourses() { return taughtCourses; }
    public void setTaughtCourses(String taughtCourses) { this.taughtCourses = taughtCourses; }
    public String getPlannedCourses() { return plannedCourses; }
    public void setPlannedCourses(String plannedCourses) { this.plannedCourses = plannedCourses; }
}
