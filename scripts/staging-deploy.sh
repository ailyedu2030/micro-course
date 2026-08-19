#!/usr/bin/env bash
# =============================================================================
# staging-deploy.sh
# 用途: 在独立 Linux 主机上拉起微课平台 staging 栈。
#
# 用法:
#   bash scripts/staging-deploy.sh
#   bash scripts/staging-deploy.sh --env-file .env.staging
#   bash scripts/staging-deploy.sh --env-file .env.staging --no-build
#   bash scripts/staging-deploy.sh --env-file .env.staging --use-local-artifacts
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE=".env.staging"
NO_BUILD=false
USE_LOCAL_ARTIFACTS=false

usage() {
  cat <<'EOF'
用法:
  bash scripts/staging-deploy.sh [--env-file <path>] [--no-build] [--use-local-artifacts]

参数:
  --env-file <path>  指定 staging 环境变量文件，默认 .env.staging
  --no-build         跳过镜像重建，仅执行 compose up -d
  --use-local-artifacts
                     直接使用本地已构建的 jar 和 dist 启动 staging 容器，跳过 Docker build
  -h, --help         显示帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    --no-build)
      NO_BUILD=true
      shift
      ;;
    --use-local-artifacts)
      USE_LOCAL_ARTIFACTS=true
      shift
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

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "❌ 缺少命令: $1" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd curl
require_cmd hostname

if [[ ! -f "$ENV_FILE" ]]; then
  echo "❌ 未找到 $ENV_FILE" >&2
  echo "   可先执行: cp .env.staging.example $ENV_FILE" >&2
  exit 1
fi

HOSTNAME_NOW="$(hostname)"
if [[ "$HOSTNAME_NOW" == *"ubuntu-proliant-dl388-gen10"* ]]; then
  echo "❌ 当前主机识别为生产机: $HOSTNAME_NOW" >&2
  echo "   staging 脚本禁止在生产机执行。" >&2
  exit 1
fi

if command -v tailscale >/dev/null 2>&1; then
  if tailscale ip -4 2>/dev/null | grep -qx '100.74.122.13'; then
    echo "❌ 当前节点 Tailscale IP 为生产机 100.74.122.13，禁止执行 staging 部署。" >&2
    exit 1
  fi
fi

set -a
source "$ENV_FILE"
set +a

BUILD_ARGS=()
if [[ "$NO_BUILD" == false ]]; then
  BUILD_ARGS+=(--build)
fi

COMPOSE_ARGS=(
  --env-file "$ENV_FILE"
  -f docker-compose.yml
  -f docker-compose.staging.yml
)

echo "==> 使用环境文件: $ENV_FILE"
echo "==> API 端口: ${STAGING_API_PORT:-18081}"
echo "==> Web 端口: ${STAGING_WEB_PORT:-18080}"
echo "==> DB 端口: ${STAGING_DB_PORT:-15432}"
echo "==> Redis 端口: ${STAGING_REDIS_PORT:-16379}"

