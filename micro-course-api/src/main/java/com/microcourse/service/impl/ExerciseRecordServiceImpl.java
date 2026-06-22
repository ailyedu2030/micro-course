package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.Course;
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
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.GradeRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.WrongQuestionRepository;
import com.microcourse.service.ExerciseRecordService;
import com.microcourse.service.NotificationService;
import com.microcourse.enums.NotificationType;
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
    private final CourseRepository courseRepository;
    private final NotificationService notificationService;

    public ExerciseRecordServiceImpl(ExerciseRecordRepository exerciseRecordRepository,
                                       ExerciseRepository exerciseRepository,
                                       ExerciseQuestionRepository exerciseQuestionRepository,
                                       QuestionRepository questionRepository,
                                       WrongQuestionRepository wrongQuestionRepository,
                                       GradeRepository gradeRepository,
                                       ObjectMapper objectMapper,
                                       CourseRepository courseRepository,
                                       NotificationService notificationService) {
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.questionRepository = questionRepository;
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.gradeRepository = gradeRepository;
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 4. 批量预加载所有题目 -> 逐题批改(N+1→1+1)
        List<SubmitAnswerRequest.AnswerItem> answerList = request.getAnswers();
        int totalScore = 0;
        List<GradingResult> gradingResults = new ArrayList<>();

        // 收集所有 questionId 批量查询
        List<Long> allQuestionIds = answerList.stream()
                .map(SubmitAnswerRequest.AnswerItem::getQuestionId)
                .filter(eqMap::containsKey)
                .collect(java.util.stream.Collectors.toList());
        Map<Long, Question> questionMap = new HashMap<>();
        if (!allQuestionIds.isEmpty()) {
            questionRepository.selectBatchIds(allQuestionIds)
                    .forEach(q -> questionMap.put(q.getId(), q));
        }

        for (SubmitAnswerRequest.AnswerItem answerItem : answerList) {
            ExerciseQuestion eq = eqMap.get(answerItem.getQuestionId());
            if (eq == null) continue;

            Question question = questionMap.get(answerItem.getQuestionId());
            if (question == null) {
                throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
            }

            GradingResult result = gradeQuestion(question, answerItem.getAnswer(), eq.getScore());
            gradingResults.add(result);
            totalScore += result.score;
        }

        // 5. 计算总分，判断是否通过
        boolean passed = totalScore >= exercise.getPassScore();

        // 6. 计算 attemptNo:用 MAX(attempt_no) 在事务内获取当前最大值 +1,并捕获 DuplicateKeyException 兜底(CON-004 修复)
        int attemptNo;
        try {
            QueryWrapper<ExerciseRecord> maxWrapper = new QueryWrapper<>();
            maxWrapper.eq("user_id", request.getUserId())
                    .eq("exercise_id", request.getExerciseId())
                    .select("COALESCE(MAX(attempt_no), 0) AS max_no");
            Map<String, Object> maxRow = exerciseRecordRepository.selectMaps(maxWrapper).stream()
                    .findFirst().orElse(java.util.Collections.singletonMap("max_no", 0));
            Object maxVal = maxRow.get("max_no");
            long currentMax = (maxVal instanceof Number n) ? n.longValue() : 0L;
            attemptNo = (int) currentMax + 1;
        } catch (Exception e) {
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
        try {
            exerciseRecordRepository.insert(record);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            // DA-1 修复:V43 UNIQUE 约束兜底,并发 submit 时第二个 insert 抛唯一约束冲突
            // 降级:重新查询已有记录并返回,避免整事务回滚导致用户答题记录丢失
            log.warn("[ExerciseRecord] 并发 submit 命中 UNIQUE,降级返回已有记录 userId={} exerciseId={} attemptNo={}",
                    request.getUserId(), request.getExerciseId(), attemptNo);
            ExerciseRecord existing = exerciseRecordRepository.selectOne(
                    new LambdaQueryWrapper<ExerciseRecord>()
                            .eq(ExerciseRecord::getUserId, request.getUserId())
                            .eq(ExerciseRecord::getExerciseId, request.getExerciseId())
                            .eq(ExerciseRecord::getAttemptNo, attemptNo));
            if (existing != null) return convertToVO(existing, exercise);
            throw dupEx;
        }

        // 9. 同步更新 grades 表
        Grade grade = new Grade();
        grade.setUserId(request.getUserId());
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
        // ★ Round 8-4 修复(P0)：成绩并发防护。grades 有部分唯一约束
        // uk_grade_user_exercise(user_id, course_id, exercise_id, attempt_no)，并发 submit 时
        // attemptNo 可能重复命中 UK 把答题主流程打成 500。这里先按唯一键预检查（命中则幂等跳过），
        // 极端并发再兜底捕获 DuplicateKeyException，确保边界 case 友好处理、绝不抛 500 给用户。
        boolean gradeExists = gradeRepository.selectCount(
                new LambdaQueryWrapper<Grade>()
                        .eq(Grade::getUserId, request.getUserId())
                        .eq(Grade::getCourseId, exercise.getCourseId())
                        .eq(Grade::getExerciseId, request.getExerciseId())
                        .eq(Grade::getAttemptNo, attemptNo)) > 0;
        if (!gradeExists) {
            try {
                gradeRepository.insert(grade);
            } catch (org.springframework.dao.DuplicateKeyException dupEx) {
                // 并发竞态：对端已写入同一成绩，幂等忽略，避免整事务因 500 中断
                log.warn("[Grade] 并发命中唯一约束，幂等忽略 userId={} exerciseId={} attemptNo={}",
                        request.getUserId(), request.getExerciseId(), attemptNo);
            }
        }

        // 10. 错题入库(批量预检查,减少单独查询)
        Set<Long> wrongQuestionIds = gradingResults.stream()
                .filter(r -> Boolean.FALSE.equals(r.isCorrect) && r.questionType != null
                        && !r.questionType.equals("SHORT_ANSWER") && !r.questionType.equals("ESSAY"))
                .map(r -> r.questionId)
                .collect(java.util.stream.Collectors.toSet());
        if (!wrongQuestionIds.isEmpty()) {
            // 批量查询已存在的错题
            LambdaQueryWrapper<WrongQuestion> existingWQ = new LambdaQueryWrapper<>();
            existingWQ.eq(WrongQuestion::getUserId, request.getUserId())
                    .in(WrongQuestion::getQuestionId, wrongQuestionIds);
            Set<Long> existingIds = wrongQuestionRepository.selectList(existingWQ).stream()
                    .map(WrongQuestion::getQuestionId)
                    .collect(java.util.stream.Collectors.toSet());
            // 增量更新已存在的
            if (!existingIds.isEmpty()) {
                wrongQuestionRepository.update(null,
                        new LambdaUpdateWrapper<WrongQuestion>()
                                .eq(WrongQuestion::getUserId, request.getUserId())
                                .in(WrongQuestion::getQuestionId, existingIds)
                                .setSql("wrong_count = wrong_count + 1")
                                .setSql("last_wrong_at = NOW()"));
            }
            // 插入不存在的 — 捕获 DuplicateKeyException 避免并发时整事务回滚(CON-005 修复)
            wrongQuestionIds.stream()
                    .filter(qid -> !existingIds.contains(qid))
                    .forEach(qid -> {
                        WrongQuestion wq = new WrongQuestion();
                        wq.setUserId(request.getUserId());
                        wq.setQuestionId(qid);
                        wq.setCourseId(exercise.getCourseId());
                        wq.setWrongCount(1);
                        wq.setLastWrongAt(LocalDateTime.now());
                        wq.setCreatedAt(LocalDateTime.now());
                        try {
                            wrongQuestionRepository.insert(wq);
                        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
                            // 并发插入:对端已先插入成功,转为原子 UPDATE +1 兜底
                            log.debug("[WrongQuestion] 并发命中唯一约束,转为原子累加 userId={} qId={}", request.getUserId(), qid);
                            wrongQuestionRepository.update(null,
                                    new LambdaUpdateWrapper<WrongQuestion>()
                                            .eq(WrongQuestion::getUserId, request.getUserId())
                                            .eq(WrongQuestion::getQuestionId, qid)
                                            .setSql("wrong_count = wrong_count + 1")
                                            .setSql("last_wrong_at = NOW()"));
                        }
                    });
        }

        // Phase B-2 (P0-7)：练习提交批改完成后，异步通知课程教师。
        // Exercise 无 teacherId 字段，经 courseId → course → teacherId 解析；@Async 不阻塞答题主流程。
        Course notifyCourse = exercise.getCourseId() != null
                ? courseRepository.selectById(exercise.getCourseId()) : null;
        if (notifyCourse != null && notifyCourse.getTeacherId() != null) {
            notificationService.notifyAsync(
                    notifyCourse.getTeacherId(),
                    NotificationType.EXERCISE_GRADED,
                    "学生完成练习",
                    "有学生完成了练习《" + exercise.getTitle() + "》，得分 " + totalScore,
                    exercise.getId());
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

        // P0 防御: correctAnswer 为 null 时视为未设置标准答案,标记为待人工批改
        if (correctAnswer == null) {
            result.score = 0;
            result.isCorrect = null;
            result.needsManualGrading = true;
            return result;
        }

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
                // ERR-006 修复:不再静默吞掉,记录日志便于排查数据异常
                log.warn("[ExerciseRecord] 答案 JSON 解析失败,降级为空列表 answer={}", answer, e);
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

    @Override
    @Transactional(readOnly = true)
    public int getAttemptCount(Long userId, Long exerciseId) {
        QueryWrapper<ExerciseRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("exercise_id", exerciseId)
                .select("COALESCE(MAX(attempt_no), 0) AS max_no");
        Map<String, Object> row = exerciseRecordRepository.selectMaps(wrapper).stream()
                .findFirst().orElse(Collections.singletonMap("max_no", 0));
        Object maxVal = row.get("max_no");
        return (maxVal instanceof Number n) ? n.intValue() : 0;
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

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
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