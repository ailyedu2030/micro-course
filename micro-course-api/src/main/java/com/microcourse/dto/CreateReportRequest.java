package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建举报请求
 */
public class CreateReportRequest {

    @NotBlank(message = "举报类型不能为空")
    private String reportedItemType;

    @NotNull(message = "被举报内容ID不能为空")
    private Long reportedItemId;

    @NotBlank(message = "举报原因不能为空")
    private String reason;

    public CreateReportRequest() {}

    public String getReportedItemType() { return reportedItemType; }
    public void setReportedItemType(String reportedItemType) { this.reportedItemType = reportedItemType; }
    public Long getReportedItemId() { return reportedItemId; }
    public void setReportedItemId(Long reportedItemId) { this.reportedItemId = reportedItemId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
