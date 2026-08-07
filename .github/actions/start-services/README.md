# start-services · composite action

> 启动 CI 依赖的 PostgreSQL 17 + Redis 7 容器并验证可达。
> `backend` / `e2e` 两 job 复用本 action，消除复制粘贴漂移（Q-6 单一来源，2026-08-07）。

## 用法

```yaml
- name: Start PostgreSQL + Redis
  uses: ./.github/actions/start-services
  with:
    db-url: jdbc:postgresql://172.17.0.1:5432/micro_course
    db-password: postgres
    redis-host: 172.17.0.1
    redis-port: "6379"
    redis-password: ""
    service-name: ci   # 同 runner 多 job 需区分时覆盖（容器名前缀）
```

## F-15 race condition 场景与修复（2026-08-07）

### 场景（P0 根因）

GitHub Actions 原生 `services:` 容器编排在 outage 后出现 **runner 零日志卡死 4 次**。
改用显式 `docker run` + 宿主网桥 `172.17.0.1` 可达后，仍存在一类 **race condition**：

> PostgreSQL 容器启动时，TCP 端口先可 accept（`docker run` 返回、映射生效），
> 但服务进程尚未完成 initdb / 连接握手 —— 此时应用立即连库会得到
> `connection refused` / `the database system is starting up`，被误判为"数据库未启动"。

`pg_isready` 的 `accepting connections` 判断也存在同一窗口：第一次检测通过后
紧接着的首次真实连接仍可能落在启动完成的临界点之前。

### 修复（本 action 内实现）

```
┌────────────────────────────────────────────────────────────────────┐
│ 1. 首段等待循环:  docker exec <svc>-pg pg_isready（每 2s，最多 30 次） │
│ 2. 首段超时兜底:  循环超时不静默 —— 立即 ::error:: 退出（暴露启动失败）  │
│ 3. 第二段重验循环: break 后 再跑 10 次 pg_isready（每 1s）             │
│                   —— 覆盖 "TCP 已 accept 但 initdb 未完成" 的窗口     │
│ 4. 终态硬校验:    再次 pg_isready 失败 → ::error:: 退出 1（fail-fast） │
└────────────────────────────────────────────────────────────────────┘
```

- Redis 同构：`redis-cli ping` 首段等待 + 第二段重验 + 终态硬校验。
- 任何阶段超时都以 `::error::` 输出并 `exit 1`，避免后续步骤在"半起状态"下模糊失败。

### 回归测试

`scripts/test-start-services-race-condition.sh`（F2 新增，2026-08-08）：

- **成功场景**：用 `busybox sleep`（无则 `sleep` 兜底）模拟 PostgreSQL 慢启动
  （延迟就绪标记），验证首段等待循环能在超时窗口内等到就绪、第二段重验通过。
- **fail-fast 场景**：模拟"服务启动时长 > 重试窗口"（永不就绪），验证循环
  在窗口内未就绪 → 启动方收到错误并 fail-fast，与 action 的 `::error::` 行为一致。

```bash
bash scripts/test-start-services-race-condition.sh
```

## 输入参数

| 参数 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `db-url` | 否 | `jdbc:postgresql://172.17.0.1:5432/micro_course` | 应用侧 JDBC 连接串（仅日志/审计） |
| `db-password` | 否 | `postgres` | PostgreSQL 密码（`docker run POSTGRES_PASSWORD`） |
| `redis-host` | 否 | `172.17.0.1` | 应用侧 Redis 主机（仅日志/审计） |
| `redis-port` | 否 | `6379` | Redis 端口（`docker run` 映射宿主端口） |
| `redis-password` | 否 | `""` | Redis 密码；非空时用 `redis-cli -a` 验证（P1-I-9 显式声明） |
| `service-name` | 否 | `ci` | 容器名前缀（`<name>-pg` / `<name>-redis`） |

## 输出

- `service-ready`：`true`（PostgreSQL + Redis 已启动并验证可达）
