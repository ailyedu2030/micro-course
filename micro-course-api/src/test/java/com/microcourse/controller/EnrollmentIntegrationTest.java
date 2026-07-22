package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EnrollmentIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("获取我的选课列表200")
    void getMyEnrollments_Success() throws Exception {
        mockMvc.perform(get("/api/enrollments/my")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("获取所有视频列表200")
    void getFavorites_List() throws Exception {
        mockMvc.perform(get("/api/favorites")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("获取错题集200")
    void getWrongQuestions() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
    }
}
