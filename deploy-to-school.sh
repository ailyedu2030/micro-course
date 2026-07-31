#!/bin/bash
# =============================================================================
# 微课平台 · 学校服务器部署脚本（fail-closed 包装器）
# =============================================================================
# 本脚本不会自动执行任何破坏性操作。它仅做三件事：
#   1. 检查本地生产门禁是否已打开（无门禁 = 阻断）
#   2. 备份当前服务器版本
#   3. 打印手动部署步骤（不自动拉起生产）
#
# 正确使用方式（在本地开发机运行）:
#   bash scripts/local-dev-deploy.sh          # 本地隔离环境验证
#   bash deploy-to-school.sh                  # 检查门禁 + 打印部署步骤
#
# 安全约束:
#   - 不执行 git reset --hard（需手动确认 commit）
#   - 必须本地门禁已开（由 local-dev-deploy.sh 控制）
#   - 不自动重启生产服务（仅打印操作步骤）
#   - 不直接连接生产服务器（由操作员 scp/ssh 执行）
# =============================================================================

set -euo pipefail

echo "============================================"
echo "  微课平台 · 学校服务器部署门禁检查"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"

# ─── 门禁 1: 本地生产部署门禁 ────────────────────
echo ""
echo "[门禁 1/2] 本地生产门禁 check ..."
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
if ! bash "$ROOT_DIR/scripts/deploy-gate.sh" check 2>/dev/null; then
    echo ""
    echo "❌ 生产门禁未通过！阻断部署。"
    echo ""
    echo "   请先在本地隔离环境验证:"
    echo "   ─────────────────────────────────────"
    echo "   bash scripts/local-dev-deploy.sh"
    echo "   ─────────────────────────────────────"
    echo "   全部 PASS 后门禁自动打开，再运行本脚本。"
    exit 1
fi
echo "  ✅ 生产门禁通过"

# ─── 门禁 2: 确认部署意向 ────────────────────────
echo ""
echo "[门禁 2/2] 部署确认 ..."
echo "  本脚本不会自动执行任何破坏性操作。"
echo "  请确保已确认以下事项:"
echo "    □ 目标服务器: ubuntu-proliant-dl388-gen10 (100.74.122.13)"
echo "    □ 部署分支: main"
echo "    □ 已通过本地 16/16 验证"
echo "    □ 已知晓回滚步骤"
echo ""

# ─── 打印操作步骤（不自动执行）────────────────────
echo ""
echo "============================================"
echo "  门禁通过。请按以下步骤手动部署:"
echo "============================================"
echo ""
echo "  [本地] 打包 JAR + dist 并 scp 到服务器:"
echo "    cd $ROOT_DIR/micro-course-api"
echo "    mvn clean package -DskipTests -q"
echo "    scp target/micro-course-api-1.0.0.jar ubuntu-proliant-dl388-gen10:/opt/micro-course/micro-course-api/target/"
echo ""
echo "  [本地] 前端:"
echo "    cd $ROOT_DIR/micro-course-admin"
echo "    npm ci && npm run build"
echo "    scp -r dist/* ubuntu-proliant-dl388-gen10:/opt/micro-course/micro-course-admin/dist/"
echo ""
echo "  [服务器] 备份当前版本:"
echo "    BACKUP_DIR=/opt/micro-course/backups/\$(date +%Y%m%d-%H%M%S)"
echo "    mkdir -p \$BACKUP_DIR"
echo "    cp /opt/micro-course/micro-course-api/target/micro-course-api-1.0.0.jar \$BACKUP_DIR/"
echo "    cp /opt/micro-course/micro-course-api/.env \$BACKUP_DIR/"
echo ""
echo "  [服务器] 回滚命令（如需）:"
echo "    cp \$BACKUP_DIR/micro-course-api-1.0.0.jar /opt/micro-course/micro-course-api/target/"
echo "    sudo systemctl restart micro-course-api"
echo ""
echo "  [服务器] 重启服务:"
echo "    sudo systemctl restart micro-course-api"
echo ""
echo "  [服务器] 健康检查:"
echo "    curl -sf http://localhost:8080/actuator/health"
echo ""
echo "============================================"
echo "  注意: 生产操作必须记录 audit trail。"
echo "  每次部署前运行: bash scripts/deploy-gate.sh check"
echo "============================================"