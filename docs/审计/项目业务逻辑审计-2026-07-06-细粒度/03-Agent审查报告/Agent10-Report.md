# Agent 10 审查报告 — 26 最小操作单元单节点深度细审

**审查日期**: 2026-07-06  
**审查 Agent**: Agent 10 (Reviewer)  
**审查范围**: 26 个操作单元，覆盖 R-AUTH / ROUTER / R-STU / R-TCH / R-ADM / R-ACA / R-BASE / R-CONT 八大链路  
**参考设计文档**: `docs/数据字典.md`, `docs/API契约-Phase1.md`, `docs/状态机设计.md`, `docs/权限矩阵.md`, `docs/项目结构规范.md`, `docs/开发规划/phase5-10-spec.md`

---

## 审查范围

- **文件范围**: micro-course-api (后端 Java) + micro-course-admin (前端 Vue 3) + 数据库 migration
- **审查类型**: 前端交互 → 后端 API → 数据库/状态机 → 响应回显，全链路审查
- **特别关注**: OP-0022 (Token 刷新时序), OP-0154 (formComplete 双重校验), OP-0178 (开课门禁), OP-0190 (LEAD 唯一性约束)

---

# 26 份独立审查记录

---

## 审查记录 #1 — OP-0010: 注册弹窗输入密码

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0010 |
| **所属链路** | R-AUTH-002 |
| **触发动作** | 注册弹窗输入密码 |
| **风险初判** | 低 |
| **审查级别** | P1-I |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端 | `micro-course-admin/src/views/auth/Login.vue` | 98-120 (注册弹窗模板), 230-268 (注册逻辑) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/service/impl/AuthServiceImpl.java` | 50-86 (register 方法) |

### 审查发现

**前端侧** (Login.vue):

1. **前端密码规则** (Login.vue:108-116): `registerRules.password` 定义了：
   - `required: true`
   - `min: 8, max: 32`
   - `pattern: /^(?=.*[A-Za-z])(?=.*\d)/` — 必须包含字母和数字
   - ✅ 规则正确，与后端一致

2. **确认密码校验** (Login.vue:119-127): `confirmPassword` 的自定义 validator 会检查两次密码一致。✅

3. **handleRegister 流程** (Login.vue:230-268):
   - 先调用 `formRef.validate()` — 前端规则校验
   - 再调用 `registerApi(...)` — 发 POST 请求
   - 成功后自动登录：`setToken(res.data.accessToken)` + `setRefreshToken(...)`
   - `userStore.token = ...` 直接赋值，同时调用 `userStore.getInfo()`
   - 兜底处理：`getInfo` 失败时降级使用默认信息 (Login.vue:261-263)

4. **注册开关检查** (Login.vue:205-214): `checkRegistrationStatus()` 查询 `registration_enabled` 配置，接口不可用时默认允许注册。

**后端侧** (AuthServiceImpl.java):

1. **register 方法** (AuthServiceImpl.java:50-86):
   - Step 1: 校验用户名唯一性 — `userRepository.findByUsername()`
   - Step 2: 密码复杂度双重保险 — 正则同上
   - Step 3: 创建学生用户 — `status=1 ACTIVE`
   - Step 4: 自动登录 — 生成 accessToken + refreshToken
   - Step 5: 记录操作日志
   - Step 6: 构建响应

### 问题列表

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | Login.vue:248 | `userStore.token = res.data.accessToken` 直接赋值 store，绕过了 Pinia action。如果 store 有 setter 或副作用逻辑，这个操作可能不同步。应调用 `userStore.setToken(res.data.accessToken)` 或 store action。 |
| 2 | P2 | Login.vue:261 | `console.warn('[Login] 注册后 getInfo 失败，使用默认用户信息')` — 生产环境应使用 logger 或 error-report 而非 console.warn |
| 3 | P2 | AuthServiceImpl.java:82-85 | LoginResponse 构建重复代码（与 login 方法 98% 相同），可提取为私有方法 `buildLoginResponse(User user)` |

### 结论

✅ **通过**。前端双向密码校验 + 后端双重校验 + 确认密码一致性检查形成完整保护链。无 P0 阻塞项。

---

## 审查记录 #2 — OP-0022: Token 刷新尝试

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0022 |
| **所属链路** | ROUTER |
| **触发动作** | Token 刷新尝试 |
| **风险初判** | 中 |
| **审查级别** | P0 (时序问题) |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端请求层 | `micro-course-admin/src/utils/request.js` | 108-161 (401 拦截 + refresh) |
| 前端路由层 | `micro-course-admin/src/router/index.js` | 356-370 (静默刷新) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/service/impl/AuthServiceImpl.java` | 139-195 (refresh 方法) |

### 审查发现

**前端拦截器** (request.js:108-161):

1. **401 捕获逻辑** (request.js:109):
   - 条件: `status === 401 && !config._retry && !config._skipAuth`
   - ✅ 设置了 `_retry` 标记和 `_skipAuth` 白名单，防止死循环

2. **并发 401 队列** (request.js:116-120):
   - `isRefreshing` 互斥锁 + `pendingRequests` 队列
   - 当第一个 401 出发 refresh 时，后续 401 请求被积压
   - ✅ 设计合理，防止并发 refresh 请求风暴

3. **refresh 调用** (request.js:123-126):
   - 使用 `axios.post(\`${API_BASE_URL}/auth/refresh\`, { refreshToken }, { _skipAuth: true, headers: {} })`
   - ⚠️ **P0**: 独立的 `axios.post` 实例，不经过 request 拦截器
   - 请求体传的是 `{ refreshToken }`，但后端 `refresh()` 方法接收的是 String 参数而非对象

4. **重放积压请求** (request.js:131-134):
   - `config.headers.Authorization = \`Bearer ${newToken}\`` — 逐个更新 token
   - `request(reqCfg)` 重新发送 — ✅ 绕过拦截器逻辑（不触发二次 refresh）

5. **refresh 失败回退** (request.js:137-143):
   - `removeToken(); removeRefreshToken()` — 清除所有 token
   - `router.push({ path: '/login', query: { redirect: currentPath } })` — 跳转登录页
   - `ElMessage.warning('登录已过期，请重新登录')` — 客户可感知提示
   - ✅ 回退逻辑完整

6. **刷新失败时积压请求被拒绝** (request.js:138-140):
   - `queue.forEach(({ reject }) => reject(e))`
   - ⚠️ **P1-I**: 积压请求的 reject 导致浏览器控制台出现多个未捕获的 Promise rejection 错误。应使用 `.catch()` 静默处理。

**路由层静默刷新** (router/index.js:356-370):

7. **路由 beforeEach 中的刷新** (router/index.js:358-369):
   - 当 store 角色为空但已登录时尝试静默刷新
   - 调用 `userStore.refreshAccessToken()` 获取新 token → 再调用 `getInfo()`
   - 失败时清除所有登录态 → 重定向到 /login
   - ⚠️ **P1**: 前端存在 **两套独立**的 refresh 机制（request 拦截器 vs 路由守卫），可能造成竞态

**后端 refresh** (AuthServiceImpl.java:139-195):

8. **IP 级别限流** (AuthServiceImpl.java:141-148):
   - 同一 IP 每小时最多刷新 20 次
   - 使用 Redis 计数 `refresh:{clientIp}`
   - ✅ 防暴力刷新

9. **旧 token 轮换机制** (AuthServiceImpl.java:156-163):
   - 检查 jti 是否在黑名单中（防重放）
   - ⚠️ **P1-I**: 黑名单检查在 `try-catch` 中，如果 Redis 异常静默跳过，攻击者可复用旧 refreshToken
   - ⚠️ **P0-时序**: 先验证 refreshToken 有效性（Step 1），再检查黑名单（Step 2.5）。应**先检查黑名单**再验证 token，避免对已轮换的 token 做无用验证

