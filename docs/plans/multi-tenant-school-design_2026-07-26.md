# 多租户学校隔离 — 设计文档

> **阶段**：Phase 3 — Mid-Level 设计文档  
> **日期**：2026-07-26  
> **策略文档**：`docs/plans/multi-tenant-school-strategy_2026-07-26.md`  
> **前置确认**：方案 A（共享表 + school_id 列）、PLATFORM_ADMIN role、全业务域隔离已确认  

---

## 1. 数据模型

### 1.1 schools 表

```sql
CREATE TABLE schools (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,              -- 学校名称（如"北京大学"）
    code            VARCHAR(30)     NOT NULL UNIQUE,       -- 学校代码（如 "pku"，用于自助注册输入）
    domain          VARCHAR(100),                          -- 学校域名（CAS 自动识别用，如 pku.edu.cn）
    logo_url        VARCHAR(500),                          -- 学校 Logo URL
    contact_name    VARCHAR(50),                           -- 联系人姓名
    contact_phone   VARCHAR(30),                           -- 联系电话
    contact_email   VARCHAR(100),                          -- 联系邮箱
    address         VARCHAR(500),                          -- 学校地址
    status          INTEGER         NOT NULL DEFAULT 1,    -- 0=INACTIVE, 1=ACTIVE, 2=DISABLED
    config          TEXT,                                  -- JSONB 学校级配置（可选扩展）
    sort_order      INTEGER         DEFAULT 0,             -- 排序号
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP       DEFAULT NULL
);

COMMENT ON TABLE schools IS '学校/租户表';
COMMENT ON COLUMN schools.code IS '学校代码（自助注册输入用，不可变）';
COMMENT ON COLUMN schools.domain IS 'CAS 域名识别（可选）';

CREATE UNIQUE INDEX uk_schools_code ON schools(code) WHERE deleted_at IS NULL;
CREATE INDEX idx_schools_status ON schools(status);
```

### 1.2 schools 对应 Java Entity

```java
// entity/School.java (新增)
@TableName("schools")
public class School {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;           // 唯一标识代码
    private String domain;         // CAS 域名识别
    private String logoUrl;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private Integer status;        // 0=INACTIVE, 1=ACTIVE, 2=DISABLED
    private String config;         // JSON 扩展配置
    private Integer sortOrder;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;
}
```

### 1.3 school_id 策略：三层决策表

> **现有表统一执行策略**：  
> - **NOT NULL / 级联继承**：school_id 来自 FK 父链（schools → departments → majors → classes → users）
> - **直接持有**：业务主体表需要直接隔离（courses, enrollments, orders 等），或逻辑上属于学校级资源（banners, admin_settings, operation_logs）
> - **NOT NULL 强制**：绝大多数表 school_id 为 NOT NULL，仅中间/关联表允许 NULL 但业务逻辑确保写入时填充

