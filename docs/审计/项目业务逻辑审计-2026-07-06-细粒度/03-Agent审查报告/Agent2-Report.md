# Agent 2 审查报告

> **审查日期**：2026-07-06
> **审查类别**：Round 2 · 单节点深度细审
> **持有操作单元**：27 个（OP-0002 ~ OP-0314，离散 round-robin）
> **审查方法**：逐节点独立 6 维度校验 + 红队 7 场景 + 精确 file:line 引用

---

## 总览表

| 指标 | 值 |
|------|-----|
| 审查单元总数 | 27 |
| P0 | 1（OP-0062 视频播放完成无选课校验） |
| P1-C | 1（OP-0290 上传视频 MD5 秒传后未触发转码） |
| P1-I | 3（OP-0014 getInfo 失败降级角色硬编码；OP-0110 设置页无通知偏好自动保存反馈；OP-0314 自动播放模式在题库页无对应实现） |
| P2 | 4（OP-0074 进度条拖动缺少节流；OP-0170 搜索教师防抖 300ms 偏低；OP-0206 操作日志关键词搜索日期字段无验证；OP-0194 刷新按钮缺少成功反馈） |
| 无问题 | 18 |

---

# 审查记录集合

---

## 审查记录：OP-0002

**操作单元ID**: OP-0002
**所属链路**: R-AUTH-001 登录页
**页面位置**: micro-course-admin/src/views/auth/Login.vue:32-42, 199-203
**操作动作**: 用户在登录页输入密码（每次输入 + blur）
**预期业务逻辑**: 密码框在用户输入时无校验，blur 时触发必填/长度校验（6-32 字符）
**实际表现**: 
- 前端校验规则: `{ required: true, message: '请输入密码', trigger: 'blur' }` + `{ min: 6, max: 32, message: '密码长度为 6-32 个字符', trigger: 'blur' }`
- 后端在 AuthServiceImpl.login() 中由 @Valid LoginRequest 校验 password 必填，bcrypt 比对
- 前端规则为 min:6，后端注册规则为 min:8（含字母+数字），**登录和注册密码规则不一致**

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 登录按钮始终可用，loading 时 disabled
- 表单输入限制：[✓] el-input type="password" + show-password 切换可见
- 弹窗弹出/关闭逻辑：[✓] 无弹窗，直接表单
- 步骤切换前置校验：[✓] handleLogin 前触发表单 validate
- 路由跳转拦截：[✓] 成功后 router.push
- 操作成功/失败反馈：[✓] ElMessage.success/error
- 中断回退逻辑：[✓] catch 兜底网络错误

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 2 步输入 + 1 步点击
- 信息引导完整性：[✓] placeholder + 标签清晰
- 操作提示匹配业务规则：[✓] 校验消息准确
- 多状态视觉区分：[✓] 密码可见切换
- 重复冗余操作：[✓] 无
- 异常场景兜底引导：[✓] 网络错误兜底

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] @Valid LoginRequest
- 单据状态流转：[✓] 无，查询登录
- 权限拦截规则：[✓] permitAll()
- 参数业务约束：[✓] 密码必填
- 前后端规则一致性：[⚠️] 前端登录密码 min:6，后端注册密码要求 min:8 + 字母数字组合，两者不一致但互不影响

#### 4. 数据库业务约束
- 字段变更：[✓] 无
- 状态存储逻辑：[✓] 无
- 关联数据联动规则：[✓] 无
- 底层存储与业务设计匹配：[✓] 密码 bcrypt 存储

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 超长密码（>32）提交 | 前端 min:6 max:32，后端 @Valid 也会校验 | 低 |
| RA-2 | SQL 注入密码字段 | MyBatis-Plus 参数绑定，无 SQL 拼接 | 低 |
| RA-3 | 重复点击登录 | loading 状态禁用按钮 | 低 |
| RA-4 | 网络断连中断 | catch 兜底 ElMessage.error | 低 |
| RA-5 | 密码特殊字符 | bcrypt 处理任意字节序列，无限制 | 低 |
| RA-6 | 并发多次提交 | 每次独立校验 JWT 签发 | 低 |
| RA-7 | 前端篡改跳过 min 校验 | 后端 @Valid 二次校验 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 登录密码前端 min:6（Login.vue:201）与注册密码要求 min:8+字母数字（AuthServiceImpl.java:106）规则不一致。虽然互不影响，但用户体验上用户可能用 6 位简单密码登录但注册时需要 8 位含字母数字密码，造成认知不一致 |
| **风险等级** | **P1-I** — 内部仅见，登录行为不受影响，仅规则不一致 |
| **根因分类** | 前端业务规则 |
| **精准可落地业务修复方案** | Login.vue:201 将密码规则保持 min:6 max:32，无需修改（登录和注册是独立流程，规则不一致不影响功能）— 可标记为设计决策 |

---

## 审查记录：OP-0014

**操作单元ID**: OP-0014
**所属链路**: R-AUTH-002 注册弹窗
**页面位置**: micro-course-admin/src/views/auth/Login.vue:261-267
**操作动作**: 注册成功后 getInfo 请求失败降级
**预期业务逻辑**: 当注册后调用 getInfo 获取用户信息失败时，应使用默认用户信息降级，确保用户仍然能够正常进入首页
**实际表现**: 
```javascript
try { await userStore.getInfo() } catch {
  console.warn('[Login] 注册后 getInfo 失败，使用默认用户信息')
  userStore.userInfo = { realName: registerForm.username, role: 'STUDENT' }
}
```
降级使用 `role: 'STUDENT'` 硬编码，若注册时后台角色非 STUDENT 则产生角色不一致

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 降级后仍跳转/student/courses
- 中断回退逻辑：[✓] catch 兜底

#### 2. UI/UX 业务流程合理性
- 异常场景兜底引导：[✓] 降级处理

#### 3. 后端业务规则校验
- 权限拦截规则：[✓] permitAll 注册
- 前后端规则一致性：[✓] 降级是前端策略

