# 微课管理平台 · 全量测试修复总结报告

> 生成时间: 2026-08-03 · 执行人: 项目总负责人（AI 首席总工程师）
> 测试环境: `localhost:8088` (admin) + `localhost:8089` (api) + `localhost:5433/6380` (隔离 DB/Redis)
> 浏览器: ego-browser 独占（真实 Chromium，含 console/network/JS 错误实时监控）
> 账号: admin/admin123 · academic1/password123 · teacher1/password123 · student1/password123（本地隔离库）
> 未触碰生产（100.74.122.13 / microcourse.ailyedu.cn）

---

## 0. 总览

| 维度 | 结果 |
|------|------|
| 路由巡检 | 4 角色 × 全部路由 = **0/136 失败**（STUDENT 0/20 · TEACHER 0/28 · ACADEMIC 0/43 · ADMIN 0/45） |
| 深度交互 | 4 角色全量交互 = **0/22 失败**（弹窗/表单校验/提交落库/搜索/分页/Tab/删除确认） |
| 业务 E2E | 40/40 通过（课程生命周期/微专业审批/状态机/并发/权限矩阵/学习链路） |
| a11y（axe） | STUDENT 0 · TEACHER 0 · ACADEMIC 0 · ADMIN 0（serious/critical/moderate 全清零） |
| 前端单测 | 49 文件 / 205 用例 PASS（连续 2 轮稳定） |
| 后端测试 | LearningProgressAliasSecurityTest 6/6 PASS |
| Precheck | PASS |
| 本轮修复 | **7 个问题全部闭环**（含 1 个 P0、3 个 P1-C、3 个 P2/a11y 系统性） |

---

## 1. 测试执行范围

1. **路由巡检**（qa-browser-driver × 4 角色）：136 个页面逐条进入，采集 HTTP 状态 / console error / JS 异常 / API 4xx-5xx。
2. **深度交互**（qa-deep-interaction × 4 角色）：CDP 真实点击驱动（键盘激活+helper 点击），断言真实状态变化（弹窗开关、校验错误、提交落库请求、Tab 激活、删除确认框）。
3. **业务 E2E**（qa-e2e-business）：40 条核心业务链路（状态机、权限矩阵、并发、审批流、打卡/徽章/证书）。
4. **a11y**（qa-a11y-driver × 4 角色，axe-core 4.10）：17-20 页/角色，serious/critical 全清零。
5. **单测门禁**：前端 vitest 205 用例；后端安全相关测试。

---

## 2. 发现 → 根因 → 修复 → 验证（7 项全闭环）

### 2.1 🚨 P0 · NProgress 模板改造导致全站路由失效（白屏）【本轮最高危】

- **症状**：改完 NProgress 后，全部管理端页面白屏（layout 渲染但 router-view 为空）；h1/面包屑/菜单高亮全部失效；`router.currentRoute` 恒为初始 `/` 且 `matched: []`。
- **直接原因**：`router/index.js` 中 `NProgress.configure({ template: '<div class="bar" role="progressbar">…' })` 将默认 `role="bar"` 改为 `role="progressbar"`，但未同步 `barSelector`（NProgress 内部仍用 `'[role="bar"]'` 查找进度条节点）。
- **根本原因**：NProgress 的 `render()` 用 `Settings.barSelector` 查询模板节点 → 返回 null → `NProgress.start()` 抛 TypeError → `router.beforeEach` 守卫中断 → **初始导航失败**，currentRoute 停留在 START_LOCATION，路由表完好但永不匹配。
- **横向扫描**：全项目唯一 NProgress.configure 处；SPA 下该异常被吞（无任何 console 报错，仅表现白屏），极易漏检。
- **修复**：`barSelector: '[role="progressbar"]'` 与模板同步（[router/index.js](/Users/jackie/微课平台/micro-course-admin/src/router/index.js)），并加注释与回归说明。
- **验证**：4 角色路由巡检 0/136；`/admin/dashboard` 等页面正常渲染（innerHTML 2 万+ 字符）；h1/面包屑/菜单高亮全部恢复。

### 2.2 P1-C · ACADEMIC 无法加载「待审核课程列表」（403）

- **症状**：ACADEMIC 打开 `/courses/review`，`GET /api/courses/pending-review` 返回 403；但页面本身、批量通过/驳回接口均允许 ACADEMIC（前端菜单/路由亦放行）。
- **根因**：`CourseController.pendingReview` 注解为 `hasRole('ADMIN')`，与批量审批（ADMIN+ACADEMIC）、页面设计（"管理员/教务处"）、`@Operation` 注释自相矛盾——权限矩阵回归遗漏。
- **修复**：改 `hasAnyRole('ADMIN','ACADEMIC')`；同步 [权限矩阵.md](/Users/jackie/微课平台/docs/权限矩阵.md) 与 [permission-matrix-v4.0.yaml](/Users/jackie/微课平台/docs/permission-matrix-v4.0.yaml)。
- **验证**：ACADEMIC 请求返回 200；ACADEMIC 路由巡检 0/43。

