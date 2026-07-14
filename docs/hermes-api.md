# Hermes API 使用说明书

> 版本: v4.3 | 最后更新: 2026-07-14
> Base URL: `https://microcourse.ailyedu.cn/api/hermes/webhook`
> 鉴权: 所有请求携带 `X-API-Key: <your-key>` 请求头
> 响应格式: `{"code":200,"message":"ok","data":...}`
> **安全**: 所有写操作均验证调用者与课程的归属关系（API Key 持有者 = 课程创建教师）

---

## 目录

1. [API Key 管理](#1-api-key-管理)
2. [课程管理](#2-课程管理)
3. [课时独立管理](#3-课时独立管理)
4. [章节独立管理](#4-章节独立管理)
5. [课件管理](#5-课件管理)
6. [讲述稿管理](#6-讲述稿管理)
7. [数据模型](#7-数据模型)
8. [错误码](#8-错误码)
9. [端到端工作流](#9-端到端工作流)

---

## 1. API Key 管理

### 获取当前 Key

```http
GET /api/auth/me/api-key
Authorization: Bearer <JWT>
```

返回已脱敏的 Key。首次使用需在平台管理后台生成。

### 刷新 Key

```http
POST /api/hermes/webhook/api-key/refresh
X-API-Key: <current-key>
```

返回 192 字符新 Key。**旧 Key 立即失效**，所有使用旧 Key 的请求将返回 `21001`。

> **注意**: 刷新 Key 后，所有正在使用旧 Key 的客户端需要同步更新。

---

## 2. 课程管理

### 创建/更新课程（幂等）

**按 `title + sortOrder` 匹配已有章节/课时。匹配则复用 ID，slide 不丢失。**

```http
POST /courses
X-API-Key: <your-key>
Content-Type: application/json
```

**请求体（完整字段）**：

```json
{
  "hermesCourseId": "course-unique-id",
  "title": "课程标题",
  "subtitle": "副标题",
  "summary": "课程简介",
  "coverUrl": "https://.../cover.png",
  "categoryId": 1,
  "teacherId": 2,
  "offerDepartmentId": 1,
  "semester": "2026秋季",
  "courseNature": "专业选修",
  "maxStudents": 200,
  "difficulty": 3,
  "description": "<p>富文本描述</p>",
  "tags": "AI,工具链",
  "courseType": "VIDEO",
  "pricing": {
    "isFree": true,
    "price": null,
    "freeAccessScope": null,
    "freeDeptIds": null
  },
  "chapters": [
    {
      "title": "第一章",
      "sortOrder": 1,
      "lessons": [
        {
          "title": "1.1 概述",
          "type": "VIDEO",
          "durationMinutes": 45,
          "sortOrder": 1,
          "scriptContent": "讲述稿..."
        }
      ]
    }
  ]
}
```

**字段规则**：

| 字段 | 必填 | 说明 |
|------|------|------|
| `hermesCourseId` | ✅ | 唯一标识，重复推送时用于匹配 |
| `title` | ✅ | 课程标题 |
| `categoryId` | ✅ | 分类 ID（从平台后台获取） |
| `teacherId` | ❌ | **可省略**。省略时自动使用 API Key 持有者身份。若提供则必须与 API Key 一致 |
| `chapters[].lessons[].type` | ✅ | `VIDEO` / `INTERACTIVE` / `OFFLINE` / `EXERCISE` |
| 其余字段 | ❌ | 可按需省略 |

**幂等规则**：

| 场景 | 行为 |
|------|------|
| 首次推送 | 创建 course + mapping + 章节 + 课时 |
| 重复推送相同数据 | 匹配已有记录，保留 ID，slide 不丢失 |
| 推送时删除一个章节/课时 | 数据库级联删除 |
| 修改 `title` 或 `sortOrder` | 被视为新记录，旧记录删除 |

---

### 查询课程列表

```http
GET /courses
X-API-Key: <your-key>
```

返回该教师**所有**课程（含非 Hermes 创建的）。`hermesCourseId` 为 `null` 表示无映射。

---

### 查询平台全部课程

```http
GET /courses/all
X-API-Key: <your-key>
```

ADMIN 可见全部，教师仅见自己。每行标注 `hermesCourseId`。

---

### 查询课程详情

```http
GET /courses/{hermesCourseId}
X-API-Key: <your-key>
```

返回课程信息 + 章节树 + 课时 + 定价 + **讲述稿（narrationScript）**。
讲述稿按 sectionId 分组，单次查询无 N+1（`batchResolveNarrationScripts` 批量加载）。

---

### 彻底删除（有映射）

```http
DELETE /courses/{hermesCourseId}
X-API-Key: <your-key>
```

**级联删除**：`mapping → course → chapters → sections → slides → slide_pages`。
删除后可重新 `POST /courses` 重建。

**事务安全**：级联删除包含事务保护，中途异常时回滚。

---

### 按内部 ID 删除（不依赖映射）

```http
DELETE /courses/by-id/{courseId}
X-API-Key: <your-key>
```

用于删除管理后台直接创建的课程。仅课主或 ADMIN 可操作。

---

## 3. 课时独立管理

**无需全量推送课程，直接对课时增删改查。**

### 列出课时

```http
GET /courses/{hermesCourseId}/sections
X-API-Key: <your-key>
```

返回该课程所有课时。**验证归属**：调用者必须是该课程 owner（API Key 持有者 = 教师本人）或 ADMIN。

```http
POST /courses/{hermesCourseId}/sections
X-API-Key: <your-key>
Content-Type: application/json

{
  "chapterId": 99,
  "title": "课时标题",
  "sectionType": "VIDEO",
  "sortOrder": 3,
  "duration": 30,
  "visible": true,
  "description": "描述",
  "scriptContent": "讲述稿"
}
```

`chapterId` 必须属于该课程（自动校验）。**验证归属**：调用者必须是该课程 owner。

### 更新课时

```http
PUT /courses/{hermesCourseId}/sections/{sectionId}
X-API-Key: <your-key>
Content-Type: application/json

{
  "title": "新标题",
  "sectionType": "INTERACTIVE",
  "duration": 35,
  "scriptContent": "新讲述稿"
}
```

**验证归属**：sectionId 必须属于该课程。

### 删除课时

```http
DELETE /courses/{hermesCourseId}/sections/{sectionId}
X-API-Key: <your-key>
```

**验证归属**：sectionId 必须属于该课程。级联删除该 section 下所有 slide 文件（存储 + DB）。

---

## 4. 章节独立管理

### 更新章节

```http
PATCH /courses/{hermesCourseId}/chapters/{chapterId}
X-API-Key: <your-key>
Content-Type: application/json

{
  "title": "新章节标题",
  "sortOrder": 2,
  "description": "章节描述",
  "duration": 45,
  "learningObjectives": "[\"掌握Python基础\",\"理解函数概念\"]"
}
```

### 删除章节

```http
DELETE /courses/{hermesCourseId}/chapters/{chapterId}
X-API-Key: <your-key>
```

**验证归属**：chapterId 必须属于该课程。
> ⚠️ 删除章节会**级联删除**旗下所有课时（含课件和 slide pages），不可恢复。

---

## 5. 课件管理

### 上传课件（幂等）

```http
POST /courses/{hermesCourseId}/lessons/{lessonId}/slide
X-API-Key: <your-key>
Content-Type: multipart/form-data

file=@课件.pptx
```

- `lessonId` = `course_sections.id`
- 支持 `.pptx`（≤50MB）、`.html` / `.htm`（≤5MB）
- **UPSERT 匹配**：按 `(courseId, chapterId, sectionId)` 三字段匹配。**每课时独立一个 slide**，同一章节下的不同课时不互相覆盖
- **chapterId 跨课程校验**：若提供，必须属于该课程；否则从 section 反查 chapterId
- **上传后自动**：
  - `course_sections.content_url` → `/api/courses/{cid}/sections/{sectionId}/slide`（课时级独有端点）
  - `SectionDTO.hasSlide` → `true`
  - `SectionDTO.slideCount` → 更新统计
  - 上传新文件时自动清理旧存储文件（`afterCommit` 回调）

**响应**：

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "slideId": 160,
    "totalPages": 0,
    "status": 0,
    "message": "上传成功，正在后台渲染..."
  }
}
```

### 查看课件列表

```http
GET /courses/{hermesCourseId}/slides
X-API-Key: <your-key>
```

返回该课程所有 slide（含 sectionId、chapterId、文件名、状态、总页数）。

### 查看课件页面（按课时）

```http
GET /courses/{hermesCourseId}/lessons/{lessonId}/slides/pages
X-API-Key: <your-key>
```

返回指定课时（`lessonId` = `course_sections.id`）下的所有 slide page，包含 `narrationScript`、`pageNumber`、`imageUrl` 等字段。
**验证归属**：lessonId 必须属于该课程。

### 更新页面讲述稿（单页编辑）

```http
PATCH /courses/{hermesCourseId}/lessons/{lessonId}/slides/pages/{pageNumber}
X-API-Key: <your-key>
Content-Type: application/json

{
  "narrationScript": "该页讲述稿内容"
}
```

**验证归属**：lessonId 必须属于该课程。更新时传入 `narrationAudioUrl` 非空且状态为 `AUDIO_READY` 时自动清除旧音频。

### 删除课件页面

```http
DELETE /courses/{hermesCourseId}/lessons/{lessonId}/slides/pages/{pageNumber}?sectionId={sectionId}
X-API-Key: <your-key>
```

- `sectionId` 参数可选，用于精确定位（当同一 pageNumber 存在于多个 section 时）
- **验证归属**：lessonId 必须属于该课程；sectionId（若提供）也必须属于该课程
- 删除后重新排序剩余页面（pageNumber 连续）

---

## 6. 讲述稿管理

### 推送讲述稿

```http
POST /courses/{hermesCourseId}/scripts
X-API-Key: <your-key>
Content-Type: application/json

{
  "scriptContent": "完整讲述稿全文，系统自动按页面数切分后写入每页 narrationScript",
  "sectionId": 582   // 可选：指定课时，则仅更新该课时页面
}
```

**业务规则**：
- `sectionId` 可选：若提供，仅更新该课时（`course_sections.id = sectionId`）下的所有页面
- 若不提供 `sectionId`，则**必须提供 `chapterId`**（`chapterId` 必须属于该课程），系统按 `pageCount` 均分脚本，写入该 chapter 下所有页面
- `scriptContent` 必须是 String 类型；非字符串返回 `400`
- 脚本字数 < 页面数时返回 `400`（防止数据丢失）
- **验证归属**：调用者必须是该课程 owner（`HermesCourseMapping` 校验）
- **安全规则**：既无 `sectionId` 也无 `chapterId` 时返回 `400`（防止跨章节数据串写）

### 查看课时课件

```http
GET /api/courses/{courseId}/sections/{sectionId}/slide
```

返回该课时的独立课件页面列表（含 HTML 内容、图片 URL 等）。需要 JWT 认证（非 Hermes 接口，Hermes 可通过 `GET /courses/{hermesId}/slides` 获取 slide 元数据）。

---

## 6. 数据模型

```
hermes_course_mapping     ← hermesCourseId ↔ courseId，DELETE 时级联删除
  └── courses              ← 课程主表
       ├── course_chapters  ← 章节
       │    └── course_sections  ← 课时（sectionType, contentUrl, slideCount）
       │         └── course_slides ← 课件（按 section 1:1）
       │              └── slide_pages ← 课件页面
       └── course_slides    ← 课程级课件（无关联 section）
```

**SectionDTO 扩展字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `contentUrl` | String | 课件 API 路径，非空表示有课件 |
| `hasSlide` | Boolean | `true` 表示有时课 |件 |

---

## 8. 错误码

| HTTP 状态 | code | 含义 | 处理建议 |
|-----------|------|------|----------|
| 200 | 200 | 成功 | — |
| 401 | 21001 | API Key 无效或缺失 | 检查请求头；尝试 `POST /api-key/refresh` 重新生成 |
| 403 | NO_PERMISSION | 权限不足 | API Key 持有者不是课程 owner |
| 403 | 10003 | 课程不存在（归属校验） | 课程不存在或无权限访问 |
| 404 | 6001 | 课程不存在 | 检查 ID 是否正确 |
| 404 | 7001 | 章节不存在 | 检查 chapterId 是否正确 |
| 404 | 7101 | 课时不存在 | 检查 sectionId 是否正确 |
| 404 | 16004 | 课件页面不存在 | 检查 pageNumber 是否正确 |
| 400 | 9005 | 参数错误 | 文件格式/大小/跨课程 chapterId/sectionId |
| 400 | 11001 | scriptContent 非 String | batchPushScripts 的 scriptContent 必须是字符串 |
| 400 | 11002 | 脚本字数不足 | scriptContent 长度 < 页面数，防止数据丢失 |
| 409 | CONCURRENT_MODIFICATION | 乐观锁冲突 | **重试即可**（并发修改保护）；常见于课时被同时编辑 |
| 500 | 500 | 服务器错误 | 检查日志；重试 |

---

## 9. 端到端工作流

```bash
KEY="your-api-key"
BASE="https://microcourse.ailyedu.cn/api/hermes/webhook"

# ── 1. 刷新 Key ──
curl -X POST "$BASE/api-key/refresh" -H "X-API-Key: $KEY"

# ── 2. 查看已有课程 ──
curl "$BASE/courses" -H "X-API-Key: $KEY"

# ── 3. 推送课程 ──
curl -X POST "$BASE/courses" -H "X-API-Key: $KEY" \
  -H "Content-Type: application/json" -d @course.json

# ── 4. 查看课程详情 ──
curl "$BASE/courses/{hermesId}" -H "X-API-Key: $KEY"

# ── 5. 查看课时列表 ──
curl "$BASE/courses/{hermesId}/sections" -H "X-API-Key: $KEY"

# ── 6. 独立创建课时 ──
curl -X POST "$BASE/courses/{hermesId}/sections" -H "X-API-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"chapterId":99,"title":"实战案例","sectionType":"VIDEO","sortOrder":1}'

# ── 7. 上传课件 ──
curl -X POST "$BASE/courses/{hermesId}/lessons/{lessonId}/slide" \
  -H "X-API-Key: $KEY" -F "file=@课件.pptx"

# ── 8. 查看课件列表 ──
curl "$BASE/courses/{hermesId}/slides" -H "X-API-Key: $KEY"

# ── 9. 推送讲述稿 ──
curl -X POST "$BASE/courses/{hermesId}/scripts" -H "X-API-Key: $KEY" \
  -H "Content-Type: application/json" -d '{"scriptContent":"..."}'

# ── 10. 删除课程 ──
curl -X DELETE "$BASE/courses/{hermesId}" -H "X-API-Key: $KEY"

# ── 11. 按 ID 删除（无映射时使用）──
curl -X DELETE "$BASE/courses/by-id/42" -H "X-API-Key: $KEY"
```

---

## 附录：变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-13 | v1 | 初始版本 |
| 2026-07-13 | v2 | 新增课时 CRUD、DELETE 级联、UPSERT 幂等、contentUrl/hasSlide |
| 2026-07-13 | v3 | 新增 API Key 刷新、slide 列表（API Key 鉴权）、@Transactional/所有权校验 |
| 2026-07-13 | v4 | upload(slide) 改为按 (courseId, chapterId, sectionId) UPSERT，每课时独立 slide |
| 2026-07-13 | v4.1 | content_url 回写移至 SlideServiceImpl 内（与上传同事务，@Version 不冲突） |
| 2026-07-13 | v4.2 | content_url 改为课时级 `/sections/{id}/slide`；新增 `GET /sections/{id}/slide` 端点 |
| 2026-07-14 | v4.3 | batchPushScripts 归属校验 + instanceof String 校验 + 脚本字数 < pageCount 校验；getCourseDetail 批量加载讲述稿（无 N+1）；章节/课时 CRUD 全量归属校验；deletePage 支持 sectionId 参数；slide 存储文件清理（afterCommit）；N+1 category 批量加载 |
