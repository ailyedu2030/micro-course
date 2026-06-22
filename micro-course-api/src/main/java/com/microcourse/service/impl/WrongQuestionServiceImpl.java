package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.WrongQuestionVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Question;
import com.microcourse.entity.WrongQuestion;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.WrongQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WrongQuestionServiceImpl implements WrongQuestionService {

    private final WrongQuestionRepository wrongQuestionRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;

    public WrongQuestionServiceImpl(WrongQuestionRepository wrongQuestionRepository,
                                     QuestionRepository questionRepository,
                                     CourseRepository courseRepository) {
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WrongQuestionVO> getMyWrongQuestions(Long userId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
                .orderByDesc(WrongQuestion::getLastWrongAt);
        List<WrongQuestion> wrongQuestions = wrongQuestionRepository.selectList(wrapper);

        return convertBatchToVO(wrongQuestions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WrongQuestionVO> getMyWrongQuestionsByCourse(Long userId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getCourseId, courseId)
                .orderByDesc(WrongQuestion::getLastWrongAt);
        List<WrongQuestion> wrongQuestions = wrongQuestionRepository.selectList(wrapper);

        return convertBatchToVO(wrongQuestions);
    }

    /**
     * N+1 修复：批量预加载 Question 和 Course，一次 selectBatchIds 替代每条错题的 selectById。
     */
    private List<WrongQuestionVO> convertBatchToVO(List<WrongQuestion> wrongQuestions) {
        if (wrongQuestions.isEmpty()) {
            return List.of();
        }
        Set<Long> questionIds = wrongQuestions.stream()
                .map(WrongQuestion::getQuestionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Collections.emptyMap()
                : questionRepository.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // P0-3: 批量预加载 Course 以填充 courseTitle
        Set<Long> courseIds = wrongQuestions.stream()
                .map(WrongQuestion::getCourseId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Course> courseMap = courseIds.isEmpty() ? Collections.emptyMap()
                : courseRepository.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        return wrongQuestions.stream()
                .map(wq -> convertToVO(wq, questionMap, courseMap))
                .toList();
    }

    private WrongQuestionVO convertToVO(WrongQuestion wrongQuestion, Map<Long, Question> questionMap, Map<Long, Course> courseMap) {
        WrongQuestionVO vo = new WrongQuestionVO();
        vo.setId(wrongQuestion.getId());
        vo.setUserId(wrongQuestion.getUserId());
        vo.setQuestionId(wrongQuestion.getQuestionId());
        vo.setCourseId(wrongQuestion.getCourseId());
        vo.setWrongCount(wrongQuestion.getWrongCount());
        vo.setLastWrongAt(wrongQuestion.getLastWrongAt());
        vo.setCreatedAt(wrongQuestion.getCreatedAt());

        // 使用预加载的题目 Map
        Question question = questionMap.get(wrongQuestion.getQuestionId());
        if (question != null) {
            vo.setQuestionType(question.getQuestionType());
            vo.setQuestionContent(question.getContent());
            vo.setContent(question.getContent());       // P0-3: content 冗余字段
            vo.setCorrectAnswer(question.getAnswer());  // P0-3: 正确答案
        }

        // P0-3: 填充课程标题
        if (wrongQuestion.getCourseId() != null) {
            Course course = courseMap.get(wrongQuestion.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }

        return vo;
    }
}