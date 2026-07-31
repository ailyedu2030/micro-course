# 事故复盘报告: PR #161 部署 P0 事故

> **事故日期**: 2026-07-31
> **事故 ID**: INC-2026-07-31-PR161-DEPLOY
> **报告作者**: 总工程师 (opencode)
> **事故等级**: P0 (生产事故, 影响生产可用性)
> **恢复时间**: 20:22 (事故发生后约 5 分钟)
> **最终状态**: PR #161 部署成功 + Bug E/F 修复部署成功 + 5 分钟监控全绿

---

## 1. 事故时间线

| 时间 | 事件 | 操作人 | 结果 |
|------|------|--------|------|
| 20:18:02 | 备份生产 dist | AI (opencode) | ✅ `/opt/micro-course/backups/admin.dist.backup.20260731_201801` (246 文件, 6.2MB) |
| 20:18:11 | 本地 build 新 dist | AI | ⚠️ 第一次 build 失败 — 本地 main 陈旧 (HEAD = `dd242620` 落后 origin/main 2 commits) |
| 20:20:34 | `git fetch + reset --hard origin/main` | AI | ✅ HEAD 同步到 `d729d36e` (PR #161 + PR #162 都包含) |
| 20:21:16 | 重新 build (新 bundle `index-Dx5dZWos.js` 含 PR #161+PR #162 修复) | AI | ✅ build OK |
| 20:21:38 | `scp tar 到生产` | AI | ⚠️ **判断为静默失败** (实际后查证 scp 可能成功, 输出未显示) |
| 20:21:38 | `ssh heredoc: docker cp + tar 解压 + mv 替换` | AI | ❌ **链式失败**: 文件不存在 → tar 解压失败 → `mv newdist/assets` 失败 → **assets/ 被删** → 用户白屏 |
| 20:21:38 | nginx reload | AI | ⚠️ HTTP 仍 200 (旧 dist 在内存), 但 assets/ 已空 |
| 20:21:39 | 备份恢复 (从 backup) | AI | ✅ dist 246 文件恢复 |
| 20:21:39 | nginx restart (尝试启动新进程) | AI | ❌ `bind() to 0.0.0.0:80 failed (98: Address in use)` (旧 master 还在) |
| 20:21:58 | `nginx -s reload` (master 还在, reload workers) | AI | ✅ HTTP 200, nginx reload OK |
| 20:22:02 | 事故恢复完成 | — | ✅ 生产完全恢复到 PR #161 部署前 |
| 20:23:00 | **事故复盘 + 重做部署决策** | AI | 用户3次授权 + L0 UX 宪法 > P0 同方法约束, **改方法重做** |
| 20:23:30 | 重新打包 + 显式 verbose scp + 每步独立验证 | AI | ✅ scp Exit 0, 文件 1,725,575 bytes 传输成功 |
| 20:25:36 | 原子 mv 替换 (用 mv html → html.bak-pr161-dep) | AI | ✅ 新 dist 上线 |
| 20:25:36 | nginx reload | AI | ✅ HTTP 200, bundle `index-Dx5dZWos.js` |
| 20:25-31 | 灰度白名单 4 阶段 + 5 分钟监控 | AI | ✅ 全绿, 0 ERROR |
| 20:36:21 | 阶段 4 全量 roll-out | AI | ✅ Redis 清空白名单 |
| 20:36-42 | 用户反馈 2 个新 console 错误 (Bug E + Bug F) | — | — |
| 20:44:28 | Bug E/F 修复 commit `aa342ffd` | AI | ✅ 6 文件, 33 行变更 |
| 20:45:46 | 重新部署 (覆盖 Bug E/F 修复) | AI | ✅ 新 bundle `index-OC1P7kb9.js` |
| 20:46-50 | 5 分钟监控 | AI | ✅ 0 ERROR |
| 21:28:31 | PR #163 squash merge 到 main | Bot auto-approve | ✅ commit `0a8faeab` |

---

## 2. 直接原因

**事故触发**: 在一个 heredoc 嵌套 ssh 命令里执行 "scp → docker cp → tar 解压 → mv 替换 dist → nginx reload → 灰度白名单" 链式操作。任何中间一步失败后续都连锁失败。

**致命问题**: 使用 `rm -rf /usr/share/nginx/html/assets && mv /tmp/newdist/assets /usr/share/nginx/html/assets` 模式 — 当 `mv` 失败时, `assets/` 已经被 `rm -rf` 删除。

**事故命令 (heredoc 嵌套 ssh)**:
```bash
ssh ubuntu@100.74.122.13 << 'EOF'
... 
docker exec micro-course-micro-course-admin-1 sh -c "rm -rf /usr/share/nginx/html/assets && mv /tmp/newdist/assets /usr/share/nginx/html/assets"
...
EOF
```

---

## 3. 深层原因

| 类别 | 描述 |
|------|------|
| **方法论错误** | 违反 PRODUCTION_SAFETY 第 3 条铁律 "一次只做一件事" — 在一个 atomic command 里做了 6 步 |
| **shell 转义陷阱** | heredoc 嵌套 ssh + 嵌套 docker exec + 嵌套 sh -c 导致错误信息被吞, 无法定位失败步骤 |
| **恢复策略缺陷** | `rm && mv` 不原子 — 失败导致状态不一致 (旧目录已删, 新目录未就位) |
| **本地 main 落后** | 第一次 build 用的本地 main HEAD = `dd242620` (R14), 落后 origin/main 2 commits — 本来就不该 build |
| **scp 输出误判** | "Truncated at 1725575" + "Exit status 0" — 当时判断 scp 失败 (实际可能成功), 导致跳过关键检查 |
| **缺乏分步验证** | 整个部署链没有任何分步 "ls 验证文件存在" 检查 |

---

## 4. 影响评估

### 直接影响
- 生产前端 dist 在 20:21:38 → 20:21:39 约 1 秒内处于 assets 缺失状态
- 用户访问 `http://100.74.122.13/` 返回 SPA HTML 但无 JS (白屏)
- API proxy 仍然正常 (nginx 80 → api 8080)

### 业务影响
- **白屏时长**: ~30 秒 (20:21:38 → 20:21:58 nginx reload 后恢复)
- **受影响用户**: 所有正在使用 `http://microcourse.ailyedu.cn/` 的用户
- **数据丢失**: 无 (前端 SPA 不持久化, 后端未受影响)
- **业务损失估算**: 教学平台用户可能在课件浏览/课程查询中受 30 秒影响

### 恢复完整性
- ✅ backup 恢复成功 (从 `/opt/micro-course/backups/admin.dist.backup.20260731_201801`)
- ✅ nginx 配置未变, master 重启 + workers reload
- ✅ 数据库 / 后端 API / Redis 均未受影响
- ✅ 最终状态: PR #161 + Bug E/F 全部成功部署

---

## 5. 修复方案（防再发）

### 5.1 技术修复

**A. 禁止 heredoc 嵌套 ssh** — 每次只发一个 ssh 命令, 验证成功后才发下一个:
```bash
# Step A: 传 tar (独立命令)
scp -v /tmp/admin-pr161.tar.gz ubuntu@100.74.122.13:/tmp/

# Step A.1: 验证 (独立命令)
ssh ubuntu@100.74.122.13 "ls -la /tmp/admin-pr161.tar.gz"

# Step B: docker cp (独立)
ssh ubuntu@100.74.122.13 "docker cp /tmp/admin-pr161.tar.gz admin-1:/tmp/"

# Step B.1: 验证 (独立)
ssh ubuntu@100.74.122.13 "docker exec admin-1 ls -la /tmp/admin-pr161.tar.gz"

# Step C: 替换 (独立)
ssh ubuntu@100.74.122.13 "docker exec admin-1 sh -c '
  mv /usr/share/nginx/html /usr/share/nginx/html.bak
  cp -r /tmp/newdist /usr/share/nginx/html
  ls /usr/share/nginx/html/assets/ | wc -l  # 验证文件数
  rm -rf /usr/share/nginx/html.bak  # 验证后才删旧
'"
```

**B. 禁止 `rm && mv` 非原子操作** — 改用 `mv` (原子 rename):
```bash
# ✅ 正确: mv 原子, 失败时旧目录仍在原位
mv /usr/share/nginx/html /usr/share/nginx/html.bak
mv /tmp/newdist /usr/share/nginx/html

# ❌ 错误: rm && mv 非原子, 失败时状态不一致
rm -rf /usr/share/nginx/html/assets
mv /tmp/newdist/assets /usr/share/nginx/html/assets  # 失败时 assets/ 已删
```

**C. 每步独立验证** — 任何 ssh 命令必须以验证步骤结束, 验证失败立即停止后续:
```bash
# 错误: 一个命令做 6 步
ssh host "cmd1 && cmd2 && cmd3 && cmd4 && cmd5 && cmd6"

# 正确: 6 个独立命令, 每个独立验证
result=$(ssh host "cmd1")
[ "$result" = "expected" ] || exit 1
result=$(ssh host "cmd2")
[ "$result" = "expected" ] || exit 1
...
```

### 5.2 治理修复

**A. 更新 `docs/deployment/PR161-DEPLOYMENT-RUNBOOK.md`** — 加入 "禁止 heredoc 嵌套 ssh" 章节 + "mv 原子操作" 模板。

**B. 更新 `PRODUCTION_SAFETY.md`** — 第 3 条铁律增加案例 INC-2026-07-31-PR161-DEPLOY。

**C. 增加 `scripts/deploy-frontend.sh`** — 一键安全部署脚本, 内置分步验证:
```bash
#!/bin/bash
# 安全前端部署脚本 (每个步骤独立验证, 失败立即退出)
set -e
# Step 1: build
# Step 2: scp + verify file size
# Step 3: docker cp + verify in container
# Step 4: mv (atomic) + verify file count
# Step 5: nginx reload + verify HTTP 200
# Step 6: smoke test (curl /api/auth/login proxy)
```

### 5.3 流程改进

**A. 部署前必须先 `git fetch + reset origin/main`** — 防止本地 main 落后 origin/main 导致 build 出旧 dist。

**B. 部署前必须看 `git log --oneline origin/main -5`** — 确认要部署的 commit 在 origin/main 上。

**C. 部署后 5 分钟监控必须分阶段**: 0 min (立即) + 1 min + 3 min + 5 min。

---

## 6. 经验教训

### 给总工程师 (AI)
1. **绝不滥用 heredoc 嵌套 ssh** — 一个命令做 6 步 = 任何一步失败都灾难
2. **每个 ssh 命令都要独立验证** — 不能依赖前一个命令的成功
3. **不要凭直觉判断 "scp 静默失败"** — 必须 ls 文件大小 + md5 验证
4. **mv 是原子操作, rm && mv 不是** — 必须用 mv
5. **本地 main 必须与 origin/main 同步** — 部署前 `git fetch + reset --hard origin/main`

### 给项目运维
1. **生产服务器必须部署回滚脚本** — 当前 rollback 步骤在 `ROLLBACK_PLAN.md` 是文档, 不是可执行脚本, 应建 `scripts/rollback-frontend.sh`
2. **生产 dist 必须双备份** — 当前只有一处 backup, 应备份到 `/opt/micro-course/backups/admin.dist.backup.<ts>` + `/opt/micro-course/backups/.latest` 软链
3. **生产环境必须有访问日志** — 方便定位事故时谁/什么时候访问

### 给项目治理
1. **PRODUCTION_SAFETY.md 应包含事故案例** — INC-2026-07-31-PR161-DEPLOY 加入 "已知违规案例" 章节
2. **docs/incidents/ 应建立标准化模板** — 8 节模板: 时间线/直接原因/深层原因/影响评估/修复方案/经验教训/Action Items

---

## 7. Action Items

| # | 责任方 | 截止 | 描述 | 状态 |
|---|--------|------|------|------|
| 1 | 总工程师 | 2026-08-01 | 写 `scripts/deploy-frontend.sh` 安全部署脚本 (内置分步验证) | ✅ 已写 |
| 2 | 总工程师 | 2026-08-01 | 更新 `docs/deployment/PR161-DEPLOYMENT-RUNBOOK.md` 加 heredoc 反模式警告 | ✅ 已更新 |
| 3 | 总工程师 | 2026-08-01 | 更新 `PRODUCTION_SAFETY.md` 第 3 条铁律 + 第 5 节"已知违规案例" | ⏳ TODO |
| 4 | 运维 | 2026-08-05 | 写 `scripts/rollback-frontend.sh` (5 分钟回滚到 backup 的脚本) | ⏳ TODO |
| 5 | 运维 | 2026-08-05 | 生产 dist 双备份策略实施 (`.latest` 软链 + 时间戳目录) | ⏳ TODO |
| 6 | 总工程师 | 2026-08-05 | Bug E/F 修复 PR #163 squash merge 到 main | ✅ 已 merge |
| 7 | 总工程师 | 持续 | 任何部署前必须先 `git fetch + reset --hard origin/main` | ✅ 已写进 runbook |
| 8 | 全员 | 长期 | 不使用 heredoc 嵌套 ssh (改用独立命令 + 每步验证) | ✅ 已写进 runbook |

---

## 8. 结论

虽然事故发生 (P0, 30 秒白屏), 但**最终成功恢复 + 重新部署 + 修复 Bug E/F**。事故复盘教训将写入 PRODUCTION_SAFETY.md 和 runbook, 防止再发。

**核心教训**: **每个 ssh 命令独立 + 每步验证 + mv 原子操作 + 不滥用 heredoc** 是生产部署的 4 大原则。

事故不丢人, 重复事故才丢人。这次事故让 SOP 改进, 让运维更安全。

---

*报告生成时间*: 2026-07-31
*报告作者*: 总工程师 (opencode)
*签字*: ✅ 接受 + 已采取改进措施