| 类别 | 表 | 获取 school_id 方式 | NOT NULL? |
|------|-----|---------------------|-----------|
| **核心租户表** | `schools` | 自己是来源 | PK |
| **组织架构** | `departments` | 用户上下文 → 由操作者传入 | **NOT NULL** |
| | `majors` | 从 `departments.school_id` 继承 | **NOT NULL**（FK→departments 链保证） |
| | `classes` | 从 `majors.school_id` → `departments.school_id` 继承 | **NOT NULL**（FK 链保证） |
| **用户表** | `users` | 注册时 schoolCode 指定 / CAS 域自动匹配 / 导入时传入 → 存入 `users.school_id` | **NOT NULL** |
| **课程域** | `courses` | 创建者 `users.school_id`（教师所属学校） | **NOT NULL** |
| | `course_categories` | 创建者 `users.school_id` | **NOT NULL** |
| | `tags` | 创建者 `users.school_id` | **NOT NULL** |
| | `course_tag_relations` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `course_chapters` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `videos` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `course_slides` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `slide_pages` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `narration_settings` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| **选课域** | `enrollments` | 学生 `users.school_id` 或课程 `courses.school_id`（两者应一致） | **NOT NULL** |
| | `enrollment_histories` | 从 `enrollments.school_id` 继承 | NOT NULL（FK 链保证） |
| | `learning_progress` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `course_favorites` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| **练习域** | `questions` | 创建教师 `users.school_id` | **NOT NULL** |
| | `exercises` | 从 `courses.school_id` 或教师 school_id | **NOT NULL** |
| | `exercise_questions` | 从 `exercises.school_id` 继承 | NOT NULL（FK 链保证） |
| | `exercise_records` | 从 `exercises.school_id` 继承 | NOT NULL（FK 链保证） |
| | `wrong_questions` | 从 `questions.school_id` 继承 | NOT NULL（FK 链保证） |
| **讨论域** | `discussion_posts` | 从 `courses.school_id` 继承 | **NOT NULL** |
| | `discussion_comments` | 从 `discussion_posts.school_id` 继承 | NOT NULL（FK 链保证） |
| | `discussion_comment_likes` | 从 `discussion_comments.school_id` 继承 | NOT NULL（FK 链保证） |
| **订单域** | `orders` | 购买者 `users.school_id` | **NOT NULL** |
| | `payments` | 从 `orders.school_id` 继承 | NOT NULL（FK 链保证） |
| | `cart_items` | 用户 `users.school_id` | **NOT NULL** |
| **教学班** | `teaching_classes` | 从 `courses.school_id` 继承 | **NOT NULL** |
| | `teaching_class_students` | 从 `teaching_classes.school_id` 继承 | NOT NULL（FK 链保证） |
| | `class_schedules` | 从 `teaching_classes.school_id` 继承 | NOT NULL（FK 链保证） |
| **成绩** | `grades` | 从 `enrollments.school_id` 继承 | **NOT NULL** |
| | `grade_components` | 从 `grades` → `enrollments.school_id` 继承 | NOT NULL（FK 链保证） |
| **签到** | `check_ins` | 从 `courses.school_id` 继承 | **NOT NULL** |
| **证书/徽章** | `certificates` | 所属学校 | **NOT NULL** |
| | `badge_definitions` | 所属学校 | **NOT NULL** |
| | `badges`（已废弃） | 不处理或标记 | - |
| | `achievements` | 从 `users.school_id` 继承 | **NOT NULL** |
| **评价** | `course_reviews` | 从 `courses.school_id` 继承 | **NOT NULL** |
| | `course_review_logs` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| **系统/审计** | `admin_settings` | **学校级配置**：school_id NOT NULL；全局配置：school_id = 0（特殊值） | **NOT NULL** |
| | `operation_logs` | 操作者 `users.school_id` | **NOT NULL** |
| | `banners` | 所属学校 | **NOT NULL** |
| | `notifications` | 发送者 `users.school_id` | **NOT NULL** |
| | `notification_preferences` | 用户 `users.school_id` | **NOT NULL** |
| **微专业** | `micro_specialties` | 创建者 `users.school_id` | **NOT NULL** |
| | `micro_specialty_courses` | 从 `micro_specialties.school_id` 继承 | NOT NULL（FK 链保证） |
| | `micro_specialty_teachers` | 从 `micro_specialties.school_id` 继承 | NOT NULL（FK 链保证） |
| | `micro_specialty_enrollments` | 从 `micro_specialties.school_id` 继承 | NOT NULL（FK 链保证） |
| | `micro_specialty_proposals` | 提出者 `users.school_id` | **NOT NULL** |
| | `micro_specialty_featured_audit` | 从 `micro_specialties.school_id` 继承 | NOT NULL（FK 链保证） |
| **课程套件** | `course_bundles` | 创建者 `users.school_id` | **NOT NULL** |
| | `course_bundle_items` | 从 `course_bundles.school_id` 继承 | NOT NULL（FK 链保证） |
| **考试** | `exams` / `exam_results` 等 | 所属课程或学校 | **NOT NULL** |
| **文件** | `attachments` | 上传者 `users.school_id` | **NOT NULL** |
| **评审** | `review_reports` | 所属课程 `courses.school_id` | **NOT NULL** |
| **评分** | `score_histories` | 用户 `users.school_id` | **NOT NULL** |
| **教师评级** | `teacher_ratings` | 所属学校 | **NOT NULL** |
| | `teacher_tier_log` | 从 `teacher_ratings.school_id` 继承 | NOT NULL（FK 链保证） |
| **课程先修** | `course_prerequisites` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| **课程培训/项目** | `course_trainings` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `course_final_project` | 从 `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| **线下章节** | `chapter_offline_sessions` | 从 `course_chapters` → `courses.school_id` 继承 | NOT NULL（FK 链保证） |
| | `attendance_records` | 从 `chapter_offline_sessions` 链 | NOT NULL（FK 链保证） |
| **笔记/书签** | `course_notes` | 用户 `users.school_id` | **NOT NULL** |
| | `video_bookmarks` | 用户 `users.school_id` | **NOT NULL** |
| **存储申报** | `storage_applications` 相关 | 提出者 `users.school_id` | **NOT NULL** |
| **插件** | `plugin_grants` | 所属学校 | **NOT NULL** |

### 1.4 唯一索引迁移

#### 现 UNIQUE → (school_id, Xxx) 联合索引

| 表 | 现唯一索引 | 迁移后索引 |
|----|-----------|-----------|
| `departments` | `uk_departments_code (code)` | `uk_dept_school_code (school_id, code)` |
| `majors` | `uk_majors_code (code)` | `uk_major_school_code (school_id, code)` |
| `users` | `idx_users_username (username)` | `uk_user_school_username (school_id, username)` |
| `users` | `idx_users_student_no (student_no)` | `uk_user_school_student_no (school_id, student_no)` |
| `users` | `idx_users_teacher_no (teacher_no)` | `uk_user_school_teacher_no (school_id, teacher_no)` |
| `users` | `uk_users_email (email)` | `uk_user_school_email (school_id, email)` — 仍保留 `WHERE email <> ''` |
| `tags` | `idx_tags_name (name)` | `uk_tag_school_name (school_id, name)` |

**迁移策略**：
1. 先 DROP 旧唯一索引（deferrable 或用 CASCADE）
2. 创建新联合唯一索引（含 school_id）
3. 创建 school_id 单列索引（若当前表无 FK 快速路径）

### 1.5 新增索引

```sql
-- 所有加 school_id 的表至少需要以下索引
-- (school_id + 该表当前主查询列) 的组合索引优先

-- 组织架构：按学校查询
CREATE INDEX idx_dept_school ON departments(school_id);
CREATE INDEX idx_major_school ON majors(school_id);
CREATE INDEX idx_class_school ON classes(school_id);

-- 用户：按学校查询
CREATE INDEX idx_users_school ON users(school_id);

-- 课程域：按学校查询（配合现有 idx_courses_teacher/status）
CREATE INDEX idx_courses_school ON courses(school_id);
CREATE INDEX idx_cc_school ON course_categories(school_id);
CREATE INDEX idx_videos_school ON videos(school_id);
CREATE INDEX idx_enrollments_school ON enrollments(school_id);
CREATE INDEX idx_questions_school ON questions(school_id);

-- 订单域
CREATE INDEX idx_orders_school ON orders(school_id);

-- 操作日志（大小预计最大）
CREATE INDEX idx_oplog_school ON operation_logs(school_id);
```

---

## 2. TenantContext 可信来源

### 2.1 数据流

```
                  ┌──────────────────────┐
                  │    JWT Token          │
                  │  claims: schoolId     │
                  └──────────┬───────────┘
                             │
                             ▼
     ┌───────────────────────────────────────┐
     │  JwtAuthenticationFilter              │
     │  → 从 token 提取 schoolId             │
     │  → 写入 TenantContext.set(schoolId)    │
     │  → 写入 SecurityContext (authorities)  │
     └──────────────────┬────────────────────┘
                        │
                        ▼
     ┌───────────────────────────────────────┐
     │  TenantContextFilter (可选显式set)     │
     │  仅在校验/登录入口触发                 │
     └──────────────────┬────────────────────┘
                        │
                        ▼
     ┌───────────────────────────────────────┐
     │  MyBatis-Plus TenantInterceptor       │
     │  → 读 TenantContext.getCurrentSchoolId()│
     │  → 自动追加 school_id = ? 条件        │
     │  → @SkipTenantFilter 跳过             │
     └───────────────────────────────────────┘
