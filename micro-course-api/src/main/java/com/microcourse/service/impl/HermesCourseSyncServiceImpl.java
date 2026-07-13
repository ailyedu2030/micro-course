package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.Lesson;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.HermesCourseMappingRepository;
import com.microcourse.repository.LessonRepository;
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
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    public HermesCourseSyncServiceImpl(HermesCourseMappingRepository mappingRepository,
                                      CourseRepository courseRepository,
                                      CourseCategoryRepository categoryRepository,
                                      CourseChapterRepository chapterRepository,
                                      LessonRepository lessonRepository,
                                      UserRepository userRepository,
                                      RedisUtil redisUtil) {
        this.mappingRepository = mappingRepository;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.chapterRepository = chapterRepository;
        this.lessonRepository = lessonRepository;
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

        syncChapters(courseId, request.getChapters());

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

        chapterRepository.delete(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId));
        syncChapters(courseId, request.getChapters());

        // 清除缓存，确保前端看到更新后的章节和课时数据
        evictCourseCache(courseId);

        log.info("[HermesSync] Updated course: hermesId={}, courseId={}, title={}",
                request.getHermesCourseId(), courseId, request.getTitle());
        return new HermesSyncResult(courseId, CourseStatus.fromCode(existing.getStatus()).name(), "updated");
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
        List<HermesCourseMapping> mappings = mappingRepository.selectList(
                new LambdaQueryWrapper<HermesCourseMapping>()
                        .eq(HermesCourseMapping::getHermesTeacherId, String.valueOf(teacherId)));
        if (mappings.isEmpty()) return List.of();

        List<Long> courseIds = mappings.stream().map(HermesCourseMapping::getCourseId).toList();
        List<Course> courses = courseRepository.selectBatchIds(courseIds);
        if (courses == null) return List.of();
        java.util.Map<Long, Course> courseMap = courses.stream()
                .collect(java.util.stream.Collectors.toMap(Course::getId, c -> c));

        java.util.Map<Long, String> categoryNames = new java.util.HashMap<>();
        courses.stream().map(Course::getCategoryId).filter(java.util.Objects::nonNull).distinct().forEach(cid -> {
            CourseCategory cat = categoryRepository.selectById(cid);
            if (cat != null) categoryNames.put(cid, cat.getName());
        });

        return mappings.stream().map(m -> {
            Course c = courseMap.get(m.getCourseId());
            if (c == null) return null;
            return new HermesCourseListVO(
                    m.getHermesCourseId(), c.getId(), c.getTitle(),
                    c.getStatus(), c.getStatus() != null ? CourseStatus.fromCode(c.getStatus()).name() : "UNKNOWN",
                    c.getCategoryId(), categoryNames.get(c.getCategoryId()), c.getCourseType(),
                    m.getLastSyncAt(), c.getCreatedAt());
        }).filter(java.util.Objects::nonNull).toList();
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

            List<Lesson> lessons = lessonRepository.selectList(
                    new LambdaQueryWrapper<Lesson>()
                            .eq(Lesson::getChapterId, ch.getId())
                            .orderByAsc(Lesson::getSortOrder));
            List<LessonVo> lessonVos = lessons.stream().map(l -> {
                LessonVo lv = new LessonVo();
                lv.setId(l.getId());
                lv.setTitle(l.getTitle());
                lv.setLessonType(l.getLessonType());
                lv.setDurationMinutes(l.getDuration());
                lv.setSortOrder(l.getSortOrder());
                lv.setScriptContent(l.getScriptContent());
                return lv;
            }).toList();
            cv.setLessons(lessonVos);
            return cv;
        }).toList();
        vo.setChapters(chapterVos);

        return vo;
    }

    private void syncChapters(Long courseId, List<ChapterDto> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return;
        }

        int chapterIndex = 0;
        for (ChapterDto chapterDto : chapters) {
            CourseChapter chapter = new CourseChapter();
            chapter.setCourseId(courseId);
            chapter.setTitle(chapterDto.getTitle());
            chapter.setSortOrder(chapterDto.getSortOrder() != null ? chapterDto.getSortOrder() : chapterIndex + 1);

            // 章节类型根据第一个课时的类型推断；无课时时默认 VIDEO（保守兜底）
            String chapterType = "VIDEO";
            if (chapterDto.getLessons() != null && !chapterDto.getLessons().isEmpty()) {
                String firstType = chapterDto.getLessons().get(0).getType();
                if (firstType != null) {
                    chapterType = firstType.trim().toUpperCase();
                }
            }
            chapter.setChapterType(chapterType);
            chapter.setDuration(0);
            chapter.setCreatedAt(LocalDateTime.now());
            chapter.setUpdatedAt(LocalDateTime.now());
            chapterRepository.insert(chapter);
            chapterIndex++;

            List<LessonDto> lessons = chapterDto.getLessons();
            if (lessons == null || lessons.isEmpty()) {
                continue;
            }

            int lessonIndex = 0;
            for (LessonDto lessonDto : lessons) {
                Lesson lesson = new Lesson();
                lesson.setChapterId(chapter.getId());
                lesson.setCourseId(courseId);
                lesson.setTitle(lessonDto.getTitle());
                lesson.setLessonType(lessonDto.getType() != null ? lessonDto.getType() : "VIDEO");
                lesson.setSortOrder(lessonDto.getSortOrder() != null ? lessonDto.getSortOrder() : lessonIndex + 1);
                lesson.setDuration(lessonDto.getDurationMinutes());
                lesson.setVisible(true);
                lesson.setScriptContent(lessonDto.getScriptContent());
                lesson.setCreatedAt(LocalDateTime.now());
                lesson.setUpdatedAt(LocalDateTime.now());
                lessonRepository.insert(lesson);
                lessonIndex++;

                log.debug("[HermesSync] Inserted lesson: chapterId={}, lessonId={}, title={}",
                        chapter.getId(), lesson.getId(), lessonDto.getTitle());
            }
        }
    }
}
