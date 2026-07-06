# 开发规范 v1.5 → v1.6 (dev-standards)

## Purpose

新增 §3.4.7 全局响应契约禁止项, 防止 R.java timestamp 反复添加 / 分页参数漂移。

## MODIFIED Requirements

### Requirement: §3.4.7 全局响应契约不可漂移

所有 API 响应 MUST 遵守全局响应契约, 禁止未经评审的字段添加或参数不一致。

#### Scenario: 响应体格式强制
- WHEN 开发者写 Controller 返回 R<T> 响应
- THEN MUST 包含 code (int), message (String), data (T) 三个字段
- AND MUST NOT 包含 timestamp 或其他额外字段
- AND 禁止字段 MUST 在 docs/API契约-Phase1.md 评审登记

#### Scenario: 分页参数统一
- WHEN 开发者写分页端点
- THEN page MUST 从 0 开始 (0-based)
- AND size MUST 默认 20, 最大 100 (@Range(min=1, max=100))
- AND 禁止 1-based page 或 size > 100

#### Scenario: 错误码统一登记
- WHEN 开发者抛 BusinessException(ErrorCode.X)
- THEN ErrorCode.X MUST 在 docs/API契约-Phase1.md 错误码章节登记
- AND ErrorCode.X MUST 在 ErrorCode.java 定义
- AND 禁止使用裸数字 400/403/404/409 等, 必须引用 ErrorCode 枚举

#### Scenario: CI 门禁集成
- WHEN 本变更完成
- THEN docs/开发规范.md v1.5→v1.6
- AND §3.4.7 MUST 包含: 全局响应契约 + 分页参数 + 错误码 3 类约束
- AND precheck-extra.sh MUST 新增规则: 禁止 R.java 多余字段 (grep "timestamp\|R.java")