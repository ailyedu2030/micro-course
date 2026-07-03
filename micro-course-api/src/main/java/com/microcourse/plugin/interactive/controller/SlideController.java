package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses/{courseId}/slides")
public class SlideController {

    private static final Logger log = LoggerFactory.getLogger(SlideController.class);

    private final SlideService slideService;
    private final CourseRepository courseRepository;
    private final com.microcourse.repository.EnrollmentRepository enrollmentRepository;

    public SlideController(SlideService slideService,
                           CourseRepository courseRepository,
                           com.microcourse.repository.EnrollmentRepository enrollmentRepository) {
        this.slideService = slideService;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlideUploadResponse> upload(@PathVariable Long courseId,
                                           @RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                log.warn("[SlideUpload] 上传文件为空 courseId={}", courseId);
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
            }
            String filename = file.getOriginalFilename();
            long size = file.getSize();
            String mime = file.getContentType();
            log.info("[SlideUpload] courseId={}, filename={}, size={}, mime={}",
                    courseId, sanitizeForLog(filename), size, mime);
            // P0-14: 应用层校验 50MB 早拒绝（Spring multipart 限制 60MB，中间 gap 由此处拦截）
            if (file.getSize() > 50 * 1024 * 1024) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课件文件不能超过 50MB");
            }
            // P1 安全修复: 文件魔数校验（PPTX=ZIP PK 0x03 0x04, 图片=JPEG/PNG）
            validateSlideFileMagic(file);
            SlideUploadResponse resp = slideService.upload(courseId, filename, file.getBytes());
            return R.ok(resp);
        } catch (IOException e) {
            log.error("[SlideUpload] 文件读取IO异常 courseId={}", courseId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件读取失败");
        } catch (BusinessException e) {
            log.warn("[SlideUpload] 业务拒绝 courseId={}, code={}, msg={}", courseId, e.getCode(), e.getMessage());
            throw e;
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<SlideVO> getByCourseId(@PathVariable Long courseId) {
        verifyAccess(courseId);
        return R.ok(slideService.getByCourseId(courseId));
    }

    @GetMapping("/pages")
    @PreAuthorize("isAuthenticated()")
    public R<List<SlidePageVO>> getPages(@PathVariable Long courseId) {
        verifyAccess(courseId);
        return R.ok(slideService.getPages(courseId));
    }

    @GetMapping("/pages/{pageNumber}")
    @PreAuthorize("isAuthenticated()")
    public R<SlidePageVO> getPage(@PathVariable Long courseId,
                                   @PathVariable Integer pageNumber) {
        verifyAccess(courseId);
        return R.ok(slideService.getPage(courseId, pageNumber));
    }

    @GetMapping("/pages/{pageNumber}/image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getPageImage(@PathVariable Long courseId,
                                                @PathVariable Integer pageNumber) {
        verifyAccess(courseId);
        byte[] imageBytes = slideService.getPageImage(courseId, pageNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(imageBytes);
    }

    @GetMapping("/pages/{pageNumber}/thumbnail")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getPageThumbnail(@PathVariable Long courseId,
                                                    @PathVariable Integer pageNumber) {
        verifyAccess(courseId);
        byte[] thumbBytes = slideService.getPageThumbnail(courseId, pageNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(thumbBytes);
    }

    /**
     * IDOR 防护：验证当前用户有权限访问此课程的课件。
     * 身份叠加原则（权限矩阵 §4.1）：同时具备多个角色的用户，权限集合取并集。
     * - ADMIN: 全部通行
     * - ACADEMIC: 全部通行
     * - TEACHER: 必须是课程的所有者
     * - STUDENT: 必须已选此课（有非 CANCELLED 的 enrollment 记录）
     */

    @DeleteMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deleteSlide(@PathVariable Long courseId) {
        slideService.deleteSlide(courseId);
        return R.ok();
    }

    @DeleteMapping("/pages/{pageNumber}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deletePage(@PathVariable Long courseId,
                               @PathVariable Integer pageNumber) {
        slideService.deletePage(courseId, pageNumber);
        return R.ok();
    }

    @PutMapping("/pages/reorder")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> reorderPages(@PathVariable Long courseId,
                                 @RequestBody List<Map<String, Integer>> order) {
        slideService.reorderPages(courseId, order);
        return R.ok();
    }

    @PutMapping("/pages/{pageNumber}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> updatePage(@PathVariable Long courseId,
                                      @PathVariable Integer pageNumber,
                                      @RequestBody Map<String, Object> body) {
        return R.ok(slideService.updatePage(courseId, pageNumber, body));
    }

    @GetMapping("/download")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<byte[]> downloadOriginal(@PathVariable Long courseId) {
        com.microcourse.plugin.interactive.dto.SlideVO slide = slideService.getByCourseId(courseId);
        byte[] fileBytes = slideService.getOriginalFile(courseId);
        String filename = slide.getFileName() != null ? slide.getFileName() : "slide.pptx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .body(fileBytes);
    }

    private void verifyAccess(Long courseId) {
        if (SecurityUtil.isAdmin()) {
            return;
        }
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (SecurityUtil.hasRole("ACADEMIC")) {
            return;
        }

        boolean allowed = false;

        if (SecurityUtil.hasRole("TEACHER")
                && SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            allowed = true;
        }

        if (!allowed && SecurityUtil.hasRole("STUDENT")) {
            Long currentUserId = SecurityUtil.getCurrentUserId();
            long count = enrollmentRepository.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.Enrollment>()
                            .eq(com.microcourse.entity.Enrollment::getUserId, currentUserId)
                            .eq(com.microcourse.entity.Enrollment::getCourseId, courseId)
                            .ne(com.microcourse.entity.Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));
            if (count > 0) {
                allowed = true;
            }
        }

        if (!allowed) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    /**
     * 清理用户输入用于日志：替换 \r \n 为下划线，防止日志注入。
     */
    private String sanitizeForLog(String input) {
        if (input == null) return "";
        return input.replace('\r', '_').replace('\n', '_');
    }

    /**
     * P1 安全修复: 课件文件魔数校验。
     * PPTX 文件是 ZIP 格式，魔数为 PK\x03\x04；
     * 图片文件魔数为 JPEG(FFD8FF) / PNG(89504E47)。
     */
    private void validateSlideFileMagic(MultipartFile file) throws IOException {
        byte[] magic = new byte[8];
        try (java.io.InputStream is = file.getInputStream()) {
            int read = is.read(magic);
            if (read < 4) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课件文件过小，无法验证格式");
            }
        }
        // ZIP/PPTX: PK\x03\x04
        boolean isZip = magic[0] == 'P' && magic[1] == 'K'
                && magic[2] == 0x03 && magic[3] == 0x04;
        // JPEG: FFD8FF
        boolean isJpeg = (magic[0] & 0xFF) == 0xFF
                && (magic[1] & 0xFF) == 0xD8
                && (magic[2] & 0xFF) == 0xFF;
        // PNG: 89504E47
        boolean isPng = (magic[0] & 0xFF) == 0x89
                && magic[1] == 'P'
                && magic[2] == 'N'
                && magic[3] == 'G';
        if (!isZip && !isJpeg && !isPng) {
            throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID,
                    "不支持的课件格式（仅支持 PPTX/PDF，或 JPEG/PNG 图片）");
        }
    }
}
