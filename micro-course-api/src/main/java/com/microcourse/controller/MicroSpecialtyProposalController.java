package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.RejectProposalRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyProposalRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.service.MicroSpecialtyProposalService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微专业申报 Controller。
 * 职责：教师申报提交 + 审批 + 撤回 + 重提。
 */
@RestController
@RequestMapping("/api/micro-specialty-proposals")
@Validated
public class MicroSpecialtyProposalController {

    private final MicroSpecialtyProposalService proposalService;

    public MicroSpecialtyProposalController(MicroSpecialtyProposalService proposalService) {
        this.proposalService = proposalService;
    }

    /** 教师提交申报 */
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public R<Long> submitProposal(@Valid @RequestBody MicroSpecialtyProposalRequest request) {
        Long proposalId = proposalService.submitProposal(request);
        return R.ok(proposalId);
    }

    /** 我的申报列表 */
    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    public R<PageResult<?>> getMyProposals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<?> result = proposalService.getMyProposals(page, size);
        return R.ok(result);
    }

    /** 所有待审申报 */
    @GetMapping
    @PreAuthorize("hasAnyRole('ACADEMIC', 'ADMIN')")
    public R<PageResult<?>> listAllPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageResult<?> result = proposalService.getAllPendingProposals(page, size, status);
        return R.ok(result);
    }

    /**
     * 批准申报 → 创建 DRAFT + LEAD INVITED。
     * Phase 15 增强：统一使用 approveAndCreateSpecialty，同时处理
     * 普通申报和 storage 类型申报（DRAFT→APPROVED + 创建微专业）。
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ACADEMIC', 'ADMIN')")
    public R<Void> approveProposal(@PathVariable Long id,
                                    @RequestParam(required = false) String comment) {
        proposalService.approveAndCreateSpecialty(id, SecurityUtil.getCurrentUserId());
        return R.ok();
    }

    /**
     * 驳回申报。
     * Phase 15 增强：Service 层已扩展 rejectProposal 以接受 storage 类型
     * proposal 的 DRAFT 状态，支持 DRAFT→REJECTED 转换。
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ACADEMIC', 'ADMIN')")
    public R<Void> rejectProposal(@PathVariable Long id, @RequestBody RejectProposalRequest request) {
        proposalService.rejectProposal(id, request.getReason());
        return R.ok();
    }

    /** 撤回申报 */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> withdrawProposal(@PathVariable Long id) {
        proposalService.withdrawProposal(id);
        return R.ok();
    }

    /** 重提申报 */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> resubmitProposal(@PathVariable Long id,
                                     @Valid @RequestBody MicroSpecialtyProposalRequest request) {
        proposalService.resubmitProposal(id, request);
        return R.ok();
    }

    /** 获取申报详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ACADEMIC','ADMIN')")  // P1-C-4 修复：增加 ACADEMIC 权限
    public R<?> getProposal(@PathVariable Long id) {
        return R.ok(proposalService.getProposal(id));
    }

    /** 编辑申报 (仅 WITHDRAWN 状态可编辑) */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> updateProposal(@PathVariable Long id,
                                   @Valid @RequestBody MicroSpecialtyProposalRequest request) {
        proposalService.updateProposal(id, request);
        return R.ok();
    }

    /** 删除申报 (仅 WITHDRAWN 状态可删除) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> deleteProposal(@PathVariable Long id) {
        proposalService.deleteProposal(id);
        return R.ok();
    }
}
