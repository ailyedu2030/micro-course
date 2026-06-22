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
    public void setCourses(List<MicroSpecialtyCourseVO> v) { courses = v; }
    public List<MicroSpecialtyTeacherVO> getTeachers() { return teachers; }
    public void setTeachers(List<MicroSpecialtyTeacherVO> v) { teachers = v; }
    public MicroSpecialtyStatsVO getStats() { return stats; }
    public void setStats(MicroSpecialtyStatsVO v) { stats = v; }
}
