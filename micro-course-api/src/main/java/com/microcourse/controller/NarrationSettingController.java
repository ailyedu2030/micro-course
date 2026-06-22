package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.narration.NarrationSettingRequest;
import com.microcourse.dto.narration.NarrationSettingVO;
import com.microcourse.service.NarrationSettingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/narration-settings")
public class NarrationSettingController {

    private final NarrationSettingService narrationSettingService;

    public NarrationSettingController(NarrationSettingService narrationSettingService) {
        this.narrationSettingService = narrationSettingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<NarrationSettingVO> get(@PathVariable Long courseId) {
        return R.ok(narrationSettingService.getByCourseId(courseId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<NarrationSettingVO> update(@PathVariable Long courseId,
                                         @Valid @RequestBody NarrationSettingRequest request) {
        return R.ok(narrationSettingService.save(courseId, request));
    }
}
