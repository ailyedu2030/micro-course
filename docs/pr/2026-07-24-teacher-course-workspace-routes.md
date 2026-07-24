# PR · 教师课程主链路路由与上下文一致性修复

> **Type:** fix
> **Date:** 2026-07-24
> **Scope:** 教师端课程模块主链路

---

## 背景

前一阶段已经把教师端课程模块重新确认为当前最高优先级，但代码层面仍存在一个会持续干扰教师工作流的问题：

- 教师明明从 `/teacher/courses` 进入课程工作区
- 课程查看、编辑、复制、保存后的跳转却仍有多处回落到 `/courses/*`
- 视频管理页的 breadcrumb 也默认指向通用课程空间
- 教师身份在部分页面仍混用 `userStore.userId` 与 `userStore.userInfo?.id`

这类问题不会立刻造成页面报错，但会让教师主链路在“创建课程 -> 查看课程 -> 编辑课程 -> 管理内容 -> 查看学员”过程中不断跳出教师工作区，导致导航心智不稳定，也提高后续重构时回归的概率。

---

## 根因分析

### 症状

教师课程模块复用了 `CourseList.vue`、`CourseDetail.vue`、`VideoList.vue` 等页面，但页面内的导航是逐处手写的：

- `CourseList.vue` 中的查看、编辑、复制、创建后跳转
- `CourseDetail.vue` 中的返回、编辑、保存、复制、课件入口、章节内容入口
- `VideoList.vue` 中的 breadcrumb 返回课程

这些跳转没有统一的教师工作区路由来源。

### 根因

此前更关注功能是否“能进入页面”，而没有把“教师是否始终停留在教师课程工作区”作为一个独立的设计约束。于是复用页面时，默认沿用了通用 `/courses/*` 路径。

---

## 本轮完成内容

### 1. 新增统一路由助手

新增 [useCourseWorkspaceRoutes.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useCourseWorkspaceRoutes.js)，统一生成：

- 课程列表路径
- 课程详情路径
- 课程编辑路径
- 课件管理路径
- 章节内容管理路径

规则：

- `TEACHER` 固定留在 `/teacher/courses/*`
- `ADMIN / ACADEMIC` 继续使用 `/courses/*`

### 2. 修复教师课程主链路导航

更新 [CourseList.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/courses/CourseList.vue)：

- 查看课程
- 编辑课程
- 复制课程后跳转
- 新建课程成功后直接进入课程详情，便于继续配置章节和内容
- 返回完整列表
- 课件入口与线下管理入口

更新 [CourseDetail.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/courses/CourseDetail.vue)：

- 返回课程列表
- 切换编辑/查看
- 复制课程后进入副本详情
- 保存成功后返回正确课程工作区详情页
- 章节内容管理入口
- `ACADEMIC` 在编辑模式下的重定向

更新 [VideoList.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/courses/VideoList.vue)：

- breadcrumb 课程管理入口
- breadcrumb 返回课程入口

### 3. 统一教师身份来源

更新 [StudentList.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/StudentList.vue)，将教师课程筛选统一改为 `userStore.userId`，避免继续混用 `userStore.userInfo?.id`。

---

## 测试与验证

### 新增单测

- [useCourseWorkspaceRoutes.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useCourseWorkspaceRoutes.test.js)

覆盖：

- `TEACHER` 角色生成教师工作区路径
- `ADMIN / ACADEMIC` 角色生成通用课程路径

### 执行验证

```bash
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/composables/useCourseWorkspaceRoutes.js
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/__tests__/useCourseWorkspaceRoutes.test.js
npm run test:unit -- src/__tests__/useCourseWorkspaceRoutes.test.js src/__tests__/CourseDetail.test.js
npm run test:unit
npm run build
PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh
```

### 当前结果

- `precheck.sh`：通过
- 目标单测：通过
- 全量前端单测：通过
- 前端构建：通过
- `PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh`：通过（隔离环境 15/15 通过）

### ego-browser 本地走查

按项目最新规则，本轮浏览器手工验证优先使用 `ego-browser`，并在本地隔离环境中完成了以下路径确认：

1. 教师工作区课程列表可正常进入  
   - 路径：`/teacher/courses`
2. 教师从课程列表点击“查看”后，仍停留在教师课程工作区  
   - 路径：`/teacher/courses/1`
3. 章节级视频管理页可直接进入，breadcrumb 返回项仍指向教师课程工作区  
   - 路径：`/teacher/courses/1/chapters/1/manage-videos`

### 浏览器验证中的环境噪音

`ego-browser` 初始走查时，直接操作登录表单会落入本地隔离测试账号噪音：

- `teacher1/password123` 在当前隔离库中并不是稳定可用账号
- 改用已验证可用的 `p0_teacher/student123` 后，页面级 token 注入与工作区页面走查可以正常完成

该问题属于**本地隔离环境的测试账号一致性问题**，不是本轮教师课程工作区路由修复引入的功能缺陷。

---

## 用户价值

这轮改动的业务收益不是“增加了一个新按钮”，而是把教师课程工作流真正串顺：

1. 教师创建课程后，立即进入教师课程详情继续配置内容
2. 教师查看、编辑、复制课程时，不会被甩到通用课程空间
3. 从视频管理页返回课程时，仍然回到教师工作区
4. 学员管理按课程过滤时，教师身份来源更稳定

这使“课程创建 -> 内容上传 -> 课程管理 -> 学员查看”这条教师核心路径更连续，也更符合后续把教师课程模块作为最高优先级推进的策略。

---

## 评审重点

- [ ] `TEACHER` 与 `ADMIN / ACADEMIC` 的路由分流是否正确
- [ ] 创建、复制、保存后的目标页是否都符合教师主链路预期
- [ ] `VideoList` breadcrumb 是否已统一走课程工作区路径
- [ ] `StudentList` 的教师身份来源是否已统一为 store getter
