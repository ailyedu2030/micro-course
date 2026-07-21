package com.microcourse.service;

import com.microcourse.enums.EnrollmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-2 选课状态机枚举单元测试（纯 JUnit，无 Spring/DB 依赖）。
 * 直接验证 EnrollmentStatus 的契约对齐、历史值兼容、状态流转白名单。
 */
@DisplayName("P0-2 EnrollmentStatus 状态机枚举")
class EnrollmentStatusTest {

    @Test
    @DisplayName("历史脏值 ENROLLED 归一为 APPROVED（大小写不敏感）")
    void fromString_legacyEnrolled_mapsToApproved() {
        assertEquals(EnrollmentStatus.APPROVED, EnrollmentStatus.fromString("ENROLLED"));
        assertEquals(EnrollmentStatus.APPROVED, EnrollmentStatus.fromString("enrolled"));
    }

    @Test
    @DisplayName("契约枚举值正常解析；null 透传")
    void fromString_contractValues() {
        assertEquals(EnrollmentStatus.PENDING, EnrollmentStatus.fromString("PENDING"));
        assertEquals(EnrollmentStatus.COMPLETED, EnrollmentStatus.fromString("completed"));
        assertEquals(EnrollmentStatus.WAITLIST, EnrollmentStatus.fromString("WAITLIST"));
        assertNull(EnrollmentStatus.fromString(null));
    }

    @Test
    @DisplayName("未知状态字符串抛 IllegalArgumentException")
    void fromString_unknown_throws() {
        assertThrows(IllegalArgumentException.class, () -> EnrollmentStatus.fromString("FOO"));
    }

    @Test
    @DisplayName("契约枚举不含 ENROLLED；LEGACY 常量保留为 ENROLLED")
    @SuppressWarnings("deprecation")
    void contractEnumExcludesEnrolled() {
        assertEquals("ENROLLED", EnrollmentStatus.LEGACY_ENROLLED_VALUE);
        for (EnrollmentStatus s : EnrollmentStatus.values()) {
            assertNotEquals("ENROLLED", s.getValue(), "契约枚举不得包含 ENROLLED 值");
        }
    }

    @Test
    @DisplayName("合法状态流转白名单（对齐状态机设计 §3.2）")
    void validTransitions() {
        assertTrue(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.APPROVED));
        assertTrue(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.REJECTED));
        assertTrue(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.WAITLIST));
        assertTrue(EnrollmentStatus.APPROVED.canTransitionTo(EnrollmentStatus.COMPLETED));
        assertTrue(EnrollmentStatus.APPROVED.canTransitionTo(EnrollmentStatus.CANCELLED));
        assertTrue(EnrollmentStatus.APPROVED.canTransitionTo(EnrollmentStatus.DROPPED));
        assertTrue(EnrollmentStatus.WAITLIST.canTransitionTo(EnrollmentStatus.APPROVED));
        assertTrue(EnrollmentStatus.WAITLIST.canTransitionTo(EnrollmentStatus.CANCELLED));
    }

    @Test
    @DisplayName("终态拒绝任何流转")
    void terminalStatesRejectAll() {
        EnrollmentStatus[] terminals = {
                EnrollmentStatus.REJECTED, EnrollmentStatus.CANCELLED,
                EnrollmentStatus.COMPLETED, EnrollmentStatus.DROPPED
        };
        // P1C-008 例外: CANCELLED → REENROLLING 合法（退课后再选课标记旧记录）
        for (EnrollmentStatus terminal : terminals) {
            for (EnrollmentStatus target : EnrollmentStatus.values()) {
                // 跳过 P1C-008 允许的合法转换
                if (terminal == EnrollmentStatus.CANCELLED && target == EnrollmentStatus.REENROLLING) {
                    continue;
                }
                assertFalse(terminal.canTransitionTo(target),
                        terminal.getValue() + " -> " + target.getValue() + " 应为非法转换");
            }
        }
    }

    @Test
    @DisplayName("典型非法流转与同态自环均被拒绝")
    void illegalTransitions() {
        assertFalse(EnrollmentStatus.CANCELLED.canTransitionTo(EnrollmentStatus.COMPLETED));
        assertFalse(EnrollmentStatus.APPROVED.canTransitionTo(EnrollmentStatus.PENDING));
        assertFalse(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.COMPLETED));
        assertFalse(EnrollmentStatus.APPROVED.canTransitionTo(EnrollmentStatus.APPROVED));
        assertFalse(EnrollmentStatus.PENDING.canTransitionTo(null));
    }
}
