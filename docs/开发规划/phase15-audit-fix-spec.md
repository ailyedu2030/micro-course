# Phase 15 微专业申请表 · 审计-修复-验证执行规格说明书 v1.0

> 定位：Phase 15 整理收纳微专业申请表系统的**审计驱动开发**（Audit-Driven Development）执行契约
> 上游契约：`docs/开发规划/phase15-storage-application-spec.md` v1.0（功能/状态机/API/数据模型/导出）
> 下游执行：主 Agent → 3 类子 Agent 协作（审查员 / 根因调查员 / 修复员）
> 配套文档：`docs/审查todo-list.md`（检查清单）+ `docs/开发规范.md` v1.4
> 总工程师签发 · 2026-06-30

---

## 0. 目标与铁律

### 0.1 目标

对 Phase 15 整理收纳微专业申请表系统（含 12 个新增 API、4 张新子表、主表扩展 30+ 字段、4 个页面、1 个 DRAFT 状态机扩展、Word/PDF 导出能力）做**最小功能颗粒**的**多智能体穷举审查**——找到所有 bug、UI 缺陷、UX 卡点、权限漏洞、数据链断裂、状态机失守、导出异常——**修复并端到端验证**，直到上线标准。

### 0.2 七条铁律（绝对不可违反）

1. **最小功能原则**：每个 HTTP 端点 + 每个 Vue 页面 + 每个状态机分支 = 1 个最小功能点，**禁止合并**。
2. **JSON 硬约束**：主 Agent → 子 Agent = JSON 任务书；子 Agent → 主 Agent = JSON 结果报告。**禁用自由格式**。
3. **3 类子 Agent 边界硬隔离**：审查员（find-only）不得改代码；根因调查员（reproduce+root-cause）可读代码不得改；修复员（apply+verify）必须先有前两者产出。
4. **单任务上下文控制**：单子 Agent 任务输入 + 输出 ≤ 80k tokens；超过则拆为 2+ 个子任务。
5. **每 5 个功能点**做一次 R1-R5 交叉验证（代码质量 / DB 迁移 / 安全配置 / 跨域一致性 / 前端 UI/UX 3 子维度）。
6. **门禁硬过**：任一 P0 阻塞 = 该功能点 FAIL → 修复员必须重做 → 重新审查 → 再次进入门禁。
7. **文档同步**：代码变更必须同步更新 `docs/数据字典.md` / `权限矩阵.md` / `功能清单.md` 中对应行，否则不算完成。

### 0.3 字段命名约束（绝对优先级）

> 来自 Phase 14 M1-08 干跑（2026-06-24）的 4 个 P3 经验沉淀。

| 优先级 | 来源 | 适用 |
|------|------|------|
| **P0** | `docs/API契约-Phase1.md`（API 字段名唯一真源） | 所有 must_pass 字段名判定 |
| **P1** | `docs/开发规划/phase15-storage-application-spec.md`（业务字段名参考） | 与 API 契约冲突时**以 API 契约为准** |
| **P2** | 本 spec §1 各功能点表格中列出的"验收关键项" | **如与 API 契约冲突，必须改本 spec**，不改代码 |
| **P3** | 子 Agent 任务书 acceptance 措辞 | 与 API 契约冲突时**以 API 契约为准** |

**审计判定原则**：

- 子 Agent 报告"字段名与 acceptance 措辞不一致，但与 API 契约一致" → **不算 issue**，verdict 可 PASS
- 子 Agent 报告"字段名与 API 契约不一致" → **必须 P1 issue**
- 子 Agent 报告"字段名与 API 契约一致但 acceptance 措辞不同" → 标 P3 DOC 仅记录，不阻塞

**主 Agent 维护动作**：发现本 spec 任何 acceptance 措辞与 API 契约冲突时，立即修订本 spec 的 acceptance 字段（不修代码、不修 API 契约）。

---

## 1. 最小功能点拆分（共 50 个）

### 1.1 拆分原则

- **1 个 HTTP 端点 = 1 个功能点**（含 request/response/状态码/权限/响应体）
- **1 个 Vue 页面/关键组件 = 1 个功能点**（含路由守卫 / 加载态 / 错误态 / 空态 / 关键操作 / 数据链）
- **1 个状态机分支 = 1 个功能点**（from → to + 触发动作 + 前置条件 + 通知）
- **跨页面/跨端点的横切能力**（通知接线 / 分页 / 错误码 / 数据链）独立成点

### 1.2 M1 · 申请表填写与保存（8 个功能点）

| ID | 功能点 | 后端文件 | 前端文件 | API 端点 | 验收关键项 |
|----|--------|---------|---------|---------|----------|
| M1-01 | 创建空草稿（init） | `StorageApplicationController.java` + `StorageApplicationService.java` | `MyProposals.vue` 创建按钮 | `POST /api/storage-applications/init` | 返回新建 proposal.id；status=DRAFT；type=急需紧缺型；3 行固定签字自动创建 |
| M1-02 | 全量保存（PUT） | `StorageApplicationController.java` + `StorageApplicationService.java` | `MicroSpecialtyProposal.vue` 保存草稿按钮 | `PUT /api/storage-applications/{id}` | 主表更新 + 子表全量替换（DELETE+INSERT）；仅 DRAFT 状态 + 本人可操作 |
| M1-03 | 自动保存（PATCH auto-save） | `StorageApplicationAutoSaveService.java` | `MicroSpecialtyProposal.vue` 1500ms 防抖 | `PATCH /api/storage-applications/{id}/auto-save` | 仅更新非 null 字段；限流 1/s（Redis）；120s 无心跳返回 stale=true；UI 显示"已自动保存 HH:mm:ss" |
| M1-04 | 模块重置（reset-module） | `StorageApplicationController.java` | `MicroSpecialtyProposal.vue` 重置按钮 | `POST /api/storage-applications/{id}/reset-module` | 4 模块分别清空（COURSES/TEAM_MEMBERS/SIGNATURES/SHARED_UNITS）；固定 3 签字重置为空；仅 DRAFT 状态 |
| M1-05 | 全部重置（reset-all） | `StorageApplicationController.java` | `MicroSpecialtyProposal.vue` | `POST /api/storage-applications/{id}/reset-all` | 全部子表清空 + 重新初始化固定 3 签字；主表保留；仅 DRAFT 状态 |
| M1-06 | 富文本字数校验 | `StorageApplicationService.java` | `MicroSpecialtyProposal.vue` 模块① | —（内嵌于 PUT/submit 校验） | 培养目标去 HTML 后 ≥20 字符；建设背景去 HTML 后 ≥50 字符；空 `<p></p>` 视为 0 |
| M1-07 | 手机号格式校验 | `StorageApplicationService.java` | `MicroSpecialtyProposal.vue` 联系人手机号 | —（内嵌于 PUT/submit 校验） | 正则 `^1[3-9]\d{9}$`；前后端双重校验；错号返回 FORMAT_PHONE |
| M1-08 | 建设日期年份校验 | `StorageApplicationService.java` | `MicroSpecialtyProposal.vue` el-date-picker year mode | —（内嵌于 PUT/submit 校验） | 起始年 2024≤year≤当前年；结束年 ≥起始年 且 ≤当前年+10 |

### 1.3 M2 · 申请表管理与提交（6 个功能点）

| ID | 功能点 | 后端文件 | 前端文件 | API 端点 | 验收关键项 |
|----|--------|---------|---------|---------|----------|
| M2-01 | 我的申请表列表（my-drafts） | `StorageApplicationController.java` | `MyProposals.vue`（★ 增强） | `GET /api/storage-applications/my-drafts` | 分页 + 仅本人数据；按 updated_at DESC 排序；卡片展示 title/status/updatedAt；空态引导创建 |
| M2-02 | 申请表详情（GET by id） | `StorageApplicationController.java` | `MicroSpecialtyProposal.vue` 编辑态加载 | `GET /api/storage-applications/{id}` | 返回主表+4 子表全部数据；TEACHER 本人校验 + ACADEMIC/ADMIN 只读；404 STORAGE_APPLICATION_NOT_FOUND |
| M2-03 | 提交审核（submit） | `StorageApplicationController.java` + `StorageApplicationService.java` | `MicroSpecialtyProposal.vue` 提交按钮 | `POST /api/storage-applications/{id}/submit` | §8.2 全量校验 15 项；DRAFT→PENDING_REVIEW + 锁定编辑；通知 ACADEMIC；校验失败返回 19001 + 错误清单 JSON |
| M2-04 | 撤回申报（withdraw） | 复用 Phase 14 `MicroSpecialtyProposalController` | `MyProposals.vue` 撤回按钮 | `POST /api/micro-specialty-proposals/{id}/withdraw` | PENDING_REVIEW→WITHDRAWN；仅本人可操作；通知 ACADEMIC |
| M2-05 | 编辑态锁定（DRAFT 状态机守卫） | `StorageApplicationService.java` assertDraft() | — | —（内嵌于 PUT/PATCH/submit） | PENDING_REVIEW/APPROVED/WITHDRAWN 状态下 PUT/PATCH 均返回 400 MS_STATUS_INVALID |
| M2-06 | 终态保护（WITHDRAWN/APPROVED 不可再编辑） | `StorageApplicationService.java` assertDraft() | `MicroSpecialtyProposal.vue` 条件渲染 | — | 非 DRAFT 状态隐藏编辑按钮；手动调 API 返回 400 |

