---
title: 微专业章节-教师分配与课程来源决策设计 v2
date: 2026-07-02
status: revised-after-review
author: chief engineer
reviewers: [architect, data-model, ux, security]
revision: 1 (基于 4 个 agent 并行审查)
tags: [micro-specialty, chapter-assignment, course-sourcing, pricing, review]
---

# 微专业章节-教师分配与课程来源决策设计 v2

## v1 → v2 变更摘要

| 严重度 | 计数 | 关键发现 |
|------|------|------|
| **P0** | 6 | 缺少"审批后章节物化"路径、竞态条件、CASCADE 不一致等 |
| **P1** | 22 | UNIQUE 约束、CHECK 缺失、源-章节一致性、定价快照冲突等 |
| **P2** | 18 | 索引缺失、可发现性、移动端忽略、幂等性等 |

**核心 P0 路径已重新设计**（见 §11）。其他 P1/P2 列入 v2 实施计划。

---

## 1. 概述

### 1.1 问题
（同 v1）

### 1.2 目标
（同 v1）

### 1.3 用户角色与职责
（同 v1）

### 1.4 核心业务规则

| ID | 规则 | v1→v2 变化 |
|----|------|------------|
| BR-1 | 教师接受时每章节必须选来源 | 同 v1 |
| BR-2 | 课程分配粒度为**章节** | 同 v1 |
| BR-3 | 一门课可被多位老师共同教不同章节 | 同 v1 |
| BR-4 | 同一章节可被多位老师分别认领 | 同 v1 |
| BR-5 | 老师必须为所有分配章节做决策 | **保留**（UX review 提了部分接受需求，v1 暂不开放） |
| BR-6 | 跨院系选课弹费用提示 | 同 v1 |
| BR-7 | 接受时**服务端**snapshot 价格到 `frozen_price` | **强化**：服务端取价，不信任客户端 |
| **BR-8** | **新增**：撤回提案需级联软删除章节分配 | 修复 v1 漏洞 |
| **BR-9** | **新增**：被邀请者必须在邀请有效期内接受 | 修复 v1 漏洞 |
| **BR-10** | **新增**：课程定价修改后，已接受老师的价格不变 | v1 已有，归入 BR-7 |

### 1.5 验收标准
（同 v1）

---

## 2. 数据模型（v2 - 修复后）

### 2.1 新增表（V107 + V107.1）

