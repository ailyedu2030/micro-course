# interactive-ppt-to-html (新增,可选)

## ADDED Requirements

### Requirement: PPT 自动转 HTML

The system MUST implement: PPT 自动转 HTML.


教师上传 `.pptx` 时,如启用 `autoConvertHtml=true`,平台**额外**尝试生成语义 HTML 副本,失败 fallback 到 PNG。

#### Scenario: PPT 上传触发自动转 HTML

- **WHEN**: 教师上传 .pptx + 勾选 "自动转 HTML" 选项
- **THEN**: 平台除生成 PNG 外,异步调用 PptxToHtmlConverter
- **AND**: HTML 副本生成成功 → slide_pages.html_content 填充,sample 状态 `HTML_GENERATED`
- **AND**: HTML 生成失败 → 不报错,只 log warning,PNG 仍是主显示

#### Scenario: PPT 上传未启用自动转 HTML

- **WHEN**: 教师上传 .pptx + 未勾选 "自动转 HTML"
- **THEN**: 平台仅生成 PNG,html_content 为 NULL
- **AND**: 行为与 phase11 完全一致 (向后兼容)

#### Scenario: HTML 转换失败回退

- **WHEN**: PptxToHtmlConverter 解析 PPT 失败 (字体缺失 / 动画复杂)
- **THEN**: log.warn 输出失败原因
- **AND**: slide_pages.content_type 保持 `PPT_RENDERED`
- **AND**: PNG 正常显示,前端不感知 HTML 转换失败

### Requirement: PptxToHtmlConverter 工具

The system MUST implement: PptxToHtmlConverter 工具.


Java 端 Apache POI + Jsoup 实现的 PPT→HTML 转换器,**不依赖 Python 脚本**。

#### Scenario: 基本文本转换

- **WHEN**: PPT 文本框含 "AI 时代商科生"
- **THEN**: HTML 包含 `<p>AI 时代商科生</p>`

#### Scenario: 字号保留

- **WHEN**: PPT 文本框字号 36pt
- **THEN**: HTML 包含 `<p style="font-size: 36pt;">...</p>`

#### Scenario: 颜色保留

- **WHEN**: PPT 文本框颜色 #1E3A5F
- **THEN**: HTML 包含 `<p style="color: #1E3A5F;">...</p>`

#### Scenario: 复杂动画丢失

- **WHEN**: PPT 含 `<p:timing>` 切换动画
- **THEN**: HTML 不包含动画 (Java 端不解析动画轨)
- **AND**: log.debug 记录"动画丢弃"

#### Scenario: 嵌入视频丢失

- **WHEN**: PPT 含嵌入 MP4
- **THEN**: HTML 不包含视频
- **AND**: 提示"嵌入媒体未转换"
