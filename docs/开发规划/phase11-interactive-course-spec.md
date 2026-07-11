# 互动课程插件 · 开发规格说明书 v2.0

> 基线：功能清单 v2.0（158 功能） · 新增功能
> 定位：可热拔插的独立插件模块，与视频课程平级共存于课程广场
> 技术选型：Apache POI 服务端渲染 + DeepSeek API（AI 讲述稿） + MiniMax Speech API（TTS 语音合成）
> 版本：v2.0 — 经 4 位专家交叉论证，否决 PPTXjs 浏览器端方案，采用服务端预渲染路线

---

## 〇、专家论证摘要

### 论证流程

启动 4 位专家并行审查 PPTXjs + reveal.js 浏览器端方案：

| 角色 | 审查维度 | 结论 |
|------|---------|:---:|
| UX 专家 | 教师/学生体验流程 | 教师 5/10，学生 6/10 |
| 兼容性专家 | 中文 PPT 渲染、浏览器兼容 | **不可用** |
| 前端架构师 | jQuery+Vue 3 集成、打包 | **高风险** |
| 性能安全专家 | 性能、XSS、依赖 CVE | **6 项 FAIL** |

### 四方一致结论

**PPTXjs 浏览器端方案必须否决。** 核心硬伤：

| 致命缺陷 | 原因 |
|---------|------|
| 中文字体渲染 FAIL | 源码只读 `a:latin` 西文字体，完全忽略 `a:ea` 东亚字体节点 |
| jQuery 1.11.3 + Vue 3 冲突 | DOM 双轨管理 → 幽灵节点、事件泄漏、响应式破坏 |
| 项目死亡 | 最后更新 2022-03，26 个 Open Issue 无人回应 |
| 安全漏洞 | jsZip v2 CVE-2021-23413 + jQuery 1.x CVE-2020-11022 |
| 移动端不可用 | 200 页课件 ≈ 200-500MB DOM 内存，低端设备 OOM |

**替代方案**：Apache POI 服务端预渲染 → 逐页 PNG 图片 + 纯 Vue 3 前端播放器。

### 方案对比

| 维度 | PPTXjs+reveal.js（否决） | Apache POI 服务端（采用） |
|------|:---:|:---:|
| 中文渲染 | ❌ 字体错乱 | ✅ 服务器安装中文字体，完美渲染 |
| 技术栈兼容 | ❌ jQuery + Vue 3 DOM 冲突 | ✅ 纯 Vue 3，零依赖冲突 |
| 安全性 | ❌ 6 FAIL（XSS / CVE / 内容完整性） | ✅ PASS |
| 移动端性能 | ❌ OOM | ✅ 只加载当前页 PNG，内存 ~30MB |
| 构建体积增量 | ❌ +485 KB（jQuery+jsZip+PPTXjs+reveal） | ✅ +0 KB（纯 Vue 组件） |
| 即时预览 | ✅ 浏览器端渲染 | ⚠️ 需等 POI 处理（≈1s/页） |
| 服务端负载 | ✅ 零 | ⚠️ 需异步处理 + 结果缓存 |

**得失权衡**：牺牲"上传即看"（替换为异步处理进度提示），换来得益覆盖所有致命缺陷。

---

## 一、插件架构总览

### 1.1 核心理念

互动课程作为**独立插件**，通过配置开关控制加载，禁用时不影响视频课程核心功能。

```
┌──────────────┐     ┌───────────────────────┐     ┌──────────────┐
│  教师上传     │ ──→ │  服务端 (Spring Boot)   │ ──→ │  本地存储     │
│  .pptx       │     │                       │     │              │
│              │     │ ① 校验（格式/大小）      │     │ slides/      │
│              │     │ ② POI 解析 PPTX          │     │  {courseId}/ │
│              │     │ ③ 逐页 AWT 渲染为 PNG     │     │   page_1.png │
│              │     │ ④ 提取每页文本             │     │   page_2.png │
│              │     │ ⑤ DeepSeek AI 生成讲述稿   │     │   ...       │
│              │     │ ⑥ MiniMax TTS 生成音频    │     │   audio_1.mp3│
│              │     │ ⑦ 输出 manifest 到 DB      │     │   audio_2.mp3│
└──────────────┘     └───────┬───────────────┘     └──────┬───────┘
                             │                            │
                             ▼                            ▼
                    ┌──────────────────┐         ┌─────────────┐
                    │ 学生浏览器 (Vue 3) │ ←───── │ 防盗链分发    │
                    │                  │         │ (签名URL)    │
                    │ 纯 Vue 图片播放器  │         └─────────────┘
                    │ • <img> 展示当前页 │
                    │ • <audio> 同步播放 │
                    │ • 讲述稿侧边面板    │
                    │ • 键盘/触屏翻页    │
                    │                  │
                    │ 依赖: 仅 Vue 3    │
                    └──────────────────┘
```

```
插件架构（代码层面）:

核心平台 (micro-course-api / micro-course-admin)
├── 视频课程 ✅  (现有体系，保持不变)
├── 题库练习 ✅
├── 讨论区 ✅
├── 选课/进度 ✅
├── 课程广场 ✅
│
├── 插件注册中心 (PluginRegistry)           ← 新增核心设施
└── 插件: 互动课程 (interactive)            ← Phase 11
       ├── @ConditionalOnProperty 控制加载
       ├── 独立 DB migration (V48)
       ├── 独立 Controller/Service/Entity
       ├── 独立前端目录 (src/plugins/interactive/)
       └── 按教师/学院授权 (plugin_grants 表)
```

