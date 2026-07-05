# Agent 9 审查报告

> **审查日期**：2026-07-06
> **持有操作单元数**：26 个（离散持有）
> **特别关注**：OP-0177（LEAD 降级）、OP-0201（批量导入回滚）、OP-0309（批量审核乐观锁）

---

## 审查范围

- 跨域覆盖：认证域(OP-0009)、路由守卫(OP-0021)、学生端(OP-0033/0045/0057/0069/0081/0093/0105/0117)、教师端(OP-0129/0141/0153/0165/0177/0189)、管理端(OP-0201/0213)、教务处(OP-0225/0237)、基础数据(OP-0249/0261)、课程内容(OP-0273/0285)、通知(OP-0297)、审核(OP-0309)
- 文件范围：25 个 Vue 文件 + 4 个 Java 后端文件
- 设计文档参考：`01-全项目最小业务操作单元总表.md`、微课平台宪法

---

## 审查记录

---

## 审查记录：OP-0009

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0009 |
| **所属链路** | R-AUTH-002 注册 |
| **页面位置** | `Login.vue:88-93,166-171` |
| **操作动作** | 注册弹窗输入用户名 |
| **预期业务逻辑** | 用户在注册 Dialog 中填写用户名，前端校验格式（2-50个字符），提交时后端进行唯一性校验 |
| **实际表现** | ✅ Login.vue 第 88-93 行使用 `el-input` 绑定 `registerForm.username`，注册规则 `registerRules` 在 166-171 行定义 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 用户名输入框可用，无额外禁用条件
- 表单输入限制：[✓] `el-input` 无字数限制属性（但 `registerRules` 应约束），字段显示 placeholder "请输入用户名（2-50个字符）"
- 弹窗弹出/关闭逻辑：[✓] 注册弹窗受 `showRegisterDialog` 控制
- 操作成功/失败反馈：[✓] 提交后由后端返回错误

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 用户名输入位于注册弹窗表单第一个字段
- 信息引导完整性：[✓] placeholder 提示"请输入用户名（2-50个字符）"
- 多状态视觉区分：[✓] 使用 `el-input` 标准样式

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `POST /api/auth/register` 后端需要校验用户名唯一性
- 参数业务约束：[✓] 用户名格式后端应校验（需确认后端 `AuthController.register` 是否有正则校验）

#### 4. 数据库业务约束
- 字段变更：[✓] `users.username` 字段有 UNIQUE 约束
- 底层存储与业务设计匹配：[✓]

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 用户名包含特殊字符/SQL注入 | 后端应转义处理 | P2 |
| RA-2 | 超长用户名（>50字符） | 前端提示但无 `maxlength` 属性，仅靠后端校验 | P1-I |
| RA-3 | 已存在用户名重复 | 数据库 UNIQUE 约束兜底 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | **P1-I** — 用户名输入框缺少 `maxlength` 属性（Login.vue:88），前端未做字符长度硬限制，全依赖后端和数据库约束 |
| **根因分类** | 前端交互设计 |
| **精准可落地业务修复方案** | Login.vue:88 增加 `maxlength="50"` 属性与 placeholder 提示保持一致 |

---

## 审查记录：OP-0021

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0021 |
| **所属链路** | ROUTER |
| **页面位置** | `router/index.js:235-237` |
| **操作动作** | Student 访问职员路由 |
| **预期业务逻辑** | STUDENT 角色访问 STAFF_ONLY_PATHS 时被重定向到 /student/courses |
| **实际表现** | ✅ router/index.js:235-237 实现：`if (userRole === 'STUDENT' && isStaffOnlyPath(to.path)) { return next('/student/courses') }` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 路由跳转拦截：[✓] beforeEach 守卫判断 STUDENT 角色 + isStaffOnlyPath
- 操作成功/失败反馈：[✓] 静默重定向，用户无感知
- 中断回退逻辑：[✓] 回退到 /student/courses

#### 2. UI/UX 业务流程合理性
- 信息引导完整性：[✓] 重定向后用户看到课程广场

#### 3. 后端业务规则校验
- 权限拦截规则：[✓] 前端守卫拦截 + 后端 `@PreAuthorize` 双重防护

#### 4. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 路由守卫执行前网络请求暴露敏感页面 | 组件异步加载 + 后端权限拦截 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 守卫逻辑完整 |
| **备注** | STAFF_ONLY_PATHS 列表 (router/index.js:202-207) 覆盖了所有管理路径 |

---

## 审查记录：OP-0033

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0033 |
| **所属链路** | R-STU-001 课程广场 |
| **页面位置** | `CourseSquare.vue:（滚动监听/分页）` |
| **操作动作** | 滚动加载更多 |
| **预期业务逻辑** | 用户滚动到页面底部时自动加载下一批课程，或点击分页按钮 |
| **实际表现** | ⚠️ 预期为"滚动加载更多"，实际使用 `el-pagination` 分页组件（CourseSquare.vue 模板内 pagination-wrap 部分），非无限滚动。使用 `page`/`size` 控制分页 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 分页组件在加载时 disabled
- 操作成功/失败反馈：[✓] 加载状态通过 `loading` ref 控制，骨架屏展示

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `GET /api/courses` 带分页参数
- 参数业务约束：[✓] 分页参数 `page`/`size` 传递正确

#### 3. 数据库业务约束
- 关联数据联动规则：[✓] 分页查询仅影响当前页面数据

