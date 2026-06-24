-- 微专业申报表补齐前置条件字段（补齐前端孤儿字段）
ALTER TABLE micro_specialty_proposals ADD COLUMN IF NOT EXISTS prerequisites TEXT;
COMMENT ON COLUMN micro_specialty_proposals.prerequisites IS '选课前置条件说明（如：先修课程、基础知识要求）';
