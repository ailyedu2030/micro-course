# 结构宪法引用视图

> **源文档**：[`docs/项目结构规范.md`](../../项目结构规范.md) v1.1
> **视图性质**：引用视图
> **冲突裁决**：以冲突评审决议为准（v1.1 已落地 C1+C5）

---

## 1. 目录树（唯一允许）

```
微课平台/
├── micro-course-api/                          # 后端：Spring Boot 3 + Java 17 + Maven + MyBatis-Plus
│   ├── pom.xml
│   └── src/main/java/com/microcourse/
│       ├── MicroCourseApplication.java        # 启动类（必须放在根包下）
│       ├── config/                            # 全局配置
│       ├── controller/                        # REST Controller
│       ├── service/                           # 业务逻辑接口
│       │   └── impl/                          # 业务逻辑实现
│       ├── repository/                        # MyBatis-Plus Mapper
│       ├── entity/                            # 数据库表映射实体
│       ├── dto/                               # 请求 DTO / 响应 DTO
│       ├── enums/                             # 枚举类
│       ├── exception/                         # 全局异常 + 错误码
│       └── util/                              # 工具类
├── micro-course-admin/                        # 前端：admin 管理后台
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                               # axios 接口封装
│       ├── views/                             # 页面组件
│       ├── components/                        # 可复用组件
│       ├── router/                            # 路由配置
│       ├── store/                             # Pinia 状态管理
│       └── utils/                             # 工具函数
├── docs/                                      # 项目文档（仅 .md / .pdf / .docx）
├── scripts/                                   # 部署/运维脚本
└── docker/                                    # Dockerfile + docker-compose.yml
```

---

## 2. 25 条负面清单（创建文件前必查）

### 2.1 文件位置

```
❌ 在项目根目录直接创建 .java / .vue / .ts
❌ Java 放在 docs/ scripts/ docker/ micro-course-admin/
❌ Vue 放在 micro-course-api/ docs/ scripts/ docker/
❌ Java 放在 micro-course-api/src/main/java/com/microcourse/ 之外
❌ Vue 页面放在 micro-course-admin/src/views/ 之外
❌ 全局组件放在 micro-course-admin/src/components/ 之外
❌ Entity 放在 micro-course-api/src/main/java/com/microcourse/entity/ 之外
❌ Controller 放在 micro-course-api/src/main/java/com/microcourse/controller/ 之外
```

### 2.2 重复定义

```
❌ 创建已存在的 Entity / Controller / Service / Mapper / DTO / Vue 组件
❌ 手动建数据库 migration 不更新 docs/数据字典.md
❌ 创建新文件前未跑 precheck.sh
```

### 2.3 分层职责

```
❌ Controller 写业务逻辑
❌ Controller 直接返回 Entity
❌ Service 直接暴露 Entity
❌ Repository 写业务逻辑
❌ 使用 Lombok 注解（@Data/@Getter/@Setter/@RequiredArgsConstructor 等，JDK 17.0.18 不兼容）
❌ 前端硬编码 baseURL（必须 import.meta.env.VITE_API_BASE_URL）
❌ 手动拼接 SQL（必须 MyBatis-Plus 条件构造器）
❌ 后端直接返回 Entity 给前端
```

### 2.4 代码风格

```
❌ Java 类名 camelCase（正确：UserController）
❌ Java 字段名 snake_case（正确：userId）
❌ DB 字段名 camelCase（正确：user_id）
❌ Vue 文件名 kebab-case（正确：UserList.vue）
❌ Vue 路由路径 PascalCase（正确：/user-list）
❌ 前后端字段命名不一致
❌ Service @Transactional 不处理 rollbackFor
❌ Controller @ResponseBody 不配置消息转换器
```

### 2.5 架构边界

```
❌ 后端绕过 Service 直接调 Repository
❌ 前端组件内直接调 axios（必须经 api/ 层）
❌ Vue 组件直接操作 Pinia 状态（必须 actions）
❌ 引入未在 pom.xml / package.json 声明的依赖
```

---

## 3. 命名规范

