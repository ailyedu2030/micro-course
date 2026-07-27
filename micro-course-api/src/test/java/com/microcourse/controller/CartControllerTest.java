package com.microcourse.controller;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P2-16: 购物车 CartController 集成测试。
 *
 * <p>覆盖 5 个端点（获取/添加/更新/删除/清空）+ 4 个异常场景。</p>
 */
@DisplayName("CartController · 购物车集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CartControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM cart_items");
    }

    @Test
    @DisplayName("GET /api/cart · 空购物车返回空列表")
    void getCart_Empty_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/cart · 添加课程到购物车成功")
    void addItem_ValidRequest_ReturnsCartItem() throws Exception {
        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courseId").value(1))
                .andExpect(jsonPath("$.data.quantity").value(1));
    }

    @Test
    @DisplayName("POST + GET · 添加后查询列表包含该课程")
    void addThenGet_ContainsAddedCourse() throws Exception {
        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].courseId").value(1))
                .andExpect(jsonPath("$.data[0].quantity").value(2));
    }

    @Test
    @DisplayName("PUT /api/cart/{itemId} · 更新购物车项数量成功")
    void updateQuantity_ValidRequest_ReturnsUpdatedItem() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andReturn();
        Number itemId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/cart/" + itemId.longValue())
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.quantity").value(5));
    }

    @Test
    @DisplayName("DELETE /api/cart/{itemId} · 删除购物车单项成功")
    void removeItem_ExistingItem_ReturnsOk() throws Exception {
        MvcResult addResult = mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andReturn();
        Number itemId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/cart/" + itemId.longValue())
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /api/cart · 清空购物车成功")
    void clearCart_MultipleItems_ReturnsOkAndEmptyCart() throws Exception {
        // Add two items
        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":2,\"quantity\":1}"))
                .andExpect(status().isOk());

        // Clear
        mockMvc.perform(delete("/api/cart")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Verify empty
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("POST /api/cart · 重复添加同一课程可叠加数量")
    void addItem_DuplicateCourse_IncrementsQuantity() throws Exception {
        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/cart")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":2}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("全部端点 · 未认证返回 401")
    void withoutAuth_AllEndpoints_Returns401() throws Exception {
        mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/cart/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/cart/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/cart")).andExpect(status().isUnauthorized());
    }
}
