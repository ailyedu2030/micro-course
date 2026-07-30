# 微课平台部署 Runbook

> 部署人员必备检查清单 (R1-R14 完整流程)
> 最后更新: 2026-07-31 (R14 完结)
> 紧急联系: docs/incidents/ 或 see ROLLBACK_PLAN.md

---

## 1. 部署前 24 小时 (准备阶段)

### 1.1 代码状态确认
```bash
cd /Users/jackie/微课平台

# 1. 确认 working tree clean
git status   # 应显示: nothing to commit, working tree clean

# 2. 确认领先 origin/main N 个 commits (R1-R14 应 9-11 个)
git log --oneline origin/main..HEAD | wc -l
# 应: 9-11

# 3. 5 个本地 gate 全过 (verify-secrets 预期 exit 1)
bash precheck.sh                                              # 期望: ✅
python3 scripts/contract-audit.py                            # 期望: ✅ 全部通过
python3 scripts/check-references-sync.py --strict             # 期望: ✅ references 已对齐
bash scripts/verify-secrets.sh --strict                      # 期望: ❌ exit 1 (12 占位符待替换)
cd micro-course-api && mvn -B test                            # 期望: BUILD SUCCESS, 1123/1123
cd micro-course-admin && npm run test:unit                    # 期望: 49 files / 205 tests
```

### 1.2 部署环境就绪
```bash
# 1. 隔离环境 (dev) 健康检查
curl http://localhost:8089/actuator/health    # 应: {"status":"UP"}
curl -I http://localhost:8088/                  # 应: 200 (admin frontend)

# 2. 隔离环境真端到端 (admin + student)
curl -X POST http://localhost:8089/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'   # 应: 200, token ~250 字符
curl -X POST http://localhost:8089/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"student","password":"student123"}' # 应: 200, token ~250 字符
```

---

## 2. 部署密钥替换 (9 处 CHANGE_ME)

`verify-secrets.sh --strict` 检测到 9 处占位符 (alertmanager.yml 6 处 + application.yml 3 处), **必须**在生产部署前替换。

### 2.1 应用密钥 (application.yml 3 处)
```bash
# production 部署前用环境变量覆盖
export REDIS_PASSWORD="<从 vault 提取的 32+ 字符密码>"
export JWT_SECRET="<从 vault 提取的 64+ 字符 HS512 secret>"
export PAYMENT_CALLBACK_SECRET="<从 vault 提取的 64+ 字符 HMAC secret>"

# 部署后验证: 重启应用, 启动时不会用占位符, 否则会报 "key 强度不足"
cd /opt/micro-course/api
java -jar target/micro-course-api-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.redis.password="${REDIS_PASSWORD}" \
  --jwt.secret="${JWT_SECRET}" \
  --payment.callback-secret="${PAYMENT_CALLBACK_SECRET}"
```

### 2.2 监控告警 (alertmanager.yml 6 处)
```bash
# 方法 A（推荐）: 通过 .env 文件注入
# 复制 alerts.env.example 并填入真实值:
cp monitoring/alertmanager/alerts.env.example alerts.env
# 编辑 alerts.env 填入真实 webhook URL / API key / 密码
# 然后 docker compose 会自动载入:
#   docker compose up -d alertmanager
#  entrypoint.sh 会在启动时自动替换 CHANGE_ME 占位符

# 方法 B: 直接设置环境变量
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/T1234/B5678/abcdefghijkl"
export PAGERDUTY_SERVICE_KEY="<PagerDuty Events API v2 integration key>"
export SMTP_PASSWORD="<SMTP auth password 或 App Password>"

# 部署 alertmanager（entrypoint.sh 自动替换占位符）
docker compose up -d alertmanager
```

### 2.3 验证替换
```bash
# 重跑 verify-secrets, 应显示 0 占位符 (跳过 CHANGE_ME_IN_PRODUCTION 字面量的注释)
bash scripts/verify-secrets.sh
# 期望: "✅ 未发现 CHANGE_ME / 占位符" (排除 deployment env vars)
```

---

## 3. 部署 (staging → production)

### 3.1 staging 部署
```bash
# 1. push 11 commits 到 origin/main
git push origin main

# 2. 触发 CI 5/5 gate (GitHub Actions 自动跑)
#    - precheck (含 verify-secrets.sh 默认 advisory)
#    - backend test (含 mvn verify + JaCoCo 30%)
#    - frontend test (含 vitest + lint)
#    - docker build (含 docker compose build)
#    - e2e test (含 Playwright)
#    - monitoring-lint (含 promtool + amtool)
#    - secrets-check (verify-secrets.sh --strict, 9 占位符应已替换)
#    - references-sync (check-references-sync.py, 应过)
# 期望: 5/5 门禁 + 2/2 新增 job 全 PASS

# 3. staging 部署 (按 ROLLBACK_PLAN.md step 5-7)
# 部署到 staging 镜像, 跑 24 小时 soak test
# 通过后, 灰度白名单 (xiaona 等测试账号)

# 4. 监控 5 分钟
# - 看后端日志无 ERROR
# - 看 5xx 比例
# - 看 P0 告警 (PostgresDown) 不应触发 (数据库 up)
```

