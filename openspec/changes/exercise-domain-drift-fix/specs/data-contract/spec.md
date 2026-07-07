## Requirement: partialScore 类型同步

数据字典声称 partialScore 是 String 但 DB 是 BOOLEAN。MUST 同步数据字典为 BOOLEAN。

#### Scenario: 数据字典同步
- WHEN 本变更完成
- THEN 数据字典 partialScore 类型 MUST 改为 BOOLEAN
