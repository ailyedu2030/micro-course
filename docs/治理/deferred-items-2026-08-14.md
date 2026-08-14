# 深度审查缺陷登记报告

> 生成时间: 2026-08-14
> 审计范围: 153 Vue 文件 + 861 Java 文件 + 46 API 契约
> 4路并行 Agent: Vue深读 / 后端深读 / 跨领域 / 契约审计

---

## 一、本次已修复（PR #241，2026-08-14 squash-merged）

| # | 严重度 | 问题 | 修复 commit | 修复方式 |
|---|--------|------|-------------|---------|
| 1 | P1-C | `menu.*` i18n 键不可达（侧边栏全部显示原始键名） | de3e5e4f | 61个键从字面键重构为嵌套结构 |
| 2 | P1-C | plugins/interactive 17文件 327处硬编码中文零i18n | de3e5e4f | 全部接入useI18n |
| 3 | P1-C | MicroSpecialtyQueryService permitAll端点匿名401 | 7dbecaf0 | getCurrentUserId()→getCurrentUserIdOpt() |
| 4 | P1-C | searchChapters端点不存在（前端404） | d76332df | 新增GET /api/courses/chapters/search |
| 5 | P1-C | MAX_PAGE_SIZE=200静默截断size=9999 | 7e4ee1a9 | 上限扩大至1000 |
| 6 | P1-C | BannerList handleBeforeUpload未定义 | 8959132b | 新增图片校验函数 |
| 7 | P1-C | 点赞乐观更新失败不回滚 | 8959132b | liked改prop派生computed |
| 8 | P1-C | PptPageEditor/ScriptEditor/HtmlBlockEditor load()无try/catch | 8959132b | 全部补try/catch+错误提示 |
| 9 | P1-C | MicroSpecialtyReview.handleReopen静默吞掉API失败 | 8959132b | 改为ElMessage.error提示 |
| 10 | P1-C | SectionEditDialog裸await validate()无try/catch | 8959132b | 补try/catch |
| 11 | P1-C | /api/enrollments/my返回List非PageResult | c1332e38 | 改为R<PageResult>，前端适配 |
| 12 | P1-C | cart.js JSON.parse内层无try/catch | 9c8d54f8 | 补try/catch+非数组兜底 |
| 13 | P1-C | userList.confirmTitle缺失i18n键 | 9c8d54f8 | 补键 |
| 14 | P1-C | microSpecialtyReview.rejectReasonRequired缺失键 | 9c8d54f8 | 补键 |
| 15 | P1-C | router.push 171处无.catch（Vue Router 4 unhandled rejection） | 9c8d54f8 | main.js monkey-patch全局吞无害导航错误 |
| 16 | P1-C | Login.handleLogin无双击幂等守卫 | 9c8d54f8 | 补if(loading.value)return |
| 17 | P1-C | Login redirect开放重定向 | 9c8d54f8 | 补站内路径校验safeRedirect |
| 18 | P1-C | 测试适配getMyEnrollments返回格式变更 | 2d8a24db | StudentLearningFlowE2ETest 3处$.data→$.data.items |

**已修复汇总: P1-C × 17项（全部）**

---

## 二、待处理缺陷（未纳入PR #241）

### P1-C（4项）- 优先级：高

| # | 问题 | 定位文件 | 说明 |
|---|------|---------|------|
| C1 | **Grade分页N+1**：每行1次selectOne，100条+100查询 | `GradeController.java` | 应改为batch query或join |
| C2 | **Hermes Webhook mass assignment**：raw Entity作@RequestBody | `HermesHtmlPush.java`等 | 应使用DTO |
| C3 | **8处POST/PUT @RequestBody缺@Valid**：数据校验缺失 | 多个Controller | 需逐个补@Valid |
| C4 | **13处meta.title硬编码中文**：国际化不完整 | 多个.vue文件 | 需替换为i18n键 |

### P1-I（18项）- 优先级：中

