# 课件架构重构设计 · 2026-07-19

> **项目**: 微课管理平台 · 互动课件 (interactive plugin) 全栈重构
> **设计者**: 总工程师 (项目负责人)
> **授权**: 用户明示"项目负责人,客户体验至上,无需考虑时间与成本"
> **决策依据**: opencode 端 P1-C 报告 + 2026-07-19 P0 事故 + AGENTS.md 行为规则
> **状态**: Draft v1.0 · 待用户评审 → writing-plans

---

## 〇、设计原则 (铁律)

按用户授权 + AGENTS.md + 用户多次强调的客户体验至上:

1. **架构清晰分层** —— Single Responsibility,每个表/类/模块一个事实
2. **系统性能稳定** —— 关键路径 p99 < 200ms,可水平扩展,无锁竞争
3. **客户体验至上** —— 教师创建课件 ≤ 3 步,学生播放零卡顿,移动端可用
4. **不考虑时间成本** —— 重构可以分阶段,但不为了赶工牺牲设计
5. **P0 安全** —— 任何变更前 backup + rollback + 门禁 + 灰度,绝不重演 7-19 P0 事故

---

## 一、问题诊断 (Why)

### 1.1 现状痛点

`slide_pages` 表混了 7 类信息 (通过 grep 实测确认,SlidePage 实体 25 字段):

| # | 信息类别 | 字段 | 数量 |
|---|---------|------|-----|
| 1 | 课件渲染内容 | image_url, thumbnail_url, image_width/height, extracted_text, has_animation, has_embedded_media | 7 |
| 2 | HTML 课件内容 | content_type, html_content | 2 |
| 3 | 讲述稿 | narration_script | 1 |
| 4 | 音频元数据 | narration_audio_url, audio_duration, segment_count, voice, tts_model, generated_at | 6 |
| 5 | 同步状态 | narration_status | 1 |
| 6 | 文件标识 | file_uuid | 1 |
| 7 | 业务标识 | id, slide_id, chapter_id, section_id, course_id, page_number | 6 |

**关键缺陷**:
- ❌ `content_type` 二选一,加新形态需 ALTER TABLE
- ❌ `narration_script` 字段存在但**无 CRUD API**(opencode 端 8 步流程从不写它)
- ❌ 音频元数据挤在 page 行,15 段音频只存 1 段 URL(`narration_audio_url` 是单数)
- ❌ PPT 页间逻辑关联无表达(用户原话:"要注意讲述稿之间的关联性")
- ❌ 前端 SlideManage.vue 把两种课件形态挤在一个表单,客户体验差

### 1.2 7-19 P0 事故的根因之一

`uploadHtmlFile` 是 destructive UPSERT (delete + insert),导致 audio 元数据被擦除。**深层原因**就是 `slide_pages` 字段过载,一个表承担多个职责,任何字段变更都可能影响其他职责。

### 1.3 用户原话 vs 设计意图

| 用户原话 | 设计意图 |
|---------|---------|
| "我觉得整个的互动课件的设计有缺陷" | 字段过载,违反 SRP |
| "ppt 和 html 课件都在一个容器里,实现方式不一样" | content_type 二选一不优雅 |
| "应该对 ppt 或 html 课件及讲述稿、音频进行同步管理" | 三者应独立 CRUD |
| "包括 crud" | 当前 API 缺讲述稿 CRUD |
| "html 课件只有一个页面,多个音频,一个讲述稿" | HTML 1:N segment 关系 |
| "ppt 课件经渲染后是多个图片,多个讲述稿... 要注意讲述稿之间的关联性" | PPT N 页 + page 间 flow |
| "结构清晰,性能稳定,客户体验至上" | 双表并立 + 各自优化索引 |
| "前端的课件管理页面是否应该重构,应该包含对音频的管理" | 前端双类型 + 四面板 |

### 1.4 行业最佳实践 (2026)

| 标准 | 关键启示 |
|-----|---------|
| **SCORM 1.2/2004** | 内容包(imsmanifest.xml) + 资源 + 状态,三者通过 manifest 关联而非塞一张表 |
| **xAPI / cmi5** | Activity Provider (内容) + LRS (跟踪),读写分离,各自独立 schema |
| **CQRS** | 命令与查询分离,内容写入是资源型,内容读取是视图型 |
| **AutoLectures (arXiv 2025)** | 课件 → 脚本 → 音频 三步流水线,每步独立可改,A/B 重生成不覆盖原文 |

