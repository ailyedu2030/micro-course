# 审查报告 — R9: 权限矩阵一致性审查

## 审查范围
- **后端**: 全部 84 个 Controller 文件的 @PreAuthorize 注解
- **前端**: `micro-course-admin/src/router/index.js` 路由 meta.roles
- **依据**: `docs/权限矩阵.md` v3.2 + `.agents/skills/microcourse/references/permission-matrix.md`
- **安全配置**: `SecurityConfig.java` permitAll/authenticated 路径配置

## 审查结论

| 级别 | 数量 | 说明 |
|------|:----:|------|
| **P0** | 1 | 必须立即修复 |
| **P1-C** | 4 | 客户可感知的不一致 |
| **P1-I** | 6 | 内部仅见问题 |
| **P2** | 3 | 可优化项 |

---

## P0 — 阻塞项（必须修复）

### #1 — ⚠️ 越权风险：已废弃的 `/api/favorites` 端点绕过 STUDENT 角色校验

| 字段 | 值 |
|------|-----|
| **文件:行号** | `CourseFavoriteController.java:72, 80, 90` |
| **问题** | 已废弃的 `POST /api/favorites`、`DELETE /api/favorites/{id}`、`GET /api/favorites/my` 使用 `@PreAuthorize("isAuthenticated()")`，但委托给 `favoriteCourse()`/`unfavoriteCourse()`/`getMyFavorites()`。这些目标方法有 `@PreAuthorize("hasRole('STUDENT')")`，但因**自调用绕过 Spring AOP 代理**，STUDENT 角色检查不会被触发。结果是：**任何已登录用户（TEACHER/ADMIN/ACADEMIC）均可收藏/取消收藏**。 |
| **风险** | 水平越权 — 权限矩阵定义 FAVORITE_COURSE = 仅 STUDENT，但 TEACHER/ADMIN/ACADEMIC 可通过已废弃端点绕过。 |
| **修复建议** | 在废弃端点方法上直接加 `@PreAuthorize("hasRole('STUDENT')")` 替代 `isAuthenticated()`，避免自调用绕过。 |

---

## P1-C — 客户可感知的问题（强烈建议修复）

### #2 — 前端路由 meta.roles 含 ACADEMIC 但后端 API 拒绝 ACADEMIC

| 字段 | 值 |
|------|-----|
| **前端路由** | `/courses/create` (:23), `/courses/:id/edit` (:25), `/chapters` (:28), `/videos` (:29), `/questions` (:33), `/exercises` (:34), `/courses/:courseId/exercises` (:35), `/courses/:courseId/exercises/form` (:36) |
| **问题** | 上述路由 meta.roles 均含 `'ACADEMIC'`，后端对应 API 仅允许 `TEACHER/ADMIN`：课程创建/更新 (`hasAnyRole('TEACHER','ADMIN')`)、章节/视频/题目/练习 CUD (`hasAnyRole('TEACHER','ADMIN')`)。 |
| **影响** | ACADEMIC 用户能看到创建/编辑按钮和页面，但 API 返回 403。用户体验降级。 |
| **修复建议** | 可从 meta.roles 中移除 `'ACADEMIC'`；或后端同步开放权限（需按业务需求决策）。 |

### #3 — NotificationController 允许 TEACHER 发送通知但权限矩阵限定 ADMIN

| 字段 | 值 |
|------|-----|
| **文件:行号** | `NotificationController.java:62` |
| **问题** | `POST /api/notifications` 使用 `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`，但 `docs/权限矩阵.md §2.11` SEND_BROADCAST = 仅 ADMIN。**类型：`[设计偏离]`** |
| **影响** | TEACHER 可广播通知给所有用户，超出矩阵定义范围。 |
| **修复建议** | 若 TEACHER 发送通知是合理需求，更新权限矩阵；否则收紧为 `hasRole('ADMIN')`。 |

### #4 — CourseController.listByTeacher 允许 STUDENT 但权限矩阵未定义

| 字段 | 值 |
|------|-----|
| **文件:行号** | `CourseController.java:130` |
| **问题** | `GET /api/courses/teacher/{teacherId}` 的 @PreAuthorize 含 STUDENT，矩阵 §2.3 READ_TEACHER_COURSES 不含 STUDENT。**类型：`[设计偏离]`** |
| **影响** | STUDENT 可查看教师课程（含已发布），Service 层强制 includeDrafts=false。属合理扩展。 |
| **修复建议** | 在权限矩阵中补充 STUDENT 的 READ_TEACHER_COURSES 权限。 |

### #5 — 前端 /bundles 路由含 ACADEMIC 但 publish/unpublish API 不含 ACADEMIC

| 字段 | 值 |
|------|-----|
| **前端路由** | `/bundles` (:42): `roles: ['TEACHER', 'ADMIN', 'ACADEMIC']` |
| **后端** | `CourseBundleController.java:62-76` publish/unpublish: `hasAnyRole('TEACHER','ADMIN')` |
| **修复建议** | 统一角色配置。 |

---

## P1-I — 内部仅见的问题

### #6 — ServerTimeController 缺少 @PreAuthorize

