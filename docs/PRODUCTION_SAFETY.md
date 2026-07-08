# 生产环境保护铁律 (Production Safety Iron Rule)

> **本文件是 P0 级硬约束，违反任一条 = 立即停止 + 向用户报告事故。**
>
> 完整铁律精简版见 `AGENTS.md` 顶部 P0 章节。本文件提供细节、触发条件、违规识别。

---

## 0. 生产环境识别（必须先做）

执行任何 ssh/curl/playwright 操作前，先判断目标：

| 目标 | 是否生产 | 允许的操作 |
|------|---------|----------|
| `100.74.122.13` (学校服务器) | ✅ **生产** | 仅 release 流程已批准后的部署/验证/回滚 |
| `microcourse.ailyedu.cn` | ✅ **生产** | 仅 release 流程已批准后的只读验证/canary |
| `localhost` / `127.0.0.1` / `0.0.0.0` | ❌ 非生产 | 任意操作 (含 local-dev-deploy.sh) |
| `localhost:8088` / `localhost:8089` | ❌ 非生产 (隔离) | 任意操作 (本地 docker 容器) |
| `localhost:5433` / `localhost:6380` | ❌ 非生产 (隔离) | 任意操作 (本地 docker 容器) |
| GitHub repo `micro-course-admin/...` CI | ❌ 非生产 | 任意操作 |
| 用户明确说"部署到生产" | ✅ 生产意图 | 仅走完整 6 步流程 |

**默认**: 任何模糊场景都按生产处理，绝不"先试试看"。

---

## 1. 铁律 (10 条，违反即 P0 事故)

### 铁律 1 · 绝对禁止在生产做实验

```
❌ 禁止: 直接 ssh 到生产 → 修改代码 → 在生产 URL 测试
❌ 禁止: 在生产 URL Playwright 调试 (即使只是选个日期看看效果)
❌ 禁止: 在生产 DB 插入测试数据 (即使 INSERT 完后 DELETE)
❌ 禁止: 在生产容器里 kill -HUP / restart 看效果

✅ 允许: local-dev-deploy.sh 跑隔离环境 → 通过 → 才走 release 流程
✅ 允许: 在生产 URL 只读验证 canary (用户已批准灰度后)
```

### 铁律 2 · 必走 6 步发布流程

```
Step 1: LOCAL BUILD    → mvn package + vite build
Step 2: LOCAL ISOLATE  → scripts/local-dev-deploy.sh
Step 3: LOCAL VERIFY   → Playwright 在 localhost:8088 验证
Step 4: STAGING       → 单独 staging 服务器 (若有) 或 staging 容器
Step 5: PROD GRAY     → scripts/gray-release.sh add <user> 灰度
Step 6: PROD ROLL-OUT → scripts/gray-release.sh roll-out 全量
```

**任一 Step FAIL → 回到 Step 1**，不可跳过。

### 铁律 3 · 测试数据必须用本地隔离环境

```
❌ 禁止: 用生产用户名(如 xiaona)复刻到本地 DB
❌ 禁止: 把生产 DB 备份恢复到本地当种子

✅ 必须: 本地 DB 创建独立测试用户 (teacher1/password123)
✅ 必须: 本地 DB 端口 5433 (与生产 5432 隔离)
✅ 必须: 本地 DB 名 micro_course_test (与生产 micro_course 隔离)
```

### 铁律 4 · 测试前后必须清理

```
✅ 前置: local-dev-deploy.sh 用 tmpfs /var/lib/postgresql/data (重启即清)
✅ 后置: 测完后 docker stop/rm api-test admin-test
✅ 后置: 不残留任何 proposal/course/user 数据到生产 DB
```

### 铁律 5 · 生产 DB 写操作必须用户批准

