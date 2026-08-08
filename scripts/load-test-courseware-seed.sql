-- =============================================================================
-- load-test-courseware-seed.sql
-- Phase 14 兜底 · Load Test 种子数据（真实 schema · 真实 FK · 幂等）
--
-- 用途: 在 dev/staging DB 建立压测课件数据（不触碰生产）:
--   - course 990001: PPT 课件（120 个 section × 30 页 + 20 个 section × 100 页）
--   - course 990002: HTML 课件（20 个 unit × 50 段）
--   每个 PPT page: 1 个 active script + 1 条 READY audio（U-5 默认音色排序路径）
--   每个 HTML segment: 1 个 active script + 1 条 READY audio
--   每个 PPT section: 线性 NEXT flow 链（真实播放器 flow 加载路径）
--
-- 幂等: 先 DELETE 旧压测课程（ON DELETE CASCADE 联动子表），再插入。
--
-- 安全: 仅本地/dev DB。禁止对生产 DB 执行本脚本（生产门禁见 staging-validation.sh）。
-- =============================================================================

BEGIN;

-- ─────────────────────────────────────────────────────────────
-- 0. 清理旧压测数据（幂等）
-- ─────────────────────────────────────────────────────────────
DELETE FROM slide_ppt_flow          WHERE section_id IN (SELECT id FROM course_sections WHERE course_id IN (990001, 990002));
DELETE FROM slide_ppt_page_audios   WHERE ppt_page_id IN (SELECT id FROM slide_ppt_pages WHERE course_id IN (990001, 990002));
DELETE FROM slide_ppt_page_scripts  WHERE ppt_page_id IN (SELECT id FROM slide_ppt_pages WHERE course_id IN (990001, 990002));
DELETE FROM slide_html_segment_audios WHERE html_unit_id IN (SELECT id FROM slide_html_units WHERE course_id IN (990001, 990002));
DELETE FROM slide_html_segment_scripts WHERE html_unit_id IN (SELECT id FROM slide_html_units WHERE course_id IN (990001, 990002));
DELETE FROM slide_ppt_pages         WHERE course_id IN (990001, 990002);
DELETE FROM slide_html_units        WHERE course_id IN (990001, 990002);
DELETE FROM course_slides           WHERE course_id IN (990001, 990002);
DELETE FROM course_sections         WHERE course_id IN (990001, 990002);
DELETE FROM course_chapters         WHERE course_id IN (990001, 990002);
DELETE FROM courses                 WHERE id IN (990001, 990002);

