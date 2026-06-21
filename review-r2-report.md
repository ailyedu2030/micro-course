# R2 审查报告 — DB迁移 vs 数据字典 全量穷举审查

## 审查范围
- **审查维度**：R2 — DB迁移 vs 数据字典
- **文件覆盖**：
  - 68 个 Flyway migration SQL 文件（V1~V68）
  - `docs/数据字典.md` v0.5（43 张表 + 附录）
  - 56 个 Entity 类
- **审查日期**：2026-06-22

---

## 问题清单

### P0 — 阻塞项（必须修复）

| # | 文件:行号 | 问题 | 修复建议 |
|---|----------|------|---------|
| 1 | `V2__operation_logs.sql:19` + `V39__fix_operation_logs.sql:15` | **operation_logs 存在两个同名异列字段 `success` 和 `is_success`**。V2 创建 `success BOOLEAN NOT NULL DEFAULT TRUE`，V39 追加 `is_success BOOLEAN`。OperationLog.java 的 `@TableField("is_success") private Boolean success` 指向 `is_success`，导致 `success` 列形同虚设/数据分裂。表中存在两列表示同一概念，写入时 Entity 只用 `is_success` 而 `success` 列始终为 NULL/DEFAULT，触发数据不一致。 | 删除 V2 创建的 `success` 列（迁移脚本中加 `ALTER TABLE operation_logs DROP COLUMN success`），统一使用 `is_success`，确保 Entity → DB 映射一对一。 |
| 2 | `V5__gate2_videos.sql:11` vs `数据字典.md:400` | **videos.file_size 缺少 NOT NULL 约束**。数据字典定义为 `NOT NULL`，但 V5 建表时写为 `file_size BIGINT DEFAULT 0` 未加 NOT NULL，允许 NULL 值入库。 | 执行 `ALTER TABLE videos ALTER COLUMN file_size SET NOT NULL` 补齐约束（先处理存量 NULL 数据）。 |
| 3 | `数据字典.md §2.1 (course_categories)` vs `V3__gate2_courses.sql:14` | **course_categories.updated_at 字段在数据字典中不存在但 DB 中存在**。V3 创建了 `updated_at` 列，数据字典 §2.1 的字段表中未列出该字段，但索引声明中无提及。这是一个文档与实现的偏差——数据字典缺少该字段的定义。 | 在数据字典 §2.1 course_categories 表中追加 `updatedAt \| updated_at \| LocalDateTime \| string \| - \| NOT NULL \| 更新时间` 字段行。 |

---

### P1 — 建议修复（强烈建议）

