# HTML 互动课件扩展 — 专项审查测试方案

> **关联分支**: `feature/html-interactive-extension`
> **关联版本**: V177（数据库）+ 前端/后端对应变更
> **审查日期**: 2026-07-12
> **风险等级**: 高（XSS 攻击面、文件上传、数据库 migration、前端渲染分支）

---

## 测试范围总览

| 层级 | 覆盖点 | 测试方法 | 优先级 |
|------|--------|---------|--------|
| **前端-渲染** | iframe sandbox / srcdoc / 内容类型分支 / 下载功能 / 加载状态 | Playwright + 手动验证 | P0 |
| **前端-上传** | 文件类型校验 / 大小校验 / 并发保护 / 进度反馈 / 错误提示 | Playwright + 手动验证 | P0 |
| **前端-管理** | 徽标显示 / 替换按钮 / 删除 / 重新上传 / 引导步骤文案 | 手动验证 | P1 |
| **后端-安全** | XSS sanitize / iframe sandbox / 权限校验 / 灰度白名单 / 文件魔数 | 单元测试 + 渗透测试 | P0 |
| **后端-逻辑** | PPTX vs HTML 分支 / 数据库写入 / 大文件拒绝 / 空内容拒绝 / 课程不存在 | 单元测试 + 集成测试 | P0 |
| **后端-死代码** | /upload-html 已删除 / uploadHtmlContent 已删除 / containsDisallowedContent 已接入 | grep 验证 | P1 |
| **数据库** | V177 迁移 / V177b 建索引 / V178 回滚 / CHECK 约束 / 现有数据兼容性 | SQL 验证 + 回滚测试 | P0 |
| **数据流** | 前端 FormData → 后端 Controller → Service → DB → 回读 → 前端渲染 | 端到端 Playwright | P0 |
| **配置** | @Value 注入 / application.yml 环境变量 / 灰度白名单 / max-file-size | 单元测试 + 手动验证 | P1 |
| **文档** | 数据字典 / 权限矩阵 / API 契约 / phase11 spec / 字段契约一致性 | 对比验证 | P2 |

---

# 第一部分：前端专项测试（8 张测试表）

## 1.1 SlidePlayer.vue — HTML 课时渲染

| # | 测试场景 | 前置条件 | 操作步骤 | 预期结果 | 优先级 |
|---|---------|---------|---------|---------|--------|
| FP-1 | HTML_DIRECT 渲染 iframe | 课程有 contentType=HTML_DIRECT 的课时 | 导航到该课时 | iframe 渲染，srcdoc=htmlContent，sandbox="" | P0 |
| FP-2 | PPT_RENDERED 渲染图片 | 课程有 contentType=PPT_RENDERED 的课时 | 导航到该课时 | img 渲染，无 iframe | P0 |
| FP-3 | iframe sandbox 完全禁用 | 同 FP-1 | 在 iframe 内尝试调用 parent.postMessage | postMessage 不被处理 | P0 |
| FP-4 | HTML 课时下载按钮 | 同 FP-1 | 点击「下载 HTML」按钮 | 浏览器下载 .html 文件 | P1 |
| FP-5 | HTML 无内容时占位 | contentType=HTML_DIRECT 但 htmlContent 为空/null | 导航到该课时 | 不渲染 iframe，显示错误提示或降级 | P0 |
| FP-6 | HTML→PPT 切换 | 课程混有 HTML 和 PPT 课时 | 在两种课时间切换 | 切换后正确渲染对应的类型 | P0 |
| FP-7 | HTML 加载错误 | 模拟 iframe @error | 设置 mock 错误 | 显示「HTML 课件加载失败，请刷新重试」 | P2 |
| FP-8 | HTML 课时的音频播放 | HTML 课时有 narrationAudioUrl | 进入自动播放模式 | 音频播放、进度更新、结束后自动跳转 | P1 |
| FP-9 | HTML 课时无音频 | HTML 课时 narrationAudioUrl 为 null | 自动播放模式 | playAudio 静默跳过（不报错） | P2 |
| FP-10 | iframe :title 属性 | HTML 课时加载 | 检查 DOM | iframe 元素有 title="第N页课件内容" | P2 |
| FP-11 | 多 HTML 页面切换性能 | 10+ 个 HTML 课时 | 快速切换（1 秒/次） | 无内存泄漏，DOM 未累积旧 iframe | P2 |

