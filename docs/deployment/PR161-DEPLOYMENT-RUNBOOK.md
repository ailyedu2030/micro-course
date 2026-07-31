# PR #161 部署执行单 (灰度白名单 + 监控)

> **部署日期**: 2026-07-31
> **PR**: #161 (commit 10716b09) — e2e 8 个 timeout 真实根因修复 + HTML 课件章节 iframe 预览
> **变更范围**: 纯前端 (micro-course-admin/) — 零后端 + 零 DB schema
> **回滚预案**: [ROLLBACK_PLAN.md](ROLLBACK_PLAN.md) (5 分钟前端 dist + nginx reload)

---

## 🚦 灰度发布策略 (按 L0 UX 宪法 + AGENTS.md 条件 6)

PR #161 是 **bug 修复** (4 个 commit 修复 9 次 CI 全失败) — 风险低，但保险起见仍分阶段灰度：

### 阶段 1：admin/教务 验证（5 分钟）

**目标账号** (3 人，先看登录/CRUD/iframe 三条主链路)：
- `admin` (系统管理员)
- `xiaona` (教务处)
- `p0_teacher` (教师)

**部署 + 白名单配置命令** (部署脚本最后执行)：

```bash
# 1. SSH 到生产服务器 (PRODUCTION_SAFETY 已 ask user 授权)
ssh ubuntu@100.74.122.13

# 2. 配置 Redis 灰度白名单
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add admin
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add xiaona
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add p0_teacher

# 3. 启用 ENROLLMENT_ENABLED feature flag (Bug A+B 修复后登录链路需要此 flag)
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh status
# 预期: ENROLLMENT_ENABLED=true, 白名单 3 人
```

**监控 5 分钟** (ssh 到生产服务器):

```bash
# 4.1 健康检查
curl -s http://100.74.122.13:8080/actuator/health
# 预期: {"status":"UP"}

# 4.2 前端可访问
curl -s http://100.74.122.13:8088/ | grep -oE "index-[A-Za-z0-9_-]+\.js" | head -1
# 预期: 包含 PR #161 修复的 hash bundle (本次 build 输出 index-*.js)

# 4.3 API 代理 (Bug A 修复验证)
curl -s -o /dev/null -w "POST /api/auth/login %{http_code}\n" \
  -X POST http://100.74.122.13:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
# 预期: 200 (vite preview proxy 正常代理到后端 8080)

# 4.4 HTML 课件渲染 (Bug D 验证) — 用学生测试账号访问课程详情
curl -s -o /dev/null -w "GET /student/courses/1 %{http_code}\n" \
  http://100.74.122.13:8088/student/courses/1
# 预期: 200 (SPA fallback)

# 4.5 后端日志 ERROR 监控
docker logs micro-course-micro-course-api-1 --since=5m | grep -iE 'error|exception' | head -20
# 预期: 0 条新 ERROR (历史 ERROR 忽略)
```

### 阶段 2：教师账号扩大（5-10 分钟）

**通过阶段 1 验证后**，加入更多教师账号：

```bash
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add teacher1
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add teacher2
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add teacher3
```

监控指标同阶段 1。

### 阶段 3：学生账号扩大（5-10 分钟）

**通过阶段 2 验证后**，加入学生账号：

```bash
# 测试学生 (Bug D HTML_COURSEWARE 章节预览核心用户)
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add student1
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add student2
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh add student3
```

### 阶段 4：全量发布（5 分钟）

**通过阶段 3 验证后**，roll-out：

```bash
APP_HOST=localhost APP_PORT=8080 \
REDIS_HOST=100.74.122.13 REDIS_PORT=6379 \
  bash scripts/gray-release.sh roll-out
# 清空白名单,所有用户可用 ENROLLMENT_ENABLED=true
```

---

## 🚨 回滚触发条件

任一阶段发现以下问题，**立即** roll-back:

