package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.BatchApproveRequest;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.dto.BatchRejectRequest;
import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CourseStatsVO;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.enums.CourseStatus;
import com.microcourse.service.CourseAdminService;
import com.microcourse.service.CourseQueryService;
import com.microcourse.service.CourseService;
import com.microcourse.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    @GetMapping(params = "hid")
    @PreAuthorize("isAuthenticated()")
    public R<CourseVO> getByHid(@RequestParam String hid) {
        CourseVO vo = courseQueryService.getByHid(hid);
        if (vo == null) {
            throw new com.microcourse.exception.BusinessException(
                com.microcourse.exception.ErrorCode.COURSE_NOT_FOUND);
        }
        return R.ok(vo);
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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("删除课程")
        @Operation(summary = "删除课程 (级联清理章节/视频/进度/讨论/笔记/书签/练习/课件)")
    public R<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return R.ok();
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

    /**
     * GET /api/courses/export
     * 导出课程数据为 Excel
     * 权限: ADMIN / ACADEMIC
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @Operation(summary = "导出课程数据为 Excel")
    public void exportCourses(HttpServletResponse response) throws IOException {
        courseService.exportCourses(response);
    }

    }
