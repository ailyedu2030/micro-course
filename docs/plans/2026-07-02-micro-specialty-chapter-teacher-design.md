---
title: 微专业章节-教师分配与课程来源决策设计
date: 2026-07-02
status: validated
author: chief engineer
tags: [micro-specialty, chapter-assignment, course-sourcing, pricing]
---

# 微专业章节-教师分配与课程来源决策设计

## 1. 概述

### 1.1 问题

微专业申报流程中，课程需要分配给具体教师。当前系统没有章节级别的教师分配，老师也无法决定"使用已有课程"还是"新建课程"。

### 1.2 目标

让专业负责人在申报时按章节分配教师，被邀请的老师在接收时自主选择课程来源（已有/新建）。

### 1.3 用户角色与职责

| 角色 | 职责 | 不做 |
|------|------|------|
| 专业负责人 | 填课程概念+章节+分配教师，提交申报 | 不搜索课程库，不决定课程来源 |
| 被邀请教师 | 接收邀请时选每章节来源 | — |
| 课程创建者 | 创建课程时设定价/免费/优惠规则 | — |

### 1.4 核心业务规则

| ID | 规则 |
|----|------|
| BR-1 | 教师接受邀请时必须为每章节选来源（已有/新建） |
| BR-2 | 课程分配粒度为**章节**（不是课程） |
| BR-3 | 一门课可被多位老师共同教不同章节 |
| BR-4 | 同一章节可被多位老师分别认领 |
| BR-5 | 老师必须为所有分配章节做来源决策才能完成接受 |
| BR-6 | 跨院系选课弹费用提示 |
| BR-7 | 接受时 snapshot 当前价格到 `frozen_price`，后续调价不影响已接受的老师 |

### 1.5 验收标准

- [ ] 申报 Step 2 课程可拆为多个章节
- [ ] 申报 Step 3 团队成员可分配具体章节
- [ ] 邀请老师时分配章节
- [ ] 老师接受邀请时为每章节选来源（已有/新建）
- [ ] 选"已有"时搜索全平台章节，显示费用提示
- [ ] 选"新建"时填写章节详情
- [ ] 一门课多章节被不同老师认领
- [ ] 跨院系显示费用提示并快照到 `frozen_price`
- [ ] 课程定价后已接受老师的价格不变

---

## 2. 数据模型

### 2.1 新增表（迁移 V107）

```sql
-- V107__chapter_teacher_assignment.sql

-- 1. proposal_chapters — 申报中的章节
CREATE TABLE IF NOT EXISTS proposal_chapters (
    id              BIGSERIAL    PRIMARY KEY,
    proposal_id     BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    course_id       BIGINT       NOT NULL REFERENCES proposal_courses(id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    sort_order      INTEGER      DEFAULT 0,
    duration        INTEGER      DEFAULT 0,  -- 学时（分钟）
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pc_chapter UNIQUE (course_id, sort_order)
);
CREATE INDEX IF NOT EXISTS idx_pc_chapter_course ON proposal_chapters(course_id);
CREATE INDEX IF NOT EXISTS idx_pc_chapter_proposal ON proposal_chapters(proposal_id);

-- 2. chapter_teacher_assignments — 章节-教师映射（核心表）
CREATE TABLE IF NOT EXISTS chapter_teacher_assignments (
    id                  BIGSERIAL    PRIMARY KEY,
    proposal_id         BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    course_id           BIGINT       NOT NULL REFERENCES proposal_courses(id) ON DELETE CASCADE,
    chapter_id          BIGINT       NOT NULL REFERENCES proposal_chapters(id) ON DELETE CASCADE,
    teacher_id          BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 来源决策（教师接受时填写，TBD=待定）
    source              VARCHAR(20)  NOT NULL DEFAULT 'TBD',
    -- TBD | existing | new

    -- 已有课程/章节引用
    source_course_id    BIGINT       REFERENCES courses(id),
    source_chapter_id   BIGINT       REFERENCES course_chapters(id),

    -- 接受状态
    accept_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | ACCEPTED | DECLINED | REVOKED | LEFT
    accepted_at         TIMESTAMP,

    -- 价格快照（BR-7）
    frozen_price        DECIMAL(10,2) DEFAULT 0,

    -- 教学分工描述
    responsibility      VARCHAR(500),

    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP,

    CONSTRAINT uk_cta_chapter_teacher UNIQUE (chapter_id, teacher_id, source)
);
CREATE INDEX IF NOT EXISTS idx_cta_teacher ON chapter_teacher_assignments(teacher_id);
CREATE INDEX IF NOT EXISTS idx_cta_chapter ON chapter_teacher_assignments(chapter_id);
CREATE INDEX IF NOT EXISTS idx_cta_proposal ON chapter_teacher_assignments(proposal_id);
```

