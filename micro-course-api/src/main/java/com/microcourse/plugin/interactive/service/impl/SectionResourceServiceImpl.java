package com.microcourse.plugin.interactive.service.impl;

import com.microcourse.entity.Course;
import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.*;
import com.microcourse.plugin.interactive.entity.*;
import com.microcourse.plugin.interactive.mapper.*;
import com.microcourse.plugin.interactive.service.SectionResourceService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(rollbackFor = Exception.class)
public class SectionResourceServiceImpl implements SectionResourceService {
    private static final Logger log = LoggerFactory.getLogger(SectionResourceServiceImpl.class);

    private final SectionQuizMapper quizMapper;
    private final SectionTaskMapper taskMapper;
    private final SectionReflectionMapper reflectionMapper;
    private final CourseTrainingMapper trainingMapper;
    private final CourseFinalProjectMapper finalProjectMapper;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository sectionRepository;
    private final ObjectMapper objectMapper;

    public SectionResourceServiceImpl(SectionQuizMapper quizMapper,
                                       SectionTaskMapper taskMapper,
                                       SectionReflectionMapper reflectionMapper,
                                       CourseTrainingMapper trainingMapper,
                                       CourseFinalProjectMapper finalProjectMapper,
                                       CourseRepository courseRepository,
                                       CourseSectionRepository sectionRepository,
                                       ObjectMapper objectMapper) {
        this.quizMapper = quizMapper;
        this.taskMapper = taskMapper;
        this.reflectionMapper = reflectionMapper;
        this.trainingMapper = trainingMapper;
        this.finalProjectMapper = finalProjectMapper;
        this.courseRepository = courseRepository;
        this.sectionRepository = sectionRepository;
        this.objectMapper = objectMapper;
    }

