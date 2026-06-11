package com.microcourse.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果 VO
 */
public class BatchImportResultVO {

    private Integer successCount;
    private Integer failCount;
    private List<String> errors;

    public BatchImportResultVO() {
        this.errors = new ArrayList<>();
        this.successCount = 0;
        this.failCount = 0;
    }

    public BatchImportResultVO(Integer successCount, Integer failCount, List<String> errors) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}