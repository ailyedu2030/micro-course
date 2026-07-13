---
title: 课程章节课时三层重构设计 (Course → Chapter → Section)
date: 2026-07-13
status: draft
scope: 全部 (表结构+UI+API)
approach: Approach B (重构, 完整1:1数据迁移)
---

# 课程章节课时三层重构设计

## 1. 概述 (Overview)

### 1.1 背景

当前课程架构是 **Course → Chapter** 两层结构，每个章节通过 `chapter_type` 字段（VIDEO/INTERACTIVE/OFFLINE/EXERCISE）来标识该章节的课程类型。这带来一个根本性设计缺陷：**一个章节只能是一种类型**。一个教师无法在同一个章节内同时提供视频课、互动课和练习。

### 1.2 目标

重新设计为 **Course → Chapter → Section (课时)** 三层架构：
- **Chapter** 退化为纯容器，不再有 type
- **Section (课时)** 是真正承载内容的最小单位，每个 Section 有自己独立的 `section_type` 和独立的内容（视频/互动/线下/练习）
- 一个 Chapter 下可以有多个不同类型的 Section
- 每个 Section 是独立的"课时"，独立管理独立课件

### 1.3 成功标准

- ✅ 一个 Chapter 包含多个不同类型的 Section
- ✅ 每个 Section 有独立的课件内容（视频/互动/线下/练习）
- ✅ 现有数据自动 1:1 迁移，无需人工介入
- ✅ 无数据丢失
- ✅ 旧 API 通过向后兼容层仍可工作（一个版本）
- ✅ UI 明确展示 Section 层级

---

## 2. 架构与数据模型 (Architecture & Data Model)

### 2.1 新表 `course_sections`

```sql
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

### 2.2 表 `course_chapters` 变更

```sql
ALTER TABLE course_chapters DROP COLUMN chapter_type;
```

### 2.3 表 `course_slides` 变更

```sql
ALTER TABLE course_slides RENAME COLUMN lesson_id TO section_id;
DROP INDEX IF EXISTS uk_slides_course_lesson;
DROP INDEX IF EXISTS uk_sp_course_lesson_page;
CREATE UNIQUE INDEX uk_slides_course_section 
    ON course_slides(course_id, section_id);
CREATE UNIQUE INDEX uk_sp_course_section_page 
    ON slide_pages(course_id, section_id, page_number);
```

### 2.4 废弃 `lessons` 表

`lessons` 表的所有数据迁移到 `course_sections` 后，`lessons` 表被 drop。Lesson 实体、LessonRepository、LessonService 全部标记为 `@Deprecated`，在下一个 release 删除。

---

## 3. 数据迁移策略 (Migration Strategy)

迁移脚本 V182 一次执行，所有操作在一个事务中：

### 3.1 阶段 A — Schema 创建

1. 创建 `course_sections` 表（含 CHECK 约束）
2. 添加 `course_slides.section_id`（保留 `lesson_id` 列以备回滚）
3. 创建 `uk_slides_course_section` 唯一约束
4. 创建 `uk_sp_course_section_page` 唯一约束

### 3.2 阶段 B — 数据迁移（4 步）

**Step 1: 迁移 chapter 到 section**
```sql
INSERT INTO course_sections 
    (chapter_id, course_id, title, section_type, sort_order, duration, 
     visible, version, created_at, updated_at)
SELECT 
    cc.id, cc.course_id, cc.title, 
    COALESCE(cc.chapter_type, 'VIDEO'),  -- 默认值
    cc.sort_order, cc.duration, cc.visible, 1, 
    cc.created_at, cc.updated_at
FROM course_chapters cc
WHERE cc.deleted_at IS NULL;
```

**Step 2: 迁移 lessons 到 section**（追加到 chapter 创建的 section 后）
```sql
INSERT INTO course_sections 
    (chapter_id, course_id, title, section_type, sort_order, duration, 
     visible, version, created_at, updated_at)
SELECT 
    l.chapter_id, l.course_id, l.title, 
    COALESCE(l.lesson_type, 'VIDEO'),
    10000 + l.sort_order,  -- 偏移确保排序在 chapter section 之后
    l.duration, l.visible, l.version, 
    l.created_at, l.updated_at