    private void verifyOwnership(Long courseId, Long sectionId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId()))
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        CourseSection section = sectionRepository.selectById(sectionId);
        if (section == null || !section.getCourseId().equals(courseId))
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "小节不存在或不属于该课程");
    }

    @Override
    public QuizVO createQuiz(Long courseId, Long sectionId, CreateQuizRequest request) {
        verifyOwnership(courseId, sectionId);
        // 交叉审查: correctIndex 必须在 options 范围内
        if (request.getCorrectIndex() < 0 || request.getCorrectIndex() >= request.getOptions().size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                "correctIndex(" + request.getCorrectIndex() + ") 超出 options 范围[0, " + request.getOptions().size() + ")");
        }
        try {
            SectionQuiz quiz = new SectionQuiz();
            quiz.setSectionId(sectionId);
            quiz.setSlide(request.getSlide());
            quiz.setPrompt(request.getPrompt());
            quiz.setOptions(objectMapper.writeValueAsString(request.getOptions()));
            quiz.setCorrectIndex(request.getCorrectIndex());
            quiz.setExplanation(request.getExplanation());
            quiz.setCreatedAt(LocalDateTime.now());
            quizMapper.insert(quiz);
            log.info("[SectionResource] quiz created: sectionId={}, slide={}", sectionId, request.getSlide());
            return toQuizVO(quiz);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "自测题创建失败: " + e.getMessage());
        }
    }

    private void verifyCourseOwnership(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId()))
            throw new BusinessException(ErrorCode.NO_PERMISSION);
    }

    @Override
    public CourseTraining createTraining(Long courseId, CreateTrainingRequest request) {
        verifyCourseOwnership(courseId);
        // 交叉审查: 检查同 course 下 no 不重复
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseTraining> qw =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseTraining>()
                .eq(CourseTraining::getCourseId, courseId)
                .eq(CourseTraining::getNo, request.getNo());
        if (trainingMapper.selectCount(qw) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                "该课程已存在序号为 " + request.getNo() + " 的实训");
        }
        CourseTraining t = new CourseTraining();
        t.setCourseId(courseId);
        t.setNo(request.getNo());
        t.setChapter(request.getChapter());
        t.setTitle(request.getTitle());
        t.setHours(request.getHours());
        t.setSubmissionForm(request.getSubmissionForm());
        t.setCreatedAt(LocalDateTime.now());
        trainingMapper.insert(t);
        log.info("[SectionResource] training created: courseId={}, no={}", courseId, request.getNo());
        return t;
    }

    @Override
    public FinalProjectVO createFinalProject(Long courseId, CreateFinalProjectRequest request) {
        verifyCourseOwnership(courseId);
        // 交叉审查: 检查同 course 下是否已有期末项目
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseFinalProject> qw =
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseFinalProject>()
                .eq(CourseFinalProject::getCourseId, courseId);
        if (finalProjectMapper.selectCount(qw) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课程已存在期末项目，请先删除再创建");
        }
        CourseFinalProject fp = new CourseFinalProject();
        fp.setCourseId(courseId);
        fp.setTitle(request.getTitle());
        try {
            if (request.getPhases() != null && !request.getPhases().isEmpty()) {
                fp.setPhases(objectMapper.writeValueAsString(request.getPhases()));
            } else {
                fp.setPhases("[\"选题\",\"中期\",\"终期\"]");  // 默认值
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "phases 序列化失败: " + e.getMessage());
        }
        fp.setFinalSubmissionForm(request.getFinalSubmissionForm());
        fp.setCreatedAt(LocalDateTime.now());
        finalProjectMapper.insert(fp);
        log.info("[SectionResource] final project created: courseId={}", courseId);
        return toFinalProjectVO(fp);
    }

    private FinalProjectVO toFinalProjectVO(CourseFinalProject fp) {
        FinalProjectVO vo = new FinalProjectVO();
        vo.setId(fp.getId());
        vo.setCourseId(fp.getCourseId());
        vo.setTitle(fp.getTitle());
        vo.setFinalSubmissionForm(fp.getFinalSubmissionForm());
        vo.setCreatedAt(fp.getCreatedAt());
        if (fp.getPhases() != null && !fp.getPhases().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                java.util.List<String> p = objectMapper.readValue(fp.getPhases(), java.util.List.class);
                vo.setPhases(p);
            } catch (Exception e) {
                log.warn("[FinalProjectVO] phases 反序列化失败: {}", e.getMessage());
                vo.setPhases(java.util.Collections.emptyList());
            }
        }
        return vo;
    }

    private QuizVO toQuizVO(SectionQuiz quiz) {
        QuizVO vo = new QuizVO();
        vo.setId(quiz.getId());
        vo.setSectionId(quiz.getSectionId());
        vo.setSlide(quiz.getSlide());
        vo.setPrompt(quiz.getPrompt());
        vo.setCorrectIndex(quiz.getCorrectIndex());
        vo.setExplanation(quiz.getExplanation());
        vo.setCreatedAt(quiz.getCreatedAt());
        if (quiz.getOptions() != null && !quiz.getOptions().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                java.util.List<String> opts = objectMapper.readValue(quiz.getOptions(), java.util.List.class);
                vo.setOptions(opts);
            } catch (Exception e) {
                log.warn("[QuizVO] options 反序列化失败: {}", e.getMessage());
                vo.setOptions(java.util.Collections.emptyList());
            }
        }
        return vo;
    }

    @Override
    public SectionTask createTask(Long courseId, Long sectionId, CreateTaskRequest request) {
        verifyOwnership(courseId, sectionId);
        SectionTask task = new SectionTask();
        task.setSectionId(sectionId);
        task.setSlide(request.getSlide());
        task.setDescription(request.getDescription());
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("[SectionResource] task created: sectionId={}, slide={}", sectionId, request.getSlide());
        return task;
    }

    @Override
    public SectionReflection createReflection(Long courseId, Long sectionId, CreateReflectionRequest request) {
        verifyOwnership(courseId, sectionId);
        SectionReflection ref = new SectionReflection();
        ref.setSectionId(sectionId);
        ref.setTemplate(request.getTemplate());
        ref.setCreatedAt(LocalDateTime.now());
        reflectionMapper.insert(ref);
        log.info("[SectionResource] reflection created: sectionId={}", sectionId);
        return ref;
    }
}
