package com.microcourse.util;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;

/**
 * 文件上传安全工具 (SEC-007/SEC-009 修复)
 * 统一入口 for assertSafeFilename, 防止路径穿越攻击
 */
public final class FileUploadUtil {

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
}
