package com.microcourse.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.R;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/courses/{courseId}/interactive")
@Tag(name = "互动课件管理", description = "互动课件 CRUD")
public class InteractiveCoursewareController {

    private final SlideService slideService;
    private final CourseSlideMapper courseSlideMapper;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public InteractiveCoursewareController(SlideService slideService,
                                          CourseSlideMapper courseSlideMapper,
                                          CourseRepository courseRepository,
                                          EnrollmentRepository enrollmentRepository) {
        this.slideService = slideService;
        this.courseSlideMapper = courseSlideMapper;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * POST /api/courses/{courseId}/interactive
     * 创建互动课件（上传文件）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建互动课件")
    @Operation(summary = "创建互动课件（上传文件）")
    public R<SlideUploadResponse> create(@PathVariable Long courseId,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) Long chapterId) throws IOException {
        verifyTeacherOwnership(courseId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        SlideUploadResponse response = slideService.upload(courseId, file.getOriginalFilename(),
                file.getBytes(), chapterId, null);
        return R.ok(response);
    }

    /**
     * GET /api/courses/{courseId}/interactive
     * 获取课程互动课件
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取课程互动课件")
    public R<SlideVO> get(@PathVariable Long courseId) {
        verifyAccess(courseId);
        SlideVO vo = slideService.getByCourseId(courseId);
        if (vo == null) {
            throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND, "该课程暂无课件");
        }
        return R.ok(vo);
    }

    /**
     * PUT /api/courses/{courseId}/interactive/{id}
     * 更新互动课件
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新互动课件")
    @Operation(summary = "更新互动课件")
    public R<Void> update(@PathVariable Long courseId,
                           @PathVariable Long id,
                           @RequestBody Map<String, Object> body) {
        verifyTeacherOwnership(courseId);
        CourseSlide slide = courseSlideMapper.selectById(id);
        if (slide == null) {
            throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND);
        }
        if (!slide.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "该课件不属于本课程");
        }
        if (body.containsKey("fileName") && body.get("fileName") instanceof String) {
            slide.setFileName((String) body.get("fileName"));
        }
        slide.setUpdatedAt(java.time.LocalDateTime.now());
        int affected = courseSlideMapper.updateById(slide);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "课件已被其他人修改，请刷新后重试");
        }
        return R.ok();
    }

    /**
     * DELETE /api/courses/{courseId}/interactive/{id}
     * 删除互动课件
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除互动课件")
    @Operation(summary = "删除互动课件")
    public R<Void> delete(@PathVariable Long courseId, @PathVariable Long id) {
        verifyTeacherOwnership(courseId);
        CourseSlide slide = courseSlideMapper.selectById(id);
        if (slide == null) {
            throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND);
        }
        if (!slide.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "该课件不属于本课程");
        }
        slideService.deleteSlide(courseId, id);
        return R.ok();
    }

    private void verifyTeacherOwnership(Long courseId) {
        if (SecurityUtil.isAdmin()) {
            return;
        }
        if (!SecurityUtil.hasRole("TEACHER")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!course.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程");
        }
    }

    private void verifyAccess(Long courseId) {
        if (SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC")) {
            return;
        }
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (SecurityUtil.hasRole("TEACHER")) {
            if (!course.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程");
            }
            return;
        }
        if (SecurityUtil.hasRole("STUDENT")) {
            Long userId = SecurityUtil.getCurrentUserId();
            Enrollment enrollment = enrollmentRepository.selectOne(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getUserId, userId)
                            .eq(Enrollment::getCourseId, courseId)
                            .in(Enrollment::getEnrollmentStatus, "APPROVED", "COMPLETED")
                            .isNull(Enrollment::getDeletedAt));
            if (enrollment == null) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "请先选课再查看课件");
            }
        }
    }
}
