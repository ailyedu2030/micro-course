package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.MicroSpecialtyInviteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微专业教师邀请 Controller。
 * 职责：待处理邀请 + 接受/拒绝 + 退出团队 + 跨学院审批 + 重新邀请。
 */
@RestController
@RequestMapping("/api/micro-specialty-teachers")
@Validated
public class MicroSpecialtyTeacherController {

    private final MicroSpecialtyInviteService inviteService;

    public MicroSpecialtyTeacherController(MicroSpecialtyInviteService inviteService) {
        this.inviteService = inviteService;
    }

    /** 我的待处理邀请列表 */
    @GetMapping("/pending-invites")
    @PreAuthorize("hasRole('TEACHER')")
    public R<PageResult<?>> getPendingInvites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<?> result = inviteService.getPendingInvites(page, size);
        return R.ok(result);
    }

    /** 接受邀请 → ACTIVE 或 PENDING_ACADEMIC */
    @PostMapping("/{inviteId}/accept")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> acceptInvite(@PathVariable Long inviteId) {
        inviteService.acceptInvite(inviteId);
        return R.ok();
    }

    /** 拒绝邀请 → DECLINED */
    @PostMapping("/{inviteId}/decline")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> declineInvite(@PathVariable Long inviteId) {
        inviteService.declineInvite(inviteId);
        return R.ok();
    }

    /** 主动退出团队 → REMOVED */
    @PostMapping("/{msId}/leave")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> leaveTeam(@PathVariable Long msId) {
        inviteService.leaveTeam(msId);
        return R.ok();
    }

    /** 跨学院审批（ACADEMIC） */
    @PostMapping("/{inviteId}/review-cross-dept")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> reviewCrossDept(@PathVariable Long inviteId,
                                    @RequestBody Map<String, Object> body) {
        boolean approve = "approve".equals(body.get("action"))
                || (body.get("approve") instanceof Boolean && (Boolean) body.get("approve"));
        String reason = body.get("reason") instanceof String
                ? (String) body.get("reason") : "";
        inviteService.reviewCrossDept(inviteId, approve, reason);
        return R.ok();
    }

    /** 重新邀请 */
    @PostMapping("/{msId}/reinvite")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")
    public R<Void> reinviteTeacher(@PathVariable Long msId,
                                    @RequestBody Map<String, Object> body) {
        Long teacherId = body.get("teacherId") != null
                ? ((Number) body.get("teacherId")).longValue()
                : null;
        String role = body.get("role") instanceof String
                ? (String) body.get("role") : null;
        String responsibility = body.get("responsibility") instanceof String
                ? (String) body.get("responsibility") : null;
        Long courseId = body.get("courseId") != null
                ? ((Number) body.get("courseId")).longValue()
                : null;
        inviteService.reinviteTeacher(msId, teacherId, role, responsibility, courseId);
        return R.ok();
    }
}