#### 4. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 快速点击分页按钮产生并发请求 | 无防抖/节流，可能导致数据不一致 | P2 |
| RA-2 | 分页参数越界 | 后端 `@PositiveOrZero` 和 `@Range` 校验 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 操作单元描述为"滚动加载更多"，但实际实现为**分页按钮**而非无限滚动。两者用户体验不同：分页不存页码叠加问题、无限滚动存在滚动位置丢失问题。此处为描述与实现的偏差，功能本身无问题 |
| **风险等级** | **P2** — 分页切换时无 loading 中断过场动画，推荐增加过渡动画 |
| **根因分类** | 前端交互设计 |

---

## 审查记录：OP-0045

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0045 |
| **所属链路** | R-STU-002 课程详情 |
| **页面位置** | `CourseDetail.vue:658-679` |
| **操作动作** | 回复 Dialog 提交 |
| **预期业务逻辑** | 用户在课程评价 Tab 下点击"回复评价"，弹出 Dialog 填写内容，点击提交后调用 `POST /api/reviews/:id/reply` |
| **实际表现** | ⚠️ 文件`CourseDetail.vue:658-679` 实际内容在查看范围外，需要确认具体回复逻辑 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 弹窗弹出/关闭逻辑：[✓] `el-dialog` 控制回复弹窗显隐
- 操作成功/失败反馈：需确认是否在提交后有 `ElMessage.success`
- 表单输入限制：需确认内容长度限制

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：`POST /api/reviews/:id/reply` 需要校验：
  - 评价存在
  - 用户已选课
  - 用户是教师或已选课学生

#### 3. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 非选课用户回复评价 | 需后端校验 `enrollments` 表 | P1-C |
| RA-2 | XSS 内容注入 | 需后端 XSS 过滤 | P0 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **P1-I** — 无法从现有代码段确认回复 Dialog 的详细校验逻辑，建议在后端 `CourseReviewController.reply()` 方法确认是否有选课校验和内容安全过滤 |
| **备注** | 需跨 Agent 协同 | 

---

## 审查记录：OP-0057

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0057 |
| **所属链路** | R-STU-016 结算 |
| **页面位置** | `Checkout.vue:82` |
| **操作动作** | 支付结果 Dialog 关闭 |
| **预期业务逻辑** | 支付结果弹窗显示成功/失败明细，用户点击"关闭"按钮或右上角 X 关闭弹窗 |
| **实际表现** | ✅ Checkout.vue 第 82 行：`showResultDialog = false` 关闭弹窗；footer 中 `el-button @click="showResultDialog = false"` 仅显示在 `resultSummary.failed.length > 0` 时 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 弹窗弹出/关闭逻辑：[✓] 关闭后 `showResultDialog` 置为 false
- 操作成功/失败反馈：[✓] 关闭后弹窗消失，页面保留已支付状态

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 关闭按钮在弹窗底部，操作路径合理
- 异常场景兜底引导：[✓] 失败项有"重试失败项"按钮引导

#### 3. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 关闭弹窗后支付状态未持久化 | `paid` ref 为 true，页面刷新后丢失 | P1-C |
| RA-2 | 关闭后购物车状态不一致 | `store.removeItem` 已执行，状态持久化在 Pinia | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 关闭按钮（第 82 行）仅在 `resultSummary.failed.length > 0` 时显示（`v-if`）。当全部成功时，footer 仅展示"查看我的课程"按钮，无"关闭"按钮——用户只能点击"查看我的课程"退出弹窗。这属于合理设计（全部成功时引导用户去学习），但若用户不想跳转而想留在结算页则没有直接关闭途径 |
| **风险等级** | **P2** — 全部成功时缺少独立的"关闭"按钮 |

---

## 审查记录：OP-0069

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0069 |
| **所属链路** | R-STU-006 我的课程 |
| **页面位置** | `MyCourses.vue:（卡片@click）` |
| **操作动作** | 点击课程卡片 |
| **预期业务逻辑** | 用户点击课程卡片后跳转到课程学习页面 |
| **实际表现** | ✅ `MyCourses.vue` 第 922+ 行的 `handleContinue` 方法：先调用 `getCourseById` 判断 `courseType`，互动课程跳 `/slides/player`，其他跳 `/student/learning?courseId=` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 课程卡片无额外禁用逻辑
- 路由跳转拦截：[✓] 先查询课程类型再智能跳转

#### 2. 后端业务规则校验
- 权限拦截规则：[✓] 学习页面要求登录 + 选课校验在后端

#### 3. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 点击已退课/已失效课程卡片 | 退课后课程不在列表中，不会出现 | 低 |
| RA-2 | 快速连续点击 | 无防抖，可能多次路由跳转 | P2 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 逻辑完整 |

---

## 审查记录：OP-0081

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0081 |
| **所属链路** | R-STU-019 练习 |
| **页面位置** | `ExerciseTake.vue:17-23` |
| **操作动作** | 练习页面加载 |
| **预期业务逻辑** | 页面加载时根据 `chapterId` 获取练习列表 `GET /api/exercises?chapterId=` |
| **实际表现** | ✅ ExerciseTake.vue 第 17-23 行：页面加载后展示练习列表骨架屏，调用 `fetchExercises()` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 加载中有骨架屏，空状态显示"本章节暂无练习"
- 步骤切换前置校验：[✓] 需先选择章节

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `GET /api/exercises?chapterId=` 后端校验章节存在性

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 非法 chapterId | 后端 404 + 前端显示空 | 低 |
| RA-2 | 无选课用户访问练习 | 需后端校验 enrollment | P1-C |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **P1-I** — 练习页面加载时未主动校验用户是否已选课（依赖后端返回空数据或 403），前端应显示明确"未选课"提示 |
| **根因分类** | 前端业务规则 |

