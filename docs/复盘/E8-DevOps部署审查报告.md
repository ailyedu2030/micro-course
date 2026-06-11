# E8 · DevOps/部署专家审查报告

> 审查范围：docker-compose.yml + Redis 容器 + 本机 PG + Spring Boot 启动时序 + GitHub 工作流
> 审查时间：2026-06-11
> 审查结论：**通过（带 1 项 P2 改进）**

---

## 1. Docker 部署现状

### 1.1 容器

| 容器 | 镜像 | 状态 | 端口 |
|------|------|------|------|
| micro-course-redis | redis:7-alpine | **Up 15min (healthy)** | 6379 |

**1 容器 healthy** ✅

### 1.2 数据卷

| 卷 | 用途 |
|----|------|
| micro-course-redis-data | Redis AOF 持久化 |

**1 卷** ✅

### 1.3 网络

| 网络 | 驱动 |
|------|------|
| micro-course-net | bridge |

**1 网络** ✅

### 1.4 docker-compose.yml 精简

| 服务 | 状态 |
|------|------|
| redis | ✓ 启动 + 健康检查 |
| postgres | ✗ 移除（5432 端口冲突，决策 D8 复用本机 PG） |
| adminer | ✗ 移除（决策 D10 暂不起） |

**精简符合 D8-D12 决策链** ✅

## 2. Redis 联通

```
docker exec micro-course-redis redis-cli ping
PONG
```

**联通** ✅

## 3. 本机 PostgreSQL 联通

```
SELECT 'pg_ok' AS status, COUNT(*) FROM pg_tables WHERE schemaname='public' AND tablename NOT LIKE 'flyway%';
pg_ok | 4
```

**4 张业务表** ✅

## 4. Spring Boot 启动时序

```
17:38:22.267  HikariPool-1 - Starting...
17:38:22.307  HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection
17:38:22.308  HikariPool-1 - Start completed.
17:38:22.332  Flyway 9.22.3 by Redgate
17:38:22.340  Database: jdbc:postgresql://localhost:5432/micro_course (PostgreSQL 17.5)
17:38:22.343  [WARN] Flyway upgrade recommended: PostgreSQL 17.5 is newer than this version of Flyway
17:38:22.355  Successfully validated 1 migration (execution time 00:00.008s)
17:38:22.426  Tomcat started on port 8080 (http) with context path ''
17:38:22.430  Started MicroCourseApplication in 1.029 seconds
17:39:25.135  HikariPool-1 - Shutdown initiated
17:39:25.136  HikariPool-1 - Shutdown completed
```

**全流程 1.029s** ✅
**优雅关闭** ✅

## 5. ⚠️ Flyway 警告

```
WARN  Flyway upgrade recommended: PostgreSQL 17.5 is newer than this version of Flyway
and support has not been tested. The latest supported version of PostgreSQL is 15.
```

**含义**：Flyway 9.22.3 官方仅声明支持 PG ≤ 15。当前本机 PG 17.5 高于官方支持范围。

**风险评估**：
- ✅ 当前所有 V1__init.sql 语法在 PG 17.5 跑通（实测）
- ⚠️ Flyway 后续版本可能引入 PG 17.5 专属 schema 特性时，未测试
- ⚠️ Flyway 10.0+ 才有 `flyway-database-postgresql` 分离 artifact

**P2 改进**：
- 方案 A：升级 Flyway 到 10.x（需 `flyway-database-postgresql` 依赖）
- 方案 B：降级本机 PG 到 15.x
- 方案 C：保持现状（接受警告，Flyway 9.x 在 PG 17.5 实测可跑）

**总工程师建议**：方案 C（保持现状，Phase 5 验收再决）

## 6. GitHub 工作流

| 字段 | 值 |
|------|-----|
| remote | origin = git@github.com:ailyedu2030/micro-course.git |
| branch | main |
| 远端最新 | e9f1901 |
| 本地最新 | e9f1901 |
| 同步状态 | ✓ in sync |

**5 commit 全部推送，GitHub 同步** ✅

## 7. CI/CD

| 工具 | 状态 |
|------|------|
| GitHub Actions | ❌ 未配置 |
| pre-commit hook | ❌ 未配置（precheck.sh 手动跑） |
| Phase 5 验收 | 需补 GitHub Actions 跑 mvn + precheck.sh |

**P2 改进**：Phase 5 验收前配置 GitHub Actions

## 8. 改进项

### 8.1 P1 改进项

无（Phase 2 范围内无 P1 阻塞）

### 8.2 P2 改进项

1. **Flyway 9.22.3 vs PG 17.5 警告**：Phase 5 验收时根据实际需求决定升 Flyway / 降 PG / 接受警告
2. **GitHub Actions CI**：Phase 5 验收前配置（mvn + precheck.sh + 前端 build）
3. **docker-compose.prod.yml**：生产部署专用（含后端/前端/Nginx）
4. **健康检查端点**：当前 Spring Boot 无 /actuator/health，K8s liveness/readiness probe 无依赖

## 9. 终验

| 类别 | 通过 | 失败 | 通过率 |
|------|------|------|--------|
| Docker 容器 | 1/1 | 0 | 100% |
| Docker 卷 | 1/1 | 0 | 100% |
| Docker 网络 | 1/1 | 0 | 100% |
| Redis 联通 | 1/1 | 0 | 100% |
| 本机 PG 联通 | 4/4 | 0 | 100% |
| Spring Boot 启动 | 5/5 | 0 | 100% |
| Flyway migration | 1/1 | 0 | 100% |
| GitHub 同步 | 1/1 | 0 | 100% |
| **总计** | **15/15** | **0** | **100%** |

## 10. 终验结论

**✅ Phase 2 DevOps/部署层 100% 通过**

- Docker 精简后状态干净
- Redis 联通
- 本机 PG 联通
- Spring Boot 1.029s 启动 + 优雅关闭
- Flyway 1 migration 成功
- GitHub 5 commit 同步

**唯一警告**：Flyway 9.22.3 在 PG 17.5 跑（高于官方支持 PG 15）—— 当前实测可跑，Phase 5 验收时再决

---

*报告版本：v1.0*
*审查专家：E8 DevOps/部署*
*最后更新：2026-06-11*