### 1.2 热拔插能力

| 层级 | 控制方式 | 禁用时效果 |
|------|---------|-----------|
| 后端 | `@ConditionalOnProperty("plugin.interactive.enabled")` | Controller/Service/Entity 全部不加载 |
| DB | Flyway migration 独立命名 `V48/V49` | 表存在但不参与核心查询 |
| 前端 | 环境变量 `VITE_PLUGIN_INTERACTIVE` | 路由不注册，chunk 不加载 |
| 课程广场 | 查询时过滤 `course_type` | 互动课程自动隐藏 |

### 1.3 插件契约接口

```java
// 核心层定义，所有插件实现
public interface CourseTypePlugin {
    String getType();                          // "INTERACTIVE"
    String getDisplayName();                   // "互动课程"
    boolean isEnabled();                       // 从配置读取
    String getPlayerRoute(Long courseId);      // 前端播放页路由
    String getTeacherPanelRoute(Long courseId);// 教师管理页路由
}
```

---

## 二、核心层变更（Phase 11.0 · 插件基础设施）

### 2.1 courses 表新增字段

| 字段 | DB 列 | 类型 | 约束 | 说明 |
|------|-------|------|------|------|
| courseType | course_type | VARCHAR(20) | NOT NULL, default 'VIDEO' | 课程类型：VIDEO / INTERACTIVE |
| price | price | DECIMAL(10,2) | — | 价格（元），NULL=免费 |
| isFree | is_free | BOOLEAN | NOT NULL, default TRUE | 是否免费 |

**Flyway**：`V48__course_type_and_price.sql`

```sql
ALTER TABLE courses
    ADD COLUMN course_type VARCHAR(20) NOT NULL DEFAULT 'VIDEO',
    ADD COLUMN price DECIMAL(10,2),
    ADD COLUMN is_free BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_courses_course_type ON courses(course_type);
```

### 2.2 plugin_grants 表（插件授权）

| 字段 | DB 列 | 类型 | 约束 | 说明 |
|------|-------|------|------|------|
| id | id | BIGSERIAL | PK | 主键 |
| pluginId | plugin_id | VARCHAR(50) | NOT NULL | 插件标识（如 'interactive'） |
| grantType | grant_type | VARCHAR(20) | NOT NULL | 授权类型：TEACHER / DEPARTMENT |
| granteeId | grantee_id | BIGINT | NOT NULL | 被授权对象 ID（user_id 或 department_id） |
| createdAt | created_at | TIMESTAMP | NOT NULL | 创建时间 |

**索引**：`uk_plugin_grants (UNIQUE: plugin_id + grant_type + grantee_id)`

```sql
CREATE TABLE plugin_grants (
    id BIGSERIAL PRIMARY KEY,
    plugin_id VARCHAR(50) NOT NULL,
    grant_type VARCHAR(20) NOT NULL,
    grantee_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plugin_grants UNIQUE (plugin_id, grant_type, grantee_id)
);

CREATE INDEX idx_plugin_grants_plugin ON plugin_grants(plugin_id);
```

### 2.3 CourseTypePlugin 接口

**文件**：`com.microcourse.plugin.CourseTypePlugin`

```java
package com.microcourse.plugin;

public interface CourseTypePlugin {
    String getType();
    String getDisplayName();
    boolean isEnabled();
    String getPlayerRoute(Long courseId);
    String getTeacherPanelRoute(Long courseId);
}
```

### 2.4 PluginRegistry（插件注册中心）

**文件**：`com.microcourse.plugin.PluginRegistry`

```java
@Service
public class PluginRegistry {
    private final Map<String, CourseTypePlugin> plugins = new ConcurrentHashMap<>();

    public PluginRegistry(List<CourseTypePlugin> pluginList) {
        for (CourseTypePlugin p : pluginList) {
            if (p.isEnabled()) plugins.put(p.getType(), p);
        }
    }

    public Set<String> getEnabledTypes() {
        return Collections.unmodifiableSet(plugins.keySet());
    }
}
```

### 2.5 Course.java 实体变更

新增字段（getter/setter 按现有模式手写，不用 Lombok）：

```java
private String courseType;     // "VIDEO" | "INTERACTIVE"
private BigDecimal price;      // NULL = 免费
private Boolean isFree;        // default TRUE
```

### 2.6 CourseCreateRequest / CourseVO 变更

- `CourseCreateRequest` 新增 `courseType` 字段
- `CourseVO` 新增 `courseType` 字段
- 建课校验：`courseType = 'INTERACTIVE'` 时检查 `plugin_grants` 表

### 2.7 CourseService 变更

- 分页查询支持 `courseType` 筛选 `GET /api/courses?courseType=INTERACTIVE`
- 建课时 `courseType != 'VIDEO'` → 检查 plugin_grants 授权

### 2.8 新增 ErrorCode

