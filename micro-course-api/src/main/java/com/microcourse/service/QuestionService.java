package com.microcourse.service;

import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.BatchImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionService {

    QuestionVO create(QuestionCreateRequest request);

    QuestionVO update(Long id, QuestionUpdateRequest request);

    void delete(Long id);

    PageResult<QuestionVO> page(Integer courseId, String questionType, Integer difficulty, Integer page, Integer size);

    QuestionVO getById(Long id);

    /**
     * 批量导入题目（Excel 解析）
     * @param file Excel 文件
     * @param courseId 课程ID
     * @return 导入结果
     */
    BatchImportResultVO batchImport(MultipartFile file, Long courseId);
}