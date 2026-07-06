# 数据字典 v0.5 → v0.6 (data-contract)

## Purpose

修复课程管理域 14 项数据字典漂移, 同步 Flyway migration SQL 的真实 schema。

## MODIFIED Requirements

### Requirement: courses 表 6 项 schema 同步

`docs/数据字典.md` 中 courses 表的索引与 CHECK 约束登记 MUST 与 Flyway migration V20/V60/V153 实际创建的索引与约束保持一致。freeDeptIds 字段类型 MUST 与 V110 后的实际类型 (TEXT) 一致。

#### Scenario: 索引 3 项补充登记
- WHEN 本变更完成
- THEN 数据字典 MUST 补充登记以下索引:
  - idx_courses_is_recommended (V20 创建)
  - idx_courses_teacher_deleted (V60 创建)
  - idx_courses_status_deleted (V60 创建)

#### Scenario: CHECK 约束 3 项补充登记
- WHEN 本变更完成
- THEN 数据字典 MUST 补充登记以下 CHECK 约束 (V153 创建):
  - chk_courses_status (status IN 0..6)
  - chk_courses_difficulty (difficulty IN 1..3)
  - chk_courses_course_type (course_type IN VIDEO/INTERACTIVE/OFFLINE)

#### Scenario: freeDeptIds 类型修正
- WHEN 本变更完成
- THEN 数据字典 freeDeptIds 字段类型 MUST 从 JSONB 改为 TEXT (V110 变更)

### Requirement: course_chapters 表移除已删约束

`docs/数据字典.md` 中 course_chapters 表 MUST 移除已删除的 uk_cc_course_sort 约束记录。

#### Scenario: uk_cc_course_sort 记录移除
- WHEN 本变更完成
- THEN 数据字典 MUST 移除 `uk_cc_course_sort` UNIQUE(course_id, sort_order) 记录
- AND 注释说明 V144 已删除此约束

### Requirement: course_prerequisites 表移除不存在字段

`docs/数据字典.md` 中 course_prerequisites 表 MUST 移除实际不存在的 deletedAt 字段记录。

#### Scenario: deletedAt 字段记录移除
- WHEN 本变更完成
- THEN 数据字典 MUST 移除 course_prerequisites.deletedAt 字段记录
- AND 注释说明 V80 未在 course_prerequisites 表添加 deletedAt

### Requirement: course_slides 表补充 chapterId 字段

`docs/数据字典.md` 中 course_slides 表 MUST 补充 V143 新增的 chapterId 字段。

#### Scenario: chapterId 字段登记
- WHEN 本变更完成
- THEN 数据字典 MUST 补充 course_slides.chapterId BIGINT (V143 新增)

### Requirement: course_review_logs 字段类型修正

`docs/数据字典.md` 中 course_review_logs 表的 previousStatus / newStatus 字段类型 MUST 从 INTEGER 改为 SMALLINT, 与 V* migration 一致。

#### Scenario: SMALLINT 类型登记
- WHEN 本变更完成
- THEN 数据字典 course_review_logs.previousStatus / newStatus 字段类型 MUST 从 INTEGER 改为 SMALLINT
- AND MUST 移除"长度 1"错误标注

### Requirement: videos 表 originalName 必填修正

`docs/数据字典.md` 中 videos.originalName 必填标注 MUST 与实际 SQL 一致。

#### Scenario: NOT NULL 状态说明
- WHEN 本变更完成
- THEN 数据字典 videos.originalName NOT NULL 标注 MUST 改为可空 (V25 仅改名未加 NOT NULL)

### Requirement: 索引/字段命名统一

`docs/数据字典.md` 中的索引与字段命名 MUST 与实际 SQL 一致。

#### Scenario: course_tag_relations 唯一索引命名
- WHEN 本变更完成
- THEN 数据字典唯一索引命名 `idx_ctr_unique` MUST 改为 `uk_ctr_course_tag`

#### Scenario: course_bundle_items.updatedAt DEFAULT 修正
- WHEN 本变更完成
- THEN 数据字典 updatedAt DEFAULT NULL MUST 改为无显式 DEFAULT (V130 实际 SQL)

### Requirement: 数据字典反向生成脚本与 CI 门禁

系统 MUST 提供从 Flyway migration SQL 自动生成数据字典的脚本, CI MUST 验证生成结果与手写 md 一致, 任何漂移 MUST 阻止合并。

#### Scenario: scripts/db-schema-doc-gen.sh 创建
- WHEN 本变更完成
- THEN 脚本 MUST 能从 Flyway migration SQL 逆向生成数据字典 Markdown
- AND 与 docs/数据字典.md diff, 不一致 → CI fail