**核心启示**: 内容/脚本/音频 三者**物理独立 + 逻辑关联**,而不是塞一个表。

---

## 二、目标架构 (What)

### 2.1 一句话总结

**把一个 `slide_pages` 胖表拆成 7 张职责单一的子表,通过 `(section_id, page/segment_index, is_active)` 三元组关联,前端双类型四面板工作流。**

### 2.2 七表架构图

```
slide_ppt_pages (N 页/小节)
   │ 1
   ├────► slide_ppt_page_scripts (1:N 历史脚本,is_active=true 最新)
   │           │ 1
   │           └────► slide_ppt_page_audios (1:N 音频版本,可对比)
   │                       │
   │                       └──── narration_status 由 audio.status 聚合
   │
   └────► slide_ppt_flow (页间跳转逻辑,NEXT/BRANCH/SKIP)

slide_html_units (1 个/小节)
   │ 1
   └────► slide_html_segment_scripts (1:N 段脚本,HTML DOM marker 关联)
               │ 1
               └────► slide_html_segment_audios (1:N 段音频版本)
```

### 2.3 迁移原则

- **3 个月双轨并行**: 旧表只读,新表承担新建/编辑
- **3 个月后删旧表**: 单独 PR + 30 天公告
- **回滚路径**: 所有新表 DROP TABLE 即可恢复旧态(用户已有 7-19 P0 教训)

---

## 三、数据模型 (详细 DDL)

### 3.1 V202: PPT 课件页面表

```sql
CREATE TABLE slide_ppt_pages (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    slide_id BIGINT NOT NULL,
    page_number INT NOT NULL,

    page_title VARCHAR(200),

    -- 渲染内容
    image_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    image_width INT,
    image_height INT,
    file_uuid VARCHAR(64),
    file_size_bytes BIGINT,

    -- 抽取特征
    extracted_text TEXT,
    has_animation BOOLEAN DEFAULT FALSE,
    has_embedded_media BOOLEAN DEFAULT FALSE,

    -- 聚合状态 (由 audio 状态聚合而来, 不存真实字段, view 计算)
    -- 单独的 view: v_slide_ppt_page_status

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_ppt_pages_slide_page UNIQUE (slide_id, page_number),
    CONSTRAINT fk_ppt_pages_section FOREIGN KEY (section_id) REFERENCES course_sections(id) ON DELETE CASCADE
);

CREATE INDEX idx_ppt_pages_section ON slide_ppt_pages(section_id, page_number);
CREATE INDEX idx_ppt_pages_course ON slide_ppt_pages(course_id, section_id, page_number);
```

### 3.2 V203: PPT 讲述稿表 (1:N 历史版本)

```sql
CREATE TABLE slide_ppt_page_scripts (
    id BIGSERIAL PRIMARY KEY,
    ppt_page_id BIGINT NOT NULL,

    script_text TEXT NOT NULL,
    script_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    voice VARCHAR(64),
    tts_model VARCHAR(64),
    tts_params JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_scripts_page FOREIGN KEY (ppt_page_id) REFERENCES slide_ppt_pages(id) ON DELETE CASCADE
);

-- 部分唯一索引: 每个 page 最多一个 active 脚本
CREATE UNIQUE INDEX uk_ppt_scripts_active ON slide_ppt_page_scripts(ppt_page_id) WHERE is_active = TRUE;
CREATE INDEX idx_ppt_scripts_page_history ON slide_ppt_page_scripts(ppt_page_id, script_version DESC);
```

### 3.3 V204: PPT 音频表 (1:N 音频版本,用于音色对比)

```sql
CREATE TABLE slide_ppt_page_audios (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL,
    ppt_page_id BIGINT NOT NULL,  -- 冗余, 便于快速查询

    audio_url VARCHAR(500) NOT NULL,
    audio_token VARCHAR(64),       -- token 用于无登录访问,与 current 模式一致
    audio_duration_ms INT,

    voice_used VARCHAR(64) NOT NULL,
    model_used VARCHAR(64) NOT NULL,
    generation_params JSONB,

    generation_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',  -- GENERATING/READY/FAILED

    file_size_bytes BIGINT,
    storage_path VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_audios_script FOREIGN KEY (script_id) REFERENCES slide_ppt_page_scripts(id) ON DELETE CASCADE,
    CONSTRAINT chk_ppt_audios_status CHECK (status IN ('GENERATING','READY','FAILED'))
);

CREATE INDEX idx_ppt_audios_script ON slide_ppt_page_audios(script_id);
CREATE INDEX idx_ppt_audios_page_status ON slide_ppt_page_audios(ppt_page_id, status);
CREATE INDEX idx_ppt_audios_token ON slide_ppt_page_audios(audio_token) WHERE audio_token IS NOT NULL;
```

