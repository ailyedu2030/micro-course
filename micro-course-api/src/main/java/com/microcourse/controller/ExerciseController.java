package com.microcourse.controller;

import com.microcourse.dto.ExerciseCreateRequest;
import com.microcourse.dto.ExerciseUpdateRequest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.R;
import com.microcourse.dto.PageResult;
import com.microcourse.service.ExerciseService;
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

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<ExerciseVO>> page(
            @RequestParam(required = false) Integer courseId,
            @RequestParam(required = false) Integer chapterId,
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer page,
            @RequestParam(defaultValue = "10") @Range(min = 1, max = 10000) Integer size) {
        PageResult<ExerciseVO> result = exerciseService.page(courseId, chapterId, page, size);
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
    public R<ExerciseVO> create(@Valid @RequestBody ExerciseCreateRequest request) {
        ExerciseVO vo = exerciseService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<ExerciseVO> update(@PathVariable Long id,
                                @Valid @RequestBody ExerciseUpdateRequest request) {
        ExerciseVO vo = exerciseService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        exerciseService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
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
    public R<Void> removeQuestion(@PathVariable Long exerciseId, @PathVariable Long questionId) {
        exerciseService.removeQuestion(exerciseId, questionId);
        return R.ok();
    }
}