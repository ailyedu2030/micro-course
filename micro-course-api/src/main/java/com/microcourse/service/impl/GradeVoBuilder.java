package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.GradeVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.Grade;
import com.microcourse.entity.User;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Grade VO 构造器 — 从 {@link GradeServiceImpl} 拆出，集中处理批量预加载 + 关联实体填充。
 *
 * <p>设计目标：
 * <ul>
 *   <li>消除 {@code GradeServiceImpl} 中的 800+ 行 ServiceImpl 体积</li>
 *   <li>提供可独立单元测试的纯函数式 API（构造函数注入依赖）</li>
 *   <li>不可变 record 传递批量预加载上下文，避免共享可变状态</li>
 * </ul>
 *
 * <p>使用：new GradeVoBuilder(courseRepo, userRepo, ...).buildAll(grades)
 *
 * @author refactor 2026-08-17
 */
public class GradeVoBuilder {

    private static final Logger log = LoggerFactory.getLogger(GradeVoBuilder.class);

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ObjectMapper objectMapper;

    public GradeVoBuilder(CourseRepository courseRepository,
                          UserRepository userRepository,
                          ExerciseRepository exerciseRepository,
                          EnrollmentRepository enrollmentRepository,
                          ExerciseRecordRepository exerciseRecordRepository,
                          ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量转换 — 预加载关联实体，避免 N+1。
     */
    public List<GradeVO> buildAll(List<Grade> grades) {
        if (grades.isEmpty()) {
            return List.of();
        }

        GradeIdSet idSet = collectGradeEntityIds(grades);
        GradeLookupContext ctx = batchLoadGradeContext(grades, idSet);

        return grades.stream()
                .map(grade -> buildGradeVO(grade, ctx))
                .collect(Collectors.toList());
    }

    /**
     * 单条转换委托 buildAll，消除 N+1。
     */
    public GradeVO buildSingle(Grade grade) {
        List<GradeVO> vos = buildAll(List.of(grade));
        return vos.isEmpty() ? new GradeVO() : vos.get(0);
    }

    /**
     * 收集 Grade 中出现的实体 ID（用于后续批量查询）。
     */
    private GradeIdSet collectGradeEntityIds(List<Grade> grades) {
        Set<Long> courseIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> exerciseIds = new HashSet<>();
        for (Grade g : grades) {
            if (g.getCourseId() != null) courseIds.add(g.getCourseId());
            if (g.getUserId() != null) userIds.add(g.getUserId());
            if (g.getGradedBy() != null) userIds.add(g.getGradedBy());
            if (g.getExerciseId() != null) exerciseIds.add(g.getExerciseId());
        }
        return new GradeIdSet(courseIds, userIds, exerciseIds);
    }

    /**
     * 批量预加载所有关联实体。
     */
    private GradeLookupContext batchLoadGradeContext(List<Grade> grades, GradeIdSet idSet) {
        Map<Long, Course> courseMap = selectMapByIds(idSet.courseIds(), courseRepository::selectBatchIds, Course::getId);
        Map<Long, User> userMap = selectMapByIds(idSet.userIds(), userRepository::selectBatchIds, User::getId);
        Map<Long, Exercise> exerciseMap = selectMapByIds(idSet.exerciseIds(), exerciseRepository::selectBatchIds, Exercise::getId);
        Map<Long, Map<Long, Long>> enrollmentIdMap = batchLoadEnrollmentIdMap(idSet);
        Map<ExerciseRecordKey, ExerciseRecord> recordMap = batchLoadExerciseRecordMap(grades);
        return new GradeLookupContext(courseMap, userMap, exerciseMap, enrollmentIdMap, recordMap);
    }

    /**
     * 通用批量查询助手：IDs 为空时直接返回空 Map，避免无谓 DB 调用。
     */
    private <T> Map<Long, T> selectMapByIds(Set<Long> ids,
                                            Function<Collection<Long>, List<T>> loader,
                                            Function<T, Long> idExtractor) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return loader.apply(ids).stream().collect(Collectors.toMap(idExtractor, e -> e));
    }

