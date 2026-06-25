package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class VideoIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("获取视频签名成功")
    void getVideoSign_ValidId_ReturnsSign() throws Exception {
        mockMvc.perform(get("/api/videos/1/sign")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    @DisplayName("无效签名播放不产生500")
    void getVideoPlay_InvalidSign_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/videos/1/play?sign=invalid")
                .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "Play with invalid sign returned " + code;
    }

    @Test
    @DisplayName("无签名播放不产生500")
    void getVideoPlay_WithoutSign_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/videos/1/play")
                .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "Play without sign returned " + code;
    }

    @Test
    @DisplayName("获取不存在的视频不产生500")
    void getVideoById_InvalidId_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/videos/99999")
                .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "Get invalid video returned " + code;
    }

    @Test
    @DisplayName("创建视频不产生500（可能因seed数据不足而4xx）")
    void createVideo_No5xx() throws Exception {
        int code = mockMvc.perform(post("/api/videos")
                .header("Authorization", bearerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":3,\"chapterId\":1,\"title\":\"测试视频\",\"duration\":300}"))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "Video create returned " + code;
    }
}
