package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.hermes.HermesCourseDetailVO;
import com.microcourse.dto.hermes.HermesCourseListVO;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.HermesCourseMappingRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.HermesCourseSyncService;
import com.microcourse.service.HermesCourseSyncService.HermesSyncResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/hermes/webhook")
public class HermesWebhookController {

    private static final Logger log = LoggerFactory.getLogger(HermesWebhookController.class);

    private final HermesCourseSyncService syncService;
    private final UserRepository userRepository;
    private final HermesCourseMappingRepository mappingRepository;
    private final CourseSectionRepository sectionRepository;
    private final SlideService slideService;

    public HermesWebhookController(HermesCourseSyncService syncService,
                                   UserRepository userRepository,
                                   HermesCourseMappingRepository mappingRepository,
                                   CourseSectionRepository sectionRepository,
                                   SlideService slideService) {
        this.syncService = syncService;
        this.userRepository = userRepository;
        this.mappingRepository = mappingRepository;
        this.sectionRepository = sectionRepository;
        this.slideService = slideService;
    }

    private User authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[HermesWebhook] Missing X-API-Key header");
            throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
        }
        Optional<User> callerOpt = userRepository.findByApiKey(apiKey);
        if (callerOpt.isEmpty()) {
            log.warn("[HermesWebhook] API key not found or user inactive");
            throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
        }
        return callerOpt.get();
    }

    @PostMapping("/courses")
    public R<HermesSyncResult> receiveCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              @Valid @RequestBody HermesWebhookRequest request) {
        User caller = authenticate(apiKey);
        HermesSyncResult result = syncService.upsertCourse(request, caller.getId());
        log.info("[HermesSync] userId={} username={} hermesCourseId={} action={}",
                caller.getId(), caller.getUsername(), request.getHermesCourseId(), result.getAction());
        return R.ok(result);
    }

    @GetMapping("/courses")
    public R<List<HermesCourseListVO>> listCourses(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        User caller = authenticate(apiKey);
        List<HermesCourseListVO> courses = syncService.listCoursesByTeacher(caller.getId());
        return R.ok(courses);
    }

    @GetMapping("/courses/{hermesCourseId}")
    public R<HermesCourseDetailVO> getCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              @PathVariable String hermesCourseId) {
        User caller = authenticate(apiKey);
        HermesCourseDetailVO course = syncService.getCourseDetail(hermesCourseId, caller.getId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return R.ok(course);
    }

    @PostMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slide")
    public R<?> uploadSlide(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                            @PathVariable String hermesCourseId,
                            @PathVariable Long lessonId,
                            @RequestParam("file") MultipartFile file) {
        User caller = authenticate(apiKey);

        HermesCourseMapping mapping = mappingRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HermesCourseMapping>()
                        .eq(HermesCourseMapping::getHermesCourseId, hermesCourseId));
        if (mapping == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        Long courseId = mapping.getCourseId();

        // 按 lessonId(实际是 section_id)查找
        Long chapterId = lessonId;
        CourseSection sec = sectionRepository.selectById(lessonId);
        if (sec != null) {
            chapterId = sec.getChapterId();
            lessonId = sec.getId();
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件名不能为空");
        }
        String lower = filename.toLowerCase();
        boolean isHtml = lower.endsWith(".html") || lower.endsWith(".htm");
        boolean isPptx = lower.endsWith(".pptx");
        if (!isHtml && !isPptx) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 .pptx / .html / .htm 文件");
        }
        long maxSize = isHtml ? 5 * 1024 * 1024 : 50 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    isHtml ? "HTML 文件大小不能超过 5MB" : "文件大小不能超过 50MB");
        }

        try {
            // 设置 SecurityContext，让 slideService 权限检查通过
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    caller.getId(), null,
                    java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            Long effectiveId = lessonId != null ? lessonId : chapterId;
            Object resp;
            if (isHtml) {
                try { slideService.deleteSlide(courseId, effectiveId); } catch (Exception ignored) {}
                resp = slideService.uploadHtmlFile(courseId, file, chapterId);
            } else {
                resp = slideService.upload(courseId, filename, file.getBytes(), chapterId);
            }
            return R.ok(resp);
        } catch (Exception e) {
            log.error("[HermesWebhook] Slide upload failed: hermesCourseId={}, lessonId={}",
                    hermesCourseId, lessonId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "PPT 上传失败: " + e.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @PostMapping("/courses/{hermesCourseId}/scripts")
    public R<?> batchPushScripts(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                 @PathVariable String hermesCourseId,
                                 @RequestBody java.util.Map<String, Object> body) {
        User caller = authenticate(apiKey);

        HermesCourseMapping mapping = mappingRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HermesCourseMapping>()
                        .eq(HermesCourseMapping::getHermesCourseId, hermesCourseId));
        if (mapping == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        Long courseId = mapping.getCourseId();

        String fullScript = (String) body.get("scriptContent");
        if (fullScript == null || fullScript.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "scriptContent 不能为空");
        }

        try {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    caller.getId(), null,
                    java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            java.util.List<com.microcourse.plugin.interactive.dto.SlidePageVO> pages =
                    slideService.getPages(courseId, null);
            if (pages == null || pages.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先上传课件");
            }
            int pageCount = pages.size();
            int chunkSize = Math.max(1, fullScript.length() / pageCount);
            int updated = 0;

            for (int i = 0; i < pageCount; i++) {
                int start = i * chunkSize;
                int end = (i == pageCount - 1) ? fullScript.length() : (i + 1) * chunkSize;
                String pageScript = fullScript.substring(start, end).trim();

                com.microcourse.plugin.interactive.dto.SlidePageVO p = pages.get(i);
                java.util.Map<String, Object> pageBody = new java.util.HashMap<>();
                pageBody.put("narrationScript", pageScript);
                if (p.getLessonId() != null) { pageBody.put("_lessonId", p.getLessonId()); }
                else if (p.getChapterId() != null) { pageBody.put("_chapterId", p.getChapterId()); }
                slideService.updatePage(courseId, p.getPageNumber(), pageBody);
                updated++;
            }

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("updated", updated);
            result.put("totalPages", pageCount);
            log.info("[HermesWebhook] Scripts pushed: courseId={}, pages={}, chars={}",
                    courseId, pageCount, fullScript.length());
            return R.ok(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[HermesWebhook] Script push failed: hermesCourseId={}", hermesCourseId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "讲述稿推送失败: " + e.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}