```

### 2.2 TenantContext 类设计

```java
// util/TenantContext.java (新增)
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT_SCHOOL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SKIP_FILTER = new ThreadLocal<>();

    private TenantContext() {}

    public static void setSchoolId(Long schoolId) {
        CURRENT_SCHOOL.set(schoolId);
    }

    public static Long getCurrentSchoolId() {
        return CURRENT_SCHOOL.get();
    }

    public static void setSkipFilter(boolean skip) {
        SKIP_FILTER.set(skip);
    }

    public static boolean isSkipFilter() {
        return Boolean.TRUE.equals(SKIP_FILTER.get());
    }

    public static void clear() {
        CURRENT_SCHOOL.remove();
        SKIP_FILTER.remove();
    }
}
```

### 2.3 JWT 扩展

```java
// JwtUtil.java (修改)
// generateToken 新增 schoolId 参数
public String generateToken(Long userId, String username, UserRole role,
                             Long departmentId, Long schoolId) {
    return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("username", username)
            .claim("role", role.name())
            .claim("departmentId", departmentId)
            .claim("schoolId", schoolId)              // ← 新增
            .claim("jti", jti)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getKey())
            .compact();
}
```

```java
// JwtAuthenticationFilter.java (修改)
// doFilterInternal 中提取 schoolId
Long schoolId = jwtUtil.getSchoolIdFromToken(token);
TenantContext.setSchoolId(schoolId);
```

### 2.4 三路认证入口 schoolId 解析

| 入口 | schoolId 来源 | 实现 |
|------|-------------|------|
| **POST /auth/login** | `LoginRequest` 扩展 `schoolCode` 字段 | 登录请求 body 传入 schoolCode → 查 schools.code → 写入 JWT |
| **POST /auth/register** | `RegisterRequest` 扩展 `schoolCode` 字段 | 注册请求传入 schoolCode → 查 schools.code → 写入 JWT |
| **GET /auth/cas** | 方式一：state 参数携带 schoolCode；方式二：CAS domain 自动识别（`schools.domain`） | CAS callback URL 已有 state 参数，扩展此字段 |
| **POST /auth/refresh** | 从旧 refreshToken 的 schoolId claim 继承 | 当前 refreshToken 机制已含 claims，复用 |
| **批量导入** | 由操作者 `TenantContext.getCurrentSchoolId()` 继承 | 导入操作在用户已登录后执行，schoolId 从线程上下文取 |

### 2.5 LoginRequest 扩展

```java
// dto/LoginRequest.java (修改)
public class LoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private String schoolCode;   // ← 新增，可选，登录时指定学校
}
```

### 2.6 RegisterRequest 扩展

```java
// dto/RegisterRequest.java (修改)
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "学校代码不能为空")   // ← 新增 NOT NULL
    private String schoolCode;   // ← 新增
}
```

### 2.7 PLATFORM_ADMIN scope 规则

```java
// service/impl/SchoolScopeServiceImpl.java (新增)
@Service
public class SchoolScopeServiceImpl implements SchoolScopeService {

    /**
     * PLATFORM_ADMIN 的 school 访问规则核心逻辑：
     * 
     * 1. 非 PLATFORM_ADMIN 用户 → school_id 必须 == 当前用户的 schoolId
     * 2. PLATFORM_ADMIN 用户：
     *    a. schoolId == null（未指定学校上下文）→ 只允许调用「全局汇总」API
     *    b. schoolId == 指定值 → 允许对该学校的读/写操作（需额外权限校验）
     *    
     * @param targetSchoolId API 所操作数据的 school_id
     * @param requireWrite   是否为写操作
     */
    public void assertSchoolAccess(Long targetSchoolId, boolean requireWrite) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        UserRole role = getUserRole(currentUserId);
        Long currentSchoolId = TenantContext.getCurrentSchoolId();
        
        if (role == UserRole.PLATFORM_ADMIN) {
            if (currentSchoolId == null && requireWrite) {
                // PLATFORM_ADMIN 未指定学校上下文时禁止写入
                throw new BusinessException(ErrorCode.FORBIDDEN, 
                    "请先选择一个学校后再执行此操作");
            }
            if (currentSchoolId != null && !currentSchoolId.equals(targetSchoolId)) {
                // 指定的学校与目标数据不匹配
                throw new BusinessException(ErrorCode.FORBIDDEN,
                    "无权访问其他学校的数据");
            }
            return; // PLATFORM_ADMIN 放行
        }
        
        // 非 PLATFORM_ADMIN：school_id 必须匹配
        if (!currentSchoolId.equals(targetSchoolId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                "无权访问其他学校的数据");
        }
    }
}
```

---

## 3. 读写路径防线

### 3.1 四层防线

```
┌──────────────┐
│ Controller   │  @PreAuthorize 角色检查 + @SchoolScope 注解（自动 assertSchoolAccess）
├──────────────┤
│ Service      │  SchoolScopeService.assertSchoolAccess() 显式调用（二次校验）
├──────────────┤
│ Repository   │  MyBatis-Plus TenantInterceptor 自动追加 school_id = ?
│ (MyBatis-Plus)│  @SkipTenantFilter 显式跳过（PLATFORM_ADMIN 全局查询）
├──────────────┤
│ DB           │  school_id NOT NULL + 索引 + UNIQUE(school_id, Xxx) 约束
│ (PostgreSQL) │  无 RLS（第 3.3 节说明）
└──────────────┘
```

### 3.2 @SchoolScope 注解

```java
// security/SchoolScope.java (新增)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchoolScope {
    boolean requireWrite() default false;
    String schoolIdParam() default "";  // SpEL 表达式从参数取 schoolId
}

// security/SchoolScopeAspect.java (新增) — AOP 拦截
@Aspect
@Component
public class SchoolScopeAspect {
    private final SchoolScopeService schoolScopeService;
    
