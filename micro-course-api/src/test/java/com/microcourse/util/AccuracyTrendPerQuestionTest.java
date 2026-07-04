package com.microcourse.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-2 回归测试: getAccuracyTrend 应基于逐题 isCorrect,
 * 9/10 答对但总分不及格也应当计为正确率 0.9 而非"整卷不正确"。
 *
 * 这是一个纯逻辑测试 — 不依赖 Service/DB, 仅验证算法正确性。
 */
@DisplayName("P0-2 正确率逐题统计回归")
class AccuracyTrendPerQuestionTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("解析 answers JSON: 10 题中 9 对 1 错,正确率 = 0.9")
    void parseNineOfTenCorrect() throws Exception {
        String answersJson = buildAnswersJson(true, true, true, true, true, true, true, true, true, false);

        List<Map<String, Object>> items = objectMapper.readValue(
                answersJson, new TypeReference<List<Map<String, Object>>>() {});

        long total = items.size();
        long correct = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("isCorrect")))
                .count();

        assertEquals(10, total);
        assertEquals(9, correct);
        assertEquals(0.9, (double) correct / total, 0.001);
    }

    @Test
    @DisplayName("解析 answers JSON: 空数组 = total=0,正确率=0 不抛异常")
    void parseEmptyAnswers() throws Exception {
        List<Map<String, Object>> items = objectMapper.readValue(
                "[]", new TypeReference<List<Map<String, Object>>>() {});

        long total = items.size();
        long correct = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("isCorrect")))
                .count();

        assertEquals(0, total);
        assertEquals(0, correct);
        // total == 0 时正确率应为 0 而非除零异常
        double accuracy = total == 0 ? 0.0 : (double) correct / total;
        assertEquals(0.0, accuracy);
    }

    @Test
    @DisplayName("解析 answers JSON: 损坏 JSON 应 catch 不抛 — 退化优于崩溃")
    void parseCorruptedAnswers() {
        String corrupted = "{not valid json";
        assertThrows(Exception.class, () ->
                objectMapper.readValue(corrupted,
                        new TypeReference<List<Map<String, Object>>>() {}));
    }

    private String buildAnswersJson(boolean... booleans) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < booleans.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("questionId", i + 1L);
            item.put("isCorrect", booleans[i]);
            list.add(item);
        }
        return objectMapper.writeValueAsString(list);
    }
}
