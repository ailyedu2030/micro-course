-- V330: 音频表增加 is_default（U-5 确定性音色）与 worker_id（Q-1 TtsWorker 幂等）
--
-- 背景 U-5: 教师同一 script 生成多音色时，播放器 findFirst() 行为无序。
--           is_default=false 默认值 + ORDER BY is_default DESC, completed_at DESC
--           → 播放器确定取"默认音色 → 最新完成"（体验确定性，教师改音色后学生端确定生效）。
-- 背景 Q-1: 多 worker 节点/多线程 scheduler 双 poll 可能重复合成同一行（重复扣费）。
--           worker_id 标识抢占者，配合原子 UPDATE claim（RETURNING 语义两段式）实现幂等消费。
-- Rollback 路径:
--   ALTER TABLE slide_ppt_page_audios DROP COLUMN is_default, DROP COLUMN worker_id;
--   ALTER TABLE slide_html_segment_audios DROP COLUMN is_default, DROP COLUMN worker_id;

ALTER TABLE slide_ppt_page_audios
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE slide_ppt_page_audios
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(64);

ALTER TABLE slide_html_segment_audios
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE slide_html_segment_audios
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(64);

-- Q-1: 抢占查询索引 (status + generation_started_at)
CREATE INDEX IF NOT EXISTS idx_ppt_audios_claim ON slide_ppt_page_audios(status, generation_started_at);
CREATE INDEX IF NOT EXISTS idx_html_seg_audios_claim ON slide_html_segment_audios(status, generation_started_at);

-- U-5: 默认音色优先排序索引
CREATE INDEX IF NOT EXISTS idx_ppt_audios_script_default ON slide_ppt_page_audios(script_id, is_default, completed_at);
CREATE INDEX IF NOT EXISTS idx_html_seg_audios_script_default ON slide_html_segment_audios(segment_script_id, is_default, completed_at);

COMMENT ON COLUMN slide_ppt_page_audios.is_default IS '是否为教师选定的默认音色（默认 FALSE；ORDER BY is_default DESC, completed_at DESC 确定播放音色），U-5';
COMMENT ON COLUMN slide_ppt_page_audios.worker_id IS 'TTS 合成 worker 抢占标识（UUID），Q-1 幂等消费';
COMMENT ON COLUMN slide_html_segment_audios.is_default IS '是否为教师选定的默认音色（默认 FALSE），U-5';
COMMENT ON COLUMN slide_html_segment_audios.worker_id IS 'TTS 合成 worker 抢占标识（UUID），Q-1 幂等消费';
