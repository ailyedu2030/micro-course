package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.TtsService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TtsServiceImpl implements TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsServiceImpl.class);

    private final SlidePageMapper slidePageMapper;
    private final CourseRepository courseRepository;

    @Value("${plugin.interactive.minimax.tts-model:speech-2.8-hd}")
    private String ttsModel;

    @Value("${plugin.interactive.minimax.tts-voice:Chinese_female_narrator}")
    private String ttsVoice;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    private static final String MMX_CMD = "mmx";

    public TtsServiceImpl(SlidePageMapper slidePageMapper,
                          CourseRepository courseRepository) {
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlidePageVO generate(Long courseId, Integer pageNumber) {
        checkOwner(courseId);

        SlidePage page = getPage(courseId, pageNumber);
        String script = page.getNarrationScript();
        if (script == null || script.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
        }

        String audioFileName = "page_" + pageNumber + ".mp3";

        Path tempTextFile = null;
        try {
            Path audioDir = Paths.get(storagePath, String.valueOf(courseId), "audio");
            Files.createDirectories(audioDir);

            tempTextFile = Files.createTempFile("narration_", ".txt");
            Files.writeString(tempTextFile, script);

            Path audioPath = audioDir.resolve(audioFileName);

            page.setNarrationStatus("AUDIO_GENERATING");
            page.setUpdatedAt(LocalDateTime.now());
            slidePageMapper.updateById(page);

            ProcessBuilder pb = new ProcessBuilder(
                    MMX_CMD, "speech", "synthesize",
                    "--text-file", tempTextFile.toString(),
                    "--voice", ttsVoice,
                    "--model", ttsModel,
                    "--format", "mp3",
                    "--out", audioPath.toString(),
                    "--quiet"
            );

            Process process = pb.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                page.setNarrationStatus("TEACHER_EDITED");
                page.setUpdatedAt(LocalDateTime.now());
                slidePageMapper.updateById(page);
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                StringBuilder errMsg = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) { errMsg.append(line); }
                }
                log.error("TTS CLI failed: exit={}, stderr={}", exitCode, errMsg);
                page.setNarrationStatus("TEACHER_EDITED");
                page.setUpdatedAt(LocalDateTime.now());
                slidePageMapper.updateById(page);
                throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED);
            }

            long fileSize = Files.size(audioPath);
            int audioDuration = estimateDuration(fileSize);

            page.setNarrationAudioUrl("/api/courses/" + courseId + "/slides/pages/" + pageNumber + "/audio");
            page.setAudioDuration(audioDuration);
            page.setNarrationStatus("AUDIO_READY");
            page.setUpdatedAt(LocalDateTime.now());
            slidePageMapper.updateById(page);

            log.info("TTS complete: courseId={}, page={}, duration={}s", courseId, pageNumber, audioDuration);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS failed for courseId={} page={}", courseId, pageNumber, e);
            page.setNarrationStatus("TEACHER_EDITED");
            page.setUpdatedAt(LocalDateTime.now());
            slidePageMapper.updateById(page);
            throw new BusinessException(ErrorCode.TTS_GENERATE_FAILED);
        } finally {
            if (tempTextFile != null) {
                try { Files.deleteIfExists(tempTextFile); } catch (IOException ignored) {
                    log.warn("Failed to delete TTS temp file: {}", tempTextFile);
                }
            }
        }

        return toPageVO(page);
    }

    @Override
    @Async("slideRenderExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void generateAll(Long courseId) {
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .in(SlidePage::getNarrationStatus, "AI_GENERATED", "TEACHER_EDITED")
                .orderByAsc(SlidePage::getPageNumber);
        List<SlidePage> pages = slidePageMapper.selectList(wrapper);

        for (SlidePage page : pages) {
            try {
                generate(courseId, page.getPageNumber());
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (BusinessException e) {
                log.warn("TTS failed for page {}: {}", page.getPageNumber(), e.getMessage());
            }
        }
    }

    private int estimateDuration(long fileSizeBytes) {
        int estimatedSeconds = (int) (fileSizeBytes / 2000);
        return Math.max(1, estimatedSeconds);
    }

    private SlidePage getPage(Long courseId, Integer pageNumber) {
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getPageNumber, pageNumber);
        SlidePage page = slidePageMapper.selectOne(wrapper);
        if (page == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        }
        return page;
    }

    private void checkOwner(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private SlidePageVO toPageVO(SlidePage page) {
        return new SlidePageVO() {{
            setId(page.getId());
            setSlideId(page.getSlideId());
            setCourseId(page.getCourseId());
            setPageNumber(page.getPageNumber());
            setImageUrl(page.getImageUrl());
            setThumbnailUrl(page.getThumbnailUrl());
            setImageWidth(page.getImageWidth());
            setImageHeight(page.getImageHeight());
            setExtractedText(page.getExtractedText());
            setHasAnimation(page.getHasAnimation());
            setHasEmbeddedMedia(page.getHasEmbeddedMedia());
            setNarrationScript(page.getNarrationScript());
            setNarrationAudioUrl(page.getNarrationAudioUrl());
            setAudioDuration(page.getAudioDuration());
            setNarrationStatus(page.getNarrationStatus());
            setNarrationStatusText(SlidePageVO.narrationStatusText(page.getNarrationStatus()));
            setCreatedAt(page.getCreatedAt());
            setUpdatedAt(page.getUpdatedAt());
        }};
    }
}
