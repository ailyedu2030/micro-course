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

    /**
     * READY 是 COMPLETED 的别名，与状态机文档 {@code READY(2)} 对齐。
     * 代码中原使用 COMPLETED，两者 code=2 等价，可互换使用。
     */
    public static final VideoStatus READY = COMPLETED;

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

    /**
     * ★ 业务逻辑审计 P2-3 修复：视频转码状态机集中白名单。
     * <p>对齐 docs/状态机设计.md §7.2 状态转换图：</p>
     * <ul>
     *   <li>UPLOADING → TRANSCODING / FAILED</li>
     *   <li>TRANSCODING → COMPLETED / FAILED（COMPLETED=READY 就绪）</li>
     *   <li>FAILED → TRANSCODING（重试）</li>
     *   <li>COMPLETED = 终态</li>
     * </ul>
     */
    public boolean canTransitionTo(VideoStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case UPLOADING:
                return target == TRANSCODING || target == FAILED;
            case TRANSCODING:
                return target == COMPLETED || target == FAILED;
            case FAILED:
                return target == TRANSCODING;
            case COMPLETED:  // 终态
            default:
                return false;
        }
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
