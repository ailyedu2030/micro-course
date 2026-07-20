package com.microcourse.plugin.interactive.audio;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AudioStorageService · 音频文件存储 + 路径白名单 (spec 4.1 / audio/)
 *
 * 设计:
 * - 白名单: storage_root 必须在配置的 audioStorageRoot 内
 * - 路径遍历防护 (Path Traversal)
 * - 文件存在性 + 可读性校验
 */
@Component
public class AudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);

    @Value("${mc.audio.storage-root:${java.io.tmpdir}/microcourse-audio}")
    private String audioStorageRoot;

    private Path rootPath;

    @PostConstruct
    public void init() {
        try {
            this.rootPath = Paths.get(audioStorageRoot).toAbsolutePath().normalize();
            Files.createDirectories(rootPath);
            log.info("[AudioStorage] root path = {}", rootPath);
        } catch (IOException e) {
            log.error("[AudioStorage] failed to init root path: {}", audioStorageRoot, e);
            throw new IllegalStateException("AudioStorage init failed", e);
        }
    }

    /**
     * 校验 storage path 在白名单内 (BUG #25 P1 路径遍历防护)
     */
    public boolean isPathSafe(String storagePath) {
        if (storagePath == null) return false;
        try {
            Path filePath = Paths.get(storagePath).toAbsolutePath().normalize();
            return filePath.startsWith(rootPath);
        } catch (Exception e) {
            log.warn("[AudioStorage] invalid path format: {}", storagePath);
            return false;
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String storagePath) {
        if (!isPathSafe(storagePath)) return false;
        return Files.exists(Paths.get(storagePath));
    }

    /**
     * 取文件大小
     */
    public long fileSize(String storagePath) throws IOException {
        if (!isPathSafe(storagePath)) {
            throw new SecurityException("Path outside storage root: " + storagePath);
        }
        return Files.size(Paths.get(storagePath));
    }

    /**
     * 打开 InputStream (流式 GET)
     */
    public InputStream openStream(String storagePath) throws IOException {
        if (!isPathSafe(storagePath)) {
            throw new SecurityException("Path outside storage root: " + storagePath);
        }
        return Files.newInputStream(Paths.get(storagePath));
    }

    /**
     * 把外部文件名拼到 root 下, 返回规范化路径
     */
    public Path resolve(String fileName) {
        return rootPath.resolve(fileName).normalize();
    }

    public Path getRootPath() {
        return rootPath;
    }
}