| 枚举值 | code | 说明 | httpStatus |
|--------|------|------|------------|
| PLUGIN_NOT_ENABLED | 16001 | "该课程类型对应的插件未启用" | 400 |
| PLUGIN_NO_GRANT | 16002 | "您没有该课程类型的创建权限" | 403 |
| SLIDE_NOT_FOUND | 16003 | "幻灯片不存在" | 404 |
| SLIDE_PAGE_NOT_FOUND | 16004 | "幻灯片页面不存在" | 404 |
| NARRATION_GENERATE_FAILED | 16005 | "AI 讲述稿生成失败" | 500 |
| TTS_GENERATE_FAILED | 16006 | "TTS 音频生成失败" | 500 |
| PPT_PARSE_FAILED | 16007 | "PPT 文件解析失败" | 400 |
| PPT_FORMAT_INVALID | 16008 | "不支持的 PPT 格式，请上传 .pptx 文件" | 400 |

---

## 三、互动课程插件 · 数据模型（Phase 11.1）

### 3.1 course_slides 表

| 字段 | DB 列 | 类型 | 约束 | 说明 |
|------|-------|------|------|------|
| id | id | BIGSERIAL | PK | 主键 |
| courseId | course_id | BIGINT | FK→courses, NOT NULL | 所属课程 |
| fileName | file_name | VARCHAR(255) | NOT NULL | 上传文件名 |
| fileUrl | file_url | VARCHAR(500) | NOT NULL | PPT 文件存储路径 |
| totalPages | total_pages | INTEGER | NOT NULL | 总页数 |
| status | status | INTEGER | NOT NULL, default 0 | 0=UPLOADING, 1=RENDERING, 2=READY, 3=FAILED |
| errorMessage | error_message | VARCHAR(500) | — | 失败原因 |
| fileHash | file_hash | VARCHAR(64) | — | 文件 SHA-256（完整性校验） |
| createdAt | created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updatedAt | updated_at | TIMESTAMP | NOT NULL | 更新时间 |

**索引**：`uk_slides_course (UNIQUE: course_id)`, `idx_slides_status`

**Flyway**：`V49__plugin_interactive_slides.sql`

### 3.2 slide_pages 表

| 字段 | DB 列 | 类型 | 约束 | 说明 |
|------|-------|------|------|------|
| id | id | BIGSERIAL | PK | 主键 |
| slideId | slide_id | BIGINT | FK→course_slides, NOT NULL | 所属幻灯片 |
| courseId | course_id | BIGINT | FK→courses, NOT NULL | 冗余，加速查询 |
| pageNumber | page_number | INTEGER | NOT NULL | 页码（1-based） |
| imageUrl | image_url | VARCHAR(500) | NOT NULL | POI 渲染的 PNG 图片路径（1920px 宽） |
| thumbnailUrl | thumbnail_url | VARCHAR(500) | — | 缩略图路径（320px 宽，管理页使用） |
| imageWidth | image_width | INTEGER | — | PNG 宽度（px） |
| imageHeight | image_height | INTEGER | — | PNG 高度（px） |
| extractedText | extracted_text | TEXT | — | 从 PPT 提取的文本 |
| hasAnimation | has_animation | BOOLEAN | NOT NULL, default FALSE | 该页是否包含动画效果（已丢失） |
| hasEmbeddedMedia | has_embedded_media | BOOLEAN | NOT NULL, default FALSE | 该页是否包含嵌入视频/音频 |
| narrationScript | narration_script | TEXT | — | AI 生成/教师编辑的讲述稿 |
| narrationAudioUrl | narration_audio_url | VARCHAR(500) | — | TTS 生成音频路径 |
| audioDuration | audio_duration | INTEGER | — | 音频时长（秒） |
| narrationStatus | narration_status | VARCHAR(20) | NOT NULL, default 'PENDING' | PENDING / AI_GENERATED / TEACHER_EDITED / AUDIO_GENERATING / AUDIO_READY |
| createdAt | created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updatedAt | updated_at | TIMESTAMP | NOT NULL | 更新时间 |

#### 3.2.1 HTML 互动课件扩展（V177 增量）

| 字段 | DB 列 | 类型 | 约束 | 说明 |
|------|-------|------|------|------|
| contentType | content_type | VARCHAR(20) | NOT NULL, default 'PPT_RENDERED', CHECK IN ('PPT_RENDERED', 'HTML_DIRECT') | 内容类型：PPT_RENDERED = 走 POI→PNG 渲染；HTML_DIRECT = 教师直传 HTML |
| htmlContent | html_content | TEXT | — | 经 HtmlSanitizer 消毒的 HTML 字符串（仅 contentType='HTML_DIRECT' 时使用） |

**新增索引**：`idx_slide_pages_content_type`

**Flyway**：
- `V177__slide_pages_content_type.sql`（加列 + CHECK 约束，事务内）
- `V177b__slide_pages_content_type_index_concurrent.sql`（建索引，事务外，CONCURRENTLY）
- `V178__rollback_slide_pages_content_type.sql`（回滚脚本）

**前端播放分支**：SlidePlayer.vue 检测 `contentType === 'HTML_DIRECT' && htmlContent` 时，渲染 `<iframe sandbox="" srcdoc="...">`（完全沙箱，禁用脚本/表单/同源/顶级导航），不渲染 PNG。

**安全约束**（HtmlSanitizer 策略）：
- 允许标签：a, abbr, b, blockquote, br, caption, cite, code, dd, del, details, div, dl, dt, em, figcaption, figure, h1-h6, hr, i, img, ins, kbd, li, mark, ol, p, pre, q, s, samp, small, span, strike, strong, sub, summary, sup, table, tbody, td, tfoot, th, thead, time, tr, tt, u, ul, var
- 禁止标签：script, iframe, form, input, button, object, embed, svg, math, style, meta, base, link
- 禁止属性：style（CSS injection / exfiltration 防护）, id/class（防 CSS 选择器配合残留 style 攻击）, target（防反向 tabnabbing）
- 协议白名单：img src 仅 http/https（**禁止 data: URI**，绕过 5MB 限制 + 任意 base64 注入）
- iframe sandbox：sandbox="" 完全沙箱