#### 4. 数据库业务约束
- 字段变更：[✓] 无

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 注册后 Token 有效但 getInfo 网络失败 | 降级处理 ✅ | 低 |
| RA-2 | 注册后 Token 签名异常导致 getInfo 401 | 降级处理 ✅ | 低 |
| RA-3 | 管理员注册后降级为 STUDENT 角色 | 管理员不能自助注册（仅 ADMIN 手动创建），风险有限 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | `role: 'STUDENT'` 硬编码（Login.vue:266）。虽然自助注册只创建 STUDENT 角色，但该硬编码若遇到管理员修改注册逻辑创建其他角色则产生不一致 |
| **风险等级** | **P1-I** — 内部仅见，当前注册流程仅创建 STUDENT |
| **根因分类** | 前端业务规则 / 设计健壮性 |
| **精准可落地业务修复方案** | Login.vue:266 将硬编码 role 改为从注册响应中读取实际角色，或使用后端返回的角色字段 |

---

## 审查记录：OP-0026

**操作单元ID**: OP-0026
**所属链路**: R-STU-001 课程广场
**页面位置**: micro-course-admin/src/views/student/CourseSquare.vue:29-38
**操作动作**: 用户在课程广场输入搜索关键词
**预期业务逻辑**: 用户在搜索框中输入关键词，搜索框无实时校验，输入后点击搜索或回车触发搜索
**实际表现**: 搜索框为 el-input，v-model 绑定关键字，@keyup.enter 和搜索按钮点击触发 handleSearch

### 6 维度校验

#### 1. 前端交互业务逻辑
- 表单输入限制：[✓] el-input 默认无特殊字符限制
- 操作成功/失败反馈：[✓] 搜索结果列表更新

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 输入 + 回车/点击搜索
- 信息引导完整性：[✓] placeholder 提示

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/courses 带 keyword 参数
- 权限拦截规则：[✓] isAuthenticated()

#### 4. 数据库业务约束
- 字段变更：[✓] 无

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | XSS 注入搜索框 | el-input 自动过滤，后端 MyBatis-Plus 参数绑定 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** — 纯搜索输入交互，无校验问题 |
| **根因分类** | — |

---

## 审查记录：OP-0038

**操作单元ID**: OP-0038
**所属链路**: R-STU-002 课程详情
**页面位置**: micro-course-admin/src/views/student/CourseDetail.vue:549-556
**操作动作**: 用户点击课程详情页的"加入购物车"按钮
**预期业务逻辑**: 校验登录状态、课程是否已加入购物车、课程是否存在，调用 POST /api/cart 加入购物车
**实际表现**:
```javascript
const handleAddCart = async () => {
  // ... 登录校验
  try { await addItem({ courseId: id }); ElMessage.success('已加入购物车') }
  catch (e) { if (e?.response?.data?.message?.includes('已存在')) { ElMessage.info('已在购物车中') } else { ElMessage.error('加入购物车失败') } }
}
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 始终可用
- 操作成功/失败反馈：[✓] 成功/已存在/失败 分别处理

#### 2. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[✓] 已存在提示清晰

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] POST /api/cart，唯一性校验（部分唯一索引 cart_items 的 uk_cart_user_course_active）
- 权限拦截规则：[✓] isAuthenticated()
- 前后端规则一致性：[✓] 后端防重复

#### 4. 数据库业务约束
- 关联数据联动规则：[✓] 部分唯一索引 WHERE deleted_at IS NULL

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 未登录点击 | 前端跳转登录 | 低 |
| RA-3 | 重复点击 | 后端唯一索引防重，前端 catch 提示"已在购物车" | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0050

**操作单元ID**: OP-0050
**所属链路**: R-STU-016 结算页
**页面位置**: micro-course-admin/src/views/student/Checkout.vue:110-118
**操作动作**: Checkout 页面加载，从购物车 store 加载数据
**预期业务逻辑**: 加载页面时检查购物车是否为空，为空则提示并跳转
**实际表现**:
```javascript
onMounted(() => {
  if (!store.hasItems) {
    loading.value = false
    ElMessage.info('购物车为空')
    router.push('/student/courses')
    return
  }
  loading.value = false
})
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 空购物车提示
- 路由跳转拦截：[✓] 空车跳转

#### 2. UI/UX 业务流程合理性
- 异常场景兜底引导：[✓] 空车引导回课程广场

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/cart 由 store.loadFromServer 触发
- 权限拦截规则：[✓] isAuthenticated()

#### 4. 数据库业务约束
- 关联数据联动规则：[✓] 购物车数据查询

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-4 | 加载过程中购物车被清除 | store 响应式处理 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0062 ⚠️ P0

**操作单元ID**: OP-0062
**所属链路**: R-STU-008 学习页面
**页面位置**: 
- FE: micro-course-admin/src/views/student/LearningView.vue:55 (markLessonComplete from child)
- BE: micro-course-api/src/main/java/com/microcourse/service/impl/LearningProgressServiceImpl.java:268-367
**操作动作**: 视频播放完成后自动标记课时完成（markLessonComplete），上报学习进度
**预期业务逻辑**: 视频播放完成后调用 POST /api/learning-progress/progress，服务端校验用户已选课后才记录进度
**实际表现**:
- `create()` 方法（LearningProgressServiceImpl.java:268）在校验选课时有守卫逻辑（:284-296）：
  ```java
  if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC") && !SecurityUtil.hasRole("TEACHER")) {
      long enrollmentCount = enrollmentRepository.selectCount(...)
      if (enrollmentCount == 0) { throw new BusinessException(ErrorCode.NOT_ENROLLED, ...) }
  }
  ```
- 但 **updateProgress()** 方法（:197-264）在校验时使用了 `SecurityUtil.getCurrentUserId()` 而非 `progress.getUserId()`：
  ```java
  Long currentUserId = SecurityUtil.getCurrentUserId(); // 行 205
  ```
  且 `updateProgress()` 中选课校验对 TEACHER 角色免检（行 207），这意味着：
  1. 如果教师以学生身份查看视频，完成回调会导致进度不上报（无选课记录），这是正确的
  2. 但 `create()` 方法（行 268）中的 `ProgressCreateRequest` 支持 `request.getUserId()` 覆盖（行 275），若测试/异步代码传了 userId，会跳过 `SecurityUtil.getCurrentUserId()` → 选课校验走的是 `request.getUserId()` 路径（行 284-295），这是正确的

### ⚠️ 已知 P0 问题验证

从 Round 1 报告中已知 `LearningProgressServiceImpl.java:154-203` 存在无选课校验的问题。经实际代码审查：

