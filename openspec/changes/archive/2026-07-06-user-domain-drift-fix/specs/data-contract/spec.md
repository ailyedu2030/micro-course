# 数据字典 v1.1 → v1.2 (data-contract)

## Purpose

修复用户管理域 24 项数据字典漂移, 同步 Flyway migration SQL 的真实 schema。

## MODIFIED Requirements

### Requirement: users 表 10 项 schema 同步

`docs/数据字典.md` 中 users 表 MUST 与 Flyway migration V100/V153 实际创建的 schema 保持一致。

#### Scenario: 3 个 CHECK 约束补充登记
- WHEN 本变更完成
- THEN 数据字典 MUST 补充登记以下 CHECK 约束 (V153 创建):
  - chk_users_status (status IN 0..3)
  - chk_users_role (role IN 'STUDENT','TEACHER','ADMIN','ACADEMIC')
  - chk_users_gender (gender IS NULL OR gender IN 'MALE','FEMALE','SECRET')

#### Scenario: uk_users_email 索引条件修正
- WHEN 本变更完成
- THEN 数据字典 MUST 修正 uk_users_email WHERE 条件, 包含 `AND email <> ''` (V100 修复空字符串冲突)

#### Scenario: 4 个索引补充登记
- WHEN 本变更完成
- THEN 数据字典 MUST 补充登记以下索引 (V1/V81):
  - uk_users_teacher_no UNIQUE
  - idx_users_username (非唯一, V81)
  - idx_users_student_no (非唯一, V81)
  - idx_users_email (V81)

#### Scenario: 字段类型修正
- WHEN 本变更完成
- THEN 数据字典 MUST 登记 email 字段 updateStrategy=FieldStrategy.IGNORED
- AND MUST 修正 role 字段类型为 UserRole enum (而非 String)

### Requirement: departments / majors / classes 表 6 项 schema 同步

本节定义用户管理域的关键规范要求。系统 MUST 实现上述场景, 不得偏离。所有变更 MUST 同步更新对应文档与测试。

#### Scenario: sortOrder NOT NULL DEFAULT 0 登记
- WHEN 本变更完成
- THEN 数据字典 MUST 修正 departments/majors/classes 的 sortOrder 为 NOT NULL DEFAULT 0 (而非可空 default 0)

#### Scenario: FK ON DELETE 策略登记
- WHEN 本变更完成
- THEN 数据字典 MUST 登记 majors.departmentId FK ON DELETE RESTRICT
- AND MUST 登记 classes.majorId FK ON DELETE RESTRICT

#### Scenario: counselorId 字段移除
- WHEN 本变更完成
- THEN 数据字典 MUST 移除 classes.counselorId → counselor_id 字段映射 (V89 已删除)
- AND MUST 移除 idx_classes_counselor 索引条目

### Requirement: teacher_ratings / teacher_tier_log 表 8 项 schema 同步

本节定义用户管理域的关键规范要求。系统 MUST 实现上述场景, 不得偏离。所有变更 MUST 同步更新对应文档与测试。

#### Scenario: teacher_ratings 字段 DEFAULT 登记
- WHEN 本变更完成
- THEN 数据字典 MUST 登记: ratingScore DEFAULT 0, tier DEFAULT 'NEW', totalStudents DEFAULT 0, totalCourses DEFAULT 0

#### Scenario: idx_teacher_ratings_tier 索引登记
- WHEN 本变更完成
- THEN 数据字典 MUST 补充登记 idx_teacher_ratings_tier (V112)

#### Scenario: V130→V159 编号修正
- WHEN 本变更完成
- THEN 数据字典 MUST 修正 manualAdjustment 字段的迁移版本号 V130→V159

#### Scenario: teacher_tier_log.triggeredBy DEFAULT 登记
- WHEN 本变更完成
- THEN 数据字典 MUST 登记 triggeredBy DEFAULT 'CRON'