# 课程管理域 · Spec 漂移总表

> 审计日期：2026-07-07
> 审计范围：5 份 spec (数据字典/API契约/权限矩阵/状态机/开发规范) × 课程管理域
> 审计依据：5 份 spec 文档 vs 实际 Java/Vue/SQL 实现
> 漂移类型：**M** = 代码有/spec 无；**D** = spec 有/代码无；**S** = 两边都有但不一致；**F** = 实现方式漂移；**G** = 守卫缺失

---

## 1. 数据字典漂移（11 项）

| # | 表/字段 | 数据字典声称 | 实际 (DB/Entity) | 类型 | 严重度 |
|---|--------|------------|------------------|------|--------|
| 1 | courses.freeDeptIds | JSONB (V111) | TEXT (V110 改 JSONB→TEXT) | S | P2 |
| 2 | courses 索引 idx_courses_is_recommended | 未登记 | V20 创建 | M | P1-I |
| 3 | courses 索引 idx_courses_teacher_deleted | 未登记 | V60 创建 | M | P1-I |
| 4 | courses 索引 idx_courses_status_deleted | 未登记 | V60 创建 | M | P1-I |
| 5 | courses CHECK chk_courses_status | 未登记 | V153 创建 (status IN 0..6) | M | P1-I |
| 6 | courses CHECK chk_courses_difficulty | 未登记 | V153 创建 (difficulty IN 1..3) | M | P1-I |
| 7 | courses CHECK chk_courses_course_type | 未登记 | V153 创建 (course_type IN VIDEO/INTERACTIVE/OFFLINE) | M | P1-I |
| 8 | course_chapters.uk_cc_course_sort | UNIQUE(course_id, sort_order) | V144 已删除此约束 | D | P2 |
| 9 | course_prerequisites.deletedAt | 字典记录 | V80 未添加, 实际不存在 | D | P2 |
| 10 | course_slides.chapterId | 未登记 | V143 新增 | M | P1-I |
| 11 | course_review_logs.previousStatus/newStatus | INTEGER 长度 1 | SMALLINT | S | P1-I |
| 12 | videos.originalName | NOT NULL | 可空 (V25 仅改名, 未加 NOT NULL) | S | P1-I |
| 13 | course_tag_relations 唯一索引 | idx_ctr_unique | uk_ctr_course_tag | S | P2 |
| 14 | course_bundle_items.updatedAt | DEFAULT NULL | 无显式 DEFAULT | S | P2 |

---

## 2. API 契约漂移（**结构性缺失** — 整域未覆盖）

**核心发现**：`docs/API契约-Phase1.md` 完全不包含课程管理域的任何端点。该文档范围限定为 Phase 1 (用户认证/院系/专业/班级/用户)。

| 控制器 | 实际端点数 | 契约覆盖数 | 缺失 (M) |
|--------|-----------|-----------|---------|
| CourseController | 23 | 0 | 23 |
| CourseChapterController | 6 | 0 | 6 |
| VideoController | 11 | 0 | 11 |
| VideoStreamController | 1 | 0 | 1 |
| CourseCategoryController | 5 | 0 | 5 |
| TagController | 7 | 0 | 7 |
| CourseBundleController | 10 | 0 | 10 |
| LessonController | 6 | 0 | 6 |
| SlideController (plugin) | 11 | 0 | 11 |
| CourseReviewController | 5 | 0 | 5 |
| **合计** | **85** | **0** | **85 (100%)** |

**错误码对比**：

| 范围 | 实际错误码 (ErrorCode.java) | 契约覆盖 |
|------|------------------------|---------|
| 1001-5004 | 认证/院系/专业/班级/用户 (Phase 1) | ✅ 全覆盖 |
| 6001-6009 | 课程管理 (COURSE_*) | ❌ 全缺失 |
| 6501-6503 | 课程套餐 (BUNDLE_*) | ❌ 全缺失 |
| 7001-7002 | 章节 (CHAPTER_*) | ❌ 全缺失 |
| 9004 | 视频 (VIDEO_*) | ❌ 全缺失 |
| 12001-12007 | 视频/评价 | ❌ 全缺失 |
| 14001-14002 | 标签 (TAG_*) | ❌ 全缺失 |
| 16001-16008 | 幻灯片 (SLIDE_*) | ❌ 全缺失 |
| 18001-18008 | 课时/审核 (LESSON_*, REVIEW_*) | ❌ 全缺失 |

---

## 3. 权限矩阵漂移（11 项）