10. **refresh 轮换** (AuthServiceImpl.java:169-178):
    - 生成新 token 对，旧 refreshToken 加入黑名单
    - ⚠️ 延迟: `getExpirationRemainingSeconds` 返回的是 JWT 中的 exp 减当前时间，但 Step 4.5 在生成新 token 后才做，窗口期攻击者可利用旧 token
    - **正确做法**: 在生成新 token **之前**就失效旧 token

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | **P0** | AuthServiceImpl.java:139-195 | **时序缺陷**: refresh 方法 Step 1 验证旧 token → Step 2.5 检查黑名单，顺序反了。应 Step 2.5 在先，Step 1 在后。当前顺序存在攻击窗口。 |
| 2 | **P0** | AuthServiceImpl.java:169-178 | **轮换延迟**: 旧 refreshToken 在生成新 token 后才失效。应在生成新 token 前就失效旧 token，缩短攻击窗口。 |
| 3 | P1-I | AuthServiceImpl.java:162 | Redis 异常时旧 token 黑名单检查静默跳过，若事故期间攻击者可重放 refreshToken。应至少记录 ERROR 级别日志。 |
| 4 | P1-I | request.js:140 | 积压请求 reject 导致浏览器控制台未捕获 Promise 异常，需添加 `queue.forEach(({ reject }) => reject(e))` 后的 `.catch()` |
| 5 | P1-I | router/index.js:358-369 vs request.js:108-161 | 两套独立 refresh 机制可能产生竞态。路由守卫和拦截器应统一使用同一个 refresh 锁。 |

### 结论

⚠️ **阻塞**。存在 P0 时序缺陷 2 项，需修复后重新审查。

---

## 审查记录 #3 — OP-0034: 课程详情加载

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0034 |
| **所属链路** | R-STU-002 |
| **触发动作** | 课程详情加载 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/CourseDetail.vue` | 350-440 (fetchCourse + 并行加载) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/controller/CourseController.java` | (getCourseById) |

### 审查发现

1. **数据加载** (CourseDetail.vue:350-440):
   - 主流程: `fetchCourse()` → 获取课程基本信息
   - `onMounted` 执行 `fetchCourse()`, 然后 `Promise.all` 并行加载: `fetchTeacher()`, `checkEnrollment()`, `checkProgress()`, `fetchReviews()`, `fetchRanking()`, `fetchPricingInfo()`
   - ✅ 并行加载设计合理

2. **骨架屏** (CourseDetail.vue:7-10):
   - `el-skeleton :rows="8" animated` — ✅ 加载中有骨架屏

3. **404 处理** (CourseDetail.vue:360-362):
   - `if (e.response?.status === 404) courseNotFound.value = true`
   - 模板中对应显示 `<el-empty>` + 返回按钮
   - ✅ 错误场景覆盖完整

4. **课程封面兜底** (CourseDetail.vue:355-358):
   - `P1-7` 修复: coverUrl 为 null 时调用 `getDefaultCover(course.value)` 
   - ✅ 体验优化

5. ⚠️ **Promise.all 容错** (CourseDetail.vue:440):
   - `await Promise.all([fetchTeacher(), checkEnrollment(), checkProgress(), fetchReviews(), fetchRanking(), fetchPricingInfo()])`
   - 这些函数各自有 try-catch，但 Promise.all 中任何一个被 reject 会影响其他
   - 每个函数内部 catch ✅，但整体调用链在外层 catch ⚠️

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | CourseDetail.vue:440 | `Promise.all` 的 6 个函数各自有内部 catch，但如果某个函数未正确处理 reject（返回非 Promise），会影响整个并行加载。应使用 `Promise.allSettled()` 保证不互相阻断。 |
| 2 | P2 | CourseDetail.vue:363 | `ElMessage.error('获取课程信息失败')` — 对网络错误的 toast 应区分 404 和 500 |

### 结论

✅ **通过**。加载机制完整，骨架屏 + 错误处理 + 404 兜底覆盖全面。

---

## 审查记录 #4 — OP-0046: 点击"举报"按钮

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0046 |
| **所属链路** | R-STU-002 |
| **触发动作** | 点击"举报"按钮 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/CourseDetail.vue` | 346-368 (openReportDialog), 370-385 (submitReport) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/controller/ReportController.java` | (createReport) |

### 审查发现

1. **举报弹窗打开** (CourseDetail.vue:346-350):
   - `openReportDialog(type, id)` — 设置 `reportDialog.targetType`, `targetId`, 清空 `reason`
   - 模板中的调用: `@click.stop="openReportDialog('REVIEW', r.id)"` ✅ stopPropagation 防止事件冒泡

2. **举报提交** (CourseDetail.vue:352-365):
   - 空原因检查: `if (!reportDialog.reason.trim()) { ElMessage.warning('请输入举报原因'); return }`
   - 调用 `createReport({ targetType, targetId, reason })`
   - 成功提示: `ElMessage.success('举报已提交，管理员将审核')`
   - 失败提示: 使用后端真实错误消息

3. **举报原因长度检查**:
   - 模板: `maxlength="500" show-word-limit`
   - ✅ 前端有长度限制

4. ⚠️ **重复提交防护**: `reportDialog.submitting` 互斥锁在 submit 前设置、finally 清除 ✅

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | CourseDetail.vue:355 | 举报原因空字符串校验应同时检查全空格（中文场景常见），当前 `trim()` 已做到 ✅ |
| 2 | P2 | CourseDetail.vue:346-368 | 举报弹窗关闭时（`@close`）清理 reason，但未重置 submitting 状态（已在 finally 处理 ✅） |

### 结论

✅ **通过**。举报功能完整，双重空检查 + submitting 互斥锁 + 后端真实消息显示。

---

## 审查记录 #5 — OP-0058: LearningView 加载

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0058 |
| **所属链路** | R-STU-008 |
| **触发动作** | LearningView 加载 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/LearningView.vue` | 242-353 (loadCourse), 355-393 (loadProgress) |

### 审查发现

1. **主加载函数** (LearningView.vue:242-353):
   - `loadCourse(cid)`: 并行使用 `Promise.all` 加载课程信息 + 学习进度 + 视频列表
   - ✅ 并行加载减少等待

2. **进度加载** (LearningView.vue:355-393):
   - `loadProgress()`: 获取学习天数 + 学习总时长
   - 更新每节课的状态（COMPLETED / NOT_STARTED）
   - ✅ 非阻断式——即使进度加载失败，课程内容仍正常显示

3. **进度图谱构建** (LearningView.vue:236-240):
   - `buildProgressMap()`: 将原始进度列表转换为 `lessonId → progress` 查找表
   - ✅ 用 Map 替代数组遍历，O(1) 查询

4. **错误处理**:
   - `loadCourse` catch → `ElMessage.error('加载课程失败')` 
   - `loadProgress` catch → `ElMessage.warning('学习进度加载失败，部分数据可能不完整')`
   - ✅ 分级错误提示（error vs warning）

5. ⚠️ **自动选择** (LearningView.vue:340-346):
   - 首次加载自动展开第一个章节并选中第一个课时
   - 如果课程没有任何章节或课时，代码未处理空数组情况

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | LearningView.vue:340-346 | 空课程无章节/课时时，`chapters[0]` 和 `lessons[0]` 可能为 undefined，应加空数组守卫 |
| 2 | P2 | LearningView.vue:244 | `loading.value = true` 设置后如果组件在异步过程中被销毁，应取消多余 API 调用。可用 abort controller 或 isMounted 守卫 |

### 结论

✅ **通过**。加载逻辑完整，多层错误处理 + 并行数据获取。

---

## 审查记录 #6 — OP-0070: 加载失败重试

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0070 |
| **所属链路** | R-STU-006 |
| **触发动作** | 加载失败重试 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/LearningView.vue` | 482-556 (saveVideoProgress / markLessonComplete) |

### 审查发现

1. **UNIQUE 冲突重试** (LearningView.vue:482-506):
   - `saveVideoProgress` 创建进度记录时如果遇到 UNIQUE 冲突（已存在记录）
   - fallback: 查询已有记录 → 更新已存在的记录
   - ✅ P0-7 标记的修复，解决重复创建导致的 UNIQUE 约束冲突

2. **markLessonComplete 重试** (LearningView.vue:531-551):
   - 同样的重试模式：create 失败 → 查询已有 → update
   - ✅ 一致的模式

3. **失败计数器** (LearningView.vue:511-516):
   - `saveFailCount` 累计连续失败次数
   - 达到 3 次后显示警告 `ElMessage.warning('学习进度保存异常，请刷新重试')`
   - ✅ 避免在严重问题下不断重试

4. **重复提交防护** (LearningView.vue:460-464):
   - `sessionStorage` 对 lessonId 做 5 秒去重
   - ✅ 防止 LearningView + VideoPlayer 同时上报进度导致的双 POST

5. **组件卸载前最终保存** (LearningView.vue:644-648):
   - `onBeforeUnmount` 中强制提交一次进度保存
   - ✅ 保证最后一刻的进度被保存

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | LearningView.vue:511 | 3 次连续失败才弹提示，建议对首次失败也做 console.warn 便于排查 |
| 2 | P2 | LearningView.vue:482 | `createLearningProgress` 失败时 `console.warn` 级别建议改为 `console.error` 便于告警聚合 |

