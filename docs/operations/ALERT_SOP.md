# 微课平台 · 告警运维 SOP

> 告警响应标准操作程序
> 版本: v1.0 | 更新: 2026-07-31
> 关联文档:
>   - [R1-R11 审计报告](../audit/r1-r11-full-audit-report.md) §3 关键P0/P1修复历史
>   - [Alertmanager 部署说明](../../monitoring/alertmanager/README.md)

---

## 1. 告警严重度分级

| 级别 | 定义 | 响应时间 | 通知通道 | 升级窗口 |
|------|------|---------|---------|---------|
| **P0** | **核心功能不可用** / 数据安全 / 用户支付失败 | 15 分钟 | Slack #incident + PagerDuty + 短信 | 30 分钟 |
| **P1** | **功能降级** / 非核心模块故障 / 用户可感知性能下降 | 1 小时 | Slack #alerts + 邮件 | 4 小时 |
| **P2** | **运维告警** / 资源水位告警 / 非功能性故障 | 24 小时 | Slack #monitoring + 每日摘要 | — |
| **P3** | **通知/信息** / 低风险事件 / 周度汇总 | 下一工作日 | 邮件周报 | — |

### 1.1 P0 告警条件

以下条件任一触发即为 P0：
- API 网关 5xx 错误率 > 5%（持续 5 分钟）
- 用户登录/注册成功率 < 95%
- 支付回调成功率 < 90%
- PostgreSQL 连接池耗尽（HikariPoolExhausted）
- Redis 不可用
- 生产数据库复制延迟 > 30 秒
- 磁盘使用率 > 90%
- 容器 OOMKill 事件

### 1.2 P1 告警条件

- API 响应 P95 延迟 > 2s
- PostgreSQL 活跃连接数 > 80% 上限
- 慢查询 > 10 条/分钟
- 视频转码队列积压 > 100 条
- 容器 CPU/内存使用率 > 85%

### 1.3 P2 告警条件

- PostgreSQL 活跃连接数 > 60% 上限
- 磁盘使用率 > 75%
- 证书即将过期（30 天内）
- 日志错误率突增

---

## 2. 通知路径

```
┌──────────────────────────────────────────────────────────┐
│                    告警触发                              │
│            Prometheus → Alertmanager                     │
└────────────────────┬─────────────────────────────────────┘
                     │
         ┌───────────┴────────────┐
         │    路由匹配优先级       │
         │  P0 > P1 > P2 > P3     │
         └───────────┬────────────┘
                     │
     ┌───────────────┼───────────────┐
     │               │               │
  P0/P1             P2              P3
     │               │               │
  ┌──┴──┐         ┌──┴──┐         ┌──┴──┐
  │Slack│         │Slack│         │ 邮件 │
  │#inc │         │#mon │         │周报  │
  │#alrt│         │itoring│        └─────┘
  └──┬──┘         └─────┘
     │
  ┌──┴──┐
  │PD   │
  │电话 │
  └─────┘
```

### 2.1 Slack 频道职责

| 频道 | 用途 | 监控人员 |
|------|------|---------|
| `#incident` | P0 事件通报、根因同步 | 全体 on-call |
| `#alerts` | P1 告警通知、降级事件 | 当值工程师 |
| `#monitoring` | P2 每日汇总、运维通知 | 全体开发团队 |

### 2.2 PagerDuty 集成

- **P0 告警**自动创建 PagerDuty Incident
- 触发电话/短信通知 on-call 工程师
- 自动升级机制: 15 分钟未确认 → 升级至二级 on-call
- 集成密钥配置: `monitoring/alertmanager/alertmanager.yml` → `pagerduty_configs.service_key`

---

## 3. 24/7 On-Call 流程

### 3.1 轮值安排

| 角色 | 职责 | 轮值周期 | 人数 |
|------|------|---------|------|
| **一级 On-Call** | 首次响应 P0/P1，执行 playbook | 每周轮换 | 2 人 |
| **二级 On-Call** | 升级处理，需架构/业务决策 | 每月轮换 | 1 人 |
| **总工程师** | 重大事故决策、生产放行 | 7×24 | 1 人 |

### 3.2 响应时间要求

| 级别 | 响应 | 确认 | 首次恢复 | 根因定位 | 复盘报告 |
|------|------|------|---------|---------|---------|
| P0 | 15 min | 15 min | 1 hr | 4 hr | 24 hr |
| P1 | 30 min | 1 hr | 4 hr | 24 hr | — |
| P2 | 24 hr | — | — | — | — |

**响应定义**: 在对应频道回复 `/ack` 确认收到告警
**确认定义**: 开始执行 playbook，在频道更新状态 `[处理中: 正在检查 X]`
**恢复定义**: 服务恢复正常，用户影响解除（可临时方案）

### 3.3 响应流程

