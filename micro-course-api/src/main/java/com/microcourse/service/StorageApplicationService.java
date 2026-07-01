package com.microcourse.service;

import com.microcourse.dto.storage.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * Phase 15: 微专业申请表 Storage Application Service 接口
 *
 * <p>职责：草稿初始化、全量保存、自动保存、上传图片、提交审核、
 * 预览构建、重置/导出校验。对应前端 5 个动态模块的 DSM。</p>
 */
public interface StorageApplicationService {

    /** 1. 初始化空草稿 — 创建一条 DRAFT 状态的 proposal */
    Long initDraft(Long userId);

    /** 2. 获取我的申请列表 */
    List<StorageApplicationSummaryVO> getMyDrafts(Long userId);

    /** 3. 获取详情（含所有子表） */
    StorageApplicationVO getDetail(Long proposalId, Long userId);

    /** 4. 全量保存（含所有子表增删改） */
    StorageApplicationVO save(Long proposalId, Long userId, StorageApplicationSaveRequest request);

    /** 5. 自动保存（轻量级，只保存变更字段） */
    void autoSave(Long proposalId, Long userId, StorageApplicationSaveRequest request);

    /** 6. 上传签名/公章图片 */
    UploadResultVO uploadImage(Long proposalId, Long userId, MultipartFile file, String type);

    /** 7. 构建预览数据 */
    StorageApplicationPreviewVO buildPreview(Long proposalId, Long userId);

    /** 8. 提交审核 */
    void submit(Long proposalId, Long userId);

    /** 9. 重置单个模块 */
    void resetModule(Long proposalId, Long userId, String module);

    /** 10. 重置全部 */
    void resetAll(Long proposalId, Long userId);
    /** 校验当前用户是否为申报 owner（P0-1 IDOR 防御） */
    void validateOwner(Long proposalId, Long userId);

    /** 11. 导出前置校验 */
    ExportValidationResult validateForExport(Long proposalId, Long userId);
}
