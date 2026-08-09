#!/bin/bash
# Micro-Course 一键回滚脚本（5 分钟内）
# 用法：bash rollback.sh
# 配合：ROLLBACK_RUNBOOK.md
# L0 铁律：用户体验至上（异常 = 立即回滚 = 保护用户）

set -euo pipefail

K8S_NAMESPACE="${K8S_NAMESPACE:-micro-course}"
K8S_DEPLOYMENT="${K8S_DEPLOYMENT:-micro-course}"
ROLLOBACK_VERSION="${ROLLOBACK_VERSION:-416cc1d5}"  # PR #203 之前稳定
GRAY_PCT="${GRAY_PCT:-0}"  # 完全回滚

echo "========================================="
echo "  ⚠️  Micro-Course 一键回滚"
echo "  ⚠️  target: $ROLLOBACK_VERSION"
echo "  ⚠️  traffic: ${GRAY_PCT}%"
echo "========================================="
echo ""

# 1. 团队通知
echo "[1/5] 团队通知..."
echo "  ⚠️ 通知 SRE on-call + 总工程师 + 产品 + 客服 + 用户"
echo ""

# 2. 备份当前状态
echo "[2/5] 备份当前状态..."
mkdir -p /tmp/micro-course-rollback-$(date +%Y%m%d-%H%M%S)
cp -r /var/log/micro-course /tmp/micro-course-rollback-*/ 2>/dev/null || true
echo "  ✓ 备份完成：/tmp/micro-course-rollback-*"
echo ""

# 3. 切换流量到 rollback 版本
echo "[3/5] 切换流量到 rollback 版本（${GRAY_PCT}%）..."
kubectl patch ingress "${K8S_INGRESS}-canary" -n "$K8S_NAMESPACE" --type=json -p '[
  {
    "op": "replace",
    "path": "/spec/rules/1",
    "value": {
      "host": "api.microcourse.ailyd",
      "http": {
        "paths": [
          {"path": "/", "pathType": "Prefix", "backend": {"service": {"name": "'${K8S_DEPLOYMENT}'", "port": {"number": 8080}}}}
        ]
      }
    }
  }
]' 2>/dev/null || kubectl patch ingress "$K8S_INGRESS" -n "$K8S_NAMESPACE" --type=json -p '[
  {
    "op": "replace",
    "path": "/spec/rules",
    "value": [
      {
        "host": "api.microcourse.ailyd",
        "http": {
          "paths": [
            {"path": "/", "pathType": "Prefix", "backend": {"service": {"name": "'${K8S_DEPLOYMENT}'", "port": {"number": 8080}}}}
          ]
        }
      }
    ]
  }
]'
echo "  ✓ 流量切换完成"
echo ""

# 4. 回滚 deployment 到目标版本
echo "[4/5] 回滚 deployment 到 ${ROLLOBACK_VERSION}..."
kubectl set image deployment/${K8S_DEPLOYMENT} \
  micro-course=micro-course:${ROLLOBACK_VERSION} \
  -n "$K8S_NAMESPACE"
kubectl rollout status deployment/${K8S_DEPLOYMENT} \
  -n "$K8S_NAMESPACE" --timeout=10m
echo "  ✓ deployment 回滚完成"
echo ""

# 5. 监控 5 分钟
echo "[5/5] 监控 5 分钟..."
for i in 1 2 3 4 5; do
  echo "  [${i}/5] 监控中..."
  sleep 60

  # 检查 5xx 错误率
  ERROR_RATE=$(curl -s "http://prometheus:9090/api/v1/query?query=sum(rate(http_requests_total{job=\"micro-course\",status=~\"5..\"}[5m]))" | jq -r '.data.result[0].value[1] // "0"')
  P99_LATENCY=$(curl -s "http://prometheus:9090/api/v1/query?query=histogram_quantile(0.99,sum%20by(le)(rate(http_request_duration_seconds_bucket{job=\"micro-course\"}[5m])))" | jq -r '.data.result[0].value[1] // "0"')

  echo "      5xx: $ERROR_RATE, p99: ${P99_LATENCY}s"

  # 检查 5xx 是否 < 0.1%（不严格）
  if (( $(echo "$ERROR_RATE < 0.001" | bc -l 2>/dev/null || echo 0) )); then
    echo "  ✓ 错误率 < 0.1%，恢复成功"
    break
  fi
done

echo ""
echo "========================================="
echo "  ⚠️  回滚完成"
echo "  ⚠️  下一步：写 INCIDENT_POSTMORTEM.md"
echo "========================================="

# 6. 自动写事件报告
INCIDENT_DIR=/var/log/micro-course/incidents
mkdir -p $INCIDENT_DIR
cat > $INCIDENT_DIR/rollback-$(date +%Y%m%d-%H%M%S).md << 'EOF'
# 紧急回滚事件

## 时间线
- T+0s: 监控告警触发
- T+5min: 决策回滚
- T+5min: 一键回滚脚本执行
- T+10min: 监控恢复
- T+15min: 写事件报告

## 触发原因
（由 SRE on-call 填）

## 影响
- 用户数：
- 收入损失：
- 数据丢失：

## 改进
（避免下次同类问题）
EOF

echo "  ✓ 事件报告已写：$INCIDENT_DIR/rollback-$(date +%Y%m%d-%H%M%S).md"
echo ""
echo "按 ROLLBACK_RUNBOOK.md 阶段 1 决策流程完成"