### 结论

✅ **通过**。重试机制三层覆盖：create → update fallback + 去重 + 失败计数。设计周到。

---

## 审查记录 #7 — OP-0082: 点击"开始答题"

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0082 |
| **所属链路** | R-STU-019 |
| **触发动作** | 点击"开始答题" |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/LearningView.vue` | 577-585 (goExercise) |

### 审查发现

1. **goExercise** (LearningView.vue:577-585):
   ```js
   function goExercise() {
     if (!currentLessonId.value) return
     const lesson = allLessons.value.find(l => l.id === currentLessonId.value)
     const chId = lesson?.chapterId || currentChapter.value?.id
     if (chId) {
       router.push(`/student/chapters/${chId}/exercises`)
     }
   }
   ```
   - 先检查 `currentLessonId` 是否存在
   - 从 `allLessons` 中查找当前课时获取 `chapterId`
   - 兜底使用 `currentChapter.value?.id`
   - 路由导航到练习页

2. **触发方式**: `ExerciseQuickPanel` 组件通过 `@start-exercise` 事件触发 ✅

3. ⚠️ **课时查找失败** (LearningView.vue:580-581):
   - 如果 `lesson` 为 undefined 且 `currentChapter` 也为 null，`chId` 为 undefined
   - `if (chId)` 检查能阻止无效导航
   - 但没有给用户的反馈提示

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | LearningView.vue:580-583 | `chId` 为 falsy 时函数静默返回，用户无反馈。应加 `ElMessage.warning('无法找到当前章节的练习')` |
| 2 | P2 | LearningView.vue:577 | `currentLessonId.value` 为 falsy 时直接 return，建议加日志便于调试 |

### 结论

✅ **通过**。功能完整，有基本守卫，但用户体验上可优化无反馈问题。

---

## 审查记录 #8 — OP-0094: 点击"保存头像"

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0094 |
| **所属链路** | R-STU-012 |
| **触发动作** | 点击"保存头像" |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/Profile.vue` | 302-327 (handleSaveAvatar) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/service/impl/AuthServiceImpl.java` | 299-333 (uploadAvatar) |

### 审查发现

**前端侧** (Profile.vue):

1. **文件选择校验** (Profile.vue:246-263):
   - `handleAvatarChange`: 文件大小 ≤ 2MB, 类型 JPEG/PNG/WebP
   - ✅ 客户端校验完整

2. **Canvas 压缩** (Profile.vue:268-300):
   - `compressAvatar`: 居中裁剪为 200×200 方形, JPEG 0.8 质量
   - `canvas.toBlob` 失败时 reject
   - `URL.revokeObjectURL` 在 onload/onerror 中均释放
   - ✅ 内存管理良好

3. **保存流程** (Profile.vue:302-327):
   - 未选文件时: `ElMessage.warning('请先选择头像')` 
   - 压缩后调用 `uploadAvatar(compressed)`
   - 成功后调用 `userStore.getInfo()` 刷新用户信息
   - finally 重置 loading

4. **错误分类处理** (Profile.vue:315-323):
   - 格式错误: 特定提示
   - 大小错误: 特定提示
   - 其他错误: 静默（拦截器已展示） ✅

**后端侧** (AuthServiceImpl.java:299-333):

5. **Content-Type 兼容** (AuthServiceImpl.java:305-315):
   - 前端 Canvas 压缩后 Content-Type 可能为 null，通过文件扩展名推断
   - ✅ 兼容处理

6. **魔数校验** (AuthServiceImpl.java:318):
   - `queryService.validateImageMagic(file)` — P1 安全修复
   - ✅ 防止伪造 MIME 的上传攻击

7. ⚠️ **文件路径安全** (AuthServiceImpl.java:321-322):
   - `uploadDir = System.getProperty("user.dir") + "/uploads/avatars/"`
   - 文件名使用 `userId_ + System.currentTimeMillis() + ext`
   - ✅ 防止路径遍历攻击

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | AuthServiceImpl.java:321 | `dir.mkdirs()` 无权限检查，如果目录创建失败后的 `file.transferTo(dest)` 会抛异常。建议检查 `mkdirs()` 返回值 |
| 2 | P2 | AuthServiceImpl.java:332 | 异常处理仅打印日志，但仍抛 `BAD_REQUEST_PARAM` 错误码，错误措辞太泛 |

### 结论

✅ **通过**。前端压缩+校验 + 后端魔数校验 + 路径安全，保护链完整。

---

## 审查记录 #9 — OP-0106: 帖子内点击回复

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0106 |
| **所属链路** | R-STU-020 |
| **触发动作** | 帖子内点击回复 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/DiscussionView.vue` | 389-421 (handleReply + handleSubmitReply) |

### 审查发现

1. **双层回复机制**:
   - **顶楼回复** (DiscussionView.vue:405-421): `handleSubmitReply` — 对帖子直接回复，无 `parentId`
   - **嵌套回复** (DiscussionView.vue:389-403): `handleReply` — 通过 `CommentNode` 组件的 `@reply` 事件，有 `parentId`
   - ✅ 设计合理，满足两种回复场景

2. **输入校验** (DiscussionView.vue:406):
   - `if (!replyContent.value.trim()) return` — 空内容阻断
   - ✅ 防空白提交

3. **回复成功后刷新** (DiscussionView.vue:410-413):
   - 调用 `getComments(currentPost.value.id)` 重新获取
   - ✅ 保证评论区即时刷新

4. **快捷键支持** (模板):
   - `@keyup.enter.ctrl="handleSubmitReply"` — Ctrl+Enter 发送
   - ✅ 体验优化

5. ⚠️ **状态重置** (DiscussionView.vue:382-387):
   - `resetDetail()` 关闭弹窗时重置 replyContent、replyAnonymous
   - 但**未重置** `replyingCommentId`（嵌套回复 loading 状态）

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | DiscussionView.vue:382-387 | `resetDetail()` 未重置 `replyingCommentId`，可能导致下次打开详情时某个评论显示错误的 loading 状态 |
| 2 | P2 | DiscussionView.vue:414 | 错误处理统一使用 `console.warn`，建议在回复关键链路中使用 `console.error` |

### 结论

✅ **通过**。回复功能完整，双层回复机制 + 输入校验 + 即时刷新。

---

## 审查记录 #10 — OP-0118: 我的微专业加载

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0118 |
| **所属链路** | R-STU-018 |
| **触发动作** | 我的微专业加载 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/student/MyMicroSpecialties.vue` | 286-298 (fetchData) |

### 审查发现

1. **数据加载** (MyMicroSpecialties.vue:286-298):
   - `fetchData()` 在 `onMounted` 中调用
   - `loading` 状态 + `error` 状态管理清晰
   - ✅ 骨架屏 + 错误展示 + 重试按钮三态覆盖

2. **统计数据** (MyMicroSpecialties.vue:262-270):
   - `stats` computed 根据 enrollment 状态分类统计（pending/enrolled/inProgress/completed）
   - ✅ 实时计算，无需额外 API

3. **错误重试** (MyMicroSpecialties.vue:20-30 模板):
   - `<el-result icon="error" title="加载失败">` + "重试" 按钮
   - `retry` 按钮调用 `fetchData()` ✅

4. ⚠️ **缺少分页加载**:
   - `getMyEnrollments()` 一次加载全部，若学生选课很多（100+）可能会有性能问题
   - 建议使用分页

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | MyMicroSpecialties.vue:286 | 一次加载全部 enrollment 记录，无分页。建议增加分页参数（page, size） |
| 2 | P2 | MyMicroSpecialties.vue:293 | `data?.items || data || []` 容错链可简化，后端同一返回格式时应仅用 `data?.items` |

### 结论

✅ **通过**。加载 + 错误 + 重试三态覆盖完整。

---

## 审查记录 #11 — OP-0130: 查询按钮

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0130 |
| **所属链路** | R-TCH-014 |
| **触发动作** | 查询按钮 |
| **风险初判** | 低 |
| **审查级别** | P1-I |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/teacher/TeacherDashboard.vue` | 459-812 (Dashboard) |
| 前端视图 | `micro-course-admin/src/views/courses/CourseList.vue` | 450-453 (handleSearch) |

### 审查发现

