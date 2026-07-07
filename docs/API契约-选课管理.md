# API 契约 · 选课管理域

> **契约范围**: 选课 (Enrollment) + 购物车 (Cart) + 订单 (Order)
> **方法论**: 代码为真相, spec 为设计意图。以下端点 git blame 已确认创建意图。
> **关联文档**: API 契约 Phase 1 (docs/API契约-Phase1.md), 权限矩阵 v4.2

---

## 修订记录
| 版本 | 日期 | 说明 | 作者 |
|------|------|------|------|
| v1.0 | 2026-07-07 | 初稿, 覆盖 23 端点。代码创建基准: EnrollmentController(f00a85b1), CartController(040b206e), OrderController(d017ef22) | 总工程师 |

---

## 1. 选课管理 (10 端点) — `/api/enrollments`

### 1.1 POST /api/enrollments — 选课
- **权限**: `hasRole('STUDENT')`
- **请求**: `EnrollmentCreateRequest { courseId*, sourceChannel(String) }`
- **响应**: `R<EnrollmentVO>`
- **守卫**: 幂等检查 / 付费检查 / 用户ACTIVE / 先修课 / 容量(满员→WAITLIST) / FOR UPDATE 行锁

### 1.2 GET /api/enrollments/my — 我的选课
- **权限**: `isAuthenticated()` (本人数据)

### 1.3 GET /api/enrollments — 选课列表 (教师/管理端)
- **权限**: `hasAnyRole('TEACHER','ADMIN','ACADEMIC')`

### 1.4 GET /api/enrollments/course/{courseId} — 课程选课列表
- **权限**: `hasAnyRole('TEACHER','ADMIN','ACADEMIC')`

### 1.5 GET /api/enrollments/course/{courseId}/ranking — 课程排名
- **权限**: `isAuthenticated()`

### 1.6 GET /api/enrollments/{id} — 选课详情
- **权限**: `hasAnyRole('STUDENT','TEACHER','ADMIN','ACADEMIC')`

### 1.7 PUT /api/enrollments/{id} — 更新选课 (进度/完成)
- **权限**: `hasAnyRole('TEACHER','ADMIN')`

### 1.8 DELETE /api/enrollments/{id} — 退课
- **权限**: `hasAnyRole('STUDENT','ADMIN')`
- **守卫**: 进度 > 50% 拒绝退课; PAID 订单自动退款

### 1.9 GET /api/enrollments/export — 导出选课
- **权限**: `hasAnyRole('TEACHER','ADMIN','ACADEMIC')`

### 1.10 GET /api/enrollments/student-detail/{userId} — 学生详情
- **权限**: `hasAnyRole('TEACHER','ADMIN','ACADEMIC')`

---

## 2. 购物车 (5 端点) — `/api/cart`
> 创建: git blame 040b206e, 2026-06-27

### 2.1 GET /api/cart — 购物车列表
- **权限**: `isAuthenticated()`

### 2.2 POST /api/cart — 添加课程
- **权限**: `isAuthenticated()`
- **请求**: `CartAddRequest { courseId* }`

### 2.3 PUT /api/cart/{itemId} — 更新数量
- **权限**: `isAuthenticated()`

### 2.4 DELETE /api/cart/{itemId} — 删除项
- **权限**: `isAuthenticated()`

### 2.5 DELETE /api/cart — 清空
- **权限**: `isAuthenticated()`

---

## 3. 订单 (8 端点) — `/api/orders`
> 创建: git blame d017ef22, 2026-06-21

### 3.1 POST /api/orders — 创建订单
- **权限**: `hasRole('STUDENT')`
### 3.2 POST /api/orders/batch — 批量创建
- **权限**: `hasRole('STUDENT')`
### 3.3 GET /api/orders/{id} — 订单详情
- **权限**: `isAuthenticated()`
### 3.4 GET /api/orders/my — 我的订单
- **权限**: `hasRole('STUDENT')`
### 3.5 POST /api/orders/{id}/pay — 支付
- **权限**: `hasRole('STUDENT')`
### 3.6 POST /api/orders/{id}/cancel — 取消
- **权限**: `hasAnyRole('STUDENT','ADMIN')`
### 3.7 POST /api/orders/{id}/refund — 退款
- **权限**: `hasAnyRole('STUDENT','ADMIN')`
### 3.8 POST /api/orders/callback — 支付回调
- **权限**: `permitAll()`

---

## 4. 状态机

### EnrollmentStatus 状态图
```
PENDING    → APPROVED, REJECTED, WAITLIST, CANCELLED
APPROVED   → COMPLETED, CANCELLED, DROPPED, SUSPENDED
WAITLIST   → APPROVED, CANCELLED, SUSPENDED
SUSPENDED  → APPROVED, CANCELLED
CANCELLED  → REENROLLING
REJECTED   → *终态*
COMPLETED  → *终态*
DROPPED    → *终态*
REENROLLING → *终态*
```

### P0 修复
- V160: CHECK 约束补充 SUSPENDED + REENROLLING (2026-07-07)

---

## 5. 错误码

| 错误码 | 说明 | HTTP |
|--------|------|------|
| 8001 | 选课失败 (ENROLLMENT_FAILED) | 400 |
| 8002 | 容量已满 (COURSE_FULL) | 409 |
| 8003 | 先修课未完成 (PREREQUISITE_NOT_MET) | 400 |
| 8004 | 已选课 (ALREADY_ENROLLED) | 409 |
| 8005 | 进度 > 50% 不可退课 (CANNOT_DROP) | 400 |