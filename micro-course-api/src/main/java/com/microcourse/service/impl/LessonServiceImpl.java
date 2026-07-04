package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.lesson.LessonVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Lesson;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.LessonRepository;
import com.microcourse.service.LessonService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LessonServiceImpl implements LessonService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;

    public LessonServiceImpl(LessonRepository lessonRepository,
                             CourseRepository courseRepository) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonVO create(Long chapterId, Long courseId, String title, String lessonType) {
        // SEC-P0-01: 校验课程 Owner 权限（防止跨教师越权创建课时）
        assertCourseOwnership(courseId);

        int maxSort = Math.toIntExact(lessonRepository.selectCount(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getChapterId, chapterId)));
        Lesson lesson = new Lesson();
        lesson.setChapterId(chapterId);
        lesson.setCourseId(courseId);
        lesson.setTitle(title);
        lesson.setLessonType(lessonType != null ? lessonType : "VIDEO");
        lesson.setSortOrder(maxSort);
        lesson.setDuration(0);
        lesson.setVisible(true);
        lesson.setVersion(0);
        lesson.setCreatedAt(LocalDateTime.now());
        lesson.setUpdatedAt(LocalDateTime.now());
        lessonRepository.insert(lesson);
        return toVO(lesson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonVO update(Long id, String title, Integer duration, Boolean visible) {
        Lesson lesson = lessonRepository.selectById(id);
        if (lesson == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课时不存在");
        // SEC-P0-01: 校验课程 Owner 权限（防止跨教师越权修改课时）
        assertCourseOwnership(lesson.getCourseId());
        if (title != null) lesson.setTitle(title);
        if (duration != null) lesson.setDuration(duration);
        if (visible != null) lesson.setVisible(visible);
        lesson.setUpdatedAt(LocalDateTime.now());
        lessonRepository.updateById(lesson);
        return toVO(lesson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Lesson lesson = lessonRepository.selectById(id);
        if (lesson != null) {
            // SEC-P0-01: 校验课程 Owner 权限（防止跨教师越权删除课时）
            assertCourseOwnership(lesson.getCourseId());
            lessonRepository.deleteById(id);
        }
    }

    /**
     * 更新所有课时排序：使用逐条 updateById（安全替代 CASE WHEN 字符串拼接，消除 SQL 注入风险）。
     * P3 性能优化：使用 Map 查找替代双重循环 O(N*M) → O(N+M)。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<LessonVO.SortItem> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> ids = items.stream().map(LessonVO.SortItem::getId).collect(Collectors.toList());
        List<Lesson> lessons = lessonRepository.selectBatchIds(ids);
        for (Lesson lesson : lessons) {
            assertCourseOwnership(lesson.getCourseId());
        }

        // P0-SEC-FIX: 使用逐条 updateById 替代 CASE WHEN 字符串拼接，消除 SQL 注入风险
        // P3 性能优化: 使用 Map 查找替代双重循环 O(N*M) → O(N+M)
        Map<Long, Lesson> lessonMap = lessons.stream()
                .collect(Collectors.toMap(Lesson::getId, Function.identity()));
        for (LessonVO.SortItem item : items) {
            Lesson lesson = lessonMap.get(item.getId());
            if (lesson != null) {
                lesson.setSortOrder(item.getSortOrder());
                lesson.setChapterId(item.getChapterId());
                lesson.setUpdatedAt(LocalDateTime.now());
                lessonRepository.updateById(lesson);
            }
        }
    }

    @Override
    public LessonVO getById(Long id) {
        Lesson lesson = lessonRepository.selectById(id);
        if (lesson == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课时不存在");
        return toVO(lesson);
    }

    @Override
    public List<LessonVO> getByChapter(Long chapterId) {
        List<Lesson> lessons = lessonRepository.selectList(
                new LambdaQueryWrapper<Lesson>()
                        .eq(Lesson::getChapterId, chapterId)
                        .orderByAsc(Lesson::getSortOrder));
        return lessons.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * SEC-P0-01: 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN。
     * <p>通用模式：实现逻辑与 ExerciseServiceImpl / VideoServiceImpl / CourseChapterServiceImpl /
     * OfflineSessionServiceImpl / QuestionServiceImpl 中的同名方法一致（LessonServiceImpl 额外增加了
     * courseId 为空校验）。若需统一重构，可抽取到公共工具类。</p>
     *
     * @param courseId 课程 ID
     * @throws BusinessException COURSE_NOT_FOUND 课程不存在，NO_PERMISSION 无权限
     */
    private void assertCourseOwnership(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程 ID 不能为空");
        }
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private LessonVO toVO(Lesson lesson) {
        LessonVO vo = new LessonVO();
        vo.setId(lesson.getId());
        vo.setChapterId(lesson.getChapterId());
        vo.setCourseId(lesson.getCourseId());
        vo.setTitle(lesson.getTitle());
        vo.setLessonType(lesson.getLessonType());
        vo.setSortOrder(lesson.getSortOrder());
        vo.setDuration(lesson.getDuration());
        vo.setVisible(lesson.getVisible());
        vo.setCreatedAt(lesson.getCreatedAt());
        return vo;
    }
}
