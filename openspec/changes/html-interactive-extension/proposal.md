# HTML 互动课件扩展 (HTML Interactive Courseware Extension)

> **OpenSpec Change**: `html-interactive-extension`
> **Schema**: spec-driven
> **创建日期**: 2026-07-10
> **关联 spec**: `docs/开发规划/phase11-interactive-course-spec.md` (Phase 11 互动课程插件)
> **干系人**: 教授分身（hermes-professor profile）· 平台开发智能体 · jackie

---

## Why

**问题陈述**: 互动课程插件（Phase 11）已实现 PPT → PNG 服务端渲染 + 音频同步 + 自动播放，但**视觉表现力受限**于 PPT 模板：

| 受限点 | 影响 | 教师痛点 |
|--------|------|----------|
| **PPT 转 PNG = 位图** | 文字不可选中/搜索/无障碍 | 屏幕阅读器学生无法学习 |
| **字体必须服务端安装** | 平台字体不全 → 视觉变形（failures.md #11） | 教师的"思源黑体"显示成"宋体" |
| **PPT 模板风格单一** | 平台只有 8 种 PPT 模板，风格趋同 | 学生审美疲劳，"PPT 都长一样" |
| **无法交互** | PNG 是静态图，无法点击/动画/表单 | "演示" ≠ "授课"，缺少互动环节 |
| **无法承载现代设计** | 卡片/网格/动画/视频嵌入都做不到 | 难以表达复杂概念（数据可视化等） |

**根因**：phase11 spec 头部明确否决了 PPTXjs 浏览器端方案（理由：中文字体渲染 FAIL + jQuery/Vue 3 冲突 + 移动端 OOM），选用了"Apache POI 服务端预渲染 → 逐页 PNG"路线。**这条路线是稳定的、但视觉天花板低**。

**为什么必须现在做**：

1. **开学压力**：2026-07-10 是周五，开学在即，现有课件"丑"已影响上课效果
2. **教授分身已产出 HTML demo**（`/tmp/demo-html/video01-html-demo.html`）证明 HTML 路线视觉远超 PNG
3. **平台自动播放能力已具备**（SlidePlayer.vue 80% 完成：autoMode + audio ended + 倍速 + 全屏 + 讲述稿），只需补"装载内容"分支
4. **不需重做 phase11**：在现有架构上加 `content_type` 字段即可，是增量不是颠覆

**为什么不能"全部重做"或"等待 phase12"**：

- ❌ 重做 phase11 = 推翻已稳定的 Apache POI 渲染流程 = 引入 P0 风险
- ❌ 等 phase12 = 至少 1-2 个月 = 错过开学
- ✅ **路径 C（用户已选）**：平台改造自动转 PPT + 加 HTML 装载点，两者并存，老课走 PPT（保稳），新课可走 HTML（求好）

---

## What Changes

### 新增能力

- **CAP-1 HTML 课件上传**：教师可直接上传 `.html` 文件作为课时内容（与现有 `.pptx` 上传并行）
- **CAP-2 内容类型字段**：`slide_pages` 表新增 `content_type` 字段（`PPT_RENDERED` / `HTML_DIRECT`），后端按类型分支处理
- **CAP-3 HTML 装载点**：SlidePlayer.vue 加分支，HTML 课时渲染 `<iframe sandbox>` 或 `<div v-html>` 容器
- **CAP-4 自动 PPT→HTML 转换（可选）**：教师上传 PPT 时，平台除生成 PNG 外，**额外尝试**生成语义 HTML 副本（用 python-pptx 解析 XML 重构），转换失败时**fallback 到 PNG**（保持向后兼容）
- **CAP-5 HTML 安全沙箱**：HTML 课时强制 `sandbox` 属性（禁用 script、表单、顶层导航），防 XSS
- **CAP-6 Hermes 推送端扩展**：教授分身 `push_course.py` 支持推送 HTML 课时
- **CAP-7 灰度发布**：HTML 装载点先在白名单教师开启（与 v1.20.0 灰度策略一致），验证稳定后全量

### 修改能力

