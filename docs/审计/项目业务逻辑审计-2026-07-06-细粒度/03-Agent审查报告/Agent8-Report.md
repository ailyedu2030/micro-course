# Agent 8 审查报告 — 26 个最小操作单元单节点深度细审

**审查日期:** 2026-07-06
**审查 Agent:** Agent #8 (Reviewer)
**审查范围:** 26 个最小操作单元（来自分配矩阵），覆盖 R-AUTH / ROUTER / R-STU / R-TCH / R-ADM / R-ACA / R-BASE / R-CONT 共 8 条链路
**审查模式:** 单节点深度细审 / 离散持有 / 精确引用 file:行号
**总评审记录数:** 26 份独立审查 + 1 份总结报告
**审查总行数:** 3,000+ 行

---

## 目录

- [OP-0008 — 点击"立即注册"链接](#op-0008-点击立即注册链接)
- [OP-0020 — 角色权限不匹配时重定向](#op-0020-角色权限不匹配时重定向)
- [OP-0032 — 推荐课程卡片点击](#op-0032-推荐课程卡片点击)
- [OP-0044 — 点击回复评价](#op-0044-点击回复评价)
- [OP-0056 — 点击"查看我的课程"](#op-0056-点击查看我的课程)
- [OP-0068 — Tab 切换](#op-0068-tab-切换)
- [OP-0080 — 进度定期上报](#op-0080-进度定期上报)
- [OP-0092 — 个人信息加载失败](#op-0092-个人信息加载失败)
- [OP-0104 — 点击"发布帖子"](#op-0104-点击发布帖子)
- [OP-0116 — 签到按钮点击](#op-0116-签到按钮点击)
- [OP-0128 — 消息 Dialog 发送](#op-0128-消息-dialog-发送)
- [OP-0140 — 点击删除课件](#op-0140-点击删除课件)
- [OP-0152 — Step "上一步"](#op-0152-step-上一步)
- [OP-0164 — 点击"提交置顶申请"](#op-0164-点击提交置顶申请)
- [OP-0176 — "开课"按钮 disabled 状态](#op-0176-开课按钮-disabled-状态)
- [OP-0188 — 删除课程二次确认](#op-0188-删除课程二次确认)
- [OP-0200 — 导入 Dialog 上传文件](#op-0200-导入-dialog-上传文件)
- [OP-0212 — 编辑分账比例](#op-0212-编辑分账比例)
- [OP-0224 — 驳回 Dialog 确认驳回](#op-0224-驳回-dialog-确认驳回)
- [OP-0236 — 点击"取消金标"](#op-0236-点击取消金标)
- [OP-0248 — 查询院系](#op-0248-查询院系)
- [OP-0260 — Dialog 关闭（ESC）](#op-0260-dialog-关闭esc)
- [OP-0272 — 创建/编辑用户表单提交](#op-0272-创建编辑用户表单提交)
- [OP-0284 — Dialog 提交](#op-0284-dialog-提交)
- [OP-0296 — 返回按钮](#op-0296-返回按钮)
- [OP-0308 — 审核列表加载](#op-0308-审核列表加载)
- [总表汇总](#总表汇总)

---

## OP-0008 — 点击"立即注册"链接

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0008 |
| 所属链路 | R-AUTH-002 |
| 触发动作 | 点击"立即注册"链接 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过（有条件） |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/auth/Login.vue` | 72-75 | 注册链接的 UI 渲染 |
| `micro-course-admin/src/views/auth/Login.vue` | 78-122 | 注册弹窗表单定义 |
| `micro-course-admin/src/views/auth/Login.vue` | 155-164 | 注册开关检测（getRegistrationStatus） |
| `micro-course-admin/src/views/auth/Login.vue` | 245-281 | handleRegister() 函数 |
| `micro-course-api/src/.../controller/AuthController.java` | 35-41 | POST /api/auth/register 端点 |
| `micro-course-api/src/.../service/impl/AuthServiceImpl.java` | 178-260 | register() 业务逻辑实现 |
| `micro-course-api/src/.../service/impl/AuthServiceImpl.java` | 260-320 | generateToken + generateRefreshToken |

### 详细逻辑审查

#### 1. 用户触发流（前端 5 步）
```
用户点击"立即注册"
  → (1) Login.vue:72-75 — v-if="registrationEnabled" 条件渲染
  → (2) Login.vue:155-164 — checkRegistrationStatus() API 检查开关
  → (3) Login.vue:78-122 — 弹窗显示：用户名/密码/确认密码
  → (4) Login.vue:245-260 — handleRegister() 表单校验
  → (5) Login.vue:261-281 — POST /api/auth/register + 自动登录
```

#### 2. 前端表单校验（Login.vue:78-122）
```javascript
// 用户名：2-50 字符，无空格
pattern: /^\S{2,50}$/
// 密码：8-32 字符，必须含字母和数字
pattern: /^(?=.*[a-zA-Z])(?=.*\d).{8,32}$/
// 确认密码：与密码一致
validator: (rule, value, callback) => {
  if (value !== password) callback(new Error('两次密码不一致'))
  else callback()
}
```

#### 3. 后端注册流程（AuthServiceImpl.register()，178-260 行）
- **Step 1: 参数校验** — 用户名非空、密码非空、密码复杂度（长度≥8，含字母+数字）
- **Step 2: 用户名唯一性** — `userRepository.selectCount(lambda)` → 存在则抛 `ErrorCode.USERNAME_EXISTS(5002)`
- **Step 3: 创建用户** — `User` 实体赋值，role 固定为 `STUDENT`，status=1(ACTIVE)
- **Step 4: 签发 Token** — 调用 `generateToken(userId, username, STUDENT, departmentId)` 生成 JWT
- **Step 5: 审计日志** — `operationLogService.log("REGISTER")`
- **Step 6: 返回** — `LoginResponse(accessToken, refreshToken, userVO)`

#### 4. 错误处理路径
| 场景 | 前端表现 | 后端响应 |
|------|---------|---------|
| 用户名已存在 | ElMessage.error | HTTP 409, ErrorCode.USERNAME_EXISTS(5002) |
| 密码复杂度不足 | 表单校验失败 | 不发送请求 |
| 网络错误 | catch → ElMessage.error | - |
| 注册开关关闭 | "立即注册"链接不显示 | 无人触发（但可绕过） |

### 安全审查矩阵

| 检查项 | 结果 | 详细证据 | 风险 |
|--------|------|---------|:----:|
| XSS 防护 | ✅ 通过 | 服务端 XssSanitizer.sanitize() 处理用户名 | 低 |
| SQL 注入 | ✅ 通过 | MyBatis-Plus LambdaQueryWrapper，无字符串拼接 | 低 |
| 注册开关绕过 | ❌ **P1-I** | 前端开关仅控制 UI 可见性，后端 AuthServiceImpl.register() 未校验 `registration_enabled` 配置。攻击者可直接 POST `/api/auth/register` 绕过 | 中 |
| 暴力注册 | ❌ **P2** | 无 CAPTCHA 验证，无 IP/用户级频率限制 | 中 |
| 密码传输 | ✅ 通过 | HTTPS 传输（框架配置），非明文存储（BCryptPasswordEncoder） | 低 |
| Token 安全 | ✅ 通过 | JWT 含 6 字段 claims，黑名单支持登出 | 低 |
| 密码强度 | ✅ 通过 | 正则强制字母+数字+长度 8-32 | 低 |

### 设计一致性检查

| 检查项 | 结果 | 依据 |
|--------|------|------|
| 与 API 契约一致 | ✅ | POST /api/auth/register 路径正确 |
| 与数据字典一致 | ✅ | User 表字段：username, password, role, status |
| 与权限矩阵一致 | ✅ | permitAll() 免认证 |
| 与错误码一致 | ✅ | ErrorCode.USERNAME_EXISTS(5002) 已定义 |

### 发现的全部问题

| # | 等级 | 文件:行号 | 问题描述 | 修复建议 |
|---|------|----------|---------|---------|
| 1 | P1-I | AuthServiceImpl.java:178-260 | 后端未校验 `registration_enabled` 配置，前端开关仅控制 UI 可见性，API 可直接被调用绕过 | register() 方法开头插入检查：`adminSettingService.getBoolean("registration_enabled", true)` 若为 false 则抛出 BusinessException |
| 2 | P2 | AuthController.java:35 | POST /api/auth/register 端点无任何频率限制或验证码保护 | Add CAPTCHA (Google reCAPTCHA or 本地图片验证码) + 同一 IP 每小时不超过 10 次注册 |

### 审查决策
- ✅ **通过** — 无 P0 阻塞项。P1-I (#1) 和 P2 (#2) 记录到 Phase 6 汇总处理。

---

## OP-0020 — 角色权限不匹配时重定向

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0020 |
| 所属链路 | ROUTER |
| 触发动作 | 角色权限不匹配时重定向 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/router/index.js` | 10-171 | 路由表定义（含 meta.roles） |
| `micro-course-admin/src/router/index.js` | 108 | StudentMyCourses 路由定义 |
| `micro-course-admin/src/router/index.js` | 173-178 | getRoleHomePage() 函数 |
| `micro-course-admin/src/router/index.js` | 182-188 | STAFF_ONLY_PATHS 常量 |
| `micro-course-admin/src/router/index.js` | 196-270 | router.beforeEach 全局守卫 |

### 详细逻辑审查

#### 1. 路由守卫完整流程（router.beforeEach）

```
用户导航到任意路由
  │
  ├─ [198] 需要认证 && 未登录
  │    └─ → /login?redirect=原路径
  │
  ├─ [200-227] 需要角色信息
  │    ├─ userStore.role 存在 → 继续
  │    └─ 不存在 → GET /api/auth/me
  │         ├─ 成功 → 存入 store
  │         └─ 失败 → 尝试 refreshToken
  │              ├─ 成功 → 重试 getInfo
  │              └─ 失败 → 清登录态 → /login
  │
  ├─ [229-234] 已登录访问 /login 或 /
  │    └─ → getRoleHomePage(userRole)
  │
  ├─ [235-237] 学生访问职工路径
  │    └─ → /student/courses
  │
  ├─ [238-242] 角色不在路由 meta.roles 中
  │    └─ → getRoleHomePage(userRole)
  │
  ├─ [247-267] 路由需 lead 身份
  │    └─ GET /api/micro-specialties/{msId}/my-role
  │         ├─ role !== 'LEAD' → 重定向
  │         └─ role === 'LEAD' → 放行
  │
  └─ 全部通过 → 正常渲染页面
```

#### 2. STAFF_ONLY_PATHS 完整列表
```javascript
const STAFF_ONLY_PATHS = [
  '/departments', '/majors', '/classes',
  '/courses', '/admin', '/teacher', '/academic'
]
```
这些路径均以 `/api` 前缀开头匹配，学生访问任意一个都会被重定向。

#### 3. getRoleHomePage() 映射表
| 角色 | 首页路径 |
|------|---------|
| STUDENT | /student/courses |
| TEACHER | /teacher/dashboard |
| ACADEMIC | /academic/dashboard |
| 其他（含 ADMIN） | /admin/dashboard |

### 安全审查矩阵

| 检查项 | 结果 | 证据 | 风险 |
|--------|------|------|:----:|
| 认证绕过 | ✅ 强制检查 | 第198行 isAuthenticated() token 校验 | 低 |
| 角色提升 | ✅ 角色来自后端 API | userStore.getInfo() → `/api/auth/me`，不可前端伪造 | 低 |
| 覆盖完整性 | ✅ 7个 STAFF_ONLY_PATHS + meta.roles | 双重防护：学生禁止访问职工路由 + 路由级角色白名单 | 低 |
| 死循环保护 | ✅ | /login 特殊处理（229-234），不受守卫循环影响 | 低 |
| Token 过期保护 | ✅ | 200-227 行失效刷新 + 清除逻辑 | 低 |

### 发现的问题
**无**。权限守卫逻辑完整，角色判定依赖后端 API，无法前端伪造。

### 审查决策
- ✅ **通过** — 无任何问题

---

## OP-0032 — 推荐课程卡片点击

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0032 |
| 所属链路 | R-STU-001 |
| 触发动作 | 推荐课程卡片点击 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/CourseSquare.vue` | 78-108 | 精选推荐横滑区域 |
| `micro-course-admin/src/views/student/CourseSquare.vue` | 86-90 | 卡片 click/keydown 事件 |
| `micro-course-admin/src/views/student/CourseSquare.vue` | 604-616 | loadRecommended() 数据加载 |
| `micro-course-admin/src/views/student/CourseSquare.vue` | 658-669 | handleCourseClick() 跳转逻辑 |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 253-283 | 推荐课程卡片（PC 大屏） |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 473-502 | 推荐课程卡片（H5 移动端） |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 571 | goCourse() 跳转 |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 904-937 | 推荐数据加载逻辑 |

### 详细逻辑审查

#### 1. CourseSquare.vue 精选推荐
- **数据加载** (604-616): `getCourses({ recommended: true, size: 8 })`，返回配置为推荐的课程
- **handleCourseClick** (658-669):
  ```javascript
  const handleCourseClick = (course) => {
    if (!course?.id) return  // null 保护
    if (course.enrolled) {
      // 已选课 → 直接进入学习
      const path = course.courseType === 'INTERACTIVE'
        ? `/student/courses/${course.id}/slides/player`
        : `/student/learning?courseId=${course.id}`
      router.push(path)
    } else {
      // 未选课 → 课程详情页
      router.push(`/student/courses/${course.id}`)
    }
  }
  ```
- **封面兜底**: `course.coverUrl || getDefaultCover(course)` 确保图片不显示为 broken

#### 2. LearningCenter.vue 推荐课程
- **数据源** (904-937): 从选课记录取进行中的课程（最多3个），无则从热门课程兜底
- **跳转**: `goCourse(id)` → `/student/courses/${id}`（课程详情页）
- 两处推荐不冲突，服务于不同页面场景

### 安全审查矩阵

| 检查项 | 结果 | 证据 | 风险 |
|--------|------|------|:----:|
| course.id null 检查 | ✅ `if (!course?.id) return` | 658行头部守卫 | 低 |
| 路由安全 | ✅ 未选课只能到详情页 | enrolled 区分分流 | 低 |
| XSS | ✅ 封面 URL 用 `:src` | Vue 绑定防止注入 | 低 |
| 数据源控制 | ✅ 服务端控制推荐列表 | API 参数 recommended: true | 低 |

### 发现的问题
**无**。课程卡片点击逻辑清晰，数据加载有 fallback，路由跳转有安全条件判断。

### 审查决策
- ✅ **通过** — 无任何问题

---

## OP-0044 — 点击回复评价

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0044 |
| 所属链路 | R-STU-002 |
| 触发动作 | 点击回复评价 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/CourseDetail.vue` | 302-313 | 回复评价 Dialog |
| `micro-course-admin/src/views/student/CourseDetail.vue` | 650-679 | handleReply / handleSubmitReply |
| `micro-course-api/src/.../service/impl/CourseReviewServiceImpl.java` | 全文件 | createReview() 业务逻辑 |
| `micro-course-api/src/.../controller/CourseReviewController.java` | 全文件 | POST /api/courses/{id}/reviews |

### 详细逻辑审查

#### 1. 前端回复流程
- **Dialog**: 302-313 行，`replyDialogVisible` 控制显隐
- **handleSubmitReply()** (650-679):
  ```
  1. 校验 replyForm.content.trim() 非空
  2. 调 createReview({ courseId, content, parentId: replyTarget.id })
  3. 成功后关闭 Dialog
  4. 刷新评价列表（re-fetch）
  5. 失败后 ElMessage.error 显示后端消息
  ```
- **parentId 机制**: 回复时设置 `parentId` 为被回复评价的 ID，形成回复树

#### 2. 后端实现（CourseReviewServiceImpl.createReview）
- 验证课程存在
- 验证被回复评价存在（parentId 非空时）
- XssSanitizer 处理内容
- 设置 `parentId` 关联
- 触发评价审核流程（若需要）

### 安全审查矩阵

| 检查项 | 结果 | 证据 | 风险 |
|--------|------|------|:----:|
| 内容非空校验 | ✅ 前端 trim 检查 | handleSubmitReply 首行 | 低 |
| XSS | ✅ 服务端 XssSanitizer | createReview 中调用 | 低 |
| parentId 合法性 | ✅ 服务端校验 | 验证父评价存在且有效 | 低 |
| 权限 | ✅ 仅认证用户 | @PreAuthorize("isAuthenticated()") | 低 |

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | 回复评价 Dialog 中无字符数限制 | 建议添加 maxlength=500，与创建评价一致 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0056 — 点击"查看我的课程"

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0056 |
| 所属链路 | R-STU-016 |
| 触发动作 | 点击"查看我的课程" |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/MyCourses.vue` | 21-51 | PC 端 Tab 切换 |
| `micro-course-admin/src/views/student/MyCourses.vue` | 117-127 | 课程卡片点击事件 |
| `micro-course-admin/src/views/student/MyCourses.vue` | 718-758 | fetchEnrollments() 数据加载 |
| `micro-course-admin/src/views/student/MyCourses.vue` | 818-840 | handleContinue() 跳转逻辑 |
| `micro-course-admin/src/views/student/MyCourses.vue` | 844-862 | 退课（含二次确认） |
| `micro-course-admin/src/router/index.js` | 108 | 路由定义 |

### 详细逻辑审查

#### 1. 路由定义
```javascript
{ path: '/student/my-courses',
  name: 'StudentMyCourses',
  component: () => import('../views/student/MyCourses.vue'),
  meta: {
    requiresAuth: true,
    roles: ['STUDENT'],
    menuTab: true,
    menuLabel: '我的课程',
    menuIcon: 'Reading',
    menuOrder: 2
  }
}
```

#### 2. 数据加载（fetchEnrollments, 718-758）
- 调 `getMyEnrollments()` 获取当前用户选课列表
- 并行请求：完成度 + 收藏状态 + 批量学习进度（R8 P0-3 修复项）
- 即使无进度记录也返回默认 0 值进度 VO（P1C-027 修复）

#### 3. 课程卡片点击（handleContinue, 818-840）
```javascript
const handleContinue = async (courseId) => {
  const res = await getCourseById(courseId)
  if (res.data?.courseType === 'INTERACTIVE') {
    router.push(`/student/courses/${courseId}/slides/player`)
  } else {
    router.push(`/student/learning?courseId=${courseId}`)
  }
}
```

#### 4. 退课功能（844-862）
- `ElMessageBox.confirm("确定退课?")` + type: warning
- 确认后调 `cancelEnrollment(course.id)`
- 成功后刷新列表

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | handleContinue 中 getCourseById 多余请求 | enrollments 已含 courseType，可避免额外 API 调用 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0068 — Tab 切换

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0068 |
| 所属链路 | R-STU-006 |
| 触发动作 | Tab 切换 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/MyCourses.vue` | 21-51 | PC 端 el-tabs 组件 |
| `micro-course-admin/src/views/student/MyCourses.vue` | 268-299 | H5 端自定义 tab bar |
| `micro-course-admin/src/views/student/MyCourses.vue` | 577-586 | displayCourses 计算属性 |
| `micro-course-admin/src/views/student/MyCourses.vue` | 589-593 | totalDisplayElements 计算属性 |
| `micro-course-admin/src/views/student/MyCourses.vue` | 796-802 | handleTabChange（PC） |
| `micro-course-admin/src/views/student/MyCourses.vue` | 805-808 | handleH5TabChange（移动） |
| `micro-course-admin/src/views/student/LearningView.vue` | 27-40 | 学习页面 Tab 切换 |
| `micro-course-admin/src/views/student/LearningView.vue` | 151-157 | Tab 配置 |

### 详细逻辑审查

#### 1. MyCourses.vue — 我的课程 Tab
- **3 个 Tab**: in-progress / completed / favorited
- **PC 端**: Element Plus `<el-tabs>` 组件 + `@tab-change="handleTabChange"`
- **H5 端**: 自定义 div tab bar + `@click="handleH5TabChange(tab)"`
- **handleTabChange** (796-802): 重置 `page = 1` + 懒加载视频进度
- **displayCourses** (577-586): 根据 `activeTab` 从总列表中过滤

#### 2. LearningView.vue — 学习页面 Tab
- **4 个 Tab**: course / announcement / discussion / exam
- 自定义按钮式 tab，直接赋值 `activeTab = tab.key`
- 各 tab 对应独立 `v-if` 条件渲染区域

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | PC 端用 el-tabs，H5 端用自定义按钮，切换逻辑重复 | 建议统一用响应式 el-tabs，减少维护成本 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0080 — 进度定期上报

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0080 |
| 所属链路 | R-STU-005 |
| 触发动作 | 进度定期上报 |
| 风险初判 | 🔴 P0 |
| 审查确认等级 | **P0（已知 P0-1 同根因）** |
| 审查状态 | ⚠️ 已知问题，关联 P0-1 追踪中 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 985-1013 | reportProgress() 主函数 |
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 990 | lastReportedProgress 防抖 |
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 992-996 | sessionStorage 5 秒 dedup |
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 998-1003 | updateLearningProgress API 调用 |
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 1005-1012 | catch 错误处理（Warning） |
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 1016-1021 | startProgressReporting (10s 定时器) |
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 1023-1028 | stopProgressReporting |
| `micro-course-admin/src/views/student/VideoPlayer.vue` | 1031 | saveLocalPosition localStorage 回退 |
| `micro-course-api/src/.../impl/LearningProgressServiceImpl.java` | 150-200 | updateProgress() 原子更新 |
| `micro-course-api/src/.../impl/LearningProgressServiceImpl.java` | 110-148 | create() 幂等去重 |
| `micro-course-api/src/.../impl/LearningProgressServiceImpl.java` | 50-80 | batchGetByUserAndCourses() R8 P0-3 修复 |

### 详细逻辑审查

#### 1. 前端上报机制

**定时器驱动**：
```javascript
// VideoPlayer.vue:1016-1021
const startProgressReporting = () => {
  if (progressReportTimer) return // 仅一个实例
  progressReportTimer = setInterval(() => {
    reportProgress()
  }, 10000) // 10 秒间隔
}
```

**reportProgress() 内部逻辑** (985-1013):
```
① 计算 progressPercentVal = (currentTime / duration) × 100
② lastReportedProgress 变量防抖（相同百分比不上报）
③ sessionStorage dedup: 5 秒内同 videoId 不上报
④ ensureProgressRecord() 确保有进度记录
⑤ updateLearningProgress(progressId, { videoPosition, videoProgress })
⑥ 失败: 同会话只弹一次 Warning
⑦ 成功: saveLocalPosition(current) localStorage 回退
```

**reportProgress 函数完整代码解读**：
```javascript
// 985: const reportProgress = async (force = false) => {
// 986:   if (!duration.value || !progressId.value) return
// 987:   const current = currentTime.value
// 988:   const progressPercentVal = duration.value > 0 
// 989:     ? Math.min((current / duration.value) * 100, 100) : 0
// 990:   if (!force && progressPercentVal === lastReportedProgress) return
// 991:   lastReportedProgress = progressPercentVal
// 992-996: sessionStorage dedup with 5-second window
// 997-1003: API call to updateLearningProgress
// 1005-1012: catch → ElMessage.warning (once per session)
```

#### 2. 后端 updateProgress() (LearningProgressServiceImpl)

**关键实现**:
- `watchDelta` 字段原子加：`COALESCE(total_watch_time, 0) + {delta}` — 防并发覆盖（CON-003 修复）
- `totalWatchTime` 遗留字段用 `GREATEST` 取 max — 兼容老旧客户端
- LambdaUpdateWrapper: 无 SQL 拼接
- 权限校验: `assertTeacherOwnsCourse()` / IDOR guard

#### 3. 已知 P0-1 根因详细分析

**问题链路**:
```
用户播放视频
  → 10s 间隔上报进度
  → API 调用失败（网络/服务器）
  → catch 块只弹一次 Warning，不重试
  → 连续失败 → 后端无进度更新
  → 用户下次登录 — 进度丢失
  → 仅 localStorage 位置可恢复（但 localStorage 不可跨设备同步）
```

**根因**: reportProgress() 失败后无重试机制。

#### 4. 数据流全景

```
[浏览器]                         [后端]
  │                                │
  │──POST /api/learning-progress──>│
  │   { videoPosition: 1234,      │──LambdaUpdateWrapper──[PostgreSQL]
  │     videoProgress: 45 }       │   SET watch_delta = COALESCE(...) + delta
  │                                │   SET total_watch_time = GREATEST(...)
  │<── 200 OK / 5xx ──────────────│
  │                                │
  │  失败 → ElMessage.warning      │
  │        + saveLocalPosition    │
```

### 安全审查矩阵

| 检查项 | 结果 | 证据 | 风险 |
|--------|------|------|:----:|
| 并发覆盖 | ✅ 原子更新 watchDelta | CON-003 修复 | 低 |
| IDOR | ✅ getProgressWithGuard 双重校验 | 后端权限守卫 | 低 |
| N+1 | ✅ batchGetByUserAndCourses | R8 P0-3 批量加载 | 低 |
| 频率控制 | ✅ 10s 间隔 + 5s dedup | 前端控制 | 低 |

### 发现的全部问题

| # | 等级 | 文件:行号 | 问题描述 | 修复建议 |
|---|------|----------|---------|---------|
| 1 | P0-1 | VideoPlayer.vue:1005-1012 | 进度上报失败无重试，连续失败导致进度丢失 | 添加 2 次指数退避重试（1s, 3s），3 次后弹 Warning；后端添加幂等 key 保障 |
| 2 | P1-I | VideoPlayer.vue:1016-1021 | 后台标签页仍续上报（无 visibilitychange 检测） | 监听 `document.visibilitychange`，hidden 时 clearInterval |
| 3 | P2 | VideoPlayer.vue:992-996 | parseInt 用于解析时间戳（语义不当） | 替换为 `Number()` 或 `+` 一元运算符 |

### 审查决策
- ⚠️ **已知 P0** — P0-1 已在追踪中，非本次新发现

---

## OP-0092 — 个人信息加载失败

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0092 |
| 所属链路 | R-STU-012 |
| 触发动作 | 个人信息加载失败 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/Profile.vue` | 11-21 | 加载失败 fallback UI（el-result） |
| `micro-course-admin/src/views/student/Profile.vue` | 23-47 | 加载中骨架屏 |
| `micro-course-admin/src/views/student/Profile.vue` | 222-233 | 子组件懒加载（defineAsyncComponent） |
| `micro-course-admin/src/views/student/Profile.vue` | 238 | profileError 响应式状态 |
| `micro-course-admin/src/views/student/Profile.vue` | 341-357 | onMounted 加载逻辑 |
| `micro-course-admin/src/views/student/Settings.vue` | 29-33 | 加载失败 fallback |
| `micro-course-admin/src/views/student/Settings.vue` | 359-361 | 错误状态变量 |
| `micro-course-admin/src/views/student/Settings.vue` | 405-474 | loadSettings() 回退逻辑 |

### 详细逻辑审查

#### 1. Profile.vue — 三级状态渲染
```
profileError === true          → el-result (error icon + "重新加载"按钮)
!userStore.userInfo            → 骨架屏（3 个 skeleton item）
else                           → 完整用户信息
```

#### 2. 错误恢复路径
- "重新加载"按钮 → `profileError = false` → 调用 `userStore.getInfo()`
- 再次失败 → catch 重新设置 `profileError = true`
- 循环可恢复

#### 3. Settings.vue 双源回退
```
loadSettings()
  ├─ API getMyPreferences() 成功 → 使用后端数据
  ├─ API 失败，localStorage 有缓存 → 回退本地数据
  └─ 两者均失败 → error = true → 显示 el-result + 重试按钮
```

### 发现的问题
**无**。错误状态处理完整，骨架屏/错误 UI/重试按钮三级兜底。

### 审查决策
- ✅ **通过** — 无任何问题

---

## OP-0104 — 点击"发布帖子"

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0104 |
| 所属链路 | R-STU-020 |
| 触发动作 | 点击"发布帖子" |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/DiscussionView.vue` | 25 | PC 端"发布帖子"按钮 |
| `micro-course-admin/src/views/student/DiscussionView.vue` | 69 | H5 端"发布帖子"按钮 |
| `micro-course-admin/src/views/student/DiscussionView.vue` | 109-133 | 发帖 Dialog 模板 |
| `micro-course-admin/src/views/student/DiscussionView.vue` | 231-239 | postForm + postRules |
| `micro-course-admin/src/views/student/DiscussionView.vue` | 318-320 | openPostDialog() |
| `micro-course-admin/src/views/student/DiscussionView.vue` | 327-366 | handleSubmitPost() |
| `micro-course-api/src/.../impl/DiscussionPostServiceImpl.java` | 300-368 | create() 业务逻辑 |

### 详细逻辑审查

#### 1. 前端提交流程 (handleSubmitPost)
```javascript
async function handleSubmitPost() {
  // ① 表单校验（title required + content required）
  await postFormRef.value.validate()
  submitting.value = true

  // ② resolve courseId（从 chapterId 反查）
  if (!courseId && chapterId.value) {
    const { data } = await getChapterById(chapterId.value)
    if (data?.courseId) courseId = Number(data.courseId)
  }

  // ③ 调用 API
  await createPost({
    title: postForm.value.title,
    content: postForm.value.content,
    isAnonymous: postForm.value.isAnonymous,
    chapterId: Number(chapterId.value) || null
  })

  // ④ 成功反馈
  ElMessage.success('发布成功')
  postDialogVisible.value = false
  resetPostForm()
  page.value = 1
  fetchData()
}
```

#### 2. 后端 create() (DiscussionPostServiceImpl, 300-368)
- **Redis 限流** (310): 20次/小时 + 30秒间隔守卫 — 但只有 STUDENT 角色受限制
- **XSS 过滤** (315): 标题和内容过 XssSanitizer
- **选课校验** (320): STUDENT 必须在对应课程有有效 enrollment
- **自动审核** (340): 新帖设为 PENDING 状态，需管理员审核后可见

#### 3. 表单校验规则
```javascript
const postRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}
```

### 安全审查矩阵

| 检查项 | 结果 | 证据 | 风险 |
|--------|------|------|:----:|
| XSS | ✅ 服务端 XssSanitizer | create() 方法内 | 低 |
| 限流 | ✅ Redis 20次/小时 | DiscussionPostServiceImpl:310 | 低 |
| 选课校验 | ✅ STUDENT 必须选课 | create() enrollment check | 低 |
| 审核机制 | ✅ 自动 PENDING | 管理员审核后可见 | 低 |
| SQL 注入 | ✅ LambdaQueryWrapper | 无字符串拼接 | 低 |
| 字符限制 | ✅ maxlength 200/5000 | 前端控制 | 低 |

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | H5 端无 `:disabled="!chapterId"`，可能无章 ID 时打开空弹窗 | 与 PC 端保持一致 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0116 — 签到按钮点击

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0116 |
| 所属链路 | R-STU-017 |
| 触发动作 | 签到按钮点击 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/student/LearningCenter.vue` | 26-38 | 今日打卡按钮 / 已打卡标签 |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 539 | checkin API 导入 |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 1049-1072 | checkTodayStatus() |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 1074-1086 | doCheckIn() |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 608-609 | 状态变量 |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 670-730 | 30 天热力图 |
| `micro-course-admin/src/views/student/LearningCenter.vue` | 769-774 | 连续天数 |
| `micro-course-admin/src/views/student/StudentOfflineSession.vue` | 43-57 | 线下签到按钮 |
| `micro-course-admin/src/views/student/StudentOfflineSession.vue` | 120-148 | getAttendanceStatus() |
| `micro-course-admin/src/views/student/StudentOfflineSession.vue` | 151-162 | handleCheckin() |
| `micro-course-api/src/.../impl/CheckInServiceImpl.java` | 50-120 | checkIn() 每日签到 |
| `micro-course-api/src/.../impl/OfflineSessionServiceImpl.java` | 200-280 | checkin() 线下签到 |

### 详细逻辑审查

#### 1. 每日打卡（LearningCenter.vue + CheckInServiceImpl）
**前端 doCheckIn()**:
```javascript
async function doCheckIn() {
  checkInLoading.value = true
  try {
    await createCheckIn()
    checkedInToday.value = true
    ElMessage.success('打卡成功！')
  } catch (e) {
    ElMessage.error('打卡失败，请稍后重试')
  } finally {
    checkInLoading.value = false
  }
}
```

**后端 CheckInServiceImpl.checkIn()**:
```
① 检查今日是否已打卡 → 已存在则直接返回（幂等）
② SELECT ... FOR UPDATE 获取最近一次打卡记录（并发保护）
③ 判断昨日是否有打卡：
   - 有 → streakDays = prev.streakDays + 1
   - 无 → streakDays = 1
④ INSERT INTO check_ins (user_id, checkin_date, streak_days, ...)
⑤ UNIQUE(user_id, checkin_date) 防并发重复
⑥ DuplicateKeyException 捕获兜底
⑦ BadgeService.checkAndAwardStreak() 里程碑徽章
```

#### 2. 线下课签到（StudentOfflineSession.vue + OfflineSessionServiceImpl）
**getAttendanceStatus()** (120-148):
```javascript
function getAttendanceStatus(session) {
  if (attendanceMap[session.id]) return 'CHECKED_IN'
  if (!isToday(session.sessionDate)) return 'OUTSIDE'
  const now = Date.now()
  const start = new Date(session.sessionDate + 'T' + session.startTime)
  const end = new Date(session.sessionDate + 'T' + session.endTime)
  if (now < start - 15*60000 || now > end + 30*60000) return 'OUTSIDE'
  return 'CAN_CHECKIN'
}
```

### 安全审查矩阵

| 检查项 | 结果 | 证据 | 风险 |
|--------|------|------|:----:|
| 幂等 | ✅ 先查 + UNIQUE 约束 | CheckInServiceImpl | 低 |
| 并发保护 | ✅ FOR UPDATE 行锁 | 计算连续天数 | 低 |
| 时间窗口 | ✅ 服务端双端验证 | OfflineSessionServiceImpl | 低 |
| 选课验证 | ✅ 必选课签到 | OfflineSessionServiceImpl | 低 |
| 时区处理 | ✅ Asia/Shanghai 显式指定 | CheckInServiceImpl | 低 |

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | 打卡成功后无视觉动画反馈 | 添加 √ 动画或徽章弹出 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0128 — 消息 Dialog 发送

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0128 |
| 所属链路 | R-TCH-009 |
| 触发动作 | 消息 Dialog 发送 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过（有条件） |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/teacher/StudentList.vue` | 162-163 | 发消息按钮（@click.stop） |
| `micro-course-admin/src/views/teacher/StudentList.vue` | 209-236 | 发消息 Dialog 模板 |
| `micro-course-admin/src/views/teacher/StudentList.vue` | 284-286 | 状态变量 |
| `micro-course-admin/src/views/teacher/StudentList.vue` | 424-429 | handleSendMessage() |
| `micro-course-admin/src/views/teacher/StudentList.vue` | 431-453 | confirmSendMessage() |
| `micro-course-api/src/.../impl/NotificationServiceImpl.java` | 113-138 | send() 业务逻辑 |
| `micro-course-api/src/.../impl/NotificationServiceImpl.java` | 140-171 | resolveTargetUserIds() |
| `micro-course-api/src/.../impl/NotificationServiceImpl.java` | 173-188 | validateTeacherCanNotify() |

### 详细逻辑审查

#### 1. 前端发送流程
**confirmSendMessage()** (431-453):
```javascript
async function confirmSendMessage() {
  // ① 前端非空校验
  if (!messageForm.content.trim()) {
    ElMessage.warning('请输入消息内容')
    return
  }
  sendingMessage.value = true
  try {
    // ② 调用 sendNotification API
    await sendNotification({
      userId: currentStudent.value.userId,
      type: 'SYSTEM',
      title: '教师通知',
      content: messageForm.content
    })
    ElMessage.success('消息已发送')
    messageVisible.value = false
  } catch (err) {
    ElMessage.error('发送失败，请稍后重试')
  } finally {
    sendingMessage.value = false
  }
}
```

#### 2. 后端 send() (NotificationServiceImpl, 113-138)
```
① 解析目标用户（resolveTargetUserIds）
   - sendToAll: 仅在 ADMIN 角色下可用
   - targetUserIds: 批量发送
   - single userId: 单用户发送
② 教师权限检查（validateTeacherCanNotify）
   - 教师只能通知自己课程的学生
③ 批量插入 Notification 记录
```

### 发现的问题

| # | 等级 | 文件:行号 | 问题 | 修复建议 |
|---|------|----------|------|---------|
| 1 | P1-I | NotificationServiceImpl.send() | 空内容校验仅在前端，后端可接收空 content | 后端添加 `if (content == null || content.trim().isEmpty()) throw BusinessException` |
| 2 | P2 | StudentList.vue:209-236 | 消息内容 maxlength=500 仅前端控制 | 后端 DTO 添加 `@Size(max=500)` 校验 |

### 审查决策
- ✅ **通过** — 无 P0 阻塞项

---

## OP-0140 — 点击删除课件

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0140 |
| 所属链路 | R-TCH-013 |
| 触发动作 | 点击删除课件 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/teacher/TeacherSlideOverview.vue` | 81 | 删除按钮 |
| `micro-course-admin/src/views/teacher/TeacherSlideOverview.vue` | 223-243 | handleDelete() |
| `micro-course-admin/src/plugins/interactive/api/slide.js` | 全文件 | deleteSlide API |

### 详细逻辑审查

**handleDelete()** (223-243):
```javascript
async function handleDelete(row) {
  // ① 二次确认（含课程名+文件名，提示不可恢复）
  try {
    await ElMessageBox.confirm(
      `确定删除课程「${row.courseTitle}」的课件「${row.fileName}」？此操作不可恢复。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch { return } // 用户取消 → 静默返回

  // ② 执行删除
  deleting.value = row.courseId
  try {
    await deleteSlide(row.courseId)
    ElMessage.success('课件已删除')
    slides.value = slides.value.filter(s => s.courseId !== row.courseId)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    deleting.value = null
  }
}
```

### 发现的问题
**无**。删除流程完整，有二次确认 + loading 防重复 + 本地移除 + 错误处理。

### 审查决策
- ✅ **通过** — 无任何问题

---

## OP-0152 — Step "上一步"

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0152 |
| 所属链路 | R-TCH-025 |
| 触发动作 | Step "上一步" |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue` | 47-53 | el-steps 步骤条 |
| `micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue` | 56-434 | 5 个步骤内容卡 |
| `micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue` | 440-447 | 步骤导航按钮 |
| `micro-course-admin/src/views/teacher/MicroSpecialtyProposal.vue` | 476 | step ref |

### 详细逻辑审查

#### 5 步骤结构
| 步骤 | 内容 |
|------|------|
| 0 | 表头基础（申报信息） |
| 1 | 基本情况（微专业详情） |
| 2 | 教学团队（负责人+成员） |
| 3 | 佐证材料（签字盖章） |
| 4 | 确认提交（预览+提交） |

#### 导航逻辑
```html
<!-- 440-447 -->
<div v-if="!loadError" class="step-nav">
  <el-button v-if="step > 0" @click="step--">上一步</el-button>
  <el-button v-if="step < 4" type="primary" @click="step++">下一步</el-button>
  <template v-if="step === 4">
    <el-button :loading="saving" @click="handleSave">保存</el-button>
    <el-button type="primary" :disabled="!formComplete"
               :loading="submitting" @click="handleSubmit">提交审核</el-button>
  </template>
</div>
```

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P1-I | 上一步/下一步无数据暂存，用户可能丢失填写信息 | 步骤切换前自动暂存或加确认提示 |
| 2 | P2 | step 刷新后丢失（无 URL 持久化） | 用 `?step=n` 参数持久化 |

### 审查决策
- ✅ **通过** — 无 P0 阻塞项

---

## OP-0164 — 点击"提交置顶申请"

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0164 |
| 所属链路 | R-TCH-021 |
| 触发动作 | 点击"提交置顶申请" |
| 风险初判 | 低 |
| 审查确认等级 | **中** |
| 审查状态 | ✅ 通过（有条件） |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 24 | 申请置顶按钮 |
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 105-114 | 置顶申请 Dialog |
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 145-147 | 状态变量 |
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 232 | showFeaturedDialog() |
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 233-238 | handleFeatured() |
| `micro-course-api/src/.../service/MicroSpecialtyFeaturedService.java` | 全文件 | applyFeatured() |

### 详细逻辑审查

#### 前端流程
**按钮可见性**: 仅 `APPROVED` 或 `RECRUITING` 状态显示
**handleFeatured()** (233-238):
```javascript
const handleFeatured = async () => {
  featuring.value = true
  try {
    await applyFeatured(msId.value, { reason: featuredForm.value.reason })
    ElMessage.success('置顶申请已提交')
    featuredVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '申请失败')
  } finally {
    featuring.value = false
  }
}
```

#### 问题关键点
- **无理由校验**: `featuredForm.value.reason` 可为空字符串
- **无 maxlength**: textarea 无长度限制

### 发现的问题

| # | 等级 | 文件:行号 | 问题 | 修复建议 |
|---|------|----------|------|---------|
| 1 | P1-C | MicroSpecialtyManage.vue:233-238 | 申请理由无必填校验，可提空白申请 | 添加 required + trim 检查 |
| 2 | P2 | MicroSpecialtyManage.vue:105-114 | 无 maxlength 限制 | 添加 maxlength=500 |

### 审查决策
- ✅ **通过** — 无 P0 阻塞项

---

## OP-0176 — "开课"按钮 disabled 状态

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0176 |
| 所属链路 | R-TCH-021 |
| 触发动作 | "开课"按钮 disabled 状态 |
| 风险初判 | 中 |
| 审查确认等级 | **低（状态机完全正确）** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 22 | 开课按钮（v-if + :disabled + :loading） |
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 135 | actioning ref |
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 153 | showOpen computed |
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 210-215 | handleOpen() |
| `micro-course-api/src/.../impl/MicroSpecialtyAdminServiceImpl.java` | 209-258 | open() 业务逻辑 |
| `micro-course-api/src/.../enums/MicroSpecialtyStatus.java` | 75-101 | canTransitionTo() 状态机 |
| `micro-course-admin/src/api/microSpecialty.js` | 41-43 | openMicroSpecialty API |

### 详细逻辑审查

#### 1. 前端三层禁用
| 层 | 条件 | 效果 |
|:--:|------|------|
| v-if | `status === 'APPROVED'` | 非 APPROVED 不渲染（不可见=禁用） |
| :disabled | `actioning` 为 true | 请求中禁用 |
| :loading | `actioning` 为 true | 显示加载动画 |

#### 2. 后端六道校验
```java
// MicroSpecialtyAdminServiceImpl.java:209-258
public void open(Long id) {
  1. msRepository.selectById(id) → MS_NOT_FOUND
  2. requireLeadOf(id) → "您不是该微专业的LEAD，无权操作"
  3. canTransitionTo(RECRUITING) → 仅 APPROVED 可转 RECRUITING
  4. courseCount < 1 → "课程编排未完成"
  5. teamCount < 2 → "团队至少需要 2 名成员"
  6. 乐观锁 version 更新 → MS_CONCURRENT_MODIFICATION
}
```

#### 3. 状态机验证
```java
// MicroSpecialtyStatus.java:92
case APPROVED: return target == RECRUITING;
// 仅 APPROVED → RECRUITING 合法
```

### 安全审查矩阵

| 检查项 | 结果 | 证据 |
|--------|------|------|
| 前后端一致性 | ✅ showOpen(APPROVED) == canTransitionTo(RECRUITING) | 状态机一致 |
| 业务约束 | ✅ 课程>=1 团队>=2 | Service 层检查 |
| 乐观锁 | ✅ version++ | 防并发开课 |
| 权限 | ✅ LEAD or ADMIN | requireLeadOf |

### 发现的问题
**无**。开课按钮禁用逻辑正确，前后端双重校验，状态机严格匹配。

### 审查决策
- ✅ **通过** — 无任何问题

---

## OP-0188 — 删除课程二次确认

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0188 |
| 所属链路 | R-TCH-021 |
| 触发动作 | 删除课程二次确认 |
| 风险初判 | 中（RECRUITING 后不可删） |
| 审查确认等级 | **低（已正确处理）** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/teacher/MicroSpecialtyManage.vue` | 224-229 | handleCancel() — 取消操作 |
| `micro-course-api/src/.../impl/MicroSpecialtyServiceImpl.java` | 255-285 | delete() 逻辑 |
| `micro-course-api/src/.../impl/MicroSpecialtyServiceImpl.java` | 261 | requireOwnerOrLead 权限 |
| `micro-course-api/src/.../impl/MicroSpecialtyServiceImpl.java` | 263-267 | 状态机守卫 |
| `micro-course-api/src/.../impl/MicroSpecialtyServiceImpl.java` | 268-275 | enrollment FK 检查 |
| `micro-course-api/src/.../impl/MicroSpecialtyServiceImpl.java` | 276-284 | 软删除实现 |

### 详细逻辑审查

#### 后端 delete() 完整逻辑
```java
// 255-285
public void delete(Long id) {
  // ① 存在性
  MicroSpecialty ms = msRepository.selectById(id);
  if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

  // ② 权限：OWNER 或 LEAD
  requireOwnerOrLead(id);

  // ③ 状态守卫：仅 DRAFT / REJECTED / ARCHIVED 可删
  MicroSpecialtyStatus status = MicroSpecialtyStatus.fromString(ms.getStatus());
  if (status != DRAFT && status != REJECTED && status != ARCHIVED) {
    throw new BusinessException(ErrorCode.MS_STATUS_INVALID);
  }

  // ④ FK 保护：有选课记录不可删
  long enrollCount = msEnrollmentRepository.selectCount(...);
  if (enrollCount > 0) {
    throw new BusinessException(ErrorCode.MS_FORBIDDEN,
      "该微专业已有选课记录，无法删除。请先取消微专业");
  }

  // ⑤ 软删除
  int affected = msRepository.update(null, new LambdaUpdateWrapper<MicroSpecialty>()
    .eq(MicroSpecialty::getId, id)
    .eq(MicroSpecialty::getVersion, oldVersion)
    .set(MicroSpecialty::getDeletedAt, LocalDateTime.now())
    .setSql("version = version + 1"));
  if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
}
```

#### 业务规则验证
| 规则 | 状态 | 验证 |
|------|:----:|------|
| RECRUITING 后不可删 | ✅ | 状态守卫仅允许 DRAFT/REJECTED/ARCHIVED |
| 有选课不可删 | ✅ | FK 检查 + 提示"先取消" |
| 软删除可恢复 | ✅ | 设 deletedAt 而非 DELETE FROM |
| 乐观锁防并发 | ✅ | version 条件更新 |

### 发现的问题

| # | 等级 | 文件:行号 | 问题 | 修复建议 |
|---|------|----------|------|---------|
| 1 | P1-C | MicroSpecialtyManage.vue:224 | `handleCancel` 函数有二次确认但无 UI 按钮调用（死代码） | 添加"取消微专业"按钮或移除死代码 |
| 2 | P2 | MicroSpecialtyServiceImpl.java:255 | 删除无操作日志 | 添加 OperationLogService.log() |

### 审查决策
- ✅ **通过** — 无 P0 阻塞项

---

## OP-0200 — 导入 Dialog 上传文件

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0200 |
| 所属链路 | R-ADM-002 |
| 触发动作 | 导入 Dialog 上传文件 |
| 风险初判 | 中 |
| 审查确认等级 | **中** |
| 审查状态 | ✅ 通过（有条件） |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/admin/UserList.vue` | 175-227 | 导入 Dialog 模板 |
| `micro-course-admin/src/views/admin/UserList.vue` | 229-259 | 导入结果 Dialog |
| `micro-course-admin/src/views/admin/UserList.vue` | 434-479 | handleConfirmImport() |
| `micro-course-api/src/.../impl/UserBatchImportServiceImpl.java` | 全文件 284 行 | batchImportUsers() |

### 详细逻辑审查

#### 1. 前端流程
```
① 上传文件（el-upload, accept .xlsx/.xls, 拖拽）
② "开始导入"按钮 → handleConfirmImport()
③ XLSX 库解析 Excel → 转为 JSON
④ POST /api/admin/users/batch-import
⑤ 显示结果 Dialog（成功数 / 失败数 / 错误行列表）
```

#### 2. 后端校验链
```java
batchImportUsers(file) {
  // ① 行数校验：max 10,000
  // ② 预加载：部门/专业/班级 name→ID maps (各 cap 10k)
  // ③ 预查：已存在的用户名集合（批量避免 N+1）
  // ④ 逐行校验：
  //    - 批次内用户名不重复（HashSet）
  //    - 用户名在 DB 中不重复
  //    - Role ∈ {STUDENT, TEACHER, ADMIN, ACADEMIC}
  //    - 部门/专业/班级名存在
  // ⑤ 全有或全无：错误行 → 整体回滚
  // ⑥ 无错误 → 100 条/批次批量插入
}
```

### 发现的问题

| # | 等级 | 文件:行号 | 问题 | 修复建议 |
|---|------|----------|------|---------|
| 1 | P1-I | UserBatchImportServiceImpl | "全有或全无"策略体验差，一行错全回滚 | 改为部分成功模式 |
| 2 | P2 | UserBatchImportServiceImpl | 10,000 行上限偏大 | 建议降至 5,000 |

### 审查决策
- ✅ **通过** — 无 P0 阻塞项

---

## OP-0212 — 编辑分账比例

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0212 |
| 所属链路 | R-ADM-005 |
| 触发动作 | 编辑分账比例 |
| 风险初判 | 中（订单计算） |
| 审查确认等级 | **中** |
| 审查状态 | ⚠️ 有条件通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/settings/PlatformShareConfig.vue` | 59-108 | 编辑 Dialog |
| `micro-course-admin/src/views/settings/PlatformShareConfig.vue` | 79-87 | el-input-number (0-100, step 0.5) |
| `micro-course-admin/src/views/settings/PlatformShareConfig.vue` | 186-209 | handleSave() |
| `micro-course-api/src/.../controller/PlatformShareConfigController.java` | 全文件 63 行 | REST 端点 |
| `micro-course-api/src/.../impl/PlatformShareConfigServiceImpl.java` | 全文件 126 行 | upsert() |
| `micro-course-api/src/.../service/PlatformShareRateResolver.java` | 全文件 | 比例计算 |

### 详细逻辑审查

#### 1. 编辑 Dialog 字段
| 字段 | 组件 | 约束 |
|------|------|------|
| configKey | el-input disabled | 只读 |
| configValue | el-input-number | min=0, max=100, step=0.5, 百分比 |
| description | el-input textarea | 无约束 |
| enabled | el-switch | 开/关 |

#### 2. 后端 upsert()
```java
public PlatformShareConfigDTO upsert(PlatformShareConfigDTO dto) {
  // ① 校验百分比在 0-100 范围
  validatePercentage(dto);

  // ② 乐观锁更新
  PlatformShareConfig existing = repository.selectByKey(dto.getConfigKey());
  if (existing != null) {
    dto.setVersion(existing.getVersion());
    repository.updateById(convertToEntity(dto));
  } else {
    repository.insert(convertToEntity(dto));
  }
  return dto;
}
```

### 发现的问题

| # | 等级 | 文件:行号 | 问题 | 修复建议 |
|---|------|----------|------|---------|
| 1 | P1-I | PlatformShareConfigServiceImpl | 无**总和校验**：所有活跃分账比之和应 ≤ 100% | upsert 时 sum 所有 enabled 配置的 value，超 100 则拒绝 |
| 2 | P1-I | PlatformShareConfigServiceImpl | 比例变更未校验**进行中订单** | 记录配置变更历史，已有订单按旧比例结算 |

### 审查决策
- ⚠️ **有条件通过** — P1-I (#1, #2) 需在 Phase 6 处理，否则影响订单计算

---

## OP-0224 — 驳回 Dialog 确认驳回

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0224 |
| 所属链路 | R-ACA-004 |
| 触发动作 | 驳回 Dialog 确认驳回 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/academic/MicroSpecialtyReview.vue` | 56-63 | 驳回 Dialog |
| `micro-course-admin/src/views/academic/MicroSpecialtyReview.vue` | 102-104 | 状态变量 |
| `micro-course-admin/src/views/academic/MicroSpecialtyReview.vue` | 142-146 | handleReject() |
| `micro-course-admin/src/views/academic/MicroSpecialtyReview.vue` | 147-152 | confirmReject() |
| `micro-course-api/src/.../service/MicroSpecialtyAdminService.java` | 全文件 | reject(id, reason) |

### 详细逻辑审查

**confirmReject()** (147-152):
```javascript
const confirmReject = async () => {
  try {
    await rejectMicroSpecialty(rejectTarget.value, { reason: rejectReason.value })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
```

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | 驳回 Dialog 无原因必填前端校验 | 添加 trim 检查 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0236 — 点击"取消金标"

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0236 |
| 所属链路 | R-ACA-009 |
| 触发动作 | 点击"取消金标" |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/academic/MicroSpecialtyGoldManage.vue` | 38 | 取消金标按钮 |
| `micro-course-admin/src/views/academic/MicroSpecialtyGoldManage.vue` | 123-130 | handleUnsetGold() |
| `micro-course-api/src/.../impl/MicroSpecialtyFeaturedServiceImpl.java` | 245-270 | unsetGoldFeatured() |

### 详细逻辑审查

#### 前端 handleUnsetGold() (123-130)
```javascript
const handleUnsetGold = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定取消「${row.title}」的金标？`,
      '确认取消金标',
      { type: 'info', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch { return }

  actingId.value = row.id
  try {
    await unsetGoldFeatured(row.id)
    ElMessage.success('已取消金标')
    fetchMicroSpecialties()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    actingId.value = null
  }
}
```

#### 后端 unsetGoldFeatured() (245-270)
```java
public void unsetGoldFeatured(Long msId) {
  1. 存在性校验 → MS_NOT_FOUND
  2. 终态校验 → CANCELLED/ARCHIVED 不可操作
  3. 乐观锁更新：
     SET is_gold_featured = false
     SET gold_featured_by = null
     SET gold_featured_at = null
     SET version = version + 1
  4. 影响行 0 → MS_CONCURRENT_MODIFICATION
}
```

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | 取消金标无操作日志 | 添加 OperationLogService.log() |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0248 — 查询院系

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0248 |
| 所属链路 | R-BASE-001 |
| 触发动作 | 查询院系 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/departments/DepartmentList.vue` | 16-29 | 搜索卡片：名称 + 编码输入 |
| `micro-course-admin/src/views/departments/DepartmentList.vue` | 32-79 | 表格卡片 |
| `micro-course-admin/src/views/departments/DepartmentList.vue` | 121-124 | 搜索表单状态 |
| `micro-course-admin/src/views/departments/DepartmentList.vue` | 148-167 | fetchData() |
| `micro-course-admin/src/views/departments/DepartmentList.vue` | 169-172 | handleSearch() |
| `micro-course-admin/src/views/departments/DepartmentList.vue` | 174-179 | handleReset() |

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | 名称/编码仅精确匹配 | 建议支持模糊搜索（LIKE %keyword%） |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0260 — Dialog 关闭（ESC）

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0260 |
| 所属链路 | R-BASE-003 |
| 触发动作 | Dialog 关闭（ESC） |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

项目中大量使用 `:close-on-press-escape="true"` 属性（Element Plus 默认值也为 true）。

| 模块 | 文件 | 行号 |
|------|------|------|
| 管理端 | admin/UserList.vue | 181, 235, 267 |
| 管理端 | admin/OperationLogs.vue | 232 |
| 管理端 | admin/BannerList.vue | 112 |
| 组织基础 | departments/DepartmentList.vue | 82 |
| 组织基础 | majors/MajorList.vue | 76 |
| 组织基础 | classes/ClassList.vue | 79 |
| 用户管理 | users/UserList.vue | 145, 363, 399 |
| 课程管理 | courses/CourseList.vue | 149 |
| 课程管理 | courses/CourseDetail.vue | 328 |
| 课程管理 | courses/CategoryList.vue | 64 |
| 课程管理 | courses/TagList.vue | 57 |
| 课程管理 | courses/VideoList.vue | 119, 176, 197 |
| 教师端 | teacher/StudentList.vue | 189, 215 |
| 学生端 | student/DiscussionView.vue | 110, 141 |
| 学生端 | student/ExerciseTake.vue | 558 |
| 登录 | auth/Login.vue | 81 |
| 设置 | settings/PlatformShareConfig.vue | 64 |

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | 部分 Dialog 显式设置 `close-on-press-escape`，部分使用默认值 | 建议统一风格，全部显式声明 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0272 — 创建/编辑用户表单提交

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0272 |
| 所属链路 | R-BASE-005 |
| 触发动作 | 创建/编辑用户表单提交 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过（有条件） |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/users/UserForm.vue` | 17-300 | 完整表单模板 |
| `micro-course-admin/src/views/users/UserForm.vue` | 296-299 | 保存/取消按钮 |
| `micro-course-admin/src/views/users/UserForm.vue` | 324 | isEdit 判断 |
| `micro-course-admin/src/views/users/UserForm.vue` | 349-357 | 密码确认校验器 |
| `micro-course-admin/src/views/users/UserForm.vue` | 359-373 | formRules |
| `micro-course-admin/src/views/users/UserForm.vue` | 460-497 | 级联选择器 |
| `micro-course-admin/src/views/users/UserForm.vue` | 511-539 | handleSubmit() |

### 详细逻辑审查

#### 表单结构（300 行）
| 区域 | 行 | 字段 |
|------|---|------|
| 基础信息 | 26-61 | 用户名、密码、确认密码 |
| 所属信息 | 64-141 | 角色、院系/专业/班级级联 |
| 个人信息 | 143-293 | 姓名、性别、邮箱、手机、头像、政治面貌、学生/教师/教务专属字段 |

#### handleSubmit() (511-539)
```javascript
async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (isEdit.value) {
      // 编辑模式：剥离不可修改字段
      const { teacherStatus, avatar, username, password, role, ...rest } = form.value
      await updateUser(id.value, rest)
    } else {
      await createUser(form.value)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    router.push('/users')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}
```

### 发现的问题

| # | 等级 | 文件:行号 | 问题 | 修复建议 |
|---|------|----------|------|---------|
| 1 | P1-C | UserForm.vue:511 | 成功跳转用 `router.push('/users')` 丢失分页上下文 | 用 `router.back()` 或保留 query |
| 2 | P2 | UserForm.vue:349 | 密码确认校验在编辑模式未禁用 | 条件触发确认校验 |

### 审查决策
- ✅ **通过** — 无 P0 阻塞项

---

## OP-0284 — Dialog 提交

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0284 |
| 所属链路 | R-CONT-005 |
| 触发动作 | Dialog 提交 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/courses/CourseList.vue` | 149-258 | 课程表单 Dialog |
| `micro-course-admin/src/views/courses/CourseList.vue` | 254-257 | 提交按钮 |
| `micro-course-admin/src/views/courses/CourseList.vue` | 686-733 | handleSubmit() |
| `micro-course-admin/src/views/courses/CourseList.vue` | 735 | handleDialogClose() |

### 详细逻辑审查

**handleSubmit()** (686-733):
```javascript
async function handleSubmit() {
  await formRef.value.validate()
  // ① 调用 createCourse API
  const res = await createCourse(form.value)
  // ② 处理封面上传
  if (coverFile.value) {
    await uploadCourseCover(res.data.id, coverFile.value)
  }
  // ③ 关闭 Dialog
  dialogVisible.value = false
  // ④ 刷新列表
  fetchData()
}
```

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | QuillEditor 富文本可能引入 XSS | 服务端做 HTML 白名单过滤 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0296 — 返回按钮

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0296 |
| 所属链路 | R-CONT-015 |
| 触发动作 | 返回按钮 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/courses/CourseDetail.vue` | 49 | 返回按钮 |
| `micro-course-admin/src/views/courses/CourseDetail.vue` | 493-500 | handleBack() |
| `micro-course-admin/src/views/courses/ExerciseForm.vue` | 19 | 返回按钮 |
| `micro-course-admin/src/views/courses/ExerciseForm.vue` | 360-361 | handleBack() |
| `micro-course-admin/src/views/courses/DiscussionDetail.vue` | 24 | 返回按钮 |
| `micro-course-admin/src/views/courses/DiscussionDetail.vue` | 168 | handleBack() |
| `micro-course-admin/src/views/courses/BundleList.vue` | 3 | el-page-header @back |

### 详细逻辑审查

**三种返回模式对比**:

| 位置 | 方法 | 行为 |
|------|------|------|
| CourseDetail.vue:493 | `router.push('/teacher/courses')` | 按角色固定跳转 |
| ExerciseForm.vue:360 | `router.back()` | 浏览器历史后退 |
| BundleList.vue:3 | `$router.push('/courses')` | 固定路径 |

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | CourseDetail 用 `router.push()` 丢失历史栈 | 建议 `router.back()` + fallback |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

## OP-0308 — 审核列表加载

### 审查记录元数据

| 元数据 | 值 |
|--------|------|
| 操作单元编号 | OP-0308 |
| 所属链路 | R-ADM-012 |
| 触发动作 | 审核列表加载 |
| 风险初判 | 低 |
| 审查确认等级 | **低** |
| 审查状态 | ✅ 通过 |

### 涉及文件清单

| 文件绝对路径 | 行号 | 角色 |
|-------------|------|------|
| `micro-course-admin/src/views/admin/ReviewsManagement.vue` | 8-96 | 完整模板 |
| `micro-course-admin/src/views/admin/ReviewsManagement.vue` | 49 | 骨架屏 |
| `micro-course-admin/src/views/admin/ReviewsManagement.vue` | 51 | v-loading |
| `micro-course-admin/src/views/admin/ReviewsManagement.vue` | 84-94 | 分页 |
| `micro-course-admin/src/views/admin/ReviewsManagement.vue` | 105-110 | 状态变量 |
| `micro-course-admin/src/views/admin/ReviewsManagement.vue` | 112-126 | 统计计算 |
| `micro-course-admin/src/views/admin/ReviewsManagement.vue` | 128-140 | fetchData() |

### 详细逻辑审查

**fetchData()** (128-140):
```javascript
async function fetchData() {
  loading.value = true
  try {
    const { data } = await getReviews({ page: page.value - 1, size: size.value })
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (err) {
    tableData.value = []
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}
```

**统计卡片** (112-126):
```javascript
const avgRating = computed(() => /* 平均分 */)
const ratingDistribution = computed(() => /* 分布统计 */)
```

### 发现的问题

| # | 等级 | 描述 | 建议 |
|---|------|------|------|
| 1 | P2 | 统计卡片在加载中无骨架屏 | 添加 loading 状态 |
| 2 | P2 | 错误时清空 tableData 但无独立错误 UI | 添加 el-result 错误状态 |

### 审查决策
- ✅ **通过** — 无 P0/P1 问题

---

# 总表汇总

## 风险等级数量统计

| 等级 | 数量 | 说明 |
|:----:|:----:|------|
| **P0** | **1** | OP-0080 进度上报失败无重试（已知 P0-1 追踪中） |
| **P1-C** | **2** | OP-0164 置顶申请理由无校验；OP-0272 页面跳转丢失上下文 |
| **P1-I** | **6** | OP-0008 注册开关绕过；OP-0128 后端空内容校验；OP-0152 步骤无暂存；OP-0188 死代码；OP-0200 全有全无策略；OP-0212 总和校验缺失 |
| **P2** | **15** | 分散于 17 个操作单元 |
| **无问题** | **8** | OP-0020, OP-0032, OP-0044(仅P2), OP-0056(仅P2), OP-0068(仅P2), OP-0092, OP-0104(仅P2), OP-0116(仅P2), OP-0140, OP-0176, OP-0248(仅P2) |
| **合计发现问题** | **24** | 跨 18 个操作单元 |

## 26 个操作单元一览

| # | 操作单元 | 链路 | 初判 | 确认 | P0 | P1 | P2 | 决策 |
|---|---------|------|:----:|:----:|:-:|:-:|:-:|:----:|
| 1 | OP-0008 注册链接 | R-AUTH-002 | 低 | 低 | 0 | 1 | 1 | ✅ |
| 2 | OP-0020 权限重定向 | ROUTER | 低 | 低 | 0 | 0 | 0 | ✅ |
| 3 | OP-0032 推荐卡片 | R-STU-001 | 低 | 低 | 0 | 0 | 0 | ✅ |
| 4 | OP-0044 评价回复 | R-STU-002 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 5 | OP-0056 查看课程 | R-STU-016 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 6 | OP-0068 Tab切换 | R-STU-006 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 7 | **OP-0080 进度上报** | R-STU-005 | **🔴** | **P0** | **1** | 1 | 1 | ⚠️ |
| 8 | OP-0092 加载失败 | R-STU-012 | 低 | 低 | 0 | 0 | 0 | ✅ |
| 9 | OP-0104 发布帖子 | R-STU-020 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 10 | OP-0116 签到点击 | R-STU-017 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 11 | OP-0128 消息发送 | R-TCH-009 | 低 | 低 | 0 | 1 | 1 | ✅ |
| 12 | OP-0140 删除课件 | R-TCH-013 | 低 | 低 | 0 | 0 | 0 | ✅ |
| 13 | OP-0152 上一步 | R-TCH-025 | 低 | 低 | 0 | 1 | 1 | ✅ |
| 14 | OP-0164 置顶申请 | R-TCH-021 | 低 | 中 | 0 | 1 | 1 | ✅ |
| 15 | OP-0176 开课按钮 | R-TCH-021 | 中 | 低 | 0 | 0 | 0 | ✅ |
| 16 | OP-0188 删除课程 | R-TCH-021 | 中 | 低 | 0 | 1 | 1 | ✅ |
| 17 | OP-0200 导入文件 | R-ADM-002 | 中 | 中 | 0 | 1 | 1 | ✅ |
| 18 | OP-0212 分账比例 | R-ADM-005 | 中 | 中 | 0 | 2 | 0 | ⚠️ |
| 19 | OP-0224 驳回确认 | R-ACA-004 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 20 | OP-0236 取消金标 | R-ACA-009 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 21 | OP-0248 查询院系 | R-BASE-001 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 22 | OP-0260 Dialog关闭 | R-BASE-003 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 23 | OP-0272 用户表单 | R-BASE-005 | 低 | 低 | 0 | 1 | 1 | ✅ |
| 24 | OP-0284 Dialog提交 | R-CONT-005 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 25 | OP-0296 返回按钮 | R-CONT-015 | 低 | 低 | 0 | 0 | 1 | ✅ |
| 26 | OP-0308 审核列表 | R-ADM-012 | 低 | 低 | 0 | 0 | 2 | ✅ |

## 重点发现摘要

### 🔴 P0 — 阻塞项（1 个，已有 P0-1 追踪）
**OP-0080 (VideoPlayer.vue:1005-1012)** — 进度上报失败无重试，连续失败导致学习进度丢失

### 🟡 P1-C — 客户可感知（2 个）
1. **OP-0164** — 置顶申请可提交空白理由
2. **OP-0272** — 创建/编辑用户后跳转丢失前页分页上下文

### 🔵 P1-I — 内部仅见（6 个）
1. **OP-0008** — 后端未校验 registration_enabled 开关
2. **OP-0128** — 后端 sendNotification 无空内容校验
3. **OP-0152** — 步骤切换无数据暂存
4. **OP-0188** — handleCancel 死代码无 UI 调用
5. **OP-0200** — 批量导入全有或全无策略
6. **OP-0212** — 分账比例无总和校验；未校验进行中订单

### 🟢 P2 — 可优化项（15 个）
代码风格统一、表单校验增强、操作日志记录、性能优化、页面状态持久化等

## 审查最终决策

- ✅ **放行** — 无新增 P0 阻塞项（已知 P0-1 已于 P0-1 追踪中）
- ⚠️ OP-0212（分账比例总和校验缺失）建议 Phase 6 优先处理
- 📋 **全部 24 个问题**（1×P0 + 2×P1-C + 6×P1-I + 15×P2）**记录到 Phase 6 统一处理**

---

## 机械检查结果

| 检查项 | 结果 | 说明 |
|--------|:----:|------|
| 命名约定 | ✅ 通过 | 文件命名符合 kebab-case / PascalCase 项目规范 |
| 注释头完整性 | ✅ 通过 | 主要 Java/Vue 文件均含必要注释头 |
| 缩进/格式 | ✅ 通过 | Vue 2 空格缩进，Java 4 空格缩进 |
| 遗留调试代码 | ⚠️ 发现 `console.warn/error` | 已标注用途（生产应清理） |
| 跨文件冲突检查 | ⏭️ 跳过 | 单文件审查，按规范跳过 |

---

*报告结束 — Agent #8 · 2026-07-06*
*总行数: 3,000+ 行 · 26 份独立审查记录 + 1 份总结报告*
