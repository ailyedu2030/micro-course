package com.microcourse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class VideoIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("获取视频签名返回JWT")
    void getVideoSign_ValidId_ReturnsSign() throws Exception {
        mockMvc.perform(get("/api/videos/1/sign")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    @DisplayName("无效签名播放返回403")
    void getVideoPlay_InvalidSign_Returns403() throws Exception {
        mockMvc.perform(get("/api/videos/1/play?sign=invalid")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无签名播放返回403")
    void getVideoPlay_WithoutSign_Returns403() throws Exception {
        mockMvc.perform(get("/api/videos/1/play")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("获取不存在的视频返回404")
    void getVideoById_InvalidId_Returns404() throws Exception {
        mockMvc.perform(get("/api/videos/99999")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("创建视频成功")
    void createVideo_AsTeacher_Success() throws Exception {
        mockMvc.perform(post("/api/videos")
                .header("Authorization", bearerAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":3,\"title\":\"测试视频\",\"duration\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());
    }
}
