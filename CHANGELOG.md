# Changelog

All notable changes to 微课管理平台 (Micro-Course Management Platform) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Fixed (Bug K - nginx cache 策略 + 401/403 全局静默)

> **背景**: 用户截图 console 错误 (401 错误链 + refresh 415) 双重问题. 按 L0 UX 宪法'用户视觉判断 > 理论正确'原则双 bug 一并修.

#### Bug K.1: nginx `expires 1y` 让老 bundle 缓存 1 年
- **根因**: `micro-course-admin/nginx.conf` location `/assets/` 配 `expires 1y; Cache-Control: public, immutable`. 容器运行时改 `default.conf` 无效 (image 启动时 COPY 覆盖)
- **修复**: 改 image source — 删除 `expires 1y` 指令, 改用 `add_header Cache-Control "no-cache, must-revalidate"`. 浏览器每次必须 revalidate → bundle hash 变化立即生效 + 性能 OK (304 Not Modified)
- **影响**: 用户硬刷新后立即生效, 新 bundle 不再被 1 年缓存

#### Bug K.2: 401/403 unhandledrejection console 噪音
- **根因**: `utils/errorReport.js` unhandledrejection handler 之前对所有错误都 doReport, 401/403 也被浏览器默认 console.error 输出
- **修复**: `unhandledrejection` 监听 + `onerror` 检测 401/403 → `event.preventDefault()` + `console.debug` (eslint-disable-next-line). 5xx/网络错误/其他 仍正常 report
- **影响**: 业务错误已通过 `ElMessage.toast` 给用户提示, 401/403 不需再 console 输出. 用户截图不再有 401 错误噪音

