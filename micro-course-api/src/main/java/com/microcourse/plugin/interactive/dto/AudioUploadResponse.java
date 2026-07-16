package com.microcourse.plugin.interactive.dto;

public class AudioUploadResponse {

    private String audioUrl;
    private Long duration;
    private Long audioSize;
    private String audioFormat;
    private Integer sampleRate;
    private Integer segmentCount;
    private String mergedAudioUrl;

    public static AudioUploadResponse single(String audioUrl, Long duration, Long audioSize,
                                            String audioFormat, Integer sampleRate) {
        AudioUploadResponse r = new AudioUploadResponse();
        r.audioUrl = audioUrl;
        r.mergedAudioUrl = audioUrl;
        r.duration = duration;
        r.audioSize = audioSize;
        r.audioFormat = audioFormat;
        r.sampleRate = sampleRate;
        return r;
    }

    public static AudioUploadResponse merged(String mergedAudioUrl, Long totalDuration,
                                            Long audioSize, String audioFormat,
                                            Integer sampleRate, Integer segmentCount) {
        AudioUploadResponse r = new AudioUploadResponse();
        r.mergedAudioUrl = mergedAudioUrl;
        r.duration = totalDuration;
        r.audioSize = audioSize;
        r.audioFormat = audioFormat;
        r.sampleRate = sampleRate;
        r.segmentCount = segmentCount;
        return r;
    }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public Long getAudioSize() { return audioSize; }
    public void setAudioSize(Long audioSize) { this.audioSize = audioSize; }
    public String getAudioFormat() { return audioFormat; }
    public void setAudioFormat(String audioFormat) { this.audioFormat = audioFormat; }
    public Integer getSampleRate() { return sampleRate; }
    public void setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; }
    public Integer getSegmentCount() { return segmentCount; }
    public void setSegmentCount(Integer segmentCount) { this.segmentCount = segmentCount; }
    public String getMergedAudioUrl() { return mergedAudioUrl; }
    public void setMergedAudioUrl(String mergedAudioUrl) { this.mergedAudioUrl = mergedAudioUrl; }
}
