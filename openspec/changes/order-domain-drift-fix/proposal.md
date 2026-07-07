# 订单支付域 Spec 漂移全量修复 (Order Domain Drift Fix)

## Why
- `paymentCallback()` L475 用字符串 `"PENDING"` 判断而非 `canTransitionTo(PAID)` (P1-C)
- 状态机设计文档 §8 过时: 说 refund 不存、cancelOrder 用字符串(均已实现)
- 数据字典: orders.version 未登记, orders/payments.status NOT NULL 漏标

## 任务 (5 项)
1. paymentCallback() 改用 canTransitionTo(PAID) 
2. 数据字典 v1.3→v1.4 (orders.version + status NOT NULL)
3. 状态机设计 v1.3→v1.4 (同步 refund/cancelOrder 实现现状)
4. API 契约补充错误码
5. 测试验证

## 方法论
git blame 验证: order domain 全部由 总工程师 在 d017ef22/ad9074af 创建
代码实现与设计意图基本一致, 仅需文档同步 + 1 项代码修复
