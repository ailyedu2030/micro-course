package com.microcourse.dto;

/**
 * 5 种课件/课程类型分布 VO（F-2026-08-10-06）
 *
 * 5 维度：HTML 课件 / PPT 课件 / 视频课件 / 线下课程 / 练习课件（章节维度聚合）。
 * 后端 CourseType 枚举保持 V333 4 值；"练习课件"为含 EXERCISE 章节的去重课程数（章节维度）。
 *
 * 字段命名与前端 COURSE_TYPE_CONFIG.label 一致，确保 UI 展示与契约对齐。
 */
public class CoursewareTypeDistributionVO {

    /** 视频课件课程数（courseType=VIDEO） */
    private Long videoCourses;

    /** HTML 课件课程数（courseType=HTML_COURSEWARE） */
    private Long htmlCoursewareCourses;

    /** PPT 课件课程数（courseType=PPT_COURSEWARE） */
    private Long pptCoursewareCourses;

    /** 线下课程数（courseType=OFFLINE） */
    private Long offlineCourses;

    /** 练习课件课程数（含 EXERCISE 章节的去重课程数，章节维度聚合） */
    private Long coursesWithExercises;

    public CoursewareTypeDistributionVO() {}

    public CoursewareTypeDistributionVO(Long videoCourses, Long htmlCoursewareCourses,
                                       Long pptCoursewareCourses, Long offlineCourses,
                                       Long coursesWithExercises) {
        this.videoCourses = videoCourses;
        this.htmlCoursewareCourses = htmlCoursewareCourses;
        this.pptCoursewareCourses = pptCoursewareCourses;
        this.offlineCourses = offlineCourses;
        this.coursesWithExercises = coursesWithExercises;
    }

    /** 5 维度总和（练习课件不算课程维度，所以 sum=前 4 项） */
    public Long getTotal() {
        long t = 0;
        if (videoCourses != null) t += videoCourses;
        if (htmlCoursewareCourses != null) t += htmlCoursewareCourses;
        if (pptCoursewareCourses != null) t += pptCoursewareCourses;
        if (offlineCourses != null) t += offlineCourses;
        return t;
    }

    public Long getVideoCourses() { return videoCourses; }
    public void setVideoCourses(Long videoCourses) { this.videoCourses = videoCourses; }

    public Long getHtmlCoursewareCourses() { return htmlCoursewareCourses; }
    public void setHtmlCoursewareCourses(Long htmlCoursewareCourses) { this.htmlCoursewareCourses = htmlCoursewareCourses; }

    public Long getPptCoursewareCourses() { return pptCoursewareCourses; }
    public void setPptCoursewareCourses(Long pptCoursewareCourses) { this.pptCoursewareCourses = pptCoursewareCourses; }

    public Long getOfflineCourses() { return offlineCourses; }
    public void setOfflineCourses(Long offlineCourses) { this.offlineCourses = offlineCourses; }

    public Long getCoursesWithExercises() { return coursesWithExercises; }
    public void setCoursesWithExercises(Long coursesWithExercises) { this.coursesWithExercises = coursesWithExercises; }
}