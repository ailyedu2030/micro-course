package com.microcourse.service;

import com.microcourse.entity.MicroSpecialtyEnrollment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Phase 14 — G2 修复：pendingCourses 字段单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>entity 字段读写</li>
 *   <li>字段允许 null（默认空）</li>
 *   <li>JSON 字符串可任意序列化形式存储</li>
 * </ul>
 *
 * @author Phase14-Development-Team
 * @since 2026-06-23
 */
@DisplayName("Phase 14 G2 MicroSpecialtyEnrollment.pendingCourses 字段")
class MicroSpecialtyPendingCoursesTest {

    @Test
    @DisplayName("默认：pendingCourses 为 null（数据库默认空数组 JSONB）")
    void defaultNull() {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        assertNull(en.getPendingCourses());
    }

    @Test
    @DisplayName("读写：可设置并读回 JSON 字符串")
    void setAndGet() {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        String json = "[{\"courseId\":101,\"courseName\":\"微积分\",\"reason\":\"前置未通过\"}]";
        en.setPendingCourses(json);
        assertEquals(json, en.getPendingCourses());
        assertNotNull(en.getPendingCourses());
    }

    @Test
    @DisplayName("读写：可设置为空数组 JSON '[]'")
    void emptyArray() {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        en.setPendingCourses("[]");
        assertEquals("[]", en.getPendingCourses());
    }

    @Test
    @DisplayName("读写：可设置多条 pending 课程")
    void multiplePending() {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        String json = "[" +
                "{\"courseId\":101,\"courseName\":\"微积分\",\"reason\":\"前置未通过\"}," +
                "{\"courseId\":102,\"courseName\":\"线性代数\",\"reason\":\"容量已满\"}" +
                "]";
        en.setPendingCourses(json);
        assertEquals(2, en.getPendingCourses().split("\\{").length - 1,
                "应包含 2 条 pending 课程");
    }
}