### 2.2 课程定价扩展（迁移 V108）

```sql
-- V108__course_pricing.sql

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  free_access_scope   VARCHAR(20) NOT NULL DEFAULT 'none';
-- none | same_department | same_college | same_school

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  free_dept_ids       TEXT;  -- JSON 数组 [1,2,3]

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  discount_scope      VARCHAR(20) NOT NULL DEFAULT 'none';
-- none | same_college | same_school

ALTER TABLE courses ADD COLUMN IF NOT EXISTS
  discount_percent    INTEGER NOT NULL DEFAULT 0;  -- 0-100
```

### 2.3 数据关系图

```
micro_specialty_proposals
    ↓ 1:N
proposal_courses
    ↓ 1:N
proposal_chapters              ← 申报阶段定义
    ↓ N:M (chapter_teacher_assignments)
users (teachers)
    + source/source_chapter_id   ← 接受时教师决策
    + frozen_price               ← BR-7 价格快照
    + accept_status              ← 接受状态

course_chapters                ← 已有章节
courses                         ← 课程（含定价）
```

---

## 3. API 端点

### 3.1 提案阶段（Phase 1 + 2）

```
GET  /api/proposals/{proposalId}/courses
     响应: List<ProposalCourseDTO>  (含 chapters 字段)

POST /api/proposals/{proposalId}/courses/{courseId}/chapters
     body: { title, description, duration, sortOrder }
     响应: ProposalChapterDTO

DELETE /api/proposals/{proposalId}/courses/{courseId}/chapters/{chapterId}

POST /api/proposals/{proposalId}/chapter-assignments
     body: { teacherId, chapterIds: [1, 2, 3] }
     响应: List<ChapterTeacherAssignmentDTO>
     权限: proposal owner

DELETE /api/proposals/{proposalId}/chapter-assignments/{id}
```

### 3.2 邀请阶段（Phase 3）

```
POST /api/micro-specialty-teachers/invite
     body: {
       microSpecialtyId,
       teacherId,
       role,                   // LEAD | MEMBER | ASSISTANT
       chapterIds: [1, 2, 3]   // 新增
     }
     响应: InviteDTO

GET  /api/micro-specialty-teachers/pending-invites
     响应: List<InviteDTO>
     每条含 assignedChapters 详情

GET  /api/courses/chapters/search
     query: { keyword, page, size, excludeCourseId }
     响应: { items: [{chapterId, courseId, courseTitle, 
                       chapterTitle, duration, deptName,
                       price, finalPrice, sameDept, sameSchool}], 
              total }
     权限: 已登录用户

POST /api/micro-specialty-teachers/{inviteId}/accept
     body: {
       chapterDecisions: [
         { chapterId, source: 'existing', sourceChapterId: 42 },
         { chapterId, source: 'new', newChapterData: { title, duration, description, sortOrder } }
       ]
     }
     业务: BR-1, BR-5, BR-7
     响应: { teacherId, microSpecialtyId, acceptedChapters: [...] }

POST /api/micro-specialty-teachers/{inviteId}/decline
     body: { reason }
```

### 3.3 定价阶段（Phase 4）

```
PUT  /api/courses/{courseId}/pricing
     body: { basePrice, freeAccessScope, freeDeptIds,
             discountScope, discountPercent }
     响应: PricingDTO
     权限: course owner / admin

POST /api/courses/{courseId}/pricing-for-adopter
     body: { adopterTeacherId }
     响应: { basePrice, finalPrice, sameSchool, sameDept, feeNote }
```

---

## 4. 前端组件设计

