---
title: 教师课程管理页面重构方案
date: 2026-07-04
status: draft
author: AI Architect
---

# 教师课程管理页面重构方案

## 1. 概述

教师端课程管理现有页面存在功能重叠、菜单层级扁平化、实体关系不清晰等问题。本方案从实体关系出发，重新设计菜单结构、页面职责分工和交互流程。

### 1.1 核心目标

- 明确各页面职责，消除功能重叠
- 菜单层级反映实体归属关系（课程→章节→视频/课件/线下课）
- 题库独立管理，支持章节关联和智能组卷
- 互动课件独立工作台，聚焦课件操作

### 1.2 涉及页面

| 页面 | 路由 | 职责 |
|------|------|------|
| 课程列表 | `/teacher/courses` | 全类型课程 CRUD |
| 课程详情 | `/teacher/courses/:id` | 课程内部管理（基本信息/章节/视频/线下课） |
| 互动课件工作台 | `/teacher/slides` | INTERACTIVE 课程课件概览与管理 |
| 课件管理 | `/teacher/courses/:courseId/slides/manage` | 单课程课件深度编辑 |
| 题库管理 | `/teacher/questions` | 题目 CRUD，关联章节 |
| 试卷管理 | `/teacher/exams` | 智能组卷与试卷管理 |

---

## 2. 实体关系模型

```
Course (课程)
  ├── type: VIDEO | INTERACTIVE | OFFLINE
  ├── Chapters (章节) — 增删改排序
  │     ├── [VIDEO] Chapter → Videos (视频)
  │     └── [OFFLINE] Chapter (学习主题) → topic + description + time + location
  ├── Slides (课件) — 仅 INTERACTIVE
  │     ├── Pages — 图片/缩略图/提取文本
  │     ├── Narration — AI 讲述稿
  │     └── Audio — TTS 音频
  └── Exercises (练习题) — 通过 question_chapter 关联

Question Bank (题库) — 独立 CRUD
  ├── Question — type(SINGLE/MULTIPLE/JUDGE/FILL), content, options, answer
  ├── question_chapter — 题目 ↔ 章节（多对多）
  ├── Exam — 试卷（由组卷生成）
  └── ExamRule — 组卷规则（选章节+题型+数量）
```

---

## 3. 菜单结构

```
教学看板
  ├── 我的看板             → /teacher/dashboard

课程管理
  ├── 我的课程             → /teacher/courses
  └── 课程套餐             → /bundles

题库管理
  ├── 题库列表             → /teacher/questions
  └── 试卷管理             → /teacher/exams

课件管理
  └── 互动课件             → /teacher/slides

学员管理
  ├── 学员列表             → /teacher/students
  ├── 成绩管理             → /teacher/grades
  ├── 我的教学班           → /teacher/teaching-classes
  ├── 讨论区               → /teacher/discussions
  └── 收藏管理             → /teacher/favorites

微专业管理
  ├── 我的微专业           → /teacher/micro-specialties
  ├── 微专业申报           → /teacher/micro-specialties/proposals
  ├── 我的申报             → /teacher/micro-specialties/my-proposals
  └── 邀请列表             → /teacher/micro-specialties/invites
```

### 3.1 菜单变更记录

| 变更 | 原因 |
|------|------|
| 移除「章节管理」独立菜单 | 章节是课程内部资源，从课程详情页进入 |
| 移除「视频管理」独立菜单 | 同上，仅 VIDEO 课程显示 |
| 移除「练习管理」菜单（旧版） | 练习归入题库管理 |
| 新增「题库管理」分组 | 题目 CRUD + 试卷管理 |
| 新增「课件管理」分组 | 互动课件独立工作台 |
| 「互动课件」从"课程管理"移入"课件管理" | 职责分离 |

---

## 4. 页面职责与交互流

### 4.1 课程列表 `/teacher/courses`

- 展示教师所有课程，按类型/状态/关键字筛选
- 操作列（按角色显示）：
  - 编辑 → `/courses/:id/edit`
  - 课件 → `/teacher/courses/:id/slides/manage`（仅 INTERACTIVE）
  - 审核/发布/下架（仅 ADMIN/ACADEMIC）
  - 查看 → 公开页面
  - 复制/删除
- 新增课程弹窗：标题 + 分类 + 教师 + 类型（VIDEO/INTERACTIVE/OFFLINE）

