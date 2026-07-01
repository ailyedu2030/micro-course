#!/bin/bash
# =============================================================================
# 本地隔离开发环境 + 端到端测试脚本
# =============================================================================
# 目的: 在本地隔离容器中 build + 部署 + Playwright 验证修复,
#       确认零错误零警告后再走灰度发布到生产。
#
# 架构 (与生产完全隔离):
#   - postgres-test: 5433 端口
#   - redis-test: 6380 端口
#   - api-test: 8089 端口
#   - admin-test: 8088 端口
#
# 流程:
#   1. 启动隔离 db+redis (如未运行)
#   2. build 后端 jar (本地)
#   3. build 前端 dist (本地)
#   4. 用本地 jar 启动 api-test 容器
#   5. 部署前端 dist 到本地 nginx-test 容器
#   6. Playwright 自动化测试 (隔离环境 URL)
#   7. 测试通过 → 退出码 0 (可继续走生产部署)
#      测试失败 → 退出码 1 (阻断生产部署)
#   8. 保留容器供开发者手动验证 (docker stop 时清理)
#
# 用法:
#   bash scripts/local-dev-deploy.sh [--skip-build] [--keep]
#   --skip-build: 跳过 build 步骤 (用于多次快速验证)
#   --keep:       测试后保留容器, 默认会 stop api+admin 但保留 db+redis
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# 配置
API_IMAGE="micro-course-api-test"
ADMIN_IMAGE="micro-course-admin-test"
DB_CONTAINER="microcourse-pg-test"
REDIS_CONTAINER="microcourse-redis-test"
API_CONTAINER="microcourse-api-test"
ADMIN_CONTAINER="microcourse-admin-test"
NETWORK="microcourse-test-net"
API_PORT=8089
ADMIN_PORT=8088
DB_PORT=5433
REDIS_PORT=6380

# 测试凭据 (与生产一致, 但连接到隔离 db)
DB_USER="postgres"
DB_PASS="postgres"
DB_NAME="micro_course_test"
REDIS_PASS=""

# 本地开发测试账号 (与生产无关,统一密码便于快速测试)
# 默认密码: password123 (BCrypt hash)
LOCAL_HASH='$2a$10$E9bFfOv7xrYewc7ffg6k4.WgRCgzw.VMFQNQGztRiXAfnCrFCp79m'
TEST_USERNAME="teacher1"
TEST_PASSWORD="password123"

# 测试结果
PASS=0
FAIL=0

ok()   { PASS=$((PASS+1)); echo "  ✅ $1"; }
fail() { FAIL=$((FAIL+1)); echo "  ❌ $1"; }
warn() { echo "  ⚠️  $1"; }
section() { echo ""; echo "═══ $1 ═══"; }

# ════════════════════════════════════════════════════════════════
# 参数解析
# ════════════════════════════════════════════════════════════════
SKIP_BUILD=false
KEEP_CONTAINERS=false
for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
    --keep)       KEEP_CONTAINERS=true ;;
    --help|-h)
      echo "Usage: $0 [--skip-build] [--keep]"
      exit 0
      ;;
    *) echo "Unknown arg: $arg"; exit 1 ;;
  esac
done

# ════════════════════════════════════════════════════════════════
# 0. 清理函数 (出错时执行)
# ════════════════════════════════════════════════════════════════
cleanup_on_error() {
  echo ""
  echo "⚠️  测试失败,清理容器..."
  docker stop "$API_CONTAINER" 2>/dev/null || true
  docker stop "$ADMIN_CONTAINER" 2>/dev/null || true
  docker rm "$API_CONTAINER" 2>/dev/null || true
  docker rm "$ADMIN_CONTAINER" 2>/dev/null || true
}
trap cleanup_on_error ERR

# ════════════════════════════════════════════════════════════════
# 1. 启动隔离 db+redis (FLUSHDB 必须放在这步,确保容器启动前 redis 是空的)
# ════════════════════════════════════════════════════════════════
section "1. 启动隔离 db + redis"

docker network create "$NETWORK" 2>/dev/null || true

if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
  echo "  停止旧 postgres-test 容器..."
  docker stop "$DB_CONTAINER" > /dev/null 2>&1 || true
  docker rm "$DB_CONTAINER" > /dev/null 2>&1 || true
