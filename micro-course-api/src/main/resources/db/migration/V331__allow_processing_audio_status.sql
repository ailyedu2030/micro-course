-- V331: v2 音频表 CHECK 约束允许 'PROCESSING' 状态（L0 兜底紧急 P0 修复）
--
-- 背景: TtsWorker Q-1 幂等修复（V330）引入 'PROCESSING' 状态用于原子抢占（claimPending），
--       但 V302/V305 创建表时的 CHECK 约束只允许 ('GENERATING','READY','FAILED')。
--       全仓无任何 migration 修改过该约束，导致 claimPending UPDATE 被拒绝 →
--       TtsWorkerService.poll() catch 吞异常 → 0 行返回 → v2 音频永远停在 GENERATING，
--       永不 READY（D3/P0-3 核心目标被破坏，用户侧播放器永久无音）。
--
-- 根因: TtsWorker 多线程幂等设计（V330 增加 is_default + worker_id 列）
--       引入 PROCESSING 中间状态，但 CHECK 约束未同步更新。
--
-- 修复: DROP 旧 CHECK + ADD 新 CHECK 允许 PROCESSING。
--       约束名沿用 V302/V305 原始命名（chk_ppt_audios_status / chk_html_seg_audios_status），
--       最小变更，避免改名带来的外部引用断裂。
--       Flyway 启动时自动应用。
--
-- Rollback 路径:
--   1) 部署前停止 TtsWorker 调度（避免 claimPending 写入）
--   2) UPDATE slide_ppt_page_audios SET status='GENERATING', worker_id=NULL WHERE status='PROCESSING'
--      UPDATE slide_html_segment_audios SET status='GENERATING', worker_id=NULL WHERE status='PROCESSING'
--   3) ALTER TABLE slide_ppt_page_audios DROP CONSTRAINT chk_ppt_audios_status;
--      ALTER TABLE slide_ppt_page_audios ADD CONSTRAINT chk_ppt_audios_status CHECK (status IN ('GENERATING','READY','FAILED'));
--      （slide_html_segment_audios 同理）
--   4) 重启 TtsWorker

-- 1. slide_ppt_page_audios
ALTER TABLE slide_ppt_page_audios
    DROP CONSTRAINT IF EXISTS chk_ppt_audios_status;

ALTER TABLE slide_ppt_page_audios
    ADD CONSTRAINT chk_ppt_audios_status
    CHECK (status IN ('GENERATING', 'PROCESSING', 'READY', 'FAILED'));

-- 2. slide_html_segment_audios
ALTER TABLE slide_html_segment_audios
    DROP CONSTRAINT IF EXISTS chk_html_seg_audios_status;

ALTER TABLE slide_html_segment_audios
    ADD CONSTRAINT chk_html_seg_audios_status
    CHECK (status IN ('GENERATING', 'PROCESSING', 'READY', 'FAILED'));

-- 3. 加注释（PostgreSQL COMMENT ON CONSTRAINT 语法）
COMMENT ON CONSTRAINT chk_ppt_audios_status ON slide_ppt_page_audios IS
    '音频状态枚举：GENERATING(等待消费)/PROCESSING(已被worker抢占,Q-1原子抢占中间态)/READY(生成完成,可播放)/FAILED(生成失败)';
COMMENT ON CONSTRAINT chk_html_seg_audios_status ON slide_html_segment_audios IS
    '音频状态枚举：GENERATING/PROCESSING/READY/FAILED';

-- 4. 索引（加快按状态查询）
CREATE INDEX IF NOT EXISTS idx_ppt_page_audios_status_processing
    ON slide_ppt_page_audios (status) WHERE status = 'PROCESSING';
CREATE INDEX IF NOT EXISTS idx_html_segment_audios_status_processing
    ON slide_html_segment_audios (status) WHERE status = 'PROCESSING';
