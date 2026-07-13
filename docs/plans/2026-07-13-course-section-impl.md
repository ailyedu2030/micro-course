# Course Section Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign the course architecture from `Course → Chapter (with chapter_type)` to `Course → Chapter → Section (课时 with section_type)`. Each Section is an independent lesson of one of four types (VIDEO/INTERACTIVE/OFFLINE/EXERCISE). Existing data auto-migrates 1:1 with no manual intervention.

**Architecture:** Single Flyway migration V182 creates `course_sections` table, copies all existing chapters as sections (1:1), then migrates all existing lessons as additional sections in the same chapter. Drops `lessons` table and `chapter_type` column. Backend gets new `Section` entity/service/controller + DTOs. Frontend gets new tree-based section UI replacing flat chapter list. One-release backward compat layer preserves old `chapter_type` reads.

**Tech Stack:** Spring Boot 3.2.12, MyBatis-Plus 3.5.6, PostgreSQL 17, Flyway, Vue 3.4 + Element Plus 2.5

---

## Phase 0: Backup & Preparation

### Task 1: Create production data backup tables

**Files:**
- Modify: `micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql` (start the file)

**Step 1:** Create the migration file with backup tables and section table creation

Run: `mkdir -p micro-course-api/src/main/resources/db/migration`
Expected: directory created

**Step 2:** Verify Flyway version number V182 is unused

Run: `ls micro-course-api/src/main/resources/db/migration/V18*`
Expected: shows V180, V181 (so V182 is free)

---

## Phase 1: Database Migration (V182)

