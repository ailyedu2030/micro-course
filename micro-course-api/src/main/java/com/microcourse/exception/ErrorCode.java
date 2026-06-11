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
    CLASS_HAS_STUDENTS(4002, "班级下存在学生，无法删除", 409),
    USER_NOT_FOUND(5001, "用户不存在", 404),
    USERNAME_EXISTS(5002, "用户名已存在", 409),
    STUDENT_NO_EXISTS(5003, "学号/工号已存在", 409),
    EMAIL_EXISTS(5004, "邮箱已存在", 409),
    RATE_LIMITED(429, "请求过于频繁", 429),
    COURSE_NOT_FOUND(6001, "课程不存在", 404),
    COURSE_CATEGORY_NOT_FOUND(6002, "课程分类不存在", 404),
    COURSE_TEACHER_NOT_FOUND(6003, "教师不存在", 404),
    COURSE_INVALID_STATUS(6004, "无效的课程状态", 400),
    COURSE_STATUS_TRANSITION_NOT_ALLOWED(6005, "不允许的状态转换", 400),
    COURSE_PUBLISHED_CANNOT_EDIT(6006, "已发布的课程不允许直接编辑", 400),
    CHAPTER_NOT_FOUND(7001, "章节不存在", 404),
    CHAPTER_COURSE_NOT_FOUND(7002, "课程不存在", 404),
    ENROLLMENT_NOT_FOUND(8001, "选课记录不存在", 404),
    ENROLLMENT_ALREADY_EXISTS(8002, "已存在选课记录", 409);

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
