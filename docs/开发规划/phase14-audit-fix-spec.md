# Phase 14 微专业 · 审计-修复-验证执行规格说明书 v1.0

> 定位：Phase 14 微专业的**审计驱动开发**（Audit-Driven Development）执行契约
> 上游契约：`docs/开发规划/phase14-micro-specialty-spec.md` v1.1（功能/状态机/API/数据模型）
> 下游执行：主 Agent → 3 类子 Agent 协作（审查员 / 根因调查员 / 修复员）
> 配套文档：`docs/审查todo-list.md`（检查清单 166 项） + `docs/开发规范.md` v1.4
> 总工程师签发 · 2026-06-24

---

## 0. 目标与铁律

### 0.1 目标

对 phase14 微专业已合并 main 的代码（含 28 主体 API + 8 选课 API + 7 申报 API + 6 邀请 API + 16 页面）做**最小功能颗粒**的**多智能体穷举审查**——找到所有 bug、UI 缺陷、UX 卡点、权限漏洞、数据链断裂、状态机失守、通知漏发——**修复并端到端验证**，直到上线标准。

### 0.2 七条铁律（绝对不可违反）

1. **最小功能原则**：每个 HTTP 端点 + 每个 Vue 页面 + 每个状态机分支 = 1 个最小功能点，**禁止合并**。
2. **JSON 硬约束**：主 Agent → 子 Agent = JSON 任务书；子 Agent → 主 Agent = JSON 结果报告。**禁用自由格式**。
3. **3 类子 Agent 边界硬隔离**：审查员（find-only）不得改代码；根因调查员（reproduce+root-cause）可读代码不得改；修复员（apply+verify）必须先有前两者产出。
4. **单任务上下文控制**：单子 Agent 任务输入 + 输出 ≤ 80k tokens；超过则拆为 2+ 个子任务。
5. **每 5 个功能点**做一次 4 维交叉验证（代码质量 / DB 迁移 / 安全配置 / 跨域一致性）。
6. **门禁硬过**：任一 P0 阻塞 = 该功能点 FAIL → 修复员必须重做 → 重新审查 → 再次进入门禁。
7. **文档同步**：代码变更必须同步更新 `docs/数据字典.md` / `权限矩阵.md` / `功能清单.md` 中对应行，否则不算完成。

### 0.3 字段命名约束（绝对优先级）

> 来自 M1-08 干跑（2026-06-24）的 4 个 P3 经验沉淀。

| 优先级 | 来源 | 适用 |
|------|------|------|
| **P0** | `docs/API契约-Phase1.md`（API 字段名唯一真源） | 所有 must_pass 字段名判定 |
| **P1** | `docs/开发规划/phase14-micro-specialty-spec.md`（业务字段名参考） | 与 API 契约冲突时**以 API 契约为准** |
| **P2** | 本 spec §1 各功能点表格中列出的"验收关键项" | **如与 API 契约冲突，必须改本 spec**，不改代码 |
| **P3** | 子 Agent 任务书 acceptance 措辞 | 与 API 契约冲突时**以 API 契约为准** |

**审计判定原则**：

- 子 Agent 报告"字段名与 acceptance 措辞不一致，但与 API 契约一致" → **不算 issue**，verdict 可 PASS
- 子 Agent 报告"字段名与 API 契约不一致" → **必须 P1 issue**
- 子 Agent 报告"字段名与 API 契约一致但 acceptance 措辞不同" → 标 P3 DOC 仅记录，不阻塞

**主 Agent 维护动作**：发现本 spec 任何 acceptance 措辞与 API 契约冲突时，立即修订本 spec 的 acceptance 字段（不修代码、不修 API 契约）。

---

## 1. 最小功能点拆分（共 72 个）

### 1.1 拆分原则

- **1 个 HTTP 端点 = 1 个功能点**（含 request/response/状态码/权限/响应体）
- **1 个 Vue 页面 = 1 个功能点**（含路由守卫 / 加载态 / 错误态 / 空态 / 关键操作 / 数据链）
- **1 个状态机分支 = 1 个功能点**（from → to + 触发动作 + 前置条件 + 通知）
- **跨页面/跨端点的横切能力**（通知接线 / 分页 / 错误码 / 数据链）独立成点