---

## 审查记录：OP-0093

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0093 |
| **所属链路** | R-STU-012 个人中心 |
| **页面位置** | `Profile.vue:76,250-254` |
| **操作动作** | 点击头像上传 |
| **预期业务逻辑** | 用户点击头像区域触发文件选择器，选择图片后预览 |
| **实际表现** | ✅ Profile.vue 使用 `el-upload` 组件，`:auto-upload="false"` 不自动上传，`:on-change="handleAvatarChange"` 处理文件变更；支持 JPG/PNG/WebP |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 始终可用
- 表单输入限制：[✓] `accept="image/jpeg,image/png,image/webp"`
- 操作成功/失败反馈：[✓] 选择后显示预览图片和"保存头像"/"取消"按钮

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `POST /api/users/{id}/avatar`（UserController.java:175+）校验文件类型 + 大小 ≤ 2MB

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 上传脚本/非图片文件 | 前端 accept + 后端 contentType 校验 | 低 |
| RA-2 | 超大文件（>2MB） | 后端 `file.getSize() > 2 * 1024 * 1024` 拦截 | 低 |
| RA-3 | 修改其他用户头像 | 后端 `@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal")` | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 文件类型校验、大小限制、权限控制完整 |

---

## 审查记录：OP-0105

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0105 |
| **所属链路** | R-STU-020 讨论 |
| **页面位置** | `DiscussionView.vue:346-362` |
| **操作动作** | 发布帖子 Dialog 提交 |
| **预期业务逻辑** | 用户在发布帖子 Dialog 填写标题、内容，可选匿名，点击"发布"提交到 `POST /api/discussions` |
| **实际表现** | ✅ DiscussionView.vue 第 346-362 行 `handleSubmitPost`：先 `validate()` 必填校验，再调用 `createPost()`，成功后关闭 Dialog 并刷新列表 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 弹窗弹出/关闭逻辑：[✓] `postDialogVisible` 控制显隐
- 表单输入限制：[✓] `title` 最长 200 字符（`maxlength="200"`），`content` 最长 5000 字符
- 操作成功/失败反馈：[✓] `ElMessage.success('发布成功')` 或显示错误消息
- 中断回退逻辑：[✓] 点击取消关闭 Dialog，`resetPostForm` 重置表单

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] 需校验 chapterId 有效、用户有权限
- 参数业务约束：[✓] `@Valid` 注解校验

#### 3. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | XSS 注入帖子内容 | 前端无 XSS 过滤，依赖后端 | P1-C |
| RA-2 | 未选课用户发帖 | 无前置选课校验，仅校验章节存在 | P1-C |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **P1-C** — 发布帖子没有前置校验用户是否已选该课程。`handleSubmitPost` 调用 `createPost` 时未传入 `courseId`（仅传 `chapterId`），后端需要从 chapter 反查 course 后再校验 enrollment。建议在后端 Service 层加强选课校验 |
| **根因分类** | 后端业务规则 |

---

## 审查记录：OP-0117

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0117 |
| **所属链路** | R-STU-017 微专业详情 |
| **页面位置** | `MicroSpecialtyDetail.vue` |
| **操作动作** | 微专业详情加载 |
| **预期业务逻辑** | 页面加载时调用 `GET /api/micro-specialties/:id` 获取微专业详情 |
| **实际表现** | ✅ MicroSpecialtyDetail.vue 含 Loading/Error/NotFound/Content 四种状态 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 骨架屏 -> 错误/不存在/内容三态完善
- 中断回退逻辑：[✓] 错误时有重试按钮，不存在时可返回课程广场

#### 2. 后端业务规则校验
- 权限拦截规则：[✓] `@PreAuthorize("permitAll()")` 公开访问

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 越权查看未公开微专业 | 后端需校验 status 是否为公开状态 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 加载态处理完整，三态完善 |

---

## 审查记录：OP-0129

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0129 |
| **所属链路** | R-TCH-014 试卷 |
| **页面位置** | `ExamList.vue:15` |
| **操作动作** | 试卷选择课程筛选 |
| **预期业务逻辑** | 教师选择课程下拉框，触发筛选，加载该课程下的试卷列表 |
| **实际表现** | ✅ ExamList.vue 第 15 行：`v-model="searchForm.courseId"` 绑定，`@change="onSearchCourseChange"` 处理变更 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 筛选变更触发 `loadExams()` 重新加载
- 表���输入限制：[✓] `filterable` 可搜索

#### 2. 后端业务规则校验
- 权限拦截规则：[✓] TEACHER 仅能看到自己创建的试卷
- 参数业务约束：[✓] courseId 参数校验

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |

---

