package com.microcourse.plugin.interactive.exception;

import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import java.util.stream.Collectors;

/**
 * 【BUG #30 修复】 全局异常处理器 (interactive plugin 范围).
 *
 * <p>
 * 解决: 之前每个 controller 内部 try-catch, 重复代码. 现在集中处理:
 * </p>
 * <ul>
 *   <li>BusinessException → 业务异常 (R.ok code)</li>
 *   <li>AccessDeniedException → 403 Forbidden</li>
 *   <li>MethodArgumentNotValidException → 400 + 字段错误</li>
 *   <li>NoHandlerFoundException → 404</li>
 *   <li>Exception → 500 + 隐藏细节 (P0 安全)</li>
 * </ul>
 *
 * <p>
 * 【BUG #31 部分实现】 MDC traceId 自动注入响应 header.
 * 配合 Sleuth/Zipkin 可实现全链路追踪.
 * </p>
 *
 * <p>
 * 7-19 P0 防御: 避免在 5xx 响应中泄露 SQL/堆栈给前端.
 * </p>
 */
@RestControllerAdvice(basePackages = "com.microcourse.plugin.interactive.controller")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常 (最常见).
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Object>> handleBusinessException(BusinessException ex, HttpServletRequest req) {
        String traceId = MDC.get("traceId");
        log.warn("[Global-Except] BusinessException path={} code={} msg={} traceId={}",
                req.getRequestURI(), ex.getCode(), ex.getMessage(), traceId);
        R<Object> body = R.fail(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 权限拒绝 (Spring Security).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("[Global-Except] AccessDenied path={} msg={}", req.getRequestURI(), ex.getMessage());
        // 【7-19 P0 防御】 用通用 MS_FORBIDDEN 码替代 (ErrorCode 没有 FORBIDDEN)
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(ErrorCode.MS_FORBIDDEN.getCode(), "Access denied"));
    }

    /**
     * @Valid 验证失败 (RequestBody 上的注解).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("[Global-Except] Validation failed: {}", errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), errorMsg));
    }

    /**
     * @Valid 验证失败 (表单绑定).
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Object>> handleBindException(BindException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("[Global-Except] Bind failed: {}", errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), errorMsg));
    }

    /**
     * @Validated 单参数验证.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Object>> handleConstraint(ConstraintViolationException ex) {
        log.warn("[Global-Except] Constraint violated: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getMessage()));
    }

    /**
     * 404 NoHandlerFound.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<R<Object>> handleNotFound(NoHandlerFoundException ex) {
        log.info("[Global-Except] NoHandler path={}", ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "Endpoint not found"));
    }

    /**
     * 兜底 - 任何未捕获异常.
     * 【P0 安全】 不泄露堆栈给前端.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Object>> handleAny(Exception ex, HttpServletRequest req) {
        String traceId = MDC.get("traceId");
        // 完整堆栈进日志, 但响应只返回 traceId (供用户报告)
        log.error("[Global-Except] Uncaught path={} class={} msg={} traceId={}",
                req.getRequestURI(), ex.getClass().getName(), ex.getMessage(), traceId, ex);
        // 【7-19 P0 防御】 用通用 9999 表示内部错误 (ErrorCode 没有 INTERNAL_ERROR)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(9999,
                        "Internal error, please contact support with traceId: " + traceId));
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}