| 字段 | 值 |
|------|-----|
| **文件:行号** | `ServerTimeController.java:25-33` |
| **问题** | 类/方法级均无 `@PreAuthorize`，仅靠 SecurityConfig `.anyRequest().authenticated()` 兜底。违反"每个 Controller 方法都加 @PreAuthorize"的编码规范（`references/permission-matrix.md §4`）。 |
| **修复建议** | 添加 `@PreAuthorize("isAuthenticated()")`。 |

### #7 — 前端 /favorites 路由与后端 API 角色不一致

| 字段 | 值 |
|------|-----|
| **前端路由** | `/favorites` (:32): `roles: ['ADMIN', 'ACADEMIC']` |
| **后端** | `CourseFavoriteController.java:101`: `hasAnyRole('ADMIN', 'ACADEMIC', 'TEACHER')` |
| **问题** | 前端排除 TEACHER，后端允许 TEACHER。 |
| **修复建议** | 统一前后端配置。 |

### #8 — 类级 @PreAuthorize 的隐性权限风险

| 文件 | 类级角色 |
|------|----------|
| `AdminStatsController.java:24` | `hasAnyRole('ADMIN','ACADEMIC')` |
| `AcademicStatsController.java:25` | `hasAnyRole('ACADEMIC', 'ADMIN')` |
| `SectionController.java:11` | `hasAnyRole('TEACHER','ADMIN')` |
| `PlatformShareConfigController.java:22` | `hasRole('ADMIN')` |
| `BannerPublicController.java:25` | `permitAll()` |
| **问题** | 5 个 Controller 使用类级注解。新增方法时易忘记覆写。目前除 `AdminStatsController.getHealth()` 显式覆写外均正确。仅做记录。 |

### #9 — GET /api/favorites（管理端）未在权限矩阵中记录

| 字段 | 值 |
|------|-----|
| **文件:行号** | `CourseFavoriteController.java:101` |
| **问题** | 管理端分页查询所有收藏记录的端点未出现在权限矩阵中。 |
| **修复建议** | 在矩阵中补充此端点。 |

### #10 — 权限矩阵中 course-bundles/my-enrollment 端点重复

| 字段 | 值 |
|------|-----|
| **文件** | `docs/权限矩阵.md §1.9` |
| **问题** | 同一行记录出现了两次。 |
| **修复建议** | 删除重复行。 |

---

## P2 — 可优化项

### #11 — CourseStudentController 添加/移除学生端点未在矩阵中定义

| 字段 | 值 |
|------|-----|
| **文件** | `CourseStudentController.java:22-38` |
| **问题** | 权限为 `hasAnyRole('TEACHER','ADMIN','ACADEMIC')`，但矩阵中未定义"直接添加学生到课程"的操作（有别于 enrollments 选课流程）。 |
| **修复建议** | 在权限矩阵中补充此操作定义。 |

### #12 — OfflineSessionController 路径 `/api/offline-sessions/{chapterId}/chapters` 语义不佳

| 字段 | 值 |
|------|-----|
| **文件** | `OfflineSessionController.java:40` |
| **问题** | 路径语义奇怪（按章节查会话），且权限矩阵无任何 offline-session 端点的定义。 |
| **修复建议** | 补充矩阵文档。 |

### #13 — 权限矩阵中未记录 `/api/courses/export` 端点

| 字段 | 值 |
|------|-----|
| **文件** | `CourseController.java:264-269` |
| **问题** | `GET /api/courses/export` 权限为 `hasAnyRole('ADMIN','ACADEMIC')`，但矩阵中无此端点。 |
| **修复建议** | 在矩阵中补充。 |

---

## 机械检查结果

| 检查项 | 结果 |
|--------|------|
| 命名约定 | ✅ 全部 Controller 以 `Controller` 结尾 |
| @PreAuthorize 覆盖 | ⚠️ 1 个缺失（ServerTimeController） |
| 类级 @PreAuthorize | ⚠️ 5 个类级（需注意新增方法覆盖） |
| 前端路由 vs API 一致性 | ❌ 多条路由包含后端不支持的 ACADEMIC 角色 |
| 废弃端点权限 | 🔴 自调用绕过 @PreAuthorize（P0） |
| permitAll 路径合理性 | ✅ 全部合理 |
| 权限矩阵 vs 实现 | ⚠️ 3 处 `[设计偏离]` + 多处未记录端点 |

---

## 决策

- [ ] 放行（无 P0 阻塞项，P1/P2 记录到 Phase 6 统一处理）
- [ ] 阻塞（存在 P0 项，需修复后重新审查）
- [x] **混合（有 P0 阻塞项 + P1/P2/`[设计偏离]`项，P0 修复后重新审查，其余记录到 Phase 6）**

### 修复顺序

1. **🔴 #1 — P0**: 修复 `CourseFavoriteController` 自调用绕过 — 废弃端点的 `@PreAuthorize("isAuthenticated()")` 改为 `@PreAuthorize("hasRole('STUDENT')")`
2. **🟡 #2 — P1-C**: 同步前端路由 meta.roles，从 ACADEMIC 页面移除创建/编辑类路由
3. **🟡 #3/#4 — P1-C**: 决定 `[设计偏离]` 去留，更新权限矩阵
4. **⚪ #6-#13 — P1-I/P2**: 记录 Phase 6 统一处理