## 1.2 SlideManage.vue — 课件管理

| # | 测试场景 | 前置条件 | 操作步骤 | 预期结果 | 优先级 |
|---|---------|---------|---------|---------|--------|
| FM-1 | HTML 文件上传 | 选择 .html 文件 | 点击上传/拖拽 | 上传成功，显示 HTML 徽标 | P0 |
| FM-2 | PPTX 文件上传（原有） | 选择 .pptx 文件 | 点击上传 | 上传成功，显示 PPT 渲染状态 | P0 |
| FM-3 | 前端 5MB 校验 | HTML 文件 > 5MB | 选择上传 | 提示「HTML 文件不能超过 5MB」，不上传 | P0 |
| FM-4 | 前端 50MB 校验 | PPTX 文件 > 50MB | 选择上传 | 提示「文件超过 50MB 限制」，不上传 | P0 |
| FM-5 | 不支持的文件类型 | 选择 .exe/.js/.docx | 选择上传 | 提示「仅支持 .pptx / .html / .htm 格式」 | P1 |
| FM-6 | 并发上传保护 | 正在上传中 | 再次选择文件 | 提示「已有课件正在上传」，不覆盖 | P1 |
| FM-7 | 替换按钮接受 HTML | 已有课件 | 在替换窗口选择 .html 文件 | 上传成功，旧课件被替换 | P1 |
| FM-8 | 重新上传按钮接受 HTML | 课件渲染失败 | 在错误卡片点重新上传（.html） | 上传成功 | P1 |
| FM-9 | 引导步骤文案检查 | 无课件 | 查看 5 步引导 | 第 1 步显示「上传课件（.pptx / .html）」 | P2 |
| FM-10 | HTML 徽标显示 | 有 HTML_DIRECT 页面 | 查看页面列表 | 页面前有 HTML 徽标 | P1 |
| FM-11 | PPT_RENDERED 无徽标 | 有 PPT_RENDERED 页面 | 查看页面列表 | 页面无 HTML 徽标 | P1 |
| FM-12 | HTML 上传后总学时 | HTML 课件 1 页 | 查看总学时 | 显示总学时 = 0（HTML 不计学时） | P2 |
| FM-13 | HTM 扩展名上传 | 选择 .htm 文件 | 点击上传 | 同 .html 上传成功 | P2 |

## 1.3 SlideUploadZone.vue — 上传组件

| # | 测试场景 | 前置条件 | 操作步骤 | 预期结果 | 优先级 |
|---|---------|---------|---------|---------|--------|
| FZ-1 | accept 属性 | 无 | 检查文件选择对话框 | 默认过滤显示 .pptx/.html/.htm | P1 |
| FZ-2 | 拖拽上传 HTML | 文件管理器拖拽 .html | 拖拽到上传区域 | 触发 handleUpload | P1 |
| FZ-3 | 拖拽上传非支持类型 | 拖拽 .txt 文件 | 拖拽 | handleUpload 返回 false | P2 |

---

# 第二部分：后端专项测试（15 张测试表）

## 2.1 SlideController — 文件上传

