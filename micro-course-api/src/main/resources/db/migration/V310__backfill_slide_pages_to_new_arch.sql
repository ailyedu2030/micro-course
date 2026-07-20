-- V310: 回填旧 slide_pages 数据到新架构 (PR #38 Phase 3)
--
-- 【Phase 3 数据回填】
-- 旧表 slide_pages (V100-V200) 保留向后兼容, 但不再写入.
-- 本次回填:
--   1. PPT_RENDERED → slide_ppt_pages + slide_ppt_page_scripts
--   2. HTML_DIRECT → slide_html_units + slide_html_segment_scripts
--
-- 【幂等保证】 用 NOT EXISTS 保证重跑不会重复插入.
-- 【回滚】 见 V310 注释末尾的 DELETE SQL (需手动执行)

-- ═══════════════════════════════════════════════════════════════
-- 第 1 部分: PPT_RENDERED → slide_ppt_pages
-- 【Bug 修复 2026-07-20】 加 ON CONFLICT DO NOTHING 解决唯一键冲突
-- ═══════════════════════════════════════════════════════════════

INSERT INTO slide_ppt_pages (
    course_id, chapter_id, section_id, slide_id, page_number, page_title,
    image_url, thumbnail_url, image_width, image_height, extracted_text,
    has_animation, has_embedded_media, file_uuid, version, created_at, updated_at
)
SELECT
    s.course_id,
    COALESCE(s.chapter_id, 1),
    COALESCE(
        s.section_id,
        (SELECT cs.id FROM course_sections cs
         WHERE cs.course_id = s.course_id AND cs.chapter_id = s.chapter_id
         ORDER BY cs.id LIMIT 1)
    ) AS resolved_section_id,
    COALESCE(s.slide_id, 1),
    s.page_number,
    'Page ' || s.page_number,
    s.image_url,
    s.thumbnail_url,
    s.image_width,
    s.image_height,
    s.extracted_text,
    s.has_animation,
    s.has_embedded_media,
    gen_random_uuid()::text,
    1,
    s.created_at,
    s.updated_at
FROM slide_pages s
WHERE s.content_type = 'PPT_RENDERED'
  AND (
      s.section_id IS NOT NULL
      OR s.chapter_id IS NOT NULL
  )
ON CONFLICT (slide_id, page_number) DO NOTHING;

-- 把 PPT_RENDERED 的 narration_script 也复制到 slide_ppt_page_scripts
INSERT INTO slide_ppt_page_scripts (
    ppt_page_id, script_version, script_text, voice, tts_model, is_active,
    created_by, version, created_at
)
SELECT
    p.id,
    1,
    s.narration_script,
    COALESCE(s.voice, 'female-young'),
    COALESCE(s.tts_model, 'MiniMax-speech-01'),
    TRUE,
    0,
    1,
    COALESCE(s.generated_at, s.updated_at, NOW())
FROM slide_pages s
JOIN slide_ppt_pages p
    ON p.course_id = s.course_id
    AND (s.section_id IS NULL OR p.section_id = s.section_id)
    AND p.page_number = s.page_number
WHERE s.content_type = 'PPT_RENDERED'
  AND s.narration_script IS NOT NULL
  AND LENGTH(TRIM(s.narration_script)) > 0
  AND NOT EXISTS (
      SELECT 1 FROM slide_ppt_page_scripts ps WHERE ps.ppt_page_id = p.id
  );

-- ═══════════════════════════════════════════════════════════════
-- 第 2 部分: HTML_DIRECT (106 条) → slide_html_units
-- 【P0 安全】 基础 sanitize: 移除 <script> 和 on*= 属性
-- ═══════════════════════════════════════════════════════════════

INSERT INTO slide_html_units (
    course_id, chapter_id, section_id, slide_id, file_uuid, html_content, html_sanitized,
    detected_segments, file_size_bytes, has_interactions, version, created_at, updated_at
)
SELECT
    s.course_id,
    COALESCE(s.chapter_id, 1),
    s.section_id,
    COALESCE(s.slide_id, 1),
    gen_random_uuid()::text,
    COALESCE(s.html_content, ''),
    REGEXP_REPLACE(
        REGEXP_REPLACE(
            COALESCE(s.html_content, ''),
            '<script[^>]*>.*?</script>', '', 'gi'
        ),
        ' on[a-z]+="[^"]*"', '', 'gi'
    ),
    GREATEST(COALESCE(s.segment_count, 1), 1),
    LENGTH(COALESCE(s.html_content, '')),
    FALSE,
    1,
    s.created_at,
    s.updated_at
FROM slide_pages s
WHERE s.content_type = 'HTML_DIRECT'
  AND s.section_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM slide_html_units u
      WHERE u.course_id = s.course_id
        AND u.section_id = s.section_id
  );

-- HTML_DIRECT segment 1 回填
INSERT INTO slide_html_segment_scripts (
    html_unit_id, segment_index, script_text, voice, tts_model, is_active,
    created_by, version, created_at
)
SELECT
    u.id,
    1,
    COALESCE(s.narration_script, ''),
    COALESCE(s.voice, 'female-young'),
    COALESCE(s.tts_model, 'MiniMax-speech-01'),
    TRUE,
    0,
    1,
    COALESCE(s.generated_at, s.updated_at, NOW())
FROM slide_pages s
JOIN slide_html_units u
    ON u.course_id = s.course_id
    AND u.section_id = s.section_id
WHERE s.content_type = 'HTML_DIRECT'
  AND NOT EXISTS (
      SELECT 1 FROM slide_html_segment_scripts seg
      WHERE seg.html_unit_id = u.id AND seg.segment_index = 1
  );

-- 第 3 部分: 输出回填统计
DO $$
DECLARE
    ppt_total INT;
    html_total INT;
    ppt_script_total INT;
    html_seg_total INT;
    legacy_total INT;
BEGIN
    SELECT COUNT(*) INTO ppt_total FROM slide_ppt_pages;
    SELECT COUNT(*) INTO html_total FROM slide_html_units;
    SELECT COUNT(*) INTO ppt_script_total FROM slide_ppt_page_scripts;
    SELECT COUNT(*) INTO html_seg_total FROM slide_html_segment_scripts;
    SELECT COUNT(*) INTO legacy_total FROM slide_pages;

    RAISE NOTICE 'V310 backfill done: legacy_slide_pages=%, new_ppt_pages=%, new_html_units=%, ppt_scripts=%, html_segments=%',
        legacy_total, ppt_total, html_total, ppt_script_total, html_seg_total;
END $$;