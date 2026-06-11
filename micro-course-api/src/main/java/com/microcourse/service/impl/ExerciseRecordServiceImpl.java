package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseQuestion;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Question;
import com.microcourse.entity.WrongQuestion;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ExerciseQuestionRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.ExerciseRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExerciseRecordServiceImpl implements ExerciseRecordService {

    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final QuestionRepository questionRepository;
    private final WrongQuestionRepository wrongQuestionRepository;
    private final ObjectMapper objectMapper;

    public ExerciseRecordServiceImpl(ExerciseRecordRepository exerciseRecordRepository,
                                       ExerciseRepository exerciseRepository,
                                       ExerciseQuestionRepository exerciseQuestionRepository,
                                       QuestionRepository questionRepository,
                                       WrongQuestionRepository wrongQuestionRepository,
                                       ObjectMapper objectMapper) {
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.questionRepository = questionRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ExerciseRecordVO submitAnswer(SubmitAnswerRequest request) {
        // 1. 查 exercise 是否存在
        Exercise exercise = exerciseRepository.selectById(request.getExerciseId());
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        // 2. 查 exercise_questions 列表
        LambdaQueryWrapper<ExerciseQuestion> eqWrapper = new LambdaQueryWrapper<>();
        eqWrapper.eq(ExerciseQuestion::getExerciseId, request.getExerciseId())
                .orderByAsc(ExerciseQuestion::getSortOrder);
        List<ExerciseQuestion> exerciseQuestions = exerciseQuestionRepository.selectList(eqWrapper);

        if (exerciseQuestions.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "练习没有题目");
        }

        // 构建 questionId -> ExerciseQuestion 的映射
        Map<Long, ExerciseQuestion> eqMap = new HashMap<>();
        for (ExerciseQuestion eq : exerciseQuestions) {
            eqMap.put(eq.getQuestionId(), eq);
        }

        // 3. 逐题批改
        List<SubmitAnswerRequest.AnswerItem> answerList = request.getAnswers();
        int totalScore = 0;
        List<GradingResult> gradingResults = new ArrayList<>();

        for (SubmitAnswerRequest.AnswerItem answerItem : answerList) {
            ExerciseQuestion eq = eqMap.get(answerItem.getQuestionId());
            if (eq == null) {
                continue;
            }

            Question question = questionRepository.selectById(answerItem.getQuestionId());
            if (question == null) {
                throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
            }

            GradingResult result = gradeQuestion(question, answerItem.getAnswer(), eq.getScore());
            gradingResults.add(result);
            totalScore += result.score;
        }

        // 4. 计算总分，判断是否通过
        boolean passed = totalScore >= exercise.getPassScore();

        // 5. 查当前用户第几次答题
        LambdaQueryWrapper<ExerciseRecord> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(ExerciseRecord::getUserId, request.getUserId())
                .eq(ExerciseRecord::getExerciseId, request.getExerciseId());
        long existingCount = exerciseRecordRepository.selectCount(countWrapper);
        int attemptNo = (int) existingCount + 1;

        // 6. 构建 answers JSON
        String answersJson;
        try {
            answersJson = objectMapper.writeValueAsString(gradingResults);
        } catch (JsonProcessingException e) {
            answersJson = "[]";
        }

        // 7. 插入 exercise_record
        ExerciseRecord record = new ExerciseRecord();
        record.setExerciseId(request.getExerciseId());
        record.setUserId(request.getUserId());
        record.setAttemptNo(attemptNo);
        record.setScore(totalScore);
        record.setTotalScore(exercise.getTotalScore());
        record.setPassed(passed);
        record.setDuration(request.getDuration());
        record.setAnswers(answersJson);
        record.setSubmittedAt(LocalDateTime.now());
        exerciseRecordRepository.insert(record);

        // 8. 错题入库
        for (GradingResult result : gradingResults) {
            if (!result.isCorrect && result.questionType != null &&
                !result.questionType.equals("SHORT_ANSWER") && !result.questionType.equals("ESSAY")) {
                upsertWrongQuestion(request.getUserId(), result.questionId, exercise.getCourseId());
            }
        }

        return convertToVO(record, exercise);
    }

    private GradingResult gradeQuestion(Question question, String userAnswer, Integer fullScore) {
        GradingResult result = new GradingResult();
        result.questionId = question.getId();
        result.questionType = question.getQuestionType();
        result.userAnswer = userAnswer;
        result.fullScore = fullScore;

        if (userAnswer == null) {
            result.score = 0;
            result.isCorrect = false;
            return result;
        }

        String correctAnswer = question.getAnswer();
        boolean isCorrect;

        switch (question.getQuestionType()) {
            case "SINGLE":
                // 单选题：直接字符串对比
                isCorrect = userAnswer.trim().equals(correctAnswer.trim());
                break;
            case "MULTIPLE":
                // 多选题：解析JSON数组，排序后对比
                isCorrect = compareMultipleAnswers(userAnswer, correctAnswer);
                break;
            case "JUDGE":
                // 判断题：直接对比
                isCorrect = userAnswer.trim().equals(correctAnswer.trim());
                break;
            case "FILL":
                // 填空题：trim后忽略大小写对比
                isCorrect = userAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
                break;
            case "SHORT_ANSWER":
            case "ESSAY":
                // 简答/论述：标记为待人工批改
                result.score = 0;
                result.isCorrect = null;
                result.needsManualGrading = true;
                return result;
            default:
                isCorrect = false;
        }

        result.isCorrect = isCorrect;
        result.score = isCorrect ? fullScore : 0;
        return result;
    }

    private boolean compareMultipleAnswers(String userAnswer, String correctAnswer) {
        try {
            List<String> userList = objectMapper.readValue(userAnswer, List.class);
            List<String> correctList = objectMapper.readValue(correctAnswer, List.class);

            List<String> sortedUser = new ArrayList<>(userList);
            List<String> sortedCorrect = new ArrayList<>(correctList);
            Collections.sort(sortedUser);
            Collections.sort(sortedCorrect);

            return sortedUser.equals(sortedCorrect);
        } catch (Exception e) {
            return false;
        }
    }

    private void upsertWrongQuestion(Long userId, Long questionId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId);
        WrongQuestion existing = wrongQuestionRepository.selectOne(wrapper);

        if (existing != null) {
            existing.setWrongCount(existing.getWrongCount() + 1);
            existing.setLastWrongAt(LocalDateTime.now());
            wrongQuestionRepository.updateById(existing);
        } else {
            WrongQuestion wrongQuestion = new WrongQuestion();
            wrongQuestion.setUserId(userId);
            wrongQuestion.setQuestionId(questionId);
            wrongQuestion.setCourseId(courseId);
            wrongQuestion.setWrongCount(1);
            wrongQuestion.setLastWrongAt(LocalDateTime.now());
            wrongQuestion.setCreatedAt(LocalDateTime.now());
            wrongQuestionRepository.insert(wrongQuestion);
        }
    }

    @Override
    public List<ExerciseRecordVO> getRecordsByExercise(Long exerciseId) {
        Exercise exercise = exerciseRepository.selectById(exerciseId);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getExerciseId, exerciseId)
                .orderByDesc(ExerciseRecord::getSubmittedAt);
        List<ExerciseRecord> records = exerciseRecordRepository.selectList(wrapper);

        return records.stream()
                .map(r -> convertToVO(r, exercise))
                .toList();
    }

    @Override
    public List<ExerciseRecordVO> getMyRecords(Long userId, Long exerciseId) {
        Exercise exercise = exerciseRepository.selectById(exerciseId);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getUserId, userId)
                .eq(ExerciseRecord::getExerciseId, exerciseId)
                .orderByDesc(ExerciseRecord::getSubmittedAt);
        List<ExerciseRecord> records = exerciseRecordRepository.selectList(wrapper);

        return records.stream()
                .map(r -> convertToVO(r, exercise))
                .toList();
    }

    @Override
    public ExerciseRecordVO getRecordById(Long id) {
        ExerciseRecord record = exerciseRecordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "答题记录不存在");
        }

        Exercise exercise = exerciseRepository.selectById(record.getExerciseId());
        return convertToVO(record, exercise);
    }

    private ExerciseRecordVO convertToVO(ExerciseRecord record, Exercise exercise) {
        ExerciseRecordVO vo = new ExerciseRecordVO();
        vo.setId(record.getId());
        vo.setExerciseId(record.getExerciseId());
        vo.setExerciseTitle(exercise != null ? exercise.getTitle() : null);
        vo.setUserId(record.getUserId());
        vo.setAttemptNo(record.getAttemptNo());
        vo.setScore(record.getScore());
        vo.setTotalScore(record.getTotalScore());
        vo.setPassed(record.getPassed());
        vo.setDuration(record.getDuration());
        vo.setAnswers(record.getAnswers());
        vo.setSubmittedAt(record.getSubmittedAt());
        return vo;
    }

    private static class GradingResult {
        Long questionId;
        String questionType;
        String userAnswer;
        Integer fullScore;
        Integer score;
        Boolean isCorrect;
        boolean needsManualGrading = false;
    }
}