| # | 测试场景 | 接口 | 请求 | 预期响应 | 优先级 |
|---|---------|------|------|---------|--------|
| BC-1 | PPTX 正常上传 | POST /upload | multipart file=test.pptx, 50MB 以下 | 200, slideId, status=0, message="上传成功" | P0 |
| BC-2 | HTML 正常上传 | POST /upload | multipart file=lesson.html, 5MB 以下 | 200, slideId, totalPages=1, status=2 | P0 |
| BC-3 | 文件为空 | POST /upload | multipart file 为 0 字节 | 400, BAD_REQUEST_PARAM | P0 |
| BC-4 | PPTX 超 50MB | POST /upload | multipart file > 50MB | 400, BAD_REQUEST_PARAM | P0 |
| BC-5 | HTML 超 5MB | POST /upload | multipart file=lesson.html > 5MB | 413, HTML_TOO_LARGE (16010) | P0 |
| BC-6 | HTML 全被消毒 | POST /upload | file=lesson.html 内容仅 `<script>` | 400, HTML_SANITIZE_REMOVED_ALL (16011) | P0 |
| BC-7 | 课程不存在 | POST /upload | 传入不存在的 courseId | 404, COURSE_NOT_FOUND | P0 |
| BC-8 | 无权限（非教师/管理员）| POST /upload | 用 STUDENT token 上传 | 403, NO_PERMISSION | P0 |
| BC-9 | 非白名单教师上传 HTML | POST /upload | whitelist-teachers 配置为空 | 如果配置非空且当前 TEACHER 不在白名单，403 | P1 |
| BC-10 | 管理员绕过白名单 | POST /upload | ADMIN token, whitelist 有值 | 202 上传成功（ADMIN 不受限） | P1 |
| BC-11 | PPTX 魔数校验 | POST /upload | 上传 .pdf 改名 .pptx | 400, PPT_FORMAT_INVALID | P0 |
| BC-12 | 覆盖已有课件（PPTX） | POST /upload | 原课件为 PPTX，新传 PPTX | 备份旧文件 → 新课件 | P0 |
| BC-13 | 覆盖已有课件（HTML） | POST /upload | 原课件为 HTML，新传 HTML | 旧被替换，新 contentType=HTML_DIRECT | P1 |

## 2.2 SlideService — 业务逻辑

| # | 测试场景 | 方法 | 输入 | 预期结果 | 优先级 |
|---|---------|------|------|---------|--------|
| BS-1 | uploadHtmlFile 正常 | uploadHtmlFile | 合法 HTML 文件 | 入库 contentType=HTML_DIRECT, htmlContent 已消毒 | P0 |
| BS-2 | uploadHtmlFile 超 5MB | uploadHtmlFile | > 5MB | 抛 HTML_TOO_LARGE | P0 |
| BS-3 | uploadHtmlFile 课程不存在 | uploadHtmlFile | 课程 ID 不存在 | 抛 COURSE_NOT_FOUND | P0 |
| BS-4 | uploadHtmlFile 无权限 | uploadHtmlFile | 非课程所属教师 | 抛 NO_PERMISSION | P0 |
| BS-5 | uploadHtmlFile 读取失败 | uploadHtmlFile | 文件损坏 | 抛 HTML_INVALID | P1 |
| BS-6 | tryConvertPptxToHtml 正常 | tryConvertPptxToHtml | 合法 PPTX | 写入第一页 htmlContent, 不抛异常 | P1 |
| BS-7 | tryConvertPptxToHtml 失败 | tryConvertPptxToHtml | 损坏 PPTX | 写日志 warn, 不抛异常 | P2 |
| BS-8 | tryConvertPptxToHtml 全部被消毒 | tryConvertPptxToHtml | PPTX 内容仅脚本 | 日志 warn, 不写入 htmlContent | P2 |

## 2.3 HtmlSanitizer — XSS 防护

| # | 测试场景 | 输入 | 预期消毒结果 | 优先级 |
|---|---------|------|------------|--------|
| HX-1 | script 标签 | `<script>alert(1)</script>` | 被完全移除 | P0 |
| HX-2 | 内联事件 | `<img src=x onerror=alert(1)>` | onerror 被移除 | P0 |
| HX-3 | javascript: URL | `<a href="javascript:alert(1)">x</a>` | href 值被清除 | P0 |
| HX-4 | SVG | `<svg/onload=alert(1)>` | svg 标签被移除 | P0 |
| HX-5 | iframe | `<iframe src=javascript:alert(1)>` | iframe 被移除 | P0 |
| HX-6 | form | `<form><input name=x></form>` | form 被移除 | P0 |
| HX-7 | style | `<style>@import url(x);</style>` | style 被移除 | P0 |
| HX-8 | data: URI | `<img src="data:image/svg+xml,...">` | data: src 被清理（只允许 http/https） | P0 |
| HX-9 | meta | `<meta http-equiv="refresh" content="0;url=javascript:...">` | meta 被移除 | P0 |
| HX-10 | base | `<base href="javascript:">` | base 被移除 | P0 |
| HX-11 | embed | `<embed src=javascript:alert(1)>` | embed 被移除 | P0 |
| HX-12 | HTML entity 编码绕过 | `&#x3C;script&#x3E;alert(1)&#x3C;/script&#x3E;` | 解码后被移除 | P0 |
| HX-13 | 正常教学 HTML | `<p>Hello</p><ul><li>Item</li></ul>` | 保留不变 | P0 |
| HX-14 | containsDisallowedContent 快速拒绝 | `<script>...</script>` | 返回 true，触发 sanitize 完整流程 | P1 |
| HX-15 | sanitize(null) | null | 返回 "" | P1 |
| HX-16 | sanitize("") | "" | 返回 "" | P1 |

