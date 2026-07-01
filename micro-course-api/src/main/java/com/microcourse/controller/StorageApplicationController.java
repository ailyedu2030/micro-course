package com.microcourse.controller;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.dto.R;
import com.microcourse.dto.storage.*;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.repository.MicroSpecialtyProposalRepository;
import com.microcourse.service.StorageApplicationService;
import com.microcourse.service.StorageApplicationExportService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Phase 15: 微专业申请表 Controller。
 * 职责：草稿初始化、全量/自动保存、图片上传、提交审核、
 * 预览构建、Word/PDF 导出、模块/全部重置。
 */
@RestController
@RequestMapping("/api/storage-applications")
public class StorageApplicationController {

    private final StorageApplicationService storageApplicationService;
    private final StorageApplicationExportService exportService;
    private final MicroSpecialtyProposalRepository proposalRepository;

    public StorageApplicationController(
            StorageApplicationService storageApplicationService,
            StorageApplicationExportService exportService,
            MicroSpecialtyProposalRepository proposalRepository) {
        this.storageApplicationService = storageApplicationService;
        this.exportService = exportService;
        this.proposalRepository = proposalRepository;
    }

    /**
     * 1. 初始化空草稿
     * POST /api/storage-applications/init
     */
    @PostMapping("/init")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Long> initDraft() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(storageApplicationService.initDraft(userId));
    }

    /**
     * 2. 我的申请列表
     * GET /api/storage-applications/my-drafts
     */
    @GetMapping("/my-drafts")
    @PreAuthorize("hasRole('TEACHER')")
    public R<List<StorageApplicationSummaryVO>> getMyDrafts() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(storageApplicationService.getMyDrafts(userId));
    }

    /**
     * 3. 获取详情
     * GET /api/storage-applications/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<StorageApplicationVO> getDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(storageApplicationService.getDetail(id, userId));
    }

    /**
     * 4. 全量保存
     * PUT /api/storage-applications/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public R<StorageApplicationVO> save(
            @PathVariable Long id,
            @Valid @RequestBody StorageApplicationSaveRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(storageApplicationService.save(id, userId, request));
    }

    /**
     * 5. 自动保存
     * PATCH /api/storage-applications/{id}/auto-save
     *
     * 注意：autoSave 不添加 @Valid 注解，因为自动保存是部分保存，
     * 允许发送不完整的表单数据。完整校验在 submit() 时执行。
     */
    @PatchMapping("/{id}/auto-save")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> autoSave(
            @PathVariable Long id,
            @RequestBody StorageApplicationSaveRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        storageApplicationService.autoSave(id, userId, request);
        return R.ok();
    }

    /**
     * 6. 上传签名/公章图片
     * POST /api/storage-applications/{id}/upload-image
     */
    @PostMapping("/{id}/upload-image")
    @PreAuthorize("hasRole('TEACHER')")
    public R<UploadResultVO> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(storageApplicationService.uploadImage(id, userId, file, type));
    }

    /**
     * 7. 预览数据
     * GET /api/storage-applications/{id}/preview
     */
    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<StorageApplicationPreviewVO> preview(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(storageApplicationService.buildPreview(id, userId));
    }

    /**
     * 8. 下载 Word
     * GET /api/storage-applications/{id}/export-word
     */
    @GetMapping("/{id}/export-word")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")  // P1-C-3 修复：增加 ACADEMIC 权限
    public ResponseEntity<byte[]> exportWord(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        // P1-C-2 修复：统一使用 BusinessException 抛出，而非 ResponseEntity.badRequest()
        ExportValidationResult validation = storageApplicationService.validateForExport(id, userId);
        if (!validation.isValid()) {
            throw new BusinessException(ErrorCode.SA_FORM_INCOMPLETE,
                    "导出校验失败：" + String.join("；", validation.getErrors()));
        }

        byte[] bytes = exportService.exportWord(id);
        String filename = "【" + resolveSchoolName(id) + "】整理收纳微专业申请表_"
                + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".docx";

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    /**
     * 9. 下载 PDF
     * GET /api/storage-applications/{id}/export-pdf
     */
    @GetMapping("/{id}/export-pdf")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")  // P1-C-3 修复：增加 ACADEMIC 权限
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        // P1-C-2 修复：统一使用 BusinessException 抛出
        ExportValidationResult validation = storageApplicationService.validateForExport(id, userId);
        if (!validation.isValid()) {
            throw new BusinessException(ErrorCode.SA_FORM_INCOMPLETE,
                    "导出校验失败：" + String.join("；", validation.getErrors()));
        }

        byte[] bytes = exportService.exportPdf(id);
        String filename = "【" + resolveSchoolName(id) + "】整理收纳微专业申请表_"
                + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    /**
     * 10. 提交审核
     * POST /api/storage-applications/{id}/submit
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> submit(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        storageApplicationService.submit(id, userId);
        return R.ok();
    }

    /**
     * 11. 重置模块
     * POST /api/storage-applications/{id}/reset-module
     */
    @PostMapping("/{id}/reset-module")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> resetModule(@PathVariable Long id, @RequestParam String module) {
        Long userId = SecurityUtil.getCurrentUserId();
        storageApplicationService.resetModule(id, userId, module);
        return R.ok();
    }

    /**
     * 12. 重置全部
     * POST /api/storage-applications/{id}/reset-all
     */
    @PostMapping("/{id}/reset-all")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> resetAll(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        storageApplicationService.resetAll(id, userId);
        return R.ok();
    }

    /**
     * P2-3 fix (R-008): 使用轻量级查询替代 getDetail() 以避免导出时重复查询。
     * getDetail() 会联查所有子表 + 用户名/院系名，而导出文件名仅需 title 字段。
     */
    private String resolveSchoolName(Long proposalId) {
        try {
            MicroSpecialtyProposal p = proposalRepository.selectById(proposalId);
            return p != null && p.getTitle() != null ? p.getTitle() : "申报高校";
        } catch (Exception e) {
            return "申报高校";
        }
    }
}
