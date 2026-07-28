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
| **Jaeger** | **16686 (UI), 4317 (OTLP gRPC)** | **链路追踪** |

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
# 启动全部服务（包含 Loki + Promtail + Jaeger）
docker compose up -d

# 验证 Loki 健康状态
curl http://localhost:3100/ready

# 验证 Promtail 健康状态
curl http://localhost:9080/ready

# 验证 Jaeger 健康状态
curl http://localhost:16686/
```

## OpenTelemetry 链路追踪

### 架构

```
[Spring Boot App] --OTLP gRPC--> [Jaeger (all-in-one)]
                                        |
                                   [UI :16686]
```

### 配置

OTel 集成在 `application.yml` 中配置：

```yaml
otel:
  service:
    name: micro-course-api
  traces:
    exporter: otlp
  exporter:
    otlp:
      endpoint: http://localhost:4317
```

### 采样率

- **默认**: `parentbased_always_on`（100% 采样，适合开发环境）
- **压测时**: 修改 `otel.tracer.sampler` 为 `parentbased_traceidratio` 并设置 ratio=0.1（10%）
  ```yaml
  otel:
    tracer:
      sampler: parentbased_traceidratio
      traceidratio: 0.1
  ```

### 在 Grafana 中查看

1. 添加 Jaeger 数据源：Grafana → 配置 → 数据源 → Jaeger → URL: `http://micro-course-jaeger:16686`
2. 在 Explore 中选择 Jaeger 数据源，按服务名搜索 `micro-course-api`

### Maven 依赖

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
    <version>2.10.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>2.10.0</version>
</dependency>
```

## 负载压测

### 安装 k6

```bash
brew install k6
```

### 压测脚本

5 个核心场景脚本位于 `scripts/load-test/`：

| 脚本 | 场景 | 并发 | 持续时间 |
|------|------|------|----------|
| `course-list-loadtest.js` | 课程列表查询 | 100 | 5min |
| `enrollment-loadtest.js` | 选课超卖验证 | 50 | 3min |
| `checkout-loadtest.js` | 结算+支付流程 | 30 | 3min |
| `video-stream-loadtest.js` | 视频流播放 | 200 | 5min |
| `login-loadtest.js` | 登录认证 | 100 | 5min |

### 运行示例

```bash
# 课程列表压测（快速验证，30s）
k6 run -e BASE_URL=http://localhost:8089 scripts/load-test/course-list-loadtest.js --duration 30s

# 压测 + 导出 JSON 结果
k6 run --summary-export=results.json -e BASE_URL=http://localhost:8089 scripts/load-test/course-list-loadtest.js
```

### CI 集成

压测作为 nightly cron 运行，不在 PR 流程中触发。
