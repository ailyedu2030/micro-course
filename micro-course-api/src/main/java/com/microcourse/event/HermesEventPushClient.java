package com.microcourse.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * P1 plan Task 10: Hermes HTTP 推送客户端.
 * 把本地 outbox 行的 payload 推到 Hermes (endpoint = event.getEndpoint()).
 *
 * 行为约定 (与 P1 spec §5.5 一致):
 *   - 2xx / 4xx (非 409): 视为成功 (DELIVERED)
 *   - 5xx / 408 / 429 / 网络错误: 视为失败 (走 retry + dead_letter)
 *   - 409 Conflict: Hermes 端 dedup 命中, 视为成功
 *   - 4xx (非 409): 通常是 payload 错, 不再重试 (留 P3 排查)
 *
 * 失败 = 抛 PushException, 由 OutboxPollerWorker 决定 markRetry / markDeadLetter.
 */
@Component
public class HermesEventPushClient {

    private static final Logger LOG = LoggerFactory.getLogger(HermesEventPushClient.class);

    private final HttpClient httpClient;
    private final String hermesBaseUrl;
    private final String apiKey;

    public HermesEventPushClient(@Value("${hermes.base-url:http://localhost:8080}") String hermesBaseUrl,
                                  @Value("${hermes.api-key:placeholder-key}") String apiKey) {
        this.hermesBaseUrl = hermesBaseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 推 event 到 Hermes endpoint.
     * @param event 完整 DomainEvent (已序列化为 JSON payload)
     * @return PushResult
     * @throws PushException 网络 / 5xx 错误 (调用方按 retry policy 处置)
     */
    public PushResult push(DomainEvent event) {
        String url = hermesBaseUrl + event.getEndpoint();
        String payload = event.toJsonPayload();
        long start = System.currentTimeMillis();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .header("X-Event-Id", event.getEventId() != null ? event.getEventId() : "")
                    .header("X-Trace-Id", event.getTraceId() != null ? event.getTraceId() : "")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            int code = resp.statusCode();

            // 成功: 2xx 或 409 (Hermes dedup 命中)
            if ((code >= 200 && code < 300) || code == 409) {
                LOG.info("[Hermes-push] eventId={} url={} status={} elapsed={}ms",
                        event.getEventId(), url, code, elapsed);
                return new PushResult(true, code, resp.body());
            }

            // 4xx (非 409) - payload 错, 不重试
            if (code >= 400 && code < 500) {
                LOG.warn("[Hermes-push] eventId={} url={} status={} body={} (4xx 不重试)",
                        event.getEventId(), url, code, resp.body());
                return new PushResult(false, code, resp.body());
            }

            // 5xx / 408 / 429 - 抛异常让 poller 走 retry
            throw new PushException("5xx", code, resp.body());

        } catch (PushException e) {
            throw e;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            throw new PushException("network/timeout", 0, e.getMessage() + " (elapsed=" + elapsed + "ms)");
        }
    }

    public record PushResult(boolean accepted, int statusCode, String body) {}

    public static class PushException extends RuntimeException {
        private final int statusCode;
        public PushException(String label, int statusCode, String body) {
            super("[" + label + "] statusCode=" + statusCode + " body=" + body);
            this.statusCode = statusCode;
        }
        public int getStatusCode() { return statusCode; }
    }
}