```
❌ 禁止: DELETE/UPDATE 生产表 (即使你说"我只是想清理测试数据")
❌ 禁止: TRUNCATE 生产表
❌ 禁止: 修改生产表的 schema (加列/改索引)

✅ 必须: 任何生产 DB 写操作前,先 ask user 确认 (引用 SQL + 解释)
✅ 例外: 用户明确说"清理 proposal 49" 才允许 DELETE WHERE id=49
```

### 铁律 6 · 生产容器操作必须用户批准

```
❌ 禁止: docker restart/stop 任何生产容器 (微课-api/admin/postgres/redis)
❌ 禁止: docker exec 进生产容器做实验
❌ 禁止: docker cp 临时文件到生产容器 (即使只是覆盖 jar 后会 HUP)

✅ 必须: 灰度流程批准后,才能 docker cp jar 到 micro-course-api-1
✅ 必须: 重启用 kill -s HUP 1 (Spring Boot reload),不是 kill -9
```

### 铁律 7 · 必须有 audit trail

```
❌ 禁止: 在生产做任何操作不留记录

✅ 必须: 每次生产操作前,dump 当前状态 (commit hash / version / config)
✅ 必须: 在 commit message 写明 deploy ticket + 操作步骤
✅ 必须: 重大操作后,查 docker logs --tail=50 验证无异常
```

### 铁律 8 · 灰度白名单强制使用

```
❌ 禁止: 全量部署新功能 (即使本地测试通过)

✅ 必须: scripts/gray-release.sh add <user> 加入白名单 (xiaona, teacher1 等)
✅ 必须: 监控 5 分钟 (前端 console error + 后端 500 错误率)
✅ 必须: 5 分钟无异常 → scripts/gray-release.sh roll-out
✅ 必须: 5 分钟有异常 → scripts/gray-release.sh roll-back <version>
```

### 铁律 9 · ROLLBACK_PLAN.md 必须存在且最新

```
✅ 必须: 每次 release 前检查 ROLLBACK_PLAN.md
✅ 必须: ROLLBACK_PLAN.md 含最近 3 个版本的回滚步骤
✅ 必须: 列出每个版本的 docker cp + kill -HUP 命令模板
```

### 铁律 10 · 自检清单 (每次部署前)

```
□ 本地 precheck 16/16 PASS?
□ 本地 ESLint 0 errors 0 warnings?
□ 本地 mvn test 全部 PASS?
□ local-dev-deploy.sh 全部 PASS (含 Playwright UI 验证)?
□ 与上一次部署相比,有没有新增 flyway migration? 如果有 → staging 先验
□ 改动是否触及 4 张核心表 (users/departments/majors/classes)?
   如果是 → 必须先 git diff 数据库 migration,用户确认
□ ROLLBACK_PLAN.md 是否覆盖本次版本?
```

---

## 2. 6 步发布流程详解

### Step 1: LOCAL BUILD (本地,无网络)

```bash
cd /Users/jackie/微课平台/micro-course-api && mvn package -DskipTests -B -q
cd /Users/jackie/微课平台/micro-course-admin && npx vite build --mode production
ls -la micro-course-api/target/micro-course-api-1.0.0.jar  # 必须存在
ls -la micro-course-admin/dist/index.html                   # 必须存在
```

### Step 2: LOCAL ISOLATE (本地隔离 docker)

```bash
bash scripts/local-dev-deploy.sh
# 期望输出:
#   ✅ postgres-test 已启动
#   ✅ redis-test 已启动
#   ✅ 后端 jar 已生成
#   ✅ 前端 dist 已生成
#   ✅ api-test 已健康
#   ✅ 种子用户已注入
#   ✅ admin-test 已启动
#   ✅ 后端 actuator/health UP
#   ✅ 前端首页 200
#   ✅ API 登录成功
#   ✅ 后端单元测试
#   ✅ precheck 通过
#   ✅ ESLint 0/0
# 期望退出码: 0 (若 1 → 阻断, 回到 Step 1)
```

### Step 3: LOCAL VERIFY (Playwright)

