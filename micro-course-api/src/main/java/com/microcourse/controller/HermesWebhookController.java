package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.hermes.HermesChapterVO;
import com.microcourse.dto.hermes.HermesCourseDetailVO;
import com.microcourse.dto.hermes.HermesCourseListVO;
import com.microcourse.dto.hermes.HermesSectionVO;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.entity.CourseChapter;
import com.microcourse.enums.UserRole;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseSectionRepository sectionRepository;
    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final SlideService slideService;

    public HermesWebhookController(HermesCourseSyncService syncService,
                                   UserRepository userRepository,
                                   HermesCourseMappingRepository mappingRepository,
                                   CourseRepository courseRepository,
                                   CourseCategoryRepository categoryRepository,
                                   CourseChapterRepository chapterRepository,
                                   CourseSectionRepository sectionRepository,
                                   CourseSlideMapper courseSlideMapper,
                                   SlidePageMapper slidePageMapper,
                                   SlideService slideService) {
        this.syncService = syncService;
        this.userRepository = userRepository;
        this.mappingRepository = mappingRepository;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
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
        User caller = callerOpt.get();
        UserRole role = caller.getRole();
        if (role != UserRole.TEACHER && role != UserRole.ADMIN) {
            log.warn("[HermesWebhook] API key belongs to non-teacher role: userId={}, role={}", caller.getId(), role);
            throw new BusinessException(ErrorCode.NO_PERMISSION, "API Key 仅限教师或管理员使用");
        }
        return caller;
    }

    private HermesCourseMapping resolveMapping(String hermesCourseId) {
        HermesCourseMapping mapping = mappingRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HermesCourseMapping>()
                        .eq(HermesCourseMapping::getHermesCourseId, hermesCourseId));
        if (mapping == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND,
                    "Hermes 课程映射不存在，请先调用 POST /courses 创建课程: hermesCourseId=" + hermesCourseId);
        }
        return mapping;
    }

    /**
     * 验证调用者拥有该课程（ADMIN 或 course.owner == caller）。
     * P1-I-1 修复：所有子资源端点（章节/课时 CRUD）必须先通过此检查。
     */
    private void verifyCourseOwnership(User caller, HermesCourseMapping mapping) {
        if (caller.getRole() == UserRole.ADMIN) return;
        com.microcourse.entity.Course course = courseRepository.selectById(mapping.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!caller.getId().equals(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程");
        }
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

    /**
     * 课时独立 CRUD：列出章节下的所有课时
     */
    @GetMapping("/courses/{hermesCourseId}/sections")
    public R<List<HermesSectionVO>> listSections(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                @PathVariable String hermesCourseId) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        List<CourseSection> sections = sectionRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getCourseId, mapping.getCourseId()));
        List<HermesSectionVO> result = sections.stream().map(s -> new HermesSectionVO(
                s.getId(), s.getChapterId(), s.getTitle(), s.getSectionType(),
                s.getSortOrder(), s.getDuration(), s.getVisible(),
                s.getDescription(), s.getScriptContent(), s.getContentUrl(),
                s.getCreatedAt(), s.getUpdatedAt()
        )).toList();
        return R.ok(result);
    }

    /**
     * 课时独立 CRUD：创建课时
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/courses/{hermesCourseId}/sections")
    public R<HermesSectionVO> createSection(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          @PathVariable String hermesCourseId,
                                          @Valid @RequestBody CourseSection body) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        if (body.getChapterId() != null) {
            CourseChapter ch = chapterRepository.selectById(body.getChapterId());
            if (ch == null || !ch.getCourseId().equals(mapping.getCourseId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节 ID 不属于该课程");
            }
        }
        body.setId(null);
        body.setCourseId(mapping.getCourseId());
        var now = java.time.LocalDateTime.now();
        body.setCreatedAt(now);
        body.setUpdatedAt(now);
        if (body.getVersion() == null) body.setVersion(1);
        if (body.getVisible() == null) body.setVisible(true);
        sectionRepository.insert(body);
        HermesSectionVO vo = new HermesSectionVO(
                body.getId(), body.getChapterId(), body.getTitle(), body.getSectionType(),
                body.getSortOrder(), body.getDuration(), body.getVisible(),
                body.getDescription(), body.getScriptContent(), body.getContentUrl(),
                body.getCreatedAt(), body.getUpdatedAt());
        return R.ok(vo);
    }

    /**
     * 课时独立 CRUD：更新课时
     */
    @Transactional(rollbackFor = Exception.class)
    @PatchMapping("/courses/{hermesCourseId}/sections/{sectionId}")
    public R<HermesSectionVO> updateSection(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          @PathVariable String hermesCourseId,
                                          @PathVariable Long sectionId,
                                          @Valid @RequestBody CourseSection body) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        CourseSection existing = sectionRepository.selectById(sectionId);
        if (existing == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        if (!existing.getCourseId().equals(mapping.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "课时不属于该课程");
        }
        if (body.getTitle() != null) existing.setTitle(body.getTitle());
        if (body.getSectionType() != null) existing.setSectionType(body.getSectionType());
        if (body.getSortOrder() != null) existing.setSortOrder(body.getSortOrder());
        if (body.getDuration() != null) existing.setDuration(body.getDuration());
        if (body.getVisible() != null) existing.setVisible(body.getVisible());
        if (body.getScriptContent() != null) existing.setScriptContent(body.getScriptContent());
        if (body.getDescription() != null) existing.setDescription(body.getDescription());
        if (body.getContentUrl() != null) existing.setContentUrl(body.getContentUrl());
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        if (sectionRepository.updateById(existing) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "课时已被其他人修改，请刷新后重试");
        }
        HermesSectionVO vo = new HermesSectionVO(
                existing.getId(), existing.getChapterId(), existing.getTitle(), existing.getSectionType(),
                existing.getSortOrder(), existing.getDuration(), existing.getVisible(),
                existing.getDescription(), existing.getScriptContent(), existing.getContentUrl(),
                existing.getCreatedAt(), existing.getUpdatedAt());
        return R.ok(vo);
    }

    /**
     * 课时独立 CRUD：删除课时
     */
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}/sections/{sectionId}")
    public R<Void> deleteSection(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                 @PathVariable String hermesCourseId,
                                 @PathVariable Long sectionId) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        CourseSection existingDelete = sectionRepository.selectById(sectionId);
        if (existingDelete == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        if (!existingDelete.getCourseId().equals(mapping.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "课时不属于该课程");
        }
        var slideQw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseSlide>()
                .eq(CourseSlide::getSectionId, sectionId);
        List<CourseSlide> slides = courseSlideMapper.selectList(slideQw);
        Long courseId = mapping.getCourseId();
        for (CourseSlide slide : slides) {
            slidePageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SlidePage>()
                    .eq(SlidePage::getSlideId, slide.getId()));
            courseSlideMapper.deleteById(slide.getId());
            registerSlideCleanup(courseId, slide.getId());
        }
        sectionRepository.deleteById(sectionId);
        return R.ok();
    }

    private void registerSlideCleanup(Long courseId, Long slideId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    slideService.cleanupSlideFiles(courseId, slideId);
                }
            });
        } else {
            slideService.cleanupSlideFiles(courseId, slideId);
        }
    }

    /**
     * 章节独立 CRUD：更新章节
     */
    @Transactional(rollbackFor = Exception.class)
    @PatchMapping("/courses/{hermesCourseId}/chapters/{chapterId}")
    public R<HermesChapterVO> updateChapter(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                          @PathVariable String hermesCourseId,
                                          @PathVariable Long chapterId,
                                          @Valid @RequestBody CourseChapter body) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        CourseChapter existing = chapterRepository.selectById(chapterId);
        if (existing == null) throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        if (!existing.getCourseId().equals(mapping.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "章节不属于该课程");
        }
        if (body.getTitle() != null) existing.setTitle(body.getTitle());
        if (body.getSortOrder() != null) existing.setSortOrder(body.getSortOrder());
        if (body.getDescription() != null) existing.setDescription(body.getDescription());
        if (body.getDuration() != null) existing.setDuration(body.getDuration());
        if (body.getLearningObjectives() != null) existing.setLearningObjectives(body.getLearningObjectives());
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        if (chapterRepository.updateById(existing) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "章节已被其他人修改，请刷新后重试");
        }
        HermesChapterVO vo = new HermesChapterVO(
                existing.getId(), existing.getTitle(), existing.getDescription(),
                existing.getSortOrder(), existing.getDuration(), existing.getLearningObjectives(),
                existing.getCreatedAt(), existing.getUpdatedAt());
        return R.ok(vo);
    }

    /**
     * 章节独立 CRUD：删除章节（级联删除旗下所有课时和课件）
     */
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}/chapters/{chapterId}")
    public R<Void> deleteChapter(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                 @PathVariable String hermesCourseId,
                                 @PathVariable Long chapterId) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        CourseChapter existing = chapterRepository.selectById(chapterId);
        if (existing == null) throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        if (!existing.getCourseId().equals(mapping.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "章节不属于该课程");
        }
        cascadeDeleteSectionsByChapterId(mapping.getCourseId(), chapterId);
        chapterRepository.deleteById(chapterId);
        return R.ok();
    }

    /**
     * Hermes 上传课件。
     * URL 中的 {lessonId} 对应 course_sections.id（lessons 表已迁移到 course_sections）。
     * 保持 /lessons/ 路径前缀是为兼容 Hermes 外部 API 协议，不涉及数据库 lessons 表。
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slide")
    public R<?> uploadSlide(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                            @PathVariable String hermesCourseId,
                            @PathVariable Long lessonId,
                            @RequestParam("file") MultipartFile file) {
        User caller = authenticate(apiKey);

        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        Long courseId = mapping.getCourseId();

        // 按 lessonId(实际是 section_id)查找所属的 chapterId
        Long chapterId = null;
        CourseSection sec = sectionRepository.selectById(lessonId);
        if (sec != null) {
            chapterId = sec.getChapterId();
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

            Object resp;
            if (isHtml) {
                // UPSERT 按 (courseId, chapterId, sectionId) 匹配，不同 section 有独立 slide
                resp = slideService.uploadHtmlFile(courseId, file, chapterId, lessonId);
            } else {
                resp = slideService.upload(courseId, filename, file.getBytes(), chapterId, lessonId);
            }
            // content_url 已在 upload()/uploadHtmlFile() 内与上传同事务写入
            return R.ok(resp);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[HermesWebhook] Slide upload failed: hermesCourseId={}, lessonId={}",
                    hermesCourseId, lessonId, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    (isHtml ? "HTML" : "PPT") + " 上传失败: " + e.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 课件页面：列出某课时（section）下的所有 slide page
     */
    @GetMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slides/pages")
    public R<List<com.microcourse.plugin.interactive.dto.SlidePageVO>> listSlidePages(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId,
            @PathVariable Long lessonId) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        CourseSection sec = sectionRepository.selectById(lessonId);
        if (sec == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        if (!sec.getCourseId().equals(mapping.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "课时不属于该课程");
        }
        List<com.microcourse.plugin.interactive.dto.SlidePageVO> pages =
                slideService.getPages(mapping.getCourseId(), lessonId);
        return R.ok(pages);
    }

    /**
     * 课件页面：更新某个 page 的讲述稿（单页编辑）
     */
    @Transactional(rollbackFor = Exception.class)
    @PatchMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slides/pages/{pageNumber}")
    public R<com.microcourse.plugin.interactive.dto.SlidePageVO> updateSlidePageNarration(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId,
            @PathVariable Long lessonId,
            @PathVariable Integer pageNumber,
            @RequestBody java.util.Map<String, Object> body) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        CourseSection sec = sectionRepository.selectById(lessonId);
        if (sec == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        if (!sec.getCourseId().equals(mapping.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "课时不属于该课程");
        }
        if (body.containsKey("narrationScript")) {
            Object ns = body.get("narrationScript");
            if (ns instanceof String) {
                body.put("narrationScript", com.microcourse.util.XssSanitizer.sanitizePlainText((String) ns));
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "narrationScript 必须为字符串类型");
            }
        }
        body.put("_lessonId", lessonId);
        try {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    caller.getId(), null,
                    java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            com.microcourse.plugin.interactive.dto.SlidePageVO updated =
                    slideService.updatePage(mapping.getCourseId(), pageNumber, body);
            return R.ok(updated);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 课件页面：删除某个 page
     */
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}/lessons/{lessonId}/slides/pages/{pageNumber}")
    public R<Void> deleteSlidePage(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId,
            @PathVariable Long lessonId,
            @PathVariable Integer pageNumber) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        CourseSection sec = sectionRepository.selectById(lessonId);
        if (sec == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        if (!sec.getCourseId().equals(mapping.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "课时不属于该课程");
        }
        try {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    caller.getId(), null, java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            slideService.deletePage(mapping.getCourseId(), pageNumber, lessonId);
        } finally {
            SecurityContextHolder.clearContext();
        }
        return R.ok();
    }

    /**
     * 级联删除课程数据（内部方法，不依赖映射表）
     */
    private void cascadeDeleteCourse(Long courseId) {
        List<CourseSection> sections = sectionRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getCourseId, courseId));
        java.util.Set<Long> seenChapterIds = new java.util.HashSet<>();
        for (CourseSection sec : sections) {
            if (seenChapterIds.add(sec.getChapterId())) {
                cascadeDeleteSectionsByChapterId(courseId, sec.getChapterId());
            }
        }
        chapterRepository.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId));
        courseRepository.deleteById(courseId);
    }

    private void cascadeDeleteSectionsByChapterId(Long courseId, Long chapterId) {
        List<CourseSection> sections = sectionRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getChapterId, chapterId));
        for (CourseSection sec : sections) {
            var slideQw = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseSlide>()
                    .eq(CourseSlide::getSectionId, sec.getId());
            List<CourseSlide> slides = courseSlideMapper.selectList(slideQw);
            for (CourseSlide slide : slides) {
                slidePageMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SlidePage>()
                        .eq(SlidePage::getSlideId, slide.getId()));
                courseSlideMapper.deleteById(slide.getId());
                registerSlideCleanup(courseId, slide.getId());
            }
            sectionRepository.deleteById(sec.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/{hermesCourseId}")
    public R<Void> deleteCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                @PathVariable String hermesCourseId) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        Long courseId = mapping.getCourseId();
        cascadeDeleteCourse(courseId);
        mappingRepository.deleteById(mapping.getId());
        log.info("[HermesWebhook] Course cascade deleted: hermesCourseId={}, courseId={}, caller={}",
                hermesCourseId, courseId, caller.getUsername());
        return R.ok();
    }

    /**
     * 按内部 ID 删除课程（不依赖 Hermes 映射，用于管理后台创建的课程）
     */
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/courses/by-id/{courseId}")
    public R<Void> deleteCourseById(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                    @PathVariable Long courseId) {
        User caller = authenticate(apiKey);
        com.microcourse.entity.Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // 只允许课主或管理员删除
        if (!caller.getId().equals(course.getTeacherId()) && caller.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        cascadeDeleteCourse(courseId);
        // 清理映射（如果有的话）
        mappingRepository.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HermesCourseMapping>()
                .eq(HermesCourseMapping::getCourseId, courseId));
        log.info("[HermesWebhook] Course by-id deleted: courseId={}, caller={}", courseId, caller.getUsername());
        return R.ok();
    }

    /**
     * 列出该课程的所有 slide（Hermes 用 API Key，不依赖 JWT）
     */
    @GetMapping("/courses/{hermesCourseId}/slides")
    public R<List<com.microcourse.plugin.interactive.dto.SlideVO>> listSlides(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @PathVariable String hermesCourseId) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        com.microcourse.entity.Course course = courseRepository.selectById(mapping.getCourseId());
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (caller.getRole() != UserRole.ADMIN && !caller.getId().equals(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权查看该课程课件");
        }
        List<com.microcourse.plugin.interactive.entity.CourseSlide> slides = courseSlideMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.plugin.interactive.entity.CourseSlide>()
                        .eq(com.microcourse.plugin.interactive.entity.CourseSlide::getCourseId, mapping.getCourseId()));
        List<com.microcourse.plugin.interactive.dto.SlideVO> vos = slides.stream()
                .map(s -> {
                    com.microcourse.plugin.interactive.dto.SlideVO vo = new com.microcourse.plugin.interactive.dto.SlideVO();
                    vo.setId(s.getId());
                    vo.setCourseId(s.getCourseId());
                    vo.setChapterId(s.getChapterId());
                    vo.setSectionId(s.getSectionId());
                    vo.setLessonTitle(s.getFileName());
                    vo.setFileName(s.getFileName());
                    vo.setTotalPages(s.getTotalPages());
                    vo.setStatus(s.getStatus());
                    vo.setErrorMessage(s.getErrorMessage());
                    vo.setCreatedAt(s.getCreatedAt());
                    vo.setUpdatedAt(s.getUpdatedAt());
                    return vo;
                })
                .toList();
        return R.ok(vos);
    }

    /**
     * 列出平台所有课程（含非 Hermes 创建的）
     */
    @GetMapping("/courses/all")
    public R<List<com.microcourse.dto.hermes.HermesCourseListVO>> listAllCourses(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        User caller = authenticate(apiKey);
        List<com.microcourse.entity.Course> courses = courseRepository.selectList(null);
        java.util.Map<Long, com.microcourse.entity.CourseCategory> categoryCache = new java.util.HashMap<>();
        java.util.Map<Long, HermesCourseMapping> mappingCache = new java.util.HashMap<>();
        java.util.Set<Long> distinctCategoryIds = courses.stream()
                .map(com.microcourse.entity.Course::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!distinctCategoryIds.isEmpty()) {
            categoryRepository.selectBatchIds(distinctCategoryIds)
                    .forEach(cat -> categoryCache.put(cat.getId(), cat));
        }
        // 一次性加载所有映射
        mappingRepository.selectList(null).forEach(m -> mappingCache.put(m.getCourseId(), m));
        List<com.microcourse.dto.hermes.HermesCourseListVO> result = courses.stream()
                .filter(c -> caller.getRole() == UserRole.ADMIN || caller.getId().equals(c.getTeacherId()))
                .map(c -> {
                    String hermesId = mappingCache.containsKey(c.getId()) ? mappingCache.get(c.getId()).getHermesCourseId() : null;
                    String catName = c.getCategoryId() != null && categoryCache.containsKey(c.getCategoryId())
                            ? categoryCache.get(c.getCategoryId()).getName() : null;
                    return new com.microcourse.dto.hermes.HermesCourseListVO(
                            hermesId, c.getId(), c.getTitle(),
                            c.getStatus(), java.util.Optional.ofNullable(c.getStatus())
                            .map(com.microcourse.enums.CourseStatus::fromCode)
                            .map(Enum::name)
                            .orElse("UNKNOWN"),
                            c.getCategoryId(), catName, c.getCourseType(),
                            c.getUpdatedAt(), c.getCreatedAt());
                })
                .toList();
        return R.ok(result);
    }

    /**
     * 轮换 API Key（仅当前调用方自己）
     * POST /api-key/refresh
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/api-key/refresh")
    public R<String> refreshApiKey(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        User caller = authenticate(apiKey);
        // 192 字符 hex 熵（约 96 字节 ≈ 768 bit），通过 UUID 拼接生成
        String newKey = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        caller.setApiKey(newKey);
        caller.setUpdatedAt(java.time.LocalDateTime.now());
        // 用乐观锁（@Version）防止并发轮换覆盖
        int rows = userRepository.updateById(caller);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "API Key 轮换失败（乐观锁冲突），请重试");
        }
        log.info("[HermesWebhook] API Key rotated: caller={}, oldPrefix={}..., newPrefix={}...",
                caller.getUsername(),
                apiKey != null && apiKey.length() >= 8 ? apiKey.substring(0, 8) : "(short)",
                newKey.substring(0, 8));
        return R.ok(newKey);
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/courses/{hermesCourseId}/scripts")
    public R<?> batchPushScripts(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                 @PathVariable String hermesCourseId,
            @RequestBody java.util.Map<String, Object> body) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        Long courseId = mapping.getCourseId();
        Object scriptContentRaw = body.get("scriptContent");
        if (!(scriptContentRaw instanceof String)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "scriptContent 必须为字符串类型");
        }
        String fullScript = (String) scriptContentRaw;
        if (fullScript == null || fullScript.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "scriptContent 不能为空");
        }
        if (fullScript.length() > 65535) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "scriptContent 不能超过 65535 字符");
        }
        fullScript = com.microcourse.util.XssSanitizer.sanitizePlainText(fullScript);

        Number sectionIdNum = (Number) body.get("sectionId");

        try {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    caller.getId(), null,
                    java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEACHER")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            Long targetSectionId = sectionIdNum != null ? sectionIdNum.longValue() : null;
            java.util.List<com.microcourse.plugin.interactive.dto.SlidePageVO> pages =
                    slideService.getPages(courseId, targetSectionId);
            if (pages == null || pages.isEmpty()) {
                if (targetSectionId != null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课时无课件页面");
                }
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先上传课件");
            }
            int pageCount = pages.size();
            int updated = 0;

            if (targetSectionId != null) {
                for (com.microcourse.plugin.interactive.dto.SlidePageVO p : pages) {
                    java.util.Map<String, Object> pageBody = new java.util.HashMap<>();
                    pageBody.put("narrationScript", fullScript);
                    if (p.getSectionId() != null) { pageBody.put("_lessonId", p.getSectionId()); }
                    slideService.updatePage(courseId, p.getPageNumber(), pageBody);
                    updated++;
                }
            } else {
                Number chapterIdNum = (Number) body.get("chapterId");
                if (chapterIdNum == null) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                            "批量推送讲述稿时必须指定 chapterId 参数，以避免跨章节数据串写");
                }
                long targetChapterId = chapterIdNum.longValue();
                if (fullScript.length() < pageCount) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                            "讲述稿长度(" + fullScript.length() + ")少于课件页数(" + pageCount + ")，无法自动分配");
                }
                int chunkSize = fullScript.length() / pageCount;
                for (int i = 0; i < pageCount; i++) {
                    int start = i * chunkSize;
                    int end = (i == pageCount - 1) ? fullScript.length() : (i + 1) * chunkSize;
                    String pageScript = fullScript.substring(start, end).trim();

                    com.microcourse.plugin.interactive.dto.SlidePageVO p = pages.get(i);
                    java.util.Map<String, Object> pageBody = new java.util.HashMap<>();
                    pageBody.put("narrationScript", pageScript);
                    pageBody.put("_chapterId", targetChapterId);
                    slideService.updatePage(courseId, p.getPageNumber(), pageBody);
                    updated++;
                }
            }

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("updated", updated);
            result.put("totalPages", pageCount);
            log.info("[HermesWebhook] Scripts pushed: courseId={}, pages={}, sectionId={}, chars={}",
                    courseId, pageCount, targetSectionId, fullScript.length());
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