### 4.1 Step 2 课程表改造

```
┌──────┬──────┬──────┬──────┬─────────────────┐
│ 课程名 │ 学时 │ 学分 │ 学期 │ 章节/操作        │
├──────┼──────┼──────┼──────┼─────────────────┤
│ Python│  48  │  3.0 │ 第1  │ ▶ 3 章节 [编辑]  │
│ 数据  │  32  │  2.0 │ 第2  │ ▶ 2 章节 [编辑]  │
└──────┴──────┴──────┴──────┴─────────────────┘

点击 [▶] 展开后内联显示:
┌────────────────────────────────────────────┐
│ Python 基础                                  │
│ 章节:                                       │
│  ┌────┬──────────┬──────┬─────────────────┐ │
│  │ 1  │ Python简介 │ 8学时 │ [删除]          │ │
│  │ 2  │ 变量类型  │ 8学时 │ [删除]          │ │
│  │ 3  │ 控制流    │ 8学时 │ [删除]          │ │
│  │[+ 新增章节]                              │ │
│  └────┴──────────┴──────┴─────────────────┘ │
└────────────────────────────────────────────┘
```

联动:
- `课程门数` = courses.length (禁用编辑)
- `总学分` = sum(courses[i].credits) (禁用编辑)
- `总学时` = sum(courses[i].hours) (新增,显示在课程表下方)

### 4.2 Step 3 教学团队分配

```
┌────────────────────────────────────────────┐
│ 团队成员                                   │
├────────────────────────────────────────────┤
│ 1. 李教授 (●负责人)  [▶ 3 章节]  [编辑] [删除]│
│    └ Python简介, 变量类型, 控制流             │
│ 2. 王老师 (○成员)   [▶ 0 章节]  [编辑] [删除]│
│ 3. [+ 新增成员]                              │
└────────────────────────────────────────────┘

点击 [编辑] 或 [+]:
┌────────────────────────────┐
│ 为 王老师 分配章节          │
├────────────────────────────┤
│ Python 基础                  │
│   ☐ 章节1: Python简介        │
│   ☐ 章节2: 变量类型         │
│   ☐ 章节3: 控制流            │
│ 数据分析                    │
│   ☐ 章节4: 数据基础          │
│   ☐ 章节5: 案例实战          │
├────────────────────────────┤
│  [取消]  [确认分配]         │
└────────────────────────────┘
```

### 4.3 邀请接受页（MicroSpecialtyInvites.vue）

```
待处理邀请 #1:
┌──────────────────────────────────────────────┐
│ 微专业: AI 数据分析  邀请人: 李教授           │
│ 分配给您的章节:                                │
│                                              │
│ 1. Python 简介 (8学时)                        │
│    来源: (●已有) (○新建)                      │
│    [搜索平台章节...]                            │
│    → Python 基础 / Python 简介 (本校·免费) ✓  │
│                                              │
│ 2. 变量类型 (8学时)                            │
│    来源: (●已有) (○新建)                      │
│    [搜索平台章节...]                            │
│    → 未选择                                   │
│                                              │
│ 3. 控制流 (8学时)                             │
│    来源: (○已有) (●新建)                      │
│    [新建章节]                                 │
│    名称: [控制流基础]                         │
│    学时: [8]                                  │
│    描述: [流程控制基础语法]                    │
│                                              │
│ ──────────────────────────────────────────  │
│ ⚠ 必须为所有章节选择来源                       │
│ [全部设为新建] [拒绝邀请]  [接受邀请]         │
└──────────────────────────────────────────────┘
```

### 4.4 已有章节搜索

```
[搜索框: 关键词]               [🔍 搜索]
┌─────────────────────────────────────────────┐
│ Python 基础 / Python 简介                    │
│ 学时 8 · 信息技术学院 · ¥0 (本校免费)        │
│ [选择]                                       │
├─────────────────────────────────────────────┤
│ 数据结构 / 链表基础                          │
│ 学时 12 · 计算机学院 · ⚠ 跨院系 ¥30 (7 折)  │
│ [选择]                                       │
└─────────────────────────────────────────────┘
```

---

## 5. 错误处理 + 边界