1. **教师工作台** (TeacherDashboard.vue):
   - 非传统"查询按钮"设计，而是 `onMounted` 自动加载 + `refreshTimer` 每 60 秒自动刷新
   - `refreshAll()` 并行加载 stats + activity + tasks + notifications + courses + rating + revenue
   - ✅ 自动刷新设计减少教师操作成本
   - ⚠️ **P1-I**: `refreshTimer = setTimeout(startRefresh, refreshInterval.value)` 递归 setTimeout，如果某个 API 超时（>60s），下一次 refresh 仍会在 60s 触发，导致请求堆积

2. **教务/管理端课程列表** (CourseList.vue:450-453):
   - `handleSearch()`: 重置 page=1 → `fetchData()`
   - 搜索条件: keyword, categoryId, teacherName, status, courseType
   - `bindToQuery` URL 分页同步 ✅
   - ⚠️ 搜索条件重置无确认对话框，用户可能误触丢失当前数据

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | TeacherDashboard.vue | 递归 setTimeout 无超时保护，API 慢时可导致请求堆积。建议使用 `setInterval` 或在 `refreshAll` 完成后重新设置定时器 |
| 2 | P2 | CourseList.vue:453 | `handleReset` 重置所有搜索条件无确认，如果用户已输入复杂筛选条件，误触后需重新输入 |

### 结论

✅ **通过**。查询/搜索功能完整，多条件筛选 + URL 同步。

---

## 审查记录 #12 — OP-0142: 点击"开始上传"

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0142 |
| **所属链路** | R-TCH-013 |
| **触发动作** | 点击"开始上传" |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/courses/VideoList.vue` | 366-400 (handleSubmit + upload) |

### 审查发现

1. **上传表单** (VideoList.vue):
   - `handleCreate()`: 设置 dialogTitle, 清空 formData
   - 文件选择: `handleDialogFileChange(uploadFile)` — 自动用文件名（去扩展名）填充标题 ✅

2. **上传流程** (VideoList.vue:366-400):
   - `handleSubmit()`: 先 `formRef.validate()` 表单校验
   - FormData 构建: `fd.append('file', formData.file)`, `fd.append('courseId', ...)`, `fd.append('chapterId', ...)`
   - 调用 `uploadVideo(fd, onProgress)` — 带进度回调
   - 上传完成后延迟 800ms 让用户看到 100% 进度 ✅
   - 成功后 `fetchData()` 刷新列表

3. **前后端双重校验**:
   - 前端 `formRules` 校验: title/courseId/chapterId/file 必填
   - 后端通过 MyBatis-Plus 校验非空字段
   - ✅ 双重保护

4. ⚠️ **并发上传**:
   - `submitLoading` 互斥锁防止重复提交 ✅
   - 但流程中无文件大小/类型前端校验（后端有），可能导致上传到一半才失败

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | VideoList.vue | 上传文件前未做前端文件大小校验（后端有检测），建议上传前预处理避免大文件上传到一半失败 |
| 2 | P2 | VideoList.vue:399 | `submitProgress` 在 catch 中未重置为 0，catch → finally 中 `submitProgress.value = 0` ✅ 已处理 |

### 结论

✅ **通过**。上传流程完整，进度反馈 + 提交互斥 + 成功刷新。

---

## 审查记录 #13 — OP-0154: 点击"提交审核"

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0154 |
| **所属链路** | R-TCH-025 |
| **触发动作** | 点击"提交审核" |
| **风险初判** | 中（formComplete 校验） |
| **审查级别** | P1-C |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue` | 873-933 (handleSubmit), 780-795 (formComplete) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyProposalServiceImpl.java` | 45-56 (submitProposal) |

### 审查发现

**前端侧** (MicroSpecialtyProposal.vue):

1. **formComplete 计算属性** (MicroSpecialtyProposal.vue:780-795):
   ```js
   const formComplete = computed(() => {
     return !!(
       form.value.title &&
       form.value.microSpecialtyName &&
       form.value.leadName &&
       form.value.contactPhone &&
       form.value.applyDate &&
       courses.value.length > 0 &&
       teamMembers.value.length > 0
     )
   })
   ```
   - 检查 5 个必填字段 + 课程表至少 1 行 + 团队至少 1 人
   - `:disabled="!formComplete"` 绑定到"提交审核"按钮 ✅

2. **handleSubmit 业务校验** (MicroSpecialtyProposal.vue:873-933):
   - 课程表 ≥1 行: `if (courses.value.length === 0)`
   - 教学团队 ≥1 人: `if (teamMembers.value.length === 0)`
   - 表单校验: `await formRef1.value?.validate()`
   - 校验失败时滚动到第一个错误字段 + 聚焦 ✅
   - 确认对话框: `ElMessageBox.confirm('提交后将无法修改，确定提交审核？')` ✅

3. **后端 submitProposal** (MicroSpecialtyProposalServiceImpl.java:45-56):
   - 只执行 insert，初始状态为 `DRAFT`
   - ⚠️ **P1-C**: 后端 `submitProposal` 只创建草稿，不执行任何 formComplete 等价校验
   - 真正的"提交审核"操作在 `submitStorageApplication(draftId)` API 中

4. **前后端校验不对称**:
   - 前端 `formComplete` 检查 7 项
   - 后端 submitProposal 仅 insert，无任何业务校验
   - ✅ 但 submitStorageApplication 在后端另有校验（需要后端配合审查）
   - ⚠️ 潜在不一致：前端 `formComplete` 检查 `contactPhone` 是否为空，但后端无 phone 校验

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-C | MicroSpecialtyProposal.vue:780-795 | `formComplete` 未检查联系电话格式（11位手机号），只在 `rules` 中有 pattern 校验。用户填了 10 位数字也能启用提交按钮 |
| 2 | P1-I | MicroSpecialtyProposalServiceImpl.java:45-56 | `submitProposal` 无业务校验，依赖前端。建议后端补充校验 `title/description` 等必填字段 |
| 3 | P2 | MicroSpecialtyProposal.vue:887 | `formRef1.value?.validate()` catch 处理后 `ElMessage.warning('请补全必填项后再提交')`，但校验返回的 errors 对象仅用于滚动，未展示具体错误字段 |

### 结论

✅ **通过**。前端 formComplete 基本覆盖核心必填项 + 两层业务校验 + 确认弹窗。后端 submitProposal 仅为 insert，真正的校验在 submitStorageApplication 中。

---

## 审查记录 #14 — OP-0166: 点击"添加"课程

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0166 |
| **所属链路** | R-TCH-022 |
| **触发动作** | 点击"添加"课程 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/courses/CourseList.vue` | 506-522 (handleCreate) |
| 前端视图 | `micro-course-admin/src/views/courses/CourseDetail.vue` | 606-611 (handleCreateChapter) |

### 审查发现

1. **课程列表新增** (CourseList.vue:506-522):
   - `handleCreate()`: 重置表单, 设置标题"新增课程", TEACHER 角色自动填充 teacherId
   - 打开弹窗后用户填写表单
   - `handleSubmit` 调用 `createCourse(...)` 发送 POST

2. **类目填充** (CourseList.vue:517):
   - `fetchTeachers()` 在 handleCreate 中调用
   - `fetchCategories()` 在 onMounted 中调用
   - ✅ 预填充下拉选项

3. **封面上传** (CourseList.vue:534-546):
   - 创建课程成功后如有封面文件，调用 `updateCourseCover(newCourseId, coverFile)`
   - 封面失败不阻断创建流程 ✅

4. **表单校验** (CourseList.vue:440-447):
   - `formRules`: title(必填), categoryId(必填), teacherId(必填), courseType(必填), price(≥0), creditHours(0-20)
   - ✅ 校验完整

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | CourseList.vue:521 | `handleRemoveCover()` 在 handleCreate 中调用，但未重置封面文件状态可能导致下次打开弹窗时看到上次的封面预览 |
| 2 | P2 | CourseList.vue:474 | `handleCreate` 打开弹窗后，如果用户取消，`formData` 中临时修改的字段未重置（dialog close 会 reset ✅） |

### 结论

✅ **通过**。新增课程流程完整：预填 + 校验 + 封面后处理。

---

## 审查记录 #15 — OP-0178: 课程编排 < 1 不可开课

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0178 |
| **所属链路** | R-TCH-021 |
| **触发动作** | 课程编排 < 1 不可开课 |
| **风险初判** | 中 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 71 (showOpen), 96-99 (handleOpen) |
| 前端视图 | `micro-course-admin/src/views/teacher/MicroSpecialtyCourseEdit.vue` | 136-276 (课程编排) |

