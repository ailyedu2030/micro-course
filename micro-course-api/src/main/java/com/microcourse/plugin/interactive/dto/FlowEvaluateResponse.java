package com.microcourse.plugin.interactive.dto;

/**
 * PPT 页间跳转求值响应（P1-1 / R-5）。
 * nextPageId=null 表示结束或调用方退化为线性 page+1。
 */
public class FlowEvaluateResponse {

    private Long nextPageId;
    private String matchedType;

    public FlowEvaluateResponse() {}

    public FlowEvaluateResponse(Long nextPageId, String matchedType) {
        this.nextPageId = nextPageId;
        this.matchedType = matchedType;
    }

    public Long getNextPageId() { return nextPageId; }
    public void setNextPageId(Long nextPageId) { this.nextPageId = nextPageId; }
    public String getMatchedType() { return matchedType; }
    public void setMatchedType(String matchedType) { this.matchedType = matchedType; }
}
