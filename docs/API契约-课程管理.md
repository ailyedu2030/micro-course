# API 契约 · 课程管理域 (Phase 5+)

> **契约范围**: 本契约覆盖课程管理域全部上线端点 (课程/章节/视频/分类/标签/套件/课时/课件/评价/收藏)
>
> **关联文档**:
> - API 契约 Phase 1 (用户认证/院系/专业/班级/用户): `docs/API契约-Phase1.md`
> - 权限矩阵 v4.0 (端点 × 角色): `docs/权限矩阵.md`
> - 数据字典 v1.1 (表结构): `docs/数据字典.md`

---

## 修订记录

| 版本 | 日期 | 说明 | 作者 |
|------|------|------|------|
| v1.0 | 2026-07-07 | 初稿, 覆盖课程管理域 85+ 端点 (含 V156 lastPublishedAt 字段) | 总工程师 |

---

## 目录

1. 课程 CRUD (`/api/courses`)
2. 章节管理 (`/api/chapters`)
3. 视频管理 (`/api/videos`)
4. 视频流 (`/api/video-stream`)
5. 课程分类 (`/api/course-categories`)
6. 标签 (`/api/tags`)
7. 课程套件 (`/api/course-bundles`)
8. 课时 (`/api/lessons`)
9. 课件/幻灯片 (`/api/courses/{courseId}/slides`)
10. 课程评价 (`/api/courses/{id}/reviews`)
11. 收藏 (`/api/courses/{id}/favorite`)

---

## 1. 课程 CRUD (23 端点)

### 1.1 GET /api/courses
- **权限**: 已认证 (角色自动过滤)
- **请求参数**:
  - `page`: int (default 0)
  - `size`: int (default 20)
  - `title`: String (可选, 模糊)
  - `keyword`: String (可选, 模糊 title 或 teacher.realName)
  - `categoryId`: Long (可选)
  - `teacherId`: Long (可选, ADMIN/ACADEMIC 用)
  - `status`: Integer (可选)
  - `recommended`: Boolean (可选)
  - `difficulty`: Integer (可选 1/2/3)
  - `courseType`: String (可选 VIDEO/INTERACTIVE/OFFLINE)
  - `offerDepartmentId`: Long (可选)
  - `sortBy`: String (studentCount/avgRating/updatedAt)
  - `sortOrder`: String (asc/desc)
- **响应**: `R<PageResult<CourseVO>>`
- **错误码**: 无

### 1.2 GET /api/courses/{id}
- **权限**: 已认证 (数据隔离在 Service)
- **路径参数**: `id`: Long
- **响应**: `R<CourseVO>` (含缓存, 5 分钟 TTL)
- **错误码**:
  - 6001 COURSE_NOT_FOUND
  - 401 (学生看 CLOSED/ARCHIVED 课程)

### 1.3 POST /api/courses
- **权限**: TEACHER / ADMIN
- **请求体**: `CourseCreateRequest`
  - `title`*: String (必填)
  - `categoryId`: Long
  - `teacherId`: Long (TEACHER 自动绑定)
  - `subtitle`, `summary`, `coverUrl`, `description`: String
  - `courseType`: String (VIDEO/INTERACTIVE/OFFLINE, 默认 VIDEO)
  - `price`, `listPrice`: BigDecimal
  - `isFree`: Boolean
  - `freeAccessScope`: String (none/same_department/same_college/same_school)
  - `freeDeptIds`: String (TEXT, JSON 格式)
  - `discountScope`, `discountPercent`: String/Integer
- **响应**: `R<CourseVO>` (status=DRAFT)
- **错误码**:
  - 6008 COURSE_CATEGORY_NOT_FOUND
  - 6003 COURSE_TEACHER_NOT_FOUND
  - 6004 COURSE_INVALID_STATUS

### 1.4 PUT /api/courses/{id}
- **权限**: TEACHER (owner) / ADMIN (Service 层校验)
- **响应**: `R<CourseVO>`
- **错误码**: 6006 COURSE_PUBLISHED_CANNOT_EDIT (已发布课程禁止编辑)