### 1.2 M1 · 学生端（9 个功能点）

| ID | 功能点 | 后端文件 | 前端文件 | API 端点 | 验收关键项 |
|----|--------|---------|---------|---------|----------|
| M1-01 | 学生端"微专业"Tab 入口 | — | `router/index.js:91` + `Layout.vue` | — | 路由 + 菜单显示正确；STUDENT 角色可见；点击进入 MyMicroSpecialties |
| M1-02 | 学生"我的微专业"列表页 | `MicroSpecialtyEnrollmentServiceImpl.java` | `views/student/MyMicroSpecialties.vue` | `GET /api/micro-specialty-enrollments/my` | 显示 5 状态徽章（PENDING/APPROVED/IN_PROGRESS/COMPLETED/CERTIFIED）+ 进度条 + 跳转详情 |
| M1-03 | 学生微专业详情页（公开） | `MicroSpecialtyServiceImpl.java` | `views/student/MicroSpecialtyDetail.vue` | `GET /api/micro-specialties/{id}` | 展示课程编排 + 团队 + LEAD + 学分 + 申请按钮；DRAFT/CANCELLED 过滤 |
| M1-04 | 学生申请修读 | `MicroSpecialtyEnrollmentServiceImpl.java` | `views/student/MicroSpecialtyDetail.vue` 申请按钮 | `POST /api/micro-specialty-enrollments/apply` | 防重复申请 + 通知 LEAD |
| M1-05 | 学生重修重审（reapply） | `MicroSpecialtyEnrollmentServiceImpl.java` | `MyMicroSpecialties.vue` 重修按钮 | `POST /api/micro-specialty-enrollments/{id}/reapply` | REJECTED/DROPPED/FAILED → PENDING |
| M1-06 | 学生退课 | `MicroSpecialtyEnrollmentServiceImpl.java` | `MyMicroSpecialties.vue` 退课按钮 | `POST /api/micro-specialty-enrollments/{id}/drop` | IN_PROGRESS/APPROVED → DROPPED；本人校验 |
| M1-07 | 学生查看证书 | `MicroSpecialtyEnrollmentServiceImpl.java` | `MyMicroSpecialties.vue` 证书下载 | `GET /api/certificates?microSpecialtyId=` | COMPLETED → CERTIFIED 后显示下载按钮 |
| M1-08 | 课程广场微专业专区（二级 Hero） | `MicroSpecialtyServiceImpl.java` | `views/student/CourseSquare.vue` | `GET /api/micro-specialties/square` | 广场展示微专业卡片 + 跳转详情 |
| M1-09 | 学生端数据链端到端 | 全部 student 微专业后端 | 全部 student 微专业前端 | 上述全部 | 报名 → 通知 → 详情 → 学习 → 完成 → 证书 整条链路 |

### 1.3 M2 · 教师端（30 个功能点）

#### 1.3.1 教师工作流 6 页面

| ID | 功能点 | 前端文件 | 关联 API | 验收关键项 |
|----|--------|---------|---------|----------|
| M2-01 | 教师"我的微专业"列表 | `views/teacher/MicroSpecialtyList.vue` | `GET /api/micro-specialties?creatorId=me` | 列表 + 状态过滤 + 创建入口 |
| M2-02 | 教师微专业工作台 | `views/teacher/MicroSpecialtyManage.vue` | `GET /api/micro-specialties/{id}` + 子资源 | Tabs：概览/团队/课程/选课/进度/统计 |
| M2-03 | 教师课程编排 | `views/teacher/MicroSpecialtyCourseEdit.vue` | `GET/POST/PUT/DELETE /api/micro-specialties/{id}/courses` | 课程增删改 + 排序 + 必修标记 |
| M2-04 | 教师团队管理 | `views/teacher/MicroSpecialtyTeamEdit.vue` | `GET/POST/DELETE /api/micro-specialties/{id}/teachers` | 邀请教师 + 移除 + 跨学院审批 |
| M2-05 | 教师微专业申报 | `views/teacher/MicroSpecialtyProposal.vue` | `POST /api/micro-specialty-proposals` | 申报书填写 + 提交 |
| M2-06 | 教师"我的申报" | `views/teacher/MyProposals.vue` | `GET /api/micro-specialty-proposals/my` | 申报状态 + 撤回/重提 |
| M2-07 | 教师邀请列表 | `views/teacher/MicroSpecialtyInvites.vue` | `GET /api/micro-specialty-teachers/pending-invites` | 待我处理的邀请 |

