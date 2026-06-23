package com.microcourse.controller;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.microSpecialty.MicroSpecialtyProposalRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.service.MicroSpecialtyProposalService;
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
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<PageResult<?>> listAllPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageResult<?> result = proposalService.getAllPendingProposals(page, size, status);
        return R.ok(result);
    }

    /** 批准申报 → 创建 DRAFT + LEAD INVITED */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<MicroSpecialtyVO> approveProposal(@PathVariable Long id) {
        MicroSpecialtyVO vo = proposalService.approveProposal(id);
        return R.ok(vo);
    }

    /** 驳回申报 */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> rejectProposal(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "");
        proposalService.rejectProposal(id, reason);
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
}
