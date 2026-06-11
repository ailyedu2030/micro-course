# Phase 0 · 根因报告

> 记录日期：2026-06-11
> 触发事件：从 `/Volumes/Coding/微课平台/` 迁至 `/Users/jackie/微课平台/`，进入新 session 后发现现有代码与开发文档存在系统性偏移
> 目的：把"发生了什么、为什么、怎么防"完整归档，作为后续 Phase 的对照基线

---

## 1. 现状快照（迁入时 grep 实证，非观点）

### 1.1 业务代码（5 个 Controller + 4 个 Service + 4 个 Entity + 20+ DTO）

```
micro-course-api/src/main/java/com/microcourse/controller/AuthController.java
micro-course-api/src/main/java/com/microcourse/controller/ClassController.java
micro-course-api/src/main/java/com/microcourse/controller/DepartmentController.java
micro-course-api/src/main/java/com/microcourse/controller/MajorController.java
micro-course-api/src/main/java/com/microcourse/controller/UserController.java
```

| 文件计数 | 数量 |
|---------|------|
| Java 文件 | 51 |
| Vue 文件 | 8 |
| 孤立 skill 文件 | 5 |

### 1.2 真文档之间的路径冲突（3 份文档用 `/api/v1`，与 API契约 v1.1 冲突）

```
docs/完整性审查报告.md
docs/权限矩阵.md
docs/项目结构规范.md
```

### 1.3 §14 过时路径（开发规范 729 行规范已规定 cp 路径，但项目已迁）

```
docs/开发规范.md
```

---

## 2. 15 项地基偏移（实证，每项 1 行）

| # | 类别 | 位置 | 真文档要求 | 实际 | 偏移度 |
|---|------|------|----------|------|--------|
| 1 | 响应 code | `dto/R.java:18` | `200` | `0` | 100% |
| 2 | 响应 message | `dto/R.java:19` | `"ok"` | `"操作成功"` | 100% |
| 3 | 响应 timestamp | `dto/R.java:8-12` | 必含 Long 毫秒 | 无字段 | 100% |
| 4 | 分页字段 | `dto/PageResult.java:10-13` | `items/page/size/totalElements/totalPages` | `items/total/page/pageSize` | 60% |
| 5 | 错误码 | `exception/ErrorCode.java:4-10` | 业务码 1xxx-5xxx 共 13 个 | 7 个通用码 | 0 业务码 |
| 6 | Auth 接口 | `controller/AuthController.java` | 5 个（login/refresh/logout/cas/me） | 1 个 | 缺 4 |
| 7 | JWT claims | `util/JwtUtil.java:27-28` | `sub/username/role/departmentId/iat/exp` | `userId/role` | 缺 4 字段 |
| 8 | JWT exp | `util/JwtUtil.java:23` | `7200s` + RefreshToken `604800s` | `86400s`，无 refresh | 100% |
| 9 | 权限注解 | 5 个 Controller | `@PreAuthorize` 20+ 条 | 0 | 100% |
| 10 | /status 端点 | `controller/UserController.java` | `PUT /api/users/{id}/status` | 缺 | 缺 1 |
| 11 | 班级删除前置 | `service/impl/ClassServiceImpl.java:105-112` | 检查 `users.class_id` | 无检查直接删 | 100% |
| 12 | Flyway | `pom.xml` | 必需 | 无依赖 | 100% |
| 13 | Redis | `pom.xml` | 必需（登录锁定+Token 黑名单） | 无依赖 | 100% |
| 14 | 软删实现 | `service/impl/UserServiceImpl.java:90-101` | `status=0` 设 `deleted_at`，`status=1` 清 | `status=0` 设 `deleted_at`，无反向 | 半 |
| 15 | 脱敏 | `dto/UserVO.java` + Service | realName/email/phone 必脱敏 | 明文 | 100% |

### 偏离度统计

- 100% 偏离：11 项
- 半偏离：1 项
- 60% 偏离：1 项
- 缺失：2 项
- **合计 15 项，0 项完全合规**

---

## 3. 5 层根因（自上而下）

### R1 · 目标层：没有把"真文档"作为不可商量的基线

