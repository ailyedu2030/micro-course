#!/bin/bash
# verify-secrets.sh · 部署前必填密钥检查 (R5 新增)
# ----------------------------------------------------------------------------
# 背景: alertmanager.yml 7 处 CHANGE_ME / hooks.slack.com/services/CHANGE_ME 等占位符
# 若直接部署会静默失败 — Slack/PagerDuty 告警不会触发，运维盲区。
# application.yml 的 3 个 secret (REDIS_PASSWORD / JWT_SECRET / PAYMENT_CALLBACK_SECRET)
# 也用 CHANGE_ME_IN_PRODUCTION 占位符防止空值静默通过。
#
# 用途:
#   bash scripts/verify-secrets.sh                       # 默认检查 (advisory)
#   bash scripts/verify-secrets.sh --strict              # CI 用，任意 CHANGE_ME 返 1
#   bash scripts/verify-secrets.sh --report=json          # 输出 JSON 给 CI
#
# 设计:
#   1. 默认模式: 列出所有 CHANGE_ME 占位符位置 + 严重度，但 exit 0
#      （开发环境可能故意用占位符测试）
#   2. --strict 模式: 任何 CHANGE_ME 返 exit 1，CI 部署门禁用
#   3. 报告: 列文件:行号 + 替换建议 (env var / 部署脚本替换)
# ----------------------------------------------------------------------------

set -e

ROOT="${ROOT:-$(cd "$(dirname "$0")/.." 2>/dev/null && pwd)}"
[ ! -d "$ROOT/micro-course-api" ] && ROOT="$(cd "$(dirname "$0")/../.." 2>/dev/null && pwd)"

STRICT=false
REPORT="text"
for arg in "$@"; do
  case $arg in
    --strict) STRICT=true ;;
    --report=json) REPORT="json" ;;
    *) ;;
  esac
done

# 待扫描文件
TARGETS=(
  "monitoring/alertmanager/alertmanager.yml"
  "monitoring/prometheus/alerts.yml"
  "monitoring/grafana/dashboards/"
  "micro-course-api/src/main/resources/application.yml"
  "micro-course-api/src/main/resources/application-prod.yml"
  "docker-compose.yml"
  "docker-compose.prod.yml"
)

findings=()
findings_count=0

scan_file() {
  local file="$1"
  [ ! -f "$file" ] && return 0
  while IFS=: read -r line_num line_text; do
    if echo "$line_text" | grep -qE "CHANGE_ME|hooks\.slack\.com/services/[A-Z]|<placeholder>|your-(webhook|api-key|password)|TODO.*secret|REPLACE_ME"; then
      findings+=("{\"file\":\"$file\",\"line\":$line_num,\"text\":$(echo "$line_text" | sed 's/"/\\"/g' | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().rstrip()))")}")
      findings_count=$((findings_count + 1))
    fi
  done < <(grep -nE "CHANGE_ME|hooks\.slack\.com/services/[A-Z]|<placeholder>|your-(webhook|api-key|password)|TODO.*secret|REPLACE_ME" "$file" 2>/dev/null || true)
}

for t in "${TARGETS[@]}"; do
  if [ -d "$ROOT/$t" ]; then
    while IFS= read -r f; do
      [ -f "$f" ] && scan_file "$f"
    done < <(find "$ROOT/$t" -type f -name "*.yml" -o -name "*.yaml" 2>/dev/null)
  else
    scan_file "$ROOT/$t"
  fi
done

if [ "$REPORT" = "json" ]; then
  printf '{"findings_count":%d,"strict":%s,"findings":[%s]}\n' \
    "$findings_count" \
    "$STRICT" \
    "$(IFS=, ; echo "${findings[*]}")"
  if [ "$STRICT" = "true" ] && [ "$findings_count" -gt 0 ]; then
    exit 1
  fi
  exit 0
fi

# 文本报告
echo "=============================================="
echo "  微课平台 · 部署密钥占位符检查 (R5)"
echo "=============================================="
if [ "$findings_count" -eq 0 ]; then
  echo -e "  \033[32m✅ 未发现 CHANGE_ME / 占位符\033[0m"
  echo "=============================================="
  exit 0
fi

echo -e "  \033[33m⚠️  发现 $findings_count 处占位符待替换：\033[0m"
echo ""
for f in "${findings[@]}"; do
  file=$(echo "$f" | python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d['file'])")
  line=$(echo "$f" | python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d['line'])")
  text=$(echo "$f" | python3 -c "import json,sys; d=json.loads(sys.stdin.read()); print(d['text'])")
  echo -e "  \033[33m•\033[0m $file:\033[1m$line\033[0m"
  echo "    $text"
done
echo ""
echo "  修复方式:"
echo "  1. 部署时用环境变量覆盖: SLACK_WEBHOOK_URL=... bash deploy.sh"
echo "  2. 或更新 alertmanager.yml 把 CHANGE_ME 改为实际值"
echo "  3. CI 部署门禁用: bash scripts/verify-secrets.sh --strict"
echo "=============================================="

if [ "$STRICT" = "true" ]; then
  echo -e "  \033[31m❌ --strict 模式: 阻断部署\033[0m"
  exit 1
fi
echo -e "  \033[32m✅ advisory 通过 (开发环境可继续)\033[0m"
exit 0
