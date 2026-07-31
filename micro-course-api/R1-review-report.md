# 审查报告 — R1: 后端代码质量 + 契约对齐审查

## 审查范围
- **工作目录**: `/Users/jackie/微课平台/micro-course-api`
- **审查类型**: R1 — 代码质量 + 契约对齐
- **审查依据**: 
  - `data-contract.md` (数据字典 v0.5)
  - `api-contract.md` (API契约-Phase1 v1.2)
  - `business-logic.md` (状态机设计 v1.0 + 开发规范 v1.4)
  - `permission-matrix.md` (权限矩阵 v2.0)
  - `structure-constitution.md` (项目结构规范 v1.1)
  - `microcourse-backend` 技能模板

---

## 1. Lombok 残留检测

| 状态 | 说明 |
|------|------|
| ✅ **PASS** | 未发现任何 `import lombok` 语句或 Lombok 注解 |

**证据**: 仅 `MybatisSlowSqlInterceptor.java:34` 注释中提及 Lombok（说明禁用原因），无实际使用。

---

## 2. @Autowired 字段注入检查

| 状态 | 说明 |
|------|------|
| ✅ **PASS** | 所有 `@Autowired` 使用均为构造器注入（非字段注入） |

**证据**: 共发现 8 处 `@Autowired`，全部位于构造函数上（已逐个验证）：

| 文件 | 行号 | 注入方式 |
|------|:----:|:--------:|
| HtmlCoursewareAdapter.java | 27-31 | 构造器注入 |
| PptCoursewareAdapter.java | 27-31 | 构造器注入 |
| LegacyCoursewareAdapter.java | 32-38 | 构造器注入 |
| CoursewareAdapterResolver.java | 30-38 | 构造器注入 |
| FlowEngine.java | 32-35 | 构造器注入 |
| AudioQueryService.java | 34-42 | 构造器注入 |
| PptCoursewareController.java | 27-29 | 构造器注入 |
| CoursewareQueryController.java | 55-57 | 构造器注入 |
| HtmlCoursewareController.java | 29-31 | 构造器注入 |

**说明**: 对于单构造函数 Bean，Spring 自动注入无需 `@Autowired` 注解（Spring 4.3+）。建议后续清理冗余 `@Autowired` 以保持一致性（P2 级别）。

---

## 3. 分页格式正确性

| 状态 | 说明 |
|------|------|
| ✅ **PASS** (Phase 1 核心) | Phase 1 核心 Controller 分页参数正确 |
| ⚠️ **P1-I** | 部分非 Phase 1 Controller size 默认值不一致 |

### 详细检查

**Phase 1 核心 Controller**（`DepartmentController`、`MajorController`、`ClassController`、`UserController`）：
- ✅ `@RequestParam(defaultValue = "0") int page` — 0-based 正确
- ✅ `@RequestParam(defaultValue = "20") int size` — 默认 20 正确
- ✅ `PageResult` 字段名：`items`/`page`/`size`/`totalElements`/`totalPages` — 与 API 契约一致
- ✅ `PageResult.of(IPage)` 内部自动 `page.getCurrent() - 1`（MyBatis-Plus 1-based → 0-based）

**非默认 Size 的 Controller**（P1-I，内部仅见）：

| 文件:行号 | size 默认值 | 说明 |
|-----------|:----------:|------|
| EnrollmentController.java:65 | 10 | 非 Phase 1 API，但 size=10 偏离通用 20 约定 |
| EnrollmentController.java:91 | 10 | 同上 |
| CourseExerciseController.java:43 | 10 | 偏离 20 约定 |
| ExerciseController.java:43 | 10 | 偏离 20 约定 |
| DiscussionAdminController.java:40 | 10 | 偏离 20 约定 |
| DiscussionAdminController.java:116 | 10 | 偏离 20 约定 |

---

## 4. 响应格式 R\<T\>

| 状态 | 说明 |
|------|------|
| ✅ **PASS** | 所有 Controller 方法统一返回 `R<T>` |

**详细检查**:
- `R.java` 字段：`code=200`, `message="ok"`, `data`, `timestamp` ✅
- 成功统一 `R.ok(data)` ✅
- 无直接返回 Entity 的情况 ✅
- 无 `code: 0` 或 `message: "success"` 的情况 ✅

---

## 5. @PreAuthorize 完整性

