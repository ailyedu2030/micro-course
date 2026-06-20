# 教师课程管理体验 · 竞品分析报告

> 分析日期：2026-06-20
> 分析范围：中国大学MOOC / Canvas LMS / Teachable / Thinkific / ClassIn
> 用途：指导微课平台教师端 Course Management 设计

---

## 一、全景对比矩阵

| 维度 | 中国大学MOOC | Canvas LMS | Teachable | Thinkific | ClassIn |
|------|-------------|-----------|-----------|-----------|---------|
| 产品类型 | 高校 MOOC 平台 | 学校 LMS | 创作者变现 | 创作者变现 | 直播互动教学 |
| 课程粒度 | 学期制 | 学期制 | 终身制 | 终身制 | 课时制 |
| 内容类型 | 视频+文档+测验 | 模块+作业+讨论 | 视频+文件+测验 | 视频+下载+直播 | 直播+板书+互动 |
| 教师入口 | 顶部导航栏「教师中心」 | 全局导航 Dashboard | 左侧 Sidebar → Products | 左侧 Sidebar → Products | 后台管理面板 |
| 课程状态体系 | 待审核/审核中/已发布/未通过 | 未发布/已发布 | Draft / Published | Draft / Published | 未开始/进行中/已结束 |

---

## 二、平台深度分析

### 2.1 中国大学MOOC (icourse163.org)

**产品定位**：高校官方 MOOC 平台，教师是"课程团队"的一部分。

**课程创建流程**：
1. 教师登录 → 顶部导航「教师中心」→ 我的课程
2. 点击「新建课程」→ 弹出课程基本信息表单
3. 填写：课程名称、封面图、所属学校、课程分类、课程简介
4. 提交后进入**审核流程**— 校级管理员审核 → 平台审核
5. 审核通过后进入「课程建设」页面

**课程列表面 (课程列表页)**：
- 卡片式展示：封面图 + 课程名称 + 学期
- 状态标签：`待审核`(灰色)、`审核中`(蓝色)、`已发布`(绿色)、`未通过`(红色)
- 每张卡片底部操作：编辑课程、课程建设、查看数据、预览
- 右上角筛选：全部 / 进行中 / 已结束 / 待审核

**课程内容管理**：
- 左侧 Tab 导航：课程信息 / 课程大纲 / 章节内容 / 测验作业 / 考试 / 讨论区
- 「章节内容」使用树形结构：章 → 节 → 知识点（视频/文档/讨论）
- 每个知识点是一个独立单元，可上传视频(MP4)、PDF、嵌入测验
- 视频上传后自动转码，有转码进度条

**进度指示器**：
- 每章右侧显示：`0/3 已完成` 的完成计数
- 头部显示整体进度条：`课程完成度 60%`
- 未完成的章节有红色 `！` 标记

**关键 UI 模式**：
- `审核状态标签` — 4 种颜色区分状态
- `树形课程大纲` — 章 → 节 → 知识点的三级嵌套
- `知识点类型图标` — 视频图标/文件图标/测验图标 区分
- `底部固定操作栏` — 保存/预览/发布 始终可见

---

### 2.2 Canvas LMS

**产品定位**：学校/机构的全功能 LMS，教师是"课程管理员"。

**课程创建流程**：
1. 登录后进入 Dashboard → 右侧「Start a New Course」
2. 选择：创建空白课程 / 从 Commons 导入 / 复制已有课程
3. 填写：课程名称、课程代号、学期起止日期
4. 自动创建后进入 Course Home Page → 设置首页样式

**教师课程列表 (Courses)**：
- 两种视图：**Card View** (网格卡片) / **List View** (列表)
- Card View：课程卡片 + 课程代号 + 学期 + 学生数
- 每门课程内左侧导航栏：Modules / Assignments / Quizzes / Files / Pages / Grades / Settings
- List View 支持按学期/状态/名称排序

**内容管理 (核心是 Modules)**：
- Modules = 模块，是 Canvas 最核心的内容组织方式
- 每个 Module 包含：Pages / Assignments / Quizzes / Files / Discussions / External URLs
- 教师可以：
  - 拖拽排序 Module 和 Module 内的项目
  - 设置"发布条件"(prerequisites) — 必须先完成 Module A 才能解锁 Module B
  - 设置"完成要求"(requirements) — 必须看完/提交/得分才能标记完成
  - 批量发布/取消发布 Module 内的所有项

**导航模式**：
- **Global Navigation**（始终固定在顶部）：
  - Dashboard / Courses / Calendar / Inbox / Account
- **Course Navigation**（左侧 Sidebar，每门课程内）：
  - Modules / Assignments / Quizzes / Grades / People / Files / Settings
  - 教师可以拖拽自定义 Sidebar 项的显示/隐藏

