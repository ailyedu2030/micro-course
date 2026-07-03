#!/bin/bash
# ==============================================================================
# scripts/demo-deploy.sh — 微课平台 Demo 环境一键启动
# ==============================================================================
# 用途: 快速为学校/客户搭建演示环境
# 用法: bash scripts/demo-deploy.sh [--seed] [--open]
# 选项:
#   --seed    导入演示种子数据（课程/用户/微专业）
#   --open    启动后自动打开浏览器
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $1"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
err()   { echo -e "${RED}[ERROR]${NC} $1"; }

SEED=false
OPEN=false
for arg in "$@"; do
  case "$arg" in --seed) SEED=true ;; --open) OPEN=true ;; *) warn "忽略未知参数: $arg" ;; esac
done

echo ""
echo "╔════════════════════════════════════════════════╗"
echo "║     微课管理平台 · Demo 环境启动              ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

# 检查前置依赖
check_dep() {
  if ! command -v "$1" &>/dev/null; then
    err "$1 未安装。请先安装: $2"
    exit 1
  fi
}

check_dep "docker" "https://docs.docker.com/engine/install/"
check_dep "docker" "docker compose (已内置于 docker)"

# 检查 .env
if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    info "未找到 .env，从 .env.example 复制..."
    cp .env.example .env
    warn "请编辑 .env 填入 DB_PASSWORD / JWT_SECRET / VIDEO_SIGN_SECRET"
    warn "Demo 环境可使用默认值:"
    echo "  DB_PASSWORD=demo123"
    echo "  JWT_SECRET=DemoJwtSecretKey2024MinLength"
    echo "  VIDEO_SIGN_SECRET=DemoVideoSignSecretKey32Chars!!"
    echo ""
    read -rp "按 Enter 继续使用默认值（或先编辑 .env）..." _
    # 填入 demo 默认值
    if grep -q "DB_PASSWORD=your_password_here" .env 2>/dev/null; then
      sed -i '' 's/DB_PASSWORD=your_password_here/DB_PASSWORD=demo123/' .env
    fi
    if grep -q "JWT_SECRET=your_jwt_secret_here" .env 2>/dev/null; then
      sed -i '' 's/JWT_SECRET=your_jwt_secret_here/JWT_SECRET=DemoJwtSecretKey2024MinLength/' .env
    fi
    if grep -q "VIDEO_SIGN_SECRET=your_video_sign_secret_here" .env 2>/dev/null; then
      sed -i '' 's/VIDEO_SIGN_SECRET=your_video_sign_secret_here/VIDEO_SIGN_SECRET=DemoVideoSignSecretKey32Chars!!/' .env
    fi
    ok ".env 已创建"
  else
    err "缺少 .env 和 .env.example 文件"
    exit 1
  fi
fi

# 启动 Docker 服务
info "启动 PostgreSQL + Redis..."
docker compose up -d postgres redis 2>/dev/null || docker compose up -d
ok "数据库服务已启动"

# 等待 PostgreSQL 就绪
info "等待 PostgreSQL 就绪..."
for i in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U postgres &>/dev/null; then
    ok "PostgreSQL 就绪"
    break
  fi
  if [ "$i" -eq 30 ]; then
    err "PostgreSQL 未能在 30 秒内启动"
    exit 1
  fi
  sleep 1
done

# 运行 Flyway 迁移
info "执行数据库迁移 (Flyway)..."
cd micro-course-api
if [ -f mvnw ]; then
  ./mvnw flyway:migrate -q -Dflyway.url="jdbc:postgresql://localhost:5432/micro_course" \
    -Dflyway.user=postgres -Dflyway.password=demo123 2>/dev/null || true
fi
# 尝试 Maven
mvn flyway:migrate -q -Dflyway.url="jdbc:postgresql://localhost:5432/micro_course" \
  -Dflyway.user=postgres -Dflyway.password=demo123 2>/dev/null || \
  warn "Flyway 迁移跳过（数据库可能已是最新）"
cd "$ROOT_DIR"
ok "数据库迁移完成"

# 导入种子数据
if [ "$SEED" = true ] && [ -f test_data.sql ]; then
  info "导入演示种子数据..."
  docker compose exec -T postgres psql -U postgres -d micro_course < test_data.sql 2>/dev/null && \
    ok "种子数据导入完成" || warn "种子数据导入失败（可能已存在）"
fi

# 构建并启动后端
info "启动后端服务 (Spring Boot)..."
cd micro-course-api
mvn spring-boot:run -q -Dspring-boot.run.profiles=demo &
BACKEND_PID=$!
cd "$ROOT_DIR"

# 等待后端就绪
info "等待后端服务就绪..."
for i in $(seq 1 60); do
  if curl -s http://localhost:8080/api/health 2>/dev/null | grep -q "ok\|UP"; then
    ok "后端服务就绪 (http://localhost:8080)"
    break
  fi
  if [ "$i" -eq 60 ]; then
    warn "后端启动超时，请检查日志"
  fi
  sleep 2
done

# 构建并启动前端
info "启动前端服务 (Vite)..."
cd micro-course-admin
npm install --silent 2>/dev/null
npm run dev -- --host 0.0.0.0 &
FRONTEND_PID=$!
cd "$ROOT_DIR"

sleep 3

echo ""
echo "╔════════════════════════════════════════════════╗"
echo "║  Demo 环境已就绪                               ║"
echo "╠════════════════════════════════════════════════╣"
echo "║  前端:  http://localhost:5173                  ║"
echo "║  后端:  http://localhost:8080                  ║"
echo "║  数据库: postgresql://localhost:5432/micro_course ║"
echo "║  Redis: localhost:6379                        ║"
echo "╠════════════════════════════════════════════════╣"
echo "║  Demo 账户:                                   ║"
echo "║  管理员:  admin    / admin123                 ║"
echo "║  教师:    teacher  / teacher123               ║"
echo "║  学生:    student  / student123               ║"
echo "╚════════════════════════════════════════════════╝"
echo ""

if [ "$OPEN" = true ]; then
  open http://localhost:5173
fi

# 等待进程
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" SIGINT SIGTERM
wait
