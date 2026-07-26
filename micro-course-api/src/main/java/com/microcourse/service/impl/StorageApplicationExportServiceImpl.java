package com.microcourse.service.impl;

import com.microcourse.dto.storage.StorageApplicationVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.StorageApplicationExportService;
import com.microcourse.service.StorageApplicationQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 15: 申请表导出 Service 实现
 *
 * <p>核心职责：从 DB 加载完整数据，委派给 PdfGenerator / WordGenerator 生成字节流。</p>
 */
@Service
public class StorageApplicationExportServiceImpl implements StorageApplicationExportService {

    private static final Logger log = LoggerFactory.getLogger(StorageApplicationExportServiceImpl.class);

    private final StorageApplicationQueryService storageApplicationQueryService;
    private final StorageApplicationPdfGenerator pdfGenerator;
    private final StorageApplicationWordGenerator wordGenerator;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public StorageApplicationExportServiceImpl(
            StorageApplicationQueryService storageApplicationQueryService,
            StorageApplicationPdfGenerator pdfGenerator,
            StorageApplicationWordGenerator wordGenerator,
            PlatformTransactionManager transactionManager) {
        this.storageApplicationQueryService = storageApplicationQueryService;
        this.pdfGenerator = pdfGenerator;
        this.wordGenerator = wordGenerator;
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    @Override
    public byte[] exportWord(Long proposalId) {
        log.info("exportWord: proposalId={}", proposalId);
        StorageApplicationVO snapshot = loadExportSnapshot(proposalId);
        return wordGenerator.generate(snapshot);
    }

    @Override
    public byte[] exportPdf(Long proposalId) {
        log.info("exportPdf: proposalId={}", proposalId);
        StorageApplicationVO snapshot = loadExportSnapshot(proposalId);
        return pdfGenerator.generate(snapshot);
    }

    private StorageApplicationVO loadExportSnapshot(Long proposalId) {
        StorageApplicationVO data = readOnlyTransactionTemplate.execute(status ->
                storageApplicationQueryService.getDetail(proposalId, null));
        if (data == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if ("WITHDRAWN".equals(data.getStatus())) {
            throw new BusinessException(ErrorCode.SA_STATUS_INVALID, "已撤回的申请表不可导出");
        }
        return data;
    }
}
