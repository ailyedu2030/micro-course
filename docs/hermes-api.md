# Hermes API 使用说明书

> 版本: v3 | 最后更新: 2026-07-13
> Base URL: `https://microcourse.ailyedu.cn/api/hermes/webhook`
> 鉴权: 所有请求携带 `X-API-Key: <your-key>` 请求头
> 响应格式: `{"code":200,"message":"ok","data":...}`

---

## 目录

1. [API Key 管理](#1-api-key-管理)
2. [课程管理](#2-课程管理)
3. [课时独立管理](#3-课时独立管理)
4. [课件管理](#4-课件管理)
5. [讲述稿管理](#5-讲述稿管理)
6. [数据模型](#6-数据模型)
7. [错误码](#7-错误码)
8. [端到端工作流](#8-端到端工作流)

---

## 1. API Key 管理

### 获取当前 Key

```http
GET /api/users/me/api-key
Authorization: Bearer <JWT>
```

返回已脱敏的 Key。首次使用需在平台管理后台生成。

### 刷新 Key

```http
POST /api/hermes/webhook/api-key/refresh
X-API-Key: <current-key>
```

返回 192 字符新 Key。**旧 Key 立即失效**，所有使用旧 Key 的请求将返回 `1001`。

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

返回课程信息 + 章节树 + 课时 + 定价。

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

返回该课程所有课时。

### 创建课时

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

`chapterId` 必须属于该课程（自动校验）。

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

### 删除课时

```http
DELETE /courses/{hermesCourseId}/sections/{sectionId}
X-API-Key: <your-key>
```

---

## 4. 课件管理

### 上传课件（幂等）

```http
POST /courses/{hermesCourseId}/lessons/{lessonId}/slide
X-API-Key: <your-key>
Content-Type: multipart/form-data

file=@课件.pptx
```

- `lessonId` = `course_sections.id`
- 支持 `.pptx`（≤50MB）、`.html` / `.htm`（≤5MB）
- **幂等**：同一课时重复上传，slide_id 不变，旧内容替换
- **上传后自动**：
  - `course_slides.section_id` → `lessonId`
  - `course_sections.content_url` → `/api/courses/{cid}/slides/pages`
  - `SectionDTO.hasSlide` → `true`
  - `SectionDTO.slideCount` → 更新统计

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

---

## 5. 讲述稿管理

### 推送讲述稿

```http
POST /courses/{hermesCourseId}/scripts
X-API-Key: <your-key>
Content-Type: application/json

{
  "scriptContent": "完整讲述稿全文，系统自动按页面数切分后写入每页 narrationScript"
}
```

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

## 7. 错误码

| HTTP 状态 | code | 含义 | 处理建议 |
|-----------|------|------|----------|
| 200 | 200 | 成功 | — |
| 401 | 1001 | API Key 无效或缺失 | 检查请求头；尝试 `POST /api-key/refresh` 重新生成 |
| 403 | NO_PERMISSION | 权限不足 | teacherId 不匹配 / 课时不属该课程 |
| 404 | 6001 | 课程不存在 | 检查 ID 是否正确 |
| 404 | 7101 | 课时不存在 | 检查 sectionId 是否正确 |
| 400 | 9005 | 参数错误 | 文件格式/大小/课时归属等 |
| 409 | — | 乐观锁冲突 | **重试即可**（并发修改保护） |
| 500 | 500 | 服务器错误 | 检查日志；重试 |

---

## 8. 端到端工作流

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
