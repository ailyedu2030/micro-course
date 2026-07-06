## ADDED Requirements

### Requirement: 165 项缺陷全量回放
系统 SHALL 对 2026-07-06 `docs/审计/项目业务逻辑审计-2026-07-06-细粒度/` 中的 165 项缺陷(去重 120 项)逐一回放,确认"已修"项真修、找出"未修"项并立即修复。

#### Scenario: 已修项验证
- **WHEN** 审计报告中标记"✅ 已修"的缺陷 (如 P0-S01 LearningProgress 无选课校验)
- **THEN** Agent MUST 编写测试用例验证修复有效,跑通测试 = 确认已修,跑不通 = 视为新缺陷重新修复

#### Scenario: 待修项强制修复
- **WHEN** 审计报告中标记"⚠ 待修"的缺陷 (如 P0-S02 金标 TOCTOU / P0-S03 用户状态 switch / P0-S04 Token 刷新时序 / P0-S05 申报自审批 / 5 个 P0 联动)
- **THEN** Agent MUST 立即执行修复 + 跑通测试 + 提交 commit,**绝对不允许**再标"待修"

#### Scenario: 缺陷 ID 严格匹配
- **WHEN** Agent 修复审计中的某项缺陷
- **THEN** commit message MUST 包含原始审计 ID (如 `fix(phase15): P0-S02 金标 TOCTOU 修复`),便于追溯

### Requirement: 修复后回归
系统 MUST 对每项修复后立即跑相关链路回归测试,确保修复不破坏其他链路。

#### Scenario: 修复后联动测试
- **WHEN** Agent 完成任一 P0/P1-C 修复
- **THEN** Agent MUST 跑至少 1 个跨链路联动测试 (如改 UserService 后跑登录+选课+学习全链路)
