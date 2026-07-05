package com.microcourse.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Question;
import com.microcourse.entity.QuestionChapter;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.util.XssSanitizer;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.QuestionChapterRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.QuestionService;
import com.microcourse.util.SecurityUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class QuestionServiceImpl implements QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionServiceImpl.class);

    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseCategoryRepository categoryRepository;
    private final QuestionChapterRepository questionChapterRepository;
    private final CourseChapterRepository courseChapterRepository;

    /** Self-reference for @Transactional proxy access (via @Lazy constructor injection) */
    private final QuestionServiceImpl self;

    public QuestionServiceImpl(QuestionRepository questionRepository,
                              CourseRepository courseRepository,
                              UserRepository userRepository,
                              CourseCategoryRepository categoryRepository,
                              QuestionChapterRepository questionChapterRepository,
                              CourseChapterRepository courseChapterRepository,
                              @Lazy QuestionServiceImpl self) {
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.questionChapterRepository = questionChapterRepository;
        this.courseChapterRepository = courseChapterRepository;
        this.self = self;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionVO create(QuestionCreateRequest request) {
        // Owner check: only course teacher or ADMIN can create question
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // SECURITY: TEACHER 只能为自己创建题目，ADMIN 可指定
        if (!SecurityUtil.isAdmin()) {
            request.setTeacherId(SecurityUtil.getCurrentUserId());
        }

        Question question = new Question();
        question.setCourseId(request.getCourseId());
        question.setTeacherId(request.getTeacherId());
        question.setQuestionType(request.getQuestionType());
        question.setContent(XssSanitizer.sanitize(request.getContent()));
        question.setOptions(request.getOptions() != null ? XssSanitizer.sanitize(request.getOptions()) : null);
        question.setAnswer(XssSanitizer.sanitize(request.getAnswer()));
        question.setPartialScore(request.getPartialScore());
        question.setExplanation(request.getExplanation() != null ? XssSanitizer.sanitize(request.getExplanation()) : null);
        question.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : 1);
        question.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        question.setVersion(1);
        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());

        questionRepository.insert(question);

        // Handle chapter associations (multi-chapter support)
        if (request.getChapterIds() != null && !request.getChapterIds().isEmpty()) {
            for (Long cid : request.getChapterIds()) {
                if (cid == null) {
                    continue;
                }
                QuestionChapter qc = new QuestionChapter();
                qc.setQuestionId(question.getId());
                qc.setChapterId(cid);
                questionChapterRepository.insert(qc);
            }
        }

        return convertToVO(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionVO update(Long id, QuestionUpdateRequest request) {
        Question question = questionRepository.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can update question
        assertCourseOwner(question.getCourseId());

        if (request.getCourseId() != null) {
            question.setCourseId(request.getCourseId());
        }
        if (request.getTeacherId() != null) {
            question.setTeacherId(request.getTeacherId());
        }
        if (request.getQuestionType() != null) {
            question.setQuestionType(request.getQuestionType());
        }
        if (request.getContent() != null) {
            question.setContent(XssSanitizer.sanitize(request.getContent()));
        }
        if (request.getOptions() != null) {
            question.setOptions(XssSanitizer.sanitize(request.getOptions()));
        }
        if (request.getAnswer() != null) {
            question.setAnswer(XssSanitizer.sanitize(request.getAnswer()));
        }
        if (request.getPartialScore() != null) {
            question.setPartialScore(request.getPartialScore());
        }
        if (request.getExplanation() != null) {
            question.setExplanation(XssSanitizer.sanitize(request.getExplanation()));
        }
        if (request.getDifficulty() != null) {
            question.setDifficulty(request.getDifficulty());
        }
        if (request.getStatus() != null) {
            question.setStatus(request.getStatus());
        }

        question.setUpdatedAt(LocalDateTime.now());
        questionRepository.updateById(question);

        // Handle chapter associations (multi-chapter support): replace old with new
        if (request.getChapterIds() != null && !request.getChapterIds().isEmpty()) {
            LambdaQueryWrapper<QuestionChapter> chapterWrapper = new LambdaQueryWrapper<>();
            chapterWrapper.eq(QuestionChapter::getQuestionId, id);
            questionChapterRepository.delete(chapterWrapper);
            for (Long cid : request.getChapterIds()) {
                if (cid == null) {
                    continue;
                }
                QuestionChapter qc = new QuestionChapter();
                qc.setQuestionId(id);
                qc.setChapterId(cid);
                questionChapterRepository.insert(qc);
            }
        }

        return convertToVO(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Question question = questionRepository.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can delete question
        assertCourseOwner(question.getCourseId());

        // Cascade delete chapter associations
        LambdaQueryWrapper<QuestionChapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(QuestionChapter::getQuestionId, id);
        questionChapterRepository.delete(chapterWrapper);

        questionRepository.deleteById(id);
    }

    @Override
    public PageResult<QuestionVO> page(Long courseId, String questionType, Integer difficulty, String keyword, Long categoryId, Long chapterId, Integer page, Integer size) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (questionType != null && !questionType.isEmpty()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        // keyword: 模糊搜索题目内容
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Question::getContent, keyword.trim());
        }
        // chapterId: 通过 question_chapters 关联表筛选（参数化查询防 SQL 注入）
        if (chapterId != null) {
            // 复用已有的 chapterId 参数：先在 question_chapters 中查 question_id 集合，再 IN
            List<Long> qIds = questionChapterRepository.selectList(
                    new LambdaQueryWrapper<QuestionChapter>()
                            .eq(QuestionChapter::getChapterId, chapterId)
                            .select(QuestionChapter::getQuestionId)
            ).stream().map(QuestionChapter::getQuestionId).collect(Collectors.toList());
            if (qIds.isEmpty()) {
                return PageResult.of(java.util.List.of(), 0, page, size);
            }
            wrapper.in(Question::getId, qIds);
        }
        // categoryId: 按课程分类筛选（通过 courses.category_id 间接筛选）
        if (categoryId != null) {
            List<Long> courseIdsInCategory = courseRepository.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getCategoryId, categoryId)
                            .select(Course::getId)
            ).stream().map(Course::getId).collect(Collectors.toList());
            if (courseIdsInCategory.isEmpty()) {
                return PageResult.of(java.util.List.of(), 0, page, size);
            }
            if (courseId == null) {
                wrapper.in(Question::getCourseId, courseIdsInCategory);
            }
            // 如果同时传了 courseId 且 categoryId，按交集处理：
            // 若该课程不属于此分类则返回空
            else if (!courseIdsInCategory.contains(courseId)) {
                return PageResult.of(java.util.List.of(), 0, page, size);
            }
        }
        wrapper.orderByDesc(Question::getCreatedAt);

        IPage<Question> questionPage = questionRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        // Batch load multi-chapter associations to avoid N+1
        Map<Long, List<Long>> questionChapterIdsMap = new HashMap<>();
        Map<Long, CourseChapter> chapterMap = new HashMap<>();
        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, User> userMap = new HashMap<>();

        Set<Long> questionIds = questionPage.getRecords().stream()
                .map(Question::getId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!questionIds.isEmpty()) {
            // Batch-load chapter associations
            LambdaQueryWrapper<QuestionChapter> qcWrapper = new LambdaQueryWrapper<>();
            qcWrapper.in(QuestionChapter::getQuestionId, questionIds);
            List<QuestionChapter> questionChapters = questionChapterRepository.selectList(qcWrapper);
            Set<Long> allChapterIds = new HashSet<>();
            for (QuestionChapter qc : questionChapters) {
                questionChapterIdsMap
                        .computeIfAbsent(qc.getQuestionId(), k -> new ArrayList<>())
                        .add(qc.getChapterId());
                allChapterIds.add(qc.getChapterId());
            }
            if (!allChapterIds.isEmpty()) {
                courseChapterRepository.selectBatchIds(allChapterIds)
                        .forEach(ch -> chapterMap.put(ch.getId(), ch));
            }

            // Batch-load courses, teachers, categories to eliminate N+1 in toBaseVO
            Set<Long> courseIds = questionPage.getRecords().stream()
                    .map(Question::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!courseIds.isEmpty()) {
                courseRepository.selectBatchIds(courseIds)
                        .forEach(c -> courseMap.put(c.getId(), c));
            }
            // categoryId comes from course, not question; will be populated during convertToVO
            Set<Long> teacherIds = questionPage.getRecords().stream()
                    .map(Question::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!teacherIds.isEmpty()) {
                userRepository.selectBatchIds(teacherIds)
                        .forEach(u -> userMap.put(u.getId(), u));
            }
        }

        final Map<Long, List<Long>> finalQuestionChapterIdsMap = questionChapterIdsMap;
        final Map<Long, CourseChapter> finalChapterMap = chapterMap;

        IPage<QuestionVO> voPage = questionPage.convert(
                q -> convertToVO(q, finalQuestionChapterIdsMap, finalChapterMap, courseMap, userMap));
        return PageResult.of(voPage);
    }

    @Override
    public QuestionVO getById(Long id) {
        Question question = questionRepository.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return convertToVO(question);
    }

    @Override
    public BatchImportResultVO batchImport(MultipartFile file, Long courseId) {
        // Step 0: 权限预检（非事务）
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        // SECURITY: Excel 魔数校验（XLS: D0CF11E0, XLSX: 504B0304）
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] magic = new byte[4];
            int read = is.read(magic);
            if (read < 4) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件过小，无法验证格式");
            boolean isXls = (magic[0] & 0xFF) == 0xD0 && (magic[1] & 0xFF) == 0xCF
                    && (magic[2] & 0xFF) == 0x11 && (magic[3] & 0xFF) == 0xE0;
            boolean isXlsx = (magic[0] & 0xFF) == 0x50 && magic[1] == 0x4B
                    && magic[2] == 0x03 && magic[3] == 0x04;
            if (!isXls && !isXlsx) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 Excel 文件（魔数校验失败）");
        } catch (java.io.IOException e) { throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取文件"); }

        // SECURITY: 只有课程教师或 ADMIN 可批量导入题目
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权向该课程导入题目");
        }

        Long teacherId = SecurityUtil.getCurrentUserId();
        if (teacherId == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // Step 1: 解析 Excel + 逐行校验（非事务，仅构建 Question 对象不入库）
        List<BatchImportResultVO.ImportErrorItem> errors = new ArrayList<>();
        List<Question> questionsToInsert = new ArrayList<>();

        cn.hutool.poi.excel.ExcelReader reader = null;
        try {
            reader = cn.hutool.poi.excel.ExcelUtil.getReader(file.getInputStream(), 0);
            List<List<Object>> rows = reader.read();

            // 第一行是表头，跳过
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                int rowNum = i + 1;

                if (row.size() < 4) {
                    errors.add(new BatchImportResultVO.ImportErrorItem(rowNum, "", "数据列数不足，至少需要 4 列"));
                    continue;
                }

                String questionType = row.get(0) != null ? row.get(0).toString() : null;
                String content = row.get(1) != null ? row.get(1).toString() : null;
                String options = row.size() > 2 && row.get(2) != null ? row.get(2).toString() : null;
                String answer = row.size() > 3 && row.get(3) != null ? row.get(3).toString() : null;
                String partialScore = row.size() > 4 && row.get(4) != null ? row.get(4).toString() : null;
                String explanation = row.size() > 5 && row.get(5) != null ? row.get(5).toString() : null;
                Integer difficulty;
                try {
                    difficulty = row.size() > 6 && row.get(6) != null ? Integer.parseInt(row.get(6).toString()) : 1;
                } catch (NumberFormatException e) {
                    log.warn("解析难度值失败: {}", e.getMessage());
                    errors.add(new BatchImportResultVO.ImportErrorItem(rowNum, "", "难度值不是有效数字"));
                    continue;
                }

                // 校验必填
                if (questionType == null || questionType.trim().isEmpty()) {
                    errors.add(new BatchImportResultVO.ImportErrorItem(rowNum, "", "题目类型不能为空"));
                    continue;
                }
                if (content == null || content.trim().isEmpty()) {
                    errors.add(new BatchImportResultVO.ImportErrorItem(rowNum, "", "题目内容不能为空"));
                    continue;
                }
                if (answer == null || answer.trim().isEmpty()) {
                    errors.add(new BatchImportResultVO.ImportErrorItem(rowNum, "", "答案不能为空"));
                    continue;
                }

                Question question = new Question();
                question.setCourseId(courseId);
                question.setTeacherId(teacherId);
                question.setQuestionType(questionType.trim().toUpperCase());
                question.setContent(XssSanitizer.sanitize(content.trim()));
                question.setOptions(options != null ? XssSanitizer.sanitize(options.trim()) : null);
                question.setAnswer(XssSanitizer.sanitize(answer.trim()));
                question.setPartialScore(partialScore);
                question.setExplanation(explanation != null ? XssSanitizer.sanitize(explanation.trim()) : null);
                question.setDifficulty(difficulty);
                question.setStatus(1);
                question.setVersion(0);
                question.setCreatedAt(LocalDateTime.now());
                question.setUpdatedAt(LocalDateTime.now());

                questionsToInsert.add(question);
            }
        } catch (Exception e) {
            log.error("[Question] Excel 解析失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "Excel 文件解析失败，请检查文件格式和内容是否正确", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        // Step 2: 分批入库（每 100 行一个独立事务，长事务修复 DB-P0-05）
        int total = questionsToInsert.size();
        int successCount = 0;
        int failedCount = errors.size();

        final int batchSize = 100;
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<Question> chunk = questionsToInsert.subList(i, end);
            try {
                // 通过 self 代理调用以触发 @Transactional
                if (self != null) {
                    self.batchInsertQuestionsTransactional(chunk);
                } else {
                    batchInsertQuestionsTransactional(chunk);
                }
                successCount += chunk.size();
            } catch (Exception e) {
                failedCount += chunk.size();
                log.error("[Question] 批量入库失败 range=[{}-{}), 原因={}", i, end, e.getMessage(), e);
                for (Question q : chunk) {
                    errors.add(new BatchImportResultVO.ImportErrorItem(
                            0, "", "入库失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
                }
            }
        }

        BatchImportResultVO result = new BatchImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailCount(failedCount);
        result.setErrors(errors);
        return result;
    }

    /**
     * 批量插入题目（单批次，由 AOP 代理调用以确保 @Transactional 生效）。
     * 调用方负责通过 self 代理调用本方法以触发事务拦截器。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void batchInsertQuestionsTransactional(List<Question> questions) {
        if (questions.isEmpty()) {
            return;
        }
        for (Question question : questions) {
            questionRepository.insert(question);
        }
    }

    private QuestionVO toBaseVO(Question question,
                                 Map<Long, Course> courseMap,
                                 Map<Long, User> userMap) {
        QuestionVO vo = new QuestionVO();
        vo.setId(question.getId());
        vo.setCourseId(question.getCourseId());
        vo.setTeacherId(question.getTeacherId());
        vo.setQuestionType(question.getQuestionType());
        vo.setContent(question.getContent());
        vo.setOptions(question.getOptions());
        vo.setAnswer(question.getAnswer());
        vo.setPartialScore(question.getPartialScore());
        vo.setExplanation(question.getExplanation());
        vo.setDifficulty(question.getDifficulty());
        vo.setVersion(question.getVersion());
        vo.setStatus(question.getStatus());
        vo.setCreatedAt(question.getCreatedAt());
        vo.setUpdatedAt(question.getUpdatedAt());

        if (question.getCourseId() != null) {
            Course course = courseMap != null ? courseMap.get(question.getCourseId()) : courseRepository.selectById(question.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
                vo.setCategoryId(course.getCategoryId());
            }
        }

        if (question.getTeacherId() != null) {
            User user = userMap != null ? userMap.get(question.getTeacherId()) : userRepository.selectById(question.getTeacherId());
            if (user != null) {
                vo.setTeacherName(user.getRealName());
            }
        }

        return vo;
    }

    private QuestionVO convertToVO(Question question) {
        QuestionVO vo = toBaseVO(question, null, null);

        // Load multi-chapter associations
        List<Long> chapterIds = new ArrayList<>();
        List<String> chapterTitles = new ArrayList<>();
        if (question.getId() != null) {
            LambdaQueryWrapper<QuestionChapter> qcWrapper = new LambdaQueryWrapper<>();
            qcWrapper.eq(QuestionChapter::getQuestionId, question.getId());
            List<QuestionChapter> questionChapters = questionChapterRepository.selectList(qcWrapper);
            for (QuestionChapter qc : questionChapters) {
                chapterIds.add(qc.getChapterId());
                CourseChapter ch = courseChapterRepository.selectById(qc.getChapterId());
                if (ch != null) {
                    chapterTitles.add(ch.getTitle());
                }
            }
        }
        vo.setChapterIds(chapterIds);
        vo.setChapterTitles(chapterTitles);

        return vo;
    }

    private QuestionVO convertToVO(Question question,
                                   Map<Long, List<Long>> questionChapterIdsMap,
                                   Map<Long, CourseChapter> chapterMap,
                                   Map<Long, Course> courseMap,
                                   Map<Long, User> userMap) {
        QuestionVO vo = toBaseVO(question, courseMap, userMap);

        // Populate multi-chapter data from pre-loaded maps
        List<Long> chapterIds = questionChapterIdsMap.getOrDefault(question.getId(), new ArrayList<>());
        List<String> chapterTitles = new ArrayList<>();
        for (Long cid : chapterIds) {
            CourseChapter ch = chapterMap.get(cid);
            if (ch != null) {
                chapterTitles.add(ch.getTitle());
            }
        }
        vo.setChapterIds(chapterIds);
        vo.setChapterTitles(chapterTitles);

        return vo;
    }

    /**
     * 校验当前用户是否为课程 owner（课程创建教师）或 ADMIN。
     * <p>通用模式：CourseChapterServiceImpl / VideoServiceImpl / OfflineSessionServiceImpl /
     * LessonServiceImpl / ExerciseServiceImpl 中也包含语义相同的实现。
     * 若需统一重构，可抽取到公共工具类。</p>
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
}