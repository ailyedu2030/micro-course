-- V165__fix_order_enrollment_defects.sql
-- 全量修复：选课 + 订单缺陷（C-27, I-26, P2-1）
--
-- 修改清单：
-- 1. (C-27) 防止同一用户对同一课程创建多个 PENDING/PAID 订单（TOCTOU 竞态修复）
--    createOrder() 的 Java 层幂等检查无 DB 约束兜底，并发请求可同时通过检查创建重复订单。
--    部分唯一索引：仅对活跃状态的订单（PENDING/PAID）生效，CANCELLED/REFUNDED 不阻塞。
-- 2. (I-26) orders.status 和 orders.amount 缺少 CHECK 约束
--    status 必须是契约枚举值之一，amount 必须 >= 0。
-- 3. (P2-1) WAITLIST 晋升缺复合索引
--    promoteFirstWaitlistToEnrolled 的查询无 (course_id, enrollment_status, enrolled_at) 索引，
--    当 enrollments 表数据量大时性能差。

BEGIN;

-- ====== 1. (C-27) 部分唯一索引：防止同一用户对同一课程创建多个活跃订单 ======
-- createOrder() 中幂等检查为 Java 层 selectOne，并发场景下两个请求可同时通过检查。
-- 此索引作为 DB 级兜底，INSERT 重复时抛出 DuplicateKeyException。
-- 作用范围：仅 PENDING 和 PAID 状态的订单。CANCELLED / REFUNDED 不参与。
CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_user_course_active
    ON orders (user_id, course_id) WHERE status IN ('PENDING', 'PAID');

COMMENT ON INDEX uk_orders_user_course_active IS 'C-27: 防止同一用户对同一课程创建多个活跃订单（TOCTOU 竞态修复）';

-- ====== 2. (I-26) orders 表 CHECK 约束 ======
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_status;
ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED'));

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_amount;
ALTER TABLE orders ADD CONSTRAINT chk_orders_amount CHECK (amount >= 0);

COMMENT ON CONSTRAINT chk_orders_status ON orders IS 'I-26: 订单状态必须在契约枚举值范围内';
COMMENT ON CONSTRAINT chk_orders_amount ON orders IS 'I-26: 订单金额不能为负数';

-- ====== 3. (P2-1) WAITLIST 晋升复合索引 ======
-- promoteFirstWaitlistToEnrolled 查询条件：
--   WHERE course_id = ? AND enrollment_status = 'WAITLIST'
--   ORDER BY enrolled_at ASC LIMIT 1
CREATE INDEX IF NOT EXISTS idx_enrollments_waitlist_promote
    ON enrollments (course_id, enrollment_status, enrolled_at);

COMMENT ON INDEX idx_enrollments_waitlist_promote IS 'P2-1: WAITLIST 晋升查询复合索引（course_id + status + enrolled_at）';

END;
