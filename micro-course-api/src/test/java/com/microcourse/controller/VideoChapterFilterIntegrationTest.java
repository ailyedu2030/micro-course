package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("视频章节筛选集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VideoChapterFilterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdVideoIds = new ArrayList<>();
    private final List<Long> createdChapterIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long videoId : createdVideoIds) {
            try {
                jdbc.update("DELETE FROM videos WHERE id = ?", videoId);
            } catch (Exception ignored) {
            }
        }
        for (Long chapterId : createdChapterIds) {
            try {
                jdbc.update("DELETE FROM course_chapters WHERE id = ?", chapterId);
            } catch (Exception ignored) {
            }
        }
        createdVideoIds.clear();
        createdChapterIds.clear();
    }

    private long insertChapter(long courseId, int sortOrder) {
        Long id = jdbc.queryForObject(
                "INSERT INTO course_chapters (course_id, title, sort_order, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 0, now(), now()) RETURNING id",
                Long.class,
                courseId,
                "章节筛选测试章-" + System.nanoTime(),
                sortOrder
        );
        createdChapterIds.add(id);
        return id;
    }

    private long insertVideo(long courseId, long chapterId, String title, int sortOrder) {
        Long id = jdbc.queryForObject(
                "INSERT INTO videos(course_id, chapter_id, title, status, m3u8_url, progress, sort_order, version, created_at, updated_at, original_name) " +
                        "VALUES (?, ?, ?, 2, '/api/videos/stream/test/index.m3u8', 100, ?, 0, now(), now(), '') RETURNING id",
                Long.class,
                courseId,
                chapterId,
                title,
                sortOrder
        );
        createdVideoIds.add(id);
        return id;
    }

    @Test
    @DisplayName("GET /api/videos 支持按章节筛选")
    void pageSupportsChapterFilter() throws Exception {
        long courseId = 1L;
        long chapterA = insertChapter(courseId, 10);
        long chapterB = insertChapter(courseId, 20);
        insertVideo(courseId, chapterA, "第一章视频", 1);
        insertVideo(courseId, chapterB, "第二章视频", 2);

        mockMvc.perform(get("/api/videos")
                        .header("Authorization", bearerAdmin())
                        .param("courseId", String.valueOf(courseId))
                        .param("chapterId", String.valueOf(chapterA))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].chapterId").value(chapterA))
                .andExpect(jsonPath("$.data.items[0].title").value("第一章视频"));
    }
}
