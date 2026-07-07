package com.microcourse.util;

import com.microcourse.entity.Video;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 视频磁盘清理工具 (从 VideoServiceImpl 提取以减少 800 行)
 *
 * <p>负责清理视频相关的磁盘文件: HLS 转码目录、原始上传文件、封面目录。</p>
 */
public final class VideoDiskCleanup {

    private static final Logger log = LoggerFactory.getLogger(VideoDiskCleanup.class);

    private VideoDiskCleanup() {}

    /**
     * 清理视频的所有磁盘文件
     * @param video 视频实体
     * @param storageBaseDir HLS 转码根目录
     * @param coverDir 封面根目录
     */
    public static void cleanup(Video video, String storageBaseDir, String coverDir) {
        // 清理 HLS 转码目录: {storageBaseDir}/{courseId}/{videoId}/
        if (video.getCourseId() != null && video.getId() != null) {
            Path hlsDir = Paths.get(storageBaseDir,
                    String.valueOf(video.getCourseId()),
                    String.valueOf(video.getId()));
            deleteDirectoryQuietly(hlsDir);
        }

        // 清理原始上传文件
        if (video.getOriginalPath() != null && !video.getOriginalPath().isBlank()) {
            deleteFileQuietly(Paths.get(video.getOriginalPath()));
        }

        // 清理封面文件目录
        if (video.getId() != null) {
            Path coverPath = Paths.get(coverDir, String.valueOf(video.getId()));
            deleteDirectoryQuietly(coverPath);
        }
    }

    /**
     * 静默删除目录 (失败仅 warn, 不抛异常)
     */
    public static void deleteDirectoryQuietly(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    log.warn("[VideoCleanup] 删除文件失败: {}", p, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.warn("[VideoCleanup] 遍历目录失败: {}", dir, e);
        }
    }

    /**
     * 静默删除文件 (失败仅 warn, 不抛异常)
     */
    public static void deleteFileQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[VideoCleanup] 删除文件失败: {}", file, e);
        }
    }
}