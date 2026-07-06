package com.microcourse.util;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件上传安全工具 (SEC-007/SEC-009 修复)
 * 统一入口 for assertSafeFilename, 防止路径穿越攻击
 *
 * <p>所有 Controller 层的文件校验 MUST 通过此类, 不得在 Controller 直接读取 InputStream 验魔数</p>
 */
public final class FileUploadUtil {

    public static final long DEFAULT_IMAGE_MAX_BYTES = 2L * 1024 * 1024;     // 2MB
    public static final long DEFAULT_VIDEO_MAX_BYTES = 2L * 1024 * 1024 * 1024; // 2GB
    public static final long DEFAULT_VIDEO_COVER_MAX_BYTES = 5L * 1024 * 1024;   // 5MB

    private FileUploadUtil() {}

    /**
     * 拒绝含 ".." / 路径分隔符 / null 字节的恶意文件名，防止路径穿越与日志注入。
     */
    public static void assertSafeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        if (filename.contains("..") || filename.contains("/")
                || filename.contains("\\") || filename.indexOf('\u0000') >= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件名不合法");
        }
    }

    /**
     * 通用文件大小校验 (用于非特定类型文件)
     */
    public static void assertFileSize(MultipartFile file, long maxBytes, String typeLabel) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, typeLabel + "文件不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    typeLabel + "文件大小不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
    }

    /**
     * 图片文件校验: 大小 + JPEG/PNG 魔数 (统一封装, 替代 Controller 内联实现)
     * @param file 上传的图片文件
     * @param maxBytes 文件大小上限 (默认 2MB)
     */
    public static void assertImage(MultipartFile file, long maxBytes) {
        assertFileSize(file, maxBytes, "图片");
        assertImageMagic(file);
    }

    /**
     * 图片魔数校验 (JPEG: FFD8FF / PNG: 89504E47)
     */
    public static void assertImageMagic(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] magic = new byte[8];
            int read = is.read(magic);
            if (read < 4) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "图片文件过小，无法验证格式");
            }
            boolean isJpeg = (magic[0] & 0xFF) == 0xFF
                    && (magic[1] & 0xFF) == 0xD8
                    && (magic[2] & 0xFF) == 0xFF;
            boolean isPng = (magic[0] & 0xFF) == 0x89
                    && magic[1] == 'P'
                    && magic[2] == 'N'
                    && magic[3] == 'G';
            if (!isJpeg && !isPng) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "图片必须为 JPEG 或 PNG 格式（魔数校验失败）");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "图片文件读取失败: " + e.getMessage());
        }
    }

    /**
     * Excel 文件大小 + 魔数校验 (.xls D0CF11E0, .xlsx PK)
     */
    public static void assertExcel(MultipartFile file) {
        assertFileSize(file, DEFAULT_IMAGE_MAX_BYTES, "Excel 导入文件");
        assertExcelMagic(file);
    }

    /**
     * Excel 魔数校验 (xls: D0CF11E0, xlsx: 50 4B 03 04)
     */
    public static void assertExcelMagic(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] magic = new byte[8];
            int read = is.read(magic);
            if (read < 4) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "Excel 文件过小");
            }
            boolean isXls = (magic[0] & 0xFF) == 0xD0
                    && (magic[1] & 0xFF) == 0xCF
                    && (magic[2] & 0xFF) == 0x11
                    && (magic[3] & 0xFF) == 0xE0;
            boolean isXlsx = magic[0] == 0x50
                    && magic[1] == 0x4B
                    && magic[2] == 0x03
                    && magic[3] == 0x04;
            if (!isXls && !isXlsx) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "仅支持 Excel 文件 (.xls/.xlsx, 魔数校验失败)");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Excel 文件读取失败: " + e.getMessage());
        }
    }
}
