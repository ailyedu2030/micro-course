package com.microcourse.service;

import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.dto.PageResult;

public interface QuestionService {

    QuestionVO create(QuestionCreateRequest request);

    QuestionVO update(Long id, QuestionUpdateRequest request);

    void delete(Long id);

    PageResult<QuestionVO> page(Integer courseId, String questionType, Integer difficulty, Integer page, Integer size);

    QuestionVO getById(Long id);
}