| 状态 | 说明 |
|------|------|
| ✅ **PASS** (Phase 1 核心) | Phase 1 核心 Controller 全部正确标注 |
| ⚠️ **P1-C** | 课件插件 Controller 7 个 GET 端点缺少 `@PreAuthorize` |

### 详细检查

**Phase 1 核心 Controller**：
| Controller | 端点 | @PreAuthorize | 与权限矩阵对齐 |
|-----------|------|:-------------:|:-------------:|
| AuthController | POST /login | `permitAll()` ✅ | ✅ |
| AuthController | POST /refresh | `permitAll()` ✅ | ✅ |
| AuthController | POST /logout | `isAuthenticated()` ✅ | ✅ |
| AuthController | GET /cas | `permitAll()` ✅ | ✅ |
| AuthController | GET /me | `isAuthenticated()` ✅ | ✅ |
| DepartmentController | GET /departments | `isAuthenticated()` ✅ | ✅ |
| DepartmentController | POST /departments | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| DepartmentController | GET /departments/{id} | `isAuthenticated()` ✅ | ✅ |
| DepartmentController | PUT /departments/{id} | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| DepartmentController | DELETE /departments/{id} | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| MajorController | GET /majors | `isAuthenticated()` ✅ | ✅ |
| MajorController | POST /majors | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| MajorController | GET /majors/{id} | `isAuthenticated()` ✅ | ✅ |
| MajorController | PUT /majors/{id} | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| MajorController | DELETE /majors/{id} | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| ClassController | GET /classes | `isAuthenticated()` ✅ | ✅ |
| ClassController | POST /classes | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| ClassController | GET /classes/{id} | `isAuthenticated()` ✅ | ✅ |
| ClassController | PUT /classes/{id} | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| ClassController | DELETE /classes/{id} | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| UserController | GET /users | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |
| UserController | POST /users | `hasRole('ADMIN')` ✅ | ✅ |
| UserController | PUT /users/{id}/status | `hasAnyRole('ADMIN','ACADEMIC')` ✅ | ✅ |

### ⚠️ P1-C: PptCoursewareController 缺少 @PreAuthorize

**文件**: `plugin/interactive/controller/PptCoursewareController.java`

以下 7 个 GET 端点缺少 `@PreAuthorize`，仅靠 SecurityConfig 的 `.anyRequest().authenticated()` 保护（任何已认证用户可访问，含 STUDENT）：

| 行号 | 方法 | 端点 |
|:----:|:----:|------|
| 34 | GET | `/api/courses/{courseId}/ppt/sections/{sectionId}/pages` |
| 50 | GET | `/api/courses/{courseId}/ppt/pages/{pageId}` |
| 75 | GET | `/api/courses/{courseId}/ppt/pages/{pageId}/scripts/active` |
| 81 | GET | `/api/courses/{courseId}/ppt/pages/{pageId}/scripts` |
| 98 | GET | `/api/courses/{courseId}/ppt/scripts/{scriptId}/audios` |
| 113 | GET | `/api/courses/{courseId}/ppt/audios/{audioId}` |
| 121 | GET | `/api/courses/{courseId}/ppt/sections/{sectionId}/flows` |

**风险**: 这些接口暴露课件 PPT 内容（含脚本、音频等），STUDENT 用户可越权读取，应添加 `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")` 或 Service 层 ownership 校验。

**文件**: `plugin/interactive/controller/CoursewareQueryController.java` 同样缺少 `@PreAuthorize`（2 个 GET 端点），尽管路径受 SecurityConfig 保护。

### ⚠️ P1-I: UserController.getById 权限矩阵文档不一致

**文件**: `UserController.java:79`
- 代码: `@PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC') or #id == authentication.principal")`
- 权限矩阵 v2.0: `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC') or hasRole('TEACHER') or #id == authentication.principal.id")`
- **差异**: 代码使用 `authentication.principal`（Long userId），文档写 `authentication.principal.id`。代码实现正确（JWT 过滤器将 Long userId 设为主体），文档需同步更新。功能等价，代码可行。

**文件**: `UserController.java:112`
- 代码: `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")`
- 权限矩阵 v2.0: `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")`
- 同上，代码正确，文档过时。

---

## 6. ErrorCode 使用

| 状态 | 说明 |
|------|------|
| ✅ **PASS** | 统一使用 `BusinessException(ErrorCode.XXX)` |