### 3.2 production 部署
```bash
# 1. 生产门禁检查 (R1+ 已实现)
bash scripts/deploy-gate.sh check
# 期望: ✅ (本地 4 小时前已通过 verify-secrets)

# 2. 灰度发布
bash scripts/gray-release.sh add xiaona      # 加入测试账号
bash scripts/gray-release.sh status          # 查看灰度状态

# 3. 全量发布 (灰度 5 分钟无异常)
bash scripts/gray-release.sh roll-out

# 4. 监控
# - 5 分钟: 前端 console 0 errors
# - 5xx 错误率 < 0.1%
# - 数据库连接池 < 80% 利用率
```

---

## 4. 部署后验证 (production)

### 4.1 立即验证
```bash
# 后端 health
curl -I https://microcourse.ailyedu.cn/actuator/health    # 应: 200

# admin 真登录
curl -X POST https://microcourse.ailyedu.cn/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<admin>","password":"<vault>"}'
# 应: 200 + JWT

# 关键页面 SPA fallback (R9 修复)
curl -I https://microcourse.ailyedu.cn/admin/dashboard        # 应: 200
curl -I https://microcourse.ailyedu.cn/student/courses        # 应: 200
```

### 4.2 监控告警
- Slack 收到 P0 告警 (PostgresDown, ActiveConnectionsHigh)
- PagerDuty 收到 P0 通知
- 见 `docs/operations/ALERT_SOP.md` 处置流程

### 4.3 24/7 on-call
- 见 `docs/operations/ALERT_SOP.md` on-call 轮值
- 4 小时内响应 P0, 8 小时内响应 P1

---

## 5. 紧急回滚

```bash
# 见 ROLLBACK_PLAN.md 5-30 分钟回滚流程
# R1-R14 共 11 个 commit, 关键回滚点:
# - bf92d52c (R11 Phase 6 完结) - 包含 alertmanager README + ALERT_SOP
# - 9f8f9950 (R1 P0 修复) - 包含 fail-closed + FileAccessController + ProfileController + V324/V325
# - 5db2fcad (Phase 5 全栈审查) - 之前 stable release 候选
```

---

## 6. 关键资源链接

- **审计报告**: `docs/audit/r1-r11-full-audit-report.md` (11 轮完整总结)
- **告警 SOP**: `docs/operations/ALERT_SOP.md` (P0-P3 处置流程)
- **回滚预案**: `ROLLBACK_PLAN.md` (5/30 分钟流程)
- **数据契约**: `.agents/skills/microcourse/references/` (3 张引用视图)
- **CLAUDE/AGENTS**: `.claude/CLAUDE.md` (架构 + 治理)
- **memory**: `memories/scratchpad/R13-Phase6-完结.md` (R13 详细)

---

## 7. 常见问题

### Q: verify-secrets.sh 报 12 处占位符但生产是真有 alertmanager 和 slack 怎么破?
A: verify-secrets.sh 检测 9 处是设计意图（alertmanager.yml 6 处 + application.yml 3 处），strict 模式在 CI 部署门禁阻断。生产部署前必须按 §2 替换 6 处 + 3 处应用密钥。替换后重跑应显示 0 占位符（除非个别 env 未设置导致 entrypoint.sh 未替换）。

### Q: 部署后 5xx 错误率突然上升怎么办?
A: 见 ROLLBACK_PLAN.md 5 分钟回滚. 5 步:
1. 备份当前 jar
2. 切换上一 stable tag (例如 v1.22.0)
3. 滚动重启
4. 健康检查
5. 通知用户

### Q: 部署后 P0 告警 (PostgresDown) 触发怎么处置?
A: 见 docs/operations/ALERT_SOP.md 的 P0 处置 playbook. 通常 5 分钟内响应, 启动 fallback 模式 (读模式), 通知 DBA.

### Q: admin 端点返 404 怎么办?
A: R9 已修 admin nginx SPA fallback. 如果 404 重新出现, 检查:
1. nginx config 中是否有 `try_files $uri $uri/ /index.html;`
2. 反向代理是否传 host header
3. 客户端是否真的访问的是 nginx 而非后端 Spring Boot
