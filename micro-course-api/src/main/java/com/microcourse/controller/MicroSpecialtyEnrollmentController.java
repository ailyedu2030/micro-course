package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import jakarta.validation.Valid;
import com.microcourse.dto.microSpecialty.ClassImportRequest;
import com.microcourse.dto.microSpecialty.DropRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyApplyRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyRejectRequest;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 微专业修读 Controller。
 * 职责：报名 + 审批 + 班级导入 + 退出/重新申请 + 证书 + 修读名单。
 */
@RestController
@RequestMapping("/api/micro-specialty-enrollments")
@Validated
public class MicroSpecialtyEnrollmentController {

    private final MicroSpecialtyEnrollmentService enrollmentService;

    public MicroSpecialtyEnrollmentController(MicroSpecialtyEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /** 学生自主报名 → PENDING */
    @PostMapping("/apply")
    @PreAuthorize("isAuthenticated()")
    public R<MicroSpecialtyEnrollmentVO> apply(@Valid @RequestBody MicroSpecialtyApplyRequest request) {
        MicroSpecialtyEnrollmentVO vo = enrollmentService.apply(request.getMicroSpecialtyId());
        return R.ok(vo);
    }

    /** 审批通过 → APPROVED */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<MicroSpecialtyEnrollmentVO> approve(@PathVariable Long id) {
        MicroSpecialtyEnrollmentVO vo = enrollmentService.approve(id);
        return R.ok(vo);
    }

    /** 驳回报名 → REJECTED */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> reject(@PathVariable Long id, @RequestBody MicroSpecialtyRejectRequest request) {
        String reason = request.getReason() != null ? request.getReason() : "";
        enrollmentService.reject(id, reason);
        return R.ok();
    }

    /** 班级批量导入 → APPROVED */
    @PostMapping("/class-import")
    @PreAuthorize("hasAnyRole('ACADEMIC','ADMIN')")
    public R<Integer> classImport(@RequestBody ClassImportRequest request) {
        List<Long> classIds = request.getClassIds();
        if (classIds == null) {
            classIds = new java.util.ArrayList<>();
        }
        // Support single classId as fallback
        if (classIds.isEmpty() && request.getClassId() != null) {
            classIds.add(request.getClassId());
        }
        int totalCount = enrollmentService.classImportBatch(request.getMicroSpecialtyId(), classIds);
        return R.ok(totalCount);
    }

    /** 退出修读 */
    @PostMapping("/{id}/drop")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<Void> drop(@PathVariable Long id, @RequestBody DropRequest request) {
        enrollmentService.drop(id, request.isCascade(), request.getReason() != null ? request.getReason() : "");
        return R.ok();
    }

    /** 重新申请 */
    @PostMapping("/{id}/reapply")
    @PreAuthorize("isAuthenticated()")
    public R<MicroSpecialtyEnrollmentVO> reapply(@PathVariable Long id) {
        MicroSpecialtyEnrollmentVO vo = enrollmentService.reapply(id);
        return R.ok(vo);
    }

    /** 我的修读列表 */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<MicroSpecialtyEnrollmentVO>> getMyEnrollments() {
        List<MicroSpecialtyEnrollmentVO> list = enrollmentService.getMyEnrollments();
        return R.ok(list);
    }

    /** 手动颁发证书 */
    @PostMapping("/{id}/issue-certificate")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> issueCertificate(@PathVariable Long id) {
        enrollmentService.issueCertificate(id);
        return R.ok();
    }
}