**详细检查**:
- 所有业务异常均通过 `BusinessException` 抛出 ✅
- `ErrorCode` 枚举覆盖 13 个核心业务码（1001-5004）+ 扩展码 ✅
- `GlobalExceptionHandler` 正确捕获并返回统一 `R.fail()` 格式 ✅
- 仅在 `DomainEvent.java:61,69` 出现 `throw new RuntimeException()` — 此为序列化/反序列化基础设施异常，可接受

---

## 7. DTO vs Entity 分离

| 状态 | 说明 |
|------|------|
| ✅ **PASS** (Phase 1 核心) | Phase 1 核心 CRUD 无直接 Entity 返回 |
| ⚠️ **P1-I** | AuthController 直接操作 User Entity |

### 详细检查

**Phase 1 核心模块**：
- `DepartmentController` → 仅使用 `DepartmentVO` / `DepartmentCreateRequest` / `DepartmentUpdateRequest` ✅
- `MajorController` → 仅使用 `MajorVO` / `MajorCreateRequest` / `MajorUpdateRequest` ✅
- `ClassController` → 仅使用 `ClassVO` / `ClassCreateRequest` / `ClassUpdateRequest` ✅
- `UserController` → 仅使用 `UserVO` / `UserCreateRequest` / `UserUpdateRequest` / `UserStatusRequest` ✅

### ⚠️ P1-I: AuthController 直接操作 User Entity

**文件**: `AuthController.java`

| 行号 | 问题 |
|:----:|------|
| 44 | 注入了 `UserRepository`（违反"Controller 不应直接持有 Repository"） |
| 147-154 | `getMyApiKey()` 直接调用 `userRepository.selectById()` 代替通过 UserService |
| 165-179 | `generateMyApiKey()` 直接操作 Entity 字段并调用 `userRepository.updateById()` |
| 188-197 | `revokeMyApiKey()` 同模式 |

**建议**: 将 API Key 操作方法移到 UserService（或独立的 ApiKeyService），Controller 保持薄层。

---

## 8. 分层职责检查

| 状态 | 说明 |
|------|------|
| ⚠️ **P1-I** | 多处 Controller 违反分层职责 |

### 详细发现

#### P1-I: SectionSlideController 业务逻辑在 Controller

**文件**: `controller/SectionSlideController.java`

| 行号 | 问题 |
|:----:|------|
| 53-78 | 完整的 API Key 鉴权 + Ownership 校验 + 权限判断 — 全部在 Controller 内 |
| 56 | `userRepository.findByApiKey()` — 直接调用 Repository |
| 71 | `courseRepository.selectById()` — 直接调用 Repository |
| 81-91 | 课件查询逻辑 — 直接使用 Mapper 绕过 Service 层 |
| 88 | `slidePageMapper.selectList()` — 直接 Mapper 调用 |

**影响**: 该 Controller 包含约 60% 业务逻辑，应下沉至 Service 层。

#### P1-I: AuthController 业务逻辑在 Controller

**文件**: `controller/AuthController.java`

| 行号 | 问题 |
|:----:|------|
| 147-154 | `getMyApiKey()` — Repository 查询 + 空值判断 + 脱敏 |
| 167-178 | `generateMyApiKey()` — UUID 生成 + Entity 赋值 + DB 更新 + 错误处理 |
| 188-197 | `revokeMyApiKey()` — Entity 赋值 + DB 更新 + 错误处理 |

#### P1-I: EnrollmentController 部分业务逻辑在 Controller

**文件**: `controller/EnrollmentController.java`

| 行号 | 问题 |
|:----:|------|
| 42-45 | PAYMENT sourceChannel 校验（应下沉 Service） |
| 47 | 手动 `request.setUserId(userId)` — 虽必须但建议 Service 层从 JWT 提取 |

#### P1-I: HermesWebhookController 直接使用 Repository

**文件**: `controller/HermesWebhookController.java`

| 行号 | 问题 |
|:----:|------|
| 159 | `sectionRepository.selectList()` — Controller 直接 Repository 调用 |
| 438 | `courseRepository.selectList(null)` |
| 450 | `mappingRepository.selectList(null)` |

#### P1-I: DepartmentServiceImpl 分页结果构建代码重复

**文件**: `service/impl/DepartmentServiceImpl.java:54-69`, `MajorServiceImpl.java:42-57`, `ClassServiceImpl.java:43-56`

