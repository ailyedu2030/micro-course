package com.microcourse.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果 VO
 * P1-1 修复：字段名与前端对齐
 * P2 修复：errors 改为结构化对象列表 [{row, username, reason}]
 */
public class BatchImportResultVO {

    private int successCount;
    private int failCount;
    private List<ImportErrorItem> errors;

    public BatchImportResultVO() {
        this.errors = new ArrayList<>();
        this.successCount = 0;
        this.failCount = 0;
    }

    public BatchImportResultVO(int successCount, int failCount, List<ImportErrorItem> errors) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    public List<ImportErrorItem> getErrors() { return errors; }
    public void setErrors(List<ImportErrorItem> errors) { this.errors = errors; }

    /**
     * 结构化错误条目，便于前端 el-table 直接渲染
     */
    public static class ImportErrorItem {
        private int row;
        private String username;
        private String reason;

        public ImportErrorItem() {}

        public ImportErrorItem(int row, String username, String reason) {
            this.row = row;
            this.username = username;
            this.reason = reason;
        }

        public int getRow() { return row; }
        public void setRow(int row) { this.row = row; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
