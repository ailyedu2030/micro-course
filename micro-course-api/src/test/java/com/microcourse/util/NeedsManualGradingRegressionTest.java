package com.microcourse.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-4 回归测试: 待批改必须用独立布尔列 needsManualGrading,
 * 禁止改回 JSON like 全表扫描(修复 #4 的锁)。
 *
 * 通过文件名 + reflective 验证锁定关键 Service 方法签名存在。
 */
@DisplayName("P0-4 待批改查询必须用 needsManualGrading 列")
class NeedsManualGradingRegressionTest {

    @Test
    @DisplayName("锁定: GradeServiceImpl.getPendingReview 必须存在")
    void mustHaveGetPendingReview() throws Exception {
        Class<?> clazz = Class.forName("com.microcourse.service.impl.GradeServiceImpl");
        boolean hasMethod = false;
        for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals("getPendingReview")) {
                hasMethod = true;
                break;
            }
        }
        assertTrue(hasMethod, "GradeServiceImpl.getPendingReview 必须存在");
    }

    @Test
    @DisplayName("锁定: ExerciseRecord entity 必须有 needsManualGrading 字段")
    void recordEntityMustHaveField() throws Exception {
        Class<?> clazz = Class.forName("com.microcourse.entity.ExerciseRecord");
        boolean hasField = false;
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals("needsManualGrading")) {
                hasField = true;
                break;
            }
        }
        assertTrue(hasField, "ExerciseRecord.needsManualGrading 字段必须存在(V138 migration)");
    }

    @Test
    @DisplayName("锁定: V138 migration 必须包含 needs_manual_grading 列")
    void migrationV138MustExist() {
        java.io.File f = new java.io.File("src/main/resources/db/migration/V138__add_needs_manual_grading_to_exercise_records.sql");
        assertTrue(f.exists(), "V138 migration 文件必须存在");
    }
}
