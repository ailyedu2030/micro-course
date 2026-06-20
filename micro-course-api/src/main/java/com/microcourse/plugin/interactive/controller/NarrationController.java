package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.service.NarrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/courses/{courseId}/slides")
public class NarrationController {

    private final NarrationService narrationService;

    public NarrationController(NarrationService narrationService) {
        this.narrationService = narrationService;
    }

    @PostMapping("/pages/{pageNumber}/narration/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> generate(@PathVariable Long courseId,
                                    @PathVariable Integer pageNumber) {
        return R.ok(narrationService.generate(courseId, pageNumber));
    }

    @PutMapping("/pages/{pageNumber}/narration")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> updateScript(@PathVariable Long courseId,
                                        @PathVariable Integer pageNumber,
                                        @RequestBody Map<String, String> body) {
        String script = body.getOrDefault("narrationScript", "");
        return R.ok(narrationService.updateScript(courseId, pageNumber, script));
    }

    @PostMapping("/narrations/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> generateAll(@PathVariable Long courseId) {
        narrationService.generateAll(courseId);
        return R.ok();
    }
}
