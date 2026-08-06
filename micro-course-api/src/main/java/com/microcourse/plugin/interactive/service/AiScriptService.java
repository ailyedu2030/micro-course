package com.microcourse.plugin.interactive.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * AI 讲述稿生成服务（P3-1 / R-7）。
 * v2 课件（slide_ppt_pages / slide_html_units）的"AI 生成讲述稿"真实接口，
 * 替代 ScriptEditor 前端 mock；LLM 调用统一走 {@link LlmChatClient}
 * （MiniMax 优先，DeepSeek 兜底，F-2026-08-07-08）。
 */
@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class AiScriptService {

    private static final Logger log = LoggerFactory.getLogger(AiScriptService.class);

    private final LlmChatClient llmChatClient;

    public AiScriptService(LlmChatClient llmChatClient) {
        this.llmChatClient = llmChatClient;
    }

    /**
     * 调用 LLM 生成讲述稿（OpenAI 兼容接口，含 3 次重试 + 429/超时退避）。
     */
    public String generate(String systemPrompt, String userPrompt) {
        return llmChatClient.generate(systemPrompt, userPrompt);
    }
}