AI 在生成代码时，**没有把 docs/ 下的 11 份真文档当作基线逐字段对齐**，而是基于"自己理解的接口形态"写代码。表现：
- `R.java` 用 `code=0` —— 没有 grep 验证 API契约-Phase1 v1.1 的 54 处一致
- `PageResult` 用 `pageSize/total` —— 没有 grep 验证 v1.1 的 5 字段格式
- `JwtUtil` 缺 `username/departmentId` —— 没有 grep 验证状态机设计 v1.0 §1.3 T1 的 claims 字段

### R2 · 治理层：skill 是平行第二套真相，但从未被任何加载器引用

```
docs/开发技能/
├── Phase1-API契约.skill.md     # 580 行
├── Phase1-业务逻辑.skill.md    # 218 行
├── Phase1-数据契约.skill.md    # 327 行
├── Phase1-验证清单.skill.md    # 327 行
└── 结构宪法.skill.md           # 217 行
                    ↓
        这些文件**永远不会被 AI 读到**：
        - 不在 ~/.claude/skills/ 路径
        - 不在 opencode.json/skills 注册
        - 9 份穷举审查文档列表**不包含**这 5 份
        ↓
        5 份 skill 与 11 份真文档无交叉引用 = 两套真相并行
```

**表现**：AI 启动时只读 11 份真文档（如果会读的话），5 份 skill 形同虚设。后缀 `.skill.md` 只是名字像 skill，物理路径和加载机制都不在 skill 标准体系内。

### R3 · 流程层：缺"开发前对齐"动作

AI 直接读 controller 反推契约，没有"先 grep 真文档、再写代码"的工序。
- 没有 pre-commit 钩子
- 没有 `verification-checklist.md` 9 节跑通门禁
- 9 节验证清单里 10+ 项无法 grep 验证（如"权限注解一致性"需 JVM 启动测试）

### R4 · 工具层：precheck.sh 不存在、verification 不可执行

`结构宪法.skill.md` §3 写的 7 条 grep 是给人看的、不是 CI 跑的。**没有可执行脚本固化规则**。

### R5 · 路径层：项目从 `/Volumes/Coding` 迁到 `/Users/jackie` 后 §14 未更新

`开发规范.md` §14.1-14.3 教子智能体 cp 文件到旧路径 `/Volumes/Coding/微课平台/`。**项目已迁，§14 没人改**。这导致：
- 子智能体按过时路径 cp → 文件可能丢失或写到错地方
- "修文档"本身就在错地方修（如果总工程师没发现）

---

## 4. 5 条防范（写进地基宪法 v1.0）

### P1 · 文档即代码

真文档是基线，任何代码提交前必须先 grep 与真文档字段名一致。

**落地**：`.claude/skills/microcourse/scripts/precheck.sh` 固化 12 条 grep 预检（包含字段名/路径/枚举/状态码 4 类对齐）。

### P2 · 单一加载路径

skill 必须放在 `.claude/skills/<name>/SKILL.md`（Claude Code 标准），AI 才读得到。

**落地**：删除 `docs/开发技能/` 整个目录；新建 `.claude/skills/microcourse/` 标准结构（SKILL.md + 7 份 references/ + scripts/）。

### P3 · pre-commit 钩子

git commit 前自动跑 precheck.sh + 字段对齐 grep。

**落地**：在 `.git/hooks/pre-commit` 注册 precheck.sh 入口（Phase 1 实现）。

### P4 · 字段冲突零容忍

未在 `docs/冲突评审决议.md` 中标注的字段冲突 = 阻塞合并。

**落地**：本次 G1 已出 5 条裁决（C1-C5）；后续 Phase 必须先在冲突评审决议中登记，再写代码。

### P5 · 路径一致

开发规范 §14 必须与 `pwd` 一致；项目迁移时 §14 同步更新。

**落地**：本次 G3 已修 §14.1-14.3 路径到 `/Users/jackie/微课平台/`；后续若再次迁移，必须同步修订。

---

## 5. 复盘结论

```
✅ 5 层根因已识别
✅ 15 项偏移已实证
✅ 5 条防范已立项
✅ Phase 0 准出：地基工程 v1.0 进入执行
```

---

*文档版本：v1.0*
*最后更新：2026-06-11*
*维护者：总工程师*