```bash
PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh --keep
# 浏览器手动登录 teacher1/password123 测试改动
# 期望:
#   - console 0 errors 0 warnings
#   - Network 无 4xx/5xx
#   - DB 写入正确 (proposal_signatures 有 3 行 LEAD/DEPT/SCHOOL)
```

### Step 4: STAGING (staging 服务器,镜像生产)

```bash
# staging 服务器通常在另一台机器 (用户配置)
ssh staging-server 'bash deploy.sh v1.7.1'
# staging URL: https://staging.microcourse.ailyedu.cn (示例)
# 用真实数据测试 (但与生产物理隔离)
```

### Step 5: PROD GRAY (生产灰度)

```bash
# 1. 备份当前版本
ssh ubuntu@100.74.122.13 "docker cp micro-course-micro-course-api-1:/app/app.jar /tmp/app.jar.backup.$(date +%Y%m%d_%H%M%S)"

# 2. 加入白名单 (xiaona 是真实测试账号)
bash scripts/gray-release.sh add xiaona

# 3. 部署新版本
tar cf - -C target micro-course-api-1.0.0.jar | ssh ubuntu@100.74.122.13 "tar xf - -C /tmp/"
ssh ubuntu@100.74.122.13 "docker cp /tmp/micro-course-api-1.0.0.jar micro-course-micro-course-api-1:/app/app.jar"
ssh ubuntu@100.74.122.13 "docker exec micro-course-micro-course-api-1 kill -s HUP 1"

# 4. 监控 5 分钟
sleep 300
ssh ubuntu@100.74.122.13 "docker logs micro-course-micro-course-api-1 --since=5m | grep -E 'ERROR|500' | head"
```

### Step 6: PROD ROLL-OUT (全量)

```bash
# 监控无异常 → 全量
bash scripts/gray-release.sh roll-out
# 监控有异常 → 回滚
ssh ubuntu@100.74.122.13 "docker cp /tmp/app.jar.backup.<TIMESTAMP> micro-course-micro-course-api-1:/app/app.jar"
ssh ubuntu@100.74.122.13 "docker exec micro-course-micro-course-api-1 kill -s HUP 1"
```

---

## 3. 紧急事故处理

### 检测到事故 (任一):

- 生产 URL 出现 5xx
- console 出现未捕获错误
- 用户报告数据丢失/UI 异常
- 后端日志大量 ERROR

### 立即执行:

```bash
# 1. 停止灰度 (如果还没全量)
bash scripts/gray-release.sh roll-back <last-good-version>

# 2. 恢复代码 (从 git)
git checkout <last-good-commit>

# 3. 通知用户 (必须在 5 分钟内)
echo "🚨 P0 事故: <描述>"
echo "  - 回滚版本: <version>"
echo "  - 影响范围: <描述>"
echo "  - 状态: 已回滚,待进一步分析"
```

### 事后:

1. 写事故复盘报告 → `docs/incidents/YYYY-MM-DD-<title>.md`
2. 加 precheck 规则防止再次发生
3. 更新 ROLLBACK_PLAN.md

---

## 4. 生产门禁（物理阻断，不可绕过）

所有生产操作（ssh/docker cp/curl/playwright）前，必须先检查门禁：

```bash
# 强制检查: 门禁是否已开?
bash scripts/deploy-gate.sh check
# 退出码 0 = 门开着 → 允许继续
# 退出码 1 = 门关着 → ❌ 阻断! 请先跑 local-dev-deploy.sh
```

**门禁生命周期**:
```
local-dev-deploy.sh 全部 PASS
  → 自动开门 (scripts/deploy-gate.sh open)
  → 有效期 4 小时
  → 超时自动关门
  → 重新 local-dev-deploy.sh 再开门
```

### 绕过后门（紧急情况，必须用户确认）

如果用户明确要求"跳过门禁直接部署"，AI 必须：
1. 确认用户已知晓风险
2. 由用户手动运行 `bash scripts/deploy-gate.sh open`
3. AI 记录到 commit message: `⚠ bypassed production gate by user request`
4. 部署后主动运行 `bash scripts/deploy-gate.sh close`