```sql
-- V107__chapter_teacher_assignment.sql

-- 1. proposal_chapters — 申报中的章节
CREATE TABLE IF NOT EXISTS proposal_chapters (
    id              BIGSERIAL    PRIMARY KEY,
    proposal_id     BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    course_id       BIGINT       NOT NULL REFERENCES proposal_courses(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),   -- v2: 限长,与 V92 review_comment 一致
    sort_order      INTEGER      DEFAULT 0,
    hours           INTEGER      DEFAULT 0,  -- v2: 重命名 duration→hours, 与 proposal_courses.hours 一致
    version         INTEGER      NOT NULL DEFAULT 0,  -- v2: 加乐观锁
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pc_chapter UNIQUE (course_id, sort_order),
    CONSTRAINT chk_pc_chapter_hours CHECK (hours >= 0)  -- v2: CHECK 约束
);
CREATE INDEX IF NOT EXISTS idx_pc_chapter_course ON proposal_chapters(course_id);
CREATE INDEX IF NOT EXISTS idx_pc_chapter_proposal ON proposal_chapters(proposal_id);

-- 2. chapter_teacher_assignments — 章节-教师映射(核心表)
CREATE TABLE IF NOT EXISTS chapter_teacher_assignments (
    id                  BIGSERIAL    PRIMARY KEY,
    proposal_id         BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE RESTRICT,  -- v2: RESTRICT(与其他 teacher FK 一致)
    course_id           BIGINT       NOT NULL REFERENCES proposal_courses(id) ON DELETE CASCADE,
    chapter_id          BIGINT       NOT NULL REFERENCES proposal_chapters(id) ON DELETE CASCADE,
    teacher_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,  -- v2: RESTRICT

    -- 来源决策
    source              VARCHAR(20)  NOT NULL DEFAULT 'TBD',
    source_course_id    BIGINT       REFERENCES courses(id),
    source_chapter_id   BIGINT       REFERENCES course_chapters(id),

    -- 接受状态
    accept_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    accepted_at         TIMESTAMP,

    -- v2: frozen_price 改为可空(用 NULL 表示未设置), DEFAULT NULL
    frozen_price        DECIMAL(10,2),

    responsibility      VARCHAR(500),
    version             INTEGER      NOT NULL DEFAULT 0,  -- v2: 乐观锁

    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP,

    -- v2: UNIQUE 改为不带 source, 避免 TBD+existing 同时存在
    CONSTRAINT uk_cta_chapter_teacher UNIQUE (chapter_id, teacher_id),

    -- v2: CHECK 约束枚举值
    CONSTRAINT chk_cta_source CHECK (source IN ('TBD', 'existing', 'new')),
    CONSTRAINT chk_cta_accept_status CHECK (accept_status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'LEFT')),

    -- v2: 源决策一致性: source=existing 必须有 source_course_id 和 source_chapter_id
    CONSTRAINT chk_cta_source_consistency CHECK (
        (source = 'existing' AND source_course_id IS NOT NULL AND source_chapter_id IS NOT NULL)
        OR (source = 'new')
        OR (source = 'TBD' AND source_course_id IS NULL AND source_chapter_id IS NULL)
    )
);
CREATE INDEX IF NOT EXISTS idx_cta_teacher ON chapter_teacher_assignments(teacher_id);
CREATE INDEX IF NOT EXISTS idx_cta_chapter ON chapter_teacher_assignments(chapter_id);
CREATE INDEX IF NOT EXISTS idx_cta_proposal ON chapter_teacher_assignments(proposal_id);
CREATE INDEX IF NOT EXISTS idx_cta_source_chapter ON chapter_teacher_assignments(source_chapter_id);  -- v2: 加索引

-- v2: 触发器: 验证 source_chapter_id 属于 source_course_id
CREATE OR REPLACE FUNCTION trg_cta_source_chapter_belongs_course_fn()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.source = 'existing' AND NEW.source_chapter_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM course_chapters
            WHERE id = NEW.source_chapter_id AND course_id = NEW.source_course_id
        ) THEN
            RAISE EXCEPTION 'source_chapter_id % does not belong to source_course_id %',
                NEW.source_chapter_id, NEW.source_course_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cta_source_chapter_belongs_course
    BEFORE INSERT OR UPDATE OF source_chapter_id, source_course_id ON chapter_teacher_assignments
    FOR EACH ROW EXECUTE FUNCTION trg_cta_source_chapter_belongs_course_fn();

-- v2: 部分唯一索引(已删除用 deleted_at) 软删除后允许重新分配
CREATE UNIQUE INDEX uk_cta_active
    ON chapter_teacher_assignments(chapter_id, teacher_id)
    WHERE deleted_at IS NULL;
```

### 2.2 课程定价扩展（V108）

```sql
-- V108__course_pricing.sql

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  free_access_scope   VARCHAR(20) NOT NULL DEFAULT 'none',
  free_dept_ids       JSONB DEFAULT '[]'::jsonb,  -- v2: TEXT → JSONB
  discount_scope      VARCHAR(20) NOT NULL DEFAULT 'none',
  discount_percent    INTEGER NOT NULL DEFAULT 0;

-- v2: CHECK 约束
ALTER TABLE courses ADD CONSTRAINT chk_course_free_scope
    CHECK (free_access_scope IN ('none', 'same_department', 'same_college', 'same_school'));
ALTER TABLE courses ADD CONSTRAINT chk_course_discount_scope
    CHECK (discount_scope IN ('none', 'same_college', 'same_school'));
ALTER TABLE courses ADD CONSTRAINT chk_course_discount_percent
    CHECK (discount_percent >= 0 AND discount_percent <= 100);
```

