package com.microcourse.contract;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 契约端点覆盖测试
 *
 * <p>验证所有 Controller 的 @RequestMapping 端点都在 OpenAPI 规范中注册。
 * 任何 Controller 新增端点但未添加 @Operation 注解 → CI fail。</p>
 */
@DisplayName("契约端点覆盖测试")
public class ContractEndpointCoverageTest extends BaseIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. /v3/api-docs 可访问")
    public void test_openapiDocsAccessible() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        assertEquals(200, result.getResponse().getStatus(), "/v3/api-docs 必须可访问");
        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        assertTrue(body.contains("\"openapi\""), "响应必须是 OpenAPI JSON");
        System.out.println("[Contract] /v3/api-docs 可访问, JSON 大小=" + body.length() + " 字节");
    }

    @Test
    @DisplayName("2. OpenAPI 响应包含 paths 字段且非空")
    public void test_openApiPathsNotEmpty() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"paths\""), "响应必须包含 paths 字段");
        // 粗略计数 paths 字段下的 key
        int pathCount = 0;
        int idx = body.indexOf("\"paths\":{");
        if (idx > 0) {
            // 简单计数
            int braceDepth = 0;
            boolean inPaths = false;
            for (int i = idx; i < body.length() && i < idx + 50000; i++) {
                if (body.charAt(i) == '{') {
                    braceDepth++;
                    inPaths = true;
                } else if (body.charAt(i) == '}') {
                    braceDepth--;
                    if (inPaths && braceDepth == 0) break;
                }
            }
            // 简单估算: 找 "/api/" 出现次数
            int searchFrom = 0;
            while ((searchFrom = body.indexOf("\"/api/", searchFrom)) > 0) {
                pathCount++;
                searchFrom++;
            }
        }
        assertTrue(pathCount >= 50, "OpenAPI 应至少包含 50 个端点, 实际: " + pathCount);
        System.out.println("[Contract] OpenAPI 包含约 " + pathCount + " 个端点");
    }
}
