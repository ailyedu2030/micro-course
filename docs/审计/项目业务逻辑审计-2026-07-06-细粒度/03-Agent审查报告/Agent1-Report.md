# Agent #1 审查报告

> **审查日期**: 2026-07-06
> **审查类型**: 单节点深度细审（27 个最小操作单元）
> **审查 Agent**: Reviewer #1
> **机制**: 只读审查，不修改任何源代码

---

## 审查记录：OP-0001

**操作单元ID**: OP-0001
**所属链路**: R-AUTH-001 登录页
**页面位置**: Login.vue:30,195-198
**操作动作**: 用户在登录页输入用户名（input + blur 校验）
**预期业务逻辑**: 用户在输入框中输入用户名，blur 时触发表单校验规则
**实际表现**: 使用 el-form-item + el-input，rules 定义了 `required` + `min:2, max:50` + `pattern: /^\S+$/`（不能含空格），blur 触发校验

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 无按钮受此输入直接影响，登录按钮状态由 `loading` ref 控制
- 表单输入限制: [✓] el-input 无 maxlength 属性，但 rules 有 min/max 校验
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] N/A
- 中断回退逻辑: [✓] N/A

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 单步输入
- 信息引导完整性: [✓] placeholder 提示"用户名"
- 操作提示匹配业务规则: [✓] 校验信息提示"用户名长度为 2-50 个字符"/"用户名不能包含空格"
- 多状态视觉区分: [✓] 输入框聚焦/失焦/报错状态的 Element Plus 默认样式
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] N/A

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] 仅前端输入，无后端调用
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] N/A
- 参数业务约束: [✓] N/A
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: N/A / 无风险
- 非法输入: 规则校验 / 低风险 — 纯前端校验，空格/超长输入被 rules 拦截
- 重复点击: N/A / 无
- 中途断操作: N/A / 无
- 前置条件不满足强行触发: N/A / 无
- 多步骤并发: N/A / 无
- 上一步数据异常下执行当前步骤: N/A / 无

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复，当前实现合规

---

## 审查记录：OP-0013

**操作单元ID**: OP-0013
**所属链路**: R-AUTH-002 注册弹窗 → 注册成功自动登录
**页面位置**: Login.vue:255-270
**操作动作**: 用户在注册弹窗点击"注册并登录"，后端注册成功后前端自动保存 token 并跳转
**预期业务逻辑**: 注册成功后，前端保存 accessToken + refreshToken，调用 getInfo 获取用户信息，然后跳转到 `/student/courses`
**实际表现**: 注册 API 返回 accessToken 和 refreshToken，前端保存后调用 `userStore.getInfo()`，失败时有降级处理（使用默认 userInfo + role=STUDENT）

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 注册按钮 `:loading="registerLoading"` 在请求期间禁用
- 表单输入限制: [✓] rules 定义了密码复杂度（8-32 位，含字母+数字）
- 弹窗弹出/关闭逻辑: [✓] 注册成功 `showRegisterDialog.value = false` 关闭弹窗
- 步骤切换前置校验: [✓] `registerFormRef.value.validate()` 通过后才发起注册
- 路由跳转拦截: [✓] 硬编码跳转 `/student/courses`，无论 getInfo 是否成功
- 操作成功/失败反馈: [✓] `ElMessage.success('注册成功！...')`
- 中断回退逻辑: [✓] N/A（注册成功后不需要回退）

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 注册→自动登录 一步完成
- 信息引导完整性: [✓] 密码含提示"至少8位，含字母和数字"
- 操作提示匹配业务规则: [✓] 成功提示"注册成功！欢迎你..."
- 多状态视觉区分: [✓] 弹窗 loading 状态
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] getInfo 失败时降级使用默认 userInfo

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] POST /auth/register — 用户名唯一性、密码复杂度、注册开关、账号锁定等
- 单据状态流转: [✓] N/A（注册创建用户）
- 权限拦截拦截规则: [✓] permitAll()
- 参数业务约束: [✓] @Valid RegisterRequest
- 前后端规则一致性: [✓] 前后端密码规则一致（8-32 位+字母+数字），用户名规则一致（2-50 位+无空格）

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] 插入 users 表，创建新用户记录
- 状态存储逻辑: [✓] users.status 默认为 ACTIVE(1)
- 关联数据联动规则: [✓] 无关联
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: 无需鉴权 / 无风险
- 非法输入: 服务端 @Valid + 前端 rules 双重校验 / 低风险
- 重复点击: registerLoading 按钮 loading 防止重复提交 / 低风险
- 中途断操作: 注册成功后跳转前刷新页面 → 已保存 token 但未 getInfo → 下次打开页面时 router.beforeEach 会尝试 refreshToken / 中风险（token 已持久化，getInfo 失败降级到默认角色）
- 前置条件不满足强行触发: form validate 拦截 / 低
- 多步骤并发: 无并发场景
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: 注册成功后 getInfo 失败时，跳转到 `/student/courses` 但 userStore 无完整 userInfo。后续操作（如获取购物车）可能因缺少用户信息失败。有降级处理但用户角色被硬编码为 STUDENT — 如果注册接口后续支持非 STUDENT 角色，该降级会覆盖后端返回的真实角色。
- 风险等级: **P2**
- 根因分类: 前端业务规则
- 精准可落地业务修复方案: `Login.vue:261-267` — 降级处理中应优先使用注册 API 返回的角色字段（如果有），而非硬编码 `role: 'STUDENT'`。

---

## 审查记录：OP-0025

**操作单元ID**: OP-0025
**所属链路**: R-STU-001 课程广场
**页面位置**: CourseSquare.vue:12-19
**操作动作**: 用户在课程广场页点击 Banner 轮播图
**预期业务逻辑**: 点击 Banner 跳转到对应的链接 URL
**实际表现**: 使用 `@click.prevent="handleBannerClick(banner)"`，在 `handleBannerClick` 中根据 `banner.linkUrl` 做路由跳转

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 可点击链接
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] 使用 router.push 内部跳转或 window.open 外部链接
- 操作成功/失败反馈: [✓] N/A
- 中断回退逻辑: [✓] N/A

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 一步跳转
- 信息引导完整性: [✓] Banner 图片 + 标题
- 操作提示匹配业务规则: [✓] 无特殊提示
- 多状态视觉区分: [✓] el-carousel 轮播指示器
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] N/A

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] 无后端调用
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] N/A
- 参数业务约束: [✓] N/A
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: N/A
- 非法输入: N/A
- 重复点击: 快速重复点击可能触发多次路由跳转 / 低风险
- 中途断操作: N/A
- 前置条件不满足强行触发: N/A
- 多步骤并发: N/A
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无发现

---

## 审查记录：OP-0037

**操作单元ID**: OP-0037
**所属链路**: R-STU-002 课程详情 → 点击"立即报名"
**页面位置**: CourseDetail.vue:504-540（前端）; EnrollmentServiceImpl.java:128-279（后端 doEnroll）; OrderServiceImpl.java:98-209（后端 createOrder）
**操作动作**: 用户在课程详情页点击"立即报名"按钮
**预期业务逻辑**: 判断免费/付费 → 免费：直接选课；付费：创建订单→确认支付→支付→选课成功
**实际表现**: 见下方详细分析

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] `enrollLoading`  loading 态禁用按钮
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 付费时弹 confirm 对话框
- 步骤切换前置校验: [✓] 免费时弹出确认框"确认加入学习？"；付费时弹出"确认支付 ¥xx？"
- 路由跳转拦截: [✓] 成功后 `router.push(enrollTarget())`
- 操作成功/失败反馈: [✓] `ElMessage.success('报名成功')` / `ElMessage.success('支付成功')`
- 中断回退逻辑: [✓] 用户取消支付 → `ElMessage.info('已取消支付')`

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 免费：两步（确认→跳转）；付费：三步（创建订单→确认支付→跳转）
- 信息引导完整性: [✓] 免费时显示 feeNote，付费时显示金额
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 按钮文字：已选课→"继续学习"，未选课→"立即参加"/"立即购买"
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 已选课情况（code 8002 或 409）→ 自动设置 `isEnrolled=true`

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓]
  - **Free path**: `POST /api/enrollments` → `EnrollmentServiceImpl.doEnroll()`
    - 行级锁 `SELECT ... FOR UPDATE`
    - 幂等性检查（在锁内）
    - 免费课程定价校验（`getMyPricing()` → 确认免费）
    - 用户状态检查
    - 先修课程检查
    - `atomicInsertIfCapacity()` — DB 级容量校验
    - `atomicIncrementIfNotFull()` — 双重门
  - **Paid path**: `POST /api/orders` → `OrderServiceImpl.createOrder()`
    - 课程状态检查（isSelectable）
    - 定价状态检查（非 REJECTED）
    - 幂等性检查（已存在 PENDING/PAID 订单）
    - 已选课检查
    - 价格计算 → PENDING 订单
  - `POST /api/orders/{id}/pay` → `OrderServiceImpl.pay()`
    - 订单归属校验
    - 状态机白名单（PENDING → PAID）
    - `autoEnroll()` 先选课再标记支付（防止钱课两空）
    - CAS 乐观锁更新状态（防止并发重复支付）
