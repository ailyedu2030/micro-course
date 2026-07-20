-- V308: 状态聚合视图 (避免 status 字段不一致)
--
-- 设计动机: narration_status 不存字段, 实时聚合 audio.status
-- Rollback 路径: DROP VIEW IF EXISTS v_slide_ppt_page_status;
--               DROP VIEW IF EXISTS v_slide_html_unit_status;

CREATE OR REPLACE VIEW v_slide_ppt_page_status AS
SELECT
    p.id AS ppt_page_id,
    p.section_id,
    p.course_id,
    p.page_number,
    CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM slide_ppt_page_scripts s
            WHERE s.ppt_page_id = p.id AND s.is_active = TRUE
        ) THEN 'PENDING'
        WHEN NOT EXISTS (
            SELECT 1 FROM slide_ppt_page_scripts s
            JOIN slide_ppt_page_audios a ON a.script_id = s.id
            WHERE s.ppt_page_id = p.id AND s.is_active = TRUE AND a.status = 'READY'
        ) THEN 'AUDIO_GENERATING'
        ELSE 'AUDIO_READY'
    END AS narration_status,
    (
        SELECT COUNT(*) FROM slide_ppt_page_audios a
        JOIN slide_ppt_page_scripts s ON s.id = a.script_id
        WHERE s.ppt_page_id = p.id AND s.is_active = TRUE AND a.status = 'READY'
    ) AS audio_ready_count,
    p.created_at,
    p.updated_at
FROM slide_ppt_pages p;

CREATE OR REPLACE VIEW v_slide_html_unit_status AS
SELECT
    u.id AS html_unit_id,
    u.section_id,
    u.course_id,
    CASE
        WHEN (SELECT COUNT(*) FROM slide_html_segment_scripts s
              WHERE s.html_unit_id = u.id AND s.is_active = TRUE) = 0
            THEN 'PENDING'
        WHEN (
            SELECT COUNT(DISTINCT s.segment_index)
            FROM slide_html_segment_scripts s
            LEFT JOIN slide_html_segment_audios a ON a.segment_script_id = s.id AND a.status = 'READY'
            WHERE s.html_unit_id = u.id AND s.is_active = TRUE AND a.id IS NULL
        ) > 0
            THEN 'AUDIO_GENERATING'
        ELSE 'AUDIO_READY'
    END AS narration_status,
    u.created_at,
    u.updated_at
FROM slide_html_units u;

COMMENT ON VIEW v_slide_ppt_page_status IS 'PPT page 状态聚合 (V308)';
COMMENT ON VIEW v_slide_html_unit_status IS 'HTML unit 状态聚合 (V308)';