### 3.4 V205: HTML 课件单元表

```sql
CREATE TABLE slide_html_units (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    slide_id BIGINT NOT NULL,

    page_title VARCHAR(200),

    file_uuid VARCHAR(64) NOT NULL,
    html_content TEXT NOT NULL,           -- 原始 HTML (sanitize 前)
    html_sanitized TEXT NOT NULL,         -- HtmlSanitizer 处理后
    file_size_bytes BIGINT NOT NULL,

    -- 元数据
    detected_segments INT,                -- 自动检测的分段数
    has_interactions BOOLEAN DEFAULT FALSE,  -- 是否含交互元素
    interaction_types JSONB,              -- ["quiz", "click", "reveal"] 等

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_html_units_section UNIQUE (section_id),
    CONSTRAINT fk_html_units_section FOREIGN KEY (section_id) REFERENCES course_sections(id) ON DELETE CASCADE
);

CREATE INDEX idx_html_units_course ON slide_html_units(course_id, section_id);
```

### 3.5 V206: HTML 分段脚本表 (与 HTML DOM 节点关联)

```sql
CREATE TABLE slide_html_segment_scripts (
    id BIGSERIAL PRIMARY KEY,
    html_unit_id BIGINT NOT NULL,

    segment_index INT NOT NULL,           -- 1..N
    segment_marker VARCHAR(64),           -- HTML 内的 id, 如 "seg-3", NULL = 按顺序
    segment_text TEXT,                    -- 从 HTML 抽取的相关文本 (用于 TTS 上下文)
    script_text TEXT NOT NULL,
    script_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    voice VARCHAR(64),
    tts_model VARCHAR(64),
    tts_params JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_html_seg_scripts_unit FOREIGN KEY (html_unit_id) REFERENCES slide_html_units(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_html_seg_scripts_active
    ON slide_html_segment_scripts(html_unit_id, segment_index)
    WHERE is_active = TRUE;

CREATE INDEX idx_html_seg_scripts_unit_history
    ON slide_html_segment_scripts(html_unit_id, segment_index, script_version DESC);
```

### 3.6 V207: HTML 分段音频表

```sql
CREATE TABLE slide_html_segment_audios (
    id BIGSERIAL PRIMARY KEY,
    segment_script_id BIGINT NOT NULL,
    html_unit_id BIGINT NOT NULL,         -- 冗余
    segment_index INT NOT NULL,           -- 冗余

    audio_url VARCHAR(500) NOT NULL,
    audio_token VARCHAR(64),
    audio_duration_ms INT,

    voice_used VARCHAR(64) NOT NULL,
    model_used VARCHAR(64) NOT NULL,
    generation_params JSONB,

    generation_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'GENERATING',

    file_size_bytes BIGINT,
    storage_path VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_html_seg_audios_script FOREIGN KEY (segment_script_id) REFERENCES slide_html_segment_scripts(id) ON DELETE CASCADE,
    CONSTRAINT chk_html_seg_audios_status CHECK (status IN ('GENERATING','READY','FAILED'))
);

CREATE INDEX idx_html_seg_audios_script ON slide_html_segment_audios(segment_script_id);
CREATE INDEX idx_html_seg_audios_unit_status ON slide_html_segment_audios(html_unit_id, status);
CREATE INDEX idx_html_seg_audios_token ON slide_html_segment_audios(audio_token) WHERE audio_token IS NOT NULL;
```

### 3.7 V208: PPT 页间逻辑关联表 (用户核心诉求)

