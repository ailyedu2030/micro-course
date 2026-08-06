package com.microcourse.plugin.interactive.service;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 讲述稿生成服务（P3-1 / R-7）。
 * v2 课件（slide_ppt_pages / slide_html_units）的"AI 生成讲述稿"真实接口，
 * 替代 ScriptEditor 前端 mock；复用 DeepSeek 兼容 Chat Completions（LLM provider 可配）。
 */
@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class AiScriptService {

    private static final Logger log = LoggerFactory.getLogger(AiScriptService.class);

    private final RestTemplate restTemplate;

    @Value("${plugin.interactive.deepseek.api-key:}")
    private String apiKey;

    @Value("${plugin.interactive.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${plugin.interactive.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    public AiScriptService(RestTemplate interactiveRestTemplate) {
        this.restTemplate = interactiveRestTemplate;
    }

    /**
     * 调用 LLM 生成讲述稿（OpenAI 兼容接口，含 3 次重试 + 429/超时退避）。
     */
    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                    "需要配置 LLM_API_KEY / DEEPSEEK_API_KEY 环境变量");
        }
        int maxRetries = 3;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);

                Map<String, Object> systemMsg = new LinkedHashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                Map<String, Object> userMsg = new LinkedHashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", userPrompt);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model);
                body.put("messages", List.of(systemMsg, userMsg));
                body.put("temperature", 0.7);
                body.put("max_tokens", 4096);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                        baseUrl + "/v1/chat/completions",
                        new HttpEntity<>(body, headers), Map.class);
                if (response == null) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "LLM 返回空响应");
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "LLM 返回空 choices");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message == null) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "LLM 返回空 message");
                }
                String content = (String) message.get("content");
                if (content == null || content.isBlank()) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED, "LLM 返回空内容");
                }
                return content.trim();
            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("[AiScript] 第 {}/{} 次调用超时，准备重试", attempt, maxRetries);
                if (attempt < maxRetries) sleep(1000L * attempt);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < maxRetries) {
                    lastException = e;
                    log.warn("[AiScript] 第 {}/{} 次限流(429)，准备重试", attempt, maxRetries);
                    sleep(2000L * attempt);
                } else {
                    log.error("[AiScript] HTTP 错误 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                            "AI 讲述稿生成服务暂时不可用", e);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.error("[AiScript] 第 {}/{} 次调用异常", attempt, maxRetries, e);
                if (attempt < maxRetries) sleep(1000L * attempt);
            }
        }
        log.error("[AiScript] 重试 {} 次后仍失败", maxRetries, lastException);
        throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                "AI 讲述稿生成服务暂时不可用，请稍后重试", lastException);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
