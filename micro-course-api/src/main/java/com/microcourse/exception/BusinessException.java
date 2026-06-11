package com.microcourse.exception;

public class BusinessException extends RuntimeException {
    private final int code;
    private final int httpStatus;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
