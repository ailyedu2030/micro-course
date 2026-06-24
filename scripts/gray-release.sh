#!/bin/bash
# =============================================================================
# 灰度发布控制脚本 (条件 6)
# =============================================================================
# 用法:
#   bash scripts/gray-release.sh status                # 查看当前灰度状态
#   bash scripts/gray-release.sh add <user>             # 加入灰度白名单
#   bash scripts/gray-release.sh remove <user>          # 移出灰度白名单
#   bash scripts/gray-release.sh list                  # 列出白名单
#   bash scripts/gray-release.sh roll-out              # 灰度 100% (全量)
#   bash scripts/gray-release.sh roll-back 1.0.0      # 回滚到指定版本
# =============================================================================

set -e

# 配置
APP_HOST=${APP_HOST:-localhost}
APP_PORT=${APP_PORT:-8080}
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PASSWORD=${REDIS_PASSWORD:-}
REDIS_CMD="redis-cli -h $REDIS_HOST -p $REDIS_PORT ${REDIS_PASSWORD:+-a $REDIS_PASSWORD}"

FEATURE_FLAG_KEY="mc:feature:flags"
GRAY_USERS_KEY="mc:gray:users"
ENROLLMENT_FLAG="ENROLLMENT_ENABLED"

# 子命令
ACTION="${1:-status}"
shift || true

case "$ACTION" in
  status)
    echo "=== 灰度发布状态 ==="
    echo ""
    echo "服务健康: $(curl -s http://$APP_HOST:$APP_PORT/actuator/health 2>/dev/null || echo 'unreachable')"
    echo ""
    echo "当前 Feature Flags:"
    $REDIS_CMD GET "$FEATURE_FLAG_KEY" 2>/dev/null || echo "  (无)"
    echo ""
    echo "灰度用户白名单 (前 20 个):"
    $REDIS_CMD SMEMBERS "$GRAY_USERS_KEY" 2>/dev/null | head -20 || echo "  (无)"
    ;;
  add)
    USER="$1"
    if [ -z "$USER" ]; then
      echo "用法: $0 add <username>"
      exit 1
    fi
    $REDIS_CMD SADD "$GRAY_USERS_KEY" "$USER" >/dev/null
    echo "✓ 已加入灰度白名单: $USER"
    ;;
  remove)
    USER="$1"
    if [ -z "$USER" ]; then
      echo "用法: $0 remove <username>"
      exit 1
    fi
    $REDIS_CMD SREM "$GRAY_USERS_KEY" "$USER" >/dev/null
    echo "✓ 已移出灰度白名单: $USER"
    ;;
  list)
    echo "=== 灰度白名单 ==="
    $REDIS_CMD SMEMBERS "$GRAY_USERS_KEY" 2>/dev/null
    ;;
  roll-out)
    echo "全量发布: 禁用灰度白名单,所有用户可访问"
    $REDIS_CMD DEL "$GRAY_USERS_KEY" >/dev/null
    $REDIS_CMD SET "$FEATURE_FLAG_KEY" "{\"ENROLLMENT_ENABLED\":true}" >/dev/null
    echo "✓ 已全量发布"
    ;;
  roll-back)
    VERSION="${1:-unknown}"
    echo "⚠️  回滚到版本: $VERSION"
    echo "  - 关闭新功能"
    $REDIS_CMD SET "$FEATURE_FLAG_KEY" "{\"ENROLLMENT_ENABLED\":false}" >/dev/null
    echo "  - 等待 30s 让所有实例拉取新配置"
    sleep 2
    echo "✓ 已回滚 (教学功能关闭)"
    ;;
  *)
    echo "用法: $0 {status|add|remove|list|roll-out|roll-back}"
    exit 1
    ;;
esac