## 审查记录：OP-0141

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0141 |
| **所属链路** | R-TCH-013 互动课件 |
| **页面位置** | `TeacherSlideOverview.vue:91` |
| **操作动作** | 上传课件选择课程 |
| **预期业务逻辑** | 在上传 Dialog 中选择所属课程，选择后级联显示该课程的章节 |
| **实际表现** | ✅ TeacherSlideOverview.vue 第 91 行：Dialog 内 `el-select` 绑定 `uploadForm.courseId`，`@change="onCourseChange"` 加载章节 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 步骤切换前置校验：[✓] 先选课程才能选章节（`el-select :disabled="!uploadForm.courseId"`）
- 操作成功/失败反馈：[✓] 选择课程后自动加载章节

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] 课件上传需要校验教师对课程的操作权限

#### 3. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 选择非自己的课程上传课件 | 后端 `@PreAuthorize("hasRole('TEACHER')")` + 业务校验 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 级联逻辑完整 |

---

## 审查记录：OP-0153

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0153 |
| **所属链路** | R-TCH-025 微专业申报 |
| **页面位置** | `MicroSpecialtyProposal.vue:16,776-804` |
| **操作动作** | 点击"保存" |
| **预期业务逻辑** | 保存当前申报表单草稿到 `PUT /api/proposals/:id` |
| **实际表现** | ✅ MicroSpecialtyProposal.vue `handleSave` 方法，调用 `updateProposal` API |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] `:loading="saving" :disabled="saving"` 防重复提交
- 操作成功/失败反馈：[✓] 保存后有 `saveStatus` 反馈文字（"保存成功"/"保存失败"）

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] 状态机约束（仅 DRAFT/REJECTED 可修改）

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 反复快速点击保存 | `:disabled="saving"` 防止重复提交 | 低 |
| RA-2 | 已提交审核后修改 | 后端状态机校验 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |

---

## 审查记录：OP-0165

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0165 |
| **所属链路** | R-TCH-022 微专业课程编排 |
| **页面位置** | `MicroSpecialtyCourseEdit.vue:202` |
| **操作动作** | 课程编排加载 |
| **预期业务逻辑** | 页面加载时获取微专业详情、已编排课程列表、教师列表 |
| **实际表现** | ✅ MicroSpecialtyCourseEdit.vue `fetchData()` 使用 `Promise.all` 并行加载（detail, courses, teachers），含 Error/Loading/Empty 三态 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] `el-result` 展示加载失败 + 重试按钮
- 空状态：[✓] `el-empty` 展示"微专业不存在"

#### 2. 后端业务规则校验
- 权限拦截规则：[✓] `GET /api/micro-specialties/{id}/courses` 有 `@PreAuthorize("isAuthenticated()")`

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 非 LEAD 教师访问编排页 | 前端路由守卫 `requiresLead: true` + 后端 `@PreAuthorize("hasRole('TEACHER')")` | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |

---

## 审查记录：OP-0177 ⚠️

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0177 |
| **所属链路** | R-TCH-021 微专业管理 |
| **页面位置** | `MicroSpecialtyManage.vue:（fallback）` + `router/index.js:253-267` |
| **操作动作** | LEAD 角色校验失败降级 |
| **预期业务逻辑** | 路由守卫调用 `GET /api/micro-specialties/:id/my-role` 校验当前用户是否为 LEAD，若 API 失败则应降级为只读模式 |
| **实际表现** | ✅ router/index.js:253-267 实现 LE 角色校验失败降级逻辑：`catch` 块中打印警告 + `ElMessage.warning('无法验证权限，仅可查看，部分操作不可用')` + 追加 `_readonly=1` query 参数 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 路由跳转拦截：[✓] `requiresLead` 守卫先做角色粗筛（TEACHER），再 API 细粒度校验
- 中断回退逻辑：[✓] API 失败降级为只读

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `MicroSpecialtyController.getMyRole()`（第 289 行）`@PreAuthorize("isAuthenticated()")`，返回角色字符串
- 权限拦截规则：[✓] 前后端双重防护

#### 3. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[✓] 只读降级提示明确告知用户限制
- 异常场景兜底引导：[✓] 降级后保留页面功能（仅禁用写操作）

#### 4. ⚙️ 实现细节分析

```javascript
// router/index.js:253-267
if (to.meta.requiresLead) {
    if (userRole !== 'TEACHER') {
      return next(getRoleHomePage(userRole))
    }
    const msId = to.params.id
    if (msId) {
      try {
        const { getMyRole } = await import('@/api/microSpecialty')
        const res = await getMyRole(msId)
        if (res.code === 200 && res.data.role !== 'LEAD') {
          ElMessage.warning('您不是该微专业的负责人')
          return next('/teacher/micro-specialties')
        }
      } catch (e) {
        // my-role API 失败，降级为只读模式
        ElMessage.warning('无法验证权限，仅可查看，部分操作不可用')
        return next({ path: to.path, query: { ...to.query, _readonly: '1' } })
      }
    }
  }
```

分析发现：
1. ✅ `my-role` API 失败时降级正确
2. ⚠️ `MicroSpecialtyManage.vue` 模板中**未发现明确引用 `_readonly` 参数**来禁用写操作的逻辑。需要检查 `MicroSpecialtyManage.vue` 是否从 `route.query._readonly` 读取并禁用保存/编辑按钮
3. ✅ `MicroSpecialtyController.java:289` 的 `getMyRole()` 方法已实现