    /**
     * Phase 6 P0: 批量查询 (courseId, userId) → enrollmentId。
     */
    private Map<Long, Map<Long, Long>> batchLoadEnrollmentIdMap(GradeIdSet idSet) {
        Map<Long, Map<Long, Long>> enrollmentIdMap = new HashMap<>();
        if (idSet.courseIds().isEmpty() || idSet.userIds().isEmpty()) {
            return enrollmentIdMap;
        }
        LambdaQueryWrapper<Enrollment> ew = new LambdaQueryWrapper<>();
        ew.in(Enrollment::getCourseId, idSet.courseIds())
          .in(Enrollment::getUserId, idSet.userIds())
          .isNull(Enrollment::getDeletedAt);
        for (Enrollment e : enrollmentRepository.selectList(ew)) {
            enrollmentIdMap
                    .computeIfAbsent(e.getCourseId(), k -> new HashMap<>())
                    .put(e.getUserId(), e.getId());
        }
        return enrollmentIdMap;
    }

    /**
     * P1-C-Grade-N+1: 批量预加载 ExerciseRecord（按 id 倒序 → putIfAbsent 保留最近一条）。
     */
    private Map<ExerciseRecordKey, ExerciseRecord> batchLoadExerciseRecordMap(List<Grade> grades) {
        List<ExerciseRecordKey> recordKeys = grades.stream()
                .filter(g -> g.getExerciseId() != null && g.getUserId() != null)
                .map(g -> new ExerciseRecordKey(
                        g.getUserId(),
                        g.getExerciseId(),
                        g.getAttemptNo() != null ? g.getAttemptNo() : 0))
                .collect(Collectors.toList());

        Map<ExerciseRecordKey, ExerciseRecord> recordMap = new HashMap<>();
        if (recordKeys.isEmpty()) {
            return recordMap;
        }

        Set<Long> recExerciseIds = recordKeys.stream().map(k -> k.exerciseId).collect(Collectors.toSet());
        Set<Long> recUserIds = recordKeys.stream().map(k -> k.userId).collect(Collectors.toSet());
        LambdaQueryWrapper<ExerciseRecord> recWrapper = new LambdaQueryWrapper<>();
        recWrapper.in(ExerciseRecord::getExerciseId, recExerciseIds)
                 .in(ExerciseRecord::getUserId, recUserIds)
                 .isNull(ExerciseRecord::getDeletedAt)
                 .orderByDesc(ExerciseRecord::getId);

        for (ExerciseRecord r : exerciseRecordRepository.selectList(recWrapper)) {
            ExerciseRecordKey key = new ExerciseRecordKey(
                    r.getUserId(),
                    r.getExerciseId(),
                    r.getAttemptNo() != null ? r.getAttemptNo() : 0);
            recordMap.putIfAbsent(key, r);
        }
        return recordMap;
    }

    /**
     * 构建单个 GradeVO。
     */
    private GradeVO buildGradeVO(Grade grade, GradeLookupContext ctx) {
        GradeVO vo = new GradeVO();
        copyBasicGradeFields(grade, vo);
        attachExerciseRecordToVO(grade, ctx.recordMap(), vo);
        attachRelatedNamesToVO(grade, ctx, vo);
        attachEnrollmentIdToVO(grade, ctx.enrollmentIdMap(), vo);
        return vo;
    }

    private void copyBasicGradeFields(Grade grade, GradeVO vo) {
        vo.setId(grade.getId());
        vo.setCourseId(grade.getCourseId());
        vo.setUserId(grade.getUserId());
        vo.setExerciseId(grade.getExerciseId());
        vo.setScore(grade.getScore());
        vo.setTotalScore(grade.getTotalScore());
        vo.setPassed(grade.getPassed());
        vo.setAttemptNo(grade.getAttemptNo());
        vo.setDuration(grade.getDuration());
        vo.setSubmittedAt(grade.getSubmittedAt());
        vo.setGradedBy(grade.getGradedBy());
        vo.setGradedAt(grade.getGradedAt());
        vo.setCreatedAt(grade.getCreatedAt());
        vo.setComment(grade.getComment());
    }

