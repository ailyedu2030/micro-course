# 课程管理域 · Spec 漂移全量修复 · 设计文档

> **OpenSpec Change**: `course-domain-drift-fix`
> **设计目标**: 治本而非治标, 修复 127 项漂移的同时消除其产生的系统性格局

---

## 设计哲学

### 三层防御模型

```
         ┌─────────────────────────────────────┐
第三层    │  防御: 静态扫描 + CI 门禁           │  让新缺陷无法引入
         │  - precheck.sh 扩展 (3 条规则)       │
         │  - controller-lint.sh               │
         │  - ContractEndpointCoverageTest     │
         └─────────────────────────────────────┘
                       ↑
第二层    │  防御: 自动化生成 = 单一真相         │  让漂移无法累积
         │  - OpenAPI 自动生成                  │
         │  - 数据字典反向生成                  │
         │  - 权限矩阵可执行化                  │
         └─────────────────────────────────────┘
                       ↑
第一层    │  修复: 结构性重构消除根因           │  一次性治本
         │  - CourseStateMachine 统一入口       │
         │  - Controller 业务逻辑下沉           │
         │  - 魔数替换为枚举引用                │
         └─────────────────────────────────────┘
```

### 为什么不能用"逐项修补"

如果按 127 项逐项修补:
- 平均每项需要 5-15 分钟 (调研 + 修复 + 测试)
- 总计 127 × 10 = **21 小时纯修补**
- 但每修一项, 同样的根因可能在 3 个月后产生 5 个新同类缺陷
- 6 个月后漂移又回到 127 项

按 3 个根因模式系统性修复:
- 模式 1 (107 项): 自动化生成, 一次性
- 模式 2 (7 项): 状态机统一入口, 一次性
- 模式 3 (13 项): 静态扫描 + CI 门禁, 一次性
- **总计 3-5 天, 治本**

---

## 设计 1: CourseStateMachine 统一入口 (模式 2 修复)

### 当前架构 (问题)

```
Controller (CourseController, CourseAdminController, ...)
  ↓ 调用
CourseAuditServiceImpl / CourseAdminServiceImpl
  ↓ 各自实现守卫
courseRepository.update(... WHERE status = X AND id = Y)
```

**问题**: 状态变更有 3 条独立路径:
1. `CourseAuditServiceImpl.approve()/reject()/publish()` — 业务守卫 + 原始 WHERE
2. `CourseAdminServiceImpl.updateStatus()` — canTransitionTo + 无业务守卫
3. `updateStatus()` 通用端点 — 调用 updateStatus() 方法

攻击者用路径 3 可绕过路径 1 的全部业务守卫。

### 设计方案

#### 1.1 接口设计

```java
/**
 * 课程状态机 · 单一入口
 * 所有课程状态变更必须通过此接口, 禁止直接调用 Repository.update
 */
@Service
public interface CourseStateMachine {

    /**
     * 通用状态变更入口
     * @param courseId 课程 ID
     * @param targetStatus 目标状态
     * @param actor 当前操作用户 (用于自审批阻断)
     * @param context 业务上下文 (publish 时需要, 其他可选)
     */
    Course transition(Long courseId, CourseStatus targetStatus,
                     User actor, TransitionContext context);

    /**
     * 状态机守卫检查 (不会修改状态)
     */
    TransitionGuardResult checkTransition(Long courseId, CourseStatus targetStatus,
                                          User actor, TransitionContext context);

    /**
     * 注册自定义业务守卫 (按状态对)
     */
    void registerGuard(CourseStatus from, CourseStatus to,
                       BiFunction<Course, TransitionContext, List<String>> guard);
}

public enum TransitionGuardResult {
    ALLOWED,
    BLOCKED_BY_GUARD,  // 业务守卫阻断
    INVALID_TRANSITION, // canTransitionTo 不允许
    VERSION_CONFLICT,   // 乐观锁
    SELF_APPROVAL_BLOCKED  // 自审批阻断
}

public class TransitionContext {
    String rejectReason;        // for REJECTED
    Boolean forceOverride;      // for ADMIN override (with audit log)
    Map<String, Object> extras;
}
```

#### 1.2 实现关键点