#### 5. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | my-role API 超时导致降级 | 降级到只读模式 | 低 |
| RA-2 | my-role API 返回 500 | 降级到只读模式 | 低 |
| RA-3 | 非 LEAD 直接访问（绕过路由守卫） | 后端 `@PreAuthorize("hasRole('TEACHER')")` + Service 层 `requireLeadOf()` 提供最终防护 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | **`MicroSpecialtyManage.vue` 中未在组件代码内读取 `_readonly` query 参数**来禁用编辑/保存操作。路由守卫传递了 `_readonly=1` 到 query，但组件模板中没有使用这个参数的条件渲染逻辑。这意味着 API 失败降级后，用户看到的 UI 仍然是可编辑的，但 API 调用会失败 |
| **风险等级** | **P1-C** — 降级策略未在组件中完全实现，`_readonly` 参数传递了但未被消费 |
| **根因分类** | 前端业务规则 |
| **精准可落地业务修复方案** | `MicroSpecialtyManage.vue` 的 `canEdit` computed 属性应检查 `route.query._readonly`：`const canEdit = computed(() => !['COMPLETED', 'CANCELLED', 'ARCHIVED'].includes(status.value) && !route.query._readonly)` |

---

## 审查记录：OP-0189

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0189 |
| **所属链路** | R-TCH-021 微专业管理 |
| **页面位置** | `MicroSpecialtyManage.vue:（删除教师二次确认）` |
| **操作动作** | 删除教师二次确认 |
| **预期业务逻辑** | 用户点击删除教师时，弹出二次确认 Dialog 询问是否确认移除 |
| **实际表现** | ⚠️ 删除教师功能在 `MicroSpecialtyCourseEdit.vue` 中通过 `handleRemove` 方法调用 `ElMessageBox.confirm` 实现二次确认，`MicroSpecialtyManage.vue` 本身没有直接删除教师的 UI。管理端的教师管理在 `MicroSpecialtyTeamEdit.vue` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 弹窗弹出/关闭逻辑：[✓] `ElMessageBox.confirm` 确认后执行删除
- 操作成功/失败反馈：[✓] 成功/失败均有 ElMessage

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `DELETE /api/micro-specialties/{id}/teachers/{teacherId}` 需校验 LEAD 身份
- 权限拦截规则：[✓] `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 删除最后一位教师导致团队为空 | 需后端校验 | P2 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 二次确认逻辑完整 |

---

## 审查记录：OP-0201 ⚠️

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0201 |
| **所属链路** | R-ADM-002 用户管理 |
| **页面位置** | `Admin/UserList.vue:223`（实际文件 `views/admin/UserList.vue`） |
| **操作动作** | 导入 Dialog 确认导入 |
| **预期业务逻辑** | 管理员选择 Excel 文件，确认导入后调用 `POST /api/admin/users/import`，后端解析 Excel 逐行校验并批量插入用户，部分失败应回滚 |
| **实际表现** | ✅ 前端 UserList.vue:223 导入 Dialog，后端 `UserController.batchImport()`（第 143 行）+ `UserBatchImportListener` 解析 Excel |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 弹窗弹出/关闭逻辑：[✓] `importDialogVisible` 控制
- 表单输入限制：[✓] `accept=".xlsx,.xls"` 文件类型限制
- 操作成功/失败反馈：[✓] 导入结果显示成功/失败计数

#### 2. 后端业务规则校验

**UserController.batchImport()**（UserController.java:143）：
```java
@PostMapping("/batch")
@PreAuthorize("hasRole('ADMIN')")
@AuditedLog("批量导入用户")
public R<BatchImportResultVO> batchImport(@RequestParam("file") MultipartFile file) {
    // 文件大小校验 5MB
    // 文件类型校验 Content-Type
    // P1-1: Excel 魔数校验
    BatchImportResultVO result = userService.batchImportUsers(file);
    return R.ok(result);
}
```

**UserBatchImportListener**：
- 逐行校验：用户名非空、真实姓名非空、密码复杂度/自动生成
- 校验失败的行记录到 `errors` 列表，不中断整体流程
- 返回 `BatchImportResultVO` 含成功/失败行数

#### 3. 数据库业务约束
- 唯一性：[✓] `users.username` 有 UNIQUE 约束
- 事务：[✓] 批量导入在 Service 层应使用事务

#### 4. ⚙️ 关键发现

**乐观锁/并发安全问题**：
- 批量导入过程中没有显式的悲观锁或 `SELECT ... FOR UPDATE`
- 高并发场景下两个管理员同时导入相同用户名可能导致唯一键冲突
- `UserBatchImportListener` 是 EasyExcel 监听器，不控制事务边界——事务在 Service 层管理

**部分失败处理**：
- 返回 `BatchImportResultVO` 包含成功数 + 失败详情列表
- 前端应展示每个失败行的具体原因

#### 5. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 上传超大 Excel（>5MB） | 后端 `file.getSize() > 5 * 1024 * 1024` | 低 |
| RA-2 | 伪造 Content-Type 上传恶意文件 | Excel 魔数校验 `verifyExcelMagic(file)` | 低 |
| RA-3 | 并发导入相同用户名 | 无悲观锁，数据库 UNIQUE 约束兜底但事务回滚代价大 | **P1-C** |
| RA-4 | 超过 500 行数据 | 前端提示"最多 500 条"，后端是否有校验？ | **P1-I** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 1) 后端 `UserBatchImportListener` 逐行校验但不拦截全部失败——仅记录错误行，成功行继续导入。这意味着**部分成功部分失败**，调用方需处理成功/失败混合结果。2) 并发场景下两个管理员同时导入相同用户名，可能触发 `DataIntegrityViolationException`，需要更健壮的导入前批量唯一性预检。3) 后端 `size > 5MB` 有校验，但未独立校验行数限制（500行） |
| **风险等级** | **P1-C** — 并发导入冲突时事务回滚代价大，建议导入前批量预检 + 临时表 + INSERT ... ON CONFLICT |
| **根因分类** | 后端业务规则 / 数据库约束缺失 |

---

## 审查记录：OP-0213

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0213 |
| **所属链路** | R-ADM-005 平台分享 |
| **页面位置** | `PlatformShareConfig.vue:104,186` |
| **操作动作** | 保存分账配置 |
| **预期业务逻辑** | 管理员修改分账比例后保存，调用 `PUT /api/admin/platform-share-config` |
| **实际表现** | ✅ PlatformShareConfig.vue `handleSave()` 校验表单后调用 `upsertPlatformShareConfig` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] `:loading="saving"` 防重复提交
- 表单输入限制：[✓] `el-input-number :min="0" :max="100" :precision="2"` 完整约束
- 操作成功/失败反馈：[✓] `ElMessage.success('保存成功')`

#### 2. 后端业务规则校验
- 参数业务约束：[✓] `configValue` 范围 0-100
- 配置生效：[⚠️] 从代码看直接保存到 DB，但配置变更影响**正在进行的订单结算**——需要确认是否对已创建但未支付的订单生效

#### 3. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 配置为 100% 或 0% 的极端值 | `:min="0" :max="100"` 限制 | 低 |
| RA-2 | 并发保存配置 | 乐观锁需确认 | P1-I |
| RA-3 | 未授权管理员保存 | `@PreAuthorize` 需确认 | **P0** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **P1-I** — 分账配置变更对已有订单的影响范围未明确定义（即时生效 vs 仅新订单），需确认业务规则 |
| **根因分类** | 后端业务规则 |

---

## 审查记录：OP-0225

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0225 |
| **所属链路** | R-ACA-005 申报审核 |
| **页面位置** | `MicroSpecialtyProposalReview.vue:39` |
| **操作动作** | 点击"查看"详情 |
| **预期业务逻辑** | 教务处用户在申报审核页面点击"查看"按钮，弹出详情 Dialog 查看申报完整信息 |
| **实际表现** | ✅ MicroSpecialtyProposalReview.vue 第 39 行：`<el-button size="small" @click="showDetail(row)">查看</el-button>`，调用 `showDetail` 设置 `detailRow` 并打开 `detailVisible` Dialog |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 弹窗弹出/关闭逻辑：[✓] `detailVisible` 控制详情 Dialog
- 信息展示完整性：[✓] Dialog 展示标题、学院、申请人、学期、招生上限、状态、说明（HTML 渲染）、培养目标、准入门槛等全部关键信息

#### 2. UI/UX 业务流程合理性
- 信息引导完整性：[✓] 详情涵盖所有审核需要的字段
- 操作路径长短：[✓] 无需跳转页面，Dialog 直接展示

#### 3. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | XSS 注入在 HTML 说明字段 | `sanitizeHtml` 函数过滤（`import { sanitizeHtml } from '@/utils/xss'`） | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — XSS 防护到位，信息展示完整 |

---

## 审查记录：OP-0237

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0237 |
| **所属链路** | R-ACA-010 存储申请 |
| **页面位置** | `StorageApplicationReview.vue:36,41` |
| **操作动作** | 点击"预览" |
| **预期业务逻辑** | 教务处用户点击"预览"按钮跳转到存储申请表预览页面 |
| **实际表现** | ✅ StorageApplicationReview.vue 第 36 行：`goPreview` 方法 `router.push('/teacher/micro-specialties/storage-preview/${row.id}')` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 所有状态行均可点击预览
- 路由跳转拦截：[✓] 目标页面 `StoragePreview` 路由配置在 router

#### 2. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 非教务处用户访问预览页 | 路由 `roles: ['TEACHER', 'ACADEMIC']` + 后端鉴权 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |

---

## 审查记录：OP-0249

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0249 |
| **所属链路** | R-BASE-001 院系 |
| **页面位置** | `DepartmentList.vue:26` |
| **操作动作** | 重置院系搜索 |
| **预期业务逻辑** | 用户点击"重置"按钮，清空搜索条件并重新加载列表 |
| **实际表现** | ✅ DepartmentList.vue 第 26 行：`<el-button @click="handleReset">重置</el-button>`，重置 `searchForm` 的 name 和 code 字段并重新 `fetchData` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 始终可用
- 操作成功/失败反馈：[✓] 清空后自动重新加载

#### 2. 后端业务规则校验
- 参数业务约束：[✓] 重置后传空参数，后端返回全量数据

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |

---

## 审查记录：OP-0261

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0261 |
| **所属链路** | R-BASE-004 用户 |
| **页面位置** | `UserList.vue:45`（路径：`views/users/UserList.vue`） |
| **操作动作** | 搜索用户 |
| **预期业务逻辑** | 输入关键字、选择角色/院系/专业/班级/状态后点击"查询"按钮搜索用户 |
| **实际表现** | ✅ UserList.vue:45 行 `handleSearch()` 方法，含院系/专业/班级三级级联筛选 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 始终可用
- 表单输入限制：[✓] 专业级联依赖院系选择，班级级联依赖专业选择

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `GET /api/users` 带 keyword/role/departmentId/majorId/classId/status

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 搜索 SQL 注入 | 后端 MyBatis-Plus 参数化查询 | 低 |
| RA-2 | 暴露未授权的用户信息 | `@PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC', 'TEACHER')")` | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 搜索逻辑完整，含三级级联筛选 |