---

## 3. API 端点

### 3.1 提案阶段（Phase 1+2）

（同 v1 + v2: 加 `@PreAuthorize("hasRole('TEACHER')")` + 业务验证）

```
GET  /api/proposals/{proposalId}/courses
     权限: must be proposal owner

POST /api/proposals/{proposalId}/courses/{courseId}/chapters
     body: { title, description, hours, sortOrder }
     验证: courseId 必须属于 proposalId  (修复 v2 #4)

POST /api/proposals/{proposalId}/chapter-assignments
     body: { teacherId, chapterIds: [1,2,3] }
     验证: chapterIds 都属于 proposalId (修复 v2 #3)
     验证: teacherId 是 TEACHER 角色  (修复 v2 #16)

DELETE /api/proposals/{proposalId}/chapter-assignments/{id}
```

### 3.2 邀请阶段（Phase 3）

```
POST /api/micro-specialty-teachers/invite
     body: { microSpecialtyId, teacherId, role, chapterIds: [1,2,3] }

-- v2: 新增端点, 不修改现有 acceptInvite, 保证向后兼容
POST /api/micro-specialty-teachers/{inviteId}/accept-with-chapters
     body: { chapterDecisions: [
              { chapterId, source: 'existing', sourceChapterId: 42 },
              { chapterId, source: 'new', newChapterData: {...} }
            ] }
     验证: chapterIds 都属于该 invite 的 chapter_teacher_assignments  (修复 v2 #2)
     验证: source_chapter_id 属于 source_course_id  (DB 触发器)
     验证: 服务端重读 courses.price 快照到 frozen_price (修复 v2 #5)
     事务: 全或无
     幂等: 已处理返回 422
```

