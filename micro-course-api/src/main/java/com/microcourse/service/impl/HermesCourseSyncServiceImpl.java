package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.HermesCourseMappingRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.HermesCourseSyncService;
import com.microcourse.dto.hermes.HermesCourseDetailVO;
import com.microcourse.dto.hermes.HermesCourseDetailVO.ChapterVo;
import com.microcourse.dto.hermes.HermesCourseDetailVO.LessonVo;
import com.microcourse.dto.hermes.HermesCourseDetailVO.PricingVo;
import com.microcourse.dto.hermes.HermesCourseListVO;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.dto.hermes.HermesWebhookRequest.ChapterDto;
import com.microcourse.dto.hermes.HermesWebhookRequest.LessonDto;
import com.microcourse.dto.hermes.HermesWebhookRequest.PricingDto;
import com.microcourse.enums.CourseStatus;
import com.microcourse.util.XssSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.microcourse.util.CourseCacheConstants;
import com.microcourse.util.RedisUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HermesCourseSyncServiceImpl implements HermesCourseSyncService {

    private static final Logger log = LoggerFactory.getLogger(HermesCourseSyncServiceImpl.class);

private final HermesCourseMappingRepository mappingRepository;
    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseSectionRepository sectionRepository;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    public HermesCourseSyncServiceImpl(HermesCourseMappingRepository mappingRepository,
                                      CourseRepository courseRepository,
                                      CourseCategoryRepository categoryRepository,
                                      CourseChapterRepository chapterRepository,
                                      CourseSectionRepository sectionRepository,
                                      UserRepository userRepository,
                                      RedisUtil redisUtil) {
        this.mappingRepository = mappingRepository;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.chapterRepository = chapterRepository;
        this.sectionRepository = sectionRepository;
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
    }

    private void evictCourseCache(Long courseId) {
        String cacheKey = CourseCacheConstants.COURSE_CACHE_PREFIX + courseId;
        redisUtil.delete(cacheKey);
        log.debug("[HermesSync] Evicted course cache: cacheKey={}", cacheKey);
    }

    @Override
    @Transactional
    public HermesSyncResult upsertCourse(HermesWebhookRequest request, Long callerTeacherId) {
        String hermesCourseId = request.getHermesCourseId();

        // 验证 caller teacher 存在且有效
        User callerTeacher = userRepository.selectById(callerTeacherId);
        if (callerTeacher == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证 categoryId 存在
        if (request.getCategoryId() != null) {
            CourseCategory category = categoryRepository.selectById(request.getCategoryId());
            if (category == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "分类 ID " + request.getCategoryId() + " 不存在");
            }
        }

        // 如果 body 里有 teacherId，必须等于 caller（防越权）
        if (request.getTeacherId() != null && !request.getTeacherId().equals(callerTeacherId)) {
            log.warn("[HermesSync] teacherId mismatch: caller={}, body={}",
                    callerTeacherId, request.getTeacherId());
            throw new BusinessException(ErrorCode.NO_PERMISSION, "API Key 身份与 body 中的 teacherId 不一致");
        }

        LambdaQueryWrapper<HermesCourseMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HermesCourseMapping::getHermesCourseId, hermesCourseId);
        HermesCourseMapping mapping = mappingRepository.selectOne(wrapper);

        if (mapping == null) {
            return createCourse(request, callerTeacher);
        } else {
            return updateCourse(mapping.getCourseId(), request, callerTeacher);
        }
    }

    private HermesSyncResult createCourse(HermesWebhookRequest request, User callerTeacher) {
        Course course = buildCourse(request, callerTeacher);
        course.setStatus(CourseStatus.DRAFT.getCode());
        course.setStudentCount(0);
        course.setAvgRating(BigDecimal.ZERO);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        courseRepository.insert(course);
        Long courseId = course.getId();

        HermesCourseMapping mapping = new HermesCourseMapping();
        mapping.setHermesCourseId(request.getHermesCourseId());
        mapping.setCourseId(courseId);
        mapping.setHermesTeacherId(String.valueOf(callerTeacher.getId()));
        mapping.setLastSyncAt(LocalDateTime.now());
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
        mappingRepository.insert(mapping);

        upsertChapters(courseId, request.getChapters());

        // 清除缓存，确保前端看到最新数据
        evictCourseCache(courseId);

        log.info("[HermesSync] Created course: hermesId={}, courseId={}, title={}",
                request.getHermesCourseId(), courseId, request.getTitle());
        return new HermesSyncResult(courseId, "DRAFT", "created");
    }

    private HermesSyncResult updateCourse(Long courseId, HermesWebhookRequest request, User callerTeacher) {
        Course existing = courseRepository.selectById(courseId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        Course updated = buildCourse(request, callerTeacher);
        updated.setId(courseId);
        updated.setStatus(existing.getStatus());
        updated.setStudentCount(existing.getStudentCount());
        updated.setAvgRating(existing.getAvgRating());
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(LocalDateTime.now());
        courseRepository.updateById(updated);

        LambdaQueryWrapper<HermesCourseMapping> q = new LambdaQueryWrapper<>();
        q.eq(HermesCourseMapping::getCourseId, courseId);
        HermesCourseMapping mapping = mappingRepository.selectOne(q);
        if (mapping != null) {
            mapping.setLastSyncAt(LocalDateTime.now());
            mapping.setUpdatedAt(LocalDateTime.now());
            mappingRepository.updateById(mapping);
        }

        upsertChapters(courseId, request.getChapters());

        // 清除缓存，确保前端看到更新后的章节和课时数据
        evictCourseCache(courseId);

        log.info("[HermesSync] Updated course: hermesId={}, courseId={}, title={}",
                request.getHermesCourseId(), courseId, request.getTitle());
        return new HermesSyncResult(courseId, CourseStatus.fromCode(existing.getStatus()).name(), "updated");
    }

    private void upsertChapters(Long courseId, List<ChapterDto> chapters) {
        if (chapters == null) return;

        List<CourseChapter> existingChapters = chapterRepository.selectList(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, courseId));

        java.util.Set<Long> matchedChapterIds = new java.util.HashSet<>();

        for (int ci = 0; ci < chapters.size(); ci++) {
            ChapterDto dto = chapters.get(ci);
            int sortOrder = dto.getSortOrder() != null ? dto.getSortOrder() : ci + 1;

            CourseChapter chapter = existingChapters.stream()
                    .filter(ch -> ch.getTitle() != null && ch.getTitle().equals(dto.getTitle())
                            && ch.getSortOrder() != null && ch.getSortOrder().equals(sortOrder))
                    .findFirst().orElse(null);

            if (chapter == null) {
                chapter = new CourseChapter();
                chapter.setCourseId(courseId);
                chapter.setTitle(dto.getTitle());
                chapter.setSortOrder(sortOrder);
                chapter.setDuration(0);
                var now = LocalDateTime.now();
                chapter.setCreatedAt(now);
                chapter.setUpdatedAt(now);
                chapterRepository.insert(chapter);
            } else {
                matchedChapterIds.add(chapter.getId());
            }

            // upsert sections under this chapter
            upsertSections(courseId, chapter.getId(), dto.getLessons());

            if (dto.getLessons() == null || dto.getLessons().isEmpty()) {
                long existingCount = sectionRepository.selectCount(
                        new LambdaQueryWrapper<CourseSection>()
                                .eq(CourseSection::getChapterId, chapter.getId()));
                if (existingCount == 0) {
                    CourseSection defaultSection = new CourseSection();
                    defaultSection.setChapterId(chapter.getId());
                    defaultSection.setCourseId(courseId);
                    defaultSection.setTitle(dto.getTitle());
                    defaultSection.setSectionType("VIDEO");
                    defaultSection.setSortOrder(1);
                    defaultSection.setVisible(true);
                    var now = LocalDateTime.now();
                    defaultSection.setCreatedAt(now);
                    defaultSection.setUpdatedAt(now);
                    defaultSection.setVersion(1);
                    sectionRepository.insert(defaultSection);
                }
            }
        }

        // delete chapters that no longer exist in Hermes data (cascade sections)
        for (CourseChapter ch : existingChapters) {
            if (!matchedChapterIds.contains(ch.getId())) {
                sectionRepository.delete(new LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getChapterId, ch.getId()));
                chapterRepository.deleteById(ch.getId());
            }
        }
    }

    private void upsertSections(Long courseId, Long chapterId, List<LessonDto> lessons) {
        if (lessons == null || lessons.isEmpty()) return;

        List<CourseSection> existingSections = sectionRepository.selectList(
                new LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getChapterId, chapterId));

        java.util.Set<Long> matchedSectionIds = new java.util.HashSet<>();

        for (int si = 0; si < lessons.size(); si++) {
            LessonDto dto = lessons.get(si);
            int sortOrder = dto.getSortOrder() != null ? dto.getSortOrder() : si + 1;

            CourseSection section = existingSections.stream()
                    .filter(s -> s.getTitle() != null && s.getTitle().equals(dto.getTitle())
                            && s.getSortOrder() != null && s.getSortOrder().equals(sortOrder))
                    .findFirst().orElse(null);

            if (section == null) {
                section = new CourseSection();
                section.setChapterId(chapterId);
                section.setCourseId(courseId);
                section.setTitle(dto.getTitle());
                section.setSectionType(dto.getType() != null ? dto.getType() : "VIDEO");
                section.setSortOrder(sortOrder);
                section.setDuration(dto.getDurationMinutes());
                section.setVisible(true);
                section.setScriptContent(dto.getScriptContent());
                var now = LocalDateTime.now();
                section.setCreatedAt(now);
                section.setUpdatedAt(now);
                section.setVersion(1);
                sectionRepository.insert(section);
            } else {
                matchedSectionIds.add(section.getId());
                // update existing section fields
                section.setSectionType(dto.getType() != null ? dto.getType() : "VIDEO");
                section.setDuration(dto.getDurationMinutes());
                section.setScriptContent(dto.getScriptContent());
                section.setUpdatedAt(LocalDateTime.now());
                sectionRepository.updateById(section);
            }
        }

        // delete sections that no longer exist in Hermes data
        for (CourseSection s : existingSections) {
            if (!matchedSectionIds.contains(s.getId())) {
                sectionRepository.deleteById(s.getId());
            }
        }
    }

    private Course buildCourse(HermesWebhookRequest request, User callerTeacher) {
        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setSubtitle(request.getSubtitle());
        course.setSummary(request.getSummary());
        course.setCoverUrl(request.getCoverUrl());
        course.setCategoryId(request.getCategoryId());
        // teacherId 强制使用 API Key 反查得到的教师身份（不信任 body）
        course.setTeacherId(callerTeacher.getId());
        course.setOfferDepartmentId(request.getOfferDepartmentId() != null
                ? request.getOfferDepartmentId()
                : callerTeacher.getDepartmentId());
        course.setSemester(request.getSemester());
        course.setCreditHours(request.getCreditHours());
        course.setCourseNature(request.getCourseNature());
        course.setMaxStudents(request.getMaxStudents());
        course.setDifficulty(request.getDifficulty());
        course.setDescription(XssSanitizer.sanitize(request.getDescription()));
        course.setTags(request.getTags());
        course.setCourseType(request.getCourseType());

        PricingDto pricing = request.getPricing();
        if (pricing != null) {
            course.setIsFree(pricing.getIsFree() != null ? pricing.getIsFree() : Boolean.TRUE);
            course.setPrice(pricing.getPrice());
            course.setFreeAccessScope(pricing.getFreeAccessScope());
            course.setFreeDeptIds(pricing.getFreeDeptIds());
        } else {
            course.setIsFree(Boolean.TRUE);
        }

        return course;
    }

    @Override
    public List<HermesCourseListVO> listCoursesByTeacher(Long teacherId) {
        // 返回该教师的所有课程（不依赖 Hermes 映射，hermesCourseId 字段标记是否已同步）
        List<Course> courses = courseRepository.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId));
        if (courses == null || courses.isEmpty()) return List.of();

        // 一次性加载该教师的所有映射
        java.util.Map<Long, HermesCourseMapping> mappingMap = new java.util.HashMap<>();
        for (HermesCourseMapping m : mappingRepository.selectList(
                new LambdaQueryWrapper<HermesCourseMapping>()
                        .eq(HermesCourseMapping::getHermesTeacherId, String.valueOf(teacherId)))) {
            mappingMap.put(m.getCourseId(), m);
        }

        java.util.Map<Long, String> categoryNames = new java.util.HashMap<>();
        courses.stream().map(Course::getCategoryId).filter(java.util.Objects::nonNull).distinct().forEach(cid -> {
            CourseCategory cat = categoryRepository.selectById(cid);
            if (cat != null) categoryNames.put(cid, cat.getName());
        });

        return courses.stream().map(c -> {
            HermesCourseMapping m = mappingMap.get(c.getId());
            return new HermesCourseListVO(
                    m != null ? m.getHermesCourseId() : null, c.getId(), c.getTitle(),
                    c.getStatus(), c.getStatus() != null ? CourseStatus.fromCode(c.getStatus()).name() : "UNKNOWN",
                    c.getCategoryId(), categoryNames.get(c.getCategoryId()), c.getCourseType(),
                    m != null ? m.getLastSyncAt() : null, c.getCreatedAt());
        }).toList();
    }

    @Override
    public HermesCourseDetailVO getCourseDetail(String hermesCourseId, Long callerTeacherId) {
        LambdaQueryWrapper<HermesCourseMapping> q = new LambdaQueryWrapper<>();
        q.eq(HermesCourseMapping::getHermesCourseId, hermesCourseId);
        HermesCourseMapping mapping = mappingRepository.selectOne(q);
        if (mapping == null) return null;

        Course course = courseRepository.selectById(mapping.getCourseId());
        if (course == null) return null;

        // 权限检查：只能读自己的课程
        if (!course.getTeacherId().equals(callerTeacherId)) {
            log.warn("[HermesSync] Permission denied: course.teacherId={}, caller={}",
                    course.getTeacherId(), callerTeacherId);
            return null;
        }

        User teacher = userRepository.selectById(callerTeacherId);
        if (teacher == null) return null;

        HermesCourseDetailVO vo = new HermesCourseDetailVO();
        vo.setHermesCourseId(mapping.getHermesCourseId());
        vo.setCourseId(course.getId());
        vo.setTitle(course.getTitle());
        vo.setSubtitle(course.getSubtitle());
        vo.setSummary(course.getSummary());
        String cover = course.getCoverUrl();
        if (cover != null && !cover.startsWith("http") && !cover.startsWith("/api/files/")) {
            cover = "/api/files/" + cover;
        }
        vo.setCoverUrl(cover);
        vo.setCategoryId(course.getCategoryId());
        vo.setTeacherId(course.getTeacherId());
        vo.setTeacherName(teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername());
        vo.setOfferDepartmentId(course.getOfferDepartmentId());
        vo.setSemester(course.getSemester());
        vo.setCreditHours(course.getCreditHours());
        vo.setCourseNature(course.getCourseNature());
        vo.setMaxStudents(course.getMaxStudents());
        vo.setDifficulty(course.getDifficulty());
        vo.setStatus(course.getStatus());
        vo.setStatusText(course.getStatus() != null ? CourseStatus.fromCode(course.getStatus()).name() : "UNKNOWN");
        vo.setDescription(course.getDescription());
        vo.setTags(course.getTags());
        vo.setCourseType(course.getCourseType());
        vo.setRejectReason(course.getRejectReason());
        vo.setStudentCount(course.getStudentCount());
        vo.setAvgRating(course.getAvgRating());
        vo.setPublishedAt(course.getPublishedAt());
        vo.setLastSyncAt(mapping.getLastSyncAt());
        vo.setCreatedAt(course.getCreatedAt());

        PricingVo pricing = new PricingVo();
        pricing.setIsFree(course.getIsFree());
        pricing.setPrice(course.getPrice());
        pricing.setFreeAccessScope(course.getFreeAccessScope());
        pricing.setFreeDeptIds(course.getFreeDeptIds());
        vo.setPricing(pricing);

        List<CourseChapter> chapters = chapterRepository.selectList(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, course.getId())
                        .orderByAsc(CourseChapter::getSortOrder));
        List<ChapterVo> chapterVos = chapters.stream().map(ch -> {
            ChapterVo cv = new ChapterVo();
            cv.setId(ch.getId());
            cv.setTitle(ch.getTitle());
            cv.setSortOrder(ch.getSortOrder());

            List<CourseSection> sections = sectionRepository.selectList(
                    new LambdaQueryWrapper<CourseSection>()
                            .eq(CourseSection::getChapterId, ch.getId())
                            .orderByAsc(CourseSection::getSortOrder));
            List<LessonVo> lessonVos = sections.stream().map(s -> {
                LessonVo lv = new LessonVo();
                lv.setId(s.getId());
                lv.setTitle(s.getTitle());
                lv.setLessonType(s.getSectionType());
                lv.setDurationMinutes(s.getDuration());
                lv.setSortOrder(s.getSortOrder());
                lv.setScriptContent(s.getScriptContent());
                return lv;
            }).toList();
            cv.setLessons(lessonVos);
            return cv;
        }).toList();
        vo.setChapters(chapterVos);

        return vo;
    }


}
