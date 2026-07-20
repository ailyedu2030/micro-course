# 全链路错误监控体系 · W33 治理

> **铁律**: 任何错误 (前端/后端/数据) 须实时感知 + 自动定级 + 立即处置.
> **依据**: 用户第 22 次授权铁律.
> **W33 范围**: 在 W31/W32 Prometheus + Grafana 基础上, 建立全链路覆盖.

---

## 一、错误监控分层架构

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 前端 (Vue 3 + Element Plus)                         │
│  - window.onerror 捕获 JS 异常                              │
│  - unhandledrejection 捕获 Promise 异常                      │
│  - Sentry-like 自建上报 (POST /api/frontend/error)            │
│  - 业务埋点 (4 面板工作台关键操作)                           │
└──────────────────┬──────────────────────────────────────────┘
                   │ HTTPS + X-Trace-Id
┌──────────────────┴──────────────────────────────────────────┐
│  Layer 2: 后端 (Spring Boot 3 + GlobalExceptionHandler)      │
│  - 全局异常捕获 (W30 已实现)                                │
│  - 业务错误码 (ErrorCode enum 9000-10003)                   │
│  - SLF4J + MDC traceId 自动关联                             │
│  - Logback ERROR log → Prometheus alert                      │
└──────────────────┬──────────────────────────────────────────┘
                   │ JDBC + HikariCP
┌──────────────────┴──────────────────────────────────────────┐
│  Layer 3: 数据链路 (PostgreSQL 17 + Flyway)                  │
│  - HikariCP 连接超时                                        │
│  - Flyway migration 失败                                    │
│  - 慢查询 > 100ms                                           │
│  - 死锁 / 锁等待                                            │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────┴──────────────────────────────────────────┐
│  Layer 4: 基础设施 (Redis + OSS + JVM)                       │
│  - Redis 连接失败                                           │
│  - OSS 上传/下载失败                                        │
│  - JVM OOM / 内存泄漏                                       │
│  - HikariCP 池耗尽                                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
              ┌─────────────────────────┐
              │  Prometheus + Grafana   │
              │  (W31/W32 已部署)       │
              └────────┬────────────────┘
                       ↓
              ┌─────────────────────────┐
              │  AlertManager (W33 新增) │
              │  → Slack / Email / SMS   │
              └─────────────────────────┘