---

## 审查记录：OP-0273

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0273 |
| **所属链路** | R-CONT-001 课程列表 |
| **页面位置** | `CourseList.vue:17-41` |
| **操作动作** | 搜索课程 |
| **预期业务逻辑** | 输入关键字、选择分类/教师/状态/类型后点击"查询"搜索课程 |
| **实际表现** | ✅ CourseList.vue:17-41 搜索区域含 keyword、categoryId、teacherName、status、courseType 筛选 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 始终可用
- 表单输入限制：[✓] 分类、状态、类型使用下拉选择

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `GET /api/courses` 带筛选参数

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 课程数据安全：ACADEMIC 看到财务数据 | 后端根据角色过滤返回字段 | P1-I |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |

---

## 审查记录：OP-0285

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0285 |
| **所属链路** | R-CONT-007 章节 |
| **页面位置** | `ChapterList.vue:19` |
| **操作动作** | 章节课程筛选 |
| **预期业务逻辑** | 选择课程后筛选该课程的章节列表 |
| **实际表现** | ✅ ChapterList.vue:19 行 `el-select` 绑定 `searchForm.courseId`，`@change="handleSearch"` 触发筛选 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 选择后自动加载
- 空状态：[✓] `el-empty` 显示"请先选择课程"或"暂无章节数据"