-- v2: 权限收紧
GET  /api/courses/chapters/search
     权限: @PreAuthorize("hasRole('TEACHER')")  (修复 v2 #1)
     验证: teacher 只能搜索本院系 + 跨院系开关
     限速: 60 req/min per user

POST /api/courses/{courseId}/pricing-for-adopter
     权限: @PreAuthorize("hasRole('TEACHER')")  (修复 v2 #9)
```

### 3.3 撤回提案（Phase 2 - 修复 v2 #8, #15）

```
POST /api/proposals/{proposalId}/withdraw
     副作用:
       - proposal.status = WITHDRAWN
       - chapter_teacher_assignments (deleted_at = now, accept_status = 'REVOKED')
       - micro_specialty_teacher (invite_status = 'REVOKED')
```

---

## 4. 前端组件

### 4.1 Step 2 课程表（v2 - 改进）

**v1 问题**：表单过于庞大（5步都是错综复杂的）
**v2 改进**：把课程+章节拆为独立的折叠区域，不嵌入富文本编辑器之间

```
[Step 1 表头基础]  [Step 2 基本情况（仅文本字段）]
[Step 3 课程与章节]  ← 新步骤, 拆出  (v2)
[Step 4 教学团队]
[Step 5 签字盖章]
[Step 6 确认提交]
```

### 4.2 Step 3 教学团队分配（v2 - 改进）

**v1 改进**：行式 + Drawer/Popover
**v2 增强**：
- 每行末尾显示章节摘要 Tag (始终可见, 无需点击)
- Drawer 打开后: 课程分组的复选框 + 搜索 + 全选/反选 + 批量 "全部设为新建" 按钮
- 空状态: "还没有分配任何章节。点击 [+ 分配章节] 开始。"
- 删除章节前: 弹确认 "此章节已分配给 2 位教师, 确认删除?"

### 4.3 邀请接受页（v2 - 改进）

**v1 问题**：老师被淹没在 8 章决策中
**v2 改进**：分步向导 (Step 1/N, 2/N, ...)

```
邀请 #1: AI 数据分析 (5/5 章节已决策)  [接受]  ← 进度条
↓
Step 1/5: Python 简介
  [● 已有] [搜索...]   或  [○ 新建]
  → 选了已有章节 (本校, 免费 ¥0)
  [下一步] 
↓
Step 2/5: 变量类型
  ...
↓
Step 5/5: 综合案例
  [确认全部接受]  ← 之前是单按钮
```

**v2 增强**：
- 进度条 "3/5 章节已决策"
- 接受前: 顶部横幅 "5 章节已全部决策完成, 可接受"
- 跨院系: 每章来源决策后弹费用提示 "⚠ 跨院系选课, 本课 ¥300, 您的院系享 7 折, 实际 ¥210"
- 搜索防抖: 300ms

### 4.4 已有章节搜索（v2 - 改进）

**v2 增强**：
- 搜索防抖 300ms
- 显示来源院系 + 价格标识
- 跨院系标 ⚠
- 加载: 行级 skeleton
- 加载完成: 立即显示
- 空结果: "未找到与 'X' 匹配的章节, 换个关键词试试"

---

## 5. 错误处理 + 边界

| 错误码 | 场景 | v2 处理 |
|------|------|------|
| E001 | 接受时某章节未选来源 | 红框 + 进度条 "还需决策 2 章节" + 客户端拦截  |
| E002 | 课程表空 | 弹"先添加课程" |
| E003 | 团队空 | 弹"至少 1 位团队成员" |
| E004 | 同一章节重复分配 | UNIQUE(chapter_id, teacher_id) DB 兜底 |
| E005 | 选已有章节已下架 | ⚠ 灰色 + 提示"重新选择", 不阻断其他章节 |
| **E010** | 邀请已处理 | 友好提示"已处理" |
| **E011** | 邀请过期 | 提示"已过期, 请联系 LEAD 重新邀请" |
| **E012** | 跨院系无访问权 | 弹费用确认, 必须勾选"我已知晓费用" |
| **E020** | **新增**: 接受时 source_chapter_id 不属于 source_course_id | DB 触发器 422 |
| **E021** | **新增**: 接受时 chapterId 不属于邀请的分配 | 422 "章节与邀请不匹配" |
| **E022** | **新增**: 接受时该邀请的 frozen_price 与实际不符 | 客户端忽略, 服务端重读 |

---

## 6. 实施计划（4 阶段 - v2 调整）

### Phase 1: Step 2 章节化（核心表 + 数据完整性）
- DB V107: `proposal_chapters` + `chapter_teacher_assignments` (含全部 CHECK/UNIQUE/trigger/索引)
- 后端: 增 chapterAssignments CRUD + 教师 ID 验证 (P2 #16)
- 前端: Step 2 改造 (v2 拆分步骤) + 课程表展开行
- 联动: 课程门数/总学分/总学时自动计算 (v1 已有)

### Phase 2: Step 3 章节分配
- DB: V107.1 (依赖检查, V107 已含逻辑)
- 后端: chapter-assignments CRUD + 提案/课程/章节归属验证 (P1 #3, #4)
- 前端: Step 3 教学团队 + Drawer (v2 增强)

### Phase 3: 邀请流程（最重要）
- 后端: **新端点** `/accept-with-chapters` (P1 #6 向后兼容) + 服务端价格快照 (P1 #5) + 跨院系流程 (P1 #10)
- 前端: MicroSpecialtyInvites.vue 重写为分步向导
- 搜索: `/courses/chapters/search` 权限收紧 (P1 #1) + 防抖

### Phase 4: 课程定价
- DB V108: courses 加定价字段 (含 CHECK 约束)
- 后端: `pricing-for-adopter` 权限 (P1 #9)
- 前端: CourseForm.vue 加定价区块

每个 Phase 完成后：
1. 跑 `bash scripts/local-dev-deploy.sh` (15/15 PASS)
2. 用户在 `http://localhost:5173` 走完完整流程
3. 修复所有 console error/warning
4. 通过 `deploy-gate.sh check` 后部署到生产
5. commit + push

---

## 7. 不做（YAGNI）

| 项 | 原因 | v1→v2 变化 |
|----|------|------------|
| 章节独立定价 | 章节价格继承课程 | 保留 |
| `crossDeptFeeType: fixed_fee` | v2 再说 | 保留 |
| 批量"全部设为新建" | v2 **包含**（用户会拒绝）| v1 删除 → **v2 保留** |
| "我已知晓费用"确认复选框 | v2 必须 | 保留 |
| 章节删除的复杂确认 | 弹"已分配 N 教师, 确认删除?" | v2 **做简单确认**（不能太粗放）|
| 移动端优化 | v2 至少保证可用 | 保留 |
| 接受时部分接受 | 教学场景下需要全部接受 | 保留（v2 改进为向导） |
| 章节学习进度跟踪 | 不在范围 | 保留 |
| 跨院系版本号 | v1 冻结价格, v2 改为服务端读取 | 保留 |

---

## 8. 风险（v2 更新）

| 风险 | v2 应对 |
|------|------|
| 大量已有章节搜索性能 | 索引 + 全文搜索 v2 |
| 老师选"新建"漏填必填 | 服务端 422 + 客户端提前验证 |
| 已选章节被原作者下架 | EC-1 软删除 + 提示 |
| 跨院系费用纠纷 | 服务端重读 + 快照 frozen_price |
| 同一老师重复邀请 | 防重检查 |
| **NEW**: 提案撤回时级联清理 | BR-8 + 软删除 |
| **NEW**: 接受非幂等 | 事务 + 版本锁 |
| **NEW**: 已有 `acceptInvite` 兼容 | 保留旧端点, 新增 `/accept-with-chapters` |
| **NEW**: 章节决策未验证归属 | 服务端强验证 |
| **NEW**: UNIQUE 包含 source 导致重复 | 改为 UNIQUE(chapter_id, teacher_id) |
| **NEW**: teacher_id CASCADE 与领域不一致 | 改为 RESTRICT |

---

## 9. 决策记录（v2 更新）

| 决策 | v1 | v2 |
|------|----|----|
| 分配粒度 | 章节 | 不变 |
| 来源决策 | 被邀请老师 | 不变 |
| 搜索范围 | 全平台 | 收紧为 TEACHER 角色 + 防爬 |
| 多人教同一课 | 支持 | 不变 |
| 接受规则 | 全部接受 | 不变 (向导式 UX) |
| UI 模式 | 行式 + Drawer | v2 增强:章节摘要始终可见 |
| 课程定价 | 简化 | v2: 客户端 + 服务端双验证 |
| 价格快照 | frozen_price | v2: **服务端重读**, DEFAULT NULL |
| 实施分阶段 | 4 阶段 | 不变, 调整每阶段范围 |
| 撤回清理 | 未定义 | v2: BR-8 软删除所有相关记录 |
| 接受幂等 | 未考虑 | v2: 事务 + 版本锁 + 状态检查 |
| 兼容性 | 假设兼容 | v2: 保留旧 `/accept`, 新增 `/accept-with-chapters` |
| 数据完整性 | 基础 | v2: 加 CHECK/触发器/JSONB/version |

---

## 10. 关键 P0 修复（来自 4 个 agent 审查）

| ID | 问题 | 修复 |
|----|------|------|
| **#1 P0-1** | 缺少"审批后章节物化"路径 | **见 §11** |
| **#2 P0-2** | Accept 时教师决策未验证章节分配 | 服务端批量查询, 严格匹配 |
| **#3 P0-3** | 缺 `version` 乐观锁 | 加 `version` 列 + 实体 `@Version` |
| **#4 P0-4** | 提案撤回未级联清理 | BR-8: 软删除 chapter_teacher_assignments |
| **#5 数据 P0-1** | UNIQUE 约束包含 source 导致重复 | 改为 `UNIQUE(chapter_id, teacher_id)` + 部分索引 |
| **#6 数据 P0-2** | teacher_id CASCADE 与领域不一致 | 改为 `ON DELETE RESTRICT` |

---

## 11. 审批后章节物化（修复 P0-1 - 缺失的关键流程）

### 问题
v1 设计只关注"邀请时分配章节", 但**没有定义审批通过后这些章节如何变成真实可教学的课程**。

### 流程

```
申报提交 (PENDING_REVIEW)
    ↓ 教务处审批
APPROVED
    ↓
[Phase X: 物化]  ←  v2 新增的 Phase 5
    ├─ 对每门 proposal_course:
    │   ├─ 检查是否已有"已有章节"决策的 source_course_id
    │   ├─ 是: 在 micro_specialty_courses 创建引用
    │   └─ 否: 在 courses 创建新课程, 在 micro_specialty_courses 引用
    ├─ 对每章 proposal_chapter:
    │   ├─ source=existing: course_chapter 已存在, 在 micro_specialty_courses 引用
    │   ├─ source=new 且教师已提交 newChapterData: 创建 course_chapter, 在 ms_course_chapters 引用
    │   └─ source=TBD: 标记 PENDING_FINALIZE, 教师须在微专业创建后补充
    └─ 对每条 chapter_teacher_assignment (accept_status=ACCEPTED):
        └─ 在 micro_specialty_teachers 确认激活
```

### 关键设计

1. **新建 chapter_teacher_assignments.accept_status = 'FINALIZE_REQUIRED'** 状态
2. **新表** `micro_specialty_course_chapters` (M:N 关系, 引用 micro_specialty_courses 和 course_chapters)
3. **后端 Phase 5** 服务: `MicroSpecialtyMaterializationService.materialize(proposalId)`
4. **触发时机**: 审批通过后立即执行
5. **失败处理**: 事务回滚, 提案状态回到 PENDING_REVIEW, 错误信息提示"章节源决策不完整, 请补充 TBD 项"

### 兼容旧版

v1 之前的 MicroSpecialtyTeacher (V82) 表没有"课程分配"概念。本次物化产生的数据**不修改** MicroSpecialtyTeacher 旧字段, 而是新增 chapter_teacher_assignments 表兼容。

### 后端表新增（V108.1 - Phase 5 配合）

```sql
-- micro_specialty_course_chapters: 微专业内章节归属
CREATE TABLE IF NOT EXISTS micro_specialty_course_chapters (
    id                  BIGSERIAL    PRIMARY KEY,
    micro_specialty_id  BIGINT       NOT NULL REFERENCES micro_specialties(id) ON DELETE CASCADE,
    course_id           BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    chapter_id          BIGINT       NOT NULL REFERENCES course_chapters(id) ON DELETE CASCADE,
    source              VARCHAR(20)  NOT NULL,  -- 'from_proposal' | 'new' | 'existing'
    proposal_chapter_id BIGINT       REFERENCES proposal_chapters(id),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ms_cc UNIQUE (micro_specialty_id, chapter_id)
);
```

### 5 阶段计划（v2 最终版）

| Phase | 范围 | 关键修复 |
|-------|------|----------|
| 1 | Step 2 章节化 (V107 + V107.1) | 数据完整性 (CHECK/UNIQUE/触发器/version) |
| 2 | Step 3 章节分配 | 验证 (chapterId/proposalId/teacherId) |
| 3 | 邀请 + 来源决策 | 兼容 + 服务端价格快照 + 跨院系 |
| 4 | 课程定价 | CHECK/JSONB + 权限 |
| **5 (新增)** | **审批后物化** | micro_specialty_course_chapters 表 + materialize() 服务 |

---

## 12. 审查摘要

**4 个 agent 并行审查发现**:
- 架构师: 6 P0 + 多 P1/P2, 重点: 材料化路径、竞态
- 数据: 2 P0 + 5 P1 + 6 P2, 重点: UNIQUE/CHECK/JSONB/version
- UX: 多 P1-C + P2, 重点: 流程效率、邀请者体验
- 安全/后端: 多 P1, 重点: 验证、权限、兼容

**v2 全部修复了 P0**。P1 已通过 v2 改造解决大部分。P2 列入 v3 路线图。

**v2 状态**: 准备进入 Phase 1 实施。等用户确认。