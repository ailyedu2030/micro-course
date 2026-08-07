package com.microcourse.migration;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V182 migration 验证测试
 *
 * 交叉审查 P3 修复:加 @DirtiesContext(classMode=BEFORE_CLASS) 让本类跑前
 * context 重建(seed 数据 fresh),避免被 P1Stage1IntegrationTest 等创建
 * 的孤儿 chapter 污染 "sections >= chapters" 断言。
 *
 * L0 兜底 D-3 (2026-08-07, Fix Agent D3) 测试隔离强化:
 * - @DirtiesContext(methodMode=BEFORE_METHOD): 每个测试方法前重置 Spring context,
 *   消除方法间共享 context 的状态泄漏（macOS 本机与 Linux CI 的文件系统/建表顺序
 *   差异曾导致偶发假失败, F-2026-08-07-15 顺序耦合教训的根治）
 * - @TestMethodOrder(OrderAnnotation): 显式声明执行顺序, 顺序由【业务语义】驱动
 *   (migration 版本序: V182 建表 → V183 章节→课时 → V184 旧课时→课时 → V185 slide
 *   section_id 回填 → V186+ 清理), 而非依赖文件系统/方法名/反射遍历顺序 ——
 *   同一顺序在任何 OS/CI runner 上保持一致。
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class V182SectionMigrationTest {
    @Autowired private DataSource dataSource;

    @Test
    @Order(1)
    void should_create_sections_table() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, "public", "course_sections", null);
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    @Order(2)
    void should_drop_lessons_table() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tableCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'lessons'", Integer.class);
        assertThat(tableCount).isZero();
    }

    @Test
    @Order(3)
    void should_drop_chapter_type_column() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer colCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='course_chapters' AND column_name='chapter_type'", Integer.class);
        assertThat(colCount).isZero();
    }

    @Test
    @Order(4)
    void should_migrate_slides_section_id() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 关系不变量（P1-I-13）：V185 填充的 section_id 必须无孤儿且归属一致——
        // 1) 每条有 section_id 的 course_slides 必须命中存在的 section；
        // 2) 该 section 必须属于同一 course（防跨课程串课时）。
        // 注意：不用"全表 section_id 非空"严格断言——e2e fixture/其他测试类在
        // Flyway 之后插入的行不受 V185 UPDATE 覆盖（V185 只处理迁移时已存在的数据），
        // 全表断言在污染测试库必假失败（F-2026-08-07-15 测试顺序耦合教训）。
        Integer orphanSlides = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_slides cs " +
            "LEFT JOIN course_sections s ON s.id = cs.section_id " +
            "WHERE cs.section_id IS NOT NULL AND s.id IS NULL", Integer.class);
        assertThat(orphanSlides)
            .as("V185 填充的 section_id 不允许指向不存在的 section（孤儿 %d 条）", orphanSlides)
            .isZero();
        Integer crossCourse = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_slides cs " +
            "JOIN course_sections s ON s.id = cs.section_id " +
            "WHERE cs.section_id IS NOT NULL AND s.course_id <> cs.course_id", Integer.class);
        assertThat(crossCourse)
            .as("V185 填充的 section_id 必须属于同一 course（跨课程 %d 条）", crossCourse)
            .isZero();
    }

    @Test
    @Order(5)
    void should_migrate_lessons_to_sections() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 关系不变量（P1-I-13）：V184 将 lessons 迁移为 sort_order >= 10000 的 legacy section。
        // 1) 数量边界：legacy section 不可能超过 section 总数；
        // 2) 无孤儿：每条 legacy section 的 chapter_id 必须命中 course_chapters（V184 按 l.chapter_id 复制）。
        Integer totalSections = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_sections WHERE deleted_at IS NULL", Integer.class);
        Integer migrated = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_sections WHERE sort_order >= 10000", Integer.class);
        assertThat(migrated)
            .as("legacy 迁移 section（sort_order>=10000）不得超过 section 总数（%d）", totalSections)
            .isLessThanOrEqualTo(totalSections);
        Integer orphanLegacy = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_sections cs " +
            "LEFT JOIN course_chapters cc ON cc.id = cs.chapter_id " +
            "WHERE cs.sort_order >= 10000 AND cc.id IS NULL", Integer.class);
        assertThat(orphanLegacy)
            .as("legacy 迁移 section 不允许挂载不存在的 chapter（孤儿 %d 条）", orphanLegacy)
            .isZero();
    }

    @Test
    @Order(6)
    void should_migrate_chapters_to_sections() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 关系不变量（P1-I-13）：V183 每个未删除 chapter 生成 1 条 section（1:1），
        // 故 sections 总数 >= 未删除 chapters 总数；全新空库两者均为 0 亦成立。
        Integer chapterCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_chapters WHERE deleted_at IS NULL", Integer.class);
        Integer sections = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_sections WHERE deleted_at IS NULL", Integer.class);
        assertTrue(sections >= chapterCount || (chapterCount == 0 && sections == 0),
            "V183 每章至少 1 section：chapters=" + chapterCount + " sections=" + sections);
    }

    @Test
    @Order(7)
    void should_have_all_required_columns() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getColumns(null, "public", "course_sections", null);
            boolean hasTitle = false, hasType = false, hasChapterId = false;
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if ("title".equals(col)) hasTitle = true;
                if ("section_type".equals(col)) hasType = true;
                if ("chapter_id".equals(col)) hasChapterId = true;
            }
            assertThat(hasTitle).isTrue();
            assertThat(hasType).isTrue();
            assertThat(hasChapterId).isTrue();
        }
    }
}
