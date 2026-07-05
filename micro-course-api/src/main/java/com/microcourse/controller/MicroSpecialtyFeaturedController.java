package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.FeaturedApplyRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyFeaturedApplyRequest;
import com.microcourse.service.MicroSpecialtyFeaturedService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微专业置顶/金标 Controller。
 * 职责：置顶申请/审批 + 金标管理。
 */
@RestController
@RequestMapping("/api/micro-specialties")
@Validated
public class MicroSpecialtyFeaturedController {

    private final MicroSpecialtyFeaturedService featuredService;

    public MicroSpecialtyFeaturedController(MicroSpecialtyFeaturedService featuredService) {
        this.featuredService = featuredService;
    }

    /** 申请置顶（LEAD）→ PENDING */
    @PostMapping("/{id}/apply-featured")
    @PreAuthorize("hasRole('TEACHER')")
    public R<Void> applyFeatured(@PathVariable Long id,
                                  @Valid @RequestBody MicroSpecialtyFeaturedApplyRequest request) {
        featuredService.applyFeatured(id, request.getReason());
        return R.ok();
    }

    /** 批准置顶（ACADEMIC）→ APPROVED */
    @PostMapping("/{id}/approve-featured")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> approveFeatured(@PathVariable Long id) {
        featuredService.approveFeatured(id);
        return R.ok();
    }

    /** 驳回置顶（ACADEMIC）→ REJECTED */
    @PostMapping("/{id}/reject-featured")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> rejectFeatured(@PathVariable Long id,
                                    @Valid @RequestBody FeaturedApplyRequest request) {
        featuredService.rejectFeatured(id, request.getReason());
        return R.ok();
    }

    /** 取消置顶（ACADEMIC）APPROVED → NONE */
    @PostMapping("/{id}/unset-featured")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> unsetFeatured(@PathVariable Long id) {
        featuredService.unsetFeatured(id);
        return R.ok();
    }

    /** 设置金标（ACADEMIC）：全校最多 2 个 */
    @PostMapping("/{id}/set-gold-featured")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> setGoldFeatured(@PathVariable Long id) {
        featuredService.setGoldFeatured(id);
        return R.ok();
    }

    /** 取消金标（ACADEMIC） */
    @PostMapping("/{id}/unset-gold-featured")
    @PreAuthorize("hasRole('ACADEMIC')")
    public R<Void> unsetGoldFeatured(@PathVariable Long id) {
        featuredService.unsetGoldFeatured(id);
        return R.ok();
    }
}
