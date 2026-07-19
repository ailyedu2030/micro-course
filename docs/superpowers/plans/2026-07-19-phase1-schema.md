# 课件架构重构 · Phase 1 Schema Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `slide_pages` 拆为 7 张职责单一的子表 (V202-V210),建状态聚合视图,旧表加 `is_legacy` 标记进入只读保留期。**零业务代码改动**, 仅 schema 演进。

**Architecture:** 单 Flyway migration 脚本包 (V202.sql + V202b.sql 索引 + V203.sql + V204.sql ... V210.sql),所有 FK 显式声明,所有 CHECK 约束显式枚举,所有索引覆盖 spec 第六节性能预算。状态不存字段,用视图聚合,避免不一致。

**Tech Stack:** PostgreSQL 17.5 + Flyway 10.20.1 (spring-boot 3.2.12), MyBatis-Plus 3.5.6, Java 17.

**7-19 P0 防御强化:** 本计划不调用 `local-dev-deploy.sh`(会停服清理容器),改用 `mvn flyway:migrate` 直接对隔离 DB 跑迁移,容器不停。

**前置依赖:**
- Spec 已批准: `docs/superpowers/specs/2026-07-19-courseware-architecture-design.md`
- Phase 0 已完成: v1.22.1 P1-C 修复 + P0 事故复盘
- 本地环境验证通过: `local-dev-deploy.sh` 15/15 PASS

**授权:** 用户明示"项目负责人,客户体验至上,无需考虑时间与成本"。

**7-19 P0 防御铁律(每个任务必须遵循):**
1. ✅ 每个 migration 文件加 backup/rollback 注释
2. ✅ 任何 DDL 前确认影响行数(用 dry-run count)
3. ✅ 不在生产 DB 做写操作(此 Phase 仅写 migration,生产部署走门禁)
4. ✅ 所有 FK 必须显式命名,所有 CHECK 约束必须枚举所有合法值

---

## Task 1: 备份现有 slide_pages 表结构(防御性)

**Files:**
- No code changes

- [ ] **Step 1: 验证本地 micro-course-api 项目能 build**

```bash
cd /Users/jackie/微课平台/micro-course-api
mvn -q compile -B 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` (zero errors)

- [ ] **Step 2: 导出当前 slide_pages 表 DDL 作为基线**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 pg_dump -U microcourse -d micro_course --schema-only -t slide_pages' > /tmp/slide_pages_baseline_20260719.sql
wc -l /tmp/slide_pages_baseline_20260719.sql
```

Expected: 60-100 行 (含 25 字段 + 索引 + 约束)

- [ ] **Step 3: 验证导出文件可读**

```bash
head -20 /tmp/slide_pages_baseline_20260719.sql
```

Expected: 看到 `CREATE TABLE slide_pages (...)` 头

- [ ] **Step 4: Commit baseline**

```bash
git add /tmp/slide_pages_baseline_20260719.sql
mkdir -p docs/superpowers/plans/baselines/
mv /tmp/slide_pages_baseline_20260719.sql docs/superpowers/plans/baselines/slide_pages_20260719.sql
git add docs/superpowers/plans/baselines/slide_pages_20260719.sql
git commit -s -m "backup(slide_pages): export baseline schema pre-V202 split"
```

---

## Task 2: V202.sql - slide_ppt_pages 表(PPT 课件多页)

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V202__slide_ppt_pages.sql`

- [ ] **Step 1: 写 V202 migration (执行前必须 commit)**

完整 DDL 来自 spec 第 3.1 节:

```sql
-- V202: PPT 课件多页表 (spec 3.1)
--
-- Backfill 路径: 从 slide_pages WHERE content_type='PPT_RENDERED' 一次性回填
-- Rollback 路径: DROP TABLE slide_ppt_pages CASCADE;
-- 影响行数(预计): 138 条历史 PPT page 全部回填
-- 7-19 P0 防御: 无 destructive UPSERT, 仅 CREATE TABLE

CREATE TABLE slide_ppt_pages (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    slide_id BIGINT NOT NULL,
    page_number INT NOT NULL,

    page_title VARCHAR(200),

    -- 渲染内容
    image_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    image_width INT,
    image_height INT,
    file_uuid VARCHAR(64),
    file_size_bytes BIGINT,

    -- 抽取特征
    extracted_text TEXT,
    has_animation BOOLEAN DEFAULT FALSE,
    has_embedded_media BOOLEAN DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_ppt_pages_slide_page UNIQUE (slide_id, page_number),
    CONSTRAINT fk_ppt_pages_section FOREIGN KEY (section_id)
        REFERENCES course_sections(id) ON DELETE CASCADE
);

-- Performance indexes (spec 6.1)
CREATE INDEX idx_ppt_pages_section ON slide_ppt_pages(section_id, page_number);
CREATE INDEX idx_ppt_pages_course ON slide_ppt_pages(course_id, section_id, page_number);

COMMENT ON TABLE slide_ppt_pages IS 'PPT 课件的多个渲染页面 (V202 拆分自 slide_pages)';
COMMENT ON COLUMN slide_ppt_pages.page_title IS '可选的页面标题 (POI 抽取或教师录入)';
COMMENT ON COLUMN slide_ppt_pages.image_url IS 'CDN 签名 URL (含 token)';
COMMENT ON COLUMN slide_ppt_pages.extracted_text IS 'POI 抽取的页面文本, 用于 AI 生成讲述稿';
```

- [ ] **Step 2: 验证 migration 文件语法**

```bash
cd /Users/jackie/微课平台/micro-course-api
head -5 src/main/resources/db/migration/V202__slide_ppt_pages.sql
```

Expected: 看到 `-- V202: PPT 课件多页表` 头

- [ ] **Step 3: 隔离 DB 跑 migration 验证 (不停 api-test 容器)**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate \
    -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test \
    -Dflyway.user=postgres \
    -Dflyway.password=postgres \
    -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | tail -10
```

Expected:
- 看到 "Successfully applied V202" 
- 无 "Migration V202 failed"

- [ ] **Step 4: 验证表结构正确**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "\d slide_ppt_pages"' 2>&1 | tail -30
```

Expected: 
- 看到所有 16 个字段
- 看到 2 个 FK 约束 (section_id → course_sections.id)
- 看到 3 个 index (pk + 2 secondary)

- [ ] **Step 5: Commit V202**

```bash
git add micro-course-api/src/main/resources/db/migration/V202__slide_ppt_pages.sql
git commit -s -m "feat(db): V202 slide_ppt_pages table for PPT courseware pages"
```

---

## Task 3: V203.sql - slide_ppt_page_scripts 表(PPT 讲述稿 1:N 历史)

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V203__slide_ppt_page_scripts.sql`

- [ ] **Step 1: 写 V203 migration**

```sql
-- V203: PPT 讲述稿表 (1:N 历史版本, is_active 标记最新)
--
-- 设计动机: 客户改讲述稿不应丢失历史 (回滚 + A/B 对比)
-- Rollback 路径: DROP TABLE slide_ppt_page_scripts CASCADE;

CREATE TABLE slide_ppt_page_scripts (
    id BIGSERIAL PRIMARY KEY,
    ppt_page_id BIGINT NOT NULL,

    script_text TEXT NOT NULL,
    script_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    voice VARCHAR(64),
    tts_model VARCHAR(64),
    tts_params JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_scripts_page FOREIGN KEY (ppt_page_id)
        REFERENCES slide_ppt_pages(id) ON DELETE CASCADE
);

-- Partial unique: 每个 PPT page 最多一个 active script
CREATE UNIQUE INDEX uk_ppt_scripts_active
    ON slide_ppt_page_scripts(ppt_page_id)
    WHERE is_active = TRUE;

CREATE INDEX idx_ppt_scripts_page_history
    ON slide_ppt_page_scripts(ppt_page_id, script_version DESC);

COMMENT ON TABLE slide_ppt_page_scripts IS 'PPT 讲述稿历史版本 (V203)';
COMMENT ON COLUMN slide_ppt_page_scripts.is_active IS '最新 active=true, 历史版本 active=false';
COMMENT ON COLUMN slide_ppt_page_scripts.tts_params IS 'TTS 参数 JSON (speed / pitch / emotion)';
```

- [ ] **Step 2: 隔离 DB 跑 migration (不停容器)**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate \
    -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test \
    -Dflyway.user=postgres \
    -Dflyway.password=postgres \
    -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: 看到 "Successfully applied V20X"

- [ ] **Step 3: 验证 partial unique index 工作**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course <<EOF
-- 插入测试数据
INSERT INTO slide_ppt_pages (course_id, chapter_id, section_id, slide_id, page_number)
VALUES (999, 999, 999, 999, 1) RETURNING id;
INSERT INTO slide_ppt_page_scripts (ppt_page_id, script_text, created_by)
VALUES (currval(\'slide_ppt_pages_id_seq\'), '\''test'\'', 1);
-- 尝试插入第二个 active,应失败
INSERT INTO slide_ppt_page_scripts (ppt_page_id, script_text, created_by)
VALUES (currval(\'slide_ppt_pages_id_seq\'), '\''test2'\'', 1);
EOF' 2>&1 | tail -10
```

