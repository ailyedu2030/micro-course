# interactive-html-content (新增)

## ADDED Requirements

### Requirement: HTML 课时上传

The system MUST implement: HTML 课时上传.


教师可直接上传 `.html`/`.htm` 文件作为课时内容,与现有 `.pptx` 上传并行。

#### Scenario: 正常上传 HTML

- **WHEN**: 教师选择 5MB 以内的 `.html` 文件,点击上传
- **THEN**: slide_pages 表新增 `content_type='HTML_DIRECT'` + `html_content` 字段填充 sanitize 后的 HTML
- **AND**: CourseSlide.status 立即变为 `READY` (2)

#### Scenario: HTML 文件超 5MB

- **WHEN**: 教师选择 6MB 的 `.html` 文件
- **THEN**: 系统抛 `code 16010` (HTML_TOO_LARGE)
- **AND**: HTTP status 413
- **AND**: 提示"HTML 文件超过 5MB 限制"

#### Scenario: HTML 含恶意脚本

- **WHEN**: 教师上传含 `<script>alert(1)</script>` 的 HTML
- **THEN**: 后端 OWASP Sanitizer 移除 `<script>` 标签
- **AND**: 前端 iframe sandbox 二次防护,即使漏过也不执行
- **AND**: HTML 安全入库

### Requirement: HTML 课时类型字段

The system MUST implement: HTML 课时类型字段.


`slide_pages` 表新增 `content_type` 字段,区分 PPT_RENDERED 与 HTML_DIRECT。

#### Scenario: 老数据迁移

- **WHEN**: Flyway V177 迁移执行
- **THEN**: 所有现有 slide_pages 行的 `content_type` 默认 `PPT_RENDERED`
- **AND**: html_content 字段默认为 NULL

#### Scenario: 新上传 HTML

- **WHEN**: 教师上传 .html
- **THEN**: slide_pages 新行 `content_type='HTML_DIRECT'`, `html_content` 填 HTML 文本
- **AND**: image_url 字段为 NULL (HTML 课时不需要图片)

### Requirement: HTML 内容安全

The system MUST implement: HTML 内容安全.


所有上传的 HTML 必须经过 OWASP Java HTML Sanitizer 处理,防止 XSS。

#### Scenario: sanitize 移除 script

- **WHEN**: HTML 含 `<script>alert(1)</script>`
- **THEN**: sanitize 后 `<script>` 标签被剥离

#### Scenario: sanitize 移除 onerror

- **WHEN**: HTML 含 `<img src=x onerror=alert(1)>`
- **THEN**: sanitize 后 onerror 属性被移除

#### Scenario: sanitize 移除 javascript: URL

- **WHEN**: HTML 含 `<a href="javascript:alert(1)">`
- **THEN**: sanitize 后 href 被替换为 `#`

### Requirement: HTML 课时大小限制

The system MUST implement: HTML 课时大小限制.


HTML 课时文件 ≤5MB。

#### Scenario: 4MB HTML 通过

- **WHEN**: 教师上传 4MB HTML
- **THEN**: 上传成功,DB 写入

#### Scenario: 6MB HTML 拒绝

- **WHEN**: 教师上传 6MB HTML
- **THEN**: code 16010

### Requirement: HTML 课时讲述稿

The system MUST implement: HTML 课时讲述稿.


HTML 课时支持与 PNG 课时共享的 `narration_script` + `narration_audio_url` 字段。

#### Scenario: HTML 课时生成讲述稿

- **WHEN**: 教师为 HTML 课时点击"AI 生成讲述稿"
- **THEN**: DeepSeek 调用成功,narration_script 填充
- **AND**: narration_status='GENERATED'

#### Scenario: HTML 课时 TTS 音频

- **WHEN**: 教师为 HTML 课时点击"TTS 生成音频"
- **THEN**: MiniMax 调用成功,narration_audio_url 填充
- **AND**: narration_status='AUDIO_READY'