### 1.4 M3 · 图片签名与公章（4 个功能点）

| ID | 功能点 | 后端文件 | 前端文件 | API 端点 | 验收关键项 |
|----|--------|---------|---------|---------|----------|
| M3-01 | 签名/公章图片上传（upload-image） | `StorageApplicationController.java` | `SignatureUploader` 组件（模块④） | `POST /api/storage-applications/{id}/upload-image` | multipart/form-data；返回 URL + 缩略图 URL；本人校验 |
| M3-02 | 图片格式校验（魔数 + 后缀） | `StorageApplicationService.java` | 前端 accept="image/jpeg,image/png" | —（内嵌于 upload-image） | 魔数校验：JPEG(FFD8)/PNG(89504E47)；非 JPG/PNG → 400 IMAGE_FORMAT_INVALID |
| M3-03 | 图片大小校验（≤2MB） | `StorageApplicationService.java` | 前端 File.size 预检 | —（内嵌于 upload-image） | ≤2,097,152 bytes；超限 → 413 IMAGE_TOO_LARGE |
| M3-04 | 图片缩放处理（150×150 @150dpi） | `StorageApplicationService.java` BufferedImage | — | —（服务端处理） | MAX(宽,高)≤150px；保持宽高比缩放；存储路径 `/data/storage-applications/{id}/images/{uuid}.{ext}` |

### 1.5 M4 · 导出（4 个功能点）

| ID | 功能点 | 后端文件 | 前端文件 | API 端点 | 验收关键项 |
|----|--------|---------|---------|---------|----------|
| M4-01 | Word 导出（Apache POI） | `StorageApplicationExportService.java` | `StorageApplicationPreview.vue` [下载 Word] | `GET /api/storage-applications/{id}/export-word` | 前置校验 → 失败返回 19003 + 错误清单；生成 .docx 含 5 模块正式表格 + 图片嵌入；Content-Disposition 含 UTF-8 编码文件名 |
| M4-02 | PDF 导出（OpenPDF） | `StorageApplicationExportService.java` | `StorageApplicationPreview.vue` [下载 PDF] | `GET /api/storage-applications/{id}/export-pdf` | 前置校验同 Word；生成 .pdf 含中文 + 图片 + 页眉页脚 + 页码；不在事务内执行 |
| M4-03 | 导出前置校验（与提交校验共享 §8.2） | `StorageApplicationService.java` validateForSubmission() | — | —（export-word/export-pdf 调用前） | 15 项校验全部通过才允许导出；草稿状态降低校验力度（仅校验格式，不阻断） |
| M4-04 | 导出文件名规范 | `StorageApplicationExportService.java` | — | — | 格式：`【{高校全称}】整理收纳微专业申请表_{yyyyMMdd}.{pdf\|docx}`；高校全称为空→默认"未知高校"；RFC 5987 编码 |

### 1.6 M5 · 预览（2 个功能点）

| ID | 功能点 | 后端文件 | 前端文件 | API 端点 | 验收关键项 |
|----|--------|---------|---------|---------|----------|
| M5-01 | 预览数据接口 | `StorageApplicationController.java` | `StorageApplicationPreview.vue` | `GET /api/storage-applications/{id}/preview` | 返回完整 StorageApplicationDetailVO；TEACHER 本人 + ACADEMIC 可访问；所有子表数据完整 |
| M5-02 | A4 打印预览页渲染 | — | `StorageApplicationPreview.vue`（★ 新增） | — | A4 容器 210mm×297mm CSS 模拟；5 模块只读正式排版；富文本渲染为 HTML；签名/公章内嵌显示；@media print 适配；页面底部显示"填报日期 + 第 X 页/共 N 页" |

### 1.7 M6 · 5 个公共组件（5 个功能点）

| ID | 功能点 | 文件 | 用途 | 验收关键项 |
|----|--------|------|------|----------|
| M6-01 | RichTextWithCounter 富文本编辑器 | `micro-course-admin/src/components/common/RichTextWithCounter.vue` | 模块① 培养目标/建设背景/培养特色/质量保障/预期成果/补充说明 | 集成富文本编辑器（Quill/TinyMCE）；底部实时显示去 HTML 后字数（如"128/5000"）；超限警告样式 |
| M6-02 | DynamicTableEditor 动态表格编辑器 | `micro-course-admin/src/components/common/DynamicTableEditor.vue` | 模块② 课程体系 + 模块③ 教学团队 + 模块⑤ 共建共享单位 | JSON 列配置驱动；增/删/改行操作；至少保留 1 行校验；空行提示 |
| M6-03 | SignatureUploader 签名/公章上传器 | `micro-course-admin/src/components/common/SignatureUploader.vue` | 模块④ 签字盖章 | el-upload 封装；accept="image/jpeg,image/png"；上传前大小校验 ≤2MB；上传后缩略图预览；上传失败 toast + 重试按钮 |
| M6-04 | DatePicker 日期选择器（year mode only） | `micro-course-admin/src/components/common/DatePicker.vue` | 模块① 建设周期 + 模块④ 签字日期 | el-date-picker type="year" 封装；年份范围校验（前端实时反馈）；清空按钮 |
| M6-05 | SignatureBlock 签字区块组件 | `micro-course-admin/src/components/common/SignatureBlock.vue` | 模块④ 三级签字行 | 固定布局（意见 textarea + 签字图 + 公章图 + 日期）；LEAD/DEPT/SCHOOL 不可删除；SHARED_UNIT 可增删；空/已填状态视觉区分 |

### 1.8 M7 · MicroSpecialtyProposal.vue 重做（5 个功能点）

| ID | 功能点 | 文件 | 验收关键项 |
|----|--------|------|----------|
| M7-01 | 模块① 表头/基本情况（渲染+编辑+校验） | `views/teacher/MicroSpecialtyProposal.vue` | 高校全称 input + 微专业名称 input + 类型固定显示 + 开课学院 el-select 加载院系 + 联系人姓名/电话/邮箱（实时正则校验）+ 建设周期 el-date-picker year + 学分/学时/规模 input-number + 面向对象 textarea + 5 个富文本编辑器（含字数计数） |
| M7-02 | 模块② 课程体系（动态表格+编辑+校验） | 同上 | DynamicTableEditor 渲染（模块名称/课程名称/学时/学分/开课学期列）；添加行/删除行按钮；至少 1 行校验 |
| M7-03 | 模块③ 教学团队（动态表格+编辑+校验） | 同上 | DynamicTableEditor 渲染（序号自动/姓名/年龄/职称/单位/专业/曾授课程/拟授课程列）；添加/删除行；至少 1 行校验（通常≥3） |
| M7-04 | 模块④ 签字盖章（固定行+动态行+上传） | 同上 | 固定 3 行 LEAD/DEPT/SCHOOL 不可删除；SHARED_UNIT 动态行关联模块⑤；SignatureBlock 组件渲染；SignatureUploader 上传接入 |
| M7-05 | 模块⑤ 共建共享单位（动态表格） | 同上 | DynamicTableEditor 渲染（排序拖拽/单位名称/单位类型下拉）；添加/删除行；单位类型三选一 |

### 1.9 M8 · MyProposals.vue 增强（2 个功能点）

| ID | 功能点 | 文件 | 验收关键项 |
|----|--------|------|----------|
| M8-01 | 列表操作列新增"预览"按钮 | `views/teacher/MyProposals.vue` | 点击跳转 StorageApplicationPreview.vue?id=xxx；任一状态均可预览；loading 态 + 权限控制 |
| M8-02 | 列表操作列新增"导出"按钮 | 同上 | 点击触发 GET export-word / export-pdf 下载（下拉菜单或双按钮）；导出中 loading 态；导出失败 toast 错误信息（含校验失败清单） |

### 1.10 M9 · StorageApplicationPreview.vue 新增（2 个功能点）

