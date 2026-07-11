# Design: HTML 互动课件扩展 (技术设计)

> **OpenSpec Change**: `html-interactive-extension`
> **Schema**: spec-driven
> **依赖**: proposal.md + specs/{interactive-html-content,interactive-html-render,interactive-ppt-to-html,interactive-courseware}/spec.md

---

## Context

**当前状态**:
- 互动课插件（Phase 11）已实现并稳定运行
- Apache POI 服务端渲染 PPT → PNG，1920px 宽位图
- SlidePlayer.vue 支持自动播放/倍速/全屏/讲述稿/进度
- 平台自动播放逻辑是通用的（`<audio>` + ended 事件），与内容格式无关
- 教授分身已产出 HTML demo（`_research-2026-07-10/`），验证 HTML 视觉远超 PNG

**约束**:
- 必须遵守 `.claude/skills/microcourse/SKILL.md` 25 条禁止项
- 数据库变更必须 Flyway 迁移（V177+ 假设 V176 是最新）
- 不允许 Lombok，必须手写 getter/setter + 构造器注入
- 分层: Controller → Service → Repository
- 权限: JWT + @PreAuthorize + plugin_grants
- 不动学校服务器 100.74.122.13（生产铁律）
- 不动生产域名 microcourse.ailyedu.cn
- HTML 安全：OWASP Java HTML Sanitizer 必装

**干系人**:
- 需求方: jackie（教授分身用户）
- 设计方: 教授分身（hermes-professor，MiniMax-M3）
- 执行方: 平台开发智能体
- 验收方: jackie

---

## Goals / Non-Goals

### Goals

1. **HTML 课时能上传并播放**：教师上传 `.html` → 学生浏览器看 HTML + 听音频 + 自动翻页
2. **PPT 自动转 HTML（尽力而为）**：上传 PPT 时除生成 PNG 外，**额外尝试**生成语义 HTML 副本，失败 fallback 到 PNG
3. **向后兼容**：现有 158 课时不受影响，老课继续走 PNG 路径
4. **HTML 安全**：强制 sandbox + 后端 sanitize + CSP header，**XSS 0 容忍**
5. **灰度发布**：先白名单教师（与 v1.20.0 灰度策略一致），稳定后全量
6. **复用现有自动播放能力**：HTML 课时享受 autoMode + 倍速 + 全屏 + 讲述稿 + 进度

### Non-Goals

- ❌ **PPT→HTML 完美无损转换**（字体/动画/可访问性很难，工程量大，性价比低）
- ❌ **HTML 互动组件**（选择题/拖拽/表单）——本次只做展示型 HTML
- ❌ **Web Component 嵌入**——本次只做 HTML 片段
- ❌ **实时协作编辑**
- ❌ **HTML→PDF 导出**（已有方案，但优先级低）
- ❌ **学校服务器 100.74.122.13**（生产本次不动）

---

## Architecture

### 数据流

```
教师上传 .html/.pptx
        ↓
SlideController.upload(courseId, file, contentType)
        ↓
    ┌────────────────┬─────────────────┐
    │ contentType=   │ contentType=    │
    │ pptx           │ html            │
    └────────────────┴─────────────────┘
        ↓                      ↓
SlideRenderService.renderAsync()    sanitize + 直接存
(Apache POI → PNG)                  (OWASP Sanitizer)
        ↓                      ↓
    slide_pages 表：
    - content_type = "PPT_RENDERED"
    - image_url = "/api/.../image"
    - html_content = NULL
    
                            ↓
                    slide_pages 表：
                    - content_type = "HTML_DIRECT"
                    - image_url = NULL
                    - html_content = "<html>...</html>"

学生播放
        ↓
SlidePlayer.vue 加载 page
        ↓
    ┌────────────────────────────────┐
    │ if (contentType === 'HTML_DIRECT') │
    │   <iframe sandbox srcdoc="...">   │
    │ else                                │
    │   <img src="imageUrl">              │
    └────────────────────────────────┘
        ↓
现有自动播放逻辑（音频 ended → goTo(next)）全部复用
```

### 数据库变更

