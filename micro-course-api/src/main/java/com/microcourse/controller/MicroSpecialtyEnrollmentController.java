package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public R<MicroSpecialtyEnrollmentVO> apply(@RequestBody Map<String, Object> body) {
        Long microSpecialtyId = body.get("microSpecialtyId") != null
                ? ((Number) body.get("microSpecialtyId")).longValue()
                : null;
        MicroSpecialtyEnrollmentVO vo = enrollmentService.apply(microSpecialtyId);
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
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        enrollmentService.reject(id, reason);
        return R.ok();
    }

    /** 班级批量导入 → APPROVED */
    @PostMapping("/class-import")
    @PreAuthorize("hasAnyRole('ACADEMIC','ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public R<Integer> classImport(@RequestBody Map<String, Object> body) {
        Long microSpecialtyId = body.get("microSpecialtyId") != null
                ? ((Number) body.get("microSpecialtyId")).longValue()
                : null;
        // Parse classIds from request body
        Object classIdsObj = body.get("classIds");
        List<Long> classIds = new ArrayList<>();
        if (classIdsObj instanceof List) {
            for (Object id : (List<?>) classIdsObj) {
                classIds.add(Long.valueOf(id.toString()));
            }
        }
        // Support single classId as fallback
        if (classIds.isEmpty() && body.containsKey("classId")) {
            Object singleId = body.get("classId");
            if (singleId instanceof Number) {
                classIds.add(((Number) singleId).longValue());
            } else if (singleId != null) {
                classIds.add(Long.valueOf(singleId.toString()));
            }
        }
        // Process each classId in a loop
        int totalCount = 0;
        for (Long classId : classIds) {
            totalCount += enrollmentService.classImport(microSpecialtyId, classId);
        }
        return R.ok(totalCount);
    }

    /** 退出修读 */
    @PostMapping("/{id}/drop")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<Void> drop(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean cascade = body.get("cascade") instanceof Boolean
                ? (Boolean) body.get("cascade") : false;
        String reason = body.get("reason") instanceof String
                ? (String) body.get("reason") : "";
        enrollmentService.drop(id, cascade, reason);
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
