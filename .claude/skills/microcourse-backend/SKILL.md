---
name: microcourse-backend
description: |
  微课平台后端 Java 开发实施手册。
  当用户在本项目 micro-course-api/ 下编写 Spring Boot 3 + MyBatis-Plus + JWT + Redis 代码时加载本 skill。

  前置技能：microcourse（项目宪法，自动加载）
  用途：提供 Controller / Service / Repository / DTO / Security / Exception 的代码模板和实现模式

  与 microcourse/SKILL.md 的关系：
  - microcourse 定义"不能做什么"（25 条禁止项）+ "契约是什么"（6 份 references）
  - 本 skill 定义"应该怎么做"（6 份代码模板 + 实现流程）
  - 本 skill 不重复 microcourse 的禁止项，二者互补
---

# 微课平台 · 后端开发技能

## 1. 何时加载本 skill

在 `/Users/jackie/微课平台/micro-course-api/` 下创建/修改 Java 文件时加载。

不加载场景：只读查阅、前端开发、文档编写、DB 操作。

## 2. 标准实现流程

### ⚠️ Lombok 已禁用（铁律 · 不可逆）

> **根因**：JDK 17.0.18 (Homebrew) 与 Lombok 注解处理器冲突，`annotationProcessorPaths`
> 编译期报 `com.sun.tools.javac.code.TypeTag UNKNOWN`。
>
> **结论**：本项目**禁止使用一切 Lombok 注解**，包括但不限于：
> `@Data` `@Getter` `@Setter` `@ToString` `@EqualsAndHashCode`
> `@NoArgsConstructor` `@AllArgsConstructor` `@Builder` `@RequiredArgsConstructor` `@Slf4j`
>
> **替代方案**：
> | Lombok 注解 | 替代方式 |
> |---|---|
> | `@Data` / `@Getter` / `@Setter` | 手写 getter/setter（IDE 生成即可） |
> | `@RequiredArgsConstructor` | `@Autowired` 构造器注入 或 显式 `public XxxService(XxxRepository r) { ... }` |
> | `@Slf4j` | 直接声明 `private static final Logger log = LoggerFactory.getLogger(Xxx.class);` |
> | `@Builder` | 手写 Builder 模式 或 使用 MyBatis-Plus `LambdaUpdateWrapper` |
> | `@NoArgsConstructor` / `@AllArgsConstructor` | 显式声明构造函数 |
>
> **违规检测**：`precheck.sh` 第 13 条规则检测 `import lombok` 并报 FAIL。

### Step 1：读 references/（契约层，来自 microcourse skill）

```
microcourse/references/data-contract.md      → Entity 字段定义
microcourse/references/api-contract.md        → REST 路径 + 响应格式 + 错误码
microcourse/references/business-logic.md      → 状态机 + 业务规则
microcourse/references/permission-matrix.md   → @PreAuthorize 注解
microcourse/references/structure-constitution.md → 目录/禁止项
```

### Step 2：选代码模板

按模块从 `templates/` 读取对应模板，用域参数替换占位符：
**注意：模板中已移除所有 Lombok 注解，直接复制即可。**

| 模板 | 用途 | 渲染后的文件路径 |
|------|------|----------------|
| `templates/controller.txt` | REST Controller | `controller/{Domain}Controller.java` |
| `templates/service.txt` | Service 接口 + 实现类 | `service/{Domain}Service.java` + `service/impl/{Domain}ServiceImpl.java` |
| `templates/repository.txt` | MyBatis-Plus Mapper | `repository/{Domain}Repository.java` |
| `templates/dto.txt` | Request/VO/PageResult | `dto/{Domain}CreateRequest.java` 等 |
| `templates/security.txt` | JWT + SecurityConfig | `config/SecurityConfig.java` 等 |
| `templates/exception.txt` | GlobalExceptionHandler + ErrorCode | `exception/ErrorCode.java` 等 |

### Step 3：按域装配

