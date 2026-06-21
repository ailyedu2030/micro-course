-- V66__learning_progress_chapter_unique.sql
-- Round 8-4 (P0)：章节级学习进度并发重复记录修复。
-- 背景：V45 仅对 lesson_id IS NOT NULL 建唯一索引(uk_lp_user_lesson)，章节级进度
--       （lesson_id 为 null）此前无任何唯一约束，多设备并发上报会产生重复记录、
--       导致完成度计算翻倍。此处补齐章节级部分唯一索引，与 LearningProgressServiceImpl.create
--       的应用层查重共同兜底。
-- 幂等：先清理历史重复（每组 (user_id, course_id, chapter_id) 仅保留 id 最大者），
--       再 CREATE UNIQUE INDEX IF NOT EXISTS。可随 Flyway 反复执行而无副作用。
--       与 V43/V44/V59 既有「清理重复 + 部分唯一索引」模式保持一致。

-- 1) 清理章节级（lesson_id 为 null）活跃记录中的重复，保留 id 最大者
DELETE FROM learning_progress a
USING learning_progress b
WHERE a.user_id = b.user_id
  AND a.course_id = b.course_id
  AND a.chapter_id IS NOT DISTINCT FROM b.chapter_id
  AND a.lesson_id IS NULL
  AND b.lesson_id IS NULL
  AND a.deleted_at IS NULL
  AND b.deleted_at IS NULL
  AND a.id < b.id;

-- 2) 部分唯一索引：仅约束章节级、未软删除记录，与 lesson 级索引互不冲突
CREATE UNIQUE INDEX IF NOT EXISTS uk_lp_user_course_chapter_active
    ON learning_progress (user_id, course_id, chapter_id)
    WHERE lesson_id IS NULL AND deleted_at IS NULL;
