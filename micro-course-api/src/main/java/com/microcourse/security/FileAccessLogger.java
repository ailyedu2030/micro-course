package com.microcourse.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 文件访问审计日志（P1-11：封面 URL 可枚举防护 · 访问日志维度）。
 *
 * <p>为 {@code /api/files/covers/**} 提供轻量级访问审计，与
 * {@link FileAccessRateLimitInterceptor} 限速维度互补，用于事后检测封面 URL
 * 的批量枚举/爬取行为。</p>
 *
 * <p><b>设计取舍（UX 零退化优先）：</b></p>
 * <ul>
 *   <li><b>正常访问</b>（{@link #logCoverAccess}）走 DEBUG 级，默认 INFO 日志级别下
 *       静默；并以 {@code isDebugEnabled()} 守卫，避免在封面图加载这一高频热路径上
 *       产生任何线程派发或日志洪泛，合法用户访问零感知、零开销。</li>
 *   <li><b>枚举告警</b>（{@link #logRateLimitExceeded}）走 WARN 级，且以 {@code @Async}
 *       脱离请求线程执行，确保 429 响应不被日志写入阻塞。</li>
 * </ul>
 *
 * <p><b>无 DB / 无 schema 变更：</b>审计写入应用日志（dedicated logger {@code FILE_ACCESS}），
 * 不写 operation_logs，避免高频静态资源访问对业务审计表造成写放大，符合"不修改 DB schema"约束。
 * 日志量由 logback 级别配置统一管控。</p>
 *
 * <p>注意：封面经 {@code <img :src>} 直接加载，物理上不携带 Authorization 头，
 * 因此用户身份通常不可得，审计以来源 IP 为主键。</p>
 */
@Component
public class FileAccessLogger {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("FILE_ACCESS");

    /**
     * 记录一次正常封面访问（DEBUG，默认静默；热路径零开销）。
     *
     * @param ip    客户端来源 IP（经 X-Forwarded-For / X-Real-IP 还原）
     * @param path  请求路径
     * @param count 当前限速窗口内该 IP 的累计访问次数
     */
    public void logCoverAccess(String ip, String path, Long count) {
        if (ACCESS_LOG.isDebugEnabled()) {
            ACCESS_LOG.debug("[cover-access] ip={} path={} count={}", ip, path, count);
        }
    }

    /**
     * 记录一次触发限速的封面访问（WARN，异步；潜在枚举/爬取告警）。
     *
     * @param ip    客户端来源 IP
     * @param path  请求路径
     * @param count 当前限速窗口内该 IP 的累计访问次数（已超阈值）
     */
    @Async
    public void logRateLimitExceeded(String ip, String path, Long count) {
        ACCESS_LOG.warn("[cover-rate-limit] ip={} path={} count={} -> blocked(429)", ip, path, count);
    }
}
