# 开发规范 v1.4 → v1.5 (dev-standards)

## Purpose

新增 5 条禁止项, 防止课程管理域已有的"魔数/Controller 业务逻辑"等问题在其他域复发。

## MODIFIED Requirements

### Requirement: §3.4.1 禁止状态字段硬编码

Java 代码 MUST NOT 在状态字段上使用数字字面量, MUST 通过 CourseStatus 枚举引用, precheck.sh MUST 静态扫描并 CI fail。

#### Scenario: Java 状态字段必须引用枚举
- WHEN 开发者写 Java 代码
- THEN 禁止 `.eq(Course::getStatus, 5)` 等数字字面量
- AND MUST 用 `.eq(Course::getStatus, CourseStatus.X.getCode())`
- AND precheck.sh 静态扫描违反则 CI fail

### Requirement: §3.4.2 禁止 Controller 含 SecurityUtil.hasRole

Controller MUST NOT 包含除 @PreAuthorize 之外的角色判断逻辑, 角色判断 MUST 下沉到 Service 层, precheck.sh MUST 静态扫描并 CI fail。

#### Scenario: Controller 只做参数提取和委托
- WHEN 开发者写 Controller 方法
- THEN 禁止 `SecurityUtil.hasRole(...)` 等角色判断 (除 @PreAuthorize)
- AND 角色判断 MUST 下沉到 Service 层
- AND precheck.sh 静态扫描违反则 CI fail

### Requirement: §3.4.3 禁止 Controller 含文件魔数校验

Controller MUST NOT 直接进行文件魔数/大小校验, MUST 通过 FileUploadUtil 工具类调用, controller-lint.sh MUST 扫描违反则 WARN。

#### Scenario: 文件验证下沉到工具类
- WHEN 开发者写 Controller 含 MultipartFile 处理
- THEN 禁止直接读 InputStream 验魔数
- AND MUST 调用 FileUploadUtil.assertImageMagic() / assertVideoMagic() 等工具方法
- AND controller-lint.sh 扫描违反则 WARN

### Requirement: §3.4.4 禁止 Controller 含私有工具方法

Controller MUST NOT 包含私有工具方法, MUST 放在 microcourse/util/ 包下, controller-lint.sh MUST 扫描违反则 WARN。

#### Scenario: 工具方法归 util 包
- WHEN 开发者写 Controller
- THEN 禁止 `private static` 工具方法
- AND MUST 放 `microcourse/util/` 包
- AND controller-lint.sh 扫描违反则 WARN

### Requirement: §3.4.5 Contract-First 新增端点必须先更新契约

新增 API 端点 MUST 同步更新 OpenAPI 契约, MUST 添加 @Operation/@Parameter/@ApiResponse 注解并跑 openapi-gen.sh, ContractEndpointCoverageTest MUST 验证一致, 缺一则 CI fail。

#### Scenario: 新增端点必须同步 OpenAPI
- WHEN 开发者新增 @RequestMapping 端点
- THEN MUST 同时添加 @Operation/@Parameter/@ApiResponse 注解
- AND MUST 跑 scripts/openapi-gen.sh 重新生成 docs/api/openapi.yaml
- AND ContractEndpointCoverageTest 验证一致
- AND 缺一则 CI fail

### Requirement: §3.4.6 禁止绕过专用端点

业务状态变更 MUST 通过专用端点, 通用端点 PUT /api/courses/{id}/status MUST 仅允许非业务状态 (CLOSED, ARCHIVED), 任何旁路 MUST 阻断。

#### Scenario: 通用状态端点拒绝业务转换
- WHEN 开发者设计状态变更端点
- THEN 业务转换 (PENDING_REVIEW, PUBLISHED) MUST 用专用端点
- AND 通用端点 PUT /api/courses/{id}/status MUST 仅允许非业务状态 (CLOSED, ARCHIVED)
- AND 任何旁路 MUST 阻断