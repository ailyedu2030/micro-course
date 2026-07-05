package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.ExerciseCreateRequest;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.ExerciseUpdateRequest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.R;
import com.microcourse.dto.PageResult;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.ExerciseRecordService;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    public R<Void> addQuestions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> questionIds = body.get("questionIds");
        if (questionIds == null || questionIds.isEmpty()) {
            throw new com.microcourse.exception.BusinessException(
                com.microcourse.exception.ErrorCode.BAD_REQUEST_PARAM, "questionIds 不能为空");
        }
        exerciseService.addQuestions(id, questionIds);
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
     * 获取练习结果（Phase A-4 P0-5 新增）
     * 权限：STUDENT(本人) / TEACHER(课程创建者) / ADMIN —— 依据 权限矩阵 v2.0 §2.7 READ_EXERCISE_RESULT。
     * - STUDENT（非 ADMIN）：仅返回本人答题记录（userId 强制取当前用户，防 IDOR）
     * - TEACHER / ADMIN：返回该练习全部答题记录
     *   注：TEACHER「课程创建者」owner 校验依赖 PermissionEvaluator（Phase C），此处与既有
     *   /api/exercise-records/exercise/{exerciseId} 行为保持一致，不引入回归。
     */
    @GetMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public R<List<ExerciseRecordVO>> getResult(@PathVariable Long id) {
        if (SecurityUtil.hasRole("STUDENT") && !SecurityUtil.isAdmin()) {
            return R.ok(exerciseRecordService.getMyRecords(SecurityUtil.getCurrentUserId(), id));
        }
        return R.ok(exerciseRecordService.getRecordsByExercise(id));
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
     * 获取练习统计分析（提交次数 / 参与人数 / 通过率 / 平均分）（Round 5-3 P1-10 新增）
     * 权限：TEACHER / ADMIN（依据 权限矩阵 v2.0 §2.7 READ_EXERCISE_ANALYTICS）
     *
     * <p>聚合既有 {@code exercise_records}（经 ExerciseRecordService.getRecordsByExercise），
     * 无新表/列。无答题记录时返回零值统计（仍 200），端点真正可用。</p>
     */
    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Map<String, Object>> getAnalytics(@PathVariable Long id) {
        List<ExerciseRecordVO> records = exerciseRecordService.getRecordsByExercise(id);
        int totalAttempts = records.size();
        long passedCount = records.stream()
                .filter(r -> Boolean.TRUE.equals(r.getPassed()))
                .count();
        long participantCount = records.stream()
                .map(ExerciseRecordVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        double avgScore = records.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(ExerciseRecordVO::getScore)
                .average()
                .orElse(0.0);

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("exerciseId", id);
        analytics.put("totalAttempts", totalAttempts);
        analytics.put("participantCount", participantCount);
        analytics.put("passedCount", passedCount);
        analytics.put("passRate", totalAttempts > 0 ? (double) passedCount / totalAttempts : 0.0);
        analytics.put("avgScore", avgScore);
        return R.ok(analytics);
    }

    /**
     * POST /api/exercises/{id}/retry
     * 重做练习（校验剩余答题次数并返回下一次答题元信息）（Round 5-3 P1-10 新增）
     * 权限：STUDENT（依据 权限矩阵 v2.0 §2.7 RETRY_EXERCISE = 仅 STUDENT）
     *
     * <p>语义：基于 {@code maxAttempts} 与当前已用次数判定是否还可重做。
     * - maxAttempts 为空或 ≤0 视为不限次数（remainingAttempts = -1）。
     * - 已无剩余次数 → 400（BAD_REQUEST_PARAM），不会 5xx。
     * 本端点不写入答题记录（提交仍走既有 /api/exercise-records/submit），仅返回 nextAttemptNo
     * 等元信息供前端开启新一轮答题，行为对既有提交链路零侵入。</p>
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('STUDENT')")
    @AuditedLog("重做练习")
    public R<Map<String, Object>> retry(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        ExerciseVO exercise = exerciseService.getById(id);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }
        int used = exerciseRecordService.getAttemptCount(userId, id);
        Integer max = exercise.getMaxAttempts();
        boolean unlimited = (max == null || max <= 0);
        int remaining = unlimited ? -1 : Math.max(0, max - used);
        if (!unlimited && remaining <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "已无剩余答题次数");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("exerciseId", id);
        result.put("attemptsUsed", used);
        result.put("maxAttempts", max);
        result.put("remainingAttempts", remaining);
        result.put("nextAttemptNo", used + 1);
        result.put("canRetry", true);
        return R.ok(result);
    }
}