FROM lessons l
WHERE l.deleted_at IS NULL;
```

**Step 3: 迁移 course_slides.lesson_id 到 section_id**
```sql
UPDATE course_slides cs
SET section_id = ls.section_id_new
FROM (
    SELECT l.id AS lesson_id_old,
           cs2.section_id_new
    FROM lessons l
    JOIN course_chapters cc ON cc.id = l.chapter_id AND cc.deleted_at IS NULL
    JOIN course_sections cs2 
        ON cs2.chapter_id = cc.id 
        AND cs2.title = cc.title 
        AND cs2.sort_order = cc.sort_order
    UNION
    SELECT l.id, cs3.section_id_new
    FROM lessons l
    JOIN course_sections cs3 
        ON cs3.chapter_id = l.chapter_id 
        AND cs3.title = l.title
        AND cs3.sort_order = (10000 + l.sort_order)
    WHERE l.deleted_at IS NULL
) ls
WHERE cs.lesson_id = ls.lesson_id_old;

-- 也迁移 slide_pages.lesson_id
UPDATE slide_pages sp
SET lesson_id = ls.new_lesson_id
FROM (
    SELECT l.id AS old_lesson_id, cs.id AS new_lesson_id
    FROM course_sections cs
    JOIN lessons l ON l.chapter_id = cs.chapter_id AND l.title = cs.title
        AND cs.sort_order >= 10000 AND l.sort_order = (cs.sort_order - 10000)
    WHERE l.deleted_at IS NULL
) ls
WHERE sp.lesson_id = ls.old_lesson_id;

-- 重命名 slide_pages.lesson_id 为 section_id
ALTER TABLE slide_pages RENAME COLUMN lesson_id TO section_id;
```

**Step 4: 清理**
```sql
ALTER TABLE course_chapters DROP COLUMN chapter_type;
DROP TABLE lessons CASCADE;
ALTER TABLE course_slides DROP COLUMN lesson_id;  -- 已迁移
```

### 3.3 阶段 C — 验证（迁移后运行）

```sql
-- 验证 section 数 >= chapter 数
SELECT 
    (SELECT COUNT(*) FROM course_sections WHERE deleted_at IS NULL) as sections,
    (SELECT COUNT(*) FROM course_chapters WHERE deleted_at IS NULL) as chapters;

-- 验证所有 section 都有合法 type
SELECT section_type, COUNT(*) FROM course_sections 
WHERE deleted_at IS NULL GROUP BY section_type;

-- 验证所有 slide 有 section
SELECT COUNT(*) FROM course_slides WHERE section_id IS NULL;
-- 应为 0
```

### 3.4 干跑模式

V182 支持 `--dry-run` Flyway 占位符（`placeholder` 模式），在 staging 上先模拟运行验证数据。

### 3.5 回滚方案

迁移在单一事务中执行。如果任何步骤失败，整个迁移回滚到迁移前状态。备份 `course_chapters`/`lessons`/`course_slides`/`slide_pages` 表到 `*_backup_pre_v182` 副本（迁移前）。

---

## 4. API 层 (API Layer)

### 4.1 新端点

| Method | Path | 用途 |
|--------|------|------|
| `GET` | `/api/courses/{cid}/chapters/{chid}/sections` | 列出章节下所有 Section（分页） |
| `GET` | `/api/courses/{cid}/chapters/{chid}/sections/{sid}` | 获取单个 Section |
| `POST` | `/api/courses/{cid}/chapters/{chid}/sections` | 创建 Section（title, sectionType, sortOrder, duration, description） |
| `PUT` | `/api/courses/{cid}/chapters/{chid}/sections/{sid}` | 更新 Section |
| `DELETE` | `/api/courses/{cid}/chapters/{chid}/sections/{sid}` | 软删 Section（级联删 slides） |
| `GET` | `/api/courses/{cid}/sections/{sid}/slides` | 列出 Section 下所有课件 |
| `POST` | `/api/courses/{cid}/sections/{sid}/slides` | 上传 PPT/HTML 课件到 Section |

### 4.2 端点变更

- `POST /api/courses/{cid}/slides/upload` 改用 `sectionId` 参数（替代 `chapterId`）
- `DELETE /api/courses/{cid}/slides?sectionId=N` 删除单个 Section 的课件
- `GET /api/courses/{cid}/slides/pages/{pageNumber}?sectionId=N` 按 section 查询
- `GET /api/courses/{cid}/slides/list` 改为 `?chapterId=N` 列出该 chapter 所有 sections 的 slides

### 4.3 兼容性层（一个 release 后删除）

- `ChapterVO.chapterType` 字段保留但返回 `null`（旧前端读取不报错）
- `GET /api/chapters` 返回 `sections: [...]` 数组，前端可忽略
- POST/PUT/DELETE chapter 自动创建一个对应 section

### 4.4 错误码

```java
SECTION_NOT_FOUND(5001),              // 5001
SECTION_DUPLICATE_TITLE(5002),       // 5002
SECTION_TYPE_INVALID(5003),          // 5003
SECTION_HAS_SLIDES(5004),            // 5004
SECTION_CHAPTER_NOT_FOUND(5005),      // 5005
SECTION_SCRIPT_CONTENT_TOO_LONG(5006) // 5006
```

### 4.5 DTO

```java
public class SectionDTO {
    private Long id;
    private Long chapterId;
    private Long courseId;
    private String title;
    @Pattern(regexp = "VIDEO|INTERACTIVE|OFFLINE|EXERCISE")
    private String sectionType;
    private Integer sortOrder;
    private Integer duration;
    private Boolean visible;
    private String description;
    private String scriptContent;
    private Integer slideCount;       // 统计
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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
}