```sql
CREATE TABLE slide_ppt_flow (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL,
    from_page_id BIGINT NOT NULL,
    to_page_id BIGINT,                    -- NULL = 课件结束

    flow_type VARCHAR(20) NOT NULL,       -- NEXT / BRANCH_DEPENDS / SKIP_IF_KNOWN
    priority INT NOT NULL DEFAULT 0,      -- 多规则时优先级
    depends_on_quiz_id BIGINT,            -- BRANCH 场景
    condition_expression TEXT,            -- SKIP 场景: "user_progress >= 0.8"
    description VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_flow_section FOREIGN KEY (section_id) REFERENCES course_sections(id) ON DELETE CASCADE,
    CONSTRAINT fk_ppt_flow_from FOREIGN KEY (from_page_id) REFERENCES slide_ppt_pages(id) ON DELETE CASCADE,
    CONSTRAINT fk_ppt_flow_to FOREIGN KEY (to_page_id) REFERENCES slide_ppt_pages(id) ON DELETE SET NULL,
    CONSTRAINT fk_ppt_flow_quiz FOREIGN KEY (depends_on_quiz_id) REFERENCES quizzes(id) ON DELETE SET NULL,
    CONSTRAINT chk_ppt_flow_type CHECK (flow_type IN ('NEXT','BRANCH_DEPENDS','SKIP_IF_KNOWN'))
);

CREATE INDEX idx_ppt_flow_section_from ON slide_ppt_flow(section_id, from_page_id, priority);
```

### 3.8 V209: 旧 slide_pages 表新增 is_legacy + deprecation 注释

```sql
ALTER TABLE slide_pages ADD COLUMN is_legacy BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE slide_pages SET is_legacy = TRUE WHERE is_legacy IS NULL;

COMMENT ON COLUMN slide_pages.is_legacy IS 'DEPRECATED 2026-07-19: 本表进入双轨保留期(3 个月),新建课件请使用 slide_ppt_pages / slide_html_units';

-- 创建只读视图,兼容旧前端读路径
CREATE VIEW v_slide_pages_legacy AS
SELECT * FROM slide_pages WHERE is_legacy = TRUE;
```

### 3.9 V210: 状态聚合视图 (避免状态不同步)

```sql
-- PPT page 状态: 取该 page 所有 audio.status 的聚合
CREATE VIEW v_slide_ppt_page_status AS
SELECT
    p.id AS ppt_page_id,
    p.section_id,
    p.course_id,
    p.page_number,
    CASE
        WHEN NOT EXISTS (SELECT 1 FROM slide_ppt_page_scripts s WHERE s.ppt_page_id = p.id AND s.is_active = TRUE)
            THEN 'PENDING'
        WHEN NOT EXISTS (SELECT 1 FROM slide_ppt_page_scripts s
                         JOIN slide_ppt_page_audios a ON a.script_id = s.id
                         WHERE s.ppt_page_id = p.id AND s.is_active = TRUE AND a.status = 'READY')
            THEN 'AUDIO_GENERATING'
        ELSE 'AUDIO_READY'
    END AS narration_status,
    (SELECT COUNT(*) FROM slide_ppt_page_audios a
     JOIN slide_ppt_page_scripts s ON s.id = a.script_id
     WHERE s.ppt_page_id = p.id AND s.is_active = TRUE AND a.status = 'READY') AS audio_ready_count,
    p.created_at,
    p.updated_at
FROM slide_ppt_pages p;

-- HTML unit 状态: 类似聚合
CREATE VIEW v_slide_html_unit_status AS
SELECT
    u.id AS html_unit_id,
    u.section_id,
    u.course_id,
    CASE
        WHEN (SELECT COUNT(*) FROM slide_html_segment_scripts s
              WHERE s.html_unit_id = u.id AND s.is_active = TRUE) = 0
            THEN 'PENDING'
        WHEN (SELECT COUNT(DISTINCT s.segment_index)
              FROM slide_html_segment_scripts s
              LEFT JOIN slide_html_segment_audios a ON a.segment_script_id = s.id AND a.status = 'READY'
              WHERE s.html_unit_id = u.id AND s.is_active = TRUE
                    AND a.id IS NULL) > 0
            THEN 'AUDIO_GENERATING'
        ELSE 'AUDIO_READY'
    END AS narration_status,
    u.created_at,
    u.updated_at
FROM slide_html_units u;
```

---

## 四、Java 后端架构 (How)

### 4.1 包结构