    @Around("@annotation(scope)")
    public Object checkScope(ProceedingJoinPoint pjp, SchoolScope scope) throws Throwable {
        // 非 PLATFORM_ADMIN → 跳过（由拦截器自动处理）
        if (!SecurityUtil.hasRole("PLATFORM_ADMIN")) {
            return pjp.proceed();
        }
        
        Long currentSchoolId = TenantContext.getCurrentSchoolId();
        Long targetSchoolId = resolveSchoolId(pjp, scope);
        
        schoolScopeService.assertSchoolAccess(targetSchoolId, scope.requireWrite());
        
        return pjp.proceed();
    }
}
```

### 3.3 为何不选用 RLS（Row-Level Security）

| 维度 | RLS | MyBatis-Plus Interceptor | 结论 |
|------|-----|-------------------------|------|
| 性能 | 每次查询自动附加 school_id 策略，但 PostgreSQL RLS 有缓存开销 | 拦截器在应用层追加 `WHERE school_id = ?` | 拦截器更可控 |
| 透明性 | RLS 对应用透明，DBA 也看不见过滤 | 拦截器需代码配合 | RLS 更难排查问题 |
| PLATFORM_ADMIN | RLS 中通过 `session.current_school_id` 变量切换，需额外配置 | 通过 `@SkipTenantFilter` 注解跳过 | 拦截器更显式 |
| 迁移成本 | 所有表启用 RLS（`ALTER TABLE xxx ENABLE ROW LEVEL SECURITY`） | 59 次表加列，但无新安全机制学习 | 拦截器+现有技术栈 |
| FK 链 | RLS 不作用于 FK 校验，需配套机制 | FK 校验由 DB 层保障，school_id 通过 FK 继承 | 拦截器+FK 更可靠 |

**决定**：不用 RLS，用 MyBatis-Plus TenantInterceptor + FK 约束 + 代码二次校验。

### 3.4 MyBatis-Plus TenantInterceptor 实现

```java
// config/TenantInterceptor.java (新增)
@Component
public class TenantInterceptor implements InnerInterceptor {
    
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                             RowBounds rowBounds, ResultHandler resultHandler,
                             BoundSql boundSql) throws SQLException {
        if (shouldSkip(ms)) return;
        appendSchoolIdCondition(boundSql, ms);
    }
    
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        if (shouldSkip(ms)) return;
        appendSchoolIdCondition(boundSql, ms);
    }
    
    private boolean shouldSkip(MappedStatement ms) {
        // 1. PLATFORM_ADMIN 且未指定 school 上下文 → skip（全局查询）
        if (TenantContext.isSkipFilter()) return true;
        
        // 2. 检查 @SkipTenantFilter 注解
        String id = ms.getId();
        Class<?> clazz = ClassUtils.forName(id.substring(0, id.lastIndexOf('.')), ...);
        Method method = ...;
        if (method != null && method.isAnnotationPresent(SkipTenantFilter.class)) return true;
        
        return false;
    }
}
```

```java
// 在 MyBatisPlusConfig 注册
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new TenantInterceptor()); // ← 新增（需在 Pagination 前）
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(1000L));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    return interceptor;
}
```

---

## 4. PLATFORM_ADMIN 角色与权限矩阵

### 4.1 UserRole 枚举扩展

```java
// enums/UserRole.java (修改)
public enum UserRole {
    STUDENT,
    TEACHER,
    ADMIN,            // 学校级管理员（原含义扩展）
    ACADEMIC,         // 学校级教务处
    PLATFORM_ADMIN    // ← 新增：平台级管理员
}
```

### 4.2 PLATFORM_ADMIN 可见/写规则

| 查询范围 | schoolId 上下文 | PLATFORM_ADMIN 行为 | 后端实现 |
|---------|---------------|-------------------|---------|
| 全局 Dashboard 汇总 | null | ✅ 返回所有学校汇总 | `@SkipTenantFilter` + 聚合查询跨 school_id |
| 指定学校详情/编辑 | schoolId=3 | ✅ 可读可写 | `TenantContext.set(3)` → 拦截器限制 school_id=3 |
| 指定学校仅读 | schoolId=3 | ✅ 只读 | `TenantContext.set(3)` + `SchoolScope(requireWrite=false)` |
| 切换学校 | schoolId=5 | ✅ 可读可写 | 前端下拉 → 重新 set TenantContext |
| 创建/更新学校配置 | 全局 | ✅ 可写（不依赖 school 上下文） | PLATFORM_ADMIN 直接授权 |
| 管理平台级设置 | 全局 | ✅ 可写 | school_id=0（全局配置） |

### 4.3 @PreAuthorize 权限映射

```java
// 平台管理端点（新增）
@RestController
@RequestMapping("/api/platform")
public class PlatformAdminController {
    
    @GetMapping("/schools")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public R<PageResult<SchoolVO>> listSchools(...) { ... }
    
    @PostMapping("/schools")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public R<SchoolVO> createSchool(...) { ... }
    
