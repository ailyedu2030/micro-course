package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round 6 用户状态机集成测试（需 DB）。
 * 验证：状态转换白名单 / DELETED→ACTIVE 180 天恢复窗口 / 枚举契约。
 */
@DisplayName("Round 6 UserStatusMachine 集成")
class UserStatusMachineTest extends BaseIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserService userService;

    // --------- helpers ---------

    private String uniq() {
        return Long.toString(System.nanoTime()) + "-" + SEQ.incrementAndGet();
    }

    /** 旁路插入用户记录（status / deleted_at 由参数指定），用于构造各状态机场景。 */
    private Long insertUser(int status, Timestamp deletedAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, deleted_at, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'STUDENT', ?, false, ?, now(), now(), 0) RETURNING id",
                Long.class,
                "ustest-" + uniq(),
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv",
                "状态机测试用户",
                status,
                deletedAt);
    }

    private UserStatusRequest req(int statusCode) {
        UserStatusRequest r = new UserStatusRequest();
        r.setStatus(statusCode);
        return r;
    }

    private Integer dbStatus(Long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", Integer.class, id);
    }

    private Timestamp dbDeletedAt(Long id) {
        return jdbcTemplate.queryForObject("SELECT deleted_at FROM users WHERE id = ?", Timestamp.class, id);
    }

    // --------- tests ---------

    @Test
    @DisplayName("非法转换 ACTIVE → INACTIVE 应被拒绝")
    void shouldRejectIllegalStatusTransition() {
        Long userId = insertUser(1, null); // ACTIVE
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateStatus(userId, req(0))); // → INACTIVE
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION.getCode(), ex.getCode());
        // 状态未被篡改
        assertEquals(1, dbStatus(userId));
    }

    @Test
    @DisplayName("合法转换 ACTIVE → DISABLED 应通过")
    void shouldAllowValidStatusTransition() {
        Long userId = insertUser(1, null); // ACTIVE
        userService.updateStatus(userId, req(2)); // → DISABLED
        assertEquals(2, dbStatus(userId));
    }

    @Test
    @DisplayName("恢复已删除超过 180 天的用户应被拒绝")
    void shouldRejectRestoringDeletedUserAfter180Days() {
        Timestamp deletedAt = Timestamp.valueOf(LocalDateTime.now().minusDays(200));
        Long userId = insertUser(3, deletedAt); // DELETED 200 天前
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.updateStatus(userId, req(1))); // → ACTIVE（恢复，S-05 修复）
        assertEquals(ErrorCode.DELETED_USER_RETENTION_EXPIRED.getCode(), ex.getCode());
        // 仍为 DELETED，未被恢复
        assertEquals(3, dbStatus(userId));
    }

    @Test
    @DisplayName("恢复已删除 180 天内的用户应通过（status=1 且清空 deleted_at）")
    void shouldAllowRestoringDeletedUserWithin180Days() {
        Timestamp deletedAt = Timestamp.valueOf(LocalDateTime.now().minusDays(30));
        Long userId = insertUser(3, deletedAt); // DELETED 30 天前
        userService.updateStatus(userId, req(1)); // → ACTIVE（恢复，S-05 修复）
        assertEquals(1, dbStatus(userId));
        assertNull(dbDeletedAt(userId), "恢复后 deleted_at 应被清空");
    }

    @Test
    @DisplayName("UserStatus.fromCode 对未知状态码抛 IllegalArgumentException")
    void userStatusFromCodeShouldThrowOnUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> UserStatus.fromCode(99));
    }

    @Test
    @DisplayName("UserStatus.canTransitionTo 遵循白名单")
    void userStatusCanTransitionShouldRespectWhitelist() {
        assertTrue(UserStatus.ACTIVE.canTransitionTo(UserStatus.DISABLED));
        assertFalse(UserStatus.INACTIVE.canTransitionTo(UserStatus.DISABLED));
    }
}
