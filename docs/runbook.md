# 运维 Runbook — 微课管理平台

**生成时间**: 2026-06-24
**目标读者**: 运维工程师 / SRE
**目标**: 出问题时,5 分钟内定位 + 处理

---

## 关键阈值 (SLO 草稿)

| 指标 | 健康 | 警告 | 告警 |
|------|------|------|------|
| `enrollment_total{result="error"}/enrollment_total` < 1% | 1-5% | > 5% |
| `enrollment_duration_seconds` P99 < 1s | 1-2s | > 2s |
| `enrollment_overcapacity_prevented_total` 增长 | 正常 | - | (用于观察选课满员趋势) |
| HikariCP active conn / max pool < 80% | 80-90% | > 90% |
| DB active conn / max_connections < 70% | 70-85% | > 85% |

---

## 紧急操作清单 (Runbook)

### 1. 选课超卖重新出现 (P0)
**症状**: 教师报告"我学生数对不上" / 学生投诉"同学比我晚选 30 秒还能进"

**立即行动**:
```bash
# 1. 关闭选课 (Feature Flag, 10 秒)
ssh prod
export ENROLLMENT_ENABLED=false
systemctl restart micro-course-api

# 2. 验证
curl -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"courseId":1}'
# 预期: {"code":1008, "message": "选课服务暂时不可用"}

# 3. 通知教务处/学生: 选课暂停维护
# 4. 查 metrics: enrollment_overcapacity_prevented_total 增速
# 5. 查 DB: SELECT COUNT(*) FROM enrollments WHERE course_id=? GROUP BY enrollment_status
```

**恢复** (修复代码后):
```bash
export ENROLLMENT_ENABLED=true
systemctl restart micro-course-api
# 监控: enrollment_total 应恢复增长
```

---

### 2. 数据库连接池耗尽
**症状**: enrollment_total{result="error"} 飙升至 20%+
**根因**: 通常是慢查询或行锁等待

**立即行动**:
```bash
# 1. 看活动连接
PGPASSWORD= psql -U postgres -c "SELECT count(*), state, query FROM pg_stat_activity GROUP BY state, query ORDER BY 1 DESC"

# 2. 找长查询 (>30s)
PGPASSWORD= psql -U postgres -c "SELECT pid, now()-query_start AS duration, query FROM pg_stat_activity WHERE state='active' AND now()-query_start > interval '30 seconds'"

# 3. 必要时 kill
PGPASSWORD= psql -U postgres -c "SELECT pg_terminate_backend(PID)"
```

**根因排查**:
- 看是否有未释放的事务 (事务方法没 commit/rollback)
- 看是否有长 SELECT (缺索引)
- 看连接池监控: HikariCP active=250, idle=0

---

### 3. Redis 故障
**症状**: 课程详情加载慢, 选课 API 慢

**立即行动**:
```bash
# 1. Redis 状态
redis-cli ping

# 2. 如果 Redis down 但 app 还在跑
# EnrollmentServiceImpl 已实现降级: 缓存失败 → 直连 DB
# 监控: enrollment_duration_seconds 上升但功能正常

# 3. 重启 Redis
brew services restart redis
```

---

### 4. 数据库迁移回滚 (V87/V88/V89)
**症状**: 升级后发现 schema/字段有问题

**回滚脚本** (sql):
```sql
-- V87: 撤销 tag.color
ALTER TABLE tags DROP COLUMN IF EXISTS color;

-- V88: 撤销 proposal.prerequisites
ALTER TABLE micro_specialty_proposals DROP COLUMN IF EXISTS prerequisites;

-- V89: 撤销 counselor_id 删除
-- 注意: 这是物理删除,如果已有数据会丢失!
ALTER TABLE classes ADD COLUMN IF NOT EXISTS counselor_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_classes_counselor ON classes(counselor_id);
ALTER TABLE classes ADD CONSTRAINT fk_classes_counselor FOREIGN KEY (counselor_id) REFERENCES users(id) ON DELETE SET NULL;

-- 然后删除 flyway schema history
DELETE FROM flyway_schema_history WHERE version IN ('87', '88', '89');
```

