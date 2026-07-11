# interactive-courseware (Modified)

## MODIFIED Requirements

### Requirement: 互动课时通用能力

The system MUST implement: 互动课时通用能力.


原能力"互动课时支持 PPT 上传、POI 渲染、音频同步、自动播放"。本次修改:**新增** HTML 直接上传能力,与 PPT 路径并行。

#### Scenario: 教师上传 PPT

- **WHEN**: 教师上传 `.pptx` 文件
- **THEN**: slide_pages.content_type = 'PPT_RENDERED'
- **AND**: image_url = "/api/.../image"
- **AND**: 行为与 phase11 一致 (向后兼容)

#### Scenario: 教师上传 HTML

- **WHEN**: 教师上传 `.html` 文件 (本次新增)
- **THEN**: slide_pages.content_type = 'HTML_DIRECT'
- **AND**: image_url = NULL
- **AND**: html_content = sanitize 后的 HTML
- **AND**: SlidePlayer.vue 渲染 iframe sandbox srcdoc

#### Scenario: 课时类型混合

- **WHEN**: 同一课程包含 PPT 课时和 HTML 课时
- **THEN**: SlidePlayer 逐页检测 contentType,分别渲染 img / iframe
- **AND**: 自动播放/倍速/全屏/讲述稿/进度对两种类型都生效