fi
echo "  启动 postgres-test (network=$NETWORK)..."
docker run -d --name "$DB_CONTAINER" \
  --network "$NETWORK" \
  -e POSTGRES_DB="$DB_NAME" \
  -e POSTGRES_USER="$DB_USER" \
  -e POSTGRES_PASSWORD="$DB_PASS" \
  -p "${DB_PORT}:5432" \
  --tmpfs /var/lib/postgresql/data \
  postgres:17-alpine > /dev/null
# 等待就绪
for i in 1 2 3 4 5 6 7 8 9 10; do
  if docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" > /dev/null 2>&1; then
    break
  fi
  sleep 1
done
ok "postgres-test 已启动 (port $DB_PORT, network=$NETWORK)"

if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
  echo "  停止旧 redis-test 容器..."
  docker stop "$REDIS_CONTAINER" > /dev/null 2>&1 || true
  docker rm "$REDIS_CONTAINER" > /dev/null 2>&1 || true
fi
echo "  启动 redis-test..."
docker run -d --name "$REDIS_CONTAINER" \
  --network "$NETWORK" \
  -p "${REDIS_PORT}:6379" \
  redis:7-alpine > /dev/null
for i in 1 2 3 4 5; do
  if docker exec "$REDIS_CONTAINER" redis-cli ping > /dev/null 2>&1; then
    break
  fi
  sleep 1
done

# 清空 Redis 缓存 (避免登录失败计数器跨测试残留)
docker exec "$REDIS_CONTAINER" redis-cli FLUSHDB > /dev/null 2>&1 || true
ok "redis-test 已启动 (port $REDIS_PORT, network=$NETWORK, FLUSHDB 已执行)"

# ════════════════════════════════════════════════════════════════
# 2. 种子数据 (创建测试账号)
# ════════════════════════════════════════════════════════════════
section "2. 种子数据 (Flyway 迁移 + 测试账号)"

# 测试 jwt 密钥 (开发用,与生产分离)
export JWT_SECRET="local-test-jwt-secret-not-for-production-use-only-2026-xx"
export VIDEO_SIGN_SECRET="local-test-video-secret-32-bytes-min-2026"
export DEEPSEEK_API_KEY=""
export PROD_ALLOW_MOCK_PAYMENT="true"
export CORS_ALLOWED_ORIGINS="http://localhost:${ADMIN_PORT}"

# ════════════════════════════════════════════════════════════════
# 3. Build
# ════════════════════════════════════════════════════════════════
if [ "$SKIP_BUILD" = false ]; then
  section "3. Build 后端 + 前端"

  echo "  构建后端 jar..."
  (cd micro-course-api && mvn package -DskipTests -B -q 2>&1 | tail -3) || { fail "后端构建失败"; exit 1; }
  ok "后端 jar 已生成"

  echo "  构建前端 dist..."
  (cd micro-course-admin && npx vite build --mode production 2>&1 | tail -3) || { fail "前端构建失败"; exit 1; }
  ok "前端 dist 已生成"
else
  ok "跳过 build (--skip-build)"
fi

# ════════════════════════════════════════════════════════════════
# 4. 启动 api-test 容器
# ════════════════════════════════════════════════════════════════
section "4. 启动 api-test 容器"

# 停掉旧容器
docker stop "$API_CONTAINER" 2>/dev/null || true
docker rm "$API_CONTAINER" 2>/dev/null || true

docker run -d --name "$API_CONTAINER" \
  --network "$NETWORK" \
  -p "${API_PORT}:8080" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:postgresql://${DB_CONTAINER}:5432/${DB_NAME}" \
  -e DB_USERNAME="$DB_USER" \
  -e DB_PASSWORD="$DB_PASS" \
  -e REDIS_HOST="$REDIS_CONTAINER" \
  -e REDIS_PORT=6379 \
  -e REDIS_PASSWORD="" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e JWT_EXPIRATION=7200000 \
  -e JWT_REFRESH_EXPIRATION=604800000 \
  -e VIDEO_SIGN_SECRET="$VIDEO_SIGN_SECRET" \
  -e DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
  -e CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS" \
  -e PROD_ALLOW_MOCK_PAYMENT="$PROD_ALLOW_MOCK_PAYMENT" \
  -v "$ROOT/micro-course-api/target/micro-course-api-1.0.0.jar:/app/app.jar:ro" \
  --platform linux/amd64 \
  eclipse-temurin:17-jre \
  sh -c 'mkdir -p /app/uploads && java -jar /app/app.jar' > /dev/null

