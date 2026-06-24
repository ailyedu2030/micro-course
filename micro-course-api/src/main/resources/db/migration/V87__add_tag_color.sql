-- 标签颜色支持（补齐前端孤儿字段）
-- 前端 TagList.vue 已实现颜色选择器和表格展示，后端实体缺失此字段
ALTER TABLE tags ADD COLUMN IF NOT EXISTS color VARCHAR(50);
COMMENT ON COLUMN tags.color IS '标签颜色（HEX 格式 #rrggbb 或颜色名）';
