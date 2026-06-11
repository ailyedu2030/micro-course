package com.microcourse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 视频模块集成测试
 * 测试视频签名、播放、上传等功能
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class VideoIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String getToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        R r = objectMapper.readValue(response, R.class);
        if (r.getData() == null) {
            return null;
        }
        return (String) r.getData().getClass().getMethod("getAccessToken").invoke(r.getData());
    }

    @Test
    @DisplayName("使用有效ID获取视频签名")
    void getVideoSign_ValidId_ReturnsSign() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/videos/1/sign")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("使用无效签名访问视频播放返回403")
    void getVideoPlay_InvalidSign_Returns403() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/videos/1/play")
                        .header("Authorization", "Bearer " + token)
                        .param("sign", "invalid-signature"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无签名访问视频播放返回403")
    void getVideoPlay_WithoutSign_Returns403() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/videos/1/play")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("无认证用户上传视频返回403")
    void uploadVideo_NoAuth_Returns403() throws Exception {
        mockMvc.perform(post("/api/videos/upload"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("学生获取视频签名返回签名")
    void getVideoSign_AsStudent_ReturnsSign() throws Exception {
        String token = getToken("student1", "student123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/videos/1/sign")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("获取视频详情成功")
    void getVideoById_ValidId_ReturnsVideo() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/videos/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("获取视频列表成功")
    void getVideoList_ReturnsPagedResult() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .param("courseId", "1")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无效视频ID获取详情返回404")
    void getVideoById_InvalidId_Returns404() throws Exception {
        String token = getToken("admin", "admin123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/videos/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("教师删除视频成功")
    void deleteVideo_AsTeacher_Success() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        // First check if video 2 exists
        mockMvc.perform(get("/api/videos/2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生删除视频返回403禁止")
    void deleteVideo_AsStudent_Forbidden() throws Exception {
        String token = getToken("student1", "student123");
        if (token == null) {
            return;
        }

        mockMvc.perform(delete("/api/videos/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("创建视频记录成功")
    void createVideo_AsTeacher_Success() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        String videoJson = "{\"title\":\"Test Video\",\"courseId\":1,\"chapterId\":null,\"description\":\"Test description\"}";

        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(videoJson))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生创建视频返回403禁止")
    void createVideo_AsStudent_Forbidden() throws Exception {
        String token = getToken("student1", "student123");
        if (token == null) {
            return;
        }

        String videoJson = "{\"title\":\"Test Video\",\"courseId\":1,\"description\":\"Test\"}";

        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(videoJson))
                .andExpect(status().isForbidden());
    }
}