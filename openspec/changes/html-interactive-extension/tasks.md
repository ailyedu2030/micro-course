# Tasks: HTML 互动课件扩展

> **OpenSpec Change**: `html-interactive-extension`
> **Schema**: spec-driven
> **进度追踪**: `- [ ]` 复选框格式 (OpenSpec apply 阶段自动识别)
> **单任务限制**: ≤ 2 小时

---

## 1. 阶段 0 — OpenSpec 文档（已完成）

- [x] **1.1 proposal.md**
  - **验收**: Why/What/Capabilities/Impact/Acceptance 完整
- [x] **1.2 design.md**
  - **验收**: Architecture/Decisions/Risks/Migration 完整
- [x] **1.3 tasks.md** (本文档)
- [x] **1.4 specs/interactive-html-content/spec.md** (已提交 a044834)
- [x] **1.5 specs/interactive-html-render/spec.md**
- [x] **1.6 specs/interactive-ppt-to-html/spec.md**（可选，Phase 2 Defer）
- [x] **1.7 specs/interactive-courseware/spec.md** (已更新 V177 增量)

---

## 2. 阶段 1 — 后端实现

### 2.1 依赖与配置

- [x] **2.1.1 pom.xml**: Jsoup 1.18.3 已安装（替代 OWASP，性能更优，功能等价）
  - **验收**: mvn dependency:tree 看到 `org.jsoup:jsoup:1.18.3`
- [x] **2.1.2 application.yml**: plugin.interactive.html-content 配置完整（max-file-size + whitelist-teachers）
  - **验收**: 启动日志显示 `html-content.enabled=true`

### 2.2 Flyway 迁移

- [x] **2.2.1 V177 migration**: 已写 V177 + V177b + V178（3 个文件提交 cb97b76）
  - **验收**: docker compose up 后 `psql -c "\d slide_pages"` 看到 content_type + html_content 列
  - **SQL**:
    ```sql
    ALTER TABLE slide_pages
        ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT 'PPT_RENDERED',
        ADD COLUMN html_content TEXT;

    CREATE INDEX idx_slide_pages_content_type ON slide_pages(content_type);

    ALTER TABLE slide_pages
        ADD CONSTRAINT chk_content_type CHECK (content_type IN ('PPT_RENDERED', 'HTML_DIRECT'));
    ```

### 2.3 实体变更

- [x] **2.3.1 SlidePage.java**: contentType + htmlContent 字段已加（含完整 @TableField 注解）
  - **验收**: 手写 getter/setter，遵循 25 条禁止项（不用 Lombok）
- [x] **2.3.2 SlidePageVO.java**: contentType + htmlContent 字段已加
  - **验收**: 前端能读到 contentType

### 2.4 Service 层

- [x] **2.4.1 SlideService.uploadHtmlFile()**: 已实现 uuid + sanitize + contentType 入库
  - **验收**: 处理 .html 上传 + sanitize + 存储
- [x] **2.4.2 HtmlSanitizer**: 已实现（含快速拒绝 containsDisallowedContent + sanitize 完整流程）
  - **验收**: 单元测试覆盖 10+ XSS payload
- [x] **2.4.3 (Phase 1) SlideRenderService.tryConvertPptxToHtml() 方法**
  - **状态**: 已实现基于 Jsoup 的基础 PPT→HTML 转换，上传 PPT 时自动生成 HTML 副本。
  - **验收**: PPT 上传时调用 tryConvertPptxToHtml，即使失败也不阻塞主渲染路径
  - **后续**: Phase 2 可用 Apache POI + Jsoup 增强转换质量（见 2.4.4）
- [ ] **2.4.4 (Phase 2 Defer) PptxToHtmlConverter 工具类**
  - **状态**: 推迟到 Phase 2 实现
  - **验收**: 用 Apache POI + Jsoup 实现基础转换
  - **不在 Phase 1 阻塞合并**: 当前 PPT_RENDERED + HTML_DIRECT 两条路径已可独立工作

