#!/usr/bin/env bash
# =============================================================================
# staging-verify.sh
# 用途: 对 staging 栈做最小可用验证，供人工执行后快速确认环境是否可用。
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE=".env.staging"

usage() {
  cat <<'EOF'
用法:
  bash scripts/staging-verify.sh [--env-file <path>]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "❌ 未知参数: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "❌ 未找到 $ENV_FILE" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

require_ok() {
  local name="$1"
  local url="$2"
  echo "==> 检查 $name: $url"
  curl -fsS "$url" >/dev/null
  echo "✅ $name 正常"
}

require_status() {
  local name="$1"
  local url="$2"
  local method="$3"
  local expected="$4"
  local actual
  echo "==> 检查 $name: $method $url"
  actual="$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url")"
  if [[ "$actual" != "$expected" ]]; then
    echo "❌ $name 返回状态码 $actual，预期 $expected" >&2
    exit 1
  fi
  echo "✅ $name 正常"
}

require_json_field() {
  local name="$1"
  local url="$2"
  local expect="$3"
  echo "==> 检查 $name: $url"
  if ! curl -fsS "$url" | grep -q "$expect"; then
    echo "❌ $name 未返回预期内容: $expect" >&2
    exit 1
  fi
  echo "✅ $name 正常"
}

require_json_field "API 健康检查" "http://127.0.0.1:${STAGING_API_PORT:-18081}/actuator/health" '"status":"UP"'
require_ok "前端首页" "http://127.0.0.1:${STAGING_WEB_PORT:-18080}/"
require_status "登录接口可达" "http://127.0.0.1:${STAGING_API_PORT:-18081}/api/auth/login" "OPTIONS" "200"

echo "✅ staging 最小验证通过"