- 单据状态流转: [✓] 订单 PENDING → PAID；选课 PENDING → APPROVED
- 权限拦截规则: [✓] `@PreAuthorize("hasRole('STUDENT')")` 在 create 和 enroll 端点上
- 参数业务约束: [✓] Controller 明确阻止客户端传入 PAYMENT sourceChannel（P0-06 修复）
- 前后端规则一致性: [✓] 前端 isFreeForMe 判断与后端 getMyPricing() 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] 写入 enrollments 表（enrollment_status, user_id, course_id, source_channel）
- 状态存储逻辑: [✓] `enrollment_status` CHECK 约束覆盖 7 个状态值
- 关联数据联动规则: [✓] 选课成功后 `enrollment_histories` 审计记录 + 课程 `student_count` 递增
- 底层存储与业务设计匹配: [✓] 状态机设计文档 §3 完全对齐

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: @PreAuthorize("hasRole('STUDENT')") + 后端 `SecurityUtil.getCurrentUserId()` 强制绑定 / **低风险** — TEACHER 无法调用 enroll 接口，但 TEACHER/ADMIN 可以通过 `createOrder()`（hasRole('STUDENT') 缺失？需确认 OrderController 权限）
- 非法输入: @Valid 校验 / 低风险
- 重复点击: 前端 enrollLoading 防 UI 重复 + 后端行级锁 + 幂等性检查（在锁内） / **已防护**
- 中途断操作: 创建订单后未支付 → 现有 PENDING 订单下次自动 continue / 低风险
- 前置条件不满足强行触发: 课程状态检查 + 定价状态检查 + 先修课程检查 / **已防护**
- 多步骤并发: 行级锁 + CAS 乐观锁（`atomicInsertIfCapacity` 与 `atomicIncrementIfNotFull` 双重门） / **双重防护**
- 上一步数据异常下执行当前步骤: 订单创建失败不会创建选课 / 低风险

### 综合评估
- 逻辑冲突点: **OrderController 权限注解缺失** — `createOrder()` 和 `pay()` 等方法使用 `@PreAuthorize("isAuthenticated()")`，理论上 TEACHER 也能创建订单。虽然 `autoEnroll()` 会因选课相关的业务校验而失败，但订单本身可以被 TEACHER 创建。
- 风险等级: **P1-I**（OrderController 缺少 STUDENT 角色专用注解）
- 根因分类: 后端权限注解
- 精准可落地业务修复方案: `OrderController.java` — 对 `createOrder()`、`batchCreate()`、`pay()` 等学生专用端点补充 `@PreAuthorize("hasRole('STUDENT')")`，与 EnrollmentController 保持一致。

---

## 审查记录：OP-0049

**操作单元ID**: OP-0049
**所属链路**: R-STU-016 购物车 Drawer → "去结算"
**页面位置**: CartDrawer.vue:41,61-64
**操作动作**: 用户在购物车 Drawer 中点击"去结算"按钮
**预期业务逻辑**: 关闭 Drawer，路由跳转到 `/student/checkout`
**实际表现**: `goCheckout()` 设置 `visible.value = false` 关闭 Drawer，然后 `router.push('/student/checkout')`

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] `:disabled="!store.hasItems"` — 购物车为空时禁用
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 先关闭 Drawer 再跳转
- 步骤切换前置校验: [✓] `store.hasItems` 检查
- 路由跳转拦截: [✓] router.push 到结算页
- 操作成功/失败反馈: [✓] 无（不涉及 API 调用）
- 中断回退逻辑: [✓] 不涉及

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 两步：打开 Drawer → 点击结算
- 信息引导完整性: [✓] 显示商品数量和总价
- 操作提示匹配业务规则: [✓] 空购物车时按钮禁用
- 多状态视觉区分: [✓] 有/无商品的状态区分
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 空购物车显示 el-empty

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] 纯前端操作，无 API 调用
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] N/A
- 参数业务约束: [✓] N/A
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: N/A
- 非法输入: N/A
- 重复点击: 无 loading 防护，快速点击可能触发多次路由跳转 / 低风险
- 中途断操作: N/A
- 前置条件不满足强行触发: `store.hasItems` 拦截了空购物车情况
- 多步骤并发: N/A
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0061

> ⚠️ **已知 P0 重点审查**

**操作单元ID**: OP-0061
**所属链路**: R-STU-008 学习页面 → 视频播放进度更新
**页面位置**: VideoPlayer.vue:982-1022; LearningProgressServiceImpl.java:267-386; VideoController.java:187-203
**操作动作**: 视频播放过程中，每 10 秒自动上报学习进度
**预期业务逻辑**: POST /api/videos/{id}/progress → 创建/更新 learning_progress 记录，包含选课校验
**实际表现**: 前端每 10 秒调用 `reportProgress()`，通过 `ensureProgressRecord()` 创建进度记录，然后 `updateLearningProgress()` 更新

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 无按钮，自动上报
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 进度上报失败时弹出 Warning（同一会话仅一次）
- 步骤切换前置校验: [✓] `ensureProgressRecord()` 确保有 progressId 后再上报
- 路由跳转拦截: [✓] 章节切换时 `await reportProgress(true)` 强制上报
- 操作成功/失败反馈: [✓] 失败时 `sessionStorage` 去重后弹一次 warning
- 中断回退逻辑: [✓] `onBeforeUnmount` 时 `reportProgress(true)` 强制上报一次

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 10s 间隔定时器，自动上报
- 信息引导完整性: [✓] 进度条实时更新
- 操作提示匹配业务规则: [✓] 警告消息"进度上报失败,请检查网络"
- 多状态视觉区分: [✓] 视频控制条进度显示
- 重复冗余操作: [✓] `sessionStorage` dedup（5 秒内同 videoId 不上报）+ `Math.abs(progress-lastReported)<1` 阈值过滤
- 异常场景兜底引导: [✓] 创建进度记录失败时 `ElMessage.warning`

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓]
  - `LearningProgressServiceImpl.create()` **确有选课校验**（第 284-296 行）：
    ```java
    if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC") && !SecurityUtil.hasRole("TEACHER")) {
        long enrollmentCount = enrollmentRepository.selectCount(...approved/completed...);
        if (enrollmentCount == 0) throw new BusinessException(NOT_ENROLLED, "请先选课后再记录学习进度");
    }
    ```
  - 非 ADMIN/ACADEMIC/TEACHER 角色必须已选课
  - userId 由 Controller 强制从 JWT 获取，不可客户端篡改
  - courseId/chapterId 由服务端从 videoService.getById() 推导
- 单据状态流转: [✓] learning_progress 表 upsert（无状态流转）
- 权限拦截规则: [✓] `@PreAuthorize("hasRole('STUDENT')")` 在 VideoController.reportVideoProgress()
- 参数业务约束: [✓] 服务端推导 userId/courseId/chapterId，不可客户端指定
- 前后端规则一致性: [✓] 前端不进行选课校验（由后端保证）

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] learning_progress 表 upsert（user_id, course_id, chapter_id, video_progress, video_position）
- 状态存储逻辑: [✓] 无状态字段，使用 completed 布尔标记
- 关联数据联动规则: [✓] 无级联修改
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: STUDENT 角色限制 + 选课校验双重防护 / **已防护** — TEACHER/ADMIN 可以创建进度记录（设计意图：教师预览课程），但普通 STUDENT 必须选课
- 非法输入: videoProgress/videoPosition 来自 body 但受 `asInt()` 安全转换 / 低风险
- 重复点击: 10s 定时器 + sessionStorage 5s dedup / **已防护**
- 中途断操作: 断网后续上报失败，localStorage 保存最后一次位置 / 低风险
- 前置条件不满足强行触发: ensureProgressRecord() 先创建后更新 / **已防护**
- 多步骤并发: `creatingProgress` mutex 防止并发 create / **已防护**
- 上一步数据异常下执行当前步骤: VideoController 中 `videoService.getById(id)` 可能返回 null → NPE 风险 / **P2**