#### 1.3.2 主体状态机 API（15 个）

| ID | 功能点 | API 端点 | Controller 文件 | 状态机分支 |
|----|--------|---------|---------------|----------|
| M2-08 | 教师提交微专业（DRAFT/REJECTED → PENDING_REVIEW） | `POST /api/micro-specialties/{id}/submit` | `MicroSpecialtyController:135` | 状态机 §2.1 路径 A |
| M2-09 | 教师开启微专业（APPROVED → RECRUITING） | `POST /api/micro-specialties/{id}/open` | `MicroSpecialtyController:160` | LEAD 确认 + 课程编排≥1 + 团队≥2 |
| M2-10 | 教师关闭微专业（RECRUITING → COMPLETED） | `POST /api/micro-specialties/{id}/close` | `MicroSpecialtyController:168` | 强制/自动 |
| M2-11 | 教师更新微专业 | `PUT /api/micro-specialties/{id}` | `MicroSpecialtyController:116` | requireLeadOf |
| M2-12 | 教师删除微专业 | `DELETE /api/micro-specialties/{id}` | `MicroSpecialtyController:125` | ADMIN 兜底 + LEAD |
| M2-13 | 教师获取微专业 | `GET /api/micro-specialties/{id}/enrollments` | `MicroSpecialtyController:94` | LEAD 限定 |

#### 1.3.3 课程编排子资源（4 个）

| ID | 功能点 | API 端点 |
|----|--------|---------|
| M2-14 | 编排列表 | `GET /api/micro-specialties/{id}/courses` |
| M2-15 | 编排新增 | `POST /api/micro-specialties/{id}/courses` |
| M2-16 | 编排更新 | `PUT /api/micro-specialties/{id}/courses/{itemId}` |
| M2-17 | 编排删除 | `DELETE /api/micro-specialties/{id}/courses/{itemId}` |

#### 1.3.4 团队邀请子资源（3 个）

| ID | 功能点 | API 端点 |
|----|--------|---------|
| M2-18 | 团队列表 | `GET /api/micro-specialties/{id}/teachers` |
| M2-19 | 邀请教师 | `POST /api/micro-specialties/{id}/teachers` |
| M2-20 | 移除教师 | `DELETE /api/micro-specialties/{id}/teachers/{teacherId}` |

#### 1.3.5 申报工作流（7 个）

| ID | 功能点 | API 端点 |
|----|--------|---------|
| M2-21 | 提交申报 | `POST /api/micro-specialty-proposals` |
| M2-22 | 我的申报 | `GET /api/micro-specialty-proposals/my` |
| M2-23 | 申报列表（ACADEMIC 视角） | `GET /api/micro-specialty-proposals` |
| M2-24 | 申报审批通过 | `POST /api/micro-specialty-proposals/{id}/approve` |
| M2-25 | 申报审批驳回 | `POST /api/micro-specialty-proposals/{id}/reject` |
| M2-26 | 申报撤回 | `POST /api/micro-specialty-proposals/{id}/withdraw` |
| M2-27 | 申报重提 | `POST /api/micro-specialty-proposals/{id}/resubmit` |

#### 1.3.6 邀请处理（4 个）

| ID | 功能点 | API 端点 |
|----|--------|---------|
| M2-28 | 待我处理邀请 | `GET /api/micro-specialty-teachers/pending-invites` |
| M2-29 | 接受/拒绝/退出/重新邀请 | `POST /{inviteId}/{accept,decline,leave,reinvite}` |

#### 1.3.7 路由/导航层

| ID | 功能点 | 文件 |
|----|--------|-----|
| M2-30 | 教师端 requiresLead 路由守卫 | `router/index.js:184-190` |

### 1.4 M3 · 教务处端（22 个功能点）

#### 1.4.1 6 个教务处页面