| ID | 功能点 | 文件 | 验收关键项 |
|----|--------|------|----------|
| M9-01 | A4 纸渲染（只读正式排版） | `views/teacher/StorageApplicationPreview.vue`（★ 新增） | 页面加载时 GET preview 接口获取数据；5 模块正式排版（表格/签字区）；富文本渲染为 HTML；签名/公章图片内嵌 `<img>`；ACADEMIC 访问只读模式（底部审批按钮） |
| M9-02 | 打印样式（@media print） | 同上 | @media print 隐藏导航/操作栏；A4 尺寸精确匹配；分页符（page-break-after）控制；页眉"高校开放共享微专业资源平台推荐表"；页脚"填报日期 + 页码" |

### 1.11 M10 · MicroSpecialtyProposalReview.vue 增强（2 个功能点）

| ID | 功能点 | 文件 | 验收关键项 |
|----|--------|------|----------|
| M10-01 | 详情查看嵌入（完整申请表只读展示） | `views/academic/MicroSpecialtyProposalReview.vue` | 点击"查看详情"弹出抽屉/弹窗/Dialog 展示完整申请表；复用 StorageApplicationPreview.vue 只读渲染；含 Word/PDF 导出下载 |
| M10-02 | 审批操作（通过/驳回 + 通知） | 同上 | 通过按钮 POST /api/micro-specialty-proposals/{id}/approve；驳回弹窗填写 review_comment → POST reject；审批后列表刷新 + 通知申报人；操作 loading 态 + 二次确认 |

### 1.12 M11 · DB 迁移（4 个功能点）

| ID | 功能点 | Flyway 版本 | 验证关键项 |
|----|--------|------------|----------|
| M11-01 | 主表扩展 V120 | `V120__phase15_proposal_fields.sql` | 新增 15+ 列（university_full_name, contact_person_name, contact_phone, contact_email, construction_start_year, construction_end_year, total_hours, target_audience, background_significance, training_features, quality_assurance, expected_outcomes, additional_notes, validation_passed, last_auto_saved_at, type）；DEFAULT/可空不破坏旧数据；索引 idx_msp_type + idx_msp_status_type |
| M11-02 | 子表创建 V121+V122 | `V121__phase15_proposal_courses.sql` + `V122__phase15_proposal_team_members.sql` | proposal_courses 表（8 列 + 2 索引）+ proposal_team_members 表（11 列 + 2 索引）；ON DELETE CASCADE；NOT NULL 约束正确 |
| M11-03 | 签字/单位表 V123+V124 | `V123__phase15_proposal_signatures.sql` + `V124__phase15_proposal_shared_units.sql` | proposal_signatures 表（11 列 + 2 索引）+ proposal_shared_units 表（7 列 + 2 索引）；ON DELETE CASCADE；sign_level 枚举约束 |
| M11-04 | 状态机默认值 V125 | `V125__phase15_status_default.sql` | status 列 DEFAULT 'DRAFT'（仅对新行）；旧行 status 不变；兼容旧 PENDING_REVIEW 记录 |

### 1.13 M12 · 横切面（3 个功能点）

| ID | 功能点 | 涉及文件 | 验收关键项 |
|----|--------|---------|----------|
| M12-01 | 错误码注册与一致性（8 个新增） | `exception/ErrorCode.java` | 19001-19008 全部注册；编号无重复；httpStatus 正确映射；Controller 抛出时统一使用 ErrorCode 枚举 |
| M12-02 | 权限全覆盖（@PreAuthorize + Service 二次校验） | `StorageApplicationController.java` + `StorageApplicationService.java` | 12 个端点全部 @PreAuthorize；assertOwner + assertDraft 方法覆盖所有写操作；ACADEMIC 可读不可写；ADMIN 只读 |
| M12-03 | 通知接线（submit/approve/reject/withdraw） | `StorageApplicationService.java` + 复用 Phase 14 通知 | submit 通知 ACADEMIC；approve/reject/withdraw 通知申报人；通知类型 MS_PROPOSAL_SUBMITTED / MS_PROPOSAL_APPROVED / MS_PROPOSAL_REJECTED / MS_PROPOSAL_WITHDRAWN |

### 1.14 M13 · 端到端数据链（3 个功能点）

| ID | 功能点 | 涉及全部模块 | 验收关键项 |
|----|--------|------------|----------|
| M13-01 | 教师端完整旅程（创建→填写→提交→查看结果→导出） | M1-M9 全部 | init → 5 模块填写 → auto-save → 预览 → 提交 → 状态变迁 → 查看审批结果 → 导出 全线走通 |
| M13-02 | 教务处端审批旅程（列表→详情→通过/驳回） | M10 + 复用 Phase 14 | 列表查看 PENDING_REVIEW 申请 → 详情预览 → 审批通过/驳回 → 通知教师 → 教师重提/查看结果 |
| M13-03 | 跨状态数据一致性（DRAFT→PENDING_REVIEW→APPROVED/REJECTED→WITHDRAWN） | M1-M13 全部 | DRAFT 可编辑；PENDING_REVIEW 锁定；APPROVED 终态锁定；REJECTED 可重提→PENDING_REVIEW；WITHDRAWN 终态可删不可提 |

### 1.15 数量统计

| 模块 | 功能点 | 备注 |
|------|------:|------|
| M1 申请表填写与保存 | 8 | init/PUT/auto-save/reset-module/reset-all/字数/手机/日期 |
| M2 申请表管理与提交 | 6 | 列表/详情/提交/撤回/编辑锁定/终态保护 |
| M3 图片签名与公章 | 4 | 上传/格式校验/大小校验/缩放处理 |
| M4 导出 | 4 | Word/PDF/前置校验/文件名规范 |
| M5 预览 | 2 | 预览数据/A4 打印渲染 |
| M6 公共组件 | 5 | RichText/DynamicTable/SignatureUploader/DatePicker/SignatureBlock |
| M7 申请表重做 | 5 | 5 模块渲染/编辑/校验 |
| M8 列表页增强 | 2 | 预览按钮/导出按钮 |
| M9 预览页新增 | 2 | A4 渲染/打印样式 |
| M10 审批页增强 | 2 | 详情查看/审批操作 |
| M11 DB 迁移 | 4 | 主表扩展/子表创建/签字单位表/状态默认值 |
| M12 横切面 | 3 | 错误码/权限/通知 |
| M13 端到端数据链 | 3 | 教师旅程/教务旅程/跨状态一致性 |
| **合计** | **50** | — |

---

## 2. 功能点详细规格（按分组）

### 2.1 M1 · 申请表填写与保存

#### M1-01 · POST /api/storage-applications/init

```
ID：M1-01
名称：创建空草稿
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
          micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
前端文件：micro-course-admin/src/views/teacher/MyProposals.vue（创建按钮 + 调用）
API 端点：POST /api/storage-applications/init
权限：@PreAuthorize("hasRole('TEACHER')")
状态机：无 → DRAFT
验收关键项：
  1. 返回 R<Long>，Long 为新建 proposal.id
  2. 创建记录：proposer_id=当前用户、status='DRAFT'、type='急需紧缺型'、其他字段 NULL
  3. proposal_signatures 自动插入 3 行固定签字（LEAD/DEPT/SCHOOL），opinion/signature/seal 均为 null
  4. 非 TEACHER 角色调用返回 403
  5. 同一教师多次调用各自创建独立记录
```

#### M1-02 · PUT /api/storage-applications/{id}

```
ID：M1-02
名称：全量保存
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
          micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（保存草稿按钮）
API 端点：PUT /api/storage-applications/{id}
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner + assertDraft
请求体：StorageApplicationSaveRequest（主表字段 + 4 List 子表数据）
验收关键项：
  1. 主表字段全部更新（包括富文本字段）
  2. 子表全量替换：DELETE WHERE proposal_id + 批量 INSERT
  3. updated_at 更新为当前时间
  4. 非 DRAFT 状态 → 400 MS_STATUS_INVALID
  5. 非本人 → 403 NO_PERMISSION
```

#### M1-03 · PATCH /api/storage-applications/{id}/auto-save

```
ID：M1-03
名称：自动保存（含心跳+限流）
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationAutoSaveService.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue
          （字段变更 1500ms 防抖 + 30s 心跳定时器）
API 端点：PATCH /api/storage-applications/{id}/auto-save
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner + assertDraft
限流：Redis key "autosave:rate:{userId}" TTL 1s
验收关键项：
  1. 请求体中非 null 字段执行 UPDATE（主表）+ 子表全量替换
  2. 更新 last_auto_saved_at = NOW()
  3. 返回 R<AutoSaveResult> 含 serverTime
  4. 同一用户 1s 内重复请求 → 429 AUTO_SAVE_RATE_LIMITED
  5. 120s 无心跳 → 返回 {"stale": true}；前端收到后 toast "编辑会话已过期，请刷新页面"
  6. UI 指示器三态："保存中..." / "已自动保存 HH:mm:ss"（绿色）/ "自动保存失败，点击重试"（红色可点击）
```

