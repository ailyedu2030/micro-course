package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class VideoBookmarkCreateRequest {

    @NotNull(message = "position 不能为空")
    @PositiveOrZero(message = "position 必须 >= 0")
    private Integer position;

    @Size(max = 100, message = "label 最长 100 字符")
    private String label;

    @Size(max = 500, message = "note 最长 500 字符")
    private String note;

    public VideoBookmarkCreateRequest() {
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