**进度指示器**：
- 每个 Module 显示：`1 of 3 items complete`
- Module 项左侧有状态图标：`◻`(未开始) / `🔄`(进行中) / `✓`(已完成)
- Dashboard 的 To-Do List 展示待办事项

**关键 UI 模式**：
- `Module 条件性发布` — prerequisites + requirements 双维度控制
- `拖拽排序` — 所有内容均支持拖拽重排
- `批处理操作` — 选择多个项目批量发布/删除/移动
- `Course Navigation` — 左侧 Sidebar 是课程内所有工具的入口

---

### 2.3 Teachable

**产品定位**：创作者变现平台，教师是"课程创业者"。

**课程创建流程**：
1. 从多个入口创建：Dashboard「Create new product」/ Sidebar `+` / Courses 页面「New course」
2. **4 步创建向导 (Wizard)**：
   - Step 1: 课程标题 + 作者 + 描述(可选) + 封面图
   - Step 2: 封面图上传(可选)
   - Step 3: 定价方案(可跳过)— 一次性/分期/订阅/免费
   - Step 4: 选择课程大纲生成方式：
     - AI 自动生成（根据描述自动创建章节和课程结构）
     - 从零开始
     - 批量上传（文件自动转为课程）
     - 从其他课程复制
3. 创建后跳转到 **Setup Guide** — 一个 checklist 式设置向导

**课程列表面**：
- Sidebar → Products 下方列出所有课程
- 每门课程显示：封面缩略图 + 标题 + Draft/Published 状态
- 点击进入后顶部有快速操作：Publish / Preview / Duplicate / Delete

**内容管理 (Curriculum)**：
- 课程 = 树形结构：Course → Section(章) → Lesson(节)
- 每节课可以包含：视频 / 文件下载 / 图文 / 测验 / 嵌入代码
- 课程内容编辑器提供了丰富的拖拽和批量操作
- 每节课有独立状态：`Published`(绿色) / `Draft`(灰色) / `Public Preview`(蓝色)
- Section 上的 Quick Actions：Publish all / Unpublish all / Set public preview

**课程设置 Tab**：
- Information: 标题/描述/封面/SEO
- Curriculum: 课程内容
- Design: 布局选择(Simple / Colossal)
- Settings: 类别/进度条开关/合规要求/Drip 发布
- Pricing: 定价方案
- Pages: 销售页/感谢页编辑

**进度指示器**：
- Setup Guide 显示：`3 of 8 steps complete`
- 课程发布时提示：`You have 3 unpublished lessons`
- 课程 Compliance 设置要求打勾：完成视频观看/测验

**关键 UI 模式**：
- `Setup Guide Checklist` — 创建后引导式进度清单
- `Quick Actions` — Section 级别的批量发布/预览操作
- `Draft/Published/Preview` — 三级发布状态管理体系
- `课程创建 Wizard` — 4 步引导，每步可跳过

---

### 2.4 Thinkific

**产品定位**：与 Teachable 类似，侧重课程营销和生态系统。

**课程创建流程**：
1. 从 Admin Dashboard → Products → +New Product → Course
2. 选择创建方式：从模板 / AI 生成大纲 / 从零开始
3. 填写：课程标题 + 描述 + 封面图
4. 完成创建后进入 Course Builder 编辑界面

**内容管理 (Course Builder)**：
- 左侧：课程大纲树（章节和课时列表）
- 中间：选中课时的编辑区域
- 右侧：课时设置（发布时间 / Drip / 预览）
- 课时类型：
  - Video (上传/嵌入 YouTube/Vimeo)
  - Quiz (选择题/判断题/填空题)
  - Text (富文本编辑器)
  - Download (PDF 等文件下载)
  - Audio (音频)
  - Assignment (学生上传作业)
  - Survey (问卷调查)
  - Live Lesson (Zoom 直播)
- 支持拖拽重新排序章节和课时

**课程设置 Tab 体系**：
- Overview: 标题/描述/分类
- Curriculum: 课程内容
- Pricing: 定价
- Checkout: 结账设置
- Promotions: 优惠券
- Reports: 学生进度/销售数据
- Settings: Drip 规则/证书/学习路径

**进度指示器**：
- 课程完成进度条（学生视角可隐藏）
- Drip Content 设置：按天数/按日期释放内容
- 课程的 Prerequisite 设置：课程间先后顺序

**关键 UI 模式**：
- `Course Builder` — 左中右三栏布局，大纲+编辑+属性
- `课时类型选择器` — 9 种课时类型一目了然
- `AI Outline Generator` — 根据描述词自动生成课程大纲
- `Drip Schedule` — 时间维度内容释放

---

### 2.5 ClassIn / 百家云

**产品定位**：直播互动教学平台，侧重实时课堂体验。