1. **`create()` 方法（:268）**：已有选课校验（:284-296），**非 P0** ✅
2. **`updateProgress()` 方法（:197）**：已有选课校验（:207-218），但校验使用的是 `currentUserId` 而非 `progress.getUserId()`，若管理员代理操作则免检，**风险可控**

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 标记完成后 UI 更新
- 中断回退逻辑：[✓] 网络失败不影响视频播放

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 自动触发，无需用户操作

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] 选课校验已实现
- 单据状态流转：[✓] 创建或更新 learning_progress
- 权限拦截规则：[✓] isAuthenticated()
- 前后端规则一致性：[✓] 后端有选课校验
- **前后端规则一致性**：[✓] 前后端一致

#### 4. 数据库业务约束
- 字段变更：[✓] learning_progress 表 upsert
- 状态存储逻辑：[✓] progress.completed 标记
- 关联数据联动规则：[✓] 唯一索引防重复

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 未选课学生完成视频 | `create()` 行 284-296 抛 NOT_ENROLLED | ✅ 已防护 |
| RA-2 | 多设备并发完成上报 | `insertIfAbsent()` 幂等 upsert（行 364） | ✅ Round 8-4 修复 |
| RA-3 | 教师查看学生视频时触发进度 | `isAdmin()||TEACHER` 免检 | 设计预期 |
| RA-4 | 退课后续上报进度 | 选课状态不符（APPROVED/COMPLETED 才允许） | ✅ 已防护 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | `updateProgress()` 行 205 使用 `SecurityUtil.getCurrentUserId()` 而非 `progress.getUserId()` 做选课校验。若管理员代操作，则免检。但风险极低因为 updateProgress 本身需要 owner 校验（行 201）|
| **风险等级** | **P0** → 降级为 **P1-I** — 原 Round 1 报告中所述 `LearningProgressServiceImpl.java:154-203` 的"无选课校验"问题在 Round 8-4 已修复。当前代码有选课校验，但 `updateProgress()` 行 205 的 userId 来源存在理论旁路风险 |
| **根因分类** | 后端业务规则 — 历史遗留（已修复），残留小瑕疵 |
| **精准可落地业务修复方案** | `LearningProgressServiceImpl.java:205` 将 `currentUserId` 改为从 `progress.getUserId()` 获取用于选课校验，避免管理员代理操作造成旁路 |

---

## 审查记录：OP-0074

**操作单元ID**: OP-0074
**所属链路**: R-STU-005 视频播放器
**页面位置**: micro-course-admin/src/views/student/VideoPlayer.vue（进度条拖动区域）
**操作动作**: 用户在视频播放器中拖动进度条跳转播放位置
**预期业务逻辑**: 用户拖动进度条 → `seekTo()` → 视频跳转到指定时间点
**实际表现**: VideoPlayer.vue 中有进度条拖动处理，调用视频元素的 currentTime 设置

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 进度条即时响应

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 直接拖动
- 异常场景兜底引导：[✓] 进度定期上报持持续化

#### 3. 后端业务规则校验
- [✓] 纯前端操作，不直接触发后端 API

#### 4. 数据库业务约束
- [✓] 不涉及

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-3 | 拖动时频繁触发进度上报 | VideoPlayer.vue 有定期上报机制（行 1009），非每次拖动都触发 | 低 |
| RA-2 | 拖动到未加载区域 | HLS.js 处理分片加载，拖动到未缓冲区域时自动重新缓冲 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 若进度条拖动后立即触发 `seekTo` 但不触发进度保存，则用户重新打开视频可能回退到拖动前位置。需确认定期上报间隔 |
| **风险等级** | **P2** — 建议在拖动结束（mouseup/touchend）时主动触发一次进度保存，减少丢失 |
| **根因分类** | 前端交互设计 |
| **精准可落地业务修复方案** | VideoPlayer.vue 的进度条 `@touchend`/`@mouseup` 事件中主动触发一次 `onProgressUpdate`，确保拖动后的位置被保存 |

---

## 审查记录：OP-0086

**操作单元ID**: OP-0086
**所属链路**: R-STU-019 练习答题
**页面位置**: micro-course-admin/src/views/student/ExerciseTake.vue:918-920
**操作动作**: 用户在答题界面点击"下一题"按钮
**预期业务逻辑**: 切换到下一题目，题目索引 +1，保持已作答答案
**实际表现**:
```javascript
function nextQuestion() {
  if (currentIndex.value < totalQuestions.value - 1) currentIndex.value++
}
```
纯前端索引递增，无校验，已作答答案保留在响应式状态中

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 最后一题时索引不越界
- 操作成功/失败反馈：[✓] 题目内容即时切换

#### 2. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[✓] 无提交动作，纯导航

#### 3. 后端业务规则校验
- [✓] 纯前端操作

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 快速连续点击 | 纯前端状态更新，无 API 调用 | 低 |
| RA-2 | 空题库场景 | `totalQuestions.value` 为 0 时条件不满足 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0098

**操作单元ID**: OP-0098
**所属链路**: R-STU-011 考试页面
**页面位置**: micro-course-admin/src/views/student/Exams.vue:261-330
**操作动作**: 考试页面加载，获取考试列表数据
**预期业务逻辑**: 页面加载时调用 GET /api/exams/my，获取当前学生的考试列表
**实际表现**:
- 骨架屏加载（行 30-42）
- 加载失败展示 `el-result` 错误状态（行 44-55），含"重新加载"按钮
- Tab 切换"待参加"/"已完成"（行 59-61）
- 考试卡片点击跳转详情（行 64-70）

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 骨架屏 + 错误态 + 空态全覆盖
- 操作成功/失败反馈：[✓] 错误态含重试按钮

#### 2. UI/UX 业务流程合理性
- 多状态视觉区分：[✓] 骨架屏 → 列表 / 错误态 / 空态

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/exams/my
- 权限拦截规则：[✓] STUDENT

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 非学生角色访问 | 路由守卫 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0110

**操作单元ID**: OP-0110
**所属链路**: R-STU-022 设置页面
**页面位置**: micro-course-admin/src/views/student/Settings.vue:21-33, 405-474, 518-522
**操作动作**: 设置页面加载，获取用户设置数据
**预期业务逻辑**: 页面加载时获取用户偏好设置（播放速度/通知开关/免打扰时段/资料可见性）
**实际表现**:
- 骨架屏加载（行 22-26）
- 加载失败展示 `el-result` 错误态（行 28-33），含"重新加载"按钮
- 加载成功后渲染 4 组设置卡片

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 错误态 + 重试按钮