### 2.5 Controller 层

- [x] **2.5.1 SlideController.upload()**: 加 HTML 分支 + 白名单灰度
  - **验收**: 后端日志显示分流：.html → uploadHtml()，.pptx → uploadPptx()
- [ ] **2.5.2 新增 SlideController.uploadHtml() 端点**
  - **路径**: `POST /api/courses/{courseId}/slides/upload`（同 upload，但 content_type=html）
  - **验收**: Swagger 文档更新

### 2.6 错误码

- [x] **2.6.1 ErrorCode**: 16009 HTML_INVALID, 16010 HTML_TOO_LARGE, 16011 HTML_SANITIZE_REMOVED_ALL
  - **验收**: docs/数据字典.md v0.7 更新

### 2.7 后端单测

- [x] **2.7.1 SlideServiceTest**: uploadHtmlFile_Success() ✅
  - **验收**: MockMultipartFile("<p>File Upload Test</p>") 上传成功，status=2，slideId=43
- [x] **2.7.2 SlideServiceTest**: uploadHtmlFile_TooLarge() ✅
  - **验收**: 5MB+1 字节 HTML 抛 HTML_TOO_LARGE (16010)
- [x] **2.7.3 SlideServiceTest**: uploadHtmlFile_XssRemoved() ✅
  - **验收**: 仅含 `<script>alert(1)</script>` 的 HTML 抛 HTML_SANITIZE_REMOVED_ALL (16011)
- [ ] **2.7.4 SlideServiceTest.uploadPptx_withAutoConvert()**
  - **验收**: PPT 上传成功，HTML 副本生成（即使失败也不抛错）
- [ ] **2.7.5 SlidePageTest.checkConstraint()**
  - **验收**: 非法 content_type 入库被 CHECK 约束拒绝
- [ ] **2.7.6 Flyway 迁移回滚测试**
  - **验收**: V177 可向下迁移（删除 content_type 列）

---

## 3. 阶段 2 — 前端实现

### 3.1 SlidePlayer.vue

- [x] **3.1.1 template 加 iframe 分支**
  - **验收**: `v-if="currentPage?.contentType === 'HTML_DIRECT'"` 渲染 `<iframe sandbox srcdoc>`
- [x] **3.1.2 script 加 loadHtmlPage() 方法**
  - **验收**: HTML 课时无需图片预加载，直接显示 htmlContent
- [x] **3.1.3 style 加 .slide-iframe 样式**
  - **验收**: iframe 100% 宽高，与 .slide-image 视觉一致

### 3.2 SlideUploadZone.vue

- [x] **3.2.1 accept 属性加 .html**
  - **验收**: 文件选择对话框可过滤 .html
- [x] **3.2.2 前端校验文件类型**
  - **验收**: 非 .html/.pptx 拒绝 + 友好提示
- [x] **3.2.3 大小校验（前端 5MB）**
  - **验收**: >5MB 拒绝 + 提示

### 3.3 plugins/interactive/api/slide.js

- [x] **3.3.1 新增 uploadHtml() 函数**
  - **验收**: 60s timeout（HTML 比 PPT 快）
- [x] **3.3.2 修改 uploadSlide() 加 contentType 参数**
  - **验收**: 默认 'auto'，可显式传 'html' 或 'pptx'

### 3.4 SlideManage.vue

- [x] **3.4.1 HTML 课时显示"HTML"徽标**
  - **验收**: 缩略图右上角蓝色"HTML"角标
- [x] **3.4.2 上传按钮文案更新**
  - **验收**: "上传 PPT / HTML" 提示

### 3.5 前端单测

- [x] **3.5.1 SlidePlayer.test.js iframe 渲染**
  - **验收**: contentType='HTML_DIRECT' 时渲染 iframe 而非 img
- [x] **3.5.2 SlidePlayer.test.js 自动播放 HTML 课时**
  - **验收**: 音频 ended 触发 goTo(next)，iframe 内容保持
- [x] **3.5.3 SlideUploadZone.test.js 接受 .html**
  - **验收**: .html 文件可上传
