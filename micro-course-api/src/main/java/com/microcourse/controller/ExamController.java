package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生考试中心控制器 (J3-01 修复)
 * 基于 exercises 表，过滤 is_exam=true 且已选课的考试列表
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
     * 获取当前学生的考试列表（只返回已选课程中 is_exam=true 的练习）
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public R<List<ExerciseVO>> myExams() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ExerciseVO> exams = exerciseService.getMyExams(userId);
        return R.ok(exams);
    }
}
