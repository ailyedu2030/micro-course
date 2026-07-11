# HANDOFF: HTML 互动课件扩展 (转交备忘录)

> **从**: 教授分身（hermes-professor profile）
> **到**: build 分身（hermes-build profile）
> **日期**: 2026-07-10
> **项目**: /Users/jackie/微课平台
> **OpenSpec Change**: `html-interactive-extension`

---

## 🎯 任务一句话

在已有互动课插件（phase11）基础上，加一个分支：**让教师可以上传 `.html` 文件作为课时**，学生播放时用 `<iframe sandbox>` 渲染，**享受平台已有的自动播放/倍速/全屏/讲述稿/进度**。同时 PPT 上传时可**额外尝试**生成 HTML 副本（失败不影响 PNG）。

完整需求、架构、决策、风险、迁移计划见同目录的：

- **`proposal.md`** (10KB) — Why / What / Impact / Acceptance
- **`design.md`** (18KB) — Architecture / Decisions / Risks / Migration
- **`tasks.md`** (10KB) — 56 个任务,7 阶段,9-14 天
- **`specs/`** — 4 个 capability 详细规范

---

## 📍 项目位置

```
/Users/jackie/微课平台/                    ← 项目根
├── micro-course-api/                     ← Spring Boot 后端 (Java 17)
├── micro-course-admin/                   ← Vue 3 前端
├── docs/开发规划/
│   ├── phase11-interactive-course-spec.md    ← 必读！现有互动课架构
│   └── FIELDS_CONTRACT.md                    ← 数据字典（要更新 v0.6→v0.7）
├── docs/PRODUCTION_SAFETY.md              ← ⛔ P0 必读：生产安全铁律
├── AGENTS.md                              ← ⛔ P0 必读：AI 开发入口规范
├── CLAUDE.md                              ← Skill routing 索引
├── openspec/changes/html-interactive-extension/  ← 本次 change 目录
└── openspec/changes/archive/2026-07-06-course-domain-drift-fix/  ← 范例 change
```

---

## 🚨 第一步（重要！不要跳）

按 AGENTS.md 规定的开发流程，**必须按顺序**：

### Step 0：读环境

```bash
cd /Users/jackie/微课平台
cat AGENTS.md                           # ⛔ P0 必读：AI 开发入口
cat docs/开发规划/phase11-interactive-course-spec.md  # 理解现有架构
cat .claude/skills/microcourse/SKILL.md # 25 条禁止项
cat docs/PRODUCTION_SAFETY.md           # 生产安全铁律
```

### Step 1：读契约

```bash
cat docs/开发规划/FIELDS_CONTRACT.md    # 数据字典（要更新）
cat docs/API契约-Phase1.md              # API 契约（要更新）
cat docs/权限矩阵.md                    # 权限矩阵（要更新 v4.0→v4.1）
```

### Step 2：预检

```bash
bash precheck.sh
# 退出码 != 0 → 禁止写代码
```

### Step 3：建分支 + 读 PRD

```bash
git checkout -b feature/html-interactive-extension
openspec show html-interactive-extension --json --deltas-only
```

### Step 4：按 tasks.md 实施

`tasks.md` 有 56 个任务，7 个阶段，**单任务 ≤ 2 小时**。每个任务验收标准都写明了。

### Step 5：自检 + 5 维交叉验证

- R1 接口契约（与 OpenAPI 一致）
- R2 数据契约（与数据字典 v0.7 一致）
- R3 权限契约（与权限矩阵 v4.1 一致）
- R4 状态机契约（与状态机设计.md 一致）
- R5 业务规则契约（与开发规范 v1.5 一致）

---

## ⛔ 绝对禁止（来自 AGENTS.md / PRODUCTION_SAFETY.md）

| 禁止 | 后果 |
|------|------|
| ❌ 在生产服务器（100.74.122.13 / microcourse.ailyedu.cn）调试 | **P0 事故，立即 revert** |
| ❌ 跳过本地预检直接部署生产 | **P0 事故** |
| ❌ 跳过 Step 1-3 直接写代码 | **阻塞合并** |
| ❌ 同一容器 5 分钟内重启 >1 次 | **必须停下报告用户** |
| ❌ 用 Lombok / @Autowired | **违反 25 条禁止项** |
| ❌ 不写 Flyway 直接改 DB | **违反 25 条禁止项** |
| ❌ 不更新数据字典 / API 契约 | **违反 25 条禁止项** |

---

## 🔑 关键技术点速查

### 现有架构（phase11 已实现，不要重做）

- **课程类型**：VIDEO / INTERACTIVE（plugin_grants 授权）
- **课时表**：`slide_pages`（已有 id/slideId/courseId/pageNumber/imageUrl/thumbnailUrl/narrationScript/narrationAudioUrl/...）
- **渲染**：`SlideRenderService.renderAsync()` 用 Apache POI + Java2D 把 PPT 画成 PNG（**已实现，不要动**）
- **播放器**：`micro-course-admin/src/views/student/SlidePlayer.vue` (27KB)
  - 已有 autoMode / audio ended / 倍速 / 全屏 / 讲述稿 / 进度 / 键盘
  - **只需加 HTML 分支**（`v-if="contentType === 'HTML_DIRECT'"` 渲染 iframe）
