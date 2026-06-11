package com.microcourse.controller;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;
import com.microcourse.dto.R;
import com.microcourse.service.LearningProgressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning-progress")
public class LearningProgressController {

    private final LearningProgressService learningProgressService;

    public LearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @GetMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<R<List<LearningProgressVO>>> getByUserAndCourse(
            @RequestParam Long userId,
            @RequestParam Long courseId) {
        List<LearningProgressVO> list = learningProgressService.getByUserAndCourse(userId, courseId);
        return ResponseEntity.ok(R.ok(list));
    }

    @PostMapping("/progress")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<R<LearningProgressVO>> create(@Valid @RequestBody ProgressCreateRequest request) {
        LearningProgressVO vo = learningProgressService.create(request);
        return ResponseEntity.ok(R.ok(vo));
    }

    @PutMapping("/progress/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<R<Void>> updateProgress(@PathVariable Long id,
                                                  @RequestBody ProgressUpdateRequest request) {
        learningProgressService.updateProgress(id, request);
        return ResponseEntity.ok(R.ok());
    }

    @GetMapping("/progress/completion")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<R<Map<String, Object>>> getCourseCompletion(
            @RequestParam Long userId,
            @RequestParam Long courseId) {
        Double completion = learningProgressService.getCourseCompletion(userId, courseId);
        Map<String, Object> result = Map.of("completion", completion);
        return ResponseEntity.ok(R.ok(result));
    }
}