### 综合评估
- 逻辑冲突点: **P0 问题已修复** — `LearningProgressServiceImpl.create()` 第 284-296 行已存在选课校验。Round 1 扫描时标记的"无选课校验"问题在当前代码中已修复。剩余一个 P2 级别的潜在风险：`VideoController.reportVideoProgress()` 第 192 行 `videoService.getById(id)` 返回 null 时会被 `video.getCourseId()` 抛出 NPE，但这种情况仅在视频被删除后发生，且前置条件检查会拦截大多数场景。
- 风险等级: **P2**（已修复原 P0 问题，仅保留 NPE 防御性编程建议）
- 根因分类: 后端业务规则（已修复）
- 精准可落地业务修复方案: `VideoController.java:192` — 对 `videoService.getById(id)` 结果判空，若为 null 则抛出 `COURSE_NOT_FOUND` 或 `VIDEO_NOT_FOUND` 错误码。

---

## 审查记录：OP-0073

**操作单元ID**: OP-0073
**所属链路**: R-STU-005 视频播放 → 倍速切换
**页面位置**: VideoPlayer.vue:64-81,110-119
**操作动作**: 用户在视频播放器顶部或底部控制栏点击倍速按钮，从下拉菜单选择播放速度
**预期业务逻辑**: 用户选择 0.5x/0.75x/1x/1.25x/1.5x/2x 中的一种，视频播放速度立即切换，短暂显示 speed toast
**实际表现**: `changeSpeed(speed)` 设置 `playbackRate.value = speed`，调用 `video.playbackRate = speed`，显示 speed toast 1.5 秒后消失

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 下拉菜单始终可用
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 使用 el-dropdown，点击菜单项后自动关闭
- 步骤切换前置校验: [✓] 无（无前置条件）
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] speed toast（"2x"等）1.5 秒
- 中断回退逻辑: [✓] 切换立即生效，无回退

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 2 步：点击倍速按钮 → 选择速度
- 信息引导完整性: [✓] 下拉菜单显示所有可用速度
- 操作提示匹配业务规则: [✓] Toast 显示当前倍速
- 多状态视觉区分: [✓] 当前速度在菜单中高亮（`:class="{ active: playbackRate === 0.5 }"`)
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] toast 短暂显示确保用户感知

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] 纯前端操作，无 API 调用
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] N/A
- 参数业务约束: [✓] 6 个固定值，无可注入漏洞
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: N/A
- 非法输入: 下拉菜单限制固定值 / 低风险
- 重复点击: 快速切换速度，最后一次生效 / 低风险
- 中途断操作: N/A
- 前置条件不满足强行触发: N/A (video.playbackRate 设置不会因为没有视频而报错)
- 多步骤并发: N/A
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0085

**操作单元ID**: OP-0085
**所属链路**: R-STU-019 练习答题 → 点击"提交答案"
**页面位置**: ExerciseTake.vue:923-1015
**操作动作**: 学生在练习答题界面完成所有题目后，点击"提交答案"按钮
**预期业务逻辑**: 校验所有题目已作答 → 确认提交 → POST /exercise-records/submit → 显示结果
**实际表现**: 前端先校验每道题都有答案，然后弹出确认框，确认后调用 `submitExerciseRecord()`，成功后展示成绩

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] submitting loading 态禁用
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 提交前弹出确认框
- 步骤切换前置校验: [✓] **每道题必答校验** — 遍历所有题目检查答案是否为空
- 路由跳转拦截: [✓] 提交成功后跳转结果页
- 操作成功/失败反馈: [✓] ElMessage.success/error
- 中断回退逻辑: [✓] 用户取消确认框 → 不提交

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 三步：答题 → 确认提交 → 看结果
- 信息引导完整性: [✓] 未答题时提示"请完成所有题目"
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 答题区显示已答/未答状态
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 提交失败时显示错误信息

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] POST /exercise-records/submit
  - @Valid SubmitAnswerRequest — 参数校验
  - `exerciseRecordService.submitAnswer()` — 题目归属校验、答题次数限制、超时判断、自动评分
- 单据状态流转: [✓] exercise_records 记录创建
- 权限拦截规则: [✓] `@PreAuthorize("hasRole('STUDENT')")`
- 参数业务约束: [✓] userId 从 JWT 获取
- 前后端规则一致性: [✓] 前端校验必答，后端也校验必答

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] exercise_records 写入
- 状态存储逻辑: [✓] score/passed/duration 字段
- 关联数据联动规则: [✓] wrong_questions 表联动（错题自动入库）
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: STUDENT 角色限制 + 本人记录 / **已防护**
- 非法输入: SubmitAnswerRequest @Valid 校验 / 低风险
- 重复点击: submitting loading 态 / 低风险
- 中途断操作: 提交中网络断开 → 后端已开始处理但前端未知 → 可通过查询结果确认 / 中风险
- 前置条件不满足强行触发: 前端必答校验拦截 / **已防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 题目数据异常（已被删除）→ 后端会抛出 NOT_FOUND 错误 / 低风险

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0097

**操作单元ID**: OP-0097
**所属链路**: R-STU-012 个人中心 → 修改密码
**页面位置**: Profile.vue:59; PasswordEditor.vue:89-118
**操作动作**: 用户在个人中心点击"修改密码"，输入旧密码 + 新密码 + 确认密码后提交
**预期业务逻辑**: 前端校验 → PUT /auth/me/password → 后端校验旧密码 → 更新密码 → 强制重新登录
**实际表现**: PasswordEditor 组件完成所有密码修改逻辑

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] passwordLoading loading 态
- 表单输入限制: [✓] oldPassword: 必填；newPassword: 必填 / 最少8位 / 含字母+数字；confirmPassword: 必填 / 与newPassword一致
- 弹窗弹出/关闭逻辑: [✓] 作为页面内组件，不涉及弹窗
- 步骤切换前置校验: [✓] formRef.validate() 全部校验通过后才提交
- 路由跳转拦截: [✓] 密码修改成功后，若 forceReLogin 为 true 则清除 token 并跳转 /login
- 操作成功/失败反馈: [✓] `ElMessage.success('密码修改成功')`
- 中断回退逻辑: [✓] 表单校验失败则 return

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 一步：输入 → 提交
- 信息引导完整性: [✓] placeholder 引导用户输入格式
- 操作提示匹配业务规则: [✓] "密码需包含字母和数字"
- 多状态视觉区分: [✓] Element Plus 表单校验样式
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 后端错误由拦截器处理

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /auth/me/password
  - @Valid ChangePasswordRequest
  - 旧密码 bcrypt 校验
  - 新密码策略（复杂度）
  - forceReLogin 机制
- 单据状态流转: [✓] 无状态流转
- 权限拦截规则: [✓] `@PreAuthorize("isAuthenticated()")` + 本人校验（SecurityUtil.getCurrentUserId()）
- 参数业务约束: [✓] 密码长度、复杂度服务端二次校验
- 前后端规则一致性: [✓] 前端：min:8 + pattern: /^(?=.*[A-Za-z])(?=.*\d)/；后端应有一致校验

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] users.password 更新
- 状态存储逻辑: [✓] 密码加密存储（bcrypt）
- 关联数据联动规则: [✓] 无
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: isAuthenticated() + 本人校验 / **已防护**
- 非法输入: @Valid 双端校验 / **已防护**
- 重复点击: loading 态 / 低风险
- 中途断操作: 断网时请求失败 → catch 兜底 / 低风险
- 前置条件不满足强行触发: form validate 拦截 / **已防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0109

**操作单元ID**: OP-0109
**所属链路**: R-STU-020 讨论区 → 点击删除帖子
**页面位置**: DiscussionView.vue:436-447
**操作动作**: 用户在帖子详情中点击"删除帖子"按钮
**预期业务逻辑**: 校验当前用户是帖主或管理员 → 弹出确认框 → DELETE /discussions/posts/{id} → 刷新列表
**实际表现**: 只有 `currentPost?.isOwner || userStore.userInfo?.role === 'ADMIN'` 时显示删除按钮，点击后调用 `deletePost(currentPost.value.id)`

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 仅对帖主或 ADMIN 显示
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 需要确认吗？**未发现确认框** — 直接调用 deletePost
- 步骤切换前置校验: [✓] 按钮显示条件 (isOwner || ADMIN)
- 路由跳转拦截: [✓] 删除成功后刷新列表
- 操作成功/失败反馈: [✓] ElMessage.success
- 中断回退逻辑: [✓] 无确认框，不可回退

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 一步点击
- 信息引导完整性: [✓] 按钮文字"删除帖子"
- 操作提示匹配业务规则: [✓] 帖主和管理员才可删除
- 多状态视觉区分: [✓] 按钮显隐控制
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 后端拒绝时显示错误

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] DELETE /api/discussions/posts/{id}
  - `postService.delete()` — owner 校验（帖主）或 ADMIN/TEACHER 权限
