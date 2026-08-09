# 生产监控检查清单

> **目的**：5xx 错误率 + p99 延迟 + 用户体验 24/7 实时监控
> **L0 铁律**：用户体验至上（用户投诉 = 监控告警阈值）
> **配合**：[DEPLOYMENT_DECISION.md](DEPLOYMENT_DECISION.md) + [ROLLBACK_RUNBOOK.md](ROLLBACK_RUNBOOK.md)

---

## 一、5xx 错误率（最高优先级）

### Prometheus 查询

```promql
# 5xx 错误率（最近 5 分钟）
rate(http_requests_total{job="micro-course",status=~"5.."}[5m])
/
rate(http_requests_total{job="micro-course"}[5m])

# 各 5xx 细分（最近 5 分钟）
sum by (status) (rate(http_requests_total{job="micro-course",status=~"5.."}[5m]))

# 总 5xx 错误计数（最近 1 小时）
sum(increase(http_requests_total{job="micro-course",status=~"5.."}[1h]))
```

### 告警阈值

| 级别 | 阈值 | 告警目标 |
|------|------|---------|
| P0 | > 1%（5 分钟内） | SRE on-call + 总工程师 |
| P1 | 0.1-1% | SRE on-call |
| P2 | 0.05-0.1% | 团队群通知 |

### 修复路径

```bash
# 1. 看 5xx 来源（按 path 分组）
grep 'status="5' /var/log/micro-course/access.log | awk -F'"' '{print $9}' | sort | uniq -c | sort -rn

# 2. 看 5xx 来源（按 stack trace）
grep -A 5 "ERROR" /var/log/micro-course/app.log | head -50

# 3. 立即触发回滚（如果 >1%）
# 按 ROLLBACK_RUNBOOK.md 阶段 1 决策
```

---

## 二、p99 延迟

### Prometheus 查询

```promql
# p99 延迟（最近 5 分钟）
histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket{job="micro-course"}[5m])))

# p95 / p90 延迟
histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket{job="micro-course"}[5m])))
histogram_quantile(0.90, sum by (le) (rate(http_request_duration_seconds_bucket{job="micro-course"}[5m])))

# 按 path 分组 p99（找最慢端点）
histogram_quantile(0.99, sum by (le, path) (rate(http_request_duration_seconds_bucket{job="micro-course"}[5m])))
```

### 告警阈值

| 级别 | 阈值 | 告警目标 |
|------|------|---------|
| P0 | > 1s（连续 5 分钟） | SRE on-call + 总工程师 |
| P1 | 500ms-1s | SRE on-call |
| P2 | 200ms-500ms | 团队群通知 |

### 修复路径

```bash
# 1. 看最慢端点
curl -s "http://prometheus:9090/api/v1/query?query=histogram_quantile(0.99,sum%20by(le,path)(rate(http_request_duration_seconds_bucket{job=\"micro-course\"}[5m])))" | jq

# 2. 查数据库慢查询
psql -h prod-pg -U readonly microcourse_prod -c "
SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements 
WHERE mean_exec_time > 1000
ORDER BY mean_exec_time DESC LIMIT 20;"

# 3. 看 JVM GC / 堆
curl -s "http://actuator:8080/actuator/metrics/jvm.memory.used" | jq
```

---

## 三、业务指标

### 3.1 用户关键路径（必须监控）

| 指标 | 目标 | 告警阈值 |
|------|------|---------|
| 用户登录成功率 | > 99.5% | < 99% 告警 |
| 课程播放成功率 | > 99% | < 98% 告警 |
| 课件上传成功率 | > 95% | < 90% 告警 |
| TTS 生成成功率 | > 99% | < 95% 告警 |
| 支付成功率 | > 99% | < 95% 告警 |

### 3.2 Prometheus 业务查询

```promql
# 登录成功率
sum(rate(user_login_attempts_total{result="success"}[5m]))
/
sum(rate(user_login_attempts_total[5m]))

# 课程播放成功率
sum(rate(course_playback_total{result="success"}[5m]))
/
sum(rate(course_playback_total[5m]))

# TTS 生成成功率
sum(rate(tts_generation_total{result="success"}[5m]))
/
sum(rate(tts_generation_total[5m]))
```

### 3.3 实时业务 Dashboard

```
Grafana Dashboard: "Micro-Course Production Overview"
- 5xx Rate (last 1h): timeseries
- p99 Latency (last 1h): timeseries
- Login Success Rate: stat
- Course Playback Success: stat
- TTS Generation Success: stat
- Active Users (now): stat
- Active Sessions (now): timeseries
```