---

# 第三部分：数据库专项测试（5 张测试表）

## 3.1 V177 迁移

| # | 测试场景 | 操作 | 预期结果 | 优先级 |
|---|---------|------|---------|--------|
| DB-1 | 空表执行 V177 | 在无数据表运行 V177 | content_type 列 'PPT_RENDERED', html_content NULL | P0 |
| DB-2 | 有数据表执行 V177 | 在有 1000 行 slide_pages 的表运行 | 现有行 content_type='PPT_RENDERED', CHECK 通过 | P0 |
| DB-3 | CHECK 约束验证 | INSERT content_type='INVALID' | 拒绝，违反 CHECK 约束 | P0 |
| DB-4 | CHECK 约束验证 | INSERT content_type='HTML_DIRECT' | 允许 | P0 |
| DB-5 | html_content 可空 | INSERT 不设 html_content | html_content=null | P0 |

## 3.2 V177b 建索引

| # | 测试场景 | 操作 | 预期结果 | 优先级 |
|---|---------|------|---------|--------|
| DB-6 | 首次建索引 | 在 V177 后运行 V177b | 索引创建成功 | P0 |
| DB-7 | 幂等性 | 再次运行 V177b | IF NOT EXISTS 跳过，不报错 | P1 |
| DB-8 | CONCURRENTLY 非阻塞 | 建索引时查询 | 查询不被阻塞 | P1 |

## 3.3 V178 回滚

| # | 测试场景 | 操作 | 预期结果 | 优先级 |
|---|---------|------|---------|--------|
| DB-9 | 完整回滚 | 执行 V178 | content_type, html_content 列被删除 | P0 |
| DB-10 | 重复回滚 | 再次执行 V178 | DROP IF EXISTS, 不报错 | P1 |
| DB-11 | 回滚后重建 | V178 → V177 → V177b | 重建成功 | P1 |

---

# 第四部分：数据流专项测试（6 张测试表）

## 4.1 端到端路径

| # | 测试场景 | 链路 | 操作 | 预期结果 | 优先级 |
|---|---------|------|------|---------|--------|
| DF-1 | HTML 上传→入库→读取 | F→B→DB→B→F | 上传 HTML → 刷新页面 → 查看 | 上传后列表有 HTML 徽标，点开 iframe 显示内容 | P0 |
| DF-2 | PPTX 上传→渲染→显示 | F→B→DB→Render→DB→F | 上传 PPTX → 等待渲染 → 查看 | 渲染完成后显示 PNG，无 iframe | P0 |
| DF-3 | HTML 替换 PPTX | F→B→DB→F | 已有 PPTX 课件，上传 HTML 替换 | 老课件备份，新课件 contentType=HTML_DIRECT | P1 |
| DF-4 | PPTX 替换 HTML | F→B→DB→Render→F | 已有 HTML 课件，上传 PPTX 替换 | 老 HTML 备份，新 PPTX 渲染 | P1 |
| DF-5 | HTML 删除 | F→B→DB | 删除 HTML 课件 | slide_pages 级联删除，存储文件清理 | P1 |
| DF-6 | 多章节 HTML | F→B→DB→B→F | 为不同 chapterId 上传 HTML | 各章节独立显示对应 HTML | P2 |

## 4.2 前后端参数一致性

| # | 检查项 | 前端值 | 后端预期 | 优先级 |
|---|--------|--------|---------|--------|
| DF-7 | HTML 文件 multipart 字段名 | fd.append('file', file) | @RequestParam("file") MultipartFile file | P0 |
| DF-8 | chapterId 格式 | fd.append('chapterId', chapterId) | @RequestParam(required=false) Long chapterId | P0 |
| DF-9 | 响应格式 | { code: 200, data: { slideId, totalPages, status } } | R<SlideUploadResponse> | P0 |
| DF-10 | 错误格式 | Axios interceptor 统一处理 | { code: 错误码, message: "..." } | P0 |