| # | 端点 | 矩阵声称 | 实际 @PreAuthorize | 类型 | 严重度 |
|---|------|---------|-------------------|------|--------|
| 1 | POST /api/courses/{id}/submit | 仅 TEACHER(创建者) | TEACHER, ADMIN | R (越权) | **P1-C** |
| 2 | POST /api/courses/{id}/favorite | 仅 STUDENT | isAuthenticated() (所有人) + 路径漂移 /api/favorites | R+P | **P1-C** |
| 3 | GET /api/courses/favorites | STUDENT | 路径漂移至 /api/favorites/my | P | **P1-C** |
| 4 | GET /api/courses/teacher/{teacherId} | 矩阵有 | **不存在** | E (缺失) | **P1-C** |
| 5 | POST /api/videos/{id}/retry | 矩阵有 (重试转码) | **不存在** | E | **P1-C** |
| 6 | GET /api/videos/{id}/analytics | 矩阵有 (播放分析) | **不存在** | E | **P1-C** |
| 7 | POST /api/videos/batch-upload | 矩阵有 (批量上传) | **不存在** (仅单文件 upload) | E | **P1-C** |
| 8 | POST /api/courses/{id}/reviews | 仅 STUDENT | STUDENT, ADMIN | R | **P1-C** |
| 9 | DELETE /.../reviews/{reviewId} | 仅 ADMIN | ADMIN, ACADEMIC | R | **P1-C** |
| 10 | PUT /api/chapters/sort | PUT /api/chapters/{id}/sort | PUT /api/chapters/sort (集合级) | P | P1-I |
| 11 | POST /api/courses/{id}/chapters | POST /api/courses/{id}/chapters | POST /api/chapters (courseId in body) | P | P1-I |

**未覆盖模块**（需补充到权限矩阵 v4.0）：
- 课程分类管理 (CourseCategoryController, 5 端点)
- 课时管理 (LessonController, 6 端点)
- 课件/幻灯片 (SlideController, 11 端点)
- 定价系列 (updatePricing/submit-review/review/pricing-for-adopter/my-price, 5 端点)
- 批量审核 (batch-approve/batch-reject, 2 端点)
- 状态变更/复制/封面 (status/copy/cover, 3 端点)

---

## 4. 状态机漂移（3 个 P0 + 3 个 P1-I）

### P0 缺陷（必修）

| # | 转换 | 状态机设计 | 实际实现 | 类型 | 后果 |
|---|------|----------|---------|------|------|
| **S1** | DRAFT → PENDING_REVIEW | 5 项校验（含至少一个视频/练习） | 4 项校验（缺失"章节下有视频/练习"） | G | 教师可提交空内容课程 |
| **S2** | PENDING_REVIEW → REJECTED | 驳回原因 ≥ 10 字符 | 无最小长度校验 | G | 管理员可空字符串驳回 |
| **S3** | CLOSED → PUBLISHED | "此前为 PUBLISHED" 前提 | 无历史校验 | G | 草稿或驳回课程可绕过审核直接发布 |
| **S4** | 通用 updateStatus() → PUBLISHED | 应走 publish() 完整流程 | 通用端点绕过所有业务守卫 | G | 通过 PUT /api/courses/{id}/status?status=4 直接发布，跳过定价/课件/插件检查 |

### P1-I 缺陷（实现漂移）

| # | 位置 | 应使用 | 实际使用 |
|---|------|-------|---------|
| S5 | approve() L113 | canTransitionTo(APPROVED) | WHERE status=PENDING_REVIEW 原始 SQL |
| S6 | reject() L143 | canTransitionTo(REJECTED) | WHERE status=PENDING_REVIEW 原始 SQL |
| S7 | publish() L198 | canTransitionTo(PUBLISHED) | WHERE status IN (APPROVED, CLOSED) |

---

## 5. 开发规范违反（12 项）

| # | 规则 | 位置 | 内容 | 严重度 |
|---|------|------|------|--------|
| **V1** | 状态字段使用枚举 | `CourseServiceImpl.java:237-238` | 硬编码 `status=5` + 注释错标 PENDING_REVIEW | **P1-C** (实际查 CLOSED 而非 PENDING) |
| V2 | Controller 不写业务逻辑 | `CourseController.updateCover()` L347-356 | 文件大小 + 魔数校验在 Controller | P1-I |
| V3 | Controller 不写业务逻辑 | `CourseController.getCourseStudents()` L289-292 | 角色条件判断在 Controller | P1-I |
| V4 | Controller 不写业务逻辑 | `VideoController.uploadCover()` L154-163 | 文件大小 + contentType 校验在 Controller | P1-I |
| V5 | Controller 不写业务逻辑 | `VideoController.reportVideoProgress()` L192-206 | 手动 DTO 组装 + 解析 | P1-I |
| V6 | Controller 不应包含工具方法 | `VideoController.asInt()` L209-222 | 工具方法定义在 Controller | P2 |
| V7 | 状态字段使用枚举 | `CourseServiceImpl.java:158` | 硬编码 `status==4` 应使用 `CourseStatus.PUBLISHED.getCode()` | P1-I |
| V8 | API 响应统一格式 | `R.java` | 多出 `timestamp` 字段不在契约 `{code, message, data}` 中 | P1-I |
| V9 | 契约优先 | `VideoController.reportVideoProgress()` L193 | `@RequestBody Map<String, Object>` 无 DTO 约束 | P1-I |
| V10 | 增量规则 (N+1) | `CourseAdminServiceImpl.convertToVO()` | 循环内 selectById 产生 N+1 | P2 |

