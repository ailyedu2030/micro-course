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
import java.nio.file.StandardCopyOption;
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
            // P0 修复 (2026-08-04): 相对路径 + file.transferTo() 会被 multipart.location
            // 前缀拼接（/data/uploads/tmp/uploads/storage/...），父目录不存在 → 500。
            // 绝对化后与 FileAccessController 的读取路径（uploadBaseDir 绝对化）保持一致。
            String uploadDir = "uploads/storage/" + proposalId;
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
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

        byte[] header;
        try (InputStream is = file.getInputStream()) {
            header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "文件内容过短，无法验证格式");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "无法读取文件内容");
        }

        // S-004: 委托给 validateImageMagicBytes（SVG 拒绝 + 双扩展名拒绝 + 魔数校验）
        validateImageMagicBytes(header, originalFilename);

        // 魔数 vs 扩展名交叉验证
        boolean isJpegMagic = (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
        boolean isPngMagic = header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E
                && header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A
                && header[6] == 0x1A && header[7] == 0x0A;
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

    /**
     * S-004: 图片魔数 + 扩展名安全校验
     * <p>
     * - 验证 JPEG 魔数 (FF D8 FF) 或 PNG 魔数 (89 50 4E 47 0D 0A 1A 0A)<br>
     * - 拒绝 SVG 文件 (XML 文本头，含脚本注入风险)<br>
     * - 拒绝双扩展名文件 (如 shell.jpg.php)
     * </p>
     *
     * @param bytes    文件前 12 字节
     * @param filename 原始文件名
     * @throws BusinessException 图片格式不支持时抛出
     */
    private void validateImageMagicBytes(byte[] bytes, String filename) {
        // 1. 拒绝 SVG（XML 文本头检测）
        if (bytes.length >= 4) {
            boolean isSvg = (bytes[0] == '<' && bytes[1] == 's' && bytes[2] == 'v' && bytes[3] == 'g')
                    || (bytes[0] == '<' && bytes[1] == '?' && bytes[2] == 'x' && bytes[3] == 'm')
                    || (bytes[0] == '<' && bytes[1] == '!' && bytes[2] == 'D' && bytes[3] == 'O');
            if (isSvg) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片格式不支持，仅允许 JPG/PNG");
            }
        }

        // 2. 验证 JPEG 魔数：FF D8 FF
        boolean isJpeg = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;

        // 3. 验证 PNG 魔数：89 50 4E 47 0D 0A 1A 0A
        boolean isPng = bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;

        if (!isJpeg && !isPng) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片格式不支持，仅允许 JPG/PNG");
        }

        // 4. 拒绝双扩展名：如 shell.jpg.php
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.contains(".jpg.") || lower.contains(".jpeg.") || lower.contains(".png.")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片格式不支持，仅允许 JPG/PNG");
            }
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
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, destPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
