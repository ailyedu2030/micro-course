#!/bin/bash
# 微课平台 · 学校服务器部署脚本
# 通过 Tailscale 连接到 ubuntu-proliant-dl388-gen10 (100.74.122.13)
# 
# 使用方法:
#   1. 在本地: scp deploy-to-school.sh ubuntu-proliant-dl388-gen10:/tmp/
#   2. 在服务器: bash /tmp/deploy-to-school.sh
#
# 或手动在服务器上执行以下步骤:
# -----------------------------------------------------------------

set -euo pipefail

APP_DIR="/opt/micro-course"
GIT_REPO="git@github.com:ailyedu2030/micro-course.git"
BACKUP_DIR="/opt/micro-course/backups/$(date +%Y%m%d-%H%M%S)"

echo "============================================"
echo "  微课平台 · 学校服务器部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"

# === Step 1: 备份当前版本 ===
echo ""
echo "[1/6] 备份当前版本 ..."
if [ -d "$APP_DIR" ]; then
    mkdir -p "$BACKUP_DIR"
    if [ -f "$APP_DIR/micro-course-api/target/micro-course-api-1.0.0.jar" ]; then
        cp "$APP_DIR/micro-course-api/target/micro-course-api-1.0.0.jar" "$BACKUP_DIR/"
        echo "  JAR 已备份: $BACKUP_DIR/"
    fi
    if [ -f "$APP_DIR/micro-course-api/.env" ]; then
        cp "$APP_DIR/micro-course-api/.env" "$BACKUP_DIR/"
        echo "  .env 已备份"
    fi
fi

# === Step 2: 拉取最新代码 ===
echo ""
echo "[2/6] 拉取最新代码 ..."
if [ -d "$APP_DIR/.git" ]; then
    cd "$APP_DIR"
    git fetch origin main
    git reset --hard origin/main
    echo "  已更新到: $(git log --oneline -1)"
else
    git clone "$GIT_REPO" "$APP_DIR"
    cd "$APP_DIR"
fi

# === Step 3: 后端构建 ===
echo ""
echo "[3/6] 构建后端 ..."
cd "$APP_DIR/micro-course-api"
mvn clean package -DskipTests -q 2>&1 | tail -5
echo "  后端构建完成"

# === Step 4: Flyway 迁移 ===
echo ""
echo "[4/6] Flyway 数据库迁移 ..."
echo "  新增迁移:"
echo "    V102: 订单幂等性部分唯一索引 (orders userId+courseId WHERE PENDING/PAID)"
echo "    V103: 高频排序索引 (enrollments/discussion_posts/exercise_records/certificates/grades)"
echo "  迁移将在应用启动时自动执行"

# === Step 5: 重启服务 ===
echo ""
echo "[5/6] 重启后端服务 ..."
if systemctl is-active --quiet micro-course-api 2>/dev/null; then
    sudo systemctl restart micro-course-api
    echo "  服务已重启"
elif [ -f "$APP_DIR/docker-compose.yml" ]; then
    cd "$APP_DIR"
    docker-compose down 2>/dev/null || true
    docker-compose up -d
    echo "  Docker 容器已启动"
else
    echo "  [WARN] 未检测到 systemd 服务或 docker-compose"
    echo "  请手动重启:"
    echo "    cd $APP_DIR/micro-course-api && nohup java -jar target/micro-course-api-1.0.0.jar &"
fi

# === Step 6: 健康检查 ===
echo ""
echo "[6/6] 健康检查 ..."
sleep 5
if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "  ✅ 后端健康检查通过"
elif curl -sf http://localhost:8080/api/enums/export > /dev/null 2>&1; then
    echo "  ✅ 后端 API 可访问"
else
    echo "  ⚠️  后端未响应, 请检查日志"
fi

echo ""
echo "============================================"
echo "  部署完成!"
echo "  本次更新: fix(super-fix-v4) 34 findings"
echo "  - 安全: BCrypt12, JWT fail-fast, XSS, 路径穿越"
echo "  - 并发: 原子SQL cart, DuplicateKey, 防重叠"
echo "  - 资源: maxLimit, 5索引, 500MB上传, LIMIT"
echo "  - 数据库: V102 订单唯一索引, V103 排序索引"
echo "============================================"