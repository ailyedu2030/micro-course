# 审查报告 — R4 跨域一致性审查

## 审查范围
- **审查维度**: 跨域一致性（Entity ↔ SQL / Controller ↔ API / 前后端字段 / 状态码 / FK 关系链）
- **分支**: `fix/red-team-audit-full-fix`（当前 HEAD: main）
- **审查时间**: 2026-07-06
- **审查人**: Reviewer Agent R4

## 审查依据
- `.claude/skills/microcourse/references/data-contract.md`
- `.claude/skills/microcourse/references/api-contract.md`
- `.claude/skills/microcourse/references/business-logic.md`
- `.claude/skills/microcourse/references/permission-matrix.md`
- `.claude/skills/microcourse/references/structure-constitution.md`
- 项目现有 Entity/Controller/Service/Vue 文件

---

## 逐项审查结果

### ✅ R4.1 — Entity 字段命名与 SQL 列命名一致

**状态: PASS**

项目严格遵守 Java camelCase（Entity 字段）→ SQL snake_case（DB 列）的映射，通过 `@TableField` 或 MyBatis-Plus 默认驼峰转下划线规则实现一致转换。

抽查确认:
- `User.java` → `users` 表: `userId` ↔ `user_id`, `realName` ↔ `real_name`, `departmentId` ↔ `department_id` ✅
- `AdminSetting.java` → `admin_settings` 表: `settingKey` ↔ `setting_key`, `settingValue` ↔ `setting_value`, `valueType` ↔ `value_type` ✅
- `Video.java` → `videos` 表: `courseId` ↔ `course_id`, `sortOrder` ↔ `sort_order` ✅

**无发现问题。**

---

### ✅ R4.2 — Controller 路径规范

**状态: PASS**

所有 Controller 使用 `/api/{resource}` 格式，无版本号（不存在 `/api/v1/` 等路径）。抽查:
- `AdminSettingsController.java` → `@RequestMapping("/api/admin/settings")` ✅
- `SlideController.java` → `@RequestMapping("/api/courses/{courseId}/slides")` ✅
- `SystemConfigController.java` → `@RequestMapping("/api/system-configs")` ✅
- `VideoController.java` → `@RequestMapping("/api/videos")` ✅

**无发现问题。**

---

### ✅ R4.3 — Service 接口与 Controller 调用签名匹配

**状态: PASS**

Controller 正确委托给 Service 接口，签名匹配。抽查:
- `AdminSettingsController.getAll()` → `adminSettingService.getAll()` — 返回 `List<AdminSettingVO>` ✅
- `AdminSettingsController.updateBatch()` → `adminSettingService.updateBatch(settings)` — 接受 `List<SettingUpdateRequest>` ✅
- `AdminSettingsController.updateUploadLimit()` → `adminSettingService.upsert("max_video_size_mb", ...)` ✅

**无发现问题。**

---

### ❌ R4.4 — 前端 API 调用路径与后端 Controller 路径一致

**状态: FAIL (P1-I)**

前端 `AdminSettings.vue` 通过 `import { getSettings, updateSettings } from '@/api/admin-settings'` 调后端，`admin-settings.js` 中 URL 为 `/admin/settings`（由 `request.js` 自动补 `/api` 前缀）。

**无差异问题。** 但存在一个潜在风险:

| # | 问题 | 级别 | 文件:行号 |
|---|------|------|----------|
| 1 | **前端直接调用 `PUT /api/admin/settings` 批量保存，但后端 `VideoServiceImpl.getMaxFileSize()` 仅读取 `max_video_size_mb` 键。若 DB 中仅有旧 `maxUploadSize` 记录、`max_video_size_mb` 尚未创建（首次部署），前端 Load 时的兼容映射仅修改表单显示值，但用户必须手动保存一次才能在后端创建 `max_video_size_mb` 记录。此场景下视频上传限制默认 2GB 而非前端的 100MB。** | P1-I | `AdminSettings.vue:438-440` / `VideoServiceImpl.java:105` |

---

### ✅ R4.5 — 前端字段名与后端 VO/Response 字段名一致

**状态: PASS**

- `AdminSettings.vue` 使用 `systemForm.max_video_size_mb` (L347) ↔ 后端 `AdminSettingVO.settingKey` 含 `max_video_size_mb` ✅
- 前端批次保存传 `settingKey: 'max_video_size_mb'` ↔ 后端 `SettingUpdateRequest.settingKey` 经 `@JsonProperty("settingKey")` 映射 ✅
- `SlideController.verifyAccess()` 状态码使用 `ErrorCode.NO_PERMISSION` 与前端错误处理一致 ✅

**无发现问题。**

---

### ✅ R4.6 — 前后端状态码值一致

**状态: PASS**

