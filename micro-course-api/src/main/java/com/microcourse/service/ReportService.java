package com.microcourse.service;

import com.microcourse.dto.CreateReportRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.ReviewReportVO;
import com.microcourse.dto.ReviewReportActionRequest;

/**
 * 举报处理 Service
 */
public interface ReportService {

    /**
     * 提交举报
     */
    void create(Long userId, CreateReportRequest req);

    /**
     * 管理员查看举报列表
     */
    PageResult<ReviewReportVO> pageByAdmin(int page, int size, Integer status);

    /**
     * 管理员审核
     */
    void review(Long reportId, ReviewReportActionRequest req, Long reviewerId);
}
