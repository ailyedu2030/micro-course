package com.microcourse.service;

import com.microcourse.dto.storage.StorageApplicationVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.impl.StorageApplicationExportServiceImpl;
import com.microcourse.service.impl.StorageApplicationPdfGenerator;
import com.microcourse.service.impl.StorageApplicationWordGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("StorageApplicationExportServiceImpl 导出事务边界")
class StorageApplicationExportServiceImplTest {

    @Test
    @DisplayName("exportWord 必须在只读事务内加载快照，并在事务外生成文档")
    void exportWordLoadsSnapshotInsideTransactionAndGeneratesOutside() {
        StorageApplicationQueryService queryService = mock(StorageApplicationQueryService.class);
        StorageApplicationPdfGenerator pdfGenerator = mock(StorageApplicationPdfGenerator.class);
        StorageApplicationWordGenerator wordGenerator = mock(StorageApplicationWordGenerator.class);
        TrackingTransactionManager txManager = new TrackingTransactionManager();
        StorageApplicationExportServiceImpl service = new StorageApplicationExportServiceImpl(
                queryService, pdfGenerator, wordGenerator, txManager);

        StorageApplicationVO snapshot = new StorageApplicationVO();
        snapshot.setStatus("APPROVED");

        when(queryService.getDetail(42L, null)).thenAnswer(invocation -> {
            assertTrue(txManager.isActive(), "查询快照必须发生在事务内");
            return snapshot;
        });
        when(wordGenerator.generate(snapshot)).thenAnswer(invocation -> {
            assertFalse(txManager.isActive(), "文档生成必须发生在事务外");
            return "word-bytes".getBytes();
        });

        byte[] exported = service.exportWord(42L);

        assertArrayEquals("word-bytes".getBytes(), exported);
        assertFalse(txManager.isActive(), "导出结束后事务必须关闭");
        assertTrue(txManager.isReadOnly(), "快照查询必须使用只读事务");
    }

    @Test
    @DisplayName("exportPdf 遇到 WITHDRAWN 申请必须阻断生成器")
    void exportPdfRejectsWithdrawnSnapshotBeforeGeneration() {
        StorageApplicationQueryService queryService = mock(StorageApplicationQueryService.class);
        StorageApplicationPdfGenerator pdfGenerator = mock(StorageApplicationPdfGenerator.class);
        StorageApplicationWordGenerator wordGenerator = mock(StorageApplicationWordGenerator.class);
        TrackingTransactionManager txManager = new TrackingTransactionManager();
        StorageApplicationExportServiceImpl service = new StorageApplicationExportServiceImpl(
                queryService, pdfGenerator, wordGenerator, txManager);

        StorageApplicationVO snapshot = new StorageApplicationVO();
        snapshot.setStatus("WITHDRAWN");
        when(queryService.getDetail(77L, null)).thenReturn(snapshot);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportPdf(77L));

        assertEquals(ErrorCode.SA_STATUS_INVALID.getCode(), ex.getCode());
        verifyNoInteractions(pdfGenerator);
    }

    private static final class TrackingTransactionManager implements PlatformTransactionManager {
        private boolean active;
        private boolean readOnly;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            this.active = true;
            this.readOnly = definition.isReadOnly();
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            this.active = false;
        }

        @Override
        public void rollback(TransactionStatus status) {
            this.active = false;
        }

        boolean isActive() {
            return active;
        }

        boolean isReadOnly() {
            return readOnly;
        }
    }
}
