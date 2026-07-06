-- P2-02 修复: banners 表增加 title 列
ALTER TABLE banners ADD COLUMN IF NOT EXISTS title VARCHAR(200) DEFAULT '';
