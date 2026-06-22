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
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.file.NoSuchFileException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.status(400).body(R.fail(400, message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(400).body(R.fail(400, "缺少必需请求参数"));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(TypeMismatchException e) {
        return ResponseEntity.status(400).body(R.fail(400, "请求参数格式错误"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(400).body(R.fail(400, "请求体格式错误或缺失"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R<Void>> handleNoHandler(NoHandlerFoundException e) {
        log.warn("No handler found: {}", e.getMessage());
        return ResponseEntity.status(404).body(R.fail(404, "接口不存在"));
    }

    /**
     * P3-13（Phase D-2 修复）：Spring 静态/资源处理链找不到目标资源时抛 NoResourceFoundException。
     * 此前无专门 handler，被 {@link #handleGeneric} 兜底为 500（语义错误）。
     * 修正为 404 —— "资源不存在" 才是符合 HTTP 语义的响应（例如公开文件 GET 命中不存在的物理路径）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.status(404).body(R.fail(404, "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(R.fail(405, "请求方法不允许"));
    }

    /**
     * Round 11-4 安全加固：请求 Content-Type 不被任何 HttpMessageConverter 支持时
     * （如向 @RequestBody JSON 端点发送 text/plain），Spring 抛 HttpMediaTypeNotSupportedException。
     * 此前无专门 handler，被 {@link #handleGeneric} 兜底为 500（语义错误）。
     * 修正为符合 HTTP 语义的 415 Unsupported Media Type，统一为标准 R&lt;Void&gt; 响应体。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415).body(R.fail(415, "不支持的媒体类型"));
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
                .map(v -> v.getMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(400).body(R.fail(400, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<R<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllValidationResults().stream()
                .findFirst()
                .map(r -> r.getResolvableErrors().stream()
                        .findFirst()
                        .map(err -> err.getDefaultMessage())
                        .orElse("参数校验失败"))
                .orElse("参数校验失败");
        return ResponseEntity.status(400).body(R.fail(400, message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMessage());
        return ResponseEntity.status(409).body(R.fail(409, "数据冲突，请检查依赖数据"));
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
