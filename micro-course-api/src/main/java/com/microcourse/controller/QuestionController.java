package com.microcourse.controller;

import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.dto.R;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.service.QuestionService;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

@GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<QuestionVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<QuestionVO> result = questionService.page(courseId, questionType, difficulty, page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<QuestionVO> getById(@PathVariable Long id) {
        QuestionVO vo = questionService.getById(id);
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<QuestionVO> create(@Valid @RequestBody QuestionCreateRequest request) {
        QuestionVO vo = questionService.create(request);
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<QuestionVO> update(@PathVariable Long id,
                                @Valid @RequestBody QuestionUpdateRequest request) {
        QuestionVO vo = questionService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return R.ok();
    }

    /**
     * POST /api/questions/batch/import
     * 批量导入题目（Excel）
     * @param file Excel 文件
     * @param courseId 课程ID（路径参数）
     */
    @PostMapping("/batch/import")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<BatchImportResultVO> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || (
                !contentType.startsWith("application/vnd.ms-excel") &&
                !contentType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 Excel 文件 (.xls/.xlsx)");
        }
        BatchImportResultVO result = questionService.batchImport(file, courseId);
        return R.ok(result);
    }
}