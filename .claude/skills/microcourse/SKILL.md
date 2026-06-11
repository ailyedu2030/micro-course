---
name: 微课平台
description: |
  微课管理平台后端（Spring Boot 3 + MyBatis-Plus）与前端（Vue 3 + Element Plus）开发的统一规范与契约入口。
  当用户在本项目内做以下任何操作时加载本 skill：
  1. 创建/修改 micro-course-api/ 或 micro-course-admin/ 下的 .java / .vue / .ts 文件
  2. 涉及 4 张核心表（users / departments / majors / classes）的字段变更或 SQL migration
  3. 设计 REST API、修改响应格式 / 分页格式 / 错误码
  4. 创建新 DTO / Entity / Controller / Service / Vue 页面
  5. 编写测试用例、CI/CD 配置、Dockerfile
  6. 修订 docs/ 下任何开发文档

  强制约束：
  - 数据契约以 docs/数据字典.md v0.5 为唯一真相
  - API 契约以 docs/API契约-Phase1.md v1.2 为唯一真相
  - 目录结构遵守 docs/项目结构规范.md v1.1
  - 权限矩阵遵守 docs/权限矩阵.md v2.0
  - 开发规范遵守 docs/开发规范.md v1.4
  - 字段冲突未在 docs/冲突评审决议.md 中登记 = 阻塞合并
---

# 微课平台 · skill 入口

## 1. 何时加载本 skill

在 `/Users/jackie/微课平台/` 目录下，任何对源码、配置、文档的修改操作。

不加载场景：
- 只读查阅（grep / cat / read）
- 与项目无关的全局命令
- 询问"项目是什么"等元问题

## 2. 标准做法（4 步）

### Step 1 · 必查 references/

按需读取以下 6 份引用视图（references/ 是真文档的引用视图，不是副本）：

| 引用视图 | 真文档来源 | 触发场景 |
|---------|----------|---------|
| `references/data-contract.md` | `docs/数据字典.md` v0.4 | 字段类型/长度/约束/索引 |
| `references/api-contract.md` | `docs/API契约-Phase1.md` v1.1 | REST 路径/请求/响应/错误码 |
| `references/business-logic.md` | `docs/状态机设计.md` v1.0 + `docs/开发规范.md` v1.3 §3.3.1 | 状态机/转换规则/业务约束 |
| `references/structure-constitution.md` | `docs/项目结构规范.md` v1.1 | 目录树/负面清单/预检命令 |
| `references/permission-matrix.md` | `docs/权限矩阵.md` v2.0 | REST 端点/角色权限 |
| `references/verification-checklist.md` | Phase 1 验证清单 | 工单交付前自检 |

**重要**：references/ 内每份文件顶部含"源文档"链接，内容是"真文档的引用视图"。如发现引用视图与真文档不一致，**先修真文档**，再回改 references/。

### Step 2 · 必跑预检

```bash
bash .claude/skills/microcourse/scripts/precheck.sh <文件路径>
```

- 退出码 0 = PASS，可继续
- 退出码非 0 = FAIL，禁止写入文件

预检覆盖：12 条 grep（路径/类名/字段/枚举/状态码 5 类对齐）。

### Step 3 · 写代码

严格遵守 references/ 中标注的契约。Commit 格式：

```
<type>(<scope>): <描述>

type:    feat | fix | refactor | test | docs | chore
scope:   Phase 阶段编号 或 docs/ 或 skill/
示例:   feat(phase1): 实现用户登录接口
        docs(地基): 修订权限矩阵 v2.0
```

无 scope 的 commit = 偏移，PR 阶段会被驳回。

### Step 4 · 自检

交付前对照 `references/verification-checklist.md` 逐项打勾：

```
□ 文件位置正确（结构宪法 §2.1）
□ 字段名与数据契约一致（数据字典 v0.4）
□ 响应格式与 API 契约一致（API契约-Phase1 v1.1）
□ 权限注解与权限矩阵一致（权限矩阵 v2.0）
□ 业务规则与状态机一致（状态机设计 v1.0）
□ 命名规范符合项目结构规范（项目结构规范 v1.1）
□ 预检脚本 12 条 grep 全部 PASS
```

未通过 = 工单未交付，必须打回。

## 3. 绝对禁止（来自项目结构规范 §2 + 复盘根因报告 P1-P5）

### 文件位置

