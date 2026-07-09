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
            com.microcourse.plugin.interactive.dto.SlideUploadResponse resp =
                    slideService.upload(courseId, filename, file.getBytes(), chapterId);
            return R.ok(resp);
        } catch (Exception e) {
            log.error("[HermesWebhook] Slide upload failed: hermesCourseId={}, lessonSortOrder={}",
                    hermesCourseId, lessonSortOrder, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "PPT 上传失败: " + e.getMessage());
        }
    }
}