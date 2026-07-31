# Alertmanager 部署说明

> 微课平台告警通知路由配置
> 路径: `monitoring/alertmanager/alertmanager.yml`

---

## 部署前必做

### 1. 配置环境变量

Alertmanager **原生不支持** `alertmanager.yml` 内的环境变量展开。
本项目通过 `entrypoint.sh` 在容器启动时用 `sed` 替换占位符。

```bash
# 1. 复制环境变量模板
cp monitoring/alertmanager/alerts.env.example alerts.env

# 2. 编辑 alerts.env 填入真实值
#    需要 3 个环境变量:
#    - SLACK_WEBHOOK_URL: Slack Webhook URL
#    - PAGERDUTY_SERVICE_KEY: PagerDuty 集成密钥 (Events API v2)
#    - SMTP_PASSWORD: SMTP 密码

# 3. 部署
docker compose up -d alertmanager

# 4. 验证
bash scripts/verify-secrets.sh --strict
# 应显示: ✅ 未发现 CHANGE_ME / 占位符
```

### 2. 配置文件验证

```bash
# 验证配置文件语法（使用 amtool，Alertmanager 自带工具）
amtool check-config alertmanager.yml

# 预期输出: "SUCCESS: 0 errors, 0 warnings"
```

### 3. 生产环境变量注入机制

| 环境变量 | 占位符 | 替换位置 | 用途 |
|---------|--------|---------|------|
| `SLACK_WEBHOOK_URL` | `CHANGE_ME_SLACK` (×4) | default-receiver, p0-oncall, p1-oncall, p2-summary | Slack 通知 |
| `PAGERDUTY_SERVICE_KEY` | `CHANGE_ME_PAGERDUTY` (×1) | p0-oncall | PagerDuty P0 告警 |
| `SMTP_PASSWORD` | `CHANGE_ME_BEFORE_DEPLOY` (×1) | global | SMTP 邮件通知 |

注入流程:
```
容器启动
  ↓
entrypoint.sh 读取环境变量
  ↓
sed 替换 /etc/alertmanager/alertmanager.yml 中的占位符
  ↓
exec /bin/alertmanager --config.file=...
```

如果某个环境变量未设置，对应占位符保持不动。Alertmanager 启动后日志会显示 `smtp_auth_password` 使用了 CHANGE_ME，但不会崩溃（只是邮件通知失败）。`verify-secrets.sh --strict` 会在 CI 门禁处阻断部署。

### 4. 门禁检查

```bash
# 部署门禁脚本会检测未替换的 CHANGE_ME 占位符
bash scripts/verify-secrets.sh --strict
# 返回 exit 1 表示仍有未替换占位符，阻断部署
```

---

## 路由规则

| 优先级 | Receiver | Slack Channel | PagerDuty | 响应时间 |
|--------|----------|---------------|-----------|---------|
| P0 | p0-oncall | #incident | ✅ 触发 | 15 分钟 |
| P1 | p1-oncall | #alerts | ❌ | 1 小时 |
| P2 | p2-summary | #monitoring | ❌ | 24 小时 |
| P3 | p3-weekly | ❌ (邮件) | ❌ | 每周 |

---

## 抑制规则

当前配置的抑制规则：
- `ApiDown` P0 告警抑制同 job 的所有 warning 级别告警（避免雪崩通知）
- `HikariPoolExhausted` 告警抑制同 job 的 `PostgresActiveConnectionsHigh` 告警

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

1. 确认 `SLACK_WEBHOOK_URL` 环境变量已设置: `docker inspect micro-course-alertmanager | grep SLACK_WEBHOOK`
2. 检查 entrypoint 日志: `docker logs micro-course-alertmanager | grep entrypoint`
3. 检查 Alertmanager 日志: `docker logs micro-course-alertmanager | grep -i slack`
4. 使用 curl 测试 Webhook: `curl -X POST -H 'Content-Type: application/json' -d '{"text":"Hello"}' <SLACK_WEBHOOK_URL>`

---

## 相关文档

- 告警运维 SOP: `docs/operations/ALERT_SOP.md`
- 部署门禁: `scripts/verify-secrets.sh`
- 环境变量模板: `monitoring/alertmanager/alerts.env.example`
- Prometheus 告警规则: `monitoring/prometheus/alerts.yml`
- Grafana 面板: `monitoring/grafana/dashboards/`
