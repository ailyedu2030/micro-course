package com.microcourse.exception;

public enum ErrorCode {
    INVALID_CREDENTIALS(1001, "用户名或密码错误", 401),
    ACCOUNT_DISABLED(1002, "账号已被禁用", 401),
    ACCOUNT_DELETED(1003, "账号已被删除", 401),
    TOKEN_EXPIRED(1004, "Token 已过期", 401),
    TOKEN_INVALID(1005, "Token 格式错误", 401),
    LOGIN_LOCKED(1006, "登录失败次数过多，账号已锁定", 423),
    DEPARTMENT_NOT_FOUND(2001, "院系不存在", 404),
    DEPARTMENT_HAS_MAJORS(2002, "院系下存在专业，无法删除", 409),
    MAJOR_NOT_FOUND(3001, "专业不存在", 404),
    MAJOR_HAS_CLASSES(3002, "专业下存在班级，无法删除", 409),
    CLASS_NOT_FOUND(4001, "班级不存在", 404),
    USER_NOT_FOUND(5001, "用户不存在", 404),
    USERNAME_EXISTS(5002, "用户名已存在", 409),
    STUDENT_NO_EXISTS(5003, "学号/工号已存在", 409),
    EMAIL_EXISTS(5004, "邮箱已存在", 409),
    RATE_LIMITED(429, "请求过于频繁", 429);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public int getHttpStatus() { return httpStatus; }
}
