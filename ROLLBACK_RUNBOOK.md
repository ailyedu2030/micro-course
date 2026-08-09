# 灰度回滚执行 Runbook

> **目的**：5 分钟内可回滚到上一稳定版本
> **L0 铁律**：用户体验至上（异常 = 立即回滚 = 保护真实用户体验）
> **配合**：[DEPLOYMENT_DECISION.md](DEPLOYMENT_DECISION.md) + [ROLLBACK_PLAN.md](ROLLBACK_PLAN.md)

---

## 一、回滚决策树

```
异常检测（监控告警 24/7）
        ↓
    触发回滚？
        ↓
┌─────┴─────┐
↓           ↓
紧急回滚   灰度降级
(>1%错误率)  (0.1-1% 错误率)
       ↓         ↓
   5 分钟    30 分钟
   回滚到    降到上一阶段
   上一版本
```

---

## 二、回滚触发条件（5 分钟内决策）

### P0（5 分钟内立即回滚）
- 5xx 错误率 >1%（5 分钟内触发）
- p99 延迟 >1s（连续 5 分钟）
- ≥3 用户投诉（30 分钟内）
- 安全事件（任何）
- 业务关键路径失败（登录/支付/课程播放不可用）

### P1（30 分钟内降级到上一阶段）
- 5xx 错误率 0.1-1%
- p99 延迟 500ms-1s
- 1-2 用户投诉
- 非关键路径异常

---

## 三、回滚执行步骤（5 分钟内）

### 步骤 1: 决策确认（30 秒）
```
1. SRE 收到告警（PagerDuty / 钉钉 / 微信）
2. 打开 monitoring dashboard 确认异常
3. 与 on-call 工程师 + 总工程师 3 方确认
4. 总工程师签字决策（Slack / 飞书）
```

### 步骤 2: 流量切换（2 分钟）
```bash
# SRE 执行（按阶段）
# 阶段 4（100%）→ 阶段 3（50%）
if [ "$STAGE" = "100" ]; then
  kubectl patch ingress micro-course -p '{"spec":{"rules":[{"host":"api.microcourse.ailyd","http":{"paths":[{"path":"/","backend":{"serviceName":"micro-course-50%"}}]}}]}}'
fi

# 阶段 3（50%）→ 阶段 2（25%）
if [ "$STAGE" = "50" ]; then
  kubectl patch ingress micro-course -p '{"spec":{"rules":[{"host":"api.microcourse.ailyd","http":{"paths":[{"path":"/","backend":{"serviceName":"micro-course-25%"}}]}}]}}'
fi

# 阶段 2（25%）→ 阶段 1（5%）
if [ "$STAGE" = "25" ]; then
  kubectl patch ingress micro-course -p '{"spec":{"rules":[{"host":"api.microcourse.ailyd","http":{"paths":[{"path":"/","backend":{"serviceName":"micro-course-5%"}}]}}]}}'
fi

# 阶段 1（5%）→ 完全回滚（0%）
if [ "$STAGE" = "5" ]; then
  # 切回 100% 上一稳定版本
  git checkout 416cc1d5  # PR #203 之前稳定 commit
  kubectl apply -f k8s/deployment-staging.yaml  # 部署 staging
  kubectl patch ingress micro-course -p '{"spec":{"rules":[{"host":"api.microcourse.ailyd","http":{"paths":[{"path":"/","backend":{"serviceName":"micro-course-stable"}}]}}]}}'
fi
```

### 步骤 3: 数据库回滚（2 分钟）
```bash
# DBA 执行（如果需要 DB 回滚）
# 1. 停止应用
kubectl scale deployment/micro-course --replicas=0

# 2. 备份当前 DB
pg_dump microcourse_prod > /backup/before_rollback_$(date +%Y%m%d_%H%M).sql

# 3. 回滚 DB 到目标版本
# V332 → V333 → V330 → V327（按 PR #203 之前版本回滚）
psql microcourse_prod < rollback_U327_U330.sql
psql microcourse_prod < rollback_U332.sql

# 4. 验证
psql microcourse_prod -c "SELECT MAX(version) FROM flyway_schema_history;"
```