```java
@Service
public class CourseStateMachineImpl implements CourseStateMachine {

    @Override
    public Course transition(Long courseId, CourseStatus targetStatus,
                             User actor, TransitionContext context) {

        // Step 1: 加载课程 + 乐观锁
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(COURSE_NOT_FOUND);

        CourseStatus current = CourseStatus.fromCode(course.getStatus());

        // Step 2: canTransitionTo 白名单
        if (!current.canTransitionTo(targetStatus)) {
            throw new BusinessException(COURSE_STATUS_TRANSITION_NOT_ALLOWED,
                "Cannot transition from " + current + " to " + targetStatus);
        }

        // Step 3: 业务守卫 hook
        TransitionGuardResult guardResult = checkTransition(courseId, targetStatus, actor, context);
        if (guardResult != ALLOWED) {
            throw new BusinessException(COURSE_INVALID_STATUS, guardResult.name());
        }

        // Step 4: 执行守卫 (含副作用: 通知, 审计, 状态字段)
        executeSideEffects(course, targetStatus, context);

        // Step 5: 乐观锁更新
        course.setStatus(targetStatus.getCode());
        int rows = courseRepository.update(course,
            new LambdaQueryWrapper<Course>()
                .eq(Course::getId, courseId)
                .eq(Course::getStatus, current.getCode())  // CAS
                .eq(Course::getVersion, course.getVersion()));
        if (rows == 0) throw new BusinessException(VERSION_CONFLICT);

        return course;
    }

    @Override
    public TransitionGuardResult checkTransition(...) {
        // 自审批阻断 (admin 不能审批自己)
        if (actor.getId().equals(course.getTeacherId()) && isApprovalAction(target)) {
            return SELF_APPROVAL_BLOCKED;
        }

        // 注册的业务守卫
        for (guard : guards.get(current, target)) {
            List<String> errors = guard.apply(course, context);
            if (!errors.isEmpty()) return BLOCKED_BY_GUARD;
        }

        return ALLOWED;
    }
}
```

#### 1.3 注册守卫 (集中配置)

```java
@Configuration
public class CourseStateMachineConfig {

    @Bean
    public CourseStateMachine courseStateMachine(CourseRepository repo, ...) {
        CourseStateMachineImpl sm = new CourseStateMachineImpl(repo, ...);

        // DRAFT → PENDING_REVIEW 守卫
        sm.registerGuard(DRAFT, PENDING_REVIEW, (course, ctx) -> {
            List<String> errors = new ArrayList<>();
            if (course.getTitle() == null) errors.add("标题不能为空");
            if (course.getCoverUrl() == null) errors.add("封面未上传");
            if (course.getCategoryId() == null) errors.add("分类未选择");
            // ✅ 修复 S1: 新增"章节下至少一个视频或练习"
            if (!hasChapterContent(course.getId())) {
                errors.add("至少一个章节下必须有视频或练习");
            }
            return errors;
        });

        // PENDING_REVIEW → REJECTED 守卫
        sm.registerGuard(PENDING_REVIEW, REJECTED, (course, ctx) -> {
            List<String> errors = new ArrayList<>();
            // ✅ 修复 S2: 驳回原因 ≥ 10 字符
            if (ctx.rejectReason == null || ctx.rejectReason.trim().length() < 10) {
                errors.add("驳回原因不能少于 10 字符");
            }
            return errors;
        });

        // CLOSED → PUBLISHED 守卫
        sm.registerGuard(CLOSED, PUBLISHED, (course, ctx) -> {
            List<String> errors = new ArrayList<>();
            // ✅ 修复 S3: 必须此前为 PUBLISHED
            if (course.getLastPublishedAt() == null) {
                errors.add("只有曾经发布过的课程才能重新上架");
            }
            // 定价 + 互动课件 + 插件授权 (原有 publish() 守卫)
            return errors;
        });

        return sm;
    }
}
```

#### 1.4 调用方改造

**改造前** (`CourseAuditServiceImpl`):
```java
public void approve(Long courseId, User actor) {
    Course course = courseRepository.selectById(courseId);
    if (course.getStatus() != PENDING_REVIEW.getCode()) throw ...;
    assertNotSelf(actor, course.getTeacherId());
    course.setStatus(APPROVED.getCode());
    courseRepository.update(course);  // ❌ 硬编码 WHERE
    log.info("Approved course {}", courseId);
    notificationService.notifyTeacher(course, "approved");
}
```