**大小限制**：
- HTML 文件：≤5MB（前端 + 后端双校验）
- 单条 HTML 内容：≤5MB
- 错误码：16009 HTML_INVALID / 16010 HTML_TOO_LARGE / 16011 HTML_CONTENT_TOO_LARGE / 16012 HTML_SANITIZE_REMOVED_ALL

**索引**：`idx_sp_slide_id`, `uk_sp_course_page (UNIQUE: course_id + page_number)`, `idx_sp_narration_status`, `idx_slide_pages_content_type`

**Flyway**：`V49__plugin_interactive_slides.sql`（同文件）

---

## 四、后端实现清单

### 4.1 插件自动配置

**文件**：`com.microcourse.plugin.interactive.InteractivePluginAutoConfig`

```java
@Configuration
@ConditionalOnProperty(name = "plugin.interactive.enabled", havingValue = "true")
@EnableAsync
@ComponentScan("com.microcourse.plugin.interactive")
public class InteractivePluginAutoConfig {

    @Bean
    public CourseTypePlugin interactivePlugin() {
        return new InteractivePlugin();
    }

    @Bean
    public ThreadPoolTaskExecutor slideRenderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("slide-render-");
        return executor;
    }
}
```

### 4.2 实体类

| 文件 | 路径 |
|------|------|
| `CourseSlide.java` | `com.microcourse.plugin.interactive.entity` |
| `SlidePage.java` | `com.microcourse.plugin.interactive.entity` |

### 4.3 Mapper

| 文件 | 路径 |
|------|------|
| `CourseSlideMapper.java` | `com.microcourse.plugin.interactive.mapper` |
| `SlidePageMapper.java` | `com.microcourse.plugin.interactive.mapper` |

### 4.4 Service

| 文件 | 职责 |
|------|------|
| `SlideService` / `SlideServiceImpl` | PPT 上传、POI 渲染、幻灯片 CRUD |
| `NarrationService` / `NarrationServiceImpl` | DeepSeek API 生成讲述稿 |
| `TtsService` / `TtsServiceImpl` | MiniMax mmx CLI 生成音频 |

### 4.5 Controller

| Controller | 端点 | Method | 说明 |
|-----------|-------|--------|------|
| `SlideController` | `/api/courses/{id}/slides/upload` | POST | 教师上传 PPT |
|  | `/api/courses/{id}/slides` | GET | 获取幻灯片信息（含总页数、状态） |
|  | `/api/courses/{id}/slides/pages` | GET | 获取所有页（缩略图列表 + 状态） |
|  | `/api/courses/{id}/slides/pages/{pageNo}` | GET | 获取单页详情（大图+文本+讲述稿+音频） |
|  | `/api/courses/{id}/slides/pages/{pageNo}/image` | GET | 获取大图 PNG（播放器用） |
|  | `/api/courses/{id}/slides/pages/{pageNo}/thumbnail` | GET | 获取缩略图（管理页用） |
| `NarrationController` | `/api/courses/{id}/slides/pages/{pageNo}/narration` | PUT | 教师编辑讲述稿 |
|  | `/api/courses/{id}/slides/pages/{pageNo}/narration/generate` | POST | AI 生成单页讲述稿 |
|  | `/api/courses/{id}/slides/narrations/generate` | POST | AI 批量生成所有页讲述稿（异步） |
| `TtsController` | `/api/courses/{id}/slides/pages/{pageNo}/audio/generate` | POST | TTS 生成单页音频 |
|  | `/api/courses/{id}/slides/audio/generate` | POST | TTS 批量生成所有页音频（异步） |
|  | `/api/courses/{id}/slides/pages/{pageNo}/audio` | GET | 获取音频文件（签名 URL） |

### 4.6 SlideService 核心逻辑（Apache POI 服务端渲染）

