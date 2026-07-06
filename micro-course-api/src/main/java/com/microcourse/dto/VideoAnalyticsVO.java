package com.microcourse.dto;

import java.math.BigDecimal;

/**
 * 视频播放分析 VO
 *
 * <p>权限矩阵 v4.0 §3.5 GET_VIDEO_ANALYTICS — 返回视频的播放统计</p>
 */
public class VideoAnalyticsVO {

    private Long videoId;
    private String videoTitle;
    private Long playCount;           // 总播放次数 (按学习记录 + 1/学习人数计算)
    private Long uniqueViewers;       // 唯一观看人数 (按 userId distinct)
    private Integer avgWatchSeconds;  // 平均观看时长 (秒)
    private Integer totalDuration;    // 视频总时长 (秒)
    private BigDecimal completionRate; // 完成率 (0-1, 看完视频人数 / 观看人数)
    private Long dropOffAt25Sec;      // 25% 处流失人数
    private Long dropOffAt50Sec;      // 50% 处流失人数
    private Long dropOffAt75Sec;      // 75% 处流失人数

    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public String getVideoTitle() { return videoTitle; }
    public void setVideoTitle(String videoTitle) { this.videoTitle = videoTitle; }
    public Long getPlayCount() { return playCount; }
    public void setPlayCount(Long playCount) { this.playCount = playCount; }
    public Long getUniqueViewers() { return uniqueViewers; }
    public void setUniqueViewers(Long uniqueViewers) { this.uniqueViewers = uniqueViewers; }
    public Integer getAvgWatchSeconds() { return avgWatchSeconds; }
    public void setAvgWatchSeconds(Integer avgWatchSeconds) { this.avgWatchSeconds = avgWatchSeconds; }
    public Integer getTotalDuration() { return totalDuration; }
    public void setTotalDuration(Integer totalDuration) { this.totalDuration = totalDuration; }
    public BigDecimal getCompletionRate() { return completionRate; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }
    public Long getDropOffAt25Sec() { return dropOffAt25Sec; }
    public void setDropOffAt25Sec(Long dropOffAt25Sec) { this.dropOffAt25Sec = dropOffAt25Sec; }
    public Long getDropOffAt50Sec() { return dropOffAt50Sec; }
    public void setDropOffAt50Sec(Long dropOffAt50Sec) { this.dropOffAt50Sec = dropOffAt50Sec; }
    public Long getDropOffAt75Sec() { return dropOffAt75Sec; }
    public void setDropOffAt75Sec(Long dropOffAt75Sec) { this.dropOffAt75Sec = dropOffAt75Sec; }
}