package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Question;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.QuestionService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public QuestionServiceImpl(QuestionRepository questionRepository,
                              CourseRepository courseRepository,
                              UserRepository userRepository) {
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
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
            }
        }

        if (question.getTeacherId() != null) {
            User user = userRepository.selectById(question.getTeacherId());
            if (user != null) {
                vo.setTeacherName(user.getRealName());
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