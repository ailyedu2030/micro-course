package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.WrongQuestionVO;
import com.microcourse.entity.Question;
import com.microcourse.entity.WrongQuestion;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.WrongQuestionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WrongQuestionServiceImpl implements WrongQuestionService {

    private final WrongQuestionRepository wrongQuestionRepository;
    private final QuestionRepository questionRepository;

    public WrongQuestionServiceImpl(WrongQuestionRepository wrongQuestionRepository,
                                     QuestionRepository questionRepository) {
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    public List<WrongQuestionVO> getMyWrongQuestions(Long userId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
                .orderByDesc(WrongQuestion::getLastWrongAt);
        List<WrongQuestion> wrongQuestions = wrongQuestionRepository.selectList(wrapper);

        return wrongQuestions.stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public List<WrongQuestionVO> getMyWrongQuestionsByCourse(Long userId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getCourseId, courseId)
                .orderByDesc(WrongQuestion::getLastWrongAt);
        List<WrongQuestion> wrongQuestions = wrongQuestionRepository.selectList(wrapper);

        return wrongQuestions.stream()
                .map(this::convertToVO)
                .toList();
    }

    private WrongQuestionVO convertToVO(WrongQuestion wrongQuestion) {
        WrongQuestionVO vo = new WrongQuestionVO();
        vo.setId(wrongQuestion.getId());
        vo.setUserId(wrongQuestion.getUserId());
        vo.setQuestionId(wrongQuestion.getQuestionId());
        vo.setCourseId(wrongQuestion.getCourseId());
        vo.setWrongCount(wrongQuestion.getWrongCount());
        vo.setLastWrongAt(wrongQuestion.getLastWrongAt());
        vo.setCreatedAt(wrongQuestion.getCreatedAt());

        // 填充题目信息
        Question question = questionRepository.selectById(wrongQuestion.getQuestionId());
        if (question != null) {
            vo.setQuestionType(question.getQuestionType());
            vo.setQuestionContent(question.getContent());
        }

        return vo;
    }
}