```
[告警触发]
  │
  ├─ 一级 On-Call 收到通知
  │   │
  │   ├─ ✅ 15分钟内确认 → 执行 Playbook
  │   │                    │
  │   │                    ├─ ✅ 恢复 → 更新频道 → 写复盘
  │   │                    └─ ❌ 超时/无法恢复 → 升级二级On-Call
  │   │
  │   └─ ❌ 超时未确认 → 自动升级至二级 On-Call
  │
  ├─ 二级 On-Call 介入
  │   │
  │   ├─ 协调多团队（后端/DB/运维）
  │   ├─ 决策：降级/回滚/灰度
  │   └─ 如需总工程师决策 → 升级
  │
  └─ 总工程师决策
      ├─ 生产放行/回滚审批
      └─ 事故复盘会议召集
```

### 3.4 交接流程

- 每轮值结束前，离值工程师在 `#monitoring` 频道发布交接摘要
- 内容包括: 本周告警记录、未完成事件、已知问题
- 新值班工程师确认接管后，交接完成

---

## 4. 告警处置 Playbook

### 4.1 PostgresDown — PostgreSQL 不可用

**严重度**: P0

**现象**: HikariPool 连接超时 / API 5xx / 管理后台白屏

**处置步骤**:
```bash
# 1. 检查容器状态
docker ps | grep postgres
docker logs micro-course-postgres-1 --tail 200 | grep -i error

# 2. 检查资源
docker stats micro-course-postgres-1 --no-stream
df -h | grep postgres

# 3. 检查日志
docker exec micro-course-postgres-1 tail -100 /var/lib/postgresql/data/log/postgresql-*.log

# 4. 尝试重启
docker restart micro-course-postgres-1

# 5. 等待 30s 后验证
sleep 30 && docker exec micro-course-postgres-1 pg_isready

# 6. 如持续失败 → 恢复备份 → 升级
```

**回滚方案**:
```bash
# 从最近的 pg_dump 恢复
pg_restore -h localhost -U microcourse -d microcourse \
  /backups/microcourse_$(date +%Y%m%d).dump --clean --if-exists
```

---

### 4.2 PostgresActiveConnectionsHigh — 连接数高

**严重度**: P1（>80%）/ P2（>60%）

**检查**:
```bash
# 查看当前连接分布
docker exec micro-course-postgres-1 psql -U microcourse -c "
SELECT state, count(*) FROM pg_stat_activity GROUP BY state;
"

# 按数据库/用户统计
docker exec micro-course-postgres-1 psql -U microcourse -c "
SELECT datname, usename, state, count(*)
FROM pg_stat_activity GROUP BY datname, usename, state
ORDER BY count(*) DESC;
"

# 找出最长运行的查询
docker exec micro-course-postgres-1 psql -U microcourse -c "
SELECT pid, now() - pg_stat_activity.query_start AS duration,
       query, state
FROM pg_stat_activity
WHERE state != 'idle'
ORDER BY duration DESC
LIMIT 10;
"
```

**处置**:
- 如存在阻塞查询: `SELECT pg_terminate_backend(pid);`
- 如正常流量高峰: 考虑增加 `spring.datasource.hikari.maximum-pool-size`
- 如连接泄漏: 检查应用层 `HikariPool` 配置是否合理关闭连接

---

### 4.3 PostgresSlowQueries — 慢查询

**严重度**: P1（>10/min） / P2（>5/min）

**检查**:
```bash
docker exec micro-course-postgres-1 psql -U microcourse -c "
SELECT query, calls, mean_time, rows,
       shared_blks_hit, shared_blks_read
FROM pg_stat_statements
WHERE mean_time > 1000
ORDER BY mean_time DESC
LIMIT 20;
"
```

**处置**:
- 检查查询计划: `EXPLAIN ANALYZE <慢查询>`
- 确认是否缺少索引
- 如临时紧急: `pg_cancel_backend(pid)` 取消查询
- 长期修复: 在 `docs/数据字典.md` 补充索引定义 → 创建 Flyway migration

---

### 4.4 ApiDown — API 不可达

**严重度**: P0

**检查**:
```bash
# 健康检查
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/actuator/health
curl -s -o /dev/null -w "%{http_code}" http://localhost:8088/admin/dashboard

# 检查容器
docker ps | grep micro-course-api
docker logs micro-course-api-1 --tail 100 | grep -i error

# 检查 nginx（admin）
docker logs micro-course-admin-1 --tail 100 | grep -i error
```

**处置**:
- 如容器退出 → 检查 OOM / JVM crash logs
- 如 nginx 配置错误 → 检查 `try_files` SPA fallback（历史事故 R9）
- 如证书过期 → 更新 SSL 证书

---

### 4.5 HikariPoolExhausted — 连接池耗尽

**严重度**: P0

**现象**: `HikariPool-1 - Thread starvation or clock leap detected` / API 超时

**根因可能**:
1. DB 慢查询阻塞连接释放
2. 应用层连接泄漏（未调用 `close()`）
3. DB 最大连接数不足