#### M1-04 · POST /api/storage-applications/{id}/reset-module

```
ID：M1-04
名称：重置指定模块
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（模块重置按钮）
API 端点：POST /api/storage-applications/{id}/reset-module
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner + assertDraft
请求体：{ "module": "COURSES" | "TEAM_MEMBERS" | "SIGNATURES" | "SHARED_UNITS" }
验收关键项：
  1. COURSES：DELETE FROM proposal_courses WHERE proposal_id = id
  2. TEAM_MEMBERS：DELETE FROM proposal_team_members WHERE proposal_id = id
  3. SIGNATURES：DELETE SHARED_UNIT 签字 + 重置 LEAD/DEPT/SCHOOL 为空
  4. SHARED_UNITS：DELETE shared_units + 关联签字
  5. 非 DRAFT 状态 → 400；非法 module 值 → 400 MODULE_RESET_INVALID
```

#### M1-05 · POST /api/storage-applications/{id}/reset-all

```
ID：M1-05
名称：全部重置
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（全部重置按钮）
API 端点：POST /api/storage-applications/{id}/reset-all
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner + assertDraft
验收关键项：
  1. 全部 4 张子表 DELETE WHERE proposal_id = id
  2. 重新初始化固定 3 行签字（LEAD/DEPT/SCHOOL）
  3. 主表字段保留不重置
  4. 非 DRAFT 状态 → 400
```

#### M1-06 · 富文本字数校验

```
ID：M1-06
名称：富文本字数统计（去 HTML 标签）
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
          （Jsoup.parse(html).text().length()）
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue
          （RichTextWithCounter 组件实时显示字数）
API 端点：内嵌于 PUT / submit 校验
验收关键项：
  1. 培养目标去 HTML 后 ≥20 字符 → 否则 REQUIRED_RICH_TEXT
  2. 建设背景与意义去 HTML 后 ≥50 字符 → 否则 MIN_RICH_TEXT_LENGTH
  3. 空 <p></p> 视为 0 字符 → 校验失败
  4. 前端 RichTextWithCounter 实时显示如 "128/5000"
```

#### M1-07 · 手机号格式校验

```
ID：M1-07
名称：手机号正则校验
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（el-input + rules）
API 端点：内嵌于 PUT / submit 校验
校验规则：^1[3-9]\d{9}$
验收关键项：
  1. 13812345678 → 通过
  2. 12345678901 → FORMAT_PHONE
  3. 14812345678 → FORMAT_PHONE
  4. 前端 blur 时实时校验 + 后端提交时二次校验
```

#### M1-08 · 建设日期年份校验

```
ID：M1-08
名称：建设周期年份范围校验
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（el-date-picker year mode）
API 端点：内嵌于 PUT / submit 校验
验收关键项：
  1. constructionStartYear ≥ 2024 且 ≤ Year.now() → 否则 RANGE_YEAR
  2. constructionEndYear ≥ constructionStartYear 且 ≤ Year.now()+10 → 否则 RANGE_YEAR
  3. 前端年份选择器限制可选范围（动态计算）
```

### 2.2 M2 · 申请表管理与提交

#### M2-01 · GET /api/storage-applications/my-drafts

```
ID：M2-01
名称：我的申请表列表
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
前端文件：micro-course-admin/src/views/teacher/MyProposals.vue
API 端点：GET /api/storage-applications/my-drafts?page=0&size=20&status=DRAFT
权限：@PreAuthorize("hasRole('TEACHER')")
响应：R<PageResult<ProposalListVO>>
验收关键项：
  1. 分页参数 page/size 生效（默认 0/20）
  2. 仅返回当前用户本人的 proposal
  3. 可选 status 过滤
  4. 按 updated_at DESC 排序
  5. ProposalListVO 含：id, title, universityFullName, status, type, createdAt, updatedAt, lastAutoSavedAt
  6. 空列表展示空态引导"创建申请表"
```

#### M2-02 · GET /api/storage-applications/{id}

```
ID：M2-02
名称：申请表详情（含全部子表）
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（编辑态初始化加载）
API 端点：GET /api/storage-applications/{id}
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner OR @PreAuthorize("hasRole('ACADEMIC')")
响应：R<StorageApplicationDetailVO>
验收关键项：
  1. 主表全部字段
  2. List<ProposalCourseVO> courses
  3. List<ProposalTeamMemberVO> teamMembers
  4. List<ProposalSignatureVO> signatures
  5. List<ProposalSharedUnitVO> sharedUnits
  6. departmentName（JOIN departments）+ proposerName（JOIN users）
  7. proposal 不存在 → 404 STORAGE_APPLICATION_NOT_FOUND
  8. TEACHER 非本人 → 403；ACADEMIC → 200
```

#### M2-03 · POST /api/storage-applications/{id}/submit

```
ID：M2-03
名称：提交审核
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
          micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（提交审核按钮）
API 端点：POST /api/storage-applications/{id}/submit
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner + assertDraft
状态机：DRAFT → PENDING_REVIEW
验收关键项：
  1. §8.2 全量 15 项校验全部通过才放行
  2. 校验失败 → 400 VALIDATION_FAILED(19001) + errors JSON 清单（含 field/rule/message）
  3. 校验通过 → status='PENDING_REVIEW', validation_passed=TRUE, submitted_at=NOW()
  4. 提交后锁定：后续 PUT/PATCH 均返回 400 MS_STATUS_INVALID
  5. 发送通知给 ACADEMIC："教师 {name} 提交了微专业申请表「{title}」"
  6. 非 DRAFT 状态提交 → 400
  7. 并发提交（2 标签页）：第一个成功，第二个 400
```

#### M2-04 · POST /api/micro-specialty-proposals/{id}/withdraw

```
ID：M2-04
名称：撤回申报（复用 Phase 14）
后端文件：micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyProposalController.java
前端文件：micro-course-admin/src/views/teacher/MyProposals.vue（撤回按钮）
API 端点：POST /api/micro-specialty-proposals/{id}/withdraw
权限：@PreAuthorize("hasRole('TEACHER')") + 本人校验
状态机：PENDING_REVIEW → WITHDRAWN
验收关键项：
  1. 仅 PENDING_REVIEW 状态可撤回
  2. 通知 ACADEMIC 申报已撤回
  3. 撤回后列表状态更新为 WITHDRAWN
  4. 撤回后不可再编辑提交（仅可删除）
```

#### M2-05 · 编辑态锁定（DRAFT 守卫）

```
ID：M2-05
名称：非 DRAFT 状态编辑拦截
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
          （assertDraft 方法）
验收关键项：
  1. PENDING_REVIEW 状态 PUT → 400 MS_STATUS_INVALID "申请表已提交审核，不可编辑"
  2. APPROVED 状态 PUT/PATCH → 400
  3. WITHDRAWN 状态 PUT/PATCH → 400
  4. REJECTED 状态 → 允许通过 resubmit 端点重新提交（不是直接 PUT）
```

#### M2-06 · 终态保护

```
ID：M2-06
名称：终态操作限制
前端文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue（条件渲染）
后端文件：StorageApplicationService.java assertDraft()
验收关键项：
  1. 非 DRAFT 状态前端隐藏编辑/保存按钮、显示"只读"提示
  2. WITHDRAWN 状态仅可删除，不可提交
  3. APPROVED 状态仅可查看 + 导出正式版
  4. 手动调 API → 后端拦截返回 400
```

### 2.3 M3 · 图片签名与公章

#### M3-01 · POST /api/storage-applications/{id}/upload-image

```
ID：M3-01
名称：签名/公章图片上传
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
前端文件：micro-course-admin/src/components/common/SignatureUploader.vue
API 端点：POST /api/storage-applications/{id}/upload-image
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner
请求：multipart/form-data（file + type: SIGNATURE|SEAL）
响应：R<ImageUploadResult> { url, thumbnail }
验收关键项：
  1. 通过魔数校验 + 大小校验 + 缩放处理
  2. 存储至 /data/storage-applications/{id}/images/{uuid}.{ext}
  3. 返回可访问的 URL + 缩略图 URL
  4. UUID 命名防止同名覆盖
```

#### M3-02 · 图片格式校验（魔数 + 后缀）

