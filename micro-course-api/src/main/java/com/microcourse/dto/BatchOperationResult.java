package com.microcourse.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchOperationResult {

    private int successCount;
    private int failCount;
    private List<BatchItemResult> failures;
    private List<Long> successIds;

    public BatchOperationResult() {
        this.successCount = 0;
        this.failCount = 0;
        this.failures = new ArrayList<>();
        this.successIds = new ArrayList<>();
    }

    public void addSuccess(Long id) {
        this.successCount++;
        this.successIds.add(id);
    }

    public void addFailure(Long id, String reason) {
        this.failCount++;
        this.failures.add(new BatchItemResult(id, reason));
    }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    public List<BatchItemResult> getFailures() { return failures; }
    public void setFailures(List<BatchItemResult> failures) { this.failures = failures; }
    public List<Long> getSuccessIds() { return successIds; }
    public void setSuccessIds(List<Long> successIds) { this.successIds = successIds; }

    public static class BatchItemResult {
        private Long id;
        private String reason;

        public BatchItemResult() {}

        public BatchItemResult(Long id, String reason) {
            this.id = id;
            this.reason = reason;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