| 域 | 创建的文件 | 模板 |
|----|----------|------|
| Auth | AuthController / AuthService / AuthServiceImpl / LoginRequest / LoginResponse / UserVO | controller + service + dto |
| Department | DepartmentController / DepartmentService / DepartmentServiceImpl / DepartmentRepository / DepartmentCreateRequest / DepartmentUpdateRequest / DepartmentVO | controller + service + repository + dto |
| Major | （同上，替换 Department → Major） | controller + service + repository + dto |
| Class | （同上） | controller + service + repository + dto |
| User | UserController / UserService / UserServiceImpl / UserRepository / UserCreateRequest / UserUpdateRequest / UserPageQuery / UserVO / UserStatusRequest | controller + service + repository + dto |

### Step 4：自检

按 `microcourse/references/verification-checklist.md` 逐项打勾。

### Step 5：启动验证

```bash
cd micro-course-api
mvn compile -q                     # 编译
mvn spring-boot:run &              # 启动
curl http://localhost:8080/api/... # 验证 API
bash .claude/skills/microcourse/scripts/precheck.sh  # 预检
```

---

## 3. 关键代码模式

### 3.1 Controller 模式

```java
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<DepartmentVO>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(departmentService.page(page, size));
    }

    @GetMapping("/{id}")
    public R<DepartmentVO> getById(@PathVariable Long id) { ... }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<DepartmentVO> create(@Valid @RequestBody DepartmentCreateRequest req) { ... }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<DepartmentVO> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest req) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) { ... }
}
```

**铁律**：
- `@PreAuthorize` 放在 **Controller 方法上**，不放 Service
- 成功统一返回 `R.ok(data)`
- 参数校验用 `@Valid @RequestBody`
- 分页参数 page 从 0 开始，size 默认 20

### 3.2 Service 模式

```java
public interface DepartmentService {
    PageResult<DepartmentVO> page(int page, int size);
    DepartmentVO getById(Long id);
    DepartmentVO create(DepartmentCreateRequest req);
    DepartmentVO update(Long id, DepartmentUpdateRequest req);
    void delete(Long id);
}

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository, MajorRepository majorRepository) {
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
    }

    @Override
    public void delete(Long id) {
        Department dept = departmentRepository.selectById(id);
        if (dept == null) throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);

        // 删除前置检查
        long majorCount = majorRepository.selectCount(
            new LambdaQueryWrapper<Major>().eq(Major::getDepartmentId, id));
        if (majorCount > 0) throw new BusinessException(ErrorCode.DEPARTMENT_HAS_MAJORS);

        departmentRepository.deleteById(id);
    }
}
```

**铁律**：
- 删除前置检查在 Service 层实现
- 异常用 `BusinessException(ErrorCode.XXX)`
- 使用 `LambdaQueryWrapper` 而非字符串拼接

### 3.3 Repository 模式

```java
@Mapper
public interface DepartmentRepository extends BaseMapper<Department> {
    // 简单查询用 BaseMapper 自带方法
    // 复杂查询才加自定义方法
}
```

**铁律**：
- 继承 `BaseMapper<Entity>` 即可
- 自定义查询优先 `LambdaQueryWrapper`，不用 `@Select` 拼接 SQL
- 分页用 `IPage<Entity> page = repository.selectPage(new Page<>(page, size), wrapper)`

### 3.4 DTO 模式

```java
// Request（@Valid 校验注解）
public class DepartmentCreateRequest {
    @NotBlank(message = "院系名称不能为空")
    private String name;
    @NotBlank(message = "院系代码不能为空")
    private String code;
    private Long parentId;
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}

// VO（响应用，不带密码等敏感字段）
public class DepartmentVO {
    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private Integer sortOrder;
    private String parentName;      // 关联查询字段
    private LocalDateTime createdAt;

    // ... getter/setter（同上模式）
}

// PageResult（统一分页）
public class PageResult<T> {
    private List<T> items;
    private int page;
    private int size;
    private long totalElements;
    private long totalPages;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setItems(page.getRecords());
        r.setPage((int) page.getCurrent());
        r.setSize((int) page.getSize());
        r.setTotalElements(page.getTotal());
        r.setTotalPages(page.getPages());
        return r;
    }
}
```