| # | 文件:行号 | 问题 | 修复建议 |
|---|----------|------|---------|
| 1 | 多文件 | **13 张 DB 表存在于 migrations 但未在数据字典中登记**：`grades`(V21), `badges`(V18), `banners`(V42), `course_slides`(V49), `slide_pages`(V49), `narration_settings`(V55), `discussion_comment_likes`(V46), `lessons`(V53), `exercise_chapters`(V58), `question_chapters`(V58), `course_bundles`(V51), `course_bundle_items`(V51), `plugin_grants`(V48)。 | 在数据字典中新增上述 13 张表的定义（表结构、字段类型、约束、索引）。 |
| 2 | `V2__operation_logs.sql:13` vs `数据字典.md:738` | **operation_logs.target_type 长度不一致**。V2 创建 `target_type VARCHAR(30)`，数据字典定义为 `VARCHAR(50)`。 | 执行 `ALTER TABLE operation_logs ALTER COLUMN target_type TYPE VARCHAR(50)` 对齐。 |
| 3 | `V2__operation_logs.sql:23-26` + `V39__fix_operation_logs.sql:19-22` | **operation_logs 存在 4 组重复索引**。V2 创建 `idx_operation_logs_user_id/action/created_at/target`，V39 又创建 `idx_ol_user/action/created/target`，功能完全重复，浪费存储并降低写入性能。 | 删除 V2 的 4 个旧索引（`DROP INDEX IF EXISTS idx_operation_logs_user_id;` 等），统一使用 V39 的 `idx_ol_*` 索引名。 |
| 4 | `V32__teaching_classes.sql:24` vs `数据字典.md:282` | **teaching_classes.status 类型不匹配**。数据字典定义 `Integer(1)`，V32 使用 `SMALLINT`。PostgreSQL 中两者不同（SMALLINT=2字节, INTEGER=4字节）。 | 统一为 `INTEGER`（`ALTER TABLE teaching_classes ALTER COLUMN status TYPE INTEGER`），或更新数据字典为 `SMALLINT`。 |
| 5 | `V36__course_review_logs.sql:20-21` vs `数据字典.md:381-382` | **course_review_logs.previous_status 和 new_status 类型不匹配**。数据字典定义 `Integer(1)`，V36 使用 `SMALLINT`。 | 同上，统一类型。 |
| 6 | `V11__course_reviews.sql:9` vs `数据字典.md:705` | **course_reviews.rating 类型不匹配**。数据字典定义 `Integer(1)`（INTEGER），V11 使用 `SMALLINT`。 | 统一类型。 |
| 7 | `V5__gate2_videos.sql` vs `数据字典.md §3.1` | **videos 表 6 个字段存在 DB 中但未在数据字典登记**：`url`(V5:15), `mime_type`(V5:13), `thumbnail_url`(V5:17), `progress`(V5:19), `sort_order`(V5:21)。这些是 V5 原始字段，虽由 V25 重命名/覆盖后部分不再使用，但仍存在于表中。 | 在数据字典中登记剩余活跃字段，或清理废弃字段后更新文档。 |

---

### P2 — 可优化项

| # | 文件:行号 | 问题 | 建议 |
|---|----------|------|------|
| 1 | `数据字典.md` 多处 | **数据字典大部分表的字段表未列出 `deleted_at`**。V16 为 12 张表批量添加了 `deleted_at` 软删除列，但数据字典中仅在 §2.3 提及概念，各表的字段清单均未列该列。 | 在每张受影响表的字段表中追加 `deletedAt \| deleted_at \| LocalDateTime \| string \| - \| - \| 软删除时间` 行。 |
| 2 | `V53__lessons_table.sql:1` | **Migration 注释头版本号错误**。文件名 `V53__lessons_table.sql`，但文件内注释写为 `-- V52__lessons_table.sql`。 | 修正文件注释为 `V53__lessons_table.sql`。 |
| 3 | `V57__fix_orders_bundle_fk.sql:1` | **Migration 注释头版本号错误**。文件名 `V57__fix_orders_bundle_fk.sql`，但文件内注释写为 `-- V51__fix_orders_bundle_fk.sql`。 | 修正文件注释为 `V57__fix_orders_bundle_fk.sql`。 |
| 4 | `NarrationSetting.java:9` | **NarrationSetting 实体缺少 `@TableId` 注解**。类上有 `@TableName("narration_settings")` 但 `id` 字段未标注 `@TableId`，可能影响 MyBatis-Plus 的 `selectById`/`updateById` 行为。 | 在 `private Long id` 字段上添加 `@TableId(type = IdType.AUTO)`。 |
| 5 | `V11__course_reviews.sql:15-16` vs `数据字典.md:711` | **course_reviews 索引名与数据字典不一致**。V11 创建 `idx_course_reviews_course_id` 和 `idx_course_reviews_user_id`，数据字典期望的索引名为 `idx_cr_course_user (UNIQUE)` 和 `idx_cr_course`。V17 补齐了 UNIQUE 索引但旧索引存留。 | 删除 V11 旧索引，重命名为 `idx_cr_course`；V17 的 UNIQUE 索引名与数据字典一致。 |
| 6 | `数据字典.md:88` | **数据字典 users 索引清单未列出 `uk_users_teacher_no`**。V1 实际创建了 `CONSTRAINT uk_users_teacher_no UNIQUE (teacher_no)`，但数据字典索引行缺少此条目。数据契约引用视图（data-contract.md §1.1）中已包含此条。 | 在数据字典 users 索引行追加 `idx_users_teacher_no (UNIQUE)`。 |
| 7 | `V18__badges_table.sql` vs `V38__achievements.sql` | **badges 表（V18）已被 achievements（V38）取代但未废弃/删除**。V38 创建了 achievements，V40 将 badges 数据迁移到 achievements，但 badges 表仍保留在 DB 中。 | 确认 badges 表不再使用后，由 DBA 手工 DROP 并更新数据字典。 |
| 8 | `数据字典.md` 多处 | **数据字典中 varchar 列约束为 "NOT NULL" 但 migration 中写为 "NOT NULL DEFAULT xxx" 的模式不统一**（如 `teaching_classes.status`: 字典 `NOT NULL, default 1`, V32 `NOT NULL DEFAULT 1`）。语义等价但增加自动化校验难度。 | 统一约束描述格式，建议使用 `NOT NULL DEFAULT <val>` 格式。 |
| 9 | `Grade.java:22-23` | **Grade 实体字段名 `studentId` 映射到 DB 列 `user_id`**。Java 字段名与 DB 列含义有差异（studentId ≠ user_id），虽然 `@TableField("user_id")` 正确映射了，但字段命名存在误导风险。 | 将 Java 字段重命名为 `userId` 以消除混淆，或保留并在文档中注明设计意图。 |

