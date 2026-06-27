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
| sortOrder | sort_order | Integer | number | - | default 0 |
| createdAt | created_at | LocalDateTime | string | - | NOT NULL |
| updatedAt | updated_at | LocalDateTime | string | - | - |

**索引**：idx_classes_major_grade (major_id + grade), idx_classes_name

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

- `deleted_at IS NULL` = 未删除
- 启用/禁用：status=0（设 deleted_at）↔ status=1（清 deleted_at）
- V16 migration 已为以下 12 张表添加 deleted_at：courses, videos, course_chapters, exercises, exercise_records, enrollments, learning_progress, course_favorites, discussion_posts, discussion_comments, check_ins, course_reviews

### 2.4 FK 方向

```
departments (1) → majors (N) → classes (N) → users (N)
```

> 注：原 `classes.counselorId → users.id` FK 已于 V89 迁移移除（`V89__drop_counselor_id.sql`），对应索引 `idx_classes_counselor` 一并删除。

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
39. admin_settings        ← Phase 9
40. badges
41. certificates
42. grades
43. operation_logs
44. course_reviews
45. badge_definitions     ← Phase 5-9
46. achievements
47. question_tag_relations
48. user_follows
49. attachments
50. score_histories
51. course_notes
52. video_bookmarks
53. teaching_classes
54. teaching_class_students
55. class_schedules
56. course_prerequisites
57. grade_components
58. enrollment_histories
59. course_review_logs
```

**强制**：Phase 1 编码**只能**涉及前 4 张表；其余表 Phase 2+ 才允许创建。

---

*视图版本：v1.1 · 与源文档 v0.5 对齐*
*最后更新：2026-06-12*

---

## 4. 微专业表（Phase 14）

> **源文档**：[`docs/开发规划/phase14-micro-specialty-spec.md` §6 数据模型](../../../docs/开发规划/phase14-micro-specialty-spec.md)
> **详细字段**：参见 phase14-spec §6.1-§6.7

| 表名 | 说明 |
|------|------|
| `micro_specialties` | 微专业主表（5 张新表 + 3 张扩展），含 8 状态机、版本乐观锁 |
| `micro_specialty_courses` | 课程编排（FK→courses，必修/选修标记，学分/学时冗余） |
| `micro_specialty_teachers` | 教师团队（LEAD/MEMBER/ASSISTANT 角色，5 状态邀请机制，部分唯一索引） |
| `micro_specialty_enrollments` | 修读记录（6 状态机，部分唯一索引支持 reapply） |
| `micro_specialty_proposals` | 微专业申报表（教师路径B入口，4 状态） |
| `micro_specialty_featured_audit` | 置顶审计表（APPLY/APPROVE/REJECT/GOLD_SET/GOLD_UNSET 操作，JSONB 快照） |

**关键约束**：
- `uk_mst_active`：教师激活态部分唯一索引 `WHERE invite_status NOT IN ('DECLINED','REMOVED')`
- `uk_mse_active`：修读激活态部分唯一索引 `WHERE status NOT IN ('REJECTED','DROPPED','FAILED')`
- `uk_ms_code`：微专业代码全局唯一
- DB 触发器 `trg_ms_one_lead`：确保每微专业恰好 1 条 ACTIVE LEAD
- `certificates` 表扩展 `cert_type` + `micro_specialty_id`，异或约束 `chk_cert_xor`