```

---

## 二、错误自动定级规则

### P0 (核心阻断) — 立即触发
| 触发条件 | 检测方式 | 响应 SLA |
|---------|---------|---------|
| API 5xx 错误率 > 1% (5min 内) | Prometheus alert | 1h 修复 |
| 登录/鉴权失败率 > 5% | 业务埋点 | 1h 修复 |
| 课件加载失败率 > 2% | 前端埋点 | 1h 修复 |
| 音频流式 GET 404 率 > 5% | Prometheus | 1h 修复 |
| 数据库连接池耗尽 | HikariCP metric | 30min 修复 |
| 前端白屏 (JS 致命错误) | window.onerror | 1h 修复 |

### P1 (严重功能) — 24h 修复
| 触发条件 | 检测方式 | 响应 SLA |
|---------|---------|---------|
| 单 PPT 页音频不能生成 | 用户报告 | 24h 修复 |
| 部分用户报错 (e.g. IDOR 防御误伤) | 业务埋点 | 24h 修复 |
| 流式 GET 部分 404 | Prometheus | 24h 修复 |
| SQL 慢查询 > 0.1% | pg_stat_statements | 24h 修复 |
| 前端特定组件崩溃 (非白屏) | window.onerror | 24h 修复 |

### P2 (一般体验) — 当前迭代修复
| 触发条件 | 检测方式 | 响应 SLA |
|---------|---------|---------|
| 页面样式偏移 | 视觉回归 | 7 天 |
| 文案错误 | 静态扫描 | 7 天 |
| 交互卡顿 (1-3s) | RUM (Real User Monitoring) | 7 天 |
| 慢查询 < 0.1% 但 > 50ms | pg_stat_statements | 7 天 |

### P3 (体验优化) — 月度优化
| 触发条件 | 检测方式 | 响应 SLA |
|---------|---------|---------|
| 未触发的边缘场景 | 静态分析 | 30 天 |
| 可优化逻辑 | code review | 30 天 |
| Lighthouse 评分 < 90 | Lighthouse CI | 30 天 |

---

## 三、监控指标清单 (全链路 50+ 项)

### 3.1 前端监控 (10 项)
| 指标 | 收集方式 | 阈值 | 告警 |
|------|---------|------|------|
| JS 致命错误数 | window.onerror | > 0/小时 | P0 |
| Promise 拒绝数 | unhandledrejection | > 5/小时 | P1 |
| 白屏时间 | Performance API | > 3s | P0 |
| 首屏加载时间 (LCP) | Performance API | > 1.5s | P2 |
| 交互响应时间 (FID) | Performance API | > 100ms | P2 |
| API 请求失败率 | 业务埋点 | > 1% | P0 |
| 4xx 错误率 | 业务埋点 | > 5% | P1 |
| 业务操作成功率 | 业务埋点 | < 99% | P1 |
| 静态资源 404 | Resource Error | > 0/小时 | P2 |
| 路由切换时长 | 业务埋点 | > 1s | P3 |

### 3.2 后端监控 (20 项)
| 指标 | 收集方式 | 阈值 | 告警 |
|------|---------|------|------|
| API 5xx 错误率 | Spring Boot | > 0.1% | P0 |
| API 4xx 错误率 | Spring Boot | > 5% | P1 |
| 核心接口 p99 | Histogram | > 200ms | P1 |
| 业务异常数 | GlobalExceptionHandler | > 10/分钟 | P0 |
| 业务错误码 9000-10003 分布 | 业务埋点 | 异常飙升 | P0/P1 |
| 鉴权失败率 | JwtFilter | > 5% | P0 |
| 业务日志 ERROR 数 | Logback | > 50/分钟 | P0 |
| Hibernate 慢查询 | actuator | > 100ms | P1 |
| 业务事务回滚数 | actuator | > 5/分钟 | P1 |
| 数据校验失败数 | @Valid | 异常飙升 | P2 |
| API 鉴权拒绝数 | SecurityFilter | 异常飙升 | P1 |
| 队列消费失败 | RabbitMQ (未来) | > 0 | P1 |
| Quartz 任务失败 | actuator | > 0 | P1 |
| ThreadPool 队列堆积 | actuator | > 80% | P1 |
| JVM OOM | jvm_memory | 0 容忍 | P0 |
| GC 暂停时间 | jvm_gc | > 1s | P2 |
| 线程死锁数 | actuator | > 0 | P0 |
| 异常堆栈哈希数 | 业务埋点 | 异常飙升 | P1 |
| 跨服务调用失败 | OpenFeign (未来) | > 0 | P0 |
| 第三方 API 失败 (DeepSeek/MiniMax) | 业务埋点 | > 1% | P0 |

### 3.3 数据链路监控 (10 项)
| 指标 | 收集方式 | 阈值 | 告警 |
|------|---------|------|------|
| PostgreSQL 慢查询率 | pg_stat_statements | > 0.1% | P1 |
| 死锁数 | pg_stat_database | > 0 | P0 |
| 活动连接数 | pg_stat_activity | > 80% | P0 |
| 主从延迟 (未来) | pg_stat_replication | > 5s | P0 |
| 表膨胀率 | pg_stat_user_tables | > 30% | P2 |
| 索引未使用 | pg_stat_user_indexes | 3+ 月未用 | P3 |
| HikariCP 等待连接 | actuator | > 50ms | P1 |
| HikariCP 池耗尽 | actuator | > 95% | P0 |
| Flyway 校验失败 | logs | 0 容忍 | P0 |
| 长事务 (> 60s) | pg_stat_activity | > 0 | P0 |

### 3.4 基础设施监控 (10 项)
| 指标 | 收集方式 | 阈值 | 告警 |
|------|---------|------|------|
| Redis 内存使用 | redis_exporter | > 80% | P2 |
| Redis 连接数 | redis_exporter | > 80% | P1 |
| Redis 慢查询 | redis-cli | > 10ms | P1 |
| Redis down | redis_exporter | 0 容忍 | P0 |
| OSS 上传失败 | 业务埋点 | > 0.1% | P1 |
| OSS 下载失败 | 业务埋点 | > 0.1% | P1 |
| Docker 容器 down | node-exporter | 0 容忍 | P0 |
| CPU 使用率 | node-exporter | > 80% | P1 |
| 内存使用率 | node-exporter | > 85% | P1 |
| 磁盘空间 | node-exporter | > 85% | P1 |

---

## 四、告警分级路由

### P0 告警
- **渠道**: Slack #incident + Email + SMS (总工程师 24h 待命)
- **响应**: 1h 内必须有人响应
- **升级**: 30min 未响应 → 升级到项目负责人

### P1 告警
- **渠道**: Slack #alerts + Email
- **响应**: 2h 内必须有人响应
- **升级**: 4h 未响应 → 升级到 P0

### P2 告警
- **渠道**: Slack #monitoring + 每日邮件摘要
- **响应**: 当周迭代修复

### P3 告警
- **渠道**: 每周报告
- **响应**: 月度优化

---

## 五、AlertManager 配置 (W33 新增)

`monitoring/alertmanager/alertmanager.yml`:

```yaml
global:
  smtp_smarthost: 'smtp.example.com:587'
  smtp_from: 'alerts@micro-course.com'

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'p0-oncall'
      continue: false
    - match:
        severity: warning
      receiver: 'p1-oncall'

receivers:
  - name: 'default'
    slack_configs:
      - channel: '#monitoring'
        title: '微课平台告警'
        text: '{{ .CommonAnnotations.summary }}'

  - name: 'p0-oncall'
    slack_configs:
      - channel: '#incident'
        title: '🚨 P0 严重告警'
    email_configs:
      - to: 'total-engineer@micro-course.com'
    pagerduty_configs:
      - service_key: '<PAGERDUTY_KEY>'

  - name: 'p1-oncall'
    slack_configs:
      - channel: '#alerts'
        title: '⚠️ P1 警告'
    email_configs:
      - to: 'oncall@micro-course.com'
```

---

## 六、SLO/SLI 目标

| SLI | SLO 目标 | 测量周期 | 责任人 |
|-----|---------|---------|--------|
| API 可用性 | ≥ 99.99% | 月度 | 总工程师 |
| API p99 响应时间 | ≤ 200ms | 周度 | 总工程师 |
| 业务成功率 | ≥ 99.9% | 月度 | 总工程师 |
| 用户投诉率 | ≤ 0.1% 订单 | 月度 | 总工程师 |
| 错误预算消耗 | ≤ 25% | 月度 | 总工程师 |

**错误预算 (Error Budget)**: 99.99% 可用性 = 月度可允许 4.3 分钟不可用.
每月初评估, 消耗 > 50% 冻结非关键发布, > 100% 启动 P0 应急流程.

---

## 七、签发

本监控体系由总工程师全权负责.

**承诺**: 任何 P0 错误 1h 响应, 4h 修复; P1 错误 2h 响应, 24h 修复; P2/P3 纳入迭代.

签发时间: 2026-07-20
签发人: 总工程师