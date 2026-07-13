package com.microcourse.plugin.interactive.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.R;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class SlideController {

    private static final Logger log = LoggerFactory.getLogger(SlideController.class);

    private final SlideService slideService;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    /**
     * HTML 课件白名单：V177 灰度发布。
     * 配置为空时所有 TEACHER 可上传 HTML；非空时仅白名单内 TEACHER 可上传。
     * 格式：逗号分隔的 userId 列表，例如 "2,3,5"（ADMIN 不受限制）。
     */
    @org.springframework.beans.factory.annotation.Value("${plugin.interactive.html-content.whitelist-teachers:}")
    private java.util.List<Long> htmlWhitelist;

    public SlideController(SlideService slideService,
                           CourseRepository courseRepository,
                           EnrollmentRepository enrollmentRepository) {
        this.slideService = slideService;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlideUploadResponse> upload(@PathVariable Long courseId,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) Long chapterId) {
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
            // 应用层校验 50MB 早拒绝（Spring multipart 限制 60MB，中间 gap 由此处拦截）
            if (file.getSize() > 50 * 1024 * 1024) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课件文件不能超过 50MB");
            }
            // HTML 课件分支：跳过 PPTX 魔数校验（修复 Hermes P0-5 错配：/upload 应接受 HTML）
            String lowerFilename = filename != null ? filename.toLowerCase() : "";
            boolean isHtmlFile = lowerFilename.endsWith(".html") || lowerFilename.endsWith(".htm");
            if (isHtmlFile) {
                // V177 灰度白名单：仅白名单 TEACHER 可上传 HTML，ADMIN 始终允许
                if (!com.microcourse.util.SecurityUtil.isAdmin()
                        && !htmlWhitelist.isEmpty()
                        && !htmlWhitelist.contains(com.microcourse.util.SecurityUtil.getCurrentUserId())) {
                    log.warn("[SlideUpload] 非白名单用户尝试上传 HTML courseId={}, userId={}, whitelist={}",
                            courseId, com.microcourse.util.SecurityUtil.getCurrentUserId(), htmlWhitelist);
                    throw new BusinessException(ErrorCode.NO_PERMISSION,
                            "HTML 课件上传功能灰度中，仅白名单教师可使用");
                }
                SlideUploadResponse resp = slideService.uploadHtmlFile(courseId, file, chapterId);
                return R.ok(resp);
            }
            // PPTX 文件魔数校验（ZIP PK 0x03 0x04）
            validateSlideFileMagic(file);
            SlideUploadResponse resp = slideService.upload(courseId, filename, file.getBytes(), chapterId);
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

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public R<List<SlideVO>> listSlides(@PathVariable Long courseId) {
        verifyAccess(courseId);
        return R.ok(slideService.listByCourseId(courseId));
    }

    @GetMapping("/pages")
    @PreAuthorize("isAuthenticated()")
    public R<List<SlidePageVO>> getPages(@PathVariable Long courseId,
                                        @RequestParam(required = false) Long chapterId) {
        verifyAccess(courseId);
        return R.ok(slideService.getPages(courseId, chapterId));
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
        String etag = "W/\"" + pageNumber + "-" + imageBytes.length + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .header(HttpHeaders.ETAG, etag)
                .body(imageBytes);
    }

    @GetMapping("/pages/{pageNumber}/thumbnail")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getPageThumbnail(@PathVariable Long courseId,
                                                    @PathVariable Integer pageNumber) {
        verifyAccess(courseId);
        byte[] thumbBytes = slideService.getPageThumbnail(courseId, pageNumber);
        String etag = "W/\"" + pageNumber + "-" + thumbBytes.length + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .header(HttpHeaders.ETAG, etag)
                .body(thumbBytes);
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deleteSlide(@PathVariable Long courseId,
                               @RequestParam(required = false) Long chapterId) {
        slideService.deleteSlide(courseId, chapterId);
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
        SlideVO slide = slideService.getByCourseId(courseId);
        byte[] fileBytes = slideService.getOriginalFile(courseId);
        String filename = slide.getFileName() != null ? slide.getFileName() : "slide.pptx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.presentationml.presentation")
                .body(fileBytes);
    }

    /**
     * IDOR 防护：验证当前用户有权限访问此课程的课件。
     * 身份叠加原则（权限矩阵 §4.1）：同时具备多个角色的用户，权限集合取并集。
     * - ADMIN: 全部通行
     * - ACADEMIC: 全部通行
     * - TEACHER: 必须是课程的所有者
     * - STUDENT: 必须已选此课（有 APPROVED/COMPLETED 的 enrollment 记录）
     */
    private void verifyAccess(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // P1-I-07: 学生必须已选课才能查看课件内容
        if (SecurityUtil.hasRole("STUDENT") && !SecurityUtil.isAdmin()) {
            LambdaQueryWrapper<Enrollment> check = new LambdaQueryWrapper<>();
            check.eq(Enrollment::getUserId, SecurityUtil.getCurrentUserId())
                 .eq(Enrollment::getCourseId, courseId)
                 .in(Enrollment::getEnrollmentStatus, "APPROVED", "COMPLETED")
                 .isNull(Enrollment::getDeletedAt);
            if (enrollmentRepository.selectCount(check) == 0) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "请先选课再查看课件");
            }
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
     * PPTX 文件是 ZIP 格式，魔数为 PK\x03\x04。
     * P1-I-02 修复：统一为仅允许 PPTX（ZIP 魔数），移除 JPEG/PNG 分支。
     * Service 层扩展名校验仅接受 .pptx，两层标准一致。
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
        if (!isZip) {
            throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID,
                    "不支持的课件格式（仅支持 PPTX 格式）");
        }
    }
}