**改造后**:
```java
public void approve(Long courseId, User actor) {
    courseStateMachine.transition(courseId, APPROVED, actor, TransitionContext.empty());
    notificationService.notifyTeacher(courseId, "approved");
}
```

**Controller 改造**:
```java
@PostMapping("/{id}/approve")
@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
public R<Void> approve(@PathVariable Long id, @AuthenticationPrincipal User actor) {
    courseAuditService.approve(id, actor);
    return R.ok();
}
```

#### 1.5 通用端点改造 (修复 S4)

```java
@PutMapping("/{id}/status")
@PreAuthorize("hasAnyRole('ADMIN')")  // 提升: 通用端点仅 ADMIN 可用
public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status,
                            @AuthenticationPrincipal User actor) {
    CourseStatus target = CourseStatus.fromCode(status);

    // ✅ 修复 S4: 拒绝 status=1/4 (PENDING_REVIEW/PUBLISHED)
    // 必须走专用端点 submit/publish
    if (target == PENDING_REVIEW || target == PUBLISHED) {
        throw new BusinessException(COURSE_STATUS_TRANSITION_NOT_ALLOWED,
            "请使用 /submit 或 /publish 专用端点");
    }

    courseStateMachine.transition(id, target, actor, TransitionContext.empty());
    return R.ok();
}
```

### 测试设计

```java
@Test
public void exhaustive_state_machine_49_transitions() {
    for (CourseStatus from : CourseStatus.values()) {
        for (CourseStatus to : CourseStatus.values()) {
            // 每个转换验证:
            // 1. canTransitionTo 是否返回正确
            // 2. 若允许, 业务守卫是否被调用
            // 3. 乐观锁是否生效
        }
    }
}
```

---

## 设计 2: OpenAPI 自动生成 (模式 1 修复)

### 当前问题

`docs/API契约-Phase1.md` 范围明确为 "Phase 1: 用户认证、院系管理、专业管理、班级管理、用户管理"。课程管理域**从未被纳入契约体系**。85 个端点零契约记录。

### 修复方案: SpringDoc OpenAPI

#### 2.1 集成

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