```sql
-- V177__slide_pages_content_type.sql
ALTER TABLE slide_pages
    ADD COLUMN content_type VARCHAR(20) NOT NULL DEFAULT 'PPT_RENDERED',
    ADD COLUMN html_content TEXT;

-- 老数据 content_type 默认 'PPT_RENDERED'，向后兼容
-- 索引
CREATE INDEX idx_slide_pages_content_type ON slide_pages(content_type);

-- CHECK 约束
ALTER TABLE slide_pages
    ADD CONSTRAINT chk_content_type CHECK (content_type IN ('PPT_RENDERED', 'HTML_DIRECT'));
```

### SlidePage 实体变更

```java
// 现有字段保持不变
private String contentType;     // "PPT_RENDERED" | "HTML_DIRECT"，default "PPT_RENDERED"
private String htmlContent;     // HTML 文本，HTML_DIRECT 时必填
```

### SlideController 变更

```java
@PostMapping("/upload")
public Result<SlideUploadResponse> upload(
    @PathVariable Long courseId,
    @RequestParam MultipartFile file,
    @RequestParam(defaultValue = "false") Boolean autoConvertHtml,
    @RequestParam(required = false) Long chapterId) {

    // 1. 校验 plugin_grants（与现有逻辑一致）
    // 2. 校验文件类型
    String fileName = file.getOriginalFilename().toLowerCase();
    if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
        // HTML 直接上传分支
        return slideService.uploadHtml(courseId, file, chapterId);
    } else if (fileName.endsWith(".pptx")) {
        // 现有 PPT 流程，可选自动转 HTML
        return slideService.uploadPptx(courseId, file, autoConvertHtml, chapterId);
    } else {
        throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID);
    }
}
```

### SlideService.uploadHtml() 关键代码

```java
@Transactional
public SlideUploadResponse uploadHtml(Long courseId, MultipartFile file, Long chapterId) {
    // 1. 大小校验（≤5MB）
    if (file.getSize() > 5 * 1024 * 1024) {
        throw new BusinessException(ErrorCode.HTML_TOO_LARGE); // 16010
    }

    // 2. 读取 + sanitize
    String rawHtml = new String(file.getBytes(), StandardCharsets.UTF_8);
    String safeHtml = htmlSanitizer.sanitize(rawHtml);  // OWASP Java HTML Sanitizer

    // 3. 创建 CourseSlide + 单页 SlidePage
    CourseSlide slide = new CourseSlide();
    slide.setCourseId(courseId);
    slide.setFileName(file.getOriginalFilename());
    slide.setTotalPages(1);
    slide.setStatus(2);  // READY
    slide.setFileHash(Sha256Util.hash(file.getBytes()));
    courseSlideMapper.insert(slide);

    SlidePage page = new SlidePage();
    page.setSlideId(slide.getId());
    page.setCourseId(courseId);
    page.setChapterId(chapterId);
    page.setPageNumber(1);
    page.setContentType("HTML_DIRECT");
    page.setHtmlContent(safeHtml);
    page.setNarrationStatus("PENDING");
    page.setCreatedAt(LocalDateTime.now());
    page.setUpdatedAt(LocalDateTime.now());
    slidePageMapper.insert(page);

    return SlideUploadResponse.of(slide.getId(), 1);
}
```

### PPT 自动转 HTML（可选，CAP-4）

```java
// SlideRenderService 中新增方法
@Async("slideRenderExecutor")
public void tryConvertPptxToHtml(Long slideId, byte[] pptxBytes) {
    // 用 python-pptx 解析（Java 端用 XSLF 同等能力）
    // 失败不报错，只 log
    try {
        String html = pptxToHtmlConverter.convert(pptxBytes);
        // 找到每页 SlidePage，写入 html_content
        // status: HTML_GENERATED
    } catch (Exception e) {
        log.warn("PPT→HTML 自动转换失败，fallback 到 PNG: slideId={}", slideId, e);
        // 不动现有 PNG，已成功生成
    }
}
```

### 前端 SlidePlayer.vue 变更

