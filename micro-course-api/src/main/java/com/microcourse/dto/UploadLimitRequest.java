package com.microcourse.dto;

public class UploadLimitRequest {

    private Integer maxVideoSizeMb;

    public UploadLimitRequest() {}

    public Integer getMaxVideoSizeMb() { return maxVideoSizeMb; }
    public void setMaxVideoSizeMb(Integer maxVideoSizeMb) { this.maxVideoSizeMb = maxVideoSizeMb; }
}
