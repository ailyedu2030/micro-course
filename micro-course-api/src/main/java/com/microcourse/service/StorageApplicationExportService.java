package com.microcourse.service;

/**
 * Phase 15: 申请表导出 Service 接口
 *
 * <p>负责将申请表数据渲染为 Word/PDF 文档并返回字节流。</p>
 */
public interface StorageApplicationExportService {

    /** 生成 Word 文档字节流 */
    byte[] exportWord(Long proposalId);

    /** 生成 PDF 文档字节流 */
    byte[] exportPdf(Long proposalId);
}