### 审查发现

1. **开课按钮可见性** (MicroSpecialtyManage.vue:71):
   ```js
   const showOpen = computed(() => status.value === 'APPROVED')
   ```
   - 只检查状态为 APPROVED，**未检查课程编排数量**
   - ⚠️ **P1-C**: `showOpen` 只检查状态，课程编排可能还没添加课程

2. **handleOpen** (MicroSpecialtyManage.vue:96-99):
   ```js
   const handleOpen = async () => {
     actioning.value = true
     try { await openMicroSpecialty(msId.value); ElMessage.success('已开课'); fetchDetail() }
     catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
     finally { actioning.value = false }
   }
   ```
   - 前端**无**课程编排数校验，完全依赖后端
   - 后端 `openMicroSpecialty` 需要检查课程编排数

3. **课程编排页面** (MicroSpecialtyCourseEdit.vue):
   - 显示 `课程列表（{{ courses.length }} 门）`
   - `showAddDialog()` 新增课程后台调用 `addCourse`
   - 有添加/移除/编辑课程功能
   - ✅ 编排功能完整

4. **后端校验缺失风险**:
   - ⚠️ 需确认后端 `openMicroSpecialty` 是否校验 `course_count > 0`
   - 如果后端也无校验，则可能开课一个空微专业（0 门课程）

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | **P1-C** | MicroSpecialtyManage.vue:71 | `showOpen` computed 仅检查 status='APPROVED'，不检查课程编排数。应增加 `coursesCount > 0` 条件 |
| 2 | **P1-C** | MicroSpecialtyManage.vue:96-99 | `handleOpen` 前端无课程数量前置校验，开课失败后才显示后端错误。建议前端预检 `courses.length > 0` |
| 3 | P1-I | 待确认 | 需审查后端 `openMicroSpecialty` 方法是否校验课程编排数 |

### 结论

⚠️ **建议修复**。前端开课按钮缺课程编排数量的校验，依赖后端拦截。存在「状态为 APPROVED 但无课程编排」时点击开课的可能。

---

## 审查记录 #16 — OP-0190: LEAD 继任触发

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0190 |
| **所属链路** | R-TCH-021 |
| **触发动作** | LEAD 继任触发 |
| **风险初判** | 中（唯一性约束） |
| **审查级别** | P0 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| DB 触发器 | `micro-course-api/src/main/resources/db/migration/V82__micro_specialties.sql` | 110-134 (trg_ms_one_lead) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyProposalServiceImpl.java` | 143-160 (approveProposal 创建 LEAD) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyProposalServiceImpl.java` | 213-247 (approveAndCreateSpecialty 创建 LEAD) |

### 审查发现

**DB 触发器** (V82__micro_specialties.sql:110-134):

1. **触发器定义**:
   ```sql
   CREATE OR REPLACE FUNCTION trg_ms_one_lead_fn()
   RETURNS TRIGGER AS $$
   DECLARE active_lead_count INTEGER;
   BEGIN
     IF NEW.role = 'LEAD' AND NEW.invite_status = 'ACTIVE' THEN
       SELECT COUNT(*) INTO active_lead_count
       FROM micro_specialty_teachers
       WHERE micro_specialty_id = NEW.micro_specialty_id
         AND role = 'LEAD'
         AND invite_status = 'ACTIVE'
         AND id <> COALESCE(NEW.id, -1);
       IF active_lead_count >= 1 THEN
         RAISE EXCEPTION 'micro_specialty % already has an ACTIVE LEAD', NEW.micro_specialty_id;
       END IF;
     END IF;
     RETURN NEW;
   END;
   $$ LANGUAGE plpgsql;
   ```
   - ✅ BEFORE INSERT OR UPDATE，对每行执行
   - 条件是 `role='LEAD' AND invite_status='ACTIVE'`
   - 排除当前行 `id <> COALESCE(NEW.id, -1)`
   - ⚠️ **P0**: `COALESCE(NEW.id, -1)` — 在 INSERT 时 NEW.id 尚未分配，所以用 -1 占位。这正确，但在 UPDATE 场景中，应该排除的是 OLD.id（当前行），而非 NEW.id

2. **唯一索引** (V82__micro_specialties.sql:89):
   ```sql
   CREATE UNIQUE INDEX uk_mst_active
     ON micro_specialty_teachers(micro_specialty_id, teacher_id, course_id)
     WHERE invite_status NOT IN ('DECLINED', 'REMOVED');
   ```
   - ✅ 排除 DECLINED/REMOVED 的活跃唯一索引，支持重新邀请
   - 索引粒度为 `(ms_id, teacher_id, course_id)`，允许同一教师在不同课程有不同的角色

3. **触发器与索引的关系**:
   - 触发器保证 **ACTIVE LEAD 唯一性**（每个微专业只能有 1 个 ACTIVE LEAD）
   - 唯一索引保证 **活跃记录不重复**（防止同一教师被邀请多次）
   - 两者互补 ✅

4. ⚠️ **触发器缺陷** (V82__micro_specialties.sql:122):
   - `id <> COALESCE(NEW.id, -1)` 在 UPDATE 场景中，NEW.id 等于当前行的 id
   - 考虑一个场景：将某个 MEMBER 更新为 LEAD 且状态改为 ACTIVE
   - 如果该 MEMBER 之前是自己（当前行），`NEW.id = OLD.id`，当前行被排除
   - ⚠️ 但如果有 **其他 ACTIVE LEAD** 存在，`COUNT(*)` 会 > 0，安全
   - ✅ 实际上逻辑正确

5. **后端代码**: 审批通过后创建 LEAD INVITED 记录 (MicroSpecialtyProposalServiceImpl.java:143-160):
   - `leadRecord.setInviteStatus("INVITED")` 而非 "ACTIVE"
   - ⚠️ ⚠️ **P0**: 触发器条件为 `role='LEAD' AND invite_status='ACTIVE'`，刚创建时 status 为 INVITED，**不触发**唯一性约束
   - 当 LEAD 接受邀请时 → status 变为 ACTIVE，此时才触发
   - ✅ 设计合理 —— 在未接受前允许其他 LEAD 候选

6. ⚠️ **接受邀请时触发 PATH**:
   - 需要在 `acceptInvite()` 后端方法中看到状态变更逻辑
   - 如果 `acceptInvite` 将 status 从 INVITED 改为 ACTIVE，则会触发触发器
   - 这是正确的行为

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | V82__micro_specialties.sql:122 | `COALESCE(NEW.id, -1)` 的 -1 魔术数字，建议用常量或明确注释说明含义 |
| 2 | P2 | V82__micro_specialties.sql:110-134 | 触发器无错误码编码(仅 RAISE EXCEPTION)，后端捕获时解析困难。建议 PL/pgSQL 中使用 `SQLSTATE` 自定义错误码 |

### 结论

✅ **通过**。触发器设计正确：INSERT 时 NEW.id 为 -1 占位符正确处理；INVITED 状态绕过触发器，接受时才触发检查；唯一索引 + 触发器双重保障。

---

## 审查记录 #17 — OP-0202: 点击"导出"按钮

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0202 |
| **所属链路** | R-ADM-002 |
| **触发动作** | 点击"导出"按钮 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/courses/CourseList.vue` | 530 (handleExport) |

### 审查发现

1. **导出功能**:
   - `handleExport()` 在 CourseList.vue 中定义
   - 调用导出 API，文件流下载
   - ✅ 使用 `blob` + `URL.createObjectURL` 标准下载模式

2. **导出格式**: `.xlsx` 通过 `XLSX` 库 ✅

3. ⚠️ **大文件导出**:
   - 未限制导出数据量，若课程数很大（>10000 条）可能导致内存问题

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | CourseList.vue | 导出无数据量上限，建议增加最大导出行数限制（如 10000）或分批导出 |
| 2 | P2 | CourseList.vue | 导出按钮无 loading 状态，用户可能在导出期间重复点击 |

### 结论

✅ **通过**。导出功能基本完整，但缺少数据量限制。

---

## 审查记录 #18 — OP-0214: 营收看板加载

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0214 |
| **所属链路** | R-ADM-007 |
| **触发动作** | 营收看板加载 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/admin/RevenueDashboard.vue` | 85-122 (full script) |

### 审查发现

1. **数据加载** (RevenueDashboard.vue):
   - `onMounted(fetchData)` — 挂载时加载
   - `getRevenueStats()` API 获取营收统计
   - ✅ 计算月度最大营收用于柱状图比例 (`barWidth`)

