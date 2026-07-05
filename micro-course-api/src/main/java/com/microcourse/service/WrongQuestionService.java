package com.microcourse.service;

import com.microcourse.dto.WrongQuestionVO;

import java.util.List;

public interface WrongQuestionService {

    /**
     * 获取当前用户的错题列表
     */
    List<WrongQuestionVO> getMyWrongQuestions(Long userId);

    /**
     * 获取当前用户在指定课程下的错题列表
     */
    List<WrongQuestionVO> getMyWrongQuestionsByCourse(Long userId, Long courseId);

    /**
     * P1I-026: 获取当前用户在指定章节下的错题列表
     */
    List<WrongQuestionVO> getMyWrongQuestionsByChapter(Long userId, Long chapterId);
}