| ID | 功能点 | 前端文件 |
|----|--------|---------|
| M3-01 | 教务处微专业审核 | `views/academic/MicroSpecialtyReview.vue` |
| M3-02 | 教务处申报审批 | `views/academic/MicroSpecialtyProposalReview.vue` |
| M3-03 | 教务处金标审核 | `views/academic/MicroSpecialtyFeaturedReview.vue` |
| M3-04 | 教务处跨学院审核 | `views/academic/MicroSpecialtyCrossDeptReview.vue` |
| M3-05 | 教务处班级导入 | `views/academic/MicroSpecialtyClassImport.vue` |
| M3-06 | 教务处金标管理 | `views/academic/MicroSpecialtyGoldManage.vue` |

#### 1.4.2 教务处 API（16 个）

| ID | 功能点 | API 端点 |
|----|--------|---------|
| M3-07 | 教务处审核通过 | `POST /api/micro-specialties/{id}/approve` |
| M3-08 | 教务处审核驳回 | `POST /api/micro-specialties/{id}/reject` |
| M3-09 | 教务处强制取消 | `POST /api/micro-specialties/{id}/cancel` |
| M3-10 | 教务处归档 | `POST /api/micro-specialties/{id}/archive` |
| M3-11 | 教务处创建 | `POST /api/micro-specialties` |
| M3-12 | 教务处 LEAD 继任 | `POST /api/micro-specialties/{id}/transfer-leadership` |
| M3-13 | 金标申请 | `POST /api/micro-specialties/{id}/apply-featured` |
| M3-14 | 金标审核通过 | `POST /api/micro-specialties/{id}/approve-featured` |
| M3-15 | 金标审核驳回 | `POST /api/micro-specialties/{id}/reject-featured` |
| M3-16 | 取消金标 | `POST /api/micro-specialties/{id}/unset-featured` |
| M3-17 | 设金标金 | `POST /api/micro-specialties/{id}/set-gold-featured` |
| M3-18 | 取消金标金 | `POST /api/micro-specialties/{id}/unset-gold-featured` |
| M3-19 | 选课审批 | `POST /api/micro-specialty-enrollments/{id}/{approve,reject}` |
| M3-20 | 班级导入 | `POST /api/micro-specialty-enrollments/class-import` |
| M3-21 | 颁发证书 | `POST /api/micro-specialty-enrollments/{id}/issue-certificate` |
| M3-22 | 跨学院邀请审批 | `POST /api/micro-specialty-teachers/{inviteId}/review-cross-dept` |

### 1.5 M4 · 横切层（11 个功能点）

| ID | 功能点 | 文件 | 验收关键项 |
|----|--------|------|----------|
| M4-01 | 微专业主表状态机 11 分支全覆盖 | `MicroSpecialtyStatus.java` + `MicroSpecialtyServiceImpl.java` | 11 个 from→to 分支全部正确执行 |
| M4-02 | 修读记录状态机 10 分支全覆盖 | `MicroSpecialtyEnrollmentStatus.java` + `MicroSpecialtyEnrollmentServiceImpl.java` | 10 个分支 + reapply 路径 |
| M4-03 | 教师邀请状态机 8 分支全覆盖 | `MicroSpecialtyTeacherRole.java` + `MicroSpecialtyInviteServiceImpl.java` | 8 分支 + reinvite 复用记录 |
| M4-04 | 置顶审批状态机 4 分支 | `MicroSpecialtyFeaturedStatus.java` + `MicroSpecialtyFeaturedServiceImpl.java` | NONE/PENDING/APPROVED/REJECTED |
| M4-05 | LEAD 继任状态机 | `MicroSpecialtyServiceImpl.java:transferLeadership` | 原 LEAD ACTIVE→REMOVED + 新 LEAD INVITED→ACTIVE |
| M4-06 | 通知矩阵 23 类型接线 | `NotificationType.java` + 各 ServiceImpl | 23 个事件类型全部有 notify 调用 + 不重复发 |
| M4-07 | 分页 5 字段统一 | 所有分页 API | total/page/size/totalPages/list 全部一致 |
| M4-08 | 错误码使用一致性 | `ErrorCode.java` + 所有 Controller | 13 个错误码 0 重复 / 0 散落 / 0 错用 |
| M4-09 | 数据链 FK 完整 | 5 张新表 + 3 张扩展 | FK 链无断裂；ON DELETE 策略正确 |
| M4-10 | @PreAuthorize 全覆盖 + Service 二次校验 | 所有 Controller | 与权限矩阵 §5 100% 对齐 |
| M4-11 | 前端导航/菜单/路由守卫一致性 | `menuConfig.js` + `router/index.js` + 13 页面 | 菜单显示的页面全部有路由；路由可访问的全部有菜单入口 |

