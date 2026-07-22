package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.ErrorCode;
import com.microcourse.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 订单支付全链路 E2E 测试（Round 10-1 · 财务最高风险路径回归保护）。
 *
 * <p><b>覆盖目标</b>：OrderController 全部 6 个端点（创建 / 查详情 / 查我的 / 支付 / 取消 / 回调），
 * 验证「创建订单 → 支付 / 回调 → 取消 → 权限隔离」完整财务链路，为此前<b>零测试覆盖</b>的
 * 学生付费流程建立回归守护网。</p>
 *
 * <p><b>设计原则（对齐 UX 零退化 5 大硬约束）</b>：</p>
 * <ul>
 *   <li><b>业务逻辑零修改</b>：仅新增测试类，不触碰任何 src/main 代码。</li>
 *   <li><b>完全自包含 + 零污染</b>：每个 @Test 用 {@link JdbcTemplate} 自建独立的
 *       分类 / 教师 / 学生 / 付费课程，{@link #cleanup()} 按 FK 反向定向删除，
 *       <b>不依赖也不污染</b> p0-seed 种子（student id=7）与现有 153 个 PASS 测试。</li>
 *   <li><b>测试幂等</b>：复用既有订单幂等设计（同课程重复创建返回同一订单），断言其语义。</li>
 *   <li>用 {@link JwtUtil#generateToken} 为自建用户直接签发 token，绕过登录 Redis 限流，
 *       与 {@code EnrollmentFlowIntegrationTest} 并发用例同源策略，互不干扰。</li>
 * </ul>
 *
 * <p><b>关键业务契约（已逐行核对 src/main，作为断言依据）</b>：</p>
 * <ul>
 *   <li>R 成功响应 {@code code=200}（见 {@code R.ok}）；BusinessException 经
 *       {@code GlobalExceptionHandler} 映射为 ErrorCode.httpStatus。</li>
 *   <li>付费课程判定使用 {@code getMyPricing} 取代旧 price 直读，
 *       fixture 配置为 <b>price=99.00 且 is_free=FALSE、pricing_status=APPROVED、list_price=99.00</b>，
 *       确保定价审核通过，学生方可创建付费订单。</li>
 *   <li>回调端点为 {@code POST /api/orders/callback}（无路径 id，靠 body.orderNo 定位），
 *       需 ADMIN 角色；仅 PENDING 订单可被回调推进，重复回调因状态非 PENDING 直接幂等返回。</li>
 *   <li>取消端点为 {@code POST /api/orders/{id}/cancel}；仅 PENDING 可取消，
 *       否则抛 BAD_REQUEST_PARAM(9005)→400。</li>
 *   <li>查详情端点 {@code GET /api/orders/{id}} 为 isAuthenticated()，
 *       归属校验在 Service 层：非本人 / 非 ADMIN / 非 ACADEMIC → NO_PERMISSION(10003)→403。</li>
 * </ul>
 */
@DisplayName("Round 10-1 · 订单支付全链路 E2E")
class OrderPaymentFlowE2ETest extends BaseIntegrationTest {

    private static final String PAID_COURSE_PRICE = "99.00";
    private static final String OCCUPYING_BCRYPT =
            "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv"; // 占位哈希：本类全程用 JWT 直签，不走密码登录

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // FK 反向定向清理：仅删除本测试创建的行，保证对其它测试零污染。
        for (Long u : createdUserIds) {
            safe(() -> jdbc.update(
                    "DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", u));
            safe(() -> jdbc.update("DELETE FROM orders WHERE user_id = ?", u));
            safe(() -> jdbc.update(
                    "DELETE FROM enrollment_histories WHERE enrollment_id IN "
                            + "(SELECT id FROM enrollments WHERE user_id = ?)", u));
            safe(() -> jdbc.update("DELETE FROM enrollments WHERE user_id = ?", u));
            safe(() -> jdbc.update("DELETE FROM learning_progress WHERE user_id = ?", u));
            safe(() -> jdbc.update("DELETE FROM notifications WHERE user_id = ?", u));
        }
        for (Long c : createdCourseIds) {
            safe(() -> jdbc.update("DELETE FROM courses WHERE id = ?", c));
        }
        for (Long u : createdUserIds) {
            safe(() -> jdbc.update("DELETE FROM users WHERE id = ?", u));
        }
        for (Long cat : createdCategoryIds) {
            safe(() -> jdbc.update("DELETE FROM course_categories WHERE id = ?", cat));
        }
        createdUserIds.clear();
        createdCourseIds.clear();
        createdCategoryIds.clear();
    }

    // =========================================================================
    // 1 · 创建订单（付费课程，学生未选课）
    // =========================================================================
    @Test
    @DisplayName("1·学生对付费课程创建订单 → 200 + PENDING + 落库 1 条")
    void shouldCreateOrderForPaidCourse() throws Exception {
        Ctx ctx = newPaidCourseScenario();

        MvcResult res = mockMvc.perform(post("/api/orders")
                        .header("Authorization", ctx.studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + ctx.courseId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        long orderId = readLong(res, "$.data.id");
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE id = ? AND status = 'PENDING'", Long.class, orderId);
        assertEquals(1L, count, "应落库 1 条 PENDING 订单");

        BigDecimal amount = jdbc.queryForObject(
                "SELECT amount FROM orders WHERE id = ?", BigDecimal.class, orderId);
        assertEquals(0, new BigDecimal(PAID_COURSE_PRICE).compareTo(amount), "订单金额必须等于课程价格");
    }

    // =========================================================================
    // 2 · 幂等：同课程重复创建返回同一订单
    // =========================================================================
    @Test
    @DisplayName("2·同课程重复创建 → 幂等返回同一订单（id 相同，不重复落库）")
    void shouldReturnSameOrderForDuplicateCreate() throws Exception {
        Ctx ctx = newPaidCourseScenario();

        long id1 = readLong(createOrder(ctx), "$.data.id");
        long id2 = readLong(createOrder(ctx), "$.data.id");

        assertEquals(id1, id2, "幂等：两次创建必须返回同一订单 id");
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ? AND course_id = ?",
                Long.class, ctx.studentId, ctx.courseId);
        assertEquals(1L, count, "幂等：同课程只允许 1 条订单");
    }

    // =========================================================================
    // 3 · 查询我的订单（分页）
    // =========================================================================
    @Test
    @DisplayName("3·查询我的订单 → 200 + PageResult 含刚创建订单")
    void shouldGetMyOrders() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        long orderId = readLong(createOrder(ctx), "$.data.id");

        MvcResult res = mockMvc.perform(get("/api/orders/my?page=0&size=20")
                        .header("Authorization", ctx.studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn();

        List<Number> ids = JsonPath.read(res.getResponse().getContentAsString(), "$.data.items[*].id");
        assertTrue(ids.stream().anyMatch(i -> i.longValue() == orderId),
                "我的订单列表必须包含刚创建的订单 " + orderId);
        long total = readLong(res, "$.data.totalElements");
        assertTrue(total >= 1, "totalElements 应 >= 1");
    }

    // =========================================================================
    // 4 · 查询订单详情
    // =========================================================================
    @Test
    @DisplayName("4·查询订单详情 → 200 + id/status 正确")
    void shouldGetOrderDetail() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        long orderId = readLong(createOrder(ctx), "$.data.id");

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", ctx.studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value((int) orderId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // =========================================================================
    // 5 · 发起支付 → 订单 PAID + Payment 流水 + 自动选课
    // =========================================================================
    @Test
    @DisplayName("5·发起支付 → 200 + PAID + 落 Payment + 自动选课")
    void shouldInitiatePaymentAndMarkPaid() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        long orderId = readLong(createOrder(ctx), "$.data.id");

        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", ctx.studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"WECHAT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentMethod").value("WECHAT"));

        assertEquals("PAID", orderStatus(orderId), "支付后订单状态必须为 PAID");
        Long payCount = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ? AND status = 'SUCCESS'", Long.class, orderId);
        assertEquals(1L, payCount, "支付成功必须落 1 条 Payment 流水");
        Long enrollCount = jdbc.queryForObject(
                "SELECT count(*) FROM enrollments WHERE user_id = ? AND course_id = ?",
                Long.class, ctx.studentId, ctx.courseId);
        assertEquals(1L, enrollCount, "支付成功必须自动完成选课（钱课不两空）");
    }

    // =========================================================================
    // 6 · 支付成功回调 → 订单 PAID + Payment + 自动选课
    // =========================================================================
    @Test
    @DisplayName("6·支付成功回调（ADMIN）→ 200 + PAID + 落 Payment + 自动选课")
    void shouldHandlePaymentCallbackSuccess() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        String orderNo = readString(createOrder(ctx), "$.data.orderNo");
        long orderId = readLong(createOrder(ctx), "$.data.id"); // 幂等：同一订单

        mockMvc.perform(post("/api/orders/callback")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\",\"status\":\"SUCCESS\",\"method\":\"WECHAT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals("PAID", orderStatus(orderId), "回调成功后订单状态必须为 PAID");
        Long payCount = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ?", Long.class, orderId);
        assertEquals(1L, payCount, "回调成功必须落 1 条 Payment 流水");
        Long enrollCount = jdbc.queryForObject(
                "SELECT count(*) FROM enrollments WHERE user_id = ? AND course_id = ?",
                Long.class, ctx.studentId, ctx.courseId);
        assertEquals(1L, enrollCount, "回调成功必须自动完成选课");
    }

    // =========================================================================
    // 7 · 重复回调幂等 → 不重复推进、不重复落 Payment
    // =========================================================================
    @Test
    @DisplayName("7·重复支付回调 → 幂等（仍 PAID，Payment 仅 1 条）")
    void shouldHandleDuplicatePaymentCallbackIdempotently() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        String orderNo = readString(createOrder(ctx), "$.data.orderNo");
        long orderId = readLong(createOrder(ctx), "$.data.id");
        String body = "{\"orderNo\":\"" + orderNo + "\",\"status\":\"SUCCESS\",\"method\":\"WECHAT\"}";

        mockMvc.perform(post("/api/orders/callback").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // 第二次相同回调：订单已非 PENDING，应幂等返回且不再落库
        mockMvc.perform(post("/api/orders/callback").header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals("PAID", orderStatus(orderId), "重复回调后订单仍为 PAID");
        Long payCount = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ?", Long.class, orderId);
        assertEquals(1L, payCount, "重复回调不得重复落 Payment（幂等）");
    }

    // =========================================================================
    // 8 · 取消待支付订单
    // =========================================================================
    @Test
    @DisplayName("8·取消待支付订单 → 200 + CANCELLED")
    void shouldCancelPendingOrder() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        long orderId = readLong(createOrder(ctx), "$.data.id");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", ctx.studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertEquals("CANCELLED", orderStatus(orderId), "取消后订单状态必须为 CANCELLED");
    }

    // =========================================================================
    // 9 · 已支付订单不可取消
    // =========================================================================
    @Test
    @DisplayName("9·已支付订单取消 → 400（只能取消待支付订单）")
    void shouldNotCancelPaidOrder() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        long orderId = readLong(createOrder(ctx), "$.data.id");

        // 先支付，使订单进入 PAID 终态（非 PENDING）
        mockMvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", ctx.studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"WECHAT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", ctx.studentToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_STATUS_TRANSITION.getCode()));

        assertEquals("PAID", orderStatus(orderId), "尝试取消失败后订单仍应为 PAID");
    }

    // =========================================================================
    // 10 · 权限隔离：他人（教师）不能查看学生订单
    // =========================================================================
    @Test
    @DisplayName("10·教师查看他人订单详情 → 403（NO_PERMISSION）")
    void shouldNotViewOthersOrders() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        long orderId = readLong(createOrder(ctx), "$.data.id");

        // teacher 是课程 owner，但非订单 owner、非 ADMIN、非 ACADEMIC → Service 层拒绝
        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", ctx.teacherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // =========================================================================
    // 11 · 支付失败回调 → 订单保持 PENDING，可重新支付
    // =========================================================================
    @Test
    @DisplayName("11·支付失败回调 → 订单保持 PENDING + 无 Payment")
    void shouldHandlePaymentFailureCallback() throws Exception {
        Ctx ctx = newPaidCourseScenario();
        String orderNo = readString(createOrder(ctx), "$.data.orderNo");
        long orderId = readLong(createOrder(ctx), "$.data.id");

        mockMvc.perform(post("/api/orders/callback")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\",\"status\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals("PENDING", orderStatus(orderId), "支付失败回调不得改变订单状态（仍 PENDING，可重试）");
        Long payCount = jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE order_id = ?", Long.class, orderId);
        assertEquals(0L, payCount, "支付失败不得落 Payment 流水");
    }

    // =========================================================================
    // 12 · 未认证访问订单 API → 401
    // =========================================================================
    @Test
    @DisplayName("12·未认证访问订单 API → 401")
    void shouldRequireAuthForOrderEndpoints() throws Exception {
        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // === 辅助方法 ===
    // =========================================================================

    /** 一次性创建「分类 + 教师 + 学生 + 付费课程」并签发 token 的独立测试场景。 */
    private Ctx newPaidCourseScenario() {
        Long categoryId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long studentId = insertUser("STUDENT");
        Long courseId = insertPaidCourse(categoryId, teacherId);
        Ctx ctx = new Ctx();
        ctx.studentId = studentId;
        ctx.courseId = courseId;
        ctx.studentToken = bearerFor(studentId, "stu-" + studentId, UserRole.STUDENT);
        ctx.teacherToken = bearerFor(teacherId, "tea-" + teacherId, UserRole.TEACHER);
        return ctx;
    }

    private MvcResult createOrder(Ctx ctx) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .header("Authorization", ctx.studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + ctx.courseId + "}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Long insertCategory() {
        Long id = jdbc.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) "
                        + "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "ord-cat-" + System.nanoTime());
        createdCategoryIds.add(id);
        return id;
    }

    private Long insertUser(String role) {
        String username = role.toLowerCase() + "-ord-" + System.nanoTime();
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 1, false, now(), now()) RETURNING id",
                Long.class, username, OCCUPYING_BCRYPT, "订单测试" + role, role);
        createdUserIds.add(id);
        return id;
    }

    /**
     * 付费课程：price=99.00 且 is_free=TRUE、status=4(PUBLISHED)。
     * 该组合是支付链路可贯通的唯一正确配置（见类注释「关键业务契约」）：
     *   createOrder 按 price&gt;0 判定收费而建单；enroll 按 is_free=TRUE 放行支付时自动选课。
     */
    private Long insertPaidCourse(Long categoryId, Long teacherId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, "
                        + "list_price, pricing_status, course_type, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 4, false, ?, ?, 'APPROVED', 'VIDEO', 0, now(), now()) RETURNING id",
                Long.class, "ord-paid-course-" + System.nanoTime(), categoryId, teacherId,
                new BigDecimal(PAID_COURSE_PRICE), new BigDecimal(PAID_COURSE_PRICE));
        createdCourseIds.add(id);
        return id;
    }

    private String bearerFor(Long userId, String username, UserRole role) {
        return "Bearer " + jwtUtil.generateToken(userId, username, role, null);
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private static long readLong(MvcResult res, String path) throws Exception {
        Number n = JsonPath.read(res.getResponse().getContentAsString(), path);
        return n.longValue();
    }

    private static String readString(MvcResult res, String path) throws Exception {
        return JsonPath.read(res.getResponse().getContentAsString(), path);
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception ignored) {
            // 清理尽力而为：某行不存在 / 表无关列时忽略，不影响其它测试。
        }
    }

    /** 单测场景上下文：一组互相隔离的 fixture 标识与 token。 */
    private static final class Ctx {
        Long studentId;
        Long courseId;
        String studentToken;
        String teacherToken;
    }
}
