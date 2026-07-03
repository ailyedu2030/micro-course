-- V101__course_bundle_items_partial_unique.sql
-- 将 course_bundle_items 的硬唯一约束改为部分唯一（仅约束未软删的行）
-- 修复"移除课程后再添加同一课程"被硬唯一约束阻挡的问题（V51 加的是无 WHERE 的硬唯一索引）

-- 清理历史重复行（保留最早的 id）：把"同一 (bundle_id, course_id)"下除最早一条外的其余行软删
-- 这是防御性清理，正常情况下不应有重复
UPDATE course_bundle_items cbi
SET deleted_at = COALESCE(deleted_at, NOW())
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY bundle_id, course_id ORDER BY id ASC) AS rn
        FROM course_bundle_items
    ) t WHERE rn > 1
) AND deleted_at IS NULL;

DROP INDEX IF EXISTS uk_cbi_bundle_course;
CREATE UNIQUE INDEX IF NOT EXISTS uk_cbi_bundle_course_active
    ON course_bundle_items(bundle_id, course_id)
    WHERE deleted_at IS NULL;