### 3.1 Java

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `UserController`、`CourseServiceImpl` |
| 方法名 | camelCase | `getUserById`、`saveCourse` |
| 字段名 | camelCase | `userId`、`courseName` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE` |
| 包名 | 全小写 dot 分隔 | `com.microcourse.controller` |
| 枚举值 | UPPER_SNAKE_CASE | `COURSE_STATUS_PUBLISHED` |

### 3.2 数据库

| 类型 | 规范 | 示例 |
|------|------|------|
| 表名 | 全小写 + snake_case | `users`、`course_chapter`（无 sys_ 前缀） |
| 字段名 | 全小写 + snake_case | `user_id`、`created_at` |
| 主键 | `id` | `id` |
| 外键 | `{referenced_table}_id` | `course_id` |
| 索引 | `idx_{table}_{column}` | `idx_users_phone` |
| 唯一索引 | `uk_{table}_{column}` | `uk_users_phone` |

### 3.3 前端

| 类型 | 规范 | 示例 |
|------|------|------|
| Vue 组件文件 | PascalCase.vue | `UserList.vue` |
| Vue 组件名 | PascalCase | `UserList` |
| 路由路径 | kebab-case | `/user-list` |
| API 方法 | 小写 + 动词 | `getUserList` |
| 目录名 | kebab-case | `user-management` |
| CSS 类名 | kebab-case | `user-list-container` |
| 常量/枚举 | UPPER_SNAKE_CASE | `API_BASE_URL` |

---

## 4. REST API 路径规范

```yaml
基础路径: /api            # 无 /v1 前缀
示例:
  GET    /api/users
  GET    /api/users/{id}
  POST   /api/users
  PUT    /api/users/{id}
  PUT    /api/users/{id}/status   # 软删/启用专用端点
  GET    /api/auth/me              # 当前位置：auth 域
```

**前端 API 封装对应**：
```js
getUserList(params)
getUserById(id)
postUserCreate(data)
putUserUpdate(id, data)
putUserStatus(id, data)            // 软删/启用
```

---

## 5. 预检命令（precheck.sh 固化）

```bash
# Entity 预检
grep -r "class XxxEntity" micro-course-api/src/main/java/com/microcourse/entity/ \
  || echo "Entity 不存在，可创建"

# Controller 预检
grep -r "class XxxController" micro-course-api/src/main/java/com/microcourse/controller/ \
  || echo "Controller 不存在，可创建"

# Service 预检
grep -r "class XxxService" micro-course-api/src/main/java/com/microcourse/service/ \
  || echo "Service 不存在，可创建"

# Mapper 预检
grep -r "interface XxxMapper" micro-course-api/src/main/java/com/microcourse/repository/ \
  || echo "Mapper 不存在，可创建"

# 前端组件预检
grep -r "XxxList.vue\|XxxForm.vue\|XxxDialog.vue" micro-course-admin/src/views/ \
  || echo "组件不存在，可创建"

# API 预检
grep -r "export.*getXxx\|export.*postXxx" micro-course-admin/src/api/ \
  || echo "API 不存在，可创建"

# 目录合规性
if echo "$file_path" | grep -qv "^micro-course-api/"; then
    echo "❌ 后端文件必须放在 micro-course-api/ 目录下"
fi
if echo "$file_path" | grep -qv "^micro-course-admin/"; then
    echo "❌ 前端文件必须放在 micro-course-admin/ 目录下"
fi
```

**AI 编码流程**：创建文件前**必须**先跑 `precheck.sh`，再决定是否创建。

---

## 6. AI 编码陷阱

```
❌ 在 micro-course-api/ 之外写 Java
❌ 跳过预检直接创建
❌ 命名风格混用（驼峰 vs snake_case）
❌ 字段命名跨层不一致
✅ 严格按目录树放文件
✅ 必跑 precheck.sh
✅ 严格按命名规范
✅ DB snake_case ↔ Java/TS camelCase 严格对应
```

---

*视图版本：v1.0 · 与源文档 v1.1 对齐*
*最后更新：2026-06-11*
