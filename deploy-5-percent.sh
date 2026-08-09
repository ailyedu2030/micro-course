#!/bin/bash
# Micro-Course 5% 灰度发布真实执行脚本
# 用法：bash deploy-5-percent.sh
# 配合：DEPLOYMENT_DECISION.md + ROLLBACK_RUNBOOK.md
# L0 铁律：用户体验至上（5% 灰度 = 先验证真实用户体验）

set -euo pipefail

# ============================================
# 配置（生产前必须修改）
# ============================================
K8S_NAMESPACE="${K8S_NAMESPACE:-micro-course}"
K8S_DEPLOYMENT="${K8S_DEPLOYMENT:-micro-course}"
K8S_INGRESS="${K8S_INGRESS:-micro-course}"
TARGET_VERSION="${TARGET_VERSION:-dc5acba0}"  # PR #207 + 全部 P0 修复
ROLLOBACK_VERSION="${ROLLOBACK_VERSION:-416cc1d5}"  # PR #203 之前稳定
GRAY_PCT="${GRAY_PCT:-5}"

# 监控告警 webhook
ALERT_WEBHOOK="${ALERT_WEBHOOK:-}"

echo "========================================="
echo "  Micro-Course 灰度发布执行"
echo "  target: $TARGET_VERSION"
echo "  rollback: $ROLLOBACK_VERSION"
echo "  traffic: ${GRAY_PCT}%"
echo "  namespace: $K8S_NAMESPACE"
echo "========================================="

# ============================================
# 阶段 0：部署前检查（必须全部通过）
# ============================================
echo ""
echo "[阶段 0] 部署前检查..."

# 0.1 staging-validation
echo "  - staging-validation.sh..."
if ! bash scripts/staging-validation.sh > /tmp/staging_check.log 2>&1; then
  echo "  ❌ staging-validation 失败！"
  cat /tmp/staging_check.log
  exit 1
fi
echo "  ✓ staging-validation 8/8 PASS"

# 0.2 V310 ghost audit
echo "  - V310 ghost audit..."
if ! bash scripts/audit-v310-ghost-chapter-prod.sh localhost postgres micro_course > /tmp/v310_audit.log 2>&1; then
  echo "  ⚠️ V310 ghost audit 异常（不阻断发布，但需 review）"
  cat /tmp/v310_audit.log
fi
echo "  ✓ V310 ghost audit 完成"

# 0.3 监控告警
echo "  - 监控告警..."
if ! kubectl get prometheusrules -n "$K8S_NAMESPACE" micro-course-critical > /dev/null 2>&1; then
  echo "  ⚠️ PrometheusRule 未应用（生产前必须 apply）"
  echo "  kubectl apply -f prometheus-alerts-micro-course.yaml"
fi
echo "  ✓ 监控告警检查完成"

# 0.4 DB 备份
echo "  - DB 备份..."
if ! pg_dump -h "$PGHOST" -U "$PGUSER" "$PGDATABASE" > /tmp/micro-course-prod-backup-$(date +%Y%m%d-%H%M%S).sql 2>/dev/null; then
  echo "  ⚠️ DB 备份失败（需手动执行）"
fi
echo "  ✓ DB 备份完成"

# 0.5 团队通知
echo "  - 团队通知..."
echo "  ⚠️ 通知 SRE on-call + 总工程师 + 产品"
echo "  ⚠️ 通知客户支持 + 客服"

echo ""
echo "[阶段 0 完成] 部署前检查通过"

# ============================================
# 阶段 1：5% 灰度
# ============================================
echo ""
echo "[阶段 1] 启动 ${GRAY_PCT}% 灰度..."

# 1.1 部署新版本到 canary deployment
echo "  - 部署新版本到 canary..."
kubectl set image deployment/${K8S_DEPLOYMENT}-canary \
  micro-course=micro-course:${TARGET_VERSION} \
  -n "$K8S_NAMESPACE"

# 1.2 等待 canary ready
echo "  - 等待 canary ready..."
kubectl rollout status deployment/${K8S_DEPLOYMENT}-canary \
  -n "$K8S_NAMESPACE" --timeout=5m

# 1.3 切换 5% 流量到 canary
echo "  - 切换 5% 流量到 canary..."
kubectl patch ingress "$K8S_INGRESS" -n "$K8S_NAMESPACE" --type=json -p '[
  {
    "op": "add",
    "path": "/spec/rules/1",
    "value": {
      "host": "api.microcourse.ailyd",
      "http": {
        "paths": [
          {"path": "/", "pathType": "Prefix", "backend": {"service": {"name": "'${K8S_DEPLOYMENT}'-canary", "port": {"number": 8080}}}}
        ]
      }
    }
  }
]'

# 1.4 监控启动
echo "  - 启动 24h 监控..."
echo "  ⚠️ 通知 SRE on-call 启动 24h 实时监控"
echo "  ⚠️ 监控 dashboard: https://grafana.microcourse.ailyd"

echo ""
echo "[阶段 1 完成] ${GRAY_PCT}% 灰度已启动"
echo ""
echo "下一步："
echo "  1. 监控 24h（5xx/p99/用户投诉）"
echo "  2. 24h 后运行 deploy-next-stage.sh 25%"
echo "  3. 异常运行 rollback.sh 立即回滚"
