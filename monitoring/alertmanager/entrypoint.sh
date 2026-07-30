#!/bin/sh
# =============================================================================
# Alertmanager 入口脚本 — 在容器启动时将 CHANGE_ME 占位符替换为真实环境变量
# =============================================================================
# 
# Alertmanager 本身不支持 alertmanager.yml 内的环境变量展开。
# 此脚本在 alertmanager 启动前，用 sed 将占位符替换为实际环境变量值。
#
# 使用方式（docker-compose）:
#   services:
#     alertmanager:
#       image: prom/alertmanager:v0.27.0
#       environment:
#         SLACK_WEBHOOK_URL: "${SLACK_WEBHOOK_URL}"
#         PAGERDUTY_SERVICE_KEY: "${PAGERDUTY_SERVICE_KEY}"
#         SMTP_PASSWORD: "${SMTP_PASSWORD}"
#
# 如果环境变量未设置,占位符保持不变（alertmanager 会报错但不会崩溃）。
# 这样 verify-secrets.sh --strict 仍能检测到未替换的占位符,阻断部署。
#
# 依赖: sed (busybox/gnu), prom/alertmanager:v0.27.0 基础镜像自带
# =============================================================================

set -e

CONFIG="${ALERTMANAGER_CONFIG:-/etc/alertmanager/alertmanager.yml}"

# 替换 Slack Webhook URL (default-receiver + p0 + p1 + p2)
if [ -n "${SLACK_WEBHOOK_URL:-}" ]; then
  sed -i "s|CHANGE_ME_SLACK|${SLACK_WEBHOOK_URL}|g" "$CONFIG"
  echo "[entrypoint] ✅ Slack Webhook URL 已从 SLACK_WEBHOOK_URL 注入"
else
  echo "[entrypoint] ⚠️  SLACK_WEBHOOK_URL 未设置,占位符保留（verify-secrets 将阻断）" >&2
fi

# 替换 PagerDuty Service Key
if [ -n "${PAGERDUTY_SERVICE_KEY:-}" ]; then
  sed -i "s|CHANGE_ME_PAGERDUTY|${PAGERDUTY_SERVICE_KEY}|g" "$CONFIG"
  echo "[entrypoint] ✅ PagerDuty Service Key 已从 PAGERDUTY_SERVICE_KEY 注入"
else
  echo "[entrypoint] ⚠️  PAGERDUTY_SERVICE_KEY 未设置,占位符保留（verify-secrets 将阻断）" >&2
fi

# 替换 SMTP 密码
if [ -n "${SMTP_PASSWORD:-}" ]; then
  sed -i "s|CHANGE_ME_BEFORE_DEPLOY|${SMTP_PASSWORD}|g" "$CONFIG"
  echo "[entrypoint] ✅ SMTP 密码已从 SMTP_PASSWORD 注入"
else
  echo "[entrypoint] ⚠️  SMTP_PASSWORD 未设置,占位符保留（verify-secrets 将阻断）" >&2
fi

# 启动 alertmanager（传递原始 CMD 参数）
exec /bin/alertmanager "$@"
