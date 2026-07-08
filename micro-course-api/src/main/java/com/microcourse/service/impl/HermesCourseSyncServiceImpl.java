package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.HermesCourseMapping;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.HermesCourseMappingRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.HermesCourseSyncService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HermesCourseSyncServiceImpl implements HermesCourseSyncService {

    private static final Logger log = LoggerFactory.getLogger(HermesCourseSyncServiceImpl.class);

    private final HermesCourseMappingRepository mappingRepository;
    private final CourseRepository courseRepository;
    private final CourseChapterRepository chapterRepository;
    private final UserRepository userRepository;

    public HermesCourseSyncServiceImpl(HermesCourseMappingRepository mappingRepository,
                                     CourseRepository courseRepository,
                                     CourseChapterRepository chapterRepository,
                                     UserRepository userRepository) {
        this.mappingRepository = mappingRepository;
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.userRepository = userRepository;
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
        mapping.setHermesTeacherId(String.valueOf(request.getTeacherId()));
        mapping.setLastSyncAt(LocalDateTime.now());
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
        mappingRepository.insert(mapping);

        syncChapters(courseId, request.getChapters());

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
                log.info("[HermesSync] Lesson not fully materialized: chapterId={}, lesson={}, type={}, url={}",
                        chapter.getId(), lessonDto.getTitle(), lessonDto.getType(), lessonDto.getContentUrl());
                lessonIndex++;
            }
        }
    }
}