### V1 详情（**P1-C** — 关键业务错误）

```java
// CourseServiceImpl.java:237-238
.eq(Course::getStatus, 5) // PENDING_REVIEW  ← 注释说 PENDING_REVIEW
```

`CourseStatus` 枚举: `PENDING_REVIEW=1, CLOSED=5`。该方法 (checkReviewTimeout @Scheduled) 意图"待审核超时检查"，但因硬编码 `5` 实际查的是 CLOSED(下架) 课程。

**业务影响**：
- 待审核课程超时永远检测不到
- 已下架课程错误收到催办通知
- 教师提交审核后无人提醒

---

## 6. 漂移总计

| 维度 | 漂移项 | P0 | P1-C | P1-I | P2 |
|------|--------|:--:|:----:|:----:|:--:|
| 数据字典 | 14 | 0 | 0 | 7 | 7 |
| API 契约 | 85 (整域缺失) | 0 | 0 | 85 | 0 |
| 权限矩阵 | 11 | 0 | 8 | 3 | 0 |
| 状态机 | 7 | 4 | 0 | 3 | 0 |
| 开发规范 | 10 | 0 | 1 | 7 | 2 |
| **合计** | **127** | **4** | **9** | **105** | **9** |

---

## 7. 必修缺陷清单（按优先级）

### P0 必修（4 项）

| # | 缺陷 | 修复方向 | 阻塞性 |
|---|------|---------|--------|
| S1 | DRAFT→PENDING_REVIEW 缺失"章节下有视频/练习"校验 | CourseAuditServiceImpl.submitForReview() 加 SELECT EXISTS 校验 | 阻塞课程审核 |
| S2 | reject() 缺失驳回原因最小长度校验 (建议 ≥ 10) | RejectRequest @Size(min=10) | 阻塞驳回流程 |
| S3 | CLOSED→PUBLISHED 缺失"此前为 PUBLISHED"校验 | publish() 增加历史状态校验或字段 | 阻塞发布安全 |
| S4 | updateStatus() 通用端点绕过 publish() 完整流程 | 阻断 status=4 通过通用端点，或重定向到 publish() | 严重安全绕过 |

### P1-C 必修（9 项）

| # | 缺陷 | 修复方向 |
|---|------|---------|
| V1 | CourseServiceImpl.java:237 硬编码 status=5 错标 PENDING_REVIEW | 改为 `CourseStatus.PENDING_REVIEW.getCode()` |
| 1 | submit 端点 ADMIN 越权 | 移除 ADMIN 角色或拆分到 admin-update |
| 2-3 | 收藏端点路径漂移 + 权限过宽 | 统一路径到 /api/courses/{id}/favorite 并限制 STUDENT |
| 4 | GET /api/courses/teacher/{teacherId} 端点缺失 | 确认是否主动废弃或实现 |
| 5-7 | 视频重试/分析/批量上传端点缺失 | 确认是否主动废弃或实现 |
| 8 | POST reviews ADMIN 越权 | 移除 ADMIN |
| 9 | DELETE reviews 角色不一致 | 统一为仅 ADMIN |

### P1-I 待处理（105 项）

主要是文档同步（API 契约 85 项 + 数据字典 7 项 + 状态机 3 项实现漂移 + 规范 7 项）

---

## 8. 处理方案（按 Plan B）

```
[Step 1] P0 必修 — 立即修复
  - 4 项 P0 全部修复 + 回归测试
  - 1 项 P1-C (V1 硬编码) 同步修复
  - 输出: 5 个修复 commit

[Step 2] P1-C 必修 — 排期修复
  - 8 项权限漂移修复
  - 输出: 修复 commit + 权限矩阵 v4.0

[Step 3] 文档同步 — P1-I 集中处理
  - 创建 docs/API契约-课程管理.md (85 端点)
  - 数据字典 v0.6 (14 项差异修复)
  - 权限矩阵 v4.0 (新增 4 模块 + 修复 11 项)
  - 状态机设计 v1.1 (新增 3 项守卫)
  - 开发规范 v1.5 (新增 3 项禁止)
  - 输出: 5 份文档同步 PR

[Step 4] TC 设计 — 基于对齐后的 spec
  - 输出: 《课程管理域测试单元总表》 (225+ TC)
```

---

**审计执行者**: AI Agent (MiniMax-M3)
**审计完成时间**: 2026-07-07
**下一步**: 等用户确认处理顺序后开始执行修复