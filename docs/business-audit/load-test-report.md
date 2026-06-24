# 选课并发压测报告 — P0-1 修复验证

**测试时间**: 2026-06-24
**测试方法**: Node.js + axios，15 学生并发 enroll
**测试工具**: `scripts/load-test-enrollment.js`
**关键发现**: 压测发现 P0-1 修复**第一版无效** — 行级锁缺失

---

## 关键发现

**首次压测暴露严重 P0 bug**:
```
场景: 15 个学生并发 enroll 到 max_students=5 的课程
预期: 5 ENROLLED + 10 WAITLIST
实际: 11 ENROLLED + 4 WAITLIST  ← 严重超卖 (220% 超出)
```

### 根因

原修复用 "INSERT INTO enrollments SELECT ... FROM courses c WHERE c.student_count < c.max_students" — 但**这不原子**。多个并发事务都看到 `student_count=5` 旧值，都通过检查。

### 修复方案

添加 `SELECT ... FOR UPDATE` 行级锁（PostgreSQL 事务内行锁）:

```java
// 在 enroll() 事务内最前面：
Map<String, Object> lockedCourse = courseRepository.selectByIdForUpdate(courseId);
// 该行在事务提交前其他事务无法读取
```

`CourseRepository.selectByIdForUpdate`:
```java
@Select("SELECT id, status, max_students, COALESCE(student_count, 0) AS student_count, deleted_at " +
        "FROM courses WHERE id = #{courseId} FOR UPDATE")
Map<String, Object> selectByIdForUpdate(@Param("courseId") Long courseId);
```

### 修复后压测结果

| 场景 | 并发数 | max | 预期 | 实际 | 状态 |
|------|--------|-----|------|------|------|
| 修复前 | 15 | 5 | 5 ENROLLED + 10 WAITLIST | **11 ENROLLED** (超卖) | ❌ |
| 修复后 | 15 | 5 | 5 ENROLLED + 10 WAITLIST | **5 ENROLLED + 10 WAITLIST** | ✅ |
| 修复后 | 15 | 3 | 3 ENROLLED + 12 WAITLIST | **3 ENROLLED + 12 WAITLIST** | ✅ |

性能: 89-113 QPS（合理，行级锁牺牲了部分并发度换取正确性）

---

## 验证清单

- ✅ 15 学生 max=5: 5/10 ENROLLED/WAITLIST
- ✅ 15 学生 max=3: 3/12 ENROLLED/WAITLIST
- ✅ E2E 32/32 PASS（含 ENROLL-4 超卖防护 + ENROLL-5 候补）
- ✅ 编译通过
- ✅ precheck 14/14 PASS

## 决策

**P0-1 修复第一版无效，row lock 修复后有效。** 没有这次压测，发现这个问题要等到生产 — 那时候是**学生投诉爆表 + 数据库脏数据**。

## 教训

**任何 P0 修复都必须压测验证。** Playwright E2E 跑 happy path + 一些边界，但**不验证极端并发**。压测（k6 / 自写脚本）是查并发的唯一方法。

## 下一步

- [ ] 推生产前再跑一次 50+ 并发（当前 15 是用现有学生上限）
- [ ] 监控 P99/P95 响应时间（行级锁会慢一点，需监控）
- [ ] 实施降级：若 lock timeout 超过 5s，自动入候补（避免雪崩）
