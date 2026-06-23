package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class MicroSpecialtyClassImportRequest {
    @NotNull(message = "微专业ID不能为空")
    private Long microSpecialtyId;
    
    private List<Long> classIds;
    
    private Long classId;
    
    public MicroSpecialtyClassImportRequest() {}
    
    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }
    public List<Long> getClassIds() { return classIds; }
    public void setClassIds(List<Long> classIds) { this.classIds = classIds; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}