```
ID：M3-02
名称：图片格式魔数校验
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
前端文件：SignatureUploader.vue（accept="image/jpeg,image/png"）
验收关键项：
  1. JPEG 魔数 FF D8 FF E0~EF → 通过
  2. PNG 魔数 89 50 4E 47 → 通过
  3. GIF / BMP / WebP → 400 IMAGE_FORMAT_INVALID(19004)
  4. 空文件 → 400
```

#### M3-03 · 图片大小校验（≤2MB）

```
ID：M3-03
名称：图片大小限制
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
前端文件：SignatureUploader.vue（beforeUpload 钩子预检）
验收关键项：
  1. ≤ 2,097,152 bytes → 通过
  2. > 2MB → 413 IMAGE_TOO_LARGE(19005)
  3. 前端 beforeUpload 返回 false + toast "图片不能超过 2MB"
```

#### M3-04 · 图片缩放处理

```
ID：M3-04
名称：图片缩放至 150×150 @150dpi
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
          （BufferedImage + AffineTransform）
验收关键项：
  1. 保持宽高比缩放至 MAX(宽,高) ≤ 150px
  2. 输出 DPI=150
  3. 缩放后文件写入临时路径再保存至目标路径
  4. 原图丢弃（不保留原始尺寸）
```

### 2.4 M4 · 导出

#### M4-01 · GET /api/storage-applications/{id}/export-word

```
ID：M4-01
名称：Word 导出（Apache POI）
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationExportService.java
前端文件：micro-course-admin/src/views/teacher/StorageApplicationPreview.vue [下载 Word]
API 端点：GET /api/storage-applications/{id}/export-word
权限：@PreAuthorize("hasRole('TEACHER')") + assertOwner OR @PreAuthorize("hasRole('ACADEMIC')")
响应：Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
验收关键项：
  1. 导出前执行 §8.2 全量校验 → 失败返回 19003 + 错误清单
  2. XWPFDocument 生成 A4 文档含 5 模块正式排版
  3. 签名/公章图片通过 XWPFPicture 内嵌
  4. Content-Disposition: attachment; filename*=UTF-8''【{高校}】整理收纳微专业申请表_{yyyyMMdd}.docx
  5. 中文字体正确（宋体/黑体）
```

#### M4-02 · GET /api/storage-applications/{id}/export-pdf

```
ID：M4-02
名称：PDF 导出（OpenPDF）
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationExportService.java
前端文件：micro-course-admin/src/views/teacher/StorageApplicationPreview.vue [下载 PDF]
API 端点：GET /api/storage-applications/{id}/export-pdf
权限：同 export-word
响应：Content-Type: application/pdf
验收关键项：
  1. 导出前执行 §8.2 全量校验 → 失败返回 19003 + 错误清单
  2. 使用 OpenPDF com.lowagie.text.* 生成
  3. 中文字体嵌入（simsun.ttc 或 NotoSansCJK）
  4. PdfPTable 嵌套表格；Image.getInstance 嵌入图片
  5. 页眉页脚 + 页码 "第 X 页 / 共 Y 页"
  6. 生成过程不在事务内执行（避免长事务）
```

#### M4-03 · 导出前置校验

```
ID：M4-03
名称：导出前数据完整性校验
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
          （与提交校验共享 §8.2 规则集）
验收关键项：
  1. 导出 Word/PDF 前强制执行 §8.2 15 项校验
  2. 校验失败 → 不生成文件，返回 19003 + errors JSON + canExport=false
  3. DRAFT 状态导出降低校验力度（仅校验文件格式，不阻断导出）
  4. APPROVED 状态导出（正式版）→ 严格校验全部通过才可导出
```

#### M4-04 · 导出文件名规范

```
ID：M4-04
名称：导出文件名
后端文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationExportService.java
规范：Content-Disposition: attachment; filename*=UTF-8''【{高校全称}】整理收纳微专业申请表_{yyyyMMdd}.{ext}
验收关键项：
  1. 文件名含高校全称（proposal.university_full_name）
  2. 高校全称为空时使用 "未知高校"
  3. 日期格式 yyyyMMdd（当前日期）
  4. 特殊字符 URL 编码（RFC 5987）
```

### 2.5 M5 · 预览

#### M5-01 · GET /api/storage-applications/{id}/preview

```
ID：M5-01
名称：预览数据接口
后端文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
前端文件：micro-course-admin/src/views/teacher/StorageApplicationPreview.vue
API 端点：GET /api/storage-applications/{id}/preview
权限：TEACHER（本人） + ACADEMIC（全部） + ADMIN（全部）
响应：R<StorageApplicationDetailVO>
验收关键项：
  1. 返回数据与 GET /{id} 格式相同（可复用同一 Service 方法）
  2. 所有子表数据完整返回
  3. TEACHER 非本人 → 403
  4. ACADEMIC → 可预览任意已提交申请表
```

#### M5-02 · A4 打印预览页渲染

```
ID：M5-02
名称：StorageApplicationPreview.vue A4 渲染
前端文件：micro-course-admin/src/views/teacher/StorageApplicationPreview.vue（★ 新增）
验收关键项：
  1. A4 容器 CSS 模拟 210mm×297mm 白底
  2. 5 模块正式排版展示（非编辑态，表格/签字区只读）
  3. 富文本字段渲染为 HTML（v-html）
  4. 签名/公章图片通过 <img> 内嵌显示
  5. 页眉："高校开放共享'微专业'资源平台推荐表"
  6. 页脚："填报日期：yyyy-MM-dd  第 X 页 / 共 N 页"
  7. @media print 适配：隐藏导航/操作栏、A4 精确分页
  8. 顶部操作栏：[返回编辑] [下载 Word] [下载 PDF] [提交审核]
  9. ACADEMIC 模式下底部追加 [通过] [驳回] 按钮
```

### 2.6 M6 · 5 个公共组件

#### M6-01 · RichTextWithCounter

```
ID：M6-01
名称：富文本编辑器（含字数计数）
文件：micro-course-admin/src/components/common/RichTextWithCounter.vue
用途：模块① 培养目标/建设背景与意义/培养特色/质量保障措施/预期成果/补充说明
验收关键项：
  1. 集成富文本编辑器（Quill 或 TinyMCE）
  2. 底部实时显示"纯文本字数/最大字数"（如 "128/5000"）
  3. 字数超限时计数器变红 + 提交时校验不通过
  4. v-model 双向绑定（支持父组件读写）
  5. 空内容显示 placeholder
```

#### M6-02 · DynamicTableEditor

```
ID：M6-02
名称：动态表格编辑器
文件：micro-course-admin/src/components/common/DynamicTableEditor.vue
用途：模块② 课程体系 + 模块③ 教学团队 + 模块⑤ 共建共享单位
验收关键项：
  1. 通过 JSON columns 配置驱动列渲染（列名/字段/类型/宽度/校验规则）
  2. [添加一行] 按钮在表尾
  3. 每行 [删除] 按钮（至少保留 1 行时禁用删除）
  4. 空行提交时前端校验提示"请填写完整"
  5. v-model 绑定 List<RowData>
  6. 模块⑤ 额外支持拖拽排序（vuedraggable）
```

#### M6-03 · SignatureUploader

```
ID：M6-03
名称：签名/公章上传器
文件：micro-course-admin/src/components/common/SignatureUploader.vue
用途：模块④ 签字盖章（3 级 + 共享单位）
验收关键项：
  1. el-upload 封装，accept="image/jpeg,image/png"
  2. beforeUpload 校验：类型 + 大小 ≤2MB
  3. 上传中显示 loading + 进度条
  4. 上传成功显示缩略图预览（150×150）+ [重新上传] 按钮
  5. 上传失败 toast 错误信息 + [重试] 按钮
  6. 支持 SIGNATURE 和 SEAL 两种模式（公章模式显示公章边框样式）
```

#### M6-04 · DatePicker

```
ID：M6-04
名称：年份选择器
文件：micro-course-admin/src/components/common/DatePicker.vue
用途：模块① 建设周期 + 模块④ 签字日期
验收关键项：
  1. el-date-picker type="year" 封装
  2. 年份范围校验：起始年 ≥2024 ≤当前年；结束年 ≥起始年
  3. 实时校验反馈（红色边框 + 错误提示）
  4. 支持清空按钮
  5. v-model 绑定
```

#### M6-05 · SignatureBlock