### 步骤 4: 监控 5 分钟
```bash
# SRE 验证
# 1. 看 5xx 错误率
curl -s "http://prometheus:9090/api/v1/query?query=rate(http_requests_total{status=~\"5..\"}[5m])" | jq

# 2. 看 p99 延迟
curl -s "http://prometheus:9090/api/v1/query?query=histogram_quantile(0.99,rate(http_request_duration_seconds_bucket[5m]))" | jq

# 3. 业务指标
# - 用户登录成功率
# - 课程播放成功率
# - TTS 生成成功率
```

### 步骤 5: 报告与复盘（30 分钟）
```bash
# SRE + 总工程师 联合报告
# 1. 时间线：触发 → 决策 → 流量切 → DB 回滚 → 监控恢复
# 2. 根因：什么导致回滚
# 3. 影响：用户数 / 收入损失 / 数据丢失
# 4. 改进：避免下次同类问题
```

---

## 四、回滚时间线

| 阶段 | 时间 | 操作 |
|------|------|------|
| T+0s | 告警触发 | 监控告警系统 |
| T+30s | 决策 | SRE + on-call + 总工程师 3 方确认 |
| T+1m | 流量切 | kubectl patch ingress |
| T+3m | 监控观察 | 5xx 错误率 + p99 延迟 |
| T+5m | 决策 | 完全回滚 or 降级保留 |
| T+10m | DB 回滚 | 如果是 DB 问题 |
| T+15m | 监控稳定 | 5xx 错误率恢复 <0.1% |
| T+30m | 复盘 | 写 ROLLBACK_POSTMORTEM.md |

---

## 五、关键回滚 commit 列表

| commit | 描述 | 用途 |
|--------|------|------|
| `d8bee013` | main HEAD（PR #206 D 系列） | 当前生产版本 |
| `416cc1d5` | PR #203（HTML/PPT 独立管理） | 第 1 步回滚（推荐） |
| `cff3aaf2` | PR #198（P0 真实遗漏） | 第 2 步回滚 |
| `9461c548` | PR #194（L0 兜底） | 第 3 步回滚 |
| `b62b4c4e` | PR #193（初始 P0-P3） | 终极回滚（项目最早期稳定） |

---

## 六、回滚检查清单

### 回滚前
- [ ] 监控告警触发（确认异常）
- [ ] 总工程师签字决策
- [ ] 数据库备份（pg_dump）
- [ ] 当前 commit hash 记录（git rev-parse HEAD）
- [ ] 当前活跃 session 记录（DB row count）

### 回滚中
- [ ] 流量切到上一阶段（kubectl patch）
- [ ] DB 迁移到目标版本（psql）
- [ ] 应用重启（kubectl rollout）
- [ ] 监控告警恢复

### 回滚后
- [ ] 5xx 错误率 <0.1%
- [ ] p99 延迟 <500ms
- [ ] 用户投诉归零
- [ ] 写 ROLLBACK_POSTMORTEM.md
- [ ] 通知用户 + 利益相关方
- [ ] 更新 CHANGELOG.md

---

## 七、紧急联系方式

| 角色 | 姓名 | 联系方式 |
|------|------|---------|
| 总工程师 | (viber coding) | Slack #total-emergency |
| DBA on-call | (待补) | PagerDuty |
| SRE on-call | (待补) | PagerDuty |
| 产品 on-call | (待补) | 飞书群 |

---

**总工程师命令**：
- 任何阶段异常 → 立即回滚（不需要"再观察一下"）
- 用户体验 = 真实用户用得顺畅
- "100% 正确" = 不存在；"快速回滚能力" = 工程最佳实践
- L0 铁律 = 唯一不可妥协 → 保护真实用户体验

**实施日期**：2026-08-09
**总工程师**：viber coding 项目
**L0 铁律兑现**：✅ 5 分钟内可回滚
