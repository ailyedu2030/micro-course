package com.microcourse.service.impl;

import com.microcourse.dto.storage.StorageApplicationVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.StorageApplicationExportService;
import com.microcourse.service.StorageApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 15: 申请表导出 Service 实现
 *
 * <p>核心职责：从 DB 加载完整数据，委派给 PdfGenerator / WordGenerator 生成字节流。</p>
 */
@Service
public class StorageApplicationExportServiceImpl implements StorageApplicationExportService {

    private static final Logger log = LoggerFactory.getLogger(StorageApplicationExportServiceImpl.class);

    private final StorageApplicationService storageApplicationService;
    private final StorageApplicationPdfGenerator pdfGenerator;
    private final StorageApplicationWordGenerator wordGenerator;

    public StorageApplicationExportServiceImpl(
            StorageApplicationService storageApplicationService,
            StorageApplicationPdfGenerator pdfGenerator,
            StorageApplicationWordGenerator wordGenerator) {
        this.storageApplicationService = storageApplicationService;
        this.pdfGenerator = pdfGenerator;
        this.wordGenerator = wordGenerator;
    }

    /**
     * P2 fix (C-006): Wrap export read in read-only transaction to prevent
     * inconsistent data if save/autoSave writes concurrently.
     */
    @Override
    @Transactional(readOnly = true)
    public byte[] exportWord(Long proposalId) {
        log.info("exportWord: proposalId={}", proposalId);
        StorageApplicationVO data = storageApplicationService.getDetail(proposalId, null);

        if (data == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }

        return wordGenerator.generate(data);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportPdf(Long proposalId) {
        log.info("exportPdf: proposalId={}", proposalId);
        StorageApplicationVO data = storageApplicationService.getDetail(proposalId, null);

        if (data == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }

        return pdfGenerator.generate(data);
    }
}
