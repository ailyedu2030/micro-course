package com.microcourse.plugin.interactive.audio;

import com.microcourse.plugin.interactive.cache.AudioStreamCache;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AudioQueryService · 音频查询统一入口 (spec 4.1 / audio/)
 *
 * 设计:
 * - 入口: resolveByToken (PPT + HTML + Legacy 三表 UNION)
 * - 缓存: AudioStreamCache (Redis 5min TTL)
 * - 7-19 P0 兼容: 仅依赖 audio_token
 */
@Component
public class AudioQueryService {

    private static final Logger log = LoggerFactory.getLogger(AudioQueryService.class);

    private final SlidePptPageAudioMapper pptAudioMapper;
    private final SlideHtmlSegmentAudioMapper htmlAudioMapper;
    private final AudioStreamCache cache;
    private final AudioTokenService tokenService;

    @Autowired
    public AudioQueryService(SlidePptPageAudioMapper pptAudioMapper,
                              SlideHtmlSegmentAudioMapper htmlAudioMapper,
                              AudioStreamCache cache,
                              AudioTokenService tokenService) {
        this.pptAudioMapper = pptAudioMapper;
        this.htmlAudioMapper = htmlAudioMapper;
        this.cache = cache;
        this.tokenService = tokenService;
    }

    /**
     * 按 token 解析音频流信息
     * @return Optional.empty() 表示未找到
     */
    public Optional<AudioStreamInfo> resolveByToken(String token) {
        if (!tokenService.isValidToken(token)) {
            tokenService.recordInvalidToken(token, "AudioQueryService");
            return Optional.empty();
        }

        // 1. Redis 缓存命中
        Optional<AudioStreamInfo> cached = cache.get(token);
        if (cached.isPresent()) {
            log.debug("[AudioQuery] cache HIT: token={}", token);
            return cached;
        }

        // 2. DB 查询: PPT 表
        SlidePptPageAudio pptAudio = pptAudioMapper.findByToken(token);
        if (pptAudio != null) {
            AudioStreamInfo info = mapPpt(pptAudio);
            cache.put(token, info);
            return Optional.of(info);
        }

        // 3. DB 查询: HTML 表
        SlideHtmlSegmentAudio htmlAudio = htmlAudioMapper.findByToken(token);
        if (htmlAudio != null) {
            AudioStreamInfo info = mapHtml(htmlAudio);
            cache.put(token, info);
            return Optional.of(info);
        }

        log.warn("[AudioQuery] token not found in any audio table: token.length={}", token.length());
        return Optional.empty();
    }

    private AudioStreamInfo mapPpt(SlidePptPageAudio a) {
        AudioStreamInfo info = new AudioStreamInfo();
        info.setToken(a.getAudioToken());
        info.setAudioUrl(a.getAudioUrl());
        info.setStatus(a.getStatus());
        info.setStoragePath(a.getStoragePath());
        info.setAudioDurationMs(a.getAudioDurationMs());
        info.setFileSizeBytes(a.getFileSizeBytes());
        info.setScriptId(a.getScriptId());
        info.setCoursewareType("PPT");
        info.setOwnerId(a.getPptPageId());
        return info;
    }

    private AudioStreamInfo mapHtml(SlideHtmlSegmentAudio a) {
        AudioStreamInfo info = new AudioStreamInfo();
        info.setToken(a.getAudioToken());
        info.setAudioUrl(a.getAudioUrl());
        info.setStatus(a.getStatus());
        info.setStoragePath(a.getStoragePath());
        info.setAudioDurationMs(a.getAudioDurationMs());
        info.setFileSizeBytes(a.getFileSizeBytes());
        info.setScriptId(a.getSegmentScriptId());
        info.setCoursewareType("HTML");
        info.setOwnerId(a.getHtmlUnitId());
        info.setSegmentIndex((long) a.getSegmentIndex());
        return info;
    }
}