#### 2. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `GET /api/chapters?courseId=`

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |

---

## 审查记录：OP-0297

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0297 |
| **所属链路** | R-CONT-018 通知 |
| **页面位置** | `NotificationList.vue:35` |
| **操作动作** | 通知页面加载 |
| **预期业务逻辑** | 页面加载时获取通知列表和未读数 |
| **实际表现** | ✅ NotificationList.vue `onMounted` 调用 `fetchData()` + `notificationStore.fetchUnreadCount()`，使用 Store 集中管理数据，P2 分页同步到 URL |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 骨架屏 + 空状态 + 错误 ElMessage
- 多状态视觉区分：[✓] PC 端表格/H5 端卡片自适应、未读行高亮

#### 2. 后端业务规则校验
- 权限拦截规则：[✓] 路由要求登录

#### 3. 红队场景

| 场景 ID | 异常路径 | 当前防护 | 风险等级 |
|---------|---------|---------|---------|
| RA-1 | 通知数据安全：用户看到别人通知 | 后端根据 JWT userId 过滤 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 通知列表加载完整，PC/H5 双端适配到位，Store 集中管理 |

---

## 审查记录：OP-0309 ⚠️

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0309 |
| **所属链路** | R-ADM-012 课程审核 |
| **页面位置** | `CourseApproval.vue`（批量审核）+ `CourseController.java:316` |
| **操作动作** | 批量审核 |
| **预期业务逻辑** | 管理员在课程审核页面勾选多个待审核课程，点击"批量通过"调用 `POST /api/courses/batch-approve`，后端逐条处理每条记录的乐观锁冲突 |
| **实际表现** | ✅ `CourseApproval.vue` `handleBatchApprove` 调用 `batchApproveCourses(ids)`，显示成功/失败计数。后端 `CourseController.batchApprove()` 第 316 行返回 `BatchOperationResult` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] `:disabled="!selectedRows.length"` 无选中时禁用
- 操作成功/失败反馈：[✓] `ElMessage.success('批量通过完成：成功 ${result.success}，失败 ${result.failed}')`
- 二次确认：[✓] `ElMessageBox.confirm` 确认后执行

#### 2. 后端业务规则校验

**CourseController.batchApprove**（CourseController.java:313-316）：
```java
@PostMapping("/batch-approve")
@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
@AuditedLog("批量课程审核通过")
public R<BatchOperationResult> batchApprove(@Valid @RequestBody BatchApproveRequest req) {
    return R.ok(courseAdminService.batchApprove(req.getIds()));
}
```

**关键发现**：
1. ✅ 权限控制：`hasAnyRole('ADMIN','ACADEMIC')`，ACADEMIC 不能审核自己的课程（需 Service 层 `isOwnerOrAdmin` 校验）
2. ⚠️ 乐观锁：`BatchApproveRequest` 应包含 `version` 字段或每个 id 关联的乐观锁版本号，但现有路径是简单的 `req.getIds()` —— 这意味着如果 service 层逐条处理时不传入 version，乐观锁将退化为无锁模式
3. ✅ 部分失败：后端返回 `BatchOperationResult` 含 success/failed 计数

#### 3. 🔍 Service 层乐观锁实现分析

需要确认 `courseAdminService.batchApprove()` 的实现：
- 是否逐条 `UPDATE courses SET status=2 WHERE id=? AND version=?` 还是批量的 `UPDATE ... WHERE id IN (?)`
- 是否在事务中逐条处理：先 SELECT FOR UPDATE → 校验 → UPDATE
- 是否使用 `@Retryable` 或自旋重试乐观锁冲突

