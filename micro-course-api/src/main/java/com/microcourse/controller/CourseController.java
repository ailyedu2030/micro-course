package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.BatchApproveRequest;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.dto.BatchRejectRequest;
import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CoursePricingInfoVO;
import com.microcourse.dto.CoursePricingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.microcourse.dto.PricingForAdopterVO;
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
import com.microcourse.service.CourseAdminService;
import com.microcourse.service.CourseQueryService;
import com.microcourse.service.CourseService;
import com.microcourse.service.EnrollmentService;
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

@RestController
@RequestMapping("/api/courses")
@Tag(name = "课程管理", description = "课程 CRUD、审批、定价、发布等 23 端点")
public class CourseController {

    private final CourseService courseService;
    private final CourseQueryService courseQueryService;
    private final EnrollmentService enrollmentService;
    private final CourseAdminService courseAdminService;

    public CourseController(CourseService courseService,
                            CourseQueryService courseQueryService,
                            EnrollmentService enrollmentService,
                            CourseAdminService courseAdminService) {
        this.courseService = courseService;
        this.courseQueryService = courseQueryService;
        this.enrollmentService = enrollmentService;
        this.courseAdminService = courseAdminService;
    }

    private static final int MAX_PAGE_SIZE = 200;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
        @Operation(summary = "分页查询课程列表 (CourseStatus 自动按角色过滤: STUDENT 仅看 APPROVED+PUBLISHED, TEACHER 仅看自己, 其他全部)")
    public R<PageResult<CourseVO>> page(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @PositiveOrZero int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean recommended,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String courseType,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false) Long offerDepartmentId) {
        CoursePageQuery query = new CoursePageQuery();
        query.setPage(page);
        // P2: clamp size 到 [1, MAX_PAGE_SIZE]
        if (size < 1) size = 1;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        query.setSize(size);
        query.setTitle(title);
        query.setKeyword(keyword);
        query.setCategoryId(categoryId);
        query.setTeacherId(teacherId);
        query.setStatus(status);
        query.setRecommended(recommended);
        query.setDifficulty(difficulty);
        query.setCourseType(courseType);
        query.setTeacherName(teacherName);
        query.setSortBy(sortBy);
        query.setSortOrder(sortOrder);
        query.setOfferDepartmentId(offerDepartmentId);
        PageResult<CourseVO> result = courseService.page(query);
        return R.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
        @Operation(summary = "获取课程详情 (含缓存, 数据隔离在 Service 层)")
    public R<CourseVO> getById(@PathVariable Long id) {
        return R.ok(courseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("创建课程")
        @Operation(summary = "创建课程 (TEACHER 创建者自动绑定, OFFLINE 不需要插件授权, INTERACTIVE 需要)")
    public R<CourseVO> create(@Valid @RequestBody CourseCreateRequest request) {
        CourseVO vo = courseService.create(request);
        return R.ok(vo);
    }

    /**
     * GET /api/courses/teacher/{teacherId}
     * 按教师查询课程列表 (权限矩阵 v4.0 §3.3 GET_TEACHER_COURSES)
     * 权限：TEACHER(本人) / ADMIN / ACADEMIC / STUDENT
     * - TEACHER 必须 teacherId == 当前用户
     */
    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC','STUDENT')")
        @Operation(summary = "按教师查询课程列表 (TEACHER 仅本人, ADMIN/ACADEMIC 全部)")
    public R<List<CourseVO>> listByTeacher(@PathVariable Long teacherId,
                                           @RequestParam(defaultValue = "true") boolean includeDrafts) {
        // TEACHER Owner 校验下沉到 Service 层
        List<CourseVO> courses = courseQueryService.listByTeacherIdWithOwnerCheck(teacherId, includeDrafts);
        return R.ok(courses);
    }

    /**
     * PUT /api/courses/{id}
     * 更新课程信息
     * 权限：TEACHER（课程创建者，Service 层 isOwnerOrAdmin 校验）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新课程信息")
        @Operation(summary = "更新课程信息 (TEACHER owner, Service 层 isOwnerOrAdmin 校验)")
    public R<CourseVO> update(@PathVariable Long id,
                              @Valid @RequestBody CourseUpdateRequest request) {
        CourseVO vo = courseService.update(id, request);
        return R.ok(vo);
    }

    /** Phase 4: 更新课程定价 */
    @PutMapping("/{id}/pricing")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'ACADEMIC')")
    @AuditedLog("更新课程定价")
        @Operation(summary = "更新课程定价 (含审批流: DRAFT→PENDING→APPROVED/REJECTED)")
    public R<Void> updatePricing(@PathVariable Long id, @Valid @RequestBody CoursePricingRequest request) {
        courseService.updatePricing(id, request);
        return R.ok();
    }

    /** Phase 4: 查询课程对某教师的费用 */
    @GetMapping("/{id}/pricing-for-adopter")
    @PreAuthorize("hasRole('TEACHER')")
        @Operation(summary = "教师查询自身课程对学生的预期定价 (含院系匹配)")
    public R<PricingForAdopterVO> getPricingForAdopter(@PathVariable Long id) {
        return R.ok(courseService.getPricingForAdopter(id));
    }

    /** Round 1: 查询课程对当前登录用户的价格（学生端可见，公开端点） */
    @GetMapping("/{id}/my-price")
    @PreAuthorize("isAuthenticated()")
        @Operation(summary = "学生查询本课程个性化定价 (基于部门/学院/学校)")
    public R<CoursePricingInfoVO> getMyPricing(@PathVariable Long id) {
        return R.ok(courseService.getMyPricing(id));
    }

    /** P0 修复: 提交定价审核 (DRAFT → PENDING) */
    @PostMapping("/{id}/pricing/submit-review")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'ACADEMIC')")
    @AuditedLog("提交定价审核")
        @Operation(summary = "提交定价审核 (DRAFT→PENDING)")
    public R<Void> submitPricingForReview(@PathVariable Long id) {
        courseService.submitPricingForReview(id);
        return R.ok();
    }

    /** P0 修复: 审核定价 (PENDING → APPROVED / REJECTED) */
    @PostMapping("/{id}/pricing/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC')")
    @AuditedLog("审核课程定价")
        @Operation(summary = "审批定价 (ADMIN/ACADEMIC, approved 决定 APPROVED 或 REJECTED)")
    public R<Void> reviewPricing(@PathVariable Long id,
                                  @RequestParam boolean approved,
                                  @RequestParam(required = false) String reason) {
        courseService.reviewPricing(id, approved, reason);
        return R.ok();
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
    @AuditedLog("变更课程状态")
        @Operation(summary = "通用状态变更 (仅支持 CLOSED/ARCHIVED, PENDING_REVIEW/PUBLISHED 须用专用端点)")
    public R<Void> updateStatus(@PathVariable Long id,
                                @RequestParam Integer status) {
        courseService.updateStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除课程")
        @Operation(summary = "删除课程 (级联清理章节/视频/进度/讨论/笔记/书签/练习/课件)")
    public R<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/submit
     * 提交课程审核（草稿 → 待审核）
     * 权限：TEACHER（创建者本人） — Service 层 isOwnerOrAdmin 校验
     * 【权限矩阵 v4.0】仅 TEACHER，ADMIN 不能越权提交
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('TEACHER')")
    @AuditedLog("提交课程审核")
        @Operation(summary = "提交课程审核 (DRAFT→PENDING_REVIEW, 守卫检查标题/分类/封面/章节/视频练习课件)")
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
        @Operation(summary = "审核通过 (PENDING_REVIEW→APPROVED, 自审批阻断, 通知教师)")
    public R<Void> approve(@PathVariable Long id) {
        courseService.approve(id);
        return R.ok();
    }

    /**
     * POST /api/courses/{id}/reject
     * 审核拒绝（待审核 → 已驳回）
     * 权限：ADMIN, ACADEMIC
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("课程审核驳回")
        @Operation(summary = "审核驳回 (PENDING_REVIEW→REJECTED, reason ≥ 10 字符)")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody com.microcourse.dto.RejectRequest request) {
        courseService.reject(id, request.getReason());
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
        @Operation(summary = "发布课程 (APPROVED/CLOSED→PUBLISHED, 定价/课件/插件守卫, 通知在学学生)")
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
    @AuditedLog("复制课程")
        @Operation(summary = "复制课程 (含章节/视频元数据, 复制后状态 DRAFT)")
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
    @Operation(summary = "待审核课程列表 (仅 ADMIN/ACADEMIC)")
    @PreAuthorize("hasRole('ADMIN')")
    public R<PageResult<CourseVO>> pendingReview(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
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
    @Operation(summary = "获取课程选课学生列表 (TEACHER owner 校验)")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<EnrollmentVO>> getCourseStudents(@PathVariable Long id) {
        // 【V3 修复】TEACHER Owner 校验下沉到 Service 层
        List<EnrollmentVO> students = enrollmentService.getCourseEnrollmentsWithOwnerCheck(id);
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
    @Operation(summary = "下架课程 (PUBLISHED→CLOSED, 通知在学学生)")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditedLog("课程下架")
    public R<Void> unpublish(@PathVariable Long id) {
        courseService.unpublish(id);
        return R.ok();
    }

    /**
     * P2-11: 批量审核通过
     * POST /api/courses/batch-approve
     * 权限: ADMIN / ACADEMIC
     */
    @PostMapping("/batch-approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("批量课程审核通过")
        @Operation(summary = "批量审核通过 (ADMIN/ACADEMIC)")
    public R<BatchOperationResult> batchApprove(@Valid @RequestBody BatchApproveRequest req) {
        return R.ok(courseAdminService.batchApprove(req.getIds()));
    }

    /**
     * P2-11: 批量审核驳回
     * POST /api/courses/batch-reject
     * 权限: ADMIN / ACADEMIC
     */
    @PostMapping("/batch-reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("批量课程审核驳回")
        @Operation(summary = "批量审核驳回 (ADMIN/ACADEMIC)")
    public R<BatchOperationResult> batchReject(@Valid @RequestBody BatchRejectRequest req) {
        return R.ok(courseAdminService.batchReject(req.getIds(), req.getReason()));
    }

    /**
     * POST /api/courses/{id}/cover
     * 更新课程封面
     * 权限：TEACHER（课程创建者）
     */
    @PostMapping("/{id}/cover")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("更新课程封面")
        @Operation(summary = "更新课程封面 (JPEG/PNG 魔数校验, ≤2MB)")
    public R<CourseVO> updateCover(@PathVariable Long id,
                                   @RequestParam("file") MultipartFile file) {
        // 【V2 修复】文件校验下沉到 FileUploadUtil 工具类, Controller 不再直接读 InputStream
        com.microcourse.util.FileUploadUtil.assertImage(file, com.microcourse.util.FileUploadUtil.DEFAULT_IMAGE_MAX_BYTES);
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
        @Operation(summary = "获取课程统计 (选课人数/完成率/平均分, 含 Redis 缓存)")
    public R<CourseStatsVO> getCourseStats(@PathVariable Long id) {
        return R.ok(courseService.computeStats(id));
    }

    }