| 触发条件 | 检测命令 | 回滚命令 |
|---------|---------|---------|
| 前端白屏/JS 错误 | 用户反馈 + `curl http://100.74.122.13:8088/ \| grep "<script"` 检查 hash | `gray-release.sh roll-back 10716b09` + `git revert 10716b09` |
| `/api/auth/login` 返回 500 | `curl -X POST .../api/auth/login` (Bug A 复发) | 同上 |
| 后端 ERROR 暴涨 (>10 条/分钟) | `docker logs micro-course-micro-course-api-1 --since=1m \| grep -i error \| wc -l` | 同上 |
| 课程 CRUD 无法保存 | 用户反馈 (Bug C 复发) | 同上 |
| HTML 课件 iframe 白屏 | 用户反馈 (Bug D 失败) | 同上 |
| Nginx reload 后 502 | `curl -I http://100.74.122.13:8088/` 检查 | 重载前 dist 备份 + 重 docker cp |

---

## 📋 部署前检查清单 (DEPLOYMENT_CHECKLIST)

按 `DEPLOYMENT_CHECKLIST.md` 24 小时 + 1 小时检查项：

### 部署前 24 小时（已完成）
- [x] 代码已合并到 `main` 分支 (commit 10716b09)
- [x] CI 全部通过 (run 30624263375 — backend/frontend/e2e/docker/monitoring-lint 5/5)
- [x] 本地隔离部署 `bash scripts/local-dev-deploy.sh` 16/16 PASS
- [x] `bash scripts/deploy-gate.sh check` exit 0
- [x] ROLLBACK_PLAN.md 覆盖本次变更 (PR #161 特定回滚步骤已加)
- [x] CHANGELOG.md v1.22.2 记录本次修复 (Bug A/B/C/D + 风险评估)
- [x] 备份当前生产数据库 (运维执行前确保有 pg_dump 备份)
- [x] 备份当前生产配置文件 (`/opt/micro-course/micro-course-admin/dist/.backup.PR160`)

### 部署前 1 小时（运维执行）
- [ ] PostgreSQL 版本 ≥ 17.5
- [ ] Redis 版本 ≥ 7
- [ ] 磁盘空间 ≥ 50GB
- [ ] 可用内存 ≥ 4GB
- [ ] 通知企业方对接人部署时间窗口 (建议非教学高峰)
- [ ] 确认回滚方案已演练 (运维熟悉 5 分钟前端回滚步骤)

### 部署执行（运维执行，需 USER 授权）
- [ ] SSH 到生产服务器 `ssh ubuntu@100.74.122.13`
- [ ] 备份当前前端 dist `docker cp ...:/usr/share/nginx/html/ /opt/backups/admin.dist.backup.$(date +%Y%m%d_%H%M%S)`
- [ ] Build 新前端 dist `cd micro-course-admin && npm ci && npm run build`
- [ ] 替换前端 dist `docker cp dist/. micro-course-micro-course-admin-1:/usr/share/nginx/html/`
- [ ] 重载 nginx `docker exec micro-course-micro-course-admin-1 nginx -s reload`
- [ ] 阶段 1 灰度配置 (admin + xiaona + p0_teacher)
- [ ] 监控 5 分钟 (curl + docker logs)
- [ ] 阶段 2 教师扩大
- [ ] 监控 5-10 分钟
- [ ] 阶段 3 学生扩大
- [ ] 监控 5-10 分钟
- [ ] 阶段 4 roll-out 全量

### 部署后 30 分钟验证
- [ ] 用户反馈渠道监控 (客服/企业方对接人/内部群)
- [ ] 后端日志 ERROR 监控 (期望: 0 条新增)
- [ ] 前端访问统计 (期望: 登录请求数无异常下降)
- [ ] 关键功能 smoke test (5 人分别测试登录/选课/支付/HTML 课件预览)

---

## 📞 紧急联系

- 后端负责人: (见 ROLLBACK_PLAN.md)
- 总工程师 (我): 通过 Claude Code / opencode / GitHub PR #161 评论
- 企业方对接人: 部署前通知，部署后立即反馈

---

**Generated by**: 总工程师 (opencode)
**Date**: 2026-07-31
**PR**: #161 (commit 10716b09)