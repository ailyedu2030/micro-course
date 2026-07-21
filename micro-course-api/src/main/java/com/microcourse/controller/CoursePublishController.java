package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.CoursePricingInfoVO;
import com.microcourse.dto.CoursePricingRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PricingForAdopterVO;
import com.microcourse.dto.R;
import com.microcourse.dto.RejectRequest;
import com.microcourse.security.RequireRole;
import com.microcourse.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "课程发布管理", description = "课程发布、审批、状态流转相关端点（从CourseController拆分）")
public class CoursePublishController {

    private final CourseService courseService;

    public CoursePublishController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('TEACHER')")
    @AuditedLog("提交课程审核")
    @Operation(summary = "提交课程审核 (DRAFT→PENDING_REVIEW, 守卫检查标题/分类/封面/章节/视频练习课件)")
    public R<Void> submitForReview(@PathVariable Long id) {
        courseService.submitForReview(id);
        return R.ok();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("课程审核通过")
    @Operation(summary = "审核通过 (PENDING_REVIEW→APPROVED, 自审批阻断, 通知教师)")
    public R<Void> approve(@PathVariable Long id) {
        courseService.approve(id);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    @AuditedLog("课程审核驳回")
    @Operation(summary = "审核驳回 (PENDING_REVIEW→REJECTED, reason ≥ 10 字符)")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        courseService.reject(id, request.getReason());
        return R.ok();
    }

    @PostMapping("/{id}/reject-to-draft")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("课程退回草稿")
    @Operation(summary = "驳回后退回草稿 (REJECTED→DRAFT)")
    public R<Void> rejectToDraft(@PathVariable Long id) {
        courseService.rejectToDraft(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("课程上架")
    @Operation(summary = "发布课程 (APPROVED/CLOSED→PUBLISHED, 定价/课件/插件守卫, 通知在学学生)")
    public R<Void> publish(@PathVariable Long id) {
        courseService.publish(id);
        return R.ok();
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("课程下架")
    @Operation(summary = "下架课程 (PUBLISHED→CLOSED)")
    public R<Void> unpublish(@PathVariable Long id) {
        courseService.unpublish(id);
        return R.ok();
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("复制课程")
    @Operation(summary = "复制课程 (含章节/视频元数据, 复制后状态 DRAFT)")
    public R<CourseVO> copy(@PathVariable Long id) {
        CourseVO vo = courseService.copy(id);
        return R.ok(vo);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    @RequireRole({"TEACHER", "ADMIN", "ACADEMIC"})
    @AuditedLog("变更课程状态")
    @Operation(summary = "通用状态变更 (仅支持 CLOSED/ARCHIVED, PENDING_REVIEW/PUBLISHED 须用专用端点)")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        courseService.updateStatus(id, status);
        return R.ok();
    }

    @PutMapping("/{id}/pricing")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'ACADEMIC')")
    @AuditedLog("更新课程定价")
    @Operation(summary = "更新课程定价 (含审批流: DRAFT→PENDING→APPROVED/REJECTED)")
    public R<Void> updatePricing(@PathVariable Long id, @Valid @RequestBody CoursePricingRequest request) {
        courseService.updatePricing(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/pricing/submit-review")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'ACADEMIC')")
    @AuditedLog("提交定价审核")
    @Operation(summary = "提交定价审核 (DRAFT→PENDING)")
    public R<Void> submitPricingForReview(@PathVariable Long id) {
        courseService.submitPricingForReview(id);
        return R.ok();
    }

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

    @GetMapping("/{id}/pricing-for-adopter")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "教师查询自身课程对学生的预期定价 (含院系匹配)")
    public R<PricingForAdopterVO> getPricingForAdopter(@PathVariable Long id) {
        return R.ok(courseService.getPricingForAdopter(id));
    }

    @GetMapping("/{id}/my-price")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "学生查询本课程个性化定价 (基于部门/学院/学校)")
    public R<CoursePricingInfoVO> getMyPricing(@PathVariable Long id) {
        return R.ok(courseService.getMyPricing(id));
    }
}
