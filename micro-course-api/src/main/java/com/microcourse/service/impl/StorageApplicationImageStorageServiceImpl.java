package com.microcourse.service.impl;

import com.microcourse.dto.storage.UploadResultVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.StorageApplicationImageStorageService;
import com.microcourse.util.FileUploadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 申报图片存储服务实现。
 */
@Service
public class StorageApplicationImageStorageServiceImpl implements StorageApplicationImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageApplicationImageStorageServiceImpl.class);

    private static final int TARGET_IMAGE_SIZE = 150;

    @Override
    public UploadResultVO storeImage(Long proposalId, MultipartFile file, String type) {
        FileUploadUtil.assertSafeFilename(file.getOriginalFilename());
        validateImage(file);

        String originalFilename = file.getOriginalFilename();
        String lowerName = originalFilename.toLowerCase();

        try {
            String uploadDir = "uploads/storage/" + proposalId;
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            cleanupPreviousFiles(uploadPath, proposalId, type);

            String ext = lowerName.endsWith(".png") ? ".png" : ".jpg";
            String newFileName = type + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path destPath = uploadPath.resolve(newFileName);

            saveResizedImage(file, ext, destPath, proposalId);
            String url = "/" + uploadDir + "/" + newFileName;
            return new UploadResultVO(url, originalFilename, file.getSize());
        } catch (IOException e) {
            log.error("uploadImage failed: proposalId={}", proposalId, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "图片上传失败");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "文件名不能为空");
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg") && !lowerName.endsWith(".png")) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE);
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_TOO_LARGE);
        }

        boolean isJpegMagic = false;
        boolean isPngMagic = false;
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header, 0, 8);
            if (read >= 8) {
                if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                    isJpegMagic = true;
                }
                if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E
                        && header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A
                        && header[6] == 0x1A && header[7] == 0x0A) {
                    isPngMagic = true;
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "无法读取文件内容");
        }

        if (!isJpegMagic && !isPngMagic) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "文件内容不是有效的 jpg/png 图片");
        }

        boolean isJpegExt = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
        boolean isPngExt = lowerName.endsWith(".png");
        if (isJpegMagic && !isJpegExt) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                    "文件内容与扩展名不匹配：JPEG 内容需使用 .jpg/.jpeg 扩展名");
        }
        if (isPngMagic && !isPngExt) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                    "文件内容与扩展名不匹配：PNG 内容需使用 .png 扩展名");
        }
    }

    private void cleanupPreviousFiles(Path uploadPath, Long proposalId, String type) {
        if (!Files.exists(uploadPath)) {
            return;
        }
        try (var dirStream = Files.newDirectoryStream(uploadPath, type + "_*")) {
            for (Path oldFile : dirStream) {
                Files.deleteIfExists(oldFile);
                log.info("Deleted old image: proposalId={}, file={}", proposalId, oldFile);
            }
        } catch (IOException e) {
            log.warn("Failed to clean old images for proposalId={}, type={}", proposalId, type, e);
        }
    }

    private void saveResizedImage(MultipartFile file, String ext, Path destPath, Long proposalId) throws IOException {
        try (InputStream is = file.getInputStream()) {
            BufferedImage original = ImageIO.read(is);
            if (original != null) {
                BufferedImage resized = new BufferedImage(TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = resized.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(original, 0, 0, TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, null);
                graphics.dispose();
                ImageIO.write(resized, ext.equals(".png") ? "png" : "jpg", destPath.toFile());
                return;
            }
        } catch (Exception e) {
            log.warn("Image resize failed, saving original: proposalId={}", proposalId, e);
        }
        file.transferTo(destPath.toFile());
    }
}
