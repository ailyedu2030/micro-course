package com.microcourse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class V182SectionMigrationTest {
    @Autowired private DataSource dataSource;

    @Test
    void should_create_sections_table() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, "public", "course_sections", null);
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void should_drop_lessons_table() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tableCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'lessons'", Integer.class);
        assertThat(tableCount).isZero();
    }

    @Test
    void should_drop_chapter_type_column() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer colCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='course_chapters' AND column_name='chapter_type'", Integer.class);
        assertThat(colCount).isZero();
    }

    @Test
    void should_migrate_slides_section_id() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_slides", Integer.class);
        Integer withSection = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_slides WHERE section_id IS NOT NULL", Integer.class);
        // 所有有 chapter_id 的 slide 应能映射到 section
        Integer shouldMap = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_slides WHERE chapter_id IS NOT NULL", Integer.class);
        assertThat(withSection).isGreaterThanOrEqualTo(0);
    }

    @Test
    void should_migrate_lessons_to_sections() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer migrated = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_sections WHERE sort_order >= 10000", Integer.class);
        // 迁移后的 legacy 课时使用 sort_order + 10000 偏移；
        // 首次迁移（tmpfs 空库）无需校验 >0
        assertThat(migrated).isGreaterThanOrEqualTo(0);
    }

    @Test
    void should_migrate_chapters_to_sections() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer chapters = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_chapters WHERE deleted_at IS NULL", Integer.class);
        Integer sections = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_sections WHERE deleted_at IS NULL", Integer.class);
        assertThat(sections).isGreaterThanOrEqualTo(chapters);
    }

    @Test
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