deploy_with_local_artifacts() {
  local network="microcourse-staging-net"
  local db_container="microcourse-staging-pg"
  local redis_container="microcourse-staging-redis"
  local api_container="micro-course-micro-course-api-1"
  local admin_container="micro-course-micro-course-admin-1"
  local api_jar="$ROOT/micro-course-api/target/micro-course-api-1.0.0.jar"
  local admin_dist="$ROOT/micro-course-admin/dist"
  local admin_nginx_conf="$ROOT/micro-course-admin/nginx.conf"

  if [[ ! -f "$api_jar" ]]; then
    echo "❌ 未找到后端 jar: $api_jar" >&2
    exit 1
  fi
  if [[ ! -f "$admin_dist/index.html" ]]; then
    echo "❌ 未找到前端 dist: $admin_dist/index.html" >&2
    exit 1
  fi

  docker network create "$network" >/dev/null 2>&1 || true

  docker rm -f "$db_container" "$redis_container" "$api_container" "$admin_container" >/dev/null 2>&1 || true

  docker run -d --name "$db_container" \
    --network "$network" \
    -e POSTGRES_DB=micro_course \
    -e POSTGRES_USER="$DB_USERNAME" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -p "${STAGING_DB_PORT:-15432}:5432" \
    postgres:17-alpine >/dev/null

  docker run -d --name "$redis_container" \
    --network "$network" \
    -e REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
    -p "${STAGING_REDIS_PORT:-16379}:6379" \
    redis:7-alpine sh -c 'if [ -n "$REDIS_PASSWORD" ]; then exec redis-server --requirepass "$REDIS_PASSWORD"; else exec redis-server; fi' >/dev/null

  docker run -d --name "$api_container" \
    --network "$network" \
    -p "${STAGING_API_PORT:-18081}:8080" \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e DB_URL="jdbc:postgresql://${db_container}:5432/micro_course" \
    -e DB_USERNAME="$DB_USERNAME" \
    -e DB_PASSWORD="$DB_PASSWORD" \
    -e REDIS_HOST="$redis_container" \
    -e REDIS_PORT=6379 \
    -e REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
    -e JWT_SECRET="$JWT_SECRET" \
    -e JWT_EXPIRATION="${JWT_EXPIRATION:-7200000}" \
    -e JWT_REFRESH_EXPIRATION="${JWT_REFRESH_EXPIRATION:-604800000}" \
    -e VIDEO_SIGN_SECRET="$VIDEO_SIGN_SECRET" \
    -e APP_SECURITY_FIELD_ENCRYPTION_KEY="${APP_SECURITY_FIELD_ENCRYPTION_KEY:-}" \
    -e APP_SECURITY_FIELD_ENCRYPTION_SALT="${APP_SECURITY_FIELD_ENCRYPTION_SALT:-}" \
    -e CORS_ALLOWED_ORIGINS="${STAGING_CORS_ALLOWED_ORIGINS:-http://localhost:18080,http://127.0.0.1:18080}" \
    -e DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}" \
    -e MINIMAX_API_KEY="${MINIMAX_API_KEY:-}" \
    -e PROD_ALLOW_MOCK_PAYMENT="${PROD_ALLOW_MOCK_PAYMENT:-true}" \
    -v "$api_jar:/app/app.jar:ro" \
    eclipse-temurin:17-jre sh -c 'mkdir -p /app/uploads /data/slides /data/uploads/tmp && java -jar /app/app.jar' >/dev/null

  docker run -d --name "$admin_container" \
    --network "$network" \
    -p "${STAGING_WEB_PORT:-18080}:80" \
    -v "$admin_dist:/usr/share/nginx/html:ro" \
    -v "$admin_nginx_conf:/etc/nginx/conf.d/default.conf:ro" \
    nginx:1.27-alpine >/dev/null
}

if [[ "$USE_LOCAL_ARTIFACTS" == true ]]; then
  echo "==> 使用本地已构建产物启动 staging 容器"
  deploy_with_local_artifacts
else
  docker compose "${COMPOSE_ARGS[@]}" up -d "${BUILD_ARGS[@]}" postgres redis micro-course-api micro-course-admin
fi

echo "==> 等待 API 健康检查..."
API_HEALTH_URL="http://127.0.0.1:${STAGING_API_PORT:-18081}/actuator/health"
for _ in $(seq 1 60); do
  if curl -fsS "$API_HEALTH_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS "$API_HEALTH_URL" >/dev/null

echo "==> 等待前端首页可达..."
WEB_HEALTH_URL="http://127.0.0.1:${STAGING_WEB_PORT:-18080}/"
for _ in $(seq 1 30); do
  if curl -fsS "$WEB_HEALTH_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS "$WEB_HEALTH_URL" >/dev/null

echo "✅ staging 栈已启动"
echo "   Frontend: $WEB_HEALTH_URL"
echo "   API:      $API_HEALTH_URL"
echo "   下一步:   bash scripts/staging-verify.sh --env-file $ENV_FILE"