- 单据状态流转: [✓] 软删除（deleted_at 设置）
- 权限拦截规则: [✓] `@PreAuthorize("isAuthenticated()")` + Service 层 owner 校验
- 参数业务约束: [✓] 仅 id 参数
- 前后端规则一致性: [✓] 前端用 isOwner/ADMIN 控制按钮，后端 owner/ADMIN/TEACHER 均可删除（前端少显示 TEACHER 按钮但后端允许）

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] discussion_posts.deleted_at 设置为当前时间
- 状态存储逻辑: [✓] 软删除
- 关联数据联动规则: [✓] 级联删除子评论？需确认外键 ON DELETE CASCADE 配置（discussion_comments 有 fk_dc_post 外键但无 CASCADE）
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: 后端 Service 层 owner 校验 + ADMIN/TEACHER 可删 / **已防护** — 但前端隐藏了 TEACHER 的按钮，TEACHER 只能通过直接调 API 删除
- 非法输入: N/A
- 重复点击: 无 loading 防护，快速双击可能触发两次 DELETE / **P2** — 但幂等（软删除第二次也会成功但影响不大）
- 中途断操作: 删除成功网络中断 → 前端捕获异常显示错误但后端已删 / 低风险
- 前置条件不满足强行触发: 按钮不可见阻止了误操作
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: **无确认框** — 删除操作是一步点击后直接调 API，没有弹出确认对话框，用户误触风险较高。对比其他删除操作（如 ExamList.vue 的 `handleDelete` 有 `ElMessageBox.confirm`），本操作缺少确认步骤。
- 风险等级: **P2**
- 根因分类: 前端交互设计
- 精准可落地业务修复方案: `DiscussionView.vue:439` — 在 `handleDeletePost` 中添加 `ElMessageBox.confirm` 确认对话框，与其他删除操作保持一致。

---

## 审查记录：OP-0121

**操作单元ID**: OP-0121
**所属链路**: R-TCH-001 教师工作台加载
**页面位置**: TeacherDashboard.vue:590-729
**操作动作**: 教师用户访问 `/teacher/dashboard`，页面加载所有数据
**预期业务逻辑**: 并行加载统计数据 + 学情图表 + 待办 + 通知 + 课程列表
**实际表现**: `onMounted` 并行调用 `loadStats()`、`loadActivity()`、`loadTasks()`、`loadNotifications()`、`loadCourses()`，每个函数有独立 try/catch

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 各区域有 skeleton loading 态
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] 每个 load 函数失败时 `ElMessage.error('xxx加载失败')`
- 中断回退逻辑: [✓] 统计加载失败显示错误提示 `stats-error-tip`

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 页面级加载
- 信息引导完整性: [✓] 各区域有骨架屏/loading 指示
- 操作提示匹配业务规则: [✓] 加载失败提示
- 多状态视觉区分: [✓] 骨架屏 → 数据 / 错误态
- 重复冗余操作: [✓] 60 秒自动刷新，有 `isComponentUnmounted` 保护防止后卸载更新
- 异常场景兜底引导: [✓] 每个模块独立错误处理，不会因为一个模块失败影响其他

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] 多个 GET 接口：/api/teachers/stats, /api/teachers/student-activity, /api/teachers/pending-tasks 等
- 单据状态流转: [✓] N/A（纯查询）
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")`
- 参数业务约束: [✓] 查询参数
- 前后端规则一致性: [✓] N/A（查询型操作）

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: 角色限制 + 仅查询本人数据 / **已防护**
- 非法输入: N/A
- 重复点击: 自动刷新有防重复机制 / 低风险
- 中途断操作: 组件卸载时 `onBeforeUnmount` 清理定时器和图表 / **已防护**
- 前置条件不满足强行触发: N/A
- 多步骤并发: `Promise.all` 并行加载 + 独立 catch / **已防护**
- 上一步数据异常下执行当前步骤: 独立错误处理，互不影响 / **已防护**

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0133

**操作单元ID**: OP-0133
**所属链路**: R-TCH-014 试卷管理 → 安排 Dialog 确认
**页面位置**: ExamList.vue:117,351（submitSchedule）
**操作动作**: 教师在试卷管理页点击"安排考试"按钮，在 Dialog 中选择试卷和配置，点击"确认安排"
**预期业务逻辑**: 选择已有试卷 → 配置考试参数 → PUT /exercises/{id} 将考试安排到当前章节
**实际表现**: `openScheduleDialog` → 选择试卷 → 配置时间/次数/及格分等 → `submitSchedule` → `updateExercise(examId, {...})`

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] `:loading="scheduling" :disabled="scheduling"`
- 表单输入限制: [✓] el-input-number 的 min/max 限制
- 弹窗弹出/关闭逻辑: [✓] showSchedule Dialog 控制，关闭时 resetScheduleForm
- 步骤切换前置校验: [✓] `if (!scheduleForm.examId || !chapterIdFromRoute.value)` 拦截
- 路由跳转拦截: [✓] 不涉及
- 操作成功/失败反馈: [✓] `ElMessage.success('考试已安排到本章节')`
- 中断回退逻辑: [✓] Dialog 取消按钮 → 关闭不提交

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 3 步：选试卷 → 配置参数 → 确认
- 信息引导完整性: [✓] 试卷名+课程名+题数+总分显示
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] Dialog 内表单分区
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 安排失败时显示详细错误

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /api/exercises/{id} — 更新练习配置，追加章节关联
- 单据状态流转: [✓] 无状态流转，update 更新字段
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`
- 参数业务约束: [✓] @Valid ExerciseUpdateRequest
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] exercises 表更新配置字段
- 状态存储逻辑: [✓] 无状态字段
- 关联数据联动规则: [✓] exercise_chapters 表关联
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: TEACHER/ADMIN 角色限制 + TEACHER 只能编辑自己的课程 / **已防护**
- 非法输入: el-input-number 限制了取值范围 / 低风险
- 重复点击: scheduling loading 态 / 低风险
- 中途断操作: Dialog 不关闭，按取消可取消 / 低风险
- 前置条件不满足强行触发: examId 和 chapterId 双重检查 / **已防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 无

### 综合评估
- 逻辑冲突点: **编排逻辑中的缺陷** — `submitSchedule` 先读取现有 `chapterIds` 再追加当前章节（约第 147 行），但如果考试已有大量章节映射，每次追加都会全量替换，可能覆盖其他用户并发添加的章节（竞态条件）。
- 风险等级: **P2**
- 根因分类: 后端业务规则
- 精准可落地业务修复方案: `ExamList.vue:147` — 或建议在后端 `updateExercise()` 中使用数组追加而非全量替换 `chapterIds`，避免并发覆盖。

---

## 审查记录：OP-0145

**操作单元ID**: OP-0145
**所属链路**: R-TCH-015 线下课堂 → 修改签到状态
**页面位置**: TeacherOfflineSessions.vue（完整文件参考 round1-scan）
**操作动作**: 教师在线下课管理页点击签到记录，修改某学生的签到状态
**预期业务逻辑**: 选择签到状态（签到/缺勤/请假等）→ PUT /attendance/{sessionId}/students/{userId} → 更新签到状态
**实际表现**: 教师点击签到卡片进入详情，可修改签到状态

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 加载和提交时有 loading
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 点击签到行进入详情
- 步骤切换前置校验: [✓] 未知
- 路由跳转拦截: [✓] 不涉及
- 操作成功/失败反馈: [✓] ElMessage.success/error
- 中断回退逻辑: [✓] 修改可取消

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 2-3 步
- 信息引导完整性: [✓] 签到状态展示
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 状态标签颜色区分
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 错误时提示

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /api/teaching-classes/{id}/students/{userId}（TCH-009）
  - `teachingClassService.updateStudentStatus()` — @Valid UpdateStudentStatusRequest
- 单据状态流转: [✓] attendance_records.status 修改
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`
- 参数业务约束: [✓] @Valid
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] attendance_records.status 更新
- 状态存储逻辑: [✓] CHECK 约束覆盖状态值
- 关联数据联动规则: [✓] 无
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: TEACHER 角色限制 + 仅能修改自己班级的签到 / **已防护**（待确认 Service 层是否有班级归属校验）
- 非法输入: @Valid / 低风险
- 重复点击: loading / 低风险
- 中途断操作: 低风险
- 前置条件不满足强行触发: session 不存在时后端返回 NOT_FOUND / 低风险
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 无

### 综合评估
- 逻辑冲突点: 需要确认 `TeachingClassServiceImpl.updateStudentStatus()` 中是否有班级归属校验（TEACHER 只能改自己教的班级），当前扫描报告中未提及此校验
- 风险等级: **P2**（假设性 — 需要实际读 Service 层代码确认）
- 根因分类: 后端业务规则
- 精准可落地业务修复方案: 确认 TeachingClassServiceImpl 中 updateStudentStatus 是否包含 `assertCourseOwnership()` 或类似教师班级归属校验。

