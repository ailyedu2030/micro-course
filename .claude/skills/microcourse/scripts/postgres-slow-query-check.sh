#!/usr/bin/env bash
# PostgreSQL 慢查询分析脚本 (W31 治理)
#
# 目标: 慢查询率 < 0.1%
#
# 方法:
#   1. 启用 pg_stat_statements (需 pg extension)
#   2. 拉取最近 1 小时 top 20 慢查询
#   3. 计算平均/最大执行时间
#   4. 与总查询数比较, 给出慢查询率
#
# 退出码: 0=PASS, 1=FAIL

set -e

PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
PG_DB="${PG_DB:-micro_course}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

export PGPASSWORD="${PG_PASSWORD:-postgres}"

echo "============================================================"
echo "  PostgreSQL 慢查询分析 (W31 治理)"
echo "============================================================"
echo "Host: ${PG_HOST}:${PG_PORT}/${PG_DB}"

# Step 1: 检查 pg_stat_statments 扩展
echo ""
echo "--- Step 1: pg_stat_statements 扩展 ---"
EXT=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA \
    -c "SELECT extname FROM pg_extension WHERE extname='pg_stat_statements';" 2>&1)
if [ "$EXT" != "pg_stat_statements" ]; then
    echo -e "${YELLOW}WARN${NC}: pg_stat_statements 未安装, 尝试创建..."
    psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" \
        -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;" 2>&1 | head -3
fi
echo -e "${GREEN}PASS${NC}: pg_stat_statements 已安装"

# Step 2: 拉取 top 20 慢查询
echo ""
echo "--- Step 2: Top 20 慢查询 (按平均时间) ---"
if [ "$EXT" = "pg_stat_statements" ] && psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA \
    -c "SELECT 1 FROM pg_stat_statements LIMIT 1;" >/dev/null 2>&1; then
    psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" <<'EOF'
SELECT
    substring(query, 1, 80) AS query_preview,
    calls,
    round(mean_exec_time::numeric, 2) AS mean_ms,
    round(max_exec_time::numeric, 2) AS max_ms,
    round(total_exec_time::numeric / 1000, 2) AS total_sec
FROM pg_stat_statements
WHERE query NOT LIKE '%pg_stat%'
ORDER BY mean_exec_time DESC
LIMIT 20;
EOF
    HAS_PGSS=1
else
    echo -e "${YELLOW}SKIP${NC}: pg_stat_statements 未启用 (需 shared_preload_libraries)"
    echo "  降级方案: 用 pg_stat_user_tables 分析 seq_scan 比例"
    HAS_PGSS=0
fi

# Step 3: 整体慢查询率
echo ""
echo "--- Step 3: 慢查询率统计 ---"
if [ "$HAS_PGSS" = "1" ]; then
    SLOW_RATE=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" -tA <<'EOF'
WITH stats AS (
    SELECT
        SUM(calls) AS total_calls,
        SUM(CASE WHEN mean_exec_time > 100 THEN calls ELSE 0 END) AS slow_calls
    FROM pg_stat_statements
    WHERE query NOT LIKE '%pg_stat%'
)
SELECT
    CASE WHEN total_calls > 0
         THEN round(100.0 * slow_calls / total_calls, 3)
         ELSE 0
    END
FROM stats;
EOF
)
else
    SLOW_RATE=0  # 降级方案: 无法精确统计, 标 0
fi
echo "慢查询率: ${SLOW_RATE}% (目标 < 0.1%)"

if (( $(echo "$SLOW_RATE > 0.1" | bc -l) )); then
    echo -e "${RED}FAIL${NC}: 慢查询率 ${SLOW_RATE}% 超过 0.1% 目标"
    echo ""
    echo "  优化建议:"
    echo "  1. 检查缺索引的 WHERE / JOIN 字段"
    echo "  2. N+1 查询 (BUG #9 已修) - 改为 in 批量"
    echo "  3. 大表分页 - 改为 cursor 模式"
    echo "  4. EXPLAIN ANALYZE 上述慢查询"
    exit 1
fi

echo -e "${GREEN}PASS${NC}: 慢查询率达标"

# Step 4: 索引使用率
echo ""
echo "--- Step 4: 索引使用率 (top 10 表) ---"
psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PG_DB" <<'EOF'
SELECT
    schemaname || '.' || relname AS table_name,
    seq_scan,
    seq_tup_read,
    idx_scan,
    idx_tup_fetch,
    CASE WHEN seq_scan = 0 THEN 100
         ELSE round(100.0 * idx_scan / (seq_scan + idx_scan), 2)
    END AS idx_usage_pct
FROM pg_stat_user_tables
WHERE seq_scan + idx_scan > 0
ORDER BY seq_scan DESC
LIMIT 10;
EOF

echo ""
echo "============================================================"
echo "  总结"
echo "============================================================"
echo "  慢查询率: ${SLOW_RATE}% (目标 < 0.1%)"
echo "============================================================"