### Task 2: Create course_sections table

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql`

**Step 1:** Write the failing test

Create: `micro-course-api/src/test/java/com/microcourse/migration/V182SectionMigrationTest.java`

```java
package com.microcourse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class V182SectionMigrationTest {
    @Autowired private DataSource dataSource;

    @Test
    void should_create_sections_table() throws Exception {
        try (var conn = dataSource.getConnection()) {
            var rs = conn.getMetaData().getTables(null, "public", "course_sections", null);
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void should_have_one_section_per_chapter() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer chapters = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_chapters WHERE deleted_at IS NULL", Integer.class);
        Integer sections = jdbc.queryForObject(
            "SELECT COUNT(*) FROM course_sections WHERE deleted_at IS NULL", Integer.class);
        assertThat(sections).isGreaterThanOrEqualTo(chapters);
    }
}
```

**Step 2:** Run test, expect FAIL

Run: `cd micro-course-api && mvn test -Dtest=V182SectionMigrationTest -q 2>&1 | tail -20`
Expected: FAIL (V182 not applied yet)

**Step 3:** Write the migration SQL

Create: `micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql`

```sql
-- V182: 课程章节课时三层重构
-- 步骤 1: 备份表（迁移前）
CREATE TABLE IF NOT EXISTS course_chapters_pre_v182 AS 
    SELECT * FROM course_chapters;
CREATE TABLE IF NOT EXISTS lessons_pre_v182 AS 
    SELECT * FROM lessons;
CREATE TABLE IF NOT EXISTS course_slides_pre_v182 AS 
    SELECT * FROM course_slides;
CREATE TABLE IF NOT EXISTS slide_pages_pre_v182 AS 
    SELECT * FROM slide_pages;

-- 步骤 2: 创建 course_sections 表
CREATE TABLE course_sections (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL REFERENCES course_chapters(id),
    course_id BIGINT NOT NULL REFERENCES courses(id),
    title VARCHAR(200) NOT NULL,
    section_type VARCHAR(20) NOT NULL 
        CHECK (section_type IN ('VIDEO','INTERACTIVE','OFFLINE','EXERCISE')),
    sort_order INTEGER NOT NULL DEFAULT 0,
    duration INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    script_content TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_cs_chapter ON course_sections(chapter_id);
CREATE INDEX idx_cs_course ON course_sections(course_id);
CREATE INDEX idx_cs_type ON course_sections(section_type);
CREATE INDEX idx_cs_chapter_sort ON course_sections(chapter_id, sort_order);
```

**Step 4:** Run test, expect PASS

Run: `cd micro-course-api && mvn test -Dtest=V182SectionMigrationTest -q 2>&1 | tail -10`
Expected: 2 tests passed

**Step 5:** Commit

```bash
git add micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql
git add micro-course-api/src/test/java/com/microcourse/migration/V182SectionMigrationTest.java
git commit -m "feat(v182): create course_sections table"
```

---

### Task 3: Migrate chapters to sections (1:1)

**Files:**
- Modify: `micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql`

**Step 1:** Add the chapter→section migration SQL

Modify the V182 file to add at the end:

```sql
-- 步骤 3: 迁移 chapters → sections (1:1)
-- 每个 chapter (含 chapter_type) 生成一条对应 section
INSERT INTO course_sections
    (chapter_id, course_id, title, section_type, sort_order, duration,
     visible, description, version, created_at, updated_at)
SELECT
    cc.id, cc.course_id, cc.title,
    COALESCE(cc.chapter_type, 'VIDEO'),
    cc.sort_order, cc.duration, cc.visible,
    cc.description, 1, cc.created_at, cc.updated_at
FROM course_chapters cc
WHERE cc.deleted_at IS NULL;
```

**Step 2:** Run all tests, expect PASS

Run: `cd micro-course-api && mvn test -Dtest=V182SectionMigrationTest -q 2>&1 | tail -10`
Expected: all tests passed (chapter→section 1:1 verified)

**Step 3:** Commit

```bash
git add micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql
git commit -m "feat(v182): migrate chapters to sections 1:1"
```

---

### Task 4: Migrate lessons to sections

**Files:**
- Modify: `micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql`

**Step 1:** Add lessons→sections migration

Append to V182 file:

```sql
-- 步骤 4: 迁移 lessons → sections
-- lessons 附加到对应 chapter 的 section 后（sort_order 偏移确保不冲突）
INSERT INTO course_sections
    (chapter_id, course_id, title, section_type, sort_order, duration,
     visible, version, created_at, updated_at)
SELECT
    l.chapter_id, l.course_id, l.title,
    COALESCE(l.lesson_type, 'VIDEO'),
    10000 + COALESCE(l.sort_order, 0),
    l.duration, COALESCE(l.visible, true), l.version,
    l.created_at, l.updated_at
FROM lessons l
WHERE l.deleted_at IS NULL;
```

**Step 2:** Add verification test

Modify `V182SectionMigrationTest.java` add:

```java
@Test
void should_migrate_lessons_as_sections() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer originalLessons = jdbc.queryForObject(
        "SELECT COUNT(*) FROM lessons_pre_v182", Integer.class);
    Integer lessonsMigrated = jdbc.queryForObject(
        "SELECT COUNT(*) FROM course_sections WHERE sort_order >= 10000", Integer.class);
    assertThat(lessonsMigrated).isEqualTo(originalLessons);
}
```

**Step 3:** Run test, expect PASS

Run: `cd micro-course-api && mvn test -Dtest=V182SectionMigrationTest -q 2>&1 | tail -10`
Expected: 3 tests passed

**Step 4:** Commit

```bash
git add micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql
git add micro-course-api/src/test/java/com/microcourse/migration/V182SectionMigrationTest.java
git commit -m "feat(v182): migrate lessons to sections"
```

---

### Task 5: Update course_slides.lesson_id → section_id

**Files:**
- Modify: `micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql`

**Step 1:** Add slides migration

Append to V182 file:

```sql
-- 步骤 5: 迁移 course_slides.lesson_id → section_id
-- 通过 chapter_id + 偏移量匹配到正确的 section
UPDATE course_slides cs
SET section_id = (
    SELECT cs2.id FROM course_sections cs2
    WHERE cs2.chapter_id = cs.chapter_id
    AND cs2.sort_order = (
        CASE
            WHEN cs.lesson_id IS NULL THEN cs.sort_order
            ELSE 10000 + cs.lesson_id
        END
    )
    LIMIT 1
)
WHERE section_id IS NULL;

-- 同样迁移 slide_pages.lesson_id → section_id
UPDATE slide_pages sp
SET lesson_id = (
    SELECT cs.id FROM course_sections cs
    JOIN lessons l ON l.chapter_id = cs.chapter_id AND l.id = sp.lesson_id
    WHERE l.sort_order + 10000 = cs.sort_order
    LIMIT 1
)
WHERE lesson_id IS NOT NULL;
```

**Step 2:** Add verification test

Add to test file:

```java
@Test
void should_preserve_all_slides() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer totalSlides = jdbc.queryForObject(
        "SELECT COUNT(*) FROM course_slides_pre_v182", Integer.class);
    Integer migratedSlides = jdbc.queryForObject(
        "SELECT COUNT(*) FROM course_slides WHERE section_id IS NOT NULL", Integer.class);
    assertThat(migratedSlides).isEqualTo(totalSlides);
}
```

**Step 3:** Run test, expect PASS

Run: `cd micro-course-api && mvn test -Dtest=V182SectionMigrationTest -q 2>&1 | tail -10`
Expected: 4 tests passed

**Step 4:** Commit

```bash
git add micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql
git add micro-course-api/src/test/java/com/microcourse/migration/V182SectionMigrationTest.java
git commit -m "feat(v182): migrate slides lesson_id to section_id"
```

---

### Task 6: Drop lessons table and chapter_type column

**Files:**
- Modify: `micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql`

**Step 1:** Add cleanup SQL

Append to V182 file:

```sql
-- 步骤 6: 清理
ALTER TABLE slide_pages RENAME COLUMN lesson_id TO section_id;
ALTER TABLE course_slides DROP COLUMN lesson_id;
ALTER TABLE course_chapters DROP COLUMN chapter_type;
DROP TABLE lessons CASCADE;
DROP TABLE lessons_pre_v182;

-- 步骤 7: 更新约束
DROP INDEX IF EXISTS uk_slides_course_lesson;
DROP INDEX IF EXISTS uk_sp_course_lesson_page;
CREATE UNIQUE INDEX uk_slides_course_section 
    ON course_slides(course_id, section_id);
CREATE UNIQUE INDEX uk_sp_course_section_page 
    ON slide_pages(course_id, section_id, page_number);