**课程管理模式**：
- 与上述平台最显著差异：**以课时(Class)为最小单元**，而非课程章节
- 教师后台：课程列表 → 班级管理 → 课时管理
- 每节课 = 一个固定的直播时间段

**内容准备**：
- 课件库：上传 PPT / Word / PDF / 图片 / 视频
- 板书录制：教学过程板书自动保存为 EDB 文件
- 教具管理：计时器/骰子/抽奖/抢答器等互动工具
- 课程回放：自动录制，教师管理回放权限

**关键 UI 模式**：
- `课节时间线` — 按时间顺序排列的课节列表
- `课件库` — 独立于课程的素材仓库
- `教学工具面板` — 直播中浮动工具栏
- `班级管理` — 学生名单/签到/出勤统计

---

## 三、跨平台 UI 模式提取

### 3.1 课程列表视图模式

| 模式 | 描述 | 参考平台 |
|------|------|---------|
| **卡片网格** | 封面图 + 标题 + 状态标签，2-4 列网格 | 中国大学MOOC, Canvas, Teachable |
| **列表行** | 标题 + 状态 + 日期 + 操作的紧凑列表 | Canvas (List View) |
| **Sidebar 列表** | 左侧 Sidebar 内仅显示课程名称 | Teachable, Thinkific |

**推荐**：微课平台使用**卡片网格**为默认视图，提供切换至**列表行**的选项。

### 3.2 状态标签体系

| 标签 | 颜色 | 语义 | 使用平台 |
|------|------|------|---------|
| Draft | 灰色 | 未发布，仅教师可见 | Teachable, Thinkific, Canvas |
| Published | 绿色 | 已发布，学生可见 | 全部平台 |
| Reviewing | 蓝色 | 提交审核，等待管理员 | 中国大学MOOC |
| Rejected | 红色 | 审核未通过 | 中国大学MOOC |
| Scheduled | 橙色 | 定时发布 | Teachable, Thinkific |

### 3.3 课程详情页导航模式

| 模式 | 布局 | 代表 |
|------|------|------|
| **左侧 Tab 导航** | 左侧垂直 Tab 栏，右侧内容区 | Thinkific Course Builder |
| **水平 Tab 导航** | 顶部水平 Tab 切换 | 中国大学MOOC |
| **左侧 Sidebar 导航** | 课程内所有工具入口在左侧 Sidebar | Canvas |

**推荐**：微课平台使用**左侧 Tab 导航**（Thinkific 模式），因为：
- 垂直空间利用更充分（可放更多 Tab）
- Tab 标签可以更清晰（图标+文字）
- 与大多数管理后台模式一致

### 3.4 内容组织层次

| 层级 | Canvas | Teachable | Thinkific | 中国大学MOOC |
|------|--------|-----------|-----------|-------------|
| L1 | Course | Course | Course | 课程 |
| L2 | Module | Section | Chapter | 章 |
| L3 | Item | Lesson | Lesson | 节 |
| L4 | — | — | — | 知识点 |

**推荐**：微课平台使用 `课程 → 章 → 节` 的三层结构，每节课可以是视频/图文/测验/作业/讨论。

### 3.5 快速操作模式

| 操作 | Teachable | Canvas | Thinkific | 中国大学MOOC |
|------|-----------|--------|-----------|-------------|
| Preview 预览 | ✅ | ✅ | ✅ | ✅ |
| Edit 编辑 | ✅ | ✅ | ✅ | ✅ |
| Duplicate 复制 | ✅ | ✅ | ✅ | ❌ |
| Delete 删除 | ✅ | ✅ | ✅ | ❌ |
| Publish/Unpublish | ✅ | ✅ | ✅ | ❌(审核制) |
| View Stats 查看数据 | ✅ | ✅ | ✅ | ✅ |
| Share 分享链接 | ✅ | ❌ | ✅ | ❌ |

### 3.6 进度与完成度指示器

| 指示器 | 展示形式 | 出现位置 |
|--------|---------|---------|
| 课程完成度 | 百分比进度条 | 课程建设页头部 |
| 章节完成计数 | `3/5 lessons` | 每章右侧 |
| 待发布提醒 | `3 unpublished` | 课程列表/设置页 |
| Setup 清单 | `3 of 8 steps` | 创建后引导 |
| 课时状态图标 | ◻/🔄/✓ | Module/章节内 |

---

## 四、对于微课平台的具体建议

### 4.1 课程列表页 (CourseList.vue)