**前置检查**:
- V89 涉及物理删除,生产前必须备份 classes 表
- 回滚后需要在 30 天内重新应用迁移 (因为 V89 已删数据)

---

## Prometheus 抓取配置示例

```yaml
# /etc/prometheus/prometheus.yml
scrape_configs:
  - job_name: micro-course-api
    scrape_interval: 15s
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['micro-course-api:8080']
```

---

## 告警规则 (Prometheus AlertManager)

```yaml
groups:
- name: micro-course-api
  rules:
  - alert: EnrollmentHighErrorRate
    expr: sum(rate(enrollment_total{result="error"}[5m])) / sum(rate(enrollment_total[5m])) > 0.05
    for: 2m
    labels: { severity: critical }
    annotations:
      summary: "选课错误率 > 5%"
      runbook: "docs/business-audit/production-readiness.md#紧急操作清单"

  - alert: EnrollmentLatencyP99
    expr: histogram_quantile(0.99, rate(enrollment_duration_seconds_bucket[5m])) > 2
    for: 5m
    labels: { severity: warning }
    annotations: { summary: "选课 P99 > 2s" }

  - alert: HikariCPExhausted
    expr: hikaricp_connections_active / hikaricp_connections_max > 0.85
    for: 3m
    labels: { severity: warning }
    annotations: { summary: "数据库连接池使用率 > 85%" }

  - alert: FeatureFlagDisabled
    expr: changes(enrollment_overcapacity_prevented_total[5m]) == 0
    for: 10m
    labels: { severity: info }
    annotations: { summary: "选课指标无增长 — 可能 Feature Flag 已关闭" }
```

---

## 数据库备份策略

```bash
# 每日自动备份
pg_dump -Fc micro_course > /backup/micro_course_$(date +%Y%m%d).dump

# 恢复 (灾难恢复)
pg_restore -d micro_course_new /backup/micro_course_20260624.dump
```

**频率**: 每日 03:00 全量
**保留**: 30 天
**异地**: rsync 到 OSS

---

## 上线日检查清单

- [ ] 数据库备份已执行
- [ ] Feature Flag 默认开启 (ENROLLMENT_ENABLED=true)
- [ ] Prometheus 抓取规则已配置
- [ ] 告警规则已部署到 AlertManager
- [ ] on-call 人员已知晓 4 个紧急操作
- [ ] 灰度比例 10% → 50% → 100% (每隔 2 小时)
- [ ] 监控 enrollment_total 增长率
- [ ] 抽样核对 10 个学生选课记录 (DB 与 API 一致)

---

## 关键指标基准 (本次压测实测)

| 指标 | 200 并发 max=20 | 300 并发 max=30 | 300 并发 max=50 |
|------|----------------|----------------|----------------|
| 总耗时 | 6.2s | 2.9s | 0.9s |
| QPS | 32 | 102 | 348 |
| 错误率 | 1.5% | 0% | 0% |
| P99 耗时 | (待测) | (待测) | (待测) |

**结论**: 行级锁 + 池 250 在 300 并发下达到零错误率,足以应对开学选课。

---

## 决策流程

```
报错 → 看错误码 → 查 runbook → 执行操作 → 验证 → 通知
```

| 错误码 | 含义 | 操作 |
|-------|------|------|
| 1001-1007 | 登录失败 | 查登录接口 (本文档范围外) |
| 1008 | 选课服务不可用 | Feature Flag 已关停 (预期) |
| 6002 | 课程下有选课 | 用其他章节删除或联系教务 |
| 8001 | 选课记录不存在 | 数据问题,查 enrollment 表 |
| 17016+ | 微专业相关 | 查 Phase 14 文档 |

---

**签字: 出问题时先看这里,5 分钟内应该能定位。**

**仍未做** (P1):
- 死锁测试 (单行锁理论无,但需验证)
- DB 故障模式完整测试 (kill -9 PG, app 行为)
- SLO 文档正式化 (1 个月数据采样)
- 压测纳入 CI (PR 合并前自动跑 100 并发)
