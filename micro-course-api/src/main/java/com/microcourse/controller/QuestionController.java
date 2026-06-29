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
            @RequestParam(required = false) @Pattern(regexp = "^(SINGLE|MULTIPLE|JUDGE|FILL|SHORT_ANSWER|ESSAY)?$", message = "题目类型无效") String questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        PageResult<QuestionVO> result = questionService.page(courseId, questionType, difficulty, keyword, categoryId, page, size);
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
        // SEC-009 修复: 文件名路径穿越校验
        com.microcourse.util.FileUploadUtil.assertSafeFilename(file.getOriginalFilename());
        // P1 安全修复: 魔数校验（防御深度，防止 Content-Type 伪造）
        verifyExcelMagic(file);
        String contentType = file.getContentType();
        if (contentType == null || (
                !contentType.startsWith("application/vnd.ms-excel") &&
                !contentType.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 Excel 文件 (.xls/.xlsx)");
        }
        BatchImportResultVO result = questionService.batchImport(file, courseId);
        return R.ok(result);
    }

    /**
     * P1 安全修复: Excel 文件魔数校验（防御深度，防止 Content-Type 伪造）。
     * XLS: D0 CF 11 E0（OLE2 复合文档）
     * XLSX: PK\x03\x04（ZIP 压缩包）
     * 使用独立 InputStream 读取，不消耗后续 service 层可用的流。
     */
    private void verifyExcelMagic(MultipartFile file) {
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] magic = new byte[4];
            int read = is.read(magic);
            if (read < 4) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件过小，无法验证格式");
            }
            boolean isOle2 = (magic[0] & 0xFF) == 0xD0 && (magic[1] & 0xFF) == 0xCF
                    && (magic[2] & 0xFF) == 0x11 && (magic[3] & 0xFF) == 0xE0;
            boolean isZip = (magic[0] & 0xFF) == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04;
            if (!isOle2 && !isZip) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请上传有效的 Excel 文件（魔数校验失败）");
            }
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取上传文件");
        }
    }
}