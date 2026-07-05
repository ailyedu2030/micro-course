package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.WrongQuestionVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Question;
import com.microcourse.entity.QuestionChapter;
import com.microcourse.entity.WrongQuestion;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.QuestionChapterRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.WrongQuestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WrongQuestionServiceImpl implements WrongQuestionService {

    private final WrongQuestionRepository wrongQuestionRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final QuestionChapterRepository questionChapterRepository;
    private final CourseChapterRepository courseChapterRepository;

    public WrongQuestionServiceImpl(WrongQuestionRepository wrongQuestionRepository,
                                     QuestionRepository questionRepository,
                                     CourseRepository courseRepository,
                                     QuestionChapterRepository questionChapterRepository,
                                     CourseChapterRepository courseChapterRepository) {
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.questionChapterRepository = questionChapterRepository;
        this.courseChapterRepository = courseChapterRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<WrongQuestionVO> getMyWrongQuestionsByChapter(Long userId, Long chapterId) {
        // 先通过 question_chapters 关联表查询该章节下的所有题目 ID
        List<QuestionChapter> qcs = questionChapterRepository.selectList(
                new LambdaQueryWrapper<QuestionChapter>()
                        .eq(QuestionChapter::getChapterId, chapterId));
        if (qcs.isEmpty()) {
            return List.of();
        }
        Set<Long> chapterQuestionIds = qcs.stream()
                .map(QuestionChapter::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
                .in(WrongQuestion::getQuestionId, chapterQuestionIds)
                .orderByDesc(WrongQuestion::getLastWrongAt);
        List<WrongQuestion> wrongQuestions = wrongQuestionRepository.selectList(wrapper);

        return convertBatchToVO(wrongQuestions);
    }

    /**
     * N+1 修复：批量预加载 Question、Course、QuestionChapter 和 CourseChapter，
     * 一次 selectBatchIds 替代每条错题的逐条查询。
     * P1-3: 通过 question_chapters 关联表填充 chapterId 和 chapterTitle。
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

        // P0 修复: 批量预加载 QuestionChapter 映射（questionId → List<chapterId>），
        // 使用 computeIfAbsent 保留多章节关联
        Map<Long, List<Long>> questionChaptersMap = new HashMap<>();
        Map<Long, CourseChapter> chapterMap = Collections.emptyMap();
        if (!questionIds.isEmpty()) {
            List<QuestionChapter> qcs = questionChapterRepository.selectList(
                    new LambdaQueryWrapper<QuestionChapter>().in(QuestionChapter::getQuestionId, questionIds));
            for (QuestionChapter qc : qcs) {
                questionChaptersMap.computeIfAbsent(qc.getQuestionId(), k -> new ArrayList<>()).add(qc.getChapterId());
            }
            // 批量预加载 CourseChapter 以填充 chapterTitle
            Set<Long> allChapterIds = questionChaptersMap.values().stream()
                    .flatMap(List::stream)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!allChapterIds.isEmpty()) {
                chapterMap = courseChapterRepository.selectBatchIds(allChapterIds).stream()
                        .collect(Collectors.toMap(CourseChapter::getId, ch -> ch));
            }
        }

        final Map<Long, CourseChapter> finalChapterMap = chapterMap;
        return wrongQuestions.stream()
                .map(wq -> convertToVO(wq, questionMap, courseMap, questionChaptersMap, finalChapterMap))
                .toList();
    }

    private WrongQuestionVO convertToVO(WrongQuestion wrongQuestion, Map<Long, Question> questionMap,
                                         Map<Long, Course> courseMap,
                                         Map<Long, List<Long>> questionChaptersMap,
                                         Map<Long, CourseChapter> chapterMap) {
        WrongQuestionVO vo = new WrongQuestionVO();
        vo.setId(wrongQuestion.getId());
        vo.setUserId(wrongQuestion.getUserId());
        vo.setQuestionId(wrongQuestion.getQuestionId());
        vo.setCourseId(wrongQuestion.getCourseId());
        vo.setWrongCount(wrongQuestion.getWrongCount());
        vo.setWatchPosition(wrongQuestion.getWatchPosition());
        vo.setLastWrongAt(wrongQuestion.getLastWrongAt());
        vo.setCreatedAt(wrongQuestion.getCreatedAt());

        // 使用预加载的题目 Map
        Question question = questionMap.get(wrongQuestion.getQuestionId());
        if (question != null) {
            vo.setQuestionType(question.getQuestionType());
            vo.setQuestionContent(question.getContent());
            vo.setCorrectAnswer(question.getAnswer());  // P0-3: 正确答案
        }

        // P0-3: 填充课程标题
        if (wrongQuestion.getCourseId() != null) {
            Course course = courseMap.get(wrongQuestion.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }

        // P0 修复: 一题多章节时取第一个章节关联（最相关的）
        Long firstChapterId = questionChaptersMap.getOrDefault(wrongQuestion.getQuestionId(), Collections.emptyList())
                .stream().findFirst().orElse(null);
        if (firstChapterId != null) {
            vo.setChapterId(firstChapterId);
            CourseChapter chapter = chapterMap.get(firstChapterId);
            if (chapter != null) {
                vo.setChapterTitle(chapter.getTitle());
            }
        }

        return vo;
    }
}