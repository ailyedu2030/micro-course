package com.microcourse.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP 地址工具类
 * 从当前 HTTP 请求中提取真实客户端 IP
 */
public class IpUtil {

    private static final String UNKNOWN = "0.0.0.0";

    /**
     * 获取当前请求的客户端真实 IP
     * 优先从 X-Forwarded-For 头获取（反向代理场景），
     * 其次 X-Real-IP，最后 fallback 到 request.getRemoteAddr()
     */
    public static String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return UNKNOWN;
        }
        HttpServletRequest request = attrs.getRequest();

        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个 IP，取第一个
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr() != null ? request.getRemoteAddr() : UNKNOWN;
    }
}
