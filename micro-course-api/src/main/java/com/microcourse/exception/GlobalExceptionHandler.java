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
import org.springframework.web.servlet.NoHandlerFoundException;

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

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<R<Void>> handleNoSuchFile(NoSuchFileException e) {
        return ResponseEntity.status(404).body(R.fail(404, "文件不存在"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusiness(BusinessException e) {
        log.warn("BusinessException: {} - {}", e.getCode(), e.getMessage());
        int httpStatus = mapToHttpStatus(e.getCode());
        return ResponseEntity.status(httpStatus).body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleGeneric(Exception e, HttpServletRequest request) {
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
