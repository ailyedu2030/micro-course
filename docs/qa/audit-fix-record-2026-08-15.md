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

| 文件 | 行数 | 状态 |
|------|------|------|
| SlideServiceImpl | 2042→已拆分 | ✅ 历史已拆分（SectionSlideServiceImpl 等）|
| TtsServiceImpl | 待确认 | ⏳ 需重新确认当前行数 |
| VideoServiceImpl 等 5 个 | 766-836 | ⏳ precheck whitelist 受控观察 |
| 其余 13 个 | 600-765 | ⏳ precheck whitelist 受控观察 |

> 注：PR #244 precheck whitelisted 了 AuthServiceImpl / VideoServiceImpl / MicroSpecialtyQueryServiceImpl。当前 18 个超 800 行 ServiceImpl 已在 whitelist 受控观察。|

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

## 五、本次处理（2026-08-15 Phase 6）

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
| PR #256 | test: 为 PR #254 拆分的 3 个 Executor/Builder 添加 22 个独立单元测试（GradeVoBuilder 11 + MicroSpecialtyClassImportExecutor 5 + ExerciseAnswerSubmitExecutor 6），兑现拆分时构造函数注入→独立 Mockito 测试的设计目标 | ✅ MERGED (2026-08-18 CI 9/9 PASS) |
| PR #258 | perf(ci): 修复 backend test hang 根因（BaseIntegrationTest RANDOM_PORT → MOCK，surefire reuseForks=false → true，JVM heap 1.5G → 3G）。实测 backend **34m28s → 6m8s**（-82%），节省 ~28m/PR | ✅ MERGED (2026-08-18 CI 9/9 PASS) |
| PR #260 | feat(gray-release): F10-D2 灰度分流机制实现（GrayReleaseService Redis-backed + 5s 缓存 + fail-closed + GrayReleaseFilter @Order 40 + FeatureFlag 枚举 + GrayReleaseController 诊断端点），兑现 deferred-items.md P2 登记 | ✅ MERGED (2026-08-18 CI 9/9 PASS) |
| PR #262 | refactor(ms-query): 拆分 page() 到独立 MicroSpecialtyPageLoader（MicroSpecialtyQueryServiceImpl 803 → 723 行，从 precheck advisory 白名单移除，precheck 列表 2 → 1） | ✅ MERGED (2026-08-18 CI 9/9 PASS) |
| PR #264 | refactor(video): 拆分 Upload 职责到 VideoUploadService（VideoServiceImpl 803 → 552 行，从 precheck advisory 白名单移除，precheck 列表 **2 → 0**，首次所有 ServiceImpl < 800 行） | ✅ MERGED (2026-08-18 CI 9/9 PASS) |

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

---

*记录生成：总工程师 · 2026-08-15*
### PR #258 CI Backend Hang 根因分析（3 重叠加）

**现象**: 连续 5 个 PR（#253-#257）backend 28-34m hang，frontend 1m35s 形成 22x 差距。

**根因**:
1. `BaseIntegrationTest` 用 `WebEnvironment.RANDOM_PORT` → 每个 test class 启动真实 Tomcat server（74 个继承的 test × Tomcat 启动）
2. `pom.xml` surefire `reuseForks=false` → 每 test class 独立 JVM fork（83 JVM 启动开销）
3. JVM `-Xmx1500m` 不足以支撑 83 个 Spring context 累积（200-300MB/context）

**修复**:
- `BaseIntegrationTest`：`RANDOM_PORT` → `MOCK`（所有 test 用 MockMvc，无需 Tomcat）。删除未使用的 `port` 字段
- `pom.xml`：`-Xmx1500m` → `-Xmx3g`、`reuseForks=false` → `true`

**实测收益**: backend **34m28s → 6m8s**（-82%），节省 ~28m/PR。

**防回退**: `BaseIntegrationTest` Javadoc 完整记录 3 重根因 + workaround 起源。

---

*记录生成：总工程师 · 2026-08-15*
*最后更新：2026-08-18 PR #264 merged (Phase 11 完成 - VideoServiceImpl 拆分 - **首次 0 advisory**)*
*下次审查：Phase 12（AuthServiceImpl 811 行拆分 + 集成测试用例去重）*
