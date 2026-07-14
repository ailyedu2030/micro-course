package com.microcourse.service;

import com.microcourse.dto.QuestionCreateRequest;
import com.microcourse.dto.QuestionUpdateRequest;
import com.microcourse.dto.QuestionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.BatchImportResultVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionService {

    QuestionVO create(QuestionCreateRequest request);

    QuestionVO update(Long id, QuestionUpdateRequest request);

    void delete(Long id);

    PageResult<QuestionVO> page(Long courseId, String questionType, Integer difficulty, String keyword, Long categoryId, Long chapterId, Integer page, Integer size);

    QuestionVO getById(Long id);

    /**
     * 批量导入题目（Excel 解析）
     * @param file Excel 文件
     * @param courseId 课程ID
     * @return 导入结果
     */
    BatchImportResultVO batchImport(MultipartFile file, Long courseId);

    /**
     * 导出题目到 Excel
     * @param courseId 课程ID（可选，为 null 则导出全部可访问题目）
     * @param questionType 题目类型筛选（可选）
     * @param difficulty 难度筛选（可选）
     * @param keyword 关键字筛选（可选）
     * @param response HTTP 响应
     */
    void export(Long courseId, String questionType, Integer difficulty, String keyword,
                HttpServletResponse response);
}