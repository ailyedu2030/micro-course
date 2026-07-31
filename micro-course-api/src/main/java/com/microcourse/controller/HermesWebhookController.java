package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.hermes.BatchPushScriptsRequest;
import com.microcourse.dto.hermes.HermesChapterVO;
import com.microcourse.dto.hermes.HermesCourseDetailVO;
import com.microcourse.dto.hermes.HermesCourseListVO;
import com.microcourse.dto.hermes.HermesSectionVO;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.dto.hermes.NarrationUpdateRequest;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.HermesCourseSyncService;
import com.microcourse.service.HermesCourseSyncService.HermesSyncResult;
import com.microcourse.service.HermesWebhookCoursewareService;
import com.microcourse.service.HermesWebhookManagementService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/hermes/webhook")
public class HermesWebhookController {

    private static final Logger log = LoggerFactory.getLogger(HermesWebhookController.class);

    private final HermesCourseSyncService syncService;
    private final HermesWebhookCoursewareService coursewareService;
    private final HermesWebhookManagementService managementService;

    public HermesWebhookController(HermesCourseSyncService syncService,
                                   HermesWebhookCoursewareService coursewareService,
                                   HermesWebhookManagementService managementService) {
        this.syncService = syncService;
        this.coursewareService = coursewareService;
        this.managementService = managementService;
    }

    @PostMapping("/courses")
    public R<HermesSyncResult> receiveCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              @Valid @RequestBody HermesWebhookRequest request) {
        User caller = managementService.authenticate(apiKey);
        HermesSyncResult result = syncService.upsertCourse(request, caller.getId());
        log.info("[HermesSync] userId={} username={} hermesCourseId={} action={}",
                caller.getId(), caller.getUsername(), request.getHermesCourseId(), result.getAction());
        return R.ok(result);
    }

    @GetMapping("/courses")
    public R<List<HermesCourseListVO>> listCourses(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        return R.ok(managementService.listAllCourses(apiKey));
    }

    @GetMapping("/courses/{hermesCourseId}")
    public R<HermesCourseDetailVO> getCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              @PathVariable String hermesCourseId) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseDetailVO course = syncService.getCourseDetail(hermesCourseId, caller.getId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return R.ok(course);
    }

    @GetMapping("/courses/{hermesCourseId}/sections")
    public R<List<HermesSectionVO>> listSections(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                @PathVariable String hermesCourseId) {
        return R.ok(managementService.listSections(hermesCourseId, apiKey));
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/courses/{hermesCourseId}/sections")
    public R<HermesSectionVO> createSection(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          @PathVariable String hermesCourseId,
                                          @Valid @RequestBody CourseSection body) {
        return R.ok(managementService.createSection(hermesCourseId, apiKey, body));
    }

    @Transactional(rollbackFor = Exception.class)
    @PatchMapping("/courses/{hermesCourseId}/sections/{sectionId}")
    public R<HermesSectionVO> updateSection(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          @PathVariable String hermesCourseId,
                                          @PathVariable Long sectionId,
                                          @Valid @RequestBody CourseSection body) {
        return R.ok(managementService.updateSection(hermesCourseId, apiKey, sectionId, body));
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}/sections/{sectionId}")
    public R<Void> deleteSection(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                 @PathVariable String hermesCourseId,
                                 @PathVariable Long sectionId) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        coursewareService.deleteSectionCascade(mapping.getCourseId(), sectionId);
        return R.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @PatchMapping("/courses/{hermesCourseId}/chapters/{chapterId}")
    public R<HermesChapterVO> updateChapter(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          @PathVariable String hermesCourseId,
                                          @PathVariable Long chapterId,
                                          @Valid @RequestBody CourseChapter body) {
        return R.ok(managementService.updateChapter(hermesCourseId, apiKey, chapterId, body));
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}/chapters/{chapterId}")
    public R<Void> deleteChapter(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                 @PathVariable String hermesCourseId,
                                 @PathVariable Long chapterId) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        coursewareService.deleteChapterCascade(mapping.getCourseId(), chapterId);
        return R.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slide")
    public R<?> uploadSlide(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                            @PathVariable String hermesCourseId,
                            @PathVariable Long lessonId,
                            @RequestParam("file") MultipartFile file) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        return R.ok(coursewareService.uploadSlide(mapping.getCourseId(), lessonId, file));
    }

    @GetMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slides/pages")
    public R<List<com.microcourse.plugin.interactive.dto.SlidePageVO>> listSlidePages(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId,
            @PathVariable Long lessonId) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        return R.ok(coursewareService.listSlidePages(mapping.getCourseId(), lessonId));
    }

    @Transactional(rollbackFor = Exception.class)
    @PatchMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slides/pages/{pageNumber}")
    public R<com.microcourse.plugin.interactive.dto.SlidePageVO> updateSlidePageNarration(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId,
            @PathVariable Long lessonId,
            @PathVariable Integer pageNumber,
            @Valid @RequestBody NarrationUpdateRequest req) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        if (req.getNarrationScript() != null) {
            body.put("narrationScript", req.getNarrationScript());
        }
        return R.ok(coursewareService.updateSlidePageNarration(mapping.getCourseId(), lessonId, pageNumber, body));
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slides/pages/{pageNumber}")
    public R<Void> deleteSlidePage(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId,
            @PathVariable Long lessonId,
            @PathVariable Integer pageNumber) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        coursewareService.deleteSlidePage(mapping.getCourseId(), lessonId, pageNumber);
        return R.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}")
    public R<Void> deleteCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                @PathVariable String hermesCourseId) {
        managementService.deleteCourse(hermesCourseId, apiKey);
        return R.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/by-id/{courseId}")
    public R<Void> deleteCourseById(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                    @PathVariable Long courseId) {
        managementService.deleteCourseById(courseId, apiKey);
        return R.ok();
    }

    @GetMapping("/courses/{hermesCourseId}/slides")
    public R<List<com.microcourse.plugin.interactive.dto.SlideVO>> listSlides(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        return R.ok(coursewareService.listSlides(mapping.getCourseId()));
    }

    @GetMapping("/courses/all")
    public R<List<HermesCourseListVO>> listAllCourses(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        return R.ok(managementService.listAllCourses(apiKey));
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/api-key/refresh")
    public R<String> refreshApiKey(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        return R.ok(managementService.refreshApiKey(apiKey));
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/courses/{hermesCourseId}/scripts")
    public R<?> batchPushScripts(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                 @PathVariable String hermesCourseId,
                                 @Valid @RequestBody BatchPushScriptsRequest req) {
        User caller = managementService.authenticate(apiKey);
        HermesCourseMapping mapping = managementService.resolveMapping(hermesCourseId);
        managementService.verifyCourseOwnership(caller, mapping);
        String scriptContent = req.getScriptContent();
        if (scriptContent == null || scriptContent.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "scriptContent 必须为字符串类型");
        }
        return R.ok(coursewareService.batchPushScripts(
                mapping.getCourseId(),
                req.getSectionId(),
                req.getChapterId(),
                scriptContent));
    }
}