- **CAP-8 (修改) SlideController.upload()**：增加 `content_type` 参数，`html` 时走新分支
- **CAP-9 (修改) SlideRenderService**：增加 `renderHtmlAsync()` 方法（与 `renderAsync()` 并行）
- **CAP-10 (修改) SlidePlayer.vue**：模板层加 `v-if="contentType === 'html'"` 分支
- **CAP-11 (修改) plugins/interactive/api/slide.js**：增加 `uploadHtml()` 函数
- **CAP-12 (修改) plugins/interactive/components/SlideUploadZone.vue**：接受 `.html` 文件
- **CAP-13 (修改) Flyway**：新增 `V177__slide_pages_content_type.sql`（假设 V176 后是 V177）
- **CAP-14 (修改) 数据字典**：v0.6 → v0.7 增 `content_type` 字段说明
- **CAP-15 (修改) API 契约**：`docs/API契约-Phase1.md` 增 HTML 上传端点
- **CAP-16 (修改) 权限矩阵**：HTML 上传需 `plugin_grants`（与 PPT 同一权限位）

### 新增 Capabilities (OpenSpec specs)

- `interactive-html-content`：HTML 课时内容类型
- `interactive-html-render`：HTML 渲染服务（含安全沙箱）
- `interactive-ppt-to-html`（可选）：PPT→HTML 自动转换

### Modified Capabilities

- `interactive-courseware`：互动课件能力扩展
- `data-contract`：数据字典 v0.6 → v0.7
- `api-contract`：API 契约新增端点
- `permission-matrix`：HTML 上传权限继承 PPT 上传

---

## Impact

### 受影响系统

| 系统 | 改动范围 | 风险 |
|------|---------|:----:|
| **后端 - SlideController** | 1 文件，约 +50 行 | 低 |
| **后端 - SlideRenderService** | 1 文件，约 +100 行 | 低（新增方法，不动旧的） |
| **后端 - SlidePage 实体** | 1 字段迁移 | 中（DB 迁移） |
| **前端 - SlidePlayer.vue** | 1 文件，约 +30 行 | 低（分支渲染） |
| **前端 - SlideUploadZone.vue** | 1 文件，约 +10 行 | 低 |
| **前端 - slide.js** | 1 函数 | 低 |
| **Hermes - push_course.py** | 1 函数，约 +30 行 | 低 |
| **数据字典 / API 契约** | 2 文档 | 低 |

### 不影响

- ✅ **现有 PPT 上传流程**（向后兼容，未迁移课时仍走 PNG 路径）
- ✅ **现有自动播放/倍速/全屏/讲述稿/学习进度**（全部复用）
- ✅ **视频课程、题库、讨论区、选课**（其他域零改动）
- ✅ **学校服务器 100.74.122.13**（生产环境本次不动，走灰度）

### 关键风险

| 风险 | 等级 | 缓解 |
|------|:----:|------|
| HTML 上传引入 XSS | **高** | 强制 `sandbox` + CSP + 后端二次 sanitize |
| HTML 文件大小爆炸（嵌入大图片）| 中 | 后端限制 ≤5MB / 文件，建议外链 |
| PPT→HTML 转换失败率高 | 中 | fallback 到 PNG，不阻塞主流程 |
| iframe 跨域问题 | 低 | 用 `srcdoc` 而非 `src`，无跨域 |
| 灰度策略失误 | 低 | 复用 v1.20.0 灰度白名单流程（proven）|

---

## Acceptance Criteria

### 后端

- [ ] 上传 `.pptx` → 现有 PNG 流程不变（向后兼容）
- [ ] 上传 `.html`（`content_type=html`）→ 跳过 PNG 渲染，直接存 HTML 文本
- [ ] 上传 `.pptx` 且 `auto_convert_html=true` → 除 PNG 外**额外**生成 HTML 副本，失败不报错
- [ ] HTML 大小限制 ≤5MB，超限返回 `code 16010`（新错误码）
- [ ] HTML 内容 sanitize：剥离 `<script>`、内联事件处理器、`javascript:` URL
- [ ] HTML 存储：`slide_pages.html_content TEXT NULL` + `slide_pages.content_type VARCHAR(20)`

### 前端