```

**Step 2:** Run all tests, expect PASS

Run: `cd micro-course-api && mvn test -Dtest=V182SectionMigrationTest -q 2>&1 | tail -10`
Expected: all 4 tests passed

**Step 3:** Commit

```bash
git add micro-course-api/src/main/resources/db/migration/V182__create_course_sections.sql
git commit -m "feat(v182): drop lessons table and chapter_type column"
```

---

### Task 7: Verify migration on production DB

**Files:**
- Modify: none (verification only)

**Step 1:** Backup current production DB

Run: `ssh ubuntu@100.74.122.13 "docker exec micro-course-postgres-1 pg_dump -U microcourse -d micro_course -t course_chapters lessons course_slides slide_pages > /tmp/pre_v182_backup.sql"`
Expected: backup file created in container

**Step 2:** Deploy new JAR with V182 to production

Run: `cd micro-course-api && mvn package -DskipTests -B -q`
Expected: JAR built

**Step 3:** Restart container

Run: `scp target/micro-course-api-1.0.0.jar ubuntu@100.74.122.13:/opt/micro-course/`
Run: `ssh ubuntu@100.74.122.13 "docker stop micro-course-micro-course-api-1 && docker rm micro-course-micro-course-api-1 && docker run -d --name micro-course-micro-course-api-1 --network micro-course_default -p 127.0.0.1:8081:8080 -e UPLOAD_BASE_DIR=/app/uploads -v /opt/micro-course/config/ssl:/etc/nginx/ssl:ro -v /opt/micro-course/micro-course-api-1.0.0.jar:/app/app.jar:ro --restart unless-stopped micro-course-micro-course-api:fontfix"`
Expected: container restarted

**Step 4:** Wait for healthy and verify migration

Run: `sleep 30 && ssh ubuntu@100.74.122.13 "docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c 'SELECT (SELECT COUNT(*) FROM course_sections) as sections, (SELECT COUNT(*) FROM course_chapters) as chapters, (SELECT COUNT(*) FROM course_slides WHERE section_id IS NOT NULL) as slides'"`
Expected: sections ≥ chapters, all slides have section_id

**Step 5:** Verify no errors in container logs

Run: `ssh ubuntu@100.74.122.13 "docker logs micro-course-micro-course-api-1 --tail 30 | grep -i 'error\|exception' | head -5"`
Expected: no errors

**Step 6:** Commit

```bash
git commit --allow-empty -m "chore(v182): deploy migration to production"
```

---

## Phase 2: Backend API

### Task 8: Create Section entity

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/entity/CourseSection.java`

**Step 1:** Write the entity

Create: `micro-course-api/src/main/java/com/microcourse/entity/CourseSection.java`

```java
package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("course_sections")
public class CourseSection {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("course_id")
    private Long courseId;

    private String title;

    @TableField("section_type")
    private String sectionType;

    @TableField("sort_order")
    private Integer sortOrder;

    private Integer duration;
    private Boolean visible;
    private String description;

    @TableField("script_content")
    private String scriptContent;

    @Version
    private Integer version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableLogic(value = "NULL", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
```

**Step 2:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/entity/CourseSection.java
git commit -m "feat: add CourseSection entity"
```

---

### Task 9: Create SectionRepository

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/repository/CourseSectionRepository.java`

**Step 1:** Create repository

```java
package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseSection;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseSectionRepository extends BaseMapper<CourseSection> {
}
```

**Step 2:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/repository/CourseSectionRepository.java
git commit -m "feat: add CourseSectionRepository"
```

---

### Task 10: Create Section DTOs

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/dto/SectionDTO.java`
- Create: `micro-course-api/src/main/java/com/microcourse/dto/SectionCreateRequest.java`
- Create: `micro-course-api/src/main/java/com/microcourse/dto/SectionUpdateRequest.java`

**Step 1:** Create SectionDTO

```java
package com.microcourse.dto;

import java.time.LocalDateTime;

public class SectionDTO {
    private Long id;
    private Long chapterId;
    private Long courseId;
    private String title;
    private String sectionType;
    private Integer sortOrder;
    private Integer duration;
    private Boolean visible;
    private String description;
    private String scriptContent;
    private Integer slideCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }
    public Integer getSlideCount() { return slideCount; }
    public void setSlideCount(Integer slideCount) { this.slideCount = slideCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

**Step 2:** Create SectionCreateRequest

```java
package com.microcourse.dto;

import jakarta.validation.constraints.*;

public class SectionCreateRequest {
    @NotBlank @Size(max = 200)
    private String title;

    @NotBlank @Pattern(regexp = "VIDEO|INTERACTIVE|OFFLINE|EXERCISE")
    private String sectionType;

    @Min(0)
    private Integer sortOrder = 0;

    @Min(0)
    private Integer duration = 0;

    private Boolean visible = true;