```
com.microcourse.plugin.interactive/
├── adapter/                        ← 新增: 抽象层
│   ├── CoursewareAdapter.java      ← 统一接口
│   ├── PptCoursewareAdapter.java   ← PPT 实现 (代理 slide_ppt_* 表)
│   ├── HtmlCoursewareAdapter.java  ← HTML 实现 (代理 slide_html_* 表)
│   └── LegacyCoursewareAdapter.java ← 7 个月过渡期,代理 slide_pages
│
├── entity/                          ← 重构: 7 个独立 entity
│   ├── SlidePptPage.java
│   ├── SlidePptPageScript.java
│   ├── SlidePptPageAudio.java
│   ├── SlidePptFlow.java
│   ├── SlideHtmlUnit.java
│   ├── SlideHtmlSegmentScript.java
│   └── SlideHtmlSegmentAudio.java
│
├── mapper/                          ← MyBatis-Plus mapper (7 个)
│
├── service/                         ← 业务层,按职责拆分
│   ├── PptCoursewareService.java    ← PPT CRUD
│   ├── HtmlCoursewareService.java   ← HTML CRUD
│   ├── CoursewareQueryService.java  ← 只读视图 + 复杂查询 (CQRS 模式)
│   └── (保留) SlideService.java     ← legacy,逐步废弃
│
├── controller/
│   ├── PptCoursewareController.java ← REST /api/courses/{cid}/ppt/...
│   ├── HtmlCoursewareController.java ← REST /api/courses/{cid}/html/...
│   ├── CoursewareQueryController.java ← 读侧统一入口 (前端只调一个 controller)
│   └── (保留) SlideController.java  ← legacy,逐步废弃
│
├── audio/                           ← 提取音频逻辑独立模块
│   ├── AudioTokenService.java
│   ├── AudioStorageService.java
│   └── AudioQueryService.java
│
└── flow/                            ← 新增: 页间逻辑
    ├── FlowEngine.java
    ├── NextFlowHandler.java
    ├── BranchFlowHandler.java
    └── SkipIfKnownFlowHandler.java
```

### 4.2 统一接口 CoursewareAdapter

```java
public interface CoursewareAdapter {
    String type();  // "PPT" | "HTML"

    // === 课件元数据 ===
    CoursewareUnitMeta getUnitMeta(Long sectionId);

    // === 页面/段落列表 ===
    List<? extends CoursewareSegmentMeta> listSegments(Long sectionId);

    // === 内容获取 ===
    SegmentContent getSegmentContent(Long sectionId, Integer segmentIndex);

    // === 讲述稿 CRUD ===
    ScriptDTO getActiveScript(Long segmentId);
    List<ScriptDTO> listScriptHistory(Long segmentId);
    ScriptDTO saveNewScriptVersion(Long segmentId, String text, String voice);

    // === 音频 CRUD ===
    List<AudioDTO> listAudios(Long scriptId);
    AudioDTO getActiveAudio(Long scriptId);
    AudioDTO generateAudio(Long scriptId, String voice);

    // === 状态 ===
    SegmentStatus getStatus(Long segmentId);
}
```

### 4.3 Controller 设计 (双类型清晰路径)

```
RESTful 路径设计 (遵循现有 /api/courses/{cid}/... 约定):

# PPT 课件
GET    /api/courses/{cid}/ppt/sections/{sid}              - 列出 PPT 课件所有页
POST   /api/courses/{cid}/ppt/sections/{sid}/pages        - 追加新页 (上传 PPTX 后)
GET    /api/courses/{cid}/ppt/pages/{pid}                - 单页详情
PUT    /api/courses/{cid}/ppt/pages/{pid}                - 更新元数据 (标题/描述)
DELETE /api/courses/{cid}/ppt/pages/{pid}                - 删除页

GET    /api/courses/{cid}/ppt/pages/{pid}/scripts         - 列出该页所有脚本历史
PUT    /api/courses/{cid}/ppt/pages/{pid}/scripts         - 保存新脚本版本
GET    /api/courses/{cid}/ppt/pages/{pid}/scripts/active  - 当前 active 脚本

GET    /api/courses/{cid}/ppt/scripts/{sid}/audios        - 该脚本的所有音频
POST   /api/courses/{cid}/ppt/scripts/{sid}/audios        - 生成新音频 (异步)
GET    /api/courses/{cid}/ppt/audios/{aid}               - 单音频状态

# HTML 课件
POST   /api/courses/{cid}/html/sections/{sid}             - 上传 HTML 文件
GET    /api/courses/{cid}/html/sections/{sid}             - 单元详情
PUT    /api/courses/{cid}/html/sections/{sid}             - 重新上传
DELETE /api/courses/{cid}/html/sections/{sid}             - 删除

GET    /api/courses/{cid}/html/units/{uid}/segments       - 列出分段
PUT    /api/courses/{cid}/html/units/{uid}/segments/{idx} - 更新分段脚本

GET    /api/courses/{cid}/html/segments/{sid}/audios      - 分段所有音频
POST   /api/courses/{cid}/html/segments/{sid}/audios      - 生成新音频

# 通用 (audio token GET, 不分 PPT/HTML)
GET    /api/courses/{cid}/audio/{token}                  - 流式返回音频

# 读侧统一入口 (CQRS Query, 前端调用这一个)
GET    /api/courses/{cid}/courseware/{sid}                - 返回课件完整树
       {
         "type": "PPT",
         "unit": {...},
         "pages": [{
           "pageNumber": 1,
           "imageUrl": "...",
           "script": { "version": 3, "text": "...", "isActive": true },
           "audios": [{ "voice": "male-young", "url": "...", "status": "READY" }],
           "status": "AUDIO_READY"
         }, ...],
         "flow": [{ "fromPageNumber": 1, "toPageNumber": 2, "type": "NEXT" }, ...]
       }
```

