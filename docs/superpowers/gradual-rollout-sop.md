# 灰度发布 SOP · W31 治理

> **目的**: 任何新功能上线必须按 10% → 50% → 100% 阶梯推进, 避免全量雪崩.
> **依据**: 用户第 16 次授权铁律 "灰度发布用户占比需按照 10%→50%→100% 的阶梯式推进".

---

## 一、灰度发布 3 阶段

### 阶段 1: 白名单 (10%)
- **范围**: 内部测试用户 + 已确认的 1-2 个真实客户
- **目标**: 验证核心功能 + 监控错误率
- **时长**: 24-72 小时
- **通过标准**:
  - 错误率 < 0.1%
  - p99 < 200ms
  - 无 P0/P1 反馈
  - 业务数据正确性 100%

### 阶段 2: 半量 (50%)
- **范围**: 50% 用户 (按用户 ID hash 取模)
- **目标**: 验证流量压力 + 兼容性
- **时长**: 48-72 小时
- **通过标准**: 同阶段 1 + 无客户投诉

### 阶段 3: 全量 (100%)
- **范围**: 全量用户
- **目标**: 全面上线
- **时长**: 永久
- **回滚预案**: 5 分钟内降级回 50%

---

## 二、Feature Flag 配置

### 配置项 (`application.yml`)
```yaml
feature:
  courseware-delete:
    enabled: true
    rollout-percent: 100     # 10 / 50 / 100
    whitelist-users: [35, 99]   # sytafe (35) + 测试账号
    exclude-courses: []          # 黑名单课程 ID
```

### 实现位置
- `mc:feature:courseware_delete` Redis key
- 启动时读取 + 运行时通过 admin API 调整

### 调整命令
```bash
# 10% 灰度
redis-cli SET mc:feature:courseware_delete '{"rollout":10,"whitelist":[35]}'

# 50% 灰度
redis-cli SET mc:feature:courseware_delete '{"rollout":50,"whitelist":[35]}'

# 全量
redis-cli SET mc:feature:courseware_delete '{"rollout":100}'

# 紧急回滚
redis-cli SET mc:feature:courseware_delete '{"rollout":0}'
```

---

## 三、上线检查清单

| 检查项 | 责任人 | 通过标准 |
|--------|--------|----------|
| 单元测试覆盖率 ≥ 90% | 总工程师 | PASS |
| 双人审核 + 总工程师 approve | 总工程师 + 模块 owner | 2 approved |
| precheck 通过 22+ 项 | 总工程师 | 22/22 |
| 集成测试 100% PASS | 总工程师 | 全绿 |
| OpenAPI 契约校验 | 总工程师 | 11 endpoint PASS |
| 压测报告 (健康检查 QPS > 5000) | 总工程师 | PASS |
| 慢查询 < 0.1% | DBA | PASS |
| 灰度白名单准备 | 总工程师 | ready |
| 回滚方案演练 | 总工程师 + SRE | 5 min 内可降级 |
| 客户告知邮件 | 客户成功 | 发送 |

---

## 四、回滚 SOP (P0 紧急)

### 触发条件
- 错误率 > 1% (5 分钟内)
- 核心 API 5xx > 5%
- P0 客户反馈 (1 起即触发)

### 执行步骤
1. **立即降级** (60 秒内)
   ```bash
   redis-cli SET mc:feature:courseware_delete '{"rollout":0}'
   ```
2. **通知客户** (5 分钟内)
   - Slack 频道 #incident
   - 邮件给灰度白名单用户
3. **根因分析** (30 分钟内)
   - 抓取 traceId + log
   - 复现步骤
4. **修复 + 重新灰度** (24 小时内)
   - 走完整 PR 流程
   - 重新从 10% 开始

---

## 五、上线后监控 (24-72h)

| 指标 | 目标 | 告警阈值 |
|------|------|----------|
| 5xx 错误率 | < 0.01% | > 0.1% |
| p99 响应时间 | < 200ms | > 500ms |
| 业务成功率 | > 99.9% | < 99% |
| 用户投诉 | 0 | ≥ 1 (P0) |

---

## 六、签发

本 SOP 由总工程师全权负责执行与监督.

**承诺**: 任何 P0/P1 异常 15 分钟内响应 + 5 分钟内降级 + 24 小时内出事故复盘.

签发时间: 2026-07-20
签发人: 总工程师