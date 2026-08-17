# 2026-08-15 全栈审查修复记录

> 分支：`fix/audit-complete-2026-08-15`
> 基准：`origin/main` (411c8fef)
> 方法：全栈穷举扫描 → Round 1-3 系统性修复 → R1-R4 并行交叉审查 → 阻塞项修复

---

## 一、本次已修复（按 commit）

### Round 1 · P1-I 集中修复（commit `fix(audit-round1)`）

| # | 问题 | 修复 |
|---|------|------|
| 1 | 分页 size 上限 max=10000（24处 DoS 风险） | `ApiLimits.MAX_REQUEST_SIZE=10000`（@Range 契约）+ `MAX_PAGE_SIZE=100`（Service 硬限） |
| 2 | PageResult 返回类型不一致 | `DiscussionCommentController` 双契约（List 兼容 + PageResult 新）+ `pagePaged` |
| 3 | 8 处 @RequestBody 缺 @Valid | PptCoursewareController 4 端点 + MicroSpecialty* 3 端点补 @Valid + DTO 字段级校验 |
| 4 | 5 个零引用死代码 | 删除 AudioQueryService/AudioStorageService/GradeComponentRepository/AttachmentRepository/UserFollowRepository |
| 5 | 28 个路由 meta.title 硬编码中文 | `titleKey` 改造 + i18n 补全 + `document.title` 设置 + Layout 面包屑 |
| 6 | limit 无上限（getCourseRanking） | 补 `@Range(1, 100)` |
| 7 | 27 处前端 size=999/1000 | 收敛到 100 |
| 8 | precheck 无法识别 ApiLimits 常量 | 修复 check_pagination_size_contract |

### Round 2 · P2 系统性修复（commit `fix(audit-round2)`）

| # | 问题 | 修复 |
|---|------|------|
| 1 | magic number 散落 | `ApiConstants` 统一常量（时间/Cookie/采样率/LIMIT/-1L） |
| 2 | FieldEncryptor 静默 catch | 解密失败 warn 日志（保留容错契约） |
| 3 | 2 个前端死代码 | 删除 useUploadProgress.js/chapterTypeConfig.js |
| 4 | 学生端 67 处硬编码中文 | 5 页面 i18n 化 + 3660 keys 双向同步 |

### Round 3 · 重复代码抽取（commit `fix(audit-round3)`）

| # | 问题 | 修复 |
|---|------|------|
| 1 | GradeServiceImpl 4 处重复校验 | 抽取 assertCourseOwner/assertCourseOwnerByGrade |
| 2 | SlidePptPageDTO @NotNull 过严 | 改 @Positive（PATCH 兼容） |
| 3 | FieldEncryptor 完全静默 | 补 warn 日志 |

### Review 修复（commit `fix(audit-review)`）— R1-R4 阻塞项

| # | 严重度 | 问题 | 修复 |
|---|--------|------|------|
| 1 | **P0** | PptFlowDTO @NotNull 破坏 createFlow/updateFlow（sectionId path 填充/toPageId 课件结束/PATCH） | 移除 @NotNull，必填下沉 Service createFlow |
| 2 | **P1-C** | pagePaged 0-based page 直传 MP → 分页偏移 | `new Page<>(page+1, size)` + 回归测试 |
| 3 | **P1-C** | updatePage copyProperties 缺 pageNumber → null 覆盖 | 排除列表加 pageNumber |
| 4 | **P1-C** | PageSizeGuard 零调用 = DoS 双层防御未落地 | MyBatisPlusConfig setMaxLimit(100) 全局兜底 |
| 5 | **P1-C** | DiscussionComment LIMIT 100 截断第101+条 | 恢复 LIMIT 500 + 分页走 pagePaged |
| 6 | **P1-I** | CourseController 私有 MAX_PAGE_SIZE=1000 双标准 | 统一 ApiLimits.MAX_PAGE_SIZE |
| 7 | **P1-I** | 前端 10+ 处 size>100（1009/5000/200） | 导出/级联改 fetchAllPages |
| 8 | **P1-I** | createPage 无必填校验 → DB NOT NULL 抛 500 | Service 补 slideId/pageNumber 校验 |

---

## 二、已登记遗留项（非阻塞，后续迭代）

### P1-I（内部仅见）

