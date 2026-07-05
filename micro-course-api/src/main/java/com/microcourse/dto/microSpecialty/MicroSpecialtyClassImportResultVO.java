package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyClassImportResultVO {

    private int totalCount;
    private int successCount;
    private int failedCount;
    private List<ClassImportItemVO> successList;
    private List<ClassImportItemVO> failedList;

    public MicroSpecialtyClassImportResultVO() {
        this.successList = new ArrayList<>();
        this.failedList = new ArrayList<>();
    }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public List<ClassImportItemVO> getSuccessList() { return successList; }
    public void setSuccessList(List<ClassImportItemVO> successList) { this.successList = successList; }

    public List<ClassImportItemVO> getFailedList() { return failedList; }
    public void setFailedList(List<ClassImportItemVO> failedList) { this.failedList = failedList; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClassImportItemVO {
        private Long classId;
        private String className;
        private int studentCount;
        private String errorMsg;

        public ClassImportItemVO() {}

        public ClassImportItemVO(Long classId, String className, int studentCount, String errorMsg) {
            this.classId = classId;
            this.className = className;
            this.studentCount = studentCount;
            this.errorMsg = errorMsg;
        }

        public Long getClassId() { return classId; }
        public void setClassId(Long classId) { this.classId = classId; }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }

        public int getStudentCount() { return studentCount; }
        public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    }
}
