package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.NotNull;

public class MicroSpecialtyLeadTransferRequest {

    @NotNull(message = "新负责人ID不能为空")
    private Long newLeadTeacherId;

    public MicroSpecialtyLeadTransferRequest() {}

    public Long getNewLeadTeacherId() { return newLeadTeacherId; }
    public void setNewLeadTeacherId(Long newLeadTeacherId) { this.newLeadTeacherId = newLeadTeacherId; }
}