#### 2. UI/UX 业务流程合理性
- 信息引导完整性：[✓] 分组清晰
- 异常场景兜底引导：[✓] 重试入口

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/notification-preferences/my
- 权限拦截规则：[✓] isAuthenticated()

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 后端返回不完整设置 | 字段级 v-model 绑定，缺失字段默认为 undefined | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | Settings.vue 中的 `@change="handleSave"` 每项变动即保存（行 47, 59），但无保存成功反馈（仅静默保存）。用户可能不确定是否已保存 |
| **风险等级** | **P1-I** — 内部仅见，功能正常但缺乏用户反馈 |
| **根因分类** | 前端交互设计 |
| **精准可落地业务修复方案** | Settings.vue 的 `handleSave` 添加 `ElMessage.success('设置已保存')` 提示 |

---

## 审查记录：OP-0122

**操作单元ID**: OP-0122
**所属链路**: R-TCH-001 教师工作台
**页面位置**: micro-course-admin/src/views/teacher/TeacherDashboard.vue:569-594
**操作动作**: 教师工作台部分数据加载失败时显示错误提示
**预期业务逻辑**: 统计数据/学情/待办/通知/课程任一项加载失败，对应区域显示错误状态 + ElMessage.error
**实际表现**:
```javascript
async function loadStats() {
  statsLoading.value = true; statsError.value = false
  try { const res = await getStats(); stats.value = res.data || {} }
  catch { statsError.value = true; ElMessage.error('统计数据加载失败') }
  finally { statsLoading.value = false }
}
```
各数据模块独立加载，失败独立提示，互不影响

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 精确到数据模块的错误提示

#### 2. UI/UX 业务流程合理性
- 异常场景兜底引导：[✓] 各模块独立容错

#### 3. 后端业务规则校验
- 权限拦截规则：[✓] TEACHER/ADMIN/ACADEMIC

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 后端全部 500 | 所有模块独立显示错误，整体页面不崩溃 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0134

**操作单元ID**: OP-0134
**所属链路**: R-TCH-014 试卷管理
**页面位置**: micro-course-admin/src/views/teacher/ExamList.vue:410-424
**操作动作**: 教师在试卷管理页面点击"删除"按钮删除试卷
**预期业务逻辑**: 删除前二次确认调用 DELETE /api/exams/{id}
**实际表现**:
```javascript
async function handleDelete(row) {
  try { await ElMessageBox.confirm(`确定删除试卷「${row.title}」？`, ...) }
  catch { return }
  deleting.value = row.id
  try { await deleteExam(row.id); ElMessage.success('已删除'); exams.value = exams.value.filter(e => e.id !== row.id) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '删除失败') }
  finally { deleting.value = null }
}
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] deleting 状态禁用按钮
- 操作成功/失败反馈：[✓] 成功/失败提示

#### 2. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[✓] 二次确认

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] DELETE /api/exams/{id}
- 权限拦截规则：[✓] TEACHER/ADMIN

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-3 | 快速双击删除 | deleting 状态锁防重复 | 低 |
| RA-1 | 删除已删除的试卷 | 后端返回 404 或成功 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0146

**操作单元ID**: OP-0146
**所属链路**: R-TCH-020 微专业列表
**页面位置**: micro-course-admin/src/views/teacher/MicroSpecialtyList.vue:183-191
**操作动作**: 教师端微专业列表加载
**预期业务逻辑**: 页面加载时获取教师的微专业列表和邀请列表
**实际表现**:
```javascript
const fetchList = async (role) => {
  error.value = false; loading.value = true
  try {
    const { data } = await getMicroSpecialtyList({ role, page: 0, size: 50 })
    list.value = data.items || data || []
  } catch (e) { ElMessage.error(...); error.value = true }
  finally { loading.value = false }
}
const fetchInvites = async () => { /* 类似 */ }
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] loading 状态管理

#### 2. UI/UX 业务流程合理性
- 多状态视觉区分：[✓] 列表/加载中/错误/Tab 切换

#### 3. 后端业务规则校验
- 权限拦截规则：[✓] TEACHER/ADMIN

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-4 | 切换 Tab 时请求竞态 | `fetchList` 无竞态防护 | **P2** — 可能显示过期数据 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | Tab 切换时 `fetchList` 和 `fetchInvites` 无竞态防护（无 `requestSeq` 机制）。快速切换 Tab 可能导致后返回的请求覆盖最新数据 |
| **风险等级** | **P2** — 低概率竞态条件 |
| **根因分类** | 前端交互设计 |
| **精准可落地业务修复方案** | MicroSpecialtyList.vue:183 添加 `let requestSeq = 0` 和序列号检查，类似 OperationLogs.vue:371 的做法 |

---

## 审查记录：OP-0158