### 1.5 DELETE /api/courses/{id}
- **权限**: TEACHER (草稿) / ADMIN
- **响应**: `R<Void>`
- **错误码**:
  - 6002 COURSE_HAS_ENROLLMENTS (有学生选课时不可删)
  - 6009 COURSE_ARCHIVED

### 1.6 POST /api/courses/{id}/submit (DRAFT → PENDING_REVIEW)
- **权限**: TEACHER (创建者)
- **守卫** (课程状态机):
  - title 非空
  - categoryId 非空
  - coverUrl 非空
  - 至少一个章节
  - **至少一个视频/练习/课件** (S1 修复)
- **响应**: `R<Void>`
- **错误码**:
  - 6005 COURSE_STATUS_TRANSITION_NOT_ALLOWED
  - 400 章节内容缺失

### 1.7 POST /api/courses/{id}/approve (PENDING_REVIEW → APPROVED)
- **权限**: ADMIN / ACADEMIC
- **守卫**: 自审批阻断 (admin 不能审批自己的课程)
- **响应**: `R<Void>`

### 1.8 POST /api/courses/{id}/reject (PENDING_REVIEW → REJECTED)
- **权限**: ADMIN / ACADEMIC
- **请求体**: `RejectRequest { reason*: String (min=10) }` (S2 修复)
- **响应**: `R<Void>`

### 1.9 POST /api/courses/{id}/publish (APPROVED/CLOSED → PUBLISHED)
- **权限**: ADMIN
- **守卫**:
  - 自审批阻断
  - 定价审批通过 (非免费)
  - INTERACTIVE 类型课件就绪
  - **CLOSED→PUBLISHED 须 lastPublishedAt != null** (S3 修复)
- **响应**: `R<Void>`

### 1.10 POST /api/courses/{id}/unpublish (PUBLISHED → CLOSED)
- **权限**: ADMIN
- **响应**: `R<Void>`

### 1.11 PUT /api/courses/{id}/status
- **权限**: ADMIN
- **守卫**: 拒绝 status=1 (PENDING_REVIEW) 和 status=4 (PUBLISHED) (S4 修复, 必须用专用端点)
- **响应**: `R<Void>`

### 1.12 POST /api/courses/{id}/copy
- **权限**: TEACHER / ADMIN
- **响应**: `R<CourseVO>` (含 videoCopied 标记)

### 1.13 POST /api/courses/{id}/cover
- **权限**: TEACHER / ADMIN
- **请求**: multipart/form-data `file`: MultipartFile (≤2MB, JPEG/PNG)
- **响应**: `R<CourseVO>`
- **错误码**: 400 文件过大/魔数校验失败

### 1.14 GET /api/courses/teacher/{teacherId}
- **权限**: TEACHER (本人) / ADMIN / ACADEMIC / STUDENT
- **响应**: `R<List<CourseVO>>`

### 1.15 GET /api/courses/{id}/students
- **权限**: TEACHER (owner) / ADMIN / ACADEMIC
- **响应**: `R<List<EnrollmentVO>>`

### 1.16 GET /api/courses/{id}/stats
- **权限**: TEACHER (owner) / ADMIN / ACADEMIC
- **响应**: `R<CourseStatsVO> { courseId, courseTitle, teacherName, enrollmentCount, completionRate, avgScore }`

### 1.17 PUT /api/courses/{id}/pricing
- **权限**: TEACHER / ADMIN / ACADEMIC
- **请求体**: `CoursePricingRequest { basePrice, freeAccessScope, freeDeptIds, discountPercent, discountScope }`
- **响应**: `R<Void>`

### 1.18 GET /api/courses/{id}/pricing-for-adopter
- **权限**: TEACHER
- **响应**: `R<PricingForAdopterVO>`

### 1.19 GET /api/courses/{id}/my-price
- **权限**: 已认证
- **响应**: `R<CoursePricingInfoVO>`

### 1.20 POST /api/courses/{id}/pricing/submit-review
- **权限**: TEACHER / ADMIN / ACADEMIC
- **响应**: `R<Void>`

### 1.21 POST /api/courses/{id}/pricing/review
- **权限**: ADMIN / ACADEMIC
- **请求参数**: `approved`: boolean, `reason`: String (可选, ≥2 字符)
- **响应**: `R<Void>`

### 1.22 GET /api/courses/pending-review
- **权限**: ADMIN
- **响应**: `R<PageResult<CourseVO>>`

