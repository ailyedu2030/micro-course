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
                return R.fail(400, "上传文件不能为空");
            }
            String filename = file.getOriginalFilename();
            long size = file.getSize();
            String mime = file.getContentType();
            log.info("[SlideUpload] courseId={}, filename={}, size={}, mime={}", courseId, filename, size, mime);
            SlideUploadResponse resp = slideService.upload(courseId, filename, file.getBytes());
            return R.ok(resp);
        } catch (IOException e) {
            log.error("[SlideUpload] 文件读取IO异常 courseId={}", courseId, e);
            return R.fail(400, "文件读取失败");
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
     * - ADMIN/ACADEMIC: 全部通行
     * - TEACHER: 必须是课程的所有者
     * - STUDENT: 必须已选此课（有 enrollment 记录）
     */
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
        if (SecurityUtil.hasRole("TEACHER")) {
            if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
            return;
        }
        // STUDENT: 检查是否已选课
        if (SecurityUtil.hasRole("STUDENT")) {
            Long currentUserId = SecurityUtil.getCurrentUserId();
            long count = enrollmentRepository.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.Enrollment>()
                            .eq(com.microcourse.entity.Enrollment::getUserId, currentUserId)
                            .eq(com.microcourse.entity.Enrollment::getCourseId, courseId)
                            .ne(com.microcourse.entity.Enrollment::getEnrollmentStatus, "CANCELLED"));
            if (count == 0) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
            return;
        }
    }
}
