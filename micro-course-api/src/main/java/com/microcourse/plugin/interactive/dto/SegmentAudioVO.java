package com.microcourse.plugin.interactive.dto;

public class SegmentAudioVO {

    private Integer pageNumber;
    private String url;
    private Integer duration;

    public SegmentAudioVO() {}

    public SegmentAudioVO(Integer pageNumber, String url, Integer duration) {
        this.pageNumber = pageNumber;
        this.url = url;
        this.duration = duration;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }
}