public class SectionUpdateRequest {
    // 同 CreateRequest，但所有字段可选
}
```

### 4.6 验证

- `section_type` 必须 4 选 1（@Pattern + DB CHECK）
- `title` 1-200 字符
- `sort_order >= 0`
- 同一 chapter 内 title 不重复

---

## 5. 前端架构 (Frontend Architecture)

### 5.1 新组件

| 组件 | 路径 | 用途 |
|------|------|------|
| `SectionList.vue` | `micro-course-admin/src/components/course/` | Section 列表（嵌入章节行） |
| `SectionEditDialog.vue` | `micro-course-admin/src/components/course/` | 新建/编辑 Section 对话框 |
| `SectionUploadDialog.vue` | `micro-course-admin/src/components/course/` | 上传 PPT/HTML 课件对话框 |
| `SectionDetailDialog.vue` | `micro-course-admin/src/components/course/` | Section 详情查看 |

### 5.2 CourseDetail.vue 重构

将现有的章节管理表格改为**树形结构**：
```
Chapter 1 - 阶段1 工具安装
  📹 视频课 - 阶段1工具安装视频 (45分钟) [操作: 编辑/课件/删除]
  📝 练习 - 阶段1练习题 (30分钟)   [操作: 编辑/选题/删除]
Chapter 2 - 阶段2 Prompt工程
  🎯 互动课 - 阶段2互动课件 (60分钟) [操作: 编辑/课件/删除]
  🏫 线下课 - 阶段2线下讨论 (120分钟)[操作: 编辑/排课/删除]
```

### 5.3 现有组件迁移

- `ChapterList.vue` 保留独立使用（admin 视图），但**移除 chapter_type 列**，增加"课时数"列
- `ChapterEditDialog.vue` 移除 `chapterType` 字段

### 5.4 路由

| Path | Component | 备注 |
|------|-----------|------|
| `/teacher/courses/{id}/chapters` | `ChapterList.vue` | admin视图(无 chapter_type) |
| `/teacher/courses/{id}/chapters/{chid}/sections` | `ChapterDetail.vue` 新版 | 含 Section 树 |
| `/api/courses/{cid}/chapters/{chid}/sections` | API | - |

### 5.5 状态管理 (Pinia)

```typescript
// useChapterStore (existing, modified)
state: () => ({
  chapters: [] as Chapter[],
  sectionsByChapterId: {} as Record<number, Section[]>,
  totalElements: 0
})

// useSectionStore (new)
state: () => ({
  sections: [] as Section[],
  totalElements: 0
})
```

### 5.6 业务侧

- 上传课件：选 Section → SectionUploadDialog
- 编辑 Section：选 Section → SectionEditDialog
- 删除 Section：确认 → 检查 slideCount=0 否则要求 force

### 5.7 迁移期 UI 行为

- 课程详情页默认展开所有章节的 Section 子树
- 章节行显示 "X 个课时" 摘要（按类型分）
- 旧 `chapter_type` 字段在 ChapterVO 中返回 null → UI 不显示 type tag

---

## 6. 数据流与状态管理 (Data Flow & State Management)

### 6.1 读取流（课程详情页）

```
CourseDetail.vue mounted
  ↓ fetchCourse(id)                          [GET /api/courses/{id}]
  ↓ fetchChapters(courseId)                 [GET /api/chapters?courseId=N]
  ↓ for each chapter: fetchSections(chapterId) [GET /api/courses/{c}/chapters/{ch}/sections]
  → 渲染树: Chapter → Section[]
```

### 6.2 写入流

```
SectionEditDialog submit
  ↓ validate (title, sectionType, sortOrder, duration)
  → POST /api/courses/{c}/chapters/{ch}/sections
  → optimistic update Pinia useChapterStore
  → refresh chapter's section list
  → close dialog
