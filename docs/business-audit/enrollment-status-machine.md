# 选课状态机 — 业务逻辑偏差审计报告

**审计目标**: 状态机设计.md §3 vs EnrollmentServiceImpl.java
**审计日期**: 2026-06-24
**风险等级**: 🔴 P0 — 选课是核心交易，可能导致数据超卖

---

## 总体评估

| 维度 | spec 要求 | 实际代码 | 评估 |
|------|----------|---------|------|
| 状态枚举 | 7 状态 | 7 状态 + ENROLLED 兼容 | ✅ MATCH |
| `canTransitionTo` 白名单 | 必需 | ✅ 已实现 | ✅ MATCH |
| 服务层强校验 | 必需 | ✅ L459-467 / L495 / L758-760 | ✅ MATCH |
| 乐观锁 | 必需 | ✅ V61 migration | ✅ MATCH |
| 单元测试 | 必需 | ✅ `EnrollmentStatusTest` + `MachineTest` | ✅ MATCH |
| **并发超卖防护** | **最后一个名额 CAS** | **❌ 缺失** | ❌ **CRITICAL** |
| WAITLIST 候补 | 必需 | 🟡 部分 | 待审计 |
| COMPLETED 触发 | 视频 100% + 练习通过 | 待审计 | 待审计 |

---

## DEVIATION-1: 🔴 选课人数超卖（最严重）

| 属性 | 说明 |
|------|------|
| **位置** | `EnrollmentServiceImpl.enroll()` L107-160 |
| **spec 要求** | §3.5 "课程满员时最后一个名额使用乐观锁，超限时回退" |
| **代码实际** | 先 check 学生人数（read），再 insert enrollment（write），最后 atomicIncrementStudentCount — 三步**不原子** |
| **业务影响** | 🔴 **P0 严重 — 并发下 maxStudents 可被超出**（100 人并发选 90/100 课程，可能 95+ 人入选） |
| **复现** | 启动 100 并发线程对 max=10 课程 enroll() — 实际选课数会超过 10 |

**问题代码片段：**
```java
// Step 1: 读课程，判断是否满员（非原子）
if (course.getMaxStudents() > 0 && course.getStudentCount() >= course.getMaxStudents()) {
    throw 满员;
}
// Step 2: 插入 enrollment（其他线程也在这个空隙插入）
enrollmentRepository.insert(enrollment);
// Step 3: 增计数（无条件，不检查上限）
courseRepository.atomicIncrementStudentCount(request.getCourseId());
```

**修复方案**：
1. 删除 check-then-insert 的非原子逻辑
2. 用原子 SQL 一次性完成"插入 + 增计数 + 校验上限"：

```sql
-- 伪 SQL
INSERT INTO enrollments (..., enrollment_status, ...)
SELECT ?, ?, 'ENROLLED', ...
FROM courses
WHERE id = ? AND status = 4  -- PUBLISHED
  AND (max_students = 0 OR COALESCE(student_count, 0) < max_students);

UPDATE courses
SET student_count = student_count + 1
WHERE id = ?
  AND (max_students = 0 OR COALESCE(student_count, 0) < max_students);
```

3. 若 `affectedRows = 0` → 抛 "课程满员" 错误

---

## DEVIATION-2: 🟡 WAITLIST 未自动触发

| 属性 | 说明 |
|------|------|
| **位置** | spec §3.2 "PENDING → WAITLIST：课程已达最大人数上限" |
| **代码实际** | `enroll()` 在满员时直接抛 `BAD_REQUEST_PARAM("该课程选课人数已满")`，**不进入候补** |
| **业务影响** | 🟡 中 — 候补机制形同虚设，学生无法看到候补排位 |
| **修复方案** | 满员时改抛 `ENTER_WAITLIST` 错误码并创建 WAITLIST 记录 |

---

## DEVIATION-3: 🟢 PENDING→CANCELLED 不在 spec

| 属性 | 说明 |
|------|------|
| **位置** | `EnrollmentStatus.canTransitionTo()` PENDING 分支 |
| **spec 要求** | PENDING → {APPROVED, REJECTED, WAITLIST} |
| **代码实际** | PENDING → {APPROVED, REJECTED, WAITLIST, **CANCELLED**} |
| **业务影响** | 🟢 实际更友好（学生可主动取消未审核的选课），建议追加到 spec |
| **修复方案** | spec §3.2 补 PENDING → CANCELLED 转换 |

---

## 修复优先级

| 偏差 | 风险 | 修复成本 | 状态 |
|------|------|---------|------|
| DEVIATION-1: 超卖 | 🔴 严重 | 1 个原子 SQL 改动 | 待修 |
| DEVIATION-2: WAITLIST | 🟡 中 | 30 行代码 | 待修 |
| DEVIATION-3: PENDING→CANCELLED | 🟢 低 | spec 更新 | spec-only |

---

## 输出

修复后追加 Playwright 测试：
```
test('ENROLL-1: 课程满员时第 N+1 个并发选课应被拒绝')
test('ENROLL-2: 满员后选课应自动进入 WAITLIST')
```
