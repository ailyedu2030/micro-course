package com.microcourse.controller;

import com.microcourse.dto.CreateReportRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.ReviewReportVO;
import com.microcourse.dto.ReviewReportActionRequest;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 举报处理 Controller
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * POST /api/reports — 提交举报(任何人)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<Void> create(@Valid @RequestBody CreateReportRequest req) {
        Long userId = getCurrentUserId();
        reportService.create(userId, req);
        return R.ok();
    }

    /**
     * GET /api/reports/admin — 管理员查看举报列表
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<ReviewReportVO>> adminPage(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size,
            @RequestParam(required = false) Integer status) {
        PageResult<ReviewReportVO> result = reportService.pageByAdmin(page, size, status);
        return R.ok(result);
    }

    /**
     * POST /api/reports/{id}/review — 管理员审核
     */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewReportActionRequest req) {
        Long reviewerId = getCurrentUserId();
        reportService.review(id, req, reviewerId);
        return R.ok();
    }

    /** 兼容 Long / String / Number 类型 principal */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Number) return ((Number) principal).longValue();
        if (principal instanceof String str) {
            try { return Long.parseLong(str); } catch (NumberFormatException ignored) { /* fall through */ }
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }
}
