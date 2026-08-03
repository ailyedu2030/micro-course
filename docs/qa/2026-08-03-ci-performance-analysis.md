# CI 后端测试耗时分析（2026-08-03）

> 目的：回答"PR 测试为什么需要那么长时间"并给出可执行的提速路径。
> 数据来源：本地 surefire 报告 + GitHub Actions 实测（PR #175/#176/#177 + main push）。

## 1. 实测数据

| 环节 | 耗时 | 占比 |
|------|------|------|
| backend（mvn verify：Precheck + 1123 测试 + JaCoCo 检查 + repackage） | 27~32 分钟 | ~90% |
| e2e（Playwright，等 backend 后串行） | 3m36s~3m42s（正常） | — |
| frontend / lint / monitoring / secrets / references / Trivy / docker | 各 1~4 分钟 | 并行 |
| auto-approve（等全部检查） | 数秒~数分钟 | — |

**结论：后端测试是绝对瓶颈（27~32 分钟），其余环节合计 <8 分钟且大多并行。**

## 2. 为什么后端要 27~32 分钟（结构性原因，非 bug）

本地 surefire 报告统计（180 测试类 / 1123 用例）：
- **测试执行时间总和 ≈ 26 分钟**，其中 Top-15 慢类合计 ~7 分钟
  （Phase9IntegrationTest 62s、CoverageRemainingEndpointsTest 58s、NullSafetyAndAcademicRoleRegressionTest 53s 等）
- 配置 `forkCount=1 + reuseForks=false`：**每个测试类启动独立 JVM**，Spring 上下文按类加载/销毁
  - 这是 2026-07-26 **P0-12 稳定性修复**的既定结果：此前共享 JVM 在 ~150 测试后触发
    classpath 资源缓存损坏（`sql/p0-seed.sql` 找不到），团队判断"单 JVM 共享 classloader 不可靠"
  - 代价：每类额外 JVM 启动 + 上下文加载开销（约 5~8 分钟量级）
- `parallel=classes` 已被实测否决：共享 Redis 锁被并行测试覆盖 → AuthFlowIntegrationTest 401 vs 200
- JaCoCo `prepare-agent` 全量插桩 + `check`（LINE ≥ 30% 门禁）有固定开销（质量门禁，保留）

## 3. 已落地/已修的安全优化

| 变更 | 内容 | 预期收益 |
|------|------|---------|
| PR #177 | e2e Bug-G 事件驱动等待 + retries 2 + CI 全仓串行 | 消除假失败与并行波动（e2e 首跑即过已验证） |
| 本次 | backend job `-Dspring-boot.repackage.skip=true` | 省去 fat jar 打包（~30~90s，e2e job 独立构建不依赖） |

## 4. 进一步提速的候选路径（按风险排序，需独立专项评估）

1. **单元/集成测试分层**（中等工作量，中等风险）：纯单元测试（无 @SpringBootTest）单独
   用 `reuseForks=true + parallel=classes` 跑，Spring 集成测试维持现有串行；
   需先盘点 180 类中单元测试占比，实测收益。
2. **集成测试隔离**（高工作量，高风险）：为并行 fork 提供独立 Redis DB index / DB schema，
   解除共享状态依赖后才能安全并行——这是打破 26 分钟地板的唯一结构性手段。
3. **慢类治理**（低风险小收益）：对 Top-15 慢类逐一审计（重复断言/冗余端点扫描），
   但不建议删覆盖测试（会削弱覆盖率门禁）。
4. **JVM 启动优化**（低风险不确定）：`-XX:TieredStopAtLevel=1` 减少 fork 预热，
   对长测试类可能略微回退 JIT，需 A/B 实测。

> 原则：不重蹈 P0-12（稳定性换速度）覆辙；任何并行化改动必须先通过
> AuthFlowIntegrationTest + 全量回归 + 连续 3 次 CI 全绿。