### 1.23 POST /api/courses/batch-approve / batch-reject
- **权限**: ADMIN / ACADEMIC
- **响应**: `R<BatchOperationResult>`

---

## 2. 章节管理 (6 端点)

### 2.1 GET /api/chapters?courseId={id}&page=0&size=20
- **权限**: 已认证
- **响应**: `R<PageResult<ChapterVO>>`

### 2.2 GET /api/chapters/{id}
- **权限**: 已认证
- **响应**: `R<ChapterVO>`

### 2.3 POST /api/chapters
- **权限**: TEACHER / ADMIN
- **请求体**: `ChapterCreateRequest { title*, courseId*, sortOrder*, chapterType, duration }`

### 2.4 PUT /api/chapters/{id}
- **权限**: TEACHER / ADMIN (owner)
- **响应**: `R<ChapterVO>`

### 2.5 DELETE /api/chapters/{id}
- **权限**: TEACHER / ADMIN
- **错误码**: 7001 CHAPTER_NOT_FOUND

### 2.6 PUT /api/chapters/sort
- **权限**: TEACHER / ADMIN
- **请求体**: `List<ChapterSortRequest> { id, sortOrder }`

---

## 3. 视频管理 (11 端点)

### 3.1 GET /api/videos?courseId={id}
- **权限**: 已认证

### 3.2 GET /api/videos/{id}
- **权限**: 已认证 (学生需选课)

### 3.3 POST /api/videos
- **权限**: TEACHER / ADMIN

### 3.4 PUT /api/videos/{id}

### 3.5 DELETE /api/videos/{id}

### 3.6 POST /api/videos/upload
- **请求**: multipart/form-data (file, courseId, chapterId)
- **错误码**: 12001 VIDEO_UPLOAD_INVALID_FORMAT, 12002 VIDEO_UPLOAD_TOO_LARGE

### 3.7 POST /api/videos/batch-upload (新)
- **权限**: TEACHER / ADMIN
- **请求**: multipart/form-data (files[], courseId, chapterId)

### 3.8 POST /api/videos/{id}/retry (新)
- **权限**: TEACHER / ADMIN
- **守卫**: 仅 FAILED(3) 状态可重试
- **响应**: `R<VideoVO>`

### 3.9 GET /api/videos/{id}/analytics (新)
- **权限**: TEACHER / ADMIN
- **响应**: `R<VideoAnalyticsVO> { playCount, uniqueViewers, avgWatchSeconds, completionRate, ... }`

### 3.10 POST /api/videos/{id}/progress
- **请求体**: `VideoProgressReportRequest { videoProgress, videoPosition, totalWatchTime, playbackSpeed, platform, deviceId, confidence }`

### 3.11 GET /api/videos/{id}/progress

---

## 4-11 章节略 (结构同上, 详见权限矩阵 v4.0)

---

## 错误码汇总 (6001-18008)

| 范围 | 模块 | 示例 |
|------|------|------|
| 6001-6009 | 课程 (COURSE_*) | 6001 NOT_FOUND, 6002 HAS_ENROLLMENTS, 6005 STATUS_TRANSITION_NOT_ALLOWED |
| 6501-6503 | 套餐 (BUNDLE_*) | 6501 NOT_FOUND, 6503 PRICE_INVALID |
| 7001-7002 | 章节 (CHAPTER_*) | 7001 NOT_FOUND |
| 9004 | 视频 (VIDEO_*) | 9004 NOT_FOUND |
| 12001-12007 | 视频/评价 | 12001 INVALID_FORMAT, 12005 REVIEW_NOT_FOUND |
| 14001-14002 | 标签 | 14001 NOT_FOUND |
| 16001-16008 | 课件 | 16001 NOT_FOUND |
| 18001-18008 | 课时/审核 | 18001 NOT_FOUND, 18007 REVIEW_PENDING |

---

## 相关变更

- **2026-07-07**: 课程管理域 drift 修复 (course-domain-drift-fix)
  - 4 项 P0 必修 (S1-S4) 已纳入契约守卫说明
  - 权限矩阵 v4.0 同步
  - 数据字典 v1.1 同步 (含 V156 lastPublishedAt 字段)