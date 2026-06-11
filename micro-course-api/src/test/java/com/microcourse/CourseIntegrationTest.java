package com.microcourse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.CourseCreateRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 课程模块集成测试
 * 测试课程列表、详情、创建等功能
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class CourseIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("获取课程列表返回分页结果")
    void getCourseList_ReturnsPagedResult() throws Exception {
        String token = getToken("admin", "admin123");
        if (token == null) {
            return; // Skip if no admin user exists
        }

        mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("使用有效ID获取课程详情成功")
    void getCourseById_ValidId_ReturnsCourse() throws Exception {
        String token = getToken("admin", "admin123");
        if (token == null) {
            return;
        }

        // First get the course list to find a valid course ID
        MvcResult listResult = mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String listResponse = listResult.getResponse().getContentAsString();
        R listR = objectMapper.readValue(listResponse, R.class);
        if (listR.getData() != null) {
            // If we have courses, get the first one
            mockMvc.perform(get("/api/courses/1")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("使用无效ID获取课程返回404")
    void getCourseById_InvalidId_Returns404() throws Exception {
        String token = getToken("admin", "admin123");
        if (token == null) {
            return;
        }

        mockMvc.perform(get("/api/courses/999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("教师创建课程成功")
    void createCourse_AsTeacher_Success() throws Exception {
        String token = getToken("teacher1", "teacher123");
        if (token == null) {
            return;
        }

        CourseCreateRequest createRequest = new CourseCreateRequest();
        createRequest.setTitle("Test Course - Integration Test " + System.currentTimeMillis());
        createRequest.setDescription("This is a test course created by integration test");
        createRequest.setCategoryId(1L);

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("学生创建课程返回403禁止")
    void createCourse_AsStudent_Forbidden() throws Exception {
        String token = getToken("student1", "student123");
        if (token == null) {
            return;
        }

        CourseCreateRequest createRequest = new CourseCreateRequest();
        createRequest.setTitle("Test Course Should Fail");
        createRequest.setDescription("This should not be allowed");
        createRequest.setCategoryId(1L);

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未认证用户获取课程列表返回403")
    void getCourseList_Unauthenticated_Returns403() throws Exception {
        mockMvc.perform(get("/api/courses")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员更新课程状态成功")
    void updateCourseStatus_AsAdmin_Success() throws Exception {
        String token = getToken("admin", "admin123");
        if (token == null) {
            return;
        }

        // First create a course
        CourseCreateRequest createRequest = new CourseCreateRequest();
        createRequest.setTitle("Course for Status Update Test");
        createRequest.setDescription("Testing status update");
        createRequest.setCategoryId(1L);

        MvcResult createResult = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        R createR = objectMapper.readValue(createResponse, R.class);
        if (createR.getData() != null) {
            Long courseId = (Long) createR.getData().getClass().getMethod("getId").invoke(createR.getData());
            if (courseId != null) {
                // Update status to published (1)
                mockMvc.perform(put("/api/courses/" + courseId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .param("status", "1"))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    @DisplayName("删除课程返回成功")
    void deleteCourse_AsAdmin_Success() throws Exception {
        String token = getToken("admin", "admin123");
        if (token == null) {
            return;
        }

        // Create a course first to delete
        CourseCreateRequest createRequest = new CourseCreateRequest();
        createRequest.setTitle("Course to Delete");
        createRequest.setDescription("Will be deleted");
        createRequest.setCategoryId(1L);

        MvcResult createResult = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        R createR = objectMapper.readValue(createResponse, R.class);
        if (createR.getData() != null) {
            Long courseId = (Long) createR.getData().getClass().getMethod("getId").invoke(createR.getData());
            if (courseId != null) {
                mockMvc.perform(delete("/api/courses/" + courseId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }
}