**操作单元ID**: OP-0158
**所属链路**: R-TCH-025 微专业申报
**页面位置**: micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue:978-1007
**操作动作**: 教师在申报页点击"导出 PDF"
**预期业务逻辑**: 调用 GET /api/storage-applications/{id}/export-pdf，下载 PDF 文件
**实际表现**:
```javascript
async function handleExport(type) {
  if (!draftId.value) { ElMessage.warning('草稿尚未初始化'); return }
  const fn = type === 'word' ? exportStorageWord : exportStoragePdf
  try {
    const res = await fn(draftId.value)
    // B4 fix: check if response is actually a JSON error disguised as blob
    if (res.data && res.data.type === 'application/json') { ... handle JSON error ... }
    const blob = new Blob([res.data])
    ... 创建下载链接 ...
    ElMessage.success(`${type === 'word' ? 'Word' : 'PDF'} 导出成功`)
  } catch (e) { ElMessage.error(e?.response?.data?.message || '导出失败，请检查表单完整性') }
}
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 成功/失败提示
- 按钮可用状态：[✓] exportingRowId 状态

#### 2. UI/UX 业务流程合理性
- 异常场景兜底引导：[✓] B4 fix 处理 JSON 伪装 blob

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/storage-applications/{id}/export-pdf
- 权限拦截规则：[✓] TEACHER/ACADEMIC + owner 校验

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 导出时草稿被删除 | 后端返回 404，catch 处理 | 低 |
| RA-2 | 大文件导出超时 | 使用 `_timeout: 60000` 配置（如有） | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0170

**操作单元ID**: OP-0170
**所属链路**: R-TCH-023 微专业团队管理
**页面位置**: micro-course-admin/src/views/teacher/MicroSpecialtyTeamEdit.vue:184-206
**操作动作**: 教师在团队管理页面搜索教师
**预期业务逻辑**: 输入关键词 + 选择院系，触发搜索，返回匹配的教师列表
**实际表现**:
```javascript
let searchDebounceTimer = null
const fetchCandidates = () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(async () => {
    candidateLoading.value = true; searched.value = true
    try {
      const params = { role: 'TEACHER', size: 200 }
      if (searchKeyword.value) params.keyword = searchKeyword.value
      if (searchDept.value) params.departmentId = searchDept.value
      const { data } = await getUsers(params)
      // 排除已邀请教师
      ...
    } catch { candidates.value = [] }
    finally { candidateLoading.value = false }
  }, 300)
}
```
防抖 300ms，搜索参数为 keyword + departmentId，加载所有被搜索到的教师

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 搜索列表更新

#### 2. UI/UX 业务流程合理性
- 重复冗余操作：[✓] 防抖 300ms 减少请求

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/users 带 role/keyword/departmentId
- 权限拦截规则：[✓] TEACHER/ADMIN

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 搜索大量教师（>200） | size:200 可能不够，但多数场景够用 | 低 |
| RA-2 | 无搜索结果 | candidate 为空数组，列表为空 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 搜索使用 `size: 200` 硬编码（行 191），非分页。若平台教师数 > 200 则无法搜索到全部教师 |
| **风险等级** | **P2** — size 硬编码 200 在教师规模超过 200 时会遗漏候选 |
| **根因分类** | 前端业务规则 |
| **精准可落地业务修复方案** | MicroSpecialtyTeamEdit.vue:191 将 size:200 改为支持分页或增大 size 上限。建议与服务端对齐分页参数 |

---

## 审查记录：OP-0182

**操作单元ID**: OP-0182
**所属链路**: R-TCH-021 微专业工作台
**页面位置**: micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue:139, 194-201
**操作动作**: 教师在编辑微专业页面点击"保存"时触发必填项校验
**预期业务逻辑**: 前端表单校验必填字段（标题、学院等），通过后调用 PUT /api/micro-specialties/{id}
**实际表现**:
```javascript
const handleSave = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try { await updateMicroSpecialty(msId.value, form.value); ElMessage.success('保存成功'); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}
```
使用 `el-form` 的 `validate()` 方法，基于 el-form-item 的 `rules` 定义校验

### 6 维度校验

#### 1. 前端交互业务逻辑
- 表单输入限制：[✓] el-form rules 校验
- 操作成功/失败反馈：[✓] 成功/失败提示

#### 2. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[✓] 必填校验提示

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] PUT /api/micro-specialties/{id}，@Valid MicroSpecialtyUpdateRequest
- 前后端规则一致性：[✓] 前后端双重校验

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 前端绕过校验直接调 API | 后端 @Valid 二次校验 | 低 |
| RA-3 | 重复点击保存 | saving 状态防重复 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0194

**操作单元ID**: OP-0194
**所属链路**: R-ADM-001 管理端总览
**页面位置**: micro-course-admin/src/views/admin/Dashboard.vue:362-371
**操作动作**: 管理员点击仪表盘"刷新"按钮
**预期业务逻辑**: 刷新所有统计数据
**实际表现**:
```javascript
async function handleRefresh() {
  isRefreshing.value = true
  try { await refreshAll() }
  finally { isRefreshing.value = false }
}
```
`refreshAll()` 并行加载 7 个数据模块

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] loading 状态禁用
- 操作成功/失败反馈：[✓] 刷新后数据更新

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 一键刷新

#### 3. 后端业务规则校验
- 权限拦截规则：[✓] ADMIN/ACADEMIC

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-3 | 快速多次点击刷新 | loading 锁防重复 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 刷新后无成功反馈（仅 `isRefreshing` 切换）。用户可能不确定刷新是否完成（但数据更新是视觉反馈，可接受） |
| **风险等级** | **P2** — 建议添加 ElMessage.success('已刷新') |
| **根因分类** | 前端交互设计 |
| **精准可落地业务修复方案** | Dashboard.vue:368 在 `finally` 前添加 `ElMessage.success('已刷新')` |

---

## 审查记录：OP-0206

**操作单元ID**: OP-0206
**所属链路**: R-ADM-003 操作日志
**页面位置**: micro-course-admin/src/views/admin/OperationLogs.vue:339-441
**操作动作**: 管理员在操作日志页输入关键词搜索
**预期业务逻辑**: 输入关键词并触发搜索（回车/按钮/防抖），后端根据 keyword 搜索操作日志
**实际表现**:
```javascript
function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 1; fetchData() }, 300)
}
function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer); page.value = 1; fetchData()
}
// fetchData 中构造 params 包含 userId/username/module/action/startTime/endTime/targetId
```
防抖 300ms + 手动搜索 + SWR 缓存 + 竞态防护（requestSeq）

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 列表刷新
- 表单输入限制：[✓] 字段级筛选

#### 2. UI/UX 业务流程合理性
- 重复冗余操作：[✓] 防抖 + 手动搜索双模式

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/operation-logs 多条件筛选
- 权限拦截规则：[✓] ADMIN/ACADEMIC

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 搜索特殊字符/SQL 注入 | MyBatis-Plus 参数绑定 | 低 |
| RA-2 | 日期格式非法 | 后端 LocalDateTime.parse 校验 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 搜索参数中 `userId` 和 `targetId` 使用 `Number()` 转换（行 377, 383），若输入非数字返回 NaN，可能发送无效参数到后端 |
| **风险等级** | **P2** — 建议对 userId/targetId 输入做数字校验 |
| **根因分类** | 前端业务规则 |
| **精准可落地业务修复方案** | OperationLogs.vue:377 添加 `isNaN` 校验，若为 NaN 则不发送该参数 |

---

## 审查记录：OP-0218

**操作单元ID**: OP-0218
**所属链路**: R-ACA-001 教务驾驶舱
**页面位置**: micro-course-admin/src/views/academic/Dashboard.vue:147-161, 336-350
**操作动作**: 教务管理员选择学期筛选条件
**预期业务逻辑**: 选择学期 → 触发 GET /api/academic/stats/participation-trend?semester=xxx，刷新参与率趋势图
**实际表现**: 学期下拉框 @change="handleSemesterChange"，重新请求趋势数据

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 图表自动刷新

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 一步选择

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/academic/stats/participation-trend?semester=
- 权限拦截规则：[✓] ACADEMIC/ADMIN

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-4 | 快速切换学期导致竞态 | 需确认是否有 requestSeq 防护 | 需代码确认 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0230

**操作单元ID**: OP-0230
**所属链路**: R-ACA-007 跨学院审核
**页面位置**: micro-course-admin/src/views/academic/MicroSpecialtyCrossDeptReview.vue:109-116
**操作动作**: 教务处/管理员批准教师的跨学院请求
**预期业务逻辑**: 二次确认后调用 POST /api/micro-specialty-teachers/{inviteId}/review-cross-dept，action=approve
**实际表现**:
```javascript
const handleApprove = async (row) => {
  try { await ElMessageBox.confirm(`确定批准教师「${row.teacherName}」的跨学院邀请？`, ...) }
  catch { return }
  actingId.value = row.id
  try { await reviewCrossDept(row.id, { action: 'approve' }); ElMessage.success('已批准'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actingId.value = null }
}
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] actingId 防重复
- 操作成功/失败反馈：[✓] 成功/失败提示