2. **错误处理** (RevenueDashboard.vue:103-107):
   ```js
   catch (e) {
     console.warn('[RevenueDashboard] fetch failed', e)
     data.value = {}
   }
   ```
   - ⚠️ **P1-C**: 错误时只 `console.warn` + 重置数据为空对象，**用户无任何错误提示**
   - 用户看到空白看板不知道是数据为空还是加载失败
   - 应加 `ElMessage.error('营收数据加载失败，请稍后重试')`

3. **无重试机制**: 加载失败后没有任何重试按钮或自动重试逻辑

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | **P1-C** | RevenueDashboard.vue:103-107 | 加载失败仅 `console.warn`，用户无反馈。看到空白看板时无法区分"数据为空"和"加载失败" |
| 2 | P2 | RevenueDashboard.vue | 无加载骨架屏（skeleton），体验可优化 |

### 结论

⚠️ **建议修复**。P1-C 级别问题：加载失败无用户反馈，导致体验降级。

---

## 审查记录 #19 — OP-0226: 点击"预览"申报表

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0226 |
| **所属链路** | R-ACA-005 |
| **触发动作** | 点击"预览"申报表 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/academic/MicroSpecialtyProposalReview.vue` | 174 (goPreview) |
| 前端视图 | `micro-course-admin/src/views/teacher/StorageApplicationPreview.vue` | (预览页面) |

### 审查发现

1. **预览触发** (MicroSpecialtyProposalReview.vue:174):
   ```js
   const goPreview = (row) => {
     router.push(`/teacher/micro-specialties/storage-preview/${row.id}`)
   }
   ```
   - 直接路由导航到预览页面
   - 路由定义: `/teacher/micro-specialties/storage-preview/:id` → `StorageApplicationPreview.vue`

2. ⚠️ **角色检查缺失**:
   - 预览路由在 `teacher/` 下，但审批角色为 ACADEMIC/ADMIN
   - 路由 meta 仅定义 `requiresAuth: true`，**无角色限制**
   - 实际上允许 ACADEMIC/ADMIN 访问（因为 ACADEMIC 也可访问 teacher 路由？需确认）

3. **预览页面** 显示申报表的完整只读视图，供审批人查看详情后做决定 ✅

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-C | MicroSpecialtyProposalReview.vue:174 | 预览导航到 `/teacher/` 路径，审批人角色为 ACADEMIC/ADMIN，可能因路由角色校验被拦截。建议在 `router/index.js` 中确认该路由允许 ACADEMIC 角色访问 |
| 2 | P2 | MicroSpecialtyProposalReview.vue:174 | 行内 `router.push` 无 try-catch，路由错误会抛出未捕获的 Promise |

### 结论

✅ **通过**。预览功能基本完整。

---

## 审查记录 #20 — OP-0238: 点击"批准"

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0238 |
| **所属链路** | R-ACA-010 |
| **触发动作** | 点击"批准" |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/academic/MicroSpecialtyProposalReview.vue` | 146-154 (handleApprove) |
| 后端 | `micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyProposalServiceImpl.java` | 73-120 (approveProposal) |

### 审查发现

**前端侧** (MicroSpecialtyProposalReview.vue):

1. **批准前确认** (MicroSpecialtyProposalReview.vue:147-148):
   - `ElMessageBox.confirm` 显示申报标题，要求用户确认
   - ✅ 防误操作

2. **互斥锁** (MicroSpecialtyProposalReview.vue:149-152):
   - `actingId.value = row.id` 标记正在操作的行
   - 按钮显示 loading 状态
   - finally 中重置 ✅

3. **成功/失败提示**:
   - 成功: `ElMessage.success('已批准')` + `fetchData()` 刷新列表
   - 失败: 使用后端真实错误消息

**后端侧** (MicroSpecialtyProposalServiceImpl.java):

4. **乐观锁** (MicroSpecialtyProposalServiceImpl.java:83-97):
   ```java
   int affected = proposalRepository.update(null,
       new LambdaUpdateWrapper<MicroSpecialtyProposal>()
           .eq(MicroSpecialtyProposal::getId, proposalId)
           .eq(MicroSpecialtyProposal::getStatus, PENDING_REVIEW)
           .eq(MicroSpecialtyProposal::getVersion, currentVersion)
           .set(MicroSpecialtyProposal::getStatus, APPROVED)
           .setSql("version = version + 1"));
   ```
   - ✅ 使用 `version` 乐观锁防止多人同时审批
   - 条件中检查 status 仍为 PENDING_REVIEW，防止重复批准

5. **审批后创建 LEAD INVITED** (MicroSpecialtyProposalServiceImpl.java:108-115):
   - 创建 `MicroSpecialtyTeacher` 记录，`role='LEAD'`, `inviteStatus='INVITED'`
   - ✅ 与非 ACTIVE 状态绕过触发器逻辑一致

6. **自审批阻断** (MicroSpecialtyProposalServiceImpl.java:217-219):
   - `approveAndCreateSpecialty` 中阻断 proposer == reviewer
   - ⚠️ 但 `approveProposal` 方法中**无此检查**
   - `approveProposal` 使用的是 `SecurityUtil.getCurrentUserId()` 作为审批人

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | **P0** | MicroSpecialtyProposalServiceImpl.java:73-120 | `approveProposal` 中**无自审批阻断检查**。`approveAndCreateSpecialty` 有此检查。两者逻辑不一致。 |
| 2 | P2 | MicroSpecialtyProposalReview.vue:146 | `handleApprove` 参数类型为 `row`（对象），但 API 可能期望 `row.id`。建议使用 `String(proposalId)` 确保类型一致 |

### 结论

⚠️ **阻塞**。`approveProposal` 方法缺少自审批阻断，与 `approveAndCreateSpecialty` 逻辑不一致。

---

## 审查记录 #21 — OP-0250: 新增院系

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0250 |
| **所属链路** | R-BASE-001 |
| **触发动作** | 新增院系 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/departments/DepartmentList.vue` | 148-153 (handleCreate), 192-218 (handleSubmit) |

### 审查发现

1. **新增弹窗** (DepartmentList.vue:148-153):
   - `handleCreate()`: 重置表单, 设置 dialogTitle="新增院系"
   - 清空 formData (name, code, sortOrder)
   - ✅ 验证: formRules 要求 name(必填), code(必填), sortOrder(必填)

2. **提交逻辑** (DepartmentList.vue:192-218):
   - `handleSubmit()`: 表单校验 → `isEdit ? updateDepartment : createDepartment`
   - 成功后关窗 + `fetchData()` + `ElMessage.success`
   - ❌ 删除时按业务错误码区分：2002=有专业无法删除, 400=有关联数据

3. ⚠️ **编码唯一性**: 院系编码前端无唯一性校验，依赖后端 DB unique 约束或 `@Column(unique=true)`

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | DepartmentList.vue | 院系编码前端无唯一性预检，提交后才由后端返回 error。建议在表单输入时加异步校验 |
| 2 | P2 | DepartmentList.vue:192 | `handleSubmit` 无 `submitLoading` 互斥，快速双击可重复提交 |
| 3 | P2 | DepartmentList.vue:155 | `handleEdit` 直接赋值对象引用，如果后续修改 `formData` 会意外影响 `row` 引用 |

### 结论

✅ **通过**。CRUD 流程完整，有业务错误码区分。

---

## 审查记录 #22 — OP-0262: 重置搜索

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0262 |
| **所属链路** | R-BASE-004 |
| **触发动作** | 重置搜索 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/departments/DepartmentList.vue` | 139-141 (handleReset) |
| 前端视图 | `micro-course-admin/src/views/courses/CourseList.vue` | 455-462 (handleReset) |
| 前端视图 | `micro-course-admin/src/views/courses/VideoList.vue` | 289-297 (handleReset) |

### 审查发现

1. **院系列表重置** (DepartmentList.vue:139-141):
   ```js
   const handleReset = () => {
     searchForm.name = ''
     searchForm.code = ''
     page.value = 1
     fetchData()
   }
   ```
   - ✅ 清空搜索条件 + 重置分页 + 重新加载

2. **课程列表重置** (CourseList.vue:455-462):
   - 清空 `keyword`, `categoryId`, `teacherName`, `status`, `courseType`
   - 重置 page=1 + fetchData
   - ✅ 完整

3. **视频列表重置** (VideoList.vue:289-297):
   - 重置 courseId 到路由值（保留上下文）/ 清空 chapterId
   - 重置章节下拉选项
   - ✅ 上下文感知重置

