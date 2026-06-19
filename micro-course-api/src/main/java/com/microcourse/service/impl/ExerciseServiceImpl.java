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
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseQuestion;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.ExerciseQuestionRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExerciseServiceImpl implements ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;
    private final CourseRepository courseRepository;
    private final CourseChapterRepository courseChapterRepository;

    public ExerciseServiceImpl(ExerciseRepository exerciseRepository,
                               ExerciseQuestionRepository exerciseQuestionRepository,
                               CourseRepository courseRepository,
                               CourseChapterRepository courseChapterRepository) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
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

        LambdaQueryWrapper<ExerciseQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseQuestion::getExerciseId, id);
        exerciseQuestionRepository.delete(wrapper);

        exerciseRepository.deleteById(id);
    }

    @Override
    public PageResult<ExerciseVO> page(Integer courseId, Integer chapterId, Integer page, Integer size) {
        LambdaQueryWrapper<Exercise> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Exercise::getCourseId, courseId);
        }
        if (chapterId != null) {
            wrapper.eq(Exercise::getChapterId, chapterId);
        }
        wrapper.orderByDesc(Exercise::getCreatedAt);

        IPage<Exercise> exercisePage = exerciseRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        // N+1 修复：批量预加载 course、chapter、questions
        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, CourseChapter> chapterMap = new HashMap<>();
        Map<Long, List<ExerciseQuestion>> questionsMap = new HashMap<>();

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
        }

        final Map<Long, Course> finalCourseMap = courseMap;
        final Map<Long, CourseChapter> finalChapterMap = chapterMap;
        final Map<Long, List<ExerciseQuestion>> finalQuestionsMap = questionsMap;

        IPage<ExerciseVO> voPage = exercisePage.convert(e ->
                convertToVO(e, finalCourseMap, finalChapterMap, finalQuestionsMap));
        return PageResult.of(voPage);
    }

    @Override
    public ExerciseVO getById(Long id) {
        Exercise exercise = exerciseRepository.selectById(id);
        if (exercise == null) {
            throw new BusinessException(ErrorCode.EXERCISE_NOT_FOUND);
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

        LambdaQueryWrapper<ExerciseQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseQuestion::getExerciseId, exercise.getId())
                .orderByAsc(ExerciseQuestion::getSortOrder);
        List<ExerciseQuestion> exerciseQuestions = exerciseQuestionRepository.selectList(wrapper);

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

    private ExerciseVO convertToVO(Exercise exercise, Map<Long, Course> courseMap,
                                     Map<Long, CourseChapter> chapterMap,
                                     Map<Long, List<ExerciseQuestion>> questionsMap) {
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