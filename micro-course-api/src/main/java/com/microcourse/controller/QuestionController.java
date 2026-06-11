package com.microcourse.controller;

import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.dto.R;
import com.microcourse.dto.PageResult;
import com.microcourse.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<R<PageResult<QuestionVO>>> page(
            @RequestParam(required = false) Integer courseId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<QuestionVO> result = questionService.page(courseId, questionType, difficulty, page, size);
        return ResponseEntity.ok(R.ok(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<R<QuestionVO>> getById(@PathVariable Long id) {
        QuestionVO vo = questionService.getById(id);
        return ResponseEntity.ok(R.ok(vo));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<R<QuestionVO>> create(@Valid @RequestBody QuestionCreateRequest request) {
        QuestionVO vo = questionService.create(request);
        return ResponseEntity.ok(R.ok(vo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<R<QuestionVO>> update(@PathVariable Long id,
                                                  @Valid @RequestBody QuestionUpdateRequest request) {
        QuestionVO vo = questionService.update(id, request);
        return ResponseEntity.ok(R.ok(vo));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<R<Void>> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ResponseEntity.ok(R.ok());
    }
}