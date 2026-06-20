package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.narration.NarrationSettingRequest;
import com.microcourse.dto.narration.NarrationSettingVO;
import com.microcourse.entity.NarrationSetting;
import com.microcourse.service.NarrationSettingService;
import com.microcourse.service.impl.NarrationSettingServiceImpl;
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
        NarrationSetting setting = narrationSettingService.getByCourseId(courseId);
        return R.ok(NarrationSettingServiceImpl.toVO(setting));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<NarrationSettingVO> update(@PathVariable Long courseId,
                                         @Valid @RequestBody NarrationSettingRequest request) {
        NarrationSetting setting = new NarrationSetting();
        setting.setCourseId(courseId);
        setting.setSpeakerIdentity(request.getSpeakerIdentity());
        setting.setTargetAudience(request.getTargetAudience());
        setting.setSpeakingStyle(request.getSpeakingStyle());
        setting.setTotalDurationMinutes(request.getTotalDurationMinutes());
        NarrationSetting saved = narrationSettingService.save(courseId, setting);
        return R.ok(NarrationSettingServiceImpl.toVO(saved));
    }
}
