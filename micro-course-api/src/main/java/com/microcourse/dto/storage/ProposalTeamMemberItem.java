package com.microcourse.dto.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class ProposalTeamMemberItem {

    private Long id;
    private String memberType;
    private Integer seq;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @Min(value = 18, message = "年龄不能小于18岁")
    @Max(value = 70, message = "年龄不能大于70岁")
    private Integer age;
    private String title;
    private String organization;
    private String profession;
    private String taughtCourses;
    private String plannedCourses;

    public ProposalTeamMemberItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