```
PPT 上传与渲染流程:

Step 1: 接收与校验
  - 接收 MultipartFile
  - MIME 校验: application/vnd.openxmlformats-officedocument.presentationml.presentation
  - 魔数校验: PK\x03\x04 (ZIP header)
  - 文件大小: ≤ 50MB
  - 解压炸弹防护: 内部文件数 ≤ 1000, 解压总大小 ≤ 500MB

Step 2: 存储
  - 保存 .pptx 到 /data/slides/{courseId}/original.pptx
  - 计算 SHA-256 → 存入 file_hash（防篡改）
  - 创建 course_slides 记录 (status=UPLOADING)

Step 3: @Async 异步渲染
  - 更新 status=RENDERING
  - Apache POI 打开 PPTX:
    XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(file));
    
  - 获取幻灯片尺寸:
    Dimension pageSize = ppt.getPageSize();  // EMU 单位
    
  - 逐页渲染:
    for (int i = 0; i < ppt.getSlides().size(); i++) {
        XSLFSlide slide = ppt.getSlides().get(i);
        
        // ① 渲染 PNG (1920px 宽，等比缩放)
        BufferedImage img = new BufferedImage(1920, scaledHeight, TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale);
        slide.draw(g);
        g.dispose();
        // 保存: /data/slides/{courseId}/images/page_{i+1}.png
        ImageIO.write(img, "PNG", outputFile);
        
        // ② 渲染缩略图 (320px 宽)
        BufferedImage thumb = resizeImage(img, 320);
        // 保存: /data/slides/{courseId}/thumbnails/page_{i+1}.png
        
        // ③ 提取文本
        String text = extractText(slide);  // XSLFSlide.getText()
        
        // ④ 检测动画（解析 slide XML 中 a:anim 节点）
        boolean hasAnimation = detectAnimation(slide);
        
        // ⑤ 检测嵌入媒体（解析 slide XML 中 p:cMediaNode 节点）
        boolean hasEmbeddedMedia = detectEmbeddedMedia(slide);
        
        // ⑥ 批量插入 slide_pages 记录
        batchInsert(page);
    }
    
  - 更新 course_slides.totalPages, status=READY

Step 4: 异常处理
  - 任一页渲染失败 → status=FAILED, errorMessage
  - 异步任务异常 → 全局 AsyncExceptionHandler 捕获

中文渲染保障:
  - 服务器必须安装中文字体（见 §九 系统依赖）
  - POI 使用系统字体渲染，AWT Graphics2D 自动匹配
  - 推荐字体: 宋体(SimSun) / 黑体(SimHei) / 微软雅黑(Microsoft YaHei)
  
兼容性检测:
  - 检测到动画效果 → has_animation=TRUE（返回给教师端提示）
  - 检测到嵌入视频 → has_embedded_media=TRUE（返回给教师端提示）
  - 教师可在管理页看到每页的兼容性警告标记
```

**关键方法**：

| 方法 | 说明 |
|------|------|
| `renderSlideToPng(XSLFSlide, width)` | 单页渲染为 PNG BufferedImage |
| `extractText(XSLFSlide)` | 提取幻灯片所有文本 |
| `detectAnimation(XSLFSlide)` | 解析 slide XML，检测 a:anim 节点 |
| `detectEmbeddedMedia(XSLFSlide)` | 解析 slide XML，检测 p:cMediaNode |
| `resizeImage(BufferedImage, targetWidth)` | 缩放图片 |
| `processUploadAsync(Long slideId)` | @Async 异步渲染入口 |

### 4.7 NarrationService（DeepSeek API）

```
请求:
  POST https://api.deepseek.com/v1/chat/completions
  Headers: Authorization: Bearer {api-key}
  Body:
    {
      "model": "deepseek-chat",
      "messages": [
        {
          "role": "system",
          "content": "你是一位经验丰富的大学教师。根据幻灯片内容生成适合口头讲述的讲解稿。要求：①语气亲切自然，像在课堂上讲课 ②用口语化表达，避免书面语 ③重点解释概念而非照读文字 ④200-500字 ⑤纯文本，不包含 Markdown 标记"
        },
        {
          "role": "user",
          "content": "PPT 第 {pageNumber} 页内容：\n{extractedText}\n\n请为这一页生成讲述稿。"
        }
      ],
      "temperature": 0.7,
      "max_tokens": 1000
    }

批量生成:
  - @Async 并行处理（最多 3 并发）
  - 每页完成后更新 narration_status=AI_GENERATED, narration_script
  - 任一页失败 → code 16005，不影响其他页
```

### 4.8 TtsService（MiniMax mmx CLI）

```
调用方式: ProcessBuilder 执行 mmx CLI

命令:
  mmx speech synthesize \
    --text-file /tmp/narration_{pageNumber}.txt \
    --voice "{voice-id}" \
    --model "{tts-model}" \
    --format mp3 \
    --out "/data/slides/{courseId}/audio/page_{pageNumber}.mp3" \
    --quiet

文本预处理:
  - 讲述稿写入临时文件（规避 shell 注入风险）
  - 长度超 10k 字符 → 按自然段分割为多个文件，合并为单个 MP3

批量生成:
  - 逐页顺序调用 CLI（CLI 本身不支持并行）
  - 每页完成后读取文件大小 → 估算 duration
  - 更新 narration_audio_url, audio_duration, narration_status=AUDIO_READY

音频防盗链:
  - 音频文件不暴露直链
  - 通过 SlideController GET /audio 端点 → 验证 JWT → 生成临时签名 URL（有效期 2h）
  - 签名 URL 格式: /data/slides/{courseId}/audio/page_{n}.mp3?sign={HMAC}&expires={ts}
```

---

## 五、前端实现清单（Phase 11.4）

### 5.1 插件目录结构

```
micro-course-admin/src/plugins/interactive/
├── index.js                    ← 插件导出
├── api/
│   └── slide.js                ← Slide API 封装
├── views/
│   └── teacher/
│       └── SlideManage.vue     ← 教师：上传/页面管理/AI/TTS
├── components/
│   ├── SlidePageEditor.vue     ← 单页编辑器（文本+讲述稿编辑+操作按钮）
│   ├── SlidePageList.vue       ← 缩略图列表 + 状态标签 + 兼容性警告
│   ├── AIGeneratePanel.vue     ← AI 生成面板（批量/进度）
│   ├── AudioGeneratePanel.vue  ← TTS 生成面板
│   └── SlidePreviewModal.vue   ← 教师预览弹窗（模拟学生视角）
└── assets/
    └── slide-icons/
```

### 5.2 插件导出 (index.js)

