# PR · Wave G VideoPlayer 字幕与页面动作收尾

> **Type:** fix / refactor
> **Branch:** 待创建
> **Target:** `main`
> **Date:** 2026-07-24
> **Scope:** `micro-course-admin/src/views/student/VideoPlayer.vue`

---

## 一句话总结

在 Wave G 已完成 7 轮播放器拆分的基础上，继续补齐 `VideoPlayer.vue` 的字幕闭环和页面动作壳层，把原本半截的字幕显示链路做成真实功能，并移除一个对用户无反馈的坏按钮。

---

## 当前任务已完成内容

### 已合并到主干的前序 Wave G 提交

| PR | Commit | 内容 |
|---|---|---|
| #113 | `86bf71cb` | 抽离页面生命周期 `useVideoPageLifecycle` |
| #114 | `80d242f1` | 抽离章节滚动 `useVideoChapterScroller` |
| #115 | `d610254d` | 抽离显示格式化 `useVideoDisplayFormatters` |
| #116 | `46cd732c` | 抽离显示态 `useVideoDisplayState` |
| #117 | `d9c1df8e` | 抽离页面视图态 `useVideoPageViewState` |

### 本轮待提交改动

1. 新增 [useVideoSubtitles.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoSubtitles.js#L1-L130)
2. 新增 [useVideoPageActions.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoPageActions.js#L1-L31)
3. 在 [VideoPlayer.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/student/VideoPlayer.vue#L127-L152) 为视频元素接入 `<track>`、`loadedmetadata`、`track load`
4. 在 [VideoPlayer.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/student/VideoPlayer.vue#L460-L467) 与 [VideoPlayer.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/student/VideoPlayer.vue#L536-L541) 让笔记 hover 变成真实高亮
5. 从 [useVideoPlaybackControls.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoPlaybackControls.js#L1-L223) 中移除字幕状态归属
6. 从 [useVideoPageViewState.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoPageViewState.js#L1-L25) 中移除错误归属的 `currentSubtitle`
7. 删除 `VideoPlayer.vue` 头部没有任何实际效果的“设置”按钮

---

## 根因分析

### 问题 1：字幕开关只有布尔切换，没有真实显示链路

- **症状**：播放器有字幕按钮和字幕展示区，但用户点击后不会看到当前字幕内容
- **直接原因**：
  - `subtitlesEnabled` 原来只在 `useVideoPlaybackControls` 里做布尔切换
  - `currentSubtitle` 只是空 `ref('')`
  - 视频元素没有 `<track>` 和 `textTracks` 同步逻辑
- **根本原因**：字幕属于独立媒体状态，但历史上被混在播放控制里，只做到了“按钮可点”，没有做完“轨道模式 + cue 文本 + 生命周期”

### 问题 2：页面动作里存在空壳交互

- **症状**：
  - 笔记项 hover 绑定了 `highlightTime`，但没有任何视觉反馈
  - 顶部“设置”按钮可点击，但没有任何实际行为
- **直接原因**：
  - `highlightTime` 和 `toggleSettings` 是空函数
- **根本原因**：页面壳层残留了未闭环的交互占位，随着组件膨胀一直没有被清理

---

## 本轮实施计划与验收结果

| 环节 | 交付内容 | 验收标准 | 结果 |
|---|---|---|---|
| TDD-1 | 字幕状态测试 | 先红后绿，覆盖轨道切换与 cue 文本 | ✅ |
| TDD-2 | 页面动作测试 | 覆盖返回、错误提示、笔记 hover 状态 | ✅ |
| 实现-1 | 字幕 composable | `track.mode`、`cuechange`、卸载清理完整 | ✅ |
| 实现-2 | 页面动作 composable | `goBack / onVideoError / highlightTime` 下沉 | ✅ |
| 实现-3 | UI 收尾 | 删除坏按钮，笔记 hover 高亮生效 | ✅ |
| 质量门禁 | 预检 / 单测 / 构建 / 隔离联调 / Playwright | 全部通过 | ✅ |

---

## 测试与验证证据

### 单元测试

- 新增 [useVideoSubtitles.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoSubtitles.test.js#L1-L110)
- 新增 [useVideoPageActions.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoPageActions.test.js#L1-L56)
- 更新 [useVideoPageViewState.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoPageViewState.test.js#L1-L63)

### 执行结果

```bash
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/views/student/VideoPlayer.vue
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/composables/useVideoSubtitles.js
bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/composables/useVideoPageActions.js
npm run test:unit
npm run build
PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh
```

### 验证摘要

- `precheck.sh`：3 个目标文件均通过
- `npm run test:unit`：`31/31` 文件、`78/78` 测试通过
- `npm run build`：成功
- `PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh`：
  - 隔离 DB/Redis/API/Admin 环境启动成功
  - API 健康检查与登录成功
  - Playwright UI 测试 `1 passed`
  - 后端单元测试与质量门禁通过

---

## 变更影响

### 用户可感知改善

1. 字幕按钮从“只会亮/灭”变成真正能显示字幕内容
2. 笔记 hover 有了明确高亮反馈
3. 顶部不再保留一个点击后毫无反应的设置按钮

### 架构改善

1. 字幕状态从播放控制中解耦
2. 页面动作壳层从 `VideoPlayer.vue` 下沉
3. `currentSubtitle` 状态归属被纠正，避免跨 composable 职责漂移

---

## 当前结果

- `VideoPlayer.vue` 当前行数：`2185`
- 虽然这轮因为补齐 `<track>` 和 hover 样式使模板略有增加，但核心目标已经达成：
  - 剩余热点不再以内联空壳函数存在
  - 用户可见缺口被补齐
  - 状态职责进一步收敛

---

## 评审重点

- [ ] 字幕轨道同步是否覆盖 `loadedmetadata + track load + cuechange`
- [ ] `useVideoPlaybackControls` 去掉字幕状态后是否保持职责纯净
- [ ] 删除坏按钮是否符合当前 UX 预期
- [ ] 笔记高亮是否在 PC / H5 两端都生效
- [ ] 本地隔离联调证据是否足够支撑合并