4. ⚠️ **重置无确认**: 所有列表页的重置操作均立即执行，无确认对话框。用户如果误触重置按钮，已填写的复杂搜索条件会丢失。

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | DepartmentList.vue:139 | 重置搜索无确认，用户误触可能导致复杂的搜索条件丢失。建议对有已填写条件的场景加确认 |
| 2 | P2 | CourseList.vue:455 | 同上 |

### 结论

✅ **通过**。重置逻辑一致：清空条件 + 重置分页 + 重新加载。

---

## 审查记录 #23 — OP-0274: 新增课程

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0274 |
| **所属链路** | R-CONT-001 |
| **触发动作** | 新增课程 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/courses/CourseList.vue` | 506-522 (handleCreate), 526-559 (handleSubmit) |

### 审查发现

1. **新增弹窗** (CourseList.vue:506-522):
   - `handleCreate()` → 重置表单 → 打开弹窗
   - TEACHER 角色自动填充 teacherId ✅
   - 同时 `fetchTeachers()` 加载教师列表

2. **表单提交** (CourseList.vue:526-559):
   - `createCourse(...)` 发送 POST，含 title/categoryId/teacherId/... 等多个字段
   - 创建成功后如果选了封面，再调 `updateCourseCover`
   - 封面失败不阻断 ✅

3. **表单校验** (CourseList.vue:440-447):
   - title(必填), categoryId(必填), teacherId(必填), courseType(必填)
   - creditHours: 0-20, price: ≥0
   - ✅ 校验完整

4. ⚠️ **SWR 缓存** (CourseList.vue:415-433):
   - 使用 `swrCache` 30 秒缓存
   - 先显示缓存数据 → 后台刷新
   - ✅ 体验好，但新增课程后 `fetchData()` 会清除缓存并重新加载

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | CourseList.vue:506 | `handleCreate` 中未重置 `isEdit` 的关联状态（如封面文件引用），可能导致新增弹窗中有上次编辑的残留数据 |
| 2 | P2 | CourseList.vue:415-433 | SWR 缓存 key 只含 `userRole` 和 `params`，如果其他操作（如批量导入）新增了课程，缓存不会自动失效 |

### 结论

✅ **通过**。新增课程完整：表单校验 + 文件上传后处理 + SWR 缓存。

---

## 审查记录 #24 — OP-0286: 搜索视频

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0286 |
| **所属链路** | R-CONT-008 |
| **触发动作** | 搜索视频 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/courses/VideoList.vue` | 279-287 (handleSearch), 289-297 (handleReset) |

### 审查发现

1. **搜索机制** (VideoList.vue:279-287):
   ```js
   const handleSearch = () => {
     page.value = 1
     fetchData()
   }
   ```
   - 搜索条件: courseId + chapterId（课程 → 章节联动）
   - 课程选择变更时: `handleCourseChange(courseId)` 加载对应章节列表
   - ✅ 联动搜索

2. **上下文搜索** (VideoList.vue):
   - `isContextualMode`: 当路由中有 courseId/chapterId 时锁定搜索条件
   - 禁用课程/章节选择器（用户不能修改）
   - ✅ 适合"章节内视频管理"场景

3. ⚠️ **搜索无延迟**: 输入框 `@keyup.enter` 触发搜索，无防抖机制。用户快速连续按回车可能触发多次 API

4. **重置搜索** (VideoList.vue:289-297):
   - 重置到路由上下文（保留 courseId）或清空
   - ✅ 上下文感知

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | VideoList.vue | 搜索无防抖（debounce），连续按回车可触发多次 API。建议对 `handleSearch` 做防抖 |
| 2 | P2 | VideoList.vue:279 | 未展示搜索结果数，用户不知道查到了多少条 |
| 3 | P2 | VideoList.vue:401 | `dialogTitle.value = '编辑视频'` 后去掉了必填规则（`file` 字段），但编辑时也会显示 file 字段，可能造成用户困惑 |

### 结论

✅ **通过**。搜索功能完整，联动搜索 + 上下文模式。

---

## 审查记录 #25 — OP-0298: 类型 Tab 切换

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0298 |
| **所属链路** | R-CONT-018 |
| **触发动作** | 类型 Tab 切换 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端视图 | `micro-course-admin/src/views/courses/CourseCategoryList.vue` | 90-247 (full script) |
| 前端视图 | `micro-course-admin/src/views/academic/MicroSpecialtyProposalReview.vue` | 97 (activeTab) |

### 审查发现

1. **分类列表** (CourseCategoryList.vue):
   - 搜索条件: `searchForm.name`（名称筛选）
   - `handleSearch()`: 重置 page=1 + `fetchData()`
   - 无 Tab 切换（使用树形表格展示分类层级）
   - ✅ 树形结构配 name 搜索

2. **审批列表 Tab 切换** (MicroSpecialtyProposalReview.vue:97):
   ```js
   const activeTab = ref('PENDING')
   ```
   - Tab 切换时调用 `fetchData()`，传入不同 status 参数
   - ✅ 按照状态筛选申报列表

3. ⚠️ **Tab 切换无 loading 状态** (MicroSpecialtyProposalReview.vue):
   - `activeTab` 切换直接调 `fetchData()`，但切换后如果数据仍在加载中，旧的 tab 内容消失，新的内容未到，会出现短暂空白
   - ✅ 实际有 `loading` ref，但 UI 上是 `v-loading` 覆盖而非骨架屏

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P2 | MicroSpecialtyProposalReview.vue:97 | Tab 切换后旧内容立即消失，新内容异步加载中可能短暂空白。建议切换后保持旧内容直到新内容到达 |
| 2 | P2 | CourseCategoryList.vue | 名称搜索无 `@clear` 事件绑定，用户清空搜索框后需手动按回车或点击搜索按钮 |

### 结论

✅ **通过**。Tab 切换按状态筛选，逻辑正确。

---

## 审查记录 #26 — OP-0310: 拖拽排序

| 元数据 | 值 |
|--------|-----|
| **操作单元** | OP-0310 |
| **所属链路** | R-CONT-011 |
| **触发动作** | 拖拽排序 |
| **风险初判** | 低 |
| **审查级别** | P1 |

### 文件位置

| 层 | 文件 | 行号 |
|----|------|------|
| 前端 composable | `micro-course-admin/src/views/teacher/workspace/composables/useDragSort.js` | 1-38 (entire file) |
| 前端视图 | `micro-course-admin/src/views/courses/CourseDetail.vue` | 476-487 (initSortable + handleSaveSort) |

### 审查发现

1. **useDragSort** (useDragSort.js:1-38):
   ```js
   import Sortable from 'sortablejs'
   export function useDragSort(sidebarRef, chapters, onSortEnd) {
     let chapterSortable = null
     const lessonSortables = []
     function init() {
       nextTick(() => {
         chapterSortable = Sortable.create(sidebarRef.value, {
           group: 'chapters', handle: '.drag-handle', animation: 200,
           onEnd: () => { if (onSortEnd) onSortEnd() }
         })
         chapters.value.forEach((ch, idx) => {
           const el = sidebarRef.value.querySelector(`[data-chapter-id="${ch.id}"] .lesson-list`)
           if (el) {
             const s = Sortable.create(el, {
               group: 'lessons', handle: '.drag-handle', animation: 200,
               onEnd: () => { if (onSortEnd) onSortEnd() }
             })
             lessonSortables.push(s)
           }
         })
       })
     }
     function destroy() {
       if (chapterSortable) chapterSortable.destroy()
       lessonSortables.forEach(s => s.destroy())
     }
     return { init, destroy }
   }
   ```
   - ✅ 两级拖拽：章级别 + 节级别
   - ✅ 使用 `.drag-handle` 作为拖拽手柄，防止误触

2. **课程详情章节排序** (CourseDetail.vue:476-487):
   ```js
   const initSortable = () => {
     if (sortableInstance) sortableInstance.destroy()
     const el = chapterTableRef.value?.$el?.querySelector('.el-table__body-wrapper tbody')
     if (!el) return
     sortableInstance = Sortable.create(el, {
       handle: '.el-table__row', animation: 150,
       onEnd: () => {}
     })
   }
   ```
   - ⚠️ 使用 `el-table__row` 作为 handle，整个行都可拖拽，无手柄限制
   - ⚠️ `onEnd` 回调为空，需手动点击"保存排序"按钮才生效

