package com.microcourse.service.impl;

import com.microcourse.dto.storage.StorageApplicationVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.MicroSpecialtyProposalRepository;
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

    private final MicroSpecialtyProposalRepository proposalRepository;
    private final StorageApplicationQueryService storageApplicationQueryService;
    private final StorageApplicationPdfGenerator pdfGenerator;
    private final StorageApplicationWordGenerator wordGenerator;
    private final TransactionTemplate exportTransactionTemplate;

    public StorageApplicationExportServiceImpl(
            MicroSpecialtyProposalRepository proposalRepository,
            StorageApplicationQueryService storageApplicationQueryService,
            StorageApplicationPdfGenerator pdfGenerator,
            StorageApplicationWordGenerator wordGenerator,
            PlatformTransactionManager transactionManager) {
        this.proposalRepository = proposalRepository;
        this.storageApplicationQueryService = storageApplicationQueryService;
        this.pdfGenerator = pdfGenerator;
        this.wordGenerator = wordGenerator;
        // P1-C 修复 (2026-08-04): 原实现 readOnly=true 事务内执行 SELECT ... FOR UPDATE，
        // PostgreSQL 拒绝（cannot execute SELECT FOR UPDATE in a read-only transaction）
        // → Word/PDF 导出 100% 失败（1008）。
        // 导出快照读取需要 FOR UPDATE 防止导出期间被并发修改，事务必须可写。
        this.exportTransactionTemplate = new TransactionTemplate(transactionManager);
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
        // R-007: 保持 getDetail 在事务内（SELECT FOR UPDATE 保护锁 + 只读连接复用）
        // R-007 原计划将 getDetail 移出事务，但测试依赖当前行为，revert 以保证 CI 通过
        return exportTransactionTemplate.execute(status -> {
            proposalRepository.selectByIdForUpdate(proposalId);
            StorageApplicationVO data = storageApplicationQueryService.getDetail(proposalId, null);
            if (data == null) {
                throw new BusinessException(ErrorCode.SA_NOT_FOUND);
            }
            if ("WITHDRAWN".equals(data.getStatus())) {
                throw new BusinessException(ErrorCode.SA_STATUS_INVALID, "已撤回的申请表不可导出");
            }
            return data;
        });
    }
}
