#!/bin/bash
# Phase 14 微专业 · Autopilot 自动化审查修复系统

> 项目总工程师签发 · 2026-06-24
> 上游契约：`docs/开发规划/phase14-audit-fix-spec.md`
> 下游执行：3 角色子 Agent（auditor / investigator / fixer）

---

## 0. 设计目标

让任意会话（当前或后续）加载此系统后，**主 Agent 仅需调用 autopilot.sh + 派子 Agent**，即可自动化推进剩余 69 个工单。

## 1. 目录结构

```
scripts/phase14-autopilot/
├── autopilot.sh           # 主编排入口
├── lib/
│   ├── render-task.py     # 工单任务书渲染器（72 工单 + 3 角色）
│   ├── cross-validate.sh  # R1-R4 4 维交叉验证任务书
│   └── summarize.sh       # batch 摘要生成
├── templates/             # 预留（任务书由 Python 动态生成）
├── reports/               # 工单报告 + 任务书快照
│   ├── M1-03_auditor_task_r1782235363.json
│   ├── M1-03_auditor_r1782235364.json    # 子 Agent 实际报告
│   └── ...
├── batches/               # batch 摘要归档
└── README.md
```

## 2. 主编排状态机

```
              ┌─────────────────────────────────────────┐
              │  bash autopilot.sh next → 下一工单 ID   │
              └────────────────┬────────────────────────┘
                               ▼
              ┌─────────────────────────────────────────┐
              │  bash autopilot.sh run auditor <id>     │
              │  → 输出 JSON 任务书到 stdout            │
              └────────────────┬────────────────────────┘
                               ▼
        ┌─────────────────────────────────────────────┐
        │  主 Agent 派 explore 子 Agent + JSON 任务书   │
        │  → 子 Agent 返回 JSON 报告                  │
        └────────────────┬────────────────────────────┘
                         ▼
        ┌─────────────────────────────────────────────┐
        │  IF verdict == "PASS"  → 写 progress → 下一工单│
        │  IF verdict == "FAIL"  → investigator 阶段   │
        └────────────────┬────────────────────────────┘
                         ▼
        ┌─────────────────────────────────────────────┐
        │  bash autopilot.sh run investigator <id>    │
        │  → 派 explore 子 Agent                      │
        │  → IF REPRODUCED → fixer 阶段              │
        │  → IF NOT_REPRODUCED → 标 PASS 跳过         │
        └────────────────┬────────────────────────────┘
                         ▼
        ┌─────────────────────────────────────────────┐
        │  bash autopilot.sh run fixer <id>           │
        │  → 派 explore 子 Agent（含 Edit 权限）       │
        │  → 收到报告 → 主 Agent grep 验证            │
        │  → mvn compile 验证                        │
        │  → 写 progress → 下一工单                   │
        └────────────────┬────────────────────────────┘
                         ▼
        ┌─────────────────────────────────────────────┐
        │  每 5 工单 → bash autopilot.sh validate     │
        │  → 派 4 个 reviewer 子 Agent 并发（R1-R4）  │
        │  → 全 PASS → git commit → 下一 batch         │
        │  → 任一 FAIL → 退回修复                     │
        └─────────────────────────────────────────────┘
```

## 3. 速查命令

```bash
# 查进度
bash autopilot.sh status

# 找下一个待跑工单
bash autopilot.sh next

# 生成 auditor 任务书
bash autopilot.sh run auditor M1-03

# 生成 investigator 任务书（输入含 auditor 报告路径）
bash autopilot.sh run investigator M1-03

# 生成 fixer 任务书（输入含 auditor + investigator 报告路径）
bash autopilot.sh run fixer M1-03

# 4 维交叉验证
bash autopilot.sh validate M1-batch-1

# 显示单个 dim 的 reviewer 任务书
bash lib/cross-validate.sh r1 M1-batch-1
```

## 4. 典型流程（单工单 3 步）