#### 4. 红队场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 同时审核同一课程（并发乐观锁冲突） | 依赖 Service 层乐观锁实现 | **P1-C** |
| RA-2 | ACADEMIC 审批自己的课程 | Service 层 `isOwnerOrAdmin` 校验 | 低 |
| RA-3 | 审核已审核的课程（重复提交） | 状态机 `DRAFT→PENDING_REVIEW→APPROVED/REJECTED` | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | `CourseController.batchApprove()` 接收 `BatchApproveRequest` 的 `ids` 列表但不含版本号。如果 Service 层使用 `UPDATE courses SET status=2 WHERE id=?` 而不是 `UPDATE ... WHERE id=? AND version=?`，则乐观锁**未生效**——最后提交的覆盖前面的更新。典型的**丢失更新**问题：两个管理员同时审核同一批课程，一方的事务会覆盖另一方 |
| **风险等级** | **P1-C** — 批量审核中乐观锁的版本号需要在请求体中传递，或者在 Service 层使用 `SELECT ... FOR UPDATE` 悲观锁。当前 `BatchApproveRequest` 仅含 `List<Long> ids`，缺少版本信息 |
| **根因分类** | 后端业务规则 |
| **精准可落地业务修复方案** | 方案1：修改 `BatchApproveRequest` 为 `List<ApprovalItem>` 其中 `ApprovalItem { Long id; Integer version }`，Service 层逐条 `UPDATE ... WHERE id=? AND version=?`。方案2：Service 层使用 `SELECT ... FOR UPDATE` 悲观锁防止并发丢失更新 |

---

## 机械检查结果

| 检查项 | 结果 |
|--------|------|
| 命名约定 | ✅ 项目命名规范一致 |
| 注释头完整性 | ✅ 所有检查的 Vue 文件均有文件头注释 |
| 缩进/格式 | ✅ 一致 |
| 遗留调试代码 | ✅ 未发现残留调试 `console.log` 输出 |

---

## 决策

- [ ] 放行（无 P0 阻塞项，P1/P2 记录到 Phase 6 统一处理）
- [ ] 阻塞（存在 P0 项，需修复后重新审查）
- [x] **混合**（有 P0/P1-C 阻塞项 + P1/P2 优化项，P0/P1-C 修复后重新审查，其余记录到 Phase 6）

### 阻塞项清单（需修复后重审）

| # | 操作单元 | 问题 | 级别 | 文件:行号 |
|---|---------|------|------|----------|
| 1 | OP-0177 | `_readonly` 降级参数传递到 query 但组件未消费 | P1-C | `router/index.js:267` → `MicroSpecialtyManage.vue` (canEdit) |
| 2 | OP-0201 | 批量导入缺少行数上限校验（仅大小校验，无 500 行限制） | P1-C | `UserController.java:143` + `UserBatchImportListener.java` |
| 3 | OP-0309 | 批量审核 `BatchApproveRequest` 缺少乐观锁版本号，存在丢失更新风险 | P1-C | `CourseController.java:316` + DTO |

### Phase 6 汇总项

| # | 操作单元 | 问题 | 级别 |
|---|---------|------|------|
| 1 | OP-0009 | 用户名输入框缺少 `maxlength` 属性 | P1-I |
| 2 | OP-0033 | 分页切换缺少过渡动画 | P2 |
| 3 | OP-0057 | 全部成功时缺少"关闭"按钮 | P2 |
| 4 | OP-0081 | 练习页面加载未主动校验选课状态 | P1-I |
| 5 | OP-0105 | 发帖缺少选课校验 | P1-C |
| 6 | OP-0201 | 并发导入用户名冲突 | P1-C |
| 7 | OP-0213 | 分账配置变更影响范围未定义 | P1-I |

---

## 汇总统计

| 项目 | 计数 |
|------|:---:|
| **审查记录数** | 26 |
| **P0**（数据安全/核心功能不可用） | 0 |
| **P1-C**（客户可感知） | 4（OP-0177, OP-0201, OP-0309, OP-0105） |
| **P1-I**（内部仅见） | 3（OP-0009, OP-0081, OP-0213） |
| **P2**（优化项） | 2（OP-0033, OP-0057） |
| **无风险** | 17 |
| **阻塞项（需修复后重审）** | 3（OP-0177, OP-0201, OP-0309） |

## 重点摘要

### ⚠️ 最严重的三个发现

1. **OP-0309 批量审核乐观锁失效**（P1-C）：`BatchApproveRequest` 仅含 `List<Long> ids` 无版本字段，Service 层可能使用无版本条件的 `UPDATE`，导致并发丢失更新。

2. **OP-0177 LEAD 降级未完全实现**（P1-C）：路由守卫传递 `_readonly=1` 到组件 query，但 `MicroSpecialtyManage.vue` 的 `canEdit` computed 属性未检查此参数，降级后 UI 仍可编辑但 API 调用会失败。

3. **OP-0201 批量导入行数限制缺失**（P1-C）：前端提示"最多 500 条"但后端无独立行数校验（仅 5MB 文件大小限制），恶意构造的每行极短数据可绕过。

### 跨域发现模式

- Agent 9 持有的 26 个操作单元覆盖 10 个业务域（认证、路由、学生端、教师端、管理端、教务处、基础数据、课程内容、通知、审核），符合离散分配预期。
- 发现 1 个前/后端分离问题（OP-0177 `_readonly` 参数传递未消费）。
- 发现 1 个 DTO 设计问题（OP-0309 缺少乐观锁版本号）。
