package com.microcourse.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * 文件哈希计算工具 (从 VideoServiceImpl 提取以减少 800 行)
 */
public final class HashUtil {

    private static final Logger log = LoggerFactory.getLogger(HashUtil.class);

    private static final int BUFFER_SIZE = 8192;

    private HashUtil() {}

    /**
     * 计算文件 MD5
     * @param filePath 文件路径
     * @return MD5 字符串 (32 字符十六进制), 失败返回 null
     */
    public static String computeFileMd5(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = is.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[HashUtil] MD5 计算失败 path={}", filePath, e);
            return null;
        }
    }
}