三处 Service 实现中分页构建逻辑高度重复（手动构建 PageResult 而非使用 `PageResult.of(IPage)`）。`PageResult.of(IPage)` 已提供静态工厂方法，但 Service 未使用。

---

## 机械检查结果

| 检查项 | 状态 | 说明 |
|--------|:----:|------|
| 命名约定 | ✅ PASS | 类名 PascalCase、字段 camelCase、DB 字段 @TableField 映射正确 |
| 注释头完整性 | ✅ PASS | 关键类有 JavaDoc 注释 |
| 缩进/格式 | ✅ PASS | 缩进一致 |
| 遗留调试代码 | ✅ PASS | 未发现 `System.out`、`console.log` 等调试输出 |

---

## 问题清单

### P0 — 阻塞项（必须修复）

无。本次审查未发现 P0 级别问题。

### P1-C — 客户可感知（必须修复）

| # | 文件:行号 | 问题 | 修复建议 |
|---|----------|------|---------|
| 1 | `plugin/interactive/controller/PptCoursewareController.java:34,50,75,81,98,113,121` | 7 个 GET 端点缺少 @PreAuthorize，任何已认证用户（含 STUDENT）可读取课件 PPT 内容 | 添加 `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")` 或 Service 层 ownership 校验 |

### P1-I — 内部仅见（记录到 Phase 6）

| # | 文件:行号 | 问题 | 修复建议 |
|---|----------|------|---------|
| 1 | `controller/AuthController.java:44,147,165,188` | 注入 UserRepository，Controller 直接操作 Entity，违反分层职责 | 将 API Key 操作提取到 UserService |
| 2 | `controller/SectionSlideController.java:53-91` | 大量业务逻辑在 Controller（鉴权、Ownership、Mapper 调用） | 下沉到 SectionSlideService |
| 3 | `controller/HermesWebhookController.java:159,438,450` | Controller 直接使用 Repository | 将数据访问通过 Service 层代理 |
| 4 | `service/impl/DepartmentServiceImpl.java:54-69` | 手写 PageResult 构建（重复代码，未用 `PageResult.of(IPage)`） | 使用统一的 `PageResult.of(IPage)` 工厂方法 |
| 5 | `service/impl/MajorServiceImpl.java:42-57` | 同上 | 同上 |
| 6 | `service/impl/ClassServiceImpl.java:43-56` | 同上 | 同上 |
| 7 | `UserController.java:79` | @PreAuthorize SpEL `authentication.principal` vs 文档 `authentication.principal.id`（代码正确，文档需同步） | 更新权限矩阵 v2.0 §2.5 中 SpEL 表达式 |
| 8 | `UserController.java:112` | 同上 | 同上 |
| 9 | `domain/event/DomainEvent.java:61,69` | 序列化/反序列化抛出 RuntimeException（基础设施层可接受，但建议封装） | 考虑包装为 BusinessException |

### P2 — 可优化项（记录到 Phase 6）

| # | 文件 | 问题 | 建议 |
|---|------|------|------|
| 1 | 多个插件的 Controller | 8 处 `@Autowired` 构造器注入（冗余，Spring 4.3+ 自动注入） | 移除冗余 `@Autowired` |
| 2 | `controller/EnrollmentController.java:65,91` | size 默认 10 而非 20 | 统一为 size=20 |
| 3 | `controller/CourseExerciseController.java:43` | size 默认 10 | 统一为 size=20 |
| 4 | `controller/ExerciseController.java:43` | size 默认 10 | 统一为 size=20 |
| 5 | `controller/DiscussionAdminController.java:40,116` | size 默认 10 | 统一为 size=20 |

---

## 决策

- [ ] 放行（无 P0 阻塞项，P1/P2 记录到 Phase 6 统一处理）
- [ ] 阻塞（存在 P0 项，需修复后重新审查）
- [ ] 混合（有 P0 阻塞项 + P1/P2/`[设计偏离]`项，P0 修复后重新审查，其余记录到 Phase 6）

**审查结论**: ✅ **放行** — 未发现 P0 阻塞项。P1-C（课件权限缺失）需优先修复，P1-I/P2 项记录到 Phase 6 统一处理。

**单文件审查说明**: 本次审查为单模块（micro-course-api）代码质量审查，未执行跨文件冲突检查（跨文件冲突检测属于 R4 范围）。
