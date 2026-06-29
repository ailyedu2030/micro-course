-- V102: CON-001 修复 — 订单幂等性数据库级防护
-- Partial unique index 阻止并发 createOrder 产生重复 PENDING/PAID 订单
-- (userId, courseId) 组合在活跃状态下唯一, 防止 double-ordering

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_user_course_active
    ON orders (user_id, course_id)
    WHERE status IN ('PENDING', 'PAID');
