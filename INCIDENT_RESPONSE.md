# 事件响应 Runbook

> **目的**：5xx 错误率 / p99 延迟 / 用户投诉 → 立即诊断 + 响应 + 修复
> **L0 铁律**：用户体验至上（用户问题 = 最高优先级）
> **配合**：[DEPLOYMENT_DECISION.md](DEPLOYMENT_DECISION.md) + [ROLLBACK_RUNBOOK.md](ROLLBACK_RUNBOOK.md) + [MONITORING_CHECKLIST.md](MONITORING_CHECKLIST.md)

---

## 一、事件分类

### P0 - 立即响应（5 分钟内）
- 服务完全不可用（5xx > 5%）
- 核心功能失败（登录/支付/课程播放 0% 成功）
- 安全事件（数据泄露/未授权访问/注入）
- 数据库主节点宕机

### P1 - 30 分钟内
- 部分功能降级（5xx 1-5%）
- p99 延迟 >2s
- ≥5 用户投诉同类问题
- 外部依赖失败（TTS API / 邮件服务）

### P2 - 4 小时内
- 性能下降（p99 >500ms 但 <2s）
- 单个功能问题（上传失败/导出失败）
- 1-4 用户投诉

### P3 - 24 小时内
- UI 优化
- 性能微调
- 用户体验细节

---

## 二、事件响应流程

### T+0s: 检测
```
- 监控告警自动触发
- 用户报告（客服/应用内反馈）
- SRE on-call 接收告警
```

### T+30s: 确认（不要 5 分钟内"再观察"）
```
1. 看监控 dashboard（5xx 错误率 + p99 延迟）
2. 看错误日志
3. 确认是 P0/P1（不靠感觉，按上面分类）
4. 决定：是否需要 ROLLBACK_RUNBOOK 立即回滚
```

### T+1m: 通知
```
P0: SRE + 总工程师 + 团队群 + 高管群
P1: SRE on-call + 产品 on-call
P2: SRE 团队群
P3: 内部工单系统
```

### T+5m: 决策
```
总工程师 + SRE + 产品 → 决策：
- P0 立即回滚（ROLLBACK_RUNBOOK.md 阶段 1）
- P1 降级到上一阶段
- P2 修复并发布补丁
- P3 排期修复
```

### T+15m: 启动修复
```
1. 启动 ROLLBACK_RUNBOOK（如果决定回滚）
2. 或启动 hotfix（修复 + 单测 + PR + 紧急 merge）
3. 监控关键指标
```

### T+30m: 用户沟通
```
P0/P1:
- 客服通知所有受影响用户
- 应用内 banner 提示
- 必要时产品暂停服务公告

P2/P3:
- 内部工单
- 不打扰用户
```

### T+2h: 复盘
```
1. 写 INCIDENT_POSTMORTEM.md
   - 时间线：触发 → 决策 → 修复 → 恢复
   - 根因：什么导致问题
   - 影响：用户数 / 收入损失 / 数据丢失
   - 改进：避免下次同类问题
2. 更新 DEPLOYMENT_DECISION.md / ROLLBACK_RUNBOOK.md
3. 通知利益相关方
```

---

## 三、常见事件类型 + 响应

### 3.1 数据库连接池耗尽

**症状**：5xx 飙升 + 慢查询 + 连接超时
**诊断**：
```bash
psql -h prod-pg -U readonly -c "
SELECT state, count(*) 
FROM pg_stat_activity 
WHERE state IS NOT NULL
GROUP BY state;"

# 看 hikari
curl -s http://actuator:8080/actuator/metrics/hikaricpool.connections.active | jq
```
**修复**：
1. 找到泄漏 SQL（pg_stat_activity 中 long-running queries）
2. kill 长查询
3. 临时扩容 hikari max-size

### 3.2 TTS API 失败

**症状**：音频生成 5xx + 用户投诉"听不到声音"
**诊断**：
```bash
# 看 TTS 调用日志
grep "TTS" /var/log/micro-course/app.log | tail -50

# 测试 TTS API 直接连通
curl -X POST "$MINIMAX_API/t2a_v2" -H "Authorization: $KEY" -d '{"text":"test"}'
```
**修复**：
1. 切到备选 TTS（Qwen3-TTS 本地）
2. 或重试（指数退避）
3. 或补偿用户（赠送会员时长）

### 3.3 Redis 连接失败

**症状**：session 丢失 + 5xx + 用户被迫重新登录
**诊断**：
```bash
redis-cli -h prod-redis ping
redis-cli -h prod-redis info clients
```
**修复**：
1. 重启 Lettuce 连接池
2. 切换 Redis 主从
3. 检查网络 ACL

### 3.4 Flyway migration 失败

**症状**：启动失败 + 5xx + 应用未启动
**诊断**：
```bash
psql -h prod-pg -U readonly -c "
SELECT version, success, installed_on
FROM flyway_schema_history 
WHERE version >= '300'
ORDER BY installed_rank DESC LIMIT 10;"
```
**修复**：
1. 修 V 脚本（idempotent）
2. 用 repair 同步 schema_history

### 3.5 上传失败（大文件）

**症状**：上传 5xx + multipart 错误
**诊断**：
```bash
# 看磁盘
df -h /data/uploads

# 看 nginx 上传大小限制
grep "client_max_body_size" /etc/nginx/nginx.conf

# 看应用 multipart 配置
grep "multipart" application.yml
```
**修复**：
1. 清理磁盘
2. 调大 client_max_body_size
3. 调大 spring.servlet.multipart.max-file-size

---

## 四、升级路径

```
T+0s    监控告警 / 用户报告
T+30s   SRE on-call 确认
T+1m    通知（按 P0/P1 群发）
T+5m    总工程师决策（回滚 or 修复）
T+15m   启动修复（hotfix / 回滚）
T+30m   用户沟通（如需）
T+2h    复盘（INCIDENT_POSTMORTEM.md）
T+24h   改进措施落地
```

---

## 五、值班交接

每班次值班：
- SRE on-call：1 人
- DBA on-call：1 人
- 产品 on-call：1 人

交接清单：
- [ ] 当前已知问题（HOT-ISSUES.md）
- [ ] 进行中的变更（DEPLOYMENT_IN_PROGRESS.md）
- [ ] 上一次事件总结
- [ ] 监控告警状态
- [ ] 紧急联系人

---

**总工程师命令**：
- 用户问题 = 最高优先级
- 任何 P0/P1 → 立即响应
- L0 铁律 = 唯一不可妥协 → 真实用户用得顺畅 = 我们最高使命

**实施日期**：2026-08-09
**总工程师**：viber coding 项目