**检查**:
```bash
# 查看 DB 当前连接
docker exec micro-course-postgres-1 psql -U microcourse -c "
SELECT count(*) FROM pg_stat_activity;
"

# 检查 HikariPool 指标（如已暴露到 Prometheus）
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

**处置**:
1. 优先终结空闲查询: `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state = 'idle' AND pid <> pg_backend_pid();`
2. 暂时增加连接池上限（立即生效无需重启）
3. 排查应用层连接泄漏

---

### 4.6 RedisDown — Redis 不可用

**严重度**: P0（影响 JWT 黑名单 + 缓存）

**检查**:
```bash
docker ps | grep redis
docker logs micro-course-redis-1 --tail 50
docker exec micro-course-redis-1 redis-cli ping
```

**处置**:
- 重启 Redis: `docker restart micro-course-redis-1`
- 恢复期间 JWT 黑名单失效（所有已签发 token 在原生过期前可用）
- 尽快恢复后验证黑名单功能

---

### 4.7 HighMemoryUsage — 内存告警

**严重度**: P1（>85%）/ P2（>75%）

**处置**:
```bash
# 查看内存分布
docker stats --no-stream

# 分析 JVM 堆使用
jcmd $(pgrep -f micro-course-api) GC.heap_info

# 如持续高位 → 增加容器 memory limit
# 或调整 JVM -Xmx
```

---

## 5. 告警静默/抑制

### 5.1 Alertmanager 静默（Silence）

```bash
# 创建静默（维护窗口用）
amtool silence add \
  --alertmanager.url=http://localhost:9093 \
  --author="姓名" \
  --comment="计划内维护 - 升级 XX 服务" \
  --duration=2h \
  alertname=PostgresActiveConnectionsHigh

# 查看静默列表
amtool silence list --alertmanager.url=http://localhost:9093

# 过期静默
amtool silence expire <silence_id> --alertmanager.url=http://localhost:9093
```

**静默规则**:
- 最长静默时间: 4 小时（超过需二级 On-Call 批准）
- 静默必须附带 `--comment` 说明原因
- 静默到期后自动恢复警报
- 计划内维护提前 24 小时在 `#monitoring` 通告

### 5.2 inhibit_rules（自动抑制）

已在 `alertmanager.yml` 配置的抑制规则：
- `ApiDown` P0 → 抑制同 job 所有 warning 告警（避免告警风暴）
- `HikariPoolExhausted` → 抑制同 job 的 `PostgresActiveConnectionsHigh`

如需添加抑制:
1. 编辑 `monitoring/alertmanager/alertmanager.yml` 的 `inhibit_rules` 段
2. 运行 `amtool check-config alertmanager.yml` 验证
3. 重启 Alertmanager

---

## 6. 事故复盘

### 6.1 复盘要求

| 级别 | 复盘文档 | 时间要求 |
|------|---------|---------|
| P0 | 必须 | 恢复后 24 小时内 |
| P1 | 建议 | 恢复后 1 周内 |
| P2 | 可选 | — |

### 6.2 复盘模板

```markdown
# 事故复盘 YYYY-MM-DD-<事件名>

## 基本信息
- **日期**: YYYY-MM-DD HH:mm ~ HH:mm
- **影响范围**: [用户/功能/区域]
- **严重度**: P0/P1
- **处理人**: [姓名]

## 时间线
| 时间 | 事件 |
|------|------|
| HH:mm | 告警触发 |
| HH:mm | 一级 On-Call 确认 |
| HH:mm | 开始执行 playbook |
| HH:mm | 服务恢复 |
| HH:mm | 根因定位完成 |

## 根因
[详细根因分析，引用日志/截图]

## 处置过程
[详细的处置步骤记录]

## 预防措施
1. [具体改进项] — [负责人] — [截止日期]
2. [具体改进项] — [负责人] — [截止日期]
```

---

## 7. 监控工具

| 组件 | 端口 | 用途 |
|------|------|------|
| Prometheus | 9090 | 指标存储 + 告警规则 |
| Alertmanager | 9093 | 告警路由 + 通知 |
| Grafana | 3000 | 可视化面板 |

### 快速检查命令

```bash
# Prometheus 告警状态
curl -s 'http://localhost:9090/alerts?silenced=false&inhibited=false' | python3 -m json.tool

# Alertmanager 当前告警
curl -s http://localhost:9093/api/v2/alerts | python3 -m json.tool

# Alertmanager 配置验证
amtool check-config /etc/alertmanager/alertmanager.yml

# Grafana 面板列表
curl -s http://admin:admin@localhost:3000/api/search | python3 -m json.tool
```

---

## 8. 附录

### 8.1 故障树决策

```
告警触发
├── 是否 P0? → 进入 P0 流程
├── 是否影响用户? → 进入 P1 流程
├── 是否资源告警? → 进入 P2 流程
└── 其他 → 进入 P3 流程

处理中
├── 已知问题 → 查 Runbook
├── 已知但在修复中 → 更新状态
├── 新问题且可回滚 → 回滚到上一版本
└── 新问题且不可回滚 → 临时修复 + 升级
```

### 8.2 联系人

| 角色 | 联系方式 |
|------|---------|
| 一级 On-Call | Slack @oncall-tier1 |
| 二级 On-Call | Slack @oncall-tier2 |
| 总工程师 | Slack @总工程师 |
| 运维团队 | Slack #ops |

---

*文档版本: v1.0 | 维护者: 运维团队 | 更新: 2026-07-31*