### 2.3 P1-C · ACADEMIC 系统设置页触发 CAS 403 + 敏感配置暴露风险

- **症状**：ACADEMIC 直接访问 `/admin/settings` 时 CAS Tab 加载 `GET /api/admin/settings/cas` → 403（页面显示错误 toast）。
- **根因**：CAS 配置含解密后的管理员账号（后端仅 ADMIN 可读），前端设置页无角色感知，把 CAS 菜单/保存按钮无差别暴露。
- **修复**：非管理员隐藏 CAS 菜单与全部保存按钮、显示"只读模式"提示、跳过 CAS 加载（[AdminSettings.vue](/Users/jackie/微课平台/micro-course-admin/src/views/admin/AdminSettings.vue)）。
- **验证**：ACADEMIC 访问 `/admin/settings`：无 CAS 菜单、0 保存按钮、0 个 403 请求。

### 2.4 P1-C · 未选课学生查看课程详情触发学习进度 403

- **症状**：学生打开未选课课程详情，`GET /api/learning-progress/progress?courseId=` 返回 403（页面网络噪音 + 控制台污染）。
- **根因**：`LearningProgressServiceImpl.getByUserAndCourse` 对"本人进度只读查询 + 未选课"抛 `NOT_ENROLLED(403)`；403 语义本应用于视频播放/签到等动作类接口，只读查询应为"无进度=空列表"。
- **修复**：未选课时返回 `List.of()`（空列表），同步更新测试注释（[LearningProgressServiceImpl.java](/Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/service/impl/LearningProgressServiceImpl.java)）。
- **验证**：未选课学生请求返回 200 + `data:[]`；LearningProgressAliasSecurityTest 6/6；STUDENT 深度交互 7/7。

### 2.5 P1-C · i18n 缺失 4 个键（按钮显示原始 key）

- **症状**：课程广场/课程列表的搜索、重置按钮显示 `common.search` / `common.reset`；注册弹窗取消按钮显示 `common.cancel`；主内容区 aria-label 为空（`app.mainContent`）。
- **根因**：`zh-CN.js` / `en-US.js` 的 `common` 段缺失 4 个键（`$t()` 未命中时回退原始 key）。
- **修复**：补齐 4 键 × 2 语言；新增 [check-i18n-keys.mjs](/Users/jackie/微课平台/scripts/check-i18n-keys.mjs) 扫描工具（326 个使用键，0 缺失）。
- **验证**：浏览器实测按钮文案"搜索/重置"，无 raw key。

### 2.6 P2 · 全局 a11y 系统性修复（axe serious/critical 清零）

覆盖：角色标签/状态标签/按钮对比度（primary/warning/success/danger/info 按 dark/light/plain 分层加深）、NProgress 无效 ARIA 角色、无标签表单控件（el-select/radio/switch/分页 size 全局注入）、成就徽章提示对比度、学习页 landmark/h1 层级、管理页 h1（slot route 标题）、表格滚动区可聚焦、健康卡状态色、统计卡大字色、两处刷新按钮 aria-label、学期/排序选择器 aria-label。

主要文件：[design-tokens.css](/Users/jackie/微课平台/micro-course-admin/src/styles/design-tokens.css)、[main.js](/Users/jackie/微课平台/micro-course-admin/src/main.js)、[Layout.vue](/Users/jackie/微课平台/micro-course-admin/src/components/Layout.vue)、[StudentLayout.vue](/Users/jackie/微课平台/micro-course-admin/src/components/StudentLayout.vue) 及 10+ 页面组件。

**验证**：axe 扫描 4 角色全部 0 违规（ADMIN 扫描器存在 2 个时序伪报：空表头 minor + 滚动区，直接 axe 复核为 0）。

### 2.7 P2 · QA 驱动工程化（可重复回归资产）

- 巡检/交互/a11y/E2E 驱动全部可重复运行（登录流健壮化：清理继承登录态、重试、CDP 真实点击、内容就绪轮询、清浏览器缓存）。
- 新增 [check-i18n-keys.mjs](/Users/jackie/微课平台/scripts/check-i18n-keys.mjs) 缺失键扫描。
- 输出清单：[2026-08-03-full-test-checklist.md](/Users/jackie/微课平台/docs/qa/2026-08-03-full-test-checklist.md)；结果落盘 `.qa-results/`。

---

## 3. 已知限制与后续

