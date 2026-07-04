package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.*;
import com.microcourse.entity.Question;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.service.ExerciseService;
import com.microcourse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 考试控制器
 * 学生端：GET /api/exams/my 查看考试列表
 * 教师端：POST /api/exams/generate 智能组卷
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExerciseService exerciseService;
    private final QuestionRepository questionRepository;
    private final com.microcourse.repository.QuestionChapterRepository questionChapterRepository;

    public ExamController(ExerciseService exerciseService,
                          QuestionRepository questionRepository,
                          com.microcourse.repository.QuestionChapterRepository questionChapterRepository) {
        this.exerciseService = exerciseService;
        this.questionRepository = questionRepository;
        this.questionChapterRepository = questionChapterRepository;
    }

    /**
     * GET /api/exams/my
     * 学生端：当前学生的考试列表
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public R<List<ExerciseVO>> myExams() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ExerciseVO> exams = exerciseService.getMyExams(userId);
        return R.ok(exams);
    }

    /**
     * POST /api/exams/generate
     * 教师端：智能组卷。从题库按条件随机抽题，创建考试。
     *
     * 请求体：
     * {
     *   "title": "期中考试",
     *   "courseId": 1,
     *   "chapterIds": [1, 2, 3],
     *   "questionCounts": { "SINGLE": 5, "MULTIPLE": 3, "JUDGE": 2 },
     *   "totalScore": 100,
     *   "timeLimit": 60
     * }
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("智能组卷")
    public R<ExerciseVO> generate(@Valid @RequestBody ExamGenerateRequest req) {
        String title = req.getTitle();
        Long courseId = req.getCourseId();
        Map<String, Integer> questionCounts = req.getQuestionCounts();
        Integer totalScore = req.getTotalScore();
        Integer timeLimit = req.getTimeLimit();
        List<Long> chapterIds = req.getChapterIds() != null ? req.getChapterIds() : new ArrayList<>();

        if (questionCounts == null || questionCounts.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请至少选择一种题型");
        }

        // 1. 按条件查询所有匹配题目
        LambdaQueryWrapper<Question> qWrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getCourseId, courseId);

        Set<String> typeSet = questionCounts.keySet();
        if (!typeSet.isEmpty()) {
            qWrapper.in(Question::getQuestionType, typeSet);
        }

        List<Question> allQuestions = questionRepository.selectList(qWrapper);

        // 2. 如果指定了章节，过滤出关联这些章节的题目
        List<Question> pool;
        if (!chapterIds.isEmpty()) {
            Set<Long> questionIdsInChapters = new HashSet<>();
            List<com.microcourse.entity.QuestionChapter> qcs = questionChapterRepository.selectList(
                    new LambdaQueryWrapper<com.microcourse.entity.QuestionChapter>()
                            .in(com.microcourse.entity.QuestionChapter::getChapterId, chapterIds));
            qcs.forEach(qc -> questionIdsInChapters.add(qc.getQuestionId()));

            Set<Long> qidSet = questionIdsInChapters;
            pool = allQuestions.stream()
                    .filter(q -> qidSet.contains(q.getId()))
                    .collect(Collectors.toList());
        } else {
            pool = allQuestions;
        }

        // 3. 按题型分组，随机抽题
        Map<String, List<Question>> byType = pool.stream()
                .collect(Collectors.groupingBy(Question::getQuestionType));

        List<Long> selectedIds = new ArrayList<>();
        int totalQuestions = 0;

        for (Map.Entry<String, Integer> entry : questionCounts.entrySet()) {
            String type = entry.getKey();
            int needed = entry.getValue();
            List<Question> candidates = byType.getOrDefault(type, new ArrayList<>());
            if (candidates.size() < needed) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        type + " 题库不足：需要 " + needed + " 题，仅有 " + candidates.size() + " 题");
            }
            Collections.shuffle(candidates);
            for (int i = 0; i < needed; i++) {
                selectedIds.add(candidates.get(i).getId());
                totalQuestions++;
            }
        }

        // 4. 构建题目列表（均分总分，最后一题兜底余数）
        int finalTotalScore = totalScore != null ? totalScore : totalQuestions;
        int baseScore = totalQuestions > 0 ? finalTotalScore / totalQuestions : 1;
        int remainder = totalQuestions > 0 ? finalTotalScore % totalQuestions : 0;
        List<ExerciseCreateRequest.ExerciseQuestionItem> items = new ArrayList<>();
        for (int i = 0; i < selectedIds.size(); i++) {
            ExerciseCreateRequest.ExerciseQuestionItem item = new ExerciseCreateRequest.ExerciseQuestionItem();
            item.setQuestionId(selectedIds.get(i));
            item.setScore(baseScore + (i == selectedIds.size() - 1 ? remainder : 0));
            item.setSortOrder(i + 1);
            items.add(item);
        }

        // 5. 创建考试（含题目，isExam=true）
        ExerciseCreateRequest createReq = new ExerciseCreateRequest();
        createReq.setCourseId(courseId);
        createReq.setChapterIds(chapterIds);
        // 同时设置单值 chapterId,确保 Exercise.chapterId 被填充(loadExams 过滤用)
        createReq.setChapterId(chapterIds.isEmpty() ? null : chapterIds.get(0));
        createReq.setTitle(title);
        createReq.setIsExam(true);
        createReq.setQuestions(items);
        createReq.setTotalScore(finalTotalScore);
        createReq.setTimeLimit(timeLimit);
        createReq.setPassScore((int) Math.ceil(finalTotalScore * 0.6));

        ExerciseVO exam = exerciseService.create(createReq);
        return R.ok(exam);
    }
}