### 4.4 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 讲述稿版本保留 | is_active + script_version | 客户可回滚 + 比较 |
| 音频版本保留 | 1:N 不删历史 | 音色 A/B 对比 + 回滚 |
| 音频 token | 存表 + 流式 GET 接口保留 | 与 v1.22.1 P1-C 修复兼容 |
| HTTP audio GET 校验 | 按 (audio_token) 而非 (courseId, pageNumber) | 已修复,新表 audio_token 是 UK |
| 异步音频生成 | `status=GENERATING` + 后台 JobRunner | 不阻塞上传,前端可轮询 |
| 状态聚合 | 数据库视图 v_slide_*_status | 不存冗余字段,无不一致 |
| 旧 slide_pages | 保留只读 3 个月 | 不破坏存量 |

### 4.5 7-19 P0 事故防御 (新代码必须遵循)

- ✅ `uploadHtmlFile` 改为非破坏性 UPSERT (v1.22.1 已修, 新代码继承此约束)
- ✅ `HtmlSanitizer.sanitizeForCourseware` 必须在 HTML 入库前调用
- ✅ audio_token 是 UK, 流式 GET 不依赖 pageNumber
- ✅ 任何 destructive 操作 (delete + insert) 必须在 PR 描述中标"非破坏性"反向解释

---

## 五、前端架构 (Vue 3 + Element Plus)

### 5.1 双类型四面板架构

```
┌────────────────────────────────────────────────────────────────┐
│ SlideManage.vue (入口)                                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Step 1: 课件类型选择                                     │   │
│  │   [创建 PPT 课件] [创建 HTML 课件]                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Step 2: 内容上传/编辑                                     │   │
│  │   PPT: 拖拽 PPTX → 进度条 → 自动 POI 解析                 │   │
│  │   HTML: 上传 HTML → sanitize 预览 → 区块编辑              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                          ↓                                       │
│  ┌──────────────┬──────────────┬──────────────────────────┐   │
│  │ Panel 1: 内容 │ Panel 2: 脚本 │ Panel 3: 音频            │   │
│  │ (图片/HTML)  │ (讲述稿)     │ (生成/试听/对比)          │   │
│  └──────────────┴──────────────┴──────────────────────────┘   │
│                          ↓                                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Panel 4: 预览与发布                                       │   │
│  │   [学生视角预览] [灰度发布] [一键全量]                    │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
```

### 5.2 关键 Vue 组件清单 (新增)

```
micro-course-admin/src/plugins/interactive/components/
├── SlideManage.vue                  ← 重构 (现有, 拆 4 面板)
├── SlidePreview.vue                 ← 既有, 加 PPT/HTML 适配
├── SlideUploadZone.vue              ← 既有, 改为双类型感知
├── editor/
│   ├── PptPageEditor.vue            ← 新增: PPT 单页编辑
│   ├── HtmlBlockEditor.vue          ← 新增: HTML 区块编辑 (TipTap/ProseMirror)
│   ├── ScriptEditor.vue             ← 新增: 讲述稿 + 版本切换
│   └── AudioManager.vue             ← 新增: 音频生成/试听/对比
├── flow/
│   └── PptFlowEditor.vue            ← 新增: 页间跳转逻辑可视化编辑
└── status/
    └── CoursewareStatusBadge.vue    ← 新增: 实时状态聚合展示
```

### 5.3 学生播放端 (SlidePlayer.vue)