### 1.6 数量统计

| 模块 | 功能点 | 备注 |
|------|------:|------|
| M1 学生端 | 9 | 含端到端数据链 |
| M2 教师端 | 30 | 6 页面 + 24 API |
| M3 教务处 | 22 | 6 页面 + 16 API |
| M4 横切层 | 11 | 状态机+通知+数据链+权限 |
| **合计** | **72** | — |

---

## 2. 三类子智能体角色定义

### 2.1 角色矩阵

| 角色 | 代号 | 工具权限 | 输入 | 输出 | 关键边界 |
|------|------|---------|------|------|---------|
| **审查员** | `auditor` | read + grep + glob | 1 个功能点 JSON 任务书 | 1 份 JSON 审计报告 | ❌ 禁止 Edit/Write |
| **根因调查员** | `investigator` | read + grep + glob + bash(测试) | 1 个功能点 JSON 任务书（含 auditor 报告） | 1 份 JSON 根因报告 + 1 个可重放脚本 | ❌ 禁止 Edit/Write |
| **修复员** | `fixer` | read + grep + glob + Edit + Write + bash(测试) | 1 个功能点 JSON 任务书（含前两份） | 1 份 JSON 修复报告 + diff + 验证证据 | ✅ 允许改 spec 约定文件 |

### 2.2 共同行为约束

- **不可越界**：子 Agent 仅能修改任务书 `allowedFiles` 列出的文件；其他文件为 `forbiddenFiles`。
- **不可跳跃**：子 Agent 必须按任务书 `steps` 顺序执行，不得跳过任何一步。
- **不可偷工**：每步必须产出 `step_output`，缺失 = 任务失败。
- **不可沉默**：发现 spec 与代码不一致时，必须在 `discrepancies` 字段上报，由主 Agent 决策。
- **不可并发单点**：同一最小功能点**不得同时**分配 2+ 子 Agent。

---

## 3. JSON 任务书 Schema（主 → 子）

### 3.1 任务书主结构

```json
{
  "ticket_id": "M1-04",
  "round": 1,
  "module": "M1",
  "title": "学生申请修读",
  "agent_role": "auditor | investigator | fixer",
  "context": {
    "spec_section": "phase14 spec §7.5 POST /api/micro-specialty-enrollments/apply",
    "related_files": [
      "micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyEnrollmentController.java",
      "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyEnrollmentServiceImpl.java",
      "micro-course-admin/src/views/student/MicroSpecialtyDetail.vue"
    ]
  },
  "allowed_files": [
    "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyEnrollmentServiceImpl.java",
    "micro-course-admin/src/views/student/MicroSpecialtyDetail.vue"
  ],
  "forbidden_files": [
    "**/test/**",
    "docs/数据字典.md",
    "package.json",
    "pom.xml"
  ],
  "deliverables": [
    "JSON 报告（按 §4 schema）",
    "（fixer 角色）修改的代码 diff",
    "（fixer 角色）验证证据（curl 输出 / 截图 / 测试通过日志）"
  ],
  "acceptance": {
    "must_pass": [
      "POST /apply 必须返回 200 + 业务数据",
      "学生用户已 PENDING 时再申请 → 400 + 友好错误",
      "已 CANCELLED 微专业申请 → 403 + 友好错误",
      "前端按钮 loading 态 + 成功 toast + 列表刷新"
    ],
    "edge_cases": [
      "网络断开",
      "未登录访问（401）",
      "权限不足（403）",
      "并发重复申请（500 错误兜底）"
    ]
  },
  "steps": [
    "step 1: 读 spec 对应章节",
    "step 2: 读后端 Controller + Service 关键代码",
    "step 3: 读前端调用代码",
    "step 4: 列出发现的问题（按角色）"
  ],
  "budget": {
    "max_files_to_read": 8,
    "max_tokens_estimate": 30000,
    "max_runtime_minutes": 8
  }
}
```

