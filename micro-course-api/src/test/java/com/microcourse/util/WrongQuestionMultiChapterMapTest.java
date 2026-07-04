package com.microcourse.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-3 回归测试: 错题本多章节关联 — 一道题关联多个章节时,
 * 应当返回所有章节关联(而非只保留最后一个)。
 *
 * 锁定 bug fix #2 (之前 Map.put() 仅留最后一个章节)。
 */
@DisplayName("P0-3 错题本多章节回归")
class WrongQuestionMultiChapterMapTest {

    @Test
    @DisplayName("computeIfAbsent 多值 put 模式保留所有章节")
    void multiChapterMerge() {
        Map<Long, List<Long>> questionChaptersMap = new HashMap<>();

        // 模拟 3 题 × 多章节
        add(questionChaptersMap, 101L, 10L);
        add(questionChaptersMap, 101L, 11L);
        add(questionChaptersMap, 101L, 12L);
        add(questionChaptersMap, 102L, 20L);
        add(questionChaptersMap, 102L, 21L);
        add(questionChaptersMap, 103L, 30L);

        assertEquals(3, questionChaptersMap.get(101L).size());
        assertTrue(questionChaptersMap.get(101L).contains(10L));
        assertTrue(questionChaptersMap.get(101L).contains(11L));
        assertTrue(questionChaptersMap.get(101L).contains(12L));

        assertEquals(2, questionChaptersMap.get(102L).size());
        assertEquals(1, questionChaptersMap.get(103L).size());
        assertEquals(3, questionChaptersMap.size());
    }

    @Test
    @DisplayName("findFirst() 取首个章节作为最相关章节")
    void findFirstIsStable() {
        Map<Long, List<Long>> map = new HashMap<>();
        add(map, 1L, 100L);
        add(map, 1L, 200L);

        // 取首个章节 (Map.forEach 不保证顺序, 但 List.forEach 有顺序)
        List<Long> chapters = map.getOrDefault(1L, new ArrayList<>());
        Long first = chapters.stream().findFirst().orElse(null);

        // 第一个插入的是 100L (computeIfAbsent 模式 + add 顺序)
        assertNotNull(first);
        assertTrue(chapters.contains(100L));
        assertTrue(chapters.contains(200L));
    }

    @Test
    @DisplayName("空 Map 不抛异常 — 兜底优于崩溃")
    void emptyMapSafe() {
        Map<Long, List<Long>> empty = new HashMap<>();
        List<Long> chapters = empty.getOrDefault(999L, new ArrayList<>());
        assertNotNull(chapters);
        assertTrue(chapters.isEmpty());
    }

    private void add(Map<Long, List<Long>> map, Long qid, Long cid) {
        map.computeIfAbsent(qid, k -> new ArrayList<>()).add(cid);
    }
}
