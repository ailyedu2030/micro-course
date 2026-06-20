# Super-Fix v4 Addendum

> 微课管理平台专属审计规程补充
> 版本: v4.0 | 基于 Super-Fix 零信任审计框架

---

## 1. 项目专属审计范围

### 1.1 子系统清单

| 子系统 | 路径 | 审计透镜 |
|--------|------|---------|
| 后端 API | `micro-course-api/src/main/java/com/microcourse/controller/` | security, dataflow |
| 业务服务 | `micro-course-api/src/main/java/com/microcourse/service/` | concurrency, error |
| 数据访问 | `micro-course-api/src/main/java/com/microcourse/repository/` | resource, dataflow |
| 数据实体 | `micro-course-api/src/main/java/com/microcourse/entity/` | dataflow |
| 互动插件 | `micro-course-api/src/main/java/com/microcourse/plugin/interactive/` | security, error, resource |
| 数据库迁移 | `micro-course-api/src/main/resources/db/migration/` | dataflow |
| 前端页面 | `micro-course-admin/src/views/` | a11y, performance, ux |
| 前端组件 | `micro-course-admin/src/components/` | a11y |
| 前端 API 层 | `micro-course-admin/src/api/` + `src/utils/request.js` | security, error |

### 1.2 角色权限矩阵

| 角色 | 代码 | 审计重点关注 |
|:-----|:-----|:-------------|
| 学生 | `STUDENT` | 选课/学习/练习/讨论 功能完整性 |
| 教师 | `TEACHER` | 课件上传/讲述稿/TTS/学员管理 |
| 管理员 | `ADMIN` | 课程审核/用户管理/系统设置 |
| 教务处 | `ACADEMIC` | 数据统计/审核权限 |

---

## 2. 审计透镜信号定义

### 2.1 Security Lens

| 信号 | 检查内容 |
|:-----|:---------|
| IDOR | 对象引用是否验证用户所有权或选课状态 |
| SpEL | `@PreAuthorize` 中 `authentication.principal` 使用是否正确 |
| JWT 密钥 | 默认值是否存在，是否可被覆盖 |
| XSS | 前端 `v-html`/`innerHTML`/`srcdoc` 使用 |
| 文件上传 | 类型/大小校验，路径穿越 |
| 速率限制 | 登录端点是否限流 |

### 2.2 Concurrency Lens

| 信号 | 检查内容 |
|:-----|:---------|
| TOCTOU | check-then-insert 是否存在竞态 |
| 丢失更新 | read-compute-write 是否原子 |
| CAS | 状态转换是否有条件更新 |
| 线程池 | @Async 是否配置线程池 |
| 安全上下文 | 异步线程是否传播 SecurityContext |

### 2.3 Dataflow Lens

| 信号 | 检查内容 |
|:-----|:---------|
| SQL 注入 | 字符串拼接的 LIKE 或 LIMIT |
| 参数校验 | `@Valid`/`@PositiveOrZero`/`@Range` 是否缺失 |
| 状态机 | 状态转换是否完整 |
| Self-invocation | `@Async`/`@Transactional` 是否被自调用绕过 |

### 2.4 Error Lens

| 信号 | 检查内容 |
|:-----|:---------|
| 空 catch | 异常是否被静默吞掉 |
| 资源泄漏 | try-with-resources 是否缺失 |
| 超时 | RestTemplate/HTTP 调用是否有超时配置 |
| 错误暴露 | 异常 message 是否返回给客户端 |

### 2.5 Resource Lens

| 信号 | 检查内容 |
|:-----|:---------|
| N+1 | 循环内 selectById |
| 无分页 | selectList(null) |
| 线程泄漏 | 无界线程池 |
| 流泄漏 | InputStream/Process 未关闭 |

---

## 3. Finding 严重度映射（项目专属）

| ErrorCode 范围 | 严重度 | 说明 |
|:--------------|:------:|:-----|
| 1001-1099 (认证) | P0 | 登录/Token 问题 |
| 2001-2099 (院系) | P2 | 院系管理 |
| 3001-3099 (专业) | P2 | 专业管理 |
| 4001-4099 (班级) | P2 | 班级管理 |
| 5001-5099 (用户) | P1 | 用户管理 |
| 6001-6099 (课程) | P1 | 课程管理 |
| 7001-7099 (选课) | P1 | 选课/学习 |
| 8001-8099 (视频) | P1 | 视频/转码 |
| 9001-9099 (练习) | P1 | 练习/答题 |
| 10001-10099 (讨论) | P2 | 讨论区 |
| 11001-11099 (通知) | P2 | 通知 |
| 12001-12099 (证书) | P2 | 证书/徽章 |
| 13001-13099 (评论) | P2 | 评价 |
| 14001-14099 (签到) | P2 | 签到 |
| 15001-15099 (管理) | P1 | 管理后台 |
| 16001-16099 (插件) | P1 | 互动课程插件 |

---

## 4. 已知例外与豁免

| 例外 | 原因 | 状态 |
|:-----|:-----|:-----|
| DEEPSEEK_API_KEY 未配置时 AI 生成返回 500 | 外部服务依赖 | 已加友好提示 |
| student/teacher/academic 测试账号可能被禁用 | 运营操作，非代码问题 | 已恢复 |
| integration test 需要数据库 | CI 环境才有 | 本地可跳过 |
| 前端 chunks >400kB | 第三方库体积 | 优化优先级低 |

---

## 5. 快速参考

```bash
# 最新审计状态
cat .audit-cache/audit_state.json | python3 -m json.tool

# 收敛检查
bash tools/convergence-check.sh

# 烟雾测试
bash tools/smoke-test.sh

# 门禁检查（全部）
for gate in PHASE_0_ENTRY PHASE_2_REVIEW PHASE_4_FIX PHASE_5_STATIC PHASE_5_5_SMOKE PHASE_6_LOOP PHASE_7_FINAL; do
    bash tools/gate-check.sh "$gate"
done

# 变异测试
bash tools/sed-mutation-test.sh micro-course-api/src/main/java/com/microcourse/service/impl/EnrollmentServiceImpl.java

# 预检
bash .claude/skills/microcourse/scripts/precheck.sh

# 全量编译+测试
cd micro-course-api && mvn compile && cd ../micro-course-admin && npm run build
```

---

## 6. 审计历史

| 日期 | 版本 | 发现 | 修复 | 工具链进度 |
|:----|:----|:----:|:----:|:---------:|
| 2026-06-19 | v1.13.0 | 59 | 59 | 0% |
| 2026-06-20 | **v1.15.0** | **88** | **88** | **33% (4/12)** |
| 2026-06-21 | **v1.16.0** | **88** | **88** | **100% (12/12)** |
