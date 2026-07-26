# 多租户学校隔离 — 策略文档

> **阶段**：Phase 2 — 策略文档  
> **日期**：2026-07-26  
> **前置**：用户已确认新增 schools/tenant 模型、PLATFORM_ADMIN 角色、全业务域隔离  
> **约束**：不修改业务代码（此阶段仅产出设计）

---

## 1. 问题定义

当前系统为**单学校架构**：所有用户同一级（departments→majors→classes→users），ADMIN/ACADEMIC 角色拥有全局 CRUD 权限。需改造为**多租户学校架构**：每所学校独立管理组织架构、用户、课程、学习、订单、日志、设置、Banner 等全部业务域，PLATFORM_ADMIN 可跨学校查看但不自动具备写权限。

---

## 2. 现状分析

### 2.1 当前架构关键特征

| 维度 | 现状 | 文件:行 |
|------|------|---------|
| **角色** | STUDENT/TEACHER/ADMIN/ACADEMIC — 4 角色 | `enums/UserRole.java:3-8` |
| **用户模型** | `users` 表含 departmentId/majorId/classId FK 链 | `entity/User.java:32-39` |
| **JWT claims** | sub(userId), username, role, departmentId, jti | `util/JwtUtil.java:82-84` |
| **角色鉴权** | `@PreAuthorize("hasRole('ADMIN')")` + SecurityUtil | `config/SecurityConfig.java:41` |
| **前端路由** | router meta `roles` 数组匹配 | `router/index.js:16-188` |
| **分页** | MyBatis-Plus PaginationInnerInterceptor | `config/MyBatisPlusConfig.java:15-17` |
| **Redis key 模式** | `mc:login:lock:`, `mc:jwt:blacklist:` | `util/RedisUtil.java:80-217` |
| **文件上传** | `uploads/{covers,avatars,banners,videos}/` 按类别 | `config/SecurityConfig.java:146-158` |
| **CAS 登录** | `AuthCasLoginServiceImpl` 支持 role upgrade | `impl/AuthCasLoginServiceImpl.java:50-127` |
| **批量导入** | `UserBatchImportServiceImpl` 按姓名→ID 映射 | `impl/UserBatchImportServiceImpl.java:67-284` |

### 2.2 核心约束

1. **59+ 表**全部与用户或组织架构关联，需 school_id 隔离
2. **departments** 当前 code 为 UNIQUE（`uk_departments_code`），多学校后需改为 (school_id, code) 联合唯一
3. **ADMIN 角色**当前拥有全权限，迁移后转为「学校级 ADMIN」；新增「平台级 PLATFORM_ADMIN」
4. **PLATFORM_ADMIN scope**：默认可看全局汇总，进入学校级详情/任何租户写操作需明确 school 上下文
5. **自助注册**提交 schoolCode，**CAS 与批量导入**继承学校上下文
6. 全业务域隔离：users/组织/课程/学习/订单/日志/设置/Banner 及全部租户业务

---

## 3. 方案对比

### 方案 A：共享表 + school_id 列（推荐）

**核心思路**：现有表追加 school_id 列，通过 TenantContext 线程级注入实现行级隔离；MyBatis-Plus 拦截器自动追加 school_id = ? 条件；PLATFORM_ADMIN 跳过自动过滤，由 Service 层 scope 控制。

| 维度 | 描述 |
|------|------|
| 新表 | `schools` 表 + school_id FK 列到 59 张现存表 |
| 隔离粒度 | 行级（school_id 列），索引优先 (school_id, Xxx) |
| 数据迁移 | 一次全量 backfill school_id = 1（默认学校），后续新增需指定 |
| 迁移复杂度 | 中低 —— 无数据移动，只加列 + 加索引 |
| 查询性能 | 有索引下过滤良好，无 JOIN 性能损耗 |
| 代码侵入 | 中 —— TenantContext filter + MyBatis-Plus 拦截器 + 显式 skip |

**优点**：
- 数据集中管理，运维简单（一个 PostgreSQL cluster）
- 现有 FK 链（departments→majors→classes→users）只需每个表加 school_id
- 跨学校汇总报表直接 SQL，无需跨 DB 查询
- PLATFORM_ADMIN 的 scope 控制简单 —— 拦截器可 skip

**缺点**：
- 所有表都需加 school_id 列（59+ 表 = 59 次 ALTER TABLE + 索引重建）
- PLATFORM_ADMIN 的「跳过过滤」场景需要精确的 `@SkipTenantFilter` 注解管理
- 批量导入/数据统计用大 SQL 时，若漏加 school_id 条件可能导致数据泄露