- [x] **3.5.4 slide.test.js uploadHtml API**
  - **验收**: POST 正确，参数正确
- [x] **3.5.5 SlideManage.test.js HTML 徽标**
  - **验收**: HTML 课时显示角标

---

## 4. 阶段 3 — Hermes 推送端

- [ ] **4.1 push_course.py 支持 htmlLessons[] 字段**
  - **验收**: 教授分身 HTML 课时可推送
- [ ] **4.2 验证 HTML 推送后端能识别**
  - **验收**: 推送日志显示 contentType=HTML_DIRECT
- [ ] **4.3 (可选) make_html.py 输出符合 push_course.py 的格式**
  - **验收**: 教授分身 HTML demo → 一键推送

---

## 5. 阶段 4 — 联调 + 安全审查

### 5.1 5 维交叉验证

- [x] **5.1.1 R1 接口契约**：后端 API 与 OpenAPI 一致
  - **状态**: ✅ Round 1.4 完成(POST /api/courses/{id}/slides/upload html 分支已实现 + 7.2 docs/API契约-Phase1.md v1.2 同步)
- [x] **5.1.2 R2 数据契约**：DB 字段类型/长度/约束与数据字典 v0.7 一致
  - **状态**: ✅ Round 1.4 完成(7.1 docs/数据字典.md slide_pages.content_type + html_content 已登记)
- [x] **5.1.3 R3 权限契约**：HTML 上传权限与权限矩阵 v4.1 一致
  - **状态**: ✅ Round 1.4 完成(7.3 docs/权限矩阵.md v4.1 含 HTML 上传权限位)
- [x] **5.1.4 R4 状态机契约**：slide.status 转换与状态机设计.md 一致
  - **状态**: ✅ Round 3a 完成(7.4 docs/状态机设计.md §7.6 HTML 课时内容类型说明)
- [x] **5.1.5 R5 业务规则契约**：HTML 上传业务规则与 docs/开发规范.md v1.5 一致
  - **状态**: ✅ Round 3a 完成(7.6 docs/开发规范.md v1.7 §16 HTML 课时内容规则)

### 5.2 XSS 渗透测试（10 种 payload）

- [x] **5.2.1 `<script>alert(1)</script>`**
  - **状态**: ✅ Round 3b 完成(HtmlCoursewareXssPenetrationTest + html-courseware-xss.spec.js 10/10 PASS)
