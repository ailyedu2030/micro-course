package com.microcourse.dto;

/**
 * 前端错误上报请求 DTO（替代 Map&lt;String, Object&gt;）。
 *
 * <p>对应 POST /api/frontend-errors 端点</p>
 */
public class FrontendErrorReportRequest {

    private String message;
    private String url;
    private String line;
    private String stack;
    private java.util.Map<String, Object> details;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public String getStack() { return stack; }
    public void setStack(String stack) { this.stack = stack; }
    public java.util.Map<String, Object> getDetails() { return details; }
    public void setDetails(java.util.Map<String, Object> details) { this.details = details; }
}
