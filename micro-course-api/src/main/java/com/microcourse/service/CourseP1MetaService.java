package com.microcourse.service;

import com.microcourse.entity.Course;

import java.util.List;

/**
 * F-2026-08-10-12: P1 课程级元信息应用 helper（独立 service 避免 CourseAdminServiceImpl 超 800 行）
 *
 * 职责：把 P1 课程架构字段（hid / totalHours / totalWeeks / learningMode / evaluationScheme / teachingPhilosophy）
 * 从 request 应用到 course entity。create/update 共享。
 */
public interface CourseP1MetaService {

    void applyP1CourseMeta(Course course, String hid, Integer totalHours, Integer totalWeeks,
                            String learningMode, String evaluationScheme,
                            List<String> teachingPhilosophy);
}