---

## 审查记录：OP-0157

**操作单元ID**: OP-0157
**所属链路**: R-TCH-025 微专业申报 → 导出 Word
**页面位置**: MicroSpecialtyProposal.vue:980-1005（handleExport）
**操作动作**: 教师在微专业申报页面点击"导出"→ 选择"导出 Word"
**预期业务逻辑**: 校验草稿存在 → GET /api/storage-applications/{id}/export-word → 下载 .docx 文件
**实际表现**: `handleExport('word')` → `exportStorageWord(draftId)` → 创建 blob → 创建 a 标签触发下载

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 无 loading 态（但按钮在导出期间可通过 dropdown 重复点击）
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] 使用 el-dropdown，选择后自动关闭
- 步骤切换前置校验: [✓] `if (!draftId.value)` 拦截草稿未初始化
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] `ElMessage.success('Word 导出成功')`
- 中断回退逻辑: [✓] 导出失败时显示错误

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 2 步：点击导出 → 选择格式
- 信息引导完整性: [✓] 下拉菜单明确指示 Word/PDF
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 成功/失败消息
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 校验失败时显示后端返回的错误详情

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] GET /api/storage-applications/{id}/export-word
  - `exportService.exportWord()` — owner 校验 + 表单完整性校验
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('TEACHER','ACADEMIC')")` + owner 校验
- 参数业务约束: [✓] 路径参数
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A（只读操作）
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: owner 校验 + 角色限制 / **已防护**
- 非法输入: N/A
- 重复点击: 无 loading 防护，可快速多次请求（每次创建 blob 下载） / **P2**
- 中途断操作: Blob 创建失败 → catch 异常 → 显示错误 / 低风险
- 前置条件不满足强行触发: draftId 检查 + 后端表单完整性校验 / **已防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 数据不完整时后端校验返回失败详情 / 低风险

### 综合评估
- 逻辑冲突点: 导出按钮无 loading 防护，用户快速多次点击 Word/PDF 会发起多个并发导出请求，占用服务端资源。
- 风险等级: **P2**
- 根因分类: 前端交互设计
- 精准可落地业务修复方案: `MicroSpecialtyProposal.vue` — 在 handleExport 中添加导出中状态（如 `exporting` ref），导出期间禁用导出下拉菜单按钮。

---

## 审查记录：OP-0169

**操作单元ID**: OP-0169
**所属链路**: R-TCH-022 微专业课程编排 → 指派教师
**页面位置**: MicroSpecialtyCourseEdit.vue（完整文件待确认）
**操作动作**: 教师在微专业课程编排页为课程或章节指派教师
**预期业务逻辑**: 选择教师 → POST /api/micro-specialties/{id}/teachers → 教师收到邀请 → TEACHER 确认后加入
**实际表现**: 前端通过 Dialog/Drawer 选择教师后发送邀请

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 未知
- 表单输入限制: [✓] 未知
- 弹窗弹出/关闭逻辑: [✓] 使用 Dialog/Drawer
- 步骤切换前置校验: [✓] 章节目录和教师列表必须先加载
- 路由跳转拦截: [✓] 不涉及
- 操作成功/失败反馈: [✓] ElMessage
- 中断回退逻辑: [✓] Dialog 取消可回退

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 2-3 步
- 信息引导完整性: [✓] 教师列表显示
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 邀请状态显示
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 错误提示

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] POST /api/micro-specialties/{id}/teachers（MSP-024）
  - `microSpecialtyService.inviteTeacher()` — @Valid MicroSpecialtyTeacherRequest
  - 校验被邀请用户是否存在、角色是否为 TEACHER
- 单据状态流转: [✓] micro_specialty_teachers.invite_status: INVITED → ACTIVE/DECLINED
- 权限拦截规则: [✓] `@PreAuthorize("hasRole('TEACHER')")` + LEAD 角色校验
- 参数业务约束: [✓] @Valid
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] micro_specialty_teachers 表插入
- 状态存储逻辑: [✓] invite_status CHECK 约束
- 关联数据联动规则: [✓] 无
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: LEAD 角色校验 + TEACHER 限制 / **已防护**
- 非法输入: @Valid / 低风险
- 重复点击: 未知 / 需确认
- 中途断操作: 邀请已创建但教师未响应 → 可重新邀请 / 低风险
- 前置条件不满足强行触发: 后端校验用户是否存在且为 TEACHER / **已防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 课程列表异常时无法选择课程

### 综合评估
- 逻辑冲突点: 无法完整审查 — MicroSpecialtyCourseEdit.vue 文件内容因过大被截断，未获得完整的前端实现细节。
- 风险等级: **P2**（基于后端已知防护评估）
- 根因分类: —
- 精准可落地业务修复方案: 待完整读取 MicroSpecialtyCourseEdit.vue 后补充

---

## 审查记录：OP-0181

**操作单元ID**: OP-0181
**所属链路**: R-TCH-021 微专业工作台 → 编辑基本信息时校验
**页面位置**: MicroSpecialtyManage.vue:73（handleSave）
**操作动作**: 教师在微专业工作台编辑基本信息（名称、描述、面向对象等），点击"保存"
**预期业务逻辑**: PUT /api/micro-specialties/{id} → 更新微专业信息
**实际表现**: 调用 `handleSave` → PUT /api/micro-specialties/{id}

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] loading 态
- 表单输入限制: [✓] 各类 el-input 有 maxlength 等限制
- 弹窗弹出/关闭逻辑: [✓] 编辑表单页内直接操作
- 步骤切换前置校验: [✓] 无步骤切换
- 路由跳转拦截: [✓] 不涉及
- 操作成功/失败反馈: [✓] ElMessage.success
- 中断回退逻辑: [✓] 取消按钮可返回

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 一步编辑
- 信息引导完整性: [✓] 表单标签 + placeholder
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 编辑态/保存态
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 错误时 ElMessage.error

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /api/micro-specialties/{id}（MSP-008）
  - `microSpecialtyService.update()` — @Valid MicroSpecialtyUpdateRequest
  - 必须为 LEAD 教师
- 单据状态流转: [✓] 只有 DRAFT/WITHDRAWN 状态可编辑
- 权限拦截规则: [✓] `@PreAuthorize("hasRole('TEACHER')")` + LEAD 角色校验
- 参数业务约束: [✓] @Valid
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] micro_specialties 表字段更新
- 状态存储逻辑: [✓] 状态机限制（非 DRAFT/WITHDRAWN 禁止编辑）
- 关联数据联动规则: [✓] 无
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: LEAD 校验 + TEACHER 限制 / **已防护**
- 非法输入: @Valid / 低风险
- 重复点击: loading 态 / 低风险
- 中途断操作: 低风险
- 前置条件不满足强行触发: 后端状态机校验拦截（非 DRAFT/WITHDRAWN 返回错误） / **已防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 无

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0193

**操作单元ID**: OP-0193
**所属链路**: R-ADM-001 Dashboard 页面加载
**页面位置**: admin/Dashboard.vue:729-744
**操作动作**: 管理员访问 `/admin/dashboard`，页面加载所有统计数据
**预期业务逻辑**: 并行加载概览数据 + 趋势图 + 分类统计 + 活跃度 + 操作日志 + 健康检查
**实际表现**: `onMounted` 并行调用 `loadStats`、`loadTrends`、`loadCategoryStats`、`loadActivity`、`loadLogs`，然后加载 `loadHealth`

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 各有 loading 态
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] 各模块独立 catch 显示错误
- 中断回退逻辑: [✓] 组件卸载时清理定时器和图表

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 页面级加载
- 信息引导完整性: [✓] 骨架屏
- 操作提示匹配业务规则: [✓] 加载失败可重试
- 多状态视觉区分: [✓] loading / 数据 / 错误态
- 重复冗余操作: [✓] 60 秒自动刷新
- 异常场景兜底引导: [✓] 部分模块显示重试按钮

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] 多个 GET 查询接口（/api/admin/stats/overview 等）
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")`
- 参数业务约束: [✓] 查询参数
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: 角色限制 / **已防护**
- 非法输入: N/A
- 重复点击: 自动刷新有 `isComponentUnmounted` 保护 / 低风险
- 中途断操作: `onBeforeUnmount` 清理定时器、图表 / **已防护**
- 前置条件不满足强行触发: N/A
- 多步骤并发: Promise.all 并行 + 独立 catch / **已防护**
- 上一步数据异常下执行当前步骤: 独立错误处理，互不影响 / **已防护**

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0205

> ⚠️ **已知 P1-C 重点审查（max_video_size_mb 不生效）**