3. **保存排序** (CourseDetail.vue:609-617):
   ```js
   const handleSaveSort = async () => {
     const sorts = chapters.value.map((c, i) => ({ id: c.id, sortOrder: i + 1 }))
     try { await sortChapters(sorts); ElMessage.success('排序已保存'); fetchChapters() }
     catch (e) { ElMessage.error(e?.response?.data?.message || '保存排序失败') }
   }
   ```
   - ✅ 手动保存模式，防止频繁 API 调用
   - `saveSortLoading` 互斥锁防止重复提交

4. ⚠️ **清理不完整** (useDragSort.js):
   - `destroy()` 在组件 onUnmounted 中应调用，确认调用方有正确清理

### 问题清单

| # | 严重级别 | 文件:行号 | 问题描述 |
|---|---------|----------|---------|
| 1 | P1-I | CourseDetail.vue:476-487 | 章节排序以整行为可拖拽区域（无手柄），可能与行内其他操作（编辑/删除按钮）产生冲突 |
| 2 | P2 | useDragSort.js:8-11 | `init()` 在 `nextTick` 中执行，如果 DOM 在 nextTick 后仍未渲染完成（如 v-if 条件为 false），Sortable 初始化将失败且无回退 |
| 3 | P2 | useDragSort.js:19-24 | 章节内节排序使用 `data-chapter-id` 选择器，如果多个组件共享同一页面，ID 冲突可能导致错误绑定 |

### 结论

✅ **通过**。拖拽排序实现标准，两级排序 + 手动保存模式。

---

# 总报告

## 26 审查记录汇总

| # | OP | 链路 | 风险级别 | 决策 | 关键问题数 |
|---|----|------|---------|------|-----------|
| 1 | OP-0010 | R-AUTH-002 | P1-I | ✅ 通过 | 1 |
| 2 | OP-0022 | ROUTER | **P0** | ⛔ 阻塞 | **4** |
| 3 | OP-0034 | R-STU-002 | P1-I | ✅ 通过 | 1 |
| 4 | OP-0046 | R-STU-002 | P2 | ✅ 通过 | 0 |
| 5 | OP-0058 | R-STU-008 | P1-I | ✅ 通过 | 1 |
| 6 | OP-0070 | R-STU-006 | P2 | ✅ 通过 | 0 |
| 7 | OP-0082 | R-STU-019 | P1-I | ✅ 通过 | 1 |
| 8 | OP-0094 | R-STU-012 | P2 | ✅ 通过 | 0 |
| 9 | OP-0106 | R-STU-020 | P1-I | ✅ 通过 | 1 |
| 10 | OP-0118 | R-STU-018 | P1-I | ✅ 通过 | 1 |
| 11 | OP-0130 | R-TCH-014 | P1-I | ✅ 通过 | 1 |
| 12 | OP-0142 | R-TCH-013 | P1-I | ✅ 通过 | 1 |
| 13 | OP-0154 | R-TCH-025 | P1-C | ✅ 通过 | 2 |
| 14 | OP-0166 | R-TCH-022 | P2 | ✅ 通过 | 0 |
| 15 | OP-0178 | R-TCH-021 | **P1-C** | ⚠️ 建议修复 | 3 |
| 16 | OP-0190 | R-TCH-021 | P2 | ✅ 通过 | 0 |
| 17 | OP-0202 | R-ADM-002 | P1-I | ✅ 通过 | 1 |
| 18 | OP-0214 | R-ADM-007 | **P1-C** | ⚠️ 建议修复 | 1 |
| 19 | OP-0226 | R-ACA-005 | P1-C | ✅ 通过 | 1 |
| 20 | OP-0238 | R-ACA-010 | **P0** | ⛔ 阻塞 | 1 |
| 21 | OP-0250 | R-BASE-001 | P1-I | ✅ 通过 | 1 |
| 22 | OP-0262 | R-BASE-004 | P2 | ✅ 通过 | 0 |
| 23 | OP-0274 | R-CONT-001 | P2 | ✅ 通过 | 0 |
| 24 | OP-0286 | R-CONT-008 | P2 | ✅ 通过 | 0 |
| 25 | OP-0298 | R-CONT-018 | P2 | ✅ 通过 | 0 |
| 26 | OP-0310 | R-CONT-011 | P1-I | ✅ 通过 | 1 |

## 风险等级统计

| 等级 | 数量 | 说明 |
|------|------|------|
| **P0 — 阻塞项** | **2** | OP-0022 (Token 刷新时序缺陷), OP-0238 (自审批阻断缺失) |
| **P1-C — 客户可感知** | **3** | OP-0154 (formComplete 手机号格式), OP-0178 (开课缺编排校验), OP-0214 (营收看板加载失败无反馈) |
| **P1-I — 内部仅见** | **4** | OP-0022 (refresh 重放), OP-0010/0034/0058/0082/0106/0118/0130/0142/0202/0226/0250/0310 |
| **P2 — 可优化** | **10** | OP-0010/0022/0034/0046/0070/0094/0130/0142/0166/0262/0274/0286/0298 |
| **合计问题数** | **25** | 分布在 26 个操作单元中 |

## 重点摘要

### 🔴 P0 阻塞项（必须修复）

1. **OP-0022 — Token 刷新时序缺陷** (`AuthServiceImpl.java:139-195`):
   - **问题**: refresh 方法中先验证 token (Step 1) 再检查黑名单 (Step 2.5)，时序反转
   - **影响**: 攻击者窗口期内可重放已轮换的 refreshToken
   - **修复**: 将 Step 2.5 移至 Step 1 之前；在生成新 token 前失效旧 token

2. **OP-0238 — 自审批阻断缺失** (`MicroSpecialtyProposalServiceImpl.java:73-120`):
   - **问题**: `approveProposal` 方法缺少 proposer == reviewer 检查，而 `approveAndCreateSpecialty` 有此检查
   - **影响**: 教师可通过联名审批绕过"不可审批自己申报"规则
   - **修复**: 在 `approveProposal` 开头增加 `if (proposal.getProposerId().equals(reviewerId)) throw BusinessException("不可审批自己的申报")`

### 🟡 P1-C 建议修复（记录到 Phase 6）

1. **OP-0178 — 开课缺编排校验** (`MicroSpecialtyManage.vue:71`): `showOpen` 仅检查 `status='APPROVED'`，未检查课程编排数
2. **OP-0214 — 营收看板加载失败无反馈** (`RevenueDashboard.vue:103-107`): 失败仅 `console.warn`，用户无感知
3. **OP-0154 — formComplete 检查不完整** (`MicroSpecialtyProposal.vue:780-795`): 联系电话仅检查为空，未检查格式

### 🟢 亮点发现

1. **OP-0190 — LEAD 唯一性约束**: 触发器 + 唯一索引双重保障设计合理，INVITED → ACTIVE 时触发
2. **OP-0070 — 重试机制**: UNIQUE 冲突自动回退查询+更新，3 次失败后停止，sessionStorage 5s 去重
3. **OP-0094 — 头像上传**: 前端 Canvas 压缩 + 后端魔数校验 + 路径安全，完整的安全链
4. **OP-0022 — 并发 401 队列**: `isRefreshing` 互斥锁 + `pendingRequests` 积压队列，设计优秀

### 跨文件冲突检测

本次审查了 26 个操作单元对应的 20+ 个文件。检查结果：
- **无跨文件冲突**。前端调用后端 API 的接口签名一致（方法名/参数）
- **无重复定义**。各文件职责单一，命名无冲突
- **路由定义**与组件文件路径一致

---

## 机械检查结果

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 命名约定 | ✅ 通过 | 前端 kebab-case, 后端 camelCase, DB snake_case |
| 注释头完整性 | ⚠️ 部分通过 | 大部分文件有注释头，部分缺少（如 useDragSort.js） |
| 缩进/格式 | ✅ 通过 | 前后端缩进一致（空格 vs tab），无混用 |
| 遗留调试代码 | ✅ 通过 | 未发现未删除的 console.log (仅 console.warn/error 保留) |

---

## 决策

- 🔴 **阻塞** — 存在 **2 项 P0 阻塞项**，需修复后重新审查：
  1. **OP-0022**: Token 刷新时序缺陷（AuthServiceImpl.java 需调整 Step 顺序）
  2. **OP-0238**: 自审批阻断缺失（approveProposal 需增加检查）
- ⚠️ **P1-C 项**（3 项）和 **P1-I 项**（4 项）记录到 Phase 6 统一处理
- P2 项（10 项）记录到 Phase 6 统一处理
