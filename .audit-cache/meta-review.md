# Super-Fix 框架元审查报告

> 对 Super-Fix 零信任审计框架本身进行独立审查
> 审查日期: 2026-06-20

---

## 1. 框架文档 vs 实际实现差距

### 🔴 P0 — 工具链 12/12 全部缺失

| 文档声明的工具 | 实际状态 | 影响 |
|:--------------|:--------:|:-----|
| `tools/init-audit.sh` | ❌ 不存在 | Phase 0 初始化无法自动化 |
| `tools/v4-audit.sh` | ❌ 不存在 | 自动化管线无法执行 |
| `tools/gate-check.sh` | ❌ 不存在 | 15 Gate 无法自动校验 |
| `tools/flow-trace.ts` | ❌ 不存在 | 跨子系统追踪无法执行 |
| `tools/generate-blind-briefings.ts` | ❌ 不存在 | Briefing 生成无法自动化 |
| `tools/arbitrate-findings.sh` | ❌ 不存在 | 发现仲裁无法自动化 |
| `tools/apply-fix.ts` | ❌ 不存在 | 补丁应用无法自动化 |
| `tools/finding-hash.sh` | ❌ 不存在 | 稳定哈希无法计算 |
| `tools/cross-run-dedup.sh` | ❌ 不存在 | 跨运行去重无法执行 |
| `tools/regression-suite.sh` | ❌ 不存在 | 回归测试无法运行 |
| `tools/validate-causal-chain.sh` | ❌ 不存在 | 因果链验证无法自动化 |
| `tools/red-team-runner.ts` | ❌ 不存在 | Red Team 无法自动执行 |

**根因**: 文档描述的是一套**理想的自动化管线**，但工具链从未实际创建。当前审计成果全部通过手动/半自动方式完成。

### 🔴 P0 — Schema 文件 2/2 全部缺失

| 文档声明 | 实际状态 |
|:---------|:--------:|
| `schemas/finding.schema.json` | ❌ 不存在 |
| `schemas/attack-result.schema.json` | ❌ 不存在 |

影响：findings.json 无 schema 约束，各视角产出物格式不一致（见 §3）。

### 🟡 P1 — 参考文档 2/2 全部缺失

| 文档声明 | 实际状态 |
|:---------|:--------:|
| `tools/README.md` | ❌ 不存在 |
| `docs/v4-addendum.md` | ❌ 不存在 |

---

## 2. Phase / Gate 映射真实性审查

文档声称"15 gates"由 `gate-check.sh` 调度，但：

| Phase | 文档状态 | 实际状态 |
|:------|:--------:|:--------:|
| Phase 0-3 | 工具自动化 | 半自动完成 ✅ |
| Phase 4 Fix | `apply-fix.ts` | **手动修复** |
| Phase 4.5 Test | `test-template.ts` | **文档不存在** |
| Phase 5 Static | `tsc --noEmit` | ✅ 手工执行 |
| Phase 5.5 Smoke | 烟雾测试脚本 | ✅ curl 手工测试 |
| Phase 5.6 Dynamic | `dynamic-test-runner.sh` | ❌ 不存在 |
| Phase 5.7 Chaos | `chaos-test.sh` | ❌ 不存在 |
| Phase 5.8 Mutation | `sed-mutation-test.sh` | ❌ 不存在 |
| Phase 6 Loop | `convergence-check.sh` | ❌ 不存在 |
| Phase 6.5 Devil's Advocate | 独立对抗审查 | ✅ Agent 多视角审查 |
| Phase 7 Final | `verify-report.sh` | ❌ 不存在 |

**发现的偏差**:
- Phase 5.6-5.8 的测试工具全部缺失
- Phase 6 的收敛检查脚本不存在（手动判断收敛）
- Phase 7 的零缺陷认证脚本不存在
- 文档声称"All pipeline steps required — no skips"，但实际执行中大量步骤被跳过

---

## 3. 5 Blue Team 视角审查

### 3.1 视角覆盖

| Lens | Briefing 存在 | Findings 存在 | 质量 |
|:-----|:------------:|:------------:|:----:|
| security | ✅ | ✅ 9 findings | 优（有 causal_chain） |
| concurrency | ✅ | ✅ 10 findings | 优（有 causal_chain） |
| dataflow | ✅ | ✅ 5 findings | 优（有 causal_chain） |
| error | ✅ | ✅ 10 findings | 中（部分无 causal_chain） |
| resource | ✅ | ✅ 8 findings | 中（所有无 causal_chain） |
| performance | ✅ | ✅ 16 findings | 低（全无 causal_chain） |
| a11y | ✅ | ✅ 30 findings | 低（全无 causal_chain） |

### 3.2 发现的问题

**🟡 P1 — 发现格式不统一**
- `findings.json` 中 42/88 有 `hash` + `causal_chain`（concurrency/dataflow 视角）
- 46/88 无 `hash` + `causal_chain`（a11y/performance/resource/security/error 视角）
- `findings/audit-blue-*.json` 中的格式与 `findings.json` 不一致

**🟡 P1 — Briefing 与 Finding 命名不一致**
- 部分 briefings 名为 `audit-blue-a11y.json`、`audit-blue-concurrency.json`
- 但 findings 文件名为 `audit-blue-a11y.json`、`audit-blue-concurrency.json`
- 文件名相同但内容结构不同（briefings 含 signals_to_hunt，findings 含具体发现）

