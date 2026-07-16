package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.AudioUploadResponse;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.AudioUploadService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
@Transactional(rollbackFor = Exception.class)
public class AudioUploadServiceImpl implements AudioUploadService {

    private static final Logger log = LoggerFactory.getLogger(AudioUploadServiceImpl.class);

    private static final Set<String> ALLOWED_FORMATS = Set.of("mp3", "wav", "m4a");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int DEFAULT_SAMPLE_RATE = 32000;

    private final SlidePageMapper slidePageMapper;
    private final CourseSectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    public AudioUploadServiceImpl(SlidePageMapper slidePageMapper,
                                  CourseSectionRepository sectionRepository,
                                  CourseRepository courseRepository,
                                  TransactionTemplate transactionTemplate) {
        this.slidePageMapper = slidePageMapper;
        this.sectionRepository = sectionRepository;
        this.courseRepository = courseRepository;
        this.transactionTemplate = transactionTemplate;
    }

    private Path resolveAudioDir(String courseId) {
        return Paths.get(storagePath, courseId, "audio");
    }

    private String generateAudioToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public AudioUploadResponse uploadSingle(Long courseId, Long sectionId, MultipartFile file) {
        verifyOwnership(courseId, sectionId);
        validateFile(file);

        String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        String audioFormat = ext;

        Path audioDir = resolveAudioDir(String.valueOf(courseId));
        String audioToken = generateAudioToken();
        String mergedFileName = "section_" + sectionId + "_" + audioToken + "_merged.mp3";
        Path destPath = audioDir.resolve(mergedFileName);
        String audioUrl = "/api/courses/" + courseId + "/slides/pages/1/audio?sectionId=" + sectionId + "&v=2&token=" + audioToken;

        long fileSize;
        int duration;
        try {
            Files.createDirectories(audioDir);
            Path tmpPath = audioDir.resolve("." + mergedFileName + ".tmp");
            // R4 fix (2026-07-17): 用 Files.copy(inputStream, ...) 替代 file.transferTo()
            // 原因: MultipartFile.transferTo() 在 multipart.location 配为绝对路径时,
            //       会把相对路径目标错位拼到 multipart.location 下 (例: /data/uploads/tmp/uploads/slides/...)
            //       改用直接复制输入流,避免 Spring Part.write() 的相对路径解析
            try (java.io.InputStream in = file.getInputStream()) {
                Files.copy(in, tmpPath, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmpPath, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            fileSize = Files.size(destPath);
            duration = estimateDuration(fileSize);
        } catch (IOException e) {
            log.error("[AudioUpload] file save failed: courseId={}, sectionId={}, path={}, error={}",
                    courseId, sectionId, destPath, e.getMessage(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "音频文件保存失败: " + e.getMessage());
        }

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                List<SlidePage> pages = getOrderedPages(courseId, sectionId);
                LocalDateTime now = LocalDateTime.now();
                for (SlidePage page : pages) {
                    page.setNarrationAudioUrl(audioUrl);
                    page.setAudioDuration(duration);
                    page.setNarrationStatus("AUDIO_READY");
                    page.setSegmentCount(pages.size());
                    page.setGeneratedAt(now);
                    page.setUpdatedAt(now);
                    slidePageMapper.updateById(page);
                }
            });
        } catch (Exception e) {
            try { Files.deleteIfExists(destPath); } catch (IOException ex) {
                log.warn("[AudioUpload] compensation failed to delete file: {} — {}", destPath, ex.getMessage());
            }
            throw e;
        }

        log.info("[AudioUpload] single uploaded: courseId={}, sectionId={}, size={}, duration={}s",
                courseId, sectionId, fileSize, duration);
        return AudioUploadResponse.single(audioUrl, (long) duration, fileSize, audioFormat, DEFAULT_SAMPLE_RATE);
    }

    @Override
    public AudioUploadResponse uploadBatch(Long courseId, Long sectionId, List<MultipartFile> files) {
        verifyOwnership(courseId, sectionId);

        List<SlidePage> pages = getOrderedPages(courseId, sectionId);
        boolean isHtmlSinglePage = pages.size() == 1 && pages.get(0).getContentType() != null
                && pages.get(0).getContentType().startsWith("HTML");
        if (!isHtmlSinglePage && files.size() != pages.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "上传文件数量(" + files.size() + ")与页数(" + pages.size() + ")不匹配");
        }

        for (MultipartFile file : files) {
            validateFile(file);
        }

        Path audioDir = resolveAudioDir(String.valueOf(courseId));
        long totalSize = 0;
        int totalDuration = 0;

        String audioToken = generateAudioToken();