    @Size(max = 2000)
    private String description;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

**Step 3:** Create SectionUpdateRequest (same fields, all optional)

```java
package com.microcourse.dto;

import jakarta.validation.constraints.*;

public class SectionUpdateRequest {
    @Size(max = 200)
    private String title;

    @Pattern(regexp = "VIDEO|INTERACTIVE|OFFLINE|EXERCISE")
    private String sectionType;

    @Min(0)
    private Integer sortOrder;

    @Min(0)
    private Integer duration;

    private Boolean visible;

    @Size(max = 2000)
    private String description;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
```

**Step 4:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/dto/SectionDTO.java
git add micro-course-api/src/main/java/com/microcourse/dto/SectionCreateRequest.java
git add micro-course-api/src/main/java/com/microcourse/dto/SectionUpdateRequest.java
git commit -m "feat: add Section DTOs (DTO/CreateRequest/UpdateRequest)"
```

---

### Task 11: Add Section ErrorCodes

**Files:**
- Modify: `micro-course-api/src/main/java/com/microcourse/exception/ErrorCode.java`

**Step 1:** Find the ErrorCode enum and find a free range

Run: `grep "SECTION\\|CHAPTER\\|LESSON" micro-course-api/src/main/java/com/microcourse/exception/ErrorCode.java | head -5`
Expected: see existing codes around chapter/lesson

**Step 2:** Add section error codes

Find the line with the highest current code (e.g. `CHAPTER_NOT_FOUND(5000)`) and add after it:

```java
SECTION_NOT_FOUND(5001, "Section 不存在"),
SECTION_DUPLICATE_TITLE(5002, "Section 标题在同章节内重复"),
SECTION_TYPE_INVALID(5003, "Section 类型非法"),
SECTION_HAS_SLIDES(5004, "Section 存在课件，无法删除"),
SECTION_CHAPTER_NOT_FOUND(5005, "父章节不存在"),
```

**Step 3:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/exception/ErrorCode.java
git commit -m "feat: add Section error codes (5001-5005)"
```

---

### Task 12: Create SectionService interface

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/service/SectionService.java`

**Step 1:** Create service interface

```java
package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.SectionCreateRequest;
import com.microcourse.dto.SectionDTO;
import com.microcourse.dto.SectionUpdateRequest;

public interface SectionService {
    PageResult<SectionDTO> listByChapter(Long chapterId, int page, int size);
    SectionDTO getById(Long id);
    SectionDTO create(Long chapterId, SectionCreateRequest request);
    SectionDTO update(Long id, SectionUpdateRequest request);
    void delete(Long id, boolean force);
}
```

**Step 2:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/service/SectionService.java
git commit -m "feat: add SectionService interface"
```

---

### Task 13: Create SectionServiceImpl

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/service/impl/SectionServiceImpl.java`

**Step 1:** Write the failing test

Create: `micro-course-api/src/test/java/com/microcourse/service/SectionServiceImplTest.java`

```java
package com.microcourse.service;

import com.microcourse.dto.SectionCreateRequest;
import com.microcourse.dto.SectionDTO;
import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSlideRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {
    @Mock private CourseSectionRepository sectionRepo;
    @Mock private CourseChapterRepository chapterRepo;
    @Mock private CourseRepository courseRepo;
    @Mock private CourseSlideRepository slideRepo;
    @InjectMocks private SectionServiceImpl service;

    @Test
    void should_create_section() {
        SectionCreateRequest req = new SectionCreateRequest();
        req.setTitle("Test");
        req.setSectionType("VIDEO");

        when(sectionRepo.insert(any())).thenReturn(1);
        when(sectionRepo.selectById(any())).thenReturn(new CourseSection());
        when(chapterRepo.selectById(1L)).thenReturn(new com.microcourse.entity.CourseChapter());

        SectionDTO result = service.create(1L, req);
        assertThat(result).isNotNull();
    }

    @Test
    void should_throw_on_invalid_type() {
        SectionCreateRequest req = new SectionCreateRequest();
        req.setTitle("Test");
        req.setSectionType("INVALID");

        assertThatThrownBy(() -> service.create(1L, req))
            .isInstanceOf(BusinessException.class);
    }
}
```

**Step 2:** Run test, expect FAIL (no impl)

Run: `cd micro-course-api && mvn test -Dtest=SectionServiceImplTest -q 2>&1 | tail -5`
Expected: FAIL (compilation error, SectionServiceImpl not found)

**Step 3:** Write the implementation

```java
package com.microcourse.service.impl;

import com.microcourse.dto.*;
import com.microcourse.entity.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.*;
import com.microcourse.service.SectionService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SectionServiceImpl implements SectionService {
    private final CourseSectionRepository sectionRepo;
    private final CourseChapterRepository chapterRepo;
    private final CourseRepository courseRepo;
    private final CourseSlideRepository slideRepo;

    public SectionServiceImpl(CourseSectionRepository sectionRepo,
                              CourseChapterRepository chapterRepo,
                              CourseRepository courseRepo,
                              CourseSlideRepository slideRepo) {
        this.sectionRepo = sectionRepo;
        this.chapterRepo = chapterRepo;
        this.courseRepo = courseRepo;
        this.slideRepo = slideRepo;
    }

    @Override
    public PageResult<SectionDTO> listByChapter(Long chapterId, int page, int size) {
        List<CourseSection> sections = sectionRepo.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseSection>()
                .eq(CourseSection::getChapterId, chapterId)
                .orderByAsc(CourseSection::getSortOrder)
        );
        List<SectionDTO> dtos = sections.stream().map(this::toDTO).collect(Collectors.toList());
        PageResult<SectionDTO> result = new PageResult<>();
        result.setItems(dtos);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(dtos.size());
        return result;
    }

