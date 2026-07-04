package com.microcourse.service;

import com.microcourse.enums.CourseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-1 回归测试: 课程状态机不允许从 PUBLISHED 直接跳到 DRAFT,
 * 防止代码回退到"任意状态可改"。
 */
@DisplayName("P0-1 课程状态机白名单回归")
class CourseStatusMachineRegressionTest {

    @Test
    @DisplayName("PUBLISHED → DRAFT 非法")
    void publishedToDraftNotAllowed() {
        assertFalse(CourseStatus.fromCode(4).canTransitionTo(CourseStatus.DRAFT));
    }

    @Test
    @DisplayName("DRAFT → PENDING_REVIEW 合法")
    void draftToPendingAllowed() {
        assertTrue(CourseStatus.fromCode(0).canTransitionTo(CourseStatus.fromCode(1)));
    }

    @Test
    @DisplayName("APPROVED → PUBLISHED 合法")
    void approvedToPublishedAllowed() {
        assertTrue(CourseStatus.fromCode(2).canTransitionTo(CourseStatus.fromCode(4)));
    }

    @Test
    @DisplayName("CLOSED → DRAFT 非法(归档课程不可再编辑)")
    void closedToDraftNotAllowed() {
        assertFalse(CourseStatus.fromCode(5).canTransitionTo(CourseStatus.DRAFT));
    }

    @Test
    @DisplayName("并发测试: 状态码是 0-based 还是 1-based 必须固化")
    void statusCodesAreStable() {
        // 任一状态码被改动会导致大量回归 — 本测试锁定
        assertEquals(0, CourseStatus.DRAFT.getCode());
        assertEquals(1, CourseStatus.PENDING_REVIEW.getCode());
        assertEquals(2, CourseStatus.APPROVED.getCode());
        assertEquals(3, CourseStatus.REJECTED.getCode());
        assertEquals(4, CourseStatus.PUBLISHED.getCode());
        assertEquals(5, CourseStatus.CLOSED.getCode());
    }
}
