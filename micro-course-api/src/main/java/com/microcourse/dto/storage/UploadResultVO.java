package com.microcourse.dto.storage;

/**
 * 图片上传结果 VO
 */
public class UploadResultVO {

    private String url;
    private String fileName;
    private Long fileSize;

    public UploadResultVO() {}

    public UploadResultVO(String url, String fileName, Long fileSize) {
        this.url = url;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
}
