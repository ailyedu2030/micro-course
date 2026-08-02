# 微课平台应急回滚预案

> 部署失败或重大故障时执行。优先 5 分钟应用层回滚，如数据库结构变更导致问题则执行 30 分钟回滚。
>
> **最后更新**: 2026-07-31 (PR #161: e2e 8 个 timeout 真实根因修复 + HTML 课件章节 iframe 预览)

---

## 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| **Bug J 增量修复** | 2026-08-02 | 1 个 commit (`0fd963ad` PR #171):<br>• **Bug J** 左侧导航文字对比度 (`--sidebar-text: #9ca3af → #e5e7eb` + Layout.vue fallback 一致). 修复后 contrast 从 7.30:1 (WCAG AA) 提升到 16.2:1 (WCAG AAA 完美). 用户视觉判断"清晰可读", L0 UX 宪法'体验至上'原则应用<br>(**纯 CSS, 零后端 + 零 DB 变更, 仅前端 dist 回滚 5 分钟即可**) |
| **Bug I 增量修复** | 2026-08-02 | 1 个 commit (`99112b6e` PR #168):<br>• **Bug I** 30 处业务 catch 块 `console.error` → `console.debug` (16 业务 Vue 组件). 保留 6 处核心 debug (App/useErrorHandler/logger/main/router). 修复后用户截图生产 console 干净, 业务错误仍通过 ElMessage.toast 给用户提示<br>• `precheck.sh` 加固 8 项 (4 项全新防再发检查: headers: {} 禁止 / axios 直接 import 禁止 / console.warn 检查 / 文档同步检查 / console.error 全局检查)<br>(**纯前端 + 1 个 precheck.sh, 零后端 + 零 DB schema 变更, 仅前端 dist 回滚 5 分钟即可**) |
| **Bug G/H 增量修复** | 2026-08-01 | 2 个 commit (`8902d0b0` PR #165) 修复生产 console 401 错误链:<br>• **Bug G** `src/utils/request.js:125` refresh 调用 `headers: {}` → 415, 改 `headers: { 'Content-Type': 'application/json' }` (axios 0.27+ 行为变更). 修复后 refresh 200 → 401 自动重试 → 用户无感知<br>• **Bug H** `src/utils/enums.js:156` fallback `console.warn` → `console.debug` (fallback 是设计预期, 噪音). 修复后生产 console 干净, fallback 行为不变<br>(**纯前端修复, 零后端 + 零 DB schema 变更, 仅前端 dist 回滚 5 分钟即可**) |
| **PR #161 e2e 真实根因修复** | 2026-07-31 | 4 个 commit (0a680a21 / 8b852e1b / 79948c40 / b5e79442) 修复 CI e2e 9 次全失败:<br>• **Bug A** `vite.config.js` 缺 `preview.proxy` → CI `npx vite preview --port 8088` 不代理 `/api` → ECONNREFUSED 500<br>• **Bug B** `main.js` 删 `ElMessage.config({ariaLive:'polite'})` → element-plus 2.14.x 移除该 API，阻断 `app.mount('#app')`<br>• **Bug C** `course-crud.spec.ts` selector 硬编码 i18n 不匹配（"课程标题"→"课程名称"，"创建课程"→"新增课程"，"已通过"→"审核通过"）<br>• **Bug D** `student/CourseDetail.vue` 实现 HTML_COURSEWARE 章节 iframe 预览（lazy load sections + iframe `sandbox=""` 严格模式 + 响应式 CSS）<br>(**纯前端修复，零 DB schema 变更，零后端 API 变更，仅应用层 + 前端 dist 回滚即可**) |
| Phase 6 候选发布 | 2026-07-25 | 教师模块收口：教师看板待办口径统一、成绩明细筛选与只读语义修复、教学班/课件保护补齐、视频上下文与重试链路回归、`local-dev-deploy.sh --keep` 复跑稳定性修复（**无 DB schema 变更，仅应用层与脚本层回滚**） |
| v1.22.0 | 2026-07-17 | 全链路审计修复：套餐购买/退款/展示 + 5 处后端状态过滤 + 6 处前端状态过滤（**无 DB schema 变更**，仅应用层回滚即可） |
| v1.21.1 | 2026-07-09 | 每教师独立 API Key（Hermes webhook 改造） |
| v1.21.0 | 2026-07-09 | Docker 部署适配（Hermes 共享 API Key） |
| **R11 audit+monitor** | 2026-07-30 | 12 轮全栈多专家审查+修复：auth fail-closed、文件越权、API Key 明文清空、OrderService 811→78 拆分、22 个 Controller size=10000→200 收敛、QuestionController size=100000→200 修 DoS、UserRetentionCleanupJob 加 orders 级联、ProfileController 拆分 alias 路由、Profile.vue i18n 化、vue-i18n vitest setup 全局 install 修 6 个月 pre-existing 15 个测试失败、verify-secrets.sh / check-references-sync.py / generate-missing-tables.py / add-viewonly-tables.py 部署工具、contract-audit 0/0 完全清零、JaCoCo 真实 45.29% 覆盖率、admin nginx SPA fallback 修复、alertmanager CHANGE_ME 命名优化。**V324 迁移清空 api_key 明文列（DB 必跑），V325 清理 V135 冗余唯一索引（DB 必跑）**（**部分回滚必须包含迁移**）|

## 5 分钟回滚（应用层）

当应用启动失败、接口大量报 500、或健康检查持续不通过时，执行应用层回滚。

### 生产环境信息

| 容器 | 宿主机 IP | 用途 |
|------|-----------|------|
| `micro-course-micro-course-api-1` | 100.74.122.13 | 后端 API (8080) |
| `micro-course-micro-course-admin-1` | 100.74.122.13 | 前端 Admin |
| `micro-course-postgres-1` | 100.74.122.13 | PostgreSQL (5432) |
| `micro-course-redis-1` | 100.74.122.13 | Redis (6379) |

### 步骤 1：备份当前问题版本

```bash
# 登录生产服务器
ssh ubuntu@100.74.122.13

# 备份当前运行中的 jar（以防需要回滚到当前版本）
docker cp micro-course-micro-course-api-1:/app/app.jar /tmp/app.jar.backup.$(date +%Y%m%d_%H%M%S)
```

### 步骤 2：回滚到上一个稳定版本

```bash
# 假设上一个稳定版本 jar 在 /tmp/ 下（部署时已备份）
# 例如回滚到 v1.20.2
docker cp /tmp/micro-course-api-1.0.0.jar.v1.20.2 micro-course-micro-course-api-1:/app/app.jar

# 优雅重启（不是 kill -9）
docker exec micro-course-micro-course-api-1 kill -s HUP 1

# 确认重启
sleep 5
docker logs micro-course-micro-course-api-1 --since=30s | grep -E "Started|ERROR|Exception"
```

### 步骤 3：验证回滚成功

```bash
# 健康检查
curl -s http://localhost:8080/actuator/health
# 预期: {"status":"UP"}

# 检查日志无新增 ERROR（最近 5 分钟）
docker logs micro-course-micro-course-api-1 --since=5m | grep -i error | head -20

# 前端也可检查
curl -s http://localhost:8088/ | head -5
```

---

## 30 分钟回滚（数据库层）

> ⚠️ **警告**：数据库层回滚风险较高，仅在确认 Flyway 迁移导致数据损坏或无法启动时执行。

### 触发条件

- Flyway 迁移执行后应用无法启动
- 迁移导致数据损坏（如误删数据、约束冲突）
- 需要立即恢复服务且应用层回滚无效

### 回滚前准备

```bash
# 1. 立即停止应用（防止写入更多数据）
docker exec micro-course-micro-course-api-1 kill -s TERM 1

# 2. 确认有完整备份可恢复
ssh ubuntu@100.74.122.13 "docker exec micro-course-postgres-1 pg_dump -U microcourse_user micro_course" > /tmp/micro_course_backup.$(date +%Y%m%d_%H%M%S).sql
```

### 回滚 V174 (hermes_course_mapping 表)

```sql
-- 连接到生产数据库
psql -h 100.74.122.13 -U microcourse_user -d micro_course

-- 删除 hermes_course_mapping 表（幂等操作）
DROP TABLE IF EXISTS hermmes_course_mapping;

-- 从 flyway_schema_history 中移除记录
DELETE FROM flyway_schema_history WHERE version = 'V174';
```

### 恢复数据库备份（最终手段）

```bash
# 从备份恢复
cat /tmp/micro_course_backup.YYYYMMDD_HHMMSS.sql | docker exec -i micro-course-postgres-1 psql -U microcourse_user -d micro_course
```

### 验证数据库回滚

```sql
-- 确认 hermes_course_mapping 表已删除
SELECT table_name FROM information_schema.tables WHERE table_name = 'hermes_course_mapping';
-- 应返回空

-- 确认 flyway 记录已移除
SELECT version, description FROM flyway_schema_history WHERE version = 'V174';
-- 应返回空
```

### 重启应用验证

```bash
docker exec micro-course-micro-course-api-1 kill -s HUP 1
sleep 10
curl -s http://localhost:8080/actuator/health
```

---

## 版本历史（最近 3 个版本）

| 版本 | 部署时间 | 变更 | 回滚命令 |
|------|----------|------|----------|
| **PR #161 e2e 真实根因修复** | 2026-07-31 | 4 个 commit (0a680a21 / 8b852e1b / 79948c40 / b5e79442) 修复 CI e2e 9 次全失败 + 新增 HTML 课件章节 iframe 预览（**纯前端变更，零后端 + 零 DB schema 变更**） | git: `git revert 10716b09 --no-edit && git push origin main` <br> frontend: `npm run build` 在 revert 后 → `docker cp dist/. micro-course-micro-course-admin-1:/usr/share/nginx/html/ && docker exec micro-course-micro-course-admin-1 nginx -s reload` <br> backend: **不变**（无后端 API 改动）<br> db: **不变**（无 Flyway 迁移）<br> redis: **不变**（无新缓存 key） |
| Phase 6 候选发布 | 2026-07-25 | PR #123：教师模块收口（教师看板 / 成绩明细 / 教学班 / 视频管理）+ 本地隔离部署复跑稳定性修复 | app/git: `git revert 812269c4 --no-edit && git push origin main` <br> frontend: 如已部署静态资源，恢复到上一稳定 `dist` 备份并 `nginx -s reload` <br> scripts: 恢复 `scripts/local-dev-deploy.sh` 到上一个稳定版本 |
| v1.22.0 | 2026-07-17 | PR #30: 全链路审计修复（套餐购买/退款/展示 + 5 处后端状态过滤 + 6 处前端状态过滤）| app: `docker cp /tmp/app.jar.backup.v1.21.3 micro-course-micro-course-api-1:/app/app.jar && docker exec micro-course-micro-course-api-1 kill -s HUP 1` <br> frontend: `docker cp /opt/micro-course/micro-course-admin/dist/.backup.v1.21.3 micro-course-micro-course-admin-1:/usr/share/nginx/html/ && docker exec micro-course-micro-course-admin-1 nginx -s reload` <br> git: `git revert d34c0e51 --no-edit && git push origin main` |

---

## PR #161 特定回滚步骤（前端 5 分钟回滚）

> **触发条件**: PR #161 部署后发现前端渲染异常（白屏 / 错位 / 功能失效），**不涉及后端**。

### 步骤 1：诊断（30 秒）

```bash
ssh ubuntu@100.74.122.13

# 1.1 容器健康
docker ps --filter name=micro-course --format "{{.Names}}: {{.Status}}"

# 1.2 前端静态资源版本（应包含 PR #161 修复的 hash bundle）
curl -s http://100.74.122.13/ | grep -oE "index-[A-Za-z0-9_-]+\.js" | head -3
# PR #161 修复后预期 hash: index-lri506Ya.js (build in 7.15s)
# 若仍是旧 hash (e.g. index-DPs9F_6s.js) → 前端未部署最新构建

# 1.3 e2e 模拟验证（在生产前端直接 curl）
curl -s -o /dev/null -w "POST /api/auth/login (vite preview proxy) HTTP %{http_code}\n" \
  -X POST http://100.74.122.13:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
# PR #161 修复后预期 HTTP 200 (vite preview proxy 正常)
# 旧版本 (未修复) 预期 HTTP 500 或 404
```

### 步骤 2：回滚前端 dist（2 分钟）

```bash
# 2.1 备份当前（异常）前端 dist
docker cp micro-course-micro-course-admin-1:/usr/share/nginx/html/ /tmp/admin.dist.backup.$(date +%Y%m%d_%H%M%S)
# 2.2 恢复上一稳定版本 dist (PR #161 之前的备份)
docker cp /opt/micro-course/micro-course-admin/dist/.backup.PR160 \
  micro-course-micro-course-admin-1:/usr/share/nginx/html/
# 2.3 重载 nginx (无需重启容器)
docker exec micro-course-micro-course-admin-1 nginx -s reload
```

### 步骤 3：验证回滚成功（1 分钟）

```bash
# 3.1 健康检查 (前端可访问)
curl -s http://100.74.122.13:8088/ | head -5
# 预期: <!DOCTYPE html><html lang="zh-CN"> ...

# 3.2 关键功能 smoke test
curl -s -o /dev/null -w "GET / %{http_code}\n" http://100.74.122.13:8088/
curl -s -o /dev/null -w "GET /login %{http_code}\n" http://100.74.122.13:8088/login
curl -s -o /dev/null -w "GET /student/courses %{http_code}\n" http://100.74.122.13:8088/student/courses
# 全应 200 (SPA fallback to index.html)
```

### 步骤 4：git revert（确保后续 CI 不再重新部署坏版本）

```bash
# 本地仓库
git revert 10716b09 --no-edit
git push origin main
# 触发 CI 重跑，验证 revert 后仍然 PASS
```

### 步骤 5：通知 + 复盘

- 通知企业方对接人（服务中断时长）
- 复盘根因（前端 Bug A/B/C/D 哪个具体触发）
- 更新 ROLLBACK_PLAN.md（如果发现新风险）

---

## PR #161 关键决策记录

| 决策 | 理由 |
|------|------|
| **纯前端回滚（不动后端/DB）** | 本次变更零后端 API 改动 + 零 Flyway 迁移 + 零 Redis key 新增 → 回滚复杂度最低 |
| **保留后端 jar 不变** | `micro-course-api/target/*.jar` 部署后保持原状（v1.22.0 + R11 audit+monitor），无需重启 API 容器 |
| **nginx reload（不是 kill -HUP 1）** | 前端静态资源是 nginx 服务，更新 dist 后 `nginx -s reload` 平滑重载，无需重启 admin 容器 |
| **PR #161 commit hash 10716b09** | merge commit 而非单个 fix commit，回滚精确到 PR 边界 |
| **回滚 5 分钟预算** | 纯前端 dist 替换 + nginx reload + smoke test 3 步，理论 ≤3 分钟，预留 2 分钟 buffer |

---

## 快速回滚脚本

```bash
#!/bin/bash
# quick_rollback.sh - 5分钟应用层回滚
# 用法: bash quick_rollback.sh <backup_file>
set -e

BACKUP_JAR=${1:-"/tmp/app.jar.backup.v1.20.2"}
CONTAINER="micro-course-micro-course-api-1"

echo "=== 开始快速回滚 ==="
echo "源文件: $BACKUP_JAR"

# 复制并重启
docker cp "$BACKUP_JAR" ${CONTAINER}:/app/app.jar
echo "[1/3] JAR 已复制"

docker exec ${CONTAINER} kill -s HUP 1
echo "[2/3] 应用已重启 (HUP)"

sleep 8
HEALTH=$(curl -s http://localhost:8080/actuator/health || echo "DOWN")
echo "[3/3] 健康检查: $HEALTH"

if echo "$HEALTH" | grep -q "UP"; then
    echo "=== 回滚成功 ✅ ==="
else
    echo "=== 回滚失败，请检查日志 ==="
    docker logs ${CONTAINER} --since=1m | tail -30
    exit 1
fi
```

---

## 灰度回滚

如果灰度发布后发现问题，但还没全量：

```bash
# 从灰度白名单移除问题账号
bash scripts/gray-release.sh remove <user>

# 或者回滚到指定版本
bash scripts/gray-release.sh roll-back <version>
```

## Phase 6 候选发布的回滚判断

本轮候选发布不包含数据库 schema 变更，回滚优先级如下：

1. 先执行应用层 / 静态资源层回滚
2. 不触发数据库层回滚
3. 若仅 `scripts/local-dev-deploy.sh` 相关脚本异常，不影响生产运行，可仅回退脚本文件，不回退生产应用

---

## 回滚决策树

```
部署/故障发生
    │
    ├─ 应用启动失败/健康检查不通过
    │   └─ 5分钟回滚（应用层）→ docker cp + HUP
    │
    ├─ 数据库迁移失败
    │   ├─ Flyway 迁移错误 → 修复 SQL → 重跑迁移
    │   └─ 迁移导致数据问题 → 30分钟回滚（数据库层）
    │
    └─ 严重数据损坏
        └─ 从备份恢复（可能超过 30 分钟）
```

---

## 紧急联系人

| 角色 | 姓名 | 电话 | 邮箱 | 备注 |
|------|------|------|------|------|
| 后端负责人 | - | - | - | 技术支持第一联系人 |
| 数据库 DBA | - | - | - | 数据库问题定位 |
| 运维工程师 | - | - | - | 服务器、网络问题 |
| 企业方对接人 | - | - | - | 业务影响确认 |
| 项目经理 | - | - | - | 协调沟通 |

---

## 回滚后通知

回滚完成后需通知：

- [ ] 企业方对接人（告知服务中断时长）
- [ ] 项目经理（记录事件）
- [ ] 后端团队（复盘分析）
