package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.TtsStatusResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TtsService {

    /** MiniMax 合成结果（字节 + 预估时长秒） */
    class SynthesizedAudio {
        private byte[] bytes;
        private int estimatedSec;
        public SynthesizedAudio() {}
        public SynthesizedAudio(byte[] bytes, int estimatedSec) {
            this.bytes = bytes;
            this.estimatedSec = estimatedSec;
        }
        public byte[] getBytes() { return bytes; }
        public void setBytes(byte[] bytes) { this.bytes = bytes; }
        public int getEstimatedSec() { return estimatedSec; }
        public void setEstimatedSec(int estimatedSec) { this.estimatedSec = estimatedSec; }
    }

    /**
     * 调用 MiniMax T2A 合成音频（P0-3 / R-11 供 TtsWorker 消费）。
     * @param script  讲述稿文本
     * @param voiceId MiniMax 官方 voice_id（历史非法枚举由调用方别名映射）
     * @param model   MiniMax 模型名（speech-2.8-hd 等）
     * @param speed   倍速（null=1.0）
     */
    SynthesizedAudio synthesize(String script, String voiceId, String model, Double speed);

    SlidePageVO generate(Long courseId, Integer pageNumber);

    void generateAll(Long courseId);

    CompletableFuture<TtsStatusResponse> generateSection(Long courseId, Long sectionId,
                                                        String voice, String model, Double speed,
                                                        boolean splitByPage);

    TtsStatusResponse getSectionTtsStatus(Long courseId, Long sectionId, String taskId);

    List<TtsStatusResponse> generateSectionsBatch(Long courseId, List<Long> sectionIds,
                                                  String voice, String model, Double speed,
                                                  boolean splitByPage);

    byte[] getAudio(Long courseId, Integer pageNumber, Long sectionId);

    boolean validateAudioToken(Long courseId, Integer pageNumber, Long sectionId, String token);

    void verifyAccess(Long courseId);
}