#### 2.2 配置

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("微课管理平台 API")
                .version("1.7.0")
                .description("课程管理域 + 用户认证域 + 微专业域 + ..."))
            .addSecurityItem(new SecurityRequirement().addList("Bearer"))
            .components(new Components()
                .addSecuritySchemes("Bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

#### 2.3 Controller 注解

```java
@RestController
@RequestMapping("/api/courses")
@Tag(name = "课程管理")  // OpenAPI 标签
public class CourseController {

    @GetMapping
    @Operation(summary = "分页查询课程列表")
    @Parameter(name = "page", description = "页码", in = ParameterIn.QUERY)
    @Parameter(name = "size", description = "每页大小", in = ParameterIn.QUERY)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(schema = @Schema(implementation = PageResultCourseVO.class)))
    })
    public R<PageResult<CourseVO>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        /* ... */) { ... }

    // ... 22 个端点类似注解
}
```

#### 2.4 自动生成 + CI 门禁

```bash
# scripts/openapi-gen.sh
#!/bin/bash
# 启动后端, 访问 /v3/api-docs 拉取 OpenAPI JSON, 转换为 YAML
mvn spring-boot:run &
SPRING_PID=$!
sleep 30
curl -s http://localhost:8080/v3/api-docs | yq -P > docs/api/openapi.yaml
kill $SPRING_PID
```

```java
// ContractEndpointCoverageTest.java
@Test
public void all_controllers_have_openapi_annotation() {
    Set<String> openApiPaths = parseOpenApiSpec("docs/api/openapi.yaml");
    Set<String> controllerPaths = scanControllers();  // 反射 @RequestMapping

    for (String controllerPath : controllerPaths) {
        if (!openApiPaths.contains(controllerPath)) {
            fail("端点 " + controllerPath + " 缺少 OpenAPI 注解");
        }
    }
}
```

---

## 设计 3: 数据字典反向生成 (模式 1 子项)

### 当前问题

14 项漂移: 索引/CHECK 约束未登记, 已删约束残留, 类型偏差(JSONB→TEXT, INTEGER→SMALLINT), 不存在字段仍记录。

### 修复方案

```bash
# scripts/db-schema-doc-gen.sh
#!/bin/bash
# 解析所有 V*__*.sql, 提取 CREATE TABLE / ALTER TABLE / CREATE INDEX
# 输出为 Markdown 表格, 包含字段/类型/约束/索引
echo "# 数据字典 (从 migration SQL 自动生成)" > docs/data-dictionary.generated.md
for f in src/main/resources/db/migration/V*__*.sql; do
    sql-to-markdown "$f" >> docs/data-dictionary.generated.md
done

# 与手写 md 对比
diff docs/数据字典.md docs/data-dictionary.generated.md > docs/data-dictionary.diff || {
    echo "数据字典与实际 schema 不一致, 请修正 docs/数据字典.md"
    cat docs/data-dictionary.diff
    exit 1
}
```

修正方向:
- 索引: 补充 V20/V60 创建的索引登记
- CHECK 约束: 补充 V153 创建的 3 个 CHECK
- 已删约束: 移除 uk_cc_course_sort 记录
- 类型偏差: JSONB→TEXT (V110 变更)

---

## 设计 4: 权限矩阵可执行化 (模式 3 修复)

### 当前问题

权限矩阵 v2.0 是静态 Markdown, `@PreAuthorize` 是代码中的 string, 两者无自动化对齐。导致 11 项权限漂移。

### 修复方案: EndpointPermissionTest

#### 4.1 权限矩阵 v4.0 格式约定

新增机器可读表格格式 (与人类可读 Markdown 共存):

```yaml
# docs/permission-matrix-v4.0.yaml
version: "4.0"
last_updated: "2026-07-07"

endpoints:
  - path: "/api/courses"
    method: GET
    roles: [STUDENT, TEACHER, ADMIN, ACADEMIC]
    source: "@PreAuthorize('isAuthenticated()')"

  - path: "/api/courses"
    method: POST
    roles: [TEACHER, ADMIN]
    source: "@PreAuthorize(\"hasAnyRole('TEACHER','ADMIN')\")"

  # ... 85 个端点
```

#### 4.2 测试类

```java
@SpringBootTest
public class EndpointPermissionTest {

    @Test
    public void all_endpoints_match_permission_matrix() {
        // 1. 加载权限矩阵 v4.0 YAML
        Map<String, Set<String>> expected = loadPermissionMatrix("docs/permission-matrix-v4.0.yaml");

        // 2. 反射扫描所有 Controller 的 @RequestMapping + @PreAuthorize
        Map<String, Set<String>> actual = scanAllControllers();

        // 3. 对比
        for (String endpoint : expected.keySet()) {
            assertEquals(expected.get(endpoint), actual.get(endpoint),
                "端点 " + endpoint + " 权限与权限矩阵不一致");
        }
    }

    @Test
    public void missing_endpoints_in_matrix() {
        // 反向检查: Controller 存在但矩阵中不存在的端点
        // 用于发现 30 端点 (分类/课时/课件等) 未登记
    }
}
```

---

## 设计 5: 静态扫描增强 (模式 3 子项)

### precheck.sh 新增规则

```bash
# === 规则 1: 禁止状态字段硬编码 ===
grep -rn '\.eq(.*Status,\s*[0-9]' src/main/java/ && {
    echo "ERROR: 发现状态字段硬编码数字, 必须引用枚举"
    exit 1
}

# === 规则 2: 禁止 Controller 业务逻辑 ===
for f in $(find src/main/java -name "*Controller.java"); do
    # Controller 不应有 SecurityUtil.hasRole 调用
    grep -q 'SecurityUtil\.hasRole' "$f" && {
        echo "ERROR: Controller 含 SecurityUtil.hasRole 调用, 下沉到 Service"
        echo "  → $f"
        exit 1
    }
done

# === 规则 3: 禁止 Controller 工具方法 ===
for f in $(find src/main/java -name "*Controller.java"); do
    # 不应有 private static 工具方法
    grep -q 'private static.*[a-z]\+(' "$f" && {
        echo "WARN: Controller 含私有工具方法, 应抽取到工具类"
        echo "  → $f"
    }
done
```

### controller-lint.sh 新脚本

```bash
#!/bin/bash
# 完整 Controller 扫描 (warn 级别)
for f in $(find src/main/java -name "*Controller.java"); do
    WARN_COUNT=0

    # 业务逻辑关键词
    for keyword in "if.*MultipartFile" "if.*getSize()" "magic" "secret"; do
        grep -q "$keyword" "$f" && {
            echo "WARN: $f 含业务逻辑关键词: $keyword"
            WARN_COUNT=$((WARN_COUNT + 1))
        }
    done

    # 工具方法
    if grep -q 'private static' "$f"; then
        echo "WARN: $f 含私有静态方法"
        WARN_COUNT=$((WARN_COUNT + 1))
    fi

    # DTO 组装
    if grep -q '@RequestBody Map<' "$f"; then
        echo "WARN: $f 用 Map 而非 DTO: 应改用 DTO"
        WARN_COUNT=$((WARN_COUNT + 1))
    fi

    if [ $WARN_COUNT -gt 0 ]; then
        echo "  → 总计 $WARN_COUNT 个警告"
    fi
done
```

---

## 设计 6: 文档同步 (模式 1 子项)

### 6.1 数据字典 v0.5→v0.6

基于阶段 1-7 修复后的 schema, 同步 14 项漂移:

| 类别 | 修复 |
|------|------|
| JSONB→TEXT | freeDeptIds 类型改为 TEXT |
| 索引补充 | idx_courses_is_recommended (V20), idx_courses_teacher_deleted (V60), idx_courses_status_deleted (V60) |
| CHECK 补充 | chk_courses_status/difficulty/course_type (V153) |
| 已删约束 | 移除 uk_cc_course_sort |
| 不存在字段 | 移除 course_prerequisites.deletedAt |
| 类型 | course_review_logs.previousStatus/newStatus 改为 SMALLINT |
| NOT NULL | videos.originalName 实际可空, 改 NOT NULL→可空 |
| 索引命名 | idx_ctr_unique → uk_ctr_course_tag |
| DEFAULT | course_bundle_items.updatedAt 改为无显式 DEFAULT |

### 6.2 API 契约-课程管理 (新建)

格式参考 docs/API契约-Phase1.md, 但范围仅课程管理域:

```markdown
# API 契约 · 课程管理域 (Phase 5+)

## 范围
本契约覆盖 课程/章节/视频/分类/标签/套件/课时/课件/评价 9 个模块, 共 85 个端点。

## 1. 课程 CRUD (/api/courses)

### 1.1 GET /api/courses
- **权限**: isAuthenticated()
- **请求参数**:
  - page: int (default 1)
  - size: int (default 20)
  - title: String (可选, 模糊匹配)
  - ...
- **响应**: R<PageResult<CourseVO>>
- **错误码**: 无

### 1.2 POST /api/courses
...

(85 个端点全部列出)
```

### 6.3 权限矩阵 v2.0→v4.0

修复 11 项 + 补充 30 端点:
- 修复: 路径漂移 (章节创建/排序), 角色漂移 (submit/review/delete)
- 补充: 课程分类(5) + 课时(6) + 课件(11) + 定价(5) + 批量(2) + 状态(1)

### 6.4 状态机设计 v1.0→v1.1

补全:
- DRAFT→PENDING_REVIEW: 新增"章节下至少一个视频或练习"
- PENDING_REVIEW→REJECTED: 新增"驳回原因 ≥ 10 字符"
- CLOSED→PUBLISHED: 新增"此前为 PUBLISHED" (使用 lastPublishedAt)
- 通用端点约定: `PUT /api/courses/{id}/status` 拒绝 status=1/4

### 6.5 开发规范 v1.4→v1.5

新增 5 条禁止项:
1. §3.4.1: 禁止状态字段硬编码数字, 必须引用枚举 (`CourseStatus.X.getCode()`)
2. §3.4.2: 禁止 Controller 含 SecurityUtil 调用 (除 @PreAuthorize), 下沉到 Service
3. §3.4.3: 禁止 Controller 含文件魔数/大小校验, 必须用 FileUploadUtil
4. §3.4.4: 禁止 Controller 含私有工具方法, 必须放 util 包
5. §3.4.5: 禁止新增端点不在 OpenAPI 中注册 (CI 门禁)

---

## 设计 7: 225+ TC 设计

### TC 设计原则

参考 `memories/scratchpad/phase15_test_units.md` (Phase 15 申报管理的 104 TC) 格式:

每个 TC 必须包含:
- 单元 ID (TC-XXX)
- 页面 + 按钮 + 业务分支
- 前置条件 + 测试步骤
- 预期结果 (UI + API)
- API 验证

### 课程管理域 TC 分布

| 模块 | TC 数 | 范围 |
|------|------|------|
| 课程 CRUD | 30 | 创建/编辑/删除/复制/封面/导出 |
| 状态机 | 25 | 7 态×7 态 转换 + 守卫 + 通知 |
| 章节管理 | 40 | 4 类型章节 × CRUD + 排序 |
| 视频管理 | 30 | 上传/转码/进度/书签/封面 |
| 定价 | 20 | 4 范围 × 3 折扣 × 审批 |
| 分类/标签 | 15 | CRUD + 树形 |
| 套件 | 15 | CRUD + 子课 + 价格 |
| 课时 | 10 | CRUD |
| 课件 | 15 | 上传/渲染/导出 |
| 评价 | 10 | CRUD + 审核 |
| 跨域 | 15 | 教师列表/批量操作/异常流程 |
| **合计** | **225** | |

### TC 执行顺序

```
TC-001 ~ TC-030   (课程 CRUD)        — 4 天
TC-031 ~ TC-055   (状态机)            — 3 天
TC-056 ~ TC-095   (章节管理 4 类型)   — 4 天
TC-096 ~ TC-125   (视频管理)          — 3 天
TC-126 ~ TC-145   (定价)              — 2 天
TC-146 ~ TC-160   (分类/标签)         — 1 天
TC-161 ~ TC-175   (套件)              — 2 天
TC-176 ~ TC-185   (课时)              — 1 天
TC-186 ~ TC-200   (课件)              — 2 天
TC-201 ~ TC-210   (评价)              — 1 天
TC-211 ~ TC-225   (跨域)              — 2 天
                                       ─────
                                       25 天 (单人 Agent 串行)
```

执行方式: 单 Agent 串行, 一个域做完再做下一个, 保证上下文累积。

---

## 执行总时间估算

| 阶段 | 内容 | 估计 |
|------|------|------|
| 阶段 1 | P0 + V1 必修 | 1 天 |
| 阶段 2 | CourseStateMachine 重构 + 49 TC 穷举 | 2 天 |
| 阶段 3 | 权限修正 + Controller 瘦身 + 静态扫描 | 2 天 |
| 阶段 4 | OpenAPI 集成 + 85 注解 + CI 门禁 | 2 天 |
| 阶段 5 | 数据字典反向生成 + 修正 | 0.5 天 |
| 阶段 6 | 权限矩阵 v4.0 + EndpointPermissionTest | 1 天 |
| 阶段 7 | precheck.sh 扩展 + controller-lint.sh | 0.5 天 |
| 阶段 8 | 5 份 spec 文档同步 | 1 天 |
| 阶段 9 | 225+ TC 设计 + 执行 | 25 天 |
| **合计** | | **35 天** |

**注**: 阶段 9 的 25 天是测试执行时间, 与修复阶段并行 — 阶段 1-8 修复完成后立即进入 TC 执行, 边测边发现增量缺陷。

---

## 验收标准

- ✅ 全部 56 个任务完成
- ✅ 4 项 P0 + 1 项 P1-C 必修全部修复 + 回归测试通过
- ✅ ExhaustiveStateMachineTest 49 个转换全部 PASS
- ✅ ContractEndpointCoverageTest 85 端点全部覆盖
- ✅ EndpointPermissionTest 0 失败
- ✅ precheck.sh 扩展规则 0 误报
- ✅ 5 份 spec 文档版本号 +1
- ✅ 225+ TC 全部 PASS, 0 个 P0/P1 残留
- ✅ 数据字典反向生成与手写 md 一致
- ✅ CI 门禁集成完成

---

**设计完成**, 等用户批准进入 /opsx-apply 阶段