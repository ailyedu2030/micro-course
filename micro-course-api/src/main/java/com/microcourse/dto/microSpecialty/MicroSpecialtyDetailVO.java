package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyDetailVO extends MicroSpecialtyVO {

    private List<MicroSpecialtyCourseVO> courses;
    private List<MicroSpecialtyTeacherVO> teachers;
    private MicroSpecialtyStatsVO stats;

    public MicroSpecialtyDetailVO() {}

    public List<MicroSpecialtyCourseVO> getCourses() { return courses; }
    public void setCourses(List<MicroSpecialtyCourseVO> courses) { this.courses = courses; }

    public List<MicroSpecialtyTeacherVO> getTeachers() { return teachers; }
    public void setTeachers(List<MicroSpecialtyTeacherVO> teachers) { this.teachers = teachers; }

    public MicroSpecialtyStatsVO getStats() { return stats; }
    public void setStats(MicroSpecialtyStatsVO stats) { this.stats = stats; }
}
