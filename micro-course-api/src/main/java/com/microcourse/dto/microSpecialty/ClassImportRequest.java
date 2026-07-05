package com.microcourse.dto.microSpecialty;

import java.util.List;

public class ClassImportRequest {

    private Long microSpecialtyId;
    private List<Long> classIds;
    private Long classId;

    public ClassImportRequest() {}

    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }

    public List<Long> getClassIds() { return classIds; }
    public void setClassIds(List<Long> classIds) { this.classIds = classIds; }

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}
