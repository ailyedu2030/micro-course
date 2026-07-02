package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CoursePricingRequest;
import com.microcourse.dto.CourseStatsVO;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.enums.CourseStatus;
import com.microcourse.security.RequireRole;
import com.microcourse.service.CourseService;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@Validated
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final NotificationService notificationService;

    public CourseController(CourseService courseService,
                            EnrollmentService enrollmentService,
                            NotificationService notificationService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<CourseVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean recommended,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String courseType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        CoursePageQuery query = new CoursePageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setTitle(title);
        query.setKeyword(keyword);
        query.setCategoryId(categoryId);
        query.setTeacherId(teacherId);
        query.setStatus(status);
        query.setRecommended(recommended);
        query.setDifficulty(difficulty);
        query.setCourseType(courseType);
        query.setSortBy(sortBy);
        query.setSortOrder(sortOrder);
        PageResult<CourseVO> result = courseService.page(query);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<CourseVO> getById(@PathVariable Long id) {
        CourseVO vo = courseService.getById(id);
        // Round 11-1 数据隔离：下架(CLOSED)/归档(ARCHIVED)课程对学生等非授权角色不可见。
        //   - ADMIN / ACADEMIC：运营与教务全程可见；
        //   - TEACHER：仅本人课程可见（继续管理已下架课程）；
        //   - 其余（STUDENT 等）：返回 404 模拟"不存在"，避免泄露下架课程的存在与元数据。
        Integer status = vo.getStatus();
        if (status != null
                && (status.intValue() == CourseStatus.CLOSED.getCode()
                    || status.intValue() == CourseStatus.ARCHIVED.getCode())) {
            boolean privileged = SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC");
            boolean ownerTeacher = SecurityUtil.hasRole("TEACHER")
                    && vo.getTeacherId() != null
                    && vo.getTeacherId().equals(SecurityUtil.getCurrentUserId());
            if (!privileged && !ownerTeacher) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
        }
        return R.ok(vo);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建课程")
    public R<CourseVO> create(@Valid @RequestBody CourseCreateRequest request) {
        CourseVO vo = courseService.create(request);
        return R.ok(vo);
    }

    /**
     * PUT /api/courses/{id}
     * 更新课程信息
     * 权限：TEACHER（课程创建者，Service 层 isOwnerOrAdmin 校验）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<CourseVO> update(@PathVariable Long id,
                              @Valid @RequestBody CourseUpdateRequest request) {
        CourseVO vo = courseService.update(id, request);
        return R.ok(vo);
    }

    /** Phase 4: 更新课程定价 */
    @PutMapping("/{id}/pricing")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'ACADEMIC')")
    public R<Void> updatePricing(@PathVariable Long id, @Valid @RequestBody CoursePricingRequest request) {
        courseService.updatePricing(id, request);
        return R.ok();
    }

    /** Phase 4: 查询课程对某教师的费用 */
    @GetMapping("/{id}/pricing-for-adopter")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Map<String, Object>> getPricingForAdopter(@PathVariable Long id) {
        return R.ok(courseService.getPricingForAdopter(id));
    }

    /**
     * PUT /api/courses/{id}/status — 课程状态变更
     *
     * <p>Phase D-1 P3-6 演示：{@code @RequireRole} 与 {@code @PreAuthorize} <b>叠加</b>使用，
     * 角色集合完全一致（ADMIN / ACADEMIC），逻辑等价。{@code @PreAuthorize} 在过滤器层
     * 先行决断，故本端点行为与权限语义零变化；{@code @RequireRole} 仅作为常量化注解的
     * 落地示例，建立渐进迁移路径，不强制全量替换。</p>
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    @RequireRole({"TEACHER", "ADMIN", "ACADEMIC"})
    public R<Void> updateStatus(@PathVariable Long id,
                                @RequestParam Integer status) {
        courseService.updateStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/submit
     * 提交课程审核（草稿 → 待审核）
     * 权限：TEACHER, ADMIN
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("提交课程审核")
    public R<Void> submitForReview(@PathVariable Long id) {
        courseService.submitForReview(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/approve
     * 审核通过（待审核 → 已通过）
     * 权限：ADMIN, ACADEMIC
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("课程审核通过")
    public R<Void> approve(@PathVariable Long id) {
        courseService.approve(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/reject
     * 审核拒绝（待审核 → 已驳回）
     * 权限：ADMIN, ACADEMIC
     * @param body {"reason": "拒绝原因"}
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("课程审核驳回")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        // P0#3 修复:驳回原因不能为空或过短
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "驳回原因不能为空");
        }
        if (reason.trim().length() < 10) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "驳回原因过短，至少需要10个字符");
        }
        courseService.reject(id, reason);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/publish
     * 发布课程（已通过 → 已发布）
     * 权限：ADMIN（Phase A-4 P0-9 修复：矩阵 §2.3 PUBLISH_COURSE 仅 ADMIN，移除 TEACHER/ACADEMIC 越权）
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("课程上架")
    public R<Void> publish(@PathVariable Long id) {
        courseService.publish(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/copy
     * 复制课程（模板复制：复制课程基本信息 + 章节结构，不含视频文件）
     * 权限：TEACHER（课程创建者）, ADMIN
     * @return 新课程VO
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<CourseVO> copy(@PathVariable Long id) {
        CourseVO vo = courseService.copy(id);
        return R.ok(vo);
    }

    /**
     * GET /api/courses/pending-review
     * 获取待审核课程列表（Phase A-4 P0-5 新增）
     * 权限：ADMIN（依据 权限矩阵 v2.0 §2.3 READ_PENDING_REVIEW_COURSES = 仅 ADMIN）
     */
    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('ADMIN')")
    public R<PageResult<CourseVO>> pendingReview(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        CoursePageQuery query = new CoursePageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setStatus(CourseStatus.PENDING_REVIEW.getCode());
        return R.ok(courseService.page(query));
    }

    /**
     * GET /api/courses/{id}/students
     * 获取课程选课学生列表（Phase A-4 P0-5 新增）
     * 权限：TEACHER(课程创建者) / ADMIN / ACADEMIC（依据 权限矩阵 v2.0 §2.3 READ_COURSE_STUDENTS）
     * - TEACHER 必须为课程 owner（owner 校验下沉 Service）
     * - ADMIN / ACADEMIC 无限制
     */
    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<EnrollmentVO>> getCourseStudents(@PathVariable Long id) {
        // TEACHER（非 ADMIN）必须为课程 owner，否则 403；ADMIN/ACADEMIC 跳过校验
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            enrollmentService.assertCourseOwnership(id);
        }
        List<EnrollmentVO> students = enrollmentService.getCourseEnrollments(id);
        return R.ok(students);
    }

    /**
     * POST /api/courses/{id}/unpublish
     * 下架课程（已发布 → 下架）（Round 5-3 P1-10 新增）
     * 权限：ADMIN（依据 权限矩阵 v2.0 §2.3 UNPUBLISH_COURSE = 仅 ADMIN）
     *
     * <p>复用既有 {@link CourseService#updateStatus} 的状态机白名单：PUBLISHED → CLOSED 为合法转换，
     * 非已发布课程将得到 400（COURSE_STATUS_TRANSITION_NOT_ALLOWED），不会 5xx。</p>
     */
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("课程下架")
    public R<Void> unpublish(@PathVariable Long id) {
        // 客户体验修复 v1.7.0: 下架前先取课程信息,下架后通知所有在学学生 (U20)
        CourseVO before = courseService.getById(id);
        courseService.updateStatus(id, CourseStatus.CLOSED.getCode());
        if (before != null) {
            try {
                notifyCourseUnpublished(id, before.getTitle());
            } catch (Exception e) {
                // 通知失败不影响主流程,只记录日志
                org.slf4j.LoggerFactory.getLogger(CourseController.class)
                    .warn("课程下架通知失败: courseId={}, err={}", id, e.getMessage());
            }
        }
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/cover
     * 更新课程封面
     * 权限：TEACHER（课程创建者）
     */
    @PostMapping("/{id}/cover")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新课程封面")
    public R<CourseVO> updateCover(@PathVariable Long id,
                                   @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面文件不能为空");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面文件不能超过 2MB");
        }
        // P1 安全修复: 封面魔数校验（JPEG/PNG）
        validateCoverImageMagic(file);
        CourseVO vo = courseService.updateCover(id, file);
        return R.ok(vo);
    }

    /**
     * GET /api/courses/{id}/stats
     * 获取课程统计数据（选课人数 / 完成率 / 平均分）（Round 5-3 P1-10 新增）
     * 权限：TEACHER(课程创建者) / ADMIN / ACADEMIC（依据 权限矩阵 v2.0 §2.3 READ_COURSE_STATS）
     * - TEACHER（非 ADMIN）必须为课程 owner，否则 403（owner 校验复用 enrollmentService.assertCourseOwnership）
     * - ADMIN / ACADEMIC 无限制
     */
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<CourseStatsVO> getCourseStats(@PathVariable Long id) {
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            enrollmentService.assertCourseOwnership(id);
        }
        return R.ok(courseService.computeStats(id));
    }

    /**
     * P1 安全修复: 封面图片魔数校验（JPEG: FFD8FF, PNG: 89504E47）
     */
    private void validateCoverImageMagic(MultipartFile file) {
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] magic = new byte[8];
            int read = is.read(magic);
            if (read < 4) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面文件过小，无法验证格式");
            }
            boolean isJpeg = (magic[0] & 0xFF) == 0xFF
                    && (magic[1] & 0xFF) == 0xD8
                    && (magic[2] & 0xFF) == 0xFF;
            boolean isPng = (magic[0] & 0xFF) == 0x89
                    && magic[1] == 'P'
                    && magic[2] == 'N'
                    && magic[3] == 'G';
            if (!isJpeg && !isPng) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "封面必须为 JPEG 或 PNG 格式（魔数校验失败）");
            }
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取封面文件");
        }
    }

    /**
     * 客户体验修复 v1.7.0: 课程下架后通知所有在学学生 (U20)
     * v1.7.0 性能优化 (P1 perf): 改用异步批量,避免串行循环阻塞 HTTP 响应
     * - 之前: 200 学生 = 620ms 同步阻塞
     * - 现在: HTTP 立即返回, 后台异步发送
     * 失败不影响主流程,只记日志
     */
    private void notifyCourseUnpublished(Long courseId, String courseTitle) {
        List<Long> userIds = enrollmentService.findActiveUserIdsByCourseId(courseId);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String title = "课程下架通知";
        String content = String.format("您正在学习的《%s》已下架,如有疑问请联系管理员。", courseTitle);
        // 异步批量发送 (notifyAsync 内部 REQUIRES_NEW + 失败重试,主线程不阻塞)
        for (Long userId : userIds) {
            try {
                notificationService.notifyAsync(userId,
                    com.microcourse.enums.NotificationType.COURSE_UNPUBLISHED,
                    title, content, courseId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(CourseController.class)
                    .debug("单条下架通知失败: userId={}, err={}", userId, e.getMessage());
            }
        }
        org.slf4j.LoggerFactory.getLogger(CourseController.class)
            .info("课程下架通知已派发 (异步): courseId={}, 收件人数={}", courseId, userIds.size());
    }
}