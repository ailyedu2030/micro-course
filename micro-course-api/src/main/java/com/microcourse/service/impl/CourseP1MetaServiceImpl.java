package com.microcourse.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.CourseP1MetaService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * F-2026-08-10-12: P1 课程级元信息应用实现
 *
 * 抽离自 CourseAdminServiceImpl.applyP1CourseMeta（独立 service 避免主类超 800 行）。
 * teachingPhilosophy 字段序列化失败时抛 BAD_REQUEST_PARAM（与原行为一致）。
 */
@Service
public class CourseP1MetaServiceImpl implements CourseP1MetaService {

    private final ObjectMapper objectMapper;

    public CourseP1MetaServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void applyP1CourseMeta(Course course, String hid, Integer totalHours, Integer totalWeeks,
                                   String learningMode, String evaluationScheme,
                                   List<String> teachingPhilosophy) {
        if (hid != null) course.setHid(hid);
        if (totalHours != null) course.setTotalHours(totalHours);
        if (totalWeeks != null) course.setTotalWeeks(totalWeeks);
        if (learningMode != null) course.setLearningMode(learningMode);
        if (evaluationScheme != null) course.setEvaluationScheme(evaluationScheme);
        if (teachingPhilosophy != null && !teachingPhilosophy.isEmpty()) {
            try {
                course.setTeachingPhilosophy(objectMapper.writeValueAsString(teachingPhilosophy));
            } catch (JsonProcessingException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "teachingPhilosophy 序列化失败: " + e.getMessage());
            }
        }
    }
}