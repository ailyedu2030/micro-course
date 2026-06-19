package com.microcourse.controller;

import com.microcourse.dto.CheckInVO;
import com.microcourse.dto.R;
import com.microcourse.service.CheckInService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/check-ins")
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<CheckInVO> checkIn() {
        Long userId = getCurrentUserId();
        CheckInVO vo = checkInService.checkIn(userId);
        return R.ok(vo);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<CheckInVO>> getMyCheckIns(
            @RequestParam(defaultValue = "30") int days) {
        Long userId = getCurrentUserId();
        List<CheckInVO> list = checkInService.getMyCheckIns(userId, days);
        return R.ok(list);
    }

    @GetMapping("/streak")
    @PreAuthorize("isAuthenticated()")
    public R<Integer> getStreak() {
        Long userId = getCurrentUserId();
        int streak = checkInService.getStreak(userId);
        return R.ok(streak);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.TOKEN_INVALID);
    }
}