    @Override
    public SectionDTO getById(Long id) {
        CourseSection section = sectionRepo.selectById(id);
        if (section == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        return toDTO(section);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SectionDTO create(Long chapterId, SectionCreateRequest req) {
        validateSectionType(req.getSectionType());
        CourseChapter chapter = chapterRepo.selectById(chapterId);
        if (chapter == null) throw new BusinessException(ErrorCode.SECTION_CHAPTER_NOT_FOUND);
        verifyCourseOwnership(chapter.getCourseId());

        CourseSection section = new CourseSection();
        section.setChapterId(chapterId);
        section.setCourseId(chapter.getCourseId());
        section.setTitle(req.getTitle());
        section.setSectionType(req.getSectionType());
        section.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        section.setDuration(req.getDuration() != null ? req.getDuration() : 0);
        section.setVisible(req.getVisible() != null ? req.getVisible() : true);
        section.setDescription(req.getDescription());
        section.setVersion(1);
        section.setCreatedAt(java.time.LocalDateTime.now());
        section.setUpdatedAt(java.time.LocalDateTime.now());
        sectionRepo.insert(section);
        return getById(section.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SectionDTO update(Long id, SectionUpdateRequest req) {
        CourseSection section = sectionRepo.selectById(id);
        if (section == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        verifyCourseOwnership(section.getCourseId());
        if (req.getTitle() != null) section.setTitle(req.getTitle());
        if (req.getSectionType() != null) {
            validateSectionType(req.getSectionType());
            section.setSectionType(req.getSectionType());
        }
        if (req.getSortOrder() != null) section.setSortOrder(req.getSortOrder());
        if (req.getDuration() != null) section.setDuration(req.getDuration());
        if (req.getVisible() != null) section.setVisible(req.getVisible());
        if (req.getDescription() != null) section.setDescription(req.getDescription());
        section.setUpdatedAt(java.time.LocalDateTime.now());
        sectionRepo.updateById(section);
        return toDTO(section);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, boolean force) {
        CourseSection section = sectionRepo.selectById(id);
        if (section == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        verifyCourseOwnership(section.getCourseId());
        if (!force) {
            Integer slideCount = slideRepo.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.CourseSlide>()
                    .eq(com.microcourse.entity.CourseSlide::getSectionId, id));
            if (slideCount != null && slideCount > 0) {
                throw new BusinessException(ErrorCode.SECTION_HAS_SLIDES);
            }
        }
        sectionRepo.deleteById(id);
    }

    private void validateSectionType(String type) {
        if (type == null || !type.matches("VIDEO|INTERACTIVE|OFFLINE|EXERCISE")) {
            throw new BusinessException(ErrorCode.SECTION_TYPE_INVALID);
        }
    }

    private void verifyCourseOwnership(Long courseId) {
        Course course = courseRepo.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private SectionDTO toDTO(CourseSection section) {
        SectionDTO dto = new SectionDTO();
        dto.setId(section.getId());
        dto.setChapterId(section.getChapterId());
        dto.setCourseId(section.getCourseId());
        dto.setTitle(section.getTitle());
        dto.setSectionType(section.getSectionType());
        dto.setSortOrder(section.getSortOrder());
        dto.setDuration(section.getDuration());
        dto.setVisible(section.getVisible());
        dto.setDescription(section.getDescription());
        dto.setScriptContent(section.getScriptContent());
        dto.setCreatedAt(section.getCreatedAt());
        dto.setUpdatedAt(section.getUpdatedAt());
        return dto;
    }
}
```

**Step 4:** Run test, expect PASS

Run: `cd micro-course-api && mvn test -Dtest=SectionServiceImplTest -q 2>&1 | tail -5`
Expected: PASS

**Step 5:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/service/impl/SectionServiceImpl.java
git add micro-course-api/src/test/java/com/microcourse/service/SectionServiceImplTest.java
git commit -m "feat: add SectionServiceImpl with full CRUD"
```

---

### Task 14: Create SectionController

**Files:**
- Create: `micro-course-api/src/main/java/com/microcourse/controller/SectionController.java`

**Step 1:** Write controller

```java
package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.service.SectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/chapters/{chapterId}/sections")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class SectionController {
    private final SectionService sectionService;

    public SectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @GetMapping
    public R<PageResult<SectionDTO>> list(@PathVariable Long courseId, @PathVariable Long chapterId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return R.ok(sectionService.listByChapter(chapterId, page, size));
    }

    @GetMapping("/{id}")
    public R<SectionDTO> getById(@PathVariable Long id) {
        return R.ok(sectionService.getById(id));
    }

    @PostMapping
    public R<SectionDTO> create(@PathVariable Long chapterId, @jakarta.validation.Valid @RequestBody SectionCreateRequest request) {
        return R.ok(sectionService.create(chapterId, request));
    }

    @PutMapping("/{id}")
    public R<SectionDTO> update(@PathVariable Long id, @jakarta.validation.Valid @RequestBody SectionUpdateRequest request) {
        return R.ok(sectionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean force) {
        sectionService.delete(id, force);
        return R.ok();
    }
}
```

**Step 2:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/controller/SectionController.java
git commit -m "feat: add SectionController with full CRUD endpoints"
```

---

### Task 15: Update ChapterVO to include sections

**Files:**
- Modify: `micro-course-api/src/main/java/com/microcourse/dto/ChapterVO.java`

**Step 1:** Find the current ChapterVO

Run: `grep -n "private\\|class ChapterVO" micro-course-api/src/main/java/com/microcourse/dto/ChapterVO.java | head -10`

**Step 2:** Add sections field

Add at the end of the field list (before getter/setter methods):

```java
private List<SectionDTO> sections = new java.util.ArrayList<>();
```

Add getter/setter:

```java
public List<SectionDTO> getSections() { return sections; }
public void setSections(List<SectionDTO> sections) { this.sections = sections; }
```

**Step 3:** Add backward compat field for chapter_type (returns null)

```java
@Deprecated
private String chapterType;
```

With deprecated accessors:

```java
@Deprecated
public String getChapterType() { return null; }
@Deprecated
public void setChapterType(String chapterType) { /* no-op for compat */ }
```

**Step 4:** Commit

```bash
git add micro-course-api/src/main/java/com/microcourse/dto/ChapterVO.java
git commit -m "feat: ChapterVO includes sections list (backward compat chapter_type returns null)"
```

---

## Phase 3: Frontend (Vue)

### Task 16: Create section API client

**Files:**
- Create: `micro-course-admin/src/api/section.js`

**Step 1:** Create section API

```javascript
import request from '@/utils/request'

export function listSections(courseId, chapterId, params) {
  return request({ method: 'GET', url: `/courses/${courseId}/chapters/${chapterId}/sections`, params })
}

export function getSection(courseId, chapterId, sectionId) {
  return request({ method: 'GET', url: `/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}` })
}

export function createSection(courseId, chapterId, data) {
  return request({ method: 'POST', url: `/courses/${courseId}/chapters/${chapterId}/sections`, data })
}

export function updateSection(courseId, chapterId, sectionId, data) {
  return request({ method: 'PUT', url: `/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}`, data })
}

export function deleteSection(courseId, chapterId, sectionId, force = false) {
  return request({ 
    method: 'DELETE', 
    url: `/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}`,
    params: force ? { force: true } : {}
  })
}
```

**Step 2:** Commit

```bash
git add micro-course-admin/src/api/section.js
git commit -m "feat: add section API client"
```

---

### Task 17: Create SectionList component

**Files:**
- Create: `micro-course-admin/src/components/course/SectionList.vue`

**Step 1:** Create component (using Element Plus)

```vue
<template>
  <div class="section-list">
    <div v-for="section in sections" :key="section.id" class="section-item">
      <el-row :gutter="12" align="middle">
        <el-col :span="1">
          <el-tag :type="typeTag(section.sectionType)" size="small">
            {{ typeIcon(section.sectionType) }}
          </el-tag>
        </el-col>
        <el-col :span="6">
          <span class="section-title">{{ section.title }}</span>
        </el-col>
        <el-col :span="3">
          <span class="section-duration">{{ section.duration ? `${section.duration}分钟` : '-' }}</span>
        </el-col>
        <el-col :span="3">
          <el-tag size="small" :type="section.slideCount > 0 ? 'success' : 'info'">
            {{ section.slideCount > 0 ? `有课件` : '无课件' }}
          </el-tag>
        </el-col>
        <el-col :span="11" style="text-align: right">
          <el-button size="small" @click="$emit('upload', section)">课件</el-button>
          <el-button size="small" @click="$emit('edit', section)">编辑</el-button>
          <el-button size="small" type="danger" @click="$emit('delete', section)">删除</el-button>
        </el-col>
      </el-row>
    </div>
    <el-empty v-if="sections.length === 0" description="暂无课时，点击新增添加" />
  </div>
</template>

<script setup>
defineProps({
  sections: { type: Array, default: () => [] }
})
defineEmits(['upload', 'edit', 'delete'])

const typeIcon = (t) => ({ VIDEO: '📹', INTERACTIVE: '🎯', OFFLINE: '🏫', EXERCISE: '📝' }[t] || '')
const typeTag = (t) => ({ VIDEO: 'primary', INTERACTIVE: 'success', OFFLINE: 'info', EXERCISE: 'warning' }[t] || '')
</script>

<style scoped>
.section-item { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.section-title { font-weight: 500; }
.section-duration { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
```

**Step 2:** Commit

```bash
git add micro-course-admin/src/components/course/SectionList.vue
git commit -m "feat: add SectionList component"
```

---

### Task 18: Create SectionEditDialog

**Files:**
- Create: `micro-course-admin/src/components/course/SectionEditDialog.vue`

**Step 1:** Create dialog component

```vue
<template>
  <el-dialog v-model="visible" :title="title" width="500px" @close="handleClose">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="类型" prop="sectionType">
        <el-select v-model="form.sectionType" class="full-width">
          <el-option label="📹 视频课" value="VIDEO" />
          <el-option label="🎯 互动课件" value="INTERACTIVE" />
          <el-option label="🏫 线下课" value="OFFLINE" />
          <el-option label="📝 练习" value="EXERCISE" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item label="时长(分钟)">
        <el-input-number v-model="form.duration" :min="0" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({ modelValue: Boolean, section: Object, isEdit: Boolean })
const emit = defineEmits(['update:modelValue', 'submit'])

const visible = ref(props.modelValue)
const formRef = ref(null)
const title = ref(props.isEdit ? '编辑课时' : '新增课时')

const form = reactive({
  title: '', sectionType: 'VIDEO', sortOrder: 0, duration: 0, description: ''
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  sectionType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

watch(() => props.modelValue, (v) => { visible.value = v })
watch(() => props.section, (s) => {
  if (s) Object.assign(form, { title: s.title, sectionType: s.sectionType, sortOrder: s.sortOrder, duration: s.duration, description: s.description })
}, { immediate: true })

const handleClose = () => { emit('update:modelValue', false) }

const handleSubmit = async () => {
  await formRef.value.validate()
  emit('submit', { ...form })
  handleClose()
}
</script>
```

**Step 2:** Commit

```bash
git add micro-course-admin/src/components/course/SectionEditDialog.vue
git commit -m "feat: add SectionEditDialog component"
```

---

### Task 19: Refactor CourseDetail.vue to use SectionList

**Files:**
- Modify: `micro-course-admin/src/views/courses/CourseDetail.vue`

**Step 1:** Find the chapter table

Run: `grep -n "el-table.*chapterData\\|v-for.*chapter\\|章节管理" micro-course-admin/src/views/courses/CourseDetail.vue | head -10`

**Step 2:** Replace flat chapter list with section-aware expandable

Wrap the chapter row in an expandable section. Add this import at top:

```javascript
import SectionList from '@/components/course/SectionList.vue'
import SectionEditDialog from '@/components/course/SectionEditDialog.vue'
import { listSections, createSection, updateSection, deleteSection } from '@/api/section'
```

Add to script setup:

```javascript
const sectionsByChapterId = ref({})
const currentChapterForSection = ref(null)
const showSectionEditDialog = ref(false)
const editingSection = ref(null)
const isEditSection = ref(false)

const loadSections = async (chapterId) => {
  const res = await listSections(courseId.value, chapterId, { page: 0, size: 100 })
  sectionsByChapterId.value[chapterId] = res.data?.items || []
}

const handleAddSection = (chapter) => {
  currentChapterForSection.value = chapter
  editingSection.value = null
  isEditSection.value = false
  showSectionEditDialog.value = true
}

const handleEditSection = (section) => {
  editingSection.value = section
  currentChapterForSection.value = { id: section.chapterId }
  isEditSection.value = true
  showSectionEditDialog.value = true
}

const handleDeleteSection = async (section) => {
  try {
    await ElMessageBox.confirm(`确定删除课时「${section.title}」？`, '确认删除', { type: 'warning' })
    await deleteSection(courseId.value, section.chapterId, section.id)
    ElMessage.success('删除成功')
    await loadSections(section.chapterId)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleSubmitSection = async (form) => {
  try {
    if (isEditSection.value) {
      await updateSection(courseId.value, currentChapterForSection.value.id, editingSection.value.id, form)
      ElMessage.success('更新成功')
    } else {
      await createSection(courseId.value, currentChapterForSection.value.id, form)
      ElMessage.success('创建成功')
    }
    showSectionEditDialog.value = false
    await loadSections(currentChapterForSection.value.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
```

In the template, modify the chapter row to add expandable section list. Replace the chapter-actions cell with a button "管理课时" that toggles the section tree, and inject `<SectionList>` below each expanded chapter.

**Step 3:** Commit

```bash
git add micro-course-admin/src/views/courses/CourseDetail.vue
git commit -m "feat: CourseDetail shows section tree per chapter"
```

---

### Task 20: Update ChapterList.vue to remove chapter_type

**Files:**
- Modify: `micro-course-admin/src/views/courses/ChapterList.vue`

**Step 1:** Find chapter_type references

Run: `grep -n "chapterType\\|chapter_type\\|EXERCISE\\|OFFLINE\\|INTERACTIVE" micro-course-admin/src/views/courses/ChapterList.vue`

**Step 2:** Remove chapter_type field from form options and tag display

In the form `<el-form-item label="类型" prop="chapterType">` block, replace with a read-only "课时数" display. In the table column showing chapter type tags, remove that column and add a "课时数" column.

**Step 3:** Commit

```bash
git add micro-course-admin/src/views/courses/ChapterList.vue
git commit -m "refactor: remove chapter_type from ChapterList (use section count instead)"
```

---

### Task 21: Build and deploy frontend

**Files:**
- Modify: none (build only)

**Step 1:** Build frontend

Run: `cd micro-course-admin && rm -rf dist node_modules/.vite && npx vite build --mode production 2>&1 | tail -3`
Expected: "built in X.XXs"

**Step 2:** Deploy to production

Run: `scp dist/index.html ubuntu@100.74.122.13:/tmp/`
Run: `scp -r dist/assets ubuntu@100.74.122.13:/tmp/`
Run: `ssh ubuntu@100.74.122.13 "docker exec micro-course-micro-course-admin-1 rm -rf /usr/share/nginx/html/assets /usr/share/nginx/html/index.html && docker exec micro-course-micro-course-admin-1 mkdir -p /usr/share/nginx/html/assets && docker cp /tmp/index.html micro-course-micro-course-admin-1:/usr/share/nginx/html/ && docker cp /tmp/assets/. micro-course-micro-course-admin-1:/usr/share/nginx/html/assets/"`
Expected: deploy ok

**Step 3:** Verify in browser

Run: navigate to `https://microcourse.ailyedu.cn/teacher/courses/{id}` and check section tree renders.

**Step 4:** Commit

```bash
git commit --allow-empty -m "chore: deploy section redesign frontend"
```

---

## Phase 4: Testing & Verification

### Task 22: Add SectionService integration test

**Files:**
- Create: `micro-course-api/src/test/java/com/microcourse/integration/SectionApiIntegrationTest.java`

**Step 1:** Write integration test

```java
package com.microcourse.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.SectionCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class SectionApiIntegrationTest {
    @Autowired private WebApplicationContext wac;
    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @org.junit.jupiter.api.BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(wac).build(); }

    @Test
    void should_reject_invalid_section_type() throws Exception {
        SectionCreateRequest req = new SectionCreateRequest();
        req.setTitle("Test");
        req.setSectionType("INVALID");
        String body = om.writeValueAsString(req);
        mvc.perform(post("/api/courses/1/chapters/1/sections")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void should_reject_empty_title() throws Exception {
        SectionCreateRequest req = new SectionCreateRequest();
        req.setTitle("");
        req.setSectionType("VIDEO");
        String body = om.writeValueAsString(req);
        mvc.perform(post("/api/courses/1/chapters/1/sections")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is4xxClientError());
    }
}
```

**Step 2:** Run test, expect PASS

Run: `cd micro-course-api && mvn test -Dtest=SectionApiIntegrationTest -q 2>&1 | tail -10`
Expected: 2 tests passed

**Step 3:** Commit

```bash
git add micro-course-api/src/test/java/com/microcourse/integration/SectionApiIntegrationTest.java
git commit -m "test: add Section API integration tests"
```

---

### Task 23: Add frontend E2E test

**Files:**
- Create: `micro-course-admin/tests/e2e/course-section-flow.spec.ts`

**Step 1:** Write E2E test (using Playwright)

```typescript
import { test, expect } from '@playwright/test'

test('admin can create a section in a chapter', async ({ page }) => {
  await page.goto('/login')
  await page.fill('[name="username"]', 'admin')
  await page.fill('[name="password"]', 'admin')
  await page.click('button[type="submit"]')
  await page.waitForURL(/\/admin/)
  
  // Navigate to course detail
  await page.goto('/admin/courses/42')
  
  // Click on chapter to expand
  await page.click('text=阶段1 工具安装')
  
  // Verify section tree is visible
  await expect(page.locator('text=视频课')).toBeVisible()
})
```

**Step 2:** Run E2E test

Run: `cd micro-course-admin && npx playwright test tests/e2e/course-section-flow.spec.ts`
Expected: 1 test passed

**Step 3:** Commit

```bash
git add micro-course-admin/tests/e2e/course-section-flow.spec.ts
git commit -m "test: add E2E test for course-section flow"
```

---

### Task 24: Final verification on production

**Files:**
- Modify: none (verification only)

**Step 1:** Verify migration succeeded

Run: `ssh ubuntu@100.74.122.13 "docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c 'SELECT (SELECT COUNT(*) FROM course_sections WHERE deleted_at IS NULL) as sections, (SELECT COUNT(*) FROM course_chapters WHERE deleted_at IS NULL) as chapters'"`
Expected: sections > 0, sections >= chapters

**Step 2:** Verify API endpoints work

Run: `curl -s "https://microcourse.ailyedu.cn/api/courses/42/chapters/67/sections" -H "Authorization: Bearer $(curl -s -X POST https://microcourse.ailyedu.cn/api/auth/login -H 'Content-Type: application/json' -d '{"username":"sytafe","password":"sy122708"}' | python3 -c 'import sys,json;print(json.load(sys.stdin).get(\"data\",{}).get(\"accessToken\",\"\"))')"`
Expected: `{"code":200,"message":"ok","data":{"items":[...],...}}`

**Step 3:** Verify frontend renders section tree

Navigate to `https://microcourse.ailyedu.cn/teacher/courses/42` and verify the section tree shows under each chapter.

**Step 4:** Commit final state

```bash
git tag v182-section-redesign -m "Course Section Redesign deployed"
git commit --allow-empty -m "chore: v182 section redesign complete and verified"
```

---

## Summary

Total: 24 tasks across 4 phases, ~200 steps. Implementation order:

1. **Phase 0** (1 task) — prep
2. **Phase 1** (6 tasks) — DB migration
2. **Phase 2** (8 tasks) — backend API
3. **Phase 3** (6 tasks) — frontend
4. **Phase 4** (3 tasks) — testing & verification

Estimated total time: 2-3 days for experienced developer with codebase familiarity.