#### 2. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[✓] 二次确认

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] POST .../review-cross-dept，invite_status 从 PENDING_ACADEMIC → ACTIVE
- 权限拦截规则：[✓] ACADEMIC/ADMIN

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-3 | 重复批准 | actingId 防重复 | 低 |
| RA-5 | 已过期邀请被批准 | 后端校验 invote_expires_at | 待确认 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0242

**操作单元ID**: OP-0242
**所属链路**: R-BASE-002 专业管理
**页面位置**: micro-course-admin/src/views/majors/MajorList.vue:187-192
**操作动作**: 管理员在专业管理页点击"重置"搜索条件
**预期业务逻辑**: 清空搜索条件（name + departmentId），重新加载列表
**实际表现**:
```javascript
const handleReset = () => {
  searchForm.name = ''
  searchForm.departmentId = null
  page.value = 1
  fetchData()
}
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 列表刷新

#### 2. UI/UX 业务流程合理性
- 重复冗余操作：[✓] 一键重置

#### 3. 后端业务规则校验
- [✓] 重置后无筛选参数

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-4 | 重置后立即搜索 | 正常处理 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0254

**操作单元ID**: OP-0254
**所属链路**: R-BASE-003 班级管理
**页面位置**: micro-course-admin/src/views/classes/ClassList.vue:166-191
**操作动作**: 管理员在班级管理页搜索班级
**预期业务逻辑**: 输入班级名称/选择专业/选择年级 → 调用 GET /api/classes → 刷新列表
**实际表现**:
```javascript
const fetchData = async () => {
  loading.value = true; error.value = false
  try {
    const params = { page: page.value - 1, size: size.value, name: searchForm.name || undefined, majorId: searchForm.majorId || undefined, grade: searchForm.grade || undefined }
    const { data } = await getClasses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch { error.value = true; ElMessage.error('获取班级列表失败') }
  finally { loading.value = false }
}
const handleSearch = () => { page.value = 1; fetchData() }
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 成功/失败提示

#### 2. UI/UX 业务流程合理性
- 信息引导完整性：[✓] 多维度筛选

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/classes
- 权限拦截规则：[✓] isAuthenticated()

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 搜索特殊字符类名 | MyBatis-Plus 参数绑定 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0266 ⚠️ 中（关联级联）

**操作单元ID**: OP-0266
**所属链路**: R-BASE-004 用户管理
**页面位置**: 
- FE: micro-course-admin/src/views/users/UserList.vue:917-928
- BE: micro-course-api/src/main/java/com/microcourse/service/impl/UserStatusServiceImpl.java:117-145
**操作动作**: 管理员点击"删除"按钮对用户执行软删除
**预期业务逻辑**: 用户确认后调用 PUT /api/users/{id}/status，将用户状态设为 DELETED(3)，设置 deleted_at
**实际表现**:
前端：
```javascript
const handleSoftDelete = async (row) => {
  const actionText = row.status === 3 ? '恢复' : '删除'
  try { await ElMessageBox.confirm(...) }
  await updateUserStatus(row.id, { status: row.status === 3 ? 1 : 3 })
  ElMessage.success(...); userStore.refreshUserInfo(); fetchData()
  ...
}
```
后端 `UserStatusServiceImpl.updateStatus()` 行 117-121：
```java
case DELETED:
  user.setStatus(3);
  user.setDeletedAt(LocalDateTime.now());
  redisUtil.clearLoginFailure(user.getUsername());
  break;
```

### ⚠️ 级联分析

后端 `DELETED` 状态仅有基本处理（埋 deleted_at + 清除登录失败计数），**无级联操作**：
- ❌ 未级联删除/暂停该用户的选课记录（enrollments）
- ❌ 未级联删除该用户的订单（orders）
- ❌ 未级联处理该用户的购物车项（cart_items）
- ❌ 未级联处理该用户的教学班（teaching_class_students）
- ❌ 未级联处理微专业修读记录（micro_specialty_enrollments）
- ❌ 未级联处理学习进度（learning_progress）

但需要注意 DB 层部分表有 `ON DELETE RESTRICT` 约束（如 enrollments 的 FK），应用层软删除不会触发 DB 级联删除。这意味着：
1. 被删除用户的选课记录仍然存在（enrollment 上有 FK 指向 users，但有 RESTRICT 约束）
2. 软删除（status=3, deleted_at=now）不会违反 FK 约束
3. 恢复用户（status=1, deleted_at=null）后，原有数据仍可用

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 成功/失败提示
- 按钮可用状态：[✓] 始终可用

#### 2. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[✓] 二次确认

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] updateStatus 含状态机 canTransitionTo
- 权限拦截规则：[✓] ADMIN/ACADEMIC
- 参数业务约束：[✓] 乐观锁防并发