- **互动课 API**：`micro-course-admin/src/plugins/interactive/api/slide.js`
  - 现有 `uploadSlide()` 走 POST `/courses/{id}/slides/upload`
  - **新增 `uploadHtml()`**，复用 uploadSlide 端点，加 `contentType=html` 参数

### 本次新增（增量，不推翻）

| 模块 | 改动 | 风险 |
|------|------|:----:|
| `slide_pages` 表加 `content_type` + `html_content` | Flyway V177 | 中 |
| `SlidePage` 实体 + VO 加字段 | 手写 getter/setter | 低 |
| `SlideService.uploadHtml()` | 复用 sanitize 工具 | 低 |
| `SlideRenderService.tryConvertPptxToHtml()` | 可选，失败 fallback | 低 |
| `SlideController.upload()` 加 content_type 分支 | 1 文件约 +50 行 | 低 |
| `SlidePlayer.vue` iframe 分支 | 1 文件约 +30 行 | 低 |
| `slide.js` `uploadHtml()` | 1 函数 | 低 |
| 错误码 16009/16010 | `ErrorCode.java` | 低 |
| OWASP Java HTML Sanitizer | pom.xml 加依赖 | 低 |

### 安全要点（验收必过）

- **iframe `sandbox="allow-same-origin"`**（禁用 script/form/top-navigation/popup）
- **后端 OWASP Sanitizer** 移除 script/onerror/javascript: URL
- **CSP header** 包含 frame-src 'self'
- **HTML 大小 ≤5MB**（防 DoS）
- **白名单灰度发布**（复用 v1.20.0 灰度策略）

---

## 📋 56 个任务快速导航（tasks.md 完整）

| 阶段 | 任务数 | 关键产出 |
|------|:----:|----------|
| 0 · OpenSpec 文档 | 7 | proposal/design/tasks/4 specs ✅ 3/7 已完成 |
| 1 · 后端 | 13 | Flyway V177 / SlideController / SlideService / sanitize |
| 2 · 前端 | 9 | SlidePlayer iframe / SlideUploadZone / slide.js |
| 3 · Hermes 推送端 | 3 | push_course.py 支持 htmlLessons[] |
| 4 · 联调 + 安全 | 18 | 5 维验证 / 10 种 XSS / 移动端 |
| 5 · 灰度发布 | 6 | 白名单 / 监控 / 扩大 / 全量 |
| 6 · 文档同步 | 6 | 数据字典 v0.7 / API 契约 / 权限矩阵 v4.1 |
| 7 · OpenSpec Archive | 4 | validate / archive / jackie 签字 |

---

## ⚙️ 验证命令清单

```bash
# 后端单测
cd /Users/jackie/微课平台/micro-course-api
mvn test -Dtest=SlideServiceTest
mvn test -Dtest=HtmlSanitizerTest
mvn test -Dtest=SlidePageTest

# Flyway 验证
docker compose up -d postgres
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/microcourse_test

# 前端单测
cd /Users/jackie/微课平台/micro-course-admin
npm run test:unit -- SlidePlayer
npm run test:unit -- SlideUploadZone
npm run test:unit -- slide.test

# OpenSpec validate
openspec validate html-interactive-extension --type change

# XSS 渗透测试（Stage 4.2 列了 10 种 payload）
```

---

## 📞 联系教授分身

如果实施过程中遇到 PRD 模糊点或 spec 冲突：

1. 先查 `docs/开发规划/phase11-interactive-course-spec.md`（现有架构文档）
2. 再查 `openspec/changes/archive/2026-07-06-course-domain-drift-fix/`（范例 change）
3. 仍然不确定 → 报告 jackie（不要硬猜）

教授分身已完成：
- ✅ 现状调研（4 方案对比、3 路径实测）
- ✅ HTML demo（/tmp/demo-html/video01-html-demo.html）
- ✅ PRD 三件套 + 4 specs（46KB）
- ✅ OpenSpec validate PASS
- ✅ 转交备忘录（本文档）

教授分身**不会**做：写代码、改 DB、改前端组件——这些是 build 的活。

---

## ✅ 启动检查清单

build 分身接手前确认：

- [ ] 已读 AGENTS.md 和 PRODUCTION_SAFETY.md
- [ ] 已读 phase11 spec 理解现有架构
- [ ] `bash precheck.sh` 退出码 = 0
- [ ] 已建分支 `feature/html-interactive-extension`
- [ ] `openspec show html-interactive-extension --deltas-only` 输出正常
- [ ] 已读 tasks.md 知道从哪个任务开始（建议从 Stage 1.1 开始）

**未完成上述检查 → 禁止写代码**。

---

*HANDOFF 由教授分身（hermes-professor profile，MiniMax-M3）产出*
*日期: 2026-07-10*