```vue
<template>
  <div class="slide-stage">
    <div class="slide-frame">
      <!-- 新增 HTML 装载分支 -->
      <iframe
        v-if="currentPage?.contentType === 'HTML_DIRECT' && currentPage?.htmlContent"
        :srcdoc="currentPage.htmlContent"
        sandbox="allow-same-origin"
        class="slide-iframe"
        :key="current"
        :aria-label="'第' + (current + 1) + '页'"
      />
      <!-- 原有 PNG 装载 -->
      <img
        v-else-if="imageUrls[current] && !imageErrors[current]"
        :src="imageUrls[current]"
        class="slide-image"
        :alt="'第' + (current + 1) + '页'"
      />
      <!-- ... -->
    </div>
  </div>
</template>

<script>
// 新增：loadHtmlPage 函数
function loadHtmlPage(idx) {
  // HTML 课时不需要图片 URL，直接用 htmlContent
  // 无需预加载逻辑（HTML 文本已在 pages[idx].htmlContent）
}
</script>
```

### 前端 slide.js 变更

```javascript
// 新增 uploadHtml 函数
export function uploadHtml(courseId, file, onProgress, chapterId) {
  const fd = new FormData()
  fd.append('file', file)
  if (chapterId) fd.append('chapterId', chapterId)
  return request({
    method: 'POST',
    url: `/courses/${courseId}/slides/upload`,
    data: fd,
    timeout: 60000,  // HTML 比 PPT 快很多
    onUploadProgress: onProgress
  })
}

// 修改 uploadSlide 函数
export function uploadSlide(courseId, file, onProgress, chapterId, contentType = 'auto') {
  const fd = new FormData()
  fd.append('file', file)
  if (chapterId) fd.append('chapterId', chapterId)
  fd.append('contentType', contentType)  // 新增
  return request({
    method: 'POST',
    url: `/courses/${courseId}/slides/upload`,
    data: fd,
    timeout: contentType === 'html' ? 60000 : 300000,
    onUploadProgress: onProgress
  })
}
```

### 错误码新增

| 枚举值 | code | 说明 | httpStatus |
|--------|------|------|------------|
| `HTML_INVALID` | 16009 | "HTML 文件解析失败" | 400 |
| `HTML_TOO_LARGE` | 16010 | "HTML 文件超过 5MB 限制" | 413 |

### 依赖新增

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
  <artifactId>owasp-java-html-sanitizer</artifactId>
  <version>20240325.1</version>
</dependency>
```

### 灰度配置

```yaml
# application.yml (灰度期间)
plugin:
  interactive:
    enabled: true
    html-content:
      enabled: true                    # HTML 装载开关
      whitelist-teachers: [101, 205]  # 白名单教师 ID
      max-file-size: 5242880           # 5MB
