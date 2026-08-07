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
import java.util.regex.Pattern;

/**
 * LLM 讲述稿生成统一客户端（F-2026-08-07-08）。
 * <p>
 * 提供方优先级：MiniMax（MMX，复用 {@code MINIMAX_API_KEY}）→ DeepSeek（兼容兜底）。
 * 用户铁律：讲述稿生成系统与 TTS 统一使用 MMX；生产仅配置 MINIMAX_API_KEY，
 * 旧实现只支持 DeepSeek 导致生产 AI 讲述稿生成 100% 失败。
 * </p>
 * <p>
 * MiniMax OpenAI 兼容端点：{@code https://api.minimaxi.com/v1/chat/completions}，
 * Bearer 鉴权，M 系列模型响应 content 可能携带 {@code <think>...</think>} 标签，
 * 此处统一剥离，避免讲述稿中出现思考过程。
 * </p>
 */
@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class LlmChatClient {

    private static final Logger log = LoggerFactory.getLogger(LlmChatClient.class);
    private static final Pattern THINK_TAG = Pattern.compile("(?s)<think>.*?</think>");

    private final RestTemplate restTemplate;

    @Value("${plugin.interactive.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${plugin.interactive.minimax.chat-model:MiniMax-M3}")
    private String minimaxModel;

    @Value("${plugin.interactive.minimax.chat-base-url:https://api.minimaxi.com/v1}")
    private String minimaxBaseUrl;

    @Value("${plugin.interactive.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${plugin.interactive.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${plugin.interactive.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    public LlmChatClient(RestTemplate interactiveRestTemplate) {
        this.restTemplate = interactiveRestTemplate;
    }

    /**
     * 任一提供方已配置（批量生成前置检查，避免无 key 时逐页报错）。
     */
    public boolean isConfigured() {
        return hasRealKey(minimaxApiKey) || hasRealKey(deepseekApiKey);
    }

    /**
     * 生成讲述稿：MiniMax 优先，DeepSeek 兜底；3 次重试 + 429/超时退避。
     */
    public String generate(String systemPrompt, String userPrompt) {
        Provider provider = resolveProvider();
        int maxRetries = 3;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(provider.apiKey);

                Map<String, Object> systemMsg = new LinkedHashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                Map<String, Object> userMsg = new LinkedHashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", userPrompt);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", provider.model);
                body.put("messages", List.of(systemMsg, userMsg));
                body.put("temperature", 0.7);
                body.put("max_tokens", 4096);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                        provider.chatCompletionsUrl(),
                        new HttpEntity<>(body, headers), Map.class);
                if (response == null) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                            provider.name + " 返回空响应");
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                            provider.name + " 返回空 choices");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message == null) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                            provider.name + " 返回空 message");
                }
                String content = (String) message.get("content");
                if (content == null || content.isBlank()) {
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                            provider.name + " 返回空内容");
                }
                return THINK_TAG.matcher(content).replaceAll("").trim();
            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("[LlmChat] {} 第 {}/{} 次调用超时，准备重试", provider.name, attempt, maxRetries);
                if (attempt < maxRetries) sleep(1000L * attempt);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < maxRetries) {
                    lastException = e;
                    log.warn("[LlmChat] {} 第 {}/{} 次限流(429)，准备重试", provider.name, attempt, maxRetries);
                    sleep(2000L * attempt);
                } else {
                    log.error("[LlmChat] {} HTTP 错误 status={} body={}",
                            provider.name, e.getStatusCode(), e.getResponseBodyAsString());
                    throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                            "AI 讲述稿生成服务暂时不可用", e);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.error("[LlmChat] {} 第 {}/{} 次调用异常", provider.name, attempt, maxRetries, e);
                if (attempt < maxRetries) sleep(1000L * attempt);
            }
        }
        log.error("[LlmChat] 重试 {} 次后仍失败, provider={}", maxRetries, provider.name, lastException);
        throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                "AI 讲述稿生成服务暂时不可用，请稍后重试", lastException);
    }

    /**
     * MiniMax 优先、DeepSeek 兜底；两者都未配置时给出明确引导。
     */
    private Provider resolveProvider() {
        if (hasRealKey(minimaxApiKey)) {
            return new Provider("MiniMax", minimaxApiKey, minimaxModel,
                    minimaxBaseUrl.endsWith("/v1") ? minimaxBaseUrl : minimaxBaseUrl + "/v1");
        }
        if (hasRealKey(deepseekApiKey)) {
            return new Provider("DeepSeek", deepseekApiKey, deepseekModel, deepseekBaseUrl);
        }
        throw new BusinessException(ErrorCode.NARRATION_GENERATE_FAILED,
                "需要配置 MINIMAX_API_KEY 或 DEEPSEEK_API_KEY 环境变量");
    }

    /**
     * 忽略空值与本地开发占位符（application.yml 默认 dev-placeholder-not-used，
     * 该占位符非真实凭据，不应触发调用）。
     */
    private boolean hasRealKey(String key) {
        return key != null && !key.isBlank()
                && !"dev-placeholder-not-used".equals(key);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    private record Provider(String name, String apiKey, String model, String baseUrl) {
        String chatCompletionsUrl() {
            // MiniMax: base 已含 /v1 → /chat/completions；DeepSeek: base 不含 /v1 → /v1/chat/completions
            return baseUrl.endsWith("/v1") ? baseUrl + "/chat/completions" : baseUrl + "/v1/chat/completions";
        }
    }
}
