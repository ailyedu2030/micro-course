package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.hermes.HermesCourseDetailVO;
import com.microcourse.dto.hermes.HermesCourseListVO;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.Lesson;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.HermesCourseMappingRepository;
import com.microcourse.repository.LessonRepository;
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
    private final LessonRepository lessonRepository;
    private final SlideService slideService;

    public HermesWebhookController(HermesCourseSyncService syncService,
                                   UserRepository userRepository,
                                   HermesCourseMappingRepository mappingRepository,
                                   LessonRepository lessonRepository,
                                   SlideService slideService) {
        this.syncService = syncService;
        this.userRepository = userRepository;
        this.mappingRepository = mappingRepository;
        this.lessonRepository = lessonRepository;
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

    @PostMapping("/courses/{hermesCourseId}/lessons/{lessonSortOrder}/slide")
    public R<?> uploadSlide(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                            @PathVariable String hermesCourseId,
                            @PathVariable Integer lessonSortOrder,
                            @RequestParam("file") MultipartFile file) {
        User caller = authenticate(apiKey);

        HermesCourseMapping mapping = mappingRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HermesCourseMapping>()
                        .eq(HermesCourseMapping::getHermesCourseId, hermesCourseId));
        if (mapping == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        Long courseId = mapping.getCourseId();

        // 根据 lessonSortOrder 查找对应课时，获取 chapterId
        List<Lesson> lessons = lessonRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Lesson>()
                        .eq(Lesson::getCourseId, courseId)
                        .eq(Lesson::getSortOrder, lessonSortOrder));
        if (lessons.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "未找到排序号为 " + lessonSortOrder + " 的课时");
        }
        Long chapterId = lessons.get(0).getChapterId();

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pptx")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 .pptx 文件");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件大小不能超过 50MB");
        }

        try {
            // 设置 SecurityContext，让 slideService 权限检查通过
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    caller.getId(), null,
                    java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            com.microcourse.plugin.interactive.dto.SlideUploadResponse resp =
                    slideService.upload(courseId, filename, file.getBytes(), chapterId);
            return R.ok(resp);
        } catch (Exception e) {
            log.error("[HermesWebhook] Slide upload failed: hermesCourseId={}, lessonSortOrder={}",
                    hermesCourseId, lessonSortOrder, e);
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

            com.microcourse.plugin.interactive.dto.SlideVO slide = slideService.getByCourseId(courseId);
            if (slide == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先上传 PPT");
            }
            if (slide.getTotalPages() == null || slide.getTotalPages() == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "PPT 尚未渲染完成，请稍后再试");
            }

            java.util.List<com.microcourse.plugin.interactive.dto.SlidePageVO> pages =
                    slideService.getPages(courseId, null);
            if (pages == null || pages.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "PPT 尚未渲染完成，请稍后再试");
            }

            int pageCount = pages.size();
            int chunkSize = Math.max(1, fullScript.length() / pageCount);
            int updated = 0;

            for (int i = 0; i < pageCount; i++) {
                int start = i * chunkSize;
                int end = (i == pageCount - 1) ? fullScript.length() : (i + 1) * chunkSize;
                String pageScript = fullScript.substring(start, end).trim();

                int pageNumber = pages.get(i).getPageNumber();
                java.util.Map<String, Object> pageBody = new java.util.HashMap<>();
                pageBody.put("narrationScript", pageScript);
                slideService.updatePage(courseId, pageNumber, pageBody);
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