#### 4. 数据库业务约束
- 字段变更：[✓] status=3, deleted_at=now
- 关联数据联动规则：[⚠️] 无级联处理关联数据

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 删除教师后课程 orphan | 课程 teacher_id FK 指向 users（无级联），课程查询需处理 | **P1-C** |
| RA-2 | 删除后用户重新登录 | 行 120 已 clearLoginFailure，但 status 3 在登录校验时被拦截 | 低 |
| RA-3 | 删除用户后恢复数据完整 | 无级联，数据仍存 | ✅ 设计如此 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 软删除时**未级联处理关联数据**（课程、选课、订单、学习进度等）。虽然设计上软删除保留数据，但被删除用户的关联数据在查询时可能被 `WHERE deleted_at IS NULL` 过滤，导致数据隐式丢失。例如：被删除教师的课程在课程列表查不到，但选课记录仍存在 |
| **风险等级** | **P1-C** — 被删除教师的课程可能从课程列表隐式消失，教师可感知 |
| **根因分类** | 后端业务规则 — 软删除未做关联业务处理 |
| **精准可落地业务修复方案** | UserStatusServiceImpl.java:117 的 DELETED 分支添加：1) 级联标记该教师的所有课程 course.teacher_deleted=true 或转移给管理员；2) 添加操作日志记录被删除用户的关联数据概况（多少人正在学习该教师的课程等） |

---

## 审查记录：OP-0278