        Map<Long, PageAudioMeta> pageMetas = new LinkedHashMap<>();
        for (int i = 0; i < pages.size(); i++) {
            SlidePage page = pages.get(i);
            int pageNum = page.getPageNumber();
            String segUrl = "/api/courses/" + courseId + "/slides/pages/" + pageNum
                    + "/audio?sectionId=" + sectionId + "&v=2&token=" + audioToken;
            pageMetas.put(page.getId(), new PageAudioMeta(segUrl, 0, null));
        }

        List<Path> savedPaths = new ArrayList<>();
        try {
            Files.createDirectories(audioDir);
            for (int i = 0; i < pages.size(); i++) {
                SlidePage page = pages.get(i);
                MultipartFile file = files.get(i);
                String segFileName = "section_" + sectionId + "_" + audioToken + "_page_" + page.getPageNumber() + ".mp3";
                Path segPath = audioDir.resolve(segFileName);
                Path tmpPath = audioDir.resolve("." + segFileName + ".tmp");
                // R4 fix (2026-07-17): 同 single upload,改用 Files.copy(inputStream, ...)
                try (java.io.InputStream in = file.getInputStream()) {
                    Files.copy(in, tmpPath, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmpPath, segPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                savedPaths.add(segPath);
                long segSize = Files.size(segPath);
                int segDuration = estimateDuration(segSize);
                totalSize += segSize;
                totalDuration += segDuration;
                pageMetas.get(page.getId()).duration = segDuration;
                pageMetas.get(page.getId()).generatedAt = LocalDateTime.now();
            }
        } catch (IOException e) {
            for (Path p : savedPaths) {
                try { Files.deleteIfExists(p); } catch (IOException cleanupEx) {
                    log.warn("[AudioUpload] cleanup failed to delete partial file: {} — {}", p, cleanupEx.getMessage());
                }
            }
            log.error("[AudioUpload] batch file save failed: courseId={}, sectionId={}, path={}, error={}",
                    courseId, sectionId, audioDir, e.getMessage(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "分段音频文件保存失败: " + e.getMessage());
        }

        final long finalTotalSize = totalSize;
        final int finalTotalDuration = totalDuration;
        final int segCount = files.size();
        final Map<Long, PageAudioMeta> finalPageMetas = new LinkedHashMap<>(pageMetas);

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                LocalDateTime now = LocalDateTime.now();
                for (SlidePage page : pages) {
                    PageAudioMeta meta = finalPageMetas.get(page.getId());
                    page.setNarrationAudioUrl(meta.audioUrl);
                    page.setAudioDuration(meta.duration);
                    page.setNarrationStatus("AUDIO_READY");
                    page.setSegmentCount(segCount);
                    page.setGeneratedAt(meta.generatedAt);
                    page.setUpdatedAt(now);
                    slidePageMapper.updateById(page);
                }
            });
        } catch (Exception e) {
            for (Path p : savedPaths) {
                try { Files.deleteIfExists(p); } catch (IOException ex) {
                    log.warn("[AudioUpload] compensation failed to delete file: {} — {}", p, ex.getMessage());
                }
            }
            throw e;
        }

        String mergedUrl = "/api/courses/" + courseId + "/slides/pages/1/audio?sectionId="
                + sectionId + "&v=2&merged=true&token=" + audioToken;

        log.info("[AudioUpload] batch uploaded: courseId={}, sectionId={}, segments={}, totalSize={}",
                courseId, sectionId, files.size(), totalSize);

        return AudioUploadResponse.merged(mergedUrl, (long) totalDuration, totalSize,
                "mp3", DEFAULT_SAMPLE_RATE, files.size());
    }

    @Override
    public void verifyOwnership(Long courseId, Long sectionId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (sectionId != null) {
            CourseSection section = sectionRepository.selectById(sectionId);
            if (section == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "小节不存在");
            }
            if (!section.getCourseId().equals(courseId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "小节不属于该课程");
            }
        }
    }

    private List<SlidePage> getOrderedPages(Long courseId, Long sectionId) {
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getSectionId, sectionId)
                .orderByAsc(SlidePage::getPageNumber);
        List<SlidePage> pages = slidePageMapper.selectList(wrapper);
        if (pages.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "该小节没有 SlidePage 记录，请先上传 HTML 课件");
        }
        return pages;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "单个文件大小不能超过 50MB");
        }
        String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_FORMATS.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "不支持的音频格式，仅支持: mp3, wav, m4a");
        }
    }

    private int estimateDuration(long fileSizeBytes) {
        long bytesPerSec = 16000L;
        return Math.max(1, (int) (fileSizeBytes / bytesPerSec));
    }

    private static class PageAudioMeta {
        final String audioUrl;
        int duration;
        LocalDateTime generatedAt;
        PageAudioMeta(String audioUrl, int duration, LocalDateTime generatedAt) {
            this.audioUrl = audioUrl;
            this.duration = duration;
            this.generatedAt = generatedAt;
        }
    }
}
