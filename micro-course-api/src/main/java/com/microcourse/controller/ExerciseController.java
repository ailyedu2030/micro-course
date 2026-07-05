package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.ExerciseCreateRequest;
import com.microcourse.dto.AddQuestionsRequest;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.ExerciseUpdateRequest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.R;
import com.microcourse.dto.PageResult;
import com.microcourse.service.ExerciseRecordService;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final ExerciseRecordService exerciseRecordService;

    public ExerciseController(ExerciseService exerciseService,
                              ExerciseRecordService exerciseRecordService) {
        this.exerciseService = exerciseService;
        this.exerciseRecordService = exerciseRecordService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<ExerciseVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(required = false) Boolean isExam,
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 10000) Integer size) {
        PageResult<ExerciseVO> result = exerciseService.page(courseId, chapterId, isExam, page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<ExerciseVO> getById(@PathVariable Long id) {
        ExerciseVO vo = exerciseService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建练习")
    public R<ExerciseVO> create(@Valid @RequestBody ExerciseCreateRequest request) {
        ExerciseVO vo = exerciseService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新练习")
    public R<ExerciseVO> update(@PathVariable Long id,
                                @Valid @RequestBody ExerciseUpdateRequest request) {
        ExerciseVO vo = exerciseService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除练习")
    public R<Void> delete(@PathVariable Long id) {
        exerciseService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("添加练习题目")
    public R<Void> addQuestions(@PathVariable Long id, @Valid @RequestBody AddQuestionsRequest request) {
        exerciseService.addQuestions(id, request.getQuestionIds());
        return R.ok();
    }

    @DeleteMapping("/{exerciseId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("移除练习题目")
    public R<Void> removeQuestion(@PathVariable Long exerciseId, @PathVariable Long questionId) {
        exerciseService.removeQuestion(exerciseId, questionId);
        return R.ok();
    }

    /**
     * GET /api/exercises/{id}/result
     * 获取练习结果（角色感知权限校验已下沉 Service 层）
     * - STUDENT（非 ADMIN）：仅返回本人答题记录
     * - TEACHER / ADMIN：返回该练习全部答题记录
     */
    @GetMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public R<List<ExerciseRecordVO>> getResult(@PathVariable Long id) {
        return R.ok(exerciseRecordService.getResult(id, SecurityUtil.getCurrentUserId()));
    }

    /**
     * GET /api/exercises/{id}/attempts
     * 获取当前学生在该练习的答题记录列表（Phase A-4 P0-5 新增）
     * 权限：STUDENT(本人) —— 依据 权限矩阵 v2.0 §2.7（本人答题记录）。
     * userId 强制取当前登录用户，天然防 IDOR。
     */
    @GetMapping("/{id}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public R<List<ExerciseRecordVO>> getAttempts(@PathVariable Long id) {
        return R.ok(exerciseRecordService.getMyRecords(SecurityUtil.getCurrentUserId(), id));
    }

    /**
     * GET /api/exercises/{id}/analytics
     * 获取练习统计分析（统计计算已下沉 Service 层）
     */
    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Map<String, Object>> getAnalytics(@PathVariable Long id) {
        return R.ok(exerciseRecordService.getAnalytics(id));
    }

    /**
     * POST /api/exercises/{id}/retry
     * 重做练习（校验逻辑已下沉 ExerciseService.retryExercise）
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('STUDENT')")
    @AuditedLog("重做练习")
    public R<Map<String, Object>> retry(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(exerciseService.retryExercise(id, userId));
    }
}