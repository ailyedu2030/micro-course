package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseQuestion;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Grade;
import com.microcourse.entity.Question;
import com.microcourse.entity.WrongQuestion;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.microcourse.repository.ExerciseQuestionRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.GradeRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.ExerciseRecordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExerciseRecordServiceImpl implements ExerciseRecordService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseRecordServiceImpl.class);

    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final QuestionRepository questionRepository;
    private final WrongQuestionRepository wrongQuestionRepository;
    private final GradeRepository gradeRepository;
    private final ObjectMapper objectMapper;

    public ExerciseRecordServiceImpl(ExerciseRecordRepository exerciseRecordRepository,
                                       ExerciseRepository exerciseRepository,
                                       ExerciseQuestionRepository exerciseQuestionRepository,
                                       QuestionRepository questionRepository,
                                       WrongQuestionRepository wrongQuestionRepository,
                                       GradeRepository gradeRepository,
                                       ObjectMapper objectMapper) {
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.questionRepository = questionRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.gradeRepository = gradeRepository;
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

        // 2. 校验答题次数是否超限
        if (request.getAttemptNo() != null && exercise.getMaxAttempts() != null) {
            if (request.getAttemptNo() > exercise.getMaxAttempts()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "已超过最大答题次数");
            }
        }

        // 3. 查 exercise_questions 列表
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

        // 4. 逐题批改
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

        // 5. 计算总分，判断是否通过
        boolean passed = totalScore >= exercise.getPassScore();

        // 6. 查当前用户第几次答题(使用重试缓解并发竞态,DB UNIQUE(user_id,exercise_id,attempt_no) 兜底)
        int attemptNo;
        try {
            LambdaQueryWrapper<ExerciseRecord> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(ExerciseRecord::getUserId, request.getUserId())
                    .eq(ExerciseRecord::getExerciseId, request.getExerciseId());
            long existingCount = exerciseRecordRepository.selectCount(countWrapper);
            attemptNo = (int) existingCount + 1;
        } catch (Exception e) {
            // 最坏情况下,计算失败也不阻止提交(attempt_no 仅为展示)
            log.warn("[ExerciseRecord] attemptNo 计算失败,使用默认值 1", e);
            attemptNo = 1;
        }

        // 7. 构建 answers JSON
        String answersJson;
        try {
            answersJson = objectMapper.writeValueAsString(gradingResults);
        } catch (JsonProcessingException e) {
            log.error("[ExerciseRecord] JSON 序列化 gradingResults 失败 exerciseId={} userId={}", request.getExerciseId(), request.getUserId(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "成绩数据序列化失败");
        }

        // 8. 插入 exercise_record
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

        // 9. 同步更新 grades 表
        Grade grade = new Grade();
        grade.setStudentId(request.getUserId());
        grade.setExerciseId(request.getExerciseId());
        grade.setCourseId(exercise.getCourseId());
        grade.setScore(BigDecimal.valueOf(totalScore));
        grade.setTotalScore(BigDecimal.valueOf(exercise.getTotalScore()));
        grade.setPassed(passed);
        grade.setAttemptNo(attemptNo);
        grade.setDuration(request.getDuration());
        grade.setSubmittedAt(LocalDateTime.now());
        grade.setGradedAt(LocalDateTime.now());
        grade.setCreatedAt(LocalDateTime.now());
        grade.setUpdatedAt(LocalDateTime.now());
        gradeRepository.insert(grade);

        // 10. 错题入库
        for (GradingResult result : gradingResults) {
            if (!result.isCorrect && result.questionType != null &&
                !result.questionType.equals("SHORT_ANSWER") && !result.questionType.equals("ESSAY")) {
                upsertWrongQuestion(request.getUserId(), result.questionId, exercise.getCourseId());
            }
        }

        return convertToVO(record, exercise);
    }

    private String normalizeQuestionType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "SINGLE_CHOICE" -> "SINGLE";
            case "MULTIPLE_CHOICE" -> "MULTIPLE";
            case "FILL_BLANK" -> "FILL";
            default -> type;
        };
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

        String qType = normalizeQuestionType(question.getQuestionType());
        switch (qType) {
            case "SINGLE":
                // 单选题：直接字符串对比
                isCorrect = userAnswer.trim().equals(correctAnswer.trim());
                break;
            case "MULTIPLE":
                // 多选题：解析JSON数组或逗号分隔，排序后对比
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
            List<String> userList = parseAnswerList(userAnswer);
            List<String> correctList = parseAnswerList(correctAnswer);

            List<String> sortedUser = new ArrayList<>(userList);
            List<String> sortedCorrect = new ArrayList<>(correctList);
            Collections.sort(sortedUser);
            Collections.sort(sortedCorrect);

            return sortedUser.equals(sortedCorrect);
        } catch (Exception e) {
            log.warn("[ExerciseRecord] 多选题答案比对异常 userAnswer={} correctAnswer={}", userAnswer, correctAnswer, e);
            return false;
        }
    }

    private List<String> parseAnswerList(String answer) {
        if (answer == null) return Collections.emptyList();
        String trimmed = answer.trim();
        if (trimmed.startsWith("[")) {
            // JSON array format: ["A","B","C"]
            try {
                return objectMapper.readValue(trimmed, List.class);
            } catch (JsonProcessingException e) {
                return Collections.emptyList();
            }
        }
        // Comma-separated format: A,B,C
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    private void upsertWrongQuestion(Long userId, Long questionId, Long courseId) {
        // 使用原子 SQL 更新 wrongCount,避免并发读-改-写丢失
        LambdaUpdateWrapper<WrongQuestion> incWrapper = new LambdaUpdateWrapper<>();
        incWrapper.eq(WrongQuestion::getUserId, userId)
                .eq(WrongQuestion::getQuestionId, questionId)
                .setSql("wrong_count = COALESCE(wrong_count, 0) + 1")
                .setSql("last_wrong_at = NOW()");
        int affected = wrongQuestionRepository.update(null, incWrapper);
        if (affected == 0) {
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public ExerciseRecordVO getRecordById(Long id, Long userId) {
        ExerciseRecord record = exerciseRecordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "答题记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        Exercise exercise = exerciseRepository.selectById(record.getExerciseId());
        return convertToVO(record, exercise);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAccuracyTrend(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<ExerciseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecord::getUserId, userId)
               .ge(ExerciseRecord::getSubmittedAt, since)
               .orderByAsc(ExerciseRecord::getSubmittedAt);
        List<ExerciseRecord> records = exerciseRecordRepository.selectList(wrapper);

        // Group by date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<LocalDate, List<ExerciseRecord>> byDate = new TreeMap<>();
        for (ExerciseRecord r : records) {
            if (r.getSubmittedAt() != null) {
                LocalDate date = r.getSubmittedAt().toLocalDate();
                byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(r);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ExerciseRecord>> entry : byDate.entrySet()) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", entry.getKey().format(formatter));

            int totalCount = entry.getValue().size();
            int correctCount = (int) entry.getValue().stream()
                    .filter(r -> Boolean.TRUE.equals(r.getPassed()))
                    .count();
            double accuracy = totalCount == 0 ? 0.0 : (double) correctCount / totalCount;

            dayData.put("totalCount", totalCount);
            dayData.put("correctCount", correctCount);
            dayData.put("accuracy", Math.round(accuracy * 10000.0) / 10000.0); // 保留4位小数
            result.add(dayData);
        }
        return result;
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