**🟢 P2 — Briefing 生成未完全遵循规范**
- 规范要求的 5 个标准透镜（security/concurrency/dataflow/error/resource）之外，还产生了 a11y/performance/ux/integration 等额外透镜
- 额外透镜无独立 briefing，但产生了 findings 文件

---

## 4. 框架设计层面的审查

### 4.1 设计优势

| 特征 | 评价 |
|:-----|:-----|
| **多视角 Blind Audit** | 5 独立透镜无共享 briefing 的设计有效防止了"从众效应" |
| **Causal Chain 追踪** | 强制要求从"触发条件→传播路径→影响范围→根因分类"的完整链路 |
| **Cross-Run Dedup** | 通过稳定哈希避免跨运行发现重复 |
| **Severity 分级明确** | P0/P1/P2/P3 的判定标准清晰可操作 |
| **Phase Failure Protocol** | 重试→日志→熔断→人工介入的递进式处理合理 |

### 4.2 设计缺陷

**🔴 P0 — 工具链与文档严重脱节**
文档描述的是一套高度自动化的工具链，但实际零落地。这导致：
- 新开发者看到文档以为有 CI/CD 级别的自动化，实际需要全手动
- Gate 检查无法自动执行，依赖人工判断
- "All pipeline steps required" 在实际执行中无法保证

**🔴 P0 — "No verbal zero defect" 规则不可执行**
规则要求每个 finding 必须有 `test_ids` + `mutation_killed=true`，但：
- 没有任何 finding 有 `test_ids` 字段
- `sed-mutation-test.sh` 工具不存在
- 没有 mutation test 被实际执行过
- 当前"零缺陷"声明是**口头声明**

**🟡 P1 — 审计状态文件缺乏演进轨迹**
`.audit-cache/audit_state.json`:
- 只记录当前 phase，不记录历史转换
- 没有 iteration 计数器
- 没有哪些发现已关闭、哪些新增的记录
- 无法支持增量审计

**🟡 P1 — Step 3 Blue Team 的 Agent 要求未定义**
文档说 `orchestrator spawns 5 general agents` 但没有定义：
- Agent 的 context window 要求
- Agent 的输出格式约束（除"写入 findings 文件"外）
- findings 合并冲突时的裁决规则

**🟢 P2 — Red Team 流程不完整**
文档描述了 `red-team-runner.ts` → `red-team-verify.ts`，但：
- 未定义 Red Team 的攻击向量模板
- 未定义 M3 跨模型验证的具体标准
- 未定义 Red Team 结果的 severity 映射规则

---

## 5. 改进建议

### 5.1 紧急（P0）

| 建议 | 说明 |
|:-----|:-----|
| **1. 工具链落地或文档降级** | 要么创建 `tools/` 中的 12 个脚本，要么将文档降级为"手动审计流程指南" |
| **2. 删除不可执行的规则** | "No verbal zero defect" 规则在 mutation test 不存在时不可执行，应先实现工具再声明规则 |
| **3. 统一发现格式** | 为所有 88 个 finding 补充 `hash`、`causal_chain`、`test_ids` 字段 |

### 5.2 重要（P1）

| 建议 | 说明 |
|:-----|:-----|
| **4. 创建 finding.schema.json** | 定义统一的 finding 格式契约，所有视角的输出必须通过 schema 校验 |
| **5. 审计状态增加历史轨迹** | `audit_state.json` 增加 `history[]` 数组记录每次 phase 转换 |
| **6. 增量审计支持** | 基于 baseline-zero.json 实现"只审计新增/修改代码"模式 |
| **7. 定义 Agent 输出契约** | Blue Team Agent 的输出必须包含 severity/file/root_cause/fix 四个核心字段 |

### 5.3 建议（P2）

| 建议 | 说明 |
|:-----|:-----|
| **8. 建立 Red Team 攻击向量库** | 定义 10-20 个标准攻击向量模板供 Red Team 使用 |
| **9. Phase 5.6-5.8 工具先创建基础版** | 至少实现 smoke test 和 convergence check 两个脚本 |
| **10. 文档与实际同步** | 每次修改代码或工具后更新 docs/ 和 README |

---

## 6. 总体评价

| 维度 | 评分 | 说明 |
|:-----|:----:|:-----|
| **框架设计** | ⭐⭐⭐⭐ | 多视角、因果链、跨运行去重等设计理念优秀 |
| **文档完整性** | ⭐⭐⭐ | 流程描述清晰，但与实现脱节 |
| **工具链落地** | ⭐ | 12 个核心工具全部缺失 |
| **审计产出质量** | ⭐⭐⭐⭐ | 88 个发现全部有 remediation 和 severity |
| **审计格式一致性** | ⭐⭐ | hash/causal_chain 覆盖率 48%，格式不统一 |
| **规则可执行性** | ⭐ | "No verbal zero defect" 等规则不可执行 |

**结论**: Super-Fix 是一个**设计优秀的审计框架**，但目前处于"文档驱动、手动执行"的阶段。核心价值在于其方法论（多视角 Blind Audit + 因果链追踪 + 跨运行去重），而非其自动化工具链。建议优先落地 `finding.schema.json` + `convergence-check.sh` 两个最小可行工具，然后逐步补齐其余工具。
