## ADDED Requirements

### Requirement: 全量回归测试执行
系统 SHALL 在 V1.20.0 基线上对全部 396 个后端 API 端点和 127 个前端 Vue 页面执行 4 维回归测试 (按钮异常 / 业务常识 / 交互错乱 / 功能残缺),不允许跳过任何端点或页面。

#### Scenario: 后端 API 全量覆盖
- **WHEN** 测试 Agent 接到"全量回归"任务
- **THEN** Agent MUST 扫描并执行全部 396 个 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping 端点

#### Scenario: 前端页面全量覆盖
- **WHEN** 测试 Agent 接到"全量回归"任务
- **THEN** Agent MUST 扫描并执行全部 127 个 .vue 页面,覆盖每个页面所有按钮和表单

#### Scenario: 4 维校验强制
- **WHEN** 任一最小测试单元 (单页面 + 单按钮 + 单业务分支) 被执行
- **THEN** Agent MUST 对该单元跑完 4 维校验 (按钮异常 / 业务常识 / 交互错乱 / 功能残缺),不允许合并混测

#### Scenario: 高优先级按钮优先
- **WHEN** 遇到动态渲染按钮 / 条件显隐按钮 / 多分支切换组件 / 跨页面数据联动 / 新增编辑保存类接口
- **THEN** Agent MUST 标记为高优先级并优先执行,严格按 Vibe Coding SOP §1.3 红线要求

### Requirement: 缺陷报告标准化
系统 SHALL 强制每个发现的缺陷按 7 字段输出:测试单元编号 / 页面+按钮名称 / 代码路径 / 测试业务分支 / 预期行业规则 / 实际系统表现 / 缺陷分类(P0/P1-C/P1-I/P2 四级) / 根因定位 / 修复方案。

#### Scenario: 7 字段强制完整
- **WHEN** 测试 Agent 报告任一缺陷
- **THEN** 报告 MUST 包含 7 字段全部内容,缺一字段视为无效报告

#### Scenario: P0 立即修复
- **WHEN** 缺陷被分类为 P0 (数据安全/核心功能不可用/客户首次操作必现错误)
- **THEN** Agent MUST 立即执行修复,不允许标"待修"或"建议修复"等回避用语

### Requirement: 提交规范
系统 MUST 严格按 Conventional Commits 提交,scope 必须是 Phase 编号,所有修复 MUST 包含根因分析而不只是表面修复。

#### Scenario: 提交格式
- **WHEN** Agent 完成任一缺陷修复
- **THEN** commit message MUST 遵循 `type(scope): description` 格式,type ∈ {feat, fix, refactor, test, docs, chore},scope 是 Phase 编号

#### Scenario: 根因分析
- **WHEN** Agent 修复任一缺陷
- **THEN** commit body MUST 包含"根因"段落,描述为什么 BUG 产生 + 为什么这种修法是根因修法 (非表面)