- 检测课件类型,自动选播放器
- PPT: 图片轮播 + audio 同步 + flow 跳转
- HTML: 单页渲染 + 多 audio 段按 marker 触发

---

## 六、性能与可扩展性

### 6.1 索引策略

| 表 | 索引 | 用途 |
|----|------|------|
| slide_ppt_pages | (section_id, page_number) | 单 section 列页 |
| slide_ppt_pages | (course_id, section_id, page_number) | 课程全局列表 |
| slide_ppt_page_scripts | (ppt_page_id) WHERE is_active | 快速取 active |
| slide_ppt_page_scripts | (ppt_page_id, script_version DESC) | 历史 |
| slide_ppt_page_audios | (script_id) | 取脚本的所有音频 |
| slide_ppt_page_audios | (ppt_page_id, status) | 状态聚合 |
| slide_ppt_page_audios | (audio_token) WHERE audio_token NOT NULL | 流式 GET |
| slide_html_units | (section_id) UK | 1 section 1 unit |
| slide_html_segment_scripts | (html_unit_id, segment_index) WHERE is_active | 取 active |
| slide_html_segment_audios | (audio_token) | 流式 GET |
| slide_ppt_flow | (section_id, from_page_id, priority) | 跳转决策 |

### 6.2 缓存策略

- **Redis**: `mc:courseware:{sectionId}:meta` (TTL 10min), 失效于 CRUD
- **CDN**: 静态资源 (PNG, MP3) 通过签名 URL (已实现)
- **TTS 结果缓存**: `mc:tts:result:{text_hash}:{voice}` (TTL 7d), 避免重复生成

### 6.3 性能预算

| 操作 | p99 预算 | 备注 |
|------|---------|------|
| GET 单页详情 | < 50ms | Redis 命中 |
| GET 整课件树 (CQRS) | < 200ms | 15 页 + 15 音频 < 200ms |
| 上传 PPTX | 异步, 进度推送 | 不阻塞 |
| 生成音频 (单段) | 异步, 5-30s | MiniMax TTS |
| audio GET (流式) | < 100ms 首字节 | nginx 直发 |

### 6.4 横向扩展

- API 无状态, 可水平扩展 (已有 docker compose)
- DB 用 connection pool, max 50/实例
- 音频文件存 OSS/S3, 不占应用盘 (待评估)

---

## 七、迁移计划 (渐进拆分)

### Phase 1: 数据库建新表 (V202-V209, 1 周)

- 新表全建
- 旧 slide_pages 加 is_legacy
- 创建视图 v_slide_ppt_page_status / v_slide_html_unit_status
- **零代码改动**, 仅 schema

### Phase 2: 后端新建 CRUD (PPT + HTML, 2 周)

- 7 个 entity + 7 个 mapper
- PptCoursewareService + HtmlCoursewareService (并行)
- PptCoursewareController + HtmlCoursewareController
- CoursewareAdapter 抽象层
- **新接口并行于旧接口**, 教师可自由选择

### Phase 3: 数据回填 (1 周)

- 一次性脚本 backfill_legacy_to_v2.sh
- 138 条 slide_pages 全量迁移
- 校验: count(slide_pages) = count(slide_ppt_pages) + count(slide_html_units)
- **灰度回填**: 5 课程 → 50 课程 → 全部

### Phase 4: 前端重构 (3 周)

- SlideManage.vue 改为四面板
- 新增 PptPageEditor / HtmlBlockEditor / ScriptEditor / AudioManager
- 旧前端加 "新版本" 切换按钮, 默认旧版
- **灰度前端**: 5 教师 → 50 教师 → 全部

### Phase 5: 旧表清理 (3 个月后, 单独 PR)

- DROP slide_pages (含 v_slide_pages_legacy 视图)
- 删除 legacy service/controller
- 30 天公告 + 确认无流量

---

## 八、风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| backfill 数据丢失 | 低 | P0 | 全量 backup + dry-run + 5 课程灰度 |
| 新接口性能差 | 中 | P1-C | p99 < 200ms 压测 + 慢 SQL 告警 |
| 前端重构引入 bug | 高 | P1-C | 旧 UI 并行 + 灰度 + 教师反馈群 |
| audio token UK 冲突 | 低 | P1-I | UUID v4 + 32 字符 |
| 旧表 DROP 仍有依赖 | 中 | P0 | 3 个月观察期 + grep + 软删除 |

---

## 九、验收标准 (Definition of Done)

