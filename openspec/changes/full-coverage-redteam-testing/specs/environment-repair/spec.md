## ADDED Requirements

### Requirement: 工具链修复
系统 SHALL 修复本机 OpenSpec CLI 残缺 (simdutf 库) + Docker 配置残缺 (redis 容器 requirepass 空密码) 等环境问题,使工具链完整可跑。

#### Scenario: simdutf 库修复
- **WHEN** 运行 `openspec --version`
- **THEN** 系统 MUST 返回版本号而非 Abort trap 错误

#### Scenario: Redis 容器健康
- **WHEN** 运行 `docker ps` 看 micro-course-redis-1
- **THEN** 容器 MUST 状态为 Up(healthy) 而非 Restarting,重启次数 MUST 为 0

#### Scenario: requirepass 修复
- **WHEN** docker-compose.yml 中 redis 服务启动
- **THEN** `--requirepass` MUST 包含实际密码,不允许空密码或语法错误

### Requirement: 修复后环境验证
系统 MUST 在所有环境修复后跑 3 项冒烟测试: (1) `openspec list --json` 通 (2) `docker ps` 全绿 (3) `curl http://localhost:8080/actuator/health` 返回 UP。

#### Scenario: 冒烟全绿
- **WHEN** Agent 完成环境修复阶段
- **THEN** 3 项冒烟测试 MUST 全部通过,任一失败 MUST 立即修复,不允许"先跳过"