```
ID：M6-05
名称：签字区块组件
文件：micro-course-admin/src/components/common/SignatureBlock.vue
用途：模块④ 签字盖章（单个签字行的布局）
验收关键项：
  1. 布局：意见 textarea + 签字 SignatureUploader + 公章 SignatureUploader(仅 DEPT/SCHOOL/SHARED_UNIT) + 日期 DatePicker
  2. LEAD 行：仅意见 + 签字图 + 日期（无公章）
  3. DEPT/SCHOOL 行：固定不可删除，显示标签
  4. SHARED_UNIT 行：可删除（关联模块⑤单位名称）
  5. 已填/未填视觉区分（绿色边框 vs 灰色虚线框）
```

### 2.7 M7 · MicroSpecialtyProposal.vue 重做

#### M7-01 · 模块① 表头/基本情况

```
ID：M7-01
名称：模块① 渲染/编辑/校验
文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue
验收关键项：
  1. 高校全称 el-input maxlength=200
  2. 微专业名称 el-input maxlength=200
  3. 微专业类型 el-input disabled 显示"急需紧缺型"
  4. 开课学院 el-select 远程加载院系列表（GET /api/departments）
  5. 联系人姓名 el-input + 电话 el-input（blur 正则校验）+ 邮箱 el-input（blur 格式校验）
  6. 建设周期 2 个 DatePicker year mode（起始/结束）
  7. 总学分/总学时/招生规模 el-input-number（min=0）
  8. 面向对象 el-input type=textarea
  9. 培养目标/建设背景/培养特色/质量保障/预期成果/补充说明 → RichTextWithCounter
  10. 所有字段变更触发 auto-save 防抖
```

#### M7-02 · 模块② 课程体系

```
ID：M7-02
名称：模块② 渲染/编辑/校验
文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue
验收关键项：
  1. DynamicTableEditor 列配置：模块名称/课程名称(必填)/学时/学分/开课学期
  2. 添加行 + 删除行（至少保留 1 行）
  3. 课程名称必填校验（前端 + 后端双重）
  4. 学分支持小数（step=0.5）
  5. 数据变更触发 auto-save
```

#### M7-03 · 模块③ 教学团队

```
ID：M7-03
名称：模块③ 渲染/编辑/校验
文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue
验收关键项：
  1. DynamicTableEditor 列配置：序号(自动递增)/姓名(必填)/年龄/职称/单位/专业/曾授课程/拟授课程
  2. 添加行 + 删除行（至少保留 1 行，引导≥3 行）
  3. 姓名必填校验
  4. 序号自动分配（前端排序）
  5. 数据变更触发 auto-save
```

#### M7-04 · 模块④ 签字盖章

```
ID：M7-04
名称：模块④ 渲染/编辑/校验
文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue
验收关键项：
  1. 固定 3 行 SignatureBlock：LEAD(意见+签字+日期) / DEPT(意见+签字+公章+日期) / SCHOOL(意见+签字+公章+日期)
  2. 3 行固定不可删除
  3. SHARED_UNIT 动态行：关联模块⑤单位列表，行数=共建共享单位数量
  4. SignatureUploader 接入（图片上传到后端）
  5. 数据变更触发 auto-save
```

#### M7-05 · 模块⑤ 共建共享单位

```
ID：M7-05
名称：模块⑤ 渲染/编辑/校验
文件：micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue
验收关键项：
  1. DynamicTableEditor 列配置：排序(拖拽)/单位名称(必填)/单位类型(el-select)
  2. 单位类型下拉：共建高校/企业/共享高校 三选一
  3. 添加行 + 删除行 + 拖拽排序
  4. 新增/删除单位自动同步模块④ SHARED_UNIT 签字行
  5. 数据变更触发 auto-save
```

### 2.8 M8 · MyProposals.vue 增强

#### M8-01 · 列表操作列新增"预览"按钮

```
ID：M8-01
名称：我的申报列表 → 预览入口
文件：micro-course-admin/src/views/teacher/MyProposals.vue
验收关键项：
  1. 每条记录操作列增加 [预览] 按钮
  2. 点击 → router.push /teacher/micro-specialties/proposals/{id}/preview
  3. 按钮 loading 态（路由跳转中）
  4. 所有状态均可预览
```

#### M8-02 · 列表操作列新增"导出"按钮

```
ID：M8-02
名称：我的申报列表 → 导出入口
文件：micro-course-admin/src/views/teacher/MyProposals.vue
验收关键项：
  1. 每条记录操作列增加 [导出] 下拉（Word/PDF）或双按钮
  2. 点击 → window.open GET export-word / export-pdf
  3. 导出中按钮 loading 态
  4. 导出失败（400/校验失败）→ toast 错误信息含错误清单
  5. WITHDRAWN 状态不应显示导出按钮（仅预览）
```

### 2.9 M9 · StorageApplicationPreview.vue 新增

#### M9-01 · A4 纸渲染（只读正式排版）

```
ID：M9-01
名称：StorageApplicationPreview.vue 页面
文件：micro-course-admin/src/views/teacher/StorageApplicationPreview.vue（★ 新增）
路由：/teacher/micro-specialties/proposals/:id/preview
验收关键项：
  1. mounted 时调 GET /api/storage-applications/{id}/preview 获取数据
  2. Loading 骨架屏 → 数据加载完成后渲染
  3. 5 模块正式排版展示（表格样式与 Word 导出保持一致）
  4. 富文本字段 v-html 渲染（需 XSS 过滤）
  5. 签名/公章图片 <img> 内嵌显示
  6. 错误态：proposal 不存在 → 404 提示
  7. ACADEMIC 访问：底部显示 [通过] [驳回] 审批按钮
  8. TEACHER 访问：底部显示 [提交审核] 按钮（仅 DRAFT/REJECTED）
```

#### M9-02 · 打印样式

```
ID：M9-02
名称：@media print 适配
文件：micro-course-admin/src/views/teacher/StorageApplicationPreview.vue
验收关键项：
  1. @media print 隐藏顶部导航栏、侧边栏、操作按钮区
  2. A4 尺寸精确匹配（210mm×297mm）
  3. page-break-after: always 控制分页（模块之间合理分页）
  4. @page { margin: 2.54cm 3.17cm } 页边距
  5. 打印时显示页眉/页脚（通过 CSS 伪元素或固定定位）
  6. 背景色/图片正常打印（-webkit-print-color-adjust: exact）
```

### 2.10 M10 · MicroSpecialtyProposalReview.vue 增强

#### M10-01 · 详情查看嵌入

```
ID：M10-01
名称：审批列表 → 详情查看
文件：micro-course-admin/src/views/academic/MicroSpecialtyProposalReview.vue
验收关键项：
  1. 列表操作列 [查看详情] 按钮
  2. 点击 → el-drawer / el-dialog 展示完整申请表（复用 StorageApplicationPreview 只读模式）
  3. 抽屉/弹窗内提供 [下载 Word] [下载 PDF] 按钮
  4. 数据加载 loading 态
  5. 点击外部关闭 drawer/dialog
```

#### M10-02 · 审批操作（通过/驳回）

```
ID：M10-02
名称：审批通过/驳回 + 通知
文件：micro-course-admin/src/views/academic/MicroSpecialtyProposalReview.vue
验收关键项：
  1. 列表 [通过] 按钮 → 二次确认弹窗 → POST /api/micro-specialty-proposals/{id}/approve
  2. 列表 [驳回] 按钮 → 弹出输入框填写 review_comment → POST /api/micro-specialty-proposals/{id}/reject
  3. 详情抽屉底部同样提供 [通过] [驳回] 按钮
  4. 审批后列表状态即时更新（PENDING_REVIEW→APPROVED/REJECTED）
  5. 通知申报人（复用 Phase 14 通知）
  6. 操作 loading 态 + 成功/失败 toast
```

### 2.11 M11 · DB 迁移

#### M11-01 · 主表扩展 V120

```
ID：M11-01
名称：micro_specialty_proposals 新增 30+ 列
文件：micro-course-api/src/main/resources/db/migration/V120__phase15_proposal_fields.sql
验收关键项：
  1. ALTER TABLE ADD COLUMN 每列带 DEFAULT 或可为 NULL
  2. 新增列清单与 spec §6.1 一致
  3. 索引 idx_msp_type + idx_msp_status_type 创建
  4. mvn flyway:migrate 成功
  5. 旧 PENDING_REVIEW 记录新增字段为 NULL/默认值（不报错）
```

#### M11-02 · 子表创建 V121+V122

```
ID：M11-02
名称：proposal_courses + proposal_team_members
文件：micro-course-api/src/main/resources/db/migration/V121__phase15_proposal_courses.sql
     micro-course-api/src/main/resources/db/migration/V122__phase15_proposal_team_members.sql
验收关键项：
  1. proposal_courses 8 列 + 2 索引正确
  2. proposal_team_members 11 列 + 2 索引正确
  3. proposal_id BIGINT REFERENCES ... ON DELETE CASCADE
  4. 字段类型与 spec §6.2/§6.3 一致
```

