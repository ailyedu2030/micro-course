# 选课管理域 · Spec 漂移全量修复 (Enrollment Domain Drift Fix)

> **OpenSpec Change**: `enrollment-domain-drift-fix`
> **Schema**: spec-driven
> **创建日期**: 2026-07-07
> **方法论**: 代码为真相, spec 为设计意图。git blame 验证每项差异。

---

## Why

**问题陈述**: 选课管理域 (Enrollment/Cart/Order) 通过 3 个扫描任务发现:
- 数据字典: cart_items 表完全缺失; orders.version 和 payments.status NOT NULL 漏标
- API 契约: Cart(5) + Order(8) = 13 端点 0% 覆盖 (因 Phase 1 范围未含支付)
- 状态机: REENROLLING 流程断裂 (已知 P0), chk_enrollments_status 缺 SUSPENDED/REENROLLING (已知 P0)

**已知 P0 遗留** (红队审计发现):
- REENROLLING 流程断裂: CHECK 约束缺该值 → SQL 异常
- chk_enrollments_status 缺 SUSPENDED 和 REENROLLING
- COMPLETED 双路径不同步

---

## What Changes

### 新增能力

- **CAP-1 enrollment-api-contract**: 补充 Cart(5) + Order(8) + Enrollment(10) = 23 端点到 API 契约
- **CAP-2 enrollment-data-contract**: 补充 cart_items 表定义到数据字典; 修正 orders/payments 约束

### 修改能力

- **CAP-3 (修改) enrollment-business-logic**: 修复 2 个已知 P0 (REENROLLING + CHECK 约束)
- **CAP-4 (修改) enrollment-permission**: 同步 @PreAuthorize → 权限矩阵 (git blame 验证)

---

## 方法论

```
Step 1: 发现差异 → spec 说 X, 代码做 Y
Step 2: git blame 查 Y 的提交者和意图
Step 3: 
  - 若 Y 是有意修复/功能 → 更新 spec 匹配代码 (80% 场景)
  - 若 Y 是疏忽/bug → 修代码匹配 spec (20% 场景)
Step 4: 记录 git blame 证据到 commits
```

---

## 执行策略

### 阶段 1: P0 必修 (2 项)
- [ ] 1.1 REENROLLING CHECK 约束修复
- [ ] 1.2 COMPLETED 双路径同步

### 阶段 2: 文档同步 (代码为真相)
- [ ] 2.1 数据字典 v1.2→v1.3 (cart_items + orders/payments NOT NULL)
- [ ] 2.2 创建 docs/API契约-选课管理.md (23 端点, git blame 确认意图)

### 阶段 3: 测试 (50+ TC)
- [ ] 3.1 写 enrollment-test-units.md
- [ ] 3.2 执行 TC

---

## 验收标准

- ✅ 2 个 P0 修复 + 全量回归
- ✅ 数据字典 v1.3 同步
- ✅ API契约-选课管理.md 创建 (23 端点, 每端 git blame 标记)
- ✅ 权限矩阵 v4.2 同步
- ✅ 50+ TC 全部 PASS

---

**总任务数**: 10
**方法论**: git blame 优先, 代码是真相, spec 是设计意图