```javascript
export default {
  id: 'interactive',
  name: '互动课程',
  version: '1.0.0',
  enabled: import.meta.env.VITE_PLUGIN_INTERACTIVE === 'true',

  routes: [
    {
      path: '/teacher/courses/:id/slides',
      name: 'SlideManage',
      component: () => import('./views/teacher/SlideManage.vue'),
      meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] },
    },
    {
      path: '/student/courses/:id/slides/player',
      name: 'SlidePlayer',
      component: () => import('../../views/student/SlidePlayer.vue'),
      meta: { requiresAuth: true, roles: ['STUDENT'] },
    },
  ],

  courseCardConfig: {
    typeLabel: '互动课',
    typeColor: '#67c23a',
    typeIcon: 'presentation',
  },
}
```

### 5.3 前端页面

| 页面 | 路由 | 功能 |
|------|------|------|
| `SlideManage.vue` | `/teacher/courses/:id/slides` | PPT 上传+处理进度+缩略图列表+兼容性标记+逐页编辑+AI/TTS 控制+模拟预览 |
| `SlidePlayer.vue` | `/student/courses/:id/slides/player` | 纯 Vue 图片轮播器 + 音频同步 + 讲述稿侧面板 |

### 5.4 插件注册中心

**文件**：`micro-course-admin/src/store/plugins.js`

```javascript
import { defineStore } from 'pinia'
import router from '@/router'

const pluginModules = import.meta.glob('@/plugins/*/index.js', { eager: true })

export const usePluginStore = defineStore('plugins', {
  state: () => ({ plugins: [], enabledTypes: [] }),
  actions: {
    registerPlugins() {
      for (const path of Object.keys(pluginModules)) {
        const plugin = pluginModules[path].default
        if (!plugin.enabled) continue
        this.plugins.push(plugin)
        this.enabledTypes.push(plugin.id)
        for (const route of plugin.routes) {
          router.addRoute(route)
        }
      }
    },
    getCourseCardConfig(type) {
      return this.plugins.find(p => p.id === type)?.courseCardConfig || null
    },
  },
})
```

### 5.5 课程广场卡片类型角标

```vue
<template>
  <el-card class="course-card">
    <div v-if="typeConfig" class="course-type-badge"
         :style="{ background: typeConfig.typeColor }">
      {{ typeConfig.typeLabel }}
    </div>
    <!-- 现有卡片内容 -->
  </el-card>
</template>

<style scoped>
.course-type-badge {
  position: absolute; top: 8px; right: 8px;
  padding: 2px 8px; border-radius: 4px;
  font-size: 12px; color: #fff;
}
</style>
```

### 5.6 SlidePlayer.vue（学生端 · 纯 Vue 3 实现）

```
布局:
┌──────────────────────────────────────────────────────────┐
│ ← 返回课程                          第 3/20 页            │
├──────────────────────────┬───────────────────────────────┤
│                          │ 📝 讲述稿                      │
│                          │                               │
│   PPT 页 PNG 图片         │ "本节我们讲解数据结构的基本     │
│   (主体区域，自适应缩放)    │ 概念。数据结构是计算机存储、   │
│                          │ 组织数据的方式..."             │
│                          │                               │
│                          ├───────────────────────────────┤
│                          │ ▶ 0:12 / 3:45  🔊 1.0x       │
│                          │ ████████████████████░░░░░░    │
│                          │ 速度: 0.75x | 1x | 1.25x | 1.5x | 2x │
├──────────────────────────┴───────────────────────────────┤
│  自动播放 ◉    ← 上一页    ● ● ○ ○ ○   下一页 →          │
│                       (页面进度点，可点击跳转)              │
└──────────────────────────────────────────────────────────┘

播放模式:
  - 自动模式（默认）: 音频播完 → 自动翻下一页 → 续播音频
    音频最后 3 秒右下角显示倒计时"即将进入下一页 3..2..1"
  - 手动模式: 用户点击翻页后停止自动播放，需手动点播放按钮
    用户点击 ← → 或进度点跳转后自动切换为手动模式
  - "继续自动播放"按钮恢复自动模式

音频预加载:
  - 进入页面时预加载当前页 + 后 2 页音频
  - 翻页后预加载新位置后 2 页
  - 使用 <link rel="preload" as="audio">

讲述稿文本:
  - 默认侧边面板展开，可折叠（折叠后显示窄手柄 📝）
  - 教师未提供讲述稿 → 面板隐藏

技术栈: 纯 Vue 3 + Element Plus，零第三方播放器依赖
核心: <img> 显示 PNG + <audio> 控制播放 + CSS transition 翻页动画
```

**Vue 组件核心逻辑**：

