# 选课管理域 · Spec 漂移全量修复 · 设计文档

> **设计目标**: 代码为真相, git blame 验证每项差异。以最小的侵入性消除漂移。

## 1. P0 修复

### 1.1 REENROLLING CHECK 约束
```sql
-- V156 INSERT
ALTER TABLE enrollments DROP CONSTRAINT IF EXISTS chk_enrollments_status CASCADE;
ALTER TABLE enrollments ADD CONSTRAINT chk_enrollments_status 
  CHECK (enrollment_status IN ('PENDING','APPROVED','WAITLIST','CANCELLED','COMPLETED','DROPPED','REJECTED','SUSPENDED','REENROLLING'));
```

### 1.2 COMPLETED 同步
```java
// 完成时同步两个字段
enrollment.setCompleted(true);
enrollment.setEnrollmentStatus(EnrollmentStatus.COMPLETED.getValue());
```

## 2. 文档同步 (代码为真相)

### 2.1 数据字典 v1.3
cart_items 表: 补充完整定义 (8 字段 + 唯一索引)
orders.version: NOT NULL 补充
payments.status: NOT NULL 补充

### 2.2 API契约-选课管理.md
23 端点, 每端 git blame 标记创建日期和提交者