---

# 第五部分：配置与文档校验（3 张测试表）

## 5.1 配置注入

| # | 检查项 | 配置来源 | 验证 | 优先级 |
|---|--------|---------|------|--------|
| CF-1 | maxHtmlSize | ${plugin.interactive.html-content.max-file-size:5242880} | 环境变量 SLIDES_HTML_MAX_SIZE 覆盖 | P1 |
| CF-2 | whitelist-teachers | ${plugin.interactive.html-content.whitelist-teachers:} | 环境变量 SLIDES_HTML_WHITELIST 覆盖 | P1 |
| CF-3 | storage-path | ${plugin.interactive.slides.storage-path:/data/slides} | 环境变量 SLIDES_STORAGE_PATH 覆盖 | P1 |

## 5.2 文档一致性

| # | 检查项 | 实际值 | 文档值 | 匹配 |
|---|--------|--------|--------|------|
| DC-1 | ErrorCode 16009 | HTML_INVALID | 数据字典/API契约 | ✅ |
| DC-2 | ErrorCode 16010 | HTML_TOO_LARGE | 数据字典/API契约 | ✅ |
| DC-3 | ErrorCode 16011 | HTML_SANITIZE_REMOVED_ALL | 数据字典/API契约 | ✅ |
| DC-4 | slide_pages.content_type | VARCHAR(20), CHECK('PPT_RENDERED','HTML_DIRECT') | 数据字典/phase11 spec | ✅ |
| DC-5 | slide_pages.html_content | TEXT, nullable | 数据字典/phase11 spec | ✅ |
| DC-6 | POST /upload 权限 | hasAnyRole(TEACHER,ADMIN) + whitelist | 权限矩阵 §1.4.1 | ✅ |
| DC-7 | V178 回滚脚本 | 存在 | 数据字典/FLYWAY_DEPLOY.md | ✅ |

---

# 第六部分：安全渗透测试（5 张测试表）

## 6.1 XSS 攻击面

| # | 攻击向量 | 攻击描述 | 预期防御层 | 优先级 |
|---|---------|---------|----------|--------|
| S-1 | 存储型 XSS | 上传 HTML 含 `<script>`，其他学生打开 | Jsoup sanitize → iframe sandbox 双层 | P0 |
| S-2 | 反射型 XSS | URL 参数直接渲染 | 无此路径 | — |
| S-3 | CSS injection | 上传 HTML 含 inline style 或 `<style>` | style 标签/属性全部被禁止 | P0 |
| S-4 | data: URI 注入 | `<img src="data:image/svg+xml,...">` | data: 协议被移出白名单 | P0 |
| S-5 | tabnabbing | `<a target="_blank" rel="opener">` | target 属性被从白名单移除 | P1 |

## 6.2 文件上传攻击

| # | 攻击向量 | 攻击描述 | 预期防御 | 优先级 |
|---|---------|---------|---------|--------|
| S-6 | 大文件 OOM | 上传 10GB 文件 | Spring multipart 限制 60MB → Controller 50MB/5MB 拒绝 | P0 |
| S-7 | Zip bomb | 上传 PPTX 内含极度压缩内容 | validateZipBomb 在校验 zip 条目数 | P0 |
| S-8 | MIME 绕过 | 上传 .html 但 Content-Type: application/octet-stream | 按扩展名判断，不依赖 MIME | P1 |
| S-9 | 路径遍历 | 文件名 "../../etc/passwd" | Paths.get().getFileName() 提取纯文件名 | P0 |

## 6.3 访问控制

| # | 攻击向量 | 攻击描述 | 预期防御 | 优先级 |
|---|---------|---------|---------|--------|
| S-10 | 越权查看 | STUDENT 查看未选课课程的 HTML 课件 | verifyAccess → 检查 enrollment | P0 |
| S-11 | 越权上传 | 教师 A 上传到教师 B 的课程 | isOwnerOrAdmin → 检查 teacherId | P0 |
| S-12 | 越权删除 | 非本课程教师删除课件 | isOwnerOrAdmin → 同上传 | P0 |
| S-13 | 灰度绕过 | 非白名单教师通过直接 API 上传 HTML | whitelist TEACHER 检查 | P1 |

