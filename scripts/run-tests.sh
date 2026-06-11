#!/bin/bash
# 微课管理平台 · 集成测试运行脚本
# 用法: bash scripts/run-tests.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== 1. 启动测试基础设施 (PostgreSQL:5433 + Redis:6380) ==="
docker compose -f "$ROOT/docker-compose.test.yml" up -d
echo ""

echo "=== 2. 等待 PG 就绪 ==="
until docker compose -f "$ROOT/docker-compose.test.yml" exec postgres-test pg_isready -U postgres 2>/dev/null; do
  echo "  等待 PostgreSQL..."
  sleep 2
done
echo "  PostgreSQL 就绪"
echo ""

echo "=== 3. 等待 Redis 就绪 ==="
until docker compose -f "$ROOT/docker-compose.test.yml" exec redis-test redis-cli ping 2>/dev/null; do
  echo "  等待 Redis..."
  sleep 1
done
echo "  Redis 就绪"
echo ""

echo "=== 4. 运行集成测试 ==="
cd "$ROOT/micro-course-api"
mvn test -Dspring.profiles.active=test "$@" 2>&1 | tail -30

echo ""
echo "=== 5. 清理测试环境 ==="
# 可选: 注释下一行走保留容器
docker compose -f "$ROOT/docker-compose.test.yml" down

echo ""
echo "完成"
