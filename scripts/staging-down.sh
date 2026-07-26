#!/usr/bin/env bash
# =============================================================================
# staging-down.sh
# 用途: 停止并清理 staging 容器（compose 模式或 local-artifacts 模式）。
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE=".env.staging"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "用法: bash scripts/staging-down.sh [--env-file <path>]"
      exit 0
      ;;
    *)
      echo "❌ 未知参数: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -f "$ENV_FILE" ]]; then
  docker compose --env-file "$ENV_FILE" -f docker-compose.yml -f docker-compose.staging.yml down >/dev/null 2>&1 || true
fi

docker rm -f \
  microcourse-staging-pg \
  microcourse-staging-redis \
  micro-course-micro-course-api-1 \
  micro-course-micro-course-admin-1 >/dev/null 2>&1 || true

docker network rm microcourse-staging-net >/dev/null 2>&1 || true

echo "✅ staging 容器已清理"