### 方案 B：Schema 级隔离（PostgreSQL Schema）

**核心思路**：每所学校一个独立 PostgreSQL Schema（如 `school_1`、`school_2`），同一数据库实例，Flyway 迁移对所有 Schema 执行。

**优点**：
- 物理隔离最强，一条 SQL 都不会跨学校
- PLATFORM_ADMIN 切换到对应 Schema 查询
- 按学校可独立备份/恢复

**缺点**：
- Flyway 需多 Schema 支持配置复杂（需每新增学校跑迁移）
- 跨学校汇总报表需 `UNION ALL` 所有 Schema，性能灾难
- 现有代码大量 `@TableName` 硬编码（无 Schema 前缀），需全局改
- PostgreSQL Schema 数量过多时（100+），`search_path` 管理复杂
- 无法使用 FK 跨 schema —— 约束退化
- 部署升级时必须确保所有 Schema 迁移完成，失败回滚复杂

### 方案 C：独立数据库实例

**核心思路**：每所学校一个独立物理/容器数据库，应用层通过多数据源路由。

**优点**：
- 极端隔离，符合金融级合规
- 可独立扩缩容、独立备份

**缺点**：
- 运维成本极高（59+ 张表 × N 所学校）
- 部署流程需为每所学校建数据库、跑迁移、配置种子数据
- 跨学校汇总需外部队列同步到数仓
- 代码层面需多数据源 + 动态路由 —— Spring 多 DataSource 配置爆炸
- FK 链完全不能跨 DB，需要应用层逻辑保障数据完整性
- **严重过度设计**：当前场景为同一高校下的多学院/多校区，非 SaaS 多租户

---

## 4. 推荐方案

### 选定方案 A（共享表 + school_id 列）

**理由**：
1. 用户确认场景为「同一高校下多学院/多校区统一管理」，非独立 SaaS 租户
2. 跨学校汇总（PLATFORM_ADMIN 看全局）是明确需求，方案 A 天然支持
3. 现有 FK 链（departments→majors→classes→users）只需依次加 school_id，不断裂
4. FK 约束 + school_id NOT NULL 保证数据完整性，无需应用层复杂校验
5. 代码侵入可控：TenantContext Filter + MyBatis-Plus 拦截器 + 显式注解 skip
6. 已存数据只需一次 backfill school_id=1，无需数据移动

---

## 5. 关键风险

| 类别 | 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|------|----------|
| **数据** | 现有数据 backfill 漏表导致 school_id=NULL 破坏 NOT NULL | 中 | P0 | 逐表确认清单 + 迁移前全量校验 |
| **安全** | PLATFORM_ADMIN 因 skip 注解遗漏而不当访问学校数据 | 中 | P0 | Service 层二次校验 `assertSchoolAccess()` |
| **性能** | 59 张表 ALTER TABLE ADD school_id + 建索引导致锁表 | 高 | P1 | 用 `CREATE INDEX CONCURRENTLY` + 业务低峰期执行 |
| **代码** | `@SkipTenantFilter` 遗漏导致 PLATFORM_ADMIN 查询被过滤 | 中 | P1 | 代码审查 + 集成测试覆盖 PLATFORM_ADMIN 场景 |
| **迁移** | 迁移过程违反 NOT NULL | 中 | P0 | 先加可空列 → backfill → 设 NOT NULL（三步迁移） |
| **第三方** | CAS 认证虽继承 school 上下文，但外部系统无此信息 | 低 | P2 | CAS callback URL 带 schoolCode 参数（state 字段已存在） |

---

## 6. 需用户确认的问题

- [x] 方案 A（共享表 + school_id 列）已确认
- [x] PLATFORM_ADMIN scope 规则：默认看全局汇总，进入学校级详情/写操作需明确 school 上下文
- [x] 自助注册提交 schoolCode，CAS 与批量导入继承学校上下文
- [ ] **Schools 表初始种子数据**：平台启动时创建"默认学校"还是通过管理页面创建？
- [ ] **PLATFORM_ADMIN 数量限制**：是否允许多个 PLATFORM_ADMIN？还是仅 1 个？
- [ ] **现有 ADMIN 升级路径**：当前 DB 中的 ADMIN 用户是否全部保留为学校级 ADMIN？还是需要逐个指定所属学校？
- [ ] **school_code 生成规则**：自助注册时用的 schoolCode 是学校代码（如 pku/tsinghua）还是 UUID？
- [ ] **文件存储隔离**：`uploads/` 下按学校子目录（如 `uploads/school_1/covers/`）还是全局共享？
