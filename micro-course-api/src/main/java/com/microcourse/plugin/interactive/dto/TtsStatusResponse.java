package com.microcourse.plugin.interactive.dto;

import java.util.List;

public class TtsStatusResponse {

    private String taskId;
    private String status;
    private Integer estimatedSeconds;
    private List<AudioSegment> segments;
    private String mergedAudioUrl;
    private Long totalDuration;
    private String errorMessage;

    public static TtsStatusResponse queued(String taskId, int estimatedSeconds) {
        TtsStatusResponse r = new TtsStatusResponse();
        r.taskId = taskId;
        r.status = "queued";
        r.estimatedSeconds = estimatedSeconds;
        return r;
    }

    public static TtsStatusResponse completed(String taskId, List<AudioSegment> segments,
                                              String mergedAudioUrl, Long totalDuration) {
        TtsStatusResponse r = new TtsStatusResponse();
        r.taskId = taskId;
        r.status = "completed";
        r.segments = segments;
        r.mergedAudioUrl = mergedAudioUrl;
        r.totalDuration = totalDuration;
        return r;
    }

    public static TtsStatusResponse failed(String taskId, String errorMessage) {
        TtsStatusResponse r = new TtsStatusResponse();
        r.taskId = taskId;
        r.status = "failed";
        r.errorMessage = errorMessage;
        return r;
    }

    public static class AudioSegment {
        private Integer page;
        private String url;
        private Long duration;
        private Long size;

        public AudioSegment() {}

        public AudioSegment(Integer page, String url, Long duration, Long size) {
            this.page = page;
            this.url = url;
            this.duration = duration;
            this.size = size;
        }

        public Integer getPage() { return page; }
        public void setPage(Integer page) { this.page = page; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Long getDuration() { return duration; }
        public void setDuration(Long duration) { this.duration = duration; }
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getEstimatedSeconds() { return estimatedSeconds; }
    public void setEstimatedSeconds(Integer estimatedSeconds) { this.estimatedSeconds = estimatedSeconds; }
    public List<AudioSegment> getSegments() { return segments; }
    public void setSegments(List<AudioSegment> segments) { this.segments = segments; }
    public String getMergedAudioUrl() { return mergedAudioUrl; }
    public void setMergedAudioUrl(String mergedAudioUrl) { this.mergedAudioUrl = mergedAudioUrl; }
    public Long getTotalDuration() { return totalDuration; }
    public void setTotalDuration(Long totalDuration) { this.totalDuration = totalDuration; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
