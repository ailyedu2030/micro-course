package com.microcourse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
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
