package com.microcourse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CourseIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("获取课程列表返回分页结果")
    void getCourseList_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/courses").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").exists());
    }

    @Test
    @DisplayName("有效课程ID返回课程信息")
    void getCourseById_ValidId_ReturnsCourse() throws Exception {
        mockMvc.perform(get("/api/courses/3").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    @DisplayName("无效课程ID返回404")
    void getCourseById_InvalidId_Returns404() throws Exception {
        mockMvc.perform(get("/api/courses/99999").header("Authorization", bearerAdmin()))
                .andExpect(status().isNotFound());
    }
}
