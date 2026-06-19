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

    /** 反向代理后缀标识: 若 request 地址等于该值,则应用后有反向代理,可信任 proxy header **/
    private static final String PROXY_SUFFIX = "/internal";

    /**
     * 获取当前请求的客户端真实 IP
     * 安全策略: 优先可信来源(request.getRemoteAddr),避免伪造 X-Forwarded-For(SEC-008)
     * 若有反向代理(Nginx/Envoy),代理负责过滤/清洗 X-Forwarded-For,应用仅信任来自代理的连接
     */
    public static String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return UNKNOWN;
        }
        HttpServletRequest request = attrs.getRequest();

        // 首先检查是否有可信代理(通过请求路径等标记);无代理时不信任 X-Forwarded-For
        boolean behindProxy = request.getRequestURI() != null && request.getRequestURI().endsWith(PROXY_SUFFIX);

        if (behindProxy) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(forwardedFor)) {
                return forwardedFor.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr() != null ? request.getRemoteAddr() : UNKNOWN;
    }
}
