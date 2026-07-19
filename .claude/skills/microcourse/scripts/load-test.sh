#!/usr/bin/env bash
# 微课平台压测脚本 (W31 治理)
#
# 目标:
#   - 单接口 p99 < 200ms
#   - 10w 并发可行性评估 (单机可达 ~5k, 分布式需 5+ 节点)
#   - 慢查询率 < 0.1%
#
# 工具: ab (Apache Bench) / wrk (任选)
#
# 用法:
#   bash load-test.sh [endpoint-name]
#   bash load-test.sh all
#   bash load-test.sh courseware-tree

set -e

API_BASE="${API_BASE:-http://localhost:8080}"
CONCURRENCY="${CONCURRENCY:-200}"   # 单进程并发
REQUESTS="${REQUESTS:-50000}"      # 总请求数
TOOL="${TOOL:-ab}"                  # ab | wrk
AUTH_TOKEN="${AUTH_TOKEN:-}"       # 可选: bearer token

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="${SCRIPT_DIR}/../../reports/load-test"
mkdir -p "$REPORT_DIR"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

# 颜色定义
declare -A ENDPOINTS
ENDPOINTS["health"]="/actuator/health|GET|"
ENDPOINTS["courseware-tree"]="/api/courses/79/courseware/41|GET|"
ENDPOINTS["course-detail"]="/api/courses/79|GET|"
ENDPOINTS["audio-resolve"]="/api/courses/79/courseware/audio/dummy|GET|"

# 工具检测
if [ "$TOOL" = "wrk" ] && ! command -v wrk >/dev/null 2>&1; then
    echo -e "${YELLOW}WARN${NC}: wrk 未安装, 改用 ab"
    TOOL=ab
fi

run_ab() {
    local name="$1"
    local path="$2"
    local method="$3"
    local auth="$4"
    local report_file="${REPORT_DIR}/${name}-$(date +%Y%m%d-%H%M%S).txt"

    echo ""
    echo "--- ${name} (${method} ${path}) ---"

    local auth_args=()
    if [ -n "$auth" ]; then
        auth_args=(-H "Authorization: Bearer ${auth}")
    fi

    # ab 不支持自定义 method 头(PUT/DELETE), 用 GET 默认
    if [ "$method" != "GET" ]; then
        echo -e "${YELLOW}SKIP${NC}: ab 不支持 ${method}, 改用 curl 简单测试"
        curl -s -o /dev/null -w "  status=%{http_code} time=%{time_total}s\n" \
            -X "$method" "${API_BASE}${path}" "${auth_args[@]}"
        return
    fi

    ab -n "$REQUESTS" -c "$CONCURRENCY" -k \
        "${auth_args[@]}" \
        "${API_BASE}${path}" > "$report_file" 2>&1 || true

    # 解析结果
    local rps p99 fail
    rps=$(grep "Requests per second" "$report_file" | awk '{print $4}')
    p99=$(grep "99%" "$report_file" | awk '{print $2}')
    fail=$(grep "Failed requests" "$report_file" | awk '{print $3}')

    # p99 单位是 ms
    local p99_ms="${p99}"
    echo -e "  ${GREEN}QPS${NC}=${rps:-N/A} | ${GREEN}p99${NC}=${p99_ms:-N/A}ms | fail=${fail:-0}"
    echo "  Report: $report_file"

    # 判定
    if [ -n "$p99_ms" ]; then
        local exceed=$(python3 -c "print(int(float('$p99_ms') > 200))")
        if [ "$exceed" = "1" ]; then
            echo -e "  ${RED}FAIL${NC}: p99 ${p99_ms}ms > 200ms 目标"
        fi
    fi
}

run_wrk() {
    local name="$1"
    local path="$2"
    local method="$3"
    local auth="$4"
    local report_file="${REPORT_DIR}/${name}-$(date +%Y%m%d-%H%M%S).txt"

    echo ""
    echo "--- ${name} (${method} ${path}) ---"

    local auth_args=()
    if [ -n "$auth" ]; then
        auth_args=(-H "Authorization: Bearer ${auth}")
    fi

    wrk -t10 -c"$CONCURRENCY" -d30s --latency \
        "${auth_args[@]}" \
        "${API_BASE}${path}" > "$report_file" 2>&1 || true

    cat "$report_file"
}

# 主流程
echo "============================================================"
echo "  微课平台压测 (W31 治理)"
echo "============================================================"
echo "API_BASE=${API_BASE}"
echo "TOOL=${TOOL}"
echo "CONCURRENCY=${CONCURRENCY}"
echo "REQUESTS=${REQUESTS}"
echo "Report Dir: ${REPORT_DIR}"

if [ "$TOOL" = "wrk" ]; then
    RUN_CMD=run_wrk
else
    RUN_CMD=run_ab
fi

# 执行
target="${1:-all}"
if [ "$target" = "all" ]; then
    for k in "${!ENDPOINTS[@]}"; do
        IFS='|' read -r path method auth <<< "${ENDPOINTS[$k]}"
        $RUN_CMD "$k" "$path" "$method" "$auth" "$AUTH_TOKEN"
    done
else
    IFS='|' read -r path method auth <<< "${ENDPOINTS[$target]}"
    $RUN_CMD "$target" "$path" "$method" "$auth" "$AUTH_TOKEN"
fi

echo ""
echo "============================================================"
echo "  报告已保存到: ${REPORT_DIR}/"
echo "============================================================"