# 数据契约引用视图

> **源文档**：[`docs/数据字典.md`](../../数据字典.md) v0.5
> **视图性质**：引用视图（不复制全文，仅抓取 AI 编码时最常查的关键字段）
> **同步规则**：真文档更新后，本视图必须 24 小时内同步
> **冲突裁决**：以冲突评审决议为准

---

## 1. 4 张核心表（Phase 1 范围）

### 1.1 users（用户表）

| 字段 | DB 列 | Java 类型 | TS 类型 | 长度 | 约束 | 说明 |
|------|-------|-----------|---------|------|------|------|
| id | id | Long | number | - | PK, AUTO | 主键 |
| username | username | String | string | 50 | UNIQUE, NOT NULL | 登录用户名 |
| password | password | String | string | 255 | NOT NULL | bcrypt 加密 |
| realName | real_name | String | string | 50 | NOT NULL | 真实姓名（API 脱敏） |
| email | email | String | string | 100 | - | 邮箱（API 脱敏） |
| phone | phone | String | string | 30 | - | 手机号（API 脱敏） |
| gender | gender | String | string | 10 | - | MALE / FEMALE |
| avatar | avatar | String | string | 500 | - | 头像 URL |
| role | role | String | string | 20 | NOT NULL | STUDENT/TEACHER/ADMIN/ACADEMIC |
| departmentId | department_id | Long | number | - | FK→departments | 所属院系 |
| majorId | major_id | Long | number | - | FK→majors | 所属专业 |
| classId | class_id | Long | number | - | FK→classes | 所属班级 |
| grade | grade | String | string | 10 | - | 年级 |
| enrollmentYear | enrollment_year | String | string | 10 | - | 入学年份 |
| graduationYear | graduation_year | String | string | 10 | - | 毕业年份 |
| politicalStatus | political_status | String | string | 20 | - | 政治面貌 |
| studentNo | student_no | String | string | 30 | UNIQUE | 学号（学生） |
| teacherNo | teacher_no | String | string | 30 | UNIQUE | 工号（教师） |
| status | status | Integer | number | 1 | NOT NULL, default 1 | 0=INACTIVE, 1=ACTIVE, 2=DISABLED, 3=DELETED |
| casBound | cas_bound | Boolean | boolean | - | default false | 是否绑定 CAS |
| lastLoginAt | last_login_at | LocalDateTime | string | - | - | 最后登录时间 |
| deletedAt | deleted_at | LocalDateTime | string | - | - | 软删除时间戳 |
| createdAt | created_at | LocalDateTime | string | - | NOT NULL | 创建时间 |
| updatedAt | updated_at | LocalDateTime | string | - | NOT NULL | 更新时间 |

**索引**：idx_users_username (UNIQUE), idx_users_student_no (UNIQUE), idx_users_teacher_no (UNIQUE), idx_users_role, idx_users_department, idx_users_major, idx_users_class, idx_users_deleted

### 1.2 departments（院系表）

| 字段 | DB 列 | Java 类型 | TS 类型 | 长度 | 约束 |
|------|-------|-----------|---------|------|------|
| id | id | Long | number | - | PK, AUTO |
| name | name | String | string | 100 | NOT NULL |
| code | code | String | string | 30 | UNIQUE, NOT NULL |
| parentId | parent_id | Long | number | - | FK→departments（自引用） |
| sortOrder | sort_order | Integer | number | - | default 0 |
| createdAt | created_at | LocalDateTime | string | - | NOT NULL |
| updatedAt | updated_at | LocalDateTime | string | - | - |

**索引**：uk_departments_code (UNIQUE), idx_departments_parent

### 1.3 majors（专业表）

| 字段 | DB 列 | Java 类型 | TS 类型 | 长度 | 约束 |
|------|-------|-----------|---------|------|------|
| id | id | Long | number | - | PK, AUTO |
| name | name | String | string | 100 | NOT NULL |
| code | code | String | string | 30 | UNIQUE, NOT NULL |
| departmentId | department_id | Long | number | - | FK→departments, NOT NULL |
| sortOrder | sort_order | Integer | number | - | default 0 |
| createdAt | created_at | LocalDateTime | string | - | NOT NULL |
| updatedAt | updated_at | LocalDateTime | string | - | - |

**索引**：idx_majors_code (UNIQUE), idx_majors_department

### 1.4 classes（班级表）

| 字段 | DB 列 | Java 类型 | TS 类型 | 长度 | 约束 |
|------|-------|-----------|---------|------|------|
| id | id | Long | number | - | PK, AUTO |
| name | name | String | string | 50 | NOT NULL |
| majorId | major_id | Long | number | - | FK→majors, NOT NULL |
| grade | grade | String | string | 10 | NOT NULL |
| counselorId | counselor_id | Long | number | - | FK→users |
| sortOrder | sort_order | Integer | number | - | default 0 |
| createdAt | created_at | LocalDateTime | string | - | NOT NULL |
| updatedAt | updated_at | LocalDateTime | string | - | - |

**索引**：idx_classes_major_grade (major_id + grade), idx_classes_name, idx_classes_counselor

---

## 2. AI 编码速查（高频陷阱）

### 2.1 字段命名（DB ↔ Java ↔ TS）

```
DB snake_case  ↔  Java/TS camelCase
created_at     ↔  createdAt
department_id  ↔  departmentId
```

**自动转换**：MyBatis-Plus 配 `map-underscore-to-camel-case: true`；前端 axios 直接 JSON 解析。

### 2.2 主键策略

- PostgreSQL：用 `BIGSERIAL`，MyBatis-Plus `@TableId(type = IdType.AUTO)`
- 不允许用雪花 ID（与现状不一致）

### 2.3 软删除

- `users.deleted_at IS NULL` = 未删除
- 启用/禁用：status=0（设 deleted_at）↔ status=1（清 deleted_at）
- **仅 users 表有 deletedAt 字段**（其他 3 表无）

### 2.4 FK 方向

```
departments (1) → majors (N) → classes (N) → users (N)
                                    ↓
                              counselorId → users.id
```

### 2.5 状态机状态值（users.status）

| 值 | 名称 | 业务 |
|----|------|------|
| 0 | INACTIVE | 未激活 |
| 1 | ACTIVE | 正常 |
| 2 | DISABLED | 禁用 |
| 3 | DELETED | 已删除（180 天保留） |

---

## 3. 38 张表概览（Phase 1 仅前 4 张，Phase 2+ 详见源文档）

```
1. users                  ← Phase 1
2. departments            ← Phase 1
3. majors                 ← Phase 1
4. classes                ← Phase 1
5. course_categories      ← Phase 2
6. courses
7. tags
8. course_tag_relations
9. course_chapters
10. videos
... (其余 28 张)
```

**强制**：Phase 1 编码**只能**涉及前 4 张表；其余表 Phase 2+ 才允许创建。

---

*视图版本：v1.0 · 与源文档 v0.4 对齐*
*最后更新：2026-06-11*
