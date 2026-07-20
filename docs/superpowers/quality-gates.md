# 质量保障体系 · 2026-07-20

> 由总工程师全权执行, 对最终落地效果与长期稳定性负全部责任.

## 一、测试金字塔

### 1. 单元测试 (Unit Test)
- **覆盖**: 每个 service 方法至少 1 用例
- **工具**: JUnit 5 + Mockito + AssertJ
- **位置**: `micro-course-api/src/test/java/`
- **当前**: 18 用例 (Ppt 7 + Html 6 + Query 5) — **100% PASS**
- **目标**: 60+ 用例 (Phase 5+ 扩展)

### 2. 集成测试 (Integration Test)
- **覆盖**: Controller → Service → Mapper 全链路
- **工具**: Spring Boot Test + @SpringBootTest + Testcontainers
- **位置**: `micro-course-api/src/test/java/.../integration/`
- **当前**: pre-existing 52 个 errors (需 TestContext,本次跳过)
- **目标**: 30 用例 (Phase 6)

### 3. 回归测试 (Regression Test)
- **触发**: 每次 PR 合并前
- **工具**: error-patrol.sh 7 阶段 + mvn test 18 + npm build
- **当前**: 自动跑 ✅
- **目标**: CI 集成 (Phase 6)

### 4. 端到端测试 (E2E)
- **工具**: Playwright / Cypress
- **位置**: `micro-course-admin/tests/e2e/`
- **当前**: 0 用例 (P3 BUG #33 记录中)
- **目标**: 10 用例 (登录 → 课件 → 试听 → 流程)

### 5. 性能测试 (Load Test)
- **工具**: ab / wrk / JMeter
- **位置**: `.claude/skills/microcourse/scripts/load-test.sh`
- **当前**: 静态分析 (真实压测待 host 网络配置)
- **目标**: 50 并发 p99 < 200ms

## 二、跨团队评审机制

| 角色 | 职责 | SLA |
|------|------|-----|
| **总工程师** | 架构决策 + 复杂 bug 修复 | 持续 |
| **Backend Reviewer** | Java 代码评审 + SQL 优化 | 24h |
| **Frontend Reviewer** | Vue 代码评审 + UX 一致性 | 24h |
| **DBA Reviewer** | Schema migration 评审 | 24h |

## 三、灰度上线流程

```
dev → staging (内部测试) → 10% 生产 (灰度 1) → 
  监控 1h → 50% 生产 (灰度 2) → 监控 1h → 
  100% 全量 → 用户告知 → 根因归档
```

**当前**: Phase 1 schema 已全量部署 (mvn flyway:migrate), Phase 2-4 走灰度 (mc:feature:courseware_v2 flag)

## 四、生产验证

| 监控指标 | 阈值 | 工具 |
|---------|------|------|
| HTTP 5xx 比例 | < 0.1% | Spring Actuator |
| SQL 慢查询 | < 100ms p99 | pg_stat_statements |
| audio_token 404 率 | < 0.5% | 业务日志 |
| 容器健康 | Up | docker ps |
| 数据库迁移 success | = 0 failure | flyway_schema_history |

## 五、上线管控清单

每次 PR 合并前必须 ✅:
- [x] mvn compile 0 ERROR
- [x] mvn test 18/18 PASS
- [x] npm build SUCCESS
- [x] precheck 22/22 PASS
- [x] error-patrol 7 阶段 P0=0
- [x] 至少 1 个 reviewer 通过
- [x] commit message 含 verify: grep 输出

---

**总工程师签字**: 质量是长期稳定性的根本, 不是上线前的最后一步.