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
import com.microcourse.entity.Question;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.QuestionService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseCategoryRepository categoryRepository;

    public QuestionServiceImpl(QuestionRepository questionRepository,
                              CourseRepository courseRepository,
                              UserRepository userRepository,
                              CourseCategoryRepository categoryRepository) {
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public QuestionVO create(QuestionCreateRequest request) {
        // Owner check: only course teacher or ADMIN can create question
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        Question question = new Question();
        question.setCourseId(request.getCourseId());
        question.setTeacherId(request.getTeacherId());
        question.setQuestionType(request.getQuestionType());
        question.setContent(request.getContent());
        question.setOptions(request.getOptions());
        question.setAnswer(request.getAnswer());
        question.setPartialScore(request.getPartialScore());
        question.setExplanation(request.getExplanation());
        question.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : 1);
        question.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        question.setVersion(1);
        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());

        questionRepository.insert(question);
        return convertToVO(question);
    }

    @Override
    @Transactional
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
            question.setContent(request.getContent());
        }
        if (request.getOptions() != null) {
            question.setOptions(request.getOptions());
        }
        if (request.getAnswer() != null) {
            question.setAnswer(request.getAnswer());
        }
        if (request.getPartialScore() != null) {
            question.setPartialScore(request.getPartialScore());
        }
        if (request.getExplanation() != null) {
            question.setExplanation(request.getExplanation());
        }
        if (request.getDifficulty() != null) {
            question.setDifficulty(request.getDifficulty());
        }
        if (request.getStatus() != null) {
            question.setStatus(request.getStatus());
        }

        question.setUpdatedAt(LocalDateTime.now());
        questionRepository.updateById(question);
        return convertToVO(question);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Question question = questionRepository.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        // Owner check: only course teacher or ADMIN can delete question
        assertCourseOwner(question.getCourseId());
        questionRepository.deleteById(id);
    }

    @Override
    public PageResult<QuestionVO> page(Integer courseId, String questionType, Integer difficulty, Integer page, Integer size) {
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
        wrapper.orderByDesc(Question::getCreatedAt);

        IPage<Question> questionPage = questionRepository.selectPage(
                new Page<>(page + 1, size), wrapper);

        IPage<QuestionVO> voPage = questionPage.convert(this::convertToVO);
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
    @Transactional
    public BatchImportResultVO batchImport(MultipartFile file, Long courseId) {
        // 验证课程存在
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        // 获取当前教师 ID
        Long teacherId = SecurityUtil.getCurrentUserId();
        if (teacherId == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        // 使用 Hutool ExcelReader 解析
        cn.hutool.poi.excel.ExcelReader reader = null;
        try {
            reader = cn.hutool.poi.excel.ExcelUtil.getReader(file.getInputStream(), 0);
            List<List<Object>> rows = reader.read();

            // 第一行是表头，跳过
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                try {
                    // 期望列：questionType, content, options, answer, partialScore, explanation, difficulty
                    // 列索引：0=questionType, 1=content, 2=options, 3=answer, 4=partialScore, 5=explanation, 6=difficulty
                    if (row.size() < 4) {
                        errors.add("第 " + (i + 1) + " 行：数据列数不足，至少需要 4 列");
                        failCount++;
                        continue;
                    }

                    String questionType = row.get(0) != null ? row.get(0).toString() : null;
                    String content = row.get(1) != null ? row.get(1).toString() : null;
                    String options = row.size() > 2 && row.get(2) != null ? row.get(2).toString() : null;
                    String answer = row.size() > 3 && row.get(3) != null ? row.get(3).toString() : null;
                    String partialScore = row.size() > 4 && row.get(4) != null ? row.get(4).toString() : null;
                    String explanation = row.size() > 5 && row.get(5) != null ? row.get(5).toString() : null;
                    Integer difficulty = row.size() > 6 && row.get(6) != null ? Integer.parseInt(row.get(6).toString()) : 1;

                    // 校验必填
                    if (questionType == null || questionType.trim().isEmpty()) {
                        errors.add("第 " + (i + 1) + " 行：题目类型不能为空");
                        failCount++;
                        continue;
                    }
                    if (content == null || content.trim().isEmpty()) {
                        errors.add("第 " + (i + 1) + " 行：题目内容不能为空");
                        failCount++;
                        continue;
                    }
                    if (answer == null || answer.trim().isEmpty()) {
                        errors.add("第 " + (i + 1) + " 行：答案不能为空");
                        failCount++;
                        continue;
                    }

                    Question question = new Question();
                    question.setCourseId(courseId);
                    question.setTeacherId(teacherId);
                    question.setQuestionType(questionType.trim().toUpperCase());
                    question.setContent(content.trim());
                    question.setOptions(options != null ? options.trim() : null);
                    question.setAnswer(answer.trim());
                    question.setPartialScore(partialScore);
                    question.setExplanation(explanation != null ? explanation.trim() : null);
                    question.setDifficulty(difficulty);
                    question.setStatus(1);
                    question.setVersion(0);
                    question.setCreatedAt(LocalDateTime.now());
                    question.setUpdatedAt(LocalDateTime.now());

                    questionRepository.insert(question);
                    successCount++;
                } catch (Exception e) {
                    errors.add("第 " + (i + 1) + " 行：处理失败，请检查数据格式");
                    failCount++;
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "Excel 解析失败");
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        BatchImportResultVO result = new BatchImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setErrors(errors);
        return result;
    }

    private QuestionVO convertToVO(Question question) {
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
            Course course = courseRepository.selectById(question.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
                vo.setCategoryId(course.getCategoryId());
            }
        }

        if (question.getTeacherId() != null) {
            User user = userRepository.selectById(question.getTeacherId());
            if (user != null) {
                vo.setTeacherName(user.getRealName());
            }
        }

        // Populate category name if categoryId is set
        if (vo.getCategoryId() != null) {
            var category = categoryRepository.selectById(vo.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

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
}