-- P0 修复：清理已废弃的 badges 表
-- badges 表（V18 创建）已被 badge_definitions（V37）+ achievements（V38）完全替代
-- V40 已将数据迁移至 achievements 表，badges 表现已无任何代码引用或数据依赖

DROP TABLE IF EXISTS badges;