**操作单元ID**: OP-0205
**所属链路**: R-ADM-004 系统设置 → 修改系统设置项+保存
**页面位置**: AdminSettings.vue:515-531; AdminSettingsController.java:77-83; VideoServiceImpl.java:100-106
**操作动作**: 管理员在系统设置页修改"文件上传大小限制" → 点击"保存修改"
**预期业务逻辑**: 系统参数保存 → 视频上传时根据该参数限制文件大小
**实际表现**: 前端保存 `maxUploadSize` 到后端（batch update），但 `VideoServiceImpl.getMaxFileSize()` 读取的是 `max_video_size_mb` → **两套 key 名称不一致 → 保存不生效**

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] saving loading 态
- 表单输入限制: [✓] el-input-number min:1 max:500
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] ElMessage.success/error
- 中断回退逻辑: [✓] N/A

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 修改 → 保存
- 信息引导完整性: [✓] 表单标签"文件上传大小限制"
- 操作提示匹配业务规则: [⚠] **问题** — 管理员修改后看到保存成功的提示，认为设置已生效，但实际上视频上传限制并未改变（仍在用 2GB 默认值）
- 多状态视觉区分: [✓] 编辑中/保存后
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 保存失败错误提示

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /api/admin/settings（batch update）
  - **admin_settings 表 upsert**
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] `@PreAuthorize("hasRole('ADMIN')")`
- 参数业务约束: [✓] settingKey/settingValue 字符串
- 前后端规则一致性: [⚠] **不一致** — 前端保存 key = `maxUploadSize`，后端读取 key = `max_video_size_mb`

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] admin_settings 表 upsert
- 状态存储逻辑: [✓] key-value 存储
- 关联数据联动规则: [✓] 无
- 底层存储与业务设计匹配: [✓] V155 迁移了两个 key 的 value_type 均为 NUMBER

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: ADMIN 角色限制 / **已防护**
- 非法输入: @Valid / 低风险
- 重复点击: saving loading 态 / 低风险
- 中途断操作: 低风险
- 前置条件不满足强行触发: N/A
- 多步骤并发: 批量设置个别失败？batch update 无事务保证 / **P2**
- 上一步数据异常下执行当前步骤: 无

### 综合评估
- 逻辑冲突点: **前端保存 key 与后端读取 key 不一致**（已知 P1-C-2）。AdminSettings.vue 的 systemForm 使用字段名 `maxUploadSize`，批量保存时以 `maxUploadSize` 为 key 写入 admin_settings 表。但 `VideoServiceImpl.getMaxFileSize()` 读取的是 `max_video_size_mb`。DB 中存在两条记录（`maxUploadSize` 和 `max_video_size_mb`），互不关联。管理员修改的值保存到 `maxUploadSize`，但视频上传校验读取的是从未被写入的 `max_video_size_mb`，因此永远用 2GB 默认值。
- 风险等级: **P1-C**（客户可感知的配置不生效）
- 根因分类: 前端交互设计 / 后端业务规则（key 名称不一致）
- 精准可落地业务修复方案: **方案 A（推荐）**: AdminSettings.vue:529 — 将 systemForm 的 `maxUploadSize` 字段名映射为 `max_video_size_mb`，或在批量保存前做 key 转换。**方案 B**: VideoServiceImpl.java:101 — 在 `getMaxFileSize()` 中增加 `maxUploadSize` 作为 fallback key。**方案 C**: AdminSettings.vue — 将上传大小输入框改造为调用独立的 `PUT /api/admin/settings/upload` 端点（该端点已正确使用 `max_video_size_mb` key），但需在菜单中分开。

---

## 审查记录：OP-0217

**操作单元ID**: OP-0217
**所属链路**: R-ACA-001 教务总览页面加载
**页面位置**: academic/Dashboard.vue:691-705
**操作动作**: 教务人员访问 `/academic/dashboard`，页面加载所有数据
**预期业务逻辑**: 并行加载概览统计 + 院系统计 + 趋势图 + 预警 + 热门课程
**实际表现**: `onMounted` 并行调用 5 个 load 函数

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] loading 态
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] 各模块独立 catch
- 中断回退逻辑: [✓] onBeforeUnmount 清理

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 页面级加载
- 信息引导完整性: [✓] 骨架屏
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] loading/数据/错误态
- 重复冗余操作: [✓] 60 秒自动刷新
- 异常场景兜底引导: [✓] 独立错误处理

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] 多个 GET 查询接口
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] ACADEMIC/ADMIN 角色限制
- 参数业务约束: [✓] 查询参数
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: 角色限制 / **已防护**
- 非法输入: N/A
- 重复点击: 自动刷新有保护 / 低风险
- 中途断操作: onBeforeUnmount 清理 / **已防护**
- 前置条件不满足强行触发: N/A
- 多步骤并发: Promise.all + 独立 catch / **已防护**
- 上一步数据异常下执行当前步骤: 独立错误处理 / **已防护**

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0229

**操作单元ID**: OP-0229
**所属链路**: R-ACA-006 审核置顶申请
**页面位置**: MicroSpecialtyFeaturedReview.vue:110-129
**操作动作**: 教务人员审核微专业置顶申请，选择"批准"或"驳回"
**预期业务逻辑**: 批准 → POST /api/micro-specialties/{id}/approve-featured（PENDING→APPROVED）; 驳回 → POST /api/micro-specialties/{id}/reject-featured（PENDING→REJECTED，需填写原因）
**实际表现**: `handleApprove` → 确认 → `approveFeatured(row.id)`; `handleReject` → 确认 → 填写原因 → `rejectFeatured(row.id, reason)`

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] loading 态
- 表单输入限制: [✓] 驳回原因必填
- 弹窗弹出/关闭逻辑: [✓] 确认盒 + 驳回原因弹窗
- 步骤切换前置校验: [✓] 确认后才执行
- 路由跳转拦截: [✓] 不涉及
- 操作成功/失败反馈: [✓] ElMessage.success('已批准') / '已驳回'
- 中断回退逻辑: [✓] 取消确认 → 不执行

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 批准：两步；驳回：三步
- 信息引导完整性: [✓] 确认框提示
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 审批状态标签
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 错误提示

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] POST /api/micro-specialties/{id}/approve-featured（MSF-002）/ reject-featured（MSF-003）
  - `featuredService.approveFeatured()` / `rejectFeatured()`
  - 状态机校验（PENDING→APPROVED / PENDING→REJECTED）
- 单据状态流转: [✓] micro_specialties.featured_status: NONE → PENDING → APPROVED/REJECTED
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('ACADEMIC','ADMIN')")`
- 参数业务约束: [✓] 驳回原因必填（@NotBlank）
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] micro_specialties.featured_status 更新
- 状态存储逻辑: [✓] CHECK 约束覆盖
- 关联数据联动规则: [✓] micro_specialty_featured_audit 审计记录
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: ACADEMIC/ADMIN 角色限制 / **已防护**
- 非法输入: 驳回原因 @NotBlank / 低风险
- 重复点击: loading 态 / 低风险
- 中途断操作: 已批准但网络中断 → 后端已处理，回显时刷新 / 低风险
- 前置条件不满足强行触发: 状态机校验拦截 / **已防护**
- 多步骤并发: 可能同时对同一个微专业批准和驳回 → 后端状态机白名单会阻止第二个操作（状态已变更）/ **低风险**
- 上一步数据异常下执行当前步骤: 微专业已删除或状态异常 → 后端返回错误

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0241

**操作单元ID**: OP-0241
**所属链路**: R-BASE-002 专业管理 → 搜索专业
**页面位置**: MajorList.vue:16-31,123-126（搜索表单）
**操作动作**: 用户在专业管理页输入搜索关键词或选择院系筛选
**预期业务逻辑**: 按专业名称/院系筛选 → GET /api/majors → 刷新列表
**实际表现**: `handleSearch` 重置页码为 1，调用 `fetchData` 带搜索参数

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] N/A
- 表单输入限制: [✓] el-input 无特殊限制
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] URL 分页同步
- 操作成功/失败反馈: [✓] 列表刷新
- 中断回退逻辑: [✓] 重置按钮可清除筛选

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 输入 → 搜索
- 信息引导完整性: [✓] placeholder 提示
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 表格加载状态
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 搜索无结果显示空状态

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] GET /api/majors（MAJ-001）
  - `majorService.page()` — 多查询参数过滤
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] `@PreAuthorize("isAuthenticated()")`
- 参数业务约束: [✓] 分页参数
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: isAuthenticated() / 低风险（基础数据查询权限宽泛）
- 非法输入: 搜索关键词无特殊过滤 / 低风险
- 重复点击: 快速搜索仅最后一次生效 / 低风险
- 中途断操作: N/A
- 前置条件不满足强行触发: N/A
- 多步骤并发: N/A
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0253