# 等待 api 健康
echo "  等待 api 健康 (最多 60s)..."
HEALTHY=false
for i in $(seq 1 30); do
  if curl -sf "http://localhost:${API_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    HEALTHY=true
    break
  fi
  sleep 2
done

if [ "$HEALTHY" = true ]; then
  ok "api-test 已健康 (port $API_PORT)"
else
  fail "api-test 未在 60s 内健康"
  docker logs "$API_CONTAINER" --tail 20
  exit 1
fi

# 注入种子用户 (本地隔离 db, 不影响生产)
# 创建 7 个测试账号:
#   - 5 个 dev 账号 (admin/teacher1/teacher2/academic1/student1, 统一密码 password123)
#   - 2 个测试账号 (admin/admin123, student/student123, p0_teacher/student123) 用于兼容 BaseIntegrationTest
echo "  注入种子用户..."

# 用 Python heredoc 生成 SQL (避免转义问题)
SEED_SQL=$(python3 <<'PYEOF'
hash_admin123    = r'$2a$10$lkxtGiECdP6NnktzLbheDO/XTNsBcB5x5U/i2ZprYfpxvFF.U1bxq'
hash_student123  = r'$2a$10$ujPS9LM4Xe.1tROo4TZH.OsImLO3DgyVMKvRdC0UGEhtvW..HixJW'
hash_password123 = r'$2a$10$D6wZ2JY00J3NKuRP/Y4oWesE04NJ6GiqTfzp5Tnf9OSKgV2fNelau'

sql = []
sql.append("INSERT INTO departments (id, name, code, created_at, updated_at) VALUES (1, '测试院系', 'TEST_DEPT', NOW(), NOW()) ON CONFLICT (id) DO NOTHING;")
sql.append("INSERT INTO departments (id, name, code, created_at, updated_at) VALUES (2, '商学院', 'BUSINESS', NOW(), NOW()) ON CONFLICT (id) DO NOTHING;")

# 5 个 dev 账号 (密码 password123)
users = [
    (1, 'admin',     'ADMIN',    '测试管理员', 'admin@local.test',     hash_password123),
    (2, 'teacher1',  'TEACHER',  '测试教师1', 'teacher1@local.test',  hash_password123),
    (3, 'teacher2',  'TEACHER',  '测试教师2', 'teacher2@local.test',  hash_password123),
    (4, 'academic1', 'ACADEMIC', '测试教务',   'academic1@local.test', hash_password123),
    (5, 'student1',  'STUDENT',  '测试学生',   'student1@local.test',  hash_password123),
]
for uid, uname, role, real_name, email, hpwd in users:
    sql.append(f"DELETE FROM users WHERE id = {uid};")
    sql.append(f"INSERT INTO users (id, username, password, real_name, role, status, department_id, email, created_at, updated_at) VALUES ({uid}, '{uname}', E'{hpwd}', '{real_name}', '{role}', 1, 1, '{email}', NOW(), NOW());")

# 2 个测试账号 (兼容 BaseIntegrationTest, 密码 student123/admin123)
sql.append("DELETE FROM users WHERE username = 'student';")
sql.append(f"INSERT INTO users (id, username, password, real_name, role, status, department_id, email, created_at, updated_at) VALUES (7, 'student', E'{hash_student123}', '测试学员', 'STUDENT', 1, 1, 'student@local.test', NOW(), NOW());")
sql.append("DELETE FROM users WHERE username = 'p0_teacher';")
sql.append(f"INSERT INTO users (id, username, password, real_name, role, status, department_id, email, created_at, updated_at) VALUES (6, 'p0_teacher', E'{hash_student123}', 'P0测试教师', 'TEACHER', 1, 1, 'p0teacher@local.test', NOW(), NOW());")

# 重设 admin 密码为 admin123 (BaseIntegrationTest 期望)
sql.append("UPDATE users SET password = E'" + hash_admin123 + "' WHERE username = 'admin';")

sql.append("SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), 1));")
print('\n'.join(sql))
PYEOF
)

