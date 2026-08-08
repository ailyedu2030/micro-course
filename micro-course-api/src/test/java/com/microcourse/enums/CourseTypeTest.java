package com.microcourse.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CourseType 枚举单测（V333 简化方案 · 4 值）。
 */
class CourseTypeTest {

    @Test
    void testNormalizeNew() {
        assertEquals(CourseType.HTML_COURSEWARE, CourseType.normalize("HTML_COURSEWARE"));
        assertEquals(CourseType.PPT_COURSEWARE, CourseType.normalize("PPT_COURSEWARE"));
    }

    @Test
    void testNormalizeLegacy() {
        // 旧值 INTERACTIVE 兼容 → HTML_COURSEWARE
        assertEquals(CourseType.HTML_COURSEWARE, CourseType.normalize("INTERACTIVE"));
        assertEquals(CourseType.VIDEO, CourseType.normalize("VIDEO"));
        assertEquals(CourseType.OFFLINE, CourseType.normalize("OFFLINE"));
    }

    @Test
    void testNormalizeNullAndUnknown() {
        assertNull(CourseType.normalize(null));
        assertNull(CourseType.normalize("INVALID"));
        assertNull(CourseType.normalize("EXERCISE_COURSE"));
    }

    @Test
    void testIsCoursewareType() {
        assertTrue(CourseType.isCoursewareType("HTML_COURSEWARE"));
        assertTrue(CourseType.isCoursewareType("PPT_COURSEWARE"));
        assertFalse(CourseType.isCoursewareType("VIDEO"));
        assertFalse(CourseType.isCoursewareType("OFFLINE"));
        assertTrue(CourseType.isCoursewareType(CourseType.HTML_COURSEWARE));
        assertTrue(CourseType.isCoursewareType(CourseType.PPT_COURSEWARE));
        assertFalse(CourseType.isCoursewareType(CourseType.VIDEO));
    }

    @Test
    void testIsValid() {
        assertTrue(CourseType.isValid("HTML_COURSEWARE"));
        assertTrue(CourseType.isValid("PPT_COURSEWARE"));
        assertTrue(CourseType.isValid("VIDEO"));
        assertTrue(CourseType.isValid("OFFLINE"));
        assertTrue(CourseType.isValid("INTERACTIVE")); // 旧值兼容
        assertFalse(CourseType.isValid("INVALID"));
    }

    @Test
    void testDisplayName() {
        assertEquals("HTML 课件", CourseType.HTML_COURSEWARE.getDisplayName());
        assertEquals("PPT 课件", CourseType.PPT_COURSEWARE.getDisplayName());
        assertEquals("VIDEO", CourseType.VIDEO.getDisplayName()); // 无显示名 → 回退 code
        assertEquals("HTML_COURSEWARE", CourseType.HTML_COURSEWARE.getCode());
    }
}