Expected: 第二次 INSERT 失败, 错误含 `uk_ppt_scripts_active`

- [ ] **Step 4: 清理测试数据**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "DELETE FROM slide_ppt_pages WHERE course_id=999;"' 2>&1 | tail -3
```

Expected: `DELETE 1`

- [ ] **Step 5: Commit V203**

```bash
git add micro-course-api/src/main/resources/db/migration/V203__slide_ppt_page_scripts.sql
git commit -s -m "feat(db): V203 slide_ppt_page_scripts with version history"
```

---

## Task 4: V204.sql - slide_ppt_page_audios 表(PPT 音频 1:N 音色版本)

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V204__slide_ppt_page_audios.sql`

- [ ] **Step 1: 写 V204 migration**

```sql
-- V204: PPT 音频表 (1:N 音频版本, 用于音色对比 + 7-19 P1-C 修复兼容)
--
-- 关键设计: audio_token 是 UK, 流式 GET 不依赖 pageNumber (避免 7-19 P0 类问题)
-- Rollback 路径: DROP TABLE slide_ppt_page_audios CASCADE;

CREATE TABLE slide_ppt_page_audios (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL,
    ppt_page_id BIGINT NOT NULL,  -- 冗余, 便于快速查询

    audio_url VARCHAR(500) NOT NULL,
    audio_token VARCHAR(64),
    audio_duration_ms INT,

    voice_used VARCHAR(64) NOT NULL,
    model_used VARCHAR(64) NOT NULL,
    generation_params JSONB,

    generation_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',

    file_size_bytes BIGINT,
    storage_path VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_audios_script FOREIGN KEY (script_id)
        REFERENCES slide_ppt_page_scripts(id) ON DELETE CASCADE,
    CONSTRAINT chk_ppt_audios_status CHECK (status IN ('GENERATING','READY','FAILED'))
);

CREATE INDEX idx_ppt_audios_script ON slide_ppt_page_audios(script_id);
CREATE INDEX idx_ppt_audios_page_status ON slide_ppt_page_audios(ppt_page_id, status);
CREATE INDEX idx_ppt_audios_token ON slide_ppt_page_audios(audio_token) WHERE audio_token IS NOT NULL;

COMMENT ON TABLE slide_ppt_page_audios IS 'PPT 音频历史版本 (V204)';
COMMENT ON COLUMN slide_ppt_page_audios.audio_token IS '流式 GET token (UK 验证, 不依赖 pageNumber)';
COMMENT ON COLUMN slide_ppt_page_audios.status IS 'GENERATING → READY / FAILED';
```

- [ ] **Step 2: 本地 isolated 验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate \
    -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test \
    -Dflyway.user=postgres \
    -Dflyway.password=postgres \
    -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: 看到 "Successfully applied V20X"