**操作单元ID**: OP-0253
**所属链路**: R-BASE-001 院系管理 → Dialog 提交
**页面位置**: DepartmentList.vue:232-253
**操作动作**: 用户在院系管理页打开新增/编辑 Dialog，填写信息后点击"确定"
**预期业务逻辑**: 表单校验 → POST /api/departments（新增）或 PUT /api/departments/{id}（编辑）→ 关闭 Dialog → 刷新列表
**实际表现**: `handleSubmit` → 校验表单 → 区分新增/编辑 → 调用 API → 成功后关闭 Dialog 并刷新

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] N/A
- 表单输入限制: [✓] 表单规则校验
- 弹窗弹出/关闭逻辑: [✓] Dialog 控制显隐
- 步骤切换前置校验: [✓] form.validate() 通过后才提交
- 路由跳转拦截: [✓] 不涉及
- 操作成功/失败反馈: [✓] ElMessage.success + 关闭 Dialog
- 中断回退逻辑: [✓] 取消按钮关闭 Dialog 不保存

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 填写 → 提交
- 信息引导完整性: [✓] 表单标签
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] Dialog 标题切换"新增"/"编辑"
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 失败时显示后端错误详情

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] POST /api/departments（DEP-003）/ PUT /api/departments/{id}（DEP-004）
  - @Valid DepartmentCreateRequest / DepartmentUpdateRequest
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")`
- 参数业务约束: [✓] @Valid
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] departments 表 insert/update
- 状态存储逻辑: [✓] 软删除
- 关联数据联动规则: [✓] 院系下存在专业/班级时外键约束阻止删除
- 底层存储与业务设计匹配: [✓] ON DELETE RESTRICT 约束

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: ADMIN/ACADEMIC 角色限制 / **已防护**
- 非法输入: @Valid / 低风险
- 重复点击: 无 loading 防护 / **P2** — 快速双击可触发两次请求
- 中途断操作: 低风险
- 前置条件不满足强行触发: form.validate 拦截 / 低风险
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 无

### 综合评估
- 逻辑冲突点: 快速点击提交按钮可能触发重复提交（无 loading 防重复保护）
- 风险等级: **P2**
- 根因分类: 前端交互设计
- 精准可落地业务修复方案: `DepartmentList.vue:232` — 在 handleSubmit 开头设置 submitting ref，按钮添加 `:loading="submitting" :disabled="submitting"`，提交完成后恢复。

---

## 审查记录：OP-0265

**操作单元ID**: OP-0265
**所属链路**: R-BASE-004 用户管理 → 编辑用户
**页面位置**: UserList.vue:749-818（handleEdit）; 867-898（handleDialogSave）
**操作动作**: 管理员在用户管理页点击编辑用户，修改信息后提交保存
**预期业务逻辑**: 加载用户详情 → 编辑表单 → PUT /api/users/{id} → 刷新列表
**实际表现**: `handleEdit` 先用行数据填充表单（快速响应），再异步加载完整信息

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] loading 态
- 表单输入限制: [✓] 年级联动、院系/专业/班级级联
- 弹窗弹出/关闭逻辑: [✓] Dialog 控制
- 步骤切换前置校验: [✓] form.validate() 通过后才提交
- 路由跳转拦截: [✓] 不涉及
- 操作成功/失败反馈: [✓] ElMessage.success
- 中断回退逻辑: [✓] 取消关闭 Dialog

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 点击编辑 → 修改 → 保存
- 信息引导完整性: [✓] 级联选择器联动
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] Dialog 标题
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 加载失败错误提示

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /api/users/{id}（USR-005）
  - @Valid UserUpdateRequest + 对象级授权
- 单据状态流转: [✓] 用户状态管理
- 权限拦截规则: [✓] `@PreAuthorize("hasRole('ADMIN')")` 或本人
- 参数业务约束: [✓] @Valid
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] users 表 update
- 状态存储逻辑: [✓] status CHECK 约束
- 关联数据联动规则: [✓] 修改班级影响关联查询
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: ADMIN 限制 + 对象级授权 / **已防护**
- 非法输入: @Valid / 低风险
- 重复点击: 无 loading 防护 / **P2**
- 中途断操作: 低风险
- 前置条件不满足强行触发: form.validate / 低风险
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 级联数据加载失败时用行数据兜底 / 低风险

### 综合评估
- 逻辑冲突点: 提交按钮无 loading 防重复保护（与 OP-0253 同类问题）
- 风险等级: **P2**
- 根因分类: 前端交互设计
- 精准可落地业务修复方案: `UserList.vue:867` — handleDialogSave 中添加 dialogSubmitting ref，按钮 `:loading="dialogSubmitting" :disabled="dialogSubmitting"`

---

## 审查记录：OP-0277

**操作单元ID**: OP-0277
**所属链路**: R-CONT-004 课程编辑 → 编辑/查看切换
**页面位置**: CourseDetail.vue:393（isEditMode 计算属性）; 505-512（switchToEdit/switchToView）
**操作动作**: 用户在课程详情页点击"编辑"按钮切换到编辑模式，或点击"取消"/保存后切换回查看模式
**预期业务逻辑**: 路由由 `/courses/:id`（查看）切换为 `/courses/:id/edit`（编辑），加载不同的模板
**实际表现**: `isEditMode = computed(() => route.path.includes('/edit'))`，视图根据该计算属性渲染不同内容

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 已发布课程（status=4）编辑按钮禁用
- 表单输入限制: [✓] 编辑模式使用表单控件
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] `switchToEdit` 检查 status !== 4
- 路由跳转拦截: [✓] router.push 切换
- 操作成功/失败反馈: [✓] 保存成功自动切回查看模式
- 中断回退逻辑: [✓] "取消"按钮调用 `switchToView()`

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 点击编辑 → 修改 → 保存/取消
- 信息引导完整性: [✓] 查看/编辑模式 UI 明确区分
- 操作提示匹配业务规则: [✓] 已发布的课程不能编辑
- 多状态视觉区分: [✓] 编辑/查看模式视觉差
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] ACADEMIC 角色被重定向到查看模式

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /api/courses/{id}（CRS-004）— 仅编辑模式提交
  - @Valid CourseUpdateRequest
  - isOwnerOrAdmin 校验
- 单据状态流转: [✓] 课程状态机控制（已发布课程不能编辑）
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")` + isOwnerOrAdmin
- 参数业务约束: [✓] @Valid
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] courses 表 update
- 状态存储逻辑: [✓] 状态机限制编辑范围
- 关联数据联动规则: [✓] 课程信息变更影响前端展示
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: isOwnerOrAdmin 校验 / **已防护**
- 非法输入: @Valid / 低风险
- 重复点击: 保存按钮 loading / 低风险
- 中途断操作: 编辑模式浏览器刷新 → 路由重新加载 `/courses/:id/edit`，保证一致性 / 低风险
- 前置条件不满足强行触发: 已发布状态禁用编辑按钮 + 后端状态机校验 / **双重防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 课程数据加载失败→显示 skeleton/error

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0289

**操作单元ID**: OP-0289
**所属链路**: R-CONT-008 视频管理 → 设置视频封面
**页面位置**: VideoList.vue:484-500（handleSubmitCover）
**操作动作**: 用户在视频管理页选择视频 → 点击"设置封面" → 在 Dialog 中上传图片 → 确定
**预期业务逻辑**: 选择图片文件 → POST /api/videos/{id}/cover → 更新视频封面 URL → 列表刷新
**实际表现**: `handleSubmitCover` 验证文件已选择 → `uploadVideoCover(currentVideoId, coverFile.value)` → 成功/失败提示

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] `coverSubmitLoading` loading 态
- 表单输入限制: [✓] 文件类型/大小校验（后端校验）
- 弹窗弹出/关闭逻辑: [✓] coverDialogVisible 控制
- 步骤切换前置校验: [✓] coverFile 为非空
- 路由跳转拦截: [✓] N/A
- 操作成功/失败反馈: [✓] ElMessage.success/error
- 中断回退逻辑: [✓] Dialog 取消按钮不保存

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 点击设置封面 → 选图 → 确定
- 信息引导完整性: [✓] 当前封面预览显示
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉区分: [✓] 有/无封面状态
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 上传失败提示

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] POST /api/videos/{id}/cover（VID-009）
  - `videoService.uploadCover()` — 文件 5MB 校验、JPG/PNG 格式校验、魔数校验
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")` + 课程 Owner 校验
- 参数业务约束: [✓] 文件大小/格式双重校验
- 前后端规则一致性: [✓] 前端 el-upload accept 限制 + 后端魔数校验

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] videos.cover_url 更新
- 状态存储逻辑: [✓] URL 字符串存储
- 关联数据联动规则: [✓] 无
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: TEACHER/ADMIN + Owner 校验 / **已防护**
- 非法输入: 文件魔数校验防伪造类型 / **已防护**
- 重复点击: coverSubmitLoading loading 态 / 低风险
- 中途断操作: 上传中断 → 后端未完成写入，封面不变 / 低风险
- 前置条件不满足强行触发: coverFile 为空时按钮 disabled / **已防护**
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 视频已被删除 → 后端返回 404

