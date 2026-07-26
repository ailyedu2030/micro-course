package com.microcourse.plugin.interactive;

import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.service.SlideService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试: Hermes HTML 课件推送链路.
 *
 * 模拟 Hermes 通过 Webhook /courses/{hermesCourseId}/lessons/{lessonId}/slide
 * 推送 HTML lesson, 验证后端能正确识别 contentType=HTML_DIRECT 并存储.
 *
 * 覆盖场景:
 * <ul>
 *   <li>Hermes 推送 HTML 文件 → POST /api/hermes/webhook/courses/{id}/lessons/{id}/slide</li>
 *   <li>后端 uploadSlide dispatcher 正确路由到 uploadHtmlFile</li>
 *   <li>响应包含 slideId / status 等字段</li>
 *   <li>HTML 文件大小 < 5MB 限制</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class HermesHtmlPushTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockBean
    private SlideService slideService;

    /**
     * 测试 T1: Hermes 推送 HTML lesson → 后端识别为 HTML_DIRECT.
     *
     * 模拟: Hermes 通过 Webhook 上传 .html 文件.
     * 预期:
     *   1. HTTP 200
     *   2. slideService.uploadHtmlFile 被调用 1 次
     *   3. 响应包含 slideId / status / message
     */
    @Test
    @DisplayName("Hermes 推送 HTML → 后端 uploadHtmlFile 分配路由 (contentType=HTML_DIRECT)")
    @WithMockUser(roles = "TEACHER")
    void hermesHtmlPush_dispatchesToUploadHtmlFile() throws Exception {
        // 初始化 MockMvc
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Mock SlideService.uploadHtmlFile 返回值
        SlideUploadResponse mockResponse = new SlideUploadResponse();
        mockResponse.setSlideId(1001L);
        mockResponse.setTotalPages(1);
        mockResponse.setStatus(0);
        mockResponse.setMessage("上传成功，HTML 课时已就绪 (contentType=HTML_DIRECT)");

        when(slideService.uploadHtmlFile(anyLong(), any(), anyLong(), any()))
                .thenReturn(mockResponse);

        // 构建 HTML 文件
        String htmlContent = "<!DOCTYPE html><html><body><h1>Hello Hermes</h1></body></html>";
        MockMultipartFile htmlFile = new MockMultipartFile(
                "file",
                "lesson-interactive.html",
                MediaType.TEXT_HTML_VALUE,
                htmlContent.getBytes("UTF-8")
        );

        // 发送请求: /api/hermes/webhook/courses/{hermesCourseId}/lessons/{lessonId}/slide
        mockMvc.perform(multipart("/api/hermes/webhook/courses/course-001/lessons/501/slide")
                        .file(htmlFile)
                        .header("X-API-Key", "test-api-key-teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.slideId").value(1001))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.message").value(containsString("HTML_DIRECT")));

        // 验证 uploadHtmlFile 被调用
        verify(slideService, times(1))
                .uploadHtmlFile(eq(42L), any(), anyLong(), eq(501L));
    }

    /**
     * 测试 T2: HTML 文件大小超过 5MB 限制 → 400 BAD_REQUEST.
     */
    @Test
    @DisplayName("HTML 文件超过 5MB 限制 → 400")
    @WithMockUser(roles = "TEACHER")
    void hermesHtmlPush_rejectsOversizedFile() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 构造 6MB 的 HTML 内容
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        // ~6MB of padding
        String padding = "x".repeat(1024 * 1024);
        for (int i = 0; i < 6; i++) {
            sb.append(padding);
        }
        sb.append("</body></html>");

        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file",
                "oversized.html",
                MediaType.TEXT_HTML_VALUE,
                sb.toString().getBytes("UTF-8")
        );

        // Hermes 端上传 → 内部 SlideController.upload → 应返回 400
        // (HermesWebhookCoursewareServiceImpl.validateUploadFile 拦截 5MB)
        mockMvc.perform(multipart("/api/courses/1/slides/upload")
                        .file(oversizedFile)
                        .header("X-API-Key", "test-api-key-teacher"))
                .andExpect(status().isOk()) // 统一 200, 业务码 400
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("5MB")));
    }

    /**
     * 测试 T3: Hermes 上传非 PPTX/HTML 文件 → 400.
     */
    @Test
    @DisplayName("Hermes 上传非课件格式 → 400")
    @WithMockUser(roles = "TEACHER")
    void hermesHtmlPush_rejectsInvalidFormat() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not a slide".getBytes("UTF-8")
        );

        mockMvc.perform(multipart("/api/courses/1/slides/upload")
                        .file(textFile)
                        .header("X-API-Key", "test-api-key-teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
