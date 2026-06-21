package com.microcourse.security;

import com.microcourse.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 封面文件访问限速拦截器（P1-11：封面 URL 可枚举防护 · 限速维度）。
 *
 * <p>{@code /api/files/covers/**} 为 {@code permitAll}（前端 {@code <img :src>} 需无 Auth
 * 直接加载），攻击者可遍历 {@code /api/files/covers/{videoId}/...} 批量抓取所有课程封面。
 * 本拦截器以来源 IP 为粒度进行<b>固定时间窗</b>限速：每分钟最多
 * {@value #MAX_REQUESTS_PER_MINUTE} 次，超出即返回 HTTP 429，遏制自动化枚举。</p>
 *
 * <p><b>仅作用于封面：</b>虽注册于 {@code /api/files/**}，但仅对前缀
 * {@code /api/files/covers/} 生效；avatars / banners / system / 私有文件等其余路径
 * 在 {@code preHandle} 首行即透传，零影响。</p>
 *
 * <p><b>UX 零退化（合法访问不被误伤）：</b></p>
 * <ul>
 *   <li>阈值 60 次/分钟/IP —— 一屏课程广场通常 ≤ 30 张封面，叠加翻页、刷新仍有充裕余量；
 *       且封面经浏览器缓存，二次浏览不再请求源，正常用户极难触达阈值。</li>
 *   <li><b>Fail-open：</b>Redis 不可用时直接放行，宁可暂时失去限速，也绝不阻断合法用户。</li>
 *   <li>计数依赖 {@link RedisUtil#incrementWithExpire} 的原子 INCR+EXPIRE（仅首次置 TTL），
 *       不会因持续访问而无限延长封禁窗口。</li>
 * </ul>
 *
 * <p>采用纯 Spring MVC {@code HandlerInterceptor}（Spring 6 已移除
 * {@code HandlerInterceptorAdapter}），不引入新依赖。静态资源由
 * {@code ResourceHttpRequestHandler} 处理，拦截器同样生效。</p>
 */
public class FileAccessRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FileAccessRateLimitInterceptor.class);

    /** 每个 IP 每分钟允许的封面请求数上限。 */
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    /** 限速时间窗（秒）。 */
    private static final long WINDOW_SECONDS = 60L;
    /** 仅对此前缀限速。 */
    private static final String COVER_PATH_PREFIX = "/api/files/covers/";
    /** Redis 计数 key 前缀。 */
    private static final String RATE_KEY_PREFIX = "mc:rate:file:cover:";

    private final RedisUtil redisUtil;
    private final FileAccessLogger fileAccessLogger;

    public FileAccessRateLimitInterceptor(RedisUtil redisUtil, FileAccessLogger fileAccessLogger) {
        this.redisUtil = redisUtil;
        this.fileAccessLogger = fileAccessLogger;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        // 仅对封面枚举面进行限速；其余 /api/files/** 直接透传
        if (path == null || !path.startsWith(COVER_PATH_PREFIX)) {
            return true;
        }

        String clientIp = resolveClientIp(request);
        String key = RATE_KEY_PREFIX + clientIp;

        Long count;
        try {
            count = redisUtil.incrementWithExpire(key, WINDOW_SECONDS);
        } catch (Exception e) {
            // Fail-open：Redis 故障时不拦截合法用户（UX 零退化高于限速）
            log.warn("[FileRateLimit] Redis 不可用，本次放行 path={} err={}", path, e.getMessage());
            return true;
        }

        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            fileAccessLogger.logRateLimitExceeded(clientIp, path, count);
            response.setStatus(429); // Too Many Requests
            return false;
        }

        fileAccessLogger.logCoverAccess(clientIp, path, count);
        return true;
    }

    /**
     * 还原真实客户端 IP：优先 {@code X-Forwarded-For}（取首段），再 {@code X-Real-IP}，
     * 最后回退 {@code getRemoteAddr()}。nginx 反向代理已注入这两个头。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
