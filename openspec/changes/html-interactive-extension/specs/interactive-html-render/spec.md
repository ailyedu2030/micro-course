# interactive-html-render (新增)

## ADDED Requirements

### Requirement: HTML 课时渲染

The system MUST implement: HTML 课时渲染.


SlidePlayer.vue 检测到 `contentType='HTML_DIRECT'` 时,渲染 `<iframe sandbox srcdoc>` 容器。

#### Scenario: HTML 课时自动切换 iframe

- **WHEN**: 当前页 slide_page.content_type === 'HTML_DIRECT'
- **THEN**: SlidePlayer.vue 渲染 `<iframe sandbox="allow-same-origin" srcdoc="...">` 而非 `<img>`
- **AND**: iframe 100% 宽高,视觉与图片一致

#### Scenario: HTML 课时保持自动播放

- **WHEN**: HTML 课时在 autoMode=true 时
- **THEN**: 音频正常播放
- **AND**: 音频 ended 触发 goTo(next)
- **AND**: iframe 内容保持不变 (只是翻页,HTML 不会被销毁)

#### Scenario: HTML 课时倍速

- **WHEN**: 教师切换倍速 0.75x / 1x / 1.25x / 1.5x / 2x
- **THEN**: HTML 课时的音频倍速生效

#### Scenario: HTML 课时全屏

- **WHEN**: 教师按 F 或点击全屏按钮
- **THEN**: iframe 进入全屏

#### Scenario: HTML 课时讲述稿

- **WHEN**: HTML 课时在播放中
- **THEN**: 右侧讲述稿面板显示当前页 narration_script

### Requirement: iframe sandbox 安全

The system MUST implement: iframe sandbox 安全.


HTML 课时 iframe 强制 `sandbox="allow-same-origin"`,禁用 script/form/top-navigation/popup。

#### Scenario: 阻止 script 执行

- **WHEN**: HTML 含 `<script>alert(1)</script>` (即使 sanitize 漏过)
- **THEN**: iframe sandbox 阻止 script 执行

#### Scenario: 阻止 form 提交

- **WHEN**: HTML 含 `<form action="...">`
- **THEN**: iframe sandbox 阻止 form 提交

#### Scenario: 阻止 top-navigation

- **WHEN**: HTML 含 `<a target="_top" href="phishing.com">`
- **THEN**: iframe sandbox 阻止顶层跳转

### Requirement: 平台 CSP 策略

The system MUST implement: 平台 CSP 策略.


平台 Content Security Policy header 包含 `sandbox allow-same-origin` 相关策略。

#### Scenario: CSP 配置生效

- **WHEN**: 学生加载任意页面
- **THEN**: HTTP response header 包含 `Content-Security-Policy` 指令
- **AND**: 包含 `frame-src 'self'` 等沙箱策略

### Requirement: HTML 课时权限

The system MUST implement: HTML 课时权限.


HTML 上传与 PPT 上传共享同一权限位 (plugin_grants)。

#### Scenario: 授权教师上传 HTML

- **WHEN**: 教师在 plugin_grants 表有 'interactive' 授权
- **THEN**: 教师可上传 .html 与 .pptx

#### Scenario: 未授权教师上传

- **WHEN**: 教师无 plugin_grants 授权
- **THEN**: 上传 API 拒绝,code 16002 (PLUGIN_NO_GRANT)

### Requirement: HTML 课时灰度发布

The system MUST implement: HTML 课时灰度发布.


HTML 装载点先对白名单教师开启,稳定后全量。

#### Scenario: 白名单教师可上传

- **WHEN**: 教师 ID 在 `plugin.interactive.html-content.whitelist-teachers` 配置中
- **THEN**: 教师看到 HTML 上传入口

#### Scenario: 非白名单教师不可见

- **WHEN**: 教师 ID 不在白名单
- **THEN**: 教师 UI 不显示 HTML 上传入口 (但 PPT 仍可用)

#### Scenario: 监控指标

- **WHEN**: HTML 课时被加载或 XSS 被拦截
- **THEN**: 监控上报 Grafana,指标 `interactive_html_load_total` / `interactive_html_xss_blocked_total`