### 3.5 R 统一响应

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.message = "ok";
        r.data = data;
        r.timestamp = System.currentTimeMillis();
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.timestamp = System.currentTimeMillis();
        return r;
    }
}
```

### 3.6 JWT 模式

```java
// 签发（含 6 字段 claims）
public String generateToken(Long userId, String username, UserRole role, Long departmentId) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("username", username)
        .claim("role", role.name())
        .claim("departmentId", departmentId)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .signWith(getKey())
        .compact();
}

// RefreshToken（7 天）
public String generateRefreshToken(Long userId) {
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
        .signWith(getKey())
        .compact();
}

// 黑名单校验
if (redisUtil.isTokenBlacklisted(jti)) {
    throw new BusinessException(ErrorCode.TOKEN_INVALID);
}
```

### 3.7 ErrorCode 枚举

```java
public enum ErrorCode {
    // 认证 1xxx
    INVALID_CREDENTIALS(1001, "用户名或密码错误", 401),
    ACCOUNT_DISABLED(1002, "账号已被禁用", 401),
    ACCOUNT_DELETED(1003, "账号已被删除", 401),
    TOKEN_EXPIRED(1004, "Token 已过期", 401),
    TOKEN_INVALID(1005, "Token 格式错误", 401),
    LOGIN_LOCKED(1006, "登录失败次数过多，账号已锁定", 423),
    // 院系 2xxx
    DEPARTMENT_NOT_FOUND(2001, "院系不存在", 404),
    DEPARTMENT_HAS_MAJORS(2002, "院系下存在专业，无法删除", 409),
    // 专业 3xxx
    MAJOR_NOT_FOUND(3001, "专业不存在", 404),
    MAJOR_HAS_CLASSES(3002, "专业下存在班级，无法删除", 409),
    // 班级 4xxx
    CLASS_NOT_FOUND(4001, "班级不存在", 404),
    // 用户 5xxx
    USER_NOT_FOUND(5001, "用户不存在", 404),
    USERNAME_EXISTS(5002, "用户名已存在", 409),
    STUDENT_NO_EXISTS(5003, "学号/工号已存在", 409),
    EMAIL_EXISTS(5004, "邮箱已存在", 409);

    // ... getter / httpStatus 字段
}
```

---

## 4. 详细模板（templates/）

- **controller.txt** → REST Controller 骨架（含 @PreAuthorize + 参数注解 + 分页）
- **service.txt** → Service 接口 + 实现类骨架（含删除前置 + 关联查询）
- **repository.txt** → MyBatis-Plus Mapper 骨架 + LambdaQueryWrapper 示例
- **dto.txt** → Request/VO/PageResult 骨架（含校验注解 + 关联字段）
- **security.txt** → SecurityConfig + JwtAuthFilter + JwtUtil 骨架
- **exception.txt** → GlobalExceptionHandler + ErrorCode 完整枚举

每个模板包含：
- 包声明 + import + 类声明 + 方法签名
- `/* ---- 替换点: {Domain} ---- */` 标记
- 与 microcourse/references/ 的交叉链接注释

---

## 5. 与其他 skill 的关系

| skill | 关系 | 触发时机 |
|-------|------|---------|
| **microcourse** | 前置技能（宪法层） | 本项目所有操作 |
| **microcourse-backend** | 本 skill | 写 Java 代码时 |

## 6. 预检

创建 Java 文件前，必须跑：
```bash
bash .claude/skills/microcourse/scripts/precheck.sh
```

---

*skill 版本：v1.0*
*最后更新：2026-06-11*
*维护者：总工程师*
