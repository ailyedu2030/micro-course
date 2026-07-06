package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.storage.*;

/**
 * 微专业申请表 Query Service 接口
 *
 * <p>仅含只读/查询方法，从 {@link StorageApplicationService} 中抽取，
 * 职责：我的申请列表、详情、预览、导出校验、Owner 校验。</p>
 */
public interface StorageApplicationQueryService {

    /** 获取我的申请列表（分页） */
    PageResult<StorageApplicationSummaryVO> getMyDrafts(Long userId, int page, int size);

    /** P1C-091: 获取待审批列表（ACADEMIC） */
    PageResult<StorageApplicationSummaryVO> getPendingList(int page, int size);

    /** 获取详情（含所有子表） */
    StorageApplicationVO getDetail(Long proposalId, Long userId);

    /** 构建预览数据 */
    StorageApplicationPreviewVO buildPreview(Long proposalId, Long userId);

    /** 校验当前用户是否为申报 owner（P0-1 IDOR 防御） */
    void validateOwner(Long proposalId, Long userId);

    /** 导出前置校验 */
    ExportValidationResult validateForExport(Long proposalId, Long userId);

    /** 构建提交校验请求（从DB读取完整数据） */
    StorageApplicationSaveRequest buildValidationRequest(Long proposalId);
}
