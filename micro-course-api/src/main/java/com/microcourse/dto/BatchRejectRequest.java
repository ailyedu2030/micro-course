package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class BatchRejectRequest {

    @NotEmpty(message = "ID 列表不能为空")
    private List<Long> ids;

    @NotBlank(message = "驳回原因不能为空")
    @Size(min = 10, max = 500, message = "驳回原因至少10个字符，不能超过500字")
    private String reason;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