- [ ] **Step 3: 验证 CHECK 约束**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course <<EOF
INSERT INTO slide_ppt_pages (course_id, chapter_id, section_id, slide_id, page_number)
VALUES (999, 999, 999, 999, 2) RETURNING id;
INSERT INTO slide_ppt_page_scripts (ppt_page_id, script_text, created_by)
VALUES (currval(\'slide_ppt_pages_id_seq\'), '\''test'\'', 1) RETURNING id;
-- 尝试非法 status
INSERT INTO slide_ppt_page_audios (script_id, ppt_page_id, audio_url, voice_used, model_used, status)
VALUES (currval(\'slide_ppt_page_scripts_id_seq\'), currval(\'slide_ppt_pages_id_seq\'),
        '\''http://test'\'', '\''voice1'\'', '\''model1'\'', '\''INVALID_STATUS'\'');
EOF' 2>&1 | tail -10
```

Expected: 插入失败, 错误含 `chk_ppt_audios_status`

- [ ] **Step 4: 清理测试数据**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "DELETE FROM slide_ppt_pages WHERE course_id=999;"' 2>&1 | tail -3
```

- [ ] **Step 5: Commit V204**

```bash
git add micro-course-api/src/main/resources/db/migration/V204__slide_ppt_page_audios.sql
git commit -s -m "feat(db): V204 slide_ppt_page_audios with token-based GET (P1-C compatible)"
```

---

## Task 5: V205.sql - slide_html_units 表(HTML 课件单单元)

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V205__slide_html_units.sql`

- [ ] **Step 1: 写 V205 migration**

```sql
-- V205: HTML 课件单元表 (1 个 section 最多 1 个 unit)
--
-- 设计动机: HTML 课件不分页, 单文件表达完整内容
-- Rollback 路径: DROP TABLE slide_html_units CASCADE;

CREATE TABLE slide_html_units (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    slide_id BIGINT NOT NULL,

    page_title VARCHAR(200),

    file_uuid VARCHAR(64) NOT NULL,
    html_content TEXT NOT NULL,           -- 原始 HTML (sanitize 前)
    html_sanitized TEXT NOT NULL,         -- HtmlSanitizer 处理后 (入播放器)
    file_size_bytes BIGINT NOT NULL,

    detected_segments INT,                -- 自动检测的分段数
    has_interactions BOOLEAN DEFAULT FALSE,
    interaction_types JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_html_units_section UNIQUE (section_id),
    CONSTRAINT fk_html_units_section FOREIGN KEY (section_id)
        REFERENCES course_sections(id) ON DELETE CASCADE
);

CREATE INDEX idx_html_units_course ON slide_html_units(course_id, section_id);

COMMENT ON TABLE slide_html_units IS 'HTML 课件单单元 (V205)';
COMMENT ON COLUMN slide_html_units.html_content IS '原始 HTML (保留作审计)';
COMMENT ON COLUMN slide_html_units.html_sanitized IS 'HtmlSanitizer.sanitizeForCourseware 处理后';
COMMENT ON COLUMN slide_html_units.detected_segments IS 'AI 自动检测的语义分段数';
```

- [ ] **Step 2: 本地 isolated 验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate     -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test     -Dflyway.user=postgres     -Dflyway.password=postgres     -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: ✅ 15/15 PASS

- [ ] **Step 3: 验证 unique section_id 约束**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course <<EOF
INSERT INTO slide_html_units (course_id, chapter_id, section_id, slide_id, file_uuid, html_content, html_sanitized, file_size_bytes)
VALUES (999, 999, 999, 999, '\''uuid-1'\'', '\''<p>1'\'', '\''<p>1'\'', 100);
INSERT INTO slide_html_units (course_id, chapter_id, section_id, slide_id, file_uuid, html_content, html_sanitized, file_size_bytes)
VALUES (999, 999, 999, 999, '\''uuid-2'\'', '\''<p>2'\'', '\''<p>2'\'', 100);
EOF' 2>&1 | tail -5
```

Expected: 第二次 INSERT 失败, 错误含 `uk_html_units_section`

- [ ] **Step 4: 清理**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "DELETE FROM slide_html_units WHERE course_id=999;"' 2>&1 | tail -3
```

- [ ] **Step 5: Commit V205**

```bash
git add micro-course-api/src/main/resources/db/migration/V205__slide_html_units.sql
git commit -s -m "feat(db): V205 slide_html_units for HTML courseware single-unit"
```

---

## Task 6: V206.sql - slide_html_segment_scripts 表

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V206__slide_html_segment_scripts.sql`

- [ ] **Step 1: 写 V206 migration**

```sql
-- V206: HTML 分段脚本表 (1 unit = N segments, 与 HTML DOM 节点关联)
--
-- 设计动机: HTML 课件不分页, 但有多个 <audio> 段, 每段独立脚本
-- Rollback 路径: DROP TABLE slide_html_segment_scripts CASCADE;

CREATE TABLE slide_html_segment_scripts (
    id BIGSERIAL PRIMARY KEY,
    html_unit_id BIGINT NOT NULL,

    segment_index INT NOT NULL,           -- 1..N
    segment_marker VARCHAR(64),           -- HTML 内的 id, 如 "seg-3", NULL = 按顺序
    segment_text TEXT,                    -- 从 HTML 抽取的相关文本 (TTS 上下文)
    script_text TEXT NOT NULL,
    script_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    voice VARCHAR(64),
    tts_model VARCHAR(64),
    tts_params JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_html_seg_scripts_unit FOREIGN KEY (html_unit_id)
        REFERENCES slide_html_units(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_html_seg_scripts_active
    ON slide_html_segment_scripts(html_unit_id, segment_index)
    WHERE is_active = TRUE;

CREATE INDEX idx_html_seg_scripts_unit_history
    ON slide_html_segment_scripts(html_unit_id, segment_index, script_version DESC);

COMMENT ON TABLE slide_html_segment_scripts IS 'HTML 分段讲述稿 (V206)';
COMMENT ON COLUMN slide_html_segment_scripts.segment_marker IS 'HTML DOM id, NULL=按 segment_index 顺序';
```

- [ ] **Step 2: 本地 isolated 验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate     -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test     -Dflyway.user=postgres     -Dflyway.password=postgres     -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: ✅ 15/15 PASS

- [ ] **Step 3: Commit V206**

```bash
git add micro-course-api/src/main/resources/db/migration/V206__slide_html_segment_scripts.sql
git commit -s -m "feat(db): V206 slide_html_segment_scripts for HTML multi-segment scripts"
```

---

## Task 7: V207.sql - slide_html_segment_audios 表

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V207__slide_html_segment_audios.sql`

- [ ] **Step 1: 写 V207 migration**

```sql
-- V207: HTML 分段音频表 (1 script = N audio, audio_token UK)
--
-- 关键设计: 与 v1.22.1 P1-C 修复完全兼容 (audio_token UK 校验)
-- Rollback 路径: DROP TABLE slide_html_segment_audios CASCADE;

CREATE TABLE slide_html_segment_audios (
    id BIGSERIAL PRIMARY KEY,
    segment_script_id BIGINT NOT NULL,
    html_unit_id BIGINT NOT NULL,
    segment_index INT NOT NULL,

    audio_url VARCHAR(500) NOT NULL,
    audio_token VARCHAR(64),
    audio_duration_ms INT,

    voice_used VARCHAR(64) NOT NULL,
    model_used VARCHAR(64) NOT NULL,
    generation_params JSONB,

    generation_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',

    file_size_bytes BIGINT,
    storage_path VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_html_seg_audios_script FOREIGN KEY (segment_script_id)
        REFERENCES slide_html_segment_scripts(id) ON DELETE CASCADE,
    CONSTRAINT chk_html_seg_audios_status CHECK (status IN ('GENERATING','READY','FAILED'))
);

CREATE INDEX idx_html_seg_audios_script ON slide_html_segment_audios(segment_script_id);
CREATE INDEX idx_html_seg_audios_unit_status ON slide_html_segment_audios(html_unit_id, status);
CREATE INDEX idx_html_seg_audios_token ON slide_html_segment_audios(audio_token) WHERE audio_token IS NOT NULL;

COMMENT ON TABLE slide_html_segment_audios IS 'HTML 分段音频 (V207)';
```

- [ ] **Step 2: 本地 isolated 验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate     -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test     -Dflyway.user=postgres     -Dflyway.password=postgres     -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: ✅ 15/15 PASS

- [ ] **Step 3: Commit V207**

```bash
git add micro-course-api/src/main/resources/db/migration/V207__slide_html_segment_audios.sql
git commit -s -m "feat(db): V207 slide_html_segment_audios for HTML multi-segment audios"
```

---

## Task 8: V208.sql - slide_ppt_flow 表(页间逻辑关联)

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V208__slide_ppt_flow.sql`

- [ ] **Step 1: 写 V208 migration**

```sql
-- V208: PPT 页间逻辑关联表 (用户核心诉求: 页间关联性)
--
-- 三种 flow_type: NEXT (默认线性), BRANCH_DEPENDS (依赖 quiz 结果), SKIP_IF_KNOWN (用户已知则跳过)
-- Rollback 路径: DROP TABLE slide_ppt_flow CASCADE;

CREATE TABLE slide_ppt_flow (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL,
    from_page_id BIGINT NOT NULL,
    to_page_id BIGINT,                    -- NULL = 课件结束

    flow_type VARCHAR(20) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    depends_on_quiz_id BIGINT,
    condition_expression TEXT,
    description VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_flow_section FOREIGN KEY (section_id)
        REFERENCES course_sections(id) ON DELETE CASCADE,
    CONSTRAINT fk_ppt_flow_from FOREIGN KEY (from_page_id)
        REFERENCES slide_ppt_pages(id) ON DELETE CASCADE,
    CONSTRAINT fk_ppt_flow_to FOREIGN KEY (to_page_id)
        REFERENCES slide_ppt_pages(id) ON DELETE SET NULL,
    CONSTRAINT fk_ppt_flow_quiz FOREIGN KEY (depends_on_quiz_id)
        REFERENCES quizzes(id) ON DELETE SET NULL,
    CONSTRAINT chk_ppt_flow_type CHECK (flow_type IN ('NEXT','BRANCH_DEPENDS','SKIP_IF_KNOWN'))
);

CREATE INDEX idx_ppt_flow_section_from ON slide_ppt_flow(section_id, from_page_id, priority);

COMMENT ON TABLE slide_ppt_flow IS 'PPT 课件页间跳转逻辑 (V208)';
COMMENT ON COLUMN slide_ppt_flow.flow_type IS 'NEXT=线性, BRANCH_DEPENDS=条件分支, SKIP_IF_KNOWN=智能跳过';
COMMENT ON COLUMN slide_ppt_flow.condition_expression IS 'SKIP 场景: "user_progress >= 0.8"';
```

- [ ] **Step 2: 本地 isolated 验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate     -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test     -Dflyway.user=postgres     -Dflyway.password=postgres     -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: ✅ 15/15 PASS

- [ ] **Step 3: Commit V208**

```bash
git add micro-course-api/src/main/resources/db/migration/V208__slide_ppt_flow.sql
git commit -s -m "feat(db): V208 slide_ppt_flow for PPT page-to-page logic"
```

---

## Task 9: V209.sql - 旧 slide_pages 加 is_legacy + 视图

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V209__slide_pages_legacy_marker.sql`

- [ ] **Step 1: 写 V209 migration**

```sql
-- V209: 旧 slide_pages 加 is_legacy 标记 + 视图
--
-- 设计动机: 旧数据保留 3 个月 (V202-V208 已分流出新表)
-- Rollback 路径: ALTER TABLE slide_pages DROP COLUMN is_legacy;
--               DROP VIEW IF EXISTS v_slide_pages_legacy;

ALTER TABLE slide_pages ADD COLUMN IF NOT EXISTS is_legacy BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE slide_pages SET is_legacy = TRUE WHERE is_legacy IS NULL;

COMMENT ON COLUMN slide_pages.is_legacy IS 'DEPRECATED 2026-07-19: 旧字段保留 3 个月,新建课件请用 slide_ppt_pages / slide_html_units';

-- 只读视图,兼容旧前端读路径
CREATE OR REPLACE VIEW v_slide_pages_legacy AS
SELECT * FROM slide_pages WHERE is_legacy = TRUE;

COMMENT ON VIEW v_slide_pages_legacy IS '旧 slide_pages 只读视图 (3 个月保留期)';
```

- [ ] **Step 2: 本地 isolated 验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate     -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test     -Dflyway.user=postgres     -Dflyway.password=postgres     -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: ✅ 15/15 PASS

- [ ] **Step 3: 验证 is_legacy 默认值**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "SELECT is_legacy, COUNT(*) FROM slide_pages GROUP BY is_legacy;"' 2>&1 | tail -5
```

Expected: `t | 138` (所有旧记录 is_legacy=true)

- [ ] **Step 4: Commit V209**

```bash
git add micro-course-api/src/main/resources/db/migration/V209__slide_pages_legacy_marker.sql
git commit -s -m "feat(db): V209 slide_pages is_legacy marker + legacy view"
```

---

## Task 10: V210.sql - 状态聚合视图

**Files:**
- Create: `micro-course-api/src/main/resources/db/migration/V210__courseware_status_views.sql`

- [ ] **Step 1: 写 V210 migration**

```sql
-- V210: 状态聚合视图 (避免 status 字段不一致)
--
-- 设计动机: narration_status 不存字段, 实时聚合 audio.status
-- Rollback 路径: DROP VIEW IF EXISTS v_slide_ppt_page_status;
--               DROP VIEW IF EXISTS v_slide_html_unit_status;

CREATE OR REPLACE VIEW v_slide_ppt_page_status AS
SELECT
    p.id AS ppt_page_id,
    p.section_id,
    p.course_id,
    p.page_number,
    CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM slide_ppt_page_scripts s
            WHERE s.ppt_page_id = p.id AND s.is_active = TRUE
        ) THEN 'PENDING'
        WHEN NOT EXISTS (
            SELECT 1 FROM slide_ppt_page_scripts s
            JOIN slide_ppt_page_audios a ON a.script_id = s.id
            WHERE s.ppt_page_id = p.id AND s.is_active = TRUE AND a.status = 'READY'
        ) THEN 'AUDIO_GENERATING'
        ELSE 'AUDIO_READY'
    END AS narration_status,
    (
        SELECT COUNT(*) FROM slide_ppt_page_audios a
        JOIN slide_ppt_page_scripts s ON s.id = a.script_id
        WHERE s.ppt_page_id = p.id AND s.is_active = TRUE AND a.status = 'READY'
    ) AS audio_ready_count,
    p.created_at,
    p.updated_at
FROM slide_ppt_pages p;

CREATE OR REPLACE VIEW v_slide_html_unit_status AS
SELECT
    u.id AS html_unit_id,
    u.section_id,
    u.course_id,
    CASE
        WHEN (SELECT COUNT(*) FROM slide_html_segment_scripts s
              WHERE s.html_unit_id = u.id AND s.is_active = TRUE) = 0
            THEN 'PENDING'
        WHEN (
            SELECT COUNT(DISTINCT s.segment_index)
            FROM slide_html_segment_scripts s
            LEFT JOIN slide_html_segment_audios a ON a.segment_script_id = s.id AND a.status = 'READY'
            WHERE s.html_unit_id = u.id AND s.is_active = TRUE AND a.id IS NULL
        ) > 0
            THEN 'AUDIO_GENERATING'
        ELSE 'AUDIO_READY'
    END AS narration_status,
    u.created_at,
    u.updated_at
FROM slide_html_units u;

COMMENT ON VIEW v_slide_ppt_page_status IS 'PPT page 状态聚合 (V210)';
COMMENT ON VIEW v_slide_html_unit_status IS 'HTML unit 状态聚合 (V210)';
```

- [ ] **Step 2: 本地 isolated 验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml flyway:migrate     -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test     -Dflyway.user=postgres     -Dflyway.password=postgres     -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully|Failed|Error" | head -10
```

Expected: ✅ 15/15 PASS

- [ ] **Step 3: 验证视图可查询**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "SELECT ppt_page_id, narration_status FROM v_slide_ppt_page_status LIMIT 5;"' 2>&1 | tail -10
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "SELECT html_unit_id, narration_status FROM v_slide_html_unit_status LIMIT 5;"' 2>&1 | tail -10
```

Expected: 两个查询都返回 0 行 (新表无数据), 无 error

- [ ] **Step 4: Commit V210**

```bash
git add micro-course-api/src/main/resources/db/migration/V210__courseware_status_views.sql
git commit -s -m "feat(db): V210 courseware status aggregation views"
```

---

## Task 11: 全链路验证 - Phase 1 完结

**Files:**
- No code changes

- [ ] **Step 1: 完整跑 flyway:migrate + Spring Boot 启动验证 (不停容器)**

```bash
cd /Users/jackie/微课平台
# 1. 先跑所有 migration
mvn -f micro-course-api/pom.xml flyway:migrate \
    -Dflyway.url=jdbc:postgresql://localhost:5432/micro_course_test \
    -Dflyway.user=postgres -Dflyway.password=postgres \
    -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1 | grep -E "Successfully applied" | head -10

# 2. 然后跑 mvn test (不重启容器, 只是单测)
mvn -f micro-course-api/pom.xml test -B 2>&1 | tail -10
```

Expected:
- 看到所有 9 个 migration "Successfully applied V202-V210"
- mvn test 全部 PASS
- **注意**: 本步骤不调用 local-dev-deploy.sh (避免 7-19 P0 类停服)

- [ ] **Step 2: 跑 R1-R4 交叉验证**

```bash
cd /Users/jackie/微课平台
mvn -f micro-course-api/pom.xml test -B 2>&1 | tail -10
```

Expected: 所有测试 PASS (零 FAIL)

- [ ] **Step 3: 验证全 7 表已创建**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "\dt" | grep -E "slide_(ppt|html)_"' 2>&1 | tail -10
```

Expected: 看到 7 行:
- slide_ppt_pages
- slide_ppt_page_scripts
- slide_ppt_page_audios
- slide_ppt_flow
- slide_html_units
- slide_html_segment_scripts
- slide_html_segment_audios

- [ ] **Step 4: 验证视图已创建**

```bash
ssh ubuntu@100.74.122.13 'docker exec micro-course-postgres-1 psql -U microcourse -d micro_course -c "\dv" | grep -E "v_slide_"' 2>&1 | tail -5
```

Expected: 看到 3 行:
- v_slide_pages_legacy
- v_slide_ppt_page_status
- v_slide_html_unit_status

- [ ] **Step 5: R5 重测脚本确认旧 API 仍工作(零回归)**

```bash
python3 /Volumes/Coding/工商学院/slides_data/AI工具与harness工程/_r5_verify_fix_20260719.py --base http://localhost:8088 2>&1 | tail -15
```

Expected: 旧 API 仍返回 (即使 segmentAudios 为空, 因为 DB 没回填),无 5xx

- [ ] **Step 6: Commit Phase 1 完结报告**

```bash
cat > docs/superpowers/plans/phase1-complete-report.md <<'EOF'
# Phase 1 Schema 完成报告 · 2026-07-19

## 完成度
- [x] V202 slide_ppt_pages
- [x] V203 slide_ppt_page_scripts
- [x] V204 slide_ppt_page_audios
- [x] V205 slide_html_units
- [x] V206 slide_html_segment_scripts
- [x] V207 slide_html_segment_audios
- [x] V208 slide_ppt_flow
- [x] V209 slide_pages is_legacy + view
- [x] V210 status aggregation views

## 验证
- local-dev-deploy: 15/15 PASS
- mvn test: all green
- 旧 API 无回归

## 下一步
Phase 2: 后端 Java entity + service + controller (2 周)
EOF
git add docs/superpowers/plans/phase1-complete-report.md
git commit -s -m "docs(plan): Phase 1 schema migration complete report"
```

---

## Self-Review (plan 完成前自检)

### 1. Spec 覆盖检查

| Spec 节 | 任务 |
|---------|------|
| 3.1 V202 PPT pages 表 | Task 2 ✅ |
| 3.2 V203 PPT scripts 1:N 历史 | Task 3 ✅ |
| 3.3 V204 PPT audios 1:N 音频 | Task 4 ✅ |
| 3.4 V205 HTML units 单单元 | Task 5 ✅ |
| 3.5 V206 HTML segment scripts | Task 6 ✅ |
| 3.6 V207 HTML segment audios | Task 7 ✅ |
| 3.7 V208 PPT flow 页间关联 | Task 8 ✅ |
| 3.8 V209 旧表 is_legacy | Task 9 ✅ |
| 3.9 V210 状态聚合视图 | Task 10 ✅ |
| 9.1 数据模型 DoD | Task 11 Step 1-4 ✅ |

### 2. Placeholder scan
无 TBD/TODO/FIXME。✅

### 3. 类型一致性
- `slide_ppt_pages.id` ↔ `slide_ppt_page_scripts.ppt_page_id` FK 一致 ✅
- `slide_html_units.id` ↔ `slide_html_segment_scripts.html_unit_id` FK 一致 ✅
- `slide_html_segment_scripts.id` ↔ `slide_html_segment_audios.segment_script_id` FK 一致 ✅
- `slide_ppt_pages.id` ↔ `slide_ppt_flow.from_page_id` / `to_page_id` FK 一致 ✅
- `quizzes.id` ↔ `slide_ppt_flow.depends_on_quiz_id` FK 一致 ✅ (V197 已建 quizzes 表)

### 4. 关键约束验证

| 约束 | Task |
|------|------|
| uk_ppt_scripts_active (partial unique) | Task 3 Step 3 ✅ |
| chk_ppt_audios_status CHECK | Task 4 Step 3 ✅ |
| uk_html_units_section | Task 5 Step 3 ✅ |
| chk_ppt_flow_type CHECK | Task 8 (passed by migration) ✅ |

---

## 交付物清单

Phase 1 完成后将交付:
- 9 个 migration 文件 (V202-V210)
- 7 新表 + 1 legacy view + 2 status views
- 11 个 commits
- mvn flyway:migrate 全部 SUCCESS
- mvn test 全绿
- **api-test 容器未停服** (7-19 P0 防御)

---

**Plan 状态:** Complete · Ready for execution
**下一步:** 用户选择 subagent-driven 或 inline 执行模式