---

## 5. 自检命令 (Agent 必跑)

每次 Agent 准备执行 ssh/curl/playwright 操作前,必须运行:

```bash
# 1. 判断目标
TARGET="$1"  # 例如: 100.74.122.13, microcourse.ailyedu.cn, localhost
if [[ "$TARGET" =~ (100\.74\.122\.13|microcourse\.ailyedu\.cn) ]]; then
  echo "🚨 检测到生产目标: $TARGET"

  # 2. 检查生产门禁 (物理阻断)
  bash scripts/deploy-gate.sh check || exit 1

  # 3. 确认本地验证已完成
  echo "当前是否已完成 local-dev-deploy.sh 全部 PASS? (yes/no)"
  read -p "" ANSWER
  if [ "$ANSWER" != "yes" ]; then
    echo "❌ 阻断: 必须先完成本地验证才能操作生产"
    exit 1
  fi
fi
```

---

## 5. 已知违规案例 (作为反面教材)

### 案例 1 · 2026-07-09 每教师 API Key 部署事故

**违规**：直接 ssh 到生产 → 反复创建/删除容器 → 试图用 docker cp 上传 jar → 上传错架构镜像 → admin 容器加错 network-alias → Redis/DB 连接池因容器重启 churn 陈旧

**正确做法**：
1. local-dev-deploy.sh 跑隔离环境 → Playwright 验证
2. 通过后才走灰度发布
3. 生产用 HUP 重启（kill -s HUP 1）保留端口映射
4. 任何调试只在本地容器进行

**已修复**: AGENTS.md + docs/PRODUCTION_SAFETY.md（本文）新增铁律

---

## 6. 相关脚本

| 脚本 | 用途 | 何时用 |
|------|------|--------|
| `scripts/local-dev-deploy.sh` | 本地隔离 build + deploy + test | 每次改动后必跑 |
| `scripts/precheck.sh` | 代码规范检查 | commit 前 |
| `scripts/deploy-dryrun.sh` | 部署前 dry-run 检查 | release 前 |
| `scripts/gray-release.sh` | 灰度白名单管理 | Step 5 |
| `scripts/cleanup-test-data.sh` | 清理测试数据 | 定期 |
| `scripts/db-backup.sh` | DB 备份 | release 前必跑 |
| `scripts/deploy-gate.sh` | 生产门禁 | 每次生产操作前 |

---

## 7. 触发场景速查

| 你要做 | 正确做法 |
|--------|---------|
| 改了某个 vue/js 文件 | Step 1 build → Step 2 deploy → Step 3 Playwright → 4-6 用户决定 |
| 加了新的 flyway migration | Step 1-3 → 用户确认 → staging 先跑迁移 → 灰度 |
| 想在生产 URL 看看效果 | ❌ 禁止;改为 Step 3 在 localhost:8088 看 |
| 想看生产某条数据 | ✅ 只读查询 OK;但不能写 |
| 发现生产 bug 想现场修 | ❌ 禁止;回到 Step 1 在本地复现 |
| 用户要求部署到生产 | 走 Step 4-6,每步报告 |
| 用户要求清理生产脏数据 | 必须 ask user 确认 SQL,然后执行 + 验证 |

---

## 8. 签到

每次 release 在 commit message 末尾追加:

```
release-checklist:
  - [x] local precheck 16/16
  - [x] local ESLint 0/0
  - [x] local mvn test all PASS
  - [x] local-dev-deploy.sh 全部 PASS
  - [x] staging 验证 (若适用)
  - [x] 灰度白名单 + 5 分钟监控
  - [x] 全量发布
```

违反本文件任一条 = 立即停止 + 24 小时内写事故复盘。

---

*版本: v1.0 · 2026-07-09 因 v1.21.1 部署事故创建*
*维护者: 总工程师 + AI Agent 团队*