### 3.2 字段约束

| 字段 | 必填 | 类型 | 约束 |
|------|------|------|------|
| `ticket_id` | ✅ | string | 必须匹配 §1 表格中的 ID（`M{模块}-{3位序号}`） |
| `agent_role` | ✅ | enum | 仅允许 `auditor` / `investigator` / `fixer` |
| `allowed_files` | ✅ | string[] | 子 Agent 仅可修改/读取此清单内文件；空数组 = 不可改 |
| `forbidden_files` | ✅ | string[] | glob 模式，禁止触碰 |
| `acceptance.must_pass` | ✅ | string[] | ≥1 条，每条是**可验证**的具体行为 |
| `budget.max_tokens_estimate` | ✅ | int | ≤80000 |
| `budget.max_runtime_minutes` | ✅ | int | auditor=8, investigator=12, fixer=20 |

---

## 4. JSON 结果报告 Schema（子 → 主）

### 4.1 审查员报告

```json
{
  "ticket_id": "M1-04",
  "agent_role": "auditor",
  "verdict": "PASS | FAIL",
  "findings": [
    {
      "id": "ISSUE-001",
      "severity": "P0 | P1 | P2 | P3",
      "category": "BUG | UX | UI | DATA | PERMISSION | STATE | NOTIFY | DOC",
      "file_path": "micro-course-api/.../MicroSpecialtyEnrollmentServiceImpl.java",
      "line_range": "85-92",
      "description": "未防重复申请：同一学生 PENDING 状态重复 POST /apply 会创建第二条记录",
      "evidence": "代码 L88 仅 check enrollment 是否存在，无 status 过滤",
      "fix_suggestion": "L88 改为 check(userId, microSpecialtyId, status in PENDING/APPROVED/IN_PROGRESS) → 抛 400 MICROSPC_DUPLICATE_APPLY"
    }
  ],
  "files_inspected": ["...", "..."],
  "step_outputs": {
    "step_1": "spec §7.5 要求 ... ",
    "step_2": "Controller L31 ... ",
    "step_3": "前端 L412 ... ",
    "step_4": "发现 3 个问题 ..."
  },
  "discrepancies": [
    "spec 要求: 通知 LEAD；代码未实现"
  ],
  "context_used_tokens": 28000
}
```

### 4.2 根因调查员报告

```json
{
  "ticket_id": "M1-04",
  "agent_role": "investigator",
  "verdict": "REPRODUCED | NOT_REPRODUCED | PARTIAL",
  "root_cause_chain": [
    "level_1: 用户体验层：学生重复点击申请按钮",
    "level_2: 前端缺 disabled 防抖",
    "level_3: 后端 Service 缺幂等检查",
    "level_4: 数据层无唯一约束"
  ],
  "reproduction_steps": [
    "1. 登录学生账号",
    "2. 进入微专业详情 M1-03",
    "3. 点击申请按钮两次（< 500ms）"
  ],
  "reproduction_evidence": "curl 输出 + DB 截图",
  "minimal_fix_proposal": {
    "approach": "后端 Service 加 status 过滤 + 前端按钮 debounce + DB 加部分唯一索引",
    "files_to_modify": ["MicroSpecialtyEnrollmentServiceImpl.java", "MicroSpecialtyDetail.vue"],
    "estimated_loc_change": 25
  },
  "non_reproduction_if_any": "未发现",
  "step_outputs": {...},
  "context_used_tokens": 35000
}
```

### 4.3 修复员报告

```json
{
  "ticket_id": "M1-04",
  "agent_role": "fixer",
  "verdict": "FIXED | PARTIAL | BLOCKED",
  "diff_summary": {
    "files_modified": [
      {"path": "...", "lines_added": 12, "lines_removed": 3, "lines_modified": 5}
    ],
    "total_loc_change": 20
  },
  "verification": {
    "type": "unit_test | integration_test | manual_curl | screenshot",
    "command": "mvn test -Dtest=MicroSpecialtyApplyTest",
    "result": "PASS",
    "output_excerpt": "Tests run: 5, Failures: 0"
  },
  "linked_issues_fixed": ["ISSUE-001", "ISSUE-002"],
  "doc_updates": {
    "data_dict_updated": false,
    "permission_matrix_updated": false,
    "function_list_updated": true
  },
  "discrepancies_to_resolve": [
    "spec 未列出 PENDING/APPROVED 状态下再次申请应返回的错误码，需补充到数据字典"
  ],
  "context_used_tokens": 42000
}
```

