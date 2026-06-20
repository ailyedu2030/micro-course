# Super-Fix 工具索引

> 微课管理平台零信任审计工具链
> 版本: v1.0 | 状态: 逐步落地中

---

## 工具总览

| 工具 | 状态 | 用途 | 依赖 |
|:-----|:----:|:-----|:-----|
| `convergence-check.sh` | ✅ | **收敛检查** — 5 维度验证审计完成度 | mvn, npm, python3, curl |
| `smoke-test.sh` | ✅ | **烟雾测试** — 14 项 API 端点覆盖 4 角色 × 5 维度 | curl, python3 |
| `sed-mutation-test.sh` | ✅ | **变异测试** — 8 种变异算子验证测试覆盖率 | mvn, sed |
| `finding.schema.json` (schemas/) | ✅ | **发现格式契约** — JSON Schema 约束所有输出 | — |

### 计划中

| 工具 | 优先级 | 说明 |
|:-----|:------:|:-----|
| `init-audit.sh` | P1 | 审计初始化 + 并发锁 |
| `gate-check.sh` | P1 | 15 门禁分阶段校验 |
| `v4-audit.sh` | P1 | 全自动审计管线 |
| `regression-suite.sh` | P1 | P0 回归测试索引 |
| `cross-run-dedup.sh` | P2 | 跨运行稳定哈希去重 |
| `finding-hash.sh` | P2 | 规范化的 finding 哈希计算 |
| `after-action-review.ts` | P2 | AAR 学习报告生成 |
| `chaos-test.sh` | P2 | 故障注入测试 |

---

## 快速开始

```bash
# 1. 克隆项目后配置环境变量
cp .env.example .env
# 编辑 .env 填入 DB_USERNAME, DB_PASSWORD, JWT_SECRET, DEEPSEEK_API_KEY

# 2. 启动后端
cd micro-course-api
export DB_USERNAME=postgres DB_PASSWORD=change_me JWT_SECRET=<your_secret>
mvn spring-boot:run

# 3. 启动前端
cd micro-course-admin
npm run dev

# 4. 运行烟雾测试
bash tools/smoke-test.sh

# 5. 运行收敛检查
bash tools/convergence-check.sh

# 6. 运行变异测试
bash tools/sed-mutation-test.sh micro-course-api/src/main/java/com/microcourse/service/impl/EnrollmentServiceImpl.java
```

---

## 环境要求

| 工具 | 最低版本 | 验证命令 |
|:-----|:--------|:---------|
| Java | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 18+ | `node -v` |
| Python | 3.9+ | `python3 --version` |
| PostgreSQL | 17.5 | `psql --version` |
| Redis | 7+ | `redis-cli ping` |

---

## 审计流程速查

```
Phase 0:  初始化 → bash tools/init-audit.sh (TODO)
Phase 1:  Blue Team 审计 → 手动执行
Phase 2:  发现仲裁 → bash tools/convergence-check.sh
Phase 3:  修复 → 手动
Phase 4:  验证 → bash tools/smoke-test.sh
Phase 5:  零缺陷 → bash tools/convergence-check.sh --verbose
```

---

## 文件定位

```
.audit-cache/          审计缓存（状态/发现/baselines）
├── audit_state.json   审计阶段 + 历史轨迹
├── findings.json      全量发现（88 项）
├── baseline.json      跨运行去重基线
├── regression-index.json  P0 回归测试索引
├── briefings/          Blue Team 视角 briefings
├── findings/           各视角发现文件
└── meta-review.md     Super-Fix 框架元审查

schemas/               格式契约
└── finding.schema.json

tools/                 审计工具
├── README.md          本文件
├── convergence-check.sh
├── smoke-test.sh
└── sed-mutation-test.sh
```
