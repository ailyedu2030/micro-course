package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.service.NarrationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/courses/{courseId}/slides")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class NarrationController {

    private final NarrationService narrationService;

    public NarrationController(NarrationService narrationService) {
        this.narrationService = narrationService;
    }

    @PostMapping("/pages/{pageNumber}/narration/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> generate(@PathVariable Long courseId,
                                    @PathVariable Integer pageNumber,
                                    @RequestParam(required = false) Long sectionId) {
        return R.ok(narrationService.generate(courseId, pageNumber, sectionId));
    }

    @PutMapping("/pages/{pageNumber}/narration")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> updateScript(@PathVariable Long courseId,
                                        @PathVariable Integer pageNumber,
                                        @RequestParam(required = false) Long sectionId,
                                        @RequestBody Map<String, String> body) {
        String script = body.getOrDefault("narrationScript", "");
        return R.ok(narrationService.updateScript(courseId, pageNumber, sectionId, script));
    }

    @PostMapping("/narrations/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> generateAll(@PathVariable Long courseId) {
        narrationService.generateAll(courseId);
        return R.ok();
    }
}