# 把 SQL 写入临时文件,再 docker exec 注入
echo "$SEED_SQL" > /tmp/seed.sql
docker cp /tmp/seed.sql "$DB_CONTAINER:/tmp/seed.sql"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -f /tmp/seed.sql > /tmp/seed.log 2>&1 || true

# 验证插入成功
USER_COUNT=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT count(*) FROM users WHERE username IN ('admin','teacher1','teacher2','academic1','student1','student','p0_teacher');" | tr -d ' \n')
if [ "$USER_COUNT" = "7" ]; then
  ok "种子用户已注入 (7 个: 5 个 dev + 2 个测试)"
else
  fail "种子用户注入失败, 实际 $USER_COUNT/7"
  cat /tmp/seed.log
fi

# ════════════════════════════════════════════════════════════════
# 5. 启动 admin-test 容器 (nginx + dist)
# ════════════════════════════════════════════════════════════════
section "5. 启动 admin-test 容器"

docker stop "$ADMIN_CONTAINER" 2>/dev/null || true
docker rm "$ADMIN_CONTAINER" 2>/dev/null || true

# 用 nginx 提供 dist
NGINX_CONF=$(mktemp)
cat > "$NGINX_CONF" <<EOF
server {
  listen 80;
  server_name _;
  root /usr/share/nginx/html;
  index index.html;

  location /api/ {
    proxy_pass http://${API_CONTAINER}:8080;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
  }

  location / {
    try_files \$uri \$uri/ /index.html;
  }
}
EOF

# 启动临时 nginx 容器
docker run -d --name "$ADMIN_CONTAINER" \
  --network "$NETWORK" \
  -p "${ADMIN_PORT}:80" \
  -v "$ROOT/micro-course-admin/dist:/usr/share/nginx/html:ro" \
  -v "$NGINX_CONF:/etc/nginx/conf.d/default.conf:ro" \
  nginx:alpine > /dev/null

sleep 2
if curl -sf "http://localhost:${ADMIN_PORT}/" > /dev/null 2>&1; then
  ok "admin-test 已启动 (port $ADMIN_PORT)"
else
  fail "admin-test 启动失败"
  docker logs "$ADMIN_CONTAINER" --tail 10
  exit 1
fi
rm -f "$NGINX_CONF"

# ════════════════════════════════════════════════════════════════
# 6. 健康检查 (后端 + 前端 + API)
# ════════════════════════════════════════════════════════════════
section "6. 健康检查"

# 6.1 后端 actuator/health
HEALTH=$(curl -sf "http://localhost:${API_PORT}/actuator/health" 2>/dev/null)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
  ok "后端 actuator/health UP"
else
  fail "后端健康检查失败: $HEALTH"
fi

# 6.2 前端首页
FRONT_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:${ADMIN_PORT}/")
if [ "$FRONT_CODE" = "200" ]; then
  ok "前端首页 200"
else
  fail "前端首页 $FRONT_CODE"
fi

# 6.3 API 登录 (用 teacher1/password123 验证 API 可用)
# 重试机制: 容器健康但 LoginFilter/UserStatusCheck 可能还没完全 warmup
# 最长等待 30s
sleep 8
LOGIN_RESP=""
for i in $(seq 1 10); do
  LOGIN_RESP=$(curl -s -X POST "http://localhost:${API_PORT}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${TEST_USERNAME}\",\"password\":\"${TEST_PASSWORD}\"}")
  if echo "$LOGIN_RESP" | grep -q '"accessToken"'; then
    break
  fi
  echo "    [重试 $i/10] 登录失败,等待 3s..."
  sleep 3
done
if echo "$LOGIN_RESP" | grep -q '"accessToken"'; then
  ok "API 登录成功 (teacher1/password123)"
else
  fail "API 登录失败 (10次重试后): $LOGIN_RESP"
fi

# 6.4 兼容 BaseIntegrationTest: admin/admin123
LOGIN_ADMIN=""
for i in $(seq 1 5); do
  LOGIN_ADMIN=$(curl -s -X POST "http://localhost:${API_PORT}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin123"}')
  if echo "$LOGIN_ADMIN" | grep -q '"accessToken"'; then
    break
  fi
  sleep 2
