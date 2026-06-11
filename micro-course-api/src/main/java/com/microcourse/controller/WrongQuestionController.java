package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.WrongQuestionVO;
import com.microcourse.service.WrongQuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<R<List<WrongQuestionVO>>> getMyWrongQuestions(
            @RequestParam(required = false) Long courseId,
            Authentication authentication) {
        Long userId = extractUserId(authentication);

        List<WrongQuestionVO> records;
        if (courseId != null) {
            records = wrongQuestionService.getMyWrongQuestionsByCourse(userId, courseId);
        } else {
            records = wrongQuestionService.getMyWrongQuestions(userId);
        }
        return ResponseEntity.ok(R.ok(records));
    }

    private Long extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return Long.parseLong(principal.toString());
    }
}