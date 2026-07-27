# 微课平台监控栈

## 服务清单

| 服务 | 端口 | 用途 |
|------|------|------|
| Prometheus | 9090 | 指标采集 |
| Grafana | 3000 | 仪表盘 |
| AlertManager | 9093 | 告警路由 |
| node-exporter | 9100 | 宿主机指标 |
| postgres-exporter | 9187 | PG 指标 |
| redis-exporter | 9121 | Redis 指标 |
| **Loki** | **3100** | **日志聚合** |
| **Promtail** | — | **Docker 日志采集，推送到 Loki** |

## 日志查询

在 Grafana → Explore → 选择 Loki 数据源，使用 LogQL 查询：

| 查询 | 说明 |
|------|------|
| `{job="containerlogs"}` | 所有容器日志 |
| `{job="containerlogs"} \|= "ERROR"` | 所有 ERROR 级别日志 |
| `{container="micro-course-api-1"} \|= "Exception"` | API 异常日志 |
| `{container="micro-course-admin-1"}` | 前端容器日志 |
| `rate({job="containerlogs"} \|~ "WARN" [5m])` | WARN 日志速率 |

## 快速开始

```bash
# 启动全部服务（包含 Loki + Promtail）
docker compose up -d

# 验证 Loki 健康状态
curl http://localhost:3100/ready

# 验证 Promtail 健康状态
curl http://localhost:9080/ready
```