    /**
     * P1-C 修复 (2026-08-04): 关联练习作答记录，识别"待人工批改"的主观题。
     */
    private void attachExerciseRecordToVO(Grade grade,
                                          Map<ExerciseRecordKey, ExerciseRecord> recordMap,
                                          GradeVO vo) {
        if (grade.getExerciseId() == null || grade.getUserId() == null) {
            return;
        }
        ExerciseRecordKey key = new ExerciseRecordKey(
                grade.getUserId(),
                grade.getExerciseId(),
                grade.getAttemptNo() != null ? grade.getAttemptNo() : 0);
        ExerciseRecord record = recordMap.get(key);
        if (record == null) {
            return;
        }
        try {
            vo.setRecordId(record.getId());
            boolean needsManual = Boolean.TRUE.equals(record.getNeedsManualGrading());
            vo.setNeedsManualGrading(needsManual);
            if (needsManual && record.getAnswers() != null && !record.getAnswers().isBlank()) {
                vo.setPendingQuestions(parsePendingQuestions(record.getAnswers(), grade));
            }
        } catch (Exception e) {
            log.warn("[Grade] 关联作答记录解析失败 gradeId={}", grade.getId(), e);
        }
    }

    /**
     * 从 ExerciseRecord.answers JSON 中抽取"待人工批改"的主观题。
     */
    private List<Map<String, Object>> parsePendingQuestions(String answersJson, Grade grade) throws Exception {
        List<Map<String, Object>> answerList = objectMapper.readValue(
                answersJson, new TypeReference<List<Map<String, Object>>>() {});
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Map<String, Object> answer : answerList) {
            if (!Boolean.TRUE.equals(answer.get("needsManualGrading"))) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            Object qidObj = answer.get("questionId");
            item.put("questionId", qidObj instanceof Number ? ((Number) qidObj).longValue() : null);
            item.put("studentAnswer", answer.get("answer"));
            item.put("maxScore", grade.getTotalScore() != null ? grade.getTotalScore().intValue() : 0);
            pending.add(item);
        }
        return pending;
    }

    /**
     * 填充课程名 / 学生名 / 试卷标题 / 批改人姓名。
     */
    private void attachRelatedNamesToVO(Grade grade, GradeLookupContext ctx, GradeVO vo) {
        Course course = ctx.courseMap().get(grade.getCourseId());
        if (course != null) {
            vo.setCourseName(course.getTitle());
        }
        User student = ctx.userMap().get(grade.getUserId());
        if (student != null) {
            vo.setStudentName(displayNameOf(student));
        }
        Exercise exercise = ctx.exerciseMap().get(grade.getExerciseId());
        if (exercise != null) {
            vo.setExerciseTitle(exercise.getTitle());
        }
        User grader = ctx.userMap().get(grade.getGradedBy());
        if (grader != null) {
            vo.setGradedByName(displayNameOf(grader));
        }
    }

    /**
     * Phase 6 P0: 填充 enrollmentId。
     */
    private void attachEnrollmentIdToVO(Grade grade,
                                         Map<Long, Map<Long, Long>> enrollmentIdMap,
                                         GradeVO vo) {
        if (grade.getCourseId() == null || grade.getUserId() == null) {
            return;
        }
        Map<Long, Long> byCourse = enrollmentIdMap.get(grade.getCourseId());
        if (byCourse == null) {
            return;
        }
        Long eid = byCourse.get(grade.getUserId());
        if (eid != null) {
            vo.setEnrollmentId(eid);
        }
    }

    /**
     * 用户展示名：优先 realName，回退 username。
     */
    private String displayNameOf(User user) {
        return user.getRealName() != null ? user.getRealName() : user.getUsername();
    }

    /**
     * ID 收集快照。
     */
    public record GradeIdSet(Set<Long> courseIds, Set<Long> userIds, Set<Long> exerciseIds) {}

    /**
     * 批量预加载上下文。
     */
    public record GradeLookupContext(
            Map<Long, Course> courseMap,
            Map<Long, User> userMap,
            Map<Long, Exercise> exerciseMap,
            Map<Long, Map<Long, Long>> enrollmentIdMap,
            Map<ExerciseRecordKey, ExerciseRecord> recordMap) {}

    /**
     * ExerciseRecord 复合键，用于批量查询结果去重。
     */
    public static final class ExerciseRecordKey {
        final Long userId;
        final Long exerciseId;
        final int attemptNo;

        public ExerciseRecordKey(Long userId, Long exerciseId, int attemptNo) {
            this.userId = userId;
            this.exerciseId = exerciseId;
            this.attemptNo = attemptNo;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExerciseRecordKey that = (ExerciseRecordKey) o;
            return attemptNo == that.attemptNo
                    && Objects.equals(userId, that.userId)
                    && Objects.equals(exerciseId, that.exerciseId);
        }

        @Override public int hashCode() {
            return Objects.hash(userId, exerciseId, attemptNo);
        }
    }
}