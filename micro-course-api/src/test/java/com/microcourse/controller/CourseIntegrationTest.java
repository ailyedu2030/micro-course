package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CourseIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("获取课程列表成功")
    void getCourseList_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/courses").header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("获取课程不产生500")
    void getCourseById_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/courses/3")
                .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "Course GET returned " + code;
    }

    @Test
    @DisplayName("无效课程ID不产生500")
    void getCourseById_InvalidId_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/courses/99999")
                .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "Invalid course GET returned " + code;
    }
}
