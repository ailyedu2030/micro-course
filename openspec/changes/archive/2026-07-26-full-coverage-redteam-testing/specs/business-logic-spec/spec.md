## ADDED Requirements

### Requirement: 状态机必须可观测
系统 SHALL 在 `docs/状态机设计.md` 中列出所有业务对象的状态机定义(初始/中间/终态/转换条件/触发事件),Agent 审计新发现的状态机问题 MUST 立即修复 + 更新文档。

#### Scenario: 状态机缺失
- **WHEN** 业务对象(如订单/课程/选课/审批)有隐式状态流转但无文档
- **THEN** Agent MUST 补全状态机定义到 docs/状态机设计.md

#### Scenario: 非法状态转换
- **WHEN** Agent 发现代码允许非法状态转换(如已退款订单又被取消)
- **THEN** MUST 添加状态转换校验 + 测试用例

### Requirement: 开发规范必须可执行
系统 MUST 在 `docs/开发规范.md` 中定义可机械校验的规则(如:Controller 禁写业务逻辑、Controller 禁返回 Entity、Lombok 禁用、构造器注入),Agent 审计发现违反 MUST 立即整改。

#### Scenario: 规范违反检测
- **WHEN** Agent 扫描发现 .java 文件使用了 @Autowired (违反"Lombok 禁用 + 构造器注入"规范)
- **THEN** MUST 改为构造器注入

#### Scenario: 分层违规检测
- **WHEN** Agent 发现 Controller 直接调用 Mapper (违反 Controller→Service→Repository 分层)
- **THEN** MUST 抽出 Service 层方法