---

## 四、用户体验指标

### 4.1 用户反馈监控

```
监控源：
- 应用内反馈入口（每用户提交）
- 客服工单系统
- 应用商店评论（每日爬取）
- 社交媒体（微博/微信）

关键指标：
- 投诉数 / 投诉率（按小时）
- NPS（每周）
- 客服工单 SLA 达成率
- 用户流失率（按天）
```

### 4.2 自动埋点

| 事件 | 关键属性 | 触发告警 |
|------|---------|---------|
| 用户卡在某个页面 > 30s | page_url, duration | 任何卡顿 |
| PPT/HTML 播放失败 | course_id, error_code | 失败率 >5% |
| TTS 失败但没重试 | audio_id, reason | 任何 |
| 视频上传失败 | video_id, error | 任何 |

---

## 五、基础设施监控

### 5.1 服务健康

```bash
# 服务可用性（HTTP 200 比例）
sum(rate(http_requests_total{job="micro-course",status="200"}[5m]))
/
sum(rate(http_requests_total{job="micro-course"}[5m]))

# JVM 堆内存
jvm.memory.used / jvm.memory.max

# GC 暂停时间
rate(jvm_gc_pause_seconds_sum[5m])

# 数据库连接池
hikaricpool_connections_active / hikaricpool_connections_max

# Redis 连接池
lettuce_connections_active
```

### 5.2 告警阈值

| 指标 | P0 | P1 |
|------|----|----|
| 服务可用性 | < 99% | < 99.5% |
| JVM 堆使用率 | > 90% | > 80% |
| DB 连接池 | > 90% | > 75% |
| Redis 连接池 | > 90% | > 75% |
| GC 暂停 P99 | > 500ms | > 200ms |
| 磁盘使用率 | > 90% | > 80% |

---

## 六、告警配置（Prometheus + AlertManager）

### alertmanager.yml 关键路由

```yaml
route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'sre-team'
  routes:
    - match:
        severity: 'critical'
      receiver: 'sre-oncall'
      group_wait: 10s
      repeat_interval: 1h
    - match:
        severity: 'warning'
      receiver: 'sre-team'
      repeat_interval: 24h
```

### 告警模板

```yaml
groups:
  - name: micro-course-critical
    rules:
      - alert: MicroCourseHighErrorRate
        expr: |
          sum(rate(http_requests_total{job="micro-course",status=~"5.."}[5m]))
          / sum(rate(http_requests_total{job="micro-course"}[5m])) > 0.01
        for: 5m
        labels:
          severity: critical
          team: sre
        annotations:
          summary: "5xx 错误率 > 1%（5 分钟内）"
          description: "立即执行 ROLLBACK_RUNBOOK.md 阶段 1"

      - alert: MicroCourseHighLatency
        expr: |
          histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket{job="micro-course"}[5m]))) > 1
        for: 5m
        labels:
          severity: critical
          team: sre
        annotations:
          summary: "p99 延迟 > 1s（连续 5 分钟）"
          description: "立即按 ROLLBACK_RUNBOOK.md 阶段 1 决策"
```

---

## 七、值班与升级

| 时间 | 告警目标 | 响应 SLA |
|------|---------|---------|
| 0-5 分钟 | SRE on-call 自动接收 | 立即响应 |
| 5-15 分钟 | 总工程师升级 | 决策参与 |
| 15-30 分钟 | 产品 / DBA | 业务影响评估 |
| 30+ 分钟 | 高管 / PR | 公关沟通 |

---

## 八、每日检查

```
每日 09:00 检查清单
- [ ] 5xx 错误率（24h 平均）< 0.1%
- [ ] p99 延迟（24h 平均）< 500ms
- [ ] 用户登录成功率 > 99.5%
- [ ] TTS 生成成功率 > 99%
- [ ] 课程播放成功率 > 99%
- [ ] 用户投诉 < 5
- [ ] 数据库连接池使用 < 80%
- [ ] Redis 内存使用 < 70%
- [ ] 磁盘使用 < 80%
- [ ] CI 5/5 全绿
- [ ] 新 PR 全部 merged
```

---

**总工程师命令**：
- 监控 = 用户体验的眼睛
- 异常 = 立即响应（不需要"再观察一下"）
- L0 铁律 = 唯一不可妥协 → 用户体验 24/7 守护

**实施日期**：2026-08-09
**总工程师**：viber coding 项目