| # | 问题 | 位置 | 状态 |
|---|------|------|------|
| L1 | 3 个孤儿 Entity（GradeComponent/Attachment/UserFollow）| entity/ | ✅ @Deprecated 归档（PR #246）|
| L2 | RejectRequest @Size(min=10) vs MicroSpecialtyRejectRequest 无 min | 2 个 DTO | ✅ 修复（PR #245）|
| L3 | TtsWorkerService 孤儿 @Scheduled Worker | plugin/interactive/ | ✅ 确认有生产者（PR #245）|
| L4 | AdminSettingsController 解密失败透传 ENC: 密文给前端 | controller/ | ✅ decryptSafe() 修复（PR #245）|

### P2（代码整洁/加固）

| # | 问题 | 位置 | 状态 |
|---|------|------|------|
| P2-1 | ErrorCode 业务码与 HTTP status 冲突（429/409/500）| exception/ErrorCode.java | ✅ 重构（PR #246）|
| P2-2 | CourseController @Operation 缩进异常 | controller/ | ✅ 确认无异常（PR #245）|
| P2-3 | @Valid 部分更新（null 放行）无专项测试 | 测试 | ⏳ 待处理 |

### ServiceImpl 超长拆分（Phase 6 专项，已登记）

| 文件 | 重构前行数 | 重构后行数 | 状态 |
|------|-----------|-----------|------|
| GradeServiceImpl | 789 | **619** + GradeVoBuilder.java (359) | ✅ PR #254 拆分 |
| MicroSpecialtyEnrollmentServiceImpl | 794 | **638** + MicroSpecialtyClassImportExecutor.java (371) | ✅ PR #254 拆分 |
| ExerciseRecordServiceImpl | 787 | **476** + ExerciseAnswerSubmitExecutor.java (583) | ✅ PR #254 拆分 |
| SlideServiceImpl | 2042→已拆分 | — | ✅ 历史已拆分（SectionSlideServiceImpl 等）|
| VideoServiceImpl | 803 | 803 | ⏳ precheck whitelist（pre-existing）|
| MicroSpecialtyQueryServiceImpl | 803 | 803 | ⏳ precheck whitelist（pre-existing）|
| AuthServiceImpl | — | 811 | ⏳ precheck whitelist（pre-existing）|

> 注：PR #244 precheck whitelisted 了 AuthServiceImpl / VideoServiceImpl / MicroSpecialtyQueryServiceImpl。  
> PR #254 把3 个新超 800 行的 ServiceImpl 全部拆分为 Executor / Builder 模式,3 个 ServiceImpl 文件本身已 < 800 行(precheck 通过)。

---

## 三、验证结果

| 验证 | 结果 |
|------|------|
| `mvn compile` | ✅ |
| `npm run lint` | ✅ |
| `npm run build` | ✅ |
| precheck.sh（.claude + .agents）| ✅ 26/26 |
| 关键测试套件（50 个）| ✅ 50/50 |
| verify 测试（全量）| ✅ 1310 tests 0 failures（PR #244）|
| 新增分页回归测试 | ✅ DiscussionCommentPagingRegressionTest |
| i18n keys 同步 | ✅ 3660 zh=en |

---

## 四、CI 门禁

- 分支推送到 GitHub 后等待 CI 5/5（backend/frontend/e2e/docker/monitoring-lint）
- auto-approve bot 通过后 squash-merge
- squash-merge 后删除分支

---

## 五、本次处理（2026-08-15 Phase 6 起，持续至 Phase 7）

| PR | 修复内容 | 状态 |
|----|---------|------|
| PR #244 | deep-audit P0×2 + P1-C×12 + P1-I×15 + P2×30 全量修复 + precheck whitelist bug | ✅ MERGED |
| PR #245 | L4 decryptSafe + L2 校验统一 + L3/TtsWorkerService 确认 + P2-2 确认 | ✅ MERGED |
| PR #246 | L1 @Deprecated 归档 + P2-1 ErrorCode 重构 | ✅ MERGED |
| PR #250 | ServiceImpl 质量治理：DiscussionPostServiceImpl copyToVO 去重 + MicroSpecialtyQueryServiceImpl copyToVO 去重（含所有字段 fallback 查库）+ toTeacherVO N+1消除 + P2-3 @Valid null 测试 | ✅ MERGED (2026-08-16 CI 7/7 PASS) |
| PR #251 | docs(audit): 补录 PR #250 合并状态 + e2e 根因分析 | ✅ MERGED |
| PR #252 | fix(VideoServiceImpl): separate video.upload-dir from video.storage-base-dir | ✅ MERGED |
| PR #253 | fix(i18n): 修复侧边栏二级菜单显示原始 i18n 键的问题（教师端/teacher/discussions 菜单显示 menu.teacherDashboard 等键名）+ Element Plus locale 同步 + 语言切换按钮可见化 | ✅ MERGED (2026-08-17 CI 9/9 PASS) |
| PR #254 | refactor(service): 拆分3 个超长方法到独立 executor 类（GradeServiceImpl 789→619 行, MicroSpecialtyEnrollmentServiceImpl 794→638 行, ExerciseRecordServiceImpl 787→476 行）。顺手修 classImport batch.clear() 漏写 bug。 | ✅ MERGED (2026-08-17 CI 9/9 PASS) |

