-- P1-C 修复：将 cart_items 表的 UNIQUE(user_id, course_id, deleted_at) 改为部分唯一索引
--
-- 原问题：UNIQUE 约束含 deleted_at 列，但 PostgreSQL 在 UNIQUE 约束中将 NULL 视为不相等的值，
--         导致同一用户可以重复添加同一课程到购物车（多条 deleted_at IS NULL 的记录），
--         违反"一个用户对一门课程只能有一条活跃购物车记录"的业务规则。
--
-- 修复方案：改为部分唯一索引，仅在 deleted_at IS NULL（活跃记录）时强制唯一性。

-- 1. 删除原来的 UNIQUE 约束（PostgreSQL 自动命名：表名_列名_key）
ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS cart_items_user_id_course_id_deleted_at_key;

-- 2. 创建部分唯一索引：活跃购物车记录中，同一用户对同一课程只能有一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_cart_user_course_active ON cart_items(user_id, course_id) WHERE deleted_at IS NULL;