---

## 5. 执行工作流

### 5.1 单功能点工作流（3 步流水线）

```
┌─────────────┐      ┌──────────────┐      ┌─────────────┐
│  Auditor    │      │ Investigator │      │   Fixer     │
│  (find)     │ ───> │ (reproduce)  │ ───> │  (apply)    │
│  ≤8min      │      │  ≤12min      │      │  ≤20min     │
└─────────────┘      └──────────────┘      └─────────────┘
       │                    │                     │
       └──── JSON 报告 ─────┴──── JSON 报告 ──────┘
                              │
                              ▼
                        主 Agent 门禁
                       ┌────────────┐
                       │ 全部 PASS? │
                       └─────┬──────┘
                       Yes  │  No
                            │   └──> 修复员重做（带回 feedback）
                            ▼
                    进入下一个功能点
```

### 5.2 批次工作流

- 每 **5 个功能点**完成 = 一个**批次**
- 批次结束自动启动 **4 维交叉验证**（R1-R4，每个维度一个 reviewer）
- 4 维验证全 PASS → 批次归档
- 任一 FAIL → 修复后重审

### 5.3 阶段工作流

| 阶段 | 范围 | 入口条件 | 出口条件 |
|------|------|---------|---------|
| **阶段 0** | 写本 spec + 子 agent 验证 | 无 | spec 落地 + 1 个端到端干跑 |
| **阶段 1** | M1 学生端 9 个 | 阶段 0 完成 | 9/9 PASS + 1 批次交叉验证通过 |
| **阶段 2** | M2 教师端 30 个 | 阶段 1 完成 | 30/30 PASS + 6 批次交叉验证通过 |
| **阶段 3** | M3 教务处 22 个 | 阶段 2 完成 | 22/22 PASS + 4-5 批次交叉验证通过 |
| **阶段 4** | M4 横切层 11 个 | 阶段 3 完成 | 11/11 PASS + 2 批次交叉验证通过 |
| **阶段 5** | R1-R4 4 维交叉验证 | 阶段 4 完成 | R1-R4 全 PASS + 全量回归测试通过 |

### 5.4 门禁规则（硬）

| 门禁 | 规则 |
|------|------|
| 单功能点 | 修复员 verifier 全部 PASS + 0 P0 + 0 P1 |
| 单批次 | 5 个功能点全 PASS + 4 维交叉验证 R1-R4 全 PASS |
| 阶段 | 上一阶段所有批次 PASS + 阶段级别回归测试 PASS |
| 全量 | 72/72 PASS + 4 维最终 R1-R4 PASS + mvn test + npm run build 全绿 |

---

## 6. 进度追踪（动态）

### 6.1 状态文件

路径：`.audit-cache/phase14-progress.json`

```json
{
  "version": "v1.0",
  "started_at": "2026-06-24T...",
  "tickets": {
    "M1-01": {"status": "PASS", "audited_at": "...", "fixed_at": "...", "reviewer_signoff": "..."},
    "M1-02": {"status": "IN_PROGRESS", "current_role": "auditor", "current_round": 1},
    "M1-03": {"status": "FAIL", "current_role": "fixer", "current_round": 2, "fail_reasons": ["..."]},
    "M1-04": {"status": "PENDING"},
    "...": "..."
  },
  "batches": {
    "M1-batch-1": {"tickets": ["M1-01","M1-02","M1-03","M1-04","M1-05"], "R1": "...", "R2": "...", "R3": "...", "R4": "..."}
  }
}
```

### 6.2 状态机

```
PENDING → AUDITING → AUDITED_FAIL → INVESTIGATING → REPRODUCED → FIXING → FIXED → REVIEWING → PASS
                                │                                                            │
                                └────────── FIX 失败回流 ←─────────────────────────────────┘
```

---

## 7. 边界与禁止

### 7.1 子 Agent 绝对禁止

