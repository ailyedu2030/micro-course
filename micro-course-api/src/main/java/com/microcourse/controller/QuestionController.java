package com.microcourse.controller;

import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.dto.R;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.service.QuestionService;

import jakarta.servlet.http.HttpServletResponse;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/questions")
@Validated
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

@GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','ACADEMIC')")
    public R<PageResult<QuestionVO>> page(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) @Pattern(regexp = "^(SINGLE|MULTIPLE|JUDGE|FILL|SHORT_ANSWER|ESSAY|SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|FILL_BLANK|COMPREHENSIVE)?$", message = "题目类型无效") String questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100000) int size) {
        PageResult<QuestionVO> result = questionService.page(courseId, questionType, difficulty, keyword, categoryId, chapterId, page, size);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','ACADEMIC')")
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
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<QuestionVO> update(@PathVariable Long id,
                                @Valid @RequestBody QuestionUpdateRequest request) {
        QuestionVO vo = questionService.update(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return R.ok();
    }

    /**
     * POST /api/questions/batch/import
     * 批量导入题目（Excel）
     * <p>P1I-038 修复：文件格式/大小/魔数校验统一由 Service 层处理，Controller 不再重复校验。</p>
     * @param file Excel 文件
     * @param courseId 课程ID（路径参数）
     */
    @PostMapping("/batch/import")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<BatchImportResultVO> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long courseId) {
        // P1I-038: 文件校验（为空、大小、文件名安全）统一由 Service 层处理
        // Controller 仅做最基本的前置检查，防止无效请求进入 Service
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        // SEC-009 修复: 文件名路径穿越校验
        com.microcourse.util.FileUploadUtil.assertSafeFilename(file.getOriginalFilename());
        BatchImportResultVO result = questionService.batchImport(file, courseId);
        return R.ok(result);
    }

    /**
     * GET /api/questions/export
     * 导出题目为 Excel
     * @param courseId 课程ID（可选）
     * @param questionType 题目类型筛选（可选）
     * @param difficulty 难度筛选（可选）
     * @param keyword 关键字筛选（可选）
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public void export(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String keyword,
            HttpServletResponse response) {
        questionService.export(courseId, questionType, difficulty, keyword, response);
    }
}