package com.microcourse.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * G3-P0-5: 播放器翻页/音频结束上报"本课时播放进度"请求体。
 * <p>
 * 服务端按 {@code video_progress = playedSeconds / totalSeconds}（0-100，max 1.0）
 * 计算后写入 learning_progress，供 evaluateFlow 的 SKIP_IF_KNOWN 服务端读取
 * （纯 PPT/HTML 学习场景此前 video_progress 恒 null → SKIP 规则永不命中）。
 * </p>
 */
public class SectionVideoProgressReportRequest {

    @PositiveOrZero(message = "已播时长不能为负数")
    private Integer playedSeconds;

    @PositiveOrZero(message = "总时长不能为负数")
    private Integer totalSeconds;

    public SectionVideoProgressReportRequest() {}

    public Integer getPlayedSeconds() { return playedSeconds; }
    public void setPlayedSeconds(Integer playedSeconds) { this.playedSeconds = playedSeconds; }
    public Integer getTotalSeconds() { return totalSeconds; }
    public void setTotalSeconds(Integer totalSeconds) { this.totalSeconds = totalSeconds; }
}