### e2e 失败根因分析（PR #250）

**现象**：CI 5次连续 e2e FAIL（backend PASS 6/6），本地 smoke test 15/15 PASS。

**根因**：`MicroSpecialtyQueryServiceImpl.copyToVO` 单参数版本被消除后，
`toVO(ms)` 委托 `copyToVO(ms, vo, emptyMap, ...)` 传入空 map，
导致 `deptName/teacherName/creatorName/courseCount/pendingCount/totalEnrollments`
字段在空 map 情况下无回退查库，全部为 null/0，破坏 API 响应结构。

**修复**：为 `deptNameMap`、`teacherNameMap`、`creatorNameMap`、`courseCountMap`、
`pendingEnrollCountMap`、`totalEnrollmentsMap` 均补充 null fallback 查库逻辑，
保持与原单参数版本功能完全等价。

**教训**：消除重复代码时，单参数版本仅做委托不够，必须确保多参数版本
在任意 map 状态下功能等价，否则会引入隐蔽的 API 响应破坏。

### i18n 键名显示根因分析（PR #253）

**现象**：教师端 /teacher/discussions 侧边栏二级菜单显示 `menu.teacherDashboard` `menu.myCourses` 等键名字符串（非英文翻译）。用户误以为是"英文"。

**根因**：vue-i18n v9 + Element Plus slot 渲染上下文中，`$t()` 全局属性未正确解析, 返回原始键字符串而非翻译值。同时 Element Plus locale (硬编码 zhCn) 与 vue-i18n locale 不同步, 切换英文后分页/对话框仍显示中文。

**修复**：
- `Layout.vue` 一级/二级菜单 `$t()` → `t()` (useI18n 闭包) 
- `App.vue` 同步 Element Plus locale 到 vue-i18n locale
- `Layout.vue` 语言切换按钮改为可见 `中/EN` 指示 + toast
- `i18n/index.js` 防御性 localStorage 值校验
- 新增 `app.langSwitchToEn/Zh` 翻译键

**教训**：vue-i18n v9 Composition API 模式下，闭包 `t()` 比全局 `$t()` 在 slot 上下文中更可靠。语言切换按钮必须有视觉反馈, 不能是隐藏图标。

### PR #254 ServiceImpl 拆分策略

**挑战**：第一轮把超长方法拆分到 inline helper 后，3 个 ServiceImpl 体积仍超过 800 行（precheck FAIL）。

**解决**：第二轮把 inline helper 提取到独立 executor / builder 类，构造函数注入所有依赖：

| 原方法 | Service 行数 | 提取到 | 新类行数 |
|--------|-----------|--------|----------|
| GradeServiceImpl.batchConvertToVO (155行) | 619 | GradeVoBuilder | 359 |
| MicroSpecialtyEnrollmentServiceImpl.classImport (170行) | 638 | MicroSpecialtyClassImportExecutor | 371 |
| ExerciseRecordServiceImpl.submitAnswer (330行) | 476 | ExerciseAnswerSubmitExecutor | 583 |

**Bug 修复**：MicroSpecialtyEnrollmentServiceImpl.classImport 原代码漏 `batch.clear()`，导致 BATCH_SIZE flush 后重复插入。PR #254 修复。

**设计原则**：
- Executor 类构造函数注入所有依赖 → 可独立单元测试
- Record 上下文快照传递不可变数据 → 避免共享可变状态
- Functional interface (QuestionGrader) 解耦 executor 与具体批改实现

---

*记录生成：总工程师 · 2026-08-15*
*最后更新：2026-08-17 PR #254 merged (Phase 7 完成)*
*下次审查：Phase 8（CI backend 测试 hang 根因 + F10-D2 灰度分流实现）*
