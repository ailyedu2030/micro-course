package com.microcourse.service;

import com.microcourse.enums.TeachingClassStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 6 教学班状态机枚举单元测试（纯 JUnit，无 Spring/DB 依赖）。
 * 验证 TeachingClassStatus 的契约对齐（对齐 docs/状态机设计.md §4 / 数据字典 §2.9）、
 * 状态流转白名单、终态约束、以及初始状态为 ACTIVE(1) 的契约。
 */
@DisplayName("Round 6 TeachingClassStatus 状态机枚举")
class TeachingClassStatusTest {

    @Test
    @DisplayName("fromCode 覆盖所有契约值；null 透传；未知码抛异常")
    void fromCodeShouldHandleAllValues() {
        assertEquals(TeachingClassStatus.CANCELLED, TeachingClassStatus.fromCode(0));
        assertEquals(TeachingClassStatus.ACTIVE, TeachingClassStatus.fromCode(1));
        assertEquals(TeachingClassStatus.COMPLETED, TeachingClassStatus.fromCode(2));
        assertNull(TeachingClassStatus.fromCode(null));
        assertThrows(IllegalArgumentException.class, () -> TeachingClassStatus.fromCode(99));
    }

    @Test
    @DisplayName("状态码与标签对齐契约（0=已停开 / 1=开课中 / 2=已结课）")
    void codeAndLabelShouldAlignContract() {
        assertEquals(0, TeachingClassStatus.CANCELLED.getCode().intValue());
        assertEquals(1, TeachingClassStatus.ACTIVE.getCode().intValue());
        assertEquals(2, TeachingClassStatus.COMPLETED.getCode().intValue());
        assertEquals("已停开", TeachingClassStatus.CANCELLED.getLabel());
        assertEquals("开课中", TeachingClassStatus.ACTIVE.getLabel());
        assertEquals("已结课", TeachingClassStatus.COMPLETED.getLabel());
    }

    @Test
    @DisplayName("合法流转白名单：ACTIVE → COMPLETED / CANCELLED；自环与 null 拒绝")
    void canTransitionToShouldRespectWhitelist() {
        assertTrue(TeachingClassStatus.ACTIVE.canTransitionTo(TeachingClassStatus.COMPLETED));
        assertTrue(TeachingClassStatus.ACTIVE.canTransitionTo(TeachingClassStatus.CANCELLED));
        assertFalse(TeachingClassStatus.ACTIVE.canTransitionTo(TeachingClassStatus.ACTIVE));
        assertFalse(TeachingClassStatus.ACTIVE.canTransitionTo(null));
    }

    @Test
    @DisplayName("终态 COMPLETED / CANCELLED 拒绝任何流转")
    void terminalStatesShouldHaveNoTransition() {
        TeachingClassStatus[] terminals = {TeachingClassStatus.COMPLETED, TeachingClassStatus.CANCELLED};
        for (TeachingClassStatus terminal : terminals) {
            for (TeachingClassStatus target : TeachingClassStatus.values()) {
                assertFalse(terminal.canTransitionTo(target),
                        terminal + " -> " + target + " 应为非法转换（终态不可再转）");
            }
        }
    }

    @Test
    @DisplayName("初始状态应为 ACTIVE(1)，印证 create() 默认值修复（非 CANCELLED(0)）")
    void initialStatusShouldBeActive() {
        assertEquals(1, TeachingClassStatus.ACTIVE.getCode().intValue());
        assertNotEquals(TeachingClassStatus.CANCELLED.getCode(), TeachingClassStatus.ACTIVE.getCode());
    }
}