-- 手动清理序列（避免显式 ID 与序列冲突；空表用 GREATEST(...,1) 防 0 越界）
SELECT setval('slide_ppt_pages_id_seq',           GREATEST((SELECT COALESCE(MAX(id), 0) FROM slide_ppt_pages),           1), true);
SELECT setval('slide_ppt_page_scripts_id_seq',    GREATEST((SELECT COALESCE(MAX(id), 0) FROM slide_ppt_page_scripts),    1), true);
SELECT setval('slide_ppt_page_audios_id_seq',     GREATEST((SELECT COALESCE(MAX(id), 0) FROM slide_ppt_page_audios),     1), true);
SELECT setval('slide_html_units_id_seq',          GREATEST((SELECT COALESCE(MAX(id), 0) FROM slide_html_units),          1), true);
SELECT setval('slide_html_segment_scripts_id_seq',GREATEST((SELECT COALESCE(MAX(id), 0) FROM slide_html_segment_scripts),1), true);
SELECT setval('slide_html_segment_audios_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM slide_html_segment_audios), 1), true);
SELECT setval('slide_ppt_flow_id_seq',            GREATEST((SELECT COALESCE(MAX(id), 0) FROM slide_ppt_flow),            1), true);
SELECT setval('course_sections_id_seq',           GREATEST((SELECT COALESCE(MAX(id), 0) FROM course_sections),           1), true);
SELECT setval('course_slides_id_seq',             GREATEST((SELECT COALESCE(MAX(id), 0) FROM course_slides),             1), true);
SELECT setval('course_chapters_id_seq',           GREATEST((SELECT COALESCE(MAX(id), 0) FROM course_chapters),           1), true);

-- ─────────────────────────────────────────────────────────────
-- 1. 压测课程 + 章节
-- ─────────────────────────────────────────────────────────────
INSERT INTO courses (id, title, subtitle, summary, category_id, teacher_id, status, course_type,
                     difficulty, max_students, is_free, price, description)
VALUES
  -- 【V333 简化方案】990001 全 PPT section → PPT_COURSEWARE；990002 全 HTML section → HTML_COURSEWARE
  (990001, 'LOADTEST-PPT 课件压测', '压测专用', '30/100 页 PPT 性能基准', 1, 1, 1, 'PPT_COURSEWARE', 1, 9999, true, 0, 'Phase 14 load test PPT'),
  (990002, 'LOADTEST-HTML 课件压测', '压测专用', '50 段 HTML 性能基准', 1, 1, 1, 'HTML_COURSEWARE', 1, 9999, true, 0, 'Phase 14 load test HTML');

INSERT INTO course_chapters (id, course_id, title, sort_order)
VALUES
  (990001, 990001, '压测章节-PPT', 0),
  (990002, 990002, '压测章节-HTML', 0);

-- ─────────────────────────────────────────────────────────────
-- 2. PPT sections（120 个 30 页 section + 20 个 100 页 section）
--    section id 范围: 9910001..9910120 (30页) / 9910201..9910220 (100页)
-- ─────────────────────────────────────────────────────────────
INSERT INTO course_sections (id, chapter_id, course_id, title, section_type, sort_order, courseware_type, audio_strategy)
SELECT 9910000 + s, 990001, 990001, 'LOADTEST-PPT30-' || s, 'INTERACTIVE', s, 'PPT', '15-segment'
FROM generate_series(1, 120) s;

INSERT INTO course_sections (id, chapter_id, course_id, title, section_type, sort_order, courseware_type, audio_strategy)
SELECT 9910200 + s, 990001, 990001, 'LOADTEST-PPT100-' || s, 'INTERACTIVE', s, 'PPT', '15-segment'
FROM generate_series(1, 20) s;

-- course_slides（每 section 一行，slide_id 供页面 FK）
INSERT INTO course_slides (id, course_id, section_id, file_name, file_url, total_pages, status)
SELECT 9920000 + s, 990001, 9910000 + s, 'loadtest-ppt30-' || s || '.pptx', '/loadtest/ppt30-' || s || '.pptx', 30, 2
FROM generate_series(1, 120) s;
INSERT INTO course_slides (id, course_id, section_id, file_name, file_url, total_pages, status)
SELECT 9920200 + s, 990001, 9910200 + s, 'loadtest-ppt100-' || s || '.pptx', '/loadtest/ppt100-' || s || '.pptx', 100, 2
FROM generate_series(1, 20) s;

-- ─────────────────────────────────────────────────────────────
-- 3. PPT pages（每个 page 一行，显式 ID 供 scripts/audios/flows FK）
--    page id 范围: 100000000 + (s-1)*100 + p   (30页, s=1..120, p=1..30)
--                 110000000 + (s-1)*200 + p   (100页, s=1..20, p=1..100)
-- ─────────────────────────────────────────────────────────────
INSERT INTO slide_ppt_pages (id, course_id, chapter_id, section_id, slide_id, page_number, page_title,
                             image_url, thumbnail_url, file_uuid, file_size_bytes, extracted_text,
                             has_animation, has_embedded_media, created_at, updated_at, version)
SELECT 100000000 + (s - 1) * 100 + p, 990001, 990001, 9910000 + s, 9920000 + s, p,
       'LoadTest Page ' || p, '/img/loadtest/p' || s || '-' || p || '.png',
       '/img/loadtest/t' || s || '-' || p || '.png',
       'lt30-' || s || '-' || p, 102400 + p,
       '压测页文本：第 ' || p || ' 页的讲解内容。',
       (p % 5 = 0), (p % 7 = 0), NOW(), NOW(), 1
FROM generate_series(1, 120) s, generate_series(1, 30) p;

INSERT INTO slide_ppt_pages (id, course_id, chapter_id, section_id, slide_id, page_number, page_title,
                             image_url, thumbnail_url, file_uuid, file_size_bytes, extracted_text,
                             has_animation, has_embedded_media, created_at, updated_at, version)
SELECT 110000000 + (s - 1) * 200 + p, 990001, 990001, 9910200 + s, 9920200 + s, p,
       'LoadTest Page ' || p, '/img/loadtest/p100-' || s || '-' || p || '.png',
       '/img/loadtest/t100-' || s || '-' || p || '.png',
       'lt100-' || s || '-' || p, 102400 + p,
       '压测页文本：第 ' || p || ' 页的讲解内容。',
       (p % 5 = 0), (p % 7 = 0), NOW(), NOW(), 1
FROM generate_series(1, 20) s, generate_series(1, 100) p;

-- ─────────────────────────────────────────────────────────────
-- 4. PPT scripts（每 page 1 个 active script）
--    script id 范围: 200000000 + (s-1)*100 + p / 210000000 + (s-1)*200 + p
-- ─────────────────────────────────────────────────────────────
INSERT INTO slide_ppt_page_scripts (id, ppt_page_id, script_text, script_version, is_active,
                                    voice, tts_model, created_at, created_by, updated_at, version)
SELECT 200000000 + (s - 1) * 100 + p, 100000000 + (s - 1) * 100 + p,
       '这是第 ' || p || ' 页的 AI 讲述稿，用于压测脚本查询。', 1, true,
       'alloy', 'tts-1', NOW(), 1, NOW(), 1
FROM generate_series(1, 120) s, generate_series(1, 30) p;

INSERT INTO slide_ppt_page_scripts (id, ppt_page_id, script_text, script_version, is_active,
                                    voice, tts_model, created_at, created_by, updated_at, version)
SELECT 210000000 + (s - 1) * 200 + p, 110000000 + (s - 1) * 200 + p,
       '这是第 ' || p || ' 页的 AI 讲述稿，用于压测脚本查询。', 1, true,
       'alloy', 'tts-1', NOW(), 1, NOW(), 1
FROM generate_series(1, 20) s, generate_series(1, 100) p;

-- ─────────────────────────────────────────────────────────────
-- 5. PPT audios（每 script 1 条 READY，is_default=true 触发 U-5 排序路径）
--    audio id 范围: 300000000 + (s-1)*100 + p / 310000000 + (s-1)*200 + p
-- ─────────────────────────────────────────────────────────────
INSERT INTO slide_ppt_page_audios (id, script_id, ppt_page_id, audio_url, audio_token, audio_duration_ms,
                                   voice_used, model_used, generation_params, generation_started_at,
                                   completed_at, status, file_size_bytes, storage_path, created_at,
                                   error_message, is_default, worker_id)
SELECT 300000000 + (s - 1) * 100 + p, 200000000 + (s - 1) * 100 + p, 100000000 + (s - 1) * 100 + p,
       '/api/courses/990001/courseware/audio/lt30-' || s || '-' || p, 'lt30-' || s || '-' || p, 12000,
       'alloy', 'tts-1', '{"speed":1.0}'::jsonb, NOW() - interval '1 hour', NOW() - interval '50 min',
       'READY', 24000, '/tmp/microcourse-audio/lt30-' || s || '-' || p || '.mp3', NOW(),
       NULL, true, NULL
FROM generate_series(1, 120) s, generate_series(1, 30) p;

INSERT INTO slide_ppt_page_audios (id, script_id, ppt_page_id, audio_url, audio_token, audio_duration_ms,
                                   voice_used, model_used, generation_params, generation_started_at,
                                   completed_at, status, file_size_bytes, storage_path, created_at,
                                   error_message, is_default, worker_id)
SELECT 310000000 + (s - 1) * 200 + p, 210000000 + (s - 1) * 200 + p, 110000000 + (s - 1) * 200 + p,
       '/api/courses/990001/courseware/audio/lt100-' || s || '-' || p, 'lt100-' || s || '-' || p, 12000,
       'alloy', 'tts-1', '{"speed":1.0}'::jsonb, NOW() - interval '1 hour', NOW() - interval '50 min',
       'READY', 24000, '/tmp/microcourse-audio/lt100-' || s || '-' || p || '.mp3', NOW(),
       NULL, true, NULL
FROM generate_series(1, 20) s, generate_series(1, 100) p;

-- ─────────────────────────────────────────────────────────────
-- 6. PPT flows（每 section 线性 NEXT 链，真实播放器 flow 加载路径）
--    flow id 范围: 400000000 + (s-1)*100 + p (p=1..29 链边)
-- ─────────────────────────────────────────────────────────────
INSERT INTO slide_ppt_flow (id, section_id, from_page_id, to_page_id, flow_type, priority, description, created_at, updated_at)
SELECT 400000000 + (s - 1) * 100 + p, 9910000 + s,
       100000000 + (s - 1) * 100 + p, 100000000 + (s - 1) * 100 + p + 1,
       'NEXT', p, 'linear', NOW(), NOW()
FROM generate_series(1, 120) s, generate_series(1, 29) p;

INSERT INTO slide_ppt_flow (id, section_id, from_page_id, to_page_id, flow_type, priority, description, created_at, updated_at)
SELECT 410000000 + (s - 1) * 200 + p, 9910200 + s,
       110000000 + (s - 1) * 200 + p, 110000000 + (s - 1) * 200 + p + 1,
       'NEXT', p, 'linear', NOW(), NOW()
FROM generate_series(1, 20) s, generate_series(1, 99) p;

-- ─────────────────────────────────────────────────────────────
-- 7. HTML units（20 个 unit × 50 段；uk_html_units_section 要求每 section 仅 1 unit）
--    unit id 范围: 50000000 + s  (s=1..20)
-- ─────────────────────────────────────────────────────────────
INSERT INTO course_sections (id, chapter_id, course_id, title, section_type, sort_order, courseware_type, audio_strategy)
SELECT 9930000 + s, 990002, 990002, 'LOADTEST-HTML-' || s, 'INTERACTIVE', s, 'HTML', '15-segment'
FROM generate_series(1, 20) s;

INSERT INTO course_slides (id, course_id, section_id, file_name, file_url, total_pages, status)
SELECT 9930000 + s, 990002, 9930000 + s, 'loadtest-html-' || s || '.html', '/loadtest/html-' || s || '.html', 50, 2
FROM generate_series(1, 20) s;

INSERT INTO slide_html_units (id, course_id, chapter_id, section_id, slide_id, page_title, file_uuid,
                              html_content, html_sanitized, file_size_bytes, detected_segments,
                              has_interactions, interaction_types, created_at, updated_at, version, is_trusted)
SELECT 50000000 + s, 990002, 990002, 9930000 + s, 9930000 + s, 'LoadTest HTML Unit ' || s,
       'lt-html-' || s,
       '<html><body><h1>Unit ' || s || '</h1><p>50 segments load test.</p></body></html>',
       '<html><body><h1>Unit ' || s || '</h1><p>50 segments load test.</p></body></html>',
       2048, 50, false, NULL, NOW(), NOW(), 1, true
FROM generate_series(1, 20) s;

-- segment scripts（每 unit 50 段 active）
-- segment_script id 范围: 510000000 + (s-1)*100 + m (m=1..50)
INSERT INTO slide_html_segment_scripts (id, html_unit_id, segment_index, segment_marker, segment_text,
                                        script_text, script_version, is_active, voice, tts_model,
                                        created_at, created_by, updated_at, version)
SELECT 510000000 + (s - 1) * 100 + m, 50000000 + s, m, 'seg-' || m,
       '第 ' || m || ' 段正文内容。',
       '第 ' || m || ' 段讲述稿，用于压测。', 1, true,
       'alloy', 'tts-1', NOW(), 1, NOW(), 1
FROM generate_series(1, 20) s, generate_series(1, 50) m;

-- segment audios（每 segment 1 条 READY）
INSERT INTO slide_html_segment_audios (id, segment_script_id, html_unit_id, segment_index, audio_url,
                                       audio_token, audio_duration_ms, voice_used, model_used,
                                       generation_params, generation_started_at, completed_at, status,
                                       file_size_bytes, storage_path, created_at, error_message, is_default, worker_id)
SELECT 520000000 + (s - 1) * 100 + m, 510000000 + (s - 1) * 100 + m, 50000000 + s, m,
       '/api/courses/990002/courseware/audio/lth-' || s || '-' || m, 'lth-' || s || '-' || m, 8000,
       'alloy', 'tts-1', '{"speed":1.0}'::jsonb, NOW() - interval '1 hour', NOW() - interval '50 min',
       'READY', 16000, '/tmp/microcourse-audio/lth-' || s || '-' || m || '.mp3', NOW(),
       NULL, true, NULL
FROM generate_series(1, 20) s, generate_series(1, 50) m;

COMMIT;

-- 汇总统计
\echo '== 种子数据完成 =='
SELECT 'PPT pages 30页' AS kind, COUNT(*) FROM slide_ppt_pages WHERE course_id = 990001 AND section_id BETWEEN 9910001 AND 9910120
UNION ALL SELECT 'PPT pages 100页', COUNT(*) FROM slide_ppt_pages WHERE course_id = 990001 AND section_id BETWEEN 9910201 AND 9910220
UNION ALL SELECT 'PPT scripts', COUNT(*) FROM slide_ppt_page_scripts WHERE ppt_page_id IN (SELECT id FROM slide_ppt_pages WHERE course_id = 990001)
UNION ALL SELECT 'PPT audios', COUNT(*) FROM slide_ppt_page_audios WHERE ppt_page_id IN (SELECT id FROM slide_ppt_pages WHERE course_id = 990001)
UNION ALL SELECT 'PPT flows', COUNT(*) FROM slide_ppt_flow WHERE section_id BETWEEN 9910001 AND 9910220
UNION ALL SELECT 'HTML units', COUNT(*) FROM slide_html_units WHERE course_id = 990002
UNION ALL SELECT 'HTML seg scripts', COUNT(*) FROM slide_html_segment_scripts WHERE html_unit_id IN (SELECT id FROM slide_html_units WHERE course_id = 990002)
UNION ALL SELECT 'HTML seg audios', COUNT(*) FROM slide_html_segment_audios WHERE html_unit_id IN (SELECT id FROM slide_html_units WHERE course_id = 990002);
