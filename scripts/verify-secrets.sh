#!/bin/bash
# verify-secrets.sh · 部署前必填密钥检查 (R5 新增)
# ----------------------------------------------------------------------------
# 背景: alertmanager.yml 6 处 CHANGE_ME / hooks.slack.com/services/CHANGE_ME 等占位符
# 若直接部署会静默失败 — Slack/PagerDuty 告警不会触发，运维盲区。
# application.yml 的 3 个 secret (REDIS_PASSWORD / JWT_SECRET / PAYMENT_CALLBACK_SECRET)
# 也用 CHANGE_ME_IN_PRODUCTION 占位符防止空值静默通过。
#
# 用途:
#   bash scripts/verify-secrets.sh                       # 默认检查 (advisory)
#   bash scripts/verify-secrets.sh --strict              # 部署门禁用，任意 CHANGE_ME 返 1
#   bash scripts/verify-secrets.sh --ci                  # CI 用，同 advisory 不阻断 (显式声明)
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
CI_MODE=false
REPORT="text"
for arg in "$@"; do
  case $arg in
    --strict) STRICT=true ;;
    --ci) CI_MODE=true ;;
    --report=json) REPORT="json" ;;
    *) ;;
  esac
done

# --ci 是显式 advisory 模式，与 "--strict" 互斥
if [ "$CI_MODE" = "true" ] && [ "$STRICT" = "true" ]; then
  echo "::error::--ci 与 --strict 互斥，不能同时使用" >&2
  exit 2
fi

# 待扫描文件
TARGETS=(
  "monitoring/alertmanager/alertmanager.yml"
  "monitoring/prometheus/alerts.yml"
  "monitoring/grafana/dashboards/"
  "micro-course-api/src/main/resources/application.yml"
  "micro-course-api/src/main/resources/application-prod.yml"
  "docker-compose.yml"
  "docker-compose.prod.yml"
  "alerts.env"
)

findings=()
findings_count=0

scan_file() {
  local file="$1"
  [ ! -f "$file" ] && return 0
  while IFS=: read -r line_num line_text; do
    # 排除规则:
    #   1. 注释行 (`#` 开头, 或 YAML 内 `# ...`) — 注释内 CHANGE_ME 是文档说明,不是配置
    #   2. Spring 占位符默认值 `${VAR_NAME:CHANGE_ME_*}` — 部署规范: 运维通过环境变量注入,不是密钥泄漏
    #      例: `password: ${REDIS_PASSWORD:CHANGE_ME_IN_PRODUCTION}` — 默认值仅作占位,部署时 env var 注入
    #   3. alertmanager 部署占位符 `CHANGE_ME_SLACK` / `CHANGE_ME_PAGERDUTY` / `CHANGE_ME_BEFORE_DEPLOY`
    #      — entrypoint.sh:30,38 自动 sed 替换 (env var 注入),docs/operations/DEPLOY_RUNBOOK.md 规范
    trimmed=$(echo "$line_text" | sed 's/^[[:space:]]*//')
    if echo "$trimmed" | grep -qE '^#'; then
      continue  # 注释行跳过
    fi
    # 仅检测"裸"CHANGE_ME (非占位符语法包裹) + 真实占位符 (alertmanager webhook 等)
    is_placeholder=$(echo "$line_text" | grep -qE '\$\{[A-Z_]+:CHANGE_ME[A-Z_]*\}' && echo 1 || echo 0)
    is_alertmanager_marker=$(echo "$line_text" | grep -qE '(CHANGE_ME_SLACK|CHANGE_ME_PAGERDUTY|CHANGE_ME_BEFORE_DEPLOY)' && echo 1 || echo 0)
    if echo "$line_text" | grep -qE 'CHANGE_ME' && [ "$is_placeholder" = "0" ] && [ "$is_alertmanager_marker" = "0" ]; then
      findings+=("{\"file\":\"$file\",\"line\":$line_num,\"text\":$(echo "$line_text" | sed 's/"/\\"/g' | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().rstrip()))")}")
      findings_count=$((findings_count + 1))
      continue
    fi
    # 第二个 regex: hooks.slack.com/services 占位符 + 通用 placeholder 关键字
    #   对 alertmanager 占位符同样排除 (entrypoint.sh 自动 sed 替换 + DEPLOY_RUNBOOK.md 规范)
    if echo "$line_text" | grep -qE "hooks\.slack\.com/services/[A-Z]|<placeholder>|your-(webhook|api-key|password)|TODO.*secret|REPLACE_ME" && [ "$is_alertmanager_marker" = "0" ]; then
      findings+=("{\"file\":\"$file\",\"line\":$line_num,\"text\":$(echo "$line_text" | sed 's/"/\\"/g' | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().rstrip()))")}")
      findings_count=$((findings_count + 1))
    fi
    # P0-2026-08-20: 不安全 dev placeholder 检测（dev-32-char-key-not-for-production-! / 0123456789abcdef 等）
    #   长度够 (32+/16+) 通过 FieldEncryptor length check, 但生产用 = P0 数据泄露
    #   检测的 dev placeholder 字面值 (替换了 L1 后 application.yml 已无, 此处永久兜底防再发)
    if echo "$line_text" | grep -qE "(dev-32-char-key-not-for-production|not-for-production|please-change-in-prod|dev-only-jwt-secret|dev-only-video-sign-secret|0123456789abcdef)" && [ "$is_alertmanager_marker" = "0" ]; then
      findings+=("{\"file\":\"$file\",\"line\":$line_num,\"text\":$(echo "$line_text" | sed 's/"/\\"/g' | python3 -c "import sys,json; print(json.dumps(sys.stdin.read().rstrip()))")}")
      findings_count=$((findings_count + 1))
    fi
  done < <(grep -nE "CHANGE_ME|hooks\.slack\.com/services/[A-Z]|<placeholder>|your-(webhook|api-key|password)|TODO.*secret|REPLACE_ME|dev-32-char-key-not-for-production|not-for-production|please-change-in-prod|dev-only-jwt-secret|dev-only-video-sign-secret|0123456789abcdef" "$file" 2>/dev/null || true)
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
echo "  4. CI PR 门禁用: bash scripts/verify-secrets.sh --ci (advisory 不阻断)"
echo "=============================================="

if [ "$STRICT" = "true" ]; then
  echo -e "  \033[31m❌ --strict 模式: 阻断部署\033[0m"
  exit 1
fi

if [ "$CI_MODE" = "true" ]; then
  echo -e "  \033[33m⚠️  --ci 模式: 发现 $findings_count 处占位符 (advisory 不阻断 PR)\033[0m"
  if [ "$findings_count" -gt 0 ]; then
    echo -e "  \033[33m  部署前必须替换: bash scripts/verify-secrets.sh --strict\033[0m"
  fi
  exit 0
fi

echo -e "  \033[32m✅ advisory 通过 (开发环境可继续)\033[0m"
exit 0