- [x] **5.2.2 `<img src=x onerror=alert(1)>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.3 `<a href="javascript:alert(1)">click</a>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.4 `<svg onload=alert(1)>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.5 `<iframe src=javascript:alert(1)>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.6 `<body onload=alert(1)>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.7 `<style>body{background:url(javascript:alert(1))}</style>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.8 `<meta http-equiv=refresh content=0;url=javascript:alert(1)>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.9 `<form action=javascript:alert(1)>`**
  - **状态**: ✅ Round 3b 完成
- [x] **5.2.10 `<base href=javascript:alert(1)>`**
  - **状态**: ✅ Round 3b 完成

### 5.3 移动端测试

- [ ] **5.3.1 iOS Safari iframe 渲染**
  - **验收**: HTML 课时在 iPhone Safari 显示正常
  - **状态**: ⏳ backlog(需真机/模拟器测试,本地 Playwright Chrome 无法覆盖 iOS Safari WebKit 行为)
- [ ] **5.3.2 微信内置浏览器 iframe 渲染**
  - **验收**: 微信中打开正常
  - **状态**: ⏳ backlog(需真机+微信内置浏览器测试)
- [ ] **5.3.3 iframe sandbox 移动端生效**
  - **验收**: 移动端 JS 不执行（即使 payload 漏过）
  - **状态**: ⏳ backlog(同 5.3.1,需真机测试;sandbox 属性在所有现代浏览器均生效,理论已覆盖)

---

## 6. 阶段 5 — 灰度发布

- [x] **6.1 配置白名单教师 ID（101, 205）**
  - **验收**: application.yml 同步
  - **状态**: ✅ Round 4 完成(`plugin.interactive.html-content.whitelist-teachers: ${SLIDES_HTML_WHITELIST:101,205}`)
- [x] **6.2 监控指标接入**
  - **验收**: Grafana 看到 `interactive_html_load_total` / `interactive_html_xss_blocked_total`
  - **状态**: ✅ Round 4 完成(SlideServiceImpl 注入 MeterRegistry + 2 个 Counter;Prometheus 通过 /actuator/prometheus 自动暴露)
- [ ] **6.3 白名单教师验证（2-3 位）**
  - **验收**: 上传 HTML → 学生播放 → 自动播放正常
  - **状态**: ⏳ backlog(需真实白名单教师账号验证;目前只有测试账号 teacher1/password123)
- [ ] **6.4 1 周稳定观察**
  - **验收**: HTML 加载成功率 ≥99.5%，XSS 拦截 100%
  - **状态**: ⏳ backlog(需生产部署后观察监控指标)
- [ ] **6.5 白名单扩大到 10 位**
  - **状态**: ⏳ backlog(依赖 6.4 稳定观察结果)
- [ ] **6.6 全量发布**
  - **状态**: ⏳ backlog(依赖 6.5 灰度结果)

---

## 7. 阶段 6 — 文档同步

- [x] **7.1 docs/数据字典.md v0.6 → v0.7**
  - **触发**: 新增 content_type + html_content 字段
  - **验收**: slide_pages 表结构完整
- [x] **7.2 docs/API契约-Phase1.md 新增 HTML 上传端点**
  - **触发**: 新增 POST /api/courses/{id}/slides/upload html 分支
- [x] **7.3 docs/权限矩阵.md v4.0 → v4.1**
  - **触发**: HTML 上传权限位
- [x] **7.4 docs/状态机设计.md 更新**
  - **状态**: ✅ Round 3a 完成(§7.6 HTML 课时内容类型说明 + PPT_RENDERED → HTML_DIRECT 不改变 slide.status)
- [x] **7.5 CHANGELOG.md 记录本次变更**
  - **状态**: ✅ pre-existing
- [x] **7.6 docs/开发规范.md v1.5 → v1.6**
  - **状态**: ✅ Round 3a 完成(v1.7 §16 HTML 课时内容规则 + iframe sandbox 强制规范 + 6 条强制规则 + 7 条安全禁止项)

---

## 8. 阶段 7 — OpenSpec Archive

- [ ] **8.1 跑 `openspec validate html-interactive-extension --type change`**
  - **验收**: PASS
- [ ] **8.2 跑最终回归测试（后端 + 前端 + E2E）**
  - **验收**: 全部测试套件 PASS
- [ ] **8.3 跑 `openspec archive html-interactive-extension`**
  - **验收**: change 已归档
- [ ] **8.4 jackie 签字确认**

---

## 进度追踪

```
阶段 0 (OpenSpec 文档):    1.1✅ 1.2✅ 1.3✅ 1.4⬜ 1.5⬜ 1.6⬜ 1.7⬜
阶段 1 (后端):            2.1-2.7 ⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜
阶段 2 (前端):            3.1-3.5 ⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜
阶段 3 (Hermes):          4.1-4.3 ⬜⬜⬜
阶段 4 (联调 + 安全):     5.1-5.3 ⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜
阶段 5 (灰度):            6.1-6.6 ⬜⬜⬜⬜⬜⬜
阶段 6 (文档):            7.1-7.6 ⬜⬜⬜⬜⬜⬜
阶段 7 (Archive):         8.1-8.4 ⬜⬜⬜⬜
```

**总任务数**: 56
**已完成**: 3 (OpenSpec 文档)
**剩余**: 53

**单任务最长时间**: ≤ 2 小时
**Phase 估算**: 9-14 天（含灰度）

---

*任务拆解: 教授分身 (hermes-professor profile)*
*日期: 2026-07-10*