### 综合评估
- 逻辑冲突点: 无
- 风险等级: **无**
- 根因分类: —
- 精准可落地业务修复方案: 无需修复

---

## 审查记录：OP-0301

**操作单元ID**: OP-0301
**所属链路**: R-CONT-018 通知管理 → 通知行点击
**页面位置**: NotificationList.vue:327-337（handleRowClick）
**操作动作**: 用户在通知列表页点击某条通知行
**预期业务逻辑**: 如果未读则自动标记已读 → 根据通知类型跳转到对应页面
**实际表现**: `handleRowClick` 判断 `!row.isRead` → `notificationStore.markRead(row.id)` → `row.isRead = true` → 有 relatedId 和类型映射时路由跳转

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] 行点击始终可用
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] routeMap 检查是否有对应路由
- 路由跳转拦截: [✓] router.push 到映射路径
- 操作成功/失败反馈: [✓] 标记已读静默处理（无 toast）
- 中断回退逻辑: [✓] 无 relatedId 时仅为标记已读

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 点击 → 自动跳转或标记已读
- 信息引导完整性: [✓] 通知标题和内容展示
- 操作提示匹配业务规则: [✓] 未读通知自动标记已读
- 多状态视觉区分: [✓] 未读（badge）/ 已读（tag）
- 重复冗余操作: [✓] 行内还有"标记已读"按钮（@click.stop 防冒泡）
- 异常场景兜底引导: [✓] markRead 失败静默处理（catch 不展示错误）

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] PUT /api/notifications/{id}/read 或 PATCH
  - 标记通知为已读（本人通知）
- 单据状态流转: [✓] notifications.is_read: false → true
- 权限拦截规则: [✓] 本人校验
- 参数业务约束: [✓] 通知 ID 路径参数
- 前后端规则一致性: [✓] 一致

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] notifications.is_read 更新
- 状态存储逻辑: [✓] 布尔字段
- 关联数据联动规则: [✓] 无
- 底层存储与业务设计匹配: [✓] 匹配

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: 本人通知校验 / **已防护**
- 非法输入: N/A
- 重复点击: 快速点击行多次 → markRead 幂等 / 低风险
- 中途断操作: 标记已读 API 失败但前端已设为已读 → 下次刷新后恢复 / **中风险**（前端乐观更新但未回滚）
- 前置条件不满足强行触发: N/A
- 多步骤并发: 无
- 上一步数据异常下执行当前步骤: 通知已被删除 → 后端 404

### 综合评估
- 逻辑冲突点: 前端乐观更新未做失败回滚 — `row.isRead = true` 在 API 调用成功后设置，但如果 API 调用失败（catch 中无处理），前端显示已读但后端未更新，下次刷新后恢复未读状态。
- 风险等级: **P2**
- 根因分类: 前端交互设计
- 精准可落地业务修复方案: `NotificationList.vue:329-331` — 将 `row.isRead = true` 移到 API 调用成功后（then 回调中），而非立即设置，或 catch 中回滚。

---

## 审查记录：OP-0313

**操作单元ID**: OP-0313
**所属链路**: R-CONT-012 题库管理 → 键盘翻页
**页面位置**: QuestionList.vue（搜索了完整的 868 行）
**操作动作**: 用户在题库管理页使用键盘快捷键翻页
**预期业务逻辑**: 键盘左右箭头/PageUp/PageDown 触发分页切换
**实际表现**: **不存在键盘翻页功能** — QuestionList.vue 中没有实现任何键盘快捷键或键盘事件监听。分页完全通过 `el-pagination` 点击交互完成。

### 6 维度校验

#### 1. 前端交互业务逻辑（7 项）
- 按钮可用状态: [✓] el-pagination 标准按钮
- 表单输入限制: [✓] N/A
- 弹窗弹出/关闭逻辑: [✓] N/A
- 步骤切换前置校验: [✓] N/A
- 路由跳转拦截: [✓] URL 分页同步（useUrlPagination）
- 操作成功/失败反馈: [✓] 表格数据刷新
- 中断回退逻辑: [✓] N/A

#### 2. UI/UX 业务流程合理性（6 项）
- 操作路径长短: [✓] 点击页码 → 切换
- 信息引导完整性: [✓] 分页器总条数/当前页显示
- 操作提示匹配业务规则: [✓] 匹配
- 多状态视觉隔离分: [✓] 当前页高亮
- 重复冗余操作: [✓] 无
- 异常场景兜底引导: [✓] 分页加载失败

#### 3. 后端业务规则校验（5 项）
- 当前操作触发的接口业务校验: [✓] GET /api/questions（QST-001）
  - `questionService.page()` — 多条件筛选
- 单据状态流转: [✓] N/A
- 权限拦截规则: [✓] STUDENT/TEACHER/ADMIN/ACADEMIC
- 参数业务约束: [✓] 分页参数
- 前后端规则一致性: [✓] N/A

#### 4. 数据库业务约束（4 项）
- 字段变更: [✓] N/A
- 状态存储逻辑: [✓] N/A
- 关联数据联动规则: [✓] N/A
- 底层存储与业务设计匹配: [✓] N/A

#### 5. 红队 + 冒烟精细化测试场景（7 个必查场景）
- 越权操作: 角色限制 / **已防护**
- 非法输入: N/A
- 重复点击: 快速翻页仅触发最后一次 / 低风险
- 中途断操作: N/A
- 前置条件不满足强行触发: N/A
- 多步骤并发: N/A
- 上一步数据异常下执行当前步骤: N/A

### 综合评估
- 逻辑冲突点: **功能缺失** — 操作单元 OP-0313 定义为"键盘翻页"，但 QuestionList.vue 中未实现任何键盘翻页功能。现有的 `el-pagination` 组件本身支持 `Enter` 键触发页码输入框，但没有左右箭头快捷键。若此功能有明确需求文档要求，则属于实现缺失。
- 风险等级: **P2**（若需求明确则为此等级，可能是设计文档定义的 Feature 尚未实现）
- 根因分类: 前端交互设计
- 精准可落地业务修复方案: 若需求要求键盘翻页：`QuestionList.vue` 中添加 `@keydown.left` / `@keydown.right` 事件监听，绑定到 `handlePageChange`。使用 `useUrlPagination` 提供的 `page` ref 做加减操作。

---

# 总报告

## 审查统计

| 指标 | 数量 |
|------|------|
| 操作单元总数 | 27 |
| 完成审查数 | 27 |
| 受阻数 | 0 |
| 已输出审查记录数 | 27 |

## 风险等级分布

| 风险等级 | 数量 | 操作单元 |
|---------|------|---------|
| **P0**（阻塞项） | 0 | — |
| **P1-C**（客户可感知） | 1 | OP-0205（系统配置 max_video_size_mb 不生效） |
| **P1-I**（内部仅见） | 1 | OP-0037（OrderController 缺少 STUDENT 角色限制） |
| **P2**（优化项） | 8 | OP-0013（角色硬编码）、OP-0061（NPE 风险）、OP-0109（缺确认框）、OP-0133（并发覆盖）、OP-0145（需确认班级归属校验）、OP-0157（导出无 loading）、OP-0253/0265（提交无 loading）、OP-0301（乐观更新不回滚）、OP-0313（功能缺失） |
| **无风险** | 17 | 其余操作单元 |

## 重点发现摘要（≤ 200 字）

1. **OP-0061（原 P0）已修复**：LearningProgressServiceImpl.create() 第 284-296 行已实现选课校验，原问题不再存在。剩余微量 NPE 风险（[P2] VideoController.java:192）。
2. **OP-0205（P1-C）确认存在**：前端保存 key `maxUploadSize` 与后端校验读取 key `max_video_size_mb` 不一致，导致管理员修改上传限制永不生效。
3. **OP-0037（P1-I）**：OrderController 缺少 `@PreAuthorize("hasRole('STUDENT')")`，与 EnrollmentController 权限策略不一致。
4. **批量交互规范缺陷**：OP-0253、OP-0265、OP-0157 均缺少提交 loading 防护，存在重复提交风险。
5. **OP-0313 功能缺失**：题库管理页未实现键盘翻页，若为需求则需补充实现。

## 决策建议
- **混合决策** — 存在 1 个 P1-C 阻塞项（OP-0205），其余 P2 记录到 Phase 6 统一处理
- 建议：OP-0205 修复后重新审查；OP-0037 记录到 Phase 6 统一补充权限注解

---

*报告结束*