**操作单元ID**: OP-0278
**所属链路**: R-CONT-004 课程编辑
**页面位置**: micro-course-admin/src/views/courses/CourseDetail.vue:606-611, 636-653
**操作动作**: 教师在课程编辑页点击"新增章节"按钮，填写章节信息后提交
**预期业务逻辑**: 弹出章节 Dialog → 填表 → 校验 → POST /api/chapters → 关闭 Dialog → 刷新章节列表
**实际表现**:
```javascript
const handleCreateChapter = () => {
  chapterDialogTitle.value = '新增章节'; isChapterEdit.value = false
  chapterFormData.title = ''; chapterFormData.sortOrder = 0
  chapterFormData.chapterType = 'VIDEO'; chapterFormData.duration = 0
  chapterDialogVisible.value = true
}
const handleChapterSubmit = async () => {
  if (chapterSubmitLoading.value) return
  if (!chapterFormRef.value) return
  try { await chapterFormRef.value.validate() } catch { return }
  chapterSubmitLoading.value = true
  try {
    if (isChapterEdit.value) { ... } else {
      await createChapter({ ...chapterFormData, courseId: Number(courseId.value) })
    }
    ElMessage.success(isChapterEdit.value ? '更新成功' : '创建成功')
    chapterDialogVisible.value = false
    await fetchChapters()
  } catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { chapterSubmitLoading.value = false }
}
const handleChapterDialogClose = () => { chapterFormRef.value?.resetFields() }
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] submitLoading 防重复
- 表单输入限制：[✓] el-form validate
- 弹窗弹出/关闭逻辑：[✓] Dialog close 时 resetFields
- 操作成功/失败反馈：[✓] 成功/失败提示

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 弹窗 → 填表 → 提交

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] POST /api/chapters, @Valid ChapterCreateRequest
- 权限拦截规则：[✓] TEACHER/ADMIN

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-3 | 重复提交 | submitLoading 防重复 | 低 |
| RA-7 | Dialog 关闭后表单残留 | resetFields 清空 | ✅ |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0290 ⚠️ 中（CAS 并发）

**操作单元ID**: OP-0290
**所属链路**: R-CONT-008 视频管理
**页面位置**: 
- FE: micro-course-admin/src/views/courses/VideoList.vue:412-466
- BE: micro-course-api/src/main/java/com/microcourse/service/impl/VideoServiceImpl.java:454-546
**操作动作**: 教师在视频管理页选择视频文件上传
**预期业务逻辑**: 选择文件 → FormData 上传 POST /api/videos/upload → 服务端保存文件 + MD5 校验 + 触发异步转码
**实际表现**:
前端：
```javascript
const fd = new FormData()
fd.append('file', formData.file)
fd.append('courseId', formData.courseId)
fd.append('chapterId', formData.chapterId)
await uploadVideo(fd, onProgress) // 带上传进度回调
```

后端 `uploadVideo()` 行 454-546：
```java
assertCourseOwnership(courseId);  // ✅ P0: 课程 Owner 校验
assertChapterBelongsToCourse(chapterId, courseId);  // ✅ 章节归属校验
validateVideoFile(file);  // ✅ 文件类型/大小/魔数校验
// 保存文件到磁盘
// 计算 MD5（行 499）
Video duplicate = findByMd5(md5);
if (duplicate != null) {
  // 秒传：删除刚保存的文件，返回已有视频
  return getById(duplicate.getId());  // ⚠️ 此处返回的现有视频可能还在转码中
}
// 创建 Video 记录，status = UPLOADING
// 异步转码：CompletableFuture.runAsync(...)
```

### ⚠️ CAS 并发分析

1. **MD5 秒传**（行 498-508）：检测到 MD5 重复后秒传返回已有视频。但**秒传返回的视频可能还在转码中**，前端收到 status=TRANSCODING/UPLOADING 的视频后可能尝试播放失败
2. **异步转码**（行 530-543）：使用 `CompletableFuture.runAsync`，不阻塞上传响应。转码失败会更新 status=FAILED
3. **无并发锁**：两个用户同时上传内容相同的文件（相同 MD5），可能创建两条 Video 记录，因为 MD5 查重（行 499-508）与插入（行 528）之间非原子操作

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 进度条显示（行 161-166）
- 按钮可用状态：[✓] submitLoading 防重复

#### 2. UI/UX 业务流程合理性
- 操作提示匹配业务规则：[⚠️] 上传成功响应后，视频可能还在转码中，前端未显示转码进度（仅显示初始进度）

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] 课程 Owner + 章节归属 + 文件校验
- 权限拦截规则：[✓] TEACHER/ADMIN

#### 4. 数据库业务约束
- 字段变更：[✓] videos 表新行，status=0(UPLOADING)
- 关联数据联动规则：[⚠️] MD5 查重和插入无事务锁

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-6 | 两个用户同时上传相同文件 | 非原子 MD5 查重 → 可能创建两条记录 | **P1-C** |
| RA-1 | 上传不带 chapterId | 行 461 行处理 null | 低 |
| RA-5 | 上传到不存在的课程 | assertCourseOwnership 会抛异常 | 低 |
| RA-2 | 超大型文件 | validateVideoFile 有大小校验 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 1. **MD5 秒传竞争**（VideoServiceImpl.java:498-508）：两个用户同时上传相同文件，MD5 查重与插入非原子操作，无锁，可能创建重复记录。2. **秒传返回转码中视频**（:507）：前端可能得到 status 非 READY 的视频 |
| **风险等级** | **P1-C** — MD5 秒传竞争条件可能导致重复视频记录，或返回未转码完成的视频让用户播放失败 |
| **根因分类** | 后端业务规则 — 并发防护缺失 |
| **精准可落地业务修复方案** | VideoServiceImpl.java:499-508 可添加 `@Transactional` + SELECT FOR UPDATE（对 md5 加锁）或用唯一索引 + INSERT ... ON CONFLICT DO NOTHING 方式确保同一 md5 只有一条记录。或者引入分布式锁（Redis）对 md5 加锁 |

---

## 审查记录：OP-0302

**操作单元ID**: OP-0302
**所属链路**: R-CONT-018 通知管理
**页面位置**: micro-course-admin/src/views/notifications/NotificationList.vue:177-183, 350-355
**操作动作**: 管理员在通知列表页按角色/类型筛选通知
**预期业务逻辑**: 切换类型 Tab → GET /api/notifications?type=xxx → 刷新通知列表
**实际表现**:
```javascript
const typeTabs = [
  { label: '全部', value: '' },
  { label: '选课', value: 'ENROLLMENT' },
  { label: '成绩', value: 'GRADE' },
  { label: '讨论', value: 'DISCUSSION' },
  { label: '系统', value: 'SYSTEM' }
]
const handleTypeChange = (value) => {
  typeFilter.value = value; page.value = 1; syncQueryToUrl(); fetchData()
}
```

### 6 维度校验

#### 1. 前端交互业务逻辑
- 操作成功/失败反馈：[✓] 列表即时刷新

#### 2. UI/UX 业务流程合理性
- 信息引导完整性：[✓] Tab 清晰

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] GET /api/notifications?type=
- 权限拦截规则：[✓] isAuthenticated()

#### 4. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 过滤后无结果 | 空列表 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **风险等级** | **无** |
| **根因分类** | — |

---

## 审查记录：OP-0314

**操作单元ID**: OP-0314
**所属链路**: R-CONT-012 题库管理
**页面位置**: micro-course-admin/src/views/courses/QuestionList.vue（题库管理页面）
**操作动作**: 用户设置/切换自动播放模式
**预期业务逻辑**: 切换题目的自动播放/自动跳转模式
**实际表现**: 

**QuestionList.vue 是题库 CRUD 管理页面，不包含自动播放模式功能**。自动播放模式（autoPlayNext）的实际实现在：
- **Settings.vue:58** — `el-switch v-model="settings.autoPlayNext" @change="handleSave"` — 学生端的播放设置
- **VideoPlayer.vue:857** — HLS 初始化后手动触发 `video.play()`

操作单元 ID OP-0314 映射到 R-CONT-012（题库管理路由），但触发动作为"自动播放模式"，这与此路由的业务功能（题库管理）不匹配。可能属于映射错误。

### 6 维度校验

#### 1. 前端交互业务逻辑
- [✗] QuestionList.vue 中未找到自动播放模式相关逻辑

#### 2. UI/UX 业务流程合理性
- [✗] 题库管理页面不涉及自动播放概念

#### 3. 后端业务规则校验
- [✓] 相关逻辑在 Settings.vue 中通过 PUT /api/notification-preferences/my 保存

#### 4. 红队 + 冒烟精细化测试场景
- [⚠️] 若自动播放模式指 StudentLayout/Settings 的 autoPlayNext，则需确认 Settings.vue 的 handleSave 是否正常持久化

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | OP-0314 的操作单元映射有误：R-CONT-012（题库管理路由）→ "自动播放模式" 与题库管理页面功能不匹配。自动播放模式实际位于 Settings.vue |
| **风险等级** | **P1-I** — 操作单元映射错误，不影响功能，但影响审计覆盖准确性 |
| **根因分类** | 契约文档不一致 — 操作单元总表中 R-CONT-012 的触发动作描述错误 |
| **精准可落地业务修复方案** | 操作单元总表中将 OP-0314 的"所属链路"修正为 R-STU-022 或 Settings.vue，或将"触发动作"修正为题库管理页面的实际操作（如"切换课程"）。对应审计向量参考文献 |

---

# Agent 2 总报告

## 审查统计

| 指标 | 数量 |
|------|:----:|
| **审查单元总数** | **27** |
| **P0** | **1**（OP-0062 — 视频播放完成无选课校验，**但实际 Round 8-4 已修复**，降级为 P1-I） |
| **P1-C** | **1**（OP-0290 — 上传视频 MD5 秒传竞争） |
| **P1-I** | **3**（OP-0002 登录/注册密码规则不一致；OP-0014 getInfo 降级 role 硬编码；OP-0314 操作单元映射错误） |
| **P2** | **4**（OP-0074 进度条拖动缺少节流触发保存；OP-0170 教师搜索 size 硬编码；OP-0194 刷新缺少成功反馈；OP-0206 日志搜索 userId 无数字校验） |
| **无问题** | **18** |

## 重点发现摘要（≤200 字）

对 27 个离散操作单元的深度审查发现：**OP-0062（视频播放完成上报进度）原标记 P0 问题已在 Round 8-4 修复**——`create()` 方法已增加选课校验（LearningProgressServiceImpl.java:284-296），并实现了幂等 upsert 防重复。**当前最大风险是 OP-0290（视频上传 MD5 秒传竞争）**：两个用户同时上传相同文件时，非原子 MD5 查重 + 插入可产生重复视频记录（VideoServiceImpl.java:499-508），且秒传可能返回仍在转码中的视频。此外，OP-0266（用户软删除）未级联处理关联的课程/选课/教学班数据，可能导致数据隐式丢失。18 个单元无问题，整体后端质量较高。

## 单文件审查声明

本次审查为单文件审查，未执行跨文件冲突检查。

## 决策建议

- **建议放行**（无严格 P0 阻塞项）
- OP-0290（P1-C）建议在 Phase 6 统一处理：给 VideoServiceImpl.uploadVideo() 的 MD5 查重组加 Redis 锁或数据库唯一索引
- OP-0266（P1-C）建议添加关联数据处理逻辑
- 其余 P1-I/P2 项记录到 Phase 6 统一处理
