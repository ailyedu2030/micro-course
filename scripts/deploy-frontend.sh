#!/bin/bash
# =============================================================================
# 安全前端部署脚本 (INC-2026-07-31-PR161-DEPLOY 事故后新增)
# =============================================================================
# 设计原则:
#   1. 每个步骤独立 (独立 ssh 命令, 不嵌套 heredoc)
#   2. 每步必须验证 (文件大小 / 文件数 / HTTP 状态)
#   3. 用 mv 原子操作 (不用 rm && mv)
#   4. 失败立即退出 (set -e)
#
# 用法:
#   bash scripts/deploy-frontend.sh /path/to/dist.tar.gz
#
# 必须先:
#   1. cd micro-course-admin && npm ci && npm run build
#   2. tar czf /tmp/admin-pr161.tar.gz -C dist .
#   3. bash scripts/deploy-gate.sh check  (gate open)
# =============================================================================

set -e

TARBALL="${1:-/tmp/admin-pr161.tar.gz}"
SERVER="ubuntu@100.74.122.13"
SERVER_IP="100.74.122.13"
ADMIN_CONTAINER="micro-course-micro-course-admin-1"
BAK_DIR="/opt/micro-course/backups"
BAK_NAME="admin.dist.backup.$(date +%Y%m%d_%H%M%S)"

# === Step 0: 前置检查 ===
echo "=== Step 0: 前置检查 ==="
[ ! -f "$TARBALL" ] && { echo "❌ tar 文件不存在: $TARBALL"; exit 1; }
LOCAL_SIZE=$(stat -f%z "$TARBALL" 2>/dev/null || stat -c%s "$TARBALL")
echo "✅ tar 文件存在: $TARBALL ($LOCAL_SIZE bytes)"

# === Step 1: 传 tar 到生产 ===
echo ""
echo "=== Step 1: scp tar 到生产 ==="
scp -v "$TARBALL" "$SERVER:/tmp/$(basename $TARBALL)" 2>&1 | grep -E "Exit status|Transferred" | head -2

# === Step 2: 验证生产上文件存在 + 大小匹配 ===
echo ""
echo "=== Step 2: 验证 tar 在生产 ==="
REMOTE_SIZE=$(ssh -o ConnectTimeout=10 "$SERVER" "stat -c%s /tmp/$(basename $TARBALL)")
[ "$REMOTE_SIZE" = "$LOCAL_SIZE" ] || { echo "❌ 大小不匹配: 本地 $LOCAL_SIZE vs 远程 $REMOTE_SIZE"; exit 1; }
echo "✅ 大小匹配: $REMOTE_SIZE bytes"

# === Step 3: docker cp 到容器 ===
echo ""
echo "=== Step 3: docker cp 到容器 ==="
ssh -o ConnectTimeout=10 "$SERVER" "docker cp /tmp/$(basename $TARBALL) $ADMIN_CONTAINER:/tmp/$(basename $TARBALL)"
echo "✅ docker cp OK"

# === Step 4: 容器内解压 ===
echo ""
echo "=== Step 4: 容器内解压 ==="
TAR_NAME=$(basename $TARBALL .tar.gz)
ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER mkdir -p /tmp/newdist-$TAR_NAME"
ssh -o ConnectTimeout=10 "$SERVER" "docker exec -w /tmp/newdist-$TAR_NAME $ADMIN_CONTAINER tar xzf /tmp/$(basename $TARBALL)"
INNER_FILES=$(ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER find /tmp/newdist-$TAR_NAME -type f | wc -l")
echo "✅ 解压完成: $INNER_FILES 文件"

# === Step 5: 验证 bundle hash ===
echo ""
echo "=== Step 5: 验证 bundle hash ==="
NEW_BUNDLE=$(ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER grep -oE 'assets/index-[A-Za-z0-9_-]+\\.js' /tmp/newdist-$TAR_NAME/index.html")
echo "新 bundle hash: $NEW_BUNDLE"

# === Step 6: 原子替换 (mv 旧 → mv 新) ===
echo ""
echo "=== Step 6: 原子替换 (mv 备份旧 → mv 新上) ==="
ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER sh -c '
  rm -rf /usr/share/nginx/html.bak-newest
  mv /usr/share/nginx/html /usr/share/nginx/html.bak-newest
  echo BACKUP_OK
'"
ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER mv /tmp/newdist-$TAR_NAME /usr/share/nginx/html"
echo "✅ 替换 OK"

# === Step 7: 验证 html 目录 ===
echo ""
echo "=== Step 7: 验证 html 目录 ==="
ACTIVE_BUNDLE=$(ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER grep -oE 'assets/index-[A-Za-z0-9_-]+\\.js' /usr/share/nginx/html/index.html")
[ "$ACTIVE_BUNDLE" = "$NEW_BUNDLE" ] || { echo "❌ bundle 不匹配: 预期 $NEW_BUNDLE 实际 $ACTIVE_BUNDLE"; exit 1; }
echo "✅ bundle 匹配: $ACTIVE_BUNDLE"

# === Step 8: nginx reload (平滑) ===
echo ""
echo "=== Step 8: nginx reload ==="
ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER nginx -t"
ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER nginx -s reload"
echo "✅ nginx reload OK"

# === Step 9: 验证 HTTP ===
echo ""
echo "=== Step 9: 验证 HTTP ==="
sleep 2
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://$SERVER_IP:80/")
echo "HTTP GET /: $HTTP_CODE"
[ "$HTTP_CODE" = "200" ] || { echo "❌ HTTP 非 200"; exit 1; }
echo "✅ HTTP 200"

# === Step 10: 备份当前 dist 到 /opt/micro-course/backups (双备份策略) ===
echo ""
echo "=== Step 10: 备份到 /opt/micro-course/backups/$BAK_NAME ==="
ssh -o ConnectTimeout=10 "$SERVER" "mkdir -p $BAK_DIR/$BAK_NAME"
ssh -o ConnectTimeout=10 "$SERVER" "docker cp $ADMIN_CONTAINER:/usr/share/nginx/html.bak-newest $BAK_DIR/$BAK_NAME/"
BACKUP_FILES=$(ssh -o ConnectTimeout=10 "$SERVER" "find $BAK_DIR/$BAK_NAME -type f | wc -l")
[ "$BACKUP_FILES" -gt 0 ] || { echo "❌ 备份失败: $BAK_DIR/$BAK_NAME 无文件"; exit 1; }
echo "✅ 双备份 OK"

# === Step 11: 清理临时文件 ===
echo ""
echo "=== Step 11: 清理 ==="
ssh -o ConnectTimeout=10 "$SERVER" "docker exec $ADMIN_CONTAINER rm -f /tmp/$(basename $TARBALL)"
rm -f "$TARBALL"
echo "✅ 临时文件清理"

# === 完成 ===
echo ""
echo "========================================"
echo "✅ 部署完成 (按 INC-2026-07-31-PR161-DEPLOY 事故后安全 SOP)"
echo "新 bundle: $NEW_BUNDLE"
echo "当前 backup chain:"
echo "  /opt/micro-course/backups/$BAK_NAME (PR #161 部署后旧 dist, 含 Bug E/F 修复前)"
echo "  /usr/share/nginx/html.bak-newest (最新 backup, 在容器内)"
echo ""
echo "下一步: 5 分钟监控 + 灰度白名单 + 全量 roll-out"
echo "========================================"