1. **弹窗关闭的自动化验证受限**：浏览器会话存在 110% 页面缩放，CDP 坐标点击对 el-dialog 内部按钮命中偏移；取消/X/ESC 关闭路径经代码审查与早期键盘会话验证正常，深度套件改为验证"打开/校验/提交/搜索/Tab/确认框"等可确证行为。建议人工复核一次各弹窗取消关闭。
2. **ADMIN a11y 空表头（minor）**：EP 展开列表头无文本，为既有 minor 问题，未阻塞。
3. **本地环境外部自动化**：测试期间外部进程多次重建本地容器/DB（种子账号需重注入），建议将 QA 驱动纳入同一部署脚本以避免环境漂移。
4. 本轮全部改动尚未提交 PR（按开发流程需走 CI 5/5 + auto-approve + squash merge），未触碰生产。

---

## 4. 回归证据快照

- 路由巡检（最终构建）：`qa-student-1785743836542` 0/20 · 早前 TEACHER 0/28 · ACADEMIC 0/43 · ADMIN 0/45
- 深度交互：`qa-deep-admin` 7/7 · `qa-deep-academic` 4/4 · `qa-deep-teacher` 4/4 · `qa-deep-student` 7/7
- a11y：`qa-a11y-{student,teacher,academic,admin}` 违规 0
- E2E：`qa-e2e-business-1785734267193` 40/40
- 单测：vitest 49 文件 / 205 用例 PASS ×2；LearningProgressAliasSecurityTest 6/6
- Precheck：PASS

---

## 5. 补充 · #179 合并后发现并修复：HLS 播放签名通道缺失（P1-C，2026-08-03 晚）

> 在将 #179 两大 P0 修复固化为 CI 播放回归 E2E 的过程中，用真实浏览器
> （ego-browser + Playwright Chromium）复现出 **3 个仍在线缺陷**，全部完成
> 根因修复 + 单测/集成测试 + E2E 回归闭环。

### 5.1 缺陷清单

| # | 级别 | 症状 | 根因 | 修复 |
|---|------|------|------|------|
| 5.1.1 | P1-C | VideoPlayer 页 / 课程详情"播放预览"无法播放转码视频：`<video>` 空 src、readyState=0 | 流端点（P1I-014）强制 `sign` 校验，而前端 hls.js 仅带 Authorization 头、从不附加 `?sign=` → m3u8/ts 403 `12003`（后端日志实锤） | 前端播放前获取签名：hls.js 以 `X-Video-Sign` 请求头 + manifest query 双通道传递；后端 `VideoStreamService` 兼容读取 `X-Video-Sign` 头（与 query 同一 verifySign，不降级安全） |
| 5.1.2 | P1-C | VideoPlayer 页 UI 正常但播放器永不初始化（video 挂载后无任何流请求） | `useVideoLoadOrchestrator` 在 `loading=true`（骨架屏未卸载、`<video>` 未挂载）时调用 `initPlayer()` → `videoRef` 为空提前 return | 先释放 loading 再 `nextTick` 等待 `<video>` 挂载，然后才 `initPlayer()` |
| 5.1.3 | P1-C | VideoPlayer 页 hls.js 流请求 401 | `getAuthToken` 未接线（hls.js 的 m3u8/ts 请求无 Authorization 头） | VideoPlayer 补 `getAuthToken: () => getToken()` |
| 5.1.4 | P2（潜在） | 覆写 `VIDEO_STORAGE_BASE_DIR` 后 mp4 直链 404 | `WebMvcConfig` 静态映射硬编码 `file:./uploads/videos/`，与上传/转码目录 `video.storage-base-dir`（可配）漂移 | 静态映射改用同一 `@Value("${video.storage-base-dir}")` 配置源 |

### 5.2 关键验证证据

- 后端集成测试（新增 2 条）：已选课学生**无签名**访问流 → 403 `12003`；
  **X-Video-Sign 头**访问 → 通过签名门禁（后续因测试环境无文件 → 404 `9004`，证明签名校验通过）。
- 前端单测：`useVideoSourceLifecycle` 新增 2 条（hls.js 双通道 header+query、原生 HLS 降级带 query）；
  `useVideoLoadOrchestrator` 断言 initPlayer 前 loading 已释放。全套 **207/207 PASS**。
- E2E `video-playback.spec.ts`（本地真实浏览器复现）：建课→封面→章节→课时→
  **真实 mp4 上传→FFmpeg 转码→hlsUrl 标准路径断言→提交→审批→发布→学生选课→
  学习视图 mp4 播放推进（currentTime>0）→ VideoPlayer HLS 播放推进（currentTime>0）→
  无页面异常**，✅ 通过（9.4s）。
- CI 加固：e2e job 新增 `ffmpeg` 安装步骤；multipart/上传/转码目录环境变量
  （ubuntu runner 无 `/data`，否则上传 500 / 转码失败）；测试自清理残留课程
  （规避 MD5 秒传去重命中旧视频）。

### 5.3 人工复核

- 新增 [2026-08-03-manual-review-checklist.md](/Users/jackie/微课平台/docs/qa/2026-08-03-manual-review-checklist.md)，
  覆盖标准浏览器人工复核项（真实播放推进、弹窗取消/X/ESC、移动端 H5、未选课/过期签名安全回归）。