#### M11-03 · 签字/单位表 V123+V124

```
ID：M11-03
名称：proposal_signatures + proposal_shared_units
文件：micro-course-api/src/main/resources/db/migration/V123__phase15_proposal_signatures.sql
     micro-course-api/src/main/resources/db/migration/V124__phase15_proposal_shared_units.sql
验收关键项：
  1. proposal_signatures 11 列（含 shared_unit_id 关联）+ 2 索引
  2. sign_level VARCHAR(20) NOT NULL
  3. proposal_shared_units 7 列 + 2 索引
  4. ON DELETE CASCADE 正确
  5. 字段类型与 spec §6.4/§6.5 一致
```

#### M11-04 · 状态机默认值 V125

```
ID：M11-04
名称：status 列 DEFAULT 'DRAFT'
文件：micro-course-api/src/main/resources/db/migration/V125__phase15_status_default.sql
验收关键项：
  1. ALTER TABLE ALTER COLUMN status SET DEFAULT 'DRAFT'
  2. 仅对新 INSERT 行生效
  3. 旧行 status 不受影响
  4. INSERT 新行不指定 status → 自动 DRAFT
```

### 2.12 M12 · 横切面

#### M12-01 · 错误码注册（8 个新增）

```
ID：M12-01
名称：Phase 15 错误码 19001-19008
文件：micro-course-api/src/main/java/com/microcourse/exception/ErrorCode.java
验收关键项：
  1. 19001 VALIDATION_FAILED（申请表校验未通过）
  2. 19002 STORAGE_APPLICATION_NOT_FOUND（申请表不存在）
  3. 19003 EXPORT_VALIDATION_FAILED（不可导出）
  4. 19004 IMAGE_FORMAT_INVALID（图片格式不支持）
  5. 19005 IMAGE_TOO_LARGE（图片过大）
  6. 19006 AUTO_SAVE_RATE_LIMITED（自动保存过频）
  7. 19007 EDIT_SESSION_EXPIRED（编辑会话过期）
  8. 19008 MODULE_RESET_INVALID（模块重置不支持）
  9. 8 个错误码编号无重复，httpStatus 正确映射
  10. 与 Phase 14 错误码范围（17001-17999）无冲突
```

#### M12-02 · 权限全覆盖

```
ID：M12-02
名称：@PreAuthorize + Service 二次校验
文件：micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java
     micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
验收关键项：
  1. 12 个端点全部 @PreAuthorize 注解
  2. assertOwner() 覆盖所有写操作（本人校验）
  3. assertDraft() 覆盖 PUT/PATCH/submit/reset（状态校验）
  4. ACADEMIC 可 GET 列表/详情/预览/导出，不可写
  5. ADMIN 只读全部
  6. 无 @PreAuthorize 遗漏端点（grep -L 验证）
```

#### M12-03 · 通知接线

```
ID：M12-03
名称：4 个通知事件正确触发
文件：micro-course-api/src/main/java/com/microcourse/service/StorageApplicationService.java
     + 复用 Phase 14 MicroSpecialtyProposalServiceImpl 通知逻辑
验收关键项：
  1. submit 成功 → 通知 ACADEMIC "教师 {name} 提交了申请表「{title}」"
  2. approve 成功 → 通知申报人 "申请表「{title}」已通过审核"
  3. reject 成功 → 通知申报人 "申请表「{title}」已驳回，原因：{comment}"
  4. withdraw 成功 → 通知 ACADEMIC "教师 {name} 撤回了申请表「{title}」"
  5. 通知不重复发送（幂等）
  6. 通知类型使用 Phase 14 已有 MS_PROPOSAL_* 或新增 MS_PROPOSAL_SUBMITTED
```

### 2.13 M13 · 端到端数据链

#### M13-01 · 教师端完整旅程

```
ID：M13-01
名称：教师申报全链路
涉及：M1-M9 全部功能点
验收关键项：
  1. 创建草稿 → 5 模块填写 → 自动保存 → PUT 全量保存 → 预览 → 导出草稿 → 提交审核
  2. 提交后查看状态变迁（列表页 PENDING_REVIEW）
  3. 审批结果查看（APPROVED/REJECTED 状态徽章 + 驳回原因）
  4. REJECTED → 重提 → 重新进入 PENDING_REVIEW
  5. APPROVED → 导出正式版 PDF
  6. 全程状态变化与 API 返回值一致
```

#### M13-02 · 教务处审批旅程

```
ID：M13-02
名称：教务处审批全链路
涉及：M10 + 复用 Phase 14 审批
验收关键项：
  1. 审批列表显示 PENDING_REVIEW 申请
  2. 详情查看完整申请表（5 模块数据）
  3. 审批通过 → 状态即时更新 + 通知教师
  4. 审批驳回（含原因）→ 教师可查看驳回原因
  5. 审批后数据不可编辑（锁定保护）
```

#### M13-03 · 跨状态数据一致性

```
ID：M13-03
名称：状态机全分支数据一致性
涉及：所有状态转换
验收关键项：
  1. DRAFT → sub-entity 数据可随意修改
  2. DRAFT → PENDING_REVIEW → 数据锁定（不可直接 PUT/PATCH）
  3. PENDING_REVIEW → APPROVED → 数据保持、可导出
  4. PENDING_REVIEW → REJECTED → 数据保持、可重提
  5. PENDING_REVIEW → WITHDRAWN → 数据保持但不可再提交
  6. 级联删除：主表删 → 4 子表自动删（ON DELETE CASCADE）
```

---

## 3. 交叉验证规范

### 3.1 R1-R5 5 维验证矩阵

| 维度 | 代号 | 触发时机 | 审查内容 | 报告字段 |
|------|------|---------|---------|---------|
| **R1 代码质量+契约** | audit-r1 | 每 5 个功能点批次末 | Lombok 残留 / @Autowired 字段注入 / 分页 5 字段 / Controller 返回 R\<T\> / ErrorCode 重复 / @PreAuthorize / 构造器注入 | file:line + 严重度 |
| **R2 DB 迁移** | audit-r2 | 含 DDL 变更的批次末 | Flyway V120-V125 vs 数据字典 §6 / 字段类型 / FK ON DELETE / 索引 / DEFAULT 值 | 逐表逐字段 diff |
| **R3 安全+配置** | audit-r3 | 涉及 Security/上传/导出的批次末 | @PreAuthorize 覆盖 / 图片魔数校验 / XSS 过滤（rich text）/ auto-save 限流 / JWT 密钥 / pom.xml CVE | file:line + CVE 编号 |
| **R4 跨域一致性** | audit-r4 | 每 5 个功能点批次末 | FK 链完整 / Entity 命名 vs SQL / Controller 路径规范 / Service 接口签名 vs 调用 | 一致性偏差列表 |
| **R5 前端 UI/UX** | audit-r5 | 每 5 个功能点批次末 | 拆为 3 子维度（见 §3.2） | 逐组件逐页面 |

### 3.2 R5 前端 3 子维度

| Sub | 代号 | 焦点 | 必查项 |
|-----|------|------|--------|
| **R5a 视觉与一致性** | audit-r5a | UI 组件规范、主题统一、三态齐全 | Element Plus 组件用法 / Loading/Empty/Error 三态覆盖 / 响应式断点 / a11y 基础（label/aria） / 颜色/字体/间距一致性 |
| **R5b 页面与功能交互** | audit-r5b | 路由、表单、按钮、用户反馈 | 路由守卫（TEACHER/ACADEMIC 角色隔离）/ 表单提交 loading + toast 反馈 / 按钮禁用逻辑 / token 持久化 / 错误 toast 覆盖 |
| **R5c 数据交互** | audit-r5c | Pinia / API / 缓存 / 分页 | Store action/getter 一致性 / API 调用时机（mounted/onActivated）/ localStorage/sessionStorage 一致性 / 分页参数 page/size 正确传递 / API 错误统一拦截处理 |

### 3.3 交叉验证触发规则

```
每 5 个功能点完成 → 自动启动 R1-R5 并行审查
  ├── R1 reviewer: 代码质量（后端）
  ├── R2 reviewer: DB 迁移 vs 数据字典
  ├── R3 reviewer: 安全+配置
  ├── R4 reviewer: 跨域一致性
  ├── R5a reviewer: 前端视觉与一致性
  ├── R5b reviewer: 前端页面与功能交互
  └── R5c reviewer: 前端数据交互
```

