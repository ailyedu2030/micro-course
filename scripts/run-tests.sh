#!/bin/bash
# 微课管理平台 · 集成测试运行脚本
# 用法: bash scripts/run-tests.sh [--docker|--local]
# --docker: 使用 Docker Compose (默认)
# --local:  使用本地 PostgreSQL + Redis
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="--docker"
if [ "$1" = "--local" ] || [ "$1" = "--docker" ]; then
    MODE="$1"
    shift
fi

export TEST_DB_URL="jdbc:postgresql://localhost:5432/micro_course_test"
export TEST_REDIS_HOST="localhost"
export TEST_REDIS_PORT="6379"

if [ "$MODE" = "--local" ]; then
    echo "=== 1. 验证本地服务 ==="
    pg_isready -h localhost -p 5432 2>/dev/null || { echo "ERROR: PostgreSQL not running on :5432"; exit 1; }
    redis-cli -h localhost -p 6379 ping 2>/dev/null || { echo "ERROR: Redis not running on :6379"; exit 1; }
    echo "  PostgreSQL ✅  Redis ✅"
else
    echo "=== 1. 启动测试容器 (PostgreSQL:5433 + Redis:6380) ==="
    docker compose -f "$ROOT/docker-compose.test.yml" up -d
    echo ""
    echo "=== 2. 等待服务就绪 ==="
    until docker compose -f "$ROOT/docker-compose.test.yml" exec postgres-test pg_isready -U postgres 2>/dev/null; do sleep 2; done
    echo "  PostgreSQL 就绪"
    until docker compose -f "$ROOT/docker-compose.test.yml" exec redis-test redis-cli ping 2>/dev/null; do sleep 1; done
    echo "  Redis 就绪"
    export TEST_DB_URL="jdbc:postgresql://localhost:5433/micro_course_test"
    export TEST_REDIS_PORT="6380"
fi

echo ""
echo "=== 3. 创建测试数据库 ==="
PGPASSWORD=postgres psql -h localhost -p "${TEST_DB_URL##*:}" -d "${TEST_DB_URL%/*}" -c "CREATE DATABASE micro_course_test" 2>/dev/null || true
PGPASSWORD=postgres psql -h localhost -p "${TEST_DB_URL##*:}" -d "micro_course_test" -f "$ROOT/test_data.sql" 2>&1 | tail -3

echo ""
echo "=== 4. 运行集成测试 ==="
cd "$ROOT/micro-course-api"
mvn test "$@"

echo ""
echo "=== 5. 结果 ==="
echo "  完成 (exit code: $?)"