```
┌─────────────────────────────────────┐
│  我的课程              [+ 新建课程] │
│  [全部] [已发布] [草稿] [已归档]     │
├─────────────────────────────────────┤
│ ┌──────┐ ┌──────┐ ┌──────┐        │
│ │ 📷   │ │ 📷   │ │ 📷   │        │
│ │ 标题  │ │ 标题  │ │ 标题  │        │
│ │ 🟢已发│ │ ⚪草稿│ │ 🟢已发│        │
│ │ ✏️📊👁 │ │ ✏️📊👁 │ │ ✏️📊👁 │        │
│ └──────┘ └──────┘ └──────┘        │
└─────────────────────────────────────┘
```

- **状态标签**：已发布(绿) / 草稿(灰) / 审核中(蓝) / 未通过(红)
- **课程卡片操作**：编辑 ✏️、统计 📊、预览 👁
- **内容类型摘要**：每张卡片底部小图标 `🎬 12` `📄 3` `📝 5`（视频/文件/测验数）
- **排序/筛选**：全部 / 已发布 / 草稿 / 已归档 + 创建时间排序

### 4.2 课程详情页 (CourseDetail.vue)

```
┌──────────┬──────────────────────────┐
│ 左侧 Tab  │    右侧内容区             │
│          │                          │
│ 📋 课程信息│   [根据 Tab 切换内容]    │
│ 📚 章节内容│                          │
│ 📝 测验管理│                          │
│ 📊 学习数据│                          │
│ ⚙️ 课程设置│                          │
│          │                          │
└──────────┴──────────────────────────┘
```

- 左侧 Tab：课程信息 / 章节内容 / 测验管理 / 学习数据 / 课程设置
- 右侧内容区域根据 Tab 切换显示

### 4.3 章节内容编辑器

```
┌──────────────────────────────────────┐
│ 📚 第一章 课程概述                     │
│ ├── 🎬 1.1 什么是微课                │
│ ├── 📄 1.2 学习资料                  │
│ └── 📝 1.3 章节测验                  │
│ ──────────────────────────────        │
│ [+ 添加课时]     [⚡ 快速操作 ▾]       │
│                                      │
│ 📚 第二章 内容创作                     │
│ ├── 🎬 2.1 视频制作                  │
│ └── 🎬 2.2 后期编辑                  │
│ ──────────────────────────────        │
│ [+ 添加课时]     [⚡ 快速操作 ▾]       │
│                                      │
│ [+ 添加章节]                          │
│                                      │
│ 进度: 5/8 课时已发布  [📖 预览课程]    │
└──────────────────────────────────────┘
```

- 每课时前有类型图标：🎬 视频 / 📄 文件 / 📝 测验 / 💬 讨论 / 📋 作业
- 每课时后状态：Draft(灰圆) / Published(绿 ✓) / Preview(蓝 🔍)
- Section 层级的 Quick Actions：全部发布/全部取消发布/批量删除
- 底部固定栏：课程完成度 + 预览 + 保存按钮
- **拖拽排序**：章节和课时均可拖拽

### 4.4 课程创建流程 (Wizard)

采用 Teachable 的 4 步向导模式：

```
Step 1 ✏️ 基本信息 → Step 2 🖼 封面设置 → Step 3 💰 定价 → Step 4 📚 大纲生成
```

- Step 1: 课程名称 + 简介 + 分类
- Step 2: 上传封面图（16:9）+ 预览效果
- Step 3: 定价（免费/付费/积分）
- Step 4: 选择大纲方式（从空白创建 / AI 生成 / 复制已有课程）
- 创建后跳转到 Setup Guide Checklist

### 4.5 进度与完成度体系

| 维度 | 前端组件 | 数据字段 |
|------|---------|---------|
| 课程完成度 | `ProgressBar` | `chapterCount`, `lessonCount`, `publishedLessonCount` |
| 章节完成度 | `章节右侧计数` | `publishedCount / totalCount` |
| 发布状态 | `StatusBadge` | `status: DRAFT / REVIEWING / PUBLISHED / REJECTED` |
| 待办提醒 | `AlertBanner` | `unpublishedLessonCount` |
| Setup 检查清单 | `ChecklistPanel` | `steps: [{key, label, done}]` |

### 4.6 跨平台最佳实践汇总

1. **左侧 Tab 导航**（Thinkific 模式）：比顶部 Tab 更适合多功能的课程管理
2. **4 步创建向导**（Teachable 模式）：降低创建门槛，每步可跳过
3. **状态标签体系**：5 种状态（Draft/Reviewing/Published/Rejected/Archived）
4. **Quick Actions**：Section 级批量操作（发布/预览/删除）
5. **拖拽排序**：章节和课时均可拖拽重排
6. **Setup Guide**：创建后的引导式 Checklist
7. **内容类型图标**：视频/文件/测验/作业/讨论 各自独立图标
8. **预览模式**：教师可随时预览学生视角
9. **发布粒度**：课程级+章节级+课时级的三级发布控制
10. **课程模板/AI生成**：从空白/AI/复制三种创建方式