## 6.4 配置安全

| # | 检查项 | 预期 | 优先级 |
|---|--------|------|--------|
| S-14 | actuator/health 暴露 | 不应暴露课件内容 | P2 |
| S-15 | 日志中的 HTML 内容 | 日志记录长度而非内容（已实现） | P2 |
| S-16 | 文件路径权限 | storagePath 不可被 Web 直接访问 | P1 |

---

# 第七部分：回归测试（2 张测试表）

## 7.1 PPTX 原有功能回归

| # | 测试场景 | 操作 | 预期结果 | 优先级 |
|---|---------|------|---------|--------|
| R-1 | PPTX 上传 | 上传 10MB PPTX | 渲染成功，每页对应 PNG | P0 |
| R-2 | PPTX 替换 | 上传新 PPTX | 备份旧文件，重新渲染 | P0 |
| R-3 | PPTX 删除 | 删除课件 | slide_pages 级联删除，文件清理 | P0 |
| R-4 | PPTX 批量生成讲述稿 | 点「全部生成」 | APi 调用成功 | P1 |
| R-5 | PPTX 批量生成音频 | 点「全部生成」 | TTS 调用成功 | P1 |

## 7.2 非 HTML 相关功能回归

| # | 测试场景 | 操作 | 预期结果 | 优先级 |
|---|---------|------|---------|--------|
| R-6 | 用户创建（gender 空） | 不选性别创建用户 | 成功（nullIfBlank 修复） | P0 |
| R-7 | 微专业申请删除 | 在 REJECTED 状态删除 | 成功 | P0 |
| R-8 | 微专业日期选择 | 选年月日 | 格式 yyyy.M.D | P1 |
| R-9 | 申请表富文本字数 | 输入超限文字 | 显示红/橙色警告 | P2 |

---

# 第八部分：执行计划

## 执行优先级

```
第一优先级（P0 · 阻塞合并 · 必须全部通过）
├── BC-1 至 BC-12: 文件上传（前端+后端全链路）
├── HX-1 至 HX-13: XSS 防护（Jsoup 消毒 + iframe sandbox）
├── DB-1 至 DB-5: 数据库迁移（V177 + V178）
├── DF-1, DF-2: 端到端链路（HTML 和 PPTX 两条路径）
└── S-1 至 S-12: 安全渗透（XSS/文件上传/越权）

第二优先级（P1 · 建议完成）
├── FM-1 至 FM-12: 课件管理 UI
├── BC-10, BC-13: 管理员绕过白名单、覆盖
├── CF-1 至 CF-3: 配置注入验证
├── DC-1 至 DC-7: 文档一致性
├── S-13: 灰度绕过
└── R-1 至 R-5: PPTX 回归

第三优先级（P2 · 可选）
├── FP-7, FP-9, FP-11: 错误/性能/辅助功能
├── FZ-3: 扩展名拖拽
├── BS-7, BS-8: 非关键路径容错
├── DB-8, DB-11: 幂等性、回滚后重建
└── R-7, R-8: 回归边界
```

## 测试工具

| 层 | 工具 | 用法 |
|----|------|------|
| 后端单元测试 | JUnit 5 + Mockito + MockMultipartFile | SlideServiceTest 已有 12 个 |
| 前端单元测试 | Vitest + @vue/test-utils + happy-dom | 4 个测试文件 14 个用例 |
| 端到端测试 | Playwright (chromium) | 模拟教师上传 → 学生观看 |
| 数据库测试 | PostgreSQL + Flyway migrate/repair | 本地执行 V177 → V178 |
| 安全测试 | Jsoup 测试 + 手动 XSS payload | HtmlSanitizer 16 个用例 |
| 配置测试 | Spring Boot Test + @TestPropertySource | @Value 注入验证 |

## 阻塞条件

以下任一条件未通过则 **阻塞合并**：

1. 任意 P0 测试失败
2. `CREATE INDEX CONCURRENTLY` 在事务内执行
3. V177 迁移在已有数据的表上导致数据丢失
4. 任意 XSS payload 通过 sanitizer + sandbox 双层防御
5. 前端 API 调用（uploadHtml）返回不可预期的 HTTP 状态码