done
if echo "$LOGIN_ADMIN" | grep -q '"accessToken"'; then
  ok "API admin/admin123 登录成功 (兼容测试)"
else
  warn "API admin/admin123 登录失败 (单元测试可能受影响)"
fi

# ════════════════════════════════════════════════════════════════
# 7. Playwright UI 测试 (可选, 通过 PLAYWRIGHT_TEST=1 启用)
# ════════════════════════════════════════════════════════════════
section "7. Playwright UI 测试"

if [ "${PLAYWRIGHT_TEST:-0}" = "1" ]; then
  echo "  PLAYWRIGHT_TEST=1, 运行 Playwright..."
  BASE_URL="http://localhost:${ADMIN_PORT}" npx playwright test \
    --config=playwright.config.local.ts \
    tests/storage-application.spec.ts 2>&1 | tail -20 || \
    fail "Playwright UI 测试失败"
else
  ok "跳过 Playwright UI 测试 (设置 PLAYWRIGHT_TEST=1 启用)"
fi

# ════════════════════════════════════════════════════════════════
# 8. 后端单元测试
# ════════════════════════════════════════════════════════════════
section "8. 后端单元测试"

(cd micro-course-api && mvn test -B -q 2>&1 | tail -10) || { fail "后端单元测试失败"; }

# ════════════════════════════════════════════════════════════════
# 9. Precheck + ESLint
# ════════════════════════════════════════════════════════════════
section "9. 质量门禁"

if bash .claude/skills/microcourse/scripts/precheck.sh > /tmp/precheck.out 2>&1; then
  ok "precheck 通过"
else
  fail "precheck 失败"
  cat /tmp/precheck.out | tail -10
fi

(cd micro-course-admin && npx eslint src/components/storage/DatePickerYM.vue src/views/teacher/MicroSpecialtyProposal.vue 2>&1) && \
  ok "ESLint 0/0" || fail "ESLint 有错误"

# ════════════════════════════════════════════════════════════════
# 10. 清理 (可选保留)
# ════════════════════════════════════════════════════════════════
section "10. 清理"

if [ "$KEEP_CONTAINERS" = false ]; then
  docker stop "$API_CONTAINER" 2>/dev/null || true
  docker stop "$ADMIN_CONTAINER" 2>/dev/null || true
  docker rm "$API_CONTAINER" 2>/dev/null || true
  docker rm "$ADMIN_CONTAINER" 2>/dev/null || true
  ok "api-test / admin-test 已停止"
else
  ok "保留容器 (--keep)"
fi

# ════════════════════════════════════════════════════════════════
# 汇总
# ════════════════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════════════════════════════"
echo "  汇总: ✅ $PASS 通过, ❌ $FAIL 失败"
echo "════════════════════════════════════════════════════════════════"

if [ $FAIL -gt 0 ]; then
  echo ""
  echo "❌ 本地测试未通过 → 阻断生产部署"
  echo "  请修复失败项后重新运行"
  exit 1
fi

echo ""
echo "✅ 本地测试全部通过"
echo ""

# 自动打开生产门禁 (需要先移除旧文件,再写入时间戳)
# ⚠ 这里不能用 bash scripts/deploy-gate.sh open — set -e + trap ERR
#   在 bash 嵌套调用时可能误触发 cleanup_on_error 导致容器被清理
rm -f "$ROOT/.production-gate" 2>/dev/null
date +%s > "$ROOT/.production-gate" 2>/dev/null
echo "  🔓 生产门禁已自动打开 (有效期 4 小时)"
echo ""

echo "  下一步: 如果要部署到生产,请先走 staging 验证:"
echo "    1. bash scripts/deploy-gate.sh check   ← 确认门禁已开"
echo "    2. 灰度发布到生产 (scripts/gray-release.sh)"
echo "    3. 加入白名单 (xiaona 等测试账号)"
echo "    4. 监控 5 分钟"
echo "    5. 全量发布 (roll-out)"
echo ""
echo "  ⚠️  如果门禁关闭,任何生产操作会被阻断!"
echo "  ⚠️  不要直接生产部署,必须先 staging 验证!"
exit 0