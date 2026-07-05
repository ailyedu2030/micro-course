package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.InviteTeacherRequest;
import com.microcourse.dto.TeacherActionRequest;
import com.microcourse.dto.invite.AcceptWithChaptersRequest;
import com.microcourse.service.MicroSpecialtyInviteService;
import jakarta.validation.Valid;
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

    /** 我的待处理邀请列表（TEACHER） */
    @GetMapping("/pending-invites")
    @PreAuthorize("hasRole('TEACHER')")
    public R<PageResult<?>> getPendingInvites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageResult<?> result = inviteService.getPendingInvites(page, size, status);
        return R.ok(result);
    }

    /** 跨学院待审批邀请列表（ACADEMIC） */
    @GetMapping("/pending-cross-dept-invites")
    @PreAuthorize("hasAnyRole('ACADEMIC', 'ADMIN')")
    public R<PageResult<?>> getPendingCrossDeptInvites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<?> result = inviteService.getPendingCrossDeptInvites(page, size);
        return R.ok(result);
    }

    /** 接受邀请 → ACTIVE 或 PENDING_ACADEMIC */
    @PostMapping("/{inviteId}/accept")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> acceptInvite(@PathVariable Long inviteId) {
        inviteService.acceptInvite(inviteId);
        return R.ok();
    }

    /** 接受邀请(含章节来源决策) → ACTIVE */
    @PostMapping("/{inviteId}/accept-with-chapters")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> acceptWithChapters(@PathVariable Long inviteId,
                                       @Valid @RequestBody AcceptWithChaptersRequest request) {
        inviteService.acceptWithChapters(inviteId, request);
        return R.ok();
    }

    /** 拒绝邀请 → DECLINED */
    @PostMapping("/{inviteId}/decline")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> declineInvite(@PathVariable Long inviteId) {
        inviteService.declineInvite(inviteId);
        return R.ok();
    }

    /** 主动退出团队 → REMOVED（§7.4 端点对齐 spec） */
    @PostMapping("/{inviteId}/leave")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> leaveTeam(@PathVariable Long inviteId) {
        inviteService.leaveTeam(inviteId);
        return R.ok();
    }

    /** 跨学院审批（ACADEMIC） */
    @PostMapping("/{inviteId}/review-cross-dept")
    @PreAuthorize("hasAnyRole('ACADEMIC', 'ADMIN')")
    public R<Void> reviewCrossDept(@PathVariable Long inviteId,
                                     @Valid @RequestBody TeacherActionRequest request) {
        boolean approve = "approve".equals(request.getAction());
        inviteService.reviewCrossDept(inviteId, approve, request.getReason());
        return R.ok();
    }

    /** 重新邀请（§7.4 端点对齐 spec：/{inviteId}/reinvite，复用 DECLINED/REMOVED 记录） */
    @PostMapping("/{inviteId}/reinvite")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> reinviteTeacher(@PathVariable Long inviteId,
                                     @Valid @RequestBody InviteTeacherRequest request) {
        inviteService.reinviteTeacher(inviteId, request.getRole(), request.getMessage(), request.getTeacherId());
        return R.ok();
    }
}