| # | 问题 | 定位文件 | 说明 |
|---|------|---------|------|
| I1 | **12处分页size上限max=10000**：最大允许1万条/请求 | 多处@Range注解 | 应收紧至合理范围(200-500) |
| I2 | **9个零引用/不可达.vue文件**：死代码 | 路径待整理 | 应删除或确认无引用 |
| I3 | **Grade N+1查询**（同C1，但归类为I） | `GradeController` | 同上 |
| I4 | **Hermes Webhook mass assignment**（同C2） | `HermesHtmlPush` | 同上 |
| I5 | **8处@Valid缺失**（同C3） | Controller层 | 同上 |
| I6 | **PageResult格式不一致**：部分端点返回List部分返回PageResult | 多个Controller | 需统一 |
| I7 | **正则版xss.js可绕过**：建议换DOMPurify | `utils/xss.js` | 安全加固 |
| I8 | **9处localStorage.setItem无QuotaExceededError处理** | 多个.vue文件 | 需补try/catch |
| I9 | **SectionEditDialog.vue:84裸await validate()** | 已修复，见上 | — |
| I10 | **router.push无catch**（同C5） | 已修复，见上 | — |
| I11 | **localStorage配额超限未处理**（同I8） | 已修复cart.js | 需检查其余8处 |
| I12 | **xss绕过风险**（同I7） | `utils/xss.js` | 同上 |
| I13 | **2个i18n键缺失**（同C6） | 已修复，见上 | — |
| I14 | **Meta.title硬编码**（同C4） | 多个.vue | 同上 |
| I15 | **API契约漂移**：契约定义与实际实现不一致 | 待核对 | 需对照API契约文档 |
| I16 | **ServiceImpl超长**：AuthServiceImpl等超过800行 | 多个Service | 建议拆分 |
| I17 | **零引用代码路径**：未使用的private方法/字段 | 多个Java文件 | 建议清理 |
| I18 | **重复代码块**：多处copy-paste相似逻辑 | 多个文件 | 建议抽象复用 |

### P2（54项）- 优先级：低（代码整洁/安全加固）

- Vue文件内：重复样式、未使用组件prop、废弃注释等
- Java文件内：日志级别不当、异常捕获过宽、魔法数字等
- i18n：翻译不完整、键命名不规范等
- 安全：HTTPvsHTTPS混用、Cookie安全标志缺失等

---

## 三、根因分析（本次PR #241修复过程中发现）

### 根因类别统计

| 根因类别 | 数量 | 示例 |
|---------|------|------|
| **契约变更未同步** | 3 | getMyEnrollments返回格式变更，测试未同步 |
| **i18n架构缺陷** | 2 | menu.*键字面vs嵌套混用 |
| **API设计不一致** | 2 | enrollments/my返回List vs PageResult |
| **异常处理缺失** | 5 | load()/validate()/API调用缺try/catch |
| **前端状态管理缺陷** | 2 | 点赞乐观更新无回滚、双击无守卫 |
| **安全欠债** | 3 | 匿名401/mass assignment/开放重定向 |
| **并发/性能** | 2 | N+1查询、size上限过大 |

### 系统模式问题

1. **契约漂移**：契约文档与实现不同步，新增API无测试覆盖
2. **i18n迁移不完整**：新功能未接入i18n（interactive模块长期硬编码）
3. **测试覆盖率盲区**：Controller层改动未触发测试失败（MockMvc断言不够严格）
4. **安全设计遗漏**：公开端点内调用需认证工具方法

---

## 四、防止再发措施

### 短期（1周内）

- [ ] 创建 Tracking Issue：Grade N+1 查询优化
- [ ] 创建 Tracking Issue：Hermes Webhook DTO 改造
- [ ] 创建 Tracking Issue：@Valid 补全清单（8处）
- [ ] 更新 API 契约文档，对齐 getMyEnrollments 返回格式

### 中期（1个月内）

- [ ] 建立契约变更检查：Controller 返回类型变更需同步更新测试
- [ ] interactive 模块 i18n 审查流程：新增 .vue 文件必须包含 useI18n
- [ ] 建立页面 size 参数校验：前端 size>MAX 后端应返回明确错误而非截断
- [ ] 安全培训：@RequestBody 必须使用 DTO，禁止 raw Entity

### 长期（季度）

- [ ] 引入 ArchUnit 或 Spring MVC Test 契约测试
- [ ] 建立死代码扫描 CI gate
- [ ] i18n 完整性扫描升级为硬阻断（非 advisory）

---

## 五、验证命令

```bash
# 后端编译
cd micro-course-api && mvn package -B -q -DskipTests

# 前端构建
cd micro-course-admin && npm run build

# 预检
bash .claude/skills/microcourse/scripts/precheck.sh

# i18n 完整性
node scripts/check-i18n-keys.mjs
```

---

*报告生成: 4路并行深度审查 Agent (2026-08-14)*
*下次审查: PR #241 合并后 1 周内*
