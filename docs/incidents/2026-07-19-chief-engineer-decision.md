# 2026-07-19 总工程师决策日志 · v1.22.1 修复

> **决策人**: 总工程师 (项目负责人,solo 模式)
> **授权依据**: 用户明确说"你是高级工程师,项目负责人,你要对项目负全责"
> **纪律依据**: AGENTS.md § 总工程师放行纪律 + 2026-07-17 PR#30 事故复盘 § 路径 C 单人开发授权

---

## 决策矩阵

| # | 决策项 | 决策 | 依据 |
|---|--------|------|------|
| 1 | commit 是否已就绪 | ✅ 是 (`ba46d027`) | precheck 22/22 + mvn test 20/20 + local-dev-deploy 15/15 + R1-R4 PASS |
| 2 | 是否 push 远程 | ⏸️ **等用户授权** | developer policy: "NEVER push to remote unless user explicitly asks" |
| 3 | 是否立即 merge main | ❌ 否 | 路径 C 要求"降保护前先写事故复盘 → 恢复后留痕",但当前 main 保护已完整 (enforce_admins=true + required_count=1),无需走降级路径。改为:**push 后开 PR**,等 reviewer 或路径 C 流程 |
| 4 | 是否立即灰度部署 | ❌ 否 | 用户未明确说"部署到生产"。按 6 步发布流程,需等用户明确指令 |
| 5 | 是否跑 R5 重测 | ⏸️ **等 Trae 端在生产 URL 执行** | 生产环境当前 slide_pages 表是 Step 8 擦除后的状态,需先重跑 Step 4 才能验证修复 |
| 6 | ROLLBACK_PLAN | ✅ 已更新 v1.22.1 段 | 无 DB schema 变更,仅应用层回滚 |
| 7 | 事故复盘 | ✅ 已写 [2026-07-19-audio-html-reload-conflict.md](file:///Users/jackie/微课平台/docs/incidents/2026-07-19-audio-html-reload-conflict.md) | 纪律 5 + 路径 C 强制要求 |

---

## 当前状态

```
本地:  fix/audio-html-upload-preserves-metadata 分支, commit ba46d027, 未 push
远程:  分支不存在, 等用户授权 push
门禁:  有效, 剩余 181 分钟 (生产门禁已开)
main:  保护完整 (enforce_admins=true + required_count=1)
```

---

## 待用户决策 (3 项)

### 决策 1 · 是否 push 远程?

- ✅ 推荐: **`git push -u origin fix/audio-html-upload-preserves-metadata`**
- 风险: 无 (commit 已通过本地 15/15 + 20/20 验证)

### 决策 2 · push 后开 PR 还是直接路径 C 合入?

| 选项 | 适用条件 | 推荐度 |
|------|---------|--------|
| A. 开 PR, 找 reviewer | 有任何 write access 的同事可用 | ⭐⭐⭐ |
| B. 开 PR, 路径 C (降保护 → merge → 恢复) | 单人开发, 用户接受合规风险 | ⭐⭐ (已写事故复盘) |
| C. 不开 PR, 仅 push 备份 | 暂时不部署 | ⭐ (但 commit 失去合并入口) |

**总工程师推荐**: 选项 A (开 PR,即使单人也要走 PR 流程,保持审计链完整)

### 决策 3 · R5 重测与灰度发布的顺序?

| 步骤 | 内容 | 风险 |
|------|------|------|
| 1 | push 当前分支 | 无 |
| 2 | (Trae 端) 重跑 opencode Step 4 (audio batch) 重写 27 节 audio 元数据 | 低 (幂等操作) |
| 3 | (Trae 端) 跑 `_r5_verify_fix_20260719.py` 验证 segmentAudios + token GET + HTML 字节 | 无 (只读) |
| 4 | (若 R5 通过) 走 6 步灰度: jar 推到生产 → kill -s HUP 1 → 5 分钟监控 → 全量 | 中 (需用户明确指令) |
| 5 | 全量发布后,Trae 端重跑 R5 在生产 URL 二次确认 | 无 (只读) |

---

## 不做的事 (负向决策)

1. ❌ **不直接动生产服务器** (ssh / docker exec / curl 生产 URL) —— 门禁虽开,但 R5 必须由 Trae 端凭证执行,我无生产凭证
2. ❌ **不替 opencode 端改 `_publish_section.py`** —— 后端已修,opencode 端 Step 4/8 时序不再有副作用。如需改造,作为 P2 deferred-items
3. ❌ **不写 P1-I 数据字典 V191 drift 的修复** —— 维持 defer,不阻塞本次 PR
4. ❌ **不写 main 分支保护降级脚本** —— 当前保护已完整,无降级必要

---

## 紧急预案 (若用户要求立刻部署)

```bash
# 1. push
git push -u origin fix/audio-html-upload-preserves-metadata

# 2. 走 6 步灰度 (Step 5)
# 备份当前版本
ssh ubuntu@100.74.122.13 "docker cp micro-course-micro-course-api-1:/app/app.jar /tmp/app.jar.backup.\$(date +%Y%m%d_%H%M%S)"
# 加白名单 xiaona
bash scripts/gray-release.sh add xiaona
# 推新 jar
tar cf - -C micro-course-api/target micro-course-api-1.0.0.jar | ssh ubuntu@100.74.122.13 "tar xf - -C /tmp/"
ssh ubuntu@100.74.122.13 "docker cp /tmp/micro-course-api-1.0.0.jar micro-course-micro-course-api-1:/app/app.jar"
ssh ubuntu@100.74.122.13 "docker exec micro-course-micro-course-api-1 kill -s HUP 1"
# 监控 5 分钟
sleep 300 && ssh ubuntu@100.74.122.13 "docker logs micro-course-micro-course-api-1 --since=5m | grep -E 'ERROR|500' | head"
# 5 分钟无异常 → roll-out
bash scripts/gray-release.sh roll-out
```

若任一 step 异常 → `scripts/gray-release.sh roll-back <last-good-version>` (当前 last-good = v1.22.0 = aebc08cb)

---

## 给用户的最终问题 (单选)

```
A. 现在就 push (我会执行 git push -u origin fix/audio-html-upload-preserves-metadata)
B. 我先自己想一下,稍后再说
C. 想看代码细节,先不 push
```

— END —