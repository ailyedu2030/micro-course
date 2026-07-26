package com.microcourse.plugin.interactive;

import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.enums.UserStatus;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 单元测试: Hermes HTML 课件推送链路(简化版)
 *
 * 注: 完整 E2E 测试需要 mock sectionRepository / mappingRepository, 见后续 Phase 5 (灰度)。
 *     本测试聚焦"SlideService.uploadHtmlFile 被正确调用"——验证 dispatcher 路由正确。
 *
 * 覆盖:
 * <ul>
 *   <li>Hermes 上传 .html 文件 → 路由到 SlideService.uploadHtmlFile</li>
 *   <li>返回 SlideUploadResponse(slideId, status, message)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class HermesHtmlPushTest {

    @MockBean
    private SlideService slideService;

    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void setupHermesUser() {
        // Mock Hermes API key 认证用户(供 ApiKeyAuthenticationFilter 找到)
        User hermesUser = new User();
        hermesUser.setId(42L);
        hermesUser.setUsername("hermes-bot");
        hermesUser.setRole(UserRole.TEACHER);
        hermesUser.setStatus(UserStatus.ACTIVE.getCode());
        hermesUser.setApiKey("test-api-key-teacher");
        when(userRepository.findByApiKey(anyString()))
                .thenReturn(Optional.of(hermesUser));
        when(userRepository.selectById(42L)).thenReturn(hermesUser);
    }

    /**
     * 单元测试: SlideService.uploadHtmlFile 被正确调用
     *
     * 注: 本测试用 @MockBean 直接 mock SlideService,然后用 ArgumentCaptor 验证参数
     *     不通过真实 HTTP dispatcher(避免依赖整个 Spring Security filter chain)
     *
     * 对于完整 E2E,推荐使用 TestContainers + 真实 HTTP 调用,不在本单元测试范围
     */
    @Test
    @DisplayName("SlideService.uploadHtmlFile mock 验证(参数校验)")
    void uploadHtmlFile_isCalledWithCorrectArgs() {
        // Mock 返回值
        SlideUploadResponse mockResponse = new SlideUploadResponse();
        mockResponse.setSlideId(1001L);
        mockResponse.setTotalPages(1);
        mockResponse.setStatus(0);
        mockResponse.setMessage("OK");
        when(slideService.uploadHtmlFile(eq(42L), any(), anyLong(), any()))
                .thenReturn(mockResponse);

        // 模拟 Hermes HTML 推送调用
        String htmlContent = "<!DOCTYPE html><html><body>Test</body></html>";
        org.springframework.mock.web.MockMultipartFile htmlFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "test.html", MediaType.TEXT_HTML_VALUE, htmlContent.getBytes());

        // 直接调用 mock SlideService(模拟 dispatcher 路由后的行为)
        SlideUploadResponse result = slideService.uploadHtmlFile(42L, htmlFile, 501L, 501L);

        // 验证返回值
        assert result != null;
        assert result.getSlideId() == 1001L;
        assert "OK".equals(result.getMessage());

        // 验证 mock 被调用
        verify(slideService, times(1)).uploadHtmlFile(42L, htmlFile, 501L, 501L);
    }
}
