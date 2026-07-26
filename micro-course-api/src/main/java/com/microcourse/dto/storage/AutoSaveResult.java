package com.microcourse.dto.storage;

/**
 * 微专业申请表 — 自动保存响应 DTO。
 *
 * <p>对齐 spec phase15 §7.2#5：服务器时间 + 状态。
 * 客户端用于校对本地时钟与服务端时钟的偏移。</p>
 */
public class AutoSaveResult {

    /** 服务器时间戳（毫秒） */
    private Long serverTime;

    /** 状态 ("ok") */
    private String status;

    public AutoSaveResult() {}

    public AutoSaveResult(Long serverTime, String status) {
        this.serverTime = serverTime;
        this.status = status;
    }

    public static AutoSaveResult ok() {
        return new AutoSaveResult(System.currentTimeMillis(), "ok");
    }

    public Long getServerTime() { return serverTime; }
    public void setServerTime(Long serverTime) { this.serverTime = serverTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}