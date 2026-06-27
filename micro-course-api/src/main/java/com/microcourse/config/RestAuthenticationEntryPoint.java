package com.microcourse.config;

import com.microcourse.dto.R;
import com.microcourse.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证请求处理
 * 返回 401 (而不是 Spring 默认的 403)
 * 让前端能正确识别"需要重新登录"
 *
 * 深度审查：错误码从 11004 修正为 1005（对齐 ErrorCode.TOKEN_INVALID），补 timestamp 对齐 R<T> 契约
 * D2 修复：使用 ObjectMapper + R.fail(ErrorCode) 替代 String.format 拼接 JSON
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        R.fail(ErrorCode.TOKEN_INVALID.getCode(), "未登录或登录已过期，请重新登录")));
    }
}
