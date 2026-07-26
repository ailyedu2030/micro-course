package com.microcourse.service;

import com.microcourse.dto.storage.UploadResultVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.impl.StorageApplicationImageStorageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StorageApplicationImageStorageService 图片存储")
class StorageApplicationImageStorageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("storeImage 必须在清理旧文件后返回新的上传地址")
    void storeImageReplacesExistingTypeFile() throws Exception {
        StorageApplicationImageStorageServiceImpl service = new StorageApplicationImageStorageServiceImpl();
        String previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            Path proposalDir = tempDir.resolve("uploads/storage/42");
            Files.createDirectories(proposalDir);
            Files.writeString(proposalDir.resolve("seal_old.png"), "old");

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "seal.png",
                    "image/png",
                    new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01}
            );

            UploadResultVO result = service.storeImage(42L, file, "seal");

            assertTrue(result.getUrl().startsWith("/uploads/storage/42/seal_"));
            assertEquals(1, Files.list(proposalDir).count());
        } finally {
            System.setProperty("user.dir", previousUserDir);
        }
    }

    @Test
    @DisplayName("storeImage 对扩展名与魔数不匹配的图片必须拒绝")
    void storeImageRejectsMagicExtensionMismatch() {
        StorageApplicationImageStorageServiceImpl service = new StorageApplicationImageStorageServiceImpl();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "seal.jpg",
                "image/jpeg",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01}
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> service.storeImage(42L, file, "seal"));

        assertEquals(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE.getCode(), ex.getCode());
    }
}
