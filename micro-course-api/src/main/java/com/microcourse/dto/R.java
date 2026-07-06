package com.microcourse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.microcourse.exception.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {
    /**
     * 【V8 修复】删除 timestamp 字段, 与 API 契约 §3.3.1 一致
     * (契约定义响应格式: {code, message, data})
     */
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() { return ok(null); }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public void setCode(int code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setData(T data) { this.data = data; }
}
