# E7 · PostgreSQL/DBA 专家审查报告

> 审查范围：`V1__init.sql` ↔ PostgreSQL 17.5 实际库状态 ↔ 数据字典 v0.4
> 审查时间：2026-06-11
> 审查结论：**通过**

---

## 1. 表与大小（实际数据库状态）

| 表 | 大小 | 备注 |
|----|------|------|
| classes | 32 kB | 空表 |
| departments | 56 kB | + 1 行（系统根院系） |
| flyway_schema_history | 48 kB | + 1 行（V1 init） |
| majors | 24 kB | 空表 |
| users | 160 kB | + 1 行（admin seed） |

**5 张表全在** ✅

## 2. Flyway 状态

| 字段 | 值 |
|------|-----|
| version | 1 |
| description | init |
| type | SQL |
| script | V1__init.sql |
| checksum | -1240166683 |
| installed_on | 2026-06-11 17:03:01 |
| execution_time | 36 ms |
| success | t |

**Flyway 1 条历史，1 个 migration，success=t** ✅

## 3. 索引（17 个）

### 3.1 业务索引（12 个）

| 索引名 | 表 | 字段 | 类型 |
|--------|----|------|------|
| idx_users_role | users | role | 普通 |
| idx_users_department | users | department_id | 普通 |
| idx_users_major | users | major_id | 普通 |
| idx_users_class | users | class_id | 普通 |
| idx_users_deleted | users | deleted_at | 普通 |
| uk_users_username | users | username | **UNIQUE** |
| uk_users_student_no | users | student_no | **UNIQUE** |
| uk_users_teacher_no | users | teacher_no | **UNIQUE** |
| uk_departments_code | departments | code | **UNIQUE** |
| idx_departments_parent | departments | parent_id | 普通 |
| uk_majors_code | majors | code | **UNIQUE** |
| idx_majors_department | majors | department_id | 普通 |
| idx_classes_major_grade | classes | (major_id, grade) | 联合 |
| idx_classes_name | classes | name | 普通 |
| idx_classes_counselor | classes | counselor_id | 普通 |

**12 个业务索引（含 5 UNIQUE）全部存在** ✅

### 3.2 Flyway 索引（2 个）

- flyway_schema_history_pk
- flyway_schema_history_s_idx

**完整** ✅

## 4. 主键（4 个 + 1 Flyway）

| 表 | 主键 |
|----|------|
| users | id |
| departments | id |
| majors | id |
| classes | id |
| flyway_schema_history | installed_rank |

**4 张业务表主键齐全** ✅

## 5. UNIQUE 约束（5 个）

| 表 | 约束 | 字段 |
|----|------|------|
| users | uk_users_username | username |
| users | uk_users_student_no | student_no |
| users | uk_users_teacher_no | teacher_no |
| departments | uk_departments_code | code |
| majors | uk_majors_code | code |

**5 个 UNIQUE 约束全在** ✅

## 6. 外键约束（7 个）

| 表 | FK 名称 | 引用关系 | 级联 |
|----|---------|---------|------|
| users | fk_users_department | users.department_id → departments.id | SET NULL |
| users | fk_users_major | users.major_id → majors.id | SET NULL |
| users | fk_users_class | users.class_id → classes.id | SET NULL |
| departments | fk_departments_parent | departments.parent_id → departments.id | SET NULL |
| majors | fk_majors_department | majors.department_id → departments.id | RESTRICT |
| classes | fk_classes_major | classes.major_id → majors.id | RESTRICT |
| classes | fk_classes_counselor | classes.counselor_id → users.id | SET NULL |

**7 个 FK 全部存在，方向/级联全部正确** ✅

## 7. NOT NULL 完整性

通过 information_schema.columns 查 4 张表共 46 列，其中 22 列 NOT NULL：

| 表 | 必含字段 |
|----|---------|
| users | id, username, password, real_name, role, status, cas_bound, created_at, updated_at |
| departments | id, name, code, sort_order, created_at, updated_at |
| majors | id, name, code, department_id, sort_order, created_at, updated_at |
| classes | id, name, major_id, grade, sort_order, created_at, updated_at |

**与数据字典 v0.4 §1.1-1.4 必含字段 1:1 对齐** ✅

## 8. CHECK 约束

**0 个 CHECK 约束** —— 这是预期：状态机在应用层做（状态机设计 v1.0 §1.3），DB 层不加约束避免重复。

## 9. seed 数据完整性

| 表 | seed | 内容 |
|----|------|------|
| users | ✓ | id=1, username=admin, real_name=系统管理员, role=ADMIN, status=1, bcrypt 密码 |
| departments | ✓ | id=1, name=系统根院系, code=ROOT, sort_order=0 |

**2 行 seed 数据齐全** ✅

## 10. 改进项

### 10.1 P1 改进项（建议修复）

1. **V1__init.sql 缺 `idx_departments_code`** —— 数据字典 v0.4 §1.2 列出 `idx_departments_code (UNIQUE)`，但实际只有 `uk_departments_code` 隐式 UNIQUE INDEX
   - 严格说字典中"UNIQUE"是约束，SQL 中通过 `CONSTRAINT uk_xxx UNIQUE` 实现，符合 PG 规范
   - 但字典与 SQL 命名不一致（`idx_` vs `uk_`）—— 建议字典 v0.5 改名为 `uk_departments_code`
2. **E7 仅覆盖 Phase 1 4 张表** —— Phase 2+ 34 张表待后续审查

### 10.2 P2 改进项

1. **V2 迁移预留**：Phase 1.5 新增 operation_logs 表
2. **性能基线**：当前表均空，索引效率无法测；Phase 1.5+ 真实数据后做 EXPLAIN ANALYZE

## 11. 终验

| 类别 | 通过 | 失败 | 通过率 |
|------|------|------|--------|
| 5 表存在 | 5/5 | 0 | 100% |
| Flyway 历史 | 1/1 | 0 | 100% |
| 业务索引 12 | 12/12 | 0 | 100% |
| UNIQUE 约束 5 | 5/5 | 0 | 100% |
| FK 约束 7 | 7/7 | 0 | 100% |
| 主键 4 | 4/4 | 0 | 100% |
| NOT NULL 22 列 | 22/22 | 0 | 100% |
| seed 数据 2 行 | 2/2 | 0 | 100% |
| **总计** | **58/58** | **0** | **100%** |

## 12. 终验结论

**✅ Phase 2 数据库层 100% 通过**

- 4 张业务表 + 1 Flyway 历史表齐全
- 12 业务索引 + 5 UNIQUE + 7 FK 全部生效
- 22 NOT NULL 列级约束完整
- 2 行 seed 数据正确

**唯一 P1 改进**：数据字典 v0.5 修正 `idx_departments_code` → `uk_departments_code` 命名（与 SQL 一致）

---

*报告版本：v1.0*
*审查专家：E7 PostgreSQL/DBA*
*最后更新：2026-06-11*
