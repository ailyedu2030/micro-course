package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.ExerciseCreateRequest;
import com.microcourse.dto.ExerciseUpdateRequest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseChapter;
import com.microcourse.entity.Question;
import com.microcourse.entity.ExerciseQuestion;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseChapterRepository;
import com.microcourse.repository.ExerciseQuestionRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final QuestionRepository questionRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final CourseRepository courseRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final ExerciseChapterRepository exerciseChapterRepository;
    private final EnrollmentRepository enrollmentRepository;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository,
                               QuestionRepository questionRepository,
                               ExerciseQuestionRepository exerciseQuestionRepository,
                               CourseRepository courseRepository,
                               CourseChapterRepository courseChapterRepository,
                               ExerciseChapterRepository exerciseChapterRepository,
                               EnrollmentRepository enrollmentRepository) {
        this.exerciseRepository = exerciseRepository;
        this.questionRepository = questionRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
        this.exerciseChapterRepository = exerciseChapterRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExerciseVO create(ExerciseCreateRequest request) {
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "练习题目不能为空");
        }
        // Owner check: only course teacher or ADMIN can create exercise
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        Exercise exercise = new Exercise();
        exercise.setCourseId(request.getCourseId());
        exercise.setChapterId(request.getChapterId());
        exercise.setTitle(request.getTitle());
        exercise.setPassScore(request.getPassScore() != null ? request.getPassScore() : 60);
        exercise.setTimeLimit(request.getTimeLimit());
        exercise.setMaxAttempts(request.getMaxAttempts());
        exercise.setShowAnswerWhen(request.getShowAnswerWhen());
        exercise.setShuffleQuestions(request.getShuffleQuestions() != null ? request.getShuffleQuestions() : false);
        exercise.setShuffleOptions(request.getShuffleOptions() != null ? request.getShuffleOptions() : false);
        exercise.setVersion(1);
        exercise.setCreatedAt(LocalDateTime.now());
        exercise.setUpdatedAt(LocalDateTime.now());

        int totalScore = request.getQuestions().stream()
                .mapToInt(q -> q.getScore() != null ? q.getScore() : 0)
                .sum();
        exercise.setTotalScore(totalScore);
        exercise.setQuestionCount(request.getQuestions().size());

        exerciseRepository.insert(exercise);

        // Handle chapter associations (multi-chapter support)
        List<Long> chapterIdsToInsert = resolveChapterIds(request.getChapterIds(), request.getChapterId());
        if (chapterIdsToInsert != null && !chapterIdsToInsert.isEmpty()) {
            for (Long cid : chapterIdsToInsert) {
                ExerciseChapter ec = new ExerciseChapter();
                ec.setExerciseId(exercise.getId());
                ec.setChapterId(cid);
                exerciseChapterRepository.insert(ec);
            }
        }

        List<ExerciseQuestion> exerciseQuestions = new ArrayList<>();
        for (ExerciseCreateRequest.ExerciseQuestionItem item : request.getQuestions()) {
            ExerciseQuestion eq = new ExerciseQuestion();
            eq.setExerciseId(exercise.getId());
            eq.setQuestionId(item.getQuestionId());
            eq.setScore(item.getScore() != null ? item.getScore() : 0);
            eq.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
            exerciseQuestions.add(eq);
        }

        for (ExerciseQuestion eq : exerciseQuestions) {
            exerciseQuestionRepository.insert(eq);
        }

        return getById(exercise.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExerciseVO update(Long id, ExerciseUpdateRequest request) {
        Exercise exercise = exerciseRepository.selectById(id);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can update exercise
        assertCourseOwner(exercise.getCourseId());

        if (request.getCourseId() != null) {
            exercise.setCourseId(request.getCourseId());
        }
        if (request.getChapterId() != null) {
            exercise.setChapterId(request.getChapterId());
        }
        if (request.getTitle() != null) {
            exercise.setTitle(request.getTitle());
        }
        if (request.getPassScore() != null) {
            exercise.setPassScore(request.getPassScore());
        }
        if (request.getTimeLimit() != null) {
            exercise.setTimeLimit(request.getTimeLimit());
        }
        if (request.getMaxAttempts() != null) {
            exercise.setMaxAttempts(request.getMaxAttempts());
        }
        if (request.getShowAnswerWhen() != null) {
            exercise.setShowAnswerWhen(request.getShowAnswerWhen());
        }
        if (request.getShuffleQuestions() != null) {
            exercise.setShuffleQuestions(request.getShuffleQuestions());
        }
        if (request.getShuffleOptions() != null) {
            exercise.setShuffleOptions(request.getShuffleOptions());
        }

        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            int totalScore = request.getQuestions().stream()
                    .mapToInt(q -> q.getScore() != null ? q.getScore() : 0)
                    .sum();
            exercise.setTotalScore(totalScore);
            exercise.setQuestionCount(request.getQuestions().size());

            LambdaQueryWrapper<ExerciseQuestion> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ExerciseQuestion::getExerciseId, id);
            exerciseQuestionRepository.delete(wrapper);

            List<ExerciseQuestion> exerciseQuestions = new ArrayList<>();
            for (ExerciseUpdateRequest.ExerciseQuestionItem item : request.getQuestions()) {
                ExerciseQuestion eq = new ExerciseQuestion();
                eq.setExerciseId(id);
                eq.setQuestionId(item.getQuestionId());
                eq.setScore(item.getScore() != null ? item.getScore() : 0);
                eq.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
                exerciseQuestions.add(eq);
            }

            for (ExerciseQuestion eq : exerciseQuestions) {
                exerciseQuestionRepository.insert(eq);
            }
        }

        if (request.getChapterIds() != null || request.getChapterId() != null) {
            List<Long> chapterIdsToUpdate = resolveChapterIds(request.getChapterIds(), request.getChapterId());
            LambdaQueryWrapper<ExerciseChapter> chapterWrapper = new LambdaQueryWrapper<>();
            chapterWrapper.eq(ExerciseChapter::getExerciseId, id);
            exerciseChapterRepository.delete(chapterWrapper);
            if (chapterIdsToUpdate != null && !chapterIdsToUpdate.isEmpty()) {
                for (Long cid : chapterIdsToUpdate) {
                    ExerciseChapter ec = new ExerciseChapter();
                    ec.setExerciseId(id);
                    ec.setChapterId(cid);
                    exerciseChapterRepository.insert(ec);
                }
            }
        }

        exercise.setUpdatedAt(LocalDateTime.now());
        exerciseRepository.updateById(exercise);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Exercise exercise = exerciseRepository.selectById(id);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can delete exercise
        assertCourseOwner(exercise.getCourseId());

        LambdaQueryWrapper<ExerciseChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(ExerciseChapter::getExerciseId, id);
        exerciseChapterRepository.delete(chapterWrapper);

        LambdaQueryWrapper<ExerciseQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseQuestion::getExerciseId, id);
        exerciseQuestionRepository.delete(wrapper);

        exerciseRepository.deleteById(id);
    }

    @Override
    public PageResult<ExerciseVO> page(Long courseId, Long chapterId, Integer page, Integer size) {
        LambdaQueryWrapper<Exercise> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Exercise::getCourseId, courseId);
        }
        if (chapterId != null) {
            wrapper.eq(Exercise::getChapterId, chapterId);
        }
        // SECURITY: TEACHER 只能看到自己授课课程的练习
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            Long currentUserId = SecurityUtil.getCurrentUserId();
            // ★ Round 11-2 性能优化：用数据库子查询替代「先 selectList 取教师课程 ID 到内存再 IN」，
            // 消除课程数 O(N) 的内存装配；currentUserId 为 JWT 解析的 Long（非字符串），无注入面。
            // 教师无任何课程时子查询返回空集，IN(空) 自然得空结果页，与原 in-memory 早返回语义一致。
            wrapper.apply("course_id IN (SELECT id FROM courses WHERE teacher_id = {0} AND deleted_at IS NULL)", currentUserId);
        }
        wrapper.orderByDesc(Exercise::getCreatedAt);

        IPage<Exercise> exercisePage = exerciseRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        // N+1 修复：批量预加载 course、chapter、questions、multi-chapter
        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, CourseChapter> chapterMap = new HashMap<>();
        Map<Long, List<ExerciseQuestion>> questionsMap = new HashMap<>();
        Map<Long, List<Long>> exerciseChapterIdsMap = new HashMap<>();

        Set<Long> courseIds = exercisePage.getRecords().stream()
                .map(Exercise::getCourseId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> chapterIds = exercisePage.getRecords().stream()
                .map(Exercise::getChapterId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> exerciseIds = exercisePage.getRecords().stream()
                .map(Exercise::getId).collect(Collectors.toSet());

        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }
        if (!chapterIds.isEmpty()) {
            courseChapterRepository.selectBatchIds(chapterIds).forEach(ch -> chapterMap.put(ch.getId(), ch));
        }
        if (!exerciseIds.isEmpty()) {
            LambdaQueryWrapper<ExerciseQuestion> qWrapper = new LambdaQueryWrapper<>();
            qWrapper.in(ExerciseQuestion::getExerciseId, exerciseIds)
                    .orderByAsc(ExerciseQuestion::getSortOrder);
            exerciseQuestionRepository.selectList(qWrapper)
                    .forEach(q -> questionsMap.computeIfAbsent(q.getExerciseId(), k -> new ArrayList<>()).add(q));

            // Batch load multi-chapter associations
            LambdaQueryWrapper<ExerciseChapter> ecWrapper = new LambdaQueryWrapper<>();
            ecWrapper.in(ExerciseChapter::getExerciseId, exerciseIds);
            List<ExerciseChapter> exerciseChapters = exerciseChapterRepository.selectList(ecWrapper);
            Set<Long> multiChapterIds = new HashSet<>();
            for (ExerciseChapter ec : exerciseChapters) {
                exerciseChapterIdsMap.computeIfAbsent(ec.getExerciseId(), k -> new ArrayList<>()).add(ec.getChapterId());
                multiChapterIds.add(ec.getChapterId());
            }
            // Batch load chapter titles for multi-chapter associations
            if (!multiChapterIds.isEmpty()) {
                courseChapterRepository.selectBatchIds(multiChapterIds).forEach(ch -> chapterMap.put(ch.getId(), ch));
            }
        }

        final Map<Long, Course> finalCourseMap = courseMap;
        final Map<Long, CourseChapter> finalChapterMap = chapterMap;
        final Map<Long, List<ExerciseQuestion>> finalQuestionsMap = questionsMap;
        final Map<Long, List<Long>> finalExerciseChapterIdsMap = exerciseChapterIdsMap;

        IPage<ExerciseVO> voPage = exercisePage.convert(e ->
                convertToVO(e, finalCourseMap, finalChapterMap, finalQuestionsMap, finalExerciseChapterIdsMap));
        return PageResult.of(voPage);
    }

    @Override
    public ExerciseVO getById(Long id) {
        Exercise exercise = exerciseRepository.selectById(id);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }
        // SECURITY: TEACHER 只能查看自己课程的练习
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            assertCourseOwner(exercise.getCourseId());
        }
        return convertToVO(exercise);
    }

    private ExerciseVO convertToVO(Exercise exercise) {
        ExerciseVO vo = new ExerciseVO();
        vo.setId(exercise.getId());
        vo.setChapterId(exercise.getChapterId());
        vo.setCourseId(exercise.getCourseId());
        vo.setTitle(exercise.getTitle());
        vo.setPassScore(exercise.getPassScore());
        vo.setTimeLimit(exercise.getTimeLimit());
        vo.setMaxAttempts(exercise.getMaxAttempts());
        vo.setShowAnswerWhen(exercise.getShowAnswerWhen());
        vo.setShuffleQuestions(exercise.getShuffleQuestions());
        vo.setShuffleOptions(exercise.getShuffleOptions());
        vo.setIsExam(exercise.getIsExam());
        vo.setTotalScore(exercise.getTotalScore());
        vo.setQuestionCount(exercise.getQuestionCount());
        vo.setVersion(exercise.getVersion());
        vo.setCreatedAt(exercise.getCreatedAt());
        vo.setUpdatedAt(exercise.getUpdatedAt());

        if (exercise.getCourseId() != null) {
            Course course = courseRepository.selectById(exercise.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }

        if (exercise.getChapterId() != null) {
            CourseChapter chapter = courseChapterRepository.selectById(exercise.getChapterId());
            if (chapter != null) {
                vo.setChapterTitle(chapter.getTitle());
            }
        }

        // Load multi-chapter associations
        // ★ Round 9-1 修复(N+1)：原实现对每个章节关联各执行一次 selectById（章节数 = N → N 次额外查询），
        // 改为先批量 selectBatchIds 预加载全部章节标题，再按原顺序装配。结果与原逻辑逐元素等价
        // （chapterIds 保留全部关联 id 含重复、chapterTitles 仅含存在章节的标题、顺序不变），查询次数由 N 降为 1。
        LambdaQueryWrapper<ExerciseChapter> ecWrapper = new LambdaQueryWrapper<>();
        ecWrapper.eq(ExerciseChapter::getExerciseId, exercise.getId());
        List<ExerciseChapter> exerciseChapters = exerciseChapterRepository.selectList(ecWrapper);
        List<Long> chapterIds = new ArrayList<>();
        List<String> chapterTitles = new ArrayList<>();
        if (!exerciseChapters.isEmpty()) {
            List<Long> distinctChapterIds = exerciseChapters.stream()
                    .map(ExerciseChapter::getChapterId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, CourseChapter> multiChapterMap = new HashMap<>();
            if (!distinctChapterIds.isEmpty()) {
                courseChapterRepository.selectBatchIds(distinctChapterIds)
                        .forEach(ch -> multiChapterMap.put(ch.getId(), ch));
            }
            for (ExerciseChapter ec : exerciseChapters) {
                chapterIds.add(ec.getChapterId());
                CourseChapter ch = multiChapterMap.get(ec.getChapterId());
                if (ch != null) {
                    chapterTitles.add(ch.getTitle());
                }
            }
        }
        vo.setChapterIds(chapterIds);
        vo.setChapterTitles(chapterTitles);

        LambdaQueryWrapper<ExerciseQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseQuestion::getExerciseId, exercise.getId())
                .orderByAsc(ExerciseQuestion::getSortOrder);
        List<ExerciseQuestion> exerciseQuestions = exerciseQuestionRepository.selectList(wrapper);

        // R14 P0-2: 预加载所有题目实际内容（之前只返回关联字段，不返回题目文本）
        Map<Long, Question> questionMap = new HashMap<>();
        List<Long> qids = exerciseQuestions.stream().map(ExerciseQuestion::getQuestionId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toList());
        if (!qids.isEmpty()) {
            questionRepository.selectBatchIds(qids).forEach(q -> questionMap.put(q.getId(), q));
        }
        List<ExerciseVO.ExerciseQuestionVO> questionVOList = exerciseQuestions.stream()
                .map(eq -> {
                    ExerciseVO.ExerciseQuestionVO qvo = new ExerciseVO.ExerciseQuestionVO();
                    qvo.setId(eq.getId());
                    qvo.setExerciseId(eq.getExerciseId());
                    qvo.setQuestionId(eq.getQuestionId());
                    qvo.setScore(eq.getScore());
                    qvo.setSortOrder(eq.getSortOrder());
                    Question qt = questionMap.get(eq.getQuestionId());
                    if (qt != null) {
                        qvo.setQuestionType(qt.getQuestionType());
                        qvo.setContent(qt.getContent());
                        qvo.setOptions(qt.getOptions());
                        qvo.setAnswer(qt.getAnswer());
                        qvo.setExplanation(qt.getExplanation());
                    }
                    return qvo;
                })
                .collect(Collectors.toList());
        vo.setQuestions(questionVOList);

        return vo;
    }

    private ExerciseVO convertToVO(Exercise exercise, Map<Long, Course> courseMap,
                                     Map<Long, CourseChapter> chapterMap,
                                     Map<Long, List<ExerciseQuestion>> questionsMap,
                                     Map<Long, List<Long>> exerciseChapterIdsMap) {
        ExerciseVO vo = new ExerciseVO();
        vo.setId(exercise.getId());
        vo.setChapterId(exercise.getChapterId());
        vo.setCourseId(exercise.getCourseId());
        vo.setTitle(exercise.getTitle());
        vo.setPassScore(exercise.getPassScore());
        vo.setTimeLimit(exercise.getTimeLimit());
        vo.setMaxAttempts(exercise.getMaxAttempts());
        vo.setShowAnswerWhen(exercise.getShowAnswerWhen());
        vo.setShuffleQuestions(exercise.getShuffleQuestions());
        vo.setShuffleOptions(exercise.getShuffleOptions());
        vo.setIsExam(exercise.getIsExam());
        vo.setTotalScore(exercise.getTotalScore());
        vo.setQuestionCount(exercise.getQuestionCount());
        vo.setVersion(exercise.getVersion());
        vo.setCreatedAt(exercise.getCreatedAt());
        vo.setUpdatedAt(exercise.getUpdatedAt());

        if (exercise.getCourseId() != null) {
            Course course = courseMap.get(exercise.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }

        if (exercise.getChapterId() != null) {
            CourseChapter chapter = chapterMap.get(exercise.getChapterId());
            if (chapter != null) {
                vo.setChapterTitle(chapter.getTitle());
            }
        }

        // Populate multi-chapter data
        List<Long> chapterIds = exerciseChapterIdsMap.getOrDefault(exercise.getId(), new ArrayList<>());
        List<String> chapterTitles = new ArrayList<>();
        for (Long cid : chapterIds) {
            CourseChapter ch = chapterMap.get(cid);
            if (ch != null) {
                chapterTitles.add(ch.getTitle());
            }
        }
        vo.setChapterIds(chapterIds);
        vo.setChapterTitles(chapterTitles);

        List<ExerciseQuestion> exerciseQuestions = questionsMap.getOrDefault(exercise.getId(), new ArrayList<>());

        List<ExerciseVO.ExerciseQuestionVO> questionVOList = exerciseQuestions.stream()
                .map(eq -> {
                    ExerciseVO.ExerciseQuestionVO qvo = new ExerciseVO.ExerciseQuestionVO();
                    qvo.setId(eq.getId());
                    qvo.setExerciseId(eq.getExerciseId());
                    qvo.setQuestionId(eq.getQuestionId());
                    qvo.setScore(eq.getScore());
                    qvo.setSortOrder(eq.getSortOrder());
                    return qvo;
                })
                .collect(Collectors.toList());
        vo.setQuestions(questionVOList);

        return vo;
    }

    /**
     * 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN
     *
     * @param courseId 课程 ID
     * @throws BusinessException NOT_FOUND 课程不存在，NO_PERMISSION 无权限
     */
    private void assertCourseOwner(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    /**
     * Resolve chapter IDs list from multi-chapter or single-chapter input.
     * If chapterIds is not empty, use it; otherwise fall back to chapterId.
     */
    private List<Long> resolveChapterIds(List<Long> chapterIds, Long chapterId) {
        if (chapterIds != null && !chapterIds.isEmpty()) {
            return chapterIds;
        }
        if (chapterId != null) {
            List<Long> singleList = new ArrayList<>();
            singleList.add(chapterId);
            return singleList;
        }
        return new ArrayList<>();
    }

    @Override
    public List<ExerciseVO> getMyExams(Long userId) {
        // J3-01: 查询学生已选课程
        LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
        enrollWrapper.eq(Enrollment::getUserId, userId)
                .ne(Enrollment::getEnrollmentStatus, "CANCELLED");
        List<Enrollment> enrollments = enrollmentRepository.selectList(enrollWrapper);
        if (enrollments.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> courseIds = enrollments.stream()
                .map(Enrollment::getCourseId)
                .distinct()
                .collect(Collectors.toList());

        // 查询这些课程中 is_exam=true 的练习
        LambdaQueryWrapper<Exercise> examWrapper = new LambdaQueryWrapper<>();
        examWrapper.in(Exercise::getCourseId, courseIds)
                .eq(Exercise::getIsExam, true);
        List<Exercise> exams = exerciseRepository.selectList(examWrapper);

        return exams.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addQuestions(Long exerciseId, List<Long> questionIds) {
        Exercise exercise = exerciseRepository.selectById(exerciseId);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }
        assertCourseOwner(exercise.getCourseId());
        for (Long qid : questionIds) {
            ExerciseQuestion eq = new ExerciseQuestion();
            eq.setExerciseId(exerciseId);
            eq.setQuestionId(qid);
            exerciseQuestionRepository.insert(eq);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeQuestion(Long exerciseId, Long questionId) {
        Exercise exercise = exerciseRepository.selectById(exerciseId);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
        }
        assertCourseOwner(exercise.getCourseId());
        LambdaQueryWrapper<ExerciseQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseQuestion::getExerciseId, exerciseId)
               .eq(ExerciseQuestion::getQuestionId, questionId);
        exerciseQuestionRepository.delete(wrapper);
    }
}