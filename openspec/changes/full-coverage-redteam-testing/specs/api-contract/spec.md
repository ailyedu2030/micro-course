## ADDED Requirements

### Requirement: API 契约必须真实
系统 SHALL 在 `docs/API契约-Phase1.md` 中定义所有 REST API 的路径/方法/请求/响应/错误码/分页格式,Agent 审计新发现的契约违规 MUST 立即修复 + 更新文档。

#### Scenario: 响应格式违规
- **WHEN** Controller 返回格式与 API 契约不一致(如返回 Map 而非标准 Result<T>)
- **THEN** MUST 改为标准格式

#### Scenario: 错误码缺失
- **WHEN** 业务异常未映射到统一错误码
- **THEN** MUST 添加 BizException + 错误码登记到 API 契约

### Requirement: API 错误处理必须统一
系统 MUST 统一所有 Controller 的异常处理(用 @ControllerAdvice 全局),不允许"try-catch 后直接返回 null 或吞异常"。

#### Scenario: 异常吞掉检测
- **WHEN** Agent 发现代码用 try-catch 吞掉异常且只 log 不抛出
- **THEN** MUST 改为抛出 BizException 或重新抛出

#### Scenario: 错误码覆盖度
- **WHEN** Agent 发现 4xx/5xx 错误未携带业务错误码
- **THEN** MUST 添加 GlobalExceptionHandler 处理逻辑
