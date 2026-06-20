package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.lesson.LessonVO;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Lesson;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
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

    public LessonServiceImpl(LessonRepository lessonRepository) {
        this.lessonRepository = lessonRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonVO create(Long chapterId, Long courseId, String title, String lessonType) {
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
        if (lesson == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
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
            lessonRepository.deleteById(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sort(List<LessonVO.SortItem> items) {
        if (items == null) return;
        for (LessonVO.SortItem item : items) {
            Lesson lesson = lessonRepository.selectById(item.getId());
            if (lesson != null) {
                lesson.setChapterId(item.getChapterId());
                lesson.setSortOrder(item.getSortOrder());
                lesson.setUpdatedAt(LocalDateTime.now());
                lessonRepository.updateById(lesson);
            }
        }
    }

    @Override
    public LessonVO getById(Long id) {
        Lesson lesson = lessonRepository.selectById(id);
        if (lesson == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
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
