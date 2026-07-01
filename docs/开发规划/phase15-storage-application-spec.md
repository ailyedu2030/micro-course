# 整理收纳微专业申请表（Phase 15）开发规格说明书 v1.0

> 定位：Phase 3 Mid-Level 设计文档 → Phase 4 TDD 执行准入
> 范围：重做现有申报功能为完整「整理收纳微专业申请表」系统——5 模块表单填报、草稿自动保存、A4 打印预览、Word/PDF 导出、教务审批
> 覆盖：1 张主表扩展（30+ 新字段）+ 4 张新子表、**12 个 API**、4 个页面、**1 个状态机（扩展 DRAFT 初始态）**
> 逻辑闭环声明：每项"触发"必有"响应"，每项"变更"必有"校验"，每个"状态"必有"终点"
> 基线版本：v1.0（初始版本）
> 模块归属：改造现有 proposal 模块，路由 `/teacher/micro-specialties/proposals` 强化

---

## 目录

1. [系统架构概览](#1-系统架构概览)
2. [状态机](#2-状态机)
3. [用户角色与权限矩阵](#3-用户角色与权限矩阵)
4. [全流程用户旅程](#4-全流程用户旅程)
5. [页面/屏幕地图](#5-页面屏幕地图)
6. [数据模型 v1.0](#6-数据模型-v10)
7. [REST API 全集](#7-rest-api-全集)
8. [关键业务逻辑](#8-关键业务逻辑)
9. [导出专项](#9-导出专项)
10. [枚举常量](#10-枚举常量)
11. [逻辑闭环自查表](#11-逻辑闭环自查表)
12. [ROADMAP & 实施节奏](#12-roadmap--实施节奏)

---

## 1. 系统架构概览

### 1.1 模块分层

```
┌──────────────────────────────────────────────────────────────────┐
│  教师端                              教务处端                      │
│  MyProposals.vue（增强）            MicroSpecialtyProposalReview  │
│  MicroSpecialtyProposal.vue（重做）  .vue（增强）                  │
│  StorageApplicationPreview.vue（新增）                            │
├──────────────────────────────────────────────────────────────────┤
│  API 层: 12 个 REST 端点 /api/storage-applications/*              │
├──────────────────────────────────────────────────────────────────┤
│  服务层:                                                          │
│  StorageApplicationService（新增）                                │
│  StorageApplicationExportService（新增 · Word/PDF）               │
│  StorageApplicationAutoSaveService（新增 · 心跳+防抖）              │
├──────────────────────────────────────────────────────────────────┤
│  数据层: micro_specialty_proposals（扩展 30+ 字段）+ 4 张新子表     │
│   proposal_courses / proposal_team_members                        │
│   proposal_signatures / proposal_shared_units                     │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 5 模块说明

基于纸质附件 2《高校开放共享"微专业"资源平台推荐表》结构：

| 模块 | 对应子表 | 填写内容 | 交互形式 |
|------|---------|---------|---------|
| **模块 1 · 表头/基本情况** | 主表字段 | 高校全称、微专业名称/类型、联系人、建设周期、学分/学时、招生规模、培养目标、建设背景与意义、培养特色、质量保障措施、预期成果 | 表单控件 + 富文本编辑器 |
| **模块 2 · 课程体系** | `proposal_courses` | 模块名称、课程名称、学时、学分、开课学期 | 动态增删行表格 |
| **模块 3 · 教学团队** | `proposal_team_members` | 序号、姓名、年龄、职称、单位、专业、曾授课程、拟授课程 | 动态增删行表格 |
| **模块 4 · 签字盖章** | `proposal_signatures` | 三级签字（负责人/院系/学校）+ 共享单位签字，含意见、签字图、公章图、日期 | 图片上传 + 日期选择 |
| **模块 5 · 共建共享单位** | `proposal_shared_units` | 单位名称、单位类型（共建高校/企业/共享高校）、排序 | 动态增删行 |

### 1.3 与 Phase 14 的集成点

| Phase 14 模块 | 集成方式 | 冲突风险 |
|--------------|---------|---------|
| `micro_specialty_proposals` 表 | **主表扩展**：新增 30+ 列 + 状态机引入 DRAFT 初始态 | **高**：需 migration 精确控制，不影响现有已审批数据 |
| `MicroSpecialtyProposalController` | **新增独立 Controller** `StorageApplicationController`，旧端点保留兼容 | 低：不同路由前缀 |
| `MicroSpecialtyProposalService` | **新增独立 Service** `StorageApplicationService`，共享 Repository | 低：查询可复用 |
| `departments` | 只读引用（offer_department_id） | 低 |
| `users` | 只读引用（proposer_id） | 低 |
| Phase 14 审批流 | **复用状态机**（PENDING_REVIEW→APPROVED/REJECTED），Phase 15 的 DRAFT→PENDING_REVIEW 为上游扩展 | 中：注意 DRAFT 状态下旧审批接口兼容 |

---

## 2. 状态机

### 2.1 申请表状态机（扩展 Phase 14）

```
                    创建空草稿
                        │
                        ▼
    ┌─────────────── DRAFT ───────────────────────────────┐
    │                  │                                   │
    │              submit（提交审核）                        │
    │                  │                                   │
    │                  ▼                                   │
    │           PENDING_REVIEW                             │
    │            │          │                              │
    │      approve    reject                               │
    │            │          │                              │
    │            ▼          ▼                              │
    │        APPROVED   REJECTED ──resubmit──→ PENDING_REVIEW
    │            │          │                              │
    │            │     withdraw                            │
    │            │          │                              │
    │            │          ▼                              │
    │            │     WITHDRAWN（终态）                     │
    │            │                                         │
    └── 任意时点可编辑（DRAFT 内） ──────────────────────────┘
```

### 2.2 转换矩阵

| 当前状态 | → 目标状态 | 触发动作 | 允许角色 | 前置条件 |
|---------|-----------|---------|---------|---------|
| — | DRAFT | `POST /api/storage-applications/init` | TEACHER | — |
| DRAFT | PENDING_REVIEW | `POST /api/storage-applications/{id}/submit` | TEACHER（本人） | 提交校验全部通过（§8.2） |
| DRAFT | DRAFT | `PUT /api/storage-applications/{id}` | TEACHER（本人） | 状态为 DRAFT |
| DRAFT | DRAFT | `PATCH /api/storage-applications/{id}/auto-save` | TEACHER（本人） | 状态为 DRAFT |
| PENDING_REVIEW | APPROVED | `POST /api/micro-specialty-proposals/{id}/approve` | ACADEMIC | 复用 Phase 14 审批端点 |
| PENDING_REVIEW | REJECTED | `POST /api/micro-specialty-proposals/{id}/reject` | ACADEMIC | 填写驳回原因 |
| REJECTED | PENDING_REVIEW | `POST /api/micro-specialty-proposals/{id}/resubmit` | TEACHER（本人） | 修改后重新提交 |
| PENDING_REVIEW | WITHDRAWN | `POST /api/micro-specialty-proposals/{id}/withdraw` | TEACHER（本人） | 仅 PENDING_REVIEW |

### 2.3 编辑范围规则表

| 当前状态 | 可编辑？ | 可提交？ | 可预览？ | 可导出？ | 可审批？ |
|---------|:------:|:------:|:------:|:------:|:------:|
| DRAFT | ✅ 全部 | ✅（校验通过后） | ✅ | ✅（仅供自查） | ❌ |
| PENDING_REVIEW | ❌ 锁定 | ❌ | ✅ | ✅ | ✅ ACADEMIC |
| APPROVED | ❌ 锁定 | ❌ | ✅ | ✅（正式版） | ❌（已审批） |
| REJECTED | ✅（修改限定字段后 resubmit） | ✅（resubmit） | ✅ | ✅（仅供自查） | ❌ |
| WITHDRAWN | ✅（但不可提交，只能删除） | ❌ | ✅ | ❌ | ❌ |

> **锁定机制**：提交后（DRAFT→PENDING_REVIEW）所有编辑 API 返回 400（`MS_STATUS_INVALID`："申请表已提交审核，不可编辑"）。

---

## 3. 用户角色与权限矩阵

### 3.1 涉及角色

| 角色 | 代码 | Phase 15 权限范围 |
|------|------|-----------------|
| 教师（申报人） | TEACHER | CRUD 自己的申请表（含草稿/预览/导出/提交/重置） |
| 教务处 | ACADEMIC | 查看全部待审/已审申请表、查看详情、审批通过/驳回 |
| 管理员 | ADMIN | 查看全部（只读） |

### 3.2 权限矩阵

| 操作 | TEACHER（本人） | TEACHER（他人） | ACADEMIC | ADMIN |
|------|:------------:|:------------:|:--------:|:-----:|
| 创建草稿 | ✅ | ❌ | ❌ | ❌ |
| 编辑草稿 | ✅ | ❌ | ❌ | ❌ |
| 自动保存 | ✅ | ❌ | ❌ | ❌ |
| 预览草稿 | ✅ | ❌ | ❌ | ❌ |
| 导出草稿 | ✅ | ❌ | ❌ | ❌ |
| 上传签名图片 | ✅ | ❌ | ❌ | ❌ |
| 提交审核 | ✅ | ❌ | ❌ | ❌ |
| 查看详情 | ✅（自己） | ❌ | ✅（全部） | ✅（全部） |
| 预览（已提交） | ✅（自己） | ❌ | ✅（全部） | ✅（全部） |
| 导出（已提交） | ✅（自己） | ❌ | ✅（全部） | ✅（全部） |
| 审批 | ❌ | ❌ | ✅ | ❌ |
| 重置模块/全部 | ✅（DRAFT 时） | ❌ | ❌ | ❌ |
| 删除草稿 | ✅（DRAFT/WITHDRAWN） | ❌ | ❌ | ❌ |

### 3.3 Controller 层鉴权模式

```java
// 本人鉴权 — Service 层方法
private void assertOwner(MicroSpecialtyProposal proposal) {
    Long userId = SecurityUtil.getCurrentUserId();
    if (!proposal.getProposerId().equals(userId)) {
        throw new BusinessException(ErrorCode.NO_PERMISSION, "只能操作自己的申请表");
    }
}

// 状态鉴权 — Service 层方法
private void assertDraft(MicroSpecialtyProposal proposal) {
    if (!"DRAFT".equals(proposal.getStatus())) {
        throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "申请表已提交审核，不可编辑");
    }
}
```

---

## 4. 全流程用户旅程

### 4.1 教师端完整旅程

```
教师登录
  │
  ├─→ 进入「我的申报」列表（MyProposals.vue）
  │     ├─ 空态引导：「创建申请表」按钮
  │     ├─ 已有草稿：卡片展示 标题/状态/更新时间
  │     └─ 操作：继续编辑 / 预览 / 导出 / 删除
  │
  ├─→ 点击「创建申请表」→ POST /api/storage-applications/init
  │     → 后端创建 DRAFT 记录（status=DRAFT, 除 proposer_id 外全字段为 NULL）
  │     → 跳转到填写页
  │
  ├─→ 填写页（MicroSpecialtyProposal.vue 重做）
  │     │
  │     ├─ 模块导航（左侧锚点 or 顶部 Steps）：
  │     │   ① 表头/基本情况  ② 课程体系  ③ 教学团队  ④ 签字盖章  ⑤ 共建共享单位
  │     │
  │     ├─ 模块 ① 表头/基本情况：
  │     │   ├─ 高校全称（input）/ 微专业名称（input）/ 类型（固定显示 急需紧缺型）
  │     │   ├─ 开课学院（el-select 加载院系列表）
  │     │   ├─ 联系人姓名/电话/邮箱（input + 前端正则校验）
  │     │   ├─ 建设起始年份/结束年份（el-date-picker year mode）
  │     │   ├─ 总学分/总学时/招生规模（input-number）
  │     │   ├─ 面向对象（textarea）
  │     │   ├─ 培养目标 / 建设背景与意义 / 培养特色 / 质量保障措施 / 预期成果（富文本）
  │     │   └─ 补充说明（富文本）
  │     │
  │     ├─ 模块 ② 课程体系（proposal_courses 动态表格）：
  │     │   ├─ 列：模块名称 / 课程名称 / 学时 / 学分 / 开课学期
  │     │   ├─ [添加一行] 按钮
  │     │   └─ 每行可删除（至少保留 1 行）
  │     │
  │     ├─ 模块 ③ 教学团队（proposal_team_members 动态表格）：
  │     │   ├─ 列：序号(自动) / 姓名 / 年龄 / 职称 / 单位 / 专业 / 曾授课程 / 拟授课程
  │     │   ├─ [添加成员] 按钮
  │     │   └─ 每行可删除（至少保留 1 行，通常≥3）
  │     │
  │     ├─ 模块 ④ 签字盖章（proposal_signatures）：
  │     │   ├─ 三级签字行（固定 3 行）：
  │     │   │   ├─ 负责人签字（LEAD）：意见(textarea) + 签字图(upload) + 日期(date-picker)
  │     │   │   ├─ 院系意见（DEPT）：意见(textarea) + 签字图(upload) + 公章图(upload) + 日期
  │     │   │   └─ 学校意见（SCHOOL）：意见(textarea) + 签字图(upload) + 公章图(upload) + 日期
  │     │   └─ 共享单位签字行（动态，关联模块⑤）：
  │     │       └─ 意见(textarea) + 签字图(upload) + 公章图(upload) + 日期
  │     │
  │     ├─ 模块 ⑤ 共建共享单位（proposal_shared_units 动态表格）：
  │     │   ├─ 列：排序(拖拽) / 单位名称 / 单位类型（共建高校/企业/共享高校）
  │     │   ├─ [添加单位] 按钮
  │     │   └─ 每行可删除
  │     │
  │     ├─ ★ 自动保存（贯穿全部模块）：
  │     │   ├─ 前端：字段变更 1500ms 防抖后调 PATCH auto-save
  │     │   ├─ 后端：30s 心跳保活（若 120s 无心跳则标记会话过期）
  │     │   └─ UI：右上角显示「已自动保存 14:32:15」
  │     │
  │     └─ ★ 全量保存按钮（手动触发 PUT /api/storage-applications/{id}）
  │
  ├─→ 打印预览（StorageApplicationPreview.vue）
  │     ├─ A4 样式页面（@media print 适配）
  │     ├─ 所有字段只读展示（富文本渲染为 HTML）
  │     ├─ 签名/公章图片内嵌显示
  │     ├─ 动态表格以正式表格形式展示
  │     └─ [下载 Word] / [下载 PDF] / [提交审核] 按钮
  │
  ├─→ 提交审核（POST submit）
  │     ├─ 前端调用提交校验 → 若失败显示错误清单
  │     ├─ 后端二次校验（§8.2）→ 若失败返回具体字段错误
  │     ├─ 校验通过 → status=PENDING_REVIEW，锁定编辑
  │     └─ 跳回列表页，Toast「申请表已提交审核」
  │
  ├─→ 查看审批结果
  │     ├─ 列表页状态更新：
  │     │   ├─ APPROVED → 绿色「已通过」+ 可导出正式版
  │     │   └─ REJECTED → 红色「已驳回」+ 显示驳回原因 + [修改重提] 按钮
  │     └─ 重提：点击进入编辑页（部分字段可修改）→ 再次 submit
  │
  └─→ 导出（任一状态可导出 Word/PDF，文件名含高校全称+日期）
```

### 4.2 教务处旅程

```
教务处登录
  │
  ├─→ 申报审批列表（MicroSpecialtyProposalReview.vue 增强）
  │     ├─ Tab 分类：待审核 / 已通过 / 已驳回 / 全部
  │     ├─ 表格列：高校全称 / 微专业名称 / 申请人 / 提交时间 / 状态
  │     └─ 操作：[查看详情] / [通过] / [驳回]
  │
  ├─→ 查看详情（复用 StorageApplicationPreview.vue 只读模式）
  │     ├─ 可查看完整申请表（所有 5 个模块）
  │     ├─ 可下载 Word/PDF
  │     └─ 底部 [通过] / [驳回] 按钮
  │
  └─→ 审批操作
        ├─ 通过：POST /api/micro-specialty-proposals/{id}/approve
        │     → status=APPROVED → 同步创建 micro_specialty DRAFT（复用 Phase 14 逻辑）
        │     → 通知申报人
        └─ 驳回：POST /api/micro-specialty-proposals/{id}/reject
              → status=REJECTED + review_comment
              → 通知申报人
```

---

## 5. 页面/屏幕地图

### 5.1 路由表（4 个页面）

| # | 路径 | 页面组件 | 角色 | 类型 |
|---|------|---------|------|------|
| 1 | `/teacher/micro-specialties/proposals` | `MicroSpecialtyProposal.vue`（★ 重做） | TEACHER | 教师 |
| 2 | `/teacher/micro-specialties/my-proposals` | `MyProposals.vue`（★ 增强） | TEACHER | 教师 |
| 3 | `/teacher/micro-specialties/proposals/:id/preview` | `StorageApplicationPreview.vue`（★ 新增） | TEACHER/ACADEMIC | 预览 |
| 4 | `/academic/micro-specialties/proposals` | `MicroSpecialtyProposalReview.vue`（★ 增强） | ACADEMIC | 教务 |

### 5.2 页面间导航关系

```
[教师端]
  MyProposals.vue（申报列表）
    │
    ├─ [创建申请表] → POST init → redirect → MicroSpecialtyProposal.vue?id=新ID
    ├─ [继续编辑]   → router push → MicroSpecialtyProposal.vue?id=xxx
    ├─ [预览]       → router push → StorageApplicationPreview.vue?id=xxx
    └─ [导出]       → window.open GET export-word / export-pdf

  MicroSpecialtyProposal.vue（申请表填写）
    ├─ 5 模块单页面，左侧步骤导航或顶部 Steps
    ├─ 右上角 [预览] 按钮 → StorageApplicationPreview.vue
    ├─ 底部 [保存草稿] / [提交审核] 按钮
    └─ 提交成功后 → redirect → MyProposals.vue

  StorageApplicationPreview.vue（打印预览）
    ├─ A4 尺寸打印样式容器
    ├─ [返回编辑] → router back
    ├─ [下载 Word] → GET /api/storage-applications/{id}/export-word
    ├─ [下载 PDF] → GET /api/storage-applications/{id}/export-pdf
    └─ [提交审核] → POST submit → redirect → MyProposals.vue

[教务处端]
  MicroSpecialtyProposalReview.vue（审批列表）
    └─ [查看详情] → StorageApplicationPreview.vue?id=xxx（只读模式 + 审批按钮）
```

### 5.3 MicroSpecialtyProposal.vue 重做规格

```
┌─ 顶部导航 ──────────────────────────────────────────────────────┐
│  ← 返回列表   整理收纳微专业申请表              [预览] 已自动保存 14:32 │
├─ 左侧步骤导航（锚点滚动，sticky）──────────────────────────────────┤
│  ① 表头/基本情况  ← 当前激活                                    │
│  ② 课程体系       (2 门课)                                      │
│  ③ 教学团队       (3 名成员)                                     │
│  ④ 签字盖章       (3/4 已填写)                                   │
│  ⑤ 共建共享单位    (1 个单位)                                    │
├─ 右侧表单区（scroll）─────────────────────────────────────────────┤
│                                                                  │
│  ┌─ 模块① 表头/基本情况 ──────────────────────────────────────┐  │
│  │ 高校全称：[________________________]                        │  │
│  │ 微专业名称：[_______________________] 类型：急需紧缺型(固定)    │  │
│  │ 开课学院：[▼ 请选择学院________]                             │  │
│  │ 联系人：[_______] 电话：[___________] 邮箱：[___________]   │  │
│  │ 建设周期：[2024] 年 ~ [2028] 年                              │  │
│  │ 总学分：[___] 总学时：[___] 招生规模：[___] 人               │  │
│  │ 面向对象：[____________________________________________]   │  │
│  │ 培养目标：[富文本编辑器_____________________________]       │  │
│  │ 建设背景与意义：[富文本编辑器_________________________]     │  │
│  │ 培养特色：[富文本编辑器_____________________________]       │  │
│  │ 质量保障措施：[富文本编辑器_________________________]       │  │
│  │ 预期成果：[富文本编辑器_____________________________]       │  │
│  │ 补充说明：[富文本编辑器_____________________________]       │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─ 模块② 课程体系 ──────────────────────────────────────────┐  │
│  │ 模块名称 | 课程名称 | 学时 | 学分 | 开课学期 | 操作           │  │
│  │ [______] | [______] | [__] | [___] | [____] | [删除]         │  │
│  │ [+ 添加课程]                                                  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─ 模块③ 教学团队 ──────────────────────────────────────────┐  │
│  │ 序号 | 姓名 | 年龄 | 职称 | 单位 | 专业 | 曾授课程 | 拟授课程|操作│
│  │  1   | [__] | [__] | [__] | [__] | [__] | [______] |[______]|[删]│
│  │ [+ 添加成员]                                                  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─ 模块④ 签字盖章 ──────────────────────────────────────────┐  │
│  │ 负责人签字：意见[________] 签字[📎上传] 日期[____]            │  │
│  │ 院系意见：  意见[________] 签字[📎] 公章[📎] 日期[____]       │  │
│  │ 学校意见：  意见[________] 签字[📎] 公章[📎] 日期[____]       │  │
│  │ 共享单位签字：(动态行，关联模块⑤)                             │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─ 模块⑤ 共建共享单位 ──────────────────────────────────────┐  │
│  │ 排序 | 单位名称 | 类型(共建高校/企业/共享高校) | 操作         │  │
│  │  1   | [______] | [▼________]              | [删除]         │  │
│  │ [+ 添加单位]                                                  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌─ 底部操作栏（sticky）────────────────────────────────────┐  │
│  │          [保存草稿]        [重置当前模块]   [提交审核]       │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 5.4 StorageApplicationPreview.vue 新增规格（A4 打印预览）

```
┌─ A4 容器（210mm × 297mm CSS 模拟，白底）──────────────────────┐
│                                                                  │
│  页眉：高校开放共享"微专业"资源平台推荐表                          │
│  ─────────────────────────────────────────────────────────────  │
│                                                                  │
│  一、推荐单位基本情况                                              │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 高校全称：XX大学          微专业名称：整理收纳                │  │
│  │ 开课学院：XX学院          类型：急需紧缺型                    │  │
│  │ ... （所有字段以正式排版展示）                                │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  二、课程体系                                                     │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 序号 | 模块名称 | 课程名称 | 学时 | 学分 | 开课学期         │  │
│  │  1   | ...                                                  │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  三、教学团队                                                     │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 序号 | 姓名 | 年龄 | 职称 | 单位 | 专业 | 曾授课程 | 拟授课程│  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  四、推荐意见                                                     │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 负责人签字：_______________  日期：________                  │  │
│  │ 院系意见：_________________  签字：___ 公章：___ 日期：____   │  │
│  │ 学校意见：_________________  签字：___ 公章：___ 日期：____   │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  五、共建共享单位                                                 │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ 单位名称 | 类型                                               │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  页脚：填报日期：2026-01-15     第 1 页 / 共 N 页                 │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

操作栏（顶部 sticky）：
  [← 返回编辑]   [下载 Word]   [下载 PDF]   [提交审核]
```

---

## 6. 数据模型 v1.0

### 6.1 micro_specialty_proposals — 主表扩展（Phase 15 新增字段）

> **基础字段**（V84/V88/V104/V105 已有，保留不变）：`id, proposer_id, title, description, offer_department_id, training_objective, prerequisites, semester, max_students, credits, status, review_comment, reviewed_by, reviewed_at, created_micro_specialty_id, created_at, updated_at, version`

**Phase 15 新增字段（DDL）：**

```sql
-- Phase 15: 整理收纳微专业申请表 — 主表扩展
-- 依赖：V105 已含 credits 列

ALTER TABLE micro_specialty_proposals
    -- 类型（固定选项）
    ADD COLUMN type                      VARCHAR(20)  NOT NULL DEFAULT '急需紧缺型',

    -- 表头信息
    ADD COLUMN university_full_name      VARCHAR(200),
    ADD COLUMN contact_person_name       VARCHAR(50),
    ADD COLUMN contact_phone             VARCHAR(20),
    ADD COLUMN contact_email             VARCHAR(100),

    -- 建设周期
    ADD COLUMN construction_start_year   INTEGER,
    ADD COLUMN construction_end_year     INTEGER,

    -- 规模与学时
    ADD COLUMN total_hours               INTEGER      DEFAULT 0,

    -- 面向对象
    ADD COLUMN target_audience           TEXT,

    -- 建设背景（富文本）
    ADD COLUMN background_significance   TEXT,

    -- 培养特色（富文本）
    ADD COLUMN training_features         TEXT,

    -- 质量保障措施（富文本）
    ADD COLUMN quality_assurance         TEXT,

    -- 预期成果（富文本）
    ADD COLUMN expected_outcomes         TEXT,

    -- 补充说明（富文本）
    ADD COLUMN additional_notes          TEXT,

    -- 提交校验标记（用于导出前置校验）
    ADD COLUMN validation_passed         BOOLEAN      DEFAULT FALSE,

    -- 最后一次自动保存时间
    ADD COLUMN last_auto_saved_at        TIMESTAMP;

-- 索引
CREATE INDEX IF NOT EXISTS idx_msp_type       ON micro_specialty_proposals(type);
CREATE INDEX IF NOT EXISTS idx_msp_status_type ON micro_specialty_proposals(status, type);
```

**新增字段明细表：**

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| type | type | String(20) | NOT NULL DEFAULT '急需紧缺型' | 微专业类型 |
| universityFullName | university_full_name | String(200) | — | 高校全称 |
| contactPersonName | contact_person_name | String(50) | — | 联系人姓名 |
| contactPhone | contact_phone | String(20) | — | 手机号（正则 `^1[3-9]\d{9}$`） |
| contactEmail | contact_email | String(100) | — | 联系邮箱 |
| constructionStartYear | construction_start_year | Integer | — | 建设起始年份（≥2024） |
| constructionEndYear | construction_end_year | Integer | — | 建设结束年份（≤当前年份） |
| totalHours | total_hours | Integer | DEFAULT 0 | 总学时 |
| targetAudience | target_audience | Text | — | 面向对象 |
| backgroundSignificance | background_significance | Text | — | 建设背景与意义（富文本） |
| trainingFeatures | training_features | Text | — | 培养特色（富文本） |
| qualityAssurance | quality_assurance | Text | — | 质量保障措施（富文本） |
| expectedOutcomes | expected_outcomes | Text | — | 预期成果（富文本） |
| additionalNotes | additional_notes | Text | — | 补充说明（富文本） |
| validationPassed | validation_passed | Boolean | DEFAULT FALSE | 上次提交校验是否通过 |
| lastAutoSavedAt | last_auto_saved_at | Timestamp | — | 最后一次自动保存时间 |

> **兼容性保证**：所有新增列带 `DEFAULT` 或可为空（`NULL`），不影响现有已审批数据。旧 `status='PENDING_REVIEW'` 的记录新增字段均为 NULL/默认值。
>
> **状态机扩展**：`status` 列新增 `DRAFT` 枚举值作为初始态。`NOT NULL DEFAULT 'DRAFT'` 仅对新行生效，旧行 status 不变。

### 6.2 proposal_courses — 课程体系动态表（新表）

```sql
CREATE TABLE proposal_courses (
    id                      BIGSERIAL PRIMARY KEY,
    proposal_id             BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    module_name             VARCHAR(200),              -- 模块名称（如"基础理论模块"）
    course_name             VARCHAR(200)  NOT NULL,     -- 课程名称
    hours                   INTEGER      DEFAULT 0,    -- 学时
    credits                 NUMERIC(5,1) DEFAULT 0,    -- 学分
    semester                VARCHAR(20),               -- 开课学期（如"第1学期"）
    sort_order              INTEGER      DEFAULT 0,    -- 排序
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pc_proposal ON proposal_courses(proposal_id);
CREATE INDEX idx_pc_sort     ON proposal_courses(proposal_id, sort_order);
```

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK 自增 | |
| proposalId | proposal_id | Long | FK→proposals, ON DELETE CASCADE | |
| moduleName | module_name | String(200) | — | 模块名称 |
| courseName | course_name | String(200) | NOT NULL | 课程名称 |
| hours | hours | Integer | DEFAULT 0 | 学时 |
| credits | credits | BigDecimal(5,1) | DEFAULT 0 | 学分 |
| semester | semester | String(20) | — | 开课学期 |
| sortOrder | sort_order | Integer | DEFAULT 0 | 排序 |
| createdAt | created_at | Timestamp | NOT NULL | |
| updatedAt | updated_at | Timestamp | NOT NULL | |

### 6.3 proposal_team_members — 教学团队成员动态表（新表）

```sql
CREATE TABLE proposal_team_members (
    id                      BIGSERIAL PRIMARY KEY,
    proposal_id             BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    seq_no                  INTEGER      NOT NULL,       -- 序号（前端自动编号）
    name                    VARCHAR(50)   NOT NULL,       -- 姓名
    age                     INTEGER,                     -- 年龄
    title                   VARCHAR(50),                 -- 职称
    institution             VARCHAR(200),                -- 单位
    major                   VARCHAR(200),                -- 专业
    previously_taught       TEXT,                        -- 曾授课程
    to_teach                TEXT,                        -- 拟授课程
    sort_order              INTEGER      DEFAULT 0,      -- 排序
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ptm_proposal ON proposal_team_members(proposal_id);
CREATE INDEX idx_ptm_sort     ON proposal_team_members(proposal_id, sort_order);
```

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK 自增 | |
| proposalId | proposal_id | Long | FK→proposals, ON DELETE CASCADE | |
| seqNo | seq_no | Integer | NOT NULL | 序号 |
| name | name | String(50) | NOT NULL | 姓名 |
| age | age | Integer | — | 年龄 |
| title | title | String(50) | — | 职称 |
| institution | institution | String(200) | — | 单位 |
| major | major | String(200) | — | 专业 |
| previouslyTaught | previously_taught | Text | — | 曾授课程 |
| toTeach | to_teach | Text | — | 拟授课程 |
| sortOrder | sort_order | Integer | DEFAULT 0 | |
| createdAt | created_at | Timestamp | NOT NULL | |
| updatedAt | updated_at | Timestamp | NOT NULL | |

### 6.4 proposal_signatures — 三级签字+共享单位签字（新表）

```sql
CREATE TABLE proposal_signatures (
    id                      BIGSERIAL PRIMARY KEY,
    proposal_id             BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    sign_level              VARCHAR(20)  NOT NULL,       -- LEAD / DEPT / SCHOOL / SHARED_UNIT
    shared_unit_id          BIGINT,                      -- 若 sign_level=SHARED_UNIT，关联 proposal_shared_units.id
    opinion                 TEXT,                        -- 意见
    signature_image_url     VARCHAR(500),                -- 签字图片 URL
    seal_image_url          VARCHAR(500),                -- 公章图片 URL
    sign_date               DATE,                        -- 签字日期
    sort_order              INTEGER      DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ps_proposal ON proposal_signatures(proposal_id);
CREATE INDEX idx_ps_level    ON proposal_signatures(proposal_id, sign_level);
```

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK 自增 | |
| proposalId | proposal_id | Long | FK→proposals, ON DELETE CASCADE | |
| signLevel | sign_level | String(20) | NOT NULL | LEAD / DEPT / SCHOOL / SHARED_UNIT |
| sharedUnitId | shared_unit_id | Long | — | 关联 shared_units（仅 SHARED_UNIT 时非空） |
| opinion | opinion | Text | — | 审核意见 |
| signatureImageUrl | signature_image_url | String(500) | — | 签字图片 URL |
| sealImageUrl | seal_image_url | String(500) | — | 公章图片 URL |
| signDate | sign_date | Date | — | 签字日期 |
| sortOrder | sort_order | Integer | DEFAULT 0 | |
| createdAt | created_at | Timestamp | NOT NULL | |
| updatedAt | updated_at | Timestamp | NOT NULL | |

**固定数据行**：创建草稿时自动初始化 3 行（LEAD/DEPT/SCHOOL），共享单位签字行由模块⑤动态追加。

### 6.5 proposal_shared_units — 共建共享单位（新表）

```sql
CREATE TABLE proposal_shared_units (
    id                      BIGSERIAL PRIMARY KEY,
    proposal_id             BIGINT       NOT NULL REFERENCES micro_specialty_proposals(id) ON DELETE CASCADE,
    unit_name               VARCHAR(200)  NOT NULL,       -- 单位名称
    unit_type               VARCHAR(20)  NOT NULL,        -- CO_BUILD_UNIV / ENTERPRISE / SHARE_UNIV
    sort_order              INTEGER      DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_psu_proposal ON proposal_shared_units(proposal_id);
CREATE INDEX idx_psu_sort     ON proposal_shared_units(proposal_id, sort_order);
```

| 字段名 | DB 列 | Java 类型 | 约束 | 说明 |
|--------|-------|-----------|------|------|
| id | id | Long | PK 自增 | |
| proposalId | proposal_id | Long | FK→proposals, ON DELETE CASCADE | |
| unitName | unit_name | String(200) | NOT NULL | 单位名称 |
| unitType | unit_type | String(20) | NOT NULL | CO_BUILD_UNIV / ENTERPRISE / SHARE_UNIV |
| sortOrder | sort_order | Integer | DEFAULT 0 | |
| createdAt | created_at | Timestamp | NOT NULL | |
| updatedAt | updated_at | Timestamp | NOT NULL | |

### 6.6 Flyway 版本规划

| 版本 | 文件 | 内容 |
|------|------|------|
| V120 | `V120__phase15_proposal_fields.sql` | 主表 ALTER TABLE 新增 30+ 列 + 新增 DRAFT 初始态默认值 |
| V121 | `V121__phase15_proposal_courses.sql` | 创建 proposal_courses 表 + 索引 |
| V122 | `V122__phase15_proposal_team_members.sql` | 创建 proposal_team_members 表 + 索引 |
| V123 | `V123__phase15_proposal_signatures.sql` | 创建 proposal_signatures 表 + 索引 |
| V124 | `V124__phase15_proposal_shared_units.sql` | 创建 proposal_shared_units 表 + 索引 |
| V125 | `V125__phase15_status_default.sql` | ALTER TABLE SET DEFAULT 'DRAFT'（仅对新行，不改旧数据） |

---

## 7. REST API 全集

### 7.1 端点总览（12 个）

| # | 方法 | 路径 | 权限 | 说明 |
|---|------|------|------|------|
| 1 | POST | `/api/storage-applications/init` | TEACHER | 创建空草稿→返回 id |
| 2 | GET | `/api/storage-applications/my-drafts` | TEACHER | 我的申请表列表（分页） |
| 3 | GET | `/api/storage-applications/{id}` | TEACHER(本人) / ACADEMIC | 申请表详情（含所有子表数据） |
| 4 | PUT | `/api/storage-applications/{id}` | TEACHER(本人, DRAFT) | 全量保存（主表+子表一起提交） |
| 5 | PATCH | `/api/storage-applications/{id}/auto-save` | TEACHER(本人, DRAFT) | 自动保存（含心跳时间戳） |
| 6 | POST | `/api/storage-applications/{id}/upload-image` | TEACHER(本人) | 签名/公章图片上传（≤2MB） |
| 7 | GET | `/api/storage-applications/{id}/preview` | TEACHER(本人) / ACADEMIC | 预览数据（A4 格式化返回） |
| 8 | GET | `/api/storage-applications/{id}/export-word` | TEACHER(本人) / ACADEMIC | 下载 Word（POI 生成，含前置校验） |
| 9 | GET | `/api/storage-applications/{id}/export-pdf` | TEACHER(本人) / ACADEMIC | 下载 PDF（OpenPDF 生成，含前置校验） |
| 10 | POST | `/api/storage-applications/{id}/submit` | TEACHER(本人, DRAFT) | 提交审核（含全量校验） |
| 11 | POST | `/api/storage-applications/{id}/reset-module` | TEACHER(本人, DRAFT) | 重置指定模块 |
| 12 | POST | `/api/storage-applications/{id}/reset-all` | TEACHER(本人, DRAFT) | 重置全部子表数据 |

### 7.2 端点详细规格

#### 1. POST /api/storage-applications/init

```
权限：hasRole('TEACHER')
Request Body: 无
Response: R<Long>  — 返回新建 proposal.id
业务逻辑：
  1. 获取当前用户 ID
  2. 创建 MicroSpecialtyProposal 记录：
     - proposer_id = userId
     - status = 'DRAFT'
     - type = '急需紧缺型'（默认值）
     - 其他字段均为 NULL
  3. 初始化 3 行固定签字（LEAD/DEPT/SCHOOL）插入 proposal_signatures
  4. 返回 proposal.id
```

#### 2. GET /api/storage-applications/my-drafts

```
权限：hasRole('TEACHER')
Query Params: page (default 0), size (default 20), status（可选过滤）
Response: R<PageResult<ProposalListVO>>
  ProposalListVO 字段：id, title, universityFullName, status, type, createdAt, updatedAt, lastAutoSavedAt
排序：updated_at DESC
过滤：仅当前用户本人的 proposal
```

#### 3. GET /api/storage-applications/{id}

```
权限：hasRole('TEACHER') + 本人校验 OR hasRole('ACADEMIC')
Response: R<StorageApplicationDetailVO>
  StorageApplicationDetailVO 包含：
  - 主表所有字段
  - List<ProposalCourseVO> courses
  - List<ProposalTeamMemberVO> teamMembers
  - List<ProposalSignatureVO> signatures
  - List<ProposalSharedUnitVO> sharedUnits
  - String departmentName（JOIN departments）
  - String proposerName（JOIN users）
```

#### 4. PUT /api/storage-applications/{id}

```
权限：hasRole('TEACHER') + 本人校验 + DRAFT 状态校验
Request Body: StorageApplicationSaveRequest {
    // 主表字段
    String universityFullName;
    String title;
    String contactPersonName;
    String contactPhone;
    String contactEmail;
    Integer constructionStartYear;
    Integer constructionEndYear;
    Integer totalHours;
    Integer maxStudents;
    String targetAudience;
    String description;
    String trainingObjective;
    String backgroundSignificance;
    String trainingFeatures;
    String qualityAssurance;
    String expectedOutcomes;
    String additionalNotes;
    Long offerDepartmentId;
    String semester;
    BigDecimal credits;

    // 子表数据（全量替换策略）
    List<ProposalCourseDTO> courses;
    List<ProposalTeamMemberDTO> teamMembers;
    List<ProposalSignatureDTO> signatures;
    List<ProposalSharedUnitDTO> sharedUnits;
}
业务逻辑：
  1. 更新主表字段
  2. 子表全量替换（DELETE WHERE proposal_id + 批量 INSERT）
  3. 更新时间戳
  4. 返回 R.ok()
```

#### 5. PATCH /api/storage-applications/{id}/auto-save

```
权限：hasRole('TEACHER') + 本人校验 + DRAFT 状态校验
Request Body: 与 PUT 相同（部分字段可以为 null，仅更新非 null 字段）
              额外字段：Long heartbeatTimestamp（客户端时间戳用于保活）
Response: R<AutoSaveResult> {
    Long serverTime;
    String status; // "ok"
}
业务逻辑：
  1. 对非 null 字段执行 UPDATE（主表）
  2. 若有子表数据 → 全量替换对应子表
  3. 更新 last_auto_saved_at = NOW()
  4. 返回服务器时间供客户端校对
  限流：单用户最多 1 次/秒（Redis INCR TTL=1s）
```

#### 6. POST /api/storage-applications/{id}/upload-image

```
权限：hasRole('TEACHER') + 本人校验
Request: multipart/form-data
  - file: 图片文件（jpg/png，≤2MB）
  - type: "SIGNATURE" | "SEAL"
Response: R<ImageUploadResult> {
    String url;       // 存储路径
    String thumbnail; // 缩略图路径（150×150 @150dpi）
}
业务逻辑：
  1. 文件类型校验：魔数检查 jpg(FFD8) / png(89504E47)
  2. 大小 ≤ 2MB
  3. 缩放至 150×150 @150dpi（Java BufferedImage + AffineTransform）
  4. 存储路径：/data/storage-applications/{proposalId}/images/{uuid}.{ext}
  5. 返回访问 URL
```

#### 7. GET /api/storage-applications/{id}/preview

```
权限：hasRole('TEACHER') + 本人校验 OR hasRole('ACADEMIC')
Response: R<StorageApplicationDetailVO>（与 GET /{id} 格式相同）
说明：返回完整数据供前端 A4 预览页渲染，与详情接口可复用同一 Service 方法
```

#### 8. GET /api/storage-applications/{id}/export-word

```
权限：hasRole('TEACHER') + 本人校验 OR hasRole('ACADEMIC')
Response: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Headers:
  Content-Disposition: attachment; filename*=UTF-8''【{高校全称}】整理收纳微专业申请表_{yyyyMMdd}.docx
业务逻辑：
  1. 前置校验（§8.2）→ 若不通过返回 400 + 错误清单
  2. 调用 StorageApplicationExportService.exportWord(id)
  3. 使用 Apache POI XWPFDocument 生成
  4. 返回 byte[] 流
```

#### 9. GET /api/storage-applications/{id}/export-pdf

```
权限：hasRole('TEACHER') + 本人校验 OR hasRole('ACADEMIC')
Response: application/pdf
Headers:
  Content-Disposition: attachment; filename*=UTF-8''【{高校全称}】整理收纳微专业申请表_{yyyyMMdd}.pdf
业务逻辑：
  1. 前置校验（§8.2）→ 若不通过返回 400 + 错误清单
  2. 调用 StorageApplicationExportService.exportPdf(id)
  3. 使用 OpenPDF 生成（参考 CertificateServiceImpl.generateCertificatePdf 模式）
  4. 返回 byte[] 流
```

#### 10. POST /api/storage-applications/{id}/submit

```
权限：hasRole('TEACHER') + 本人校验 + DRAFT 状态校验
Request Body: 无（数据已通过 auto-save/PUT 保存在 DB 中）
Response: R<Void>
业务逻辑：
  1. 执行全量提交校验（§8.2）
  2. 若校验失败 → 返回 R.fail(ErrorCode.VALIDATION_FAILED, 错误清单JSON)
  3. 若校验通过：
     a. UPDATE status = 'PENDING_REVIEW'
     b. UPDATE validation_passed = TRUE
     c. UPDATE submitted_at = NOW()
     d. 锁定编辑（后续 PUT/PATCH 均返回 400）
  4. 发送通知给 ACADEMIC："教师 {name} 提交了微专业申请表「{title}」"
```

#### 11. POST /api/storage-applications/{id}/reset-module

```
权限：hasRole('TEACHER') + 本人校验 + DRAFT 状态校验
Request Body: { "module": "COURSES" | "TEAM_MEMBERS" | "SIGNATURES" | "SHARED_UNITS" }
Response: R<Void>
业务逻辑：
  1. COURSES → DELETE FROM proposal_courses WHERE proposal_id = id
  2. TEAM_MEMBERS → DELETE FROM proposal_team_members WHERE proposal_id = id
  3. SIGNATURES → DELETE FROM proposal_signatures WHERE proposal_id = id AND sign_level = 'SHARED_UNIT'
                + 重置 LEAD/DEPT/SCHOOL 固定 3 行为空
  4. SHARED_UNITS → DELETE FROM proposal_shared_units WHERE proposal_id = id
                   + DELETE FROM proposal_signatures WHERE proposal_id = id AND sign_level = 'SHARED_UNIT'
```

#### 12. POST /api/storage-applications/{id}/reset-all

```
权限：hasRole('TEACHER') + 本人校验 + DRAFT 状态校验
Request Body: 无
Response: R<Void>
业务逻辑：
  1. DELETE FROM proposal_courses WHERE proposal_id = id
  2. DELETE FROM proposal_team_members WHERE proposal_id = id
  3. DELETE FROM proposal_signatures WHERE proposal_id = id
  4. DELETE FROM proposal_shared_units WHERE proposal_id = id
  5. 重新初始化固定 3 行签字（LEAD/DEPT/SCHOOL）
  6. 主表字段不重置（保留表头基本信息）
```

### 7.3 Phase 14 审批端点兼容

Phase 15 的提交审核入口是 `POST /api/storage-applications/{id}/submit`，提交后 status=PENDING_REVIEW。

此时复用 Phase 14 已有审批端点：
- `POST /api/micro-specialty-proposals/{id}/approve` — ACADEMIC 审批通过
- `POST /api/micro-specialty-proposals/{id}/reject` — ACADEMIC 驳回
- `POST /api/micro-specialty-proposals/{id}/withdraw` — TEACHER 撤回
- `POST /api/micro-specialty-proposals/{id}/resubmit` — TEACHER 重提

无需新增审批端点。

---

## 8. 关键业务逻辑

### 8.1 自动保存策略

```
┌─ 前端 ──────────────────────────────────────────────────────────┐
│                                                                  │
│  字段变更监听（debounce 1500ms）                                  │
│    │                                                             │
│    ├─→ 1500ms 内无新变更 → 调 PATCH auto-save                    │
│    │                                                             │
│    └─→ 心跳定时器（30s 间隔）                                    │
│          └─→ 30s 到 → 调 PATCH auto-save（附带 heartbeatTimestamp）│
│                                                                  │
│  UI 指示器：                                                      │
│    - "保存中..."  （请求进行中）                                   │
│    - "已自动保存 14:32:15"（请求成功，绿色）                       │
│    - "自动保存失败，点击重试"（请求失败，红色，可点击手动触发）        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

┌─ 后端 ──────────────────────────────────────────────────────────┐
│                                                                  │
│  限流：Redis key "autosave:rate:{userId}" TTL 1s                │
│    若 key 存在 → 429 RATE_LIMITED                                 │
│                                                                  │
│  120s 无心跳 → 返回警告 {"stale": true}                           │
│    前端收到后 toast "编辑会话已过期，请刷新页面"                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 8.2 导出前置校验（提交校验复用同一套规则）

| # | 校验项 | 规则 | 错误码 |
|---|--------|------|--------|
| 1 | 高校全称 | 必填，≤200 字符 | `REQUIRED` / `MAX_LENGTH` |
| 2 | 微专业名称 | 必填，≤200 字符 | `REQUIRED` |
| 3 | 开课学院 | 必填（有效 department_id） | `REQUIRED` |
| 4 | 联系人 | 必填，≤50 字符 | `REQUIRED` |
| 5 | 手机号 | 必填，正则 `^1[3-9]\d{9}$` | `FORMAT_PHONE` |
| 6 | 邮箱 | 必填，标准 email 格式 | `FORMAT_EMAIL` |
| 7 | 建设起始年份 | 2024 ≤ year ≤ 当前年份 | `RANGE_YEAR` |
| 8 | 建设结束年份 | 起始年 ≤ year ≤ 当前年份+10 | `RANGE_YEAR` |
| 9 | 总学分 | ≥0，≤200 | `RANGE_NUMBER` |
| 10 | 总学时 | ≥0，≤5000 | `RANGE_NUMBER` |
| 11 | 招生规模 | ≥0，≤10000 | `RANGE_NUMBER` |
| 12 | 培养目标 | 必填，去 HTML 后 ≥20 字符 | `REQUIRED_RICH_TEXT` |
| 13 | 建设背景与意义 | 去 HTML 后 ≥50 字符 | `MIN_RICH_TEXT_LENGTH` |
| 14 | 课程体系 | ≥1 行，每行课程名称必填 | `MIN_ROWS` |
| 15 | 教学团队 | ≥1 行，每行姓名必填 | `MIN_ROWS` |

**校验失败响应格式**：

```json
{
  "code": 17022,
  "message": "申请表校验未通过",
  "data": {
    "errors": [
      {"field": "contactPhone", "rule": "FORMAT_PHONE", "message": "手机号格式不正确"},
      {"field": "courses[0].courseName", "rule": "REQUIRED", "message": "第1行课程名称不能为空"}
    ],
    "passed": false
  }
}
```

### 8.3 图片上传校验

```
校验链（按顺序）：
  1. 文件非空 → 否则 400 "文件不能为空"
  2. 魔数验证 → 读前 4 字节：
     - JPEG: FF D8 FF E0~EF → 通过
     - PNG:  89 50 4E 47   → 通过
     - 否则 → 400 "仅支持 JPG/PNG 格式"
  3. 大小 ≤ 2MB（2,097,152 bytes） → 否则 413 "图片不能超过2MB"
  4. 缩放处理：
     - BufferedImage 读取
     - 按比例缩放至 MAX(宽,高) ≤ 150px
     - 写入临时文件
  5. 存储：
     - 路径：/data/storage-applications/{proposalId}/images/{UUID}.{ext}
     - 返回 URL：/api/storage-applications/{proposalId}/images/{filename}
```

### 8.4 日期年份区间

```java
// 建设起始年份校验
int currentYear = Year.now().getValue();
if (year < 2024 || year > currentYear) {
    errors.add(new ValidationError("constructionStartYear", "RANGE_YEAR",
        String.format("建设起始年份必须在 2024 ~ %d 之间", currentYear)));
}

// 建设结束年份校验
if (endYear < startYear || endYear > currentYear + 10) {
    errors.add(new ValidationError("constructionEndYear", "RANGE_YEAR",
        String.format("建设结束年份必须在 %d ~ %d 之间", startYear, currentYear + 10)));
}
```

### 8.5 富文本字数统计

```java
/**
 * 去除 HTML 标签后计算纯文本字符数。
 * 使用 Jsoup.parse(text).text() 提取纯文本。
 */
public static int richTextLength(String html) {
    if (html == null || html.isBlank()) return 0;
    return Jsoup.parse(html).text().length();
}
```

### 8.6 手机号正则

```
前端校验（实时） + 后端校验（提交时）：
  Pattern: ^1[3-9]\d{9}$
  示例：13812345678 ✓  |  12345678901 ✗  |  14812345678 ✗
```

---

## 9. 导出专项

### 9.1 Word 导出（Apache POI）

```java
@Service
public class StorageApplicationExportService {

    /**
     * 生成 Word 文档（.docx）。
     * 使用 XWPFDocument + XWPFTable 构建 A4 版面正式申报表。
     *
     * 结构：
     *   页眉：高校开放共享"微专业"资源平台推荐表
     *   一、推荐单位基本情况（XWPFTable，2 列 N 行）
     *   二、课程体系（XWPFTable，6 列表格）
     *   三、教学团队（XWPFTable，8 列表格）
     *   四、推荐意见（固定格式 + 嵌入签名/公章图片 XWPFPicture）
     *   五、共建共享单位（XWPFTable，3 列表格）
     *   页脚：填报日期
     *
     * 字体：宋体（正文）/ 黑体（标题），需嵌入中文字体文件
     * 纸张：A4（595.275 × 841.889 twips）
     * 页边距：上下 2.54cm，左右 3.17cm
     */
    public byte[] exportWord(Long proposalId) {
        // 1. 查主表 + 所有子表
        // 2. 构建 XWPFDocument
        // 3. 逐模块填充
        // 4. 返回 ByteArrayOutputStream.toByteArray()
    }
}
```

**关键技术点**：
- 中文字体：`/resources/fonts/simsun.ttc` 或系统字体
- 表格宽度：`tbl.getCTTbl().addNewTblPr().addNewTblW().setW(BigInteger.valueOf(5000))` 设置全宽
- 图片嵌入：`XWPFParagraph.createRun().addPicture(inputStream, XWPFDocument.PICTURE_TYPE_PNG, filename, Units.toEMU(150), Units.toEMU(150))`
- 合并单元格：`CTTc c1 = row.getCell(0).getCTTc(); CTTc c2 = row.getCell(1).getCTTc(); CTTc c1Merge = ... CTHMerge`

### 9.2 PDF 导出（OpenPDF）

```java
/**
 * 生成 PDF 文档。
 * 使用 OpenPDF（com.lowagie.text.*）构建。
 * 参考现有 CertificateServiceImpl.generateCertificatePdf() 模式。
 *
 * 结构：同 Word 导出模块结构
 *
 * 字体：需嵌入中文字体（simsun.ttc 或 NotoSansCJK）
 *   Font titleFont = FontFactory.getFont("fonts/simsun.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 16, Font.BOLD);
 *   Font bodyFont = FontFactory.getFont("fonts/simsun.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 12, Font.NORMAL);
 *
 * 表格：PdfPTable 嵌套
 * 图片：Image.getInstance(imageUrl) → scaleToFit(100, 50)
 * 页眉页脚：PdfPageEventHelper onEndPage 中添加
 * 页码："第 X 页 / 共 Y 页" —— PdfWriter.setPageEvent(new PageNumberEvent())
 */
public byte[] exportPdf(Long proposalId) {
    // 使用 ByteArrayOutputStream + Document(PageSize.A4)
    // 注意：不在事务内执行（参考 C2-1 修复），避免长事务占用 DB
}
```

### 9.3 文件名规范

```
格式：【{高校全称}】整理收纳微专业申请表_{yyyyMMdd}.{pdf|docx}

示例：
  【XX大学】整理收纳微专业申请表_20260630.pdf
  【XX大学】整理收纳微专业申请表_20260630.docx

高校全称取值为 proposal.university_full_name：
  - 若为空 → 使用 "未知高校"
  - URL 编码处理（RFC 5987）：Content-Disposition: attachment; filename*=UTF-8''...
```

### 9.4 导出前置校验（与 §8.2 共享）

导出 Word/PDF 前强制执行 §8.2 完整校验。若未通过，返回：

```json
{
  "code": 19001,
  "message": "申请表信息不完整，无法导出。请先完善以下内容：",
  "data": {
    "errors": [ ... ],
    "canExport": false
  }
}
```

> 草稿状态（DRAFT）也可导出，但仅用于教师自查。此时校验力度降低（只校验文件格式，不阻断导出）。

---

## 10. 枚举常量

### 10.1 ProposalType

```java
public enum ProposalType {
    URGENT_SHORTAGE("急需紧缺型", "面向国家战略急需或市场紧缺人才领域");

    private final String label;
    private final String description;
}
```

### 10.2 SignLevel

```java
public enum SignLevel {
    LEAD("负责人签字"),
    DEPT("院系意见"),
    SCHOOL("学校意见"),
    SHARED_UNIT("共享单位意见");

    private final String label;
}
```

### 10.3 UnitType

```java
public enum UnitType {
    CO_BUILD_UNIV("共建高校"),
    ENTERPRISE("企业/行业"),
    SHARE_UNIV("共享高校");

    private final String label;
}
```

### 10.4 ModuleType（用于 reset-module）

```java
public enum ModuleType {
    COURSES,
    TEAM_MEMBERS,
    SIGNATURES,
    SHARED_UNITS
}
```

### 10.5 ImageType（用于 upload-image）

```java
public enum ImageType {
    SIGNATURE,
    SEAL
}
```

### 10.6 ErrorCode 新增（8 个，编号范围 19001-19008）

```java
// Phase 15: 整理收纳微专业申请表 19xxx

/** 申请表校验未通过（导出/提交前），data 中包含具体字段错误清单 */
VALIDATION_FAILED(19001, "申请表校验未通过", 400),

/** 申请表未找到 */
STORAGE_APPLICATION_NOT_FOUND(19002, "申请表不存在", 404),

/** 不可导出——未通过导出前置校验 */
EXPORT_VALIDATION_FAILED(19003, "申请表信息不完整，无法导出", 400),

/** 图片格式不支持 */
IMAGE_FORMAT_INVALID(19004, "仅支持 JPG/PNG 格式图片", 400),

/** 图片过大 */
IMAGE_TOO_LARGE(19005, "图片大小不能超过 2MB", 413),

/** 自动保存过于频繁 */
AUTO_SAVE_RATE_LIMITED(19006, "自动保存过于频繁，请稍后再试", 429),

/** 编辑会话已过期 */
EDIT_SESSION_EXPIRED(19007, "编辑会话已过期，请刷新页面", 400),

/** 模块重置不支持 */
MODULE_RESET_INVALID(19008, "不支持的模块名称，可选：COURSES/TEAM_MEMBERS/SIGNATURES/SHARED_UNITS", 400);
```

> **ErrorCode 范围分配**：
> - Phase 14 微专业：`17001-17999`
> - Phase 15 申请表：`19001-19999`
> - 预留 18xxx 给未来 Phase

---

## 11. 逻辑闭环自查表

> 格式：每行 = 场景 → 期望行为。标记 ✅ = 已验证。

### 11.1 创建与初始化（8 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 1 | 教师点击「创建申请表」 | POST init 创建 DRAFT 记录 + 3 行固定签字，返回 id |
| 2 | 同一教师多次创建 | 每次创建独立新记录，无限制 |
| 3 | 非 TEACHER 角色调 init | 403 No Permission |
| 4 | 创建后默认 type | 固定为「急需紧缺型」 |
| 5 | 创建后 status | DRAFT |
| 6 | 创建后 3 行签字 | LEAD/DEPT/SCHOOL 自动插入 proposal_signatures（opinion/signature/seal 均为 null） |
| 7 | 创建后其他子表 | 空（无 course/team/shared_unit 行） |
| 8 | 创建后列表可见 | 在 my-drafts 列表中可见（status=DRAFT） |

### 11.2 编辑与保存（10 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 9 | 编辑 DRAFT 状态申请表 | PUT/PATCH 全部成功 |
| 10 | 编辑 PENDING_REVIEW 状态 | 400 MS_STATUS_INVALID "申请表已提交审核" |
| 11 | 编辑 APPROVED 状态 | 400 MS_STATUS_INVALID |
| 12 | 编辑他人申请表 | 403 NO_PERMISSION |
| 13 | PUT 全量保存 | 主表更新 + 子表全量替换（DELETE+INSERT） |
| 14 | PATCH auto-save null 字段 | 仅更新非 null 字段 |
| 15 | auto-save 并发（同用户 1s 内 2 次） | 429 RATE_LIMITED |
| 16 | auto-save 120s 无心跳 | 返回 {"stale": true}，前端提示刷新 |
| 17 | auto-save 成功后 UI | 显示 "已自动保存 HH:mm:ss" |
| 18 | PUT 后 updated_at | 更新为当前时间 |

### 11.3 图片上传（8 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 19 | 上传 JPG 签名（≤2MB） | 成功，返回 URL + 缩略图 URL |
| 20 | 上传 PNG 公章（≤2MB） | 成功 |
| 21 | 上传 GIF 文件 | 400 IMAGE_FORMAT_INVALID |
| 22 | 上传 3MB JPG | 413 IMAGE_TOO_LARGE |
| 23 | 上传空文件 | 400 BAD_REQUEST_PARAM |
| 24 | 缩放处理 | 宽/高 max 150px @150dpi |
| 25 | 存储路径 | /data/storage-applications/{id}/images/{uuid}.{ext} |
| 26 | 文件同名覆盖 | UUID 命名保证不冲突 |

### 11.4 提交审核（8 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 27 | DRAFT 提交，全字段合法 | status→PENDING_REVIEW，validation_passed=TRUE |
| 28 | DRAFT 提交，手机号格式错 | 返回 19001 + errors: [{field:"contactPhone", rule:"FORMAT_PHONE"}] |
| 29 | DRAFT 提交，必填字段缺失 | 返回 19001 + errors 含具体 field |
| 30 | DRAFT 提交，课程体系为空 | 返回 19001 + errors: [{field:"courses", rule:"MIN_ROWS"}] |
| 31 | 非 DRAFT 状态提交 | 400 MS_STATUS_INVALID |
| 32 | 提交他人申请表 | 403 NO_PERMISSION |
| 33 | 提交成功后编辑 | 400（锁定） |
| 34 | 提交后通知 ACADEMIC | 通知 "教师 XX 提交了申请表「XX」" |

### 11.5 审批（复用 Phase 14）（6 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 35 | ACADEMIC approve PENDING_REVIEW | status→APPROVED，通知申报人 |
| 36 | ACADEMIC reject PENDING_REVIEW | status→REJECTED + review_comment，通知申报人 |
| 37 | ACADEMIC 审批 DRAFT 状态 | 400（仅 PENDING_REVIEW 可审批） |
| 38 | 申报人 withdraw PENDING_REVIEW | status→WITHDRAWN |
| 39 | 申报人 resubmit REJECTED | status→PENDING_REVIEW（修改后重提交） |
| 40 | 审批后通知 | 复用 Phase 14 通知 MS_PROPOSAL_APPROVED / MS_PROPOSAL_REJECTED |

### 11.6 预览与导出（10 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 41 | GET preview（DRAFT） | 返回完整只读数据 |
| 42 | GET preview（APPROVED） | 返回完整只读数据 |
| 43 | GET preview 他人 | 403 |
| 44 | GET export-word（信息完整） | 下载 .docx，文件名含高校全称+日期 |
| 45 | GET export-word（信息不完整） | 400 EXPORT_VALIDATION_FAILED + 错误清单 |
| 46 | GET export-pdf（信息完整） | 下载 .pdf |
| 47 | GET export-pdf（信息不完整） | 400 EXPORT_VALIDATION_FAILED + 错误清单 |
| 48 | Word 生成含图片 | XWPFPicture 嵌入签名/公章 |
| 49 | PDF 生成含图片 | Image.getInstance 嵌入 |
| 50 | 导出 ACL（ACADEMIC） | ACADEMIC 可导出任意已提交申请表 |

### 11.7 重置操作（6 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 51 | reset-module COURSES | proposal_courses 清空 |
| 52 | reset-module TEAM_MEMBERS | proposal_team_members 清空 |
| 53 | reset-module SIGNATURES | SHARED_UNIT 签字删除，LEAD/DEPT/SCHOOL 重置为空 |
| 54 | reset-module SHARED_UNITS | proposal_shared_units 清空 + 关联签字删除 |
| 55 | reset-all | 全部子表清空 + 重新初始化 3 行签字，主表保留 |
| 56 | 非 DRAFT 状态重置 | 400 |

### 11.8 列表与权限（6 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 57 | my-drafts（TEACHER） | 仅返回当前用户本人的 proposal |
| 58 | my-drafts 分页 | page/size 参数生效 |
| 59 | my-drafts 按状态过滤 | ?status=DRAFT 过滤 |
| 60 | ACADEMIC 全部待审列表 | 复用 GET /api/micro-specialty-proposals |
| 61 | ACADEMIC 查看详情 | GET /api/storage-applications/{id}（无本人校验） |
| 62 | ADMIN 只读查看 | 同上 |

### 11.9 枚举与常量（4 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 63 | 前端获取类型列表 | GET /api/enums/export 包含 ProposalType/SignLevel/UnitType |
| 64 | 类型默认值 | 创建时自动设为「急需紧缺型」 |
| 65 | SignLevel 固定 3 行 | LEAD/DEPT/SCHOOL 不可删除 |
| 66 | UnitType 下拉 | 共建高校/企业/共享高校 三选一 |

### 11.10 边界与异常（14 项）

| # | 场景 | 期望行为 |
|---|------|---------|
| 67 | 手机号 12345678901 | 校验失败 FORMAT_PHONE |
| 68 | 建设起始年 2023 | 校验失败 RANGE_YEAR（≥2024） |
| 69 | 建设结束年 2040 | 校验失败 RANGE_YEAR（≤当前年+10） |
| 70 | 总学分 -1 | 校验失败 RANGE_NUMBER |
| 71 | 培养目标为空 | 校验失败 REQUIRED_RICH_TEXT |
| 72 | 富文本仅含 `<p></p>` | 校验失败（去标签后长度=0） |
| 73 | 课程体系 0 行 | 校验失败 MIN_ROWS |
| 74 | 教学团队 0 行 | 校验失败 MIN_ROWS |
| 75 | 高校全称 >200 字符 | 前端 maxlength 限制，后端截断校验 |
| 76 | 并发提交（2 个标签页同时 submit） | 第一个成功（status→PENDING_REVIEW），第二个 400（status 不是 DRAFT） |
| 77 | proposal 不存在 GET /{id} | 404 STORAGE_APPLICATION_NOT_FOUND |
| 78 | proposal 级联删除（主表删→子表自动删） | ON DELETE CASCADE 保证 |
| 79 | 旧版 proposal（Phase 14 创建的 PENDING_REVIEW）在新 API 中 GET | 正常返回，新增字段为 null（兼容） |
| 80 | 旧版 proposal 的 auto-save | 400（status 非 DRAFT，跳过） |

---

## 12. ROADMAP & 实施节奏

### 12.1 14 步实施计划

| 步 | 阶段 | 内容 | 涉及文件 | 预计 assistant | 验收标准 |
|----|------|------|---------|:-----:|---------|
| 1 | **DB** | Flyway V120-V125 migration | `micro-course-api/src/main/resources/db/migration/V12*.sql` | 2 | `mvn flyway:migrate` 成功，表/列/索引全部创建 |
| 2 | **Entity** | 主表扩展 + 4 子表 Entity | `entity/MicroSpecialtyProposal.java`（改）+ `entity/ProposalCourse.java` 等 4 个（新） | 2 | 编译通过，@TableName/@TableField 映射正确 |
| 3 | **DTO** | Request/Response VO/DTO | `dto/storageApplication/*.java` ~10 个 | 2 | JSON 序列化/反序列化正确 |
| 4 | **Enum** | ProposalType/SignLevel/UnitType/ModuleType/ImageType + ErrorCode | `enums/*.java` + `exception/ErrorCode.java` | 1 | EnumExportController 可导出新增枚举 |
| 5 | **Repository** | 子表 MyBatis-Plus Mapper | `repository/ProposalCourseRepository.java` 等 4 个 | 1 | CRUD 通过测试 |
| 6 | **Service** | StorageApplicationService（核心 CRUD + 校验 + 状态机） | `service/StorageApplicationService.java` + impl | 3 | 单元测试覆盖所有状态转换 |
| 7 | **Service** | StorageApplicationExportService（Word/PDF 生成） | `service/StorageApplicationExportService.java` + impl | 3 | 生成文件可打开、格式正确、含中文 |
| 8 | **Service** | StorageApplicationAutoSaveService（心跳+限流） | `service/StorageApplicationAutoSaveService.java` + impl | 1 | 限流生效，stale 检测正常 |
| 9 | **Controller** | StorageApplicationController（12 端点） | `controller/StorageApplicationController.java` | 3 | 全部端点可调通，@PreAuthorize 生效 |
| 10 | **Frontend** | MicroSpecialtyProposal.vue 重做（5 模块表单） | `micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue` | 4 | 5 模块均可填写、自动保存、提交 |
| 11 | **Frontend** | MyProposals.vue 增强（列表+操作） | `micro-course-admin/src/views/teacher/MyProposals.vue` | 2 | 状态标签正确、预览/导出按钮可用 |
| 12 | **Frontend** | StorageApplicationPreview.vue 新增（A4 预览） | `micro-course-admin/src/views/teacher/StorageApplicationPreview.vue` | 3 | A4 打印样式正确、图片内嵌显示、导出按钮可用 |
| 13 | **Frontend** | MicroSpecialtyProposalReview.vue 增强 | `micro-course-admin/src/views/academic/MicroSpecialtyProposalReview.vue` | 2 | 详情查看可展示完整申请表、审批按钮可用 |
| 14 | **联调** | 全链路集成测试 + E2E | 端到端流程 | 3 | 学生旅程+教务旅程全线走通 |

### 12.2 质量门禁（强制）

```
Step 14 完成后强制执行：

R1 - 代码质量+契约
  □ 无 Lombok（全项目禁止）
  □ 无 @Autowired 字段注入（构造器注入）
  □ 所有 Controller 返回 R<T> 包装
  □ 所有 POST/PUT @Valid 校验
  □ ErrorCode 枚举无重复编号

R2 - DB 迁移
  □ 逐表逐字段 vs 本 spec §6
  □ ALTER TABLE 全部含 IF NOT EXISTS / DEFAULT
  □ 索引覆盖所有查询路径

R3 - 安全+配置
  □ @PreAuthorize 覆盖所有端点
  □ 图片上传魔数校验
  □ XSS 过滤（rich text 保存前）
  □ auto-save 限流

R4 - 跨域一致性
  □ FK 链完整（ON DELETE CASCADE 正确）
  □ REST 路径驼峰转下划线一致
  □ Service 接口/实现方法签名一致

R5 - 前端 UI/UX
  □ R5a 视觉一致性：Element Plus 组件规范、三态（Loading/Empty/Error）
  □ R5b 页面交互：路由守卫、表单提交反馈、token 持久化
  □ R5c 数据交互：Pinia store / API 调用 / 分页一致性
```

### 12.3 技术依赖

| 依赖 | 用途 | 版本要求 |
|------|------|---------|
| Apache POI (poi-ooxml) | Word .docx 生成 | 5.2.5+ |
| OpenPDF | PDF 生成（已引入） | 1.3.30+ |
| Jsoup | 富文本 HTML 解析/字数统计（已引入） | 1.17.2+ |
| Redis | auto-save 限流 / 心跳过期 | 已配置 |
| Flyway | DB migration | 9.22.3 |

---

*文档版本：v1.0*
*日期：2026-06-30*
*基线：Phase 14 spec v1.1 / micro_specialty_proposals 表 V84-V105*
*作者：微课平台架构组*
*状态：待用户确认 → Phase 4 执行*
