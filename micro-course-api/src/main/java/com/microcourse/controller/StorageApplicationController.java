package com.microcourse.controller;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.storage.*;
import com.microcourse.service.StorageApplicationService;
import com.microcourse.service.StorageApplicationExportService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
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

    public StorageApplicationController(
            StorageApplicationService storageApplicationService,
            StorageApplicationExportService exportService) {
        this.storageApplicationService = storageApplicationService;
        this.exportService = exportService;
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
    public R<PageResult<StorageApplicationSummaryVO>> getMyDrafts(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(storageApplicationService.getMyDrafts(userId, page, size));
    }

    /**
     * 3. 获取详情
     * GET /api/storage-applications/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<StorageApplicationVO> getDetail(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        // P0-03 修复：添加 owner 校验，与 exportWord()/exportPdf() 方法一致，防止 IDOR
        storageApplicationService.validateOwner(id, userId);
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
     * 注意：autoSave 不使用 @Valid 注解，因为自动保存是部分保存，
     * 允许发送不完整的表单数据。完整校验在 submit() 时执行。
     */
    @PatchMapping("/{id}/auto-save")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> autoSave(
            @PathVariable Long id,
            @Valid @RequestBody StorageApplicationSaveRequest request) {
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
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public ResponseEntity<byte[]> exportWord(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 校验 owner 权限，防止 IDOR（P0 安全修复）
        storageApplicationService.validateOwner(id, userId);
        byte[] bytes = exportService.exportWord(id);
        String schoolName = storageApplicationService.resolveSchoolName(id);
        String filename = "【" + schoolName + "】微专业申报表_"
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
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 校验 owner 权限，防止 IDOR（P0 安全修复）
        storageApplicationService.validateOwner(id, userId);
        byte[] bytes = exportService.exportPdf(id);
        String schoolName = storageApplicationService.resolveSchoolName(id);
        String filename = "【" + schoolName + "】微专业申报表_"
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

    // ================================================================
    // P1C-091: 教务处审批端点
    // ================================================================

    /**
     * 13. 获取待审批列表（ACADEMIC）
     * GET /api/storage-applications/pending
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ACADEMIC','ADMIN')")
    public R<PageResult<StorageApplicationSummaryVO>> getPendingList(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        return R.ok(storageApplicationService.getPendingList(page, size));
    }

    /**
     * 14. 审批通过（ACADEMIC）
     * POST /api/storage-applications/{id}/approve
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ACADEMIC','ADMIN')")
    public R<Void> approve(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        storageApplicationService.approve(id, userId);
        return R.ok();
    }

    /**
     * 15. 审批驳回（ACADEMIC）
     * POST /api/storage-applications/{id}/reject
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ACADEMIC','ADMIN')")
    public R<Void> reject(@PathVariable Long id, @RequestParam String reason) {
        Long userId = SecurityUtil.getCurrentUserId();
        storageApplicationService.reject(id, userId, reason);
        return R.ok();
    }

}