```
❌ 在项目根目录直接创建 .java / .vue / .ts
❌ Java 放在 docs/ scripts/ docker/ micro-course-admin/
❌ Vue 放在 micro-course-api/ docs/ scripts/ docker/
❌ Java 放在 micro-course-api/src/main/java/com/microcourse/ 之外
❌ Vue 页面放在 micro-course-admin/src/views/ 之外
❌ 全局组件放在 micro-course-admin/src/components/ 之外
❌ Entity 放在 micro-course-api/src/main/java/com/microcourse/entity/ 之外
❌ Controller 放在 micro-course-api/src/main/java/com/microcourse/controller/ 之外
```

### 重复定义

```
❌ 创建已存在的 Entity / Controller / Service / Mapper / DTO / Vue 组件
❌ 手动建数据库 migration 不更新 docs/数据字典.md
❌ 创建新文件前未跑 precheck.sh
```

### 分层职责

```
❌ Controller 写业务逻辑（必须经 Service）
❌ Controller 直接返回 Entity（必须 DTO 转换）
❌ Service 直接暴露 Entity（必须 DTO 转换）
❌ Repository 写业务逻辑（只做数据访问）
❌ 前端硬编码 baseURL（必须 import.meta.env.VITE_API_BASE_URL）
❌ 手动拼接 SQL（必须 MyBatis-Plus 条件构造器）
❌ 后端直接返回 Entity 给前端
```

### 代码风格

```
❌ Java 类名 camelCase（正确：UserController）
❌ Java 字段名 snake_case（正确：userId）
❌ DB 字段名 camelCase（正确：user_id）
❌ Vue 文件名 kebab-case（正确：UserList.vue）
❌ Vue 路由路径 PascalCase（正确：/user-list）
❌ 前后端字段命名不一致
❌ Service @Transactional 不处理 rollbackFor
❌ Controller @ResponseBody 不配置消息转换器
```

### 架构边界

```
❌ 后端绕过 Service 直接调 Repository
❌ 前端组件内直接调 axios（必须经 api/ 层）
❌ Vue 组件直接操作 Pinia 状态（必须 actions）
❌ 引入未在 pom.xml / package.json 声明的依赖
```

### 文档契约

```
❌ 在冲突评审决议未登记的字段冲突
❌ 字段名不与数据字典 v0.4 一致
❌ 响应格式不与 API 契约 v1.1 一致
❌ REST 路径不与权限矩阵 v2.0 一致（路径前缀 = /api，无 /api/v1/）
❌ 表名带 sys_ 前缀（应为 users / departments / majors / classes）
❌ 项目迁移后开发规范 §14 不更新
❌ 修改了 references/ 但未同步真文档
❌ 在问题处讨论问题（必须先修文档，再修代码）
```

## 4. 详细参考（指向 references/）

- **数据契约** → `references/data-contract.md`（38 张表 / 字段类型 / 索引 / FK）
- **API 契约** → `references/api-contract.md`（18 个 Phase 1 API / 响应格式 / 错误码 / 分页 5 字段）
- **业务逻辑** → `references/business-logic.md`（8 个状态机 / 业务规则 / 13 个错误码）
- **结构宪法** → `references/structure-constitution.md`（目录树 / 25 条负面清单 / 7 条预检命令）
- **权限矩阵** → `references/permission-matrix.md`（11 类资源 / 4 角色 / REST 端点）
- **验证清单** → `references/verification-checklist.md`（10 节 / Phase 1 工单交付门禁）

## 5. 子技能（实施层）

本 skill 是**宪法层**，定义"不做什么"和"契约是什么"。

**实施层技能**定义"应该怎么做"：

- **后端开发** → `../microcourse-backend/SKILL.md` — Controller/Service/Repository/DTO/Security/Exception 代码模板 + 6 份 templates/
- **前端开发** → `../microcourse-frontend/SKILL.md` — API 封装/Store 模式/权限前端/页面模板/Element Plus 规范 + ui-ux-pro-max 强制加载

编写 Java 代码时加载 `microcourse-backend`；编写 Vue 代码时加载 `microcourse-frontend`。

## 6. 加载验证

重启 Claude Code 后，确认本 skill 出现在 `~/.claude/skills/微课平台/SKILL.md` 路径下。YAML frontmatter 解析正确。

---

*skill 版本：v1.1*
*最后更新：2026-06-11*
*维护者：总工程师*
