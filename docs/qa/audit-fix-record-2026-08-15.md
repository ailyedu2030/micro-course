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

| # | 问题 | 位置 | 建议 |
|---|------|------|------|
| L1 | 3 个孤儿 Entity（GradeComponent/Attachment/UserFollow）| entity/ | 删除或归档 + 更新数据字典 |
| L2 | RejectRequest @Size(min=10) vs MicroSpecialtyRejectRequest 无 min | 2 个 DTO | 统一校验策略 |
| L3 | TtsWorkerService 孤儿 @Scheduled Worker | plugin/interactive/ | 确认 poll 是否有生产者 |
| L4 | AdminSettingsController 解密失败透传 ENC: 密文给前端 | controller/ | 前端对 ENC: 前缀兜底"配置异常" |

### P2（代码整洁/加固）

| # | 问题 | 位置 |
|---|------|------|
| P2-1 | ErrorCode 编号顺序混乱 | exception/ErrorCode.java |
| P2-2 | CourseController @Operation 缩进异常 | controller/ |
| P2-3 | @Valid 部分更新（null 放行）无专项测试 | 测试 | 

### ServiceImpl 超长拆分（Phase 6 专项，已登记）

| 文件 | 行数 | 拆分建议 |
|------|------|---------|
| SlideServiceImpl | 2042 | PPT/HTML/Flow 三个子服务 |
| TtsServiceImpl | 1225 | mmx 调用/TTS 状态机拆分 |
| VideoServiceImpl 等 18 个 | 600-900 | 查询/校验/写库/转换拆分 |

---

## 三、验证结果

| 验证 | 结果 |
|------|------|
| `mvn compile` | ✅ |
| `npm run lint` | ✅ |
| `npm run build` | ✅ |
| precheck.sh（.claude + .agents）| ✅ 26/26 |
| 关键测试套件（50 个）| ✅ 50/50 |
| 新增分页回归测试 | ✅ DiscussionCommentPagingRegressionTest |
| i18n keys 同步 | ✅ 3660 zh=en |

---

## 四、CI 门禁

- 分支推送到 GitHub 后等待 CI 5/5（backend/frontend/e2e/docker/monitoring-lint）
- auto-approve bot 通过后 squash-merge
- squash-merge 后删除分支

---

*记录生成：总工程师 · 2026-08-15*
*下次审查：Phase 6 专项（ServiceImpl 拆分 + 孤儿 Entity 清理）*