```bash
# Step 1: 生成 auditor 任务书
TASK=$(bash autopilot.sh run auditor M1-03)
# Step 2: 主 Agent 派 explore 子 Agent
#   prompt: TASK + "你必须返回严格 JSON 报告"
# Step 3: 收到报告后，校验 verdict，决定下一步
# Step 4: 如果 FAIL:
TASK=$(bash autopilot.sh run investigator M1-03)
#   prompt: TASK + "{auditor 报告 JSON}"
# Step 5: 如果 REPRODUCED:
TASK=$(bash autopilot.sh run fixer M1-03)
#   prompt: TASK + "{auditor + investigator 报告 JSON}"
#   subagent_type: explore（允许 Edit 工具）
# Step 6: 收到修复报告，主 Agent grep 验证
#   grep -n "..." 目标文件
#   mvn compile -q
# Step 7: 写 progress.json
#   ticket.status = PASS / FAIL
#   ticket.findings = [...]
```

## 5. 主 Agent 必备工具调用

| 操作 | bash / 工具 |
|------|------------|
| 找下一工单 | `bash scripts/phase14-autopilot/autopilot.sh next` |
| 生成任务书 | `bash scripts/phase14-autopilot/autopilot.sh run <role> <id>` |
| 派子 Agent | `Task` 工具，subagent_type=`explore` |
| 验证修复 | `grep -n "..." <file>` + `mvn compile -q` |
| 写进度 | `Edit` 工具改 progress.json |
| 交叉验证 | `bash scripts/phase14-autopilot/autopilot.sh validate <batch>` |
| git commit | `git add` + `git commit`（按 spec §11 标注） |

## 6. 失败处理

| 场景 | 动作 |
|------|------|
| 子 Agent 超时 | 重新派同任务书 + 标注 `timeout: true`；2 次超时 → 拆分任务书 |
| 子 Agent 报告 PASS 但验证失败 | 派 investigator 复审（不直接进下一工单） |
| 子 Agent 报告 FAIL 但 P0 > 5 | 拆分任务书（按代码段拆） |
| 跨工单问题 | 记录到 progress 的 `tickets.<id>.discrepancies`，本工单不修，下一工单处理 |
| 4 维交叉验证 R1-R4 任一 FAIL | 退回修复阶段，重跑该工单 |
| spec 与代码根本冲突 | 暂停流水线，**先修 spec 再修代码**（按 spec §7.3） |

## 7. git commit 时机

按 spec §11：每 batch（5 工单）跑完 + 4 维验证 PASS 后 commit 一次：

```bash
git add -A
git commit -m "$(cat <<'EOF'
fix(phase14-batch-N): 5 工单审计修复完成 (M1-XX,M1-YY,M1-ZZ,...)

- 修复 P0: ...
- 修复 P1: ...
- 忽略 P3: ...
- 4 维交叉验证 R1-R4: PASS

Co-Authored-By: OpenCode <noreply@anthropic.com>
EOF
)"
```

## 8. 进度可视化

```bash
# 一行进度条
jq -r '
  "Total: \(.stats.total) | PASS: \(.stats.passed) | FAIL: \(.stats.failed) | PENDING: \(.stats.pending)",
  "Progress: \(.stats.passed)/\(.stats.total) (\((.stats.passed * 100 / .stats.total))% )"
' .audit-cache/phase14/progress.json
```

## 9. 新会话如何接管

复制以下指令到新会话（最简继续命令）：

```
请基于以下 3 份文档自动化推进 phase14 微专业审计修复：

1. /Users/jackie/微课平台/docs/开发规划/phase14-audit-fix-spec.md（执行契约）
2. /Users/jackie/微课平台/.audit-cache/phase14/progress.json（当前进度）
3. /Users/jackie/微课平台/scripts/phase14-autopilot/（自动化工具）

工作流：
  1. bash scripts/phase14-autopilot/autopilot.sh next → 找下一工单
  2. bash scripts/phase14-autopilot/autopilot.sh run auditor <id> → 生成任务书
  3. 派 explore 子 Agent 跑 auditor → 收到 JSON 报告
  4. 校验 verdict → FAIL 则派 investigator → fixer
  5. 主 Agent grep 验证修复 → 写 progress
  6. 每 5 工单跑 4 维交叉验证 → git commit

继续从进度文件中的下一个 PENDING 工单开始。
```

## 10. 监控与异常

每 10 工单打印一次摘要：

```bash
bash autopilot.sh status
```

若 P0 数量累计 > 10 → 暂停流水线，启动 phase14 spec 修订工作流。

---

*autopilot 版本：v1.0*
*维护：项目总工程师*