```

### 6.3 上传流

```
SectionUploadDialog file selected
  ↓ validate type (PPT/HTML)
  → POST /api/courses/{c}/sections/{s}/slides (multipart)
  → progress: 0% → 100%
  → success: refresh section's slide list
  → close dialog
```

### 6.4 状态管理

- `useChapterStore` — chapters keyed by id, 含 `sectionsByChapterId` map
- `useSectionStore` — sections keyed by id，含 `slideCount`
- 所有 mutation 触发组件级响应式更新

### 6.5 错误处理

- 迁移失败：自动回滚，提示用户"数据迁移失败，请联系管理员"
- 上传失败：保留对话框内容，用户可重试
- 权限错误：显示 Toast，不跳转

---

## 7. 错误处理与边界情况 (Error Handling & Edge Cases)

### 7.1 迁移错误

- 迁移在单事务中执行，任何步骤失败自动回滚
- 备份表 `*_pre_v182` 保留 7 天（Flyway 之外的备份）
- 迁移后自动运行 3 个验证查询：
  - sections 数 ≥ chapters 数
  - 所有 section 都有合法 type
  - 所有 course_slides 都有 section_id

### 7.2 运行时错误

- 404：chapter/section/slide 未找到
- 409：title 在同一 chapter 内重复
- 400：section_type 非法
- 403：非所有者操作（保留现有逻辑）

### 7.3 边界情况

- 没有任何 Section 的 chapter：显示空态"暂无课时，点击新增"
- 只有一个 Section 的 chapter：直接显示内容
- 多个不同类型 Section：按 type 分组显示
- 上传类型与 Section 类型不匹配：警告但允许
- OFFLINE 类型 Section：不显示课件 UI，只显示线下排课入口
- 同一 title 在不同 chapter 允许重复（仅同 chapter 限制）

### 7.4 向后兼容（一个 release）

- `ChapterVO.chapterType` 字段返回 null
- `GET /api/chapters` 返回 `sections: []` 数组
- POST/PUT/DELETE chapter 自动创建一个对应 section
- 删除 `chapter_type` 输入参数不报错

---

## 8. 测试策略 (Testing Strategy)

### 8.1 单元测试（后端）

- `SectionServiceTest` — CRUD + 验证
- `SectionRepositoryTest` — 查询
- `MigrationTest` — V182 数据完整性

### 8.2 集成测试（后端）

- `SectionApiTest` — REST + 鉴权
- `SlideApiTest` — 课件上传/删除按 sectionId
- `MigrationIT` — 真实数据库迁移测试

### 8.3 前端测试

- `ChapterSectionTree.test.ts` — 树形渲染
- `SectionEditDialog.test.ts` — 表单验证
- `SectionUpload.test.ts` — 上传

### 8.4 E2E 测试

- `course-section-flow.spec.ts` — 完整用户流程
- `migration-validation.spec.ts` — 在测试 DB 上运行 V182

### 8.5 手动 QA

- 所有现有 chapter 功能正常
- 4 种 Section 类型 UI 正常
- 迁移在 staging 验证

### 8.6 验收标准

- ✅ 现有数据全部保留
- ✅ 旧 API 返回 chapter_type=null
- ✅ 新端点工作
- ✅ UI 展示 Section 树
- ✅ 上传 4 种类型工作

---

## 9. 实施计划 (Implementation Plan)

### 9.1 Phase 1 — DB 迁移（V182）
- 创建 `course_sections` 表
- 数据迁移（4 步）
- 验证查询
- 备份表清理

### 9.2 Phase 2 — 后端 API
- `Section` 实体 + `SectionRepository` + `SectionService` + `SectionController`
- DTO: `SectionDTO` / `SectionCreateRequest` / `SectionUpdateRequest`
- 错误码 5001-5006
- 旧端点兼容层

### 9.3 Phase 3 — 前端
- 4 个新 Vue 组件
- `CourseDetail.vue` 树形重构
- `ChapterList.vue` 移除 chapter_type
- API 调用层

### 9.4 Phase 4 — 测试 & 部署
- 单元测试 + 集成测试
- E2E 测试
- staging 环境验证
- 生产环境灰度发布

### 9.5 依赖关系

- Phase 1 必须先于 Phase 2
- Phase 2 完成后 Phase 3 可以开始
- Phase 4 全程并行

### 9.6 预计工作量

- Phase 1: 1-2 天
- Phase 2: 2-3 天
- Phase 3: 3-4 天
- Phase 4: 2-3 天
- 总计: ~10 天
