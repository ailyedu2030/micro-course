-- V116__fix_pricing_list_price_sync.sql
-- P0 修复: 同步 list_price 字段
--
-- 问题: V111 引入 list_price 列后，所有 API 都只写 price 不写 list_price，
-- 导致 list_price 永远为 0，getMyPricing() 所有课程返回免费。
--
-- 修复:
-- 1. 现有课程: price > 0 且 list_price = 0 → 同步 price 到 list_price
-- 2. 免费的付费课程: price = 0 且 is_free = false 且 list_price = 0
--    → 通常是付费但价格尚未设置的课程，保持 0 但标明需要定价审批
-- 3. 免费课程: is_free = true → 保持 list_price = 0

-- 第 1 类: 有价格但 list_price 为 0 的课程 → 同步
UPDATE courses
SET list_price = price,
    pricing_status = 'DRAFT',
    updated_at = CURRENT_TIMESTAMP
WHERE (list_price IS NULL OR list_price = 0)
  AND (price IS NOT NULL AND price > 0)
  AND deleted_at IS NULL;

-- 第 2 类: is_free = false 但 price = 0 且 list_price = 0
-- → 这些课程可能价格还没设置，标记为 DRAFT 需要定价
UPDATE courses
SET pricing_status = 'DRAFT',
    updated_at = CURRENT_TIMESTAMP
WHERE (list_price IS NULL OR list_price = 0)
  AND (price IS NULL OR price = 0)
  AND (is_free IS NULL OR is_free = FALSE)
  AND deleted_at IS NULL;

-- 第 3 类: 真正免费的课程 → 确保 list_price = 0 且 pricing_status = APPROVED
-- 注意: V111 设置 list_price NOT NULL DEFAULT 0, 所以 WHERE 用 list_price = 0 而非 IS NULL
UPDATE courses
SET list_price = 0,
    pricing_status = 'APPROVED',
    pricing_reviewed_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE (list_price = 0)
  AND is_free = TRUE
  AND deleted_at IS NULL;
