package com.microcourse.plugin.interactive.dto;

import java.util.List;

public class BatchTtsResponse {

    private String batchTaskId;
    private Integer sectionCount;
    private Integer estimatedSeconds;
    private List<BatchTtsSectionTask> sections;

    public static BatchTtsResponse of(String batchTaskId, int sectionCount, int estimatedSeconds,
                                      List<BatchTtsSectionTask> sections) {
        BatchTtsResponse r = new BatchTtsResponse();
        r.batchTaskId = batchTaskId;
        r.sectionCount = sectionCount;
        r.estimatedSeconds = estimatedSeconds;
        r.sections = sections;
        return r;
    }

    public static class BatchTtsSectionTask {
        private Long sectionId;
        private String taskId;
        private String status;

        public BatchTtsSectionTask(Long sectionId, String taskId, String status) {
            this.sectionId = sectionId;
            this.taskId = taskId;
            this.status = status;
        }

        public Long getSectionId() { return sectionId; }
        public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public String getBatchTaskId() { return batchTaskId; }
    public void setBatchTaskId(String batchTaskId) { this.batchTaskId = batchTaskId; }
    public Integer getSectionCount() { return sectionCount; }
    public void setSectionCount(Integer sectionCount) { this.sectionCount = sectionCount; }
    public Integer getEstimatedSeconds() { return estimatedSeconds; }
    public void setEstimatedSeconds(Integer estimatedSeconds) { this.estimatedSeconds = estimatedSeconds; }
    public List<BatchTtsSectionTask> getSections() { return sections; }
    public void setSections(List<BatchTtsSectionTask> sections) { this.sections = sections; }
}
