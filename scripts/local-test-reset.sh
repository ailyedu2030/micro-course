#!/bin/bash
# ==============================================================================
# scripts/local-test-reset.sh · 重置本地测试 DB (5433 + 6380 隔离)
# ==============================================================================
# 目的: 解决本地 mvn test 时 5433/micro_course_test 数据污染问题
#       (CI 用全新空 DB 容器, 本地没有 → 测试间相互干扰)
# 用法: bash scripts/local-test-reset.sh
# 前置: 5433 postgres + 6380 redis 已启动 (docker run ...)
# ==============================================================================
set -euo pipefail

PG_CONTAINER="${TEST_PG_CONTAINER:-micro-course-test-pg}"
REDIS_CONTAINER="${TEST_REDIS_CONTAINER:-micro-course-test-redis}"

echo "=== 1) Stop any test postgres/redis containers ==="
docker rm -f $PG_CONTAINER 2>/dev/null || true
docker rm -f $REDIS_CONTAINER 2>/dev/null || true

echo "=== 2) Start fresh postgres 5433 ==="
docker run -d --name $PG_CONTAINER -p 5433:5432 \
    -e POSTGRES_DB=micro_course_test \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=postgres \
    postgres:17-alpine >/dev/null

echo "=== 3) Start fresh redis 6380 ==="
docker run -d --name $REDIS_CONTAINER -p 6380:6379 \
    redis:7-alpine >/dev/null

echo "=== 4) Wait for ready ==="
for i in $(seq 1 30); do
    if docker exec $PG_CONTAINER pg_isready -U postgres >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
for i in $(seq 1 15); do
    if docker exec $REDIS_CONTAINER redis-cli ping >/dev/null 2>&1; then
        break
    fi
    sleep 1
done

echo "✅ Test infra ready: 5433 (postgres micro_course_test) + 6380 (redis)"
echo "👉 跑测试: cd micro-course-api && mvn test"
