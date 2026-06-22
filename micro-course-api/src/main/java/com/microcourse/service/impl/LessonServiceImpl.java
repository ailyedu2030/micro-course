package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.lesson.LessonVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
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
     * C2-5 修复：使用 CASE WHEN SQL 一次更新所有课时排序，替代逐条 updateById 循环。
     * 对 N 条排序请求从 N 次 UPDATE 降为 1 次 UPDATE，大幅减少 DB 往返。
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

        // 构建 CASE WHEN SQL: sort_order = CASE id WHEN 1 THEN 0 WHEN 2 THEN 1 END
        StringBuilder sortCase = new StringBuilder("CASE id ");
        StringBuilder chapterCase = new StringBuilder("CASE id ");
        for (LessonVO.SortItem item : items) {
            sortCase.append("WHEN ").append(item.getId()).append(" THEN ").append(item.getSortOrder()).append(" ");
            chapterCase.append("WHEN ").append(item.getId()).append(" THEN ").append(item.getChapterId()).append(" ");
        }
        sortCase.append("END");
        chapterCase.append("END");

        LambdaUpdateWrapper<Lesson> wrapper = new LambdaUpdateWrapper<>();
        wrapper.setSql("sort_order = " + sortCase.toString()
                + ", chapter_id = " + chapterCase.toString()
                + ", updated_at = NOW()");
        wrapper.in(Lesson::getId, ids);
        lessonRepository.update(null, wrapper);
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
