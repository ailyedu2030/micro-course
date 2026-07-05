package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.*;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考试控制器
 * 学生端：GET /api/exams/my 查看考试列表
 * 教师端：POST /api/exams/generate 智能组卷
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExerciseService exerciseService;

    public ExamController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    /**
     * GET /api/exams/my
     * 学生端：当前学生的考试列表
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public R<List<ExerciseVO>> myExams() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ExerciseVO> exams = exerciseService.getMyExams(userId);
        return R.ok(exams);
    }

    /**
     * POST /api/exams/generate
     * 教师端：智能组卷。从题库按条件随机抽题，创建考试。
     *
     * 请求体：
     * {
     *   "title": "期中考试",
     *   "courseId": 1,
     *   "chapterIds": [1, 2, 3],
     *   "questionCounts": { "SINGLE": 5, "MULTIPLE": 3, "JUDGE": 2 },
     *   "totalScore": 100,
     *   "timeLimit": 60
     * }
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("智能组卷")
    public R<ExerciseVO> generate(@Valid @RequestBody ExamGenerateRequest req) {
        ExerciseVO exam = exerciseService.generateExam(req);
        return R.ok(exam);
    }
}
