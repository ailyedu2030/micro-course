package com.microcourse.controller;

import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.R;
import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.service.ExerciseRecordService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exercise-records")
public class ExerciseRecordController {

    private final ExerciseRecordService exerciseRecordService;

    public ExerciseRecordController(ExerciseRecordService exerciseRecordService) {
        this.exerciseRecordService = exerciseRecordService;
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<ExerciseRecordVO> submitAnswer(@Valid @RequestBody SubmitAnswerRequest request) {
        ExerciseRecordVO vo = exerciseRecordService.submitAnswer(request);
        return R.ok(vo);
    }

    @GetMapping("/exercise/{exerciseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<ExerciseRecordVO>> getRecordsByExercise(@PathVariable Long exerciseId) {
        List<ExerciseRecordVO> records = exerciseRecordService.getRecordsByExercise(exerciseId);
        return R.ok(records);
    }

    @GetMapping("/my/{exerciseId}")
    @PreAuthorize("isAuthenticated()")
    public R<List<ExerciseRecordVO>> getMyRecords(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        List<ExerciseRecordVO> records = exerciseRecordService.getMyRecords(userId, exerciseId);
        return R.ok(records);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<ExerciseRecordVO> getRecordById(@PathVariable Long id, Authentication authentication) {
        Long userId = extractUserId(authentication);
        ExerciseRecordVO vo = exerciseRecordService.getRecordById(id, userId);
        return R.ok(vo);
    }

    private Long extractUserId(Authentication authentication) {
        // 从认证信息中提取用户ID，实际实现依赖于安全配置
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        // 如果是字符串或其他的用户标识，需要转换
        return Long.parseLong(principal.toString());
    }
}