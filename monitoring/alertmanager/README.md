# Alertmanager 部署说明

> 微课平台告警通知路由配置
> 路径: `monitoring/alertmanager/alertmanager.yml`

---

## 部署前必做

### 1. 替换占位符

`alertmanager.yml` 包含 8 处 `CHANGE_ME_*` 占位符，**部署前必须全部替换**：

| 占位符 | 位置 | 替换为 | 获取方式 |
|--------|------|--------|---------|
| `CHANGE_ME_BEFORE_DEPLOY` | smtp_auth_password | SMTP 真实密码 | 运维管理 |
| `CHANGE_ME_SLACK` (×5) | slack_configs[*].api_url | Slack Webhook URL | Slack App → Incoming Webhooks |
| `CHANGE_ME_PAGERDUTY` | pagerduty_configs.service_key | PagerDuty 集成密钥 | PagerDuty → Developer Tools → Service Directories |

### 2. 配置文件验证

```bash
# 验证配置文件语法（使用 amtool，Alertmanager 自带工具）
amtool check-config alertmanager.yml

# 预期输出: "SUCCESS: 0 errors, 0 warnings"
```

### 3. 生产环境变量注入

Alertmanager **不支持** `alertmanager.yml` 内的环境变量展开。生产部署建议：

#### 方案 A: 部署脚本替换（推荐）
```bash
# deploy.sh 中使用 sed 替换占位符为真实值
sed -i "s|CHANGE_ME_SLACK|${SLACK_WEBHOOK_URL}|g" alertmanager.yml
sed -i "s|CHANGE_ME_BEFORE_DEPLOY|${SMTP_PASSWORD}|g" alertmanager.yml
sed -i "s|CHANGE_ME_PAGERDUTY|${PAGERDUTY_SERVICE_KEY}|g" alertmanager.yml
```

#### 方案 B: ConfigMap/Secret 注入（K8s 环境）
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: alertmanager-secrets
stringData:
  alertmanager.yml: |
    # 完整 alertmanager.yml 内容，已替换真实值
```

#### 方案 C: Docker Compose volume 挂载
```yaml
services:
  alertmanager:
    image: prom/alertmanager:v0.27.0
    volumes:
      - ./alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
    command:
      - --config.file=/etc/alertmanager/alertmanager.yml
```

### 4. 门禁检查

```bash
# 部署门禁脚本会检测未替换的 CHANGE_ME 占位符
bash scripts/verify-secrets.sh --strict
# 返回 exit 1 表示仍有未替换占位符，阻断部署
```

---

## 路由规则

| 严重度 | Receiver | Slack Channel | PagerDuty | 响应时间 |
|--------|----------|---------------|-----------|---------|
| P0 | p0-oncall | #incident | ✅ 触发 | 15 分钟 |
| P1 | p1-oncall | #alerts | ❌ | 1 小时 |
| P2 | p2-summary | #monitoring | ❌ | 24 小时 |
| P3 | p3-weekly | ❌ (邮件) | ❌ | 每周 |

---

## 抑制规则

当前配置的抑制规则：
- `ApiDown` P0 告警抑制同 job 的所有 warning 级别告警（避免雪崩通知）
- `HikariPoolExhausted` P0 告警抑制同 job 的 `PostgresActiveConnectionsHigh` 告警

---

## 故障排查

### 测试告警发送

```bash
# 使用 amtool 手动触发测试告警
amtool alert add \
  --alertmanager.url=http://localhost:9093 \
  --annotation=summary="测试告警" \
  --annotation=description="部署验证测试" \
  --label=alertname="TestAlert" \
  --label=severity=critical \
  --label=priority=P0 \
  --start="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  TestAlert
```

### 查看当前告警

```bash
amtool alert list --alertmanager.url=http://localhost:9093
```

### 排查 Slack 通知失败

1. 验证 `api_url` 格式正确: `https://hooks.slack.com/services/Txxx/Bxxx/xxxx`
2. 检查 Alertmanager 日志: `docker logs micro-course-alertmanager-1 | grep -i slack`
3. 使用 curl 测试 Webhook: `curl -X POST -H 'Content-Type: application/json' -d '{"text":"Hello"}' <SLACK_WEBHOOK_URL>`

---

## 相关文档

- 告警运维 SOP: `docs/operations/ALERT_SOP.md`
- 部署门禁: `scripts/verify-secrets.sh`
- Prometheus 告警规则: `monitoring/prometheus/alerts.yml`
- Grafana 面板: `monitoring/grafana/dashboards/`