### 4.2 课程详情页 `/teacher/courses/:id`（新增）

动态 TAB 栏，按课程类型显示：

| 课程类型 | 显示的 TAB |
|---------|-----------|
| VIDEO | 基本信息、章节管理、视频管理 |
| INTERACTIVE | 基本信息（含"管理课件"按钮跳转 SlideManage） |
| OFFLINE | 基本信息、章节管理（学习主题）、线下课堂 |

#### 4.2.1 基本信息 TAB

编辑课程标题、分类、描述、封面、学分、难度、价格等。

#### 4.2.2 章节管理 TAB

- 章节列表：增删改排序（拖拽）
- 线下课模式：章节 = 学习主题，每项含 topic / description / time / location

#### 4.2.3 视频管理 TAB（仅 VIDEO）

- 视频列表：上传/删除/预览
- 按章节筛选

#### 4.2.4 线下课堂 TAB（仅 OFFLINE）

- 学习主题列表：手动添加/编辑/删除
- 每项：主题名 + 介绍 + 学习时间 + 地点

### 4.3 互动课件工作台 `/teacher/slides`

- 自动筛选 INTERACTIVE 课程
- 统计卡片：总数 / 就绪 / 渲染中 / 失败
- 课程表格：课程名 + 课件状态 + 页数 + AI 进度 + 音频进度
- 操作：新增互动课程（弹窗快捷创建）、管理课件、删除课件

### 4.4 题库管理 `/teacher/questions`

- 题目列表 CRUD：类型（单选/多选/判断/填空）+ 内容 + 答案 + 难度
- 关联章节：多选（1~N 个章节）
- 筛选：按课程→章节→类型→难度
- 批量操作：批量关联章节、批量导出

### 4.5 试卷管理 `/teacher/exams`

- 试卷列表：标题 + 题目数 + 总分 + 创建时间
- 新增试卷 → 组卷规则配置：
  - 选择章节（多选）
  - 选题型（多选）
  - 各题型数量（输入）
  - 自动计算总分
  - 一键组卷 → 随机从题库抽取 → 预览 → 确认保存
- 试卷不可编辑，需重组卷覆盖

---

## 5. CRUD 矩阵

| 实体 | 创建 | 读取 | 更新 | 删除 | 特殊操作 |
|------|:----:|:----:|:----:|:----:|---------|
| 课程 | ✅ | ✅ | ✅ | ✅ | 复制、审核、发布、下架 |
| 章节 | ✅ | ✅ | ✅ | ✅ | 排序（拖拽） |
| 视频 | ✅ | ✅ | ✅ | ✅ | 上传/预览 |
| 课件 | ✅（上传 PPT） | ✅ | ✅（替换） | ✅ | 渲染/讲述稿/音频 |
| 课件页面 | ❌（渲染生成） | ✅ | ✅（讲述稿） | ✅ | 排序（拖拽） |
| 题目 | ✅ | ✅ | ✅ | ✅ | 批量关联章节、导出 |
| 试卷 | ✅（组卷生成） | ✅ | ❌（重新组卷） | ✅ | 随机抽题 |
| 线下学习主题 | ✅ | ✅ | ✅ | ✅ | — |

---

## 6. 实现优先级

| 优先级 | 内容 | 估算 |
|--------|------|------|
| P0 | 菜单结构调整（menuConfig.js） | 小 |
| P0 | 互动课件工作台功能补全（TeacherSlideOverview 加新增课程/统计卡片） | 中 |
| P1 | 课程详情页（含动态 TAB） | 大 |
| P1 | 章节管理 TAB（CRUD + 排序） | 中 |
| P2 | 题库管理增强（章节关联 + 批量操作） | 中 |
| P2 | 试卷管理（组卷规则 + 试卷 CRUD） | 大 |
| P3 | 线下课堂 TAB（学习主题 CRUD） | 中 |
| P3 | 移除独立章节/视频管理菜单 | 小 |

---

## 7. 成功标准

- [ ] 菜单层级反映实体关系，无功能重叠
- [ ] 教师可在课程详情页内完成章节/视频/线下课管理，无需跳出
- [ ] 题库支持关联 1~N 章节，支持按章节筛选
- [ ] 智能组卷支持选章节+选题型+设数量→随机抽题
- [ ] 互动课件工作台是教师管理课件的单一入口
- [ ] 所有实体均有完整 CRUD（试卷除外，不可编辑）
