package com.microcourse.service;

import com.microcourse.dto.lesson.LessonVO;
import java.util.List;

public interface LessonService {
    LessonVO create(Long chapterId, Long courseId, String title, String lessonType);
    LessonVO update(Long id, String title, Integer duration, Boolean visible);
    void delete(Long id);
    void sort(List<LessonVO.SortItem> items);
    LessonVO getById(Long id);
    List<LessonVO> getByChapter(Long chapterId);
}