- 改 `pom.xml` / `package.json`（除非任务书明确授权）
- 改 `docs/` 下任何文档（spec/数据字典/权限矩阵）— 由主 Agent 同步
- 改 `src/main/resources/db/migration/*`（DDL 变更须经 R2 审查员 + 主 Agent 决策）
- 跳过任务书 `steps` 任何一步
- 同时执行 2+ 个功能点
- 跨工单传递状态（每个工单独立）
- 在 JSON 报告里写自由散文（必须结构化）

### 7.2 主 Agent 责任

- 任务书生成（含 allowed_files / forbidden_files 严格圈定）
- 子 Agent 报告审阅 + 决策下一步
- 跨工单状态同步
- 文档同步更新（数据字典 / 权限矩阵 / 功能清单 / 本 spec）
- 4 维交叉验证触发
- 阶段门禁把关
- git commit（必须标注阶段 + 交叉验证通过）

### 7.3 异常处理

| 异常 | 处理 |
|------|------|
| 子 Agent 超时 | 重派同任务书 + 标注 `timeout: true`；2 次超时 → 拆分任务书 |
| 子 Agent 报告 verdit = PASS 但验证失败 | 立即重新派 investigator 复审；不得直接进入下一工单 |
| 子 Agent 报告 verdit = FAIL 但 P0 数量 > 5 | 拆分任务书（按代码段拆） |
| 发现 spec 与代码根本性冲突 | 主 Agent 暂停流水线，**先修 spec 再修代码** |
| 发现跨工单问题 | 记录到 `discrepancies` 字段，**不在本工单内修复**，转下一工单头部 |

---

## 8. 干跑验证

为保证 spec 可执行，**阶段 0 末尾**必须用 1 个最简功能点（M1-08 课程广场微专业专区）完成完整 3 步干跑：

1. 主 Agent 生成 3 份任务书（auditor / investigator / fixer）
2. 3 个子 Agent 串行执行
3. 报告归档 + 进度文件更新
4. 主 Agent 门禁确认

通过 → 进入阶段 1；不通过 → 修订 spec 模板。

---

## 9. 文档同步清单

每次工单完成后，主 Agent 必须检查并更新：

| 文档 | 更新条件 |
|------|---------|
| `docs/数据字典.md` | 字段增删改 |
| `docs/权限矩阵.md` | 权限注解变化 |
| `docs/功能清单.md` | 功能点 PASS/FAIL 状态 |
| `docs/开发规划/phase14-audit-fix-spec.md` | 工单 ID 进入"已完成"表 |
| `CHANGELOG.md` | 累计阶段变更 |
| `.audit-cache/phase14-progress.json` | 状态更新 |

---

## 10. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 子 Agent 上下文爆炸 | 单工单 ≤80k tokens；超则按功能点拆 |
| 子 Agent 假性 PASS | 修复员必须提供 verifier 证据（测试/curl/截图） |
| 跨工单状态污染 | 每个工单独立 ticket_id；状态机隔离 |
| spec 错误传播 | R2 审查员专门盯数据字典 vs 代码 diff |
| 并发写入冲突 | 单工单单 Agent；批次结束后才进入下一批次 |
| 误删/误改 | forbidden_files 显式列出；超过 allowed_files 范围 = 报告 FAIL |

---

## 11. 附：4 维交叉验证触发规则

| 维度 | 触发时机 | reviewer 输入 | 报告字段 |
|------|---------|-------------|---------|
| **R1 代码质量+契约** | 每个批次末 | 该批次 5 个工单 diff | lombok 残留 / @Autowired / 分页 / ErrorCode / @PreAuthorize |
| **R2 DB 迁移** | 涉及 DDL 的批次末 | 新增/修改 migration vs 数据字典 | 字段类型 / FK / 索引 / 约束 |
| **R3 安全+配置** | 涉及 SecurityConfig/JWT 的批次末 | application.yml + pom.xml | CVE / 密钥 / 路径 |
| **R4 跨域一致性** | 每个批次末 | 跨工单的 FK / 命名 / 路径 / 接口 | 一致性偏差 |

---

*spec 版本：v1.0*
*签发日期：2026-06-24*
*总工程师：微课平台项目总负责*
