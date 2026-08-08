# 事故复盘 · 2026-08-07 V310 chapter_id=1 硬编码回填产生"幽灵章节"数据

> **事故等级**: P1-I-14 遗留（内部数据完整性问题，学生端可能看到错误的章节归属 → L0 兜底升级为数据完整性 = 体验保障）
> **触发场景**: `V310__backfill_slide_pages_to_new_arch.sql` PPT/HTML 回填段使用 `COALESCE(s.chapter_id, 1)` 硬编码兜底
> **影响范围**: 无 chapter 归属（`slide_pages.chapter_id IS NULL`）的存量 slide 被归入"幽灵章节 1"
> **修复状态**: ✅ 已建 V328 纯审计（视图 + 函数 + admin 方法）；数据修复待人工 review 审计结果后 V329+ 后置迁移

---

## 1. 症状

### 1.1 数据症状

`slide_ppt_pages` / `slide_html_units` 中存在 `chapter_id = 1` 的记录，但满足以下任一：

| 判定 | SQL 特征 |
|------|---------|
| a. 所属 section 缺失 | `section_id` 无对应 `course_sections` 行（无法反查真实章节） |
| b. 章节错位（主因） | 按 `section_id` 反查 `course_sections.chapter_id ≠ 1`（V310 硬编码产物） |
| c. 跨课程引用 | `course_chapters.id = 1` 属于其它课程（chapter_id 全局自增，不同课程各自有"章节 1"） |

### 1.2 客户可见症状（潜在）

- 学生端"课时 → 章节"归属展示错误：本该挂在正确章节下的课时/课件被归到"章节 1"
- 章节级课件概览（增量 4b 章节级课时概览）按 `chapter_id` 聚合时统计口径错误

---

## 2. 根因

**位置**: `micro-course-api/src/main/resources/db/migration/V310__backfill_slide_pages_to_new_arch.sql`

```sql
-- 第 1 部分 PPT_RENDERED → slide_ppt_pages
SELECT
    s.course_id,
    COALESCE(s.chapter_id, 1),          -- ← 硬编码 1 兜底
    COALESCE(s.section_id, (...ORDER BY cs.id LIMIT 1)),  -- section 也兜底
    ...

-- 第 2 部分 HTML_DIRECT → slide_html_units
    COALESCE(s.chapter_id, 1),          -- ← 同样硬编码 1
```

**问题链**：
1. 旧表 `slide_pages` 中 `chapter_id` 可空（V100-V200 时期未强制）。
2. V310 回填时对空值 `COALESCE(..., 1)`——**1 是全局 chapter id，不是"该 course 的第一个章节"**。
3. 若该 course 根本没有 id=1 的章节，或该行所属 section 真实章节 ≠ 1，则该行成为"幽灵章节"记录。
4. `course_chapters.id` 是全局 BIGSERIAL，课程 A 的"章节 1"与课程 B 的"章节 1"是不同行——硬编码 1 必然错位。

**为什么不是 P0**：V310 回填数据多来自演示/测试环境，且 `slide_pages` 空 chapter 的存量有限；但**正确归属只能从 `course_sections.chapter_id` 反查**，直接改数据有风险 → 采用"先审计、人工 review、后置修复"的兜底策略。

---

## 3. 影响

| 维度 | 影响 | 等级 |
|------|------|------|
| 学生端章节归属 | 课时/课件可能显示在错误的章节下 | P1-C（潜在） |
| 章节级聚合 | 增量 4b 章节级课时概览按 chapter_id 统计口径错误 | P1-C（潜在） |
| 数据完整性 | slide 归属错误章节，长期累积无法自愈 | P1-I |
| 外键语义 | chapter_id 无 FK（V300 仅 FK section），错位不报错、静默存在 | P1-I |

**L0 铁律依据**：数据完整性 = 体验保障。学生端看到错误的章节归属 = 体验断裂，必须兜底到 0 遗留。

---

## 4. 短期缓解（本批次，已实施）

### 4.1 V328 纯审计 migration（不改数据）

```bash
# 文件: micro-course-api/src/main/resources/db/migration/V328__audit_chapter_backfill.sql
```

- **视图** `v_ghost_chapter_backfill`：列出全部幽灵章节嫌疑行（PPT + HTML 明细，含 `actual_chapter_id` / `chapter1_cross_course` 判定信息）
- **函数** `audit_ghost_chapters()`：返回 JSONB 报告（`total_ghost_rows` + `by_course` 分布 + `sample_rows` ≤200 行）

**运维/DB 同学立即执行的审计 SQL**：

```sql
-- 1. 全量明细（确认范围）
SELECT * FROM v_ghost_chapter_backfill ORDER BY course_id, row_id;

-- 2. JSON 汇总报告
SELECT audit_ghost_chapters();

-- 3. 按 course 分布
SELECT course_id, source_type, COUNT(*) AS cnt
FROM v_ghost_chapter_backfill
GROUP BY course_id, source_type
ORDER BY course_id;
```

### 4.2 Admin 调用入口

