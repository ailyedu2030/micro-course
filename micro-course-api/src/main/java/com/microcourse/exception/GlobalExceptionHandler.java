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
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), "缺少必需请求参数"));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(TypeMismatchException e) {
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), "请求参数格式错误"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), "请求体格式错误或缺失"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R<Void>> handleNoHandler(NoHandlerFoundException e) {
        log.warn("No handler found: {}", e.getMessage());
        return ResponseEntity.status(404).body(R.fail(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "接口不存在"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.status(404).body(R.fail(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(R.fail(ErrorCode.METHOD_NOT_ALLOWED.getCode(), "请求方法不允许"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415).body(R.fail(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED.getCode(), "不支持的媒体类型"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<R<Void>> handleMultipart(MultipartException e) {
        log.warn("[MultipartException] {}", e.getMessage());
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), "上传请求格式错误,请检查文件大小和格式"));
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<R<Void>> handleNoSuchFile(NoSuchFileException e) {
        return ResponseEntity.status(404).body(R.fail(ErrorCode.FILE_NOT_FOUND.getCode(), "文件不存在"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusiness(BusinessException e) {
        log.warn("BusinessException: {} - {}", e.getCode(), e.getMessage());
        int httpStatus = e.getHttpStatus() > 0 ? e.getHttpStatus() : 400;
        return ResponseEntity.status(httpStatus).body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(R.fail(ErrorCode.NO_PERMISSION.getCode(), "无权访问"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), message));
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
        return ResponseEntity.badRequest().body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMessage());
        return ResponseEntity.status(409).body(R.fail(409, "数据冲突，请检查依赖数据"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGeneric(Exception e, HttpServletRequest request) {
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
}
