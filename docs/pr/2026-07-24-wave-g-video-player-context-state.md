# PR · Wave G VideoPlayer 上下文与模块状态抽离

> **Type:** refactor
> **Date:** 2026-07-24
> **Scope:** `micro-course-admin/src/views/student/VideoPlayer.vue`

---

## 一句话总结

继续收敛 `VideoPlayer.vue` 的页面壳层，把还残留在页面顶部的路由上下文和基础模块状态抽到独立 composable，并补上对应单测，进一步降低主文件的接线噪音。

---

## 当前任务已完成内容

Wave G 前序已完成：

- 生命周期编排
- 章节滚动
- 显示格式化
- 显示态
- 页面视图态
- 字幕状态
- 页面动作
- 模块级组件回归测试

本轮继续完成：

1. 新增 [useVideoRouteContext.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoRouteContext.js)
2. 新增 [useVideoModuleState.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoModuleState.js)
3. 新增测试 [useVideoRouteContext.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoRouteContext.test.js)
4. 新增测试 [useVideoModuleState.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoModuleState.test.js)
5. 更新 [VideoPlayer.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/student/VideoPlayer.vue) 的基础接线，移除页面内重复的 route/userId/state 声明

---

## 根因分析

### 症状

`VideoPlayer.vue` 在经历多轮拆分后，复杂交互虽然已经基本下沉，但文件顶部仍保留一批基础上下文和基础模块状态：

- `videoId / courseId / chapterId`
- 多处重复的 `computed(() => userStore.userInfo?.id)`
- `loading / errorMsg / videoData / chapters / discussions / isPipSupported / currentChapterIndex / isComponentUnmounted`

这些逻辑不复杂，但持续占据页面壳层，让页面文件仍然承担“基础状态定义 + 接线”双重职责。

### 直接原因

前几轮优先抽离的是高复杂度交互逻辑，而最基础的上下文与状态容器没有一起收口。

### 根本原因

拆分顺序优先处理了复杂行为，导致页面文件里剩下的“低复杂度但高噪音”部分被延后。它们虽然不是 bug，但会让后续继续拆分时上下文负担变大。

---

## 本轮设计与实现

### 1. 路由上下文收口

[useVideoRouteContext.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoRouteContext.js#L1-L21) 统一派生：

- `videoId`
- `courseId`
- `chapterId`
- `userId`

收益：

- 页面不再重复读取 `route.params / route.query`
- `userId` 不再在多个 composable 调用点各自 `computed`

### 2. 模块基础状态收口

[useVideoModuleState.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoModuleState.js#L1-L39) 统一承载：

- `loading`
- `errorMsg`
- `videoData`
- `chapters`
- `discussions`
- `isPipSupported`
- `currentChapterIndex`
- `isComponentUnmounted`

并补了轻量 helper：

- `setErrorMessage`
- `clearErrorMessage`
- `markComponentUnmounted`

收益：

- 页面基础状态有了单一入口
- `useVideoSourceLifecycle` 等调用点不需要再手写 setter 包装

---

## 测试与验证

### 新增单测

- [useVideoRouteContext.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoRouteContext.test.js#L1-L44)
- [useVideoModuleState.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoModuleState.test.js#L1-L28)

### 执行命令

```bash
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/composables/useVideoRouteContext.js
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/composables/useVideoModuleState.js
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/__tests__/useVideoRouteContext.test.js
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/__tests__/useVideoModuleState.test.js
npm run test:unit -- src/__tests__/useVideoRouteContext.test.js src/__tests__/useVideoModuleState.test.js src/__tests__/VideoPlayer.test.js
npm run test:unit
npm run build
PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh
```

### 验证摘要

- `precheck.sh`：4 个目标文件全部通过
- 目标测试：`4/4` 通过
- `npm run test:unit`：`34/34` 文件、`82/82` 测试通过
- `npm run build`：通过
- `PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh`：`15/15` 通过

---

## 当前结果

`VideoPlayer.vue` 现在的页面壳层进一步聚焦为：

- DOM refs
- composable wiring
- 模板绑定

而不再负责：

- route/user 解析
- 基础会话状态初始化
- 重复的错误 setter 封装

虽然行数因为新增 import 和 destructuring 基本持平，但文件顶部的职责边界更清晰，后续继续拆分时上下文噪音明显更低。

---

## 评审重点

- [ ] `useVideoRouteContext` 是否准确覆盖当前页面的 route/user 派生值
- [ ] `useVideoModuleState` 是否只承载“基础状态”，没有偷偷侵入业务逻辑
- [ ] `VideoPlayer.vue` 对 `userId`、`setErrorMessage`、`currentChapterIndex` 的接线是否已统一走新 composable
- [ ] 回归测试与隔离联调证据是否足够支撑本轮合并