`CoursewareQueryService.auditGhostChapters()`（仅 ADMIN 角色）→ 调 `audit_ghost_chapters()`，返回 JSON 文本。由 admin 后台接入（后续 API 暴露由 D1/D2 或前端批次负责）。

### 4.3 生产审计脚本（DBA 手工执行）

> **安全**：只读审计（纯 SELECT），**不修改任何数据**。生产 DB 写操作必须先 ask user（生产安全铁律 #5）。

提供统一审计脚本 `scripts/audit-v310-ghost-chapter-prod.sh`，DBA 在生产执行生成审计报告给总工程师 review：

```bash
# 本地提前验证脚本语法（只读，不连库）
bash -n scripts/audit-v310-ghost-chapter-prod.sh

# 生产执行（参数：PGHOST PGDATABASE；用 readonly 账号，禁止用超级用户）
bash scripts/audit-v310-ghost-chapter-prod.sh <prod-pg-host> readonly_user micro_course
```

脚本输出四段审计结果：
1. `slide_ppt_pages` 中 `chapter_id=1` 的幽灵行总数 + 受影响课程数
2. `slide_html_units` 中 `chapter_id=1` 的幽灵行总数 + 受影响课程数
3. 跨课程引用：`chapter_id=1` 但 `course_chapters.id=1` 属于其它课程的明细（按 course 分布）
4. 孤儿引用：`section_id` 无对应 `course_sections` 的 PPT 页明细（sample rows）

**review 决策流程**：DBA 将报告交总工程师 → 判定每行正确归属（section 反查 / 跨课程 / 孤儿）→ 可自动修复部分由 V332 migration 处理（幂等 + operation_logs 留痕）→ 剩余待人工 review 行按 §5 模板人工确认后走 V329+ 后置迁移。

---

## 5. 长期修复（人工 review 后执行，V329+）

> ⚠️ **禁止直连生产 DB 改数据**（生产安全铁律 #5）。修复必须走 V329+ migration + 生产门禁 + 灰度。

### 5.1 修复模板 1：按 section 反查修正（主路径）

```sql
-- PPT：以 section 反查正确 chapter_id
UPDATE slide_ppt_pages p
SET chapter_id = cs.chapter_id, updated_at = NOW()
FROM course_sections cs
WHERE cs.id = p.section_id
  AND p.chapter_id = 1
  AND cs.chapter_id IS DISTINCT FROM 1;

-- HTML：同理
UPDATE slide_html_units u
SET chapter_id = cs.chapter_id, updated_at = NOW()
FROM course_sections cs
WHERE cs.id = u.section_id
  AND u.chapter_id = 1
  AND cs.chapter_id IS DISTINCT FROM 1;
```

### 5.2 修复模板 2：section 缺失的孤儿行

先修复 section 归属（或确认该行应删除），再按新 section 反查：

```sql
-- 先列出孤儿：section_id 无对应 course_sections
SELECT * FROM v_ghost_chapter_backfill WHERE section_id NOT IN (SELECT id FROM course_sections);
-- 人工逐行确认后：
--   方案 A：UPDATE 到正确的 section_id，再按模板 1 反查 chapter_id
--   方案 B：确认为垃圾数据 → 标记/删除（需走审批）
```

### 5.3 修复后验证

```sql
-- 修复后重跑应为 0 行
SELECT COUNT(*) FROM v_ghost_chapter_backfill;          -- 期望 0
SELECT audit_ghost_chapters();                          -- total_ghost_rows 期望 0
```

---

## 6. 防止再发

1. **回填兜底禁止硬编码全局 id**：新增 migration 中任何 `COALESCE(x, <常量>)` 回填都必须以业务可反查字段（如 section → chapter）推导，禁止用全局自增 id 兜底
2. **V310 同类扫描**：`grep -rn "COALESCE(s.chapter_id" micro-course-api/src/main/resources/db/migration/` 确认无其它硬编码
3. **审计机制常态化**：`auditGhostChapters()` 已入 `CoursewareQueryService`，接入 admin 后台后可定期巡检（数据完整性 = 体验保障）
4. **precheck 规则建议**：扫描 migration 中 `COALESCE(.*, [0-9]+)` 模式，阻断硬编码兜底（待后续批次纳入）

---

## 7. 验证清单

- [x] V328 migration 语法（Flyway 兼容：纯 CREATE OR REPLACE VIEW / FUNCTION，幂等、非破坏）
- [x] `mvn -o compile` 通过（CoursewareQueryServiceImpl + JdbcTemplate 注入）
- [x] 审计函数可由 `SELECT audit_ghost_chapters()` 直接调用
- [x] admin 权限校验：非 ADMIN 抛 `NO_PERMISSION`
- [x] 数据字典登记 V328（§2.28）
- [ ] 生产执行审计 → 人工 review 审计结果（V329+ 修复迁移）→ 重跑 0 行

---

**复盘**: V310 回填的 `COALESCE(s.chapter_id, 1)` 把"未知归属"当成"章节 1"，是典型的**用全局 id 当业务默认值**。L0 兜底策略：先审计暴露问题面（不盲目改数据），再由人工确认每行正确归属后 V329+ 修复。审计入口入 service + admin 角色控制，形成可巡检的常态化机制。

— END —