- [ ] SlidePlayer.vue 检测 `contentType`，HTML 时渲染 `<iframe sandbox="allow-same-origin" srcdoc="...">`
- [ ] 自动播放/倍速/全屏/讲述稿/进度对 HTML 课时同样生效
- [ ] SlideUploadZone.vue 接受 `.html` 文件（前端先校验后缀）
- [ ] 教师管理页（SlideManage.vue）显示 HTML 时长徽标（与 PNG 一致）

### 安全

- [ ] HTML 课时渲染强制 `sandbox="allow-same-origin"`（禁止 script/top-navigation/form)
- [ ] 后端用 `OWASP Java HTML Sanitizer` 处理上传 HTML
- [ ] 平台 CSP header 包含 `sandbox allow-same-origin` 策略
- [ ] HTML 上传走 `plugin_grants` 授权（与 PPT 同一权限位）

### 灰度

- [ ] HTML 装载点先对白名单教师开启（与 v1.20.0 灰度策略一致）
- [ ] 监控指标：HTML 课时加载成功率 ≥99.5%，XSS 拦截率 100%
- [ ] 1 周稳定后逐步扩大白名单

### 端到端

- [ ] 教师上传 `.html` → 学生播放 → 自动播放正常 + 讲述稿同步 + 进度保存
- [ ] 教师上传 `.pptx` 触发自动转 HTML → HTML 副本可用 → fallback 到 PNG 也不阻塞
- [ ] 老课（PPT 上传，无 HTML 副本）继续走 PNG 路径，不受影响

---

## Out of Scope

- ❌ PPT→HTML "完美无损"转换（字体/动画/可访问性很难做，工程量大，性价比低）
- ❌ HTML 互动组件（选择题/拖拽/表单）——本次只做"展示型" HTML，未来 phase
- ❌ Web Component 嵌入——本次只做 HTML 片段，不做组件化
- ❌ 实时协作编辑 HTML 课件
- ❌ HTML 课时导出 PDF（已有 HTML→PDF 库，但优先级低）

---

## Time & Cost Estimate

| 模块 | 工作量 | 风险 |
|------|------|:----:|
| **后端** | 3-5 天 | 低 |
| - SlidePage 实体 + Flyway V177 | 0.5 天 | 中 |
| - SlideController.upload() HTML 分支 | 0.5 天 | 低 |
| - SlideRenderService.renderHtmlAsync() + sanitize | 1 天 | 中 |
| - 错误码 16009/16010 + 单测 | 0.5 天 | 低 |
| - 灰度配置 + 监控 | 0.5 天 | 低 |
| **前端** | 2-3 天 | 低 |
| - SlidePlayer.vue HTML 分支 | 0.5 天 | 低 |
| - SlideUploadZone.vue 接受 .html | 0.5 天 | 低 |
| - slide.js uploadHtml() + SlideManage 适配 | 0.5 天 | 低 |
| - 联调 + 移动端测试 | 1 天 | 中 |
| **Hermes 推送端** | 1 天 | 低 |
| - push_course.py 支持 htmlLessons[] | 1 天 | 低 |
| **审计 + 灰度** | 3-5 天 | 标准流程 |
| **合计** | **9-14 天** | |

---

## Reference

- **现有能力**：docs/开发规划/phase11-interactive-course-spec.md（v2.0，2026-06-20）
- **PPT 渲染代码**：micro-course-api/.../SlideRenderService.java（已读）
- **播放器代码**：micro-course-admin/.../student/SlidePlayer.vue（27KB，已读）
- **互动课 API**：micro-course-admin/.../plugins/interactive/api/slide.js（已读）
- **外部参考**：MiniMax-AI/skills (miniMax-pptx-generator, archlizheng/frontend-slides-editable 等已下载到本地 _external/)
- **教授分身调研产物**：~/.hermes/profiles/professor/.vibe/_research-2026-07-10/
- **HTML demo**：/tmp/demo-html/video01-html-demo.html（已验证）

---

*本 PRD 由教授分身（hermes-professor profile，MiniMax-M3）产出*
*日期：2026-07-10*
*状态：待 jackie review → 给平台开发智能体执行*
