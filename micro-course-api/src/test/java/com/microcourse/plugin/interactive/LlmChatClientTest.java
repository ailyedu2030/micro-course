package com.microcourse.plugin.interactive;

import com.microcourse.exception.BusinessException;
import com.microcourse.plugin.interactive.service.LlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * LlmChatClient 单元测试（F-2026-08-07-08：MiniMax 优先 + DeepSeek 兜底）。
 */
class LlmChatClientTest {

    private RestTemplate restTemplate;
    private LlmChatClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new LlmChatClient(restTemplate);
        // 默认全空
        ReflectionTestUtils.setField(client, "minimaxApiKey", "");
        ReflectionTestUtils.setField(client, "deepseekApiKey", "");
        ReflectionTestUtils.setField(client, "minimaxModel", "MiniMax-M3");
        ReflectionTestUtils.setField(client, "minimaxBaseUrl", "https://api.minimaxi.com/v1");
        ReflectionTestUtils.setField(client, "deepseekModel", "deepseek-chat");
        ReflectionTestUtils.setField(client, "deepseekBaseUrl", "https://api.deepseek.com");
    }

    @Test
    @DisplayName("MiniMax key 配置时调用 MiniMax OpenAI 兼容端点并携带 Bearer")
    void usesMiniMaxWhenKeyConfigured() {
        ReflectionTestUtils.setField(client, "minimaxApiKey", "mmx-test-key");
        Map<String, Object> choice = new LinkedHashMap<>();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", "这是生成的讲述稿");
        choice.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("choices", List.of(choice));
        when(restTemplate.postForObject(eq("https://api.minimaxi.com/v1/chat/completions"),
                any(HttpEntity.class), eq(Map.class))).thenReturn(resp);

        String out = client.generate("sys", "user");

        assertEquals("这是生成的讲述稿", out);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("https://api.minimaxi.com/v1/chat/completions"),
                captor.capture(), eq(Map.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals("Bearer mmx-test-key", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("MiniMax-M3", captor.getValue().getBody().get("model"));
    }

    @Test
    @DisplayName("仅 DeepSeek key 时走 DeepSeek 兼容端点（兜底）")
    void fallsBackToDeepSeekWhenOnlyDeepSeekConfigured() {
        ReflectionTestUtils.setField(client, "deepseekApiKey", "ds-test-key");
        Map<String, Object> choice = new LinkedHashMap<>();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", "deepseek 讲述稿");
        choice.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("choices", List.of(choice));
        when(restTemplate.postForObject(eq("https://api.deepseek.com/v1/chat/completions"),
                any(HttpEntity.class), eq(Map.class))).thenReturn(resp);

        String out = client.generate("sys", "user");

        assertEquals("deepseek 讲述稿", out);
        verify(restTemplate).postForObject(eq("https://api.deepseek.com/v1/chat/completions"),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("两者均未配置（含 dev 占位符）时抛出明确引导错误")
    void throwsClearErrorWhenNoRealKey() {
        // dev 占位符不应视为真实凭据
        ReflectionTestUtils.setField(client, "deepseekApiKey", "dev-placeholder-not-used");

        BusinessException ex = assertThrows(BusinessException.class, () -> client.generate("sys", "user"));
        assertTrue(ex.getMessage().contains("MINIMAX_API_KEY"), ex.getMessage());
        assertFalse(client.isConfigured());
    }

    @Test
    @DisplayName("MiniMax M 系列响应剥离 <think> 思考标签")
    void stripsThinkTagsFromMiniMaxContent() {
        ReflectionTestUtils.setField(client, "minimaxApiKey", "mmx-test-key");
        Map<String, Object> choice = new LinkedHashMap<>();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", "<think>先分析再回答</think>这是最终讲述稿");
        choice.put("message", message);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("choices", List.of(choice));
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenReturn(resp);

        String out = client.generate("sys", "user");

        assertEquals("这是最终讲述稿", out);
    }
}
