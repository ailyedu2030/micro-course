package com.microcourse.service;

import com.microcourse.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 6 用户状态机枚举单元测试（纯 JUnit，无 Spring/DB 依赖）。
 * 直接验证 UserStatus 的状态码映射、状态流转白名单、容错解析。
 */
@DisplayName("Round 6 UserStatus 状态机枚举")
class UserStatusTest {

    @Test
    @DisplayName("fromCode 正确映射 0/1/2/3；null 透传；未知码抛异常")
    void fromCodeShouldHandleAllValues() {
        assertEquals(UserStatus.INACTIVE, UserStatus.fromCode(0));
        assertEquals(UserStatus.ACTIVE, UserStatus.fromCode(1));
        assertEquals(UserStatus.DISABLED, UserStatus.fromCode(2));
        assertEquals(UserStatus.DELETED, UserStatus.fromCode(3));
        assertNull(UserStatus.fromCode(null));
        assertThrows(IllegalArgumentException.class, () -> UserStatus.fromCode(99));
        // getCode 反向自洽
        for (UserStatus s : UserStatus.values()) {
            assertEquals(s, UserStatus.fromCode(s.getCode()));
        }
    }

    @Test
    @DisplayName("canTransitionTo 严格遵循白名单（对齐状态机设计 §1.2）")
    void canTransitionToShouldRespectWhitelist() {
        // 合法转换
        assertTrue(UserStatus.INACTIVE.canTransitionTo(UserStatus.ACTIVE));
        // 【user-domain-drift-fix 修复】移除 INACTIVE→DELETED 超额转换 (状态机设计 §1.3)
        assertFalse(UserStatus.INACTIVE.canTransitionTo(UserStatus.DELETED));
        assertTrue(UserStatus.ACTIVE.canTransitionTo(UserStatus.DISABLED));
        assertTrue(UserStatus.ACTIVE.canTransitionTo(UserStatus.DELETED));
        assertTrue(UserStatus.DISABLED.canTransitionTo(UserStatus.ACTIVE));
        assertTrue(UserStatus.DISABLED.canTransitionTo(UserStatus.DELETED));
        assertTrue(UserStatus.DELETED.canTransitionTo(UserStatus.ACTIVE)); // S-05: DELETED→ACTIVE

        // 典型非法转换
        assertFalse(UserStatus.ACTIVE.canTransitionTo(UserStatus.INACTIVE));
        assertFalse(UserStatus.DISABLED.canTransitionTo(UserStatus.INACTIVE));
        assertFalse(UserStatus.INACTIVE.canTransitionTo(UserStatus.DISABLED));
        assertFalse(UserStatus.DELETED.canTransitionTo(UserStatus.INACTIVE)); // S-05: DELETED 不再允许→INACTIVE
        assertFalse(UserStatus.DELETED.canTransitionTo(UserStatus.DISABLED));

        // self / null 均拒绝
        assertFalse(UserStatus.ACTIVE.canTransitionTo(UserStatus.ACTIVE));
        assertFalse(UserStatus.ACTIVE.canTransitionTo(null));
    }

    @Test
    @DisplayName("DELETED 为准终态：唯一出口 ACTIVE（恢复），其余全拒绝")
    void terminalStatesShouldHaveNoTransition() {
        // UserStatus 无完全终态；DELETED 仅保留单一恢复出口 ACTIVE（S-05 修复）
        for (UserStatus target : UserStatus.values()) {
            if (target == UserStatus.ACTIVE) {
                assertTrue(UserStatus.DELETED.canTransitionTo(target),
                        "DELETED -> ACTIVE（恢复）应为合法转换");
            } else {
                assertFalse(UserStatus.DELETED.canTransitionTo(target),
                        "DELETED -> " + target + " 应为非法转换");
            }
        }
    }
}
