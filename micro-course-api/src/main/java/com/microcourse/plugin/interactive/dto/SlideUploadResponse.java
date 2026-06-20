package com.microcourse.plugin.interactive.dto;

public class SlideUploadResponse {
    private Long slideId;
    private Integer totalPages;
    private Integer status;
    private String message;

    public SlideUploadResponse() {}

    public Long getSlideId() { return slideId; }
    public void setSlideId(Long slideId) { this.slideId = slideId; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