**铁律**：
- 7 个 reviewer **必须并行**启动，不能串行
- 任一 FAIL → 立即修复 → 重新审查
- 全部 PASS → 才能进入下一批次
- 每条审查结果必须标注 `P0 / P1-C / P1-I / P2`，不标注的审查报告视为无效

### 3.4 4 类子 Agent 角色定义

| 角色 | 代号 | 工具权限 | 输入 | 输出 | 关键边界 |
|------|------|---------|------|------|---------|
| **审查员** | `auditor` | read + grep + glob | 1 个功能点 JSON 任务书 | 1 份 JSON 审计报告 | ❌ 禁止 Edit/Write |
| **根因调查员** | `investigator` | read + grep + glob + bash(测试) | 1 个功能点 JSON 任务书（含 auditor 报告） | 1 份 JSON 根因报告 + 1 个可重放脚本 | ❌ 禁止 Edit/Write |
| **修复员** | `fixer` | read + grep + glob + Edit + Write + bash(测试) | 1 个功能点 JSON 任务书（含前两份） | 1 份 JSON 修复报告 + diff + 验证证据 | ✅ 允许改 spec 约定文件 |
| **R1-R5 审查员** | `reviewer` | read + grep + glob | 批次 diff + spec 对应章节 | 批次审查报告 | ❌ 禁止 Edit/Write |

### 3.5 JSON 任务书 Schema（主 → 子）

```json
{
  "ticket_id": "M1-03",
  "round": 1,
  "module": "M1",
  "title": "自动保存（PATCH auto-save）",
  "agent_role": "auditor | investigator | fixer",
  "context": {
    "spec_section": "phase15 spec §7.2-5 PATCH auto-save",
    "related_files": [
      "micro-course-api/src/main/java/com/microcourse/controller/StorageApplicationController.java",
      "micro-course-api/src/main/java/com/microcourse/service/StorageApplicationAutoSaveService.java",
      "micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue"
    ]
  },
  "allowed_files": [
    "micro-course-api/src/main/java/com/microcourse/service/StorageApplicationAutoSaveService.java",
    "micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue"
  ],
  "forbidden_files": [
    "**/test/**",
    "docs/数据字典.md",
    "pom.xml",
    "package.json"
  ],
  "deliverables": [
    "JSON 报告（按 §4 schema）",
    "（fixer 角色）修改的代码 diff",
    "（fixer 角色）验证证据（curl 输出 / 截图 / 测试通过日志）"
  ],
  "acceptance": {
    "must_pass": [
      "PATCH auto-save 仅更新非 null 字段",
      "同一用户 1s 内重复请求 → 429 AUTO_SAVE_RATE_LIMITED",
      "120s 无心跳 → 返回 stale=true",
      "前端 UI 显示'已自动保存 HH:mm:ss'（绿色）"
    ],
    "edge_cases": [
      "网络断开时前端显示'自动保存失败，点击重试'",
      "DRAFT 状态外 PATCH → 400 MS_STATUS_INVALID",
      "非本人 → 403 NO_PERMISSION"
    ]
  },
  "steps": [
    "step 1: 读 spec 对应章节 phase15 spec §7.2-5 + §8.1",
    "step 2: 读后端 Controller + Service 关键代码",
    "step 3: 读前端自动保存 + 心跳逻辑代码",
    "step 4: 列出发现的问题（按角色）"
  ],
  "budget": {
    "max_files_to_read": 8,
    "max_tokens_estimate": 30000,
    "max_runtime_minutes": 8
  }
}
```

### 3.6 JSON 结果报告 Schema（子 → 主）

#### 审查员报告

```json
{
  "ticket_id": "M1-03",
  "agent_role": "auditor",
  "verdict": "PASS | FAIL",
  "findings": [
    {
      "id": "ISSUE-001",
      "severity": "P0 | P1 | P2 | P3",
      "category": "BUG | UX | UI | DATA | PERMISSION | STATE | NOTIFY | DOC",
      "file_path": "micro-course-api/.../StorageApplicationAutoSaveService.java",
      "line_range": "45-52",
      "description": "限流 Key 不含 proposalId，不同申请表共享限流窗口",
      "evidence": "Redis key 仅 autosave:rate:{userId}，多申请表并发保存会被误限流",
      "fix_suggestion": "key 改为 autosave:rate:{userId}:{proposalId}"
    }
  ],
  "files_inspected": ["...", "..."],
  "step_outputs": {
    "step_1": "spec §7.2-5 要求限流 1 次/秒 ... ",
    "step_2": "Service L45 ... ",
    "step_3": "前端 L312 ... ",
    "step_4": "发现 2 个问题 ..."
  },
  "discrepancies": [
    "spec 要求限流 Key 含 proposalId，但代码未实现"
  ],
  "context_used_tokens": 28000
}
```

#### 修复员报告

```json
{
  "ticket_id": "M1-03",
  "agent_role": "fixer",
  "verdict": "FIXED | PARTIAL | BLOCKED",
  "diff_summary": {
    "files_modified": [
      {"path": "...", "lines_added": 5, "lines_removed": 2, "lines_modified": 3}
    ],
    "total_loc_change": 10
  },
  "verification": {
    "type": "manual_curl",
    "command": "curl -X PATCH http://localhost:8080/api/storage-applications/1/auto-save ...",
    "result": "PASS",
    "output_excerpt": "HTTP 200 {\"code\":200,\"data\":{\"serverTime\":...,\"status\":\"ok\"}}"
  },
  "linked_issues_fixed": ["ISSUE-001"],
  "doc_updates": {
    "data_dict_updated": false,
    "permission_matrix_updated": false,
    "function_list_updated": true
  },
  "context_used_tokens": 35000
}
```

---

## 4. 门禁

### 4.1 缺陷分级标准

| 级别 | 定义 | Phase 15 示例 |
|------|------|-------------|
| **P0** | 数据安全 / 核心功能不可用 / 客户首次操作必现错误 | 提交后数据丢失、DRAFT 状态编辑被错误拦截、Word 导出中文乱码、导出文件名编码错误 |
| **P1-C** | **客户可感知**的不一致、错误消息、体验降级 | 自动保存失败无 toast 提示、手机号格式校验前端报错后端未报错、预览页签名图片不显示、富文本字数统计不一致 |
| **P1-I** | **内部仅见**的代码/文档问题，客户在正常使用中不可感知 | 未使用的 import、注释不一致、日志级别不当、数据字典未同步 |
| **P2** | 代码整洁、安全加固建议、性能优化 | magic number 替换为常量、大文件拆分建议、缓存策略优化 |

### 4.2 门禁规则（硬）

| 门禁层级 | 规则 | 阻断条件 |
|---------|------|---------|
| **单功能点** | 修复员 verifier 全部 PASS + 0 P0 + 0 P1-C | 任一 P0 或 P1-C 存在 = 该功能点 FAIL |
| **单批次** | 5 个功能点全 PASS + R1-R5 共 7 个 reviewer 全 PASS | 任一 reviewer FAIL = 批次 FAIL |
| **全阶段** | 50/50 PASS + R1-R5 最终全 PASS + mvn test + npm run build 全绿 | 任一项不通过 |
| **发布** | P0+P1-C 清零 + P1-I 登记 deferred-items.md + mvn compile + npm build 全绿 | P0/P1-C 残留 = 阻塞发布 |

### 4.3 发布决策 5 步流程

```
Step 1: 收集 R1-R5c 全部审查报告
Step 2: 逐条过筛，标记每条为 P0 / P1-C / P1-I / P2
Step 3: P0 + P1-C → 全部修复 → 重新审查 → 进入下一步
Step 4: P1-I → 总工程师逐条评估 → 登记到 docs/deferred-items.md → 签字放行
Step 5: commit message 必须标注:
        "Phase 15 审计通过 | P0+P1-C 已清零 | P1-I 登记 deferred-items.md"
```

### 4.4 铁律

- ❌ **禁止**：将 P1-C 误判为 P1-I 而放行（审查 → 总工程师逐条确认）
- ❌ **禁止**：批量放行 P1-I 而不逐个评估（每一条都要写清为什么可以 defer）
- ❌ **禁止**：deferred-items.md 中的条目超过 1 个版本不处理
- ✅ **每次发布前，deferred-items.md 必须是空的或仅有当前版本新增的条目**
- ✅ **每条审查结果必须标注 P0/P1-C/P1-I/P2，不标注视为无效**

---

*spec 版本：v1.0*
*签发日期：2026-06-30*
*上游基线：phase15-storage-application-spec.md v1.0*
*总工程师：微课平台项目总负责*