| 枚举 | 前端 | 后端 |
|------|------|------|
| `UserStatus.ACTIVE=1` | `enums.js` → `{ ACTIVE:1, DISABLED:2, INACTIVE:0 }` | `UserStatus.ACTIVE=1` |
| `EnrollmentStatus` | `enums.js` → `'ENROLLED','APPROVED','COMPLETED','CANCELLED'` | `EnrollmentStatus.getValue()` 一致 |
| `UserRole` | `enums.js` → `'STUDENT','TEACHER','ADMIN','ACADEMIC'` | `UserRole.name()` 一致 |

抽查确认:
- `SlideController.verifyAccess()` (L191) 用硬编码字符串 `"APPROVED"`、`"COMPLETED"` — 与 `EnrollmentStatus` 枚举值一致 ✅
- `TtsServiceImpl.verifyAccess()` (L430) 用 `EnrollmentStatus.CANCELLED.getValue()` — 通过枚举引用 ✅

**无发现问题。**

---

### ⚠️ R4.7 — FK 关系链完整性

**状态: PASS**（本次审查范围内未发现 FK 断裂）

本次审查重点关注 `max_video_size_mb` 相关的数据流完整性:
- 前端保存: `AdminSettings.vue` → `PUT /api/admin/settings` → `AdminSettingService.upsert("max_video_size_mb", value)`
- 后端读取: `VideoServiceImpl.getMaxFileSize()` → `adminSettingService.getByKey("max_video_size_mb")`
- DB 存储: `admin_settings` 表 upsert by `setting_key`

链路完整。旧 `maxUploadSize` 记录在前端的兼容映射（L438-440）存在加载顺序依赖风险（见 R4.4#1）。

---

## 特别关注项审查

### 🔴 R4-SP1: `maxUploadSize` → `max_video_size_mb` (OP-0205) 双向兼容

| 方向 | 现状 | 评估 |
|------|------|------|
| **前端 Load** | 在 `AdminSettings.vue:438-440` 加兼容映射: `maxUploadSize` → `systemForm.max_video_size_mb` | ✅ 已实现 |
| **前端 Save** | 发送 `settingKey: 'max_video_size_mb'` 到后端 | ✅ 正确 |
| **后端 Read** | `VideoServiceImpl.getMaxFileSize()` 仅读 `max_video_size_mb`，无 `maxUploadSize` fallback | ⚠️ 单向兼容 |
| **后端 Write** | `AdminSettingsController.upsert("max_video_size_mb", ...)` 写入 | ✅ 正确 |
| **DB Migration** | `V155` migration 同时列出 `maxUploadSize` 和 `max_video_size_mb` 为 NUMBER 类型 | ✅ 兼容 |

**结论**: 双向兼容存在缺口 — 后端读取没有 `maxUploadSize` fallback。若旧部署仅含 `maxUploadSize` 记录，首次部署后系统使用 2GB 默认值而非用户配置值。但此问题在用户首次保存表单后自动消除（前端会写入 `max_video_size_mb`）。

### ⚠️ R4-SP2: `verifyAccess()` 新增选课校验 (P1-I-07)

**前端**: 未发现前端 `_.verifyAccess()` 函数。P1-I-07 的选课校验在后端 `SlideController.verifyAccess()` 中实现。

**后端模式一致性对比**:

| 维度 | `SlideController.verifyAccess()` (L181-197) | `TtsServiceImpl.verifyAccess()` (L414-433) |
|------|--------------------------------------------|-------------------------------------------|
| 文件位置 | `controller/SlideController.java:181` | `plugin/interactive/service/impl/TtsServiceImpl.java:414` |
| 角色豁免 | ADMIN + ACADEMIC 跳过校验 | ADMIN + ACADEMIC 跳过校验 |
| 教师所有权 | 未检查 | 检查 `course.getTeacherId() == currentUserId` |
| 学生选课状态 | 仅 `APPROVED`、`COMPLETED` | `!= CANCELLED`（含 ENROLLED、APPROVED、COMPLETED、WAITLIST） |
| 错误码 | `ErrorCode.NO_PERMISSION` + "请先选课再查看课件" | `ErrorCode.NO_PERMISSION` |

**结论**: 两处 `verifyAccess()` 的**学生选课状态过滤逻辑不一致**。
- `SlideController`: 限定 APPROVED/COMPLETED — 不含 WAITLIST/ENROLLED 的学生
- `TtsServiceImpl`: 仅排除 CANCELLED — 含 ENROLLED/WAITLIST 的学生
- 且 `SlideController` 缺少教师所有权检查（教师可查看自己课程课件的场景）

### ✅ R4-SP3: `SecurityUtil.assertNotSelf` 5 处调用

| # | 文件 | 行号 | message | 评估 |
|---|------|------|---------|------|
| 1 | `MicroSpecialtyAdminServiceImpl.java` | 160 | "不能审批自己的微专业" | ✅ 正确 |
| 2 | `MicroSpecialtyAdminServiceImpl.java` | 195 | "不能驳回自己的微专业" | ✅ 正确 |
| 3 | `MicroSpecialtyProposalServiceImpl.java` | 169 | "不能审批自己的申报" | ✅ 正确 |
| 4 | `MicroSpecialtyProposalServiceImpl.java` | 414 | "不能审批自己的申报" | ✅ 正确 |
| 5 | `CoursePricingServiceImpl.java` | 120 | "不能审核自己的课程定价" | ✅ 正确 |