```javascript
// SlidePlayer.vue <script setup>
const currentPage = ref(0)
const isAutoMode = ref(true)
const audioRef = ref(null)
const pages = ref([])  // GET /api/courses/{id}/slides/pages

// 翻页
function goToPage(n) {
  currentPage.value = n
  // 切换图片: 淡入淡出 CSS transition
  // 切换音频源: audioRef.value.src = pages[n].audioUrl
  // 自动模式: audioRef.value.play()
  preloadAdjacentAudio(n)
}

// 音频事件
audioRef.value.onended = () => {
  if (isAutoMode.value && currentPage.value < pages.value.length - 1) {
    goToPage(currentPage.value + 1)
  }
}

audioRef.value.ontimeupdate = () => {
  // 更新进度条 + 讲述稿文本滚动位置
}

// 预加载
function preloadAdjacentAudio(current) {
  [current+1, current+2].forEach(i => {
    if (i < pages.value.length) {
      const link = document.createElement('link')
      link.rel = 'preload'; link.as = 'audio'
      link.href = pages.value[i].audioUrl
      document.head.appendChild(link)
    }
  })
}

// 键盘快捷键
onMounted(() => document.addEventListener('keydown', handleKeydown))
onUnmounted(() => document.removeEventListener('keydown', handleKeydown))

function handleKeydown(e) {
  if (e.key === 'ArrowRight' || e.key === ' ') goToPage(currentPage.value + 1)
  if (e.key === 'ArrowLeft') goToPage(currentPage.value - 1)
  if (e.key === 'f') toggleFullscreen()
  // Esc, Home, End 等
}
```

### 5.7 SlideManage.vue（教师端 · 工作流）

```
Step 1: PPT 上传区（拖拽上传 + 进度条）
  上传完成 → 后端开始异步渲染 → 页面轮询 course_slides.status
  显示渲染进度: "正在处理第 3/20 页..." (status=RENDERING)
  渲染完成 → 显示缩略图网格 (status=READY)

Step 2: 页面管理（缩略图网格 + 逐页编辑）
  ┌─────────────────────────────────────────────┐
  │ [page_1.png] [page_2.png] [page_3.png] ...  │
  │  ⚠️动画      ✅正常      ⚠️嵌入视频          │
  │                                             │
  │ 点击缩略图 → 右侧滑出 SlidePageEditor        │
  │   - 大图预览                                │
  │   - 提取文本展示（只读）                      │
  │   - 讲述稿编辑器（textarea，可编辑）          │
  │   - 操作按钮: [AI生成本页] [试听本页] [重新TTS] │
  │   - 兼容性警告条: "⚠️ 本页包含动画效果，              │
  │                    播放时将展示最终静态状态"    │
  └─────────────────────────────────────────────┘

Step 3: AI 生成
  点击"批量 AI 生成" → 异步生成所有未生成页
  进度条: "AI 生成中... 5/20"
  完成后各页状态标签变为 "AI 已生成"

Step 4: TTS 生成
  点击"批量生成音频" → 异步处理
  进度条: "TTS 生成中... 5/20"
  完成后可逐页试听

Step 5: 教师预览（SlidePreviewModal）
  以学生视角完整播放一遍（PPT+音频+页面切换）
  检查: 讲述稿与幻灯片是否匹配、音频速度是否合适

Step 6: 提交发布
  确认后提交课程审核（走现有审核流程）
```

### 5.8 建课页类型选择

```vue
<template>
  <el-radio-group v-model="form.courseType" class="course-type-selector">
    <el-radio label="VIDEO" border>
      <div class="type-option">
        <el-icon><VideoCamera /></el-icon>
        <span class="type-label">视频课程</span>
        <span class="type-desc">上传视频，真人讲解</span>
      </div>
    </el-radio>
    <el-radio label="INTERACTIVE" :disabled="!hasInteractiveGrant" border>
      <div class="type-option">
        <el-icon><Present /></el-icon>
        <span class="type-label">互动课程</span>
        <span class="type-desc">上传 PPT，AI 生成讲述稿 + TTS 配音</span>
      </div>
    </el-radio>
  </el-radio-group>
</template>
```

---

## 六、配置项

### 6.1 application.yml（后端）

```yaml
plugin:
  interactive:
    enabled: true
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      model: deepseek-chat
      base-url: https://api.deepseek.com
    minimax:
      tts-model: speech-2.8-hd
      tts-voice: Chinese_female_narrator       # 默认音色
      available-voices:                        # 教师可选音色列表
        - id: Chinese_female_narrator
          name: "标准女声"
        - id: Chinese_male_narrator
          name: "标准男声"
        - id: Chinese_gentle_female
          name: "柔和女声"
    upload:
      max-file-size: 50MB
      allowed-formats: .pptx
    slides:
      storage-path: /data/slides
      page-image-width: 1920
      page-image-format: png
      thumbnail-width: 320
      audio-format: mp3
      audio-sign-ttl: 7200                     # 签名 URL 有效期（秒）
    render:
      async-core-pool-size: 2
      async-max-pool-size: 4
```

### 6.2 .env（前端）

```bash
VITE_PLUGIN_INTERACTIVE=true
```

---

## 七、实施阶段

| 子阶段 | 任务 | 新增文件/表 | 预估 |
|--------|------|---------|------|
| **11.0** | 插件基础设施 | courses+3字段, plugin_grants, PluginRegistry, CourseTypePlugin, ErrorCode+8 | 2 天 |
| **11.1** | PPT 上传+POI 渲染 | course_slides, slide_pages, SlideService, SlideController, SlideManage.vue（上传+管理） | 3 天 |
| **11.2** | AI 讲述稿 | NarrationService(DeepSeek), NarrationController, AIGeneratePanel, SlidePageEditor | 2 天 |
| **11.3** | TTS 音频 | TtsService(MiniMax), TtsController, AudioGeneratePanel, 音频防盗链 | 2 天 |
| **11.4** | 前端播放器+集成 | SlidePlayer.vue, 插件注册中心, 课程广场卡片, 建课页类型选择, SlidePreviewModal | 3 天 |
| **11.5** | 联调+自检 | 全链路测试, 热拔插测试, 中文渲染验证, 移动端测试 | 2 天 |

