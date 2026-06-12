package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TeachingClassCreateRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Long teacherId;

    @NotBlank(message = "教学班名称不能为空")
    @Size(max = 100, message = "教学班名称不能超过100字符")
    private String name;

    private Integer maxStudents;
    private String schedule;
    private String location;

    @Size(max = 20, message = "学期不能超过20字符")
    private String semester;

    private List<ClassScheduleDTO> classSchedules;

    public TeachingClassCreateRequest() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public List<ClassScheduleDTO> getClassSchedules() { return classSchedules; }
    public void setClassSchedules(List<ClassScheduleDTO> classSchedules) { this.classSchedules = classSchedules; }
}