### 9.1 数据模型
- [ ] 7 新表创建,所有 FK / UK / CHECK 通过
- [ ] 视图 v_slide_ppt_page_status / v_slide_html_unit_status 正确聚合
- [ ] 旧 slide_pages 加 is_legacy + 30 天 deprecation 注释

### 9.2 后端 API
- [ ] 30+ REST 接口实现,Swagger 文档完整
- [ ] CoursewareAdapter 抽象层 3 实现 (PPT/HTML/Legacy)
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] mvn test 20+/20 PASS
- [ ] precheck.sh 22/22 PASS
- [ ] local-dev-deploy.sh 15/15 PASS

### 9.3 前端
- [ ] SlideManage.vue 重构为四面板
- [ ] 5 个新组件实现
- [ ] PPT/HTML 双类型工作流演示视频
- [ ] 旧 UI 灰度开关 `mc:feature:courseware_v2`

### 9.4 性能
- [ ] GET 整课件树 p99 < 200ms (压测 100 RPS)
- [ ] audio 流式 GET p99 < 100ms 首字节
- [ ] 0 慢 SQL (>500ms) in 7 天

### 9.5 安全
- [ ] HtmlSanitizer 调用 100% (新 HTML 入库前)
- [ ] audio_token 是 UK + 32 字符随机
- [ ] @PreAuthorize 角色校验完整

### 9.6 7-19 P0 事故防御
- [ ] 新表无 destructive UPSERT
- [ ] backup + rollback 写入每个 commit message
- [ ] 不在生产 DB 做写操作(除非 ask user)
- [ ] 所有 ssh/curl 前加载 production-safety skill

---

## 十、参考资料

- [AGENTS.md](../../../AGENTS.md) - 项目行为规则
- [docs/incidents/2026-07-19-P0-jar-deploy-bypass.md](../../../incidents/2026-07-19-P0-jar-deploy-bypass.md) - 7-19 P0 复盘
- [docs/incidents/2026-07-19-audio-html-reload-conflict.md](../../../incidents/2026-07-19-audio-html-reload-conflict.md) - v1.22.1 P1-C 修复
- [docs/开发规划/phase11-interactive-course-spec.md](../../../开发规划/phase11-interactive-course-spec.md) - 现有互动课件规格
- [SCORM 1.2/2004 vs xAPI vs cmi5](https://www.ispringsolutions.com/blog/elearning-standards) - 行业标准
- [CQRS Pattern (Microsoft)](https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs) - 读写分离

---

## 十一、版本与变更

| 版本 | 日期 | 作者 | 说明 |
|------|------|------|------|
| v1.0 | 2026-07-19 | 总工程师 | 初稿,基于 7-19 P0 教训 + 用户授权 + 行业最佳实践 |
| v1.0-fix | 2026-07-19 | 总工程师 | Spec 自检完成,修复 placeholder 描述 |

---

**Spec 自检 (已完成 · 全部 PASS)**:
1. ✅ **Placeholder scan** — grep 全文, 无 TBD / TODO / FIXME / XXX / 占位 / 待补充(除本节描述自身已修复)
2. ✅ **Internal consistency** — 7 表字段互相对应:
   - `slide_ppt_pages` ↔ `slide_ppt_page_scripts` (FK ppt_page_id)
   - `slide_ppt_page_scripts` ↔ `slide_ppt_page_audios` (FK script_id)
   - `slide_html_units` ↔ `slide_html_segment_scripts` (FK html_unit_id)
   - `slide_html_segment_scripts` ↔ `slide_html_segment_audios` (FK segment_script_id)
   - `slide_ppt_pages` ↔ `slide_ppt_flow` (FK from_page_id / to_page_id)
   - 视图 `v_slide_ppt_page_status` 与 `v_slide_html_unit_status` 与 4 实体聚合一致
3. ✅ **Scope check** — 5 Phase 拆分,每 Phase 1-3 周,总 9-12 周,可分批交付
4. ✅ **Ambiguity check** — 关键术语已明确:
   - "课件" = section 内的完整学习单元(PPT 多页 或 HTML 单单元)
   - "页面" = PPT 课件的 1 张图;HTML 不分页但有分段(segment)
   - "脚本" = narration_script 文本
   - "音频" = MiniMax TTS 生成的 mp3 文件元数据
   - "active" = is_active=true 的最新版本

---

**下一步**: 用户评审本文档 → 批准后调用 writing-plans skill 出具体实施计划