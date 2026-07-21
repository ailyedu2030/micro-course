package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.AddQuestionsRequest;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.ExerciseRecordService;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/exercises")
@Tag(name = "课程练习管理", description = "练习管理课程子资源路径（与/api/exercises平行，保留向后兼容）")
public class CourseExerciseController {

    private final ExerciseService exerciseService;
    private final ExerciseRecordService exerciseRecordService;

    public CourseExerciseController(ExerciseService exerciseService,
                                  ExerciseRecordService exerciseRecordService) {
        this.exerciseService = exerciseService;
        this.exerciseRecordService = exerciseRecordService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程下练习列表")
    public R<PageResult<ExerciseVO>> listByCourse(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(required = false) Boolean isExam,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 10000) int size) {
        PageResult<ExerciseVO> result = exerciseService.page(courseId, chapterId, isExam, page, size);
        return R.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建练习")
    @Operation(summary = "在课程下创建练习")
    public R<ExerciseVO> create(@PathVariable Long courseId,
                                 @Valid @RequestBody com.microcourse.dto.ExerciseCreateRequest request) {
        if (request.getCourseId() == null) {
            request.setCourseId(courseId);
        }
        ExerciseVO vo = exerciseService.create(request);
        return R.ok(vo);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取练习详情")
    public R<ExerciseVO> getById(@PathVariable Long courseId, @PathVariable Long id) {
        ExerciseVO vo = exerciseService.getById(id);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新练习")
    @Operation(summary = "更新练习")
    public R<ExerciseVO> update(@PathVariable Long courseId,
                                @PathVariable Long id,
                                @Valid @RequestBody com.microcourse.dto.ExerciseUpdateRequest request) {
        ExerciseVO vo = exerciseService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除练习")
    @Operation(summary = "删除练习")
    public R<Void> delete(@PathVariable Long courseId, @PathVariable Long id) {
        exerciseService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("添加练习题目")
    @Operation(summary = "向练习添加题目")
    public R<Void> addQuestions(@PathVariable Long courseId,
                                @PathVariable Long id,
                                @Valid @RequestBody AddQuestionsRequest request) {
        exerciseService.addQuestions(id, request.getQuestionIds());
        return R.ok();
    }

    @DeleteMapping("/{exerciseId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("移除练习题目")
    @Operation(summary = "从练习移除题目")
    public R<Void> removeQuestion(@PathVariable Long courseId,
                                 @PathVariable Long exerciseId,
                                 @PathVariable Long questionId) {
        exerciseService.removeQuestion(exerciseId, questionId);
        return R.ok();
    }

    @GetMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    @Operation(summary = "获取练习结果")
    public R<List<ExerciseRecordVO>> getResult(@PathVariable Long courseId, @PathVariable Long id) {
        return R.ok(exerciseRecordService.getResult(id, SecurityUtil.getCurrentUserId()));
    }

    @GetMapping("/{id}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "获取学生答题记录")
    public R<List<ExerciseRecordVO>> getAttempts(@PathVariable Long courseId, @PathVariable Long id) {
        return R.ok(exerciseRecordService.getMyRecords(SecurityUtil.getCurrentUserId(), id));
    }
}
