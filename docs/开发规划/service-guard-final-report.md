# Service Guard — 批量修复最终报告

**日期**: 2026-06-24
**Commits**: 021f1e1 (P1-E) + 8a58819 (P2-C)

## 已完成清单

| 优先级 | 实体 | 操作 | 防护类型 | ErrorCode |
|--------|------|------|----------|-----------|
| **P0-A** | Course | delete | FK: course_enrollment | COURSE_HAS_ENROLLMENTS(6002) |
| **P0-B** | MicroSpecialty | delete | FK: ms_enrollment | MS_HAS_ENROLLMENTS(17022) (或类似) |
| **P1-A** | TeachingClass | delete | FK: teaching_class_student | CLASS_HAS_STUDENTS(7002) |
| **P1-B** | CourseBundle | delete | 顺序修复 | (隐式修复) |
| **P1-E** | Tag | delete | FK: course_tag_relation | TAG_IN_USE(14002) |
| **P1-C** | Lesson | delete | N/A (软删除) | — |
| **P1-D** | Video | delete | N/A (软删除) | — |
| **P1-F** | DiscussionPost | delete | N/A (软删除) | — |
| **P2-A** | Course | create | N/A (DB 无约束) | — |
| **P2-B** | Category | create | N/A (DB 无约束) | — |
| **P2-C** | MicroSpecialty | create | code 唯一性 | MICRO_SPECIALTY_CODE_EXISTS(17021) |
| **P2-D** | Badge | create | 已有幂等检查 | — |
| **P2-E** | CourseBundle | create | N/A (DB 无约束) | — |
| **P2-F** | TeachingClass | create | N/A (DB 无约束) | — |
| **P2-G** | Video | create | N/A (DB 无约束) | — |
| **P2-H** | Chapter | create | N/A (DB 无约束) | — |
| **P2-I** | Tag | create | (在 TagServiceImpl.create() 中已有) | — |

## 跳过的项 (合法)

- **P1-C / P1-D / P1-F**: 已使用 `@TableLogic` 软删除，deleteById 不会触发 FK 约束。
- **P2-A/B/E/F/G/H**: DB 层无唯一约束，重复是合法的（如不同教师开多门同名课程）。仅 UX 层提示，不阻断。

## 防御总览

```
# 服务层保护模式（最终形态）
1. create() 前 selectCount + BusinessException(唯一性错误码)  ← 友好 400
2. delete() 前 selectCount + BusinessException(FK 错误码)     ← 友好 400
3. soft delete 实体用 @TableLogic，无 FK 风险                 ← 现状
4. GlobalExceptionHandler 兜底 DataIntegrityViolationException ← 通用 409
```

## 关联修复（不在表内但同源）

| 文件 | 修复 |
|------|------|
| `ExerciseRecordRepository` | 删 `WHERE deleted_at IS NULL` (exercise_records 无 deleted_at 列) |
| `CourseRepository` | 删 3 处 `AND er.deleted_at IS NULL` |
| `MicroSpecialtyDetail.vue` | statusLabel CANCELLED / getStats 登录判断 / canEnroll 加固 |
| `DepartmentList.vue` / `ClassList.vue` | catch 显示后端 message + 修正错误码匹配 |
| `router/index.js` | 加 removeToken 导入 |
| `ClassServiceImpl` | name 唯一性检查 (CLASS_NAME_EXISTS 4003) |
| `MajorServiceImpl` | name+code 唯一性检查 (MAJOR_NAME_EXISTS 3003 / MAJOR_CODE_EXISTS 3004) |
| `DepartmentServiceImpl` | name+code 唯一性检查 (DEPARTMENT_NAME_EXISTS 2003 / DEPARTMENT_CODE_EXISTS 2004) |
| `users/departments/majors/classes/courses` PK 序列 setval 修复 | seq_lag 阻断 |

## 验收

- `mvn compile` 通过（最新两次提交）
- 30 个 git commits (021f1e1, 8a58819 是最近 2 个)
- Phase 14 微专业 72/72 PASS