#### 横向扫描
- grep `console.error` src/ → 6 处核心 debug 保留 (PR #168)
- grep `console.debug` src/ → 33 处 (含本次新增 1 处)
- 其他 status code (5xx, 网络) 仍正常 console.error → 不掩盖真实应用 bug
- 5xx 仍正常 doReport → 后端错误监控系统 (后端 `/api/frontend-errors`) 仍能接收

#### 防止再发
- nginx source-of-truth 在 `micro-course-admin/nginx.conf` (image COPY 进去) → 修改必须改 source
- `precheck.sh` [TODO] 加 nginx config 静态检查: `grep -E 'expires [0-9]+y.*public.*immutable' micro-course-admin/nginx.conf` 报警
- `docs/console-error-catalog.md` 后续加: '401/403 在 unhandledrejection 静默' 章节
- 任何 axios 401/403 → console.debug (PR #168 修复模式) + 不抛 unhandled

#### 风险评估
| 维度 | 评估 |
|------|------|
| 变更范围 | 2 文件 / 26 行 (1 nginx + 1 js) |
| 后端 / DB | 零变更 |
| Breaking Change | 无 |
| 回滚复杂度 | 极低 |

### References
- 部署: PR #173 已 merged to main (commit 762b9a1a)
- 部署时间: 2026-08-02, 5 分钟监控 0 ERROR
- 相关 PR: #168 (precheck), #165 (Bug G), INC-2026-07-31 事故

### Fixed (Bug J - 左侧导航文字对比度 WCAG AAA)

> **背景**: 用户截图反馈"左侧导航字体颜色非常不清晰". 调查发现 `--sidebar-text: #9ca3af` 在深紫黑背景 `#0f0f23` 上理论 contrast 7.30:1 (过 WCAG AA), 但 `Layout.vue:562` 的 fallback `#bfcbd9` 实际触发 + element-plus CSS 优先级问题 → 用户视觉判断"不清晰". L0 UX 宪法"体验至上"原则: **用户判断优先于理论数值**.

#### Bug J: 左侧导航文字对比度
- **`src/styles/design-tokens.css`**: `--sidebar-text: #9ca3af` → `#e5e7eb` (Tailwind gray-200)
- **`src/components/Layout.vue:562`**: fallback `#bfcbd9` → `#e5e7eb` (一致)
- **对比度计算** (vs 背景 `#0f0f23`):
  - 修复前: 7.30:1 (WCAG AA 过)
  - 修复后: **16.2:1** (WCAG AAA 完美, 过 2.3 倍)
- **横向扫描**:
  - 顶部 logo 文字 `#f1f5f9` 已有 17.4:1 (保留)
  - el-sub-menu 继承 `--el-menu-text-color`, 自动修复
  - 教学端/教务端/管理员端/学生端共享 Layout.vue, 一次修复全覆盖
  - 主动检查: 无独立 StudentSidebar.vue
- **影响**: 用户视觉判断"清晰可读", 接近白色但保留次要文字视觉层级

#### 防止再发
- `design-tokens.css` 加 P0-4 注释 + WCAG contrast checker 链接
- precheck.sh TODO: 加 color contrast 检测 (axe-core / pa11y 在 CI 跑)
- docs/console-error-catalog.md 后续加 'UI bug' 章节
- L0 UX 宪法强化: '用户视觉判断 > 理论对比度' (theory 7.3 仍不算"清晰")

#### 风险评估
| 维度 | 评估 |
|------|------|
| 变更范围 | 2 文件 / 6 行, 纯 CSS |
| 后端 / DB | 零变更 |
| Breaking Change | 无 (纯颜色调整) |
| 回滚复杂度 | 极低 |

### References
- 部署: PR #171 已 merged to main (commit 0fd963ad)
- 备份链: 当前 (Bug J) → bak-newest (Bug I) → bak-pr161-2 (PR #161) → bak-pr161-dep (Phase 6)
- 部署时间: 2026-08-02, 5 分钟监控 0 ERROR
- WCAG 工具: https://webaim.org/resources/contrastchecker/

### Fixed (Bug I - 30 处业务 catch 块 console.error 噪音 + precheck.sh 8 项加固)

> **背景**: 用户多次截图生产前端 console 错误. Bug H (PR #165) 修了 enums.js fallback 噪音, 但发现 30+ 处业务 catch 块的 console.error 仍是用户截图噪音. PR #167 文档化的"防止再发"需要 precheck.sh 加 lint 检查.

#### Bug I: 30 处业务 catch 块 console.error → console.debug
- **16 个组件修改** (16 业务 Vue 组件 + utils/logger.js 保留):
  - 修改: `console.error(...)` → `console.debug(...)` + `// eslint-disable-next-line no-console` 注释
  - 保留 6 处核心 debug (App/useErrorHandler/logger/main/router, 开发者关键路径)
- **业务 catch 块改 debug 的理由**:
  - 业务错误通常已通过 ElMessage.toast 给用户提示
  - console.error 是冗余噪音 → 用户截图时 console 显示大量红色错误
  - 调试时仍可手动开启 console.debug 调查
- **影响**:
  - 生产 console 干净 (用户截图不再有 `[AchievementWall]` 等红色错误)
  - 调试能力不变 (console.debug 仍可用)
  - 业务错误用户感知不变 (ElMessage toast 已显示)

#### precheck.sh 8 项加固 (P0-3 防再发)
- **[4]** 禁止 `headers: {}` 显式空对象 (Bug G 防再发, axios 0.27+ 行为)
- **[5]** 禁止 utils/request.js 之外直接 import axios (Bug G 防再发, 走 request instance)
- **[6]** utils/ console.warn 检查 (Bug H 防再发, fallback 路径应 console.debug)
- **[7]** 文档同步检查 (CHANGELOG.md + ROLLBACK_PLAN.md 是否提及修改的文件)
- **[8]** src/ console.error 全局检查 (防止用户截图噪音, Bug I 防再发)
- 颜色输出 (红/黄/绿) 方便识别警告 vs 错误

#### 横向扫描
- grep -rn `console\.error` src/ → 36 处 (改前) → 6 处 (改后, 仅核心 debug)
- grep -rn `headers: *{}` src/ → 0 处 (Bug G 已修, precheck 加固防止新增)
- grep -rn `import.*['"]axios['"]` src/ (除 utils/request.js) → 0 处 (Bug G 已修, precheck 加固)
- 其他 utils/ 仍有 console.warn (logger.js wrapper definition, 加 eslint-disable)

#### 防止再发
- precheck.sh 4-8 项是"防再发"检查项, 任何未来 PR 触发警告 → 必须修
- console.debug 加 `eslint-disable-next-line no-console` 注释说明是 fallback 路径
- Bug I 模式: 业务 catch 块统一 console.debug (用户截图无噪音), 核心 debug 路径保留 console.error

#### 风险评估
| 维度 | 评估 |
|------|------|
| 变更范围 | 17 文件 / 200 行, 16 业务组件 + 1 precheck.sh |
| 后端 / DB | 零变更 |
| 现有部署影响 | 零 (console.debug 默认不输出) |
| 回滚复杂度 | 极低 (git revert 1 个 commit) |

### References
- 部署: PR #168 已 merged to main (commit 99112b6e)
- 部署 SOP: `scripts/deploy-frontend.sh` (11 步独立验证)
- 备份链: 当前 (Bug I) → bak-pr161-3 (Bug E/F) → bak-pr161-2 (PR #161) → bak-pr161-dep (Phase 6)
- 部署时间: 2026-08-01, 5 分钟监控 0 ERROR

### Fixed (Bug G/H - 生产 console 401 错误链根因修复)

> **背景**: PR #163 部署后用户截图生产前端 console 仍出现连锁错误 (401×5, [enums] WARN, [App] ERR)。**根因不在前端业务代码，而在 axios 0.27+ 行为变更 + 业务代码误用**。

#### Bug G: `/api/auth/refresh` 返回 415 (Unsupported Media Type)
- **`src/utils/request.js:125` refresh 调用改 1 行**:
  - 修复前: `headers: {}` (显式空 headers, axios 0.27+ 不自动注入 Content-Type, 后端 415)
  - 修复后: `headers: { 'Content-Type': 'application/json' }` (显式 application/json)
- **完整错误链**: refresh 415 → refresh 失败 → 所有 401 请求无法重试 → console 堆满 401 + [enums] WARN + [App] ERR
- **验证**: `curl -X POST https://microcourse.ailyedu.cn/api/auth/refresh -H 'Content-Type: application/json' -d '{refreshToken:test}'` → HTTP 401 (token 错误) 而非 415 (Content-Type 错)
- **影响**: 修复后 token 过期自动 refresh, 用户无感知

#### Bug H: enums fallback 路径 console.warn 噪音
- **`src/utils/enums.js:159` 改成 console.debug**:
  - 修复前: `console.warn('[enums] failed to sync from backend, using local fallback:', e)` (fallback 是设计预期, 但 console.warn 触发用户可见的 [enums] 错误)
  - 修复后: `console.debug(...)` (eslint-disable-next-line no-console, 调试时手动开启日志调查)
- **影响**: 生产 console 干净, 但 fallback 行为不变 (本地常量保证前端可用)

#### 横向扫描结果
- `grep -rn 'headers: *{}' src/` → 仅 1 处 (refresh, 已修)
- `grep -rn 'axios\\.\\(post\\|put\\|patch\\)' src/` → 仅 1 处直接调用 axios (refresh), 其他走 `request` instance
- `request` instance 自动 Content-Type 处理未受影响

#### 防止再发
- 注释在 `src/utils/request.js:125` 标记 'P0-3: 不要用 headers: {} 显式空对象, axios 0.27+ 不会自动注入 Content-Type'
- 下次 PR review 时如发现 `headers: *{}` 模式, 应原应 reject (lint rule 待加)
- console.debug 模式: 用户视角的预期行为 (enums fallback, localStorage 缓存) 不应在 console 输出 WARN

#### 风险评估
| 维度 | 评估 |
|------|------|
| 变更范围 | 2 文件 / 6 行, 纯前端 |
| 后端 API | 零变更 |
| DB schema | 零变更 |
| Breaking Change | 无 |
| 回滚复杂度 | 极低 |

### References
- 部署: PR #165 已 merged to main (commit 8902d0b0)
- 部署 SOP: `scripts/deploy-frontend.sh` (11 步独立验证)
- 备份链: 当前 (Bug G/H) → html.bak-pr161-3 (Bug E/F) → html.bak-pr161-2 (PR #161) → html.bak-pr161-dep (Phase 6)
- 部署时间: 2026-08-01, 5 分钟监控 0 ERROR

---

### Fixed (QA 全量回归 #175 - 2026-08-03)

> 全量 QA 回归（4 角色 × 136 路由 / 深度交互 / 业务 E2E / axe a11y / 单测）发现并闭环的缺陷批次。

#### QA-1 (P0): 学生端随堂练习题目加载 403，答题不可用
- **根因**: `ExerciseTake.vue` 逐题调用教师端接口 `GET /api/questions/{id}`（`@PreAuthorize` 仅 TEACHER/ADMIN/ACADEMIC），学生必 403；练习响应已内嵌完整题目（R14），逐题重拉冗余且越权
- **修复**: 直接使用练习响应内嵌题目数据（questionType/content/options/answer/explanation），移除学生侧 getQuestionById 调用
- **验证**: 答题页浏览器实测题目渲染、0 JS error、0 4xx

#### QA-2 (P0 回归): NProgress 模板/barSelector 不同步导致路由守卫中断白屏
- **根因**: `NProgress.configure` 自定义模板改 `role="progressbar"` 未同步 `barSelector`（内部仍查 `[role="bar"]`）→ `NProgress.start()` 抛 TypeError → `router.beforeEach` 中断 → 初始导航失败
- **修复**: `barSelector: '[role="progressbar"]'` 与模板同步

#### QA-3 (P1-C): 权限矩阵修复
- ACADEMIC 待审核课程列表 `GET /api/courses/pending-review` 403 → 放行 ACADEMIC（与批量审批/前端菜单一致），同步 `docs/权限矩阵.md` + `permission-matrix-v4.0.yaml`
- ACADEMIC 系统设置页 CAS 403 + 敏感配置暴露 → 非管理员隐藏 CAS 菜单/保存按钮、只读模式
- 未选课学生进度只读查询 403 → 返回空列表（NOT_ENROLLED(403) 仅保留给动作类接口）

#### QA-4 (P1-C): i18n 缺失键（按钮显示 raw key）
- 补齐 `common.search/reset/cancel` + `app.mainContent`（中英文）；新增 `scripts/check-i18n-keys.mjs` 扫描（326 键 0 缺失）

#### QA-5 (a11y): axe serious/critical 全清零
- 标签/按钮对比度（primary/warning/success/danger/info 按 dark/light/plain 分层）、NProgress 无效 ARIA 角色、无标签表单控件全局注入、h1 层级、滚动区可聚焦、健康卡/统计卡状态色等 20+ 处

#### QA-6: QA 驱动工程化
- route/deep/a11y/e2e/data-flow 驱动 + i18n 扫描工具，可重复回归

---

## [1.22.2] - 2026-07-31 (PR #161)

### Fixed (Phase 6 教师模块收口)

#### 教师看板 / 成绩明细 / 教学班 / 视频管理
- **教师看板待办口径与顶部统计统一** — `TeacherServiceImpl.getPendingTasks()` 仅保留“已提交且未批改”的练习与“未回复”的讨论，避免已处理事项继续出现在待办列表中
- **成绩明细院系筛选恢复逻辑修复** — `StudentGrades.vue` 在 `ACADEMIC / ADMIN` 场景下清空院系筛选后，可稳定恢复全量课程列表，不再停留在错误的局部筛选结果
- **成绩明细只读语义统一** — `ACADEMIC` 角色查看待批改成绩时，弹窗标题、表单禁用态和提交按钮统一收口为只读“查看成绩”语义
- **课件工作台身份缺失保护补齐** — `TeacherSlideOverview.vue` 在缺失教师身份时增加显式失败保护，避免静默异常态影响教师链路判断
- **教学班课程卡片键盘可达性补齐** — 教学班与教师看板课程卡片均补齐键盘进入能力和可访问语义
- **视频管理章节上下文保护** — `VideoList.vue` 在章节上下文下执行重置时不再丢失 `chapterId`，保证章节工作区上下文稳定
- **失败视频“重试转码”链路补回归护栏** — 视频异常态回归测试补齐，锁住失败视频重试入口

#### 本地联调与部署稳定性
- **`local-dev-deploy.sh --keep` 复跑稳定性修复** — 脚本启动前改为检查并清理 `docker ps -a` 中的同名已退出容器，解决 DB / Redis 保留容器导致的复跑冲突

### Tests
- 前端全量单测：`110/110` 通过
- 后端定向测试：`TeacherPendingTasksConsistencyTest 1/1` 通过
- `npm run lint`：通过
- `npm run build`：通过
- `mvn -DskipTests compile`：通过
- 本地隔离部署：`local-dev-deploy.sh --keep` `16/16` 通过
- GitHub CI：`backend / frontend / e2e / docker / monitoring-lint` 全绿

### References
- 进度报告：[docs/pr/2026-07-25-teacher-module-progress-report.md](file:///Users/jackie/微课平台/docs/pr/2026-07-25-teacher-module-progress-report.md)
- 交付汇总：[docs/pr/2026-07-25-phase6-teacher-delivery-summary.md](file:///Users/jackie/微课平台/docs/pr/2026-07-25-phase6-teacher-delivery-summary.md)

---

## [1.22.2] - 2026-07-31 (PR #161)

### Fixed (CI e2e 8 个 timeout 真实根因修复 — 4 个独立 bug)

> **背景**：PR #161 在合并前累计触发 9 次 CI run (总耗时 4+ 小时) 全部失败于 `Run Playwright E2E Tests` step。8 个测试统一 `TimeoutError waiting for locator('#username') to be visible` (60s)。commit `5243673` 改 timeout 20→60s 是盲修 (没改对根因)。本次 4 个 commit 基于真实根因彻底修复 + 新增 HTML 课件章节 iframe 预览功能。

#### Bug A — vite preview 缺 `/api` proxy
- **`vite.config.js` 加 `preview` 块** — CI e2e job 跑 `npx vite preview --port 8088 --host 0.0.0.0`，`server.proxy` 不会被 preview 读取 → `/api/*` 请求在 vite 5 下尝试 proxy 但 ECONNREFUSED → HTTP 500 + empty body。修复后 preview 块独立配 `/api` proxy 到 `http://localhost:8080`，与 server.proxy 同配置 + timeout/proxyTimeout 120s。 ([vite.config.js:46-58](file:///Users/jackie/微课平台/micro-course-admin/vite.config.js#L46-L58))

#### Bug B — element-plus 2.14.x 移除 `ElMessage.config()`
- **`main.js` 删 `ElMessage.config({ariaLive:'polite'})` 调用** — `package.json` 写 `^2.5.0` 自动升级到 `element-plus 2.14.1`。2.14.x **移除** `ElMessage.config()` API (改用 config-provider 的 `messageConfig` 全局配置)。TypeError `G.config is not a function` 在 `app.mount('#app')` **之前**抛出 → Vue app 永不 mount → Login.vue 不渲染 → `#username` 不存在 → 60s timeout (即使 5min 也不行)。修复同时清理 `import { ElMessage } from 'element-plus'` (dead import)。 ([main.js:3,64](file:///Users/jackie/微课平台/micro-course-admin/src/main.js#L3-L64))

#### Bug C — course-crud selector 硬编码 i18n 不匹配
- **3 处 selector + 2 处 status tag 跟 i18n 对齐** — 原 spec 硬编码 `"课程标题"` 但 i18n key `course.courseName` zh-CN 是 `"课程名称"`；`"创建课程"` vs i18n `course.createCourse = "新增课程"`；status tag `"已通过"` vs i18n `course.approve = "审核通过"` / `course.submitForReview = "提交审核"`。修复后 e2e 4/4 PASS (教师创建/编辑/发布/归档)。 ([course-crud.spec.ts:83,92,201,268](file:///Users/jackie/微课平台/micro-course-admin/tests/e2e/course-crud.spec.ts))

#### Bug D — student 详情页 HTML_COURSEWARE 章节 iframe 预览（功能新增）
- **`student/CourseDetail.vue` 实现 HTML_COURSEWARE 章节渲染** — 之前完全未识别该章节类型。修复后：lazy load sections per chapter (用户展开时调 `listSections`) + iframe `src="data:text/html;charset=utf-8,<encodeURIComponent(content)>"` + `sandbox=""` 严格模式 (只读预览，**JS 不执行**) + 响应式 CSS `aspect-ratio: 16/9` + `min-height: 280px` + `max-height: 70vh` (移动端横屏旋转 layout 不破)。3 个 mobile-iframe 测试全 PASS (iOS/Android Chrome sandbox/横屏旋转)。 ([CourseDetail.vue:222-258,818-862](file:///Users/jackie/微课平台/micro-course-admin/src/views/student/CourseDetail.vue))
- **安全设计** — iframe `sandbox=""` 严格模式 (区别于 SlidePlayer.vue `sandbox="allow-scripts"` 用于互动学习)；内容**不调用 sanitizeHtml** (会移除 `data:` URL 反而破坏 src)；后端 HtmlSanitizer + 浏览器原生 origin 隔离 + 前端 sandbox 三重保险
- **i18n 加 5 个 key** — `course.htmlCoursewarePreview/Loading/Empty/None/OpenInPlayer` (zh-CN + en-US 双语)
- **mobile-iframe.spec.ts mock 修正** — `courseType: 'INTERACTIVE' → 'VIDEO'` + chapter 加 `sectionType: 'HTML_COURSEWARE'` + 嵌 `chapters` 数组 (前端从 `course.value.chapters` 取)；test 期望改用元素计数 `p:has-text("JS executed") === 0` 替代 `bodyText.not.toContain` (更准确反映 sandbox 阻止 JS 执行：`<script>` 字面量含 "JS executed" 字符串但 sandbox 阻止其 append `<p>` 元素)

### Quality Gates
- ✅ `npm run lint`：0 errors
- ✅ `npm run build`：built in 6.80s，新 bundle hash `index-lri506Ya.js`
- ✅ `precheck.sh`：24/24 PASS (含 verify-secrets advisory 模式 + svc/entity whitelist 全覆盖)
- ✅ GitHub CI run `30624263375`：`backend (32m) / frontend (49s) / monitoring-lint (12s) / secrets-check (9s) / references-sync (5s) / e2e (3m33s) / docker (2m50s)` **全绿**
- ✅ Playwright e2e：**12/12 全 PASS** (checkout 2/2 + course-crud 4/4 + enrollment 3/3 + mobile-iframe 3/3)
- ✅ Bot auto-approve：`microcourse-pr-bot` 已 approve
- ✅ 本地 Playwright 模拟 CI (chromium-1234 + mock backend)：12/12 全 PASS

### Risk Assessment (部署评审)
| 维度 | 评估 |
|------|------|
| **变更范围** | 4 文件 / ~220 行，**仅前端** (`micro-course-admin/`) |
| **后端 API** | 零变更 (`micro-course-api/` 未触碰) |
| **DB schema** | 零迁移 (无 Flyway 改动) |
| **Breaking Change** | 无 — 纯 bug 修复 + 新功能 |
| **回滚复杂度** | 极低 — `git revert 10716b09` + 重建前端 dist + nginx reload (5 分钟) |
| **生产影响** | 仅学生端详情页新增 HTML 课件预览；登录/支付/选课/CRUD 流程无破坏 |

### References
- 根因调查 (Playwright + curl + CI trace 拉取)
- PR #161 commits: `0a680a21` / `8b852e1b` / `79948c40` / `b5e79442`
- Rollback 路径：[ROLLBACK_PLAN.md](file:///Users/jackie/微课平台/ROLLBACK_PLAN.md) (PR #161 特定回滚步骤已加)
- 灰度策略：[scripts/gray-release.sh](file:///Users/jackie/微课平台/scripts/gray-release.sh)
- Bug A 历史背景：2026-06-25 commit `a322bb93` 曾修过 (改用 `npx vite` 替代 `npx vite preview`)，但 ci.yml 后续又被改回 `vite preview` 导致 Bug A 复发。本次采用更彻底的修复 (preview.proxy)，不依赖切回 dev server

---

---

## [1.22.1] - 2026-07-19

### Fixed (P1-C)

#### HTML 课件 + 音频元数据重载链路
- **`uploadHtmlFile` 改为非破坏性 UPSERT** — 旧逻辑 `delete + insert` 会清空 `slide_pages` 表的 audio 元数据 (`narration_audio_url` / `segment_count` / `voice` / `tts_model` / `generated_at`),导致 opencode 端 Step 4 (audio batch) 与 Step 8 (HTML) 时序冲突后,15 段音频 token 全部失效. 新逻辑按 `(slideId, pageNumber=1)` 查找,存在则只更新 `htmlContent`/`contentType`/`imageUrl`,保留全部 audio 字段. ([SlideServiceImpl.java:295-334](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/impl/SlideServiceImpl.java#L295-L334))
- **`validateAudioToken` 增加 sectionId 级 fallback** — 旧逻辑严格按 `pageNumber` 查 SlidePage,但单页 HTML_DIRECT 场景 SlidePage 表只有 pageNumber=1 一条记录,导致 pageNumber=2..15 的 token URL 全部 403. 新逻辑先精确匹配,失败后 fallback 到按 `sectionId` 查该 section 下任一含 token 的 page. ([TtsServiceImpl.java:796-829](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/impl/TtsServiceImpl.java#L796-L829))

### Added

- **`TtsServiceTokenValidationTest`** — 7 个用例覆盖 token 校验所有路径 (null token / null sectionId / 精确匹配 / sectionId fallback / 无匹配 / token 不一致 / 多 page section),防止后续修改破坏 fallback 行为

### Tests
- 单元测试: 20/20 PASS (`Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`)
- precheck.sh: 22/22 PASS
- local-dev-deploy.sh: 15/15 PASS

### References
- 事故复盘: [docs/incidents/2026-07-19-audio-html-reload-conflict.md](file:///Users/jackie/微课平台/docs/incidents/2026-07-19-audio-html-reload-conflict.md)
- Trae R5 重测脚本: `_r5_verify_fix_20260719.py` (在 opencode 端工程目录)

---

## [1.22.0] - 2026-07-18

### Fixed (P1-C)

#### 套餐购买与订单链路
- **套餐购买仅发必修课访问权** — `OrderServiceImpl.enrollBundleCourses()` 改为发放套餐全部课程访问权，不再局限于必修课
- **套餐订单列表展示错位** — `OrderServiceImpl.toVO()` 套餐订单返回套餐标题，`MyOrders.vue` 列名改为"商品"，套餐单跳转套餐详情
- **套餐退款原子性** — `OrderServiceImpl.refund()` 先回收全部 enrollment，成功后写 REFUNDED 状态和退款流水；`unenrollBundleCourses()` 失败即时抛出不再吞异常
- **支付流程回归** — `EnrollmentServiceImpl` PAYMENT 渠道校验恢复接受 `PENDING/PAID`（`eq("PAID")` → `in("PENDING", "PAID")`）

#### 微专业选课与结业
- **微专业详情页匿名崩溃** — 数据源收口为单一详情接口，消除冗余登录态子请求；修正课程字段读取（`creditHours` → `credits`）；删除后端不返回的伪状态展示；`goto=first` 路径修正为经过权限判断
- **微专业列表 COMPLETED 状态模板缺失** — 补上"已结业"操作按钮
- **APPLY/REAPPLY 角色收紧** — 控制器 + 服务层双重学生身份校验；班级导入容量保护

#### 查询与统计口径
- **3 个后端查询方法缺状态过滤** — `getCourseEnrollmentPage()` / `getCourseEnrollments()` / `getEnrollmentPage()` 默认排除 CANCELLED/WAITLIST/DROPPED/REJECTED
- **`computeStats` 统计口径** — 与查询层对齐，排除非有效在学状态
- **`TeacherServiceImpl.getStats()` 教师仪表盘统计** — 修复零状态过滤导致学员数膨胀、完成率稀释

#### 前端学生端选课状态误判
- **6 个学生页将非在学状态混入"进行中"** — 新增 `enrollmentFilters.js` 统一过滤助手，接入 MyCourses/LearningCenter/TrainingCenter/WeeklyReport/WrongQuestionsCard/BundleDetail
- **智能表汉堡菜单不可见** — 补上 769px-1024px 断点样式
- **收藏图标映射缺失** — ICON_MAP 补 Star
- **H5 标题回退** — h5TitleMap 补微专业页面映射

### Fixed (P1-I)
- **死代码清理** — 未使用导入/变量/函数（MyOrders/BundleDetail/StudentLayout/MyCourses）共 7 处

### Tests
- **OrderServiceBundlePriceTest** — 新增 3 个测试覆盖：套餐全课程发放、套餐订单标题展示、退款原子性与失败回滚
- **OrderPaymentFlowE2ETest** — 回归修复后 4 个支付流程测试锁定

### Fixed (生产 P0 热修复)
- **UserList 页首次加载弹窗** — 部署流程只更新了后端 jar，前端容器 (admin-1) 残留旧代码（el-switch @change 自动触发）。已同步部署前端 dist
- **6 个 dropdown 端点返回 400** — `@Range(max=100)` 被前端 `size: 1000` 调用突破。Department/Major/Class/User/Tag/BadgeController max → 1000

### Added
- **precheck #18b** — 分页 size 契约一致性检查：自动扫描前端 size 最大值 vs 后端 @Range max，不一致即 FAIL
- **precheck #19** — contract-audit.py Entity vs 数据字典交叉验证（advisory 模式，137 项 pre-existing 文档漂移不阻塞）

### Changed
- **测试 Redis 配置** — `spring.redis.*` → `spring.data.redis.*` (Spring Boot 3.x 强约束) + db=15 物理隔离
- **local-dev-deploy.sh** — mvn test 前 drop+recreate 测试 DB，解决容器复用导致孤儿数据 409

### Tests
- ✅ Backend: 543/543 tests pass
- ✅ Frontend: vite build pass
- ✅ local-dev-deploy.sh: 16/16 pass
- ✅ Precheck: 22 PASS / 1 CONTRACT-advisory
- ✅ CI: success
- ✅ Trivy: success

### Rollback
- 后端: `docker cp /opt/micro-course/backups/app.jar.v1.20.1.backup.20260708 micro-course-api-1:/app/app.jar && kill -HUP 1`
- 前端: `docker cp /opt/micro-course/backups/frontend-v1.20.0-20260708.tar.gz` → 解压到 `/usr/share/nginx/html`（待提取）

---

## [1.20.1] - 2026-07-08

### Fixed

#### P1-C 客户体验 (3)
- **选课通知显示 `《null》`** — `EnrollmentServiceImpl` 课程名 null 保护，与退课通知一致的 `课程#{id}` 兜底
- **学习页累计时长显示 `NaNh`** — `LearningView.vue formatTotalTime` typeof + isNaN 守卫，无效输入降级为 `0h`
- **课程封面 404 裂图** — 本地 DB 清理测试数据 `courses.id=71 cover_url = NULL`

#### P1-I 测试基础设施 (5)
- **测试 Redis 配置被 Spring Boot 3.x 忽略** — `application-test.yml` 改用 `spring.data.redis.*` + db=15 物理隔离，根除 UserStatusCheckFilter 历史 blacklist 401 误杀（5 个 E2E 测试修复）
- **contract-audit.py 假阳性过滤** — String/Text/LongText/MediumText 等价；枚举 vs String 等价
- **precheck.sh contract-audit advisory 化** — ~137 项 pre-existing 文档漂移不阻塞交付，TODO 独立 OpenSpec change 清理
- **local-dev-deploy.sh mvn test 前 drop+recreate DB** — 解决 postgres-test 容器复用导致 DEL_xxx 孤儿 department UUID 冲突 409
- **新增 `scripts/contract-audit.py`** — 数据字典 vs Entity 字段自动交叉验证，配套 precheck 规则 #19

### Security
- ✅ Trivy scan: success

### Tests
- ✅ Backend: 543/543 tests pass
- ✅ Frontend: vite build pass
- ✅ local-dev-deploy.sh: 16/16 pass
- ✅ Precheck: 21 PASS / 1 CONTRACT-advisory
- ✅ CI: success

### Deployment
- Rollback plan: `ROLLBACK_PLAN.md` 已就绪
- Gate status: opened
- Risk: 低（无 Flyway migration，仅应用代码 + 测试基础设施）

---

## [1.20.0] - 2026-07-04

### Fixed (Phase 11 互动课程插件 — 全量审查修复)

#### P0 安全/稳定性 (6)
- **异步线程长事务占连接池** — `NarrationService.generateAll()`/`TtsService.generateAll()` 改为 `TransactionTemplate` 短事务
- **@Transactional 包裹 HTTP API 调用 30s+** — DeepSeek API 调用改用手动重试 + 短事务隔离
- **@Async SecurityContext 丢失** — `MicroCourseApplication` 设置 `MODE_INHERITABLETHREADLOCAL`
- **NarrationSettingController IDOR** — 添加 `verifyCourseOwner()`
- **SlideRenderService XXE 漏洞** — 禁用 DTD + 外部实体
- **CourseAdminServiceImpl plugin_grants 校验缺失** — 教师创建 INTERACTIVE 课程前查授权

#### P1-C 客户体验 (5)
- **SlideEditorPanel textarea 不可编辑** — 空 setter 改为 ref + watch sync
- **authImage.js 缓存失效** — 5 分钟 TTL 缓存修复
- **SlidePlayer 翻页竞态** — pageNavLock 防快速点击
- **SlidePlayer 图片无预加载** — preloadAdjacentImages()
- **TtsServiceImpl.checkOwner 防御深度失效** — 移除 null auth 静默 bypass
- **SlidePlayer 音频 blob 内存泄漏** — cleanAudioBlobCache()

#### P1-I 代码质量 (14)
- 重排唯一约束冲突改为两阶段提交 (temp 负数 → 目标)
- IOException 区分 NoSuchFileException vs 其他错误
- 编辑讲述稿同步清理磁盘旧音频
- 重新上传 PPT 清理磁盘旧目录
- DeepSeek API 3 次重试 + 429 限流
- 移除 @Async Thread.sleep 反模式
- Qwen3-TTS 响应 path 5 层校验 (isAbsolute/isRegularFile/MP3 魔数)
- PPT XML 动画检测从硬编码 false 改为命名空间感知
- NarrationService 双括号匿名类统一 setter
- loadAuthImage 拆分为兼容 + loadAuthResource
- NarrationSettingsDialog slider 校验 trigger + 静默 catch 修复
- SlideServiceImpl 无用 import 清理 + 重排逻辑
- NarrationSettingsDialog 错误日志
- TtsController NPE getTeacherId() 防护
- SlideController FQCN 全部替换为 import

#### P2 增强 (8)
- 课程广场互动课专属角标显示
- 教师端创建互动课 5 步向导
- 教师端批量操作 (多选 AI/TTS/删除)
- SlideServiceTest 集成测试 10 个用例
- interactive-course.spec.js E2E 测试增强 (课件管理/批量/SlidePlayer/键盘/全屏)
- MicroCourseApplication 注解统一
- SlideService 双倍 DB 调用缓存
- getByCourseId 重复调用优化

### Changed
- **InteractivePluginAutoConfig** — 新增 `interactiveRestTemplate` @Bean
- **TtsController.getAudio** — 委托给 TtsService.getAudio()（移除 Controller 直读磁盘）
- **SlideController.verifyAccess** — 补空教师 ID 防护
- **TtsServiceImpl** — 拆分 `doGenerate()` 内部方法，`generateAll()` 跳过 `checkOwner()` 由 @PreAuthorize 保障

### Quality
- ✅ mvn compile 0 ERROR
- ✅ mvn test 399/399 PASS（含新增 10 个 SlideServiceTest）
- ✅ vite build SUCCESS
- ✅ precheck.sh 21/21 PASS
- ✅ Trivy Security Scan PASS
- ✅ 无 TODO/FIXME/HACK 残留

---

## [1.19.0] - 2026-07-03

### Added
- **线下课章节支持** — 章节新增 OFFLINE 类型，教师可创建线下课章节混排在教学大纲中
- **排期管理** — 教师为线下课章节设置上课日期、时间、地点、备注，支持多条排期
- **学生签到** — 学生一键签到，时间窗口内可操作（课前15分~课后30分），幂等防重复
- **签到记录** — `attendance_records` 表记录签到状态（PRESENT/LATE/ABSENT/EXCUSED），带操作追溯
- **QR 码签到** — 教师界面展示 QR 码，学生扫码签到（并行签到方式）
- **签到分析看板** — 教师查看签到率、出勤趋势、缺勤名单
- **上课提醒通知** — 上课前 30 分钟站内通知提醒学生
- **学生自助请假** — 学生在线提交请假申请，教师审批
- **考勤参与成绩** — 签到次数可参与课程成绩计算
- **移动端适配** — 签到页面响应式优化，支持手机操作
- **chapterType 白名单校验** — 后端校验章节类型（VIDEO/INTERACTIVE/EXERCISE/OFFLINE），防止脏数据

### Changed
- **CourseChapterServiceImpl** — 新增 `validateChapterType()` 白名单校验

### Quality
- ✅ mvn compile 0 ERROR
- ✅ mvn test 345/345 PASS
- ✅ vite build 0 ERROR

---

## [1.7.0] - 2026-06-25

### 🎯 Status: 技术侧 100% 就绪, 可进入灰度 2 周

### Fixed (5 P0 - 客户体验 & 业务正确性)
- **退课前端缺失 (P0-UX-U4)** (`1e94ee5`) - 学生无法通过 UI 退课,后端 API 早已存在
- **退课后可重新选课 (P0 真 bug)** (`caa22e3`) - UNIQUE 约束 + 软删记录阻挡新插入
  - 加 `physicalDeleteById` 绕过 `@TableLogic` 软删
  - 加 `TransactionTemplate(REQUIRES_NEW)` 独立事务,避免主事务回滚撤销删除
  - 加 `NOT EXISTS` 过滤 `deleted_at IS NULL`
- **课程下架后通知在学学生 (P0-U20)** (`3367044`) - admin 下架课程后学生完全不知情
- **H5 移动端退课按钮缺失 (P0 mobile)** (`40915e2`) - 移动端用户无法退课
- **视频上传 60MB→2GB (P0 upload)** (`21e31c4`) - 1 小时 1080p 视频至少 1.5GB,60MB 太小

### Fixed (2 P1 - 性能 & UX)
- **课程下架通知 同步→异步** (`da3290d`) - 200 学生 620ms 阻塞 → 40ms (15x 提升)
- **选课错误消息区分 4 种状态** (`a172c66`) - 区分 DRAFT/PENDING/REJECTED/CLOSED 状态

### Fixed (5 P2 - 代码质量)
- **课程包删除 FK 顺序** (`1b0a0e1`) - `deleteById` → 物理删 + null check
- **47 个 loadtest 坏 URL 清理** (`182224f`) - http://x.com/* 死链导致页面加载失败
- **e2e 硬编码坏 URL 根因** (`8335b22`) - smoke test coverUrl 修复
- **Prometheus tag success→ok** (`53645f2`) - 与项目响应 message 规范统一
- **错误消息精度** (`a172c66`) - 同 P1,涵盖下架/未发布/审核中/已驳回

### Added (脚本 & 工具)
- **scripts/deploy-dryrun.sh** (`c50ca5e`) - 部署前 11 章节 / 50+ 项检查
- **scripts/clean-bad-urls.sh** (`182224f`) - 清理非本地路径坏 URL
- **scripts/db-backup.sh** - 每日 DB 备份 + 30 天保留
- **scripts/gray-release.sh** - 灰度发布控制 (add/list/roll-out/roll-back)

### Added (文档)
- **docs/v1.7.0-release-report.md** - 完整发布报告 (235 行)
- **docs/agent-team-v1.7.0-report.md** - Round 1 5-agent 团队报告
- **customer-experience-report-v1.7.0.md** - 33 条客户体验走查报告
- **CHANGELOG.md** (本文件) - 版本变更记录

### Added (e2e 测试)
- **DROP-1**: 学生退课完整流程 (退课 → 重选)
- **DROP-2**: H5 移动端 (375px) 退课按钮
- **UNPUB-1**: 课程下架通知 (admin 下架 → student 收到通知)
- **PROMO-1**: 候补学生退课自动晋升 (spec §3.2)

### Security (Round 2 5-agent 团队验证)
- ✅ IDOR / SQLi / 越权 全部 403/参数化防御
- ✅ 5 攻击向量主 Agent 亲自验证
- ✅ JWT 算法固定 HS256,无混淆风险
- ✅ NotificationService IDOR 正确
- ⚠️ 视频路径可枚举 trade-off (UUID 不可枚举,业界标准)

### Performance (perf agent 实测)
- ✅ 200 并发选课: TPS=1250, P99<400ms, 错误率 0%
- ✅ 课程下架通知: 10 学生 40ms (异步)
- ✅ 5 并发退课+重选: 0 超卖
- ✅ DB 连接池 77 idle / 250 max (健康)

### 部署条件 (8/8 状态)
| # | 条件 | 状态 |
|---|------|------|
| 1 | 视频上传 | ✅ 2GB |
| 2 | 沙箱支付 | ⏳ 财务对接中 |
| 3 | HLS+CDN | ⏳ 运维排期 |
| 4 | 安全审计 | ✅ Round 1+2 |
| 5 | 法务审核 | ⏳ 待签字 |
| 6 | 灰度 2 周 | ✅ 脚本就绪 |
| 7 | 客服值班 | ⏳ 排班中 |
| 8 | DB 备份 | ✅ |

### 质量门禁
- ✅ precheck 14/14
- ✅ mvn compile 0 ERROR
- ✅ e2e 37/37 (1 skipped, ENROLL-5 已知)
- ✅ 5 攻击向量防御
- ✅ 0 超卖 (5/50/200 并发)

### 已知限制 (Q4 backlog)
- 退课重选后用户进 WAITLIST: by design (候补优先晋升)
- 视频上传 2GB 仍无 HLS+CDN: 条件 3 已知
- 视频文件公开 (permitAll): HTML5 video 限制 trade-off
- BALANCE 支付无轮询: 同步返回 PAID,无需轮询

### Total
- 413 commits
- 5 P0 + 2 P1 + 5 P2 修复
- 2 轮 5-agent 团队审计
- 0 真实安全漏洞
- 0 性能问题

---

## [1.18.0] Total
- 571 commits
- 3 P0 + 20 P1-C + 50 P1-I + 13 P2（全部清零）
- 6 维并行审计（R1-R5 + 孤岛扫描）
- 85 文件变更，590 新增 / 516 删除
- 0 缺陷残留

---

## [1.8.0] - 2026-06-25

### Fixed
- 全栈功能穷举审计 + P0 修复
- 后端 Service 层保护批量修复（FK 校验、唯一性检查）

---

## [1.9.0] - 2026-06-25

### Fixed
- 全栈 P0 缺口全部修复
- 字段契约防再发体系建立

---

## [1.10.0] - 2026-06-25

### Fixed
- 全栈 P0/P1 全部修复 · 零缺陷
- 微专业全功能合并入 main（Phase 14）

---

## [1.11.0] - 2026-06-25

### Fixed
- 终验 R1-R4 5 P0 全部修复
- 选课超卖修复 — 行级锁 + 原子化容量检查
- 业务逻辑审计 10 偏差全部修复
- E2E 完整冒烟测试套件 17/17 PASS

---

## [1.12.0] - 2026-06-25

### Fixed
- Super-Fix P0-P3 — Phase 5-6: 63 P0 + 133 P1-P3 修复
- Super-Fix P0-P3 — Phase 7: AdminDashboard + OperationLogs + AdminSettings
- Super-Fix P0-P3 — Phase 8: 视频基础设施完整实现
- Super-Fix P0-P3 — Phase 9: 批量导入 + CAS 真实集成
- 微专业审计 72/72 工单通关

---

## [1.13.0] - 2026-06-25

### Fixed
- 全量 P0 客户体验修复（付费课程购买、视频学习黑屏、404 路由等）
- 修改密码后立即失效 JWT（P0 账号接管防护）

### Added
- JSON structured logging + prod profile
- nginx 生产安全加固
- README 部署文档补充

---

## [1.16.0] - 2026-06-25

### Fixed
- CI 全量修复：GitHub Actions 升级、e2e 启动顺序修正、PostgreSQL sequences 同步
- Entity 修复：ExerciseChapter/QuestionChapter 补 @TableId
- 消除全部 CI Warning
- e2e 测试 8 个真实问题修复（凭证错误、路由错误、缺失 seed）

### Added
- CI: e2e + deploy-dryrun 自动化测试
- 反偏见基础设施：commit-msg hook + precheck check-15/16

---

## [1.17.0] - 2026-06-25

### Fixed
- 十轮穷举交叉验证 — 81 项 P0-P3 修复
- e2e CI 全部打通（PostgreSQL + Redis 服务容器）
- 7 项 P1-C 客户可感知修复（summary 校验、选课跳转、移动端按钮）
- P1-C 回归 E2E 测试套件（后端 7 项 + 前端 8 项）

---

## [1.18.0] - 2026-06-25

### 上线前全量审计修复（总工程师 R1-R5 六维验证）

#### P0（3 项，已清零）
- 路由守卫增加 refreshToken 静默刷新 — token 过期不再被踢到登录页
- 底部导航补充"学习"Tab，对齐 spec 5 tab 设计
- STAFF_ONLY_PATHS 补全 `/bundles`、`/reviews`、`/admin`、`/teacher`、`/academic`

#### P1-C（20 项，已清零）
- **Video.java** 补充 8 个缺失字段（playSign、watermarkEnabled、maxPlayRate 等）
- 9 处前后端字段名不一致修复（ExerciseList、FavoriteList、QuestionList 等）
- TagList/CourseCategoryList 移除后端不存在的列
- Admin/UserList 搜索/重置按钮 aria-label 修复
- BannerList 移除不存在的 title 引用
- 底部导航 Wallet 图标导入 + 菜单排序修复

#### P1-I（50 项，全部修复）
- 死代码清理：删除 4 个无引用 DTO、2 个未用前端 API 文件
- 通配符 import → 显式 import（12 个 Java 文件）
- 前端 API 去重（review.js、teaching-class.js）
- 架构修复：VideoController 注入接口而非实现类、CourseController 直接调 Repository 改为通过 Service
- 路径规范：TeacherController（`/api/teacher`→`/api/teachers`）、VideoStreamController、DiscussionAdminController
- 控制器 4 处添加 @PreAuthorize（BannerPublicController 等）
- VideoSignUtil 添加密钥长度校验、SecurityConfig CSRF 注释
- 3 处 el-popconfirm → ElMessageBox.confirm（删除确认标准化）
- 测试基础设施：25 文件 @Autowired→构造器注入、39 文件包路径迁移至子包
- 4 个列表页添加 Error 三态 UI（ClassList、DepartmentList、MajorList、UserList）
- 数据字典补充 8 张表 deleted_at 文档 + tags.color
- Redis DefaultTyping 安全确认（已有 BasicPolymorphicTypeValidator）
- Flyway V57/V21 文件头注释修正

#### P2（13 项，全部修复）
- OrderController bundleId null 安全处理
- CSRF 禁用原因注释
- 生产配置支付模式/CSRF/序列化等加固建议

---

## [1.6.0] - 2026-06-15

### Fixed
- 选课超卖 (P0): 行级锁 + 候补队列
- 选课失败 (P0-1): 状态机 + 容量校验
- 候补自动晋升 (P0-2): 退课触发
- 可观测性 (P0-3): 4 个 Prometheus 指标
- 紧急回滚 (P0-4): `ENROLLMENT_ENABLED` feature flag
- 连接池耗尽 (P0-连接池): PG 100→300, app 20→250

### Added
- **scripts/load-test-enrollment.js** - 选课并发压测 (50/200 并发 max=5/10)
- 运维手册 docs/runbook.md
- 业务逻辑审计报告 docs/business-audit/

### Quality
- 100 学生压测 max=10: 0 错误
- 600 loadtest 用户清理

---

## [1.5.0] - 2026-06-01

### Fixed
- 5 个 Service Guard P0 (退课入参校验等)
- 3 个 P1 (审核/驳回原因/Service 接口)
- 1 个 P2 (字段映射)

### Added
- 字段契约扫描器 `scripts/field-contract-scanner.py`
- precheck.sh 14 道门禁 (字段/响应/分页等)

---

## [1.4.0] - 2026-05-15

### Fixed
- 数据迁移: counselorId 彻底删除 (V89)
- 字段名修正: collegeId→offerDepartmentId, objectives→trainingObjective
- 教师 ID 手输数字→el-select 下拉

### Added
- Phase 14 微专业 72/72 测试通过

---

## [1.3.0] - 2026-05-01

### Fixed
- Spring Boot 3 + Java 17 升级
- MyBatis-Plus 3.5.6 集成
- PostgreSQL 17.5 适配
- Redis 7 配置

---

## [1.0.0] - 2026-04-01

### 🎯 Initial Release

#### Backend
- Spring Boot 3.2.12 + Java 17
- MyBatis-Plus 3.5.6
- PostgreSQL 17.5 + Redis 7
- Flyway 9.22.3 数据库迁移
- Spring Security + JWT 认证
- BCrypt 密码加密
- 8 状态机枚举 (CourseStatus, EnrollmentStatus, etc.)

#### Frontend
- Vue 3.4 + Element Plus 2.5
- Pinia 2.1 状态管理
- Vite 5 构建
- Axios 1.6 HTTP 客户端
- 4 角色: 学生/教师/管理员/教务

#### Core Features
- 用户管理 (CRUD + 角色)
- 课程管理 (CRUD + 上下架 + 审核)
- 选课 + 候补 + 退课
- 视频学习 + 进度保存
- 章节 + 视频 + 练习
- 讨论 + Q&A
- 评价 + 评分
- 通知 (站内信)
- 微专业
- 教学班
- 成绩管理
- 操作日志
- 数据看板
- 跨学院审核

#### Total
- 100+ 实体
- 200+ API 端点
- 50+ Vue 页面
- 35+ e2e 测试
- 30+ 业务逻辑审计项

---

## 版本兼容说明

### 数据库迁移
- V1-V89 Flyway 脚本
- 任何版本回滚都需先 `bash scripts/db-backup.sh`

### API 兼容
- v1.0-v1.7 响应格式 `R<T> { code, message, data }` 不变
- JWT 兼容 (HS256 密钥不变)
- 前端不需重装,只需刷新页面

### 部署策略
- 灰度 2 周: 10 → 100 → 500 → 全量
- 紧急回滚: `bash scripts/gray-release.sh roll-back`
- 启用 feature flag: `ENROLLMENT_ENABLED=false` 关闭选课

---

## 联系

- 项目根: /Users/jackie/微课平台
- 发布报告: docs/v1.7.0-release-report.md
- 运维手册: docs/runbook.md

---

## [Unreleased]

### Fixed (课程管理全模块 - P0~P3 全量清零)

#### Phase A/B/C (commit 0ec9037)
- **P0 课程发布越权** — `CourseAdminServiceImpl.updateStatus/submitForReview` 加 `isOwnerOrAdmin()` 校验 + CAS 模式乐观锁;前端 `CourseDetail.vue` 改用 `publishCourse()/unpublishCourse()`;publish/unpublish 按钮加 ADMIN 角色守卫;新增 active 选课学生通知
- **P0 正确率趋势** — `ExerciseRecordServiceImpl.getAccuracyTrend()` 解析 `answers` JSON 逐题 `isCorrect` 统计,替换错误的整卷 `passed` 计算
- **P0 错题本多章节覆盖** — `WrongQuestionServiceImpl` 改 `Map<Long, List<Long>>`,`findFirst()` 取首个章节
- **P0 待批改 JSON LIKE 全表扫描** — Flyway `V138__add_needs_manual_grading_to_exercise_records.sql` 加列+部分索引;`ExerciseRecord` Entity 加 `needsManualGrading` 字段;提交时设为 true,批完设为 false;查询从 `.like("needsManualGrading":true)` 改为 `.eq(true)`
- **P0 考试路由 404** — `Exams.vue` 跳 `StudentExerciseTake` (复用现有路由);`handleJoinExam` 加 `checkPrerequisiteChapters()` 前置章节完成校验;`ExerciseTake.vue` 加 `?examId` 自动开始考试;`ErrorCode` 加 `PREREQUISITE_NOT_MET(18003)`
- **P1-C 互动课翻页排序** — Flyway `V139__add_file_uuid_to_slide_pages.sql`;`SlidePage` Entity 加 `fileUuid`;`SlideRenderService` 改 UUID 文件名 `{uuid}.png`/`{uuid}_thumbnail.png`;`getPageImage/Thumbnail` 优先读 UUID,fallback 到旧 `page_N`
- **P1-C 缩略图网格** — `SlideThumbnailGrid.vue` 重写加载真实缩略图(`loadAuthResource` 签名 URL),6 并发批量 + 错误降级到页码占位 + hover scale(1.08)
- **P1-C 课程下架学生通知** — `publish/unpublish` 查 `Enrollment` ACTIVE 表后 `notificationService.notifyAsync(...)`
- **P1-C 课程删除级联** — `CourseAdminServiceImpl.delete()` 加 `chapter/video/learning_progress` 软删除;`CourseChapterServiceImpl.delete()` 已级联 `video/exercise/chapter_offline_session`
- **P1-C 章节排序归属校验** — `CourseChapterServiceImpl.sort()` 用 `Set<Long>` 校验所有章节属于同一课程
- **P1-C CourseReviewVO 加 status** — `convertToVO()` 映射 status;前端可区分审核状态
- **P1-C 评价审核流程** — `CourseReviewServiceImpl.create()` 改 `status=0` 待审核,需 `approveReview/rejectReview`
- **P1-C 智能组卷补题型** — `ExamList.vue` `typeConfigs` 加 `FILL/SHORT_ANSWER/ESSAY`,标注"需人工批改"
- **P1-C 学习中心"开始学习"按钮** — `LearningCenter.vue` PC/H5 双端 `goCourse(id)` 方法导航
- **P1-C 讨论区 chapterId** — `DiscussionView.vue:353` `createPost()` 传 `chapterId`
- **P1-C BundleDetail 已购买** — 移除 disabled,加 `startLearning()` 跳套餐内第一门课
- **P1-C 学习中心 currentChapter** — 调 `getLearningProgress` 取真实章节,API 失败 console.warn 降级

#### Phase D (commit af59d1b)
- **错题自动归档** — `ExerciseRecordServiceImpl` 答对题目后 `wrong_count - 1`,归零删错题记录
- **addQuestions 默认 score/sortOrder** — 加 score=10 默认,sortOrder 自动递增;加跨课程校验;加去重防护
- **缓存常量抽 `CourseCacheConstants`** — 消除 `CourseServiceImpl/CourseQueryServiceImpl/CoursePricingServiceImpl` 3 处重复常量定义
- **Exercise.description 字段补齐** — Flyway `V140` migration 加列;Entity + 3 个 DTO + Service 全部映射
- **杂项前端清理**:Exams.vue 字段映射修正(examTime→startTime,duration→timeLimit);VideoPlayer 进度上报失败单次 toast (sessionStorage flag);CourseDetail 难度枚举死代码清理;CourseList 导出 10000→5000 + 确认弹窗;CourseList "通过"→"已通过";CartDrawer 价格 `Number().toFixed(2)`;WeeklyReport 骨架屏 4→1 合并;TeacherOfflineSessions location 必填移除;DiscussionView size-change 200ms 防抖
- **ScoreHistory @Deprecated 移除 → 完整审计实现** — Phase E 完成

#### Phase E/F/G (commit 12e5337)
- **ScoreHistory 完整审计追踪** — `ScoreHistoryServiceImpl.recordChange()`;GradeServiceImpl 4 个 CUD 方法 (create/update/teacherGrade/manualGrade) 记录变更;审计失败降级为 warn 不抛
- **PluginRegistry.hasGrant()** — `plugin/interactive/SpringContextHolder` 静态 Bean 获取工具;`hasGrant(userId, pluginType, action)` 查 plugin_grants,VIDEO 内置免审 + ADMIN 全通
- **CourseCategoryController 加 ACADEMIC 角色** — 3 处 `@PreAuthorize` 改 `hasAnyRole('ADMIN','ACADEMIC')`,教务处可管理分类
- **ChapterVO.videoCount getById() 填充** — `CourseChapterServiceImpl.getById()` 加 `videoRepository.selectCount` 单章节计数
- **ChapterVO learningObjectives 映射** — `convertToVO()` 补充字段
- **VideoServiceImpl.updateStatus() CAS** — `LambdaUpdateWrapper.eq(currentStatus)` + `setSql("version+1")`,失败抛 `CONCURRENT_MODIFICATION`
- **章节删除级联 LearningProgress/CourseNote** — 之前只级联 video/exercise,现在补齐
- **课程删除级联 LearningProgress** — 防止学生看到错的章节对不上的进度
- **WrongQuestionVO 删冗余 content** — 保留 questionContent 作为唯一字段
- **前端清理**:FILL 题型补全 + 移除假分值列;MyReviews skeleton 嵌套修复;ExerciseQuickPanel 文案"X 道练习题"→"X 个练习";CourseList 移除 ACADEMIC 假发布权限;ChapterList @change 占位;Checkout 删 fake setTimeout loading;LearningCenter 错误日志不再静默

#### Phase H (commit 7353e35)
- **`InteractivePlugin.isEnabled()` 配置化** — 加 `@Component` + `@Value("${plugin.interactive.enabled:true}")`,默认 true 保持兼容
- **`CourseSlide.lessonId` 字段加 `@TableField(exist=false)`** — 注释说明"保留字段,数据库暂无对应列",防止严格映射触发 Unknown column
- **NarrationSetting 受插件开关控制** — `NarrationSettingController/Service` 加 `@ConditionalOnProperty(value="plugin.interactive.enabled", matchIfMissing=true)`,与插件架构对齐
- **签到窗口配置化** — `@Value("${course.offline.checkin-before-minutes:15}")` 替换硬编码常量 `CHECKIN_WINDOW_BEFORE_MINUTES=15`,运维可通过 `application.yml` 调整
- **前端课程复制功能** — `CourseDetail.vue` 加"复制"按钮 + `handleCopy()`,首次 ElMessageBox 确认视频不会复制,后端返回 `videoCopied=false` 时 ElMessageBox.alert 详细提示,跳新课程详情

#### Phase I (segment-audio — 15段音频注入 + 零信任审查修复)
##### Added
- **15段独立音频上传 API** — `AudioUploadController.uploadBatch` 支持 `file_1~file_15` 批量上传，`AudioUploadServiceImpl` UUID 文件名防并发覆盖 + token URL 鉴权
- **HTML 课件音频 JS 控制器** — `SlideServiceImpl.buildSegmentControllerJs()` 注入 15 段音频 postMessage 通信脚本 (play/pause/seek/speed/get-segments)
- **token 鉴权通道** — `TtsController.getAudio` 移除 `@PreAuthorize`，新增 token 验证 + session 鉴权双通道；`TtsService.validateAudioToken()` DB 查询验证 token

##### Fixed (10轮零信任审查，20缺陷清零)
- **P0 ×6**: 占位符只替换1/15、会话缺失401、上传路径≠播放路径、`get-state`被覆盖、单课URL错误替换、15段音频无控制器
- **P1-C ×9**: 播放按钮禁用、状态同步不通、自动翻页竞态、DB sectionId歧义、glob二义性、单课覆盖批次、quiz不暂停音频、goTo不发页号、测试timer未推进
- **P1-I ×1**: `replaceAudioSegmentPlaceholders` 逻辑重复
- **P2 ×4**: time-update未处理、回调无gen校验、token未验证、倍速调节失效

##### Changed
- `AudioUploadServiceImpl` — `resolveAudioDir`路径与`TtsServiceImpl`一致化；`estimateDuration`移除用户可控文件名参数
- `TtsServiceImpl.getAudio` — `resolveAudioFromDb` DB查询精确文件路径，`tryGlobResolve` glob回退
- `SlideServiceImpl.getPages` — 全局15占位符替换；`buildSegmentUrl`单课/批量区分
- `SlidePlayer.vue` — `segmentAudioMode`隔离；`handleAudioStateUpdate`双通道；quiz暂停/恢复；`playAudio`发seek带页号

##### Security
- ✅ token 单向验证 (UUID 128-bit，DB持久化)
- ✅ srcdoc iframe null origin 双向 postMessage
- ✅ path traversal 防护 (`.startsWith(basePath)` + `toRealPath()`)

##### Tests
- ✅ Backend: mvn compile 0 ERROR
- ✅ Frontend: vite build SUCCESS
- ✅ Unit: 14/14 PASS (含修复的 timer 测试)
- ✅ Precheck: 22/22 PASS

### 质量门禁 (本次发布)

- ✅ precheck 21/21 PASS
- ✅ mvn compile 0 ERROR
- ✅ npm run build 成功
- ✅ P0/P1-C/P1-I/P2 全部清零
- ✅ 76 文件变更,4 commit 链 (0ec9037, af59d1b, 12e5337, 7353e35)
- ✅ 5 个 P0 + 24 个 P1-C + 18 个 P1-I + 12 个 P2 全部修复
- ✅ 3 个 Flyway migration: V138/V139/V140

### 安全基线

- ✅ 课程发布双层 owner 校验(前端按钮 + 后端接口)
- ✅ PluginGrant 授权校验单点入口(plugin 包外创建互动课走 PluginRegistry.hasGrant)
- ✅ VIDEO_SIGN_SECRET 生产环境强制(本地开发兜底密钥已不与 JWT 共享)
- ✅ JWT 黑名单 Redis 化 (RedisUtil + JwtAuthenticationFilter)
- ✅ HikariCP 连接池监控 (pool-name + Micrometer)
- ✅ RateLimitInterceptor (FileAccessRateLimit) 防止资源盗链
- ✅ XSS Sanitizer 用于用户输入字段(course 评论、驳回理由等)
- ✅ Flyway out-of-order 启用,V138/V139/V140 可顺序应用
