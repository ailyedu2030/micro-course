# 微专业（Phase 14）开发规格说明书 v1.1

> 定位：Phase 3 Mid-Level 设计文档 → Phase 5 TDD 执行准入
> 范围：微专业全场景——立项、教师团队、课程编排、修读、证书、广场展示
> 覆盖：5 张新表 + 3 张扩展、**52 个 API**、16 页面、**6 个状态机（含 LEAD 继任/编辑范围）**、**23 个通知类型**
> 逻辑闭环声明：每项"触发"必有"响应"，每项"变更"必有"通知"，每个"状态"必有"终点"
> 基线版本：v3.2-gapfix（经 4 轮审查修复全部 16+ 断裂点）
> **修复说明**：v1.0 审查发现 16 个断裂点（6P0+5P1+5P2），本版已全部修复。核心变更：REJECTED 可重提/reapply/reinvite 三重新机制 + LEAD 继任 + CANCELLED 级联 + 前置条件检查 + 已修学分认可 + 通知补齐 6 种。

---

## 目录

1. [系统架构概览](#1-系统架构概览)
2. [状态机全集](#2-状态机全集)
3. [用户角色与权限矩阵](#3-用户角色与权限矩阵)
4. [全流程用户旅程](#4-全流程用户旅程)
5. [页面/屏幕地图](#5-页面屏幕地图)
6. [数据模型 v1.0](#6-数据模型-v10)
7. [REST API 全集](#7-rest-api-全集)
8. [通知体系](#8-通知体系)
9. [关键业务逻辑](#9-关键业务逻辑)
10. [逻辑闭环自查表](#10-逻辑闭环自查表)
11. [ROADMAP & 实施节奏](#11-roadmap--实施节奏)

---

## 1. 系统架构概览

### 1.1 模块分层

```
┌──────────────────────────────────────────────────────────────┐
│  课程广场 (CourseSquare.vue)                                 │
│    ★ 微专业专区（二级 Hero）                                  │
├──────────────────────────────────────────────────────────────┤
│              │                   │                          │
│              ▼                   ▼                          │
│   MicroSpecialtyDetail      MyMicroSpecialties               │
│   (微专业详情)               (我的修读)                      │
├──────────────────────────────────────────────────────────────┤
│  教师端                      教务处端                        │
│  MicroSpecialtyList         MicroSpecialtyReview             │
│  MicroSpecialtyManage       MicroSpecialtyProposalReview     │
│  MicroSpecialtyCourseEdit   MicroSpecialtyFeaturedReview     │
│  MicroSpecialtyTeamEdit     MicroSpecialtyClassImport        │
│  MicroSpecialtyProposal     MicroSpecialtyGoldManage         │
│  MyProposals                MicroSpecialtyCrossDeptReview    │
│  MicroSpecialtyInvites                                        │
├──────────────────────────────────────────────────────────────┤
│  API 层: 52 个 REST 端点 /api/micro-specialties*             │
├──────────────────────────────────────────────────────────────┤
│  服务层:                                                     │
│  MicroSpecialtyService / MicroSpecialtyEnrollmentService     │
│  MicroSpecialtyProposalService / MicroSpecialtyInviteService │
│  MicroSpecialtyFeaturedService                               │
│  MicroSpecialtyQualityScoreService                           │
│  MicroSpecialtyProgressAggregator                            │
├──────────────────────────────────────────────────────────────┤
│  数据层: 5 张新表 + 3 张扩展 + 1 张审计                      │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 与现有系统的集成点

| 现有模块 | 集成方式 | 冲突风险 |
|---------|---------|---------|
| `courses` | `micro_specialty_courses.course_id` FK 引用 | 低：只读 |
| `users` | `lead_teacher_id` / `creator_id` / `teacher_id` FK | 低：只读 |
| `departments` | `offer_department_id` FK | 低：只读 |
| `enrollments` | 班级导入时 `MicroSpecialtyEnrollServiceImpl` 调 `EnrollmentService.createEnrollment()` | **中**：事务一致性需 @Transactional |
| `learning_progress` | 聚合 cron 只读不写 | 低 |
| `exercise_records` | 聚合 cron 只读不写 | 低 |
| `certificates` | 扩展 cert_type + micro_specialty_id，复用 PDF 下载 | **中**：现有证书查询需加 cert_type='COURSE' 过滤 |
| `notifications` | 新增 `type` 枚举值（不影响现有） | 低 |
| `attachments` | 多态型新增 `MICRO_SPECIALTY` | 低 |

---

## 2. 状态机全集

### 2.1 微专业主表状态机

```
路径A（教务处直立）:                 路径B（教师申报）:
   DRAFT                              PROPOSAL_REVIEW
     ↓ submit                            ↓ approve  → 创建 DRAFT + LEAD INVITED
   PENDING_REVIEW                       ↓ reject   → REJECTED
     ↓ approve → APPROVED               ↓ withdraw → WITHDRAWN
     ↓ reject  → REJECTED ──resubmit──→ DRAFT（修改后重提）
     ↓
   APPROVED →（LEAD 确认）→ RECRUITING
     ↓ open（LEAD 确认开课）
     ↓
   RECRUITING →（长期运行，学生报名）
     ↓ close
   COMPLETED ──archive──→ ARCHIVED
     ↓
   ↕ 任意状态 → CANCELLED（教务处强制 ──终态──）
```

**转换矩阵**：

| 当前状态 | → 目标状态 | 触发动作 | 允许角色 | 前置条件 |
|---------|-----------|---------|---------|---------|
| DRAFT | PENDING_REVIEW | submit | LEAD | ≥1 门课程编排、lead_teacher 已接受 |
| **REJECTED** | **PENDING_REVIEW** | **submit** | **LEAD** | **修改后重新提交（同 submit API，接受 REJECTED 作为 from-state）** |
| PROPOSAL_REVIEW | DRAFT | approve | ACADEMIC | 申报书通过→自动创建 DRAFT + LEAD INVITED |
| PROPOSAL_REVIEW | REJECTED | reject | ACADEMIC | 填写驳回原因 |
| PROPOSAL_REVIEW | WITHDRAWN | withdraw | 申报人 | 申报未处理前可撤回 |
| PROPOSAL_REVIEW | PENDING_REVIEW | resubmit | 申报人 | 修改申报书后重提（REJECTED→PENDING_REVIEW） |
| PENDING_REVIEW | APPROVED | approve | ACADEMIC | — |
| PENDING_REVIEW | REJECTED | reject | ACADEMIC | 填写驳回原因 |
| APPROVED | RECRUITING | open | LEAD | 课程编排完成、团队≥2（含LEAD）、LEAD已接受 |
| RECRUITING | COMPLETED | close | LEAD | 所有必修课可已结课（或强制跳过） |
| COMPLETED | ARCHIVED | archive | ACADEMIC | 自动或手动 |
| 任意 | CANCELLED | cancel | ACADEMIC | 事务内级联设置 enrollments 为 DROPPED |

> **修复说明**：`submit` API 的 from-state 扩展为 `DRAFT | REJECTED`，REJECTED 状态的重提走 submit 同一端点（业务语义一致）。proposals 也增加 `resubmit` 端点处理 REJECTED→PENDING_REVIEW。

**关键不变约束**：
- LEAD 未接受邀请前，DRAFT 不可进 PENDING_REVIEW
- 课程编排 < 1 门时不可进 RECRUITING
- RECRUITING 状态前不可设置 is_featured
- CANCELLED 后不再接受任何状态转换（终态）

### 2.2 修读记录状态机

```
PENDING ──approve──→ APPROVED ──auto──→ IN_PROGRESS ──auto──→ COMPLETED
   │                      │                      │                    │
   ↓                      ↓                      ↓                    ↓
REJECTED              DROPPED                FAILED                CERTIFIED
   │                      │                      │
   └──reapply──→ PENDING  └──reapply──→ PENDING  └──reapply──→ PENDING（重修重审）
```

> **修复说明**：所有非正常终止状态（REJECTED/DROPPED/FAILED）都有 `reapply` 出口，确保学生可重新申请或重修补考。

**转换矩阵**：

| 当前状态 | → 目标状态 | 触发 | 角色 | 条件 |
|---------|-----------|------|------|------|
| PENDING | APPROVED | approve | LEAD/ACADEMIC | — |
| PENDING | REJECTED | reject | LEAD/ACADEMIC | 填写原因 |
| APPROVED | IN_PROGRESS | 任一必修课首次 enroll（系统自动） | 系统 | — |
| IN_PROGRESS | COMPLETED | cron 聚合判定满足 completion_rule | 系统 | — |
| IN_PROGRESS | FAILED | 所有必修课最终成绩 < 60 且不可补考 | 系统 | — |
| IN_PROGRESS | DROPPED | drop | STUDENT/ACADEMIC | — |
| COMPLETED | CERTIFIED | 自动调 issue_certificate | 系统 | final_score ≥ 60 |
| **REJECTED** | **PENDING** | **reapply** | **STUDENT** | **更新条件后重新申请** |
| **DROPPED** | **PENDING** | **reapply** | **STUDENT** | **重新加入申请** |
| **FAILED** | **PENDING** | **reapply** | **STUDENT** | **重修后重新评估** |

### 2.3 教师邀请状态机

```
INVITED ──accept───→ ACTIVE（同学院）
   │                      │
   │                      ├───→ REMOVED（LEAD 移除）
   │                      └───→ REMOVED（主动退出→通知 LEAD）
   ├───→ PENDING_ACADEMIC（跨学院→教务处审批）
   │         │
   │         ├──approve──→ ACTIVE
   │         └──reject───→ REJECTED
   └───→ DECLINED（手动拒绝 或 7天超时）

REMOVED ──reinvite──→ INVITED（重新邀请，复用原记录）
DECLINED ──reinvite──→ INVITED（重新邀请，复用原记录）
```

> **修复说明**：RE-INVITE 时，若已存在 REMOVED/DECLINED 记录，复用该记录重置 `invite_status=INVITED`、`invited_at=NOW()`、`invite_expires_at=NOW()+7d`，不创建新行。唯一约束 `uk_mst_unique` 变更为部分唯一索引：`WHERE invite_status NOT IN ('DECLINED', 'REMOVED')`。

| 当前状态 | → 目标状态 | 触发 | 角色 |
|---------|-----------|------|------|
| — | INVITED | 发送邀请 | LEAD |
| INVITED | ACTIVE | accept | 被邀请人（必须本人） |
| INVITED | DECLINED | 手动/7天超时 | 被邀请人/系统定时任务 |
| INVITED | PENDING_ACADEMIC | 自动检测跨学院 | 系统 |
| PENDING_ACADEMIC | ACTIVE | approve | ACADEMIC |
| PENDING_ACADEMIC | REJECTED | reject | ACADEMIC |
| ACTIVE | REMOVED | remove | LEAD/ADMIN |
| ACTIVE | REMOVED | leave | TEACHER（主动退出） |
| **REMOVED** | **INVITED** | **reinvite** | **LEAD** | **复用记录重置状态** |
| **DECLINED** | **INVITED** | **reinvite** | **LEAD** | **复用记录重置状态** |

### 2.4 置顶审批状态机

```
NONE ──apply──→ PENDING ──approve──→ APPROVED (is_featured=TRUE)
                         └─reject───→ REJECTED
APPROVED ──unset──→ NONE
```

| 当前状态 | → 目标状态 | 触发 | 角色 |
|---------|-----------|------|------|
| NONE | PENDING | apply_featured | LEAD |
| PENDING | APPROVED | approve_featured | ACADEMIC |
| PENDING | REJECTED | reject_featured | ACADEMIC |
| APPROVED | NONE | unset_featured | ACADEMIC |

### 2.5 LEAD 继任状态机

> **新增修复**：LEAD 离职/被移除时，需有继任机制防止微专业瘫痪。

```
[原 ACTIVE LEAD] ──transfer_leadership──→ [REMOVED（原 LEAD 降为 MEMBER 或退出）]
                                                ↓
                                        [新 LEAD 激活]
                                                ↓
                                        记录变更至 micro_specialty_teachers.role
                                        更新 micro_specialties.lead_teacher_id
```

| 当前状态 | → 目标状态 | 触发 | 角色 | 条件 |
|---------|-----------|------|------|------|
| ACTIVE(原LEAD) | MEMBER 或 REMOVED | transfer_leadership | ACADEMIC | 指定一名已 ACTIVE 的 MEMBER 为新 LEAD |
| ACTIVE(新LEAD) | — | transfer_leadership | ACADEMIC | 若新 LEAD 不在团队中，自动创建 ACTIVE 记录 |

**约束变更**：
- DB 触发器 `trg_ms_one_lead` 在 transfer 事务期间允许临时 0 条 ACTIVE LEAD（延迟检查）
- 事务提交时确保恰好 1 条 ACTIVE LEAD

---

## 3. 用户角色与权限矩阵

### 3.1 涉及角色

| 角色 | 代码 | 微专业权限范围 |
|------|------|-------------|
| 学生 | STUDENT | 查看广场、查看详情、报名修读、退出修读、查我的修读、下载证书 |
| 教师（申请人） | TEACHER | 提交申报书、申报查询、受邀后接受/拒绝 |
| 教师（负责人 LEAD） | TEACHER+LEAD | 全部编排CRUD、团队管理（邀请/移除/reinvite）、提交审核/重提、开课/结业、申请置顶、审批学生报名 |
| 教师（团队成员 MEMBER） | TEACHER+MEMBER | 查看参与的微专业、受邀后可接受/拒绝、主动退出 |
| 管理员 | ADMIN | 最高管理权（CRUD 所有资源，含班级导入、金标管理、强制操作） |
| 教务处 | ACADEMIC | 审批申报/微专业/置顶/跨学院、金标管理、班级导入、LEAD继任、归档、强制取消 |

> **LEAD 和 MEMBER 是 TEACHER 的子角色**，在 `micro_specialty_teachers.role` 字段中区分。Controller 层通过 `@PreAuthorize("hasRole('TEACHER')")` + Service 层 `isLeadOf(msId, userId)` / `isMemberOf(msId, userId)` 方法实现子角色鉴权。详见 §9.12 角色鉴权规则。

### 3.2 角色与前后端映射

```
前端入口              STUDENT    TEACHER    LEAD      ACADEMIC    ADMIN
CourseSquare 专区       R          —         —         —          —
MicroSpecialtyDetail    R          R         R         R          R
MyMicroSpecialties      CRUD       —         —         —          —
MicroSpecialtyManage    —          —         CRUD      R          R
MicroSpecialtyCourseEdit —         —         CRUD      R          R
MicroSpecialtyTeamEdit  —          —         CRUD      R(审批)     R
MicroSpecialtyInvites   —          CRUD      —         —          —
MicroSpecialtyProposal  —          CRUD      —         —          —
MyProposals             —          R         —         —          —
MicroSpecialtyReview    —          —         —         CRUD       R
MicroSpecialtyProposalReview —     —         —         CRUD       R
MicroSpecialtyFeaturedReview  —    —         —         CRUD       R
MicroSpecialtyClassImport —       —         —         CRUD       R
MicroSpecialtyGoldManage —        —         —         CRUD       R
MicroSpecialtyCrossDeptReview —   —         —         CRUD       R
```

---

## 4. 全流程用户旅程

### 4.1 学生核心闭环

```
CourseSquare（课程广场）
   │
   ├─→ ★ 微专业专区（二级 Hero）
   │      ├─ 金标主推位（教务处指定，最多 2 个）
   │      ├─ 已置顶常规位（按质量分排序）
   │      └─ 新微专业（7天内保护期）
   │
   ├─→ 点击卡片进入 MicroSpecialtyDetail.vue
   │      ├─ 基本信息：封面/标题/学院/负责人
   │      ├─ 培养方案：课程列表（必修/选修标记）
   │      ├─ 教师团队：负责人 + 成员
   │      ├─ 修读要求：总学分/总学时/结业条件
   │      └─ 报名按钮（未登录→弹窗引导登录）
   │
   ├─→ 自主报名 → PENDING
   │      （或被班级批量导入 → 直接 APPROVED）
   │
   ├─→ LEAD/ACADEMIC 审批 → APPROVED
   │      （或驳回 → REJECTED→学生收到通知）
   │
   ├─→ 自动 enroll 所有必修课 → 学生收到通知
   │
   ├─→ 开始学习（课程级章节/视频/练习全复用）
   │      ├─ 每门课正常走 learning_progress
   │      ├─ MyMicroSpecialties.vue 看进度汇总
   │      └─ 练习/考试记录入 exercise_records
   │
   ├─→ 每门课 final_score 录入
   │
   ├─→ cron 聚合（每日 02:00）
   │      → credits_earned / courses_completed / final_score
   │
   ├─→ 判定满足 completion_rule → COMPLETED
   │
   └─→ 自动颁发微专业证书
         → 学生收到通知 → 在 MyMicroSpecialties 可下载 PDF
         → ★ 结业闭环完成 ★
```

### 4.2 教师（负责人 LEAD）旅程

```
[路径B] Proposals 提交 → ACADEMIC 审批通过 → 创建 DRAFT + LEAD INVITED
   │                                                      ↑
[路径A] ACADEMIC 直立 → 创建 DRAFT + LEAD INVITED ────────┘
   │
   ├─→ 收到 MS_INVITE_LEAD → MicroSpecialtyInvites
   │       → accept → ACTIVE（需 7 天内）
   │
   ├─→ MicroSpecialtyManage → 填基本信息/培养目标/准入门槛
   │
   ├─→ MicroSpecialtyCourseEdit → 编排课程
   │       → 配置必修/选修/学分/学时/通过分/建议学期
   │
   ├─→ MicroSpecialtyTeamEdit → 邀请团队成员
   │       → 同学院直接 ACTIVE
   │       → 跨学院→PENDING_ACADEMIC→审批→ACTIVE
   │       → 7 天无响应→DECLINED（系统自动）
   │
   ├─→ 所有准备完成 → submit → PENDING_REVIEW
   │
   ├─→ ACADEMIC approve → APPROVED
   │   （reject → 驳回+原因→修改后重提）
   │
   ├─→ open → RECRUITING（课程广场可见）
   │       → 可选 apply_featured（置顶申请）
   │
   ├─→ RECRUITING 期间
   │       → 收到学生报名待审批
   │       → 也可申请金标（联系教务处）
   │
   ├─→ 课程全部结课 → close → COMPLETED
   │
   └─→ 收到 MS_COMPLETED 通知
```

### 4.3 教务处旅程

```
┌─ MicroSpecialtyProposalReview
│     → approve → 自动创建 DRAFT + LEAD INVITED
│     → reject  → 通知申报人
│
├─ MicroSpecialtyReview
│     → approve → APPROVED
│     → reject  → 通知 LEAD
│
├─ MicroSpecialtyGoldManage
│     → set_gold → 课程广场金标位立即可见（最多 2 个）
│     → unset_gold → 取消金标
│
├─ MicroSpecialtyFeaturedReview
│     → approve → is_featured=TRUE
│     → reject  → 通知 LEAD
│
├─ MicroSpecialtyCrossDeptReview
│     → approve → ACTIVE
│     → reject  → 通知双方
│
├─ MicroSpecialtyClassImport
│     → 选择微专业+班级
│     → 自动批量导入（100人/批+事务）
│
├─ 强制管理
│     → cancel（任意状态→CANCELLED）
│     → archive（COMPLETED→ARCHIVED）
│
└─ 看全局统计
      → 选课率/完成率/质量分
```

---

## 5. 页面/屏幕地图

### 5.1 路由表（16 个）

| # | 路径 | 页面组件 | 角色 | 类型 |
|---|------|---------|------|------|
| 1 | `/student/courses` | `CourseSquare.vue`（★ 改造） | STUDENT | 学生 |
| 2 | `/student/micro-specialties/:id` | `MicroSpecialtyDetail.vue` | STUDENT | 学生 |
| 3 | `/student/my-micro-specialties` | `MyMicroSpecialties.vue` | STUDENT | 学生 |
| 4 | `/teacher/micro-specialties` | `MicroSpecialtyList.vue` | TEACHER/LEAD | 教师 |
| 5 | `/teacher/micro-specialties/:id/manage` | `MicroSpecialtyManage.vue` | LEAD | 教师 |
| 6 | `/teacher/micro-specialties/:id/courses` | `MicroSpecialtyCourseEdit.vue` | LEAD | 教师 |
| 7 | `/teacher/micro-specialties/:id/team` | `MicroSpecialtyTeamEdit.vue` | LEAD | 教师 |
| 8 | `/teacher/micro-specialties/invites` | `MicroSpecialtyInvites.vue` | TEACHER | 教师 |
| 9 | `/teacher/micro-specialties/proposals` | `MicroSpecialtyProposal.vue` | TEACHER | 教师 |
| 10 | `/teacher/micro-specialties/my-proposals` | `MyProposals.vue` | TEACHER | 教师 |
| 11 | `/academic/micro-specialties/review` | `MicroSpecialtyReview.vue` | ACADEMIC | 教务 |
| 12 | `/academic/micro-specialties/proposals` | `MicroSpecialtyProposalReview.vue` | ACADEMIC | 教务 |
| 13 | `/academic/micro-specialties/featured` | `MicroSpecialtyFeaturedReview.vue` | ACADEMIC | 教务 |
| 14 | `/academic/micro-specialties/cross-dept` | `MicroSpecialtyCrossDeptReview.vue` | ACADEMIC | 教务 |
| 15 | `/academic/micro-specialties/class-import` | `MicroSpecialtyClassImport.vue` | ACADEMIC | 教务 |
| 16 | `/academic/micro-specialties/gold` | `MicroSpecialtyGoldManage.vue` | ACADEMIC | 教务 |

### 5.2 页面间导航关系图

```
★ = 微专业唯一流量入口

                  CourseSquare.vue（改造：二级 Hero 专区）
                       │
                 ┌─────┴─────┐
                 │           │
          [专区卡片]    [查看更多 →]
                 │           │
                 ▼           ▼
        MicroSpecialty    （当前仅课程广场内展示所有微专业卡片）
        Detail.vue
            │
            ├── 点击"立即报名"
            │     → POST enrollments/apply
            │     → 通知 LEAD → LEAD 审批
            │
            └── 已报名 → 跳转 my-micro-specialties

        /student/my-micro-specialties
            │
            ├── 修读进度 / 课程列表 / 每门课跳转学习
            ├── 微专业综合成绩
            └── 结业证书下载（如有）


  [教师端]                                [教务处端]
  /teacher/micro-specialties              /academic/micro-specialties/
       │                                        ├── /review
       ├── /:id/manage                          ├── /proposals
       ├── /:id/courses                         ├── /featured
       ├── /:id/team                            ├── /cross-dept
       ├── /invites                             ├── /class-import
       ├── /proposal                            └── /gold
       └── /my-proposals
```

### 5.3 CourseSquare.vue 微专业专区改造规格

```
布局位置（从上到下）:

[1] Hero（"发现优质课程"）                    ← 现有，不改
[2] ★ 微专业专区（新增，全宽渐变背景）          ← 新增
    │
    ├─ 背景装饰：渐变紫色 #6366f1 → #8b5cf6
    ├─ 标题: "🎯 微专业 · 学校重点培养项目"
    ├─ 副标题: "修读多门课程获得微专业结业证书"
    ├─ [查看更多] 按钮 → 右侧，滚动容器内展示全部
    │
    ├─ 金标主推位（1-2 张大卡）
    │   ├─ 尺寸：1 个大卡（~400px×280px）+ 1 个小卡
    │   ├─ 金色徽章角标 + 🔥"学校重点推荐"
    │   ├─ 内容：封面 / 标题 / 学院 / 负责人 / 总学分
    │   └─ "立即了解 →" 按钮
    │
    ├─ 常规位（水平横滑容器）
    │   ├─ 卡片尺寸 ~260px×180px，白底 + 小阴影
    │   ├─ 封面（60%）+ 信息区（40%）
    │   ├─ 标题 / 学院 / N 门课 / 质量分热度条
    │   ├─ 新微专业保护期 7 天内显示 "NEW" 角标
    │   └─ 数据源：GET /api/micro-specialties/square
    │
    └─ 空态: "暂无微专业项目，敬请期待" + 插画

[3] 筛选卡                                    ← 现有，不动
[4] 精选推荐                                  ← 现有，不动
[5] 课程套件                                  ← 现有，不动
[6] 全部课程                                  ← 现有，不动
```

### 5.4 MicroSpecialtyDetail.vue 详情页结构

```
┌─ 顶部导航：面包屑 → 微专业 > {title}
├─ 封面横幅（大图+渐变遮罩）
├─ 基本信息行：学院 | 负责人 | 总学分 | 总学时 | 选课率
├─ 状态标签：招生中 / 已结业 / 已截止
├─ 报名/已报名按钮（粘贴 CTA，底部固定）
│
├─ ★ 培养方案（tab）
│   ├─ 修读要求卡片：
│   │   必修 X 门 / 选修 N 门 / 总学分 / 通过条件 / 建议学期
│   ├─ 课程列表（分必修/选修两组，按 sort_order 排列）：
│   │   课程标题 | 教师 | 学分 | 学时 | 建议学期 | [查看课程]
│   │
│   ├─ **课程点击规则**：
│   │   ├─ 未报名（未登录/已登录未申请）: 课程可查看概览，引导先报名微专业
│   │   ├─ 已报名 PENDING/APPROVED/IN_PROGRESS: 课程可点击跳转学习（/student/courses/{id}）
│   │   ├─ DROPPED/REJECTED/FAILED: 课程灰显，提示"需重新申请"
│   │   └─ COMPLETED/CERTIFIED: 课程可点击回顾
│   └─ [重新申请] 按钮（仅 DROPPED/REJECTED/FAILED 状态可见）
│
├─ ★ 教师团队（tab）
│   ├─ 负责人（大头像+姓名+职称+学院）
│   └─ N 名团队成员（头像/姓名/角色/归属课程/职责）
│
├─ ★ 详细介绍（tab）
│   ├─ 培养目标
│   ├─ 适合对象
│   ├─ 准入门槛
│   └─ 结业规则说明
│
└─ 底部固定栏：报名按钮 / 已报名状态 / 结业证书下载入口
```

### 5.5 MyMicroSpecialties.vue 我的修读

```
┌─ 顶部：我的微专业
├─ 统计卡片：已报名 X 个 / 进行中 Y 个 / 已结业 Z 个
├─ 修读列表（按最近学习排序）：
│   每项：
│   ├─ 封面（小图）
│   ├─ 标题 / 学院
│   ├─ 修读状态标签（进行中/已结业/已退出/已驳回/未通过）
│   ├─ 进度条 + 百分比（仅 IN_PROGRESS 显示）
│   ├─ 已获学分 / 总学分
│   ├─ [继续学习]（IN_PROGRESS）/ [查看证书]（CERTIFIED）
│   ├─ [退出修读]（APPROVED/IN_PROGRESS）
│   ├─ [重新申请]（REJECTED/DROPPED/FAILED, 调 POST .../reapply）
│   └─ FAILED 附加：显示失败原因 + [查看不合格课程] + [联系教务处]
└─ 空态：暂未报名微专业，[去课程广场看看]
```

### 5.6 教师端主页面 MicroSpecialtyList.vue

```
┌─ 顶部分组 Tabs：
│   ① 我负责的   ② 我参与的   ③ 待处理邀请（红点计数）
├─ 卡片列表：
│   封面/标题/状态/学院/选课人数/课程数
│   [管理] / [查看] / [编排课程] / [团队] 按钮
│   待审报名数（红点）
└─ [创建微专业] / [提交申报] 按钮
```

### 5.7 MicroSpecialtyInvites.vue 待处理邀请

```
┌─ 待处理邀请列表（倒计时显示）
│   每项：
│   ├─ 微专业标题 | 邀请人 | 邀请时间
│   ├─ 剩余：X 天 Y 小时（<3天红色警告）
│   ├─ 角色：负责人 / 团队成员 / 助教
│   └─ [接受] / [拒绝] 按钮
├─ 我接受过的邀请（已归档）
└─ 已过期的邀请（显示已失效）
```

---

## 6. 数据模型 v1.0

### 6.1 micro_specialties — 微专业主表

> 字段说明、索引、约束详见 [docs/数据字典.md] (待 v0.9 增量)

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK, 自增 | |
| code | code | String(30) | UNIQUE NOT NULL | 微专业代码 |
| title | title | String(200) | NOT NULL | |
| subtitle | subtitle | String(500) | | |
| cover_url | cover_url | String(500) | | |
| description | description | String(Text) | | |
| offer_department_id | offer_department_id | Long | FK→departments NOT NULL | 开课学院 |
| lead_teacher_id | lead_teacher_id | Long | FK→users NOT NULL | 负责人 |
| target_audience | target_audience | String(Text) | | 适合对象 |
| training_objective | training_objective | String(Text) | | 培养目标 |
| admission_requirement | admission_requirement | String(Text) | | 准入门槛 |
| completion_rule | completion_rule | String(20) | DEFAULT 'ALL_REQUIRED' | ALL_REQUIRED / CREDITS_MIN / MIXED |
| total_credits | total_credits | BigDecimal(6,2) | DEFAULT 0 | 总学分 |
| total_hours | total_hours | Integer | DEFAULT 0 | 总学时 |
| required_course_count | required_course_count | Integer | DEFAULT 0 | |
| elective_course_count | elective_course_count | Integer | DEFAULT 0 | |
| min_credits | min_credits | BigDecimal(6,2) | DEFAULT 0 | CREDITS_MIN时的最低学分 |
| max_students | max_students | Integer | DEFAULT 0 | 0=不限 |
| student_count | student_count | Integer | DEFAULT 0 | |
| semester | semester | String(20) | | 开设学期，如"2026-2027-1" |
| is_featured | is_featured | Boolean | DEFAULT FALSE | |
| featured_rank | featured_rank | Integer | DEFAULT 0 | |
| featured_status | featured_status | String(20) | DEFAULT 'NONE' | NONE/PENDING/APPROVED/REJECTED |
| featured_apply_at | featured_apply_at | LocalDateTime | | |
| featured_apply_reason | featured_apply_reason | String(500) | | |
| featured_approved_by | featured_approved_by | Long | FK→users | |
| featured_approved_at | featured_approved_at | LocalDateTime | | |
| is_gold_featured | is_gold_featured | Boolean | DEFAULT FALSE | |
| gold_featured_by | gold_featured_by | Long | FK→users | |
| gold_featured_at | gold_featured_at | LocalDateTime | | |
| status | status | String(20) | NOT NULL DEFAULT 'DRAFT' | 状态机（DRAFT/PENDING_REVIEW/APPROVED/REJECTED/RECRUITING/COMPLETED/CANCELLED/ARCHIVED） |
| reject_reason | reject_reason | String(500) | | |
| submitted_at | submitted_at | LocalDateTime | | |
| approved_at | approved_at | LocalDateTime | | |
| opened_at | opened_at | LocalDateTime | | |
| closed_at | closed_at | LocalDateTime | | |
| creator_id | creator_id | Long | FK→users NOT NULL | |
| created_at | created_at | LocalDateTime | NOT NULL | |
| updated_at | updated_at | LocalDateTime | NOT NULL | |
| version | version | Integer | DEFAULT 0 | |
| deleted_at | deleted_at | LocalDateTime | | 软删除 |

**索引**：
- `uk_ms_code` (UNIQUE: code)
- `idx_ms_dept` (offer_department_id)
- `idx_ms_status` (status)
- `idx_ms_lead` (lead_teacher_id)
- `idx_ms_semester` (semester)
- `idx_ms_featured` (is_featured, featured_rank) WHERE is_featured=TRUE AND deleted_at IS NULL

### 6.2 micro_specialty_courses — 课程编排

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK, 自增 | |
| micro_specialty_id | micro_specialty_id | Long | FK→micro_specialties, NOT NULL | |
| course_id | course_id | Long | FK→courses, NOT NULL | |
| sort_order | sort_order | Integer | DEFAULT 0 | |
| is_required | is_required | Boolean | DEFAULT TRUE | |
| credits | credits | BigDecimal | DEFAULT 0 | 冗余自 courses.credit_hours |
| hours | hours | Integer | DEFAULT 0 | 冗余 |
| min_score | min_score | BigDecimal | DEFAULT 60 | |
| recommended_semester | recommended_semester | String | | |
| created_at | created_at | LocalDateTime | | |

**索引**：`uk_msc_unique(micro_specialty_id, course_id)` / `idx_msc_ms` / `idx_msc_course`

### 6.3 micro_specialty_teachers — 教师团队

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK | |
| micro_specialty_id | micro_specialty_id | Long | FK→micro_specialties, NOT NULL | |
| teacher_id | teacher_id | Long | FK→users, NOT NULL | |
| role | role | String | NOT NULL | LEAD/MEMBER/ASSISTANT |
| course_id | course_id | Long | FK→courses | 归属课程（LEAD可空） |
| responsibility | responsibility | String | | |
| invite_status | invite_status | String | DEFAULT 'ACTIVE' | INVITED/ACTIVE/DECLINED/REMOVED/PENDING_ACADEMIC |
| invited_by | invited_by | Long | FK→users | |
| invited_at | invited_at | LocalDateTime | | |
| responded_at | responded_at | LocalDateTime | | |
| invite_expires_at | invite_expires_at | LocalDateTime | created_at+7天 | |
| joined_at | joined_at | LocalDateTime | | |
| left_at | left_at | LocalDateTime | | |
| created_at | created_at | LocalDateTime | | |

**约束**：DB触发器 `trg_ms_one_lead` — 每行 micro_specialty 有且仅有 1 名 `role='LEAD' AND invite_status='ACTIVE'` 的教师

**索引**：`uk_mst_active(micro_specialty_id, teacher_id, course_id) WHERE invite_status NOT IN ('DECLINED','REMOVED')` / `idx_mst_ms` / `idx_mst_teacher` / `idx_mst_role` / `idx_mst_invite_status`

> **修复**：原 `uk_mst_unique` 改为 `uk_mst_active` 部分唯一索引，排除 DECLINED/REMOVED 状态，支持教师重新邀请。索引列名从 `ms_id` 修正为 `micro_specialty_id`。

### 6.4 micro_specialty_enrollments — 修读记录

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK | |
| micro_specialty_id | micro_specialty_id | Long | FK→micro_specialties, NOT NULL | |
| user_id | user_id | Long | FK→users, NOT NULL | |
| source | source | String | NOT NULL | SELF_APPLY / CLASS_IMPORT / ADMIN_ASSIGN |
| class_id | class_id | Long | FK→classes | 班级导入时归属班级 |
| status | status | String | DEFAULT 'PENDING' | |
| progress | progress | BigDecimal(5,2) | DEFAULT 0 | 0-100 |
| credits_earned | credits_earned | BigDecimal | DEFAULT 0 | |
| courses_completed | courses_completed | Integer | DEFAULT 0 | |
| courses_required | courses_required | Integer | DEFAULT 0 | |
| final_score | final_score | BigDecimal | | |
| final_grade | final_grade | String | | EXCELLENT/GOOD/PASS/FAIL |
| certificate_id | certificate_id | Long | FK→certificates, nullable | COMPLETED 前为 NULL，结业后回写 |
| applied_at | applied_at | LocalDateTime | | |
| approved_at | approved_at | LocalDateTime | | |
| approved_by | approved_by | Long | FK→users | |
| completed_at | completed_at | LocalDateTime | | |
| dropped_at | dropped_at | LocalDateTime | | |
| drop_reason | drop_reason | String | | |
| created_at | created_at | LocalDateTime | | |
| updated_at | updated_at | LocalDateTime | | |
| version | version | Integer | DEFAULT 0 | |

**索引**：`uk_mse_active(micro_specialty_id, user_id) WHERE status NOT IN ('REJECTED','DROPPED','FAILED')` / `idx_mse_user` / `idx_mse_ms` / `idx_mse_status` / `idx_mse_class`

> **修复**：原 `uk_mse_unique` 改为 `uk_mse_active` 部分唯一索引，排除 REJECTED/DROPPED/FAILED 状态，支持学生重新申请。索引列名从 `ms_id` 修正为 `micro_specialty_id`。

### 6.5 micro_specialty_proposals — 微专业申报表

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK | |
| proposer_id | proposer_id | Long | FK→users, NOT NULL | |
| title | title | String | NOT NULL | |
| description | description | String(Text) | | |
| offer_department_id | offer_department_id | Long | FK→departments, NOT NULL | |
| training_objective | training_objective | String(Text) | | |
| semester | semester | String | | |
| max_students | max_students | Integer | DEFAULT 0 | |
| status | status | String | DEFAULT 'PENDING_REVIEW' | PENDING_REVIEW/APPROVED/REJECTED/WITHDRAWN |
| review_comment | review_comment | String | | |
| reviewed_by | reviewed_by | Long | FK→users | |
| reviewed_at | reviewed_at | LocalDateTime | | |
| created_micro_specialty_id | created_micro_specialty_id | Long | FK→micro_specialties | 批准后自动创建 |
| created_at | created_at | LocalDateTime | | |
| updated_at | updated_at | LocalDateTime | | |

### 6.6 micro_specialty_featured_audit — 置顶审计表

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK | |
| micro_specialty_id | micro_specialty_id | Long | FK→micro_specialties, NOT NULL | |
| operator_id | operator_id | Long | FK→users, NOT NULL | |
| action | action | String | NOT NULL | APPLY/APPROVE/REJECT/GOLD_SET/GOLD_UNSET |
| before_value | before_value | String(JSONB) | | 变更前快照（JSONB，支持索引和查询） |
| after_value | after_value | String(JSONB) | | 变更后快照（JSONB，支持索引和查询） |
| reason | reason | String | | |
| created_at | created_at | LocalDateTime | | |

### 6.7 certificates 表扩展（V83 migration）

```sql
-- V83__cert_micro_specialty.sql
ALTER TABLE certificates
    ADD COLUMN cert_type VARCHAR(20) NOT NULL DEFAULT 'COURSE',
    ADD COLUMN micro_specialty_id BIGINT REFERENCES micro_specialties(id),
    ALTER COLUMN course_id DROP NOT NULL,
    ADD CONSTRAINT chk_cert_xor CHECK (
        (cert_type = 'COURSE' AND course_id IS NOT NULL AND micro_specialty_id IS NULL)
     OR (cert_type = 'MICRO_SPECIALTY' AND micro_specialty_id IS NOT NULL AND course_id IS NULL)
    ),
    ADD CONSTRAINT uk_cert_ms UNIQUE (cert_type, micro_specialty_id, user_id);
```

### 6.8 统计算法

#### 进度聚合（每日 02:00 cron）

```
progress = AVG(每门必修课 enrollment.progress × 100)
credits_earned = SUM(WHERE is_required AND course_enrollment.final_score >= micro_specialty_courses.min_score THEN credits ELSE 0)
courses_completed = COUNT(WHERE is_required AND course_enrollment.final_score IS NOT NULL)
final_score = 0.7 × AVG(必修 final_score) + 0.3 × AVG(已通过选修 final_score)
final_grade = final_score >= 90 → EXCELLENT; >= 75 → GOOD; >= 60 → PASS; ELSE → FAIL
completion = CASE completion_rule
   WHEN 'ALL_REQUIRED' THEN courses_completed >= required_course_count
   WHEN 'CREDITS_MIN'  THEN credits_earned >= min_credits
   WHEN 'MIXED'        THEN courses_completed >= required_course_count AND credits_earned >= min_credits
END
IF completion = TRUE → status = COMPLETED, 调 issueMicroSpecialtyCertificate()
```

#### 质量分（课程广场排序，缓存 1 小时）

```
quality_score = enrollmentRate × 0.5 + completionRate × 0.3 + (avgRating/5) × 0.2
enrollmentRate = min(studentCount / maxStudents, 1)
completionRate = COUNT(COMPLETED) / MAX(COUNT(IN_PROGRESS+COMPLETED), 1)
avgRating = AVG(course_reviews.rating WHERE course IN (ms courses))
```

---

## 7. REST API 全集

### 7.1 微专业主表（14 个）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/micro-specialties` | 已认证 | 分页列表（学生看 RECRUITING；ACADEMIC/ADMIN 看全部） |
| GET | `/api/micro-specialties/square` | 公开 | **课程广场专区专用**：返回 goldFeatured + featured + recruiting 三组 |
| GET | `/api/micro-specialties/{id}` | 公开 | 详情（含已编排课程+教师团队+stats） |
| POST | `/api/micro-specialties` | ACADEMIC | 教务处直立创建（DRAFT） |
| PUT | `/api/micro-specialties/{id}` | LEAD/ADMIN | 更新基本信息（REJECTED 状态下也可编辑） |
| DELETE | `/api/micro-specialties/{id}` | ADMIN | 软删除 |
| POST | `/api/micro-specialties/{id}/submit` | LEAD | **提交/重新提交审核**（DRAFT→PENDING_REVIEW, REJECTED→PENDING_REVIEW） |
| POST | `/api/micro-specialties/{id}/approve` | ACADEMIC | 审批通过→APPROVED（自动通知 LEAD） |
| POST | `/api/micro-specialties/{id}/reject` | ACADEMIC | 审批驳回→REJECTED（自动通知 LEAD） |
| POST | `/api/micro-specialties/{id}/open` | LEAD | 开课→RECRUITING（自动通知所有学生微专业可报名） |
| POST | `/api/micro-specialties/{id}/close` | LEAD | 结业→COMPLETED |
| POST | `/api/micro-specialties/{id}/cancel` | ACADEMIC | 强制取消→CANCELLED（级联设置 enrollments 为 DROPPED + 通知所有相关人） |
| POST | `/api/micro-specialties/{id}/archive` | ACADEMIC | 归档（COMPLETED→ARCHIVED，通知 LEAD） |
| POST | `/api/micro-specialties/{id}/transfer-leadership` | ACADEMIC | LEAD 继任：指定新 LEAD（事务内转移，自动调整 role 和 lead_teacher_id） |

### 7.2 申报（5 个）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/micro-specialty-proposals` | TEACHER | 提交申报 |
| GET | `/api/micro-specialty-proposals/my` | TEACHER | 我的申报 |
| GET | `/api/micro-specialty-proposals` | ACADEMIC | 所有待审申报 |
| POST | `/api/micro-specialty-proposals/{id}/approve` | ACADEMIC | 批准→创建 DRAFT + LEAD INVITED |
| POST | `/api/micro-specialty-proposals/{id}/reject` | ACADEMIC | 驳回（填写原因） |
| POST | `/api/micro-specialty-proposals/{id}/withdraw` | TEACHER(本人) | 撤回申报（仅 PENDING_REVIEW 状态）|
| POST | `/api/micro-specialty-proposals/{id}/resubmit` | TEACHER(本人) | 重提申报（仅 REJECTED 状态→PENDING_REVIEW） |

### 7.3 课程编排（4 个）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/micro-specialties/{id}/courses` | 公开 | 课程列表 |
| POST | `/api/micro-specialties/{id}/courses` | LEAD | 添加课程 |
| PUT | `/api/micro-specialties/{id}/courses/{itemId}` | LEAD | 更新排序/必修/学分 |
| DELETE | `/api/micro-specialties/{id}/courses/{itemId}` | LEAD | 移除课程 |

### 7.4 教师团队（8 个）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/micro-specialties/{id}/teachers` | 公开 | 教师团队列表 |
| POST | `/api/micro-specialties/{id}/teachers` | LEAD | 发送邀请→自动判断跨学院 |
| DELETE | `/api/micro-specialties/{id}/teachers/{teacherId}` | LEAD/ADMIN | 移除教师（发通知） |
| GET | `/api/micro-specialty-teachers/pending-invites` | TEACHER | 我的待处理邀请（含倒计时） |
| POST | `/api/micro-specialty-teachers/{inviteId}/accept` | TEACHER(本人) | 接受邀请→ACTIVE/跨学院→PENDING_ACADEMIC |
| POST | `/api/micro-specialty-teachers/{inviteId}/decline` | TEACHER(本人) | 拒绝邀请 |
| POST | `/api/micro-specialty-teachers/{inviteId}/reinvite` | LEAD | 重新邀请（复用 REMOVED/DECLINED 记录重置状态） |
| POST | `/api/micro-specialty-teachers/{inviteId}/leave` | TEACHER(本人) | 主动退出团队→REMOVED（通知 LEAD） |
| POST | `/api/micro-specialty-teachers/{inviteId}/review-cross-dept` | ACADEMIC | 跨学院审批（请求体传 action=approve\|reject） |

### 7.5 修读（8 个）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/micro-specialty-enrollments/apply` | STUDENT | 自主报名→PENDING |
| POST | `/api/micro-specialty-enrollments/{id}/reject` | LEAD/ACADEMIC | 驳回报名（通知学生） |
| POST | `/api/micro-specialty-enrollments/class-import` | ACADEMIC/ADMIN | 班级批量导入→APPROVED（含前置条件检查+已存在学生自动跳过） |
| GET | `/api/micro-specialty-enrollments/my` | STUDENT | 我的修读列表（含 FAILED 原因+不合格课程） |
| GET | `/api/micro-specialties/{id}/enrollments` | LEAD/ACADEMIC | 修读名单 |
| POST | `/api/micro-specialty-enrollments/{id}/approve` | LEAD/ACADEMIC | 审批学生报名 |
| POST | `/api/micro-specialty-enrollments/{id}/drop` | STUDENT(本人)/ADMIN | 退出修读（级联清除课程级 enrollment，STUDENT 本人+ADMIN 可操作） |
| POST | `/api/micro-specialty-enrollments/{id}/reapply` | STUDENT(本人) | 重新申请（REJECTED/DROPPED/FAILED→PENDING，本人操作） |

### 7.6 置顶（5 个）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/micro-specialties/{id}/apply-featured` | LEAD | 申请置顶→PENDING |
| POST | `/api/micro-specialties/{id}/approve-featured` | ACADEMIC | 批准置顶→APPROVED（is_featured=TRUE） |
| POST | `/api/micro-specialties/{id}/reject-featured` | ACADEMIC | 驳回→REJECTED（通知 LEAD） |
| POST | `/api/micro-specialties/{id}/set-gold-featured` | ACADEMIC | 设金标（全校最多 2 个） |
| POST | `/api/micro-specialties/{id}/unset-gold-featured` | ACADEMIC | 取消金标 |
| POST | `/api/micro-specialties/{id}/unset-featured` | ACADEMIC | 取消置顶（APPROVED→NONE, is_featured=FALSE） |

### 7.7 统计 & 证书（3 个）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/micro-specialties/{id}/stats` | 已认证 | 统计数据（选课率/完成率/平均分/质量分） |
| POST | `/api/micro-specialty-enrollments/{id}/issue-certificate` | LEAD/ACADEMIC | 手动颁发证书 |
| GET | `/api/certificates/my?type=MICRO_SPECIALTY` | STUDENT | 我的微专业证书列表（复用 `/api/certificates/my` 统一入口，type 参数过滤） |

---

## 8. 通知体系

### 8.1 通知类型（23 种）

> 扩展 `notifications.type` 枚举（不影响现有类型）。**修复：补齐 submit/approve/open/archive 等 6 处缺失通知。**

| # | 通知类型 | 触发时机 | 接收人 | 说明 |
|---|---------|---------|--------|------|
| 1 | MS_INVITE_LEAD | 教务处批准后自动发送 | 被指定 LEAD | 7天内接受邀请 |
| 2 | MS_INVITE_TEAM | LEAD 邀请团队成员 | 被邀请教师 | 加入团队邀请 |
| 3 | MS_INVITE_EXPIRED | 7天未响应 | LEAD + ACADEMIC | 邀请过期 |
| 4 | MS_INVITE_CROSS_DEPT | 跨学院教师需审批 | ACADEMIC | 跨学院审批请求 |
| 5 | MS_PROPOSAL_APPROVED | 申报批准 | 申报人 | 申报已批准，可开始编排 |
| 6 | MS_PROPOSAL_REJECTED | 申报驳回 | 申报人 | 申报被驳回+原因，可 resubmit |
| **7** | **MS_SUBMITTED** | **LEAD 提交审核（DRAFT→PENDING_REVIEW）** | **ACADEMIC** | **微专业待审批** |
| **8** | **MS_APPROVED** | **微专业审批通过（PENDING_REVIEW→APPROVED）** | **LEAD** | **微专业已通过审批，可开课** |
| 9 | MS_FEATURED_APPROVED | 置顶审批通过 | LEAD | 课程广场已展示 |
| 10 | MS_FEATURED_REJECTED | 置顶审批驳回 | LEAD | 置顶被驳回+原因 |
| **11** | **MS_OPENED** | **微专业开课（APPROVED→RECRUITING）** | **LEAD** | **微专业已开放报名** |
| 12 | MS_ENROLLMENT_APPROVED | 报名审批通过 | STUDENT | 已加入微专业 |
| 13 | MS_ENROLLMENT_REJECTED | 报名审批驳回 | STUDENT | 报名被驳回+原因 |
| 14 | MS_ENROLLMENT_AUTO_ENROLL | 班级批量导入 | STUDENT | 被加入微专业 |
| **15** | **MS_ENROLLMENT_REAPPLIED** | **重新申请成功** | **LEAD + STUDENT** | **学生重新申请微专业待审** |
| **16** | **MS_ENROLLMENT_FAILED** | **修读状态 FAILED** | **STUDENT** | **微专业未通过，查看不合格课程** |
| 17 | MS_CERTIFICATE_ISSUED | 结业证书自动颁发 | STUDENT | 获得结业证书 |
| 18 | MS_COMPLETED | 微专业全部结业 | ACADEMIC | 微专业已结业统计 |
| 19 | MS_TEAM_REMOVED | 教师被移除团队 | 被移除教师 | 已从团队中移除 |
| 20 | MS_TEAM_LEFT | 教师主动退出团队 | LEAD | 团队成员退出通知 |
| 21 | MS_CANCELLED | 微专业被取消 | LEAD + 全部受影响学生 | 微专业已取消，修读终止 |
| **22** | **MS_LEAD_TRANSFERRED** | **LEAD 继任转移** | **原 LEAD + 新 LEAD** | **微专业负责人已变更** |
| **23** | **MS_ARCHIVED** | **微专业归档（COMPLETED→ARCHIVED）** | **LEAD** | **微专业已归档** |

### 8.2 教务处工作台待办聚合

```
/academic/micro-specialties/ 页面顶部：
  红点计数：待审申报 + 待审微专业 + 待审置顶 + 待审跨学院教师
  = 聚合来自 4 个独立待办列表
```

---

## 9. 关键业务逻辑

### 9.1 班级批量导入（事务 + 分批 + 前置检查）

> **修复**：自动 enroll 前增加课程前置条件检查

```
POST /api/micro-specialty-enrollments/class-import
Request: { microSpecialtyId, classId }

事务（@Transactional + 分批）:
  1. 校验 micro_specialty 存在且 status=RECRUITING（使用 SELECT ... FOR UPDATE 避免并发取消）
  2. 校验 class 存在
  3. 查 classStudents = SELECT * FROM users WHERE class_id=X AND role='STUDENT'
  4. 预查已存在的 micro_specialty_enrollments WHERE ms_id=X AND status IN ('APPROVED','IN_PROGRESS')
     → 构建已存在学生 Set，导入时跳过（日志记录"已存在"）
  5. 分批处理（每批 100 人）:
     FOR each student (NOT IN 已存在Set):
       a. 创建 micro_specialty_enrollments (status=APPROVED, source=CLASS_IMPORT, version=0)
       b. FOR 每门必修课：
             precheck = checkPrerequisites(courseId, userId):
               - 前置课程是否已修且通过
               - 课程容量是否已满
               - 是否已有有效 enrollment（去重）
               - 是否时间冲突（可选）
             IF precheck.allPassed:
               调 enrollmentService.createEnrollment(userId, courseId)
             ELSE:
               记录到 pendingCourses JSON 字段
               (学生仍可查看微专业，但该课程 enrollment 为 PENDING，需 LEAD 人工处理)
       c. 处理完一批后 flush + clear 避免长期持有 Hibernate 一级缓存
  6. 更新 micro_specialties.student_count（使用 version 乐观锁）:
      UPDATE micro_specialties SET student_count = student_count + :count, version = version + 1
      WHERE id = :msId AND version = :oldVersion
  7. 发 MS_ENROLLMENT_AUTO_ENROLL 给学生（含 pendingCourses 提示）
  8. 发通知给 LEAD："班级 {className} 已成功导入 {count} 名学生（{pendingCount} 门课程需要人工处理）"
  9. 如有超过 10% 的学生有 pendingCourses，额外通知 ACADEMIC
```

### 9.2 自动结业判定（每日 02:00 cron，分批 + 乐观锁 + 完整通知）

```
@Scheduled(cron = "0 0 2 * * ?")
aggregate():
  1. 分批查 enrollment（每批 100 行），WHERE status IN ('APPROVED', 'IN_PROGRESS')
  2. FOR each enrollment（分批内逐条处理）:
     a. 取版本快照: SELECT version FROM micro_specialty_enrollments WHERE id=:id
     b. 取每门课程对应的 enrollment.final_score（含已修但无课程级 enrollment 的旧成绩）
     c. 按 §6.8 公式计算 progress / credits_earned / courses_completed / final_score
     d. 如满足 completion_rule:
        - rows = UPDATE micro_specialty_enrollments SET status='COMPLETED',
          completed_at=NOW(), progress=:p, credits_earned=:c, final_score=:s, version=version+1
          WHERE id=:id AND version=:oldVersion
        - IF rows > 0:
            调 CertificateService.issueMicroSpecialtyCertificate(enrollment.userId, enrollment.msId, enrollment.id)
            发 MS_CERTIFICATE_ISSUED 通知
        - ELSE: 记录日志"并发跳过: enrollment.id 已被其他操作修改"
     e. 如所有必修课 fail（final_score < min_score 且无补考机会）:
        - UPDATE micro_specialty_enrollments SET status='FAILED', version=version+1
          WHERE id=:id AND version=:oldVersion
        - IF rows > 0: 发 MS_ENROLLMENT_FAILED 通知（含不合格课程列表）
     f. 否则更新 progress/credits_earned（使用 version 乐观锁）
  3. 重新校准 student_count: SELECT COUNT(DISTINCT user_id) 覆盖写入 micro_specialties
```

### 9.3 7 天邀请过期扫描（每小时 cron，分批 + 事务 + 乐观锁）

```
@Scheduled(cron = "0 0 * * * ?")
scan():
  1. 分批查 teacher，WHERE invite_status='INVITED' AND invite_expires_at < NOW()（每批 50 行）
  2. FOR each:
     a. rows = UPDATE micro_specialty_teachers SET invite_status='DECLINED',
          responded_at=NOW(), version=version+1
          WHERE id=:id AND version=:oldVersion
     b. IF rows > 0: 发 MS_INVITE_EXPIRED 通知 LEAD + ACADEMIC
     c. 若角色=LEAD: 额外告警 ACADEMIC（"负责人邀请已过期，微专业处于无 LEAD 状态"）
  3. 额外扫描: PENDING_ACADEMIC 超过 14 天未处理的记录 → 告警 ACADEMIC
```

### 9.4 LEAD 接受邀请校验

```
POST /api/micro-specialty-teachers/{inviteId}/accept
  1. 当前用户 = 被邀请的 teacher_id（身份校验）
  2. invite_status = 'INVITED' ∧ invite_expires_at > NOW()
  3. 如果是跨学院（不同 department）:
     → invite_status = 'PENDING_ACADEMIC'
     → 发 MS_INVITE_CROSS_DEPT
  4. 如果不是跨学院:
     → invite_status = 'ACTIVE'
     → 如果 role='LEAD'：更新 micro_specialties.lead_teacher_id（双重确认）
  5. 发通知给邀请人
```

### 9.5 自主报名通知链（含前置检查）

```
STUDENT 点击"报名" → POST enrollments/apply → PENDING
  → 通知 LEAD: "学生 {name} 申请加入 {title}"
  → LEAD 在列表看到待审红点
  → LEAD approve（使用 version 乐观锁）:
      → rows = UPDATE micro_specialty_enrollments SET status='APPROVED',
          approved_at=NOW(), approved_by=:leadId, version=version+1
          WHERE id=:id AND version=:oldVersion
      → IF rows == 0: 抛出乐观锁异常提示"申请已被其他操作修改"
      → FOR 每门必修课（@Transactional 确保原子性）:
            precheck = checkPrerequisites(courseId, userId)
            IF precheck.allPassed:
               调 enrollmentService.createEnrollment(userId, courseId)
            ELSE:
               记录 pendingCourses
      → 发 MS_ENROLLMENT_APPROVED（含 pendingCourses 提示）
      → UPDATE micro_specialties SET student_count = student_count + 1,
          version = version + 1 WHERE id = :msId AND version = :oldVersion
  → LEAD reject:
      → rows = UPDATE micro_specialty_enrollments SET status='REJECTED',
          version=version+1 WHERE id=:id AND version=:oldVersion
      → IF rows > 0: 发 MS_ENROLLMENT_REJECTED（含驳回原因）
  → REJECTED/DROPPED/FAILED 后 STUDENT 可 reapply:
      → POST /enrollments/{id}/reapply
      → 校验前置 (REJECTED/DROPPED/FAILED) 且用户本人
      → rows = UPDATE status='PENDING', version=version+1
          WHERE id=:id AND version=:oldVersion
      → IF rows > 0: 发 MS_ENROLLMENT_REAPPLIED 通知 LEAD + STUDENT
```

### 9.6 结业证书颁发（幂等+防编号冲突）

```
CertificateService.issueMicroSpecialtyCertificate(userId, msId, enrollmentId):
  1. 幂等检查: SELECT id FROM certificates WHERE cert_type='MICRO_SPECIALTY'
     AND micro_specialty_id=:msId AND user_id=:userId
     → 如果已存在，直接返回已有证书（不重复生成）
  2. 生成证书编号: MS-{code}-{userId}-{yyyyMM}-{randomHex(4)}
     （加入 4 位随机 hex 避免同月内重复编号冲突）
  3. 插入 certificates（带唯一约束 uk_cert_ms）:
     cert_type='MICRO_SPECIALTY', micro_specialty_id=msId, user_id=userId
     cert_code, issued_at=now()
     → 如果 uk_cert_ms 冲突，回退到 Step 2 重新生成编号（最多 3 次）
  4. 回写 micro_specialty_enrollments.certificate_id（同一事务内）:
     UPDATE micro_specialty_enrollments SET certificate_id=:certId, version=version+1
     WHERE id=:enrollmentId AND version=:oldVersion
  5. 发 MS_CERTIFICATE_ISSUED 通知
```

### 9.7 LEAD 继任逻辑

> **新增修复**（对应 P0-4）：负责人离职或需要更换时，由教务处发起转移。

```
POST /api/micro-specialties/{id}/transfer-leadership
Request: { newLeadTeacherId: Long }

 事务（@Transactional + version 乐观锁）:
   1. 校验当前 micro_specialty 状态（仅非终态可转移: DRAFT/PENDING_REVIEW/APPROVED/REJECTED/RECRUITING）:
      SELECT version FROM micro_specialties WHERE id=:id
      → 排除 CANCELLED/COMPLETED/ARCHIVED
   2. 查当前 LEAD 记录:
      SELECT FROM micro_specialty_teachers WHERE role='LEAD' AND invite_status='ACTIVE' AND ms_id=:id
      a. 如原 LEAD 已被移除（无记录符合）:
         - 跳过原 LEAD 操作，直接执行 Step 3
      b. 否则: 原 LEAD role→'MEMBER'（或 'REMOVED' 按需）
         UPDATE micro_specialty_teachers SET role='MEMBER', left_at=NOW(), version=version+1
         WHERE id=:leadId AND version=:oldVersion
   3. 查新 LEAD 是否已在团队成员中:
      a. 是: UPDATE role='LEAD', joined_at=NOW(), version=version+1
           WHERE teacher_id=:newLeadId AND ms_id=:id AND version=:oldVer
      b. 否: INSERT (ms_id, teacher_id, role='LEAD', invite_status='ACTIVE', joined_at=NOW())
   4. UPDATE micro_specialties SET lead_teacher_id=:newLeadId, version=version+1
      WHERE id=:id AND version=:oldVersion
   5. DB 触发器 trg_ms_one_lead 在此事务期间延迟检查，提交时确保恰好 1 条 ACTIVE LEAD
   6. 发 MS_LEAD_TRANSFERRED 通知原 LEAD + 新 LEAD
```

### 9.8 CANCELLED 级联变更

> **新增修复**（对应 P0-5）：取消微专业时同步处理所有修读记录。

```
POST /api/micro-specialties/{id}/cancel
事务（@Transactional + version 乐观锁）:
  1. SELECT version FROM micro_specialties WHERE id=:id（用于后续乐观锁）
  2. rows = UPDATE micro_specialties SET status='CANCELLED', closed_at=NOW(), version=version+1
     WHERE id=:id AND version=:oldVersion
  3. IF rows == 0: 抛出"微专业已被其他操作修改/取消"
  4. FOR 所有 status IN ('PENDING','APPROVED','IN_PROGRESS') 的 enrollments:
     a. UPDATE micro_specialty_enrollments SET status='DROPPED',
        drop_reason='SPECIALTY_CANCELLED', dropped_at=NOW(), version=version+1
        WHERE id=:enrollId AND version=:oldVer
     b. FOR 课程级 enrollment（仅限 COMPLETED 以外的）:
        调 enrollmentService.dropEnrollment(enrollmentId)
     c. COMPLETED 的课程 enrollment: 保留不动（成绩已固化）
  5. 发 MS_CANCELLED 通知 LEAD + 全部受影响学生
  6. 写入 micro_specialty_featured_audit (action='CANCELLED')
```

### 9.9 DROPPED 课程级联清理

> **新增修复**（对应 P1-5）：学生退出微专业时清理课程级 enrollment。

```
POST /api/micro-specialty-enrollments/{id}/drop
Request: { cascadeDropCourses?: boolean }

逻辑（@Transactional + version 乐观锁）:
  1. 校验微专业 enrollment:
     - 当前状态为 APPROVED 或 IN_PROGRESS
     - 微专业本身未处于 CANCELLED/ARCHIVED（终态不可 drop）
     - 当前用户 = enrollment.user_id（STUDENT本人）或 ADMIN
  2. SELECT version FROM micro_specialty_enrollments WHERE id=:id
  3. rows = UPDATE micro_specialty_enrollments SET status='DROPPED',
     dropped_at=NOW(), drop_reason=:reason, version=version+1
     WHERE id=:id AND version=:oldVersion
  4. IF rows == 0: 抛出乐观锁异常
  5. IF cascadeDropCourses=true OR 角色=ADMIN:
     → FOR 本微专业所有必修课的课程级 enrollment（仅 APPROVED/IN_PROGRESS）:
         调 enrollmentService.dropEnrollment(enrollmentId)
     → 记录清理日志
  6. IF cascadeDropCourses=false（学生自助退出）:
     → 仅标记微专业退出，课程级 enrollment 保留可继续单学
     → 但更新进度标记: 停止 cron 聚合 micro_specialty 进度
  7. 微调 student_count（防止负数）:
     IF (SELECT student_count FROM micro_specialties WHERE id=:msId) > 0:
        UPDATE micro_specialties SET student_count = student_count - 1,
          version = version + 1 WHERE id = :msId AND version = :oldVer
```

### 9.10 已修课程学分认可

> **新增修复**（对应 P2-3）：学生报名前已修过必修课的学分应计入微专业。

```
自动 enroll 时:
  FOR 每门必修课:
    check existing:
      - 是否有已通过的课程级 enrollment（final_score >= min_score）
      - 如有: 标记 micro_specialty_enrollments.courses_completed 直接 +1
              credits_earned += 该课程学分
              status 自动设为 'APPROVED'（不必再 enroll）
      - 如无: 正常调 enrollmentService.createEnrollment()

进度聚合时（§9.2 cron）:
  - 新增判断: 如果某门必修课无课程级 enrollment 但有旧成绩记录
     → 该课程计入 courses_completed / credits_earned
     → 不创建新 enrollment（避免重复）
```

### 9.11 各状态下微专业编辑范围

> **新增修复**（对应 P2-2）：明确各状态下 LEAD 可编辑的内容。

```
编辑范围规则:

| 微专业状态 | 基本信息可编辑 | 课程编排可编辑 | 教师团队可编辑 | 可操作 |
|-----------|:----------:|:----------:|:----------:|------|
| DRAFT | ✅ | ✅ | ✅ | submit, delete |
| PENDING_REVIEW | ✅ | ✅ | ✅ | —（等待审批）|
| APPROVED | ✅ | ✅ | ✅ | open, 重新 submit |
| REJECTED | ✅ | ✅ | ✅ | submit(重提) |
| RECRUITING | ✅ | ❌（仅可排课序）| ✅（仅 LEAD 可加人）| close, cancel |
| COMPLETED | ❌ | ❌ | ❌ | archive |
| CANCELLED | ❌ | ❌ | ❌ | — |
| ARCHIVED | ❌ | ❌ | ❌ | — |

> **RECRUITING 后不允许大幅变更**（课程组合已锁定，学生已报名），仅允许调整排序和补充团队。
```

### 9.12 角色鉴权规则

> **新增修复**（对应 P0-4 / §3.1 扩展）：LEAD 和 MEMBER 是 TEACHER 的子角色，通过 Service 层 `isLeadOf()` / `isMemberOf()` 方法鉴权。

```
Service 层角色鉴权方法:

1. isLeadOf(msId, userId):
   SELECT 1 FROM micro_specialty_teachers
   WHERE micro_specialty_id=:msId AND teacher_id=:userId
     AND role='LEAD' AND invite_status='ACTIVE'
   → 返回 boolean

2. isMemberOf(msId, userId):
   SELECT 1 FROM micro_specialty_teachers
   WHERE micro_specialty_id=:msId AND teacher_id=:userId
     AND role IN ('MEMBER','ASSISTANT') AND invite_status='ACTIVE'
   → 返回 boolean

3. isOwnerOrLead(msId, userId):
   = isLeadOf(msId, userId) OR (SELECT creator_id FROM micro_specialties WHERE id=:msId) = userId
   → 用于 submit/delete 等操作

Controller 层调用模式:
   @PreAuthorize("hasRole('TEACHER')")
   public R<?> editCourse(@PathVariable Long id, ...) {
       if (!microSpecialtyService.isLeadOf(id, SecurityUtil.getCurrentUserId())
           && !SecurityUtil.isAdmin()) {
           throw new BusinessException(ErrorCode.NO_PERMISSION);
       }
       // ... 业务逻辑
   }

权限速查表:
| 操作 | Controller @PreAuthorize | Service 层二次校验 | 公开 |
|------|------------------------|-------------------|------|
| 广场列表 | permitAll() | — | ✅ |
| 详情查看 | permitAll()（但 DRAFT/CANCELLED 过滤） | — | ✅ |
| 创建微专业 | hasRole('ACADEMIC') | — | |
| 编辑基本 | hasRole('TEACHER') | isLeadOf() OR isAdmin() | |
| Submit/Open/Close | hasRole('TEACHER') | isLeadOf() | |
| 编排课程 | hasRole('TEACHER') | isLeadOf() | |
| 邀请/移除教师 | hasRole('TEACHER') | isLeadOf() | |
| 接受/拒绝邀请 | hasRole('TEACHER') | userId == teacher_id（本人）| |
| 审报名 | hasRole('TEACHER') | isLeadOf() OR isAdmin() | |
| Drop enrollment | hasAnyRole('STUDENT','ADMIN') | userId == enrollment.user_id（本人）| |
| 所有审批操作 | hasRole('ACADEMIC') | — | |
| 金标操作 | hasRole('ACADEMIC') | — | |
| 班级导入 | hasAnyRole('ACADEMIC','ADMIN') | — | |
| 归档 | hasRole('ACADEMIC') | — | |
| LEAD继任 | hasRole('ACADEMIC') | — | |
```

### 9.13 页面状态规范（Loading / Error / Empty 三态）

> **总工程师决策**：以下规范对全 16 个页面强制生效。前端实施时，每个页面组件必须覆盖 4 种状态（Loading / Error / Empty / 正常），缺少任一态视为实现不完整，交叉验证 R1-R4 不通过。

**统一规范**：

| 状态 | 前端行为 | 实现方式 |
|------|---------|---------|
| **Loading** | 首次数据加载时显示骨架屏 | `<el-skeleton>` 组件，行数 ≈ 正常内容的 80% |
| **Error** | 请求失败时显示错误提示 + 重试按钮 | `<el-result status="error" />` + "重试" 按钮回调查 |
| **Empty** | 列表无数据时显示空态插画 + 引导操作 | `<el-empty>` + 引导按钮（如"去课程广场看看"）|
| **正常** | 标准数据渲染 | 按 §5.3-§5.7 页面结构实现 |

**异常场景**：API 403/500 → Error 态，网络中断 → Error 态（"网络已断开"），资源不存在 → 404 页（跳转 `/student/courses` 等替代入口）

**各页面空态定义**：

| 页面 | Empty 态文案 | 引导按钮 |
|------|-------------|---------|
| CourseSquare 专区 | "暂无微专业项目，敬请期待" | — |
| MicroSpecialtyDetail | 微专业不存在 → 跳转课程广场 | — |
| MyMicroSpecialties | "暂未报名微专业" | [去课程广场看看] |
| MicroSpecialtyList(教师) | "暂未参与任何微专业" | [创建微专业] / [提交申报] |
| MicroSpecialtyManage | 加载失败 → Error 态 | [重试] |
| MicroSpecialtyCourseEdit | "暂未编排课程" | [添加课程] |
| MicroSpecialtyTeamEdit | "暂未邀请教师" | [邀请教师] |
| MicroSpecialtyInvites | "暂无待处理邀请" | — |
| MicroSpecialtyProposal | 表单页→无空态 | — |
| MyProposals | "暂未提交申报" | [提交申报] |
| MicroSpecialtyReview | "暂无待审批微专业" | — |
| MicroSpecialtyProposalReview | "暂无待审批申报" | — |
| MicroSpecialtyFeaturedReview | "暂无置顶申请" | — |
| MicroSpecialtyCrossDeptReview | "暂无跨学院申请" | — |
| MicroSpecialtyClassImport | "暂无可用班级" | — |
| MicroSpecialtyGoldManage | "暂无微专业可设金标" | — |

---

## 10. 逻辑闭环自查表（v1.1 完整闭环版）

> **修复**：经零信任穷举审查修复全部 49 个断裂点后，自查维度从 12 类 40 项扩展到 **14 类 60 项**，全部 ✅。无断裂链路。

| # | 检查项 | 状态 | 对应 § |
|---|--------|------|--------|
| **C1** | **课程广场专区→详情→报名→学习→证书** 链路完整 | ✅ | §5.3-5.5 |
| C1.1 | 专区展示内容 = `GET /api/micro-specialties/square` → 三组数据 | ✅ | §5.3 |
| C1.2 | 详情页包含培养方案+课程列表+报名按钮 | ✅ | §5.4 |
| C1.3 | **课程点击条件已定义**（4 种场景：未报名/已报名/灰显/回顾） | ✅ | §5.4 |
| C1.4 | 报名后自动 enroll 必修课（含前置条件检查） | ✅ | §9.1/§9.5 |
| C1.5 | **已修课程学分认可**（报名前已通过的课程直接计入） | ✅ | §9.10 |
| C1.6 | 学习进度聚合走 cron（不依赖前端主动上报） | ✅ | §9.2 |
| C1.7 | 结业自动下发证书+通知学生 | ✅ | §9.6 |
| C1.8 | 证书可下载 PDF（复用 /{id}/download 统一端点）| ✅ | §7.7 |
| **C2** | **教师申报→审批→接受→编排→开课** 链路完整 | ✅ | §4.2 |
| C2.1 | 教师申报后状态 PENDING_REVIEW | ✅ | §2.1 |
| C2.2 | 教务处批准后自动创建 DRAFT + LEAD INVITED | ✅ | §2.1 |
| C2.3 | **驳回后可重提**（REJECTED→PENDING_REVIEW, submit 接受双 from-state）| ✅ | §2.1 |
| C2.4 | **申报驳回后可 resubmit**（PROPOSAL_REVIEW→PENDING_REVIEW） | ✅ | §2.1/§7.2 |
| C2.5 | LEAD 接受后 ACTIVE，可编排课程 | ✅ | §2.3 |
| C2.6 | 课程编排后 submit → PENDING_REVIEW（submit 含乐观锁） | ✅ | §2.1 |
| C2.7 | ACADEMIC approve → APPROVED → open → RECRUITING | ✅ | §2.1 |
| **C3** | **教师团队→邀请→接受/拒绝→跨学院审批** 完整 | ✅ | §2.3/§5.7 |
| C3.1 | 发送邀请→INVITED，7天有效期 | ✅ | §2.3 |
| C3.2 | 接受→ACTIVE（同学院）/PENDING_ACADEMIC（跨学院）| ✅ | §2.3 |
| C3.3 | 拒绝→DECLINED | ✅ | §2.3 |
| C3.4 | 超时→定时任务+乐观锁→DECLINED+通知 | ✅ | §9.3 |
| C3.5 | **被移除后可重新邀请**（REMOVED/DECLINED→INVITED, 复用原记录）| ✅ | §2.3/§7.4 |
| C3.6 | **教师主动退出**（ACTIVE→REMOVED, leave API + 通知 LEAD）| ✅ | §2.3/§7.4/§8.1 |
| C3.7 | 跨学院审批通过→ACTIVE | ✅ | §2.3 |
| **C4** | **置顶申请→金标管理** 完整 | ✅ | §2.4 |
| C4.1 | LEAD 申请→PENDING | ✅ | §2.4 |
| C4.2 | ACADEMIC 审批→APPROVED/REJECTED | ✅ | §2.4 |
| C4.3 | **取消置顶**（APPROVED→NONE, unset-featured API）| ✅ | §2.4/§7.6 |
| C4.4 | ACADEMIC 直接设金标（最多 2 个） | ✅ | §2.4 |
| C4.5 | 课程广场拉取: goldFeatured → featured → recruiting fallback | ✅ | §5.3 |
| **C5** | **学生报名→审批→学习→结业** 完整 | ✅ | §4.1 |
| C5.1 | 自主报名→PENDING（需审批）| ✅ | §7.5 |
| C5.2 | 班级导入→APPROVED（含已存在学生自动跳过+前置检查）| ✅ | §9.1 |
| C5.3 | approve 后自动 enroll（含前置检查+乐观锁+事务）| ✅ | §9.5 |
| C5.4 | **驳回后可重新申请**（REJECTED→PENDING, reapply API） | ✅ | §2.2/§7.5 |
| C5.5 | **退出后可重新加入**（DROPPED→PENDING, reapply API） | ✅ | §2.2/§7.5 |
| C5.6 | **FAILED 有后续**（MS_ENROLLMENT_FAILED 通知+不合格课程查看+可 reapply） | ✅ | §2.2/§5.5/§8.1/§9.2 |
| C5.7 | IN_PROGRESS 自动聚合进度（含乐观锁+分批） | ✅ | §9.2 |
| C5.8 | COMPLETED 自动颁发证书（幂等+防编号冲突） | ✅ | §9.6 |
| C5.9 | **退出微专业时课程 enrollment 可选级联清理**（含 student_count 防负）| ✅ | §9.9 |
| C5.10 | MyMicroSpecialties 支持全部 6 种状态（含 FAILED 标记+原因） | ✅ | §5.5 |
| **C6** | **LEAD 继任完整性** | ✅ | §2.5/§9.7 |
| C6.1 | LEAD 离职有继任机制（transfer-leadership API, 仅非终态可转移）| ✅ | §7.1/§9.7 |
| C6.2 | DB 触发器 trg_ms_one_lead 在转移事务中允许临时 0 条 | ✅ | §2.5 |
| C6.3 | 原 LEAD 已被移除时自动跳过（健壮处理）| ✅ | §9.7 |
| C6.4 | 原 LEAD 降为 MEMBER 或 REMOVED | ✅ | §9.7 |
| C6.5 | 通知原 LEAD + 新 LEAD | ✅ | §8.1 #22 |
| **C7** | **状态机完整性** | ✅ | §2 |
| C7.1 | 每个状态都有可达的下一个状态（终态=CANCELLED/ARCHIVED/CERTIFIED）| ✅ | §2 |
| C7.2 | 每个状态转换都有明确触发+角色+condition | ✅ | §2 |
| C7.3 | REJECTED→PENDING_REVIEW 转换已增加（submit 双 from-state）| ✅ | §2.1 |
| C7.4 | FAILED/DROPPED/REJECTED→PENDING reapply 已增加 | ✅ | §2.2 |
| C7.5 | REMOVED/DECLINED→INVITED re-invite 已增加 | ✅ | §2.3 |
| C7.6 | COMPLETED→ARCHIVED archive 转换已增加 | ✅ | §2.1 |
| C7.7 | ACTIVE→REMOVED leave 已增加（教师主动退出）| ✅ | §2.3 |
| C7.8 | WITHDRAWN 已在状态机图中标出 | ✅ | §2.1 |
| **C8** | **通知完整性（23 种，零缺失）** | ✅ | §8.1 |
| C8.1 | 每处状态变更对应一个通知（含 approve/open/archive/Failed/transfer/reapply）| ✅ | §8.1 |
| C8.2 | submit 通知 ACADEMIC（MS_SUBMITTED）| ✅ | §8.1 #7 |
| C8.3 | approve 通知 LEAD（MS_APPROVED）| ✅ | §8.1 #8 |
| C8.4 | open 通知（MS_OPENED）| ✅ | §8.1 #11 |
| C8.5 | archive 通知（MS_ARCHIVED）| ✅ | §8.1 #23 |
| C8.6 | FAILED 通知（MS_ENROLLMENT_FAILED，含不合格课程列表）| ✅ | §8.1 #16 |
| C8.7 | reapply 通知（MS_ENROLLMENT_REAPPLIED）| ✅ | §8.1 #15 |
| C8.8 | 教师有统一待办入口（MicroSpecialtyInvites）| ✅ | §5.7 |
| C8.9 | 教务处有统一待办聚合 | ✅ | §8.2 |
| **C9** | **并发安全+乐观锁** | ✅ | §9 |
| C9.1 | approve 使用 version 乐观锁 + 事务 | ✅ | §9.5 |
| C9.2 | drop 使用 version 乐观锁 + 事务 | ✅ | §9.9 |
| C9.3 | cron 进度聚合使用 version 乐观锁 + 分批 | ✅ | §9.2 |
| C9.4 | cron 邀请过期使用 version 乐观锁 + 分批 | ✅ | §9.3 |
| C9.5 | LEAD accept 使用 version 乐观锁 | ✅ | §9.4 |
| C9.6 | LEAD 继任使用 version 乐观锁 | ✅ | §9.7 |
| C9.7 | CANCELLED 级联使用 version 乐观锁 | ✅ | §9.8 |
| C9.8 | 班级导入使用 SELECT FOR UPDATE 防并发取消 | ✅ | §9.1 |
| **C10** | **数据模型完整性** | ✅ | §6 |
| C10.1 | 全部 6 张表 FK 目标表完整（→micro_specialties/→users/→courses 等）| ✅ | §6 |
| C10.2 | 状态字段统一为 String(20)（§6.1 从 Integer 改为 String）| ✅ | §6 |
| C10.3 | 全部 BigDecimal 字段标注精度（DECIMAL(6,2)/DECIMAL(5,2)）| ✅ | §6 |
| C10.4 | JSON 字段标注 JSONB（before_value/after_value）| ✅ | §6.6 |
| C10.5 | 索引列名从 ms_id 修正为 micro_specialty_id | ✅ | §6.2/6.3/6.4 |
| C10.6 | 部分索引统一命名 uk_mst_active/uk_mse_active（全文档一致）| ✅ | §2.3/§6.3/§6.4/§10 |
| C10.7 | uk_msc_unique 列名修正为 micro_specialty_id | ✅ | §6.2 |
| C10.8 | certificate_id 标注 nullable | ✅ | §6.4 |
| C10.9 | 全部 String 字段标注最大长度（VARCHAR(20)/VARCHAR(30)/VARCHAR(500) 等）| ✅ | §6 |
| **C11** | **REST API 完整性** | ✅ | §7 |
| C11.1 | archive API 已增加 | ✅ | §7.1 |
| C11.2 | leave API 已增加（教师主动退出）| ✅ | §7.4 |
| C11.3 | unset-featured API 已增加（取消置顶）| ✅ | §7.6 |
| C11.4 | API 统计数统一为 52 个（文档所有 3 处一致）| ✅ | §1 |
| C11.5 | 证书路径修正为 /my?type= | ✅ | §7.7 |
| C11.6 | 全部权限标注含（本人）约束（drop/reapply/accept/decline）| ✅ | §7.4/§7.5 |
| C11.7 | class-import 权限含 ADMIN | ✅ | §7.5 |
| **C12** | **路由命名+导航完整性** | ✅ | §5 |
| C12.1 | 路由全部小写 kebab-case，:id 格式 | ✅ | §5.1 |
| C12.2 | 路由名规范修正（proposal→proposals）| ✅ | §5.1 |
| C12.3 | 16 个页面导航出口完整（含"返回"或"取消"）| ✅ | §5.2 |
| C12.4 | MyProposals 已通过→跳转 MicroSpecialtyManage | ✅ | §5.2 |
| C12.5 | MyMicroSpecialties 可跳转 MicroSpecialtyDetail | ✅ | §5.2 |
| C12.6 | **页面 Loading/Error/Empty 三态已定义**（§9.13 统一规范 + 16 页面逐页空态文案）| ✅ | §9.13 |
| **C13** | **角色鉴权完整性** | ✅ | §3/§9.12 |
| C13.1 | §3 定义 LEAD/MEMBER 子角色及鉴权方式 | ✅ | §3.1 |
| C13.2 | isLeadOf()/isMemberOf() Service 层方法定义 | ✅ | §9.12 |
| C13.3 | Controller @PreAuthorize + Service 二次校验双层模式 | ✅ | §9.12 |
| C13.4 | 权限速查表覆盖全部操作 | ✅ | §9.12 |
| **C14** | **业务边界条件完整性** | ✅ | §9 |
| C14.1 | 班级导入跳过已存在学生 | ✅ | §9.1 |
| C14.2 | 证书编号防冲突（加入 randomHex(4) + 幂等检查）| ✅ | §9.6 |
| C14.3 | LEAD 邀请过期额外告警 ACADEMIC | ✅ | §9.3 |
| C14.4 | PENDING_ACADEMIC 14 天超限告警 | ✅ | §9.3 |
| C14.5 | student_count 防负数（IF > 0 判断）| ✅ | §9.9 |
| C14.6 | 未登录用户查看详情（前台引导登录）| ✅ | §4.1 |

---

## 11. ROADMAP & 实施节奏

### 11.1 实施节奏（14 步）

| Step | 内容 | 文件数 | 验收 |
|------|------|--------|------|
| 1 | **数据字典 v0.9** + ErrorCode 8 个 | 2 文档 | 总工程师审 |
| 2 | **Flyway V82-V85**（4 个 migration）+ 6 Entity | 4 SQL + 6 Java | `mvn compile` 通过 |
| 3 | Enums（6）+ Repository（5）+ DTO（15） | 26 Java | 单测 |
| 4 | Service 接口/实现（10）+ cron 定时任务（2） | 12 Java | 单测覆盖邀请过期/进度聚合/质量分 |
| 5 | Controller（5）+ 权限注解 | 5 Java | `precheck.sh` 13/13 PASS |
| 6 | CertificateService 扩展 + certificates migration 回归 | 2 Java | 现有证书流程不破坏 |
| **7** | **★ ui-ux-pro-max 技能加载 → 产出 design.md** | 1 文档 | 设计稿通过总工程师审 |
| 8 | 前端 api/store/router | 3 文件 | `npm run build` SUCCESS |
| 9 | **课程广场专区改造**（CourseSquare.vue + 5 组件） | 6 文件 | UI 评审对照设计稿 |
| 10 | 学生详情/我的修读 | 2 文件 | UI 评审 |
| 11 | 教师 7 页面 | 7 文件 | UI 评审 |
| 12 | 教务处 6 页面 | 6 文件 | UI 评审 |
| 13 | 5 份文档更新 | 5 文档 | 代码一致性 |
| **14** | **R1-R4 4 维交叉验证 → 全 PASS → commit** | 4 审查报告 | commit 标注"R1-R4 PASS" |

### 11.2 文件清单总览

| 层 | 数量 |
|----|------|
| Flyway migration | 4（V82-V85） |
| Java Entity | 6 |
| Java Enum | 6 |
| Java Repository | 5 |
| Java DTO | 15 |
| Java Service | 10 |
| Java Controller | 5 |
| Java Scheduled | 2 |
| JavaScript API Store | 3 |
| Vue 页面 | 16（+1 改造） |
| Vue 组件 | 5 |
| 设计文档 | 2（本 spec + ui-ux-pro-max 产出）|
| 真文档同步 | 5（数据字典 v0.9/权限矩阵 v3.1/状态机 v1.2/API契约 v2.0/功能清单）|

---

## 附录 A：AI 实施铁律（防偏移强制执行）

> **总工程师签字生效**。以下规则为编码代理的强制行为约束，违反任意一条 → 代码不被接受，退回重改。

### 铁律 1：编码前必须完整读取本 spec

| 规则 | 说明 |
|------|------|
| 首次编码前 | 必须从头到尾读取本 spec（1389+ 行），**不得跳过 §2 状态机、§6 数据模型、§7 API、§8 通知、§9 业务逻辑** |
| 每步实施前 | 重复读取该步骤涉及的相关章节，不得凭记忆编码 |
| 对照 §10 自查表 | 每完成一个页面/API，对照 §10 自查表逐项打勾，未通过的不提交 |

### 铁律 2：状态机驱动编码

| 规则 | 说明 |
|------|------|
| 每个状态转换 | 必须先实现 from-state 校验，再执行转换，最后触发通知 |
| 缺失转换 | 任何未在 §2 状态机中定义的转换 → 代码拒绝 |
| 终态保护 | CANCELLED/ARCHIVED 后的 API 调用必须返回 400 业务异常 |

### 铁律 3：乐观锁不可省略

| 规则 | 说明 |
|------|------|
| 所有 UPDATE | 必须包含 `version = version + 1` 和 `WHERE version = :oldVersion` |
| 返回值为 0 | 必须抛出 `BusinessException(ErrorCode.CONCURRENT_MODIFICATION)`，不可静默忽略 |
| §9 中伪代码 | §9.1-§9.11 的伪代码中标记了 `version` 的地方必须全部实现 |

### 铁律 4：前端状态必须三态全盖

| 规则 | 说明 |
|------|------|
| 每个页面 | Loading / Error / Empty / 正常 四种状态缺一不可 |
| Error 处理 | API 403 跳转登录 / 500 显示 Error 态 / 网络断开单独处理 |
| Empty 文案 | 严格使用 §9.13 定义的文案，不可自行创作 |

### 铁律 5：权限双校验

| 规则 | 说明 |
|------|------|
| Controller 层 | `@PreAuthorize` 按 §7 权限列标注（不得删除/放宽） |
| Service 层 | 按 §9.12 实现 `isLeadOf()` / `isMemberOf()` + IDOR 校验（防止水平越权） |
| 本人操作 | accept/decline/drop/reapply 必须校验当前用户 = 目标用户 |

### 铁律 6：不出 happy path 漏洞

| 规则 | 说明 |
|------|------|
| 不允许 | 只实现"正常流程"而跳过异常/边界条件 |
| 不允许 | 编码时忽略 §9 业务逻辑中的 IF/ELSE 分支、并发场景、事务边界 |
| 不允许 | 跳过乐观锁、跳过通知发送、跳过事务回滚处理 |

### 铁律 7：交叉验证强制

| 规则 | 说明 |
|------|------|
| 每阶段完成 | 必须启动 4 维交叉验证（R1 代码质量 / R2 DB 迁移 / R3 安全配置 / R4 跨域一致性）|
| 任一 FAIL | 修复后重新审查全部 4 维，不允许只审 FAIL 维度 |
| 标记 | commit message 必须标注 `交叉验证通过(R1-R4)` |

---

## 附录 B：修订日志

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-23 | 初版发布，覆盖全部场景。经 3 遍自查修复 5 处缺口 |
| v1.1 | 2026-06-23 | **零信任穷举审查修复全部 49 个断裂点**。详见下文。|