| 错误码 | 场景 | 处理 |
|------|------|------|
| E001 | 接受时某章节未选来源 | 红框 + tooltip，后端 422 |
| E002 | 课程表空 | 弹"先添加课程" |
| E003 | 团队空 | 弹"至少 1 位团队成员" |
| E004 | 同一章节重复分配 | UNIQUE DB 兜底 |
| E005 | 选已有章节已下架 | 红色提示 |
| E010 | 邀请已处理 | 友好提示"已处理" |
| E011 | 邀请过期 | 提示"已过期，请联系 LEAD" |
| E012 | 跨院系无访问权 | 提示费用 + 强制选课时弹窗 |

**EC-1** 已选章节被下架: 软删除 `chapter_chapters.is_deleted=1`，老师端显示"⚠ 已下架"灰色，**不撤销**接受状态。

**EC-2** 已接受后课程调价: `frozen_price` 快照，**不重算**已接受记录。

**EC-3** 重复邀请: 发送前检查 `teacher_id+ms_id` 已有 PENDING/ACTIVE → 提示"已在团队"。

---

## 6. 实施计划（4 阶段）

### Phase 1: Step 2 章节化（核心表）
- DB V107: `proposal_chapters` 表
- 后端: `ProposalCourse` 加 chapters 字段，load/save 处理
- 前端: Step 2 课程表加展开行 + 章节子表
- 联动: 课程门数/总学分自动计算

### Phase 2: Step 3 章节分配
- DB V107: `chapter_teacher_assignments` 表
- 后端: chapterAssignments CRUD
- 前端: Step 3 教学团队 + `ChapterAssignmentDialog` 组件

### Phase 3: 邀请 + 来源决策
- DB V107: `chapter_teacher_assignments` 增 `source/source_chapter_id`
- 后端: `invite` 接受 chapterIds, `accept` 接受 chapterDecisions
- 前端: MicroSpecialtyTeamEdit 邀请弹窗加章节多选
- 前端: MicroSpecialtyInvites 重写为来源决策 UI

### Phase 4: 课程定价
- DB V108: courses 增定价字段
- 后端: `updatePricing`, `pricing-for-adopter`
- 前端: CourseForm 加定价区块
- 前端: 章节搜索结果显示费用

每个 Phase 完成后：
1. 跑 `bash scripts/local-dev-deploy.sh`（15/15 PASS）
2. 用户在 `http://localhost:5173` 走完完整流程
3. 修复所有 console error/warning
4. 通过 `deploy-gate.sh check` 后部署到生产
5. commit + push

---

## 7. 不做（YAGNI）

| 项 | 原因 |
|----|------|
| 章节独立定价 | 章节价格继承课程即可 |
| `crossDeptFeeType: fixed_fee` | v2 再加 |
| "我已知晓费用" 确认复选框 | 直接显示金额 |
| 章节删除的复杂确认 | 一次普通提示 |
| 批量"全部设为新建" | 老师手动选即可 |
| 章节学习进度跟踪 | 不在范围 |
| 移动端特殊优化 | v2 再说 |

---

## 8. 风险

| 风险 | 应对 |
|------|------|
| 大量已有章节（万级）搜索性能 | 加索引 + 全文搜索 v2 升级 ES |
| 老师选"新建"时漏填必填 | 提交前校验 + 后端 422 |
| 已选章节被原作者下架 | EC-1 软删除 + 提示 |
| 跨院系费用纠纷 | 接受时显示费用 + 快照 frozen_price |
| 同一老师重复邀请 | EC-3 防重 |
| 提案撤回时已有邀请 | 软删除 chapter_teacher_assignments |

---

## 决策记录

| 决策 | 最终方案 |
|------|------|
| 分配粒度 | 章节 |
| 来源决策 | 被邀请老师 |
| 搜索范围 | 全平台（带费用提示） |
| 多人教同一课 | 支持 |
| 接受规则 | 全部接受才能成功 |
| UI 模式 | 行式 + Drawer/Popover |
| 课程定价 | v1 简化为 basePrice + discountPercent |
| 价格快照 | frozen_price on accept (BR-7) |
| 实施分阶段 | 4 阶段（严格顺序） |
| 不做的事 | YAGNI 列表（第 7 节） |