**所有 5 处调用均配置了 message 参数，错误码统一使用 `ErrorCode.CANNOT_APPROVE_SELF(9001)`，上下文消息清晰。PASS**

⚠️ 注意: `assertNotSelf()` 方法本身（`SecurityUtil.java:125-126`）在 `reviewerId == null || ownerId == null` 时静默跳过校验。`CoursePricingServiceImpl.java:120` 中 `course.getTeacherId()` 若为 null 则校验被跳过，但后续 `isAdminOrAcademic()` 检查（L122）提供防护层。

---

## 机械检查结果

| 检查项 | 状态 | 备注 |
|--------|------|------|
| 命名约定 | ✅ PASS | 遵循项目约定（camelCase Java, snake_case DB, kebab-case Vue） |
| 注释头完整性 | ✅ PASS | 关键方法有 P0/P1 标记注释 |
| 缩进/格式 | ✅ PASS | 一致 |
| 遗留调试代码 | ✅ PASS | 未发现未删除的 console.log/fmt.Println |

---

## 问题清单

### P0 — 阻塞项（必须修复）
无

### P1-C — 客户可感知（强烈建议修复）
无

### P1-I — 内部仅见（建议修复）

| # | 文件:行号 | 问题 | 修复建议 | 标签 |
|---|----------|------|---------|------|
| 1 | `AdminSettings.vue:438-440` + `VideoServiceImpl.java:105` | `maxUploadSize` → `max_video_size_mb` 兼容仅覆盖前端 Load 方向。后端 `getMaxFileSize()` 无 `maxUploadSize` fallback，若 DB 中仅有旧记录则默认使用 2GB | 在 `VideoServiceImpl.getMaxFileSize()` 中添加 `maxUploadSize` 作为第二 fallback key | OP-0205 |
| 2 | `SlideController.verifyAccess()` (L181-197) vs `TtsServiceImpl.verifyAccess()` (L414-433) | 两处 verifyAccess 的选课状态过滤逻辑不一致：Slide 用 APPROVED/COMPLETED，Tts 用 `!= CANCELLED`。Slide 缺少教师所有权检查 | 统一两处选课校验模式：建议使用 `!= CANCELLED`（含 WAITLIST/ENROLLED）且增加教师所有权检查 | P1-I-07 |
| 3 | `SlideController.java:181` | `verifyAccess()` 写在 Controller 内部而非 Service 层，与项目分层规范（Controller 不写业务逻辑）不完全一致。虽然合理（只需要 Course 和 Enrollment 查询），但可提取为公共 Service 方法 | 考虑提取为 `CourseAccessService.verifyAccess()` 或复用 `TtsService.verifyAccess()` | P1-I |
| 4 | `SecurityUtil.assertNotSelf()` (L125-126) | `reviewerId == null || ownerId == null` 时静默跳过校验。`CoursePricingServiceImpl:120` 的 `course.getTeacherId()` 若为 null 走此旁路 | 建议改为 `null` 时抛异常或至少记 `log.warn` | P1-I |

### P2 — 可优化项

| # | 文件:行号 | 问题 | 建议 |
|---|----------|------|------|
| 1 | `AdminSettings.vue:436-443` | `maxUploadSize` 与 `max_video_size_mb` 同时存在时，后遍历到的值覆盖前者，加载顺序决定最终值 | 按优先级处理：`max_video_size_mb` 优先，`maxUploadSize` 仅作为无记录时的 fallback |
| 2 | `AdminSettingsController.java:80` | `getMaxVideoSizeMb()` 可为 null 时使用三元 `?: 100`，建议使用 `Objects.requireNonNullElse` | `int maxVideoSizeMb = Objects.requireNonNullElse(request.getMaxVideoSizeMb(), 100);` |

---

## 无问题项
- R4.1 Entity ↔ 列命名: ✅
- R4.2 Controller 路径: ✅
- R4.3 Service ↔ Controller 签名: ✅
- R4.5 前后端字段名: ✅
- R4.6 前后端状态码: ✅
- R4.7 FK 关系链: ✅
- R4-SP3 assertNotSelf 5 处调用: ✅

---

## 决策

- [ ] 放行（无 P0 阻塞项，P1/P2 记录到 Phase 6 统一处理）
- [ ] 阻塞（存在 P0 项，需修复后重新审查）
- [x] **混合**（有 P1-I 内部一致性问题 + P2 建议项，P1-I 记录到 Phase 6 统一处理，无 P0 阻塞项 → **建议放行**）

**总体评价**: R4 跨域一致性审查未发现 P0/P1-C 级别问题。两处 `verifyAccess()` 内部逻辑不一致（P1-I-07）和 `maxUploadSize` 后端 fallback 缺失（OP-0205）为 P1-I 级别，不影响核心功能，建议记录到 Phase 6 统一处理。代码整体前后端一致性良好。
