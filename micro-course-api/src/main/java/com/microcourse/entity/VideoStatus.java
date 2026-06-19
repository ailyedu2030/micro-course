package com.microcourse.entity;

/**
 * 视频状态枚举
 *
 * 状态机：UPLOADING → TRANSCODING → COMPLETED / FAILED
 */
public enum VideoStatus {

    UPLOADING(0, "上传中"),
    TRANSCODING(1, "转码中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "转码失败");

    private final int code;
    private final String label;

    VideoStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static VideoStatus fromCode(int code) {
        for (VideoStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知视频状态码: " + code);
    }
}
