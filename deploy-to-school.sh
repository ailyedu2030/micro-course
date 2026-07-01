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
# Phase 15: 生产环境 Maven 必须使用 JDK 17（M1 Mac 默认 JDK 26 与 MyBatis-Plus 冲突）
# 服务器验证: java -version 应显示 17.x
mvn clean package -DskipTests -q 2>&1 | tail -5
echo "  后端构建完成"

# === Step 4: Frontend 构建 ===
echo ""
echo "[4/7] 构建前端 ..."
cd "$APP_DIR/micro-course-admin"
npm ci 2>&1 | tail -3
npm run build 2>&1 | tail -3
echo "  前端构建完成"

# === Step 5: Flyway 迁移 ===
echo ""
echo "[5/7] Flyway 数据库迁移 ..."
echo "  Phase 15 新增迁移 (V91-V96):"
echo "    V91: expand_proposal_fields — 申请表扩展 27 字段"
echo "    V92: proposal_courses_tables — 课程体系+负责人课程表"
echo "    V93: proposal_team_members — 教学团队成员表"
echo "    V94: proposal_signatures_and_shared — 签字+共建共享单位表"
echo "    V96: add_micro_specialty_name — 微专业名称字段"
echo "  注意: V91-V96 版本号低于已应用的 V99-V103，需 outOfOrder=true"
echo "  已在 application.yml 中配置: flyway.out-of-order: true"
echo "  迁移将在应用启动时自动执行"

# === Step 6: 重启服务 ===
echo ""
echo "[6/7] 重启后端服务 ..."
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

# === Step 7: 健康检查 ===
echo ""
echo "[7/7] 健康检查 ..."
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
echo "  本次更新: feat(phase15) 整理收纳微专业申请表系统"
echo "  - 新增: 5模块申请表（表头/基本情况/教学团队/签字/共享单位）"
echo "  - 新增: Word导出(Apache POI) + PDF导出(OpenPDF)"
echo "  - 新增: 12个REST API端点 + 自动保存(1.5s防抖)"
echo "  - 新增: null值在V91中被禁止，默认使用0或空字符串"
echo "  - Flyway: outOfOrder=true（V91-V96低于已应用的V99）"
echo "  - 配置: JDK 17必需（JDK 26与MyBatis-Plus不兼容）"
echo "  - 资源: maxLimit, 5索引, 500MB上传, LIMIT"
echo "  - 数据库: V102 订单唯一索引, V103 排序索引"
echo "============================================"