package com.microcourse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.microcourse.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.message = "ok";
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> R<T> ok() { return ok(null); }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public long getTimestamp() { return timestamp; }
    public void setCode(int code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setData(T data) { this.data = data; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
