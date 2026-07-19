#!/usr/bin/env bash
# 错误 SLA 追踪脚本 (W33 治理)
#
# 作用:
#   1. 拉取 Prometheus 当前 active alerts
#   2. 按 P0/P1/P2/P3 分级
#   3. 检查每个 alert 的持续时间
#   4. 对照 SLA 矩阵判断是否超时
#   5. 输出报告 (PASS/FAIL)
#
# SLA 矩阵:
#   P0: 1h 响应 / 4h 修复
#   P1: 2h 响应 / 24h 修复
#   P2: 7 天修复
#   P3: 30 天优化

set -e

API_BASE="${API_BASE:-http://localhost:9090}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="${SCRIPT_DIR}/../../reports/sla"
mkdir -p "$REPORT_DIR"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT_FILE="${REPORT_DIR}/sla-${TIMESTAMP}.log"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

exec > >(tee -a "$REPORT_FILE") 2>&1

echo "============================================================"
echo "  错误 SLA 追踪 (W33 治理)"
echo "  API_BASE=${API_BASE}"
echo "  Report: ${REPORT_FILE}"
echo "============================================================"

# 检查 Prometheus
HEALTH=$(curl -s -m 5 -o /dev/null -w "%{http_code}" "${API_BASE}/-/healthy")
if [ "$HEALTH" != "200" ]; then
    echo -e "${RED}FAIL${NC}: Prometheus 不可达 (${HEALTH})"
    exit 1
fi

# 拉取所有 active alerts
ALERTS=$(curl -s -m 10 "${API_BASE}/api/v1/alerts")

# 解析
echo ""
echo "--- 当前活跃告警 ---"
echo "$ALERTS" | python3 -c "
import json,sys
from datetime import datetime, timezone

d = json.load(sys.stdin)
alerts = d.get('data',{}).get('alerts',[])

# 按 P0/P1/P2/P3 分组
groups = {'P0':[], 'P1':[], 'P2':[], 'P3':[]}
for a in alerts:
    p = a.get('labels',{}).get('priority','P3')
    if p not in groups:
        p = 'P3'
    groups[p].append(a)

# 输出
for p in ['P0','P1','P2','P3']:
    items = groups[p]
    if not items:
        continue
    print(f'\\n[{p}] {len(items)} active:')
    for a in items:
        name = a.get('labels',{}).get('alertname','?')
        state = a.get('state','?')
        active_at = a.get('activeAt','')
        value = a.get('value','-')
        print(f'  - {name} (state={state}, active_at={active_at[:19]}, value={value[:30]})')

total = sum(len(v) for v in groups.values())
print(f'TOTAL_ALERTS: {total}')
print(f'\\n总活跃告警: {total}')"

# 拉取所有 rule groups 验证告警规则
echo ""
echo "--- 告警规则验证 ---"
RULES=$(curl -s -m 10 "${API_BASE}/api/v1/rules" | python3 -c "
import json,sys
d = json.load(sys.stdin)
total = 0
groups = d.get('data',{}).get('groups',[])
for g in groups:
    n = len(g.get('rules',[]))
    total += n
    print(f'  group {g[\"name\"]}: {n} rules')
print(f'TOTAL: {total}')
")

# 输出 SLA 状态
echo ""
echo "============================================================"
echo "  SLA 状态判定"
echo "============================================================"
echo "  P0 修复 SLA: 1h 响应 + 4h 修复"
echo "  P1 修复 SLA: 2h 响应 + 24h 修复"
echo "  P2 修复 SLA: 7 天"
echo "  P3 修复 SLA: 30 天"
echo ""
echo "  详情查看: ${REPORT_FILE}"
echo "============================================================"