    @PutMapping("/schools/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public R<SchoolVO> updateSchool(...) { ... }
    
    @GetMapping("/dashboard/overview")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @SkipTenantFilter  // 跳过学校过滤
    public R<PlatformDashboardVO> platformOverview() { ... }
}
```

```java
// 现有 Controller 无改动 — 因 @PreAuthorize 角色声明不改变，仅新增 PLATFORM_ADMIN 兼容
// 例如：@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')") 已排除 PLATFORM_ADMIN
// PLATFORM_ADMIN 的学校级访问通过 @SchoolScope + TenantContext 机制实现
```

### 4.4 SecurityConfig 路径权限扩展

```java
// config/SecurityConfig.java (修改)
.requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")
```

---

## 5. JWT / 注册 / CAS / 批量导入 / 审计 / 缓存 key / 文件路径 / 后台学校切换器

### 5.1 JWT 变更

| claim | 类型 | 来源 | 用途 |
|-------|------|------|------|
| `schoolId` | Long | JWT 生成时从 login/register/cas 获取 | 授权时写入 SecurityContext + TenantContext |

### 5.2 自助注册流程

```
用户 → POST /api/auth/register { username, password, schoolCode }
  ↓
验证 schoolCode → 查询 schools.code
  ↓
创建 User.school_id = schools.id
  ↓
生成 JWT（含 schoolId claim）
  ↓
返回 LoginResponse
```

### 5.3 CAS 登录流程

```
用户 → GET /api/auth/cas?ticket=xxx&state=[schoolCode]
  ↓
方式 1：state 参数携带 schoolCode
方式 2：CAS Server 返回 domain → 匹配 schools.domain → 确定 school_id
  ↓
创建/登录 User.school_id = 确定值
  ↓
生成 JWT（含 schoolId claim）
```

### 5.4 批量导入流程

```
用户（已登录，TenantContext 含 schoolId）→ POST /api/users/batch-import
  ↓
UserBatchImportServiceImpl 从 TenantContext 取 schoolId
  ↓
每个新 User 设置 school_id = 当前学校
  ↓
事务写入时，被 TenantInterceptor 拦截器二次保障
```

**文件变更**：`UserBatchImportServiceImpl.java:218-238` — `buildUserFromRow()` 新增 `user.setSchoolId(TenantContext.getCurrentSchoolId())`

### 5.5 审计日志扩展

```java
// OperationLog.java (修改) — 新增 schoolId 字段
@TableField("school_id")
private Long schoolId;
```

```sql
ALTER TABLE operation_logs ADD COLUMN school_id BIGINT NOT NULL REFERENCES schools(id);
CREATE INDEX idx_oplog_school ON operation_logs(school_id);
```

### 5.6 Redis Key 模式迁移

| 用途 | 现 Key | 新 Key |
|------|--------|--------|
| 登录失败 | `mc:login:lock:{username}` | `mc:login:lock:{schoolId}:{username}` |
| Refresh 限流 | `mc:refresh:limit:{ip}` | 不变（IP 级限流不区分学校）|
| Token 代数 | `mc:user:token-gen:{userId}` | 不变（userId 全局唯一）|
| Token 黑名单 | `mc:jwt:blacklist:{jti}` | 不变（jti 全局唯一）|
| 用户级黑名单 | `mc:jwt:user-blacklist:{userId}` | 不变（userId 全局唯一）|

**只有登录失败 key 需要加 schoolId 前缀** — 同用户名在不同学校独立计数。

### 5.7 文件路径隔离

```java
// config/WebMvcConfig.java (不变 — 文件映射路径不直接暴露)
// 存储路径变更：FileStorageService 内部
// 原：uploads/covers/{filename}
// 新：uploads/{schoolId}/covers/{filename}

// 访问 URL 不变：/api/files/covers/{filename}
// nginx 转发：/api/files/covers/{schoolId}/{filename} 或内部映射
```

```java
// 推荐策略：文件名前缀加 schoolId
// 存 schoolId_filename.ext 或者子目录
// 例：coverUrl = "/api/files/covers/3_abc123.jpg"

// 实现：所有文件上传 Service 中获取 TenantContext.getCurrentSchoolId()
// 拼接存储路径
```

### 5.8 后台学校切换器

**前端组件**：`SchoolSwitcher.vue`（新增）

```vue
<!-- 位置：layouts/components/SchoolSwitcher.vue -->
<!-- 仅 PLATFORM_ADMIN 可见 -->
<template>
  <el-select v-model="currentSchoolId" @change="onSchoolChange" placeholder="选择学校">
    <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
  </el-select>
</template>

<script setup>
// 调用 GET /api/platform/schools 获取学校列表
// 切换时：
// 1. 向后端发送 refresh JWT（含新 schoolId）
// 2. 或前端全局注入 X-School-Id header + TenantContext 前端模拟
// 推荐：变更 schoolId → 调用 refresh 获得新 JWT（含新 schoolId claim）

const onSchoolChange = async (schoolId) => {
  // 调用 POST /api/auth/switch-school { schoolId }
  // 后端刷新 JWT，schoolId claim 更新
  // 返回新 JWT，前端替换
  await userStore.switchSchool(schoolId)
}
</script>
```

```java
// AuthController.java (新增)
@PostMapping("/switch-school")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public R<LoginResponse> switchSchool(@RequestBody Map<String, Long> body) {
    Long targetSchoolId = body.get("schoolId");
    // 校验 school 存在且 ACTIVE
    School school = schoolRepository.selectById(targetSchoolId);
    if (school == null || school.getStatus() != 1) {
        throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "学校不存在或已禁用");
    }
    Long userId = SecurityUtil.getCurrentUserId();
    User user = userRepository.selectById(userId);
    // 使用请求的 schoolId 生成新 JWT
    String newAccessToken = jwtUtil.generateToken(userId, user.getUsername(), 
        user.getRole(), user.getDepartmentId(), targetSchoolId);
    String newRefreshToken = jwtUtil.generateRefreshToken(userId, 
        redisUtil.getTokenGeneration(userId));
    // 返回新 token
    LoginResponse response = new LoginResponse();
    response.setAccessToken(newAccessToken);
    response.setRefreshToken(newRefreshToken);
    response.setExpiresIn(7200);
    response.setTokenType("Bearer");
    return R.ok(response);
}
```

---

## 6. 文件清单与修改范围

### 6.1 新增文件（21 个）

| 文件路径 | 类型 | 职责 |
|---------|------|------|
| `entity/School.java` | 新增 Entity | 学校/租户实体 |
| `repository/SchoolRepository.java` | 新增 Mapper | 学校 CRUD |
| `controller/admin/PlatformAdminController.java` | 新增 Controller | 平台管理 API |
| `service/SchoolService.java` | 新增 Service | 学校管理业务 |
| `service/impl/SchoolServiceImpl.java` | 新增 Service Impl | 学校管理业务实现 |
| `service/SchoolScopeService.java` | 新增 Service | 学校访问范围校验 |
| `service/impl/SchoolScopeServiceImpl.java` | 新增 Service Impl | scope 校验实现 |
| `util/TenantContext.java` | 新增 Util | TenantContext ThreadLocal |
| `config/TenantInterceptor.java` | 新增 MyBatis-Plus 拦截器 | 自动追加 school_id 条件 |
| `security/SchoolScope.java` | 新增 Annotation | @SchoolScope 注解 |
| `security/SchoolScopeAspect.java` | 新增 Aspect | @SchoolScope AOP |
| `security/SkipTenantFilter.java` | 新增 Annotation | @SkipTenantFilter 注解 |
| `dto/SchoolVO.java` | 新增 DTO | 学校视图 |
| `dto/LoginRequest.java`（→ 扩展） | 修改 DTO | 加 schoolCode |
| `dto/RegisterRequest.java`（→ 扩展） | 修改 DTO | 加 schoolCode |
| `views/admin/SchoolList.vue` | 新增 Vue | 学校管理页面（PLATFORM_ADMIN） |
| `views/admin/SchoolDetail.vue` | 新增 Vue | 学校详情编辑 |
| `views/admin/PlatformDashboard.vue` | 新增 Vue | 平台级全局 Dashboard |
| `layouts/components/SchoolSwitcher.vue` | 新增 Vue | 学校切换器组件 |
| `api/platform.js` | 新增 API | 平台管理 API 封装 |
| `store/platform.js` | 新增 Store | 平台管理 Pinia Store |

### 6.2 修改文件（59 个 — 表对应 Entity + Repository + Service 三层）

**Entity 层**（所有 59 张表 Entity）：

| 修改文件 | 修改内容 |
|---------|---------|
| `entity/User.java` | 新增 `private Long schoolId;` + getter/setter |
| `entity/Department.java` | 新增 `private Long schoolId;` |
| `entity/Major.java` | 新增 `private Long schoolId;` |
| `entity/Classes.java` | 新增 `private Long schoolId;` |
| `entity/Course.java` | 新增 `private Long schoolId;` |
| ... 其余 54 张表实体 | 同上（添加 `schoolId` 字段）+ `@TableField("school_id")` |

**Service 层**：

| 修改文件 | 修改内容 |
|---------|---------|
| `service/impl/AuthServiceImpl.java` | `register()`、`login()` 解析 schoolCode → 设 `User.schoolId`；`generateToken()` 传 schoolId |
| `service/impl/AuthCasLoginServiceImpl.java` | `loginOrRegister()` 解析 school 上下文 → 设 `User.schoolId` |
| `service/impl/UserBatchImportServiceImpl.java` | `buildUserFromRow()` 新增 `user.setSchoolId(tenantContext.getCurrentSchoolId())` |
| `service/impl/UserServiceImpl.java` | `createUser()` 从上下文取 schoolId |
| `service/impl/BannerServiceImpl.java` | 查询自动带 school_id 条件（无需改逻辑，拦截器自动） |
| `service/impl/AdminSettingServiceImpl.java` | school_id=0 全局配置 + school_id>0 学校级配置 |
| `service/impl/OperationLogServiceImpl.java` | `log()` 自动写 schoolId |
| 所有 Service impl 文件 | 无需改逻辑 — 拦截器自动处理 school_id 追加；Service 层在跨学校写操作时调用 `SchoolScopeService.assertSchoolAccess()` |

**Controller 层**：

| 修改文件 | 修改内容 |
|---------|---------|
| `controller/AuthController.java` | 新增 `/api/auth/switch-school` 端点；`register()` 接收 schoolCode |
| 现有其他 Controller | 无修改（仅加 `@SchoolScope` 在需要的地方） |

**Config 层**：

| 修改文件 | 修改内容 |
|---------|---------|
| `config/MyBatisPlusConfig.java` | 注册 `TenantInterceptor` |
| `config/JwtAuthenticationFilter.java` | `doFilterInternal` 提取 schoolId → 写入 `TenantContext` |
| `config/SecurityConfig.java` | 新增 `/api/platform/**` 路径权限；放行 `POST /api/auth/switch-school` |
| `config/WebMvcConfig.java` | 文件路径含 `{schoolId}` |

**Util 层**：

| 修改文件 | 修改内容 |
|---------|---------|
| `util/JwtUtil.java` | `generateToken()` 新增 schoolId 参数；`getSchoolIdFromToken()` 新方法 |
| `util/RedisUtil.java` | 登录失败 key 含 schoolId（`mc:login:lock:{schoolId}:{username}`） |
| `util/SecurityUtil.java` | 新增 `hasRole("PLATFORM_ADMIN")` 兼容；`isAdminOrPlatformAdmin()` 方法 |

**DTO 层**：

| 修改文件 | 修改内容 |
|---------|---------|
| `dto/LoginRequest.java` | 新增 `schoolCode` 字段（可选） |
| `dto/RegisterRequest.java` | 新增 `schoolCode` 字段（必填） |
| `dto/UserVO.java` | 新增 `schoolId`、`schoolName` 字段返回 |

**Enum 层**：

| 修改文件 | 修改内容 |
|---------|---------|
| `enums/UserRole.java` | 新增 `PLATFORM_ADMIN` 枚举值 |

**前端**：

| 修改文件 | 修改内容 |
|---------|---------|
| `src/router/index.js` | `getRoleHomePage()` 新增 `PLATFORM_ADMIN → /admin/platform/dashboard`；`PLATFORM_ADMIN` 角色路由声明 |
| `src/utils/request.js` | 可选：`X-School-Id` header（前端侧选项，非必须） |
| `src/store/user.js` | `login()` 传 schoolCode；`switchSchool()` 新 action |
| `src/views/auth/Login.vue` | 登录页新增 schoolCode 输入框（可选） |
| `src/views/auth/Register.vue` | 注册页新增 schoolCode 输入框（必填） |
| `src/utils/auth.js` | 新增 `getSchoolId()`/`setSchoolId()` 存储（可选） |

### 6.3 Flyway 迁移文件（3 个）

| 文件名 | 内容 |
|--------|------|
| `V200__create_schools.sql` | `CREATE TABLE schools` + 默认学校种子数据 |
| `V201__add_school_id.sql` | 59 张表 `ALTER TABLE xxx ADD COLUMN school_id BIGINT`（可空）|
| `V202__backfill_school_id.sql` | `UPDATE ... SET school_id = 1 WHERE school_id IS NULL`（backfill）|
| `V203__set_school_id_not_null.sql` | `ALTER TABLE xxx ALTER COLUMN school_id SET NOT NULL` + 重建 FK |
| `V204__rebuild_unique_indexes.sql` | DROP 旧 UNIQUE，CREATE (school_id, Xxx) 联合唯一 |

---

## 7. 分阶段安全迁移计划

### Phase A（打地基 — 学校管理 + PLATFORM_ADMIN 角色）
1. 创建 `schools` 表 + 默认学校种子（school.id = 1）
2. `UserRole` 枚举新增 `PLATFORM_ADMIN`
3. 新增 PLATFORM_ADMIN 的 CRUD 端点（`/api/platform/schools`）
4. 前端学校管理页面（只对新角色可见）
5. **交付门禁**：PLATFORM_ADMIN 可创建/编辑学校，现有系统无影响

### Phase B（数据迁移 — 59 表加 school_id）
1. 执行 V201 迁移（ADD COLUMN school_id，可空）
2. 执行 V202 backfill（school_id = 1，假设当前所有数据属默认学校）
3. 执行 V203 设置 NOT NULL + FK
4. 执行 V204 重建唯一索引
5. **交付门禁**：所有现有 API 不变、数据完整、唯一约束不冲突

### Phase C（运行时隔离 — TenantContext + 拦截器）
1. 新增 `TenantContext` 类
2. 新增 `TenantInterceptor` 注册到 MyBatis-Plus
3. 新增 `@SkipTenantFilter` 注解
4. `JwtAuthenticationFilter` 提取 schoolId → 写 `TenantContext`
5. `JwtUtil.generateToken()` 含 schoolId claim
6. **交付门禁**：新用户请求自动带 school_id 条件；手动 SQL 测试 PLATFORM_ADMIN skip

### Phase D（认证入口集成 — Login/Register/CAS/Import）
1. `LoginRequest` 扩展 schoolCode
2. `RegisterRequest` 扩展 schoolCode
3. `AuthServiceImpl.login()` 解析 schoolCode
4. `AuthServiceImpl.register()` 解析 schoolCode
5. `AuthCasLoginServiceImpl` 解析 school 上下文
6. `UserBatchImportServiceImpl` 从上下文取 schoolId
7. 前端登录/注册页 schoolCode 输入
8. **交付门禁**：每条认证路径都正确写入 schoolId + 生成含 schoolId 的 JWT

### Phase E（PLATFORM_ADMIN scope — 学校切换 + Dashboard）
1. `POST /api/auth/switch-school` 端点
2. `SchoolSwitcher.vue` 前端组件
3. `@SchoolScope` 注解 + AOP
4. `SchoolScopeService.assertSchoolAccess()`
5. 平台级全局 Dashboard
6. **交付门禁**：PLATFORM_ADMIN 可切换学校看到对应学校数据；未切换时看到全局汇总

### Phase F（审计/缓存/文件隔离）
1. `operation_logs.schoolId` 写入
2. Redis 登录失败 key 含 schoolId
3. 文件存储路径含 schoolId
4. **交付门禁**：日志/缓存/文件三阵地完成隔离

### 回滚策略

| 阶段 | 回滚操作 | 数据损失 | 耗时 |
|------|---------|---------|------|
| A | 回滚 V200 + 删除新增 Entity/Controller | 无 | 30min |
| B | 执行 U201（DROP COLUMN school_id）+ 重建旧索引 | 无 | 1h（59 表） |
| C | 移除 `TenantInterceptor` + 回滚 JwtAuthenticationFilter | 无 | 15min |
| D | 回退 LoginRequest/RegisterRequest 字段 | 无 | 15min |
| E | 回滚 SchoolSwitcher + SchoolScopeService | 无 | 30min |
| F | 回滚 Redis key 变更 + 文件路径变更 | 无 | 15min |

**注意**：
- Phase B 执行前必须全量数据库备份
- 每个 Phase 单独 PR + CI 验证，不跨 Phase 合并
- Phase B 的 ALTER TABLE 建议在低峰期执行 `SET lock_timeout = '30s'` 防止死锁

---

## 8. 测试矩阵

| 测试场景 | 用例 | 预期 | 级别 |
|---------|------|------|------|
| **学校管理** | PLATFORM_ADMIN 创建学校 | 学校创建成功，schools 表新行 | P0 |
| | PLATFORM_ADMIN 编辑学校 | 更新成功 | P0 |
| | ADMIN（非 PLATFORM_ADMIN）创建学校 | 403 | P0 |
| **认证** | 新用户注册时传有效 schoolCode | 用户 school_id 正确 | P0 |
| | 新用户注册时传无效 schoolCode | 400 错误码 | P0 |
| | 用户登录时传 schoolCode | JWT 含 schoolId claim | P0 |
| | CAS 登录（带 state schoolCode） | 用户 school_id 正确 | P0 |
| | CAS 登录（domain 匹配） | 自动匹配学校 | P1 |
| **数据隔离** | A 学校用户查询不会看到 B 学校用户 | 列表仅含本校 | P0 |
| | A 学校 ADMIN 查询课程列表 | 仅该校课程 | P0 |
| | 请求手动修改 SQL（绕过）school_id 被拦截 | 拦截条件有效 | P0 |
| | 跨 IDOR：A 学校用户通过 ID 查 B 学校课程 | 404 或 403 | P0 |
| **PLATFORM_ADMIN** | 未切换学校时查全局 Dashboard | 跨学校汇总 | P0 |
| | 切换学校后查课程列表 | 仅该校课程 | P0 |
| | 切换学校后写操作 | 成功写入该校 | P0 |
| | 未切换学校时写操作 | 403（需指定学校） | P0 |
| **批量导入** | 导入 CSV 含学校关联数据 | 新用户 school_id 正确 | P0 |
| | 导入时不传学校上下文（无 schoolCode） | 400 | P1 |
| **文件隔离** | A 学校上传封面 | 路径含 A 学校 ID | P0 |
| | A 学校封面 URL B 学校用户不可直接访问 | WebMvc 层面隔离 | P1 |
| **审计日志** | A 学校用户的操作日志含 school_id | 查询可过滤 | P0 |
| **唯一约束** | 两所学校下同 username | 各自可用 | P0 |
| | 同一学校下重复 username | uk_user_school_username 报错 | P0 |
| **性能** | school_id 索引生效 EXPLAIN | Index Scan | P1 |
| | 全校查询（无 school 上下文） | @SkipTenantFilter 跳过 | P0 |

---

## 9. MVP 验证计划

| MVP 项 | 优先级 | 验证目标 | 预计 assistant | 失败回退方案 |
|--------|--------|---------|---------------|-------------|
| MVP-1 | P0 | `TenantInterceptor` 自动追加 `school_id = ?` 的正确性 | 1 | 回退至显式 Service 层传参方案 |
| MVP-2 | P0 | JWT schoolId claim 的写入/读取/TenantContext 赋值 | 1 | 回退至 Header X-School-Id 方案 |
| MVP-3 | P1 | PLATFORM_ADMIN `@SkipTenantFilter` 全局查询 | 1 | 回退至纯 SQL 硬编码无拦截器 |

### MVP-1：TenantInterceptor 自动追加 school_id

**验证目标**：验证 MyBatis-Plus InnerInterceptor 能正确拦截 SQL 并追加 `AND school_id = ?`

**可转发的 assistant prompt**：

> **任务目标**: 独立验证 MyBatis-Plus TenantInterceptor 的 school_id 追加行为
> **执行范围**: `micro-course-api/src/test/java/com/microcourse/config/`
> **可做**:
>   - 在 `Script/Tests/` 或测试目录创建独立测试文件 `MVP_TenantInterceptorTest.java`
>   - 模拟 MyBatis-Plus MappedStatement 和 BoundSql
>   - 验证：
>     1. 不带 `@SkipTenantFilter` 时 SQL 被追加 `WHERE school_id = ?`
>     2. 带 `@SkipTenantFilter` 时 SQL 不变
>     3. PLATFORM_ADMIN 且 TenantContext.isSkipFilter() == true 时不追加
>     4. 已有 WHERE 子句时正确 AND 追加
>     5. 无 WHERE 子句时追加 WHERE school_id = ?
>   - 打印 SQL 验证结果
> **不可做**: 不修改任何业务代码（非 Tests/ 目录）
> **预期返回**:
>   - 每种场景的 SQL 输出对比（before → after）
>   - 是否全部 5 项通过
>   - 如失败，完整错误信息

**失败处理**：若验证失败，回退至「所有 Repository 查询显式追加 `.eq(User::getSchoolId, tenantContext.getCurrentSchoolId())`」方案

### MVP-2：JWT schoolId 完整链路

**验证目标**：验证 JWT 生成→解析→写 SecurityContext→TenantContext 的完整链路

**可转发的 assistant prompt**：

> **任务目标**: 独立验证 JWT 中 schoolId claim 的完整读写链路
> **执行范围**: `Script/Tests/MVP_JWT_SchoolId_Test.java`（新建）
> **可做**:
>   - 用 JwtUtil.generateToken() 生成含 schoolId 的 token
>   - 用 JwtUtil.getSchoolIdFromToken() 读取 schoolId
>   - 模拟 JwtAuthenticationFilter 的 doFilter 流程提取 schoolId
>   - 验证 TenantContext.getCurrentSchoolId() 与 claim 一致
>   - 验证多角色 token 解析：ADMIN、PLATFORM_ADMIN
>   - 验证缺失 schoolId 的情况（兼容旧 token）
> **不可做**: 不修改业务代码
> **预期返回**:
>   - 各场景打印：claim → Token → Filter → Context
>   - 是否全部通过

**失败处理**：兼容旧 token 的 null schoolId，用 `TenantContext.setSchoolId(0L)` 特殊值标记

### MVP-3：PLATFORM_ADMIN @SkipTenantFilter 全局查询

**验证目标**：验证 PLATFORM_ADMIN 角色用 @SkipTenantFilter 跳过 school_id 过滤后能查到全局数据

**可转发的 assistant prompt**：

> **任务目标**: 验证 @SkipTenantFilter 注解在 TenantInterceptor 中的效果
> **执行范围**: `Script/Tests/MVP_SkipTenantFilterTest.java`（新建）
> **可做**:
>   - 创建测试 Mock Mapper 接口，标注 @SkipTenantFilter
>   - 在 TenantInterceptor 中检测该注解，确认不追加 school_id
>   - 未标注时确认追加 school_id
>   - 测试 PLATFORM_ADMIN 角色的 TenantContext.isSkipFilter()
> **不可做**: 不修改业务代码
> **预期返回**: 两种场景的 SQL 是否按预期过滤/放过

**失败处理**：用「查询时不走 MyBatis-Plus 拦截器，直接手写 SQL 排除 school_id 条件」作为回退

---

## 10. 技术可行性评估

| 不确定点 | 建议验证方式 | 结论预期 |
|---------|------------|---------|
| MyBatis-Plus TenantInterceptor 与 PaginationInnerInterceptor 顺序 | MVP-1 验证叠加效果 | 先 Tenant → 再 Pagination |
| JWT schoolId claim 兼容旧 token（无 schoolId） | MVP-2 验证 null 处理 | TenantContext.setSchoolId(0L) 兼容 |
| 59 张表 ADD COLUMN 锁表风险 | 生产低峰期 `SET lock_timeout='10s'` | 逐一加列可接受 |
| `@SkipTenantFilter` 反射扫描性能 | 无大流量扫描场景 | 可忽略 |
| `PLATFORM_ADMIN` 在现有 `@PreAuthorize("hasRole('ADMIN')")` 中的位置 | 非 PLATFORM_ADMIN 不能调 ADMIN 接口 | 新角色用 `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` 独立声明 |

---

*设计版本：v1.0*  
*最后更新：2026-07-26*  
*策略文档：docs/plans/multi-tenant-school-strategy_2026-07-26.md*
