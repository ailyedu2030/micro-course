-- W37 治理: Phase 5 旧表清理倒计时视图 (spec 7)
--
-- 目的:
--   监控旧表 slide_pages 的流量, 当 is_legacy=TRUE 的行数 = 0 且 30 天无写入时
--   才执行最终 DROP TABLE slide_pages (Phase 5 收尾).
--
-- 当前状态 (W37 压测):
--   is_legacy=TRUE: 1 行 (剩余)
--   is_legacy=FALSE: 138 行 (V310 回填)
--
-- 启动条件 (Phase 5):
--   1. v_legacy_cleanup_eligible.eligible = true
--   2. 公告满 30 天
--   3. 生产 DBA 人工确认

-- ===== 视图 1: 旧表行数监控 =====
CREATE OR REPLACE VIEW v_slide_pages_legacy_status AS
SELECT
    COUNT(*) FILTER (WHERE is_legacy = TRUE) AS legacy_rows,
    COUNT(*) FILTER (WHERE is_legacy = FALSE) AS migrated_rows,
    COUNT(*) AS total_rows,
    MAX(updated_at) FILTER (WHERE is_legacy = TRUE) AS last_legacy_update,
    EXTRACT(DAY FROM (NOW() - MAX(updated_at) FILTER (WHERE is_legacy = TRUE))) AS days_since_last_legacy_write
FROM slide_pages;

-- ===== 视图 2: Phase 5 启动资格判定 =====
-- eligible = true 时表示 30 天倒计时可结束, 允许 DROP
CREATE OR REPLACE VIEW v_legacy_cleanup_eligible AS
SELECT
    legacy_rows,
    migrated_rows,
    last_legacy_update,
    days_since_last_legacy_write,
    CASE
        WHEN legacy_rows = 0 AND days_since_last_legacy_write >= 30 THEN TRUE
        ELSE FALSE
    END AS eligible
FROM v_slide_pages_legacy_status;

-- ===== 视图 3: 当前状态 (供 Grafana / Prometheus 监控) =====
-- 命名遵循 v_slide_*_status 约定 (与 V308 一致)
COMMENT ON VIEW v_slide_pages_legacy_status IS
    'W37 Phase 5 旧表清理监控视图. legacy_rows=0 表示旧表清空. days_since_last_legacy_write >= 30 时可执行 DROP.';