---

## 八、验收标准

### Phase 11.0 插件基础设施

- [ ] `courses` 表有 `course_type`, `price`, `is_free` 三个字段
- [ ] `plugin_grants` 表存在且支持 TEACHER / DEPARTMENT 两种授权
- [ ] `CourseTypePlugin` 接口 + `PluginRegistry` 正常工作
- [ ] `GET /api/courses?courseType=VIDEO` 仅返回视频课程
- [ ] `GET /api/courses?courseType=INTERACTIVE` 仅返回互动课程
- [ ] 无授权教师创建互动课程 → code 16002
- [ ] 关闭插件开关 → 互动课程在广场自动隐藏

### Phase 11.1 PPT 上传 + POI 渲染

- [ ] 上传 .pptx → 魔数校验 PASS → POI 异步渲染
- [ ] 上传非 .pptx → code 16008
- [ ] 渲染完成后每页有 imageUrl（1920px PNG）+ thumbnailUrl（320px）
- [ ] 中文文本提取正常（宋体/黑体/微软雅黑）
- [ ] 含动画的页 `has_animation=TRUE`，教师端显示兼容性警告
- [ ] 含嵌入媒体的页 `has_embedded_media=TRUE`，教师端显示警告
- [ ] 渲染失败 → status=FAILED + errorMessage
- [ ] 文件 SHA-256 已存储（`file_hash` 字段）
- [ ] 教师可在管理页看到缩略图网格 + 兼容性标记

### Phase 11.2 AI 讲述稿

- [ ] 单页生成：DeepSeek 返回中文讲述稿 → 存入 `narration_script`
- [ ] 批量生成：@Async 并行处理，前端轮询 `narration_status`
- [ ] 教师编辑讲述稿 → `narration_status=TEACHER_EDITED`
- [ ] API 调用失败 → code 16005，不影响已生成内容
- [ ] 系统 prompt 生成的中文讲解自然流畅

### Phase 11.3 TTS 音频

- [ ] 单页生成：MiniMax CLI 生成 MP3 → 存入 `narration_audio_url`
- [ ] 批量生成：逐页调用，完成后 `narration_status=AUDIO_READY`
- [ ] 音频文件通过签名 URL 访问（非直链）
- [ ] 音频播放正常，中文语音清晰
- [ ] 教师可从 3 种音色中选择
- [ ] TTS 调用失败 → code 16006

### Phase 11.4 前端

- [ ] `VITE_PLUGIN_INTERACTIVE=false` → 路由不注册、菜单不显示
- [ ] `VITE_PLUGIN_INTERACTIVE=true` → 插件正常加载
- [ ] 课程广场卡片显示"视频课"(蓝色角标) / "互动课"(绿色角标)
- [ ] 建课页可选课程类型，INTERACTIVE 仅对授权教师可选
- [ ] SlideManage.vue：上传→看进度→看缩略图→编辑讲述稿→生成 AI/TTS→预览→提交
- [ ] SlidePlayer.vue：PNG 图片 + 音频同步 + 讲述稿面板 + 键盘/触屏翻页
- [ ] 自动/手动播放模式切换正常
- [ ] 翻页时图片淡入淡出过渡流畅
- [ ] 音频预加载（当前+后2页）
- [ ] 教师模拟预览功能正常

### Phase 11.5 端到端

- [ ] 教师建互动课 → 上传 PPT → 等待渲染 → AI 生成 → 编辑 → TTS → 预览 → 提交审核 → 发布
- [ ] 学生在广场看到互动课 → 进入详情 → 报名 → 播放器学习
- [ ] 插件禁用后重新启用 → 数据保留、功能恢复
- [ ] 视频课程完整流程不受影响
- [ ] 中文字体渲染无误（与原始 PPT 一致）
- [ ] 移动端（iOS Safari / 微信浏览器）播放流畅

---

## 九、依赖清单

### 新增 Maven 依赖

```xml
<!-- Apache POI - PPTX 解析与渲染 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- 已有: poi-ooxml 自带 AWT 渲染，无需额外依赖 -->
```

### 系统依赖（服务器）

| 依赖 | 版本 | 用途 |
|------|------|------|
| **中文字体包** | — | 宋体(SimSun)、黑体(SimHei)、微软雅黑(MS YaHei) |
| **MiniMax mmx CLI** | latest | TTS 语音合成 |
| **Java AWT** | JDK 17 内置 | POI 渲染幻灯片为图片 |

**macOS 字体安装**：
```bash
# macOS 自带中文字体，无需额外安装
# Linux 服务器需要:
apt-get install fonts-wqy-zenhei fonts-wqy-microhei  # 文泉驿
# 或安装 Windows 字体包
```

**MiniMax CLI 安装与认证**：
```bash
npm install -g mmx-cli
mmx auth login --api-key {MINIMAX_API_KEY}
```

### 应用配置

- `DEEPSEEK_API_KEY` — 环境变量
- `MINIMAX_API_KEY` — 环境变量（mmx CLI 自动读取 `~/.mmx/credentials.json`）

---

*文档版本：v2.0*
*日期：2026-06-20*
*变更：v1.0→v2.0 — 经 4 位专家交叉论证，从 PPTXjs 浏览器端方案切换为 Apache POI 服务端渲染方案*
*基线：docs/功能清单.md v2.0*
*依赖：Phase 5 课程相关功能已全部实现*