```

```javascript
// src/config/plugins.js
export const INTERACTIVE_HTML = {
  enabled: import.meta.env.VITE_PLUGIN_INTERACTIVE_HTML === 'true',
  // 白名单教师才能上传 HTML
}
```

---

## Decisions

### 决策 1: HTML 用 `<iframe sandbox srcdoc>` 而非 `<div v-html>`

**选择**: `<iframe sandbox="allow-same-origin" srcdoc="...">`
**理由**:
- sandbox 天然隔离：禁用 script、表单、顶层导航、cookie
- v-html 风险高：XSS 攻击面大，必须后端 sanitize 100% 才安全
- iframe 即使被攻破，攻击范围只限 iframe 内
- srcdoc 比 src 更安全：无跨域，HTML 内嵌

**备选**:
- ❌ `<div v-html>`：必须后端 100% sanitize，开发成本高
- ❌ `<iframe src="/api/.../html-content">`：有跨域问题，需签名 URL

### 决策 2: PPT 自动转 HTML 是"尽力而为"，失败不阻塞

**选择**: 上传 PPT 时**额外**尝试生成 HTML，失败 fallback 到 PNG
**理由**:
- 用户的核心痛点（"丑"）要解决，但不能阻塞稳定流程
- PPT→HTML 转换失败率高（字体/动画），强制转是负价值
- "额外尝试"模式：成功 → HTML 视觉更好；失败 → 用户仍有 PNG 可用

**触发条件**: `autoConvertHtml=true`（默认 false，避免误触发）

### 决策 3: HTML 文件大小限制 5MB（PPT 是 50MB）

**选择**: 5MB 上限
**理由**:
- HTML 文本应该小（CSS 内嵌 + 少量图片），5MB 已很大
- 防 DoS：恶意教师上传巨大 HTML 阻塞渲染
- 大 HTML 应外链资源（图片用 URL 而非 base64）

### 决策 4: HTML 内容用 TEXT 字段存储，不用文件

**选择**: `html_content TEXT` 字段直接存
**理由**:
- HTML 文本通常 <100KB，TEXT 字段足够
- 简化 IO：不用文件存储 + 签名 URL 流程
- 备份恢复简单：DB 一份搞定
- 如果未来 HTML 超大（视频嵌入），再升级为独立文件存储

### 决策 5: 灰度白名单复用 v1.20.0 策略

**选择**: 与 v1.20.0 灰度发布一致
**理由**:
- v1.20.0 灰度流程已 proven
- 复用避免新流程引入新风险
- 白名单教师先试，反馈再全量

### 决策 6: HTML 沙箱用 `sandbox="allow-same-origin"`

**选择**: 仅 `allow-same-origin`
**理由**:
- 不允许 script：防 XSS
- 不允许 form：防表单劫持
- 不允许 top-navigation：防钓鱼跳转
- 不允许 popup：防广告骚扰
- allow-same-origin：让 HTML 能正常加载本地资源（同域图片/CSS）

---

## Risks / Trade-offs

| 风险 | 等级 | 缓解 |
|------|:----:|------|
| HTML XSS | **高** | iframe sandbox + OWASP Sanitizer + CSP header + 白名单灰度 |
| HTML 5MB 限制被绕过（chunked upload）| 中 | 后端 `MultipartFile.getSize()` 强制校验 |
| iframe 内 HTML 死循环导致浏览器卡死 | 中 | 平台 CSP 加 `frame-ancestors 'self'` |
| 教师上传恶意 HTML 盗取登录态 | 低 | sandbox 隔离 + 不允许 script |
| PPT→HTML 转换 100% 失败（用户以为有 HTML 但其实没有）| 中 | 前端显示"PPT 模式"徽标，明确告诉教师 |
| 灰度策略失误 | 低 | 复用 v1.20.0 灰度流程 |
| iframe 性能（每页重新渲染）| 低 | `srcdoc` 内嵌，无网络 IO |

### Trade-off 1: 安全性 vs 教师灵活性

**取舍**: 安全性 > 教师灵活性
**理由**: iframe sandbox 禁用 script 是"硬约束"，教师想要 JS 交互可走未来 phase

### Trade-off 2: 灰度慢 vs 全量风险

**取舍**: 灰度慢 > 全量风险
**理由**: HTML 是新的攻击面，必须小流量验证后再放大

### Trade-off 3: PPT→HTML 转换质量 vs 工程量

**取舍**: 尽力而为 + fallback > 完美转换
**理由**: 用户核心痛点是"丑"，PNG 也能用，转成功是 bonus

---

## Migration Plan

### 阶段 0: 设计（1 天，已完成）
- ✅ proposal.md
- ✅ design.md
- ✅ tasks.md
- ⏳ specs/*.md（4 份）

### 阶段 1: 后端实现（3-5 天）
- Flyway V177 迁移
- SlidePage 实体加字段
- OWASP HTML Sanitizer 引入
- SlideController.upload() HTML 分支
- SlideService.uploadHtml()
- SlideRenderService.tryConvertPptxToHtml()（可选）
- 错误码 16009/16010
- 后端单测（≥10 个 case）

### 阶段 2: 前端实现（2-3 天）
- SlidePlayer.vue iframe 分支
- SlideUploadZone.vue 接受 .html
- slide.js uploadHtml()
- SlideManage.vue HTML 徽标
- 前端单测（≥5 个 case）

### 阶段 3: Hermes 推送端（1 天）
- push_course.py 支持 htmlLessons[]
- 验证教授分身 HTML demo 可推送

### 阶段 4: 联调 + 安全审查（2-3 天）
- 5 维交叉验证（R1-R5）
- XSS 渗透测试（最少 10 种 payload）
- 移动端测试（iOS Safari / 微信）
- 灰度白名单教师验证

### 阶段 5: 灰度发布（3-5 天）
- 配置白名单教师 ID
- 监控 HTML 加载成功率 / XSS 拦截率
- 1 周稳定后逐步扩大白名单
- 全量发布

### 阶段 6: 文档同步（1 天）
- docs/数据字典.md v0.6 → v0.7
- docs/API契约-Phase1.md 新增 HTML 上传端点
- docs/权限矩阵.md v4.0 → v4.1
- CHANGELOG.md 记录本次变更

### 回滚策略

- 任一阶段失败：`git revert` 该阶段 commit
- 灰度失败：关闭 `plugin.interactive.html-content.enabled`，回到仅 PNG
- DB 迁移失败：`ALTER TABLE DROP COLUMN`（向下迁移）

---

## Open Questions

1. **Q1**: HTML 课时是否支持讲述稿（narration_script）？
   - **默认**: 支持，与 PNG 课时共享同一字段
2. **Q2**: HTML 课时是否需要单独的 audio 字段？
   - **默认**: 需要，复用 narration_audio_url
3. **Q3**: 白名单教师名单是硬编码还是 DB 存？
   - **默认**: DB 存（plugin_grants），灵活
4. **Q4**: 是否支持 HTML 课时转 PDF（学生下载讲义）？
   - **默认**: 否，本 phase 排除，未来 phase
5. **Q5**: HTML 课时大小限制 5MB 是否过小？
   - **默认**: 5MB 起步，后续根据用户反馈调整
6. **Q6**: PPT→HTML 自动转换用 Java 实现还是调 Python 脚本？
   - **默认**: Java 端用 Apache POI + Jsoup，**不调 Python**（避免环境依赖）

---

## Acceptance Criteria

### 后端验收

- [ ] Flyway V177 迁移成功，老数据 `content_type` 默认 `PPT_RENDERED`
- [ ] 上传 `.html`（5MB 内）→ slide_pages 表新增 `content_type='HTML_DIRECT'` + `html_content`
- [ ] 上传 `.html`（>5MB）→ 抛 `code 16010`
- [ ] 上传恶意 HTML（含 `<script>`）→ 后端 sanitize 移除脚本，前端 iframe sandbox 二次防护
- [ ] 上传 `.pptx` + `autoConvertHtml=true` → 同时生成 PNG + HTML（HTML 生成失败不报错）
- [ ] 单测：uploadHtml / sanitize / Flyway 迁移 / 错误码 全部 PASS

### 前端验收

- [ ] SlidePlayer.vue 检测 `contentType='HTML_DIRECT'` → 渲染 iframe sandbox
- [ ] iframe srcdoc 显示教师上传的 HTML（含样式/图片）
- [ ] HTML 课时享受自动播放/倍速/全屏/讲述稿/进度
- [ ] SlideUploadZone.vue 接受 .html/.htm，前端先校验后缀
- [ ] SlideManage.vue HTML 课时显示"HTML"徽标

### 安全验收

- [ ] XSS 渗透测试 10 种 payload 全部拦截（iframe sandbox + sanitize + CSP）
- [ ] HTML 不允许 script/form/top-navigation/popup
- [ ] 平台 CSP header 包含 `sandbox allow-same-origin` 策略
- [ ] HTML 上传走 plugin_grants 授权

### 灰度验收

- [ ] 白名单教师可上传 + 播放 HTML
- [ ] 非白名单教师上传 → 拒绝 + 友好提示
- [ ] 监控指标：HTML 加载成功率 ≥99.5%
- [ ] 1 周稳定后白名单扩大 2x

### 端到端验收

- [ ] 完整流程：上传 HTML → 等待处理 → 学生播放 → 自动播放 → 进度保存
- [ ] 老课（PNG）继续工作，零影响
- [ ] 移动端 iOS Safari / 微信浏览器 iframe 正常渲染

---

*设计方: 教授分身（hermes-professor profile，MiniMax-M3）*
*日期: 2026-07-10*
*状态: 待 jackie review → 给平台开发智能体执行*