---

## 机械检查结果

[委派子代理检查 — 已完成]

### @TableName 一致性
- **56/56 实体有显式 @TableName，全部指向正确表名** ✅
- **NarrationSetting 缺少 @TableId** ⚠️（见 P2 #4）

### @TableField 一致性
- 所有显式 @TableField 映射正确
- Grade.studentId → `user_id`（正确但命名易混淆，见 P2 #9）
- OperationLog.success → `is_success`（正确但 DB 中同时存在 `success` 和 `is_success` 两列，见 P0 #1）

### 迁移版本号连续性
- V1 至 V68 共 68 个迁移文件，**无版本号缺失** ✅
- 但 V53 和 V57 文件内注释头版本号与文件名不一致 ⚠️（见 P2 #2, #3）

### 数据字典覆盖完整性
- 数据字典登记 43 张表（含附录），**全部有对应 migration** ✅
- 但 **13 张 migration 创建的表未在数据字典中登记**，需补齐（见 P1 #1）

---

## 完整性检查矩阵

| 检查项 | 状态 |
|--------|------|
| 数据字典每张表都有对应 migration？ | ✅ 是（43/43） |
| Migration 版本号连续递增（V1-V68）？ | ✅ 是（无缺失） |
| 有重复版本号？ | ❌ 否（无重复） |
| 数据字典中字段与 migration 中字段完全一致？ | ❌ 否（见 P0/P1/P2） |
| 所有 Entity @TableName 正确？ | ✅ 是（56/56） |
| 所有 Entity 字段映射正确？ | ⚠️ 基本正确（见 P0 #1 OperationLog 双列问题） |

---

## 决策

- [ ] 放行（无 P0 阻塞项，P1/P2 记录到 Phase 6 统一处理）
- [x] **阻塞（存在 P0 项，需修复后重新审查）**
- [ ] 混合（有 P0 阻塞项 + P1/P2 项，P0 修复后重新审查，其余记录到 Phase 6）

### 阻塞原因

存在 **3 个 P0 阻塞项** 必须修复：
1. **operation_logs 表 `success`/`is_success` 双列冲突** — 数据分裂风险，运行期行为不可预测
2. **videos.file_size 缺少 NOT NULL 约束** — 违反数据契约，可能导致代码层 NPE
3. **course_categories.updated_at 字段缺失于数据字典** — 数据字典未如实反映 DB 结构

### 修复后需重新执行的验证
- R2 审查全部 P0/P1 问题修复完成
- 必要时执行 `mvn flyway:migrate` 验证迁移可运行
- 更新数据字典后重新对照各表字段

---

*审查人：Reviewer (R2)*
*日期：2026-06-22*
