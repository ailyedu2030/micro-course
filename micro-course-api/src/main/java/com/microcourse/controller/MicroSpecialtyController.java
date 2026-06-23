package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCreateRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyDetailVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyLeadTransferRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtySquareVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyStatsVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyUpdateRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import com.microcourse.service.MicroSpecialtyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 微专业主表 Controller。
 * 职责：CRUD + 状态流转 + 课程编排 + 教师团队 + LEAD 继任 + 统计。
 * 置顶操作委托 MicroSpecialtyFeaturedController。
 */
@RestController
@RequestMapping("/api/micro-specialties")
@Validated
public class MicroSpecialtyController {

    private final MicroSpecialtyService microSpecialtyService;
    private final MicroSpecialtyEnrollmentService msEnrollmentService;

    public MicroSpecialtyController(MicroSpecialtyService microSpecialtyService,
                                     MicroSpecialtyEnrollmentService msEnrollmentService) {
        this.microSpecialtyService = microSpecialtyService;
        this.msEnrollmentService = msEnrollmentService;
    }

    // ==================== 查询 ====================

    /** 分页列表 */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<MicroSpecialtyVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        PageResult<MicroSpecialtyVO> result = microSpecialtyService.page(page, size, keyword, status);
        return R.ok(result);
    }

    /** 课程广场专区 */
    @GetMapping("/square")
    @PreAuthorize("permitAll()")
    public R<MicroSpecialtySquareVO> square() {
        MicroSpecialtySquareVO vo = microSpecialtyService.getSquareData();
        return R.ok(vo);
    }

    /** 详情 */
    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    public R<MicroSpecialtyDetailVO> getDetail(@PathVariable Long id) {
        MicroSpecialtyDetailVO vo = microSpecialtyService.getDetail(id);
        return R.ok(vo);
    }

    /** 统计数据 */
    @GetMapping("/{id}/stats")
    @PreAuthorize("isAuthenticated()")
    public R<MicroSpecialtyStatsVO> stats(@PathVariable Long id) {
        MicroSpecialtyStatsVO vo = microSpecialtyService.stats(id);
        return R.ok(vo);
    }

    /** 修读名单（§7.5 端点对齐 spec）*/
    @GetMapping("/{id}/enrollments")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<PageResult<MicroSpecialtyEnrollmentVO>> listEnrollments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageResult<MicroSpecialtyEnrollmentVO> result = msEnrollmentService.listEnrollments(id, page, size, status);
        return R.ok(result);
    }

    // ==================== CUD ====================

    /** 创建微专业 */
    @PostMapping
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<MicroSpecialtyVO> create(@Valid @RequestBody MicroSpecialtyCreateRequest request) {
        MicroSpecialtyVO vo = microSpecialtyService.create(request);
        return R.ok(vo);
    }

    /** 更新基本信息 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<MicroSpecialtyVO> update(@PathVariable Long id,
                                       @Valid @RequestBody MicroSpecialtyUpdateRequest request) {
        MicroSpecialtyVO vo = microSpecialtyService.update(id, request);
        return R.ok(vo);
    }

    /** 软删除 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        microSpecialtyService.delete(id);
        return R.ok();
    }

    // ==================== 状态流转 ====================

    /** 提交审核 */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> submit(@PathVariable Long id) {
        microSpecialtyService.submit(id);
        return R.ok();
    }

    /** 审批通过 */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> approve(@PathVariable Long id) {
        microSpecialtyService.approve(id);
        return R.ok();
    }

    /** 审批驳回 */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        microSpecialtyService.reject(id, reason);
        return R.ok();
    }

    /** 开课 */
    @PostMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> open(@PathVariable Long id) {
        microSpecialtyService.open(id);
        return R.ok();
    }

    /** 结业 */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> close(@PathVariable Long id) {
        microSpecialtyService.close(id);
        return R.ok();
    }

    /** 强制取消 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ACADEMIC','ADMIN')")
    public R<Void> cancel(@PathVariable Long id) {
        microSpecialtyService.cancel(id);
        return R.ok();
    }

    /** 归档 */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> archive(@PathVariable Long id) {
        microSpecialtyService.archive(id);
        return R.ok();
    }

    // ==================== 课程编排 ====================

    /** 获取课程列表 */
    @GetMapping("/{id}/courses")
    @PreAuthorize("permitAll()")
    public R<List<MicroSpecialtyCourseVO>> listCourses(@PathVariable Long id) {
        List<MicroSpecialtyCourseVO> courses = microSpecialtyService.listCourses(id);
        return R.ok(courses);
    }

    /** 添加课程 */
    @PostMapping("/{id}/courses")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<MicroSpecialtyCourseVO> addCourse(@PathVariable Long id,
                                                @Valid @RequestBody MicroSpecialtyCourseRequest request) {
        MicroSpecialtyCourseVO vo = microSpecialtyService.addCourse(id, request);
        return R.ok(vo);
    }

    /** 更新课程编排 */
    @PutMapping("/{id}/courses/{itemId}")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<MicroSpecialtyCourseVO> updateCourseItem(@PathVariable Long id,
                                                       @PathVariable Long itemId,
                                                       @Valid @RequestBody MicroSpecialtyCourseRequest request) {
        MicroSpecialtyCourseVO vo = microSpecialtyService.updateCourseItem(id, itemId, request);
        return R.ok(vo);
    }

    /** 移除课程 */
    @DeleteMapping("/{id}/courses/{itemId}")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> removeCourse(@PathVariable Long id,
                                 @PathVariable Long itemId) {
        microSpecialtyService.removeCourse(id, itemId);
        return R.ok();
    }

    // ==================== 教师团队 ====================

    /** 获取教师团队列表 */
    @GetMapping("/{id}/teachers")
    @PreAuthorize("permitAll()")
    public R<List<MicroSpecialtyTeacherVO>> listTeachers(@PathVariable Long id) {
        List<MicroSpecialtyTeacherVO> teachers = microSpecialtyService.listTeachers(id);
        return R.ok(teachers);
    }

    /** 发送邀请 */
    @PostMapping("/{id}/teachers")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<MicroSpecialtyTeacherVO> inviteTeacher(@PathVariable Long id,
                                                     @Valid @RequestBody MicroSpecialtyTeacherRequest request) {
        MicroSpecialtyTeacherVO vo = microSpecialtyService.inviteTeacher(id, request);
        return R.ok(vo);
    }

    /** 移除教师 */
    @DeleteMapping("/{id}/teachers/{teacherId}")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> removeTeacher(@PathVariable Long id,
                                  @PathVariable Long teacherId) {
        microSpecialtyService.removeTeacher(id, teacherId);
        return R.ok();
    }

    // ==================== LEAD 继任 ====================

    /** LEAD 继任 */
    @PostMapping("/{id}/transfer-leadership")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> transferLeadership(@PathVariable Long id,
                                       @Valid @RequestBody MicroSpecialtyLeadTransferRequest request) {
        microSpecialtyService.transferLeadership(id, request);
        return R.ok();
    }
}
