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
import java.util.Map;

@RestController
@RequestMapping("/api/exercise-records")
public class ExerciseRecordController {

    private final ExerciseRecordService exerciseRecordService;

    public ExerciseRecordController(ExerciseRecordService exerciseRecordService) {
        this.exerciseRecordService = exerciseRecordService;
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<ExerciseRecordVO> submitAnswer(
            @Valid @RequestBody SubmitAnswerRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        request.setUserId(userId);
        ExerciseRecordVO vo = exerciseRecordService.submitAnswer(request);
        return R.ok(vo);
    }

    @GetMapping("/exercise/{exerciseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
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

    @GetMapping("/my/accuracy-trend")
    @PreAuthorize("isAuthenticated()")
    public R<List<Map<String, Object>>> getAccuracyTrend(
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        List<Map<String, Object>> trend = exerciseRecordService.getAccuracyTrend(userId, days);
        return R.ok(trend);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<ExerciseRecordVO> getRecordById(@PathVariable Long id, Authentication authentication) {
        Long userId = extractUserId(authentication);
        ExerciseRecordVO vo = exerciseRecordService.getRecordById(id, userId);
        return R.ok(vo);
    }

    @GetMapping("/my/{exerciseId}/attempt-count")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> getMyAttemptCount(
            @PathVariable Long exerciseId,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        int count = exerciseRecordService.getAttemptCount(userId, exerciseId);
        return R.ok(Map.of("attemptCount", count));
    }

    private Long extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return Long.parseLong(principal.toString());
    }
}