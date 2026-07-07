# Tasks: 选课管理域 Spec 漂移全量修复

> **OpenSpec Change**: `enrollment-domain-drift-fix`
> **Schema**: spec-driven
> **总任务数**: 10
> **方法论**: git blame 优先, 代码是真相

---

## 阶段 1: P0 必修 (2 项)
- [x] **1.1 修复 REENROLLING CHECK 约束断裂**
  - 位置: `V153__add_check_constraints.sql`, 加 `REENROLLING` 到 `chk_enrollments_status` CHECK
  - 加 `SUSPENDED` 到 `chk_enrollments_status` CHECK
  - 验证: 插入 REENROLLING/SUSPENDED 状态的 enrollment SQL 正常

- [x] **1.2 修复 COMPLETED 双路径同步**
  - 位置: `EnrollmentServiceImpl.java` 完成逻辑
  - 确保 `completed=true` 时同步 `enrollmentStatus=COMPLETED`
  - 反之亦然

## 阶段 2: 文档同步 (3 项)
- [x] **2.1 数据字典 v1.2→v1.3 (cart_items 表 + orders/payments NOT NULL)**
- [x] **2.2 创建 docs/API契约-选课管理.md**
  - EnrollmentController: 10 端点 (git blame 确认)
  - CartController: 5 端点 (git blame: 040b206e, 2026-06-27)
  - OrderController: 8 端点 (git blame: d017ef22, 2026-06-21)
- [x] **2.3 权限矩阵 v4.2 (同步选课域 @PreAuthorize)**

## 阶段 3: 测试 (5 项)
- [x] **3.1 写 enrollment-test-units.md (50+ TC)**
- [x] **3.2 执行 TC-001 ~ TC-020 (选课 + 退课)**
- [x] **3.3 执行 TC-021 ~ TC-035 (购物车 + 订单)**
- [x] **3.4 执行 TC-036 ~ TC-050 (状态机 + P0 验证)**
- [x] **3.5 全部 PASS 后 commit + archive**

---

**总任务数**: 10
**方法**: git blame 优先, 代码是真相, spec 是设计意图