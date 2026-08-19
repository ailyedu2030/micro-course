package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.hermes.HermesChapterRequest;
import com.microcourse.dto.hermes.HermesChapterVO;
import com.microcourse.dto.hermes.HermesCourseListVO;
import com.microcourse.dto.hermes.HermesSectionRequest;
import com.microcourse.dto.hermes.HermesSectionVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.HermesCourseMappingRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.HermesWebhookCoursewareService;
import com.microcourse.service.HermesWebhookManagementService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HermesWebhookManagementServiceImpl implements HermesWebhookManagementService {

    private static final Logger log = LoggerFactory.getLogger(HermesWebhookManagementServiceImpl.class);

    private final UserRepository userRepository;
    private final HermesCourseMappingRepository mappingRepository;
    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseSectionRepository sectionRepository;
    private final HermesWebhookCoursewareService coursewareService;

    public HermesWebhookManagementServiceImpl(UserRepository userRepository,
                                              HermesCourseMappingRepository mappingRepository,
                                              CourseRepository courseRepository,
                                              CourseCategoryRepository categoryRepository,
                                              CourseChapterRepository chapterRepository,
                                              CourseSectionRepository sectionRepository,
                                              HermesWebhookCoursewareService coursewareService) {
        this.userRepository = userRepository;
        this.mappingRepository = mappingRepository;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.coursewareService = coursewareService;
    }

    @Override
    public User authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[HermesWebhook] Missing X-API-Key header");
            throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
        }
        // S-004 Phase 2: 仅按 hash 查询。明文 fallback 已移除（V324 清空明文列，
        // findByApiKeyHash 是唯一认证路径）
        String apiKeyHash = DigestUtils.sha256Hex(apiKey);
        Optional<User> callerOpt = userRepository.findByApiKeyHash(apiKeyHash);
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

    @Override
    public HermesCourseMapping resolveMapping(String hermesCourseId) {
        HermesCourseMapping mapping = mappingRepository.selectOne(
                new LambdaQueryWrapper<HermesCourseMapping>()
                        .eq(HermesCourseMapping::getHermesCourseId, hermesCourseId));
        if (mapping == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND,
                    "Hermes 课程映射不存在，请先调用 POST /courses 创建课程: hermesCourseId=" + hermesCourseId);
        }
        return mapping;
    }

    @Override
    public void verifyCourseOwnership(User caller, HermesCourseMapping mapping) {
        if (caller.getRole() == UserRole.ADMIN) return;
        Course course = courseRepository.selectById(mapping.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!caller.getId().equals(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程");
        }
    }

    @Override
    public List<HermesSectionVO> listSections(String hermesCourseId, String apiKey) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        List<CourseSection> sections = sectionRepository.selectList(
                new LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getCourseId, mapping.getCourseId()));
        return sections.stream().map(s -> new HermesSectionVO(
                s.getId(), s.getChapterId(), s.getTitle(), s.getSectionType(),
                s.getSortOrder(), s.getDuration(), s.getVisible(),
                s.getDescription(), s.getScriptContent(), s.getContentUrl(),
                s.getCreatedAt(), s.getUpdatedAt()
        )).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HermesSectionVO createSection(String hermesCourseId, String apiKey, HermesSectionRequest body) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        verifyCourseOwnership(caller, mapping);
        if (body.getChapterId() != null) {
            CourseChapter ch = chapterRepository.selectById(body.getChapterId());
            if (ch == null || !ch.getCourseId().equals(mapping.getCourseId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节 ID 不属于该课程");
            }
        }
        CourseSection section = new CourseSection();
        section.setCourseId(mapping.getCourseId());
        section.setChapterId(body.getChapterId());
        section.setTitle(body.getTitle());
        section.setSectionType(body.getSectionType());
        section.setSortOrder(body.getSortOrder());
        section.setDuration(body.getDuration());
        section.setVisible(body.getVisible() != null ? body.getVisible() : true);
        section.setDescription(body.getDescription());
        section.setScriptContent(body.getScriptContent());
        section.setContentUrl(body.getContentUrl());
        section.setVersion(body.getVersion() != null ? body.getVersion() : 1);
        LocalDateTime now = LocalDateTime.now();
        section.setCreatedAt(now);
        section.setUpdatedAt(now);
        sectionRepository.insert(section);
        return new HermesSectionVO(
                section.getId(), section.getChapterId(), section.getTitle(), section.getSectionType(),
                section.getSortOrder(), section.getDuration(), section.getVisible(),
                section.getDescription(), section.getScriptContent(), section.getContentUrl(),
                section.getCreatedAt(), section.getUpdatedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HermesSectionVO updateSection(String hermesCourseId, String apiKey, Long sectionId, HermesSectionRequest body) {
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
        existing.setUpdatedAt(LocalDateTime.now());
        if (sectionRepository.updateById(existing) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "课时已被其他人修改，请刷新后重试");
        }
        return new HermesSectionVO(
                existing.getId(), existing.getChapterId(), existing.getTitle(), existing.getSectionType(),
                existing.getSortOrder(), existing.getDuration(), existing.getVisible(),
                existing.getDescription(), existing.getScriptContent(), existing.getContentUrl(),
                existing.getCreatedAt(), existing.getUpdatedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HermesChapterVO updateChapter(String hermesCourseId, String apiKey, Long chapterId, HermesChapterRequest body) {
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
        existing.setUpdatedAt(LocalDateTime.now());
        if (chapterRepository.updateById(existing) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "章节已被其他人修改，请刷新后重试");
        }
        return new HermesChapterVO(
                existing.getId(), existing.getTitle(), existing.getDescription(),
                existing.getSortOrder(), existing.getDuration(), existing.getLearningObjectives(),
                existing.getCreatedAt(), existing.getUpdatedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(String hermesCourseId, String apiKey) {
        User caller = authenticate(apiKey);
        HermesCourseMapping mapping = resolveMapping(hermesCourseId);
        Long courseId = mapping.getCourseId();
        verifyCourseOwnership(caller, mapping);
        coursewareService.deleteCourseCascade(courseId);
        courseRepository.deleteById(courseId);
        mappingRepository.deleteById(mapping.getId());
        log.info("[HermesWebhook] Course cascade deleted: hermesCourseId={}, courseId={}, caller={}",
                hermesCourseId, courseId, caller.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseById(Long courseId, String apiKey) {
        User caller = authenticate(apiKey);
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!caller.getId().equals(course.getTeacherId()) && caller.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        coursewareService.deleteCourseCascade(courseId);
        courseRepository.deleteById(courseId);
        mappingRepository.delete(new LambdaQueryWrapper<HermesCourseMapping>()
                .eq(HermesCourseMapping::getCourseId, courseId));
        log.info("[HermesWebhook] Course by-id deleted: courseId={}, caller={}", courseId, caller.getUsername());
    }

    @Override
    public List<HermesCourseListVO> listAllCourses(String apiKey) {
        User caller = authenticate(apiKey);
        List<Course> courses = courseRepository.selectList(null);
        java.util.Map<Long, com.microcourse.entity.CourseCategory> categoryCache = new java.util.HashMap<>();
        java.util.Map<Long, HermesCourseMapping> mappingCache = new java.util.HashMap<>();
        Set<Long> distinctCategoryIds = courses.stream()
                .map(Course::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!distinctCategoryIds.isEmpty()) {
            categoryRepository.selectBatchIds(distinctCategoryIds)
                    .forEach(cat -> categoryCache.put(cat.getId(), cat));
        }
        mappingRepository.selectList(null).forEach(m -> mappingCache.put(m.getCourseId(), m));
        return courses.stream()
                .filter(c -> caller.getRole() == UserRole.ADMIN || caller.getId().equals(c.getTeacherId()))
                .map(c -> {
                    String hermesId = mappingCache.containsKey(c.getId()) ? mappingCache.get(c.getId()).getHermesCourseId() : null;
                    String catName = c.getCategoryId() != null && categoryCache.containsKey(c.getCategoryId())
                            ? categoryCache.get(c.getCategoryId()).getName() : null;
                    return new HermesCourseListVO(
                            hermesId, c.getId(), c.getTitle(),
                            c.getStatus(), Optional.ofNullable(c.getStatus())
                            .map(CourseStatus::fromCode)
                            .map(Enum::name)
                            .orElse("UNKNOWN"),
                            c.getCategoryId(), catName, c.getCourseType(),
                            c.getUpdatedAt(), c.getCreatedAt());
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refreshApiKey(String apiKey) {
        User caller = authenticate(apiKey);
        String newKey = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        caller.setApiKey(newKey);
        caller.setUpdatedAt(LocalDateTime.now());
        int rows = userRepository.updateById(caller);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "API Key 轮换失败（乐观锁冲突），请重试");
        }
        log.info("[HermesWebhook] API Key rotated: caller={}, oldPrefix={}..., newPrefix={}...",
                caller.getUsername(),
                apiKey != null && apiKey.length() >= 8 ? apiKey.substring(0, 8) : "(short)",
                newKey.substring(0, 8));
        return newKey;
    }
}
