package com.microcourse.exception;

import com.microcourse.dto.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.nio.file.NoSuchFileException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(400).body(R.fail(400, message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(400).body(R.fail(400, "缺少必需参数: " + e.getParameterName()));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(TypeMismatchException e) {
        return ResponseEntity.status(400).body(R.fail(400, "参数类型不匹配: " + e.getPropertyName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400).body(R.fail(400, "请求体格式错误或缺失"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R<Void>> handleNoHandler(NoHandlerFoundException e) {
        return ResponseEntity.status(404).body(R.fail(404, "接口不存在: " + e.getRequestURL()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(R.fail(405, "请求方法不允许: " + e.getMethod()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<R<Void>> handleMultipart(MultipartException e) {
        // DF-NEW-1 修复:不直接拼接 e.getMessage(),避免泄露内部路径/限制等敏感信息
        log.warn("[MultipartException] {}", e.getMessage());
        return ResponseEntity.status(400).body(R.fail(400, "上传请求格式错误,请检查文件大小和格式"));
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<R<Void>> handleNoSuchFile(NoSuchFileException e) {
        return ResponseEntity.status(404).body(R.fail(404, "文件不存在"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusiness(BusinessException e) {
        log.warn("BusinessException: {} - {}", e.getCode(), e.getMessage());
        int httpStatus = e.getHttpStatus() > 0 ? e.getHttpStatus() : mapToHttpStatus(e.getCode());
        return ResponseEntity.status(httpStatus).body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(R.fail(403, "无权访问"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(400).body(R.fail(400, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<R<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllValidationResults().stream()
                .findFirst()
                .map(r -> {
                    String paramName = r.getMethodParameter().getParameterName();
                    String errorMsg = r.getResolvableErrors().stream()
                            .findFirst()
                            .map(err -> err.getDefaultMessage())
                            .orElse("参数校验失败");
                    return (paramName != null ? paramName + ": " : "") + errorMsg;
                })
                .orElse("参数校验失败");
        return ResponseEntity.status(400).body(R.fail(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGeneric(Exception e, HttpServletRequest request) {
        // 检测唯一键冲突，转换为 409
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.springframework.dao.DuplicateKeyException
                || (cause.getMessage() != null && cause.getMessage().contains("duplicate key value"))) {
                log.warn("Duplicate key constraint violation: {}", cause.getMessage());
                return ResponseEntity.status(409).body(R.fail(409, "数据已存在,操作冲突"));
            }
            cause = cause.getCause();
        }
        log.error("Unhandled exception", e);
        return ResponseEntity.status(500).body(R.fail(500, "服务器内部错误"));
    }

    private int mapToHttpStatus(int code) {
        if (code >= 10000 && code < 11000) return 400;
        if (code >= 11000 && code < 12000) return 401;
        if (code >= 12000 && code < 13000) return 403;
        if (code >= 13000 && code < 14000) return 404;
        if (code >= 14000 && code < 15000) return 409;
        return 500;
    }
}
