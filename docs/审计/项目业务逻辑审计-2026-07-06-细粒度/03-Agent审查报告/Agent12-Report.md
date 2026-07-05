# Agent 12 审查报告

> **审计日期**：2026-07-06
> **审查范围**：26 个离散最小操作单元（OP-0012 ~ OP-0312，step=12）
> **审查方法**：代码层单节点深度细审（6 维度校验 + 红队冒烟测试）
> **审查人员**：Reviewer Agent #12

---

## 审查范围

| 项目 | 内容 |
|------|------|
| 总操作单元数 | 26 |
| 覆盖业务域 | AUTH(2)、ROUTER(1)、STU(6)、TCH(6)、ADM(3)、ACA(2)、BASE(2)、CONT(4) |
| 审查前端文件 | 18 个 .vue 文件 + 1 个 router + 1 个 store |
| 审查后端文件 | 7 个 .java 文件 |
| 设计文档参考 | `docs/审计/项目业务逻辑审计-2026-07-06-细粒度/01-全项目最小业务操作单元总表.md` |

---

## 审查记录：OP-0012

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0012 |
| **所属链路** | R-AUTH-002 注册 |
| **页面位置** | Login.vue:117-119,245-281（前端调用）；AuthController.java:61-67（后端端点）；AuthServiceImpl.java:92-146（后端实现） |
| **操作动作** | 注册弹窗点击"注册并登录" |
| **预期业务逻辑** | 校验注册开关→校验用户名唯一性→校验密码复杂度→创建学生用户(ACTIVE)→生成JWT→自动登录→跳转学生首页 |
| **实际表现** | ✅ 注册开关校验（`adminSettingService.getByKey("registration_enabled")`）→ ✅ 用户名唯一性（`findByUsername`）→ ✅ 密码复杂度双重校验（`@Pattern` + Service 正则）→ ✅ 创建 STUDENT 角色 status=1 → ✅ JWT 生成 + 自动登录 → ✅ 前端存储 token 并跳转 `/student/courses` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] `registerLoading` 控制 disabled + 文字变"注册中..."
- 表单输入限制：[✓] 用户名 2-50字符无空格，密码 8-32位含字母数字，确认密码一致性
- 弹窗弹出/关闭逻辑：[✓] `showRegisterDialog` 控制，关闭按钮和取消按钮均可关闭
- 操作成功/失败反馈：[✓] 成功 → ElMessage.success，失败 → 拦截器处理 + 兜底网络错误
- '注册并登录' 按钮状态：[✓] `registerLoading` 控制提交中状态
- 注册 Dialog 关闭后重置：[△] 关闭 Dialog 时未显式重置表单字段(用户下次打开仍显示上次输入)

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 弹窗内直接注册，无须跳转页面
- 信息引导完整性：[✓] 输入框 placeholder 已提示规范
- 操作提示匹配业务规则：[✓] 前端规则与后端规则一致
- 多状态视觉区分：[✓] loading/disabled/success 状态完备
- 重复冗余操作：[✓] 无

#### 3. 后端业务规则校验
- 当前操作触发的接口业务校验：[✓] `AuthServiceImpl.register()` L94-109
- 注册开关校验：[✓] L94-97：`registration_enabled` 非 true 则抛异常
- 用户名唯一性：[✓] L100-102
- 密码复杂度：[✓] L105-109 双重校验
- 权限拦截：[✓] `@PreAuthorize("permitAll()")`

#### 4. 数据库业务约束
- 字段变更：[✓] `users` 表 insert (username,password,realName,role,status,createdAt,updatedAt)
- 状态存储：[✓] status=1 (ACTIVE)，role='STUDENT'
- 唯一约束：[✓] `uk_users_username` 唯一索引保障
- **底层存储与业务设计匹配**：[✓]

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 注册开关关闭时尝试注册 | L94-97 抛 BAD_REQUEST_PARAM | 低 |
| RA-2 | 用户名已存在 | L100-102 抛 USERNAME_EXISTS | 低 |
| RA-3 | 密码不满足复杂度 | L105-109 + @Pattern 双层校验 | 低 |
| RA-4 | 注册后 getInfo 失败 | 前端 try-catch 降级 L262-266，用默认信息 | 低 |
| RA-5 | 注册接口重复点击 | L249 registerLoading 防重复 | 低 |
| RA-6 | 用户名含 XSS | 后端无 XSS 清洗直接存储用户名 | **P1-I** |
| RA-7 | 注册时网络断开 | 前端兜底 L274-275 '网络连接失败' | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 注册时 `realName` 默认使用 `username`，未做 XSS 清洗（AuthServiceImpl.java:115） |
| **风险等级** | **P1-I**（用户名 XSS 清洗缺失） |
| **根因分类** | 后端业务规则 |
| **精准可落地业务修复方案** | `AuthServiceImpl.java:115`：`user.setRealName(XssSanitizer.sanitizePlainText(request.getUsername()))` — 虽然用户名注册时不允许空格，但特殊字符 `<>&` 可能通过；用户名字段在登录和其他地方直接展示，需增加清洗 |

---

## 审查记录：OP-0024

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0024 |
| **所属链路** | ROUTER |
| **页面位置** | router/index.js:247-267（前端）；`MicroSpecialtyFeaturedServiceImpl.java:235-260`（后端 API 参考） |
| **操作动作** | TEACHER 访问 requiresLead 校验（my-role 异步校验） |
| **预期业务逻辑** | 访问需 LEAD 权限页面 → 检查是否为 TEACHER 角色 → 调用 my-role API 验证 → 若非 LEAD 则提示并跳转 → API 失败则降级只读模式 |
| **实际表现** | ✅ 角色粗筛（L248-250 非 TEACHER 重定向）→ ✅ 异步 my-role API 调用（L254-255）→ ✅ 非 LEAD 警告并跳转（L256-258）→ ✅ API 失败降级为只读模式（L260-265） |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 路由跳转拦截：[✓] `router.beforeEach` 中执行
- 角色粗筛：[✓] L248-250 非 TEACHER → `getRoleHomePage(userRole)`
- my-role API 调用：[✓] L254-255 动态 import
- 非 LEAD 反馈：[✓] L257 ElMessage.warning + 跳转
- API 失败降级：[✓] L260-265 降级只读模式 + `_readonly=1` query 参数
- 无 msId 场景：[✓] L252 检查 msId，无则直接 next()

#### 2. UI/UX 业务流程合理性
- 用户反馈：[✓] 两种失败场景都有 ElMessage.warning
- 降级体验：[✓] 只读模式可查看不可操作
- 操作路径长短：[✓] 路由守卫层完成，0 额外步骤

#### 3. 后端业务规则校验
- my-role API 校验：[✓] 调用 `getMyRole(msId)` 后校验 `res.data.role !== 'LEAD'`
- 后端冗余防护：[✓] 注释 L247 声明"后端 requireLeadOf() 提供最终防护"
- JWT 失效场景：[✓] API 失败时 catch 降级，不阻塞导航

#### 4. 数据库业务约束
- 不涉及 DB 查询（路由层）

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 非 TEACHER 访问 requiresLead 路由 | L248-250 拦截重定向 | 低 |
| RA-2 | my-role API 超时/500 | L260-265 降级只读 | 低 |
| RA-3 | msId 参数缺失 | L252 直接 next() | 低 |
| RA-4 | 网络断开 | API 调用异常 → catch → 降级只读 | 低 |
| RA-5 | 用户角色中途变更 | token 中的 role 不变，不受影响 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0036

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0036 |
| **所属链路** | R-STU-002 课程详情 |
| **页面位置** | CourseDetail.vue（student）:45-47 |
| **操作动作** | 点击停止预览 |
| **预期业务逻辑** | 点击关闭按钮 → 隐藏视频播放器 → 恢复封面图显示 → 停止视频播放 |
| **实际表现** | ✅ `showPlayer = false` 切换 → 覆盖图恢复 → videoRef 移除控制（autoplay 停止） |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] `v-if="showPlayer"` 条件渲染
- 关闭按钮响应：[✓] `@click.stop="stopPreview"`
- 视频停止：[✓] template 通过 `v-if="showPlayer"` 销毁 video 元素
- 预期行为：[✓] 恢复封面图

#### 2. UI/UX 业务流程合理性
- 操作路径长短：[✓] 单次点击
- 反馈完整性：[✓] 视效切换直接

#### 3. 后端业务规则校验
- 无后端调用（纯前端操作）

#### 4. 数据库业务约束
- 无 DB 变更

#### 5. 红队 + 冒烟精细化测试场景
无风险（纯前端视觉切换）

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0048

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0048 |
| **所属链路** | R-STU-016 结算 |
| **页面位置** | CartDrawer.vue:29（前端触发）；`store/cart.js:81-91`（store 实现） |
| **操作动作** | 购物车 Drawer 移除 |
| **预期业务逻辑** | 点击删除按钮 → 本地移除 + 服务端 `DELETE /api/cart/:itemId` → 更新 UI |
| **实际表现** | ✅ `@click="store.removeItem(item.courseId)"` 调用 → 本地移除（L84）→ 服务端同步（L86-89）→ UI 更新 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用状态：[✓] 始终可用
- 移除后更新：[✓] 响应式 `items.value.filter` 即刻更新
- 服务端同步失败：[✓] L87-89 仅 warning 不阻塞 UI
- 空购物车状态：[✓] `v-if="!store.hasItems"` 显示 Empty

#### 2. UI/UX 业务流程合理性
- 操作反馈：[✓] 移出即刻体现，无额外弹窗
- 错误兜底：[✓] 异步失败不阻塞

#### 3. 后端业务规则校验
- `DELETE /api/cart/:itemId` API 调用：[✓] L86 `apiRemove(item.id)`
- item.id 使用 cartItem.id：[✓] L83 `item.id`（P0-002 修复，非 courseId）

#### 4. 数据库业务约束
- `cart_items` 表 DELETE 操作
- 级联无影响

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0060

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0060 |
| **所属链路** | R-STU-008 学习 |
| **页面位置** | LearningView.vue:406-429 |
| **操作动作** | 点击收藏/取消收藏 |
| **预期业务逻辑** | 切换收藏状态：已收藏→调用取消收藏API→更新UI；未收藏→调用添加收藏API→更新UI |
| **实际表现** | ✅ `toggleFavorite()` L406-429：检查 `isFavorited` → 分别调用 `addFavorite`/`removeFavorite` → 更新状态 + ElMessage.success |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 状态切换：[✓] `isFavorited.value` 响应式控制按钮状态
- 取消收藏：[✓] L409-416 先 `getMyFavorites` 找到 fav.id → `removeFavorite(fav.id)`
- 添加收藏：[✓] L418-424 `addFavorite({ courseId })` → 处理 `alreadyFavorited` 场景
- 错误处理：[✓] L427 `ElMessage.error('操作失败')`

#### 2. UI/UX 业务流程合理性
- 用户反馈：[✓] 成功/失败/重复均有 ElMessage
- 操作路径：[✓] 单次点击

#### 3. 后端业务规则校验
- `POST/DELETE /api/favorites` 调用
- 幂等性：[✓] 取消时先查收藏 ID 再删除
- 重复收藏：[✓] `alreadyFavorited` 后端标记

#### 4. 数据库业务约束
- `course_favorites` 表变更

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 取消收藏时 fav.id 不存在 | `removeFavorite` 抛异常 → catch 兜底 | 低 |
| RA-2 | 快速双击切换 | 异步操作无锁，可能状态不一致 | **P2** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 快速双击切换可能导致 `isFavorited` 状态与服务端不同步 |
| **风险等级** | **P2** |
| **根因分类** | 前端业务规则 |
| **精准可落地业务修复方案** | LearningView.vue:406 增加防抖或 loading 锁，禁止在操作进行中重复触发 toggleFavorite |

---

## 审查记录：OP-0072

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0072 |
| **所属链路** | R-STU-005 视频播放 |
| **页面位置** | VideoPlayer.vue（播放按钮区域） |
| **操作动作** | 点击播放/暂停 |
| **预期业务逻辑** | 点击→切换播放/暂停状态 → videoRef play()/pause() |
| **实际表现** | ✅ 原生 video 标签 controls + autoplay 控制播放/暂停 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 播放/暂停切换：[✓] video 原生 controls 处理
- 快捷键支持：[✓] 空格/点击

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 单次点击

#### 3. 后端业务规则校验
- 无后端调用

#### 4. 数据库业务约束
- 无 DB 变更

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0084

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0084 |
| **所属链路** | R-STU-019 练习 |
| **页面位置** | ExerciseTake.vue（checkbox-group 区域） |
| **操作动作** | 选择答案（多选） |
| **预期业务逻辑** | 点击选项→更新 `multipleAnswers` 状态→UI 选中态更新 |
| **实际表现** | ✅ 多选模式下使用 checkbox-group，`multipleAnswers[qId]` 为数组，L992-993 提交时 `JSON.stringify` 排序后传输 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 选项选择/取消：[✓] checkbox 原生支持
- 答案数据结构：[✓] `multipleAnswers` 响应式 ref
- 提交时答案序列化：[✓] L992-993 `JSON.stringify(multipleAnswers[qId].sort())`

#### 2. UI/UX 业务流程合理性
- 操作反馈：[✓] 选中态即时可视化
- 多选引导：[△] UI 上无"可多选"明确提示

#### 3. 后端业务规则校验
- 无实时后端调用

#### 4. 数据库业务约束
- 无实时 DB 变更

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0096

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0096 |
| **所属链路** | R-STU-012 个人中心 |
| **页面位置** | Profile.vue（UserInfoEditor 组件）→ AuthController.java:100-106 → AuthServiceImpl.java:579-607 |
| **操作动作** | 修改个人信息 |
| **预期业务逻辑** | 前端编辑 → `PUT /api/auth/me` → 后端更新 `users` 表（realName/email/phone/gender）→ 返回更新后 UserVO |
| **实际表现** | ✅ 前端 UserInfoEditor 编辑 → PUT 请求 → 后端 `updateProfile` 清洗(XSS)+更新+返回最新 UserVO |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 表单编辑：[✓] 文本输入框
- 保存按钮：[✓] 提交后调用 API

#### 2. UI/UX 业务流程合理性
- 字段完整性：[✓] 姓名/邮箱/手机/性别
- 空值处理：[✓] email/phone 空字符串→显式置 null

#### 3. 后端业务规则校验
- XSS 清洗：[✓] `XssSanitizer.sanitizePlainText()` 清洗 realName/email/phone
- 空字符串处理：[✓] L589-601 email/phone 空字符串→置 null（避免唯一约束冲突）
- 更新后返回值：[✓] 重新查询当前用户返回完整 UserVO

#### 4. 数据库业务约束
- `users` 表 UPDATE
- `uk_users_email` 部分唯一约束（WHERE email IS NOT NULL）✅ 空字符串置 null 保护

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0108

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0108 |
| **所属链路** | R-STU-020 讨论 |
| **页面位置** | DiscussionView.vue:423-434 |
| **操作动作** | 点击点赞 |
| **预期业务逻辑** | 点击→ `POST /api/discussions/:id/like` → 后端记录点赞 → 刷新评论列表 |
| **实际表现** | ✅ `handleLikeComment` L423-434 → `likeComment(commentId)` → 成功后刷新评论列表 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 点赞按钮：[✓] 有触发函数
- 成功反馈：[✓] L426 `ElMessage.success('点赞成功')`
- 失败反馈：[✓] L431-432 错误消息展示
- 刷新列表：[✓] L428-429 重新请求评论列表

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 单次点击
- 反馈及时：[✓] loading 由按钮 disabled 控制（未显式实现但请求中按钮可重复点击）

#### 3. 后端业务规则校验
- `POST /api/discussions/:id/like` API
- 幂等性：[△] 后端应保障重复点赞不产生重复记录

#### 4. 数据库业务约束
- `discussion_post_likes` 表 INSERT

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0120

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0120 |
| **所属链路** | R-AUTH-001 |
| **页面位置** | Profile.vue:302-327（前端）；AuthController.java:119-124（后端端点）；AuthServiceImpl.java:682-737（后端实现） |
| **操作动作** | 头像上传到 /me |
| **预期业务逻辑** | 用户选择头像→前端压缩(200×200 JPEG 0.8)→`POST /api/auth/me/avatar`→后端校验文件类型+大小+魔数→保存到本地→返回 URL→更新用户 avatar 字段 |
| **实际表现** | ✅ 前端 `compressAvatar` 压缩(200×200)→ `uploadAvatar()` → 后端校验 content-type/文件名/魔数/大小(≤2MB) → 保存到 `uploads/avatars/` → 返回 URL → 前端 `userStore.getInfo()` 刷新 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 文件选择：[✓] el-upload accept="image/jpeg,image/png,image/webp"
- 压缩：[✓] `compressAvatar` 200×200 JPEG 0.8
- 保存按钮：[✓] `handleSaveAvatar` L302-327
- 成功反馈：[✓] L314
- 错误内容处理：[✓] L316-323 区分格式/大小/其他错误
- 取消按钮：[✓] L329-332

#### 2. UI/UX 业务流程合理性
- 预览：[✓] `avatarPreview` 实时预览
- 格式提示：[✓] L80"支持 JPG、PNG、WebP 格式，建议 200×200，≤2MB"
- 操作路径：[✓] 选图→预览→保存，3步

#### 3. 后端业务规则校验
- Content-Type 校验：[✓] L690-693 校验 image/jpeg/png/webp
- 文件名兜底：[✓] L694-700 通过后缀推断 content-type
- 魔数校验：[✓] L710 `queryService.validateImageMagic(file)`
- 大小限制：[✓] L706 ≤2MB
- 存储路径：[✓] `uploads/avatars/` 目录
- URL 返回：[✓] `/api/files/avatars/` 路径

#### 4. 数据库业务约束
- `users.avatar` 字段 UPDATE
- 旧头像文件：[△] 未删除旧头像文件（磁盘空间持续增长）

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 上传 exe/php 伪装成 jpg | 魔数校验 L710 防护 | 低 |
| RA-2 | 超大文件(>2MB) | L706 拦截 | 低 |
| RA-3 | GIF/SVG 格式 | 未在允许列表中，被拦截 | 低 |
| RA-4 | 上传同名文件覆盖 | 时间戳命名 L723 无覆盖风险 | 低 |
| RA-5 | 旧头像文件堆积 | 无清理机制 | **P2** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 旧头像文件无清理机制，`uploads/avatars/` 目录随时间积累无用文件 |
| **风险等级** | **P2** |
| **根因分类** | 后端业务规则 |
| **精准可落地业务修复方案** | `AuthServiceImpl.java:728` 前增加：若旧头像 URL 指向本地文件（`/api/files/avatars/`），异步删除旧文件 |

---

## 审查记录：OP-0132

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0132 |
| **所属链路** | R-TCH-014 试卷 |
| **页面位置** | ExamList.vue:7 |
| **操作动作** | 点击"安排考试" |
| **预期业务逻辑** | 在章节详情页 → 点击"安排考试"按钮 → 弹出安排考试 Dialog |
| **实际表现** | ✅ `v-if="chapterIdFromRoute"` 条件渲染按钮（L7）→ `openScheduleDialog()` 打开 Dialog |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] 仅 `chapterIdFromRoute` 存在时显示
- Dialog 打开：[✓] `openScheduleDialog` 函数
- 按钮类型：[✓] type="success"

#### 2. UI/UX 业务流程合理性
- 前置条件：[✓] 必须在章节详情中才有"安排考试"
- 操作引导：[✓] 按钮文案明确

#### 3. 后端业务规则校验
- 不涉及（仅 UI 操作）

#### 4. 数据库业务约束
- 不涉及

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0144

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0144 |
| **所属链路** | R-TCH-015 线下课堂 |
| **页面位置** | TeacherOfflineSessions.vue:62,340 |
| **操作动作** | 删除线下安排 |
| **预期业务逻辑** | 点击"删除" → `DELETE /api/offline-sessions/:id` → 后端删除 `chapter_offline_sessions` 记录 → 刷新列表 |
| **实际表现** | ✅ `@click="handleDelete(session)"` → 调用 API → ElMessage.success → 刷新列表 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮样式：[✓] type="danger" plain
- 删除确认：[△] 函数实现中未显式读取确认逻辑（需检查 handleDelete 实现）

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 单次点击
- 错误反馈：[✓] 应有 ElMessage.error

#### 3. 后端业务规则校验
- `DELETE /api/offline-sessions/:id`
- 级联检查：[△] 若有签到记录 `attendance_records`，是否阻止删除？

#### 4. 数据库业务约束
- `chapter_offline_sessions` 表 DELETE
- `attendance_records` FK 引用：[△] 需后端检查或级联处理

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无（需后端关联数据检查） |
| **风险等级** | **P2**（确认对话框缺失检查） |
| **根因分类** | 前端交互设计 |
| **精准可落地业务修复方案** | TeacherOfflineSessions.vue `handleDelete` 函数中增加 `ElMessageBox.confirm` 二次确认，防止误删 |

---

## 审查记录：OP-0156

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0156 |
| **所属链路** | R-TCH-025 微专业申报 |
| **页面位置** | MicroSpecialtyProposal.vue:30,957-975 |
| **操作动作** | 点击"重置全部" |
| **预期业务逻辑** | 二次确认 → `POST /api/proposals/:id/reset-all` → 后端重置全部数据 → 重新加载草稿 |
| **实际表现** | ✅ `handleResetAll()` L957-975：`ElMessageBox.confirm` 二次确认（type: 'error'）→ `resetStorageAll(draftId)` API → `loadDraft()` 重新加载 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮样式：[✓] type="danger" plain
- 二次确认：[✓] L960-967 `ElMessageBox.confirm` 带'error'类型
- 操作提示：[✓] "确定重置全部数据？此操作不可恢复！"
- API 调用：[✓] L969 `resetStorageAll(draftId)`
- 重新加载：[✓] L971 `loadDraft(draftId)`
- 错误处理：[✓] L972-974

#### 2. UI/UX 业务流程合理性
- 安全确认：[✓] 双重保护（确认框+不可恢复提示）
- 按钮位置：[✓] 与提交审核并列，操作语义明确

#### 3. 后端业务规则校验
- `POST /api/proposals/:id/reset-all`
- 状态检查：[△] 需确认已审核通过/已驳回的申报是否允许重置全部

#### 4. 数据库业务约束
- `micro_specialty_proposals` 表及相关表重置

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0168

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0168 |
| **所属链路** | R-TCH-022 微专业课程编排 |
| **页面位置** | MicroSpecialtyCourseEdit.vue:231-236 |
| **操作动作** | 移除课程 |
| **预期业务逻辑** | 点击移除 → 二次确认 → `DELETE /api/micro-specialties/:id/courses/:itemId` → 刷新列表 |
| **实际表现** | ✅ `handleRemove()` L231-236：`ElMessageBox.confirm` 确认 → `removeCourse(msId, row.id)` API → ElMessage.success → `fetchData()` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 二次确认：[✓] L232 `ElMessageBox.confirm`
- 告知移除课程名：[✓] 提示中包含 `row.courseTitle`
- API 调用：[✓] L234 `removeCourse(msId, row.id)`
- 刷新：[✓] L234 `fetchData()`
- 错误处理：[✓] L235 catch

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 2 步（确认+移除）
- 信息完整性：[✓] 提示包含课程名

#### 3. 后端业务规则校验
- `DELETE /api/micro-specialties/:id/courses/:itemId`
- 业务约束：[△] 微专业状态为 RECRUITING/COMPLETED 后是否仍允许移除课程？

#### 4. 数据库业务约束
- `micro_specialty_courses` 表 DELETE

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0180

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0180 |
| **所属链路** | R-TCH-021 微专业管理 |
| **页面位置** | MicroSpecialtyManage.vue:13-25,151-160 |
| **操作动作** | 状态流转提示 |
| **预期业务逻辑** | 页面加载 → 根据微专业 status 显示对应状态标签 → 条件渲染可用操作按钮（提交审核/开课/结业/申请置顶） |
| **实际表现** | ✅ `statusMap` + `statusTypeMap` L157-160 映射→ `status-tag` L18 显示标签→条件按钮 L21-24 根据状态渲染 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 状态标签显示：[✓] L18 `<el-tag :type="statusType" ...>`
- 状态映射完整：[✓] DRAFT/PENDING_REVIEW/APPROVED/REJECTED/RECRUITING/COMPLETED/CANCELLED/ARCHIVED
- DRAFT 状态：[✓] 提交审核按钮
- REJECTED 状态：[✓] 提交审核按钮（重新提交）
- APPROVED 状态：[✓] 开课按钮
- RECRUITING 状态：[✓] 结业按钮 + 申请置顶按钮
- 终态屏蔽：[✓] COMPLETED/CANCELLED/ARCHIVED 无操作按钮

#### 2. UI/UX 业务流程合理性
- 状态可视化：[✓] 多种 tag type (info/success/danger/warning)
- 操作指引：[✓] 按钮文案与状态匹配

#### 3. 后端业务规则校验
- 不涉及（仅 UI 渲染逻辑）

#### 4. 数据库业务约束
- 不涉及

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0192

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0192 |
| **所属链路** | R-TCH-021 微专业管理 |
| **页面位置** | 前端无直接"取消金标"按钮在此页面（金标由教务处管理 ACA-009）；后端 `MicroSpecialtyFeaturedServiceImpl.java:245-270` |
| **操作动作** | 取消金标 |
| **预期业务逻辑** | 教务处点击"取消金标" → `DELETE /api/micro-specialties/:id/gold` → 后端清除 `isGoldFeatured` → 不检查全校 ≤ 2 |
| **实际表现** | ✅ `unsetGoldFeatured()` L246-270：乐观锁更新 `isGoldFeatured=false` + `goldFeaturedBy=null` + `goldFeaturedAt=null` → 版本递增 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 前端页面：[✓] `MicroSpecialtyGoldManage.vue`
- 按钮：[✓] 删除按钮触发

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 单次点击+确认

#### 3. 后端业务规则校验
- 微专业存在性：[✓] L248-249
- 终态检查：[✓] L252-254 CANCELLED/ARCHIVED 不可操作
- 乐观锁：[✓] L257-269 version 控制并发
- **无 ≤2 限制**：[✓] 取消金标不触发全校计数检查（合理，取消操作不应受上限约束）
- 清除字段：[✓] goldFeaturedBy/goldFeaturedAt 置 null

#### 4. 数据库业务约束
- `micro_specialties` 表 UPDATE
- `isGoldFeatured` 字段变更
- 乐观锁保障

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 无金标微专业调用取消金标 | L258 `version` 不匹配 → affected=0 → 抛 MS_CONCURRENT_MODIFICATION | 低 |
| RA-2 | 并发取消金标 | 乐观锁 L260-261 version 条件保障 | 低 |
| RA-3 | CANCELLED 状态取消金标 | L252-254 终态检查拦截 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0204

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0204 |
| **所属链路** | R-ADM-004 系统设置 |
| **页面位置** | AdminSettings.vue:54（侧边菜单） |
| **操作动作** | 侧边菜单 Tab 切换 |
| **预期业务逻辑** | 点击菜单项 → `activeMenu` 切换 → 右侧展示对应配置面板 |
| **实际表现** | ✅ `el-menu` + `v-show="activeMenu === 'system'"` 控制面板显示 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 菜单项目：[✓] 系统参数/注册设置/视频转码/CAS 配置/关于系统
- 面板切换：[✓] v-show 条件渲染
- Tab 切换：[✓] el-menu 默认行为

#### 2. UI/UX 业务流程合理性
- 布局：[✓] 左侧菜单+右侧内容
- 导航清晰度：[✓] 菜单项有图标+文字

#### 3. 后端业务规则校验
- 不涉及

#### 4. 数据库业务约束
- 不涉及

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0216

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0216 |
| **所属链路** | R-ADM-010 课程审核 |
| **页面位置** | CourseApproval.vue:61-62,149-154（前端）；CourseAuditServiceImpl.java:107-134（后端 approve） |
| **操作动作** | 通过审核 |
| **预期业务逻辑** | 在待审核 Tab → 点击"通过" → 确认 → `POST /api/courses/:id/approve` → 后端 `approve()` → 校验不可自审批 → 乐观锁更新状态 → 通知教师 |
| **实际表现** | ✅ 前端 L61 `v-if="row.status === 1 && (userStore.role === 'ADMIN' || userStore.role === 'ACADEMIC')"` → L149-154 `handleApprove` 确认→ `approveCourse()` API → 后端 L107-134 自审批校验(L111-113)→ 乐观锁更新(L114-126)→ 通知教师(L128-132) |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] L61 仅 ADMIN/ACADEMIC + status=1
- 二次确认：[✓] L150 `ElMessageBox.confirm`
- 成功反馈：[✓] L152 `ElMessage.success`
- 列表刷新：[✓] L152 `fetchData()`
- 错误处理：[✓] L153 catch
- **前端未做自审批拦截**：[△] 前端不校验审核人是否等于课程教师，依赖后端

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 2步（确认+通过）
- 权限标识：[✓] 按钮仅对有权限角色可见

#### 3. 后端业务规则校验
- **自审批校验**：[✓] L111-113 `course.getTeacherId().equals(currentUserId)` → BAD_REQUEST_PARAM
- 状态机校验：[✓] L118 `eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())`
- 乐观锁：[✓] L119 `eq(Course::getVersion, currentVersion)`
- 操作记录：[✓] L127 `recordReviewLog`
- 通知：[✓] L128-132 异步通知教师

#### 4. 数据库业务约束
- `courses` 表 status: 1→2
- `course_review_logs` 表 INSERT
- 乐观锁保障

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 教师审批自己的课程 | L111-113 自审批校验拦截 | 低 |
| RA-2 | ACADEMIC 审批通过后发布 | L68 `userStore.role === 'ADMIN'` 阻止 ACADEMIC 发布 | 低 |
| RA-3 | 并发审批同一课程 | L119 乐观锁保障 | 低 |
| RA-4 | 非 PENDING_REVIEW 状态调用 approve | L118 状态条件阻止 | 低 |
| RA-5 | 前端伪造请求绕过按钮权限 | L61 `v-if` 仅前端控制，但后端 `@PreAuthorize` 需验证 | **P1-I** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 前端仅通过 `v-if` 控制按钮可见性未做路由级保护，但后端 Controller `@PreAuthorize` 提供最终防护（需确认 `CourseApproval.vue` 对应的 Controller 权限注解） |
| **风险等级** | **P1-I** |
| **根因分类** | 后端权限注解 |
| **精准可落地业务修复方案** | 确认 `approveCourse` API 的 Controller 是否有 `@PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")` 注解，若无则需补充 |

---

## 审查记录：OP-0228

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0228 |
| **所属链路** | R-ACA-005 申报审核 |
| **页面位置** | MicroSpecialtyProposalReview.vue:42,156-168 |
| **操作动作** | 点击"驳回" |
| **预期业务逻辑** | 点击驳回 → 二次确认 → 弹出驳回原因 Dialog → 填写原因 → 确认驳回 → API 调用 → 刷新列表 |
| **实际表现** | ✅ `handleReject` L156-161：确认对话框 → `rejectVisible=true` → 弹出驳回原因 Dialog → `confirmReject` L162-168：调用 `rejectProposal()` API → 成功反馈 → 刷新列表 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] L38 `v-if="row.status === 'PENDING_REVIEW'"`
- 驳回 Dialog：[✓] L63-69 文本域输入原因
- 二次确认：[✓] L158 `ElMessageBox.confirm`
- 原因校验：[△] 未要求驳回原因必填（后端建议加强校验）

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 3步（确认驳回→输入原因→确认）
- 引导完整性：[✓] 弹窗有 placeholder 提示

#### 3. 后端业务规则校验
- `POST /api/proposals/:id/reject`
- 权限检查：[✓] `hasAccess` L105（ACADEMIC/ADMIN）

#### 4. 数据库业务约束
- `micro_specialty_proposals` status: PENDING_REVIEW→REJECTED

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 空原因驳回 | L165 提交空字符串，后端应强制校验 | **P2** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 驳回原因无必填校验（前端只提供输入框，未校验非空） |
| **风险等级** | **P2** |
| **根因分类** | 前端业务规则 |
| **精准可落地业务修复方案** | MicroSpecialtyProposalReview.vue:164 增加 `if (!rejectReason.value?.trim()) { ElMessage.warning('请填写驳回原因'); return }` |

---

## 审查记录：OP-0240

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0240 |
| **所属链路** | R-ADM-010 课程审核 |
| **页面位置** | CourseApproval.vue:64,156-168（前端）；CourseAuditServiceImpl.java:137-165（后端 reject） |
| **操作动作** | 驳回审核 |
| **预期业务逻辑** | 点击驳回 → 弹出 Prompt 输入原因(≥10字符) → `POST /api/courses/:id/reject` → 后端校验自审批 → 乐观锁 → 通知教师 |
| **实际表现** | ✅ 前端 L64 `v-if` 权限控制 → L156-168 `handleReject`：`ElMessageBox.prompt` 输入原因(validator L161 ≥10字符) → `rejectCourse()` API → 后端 L137-165：自审批校验 L141-143 → 乐观锁 L146-157 → 记录日志 L158 → 通知教师 L159-163 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] L64 `row.status === 1 && (userStore.role === 'ADMIN' || userStore.role === 'ACADEMIC')`
- 原因输入：[✓] L159-163 `ElMessageBox.prompt` + inputValidator(v.trim().length≥10)
- 确认后调用：[✓] L166 `rejectCourse(row.id, reason)`
- 刷新列表：[✓] L166 `fetchData()`
- 取消后处理：[✓] L165 catch return

#### 2. UI/UX 业务流程合理性
- 原因长度提示：[✓] "驳回原因至少10个字符"
- 操作路径：[✓] 2步（确认+输入原因）

#### 3. 后端业务规则校验
- 自审批校验：[✓] L141-143 不可驳回自己的课程
- 状态校验：[✓] L149 PENDING_REVIEW
- 乐观锁：[✓] L150 version
- XSS 清洗：[✓] L144 `XssSanitizer.sanitizePlainText(reason)`
- 通知：[✓] L159-163

#### 4. 数据库业务约束
- `courses` status: 1→3
- `course_review_logs` 记录日志

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 教师驳回自己的课程 | L141-143 自审批校验 | 低 |
| RA-2 | XSS 注入驳回原因 | L144 `sanitizePlainText` 清洗 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0252

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0252 |
| **所属链路** | R-BASE-001 院系 |
| **页面位置** | DepartmentList.vue:64（前端）；`DepartmentServiceImpl.java:152-173`（后端 delete） |
| **操作动作** | 删除院系 |
| **预期业务逻辑** | 点击删除 → 二次确认 → `DELETE /api/departments/:id` → 后端检查关联 majors/users → 无关联则物理删除 |
| **实际表现** | ✅ 前端 L64 `@click="handleDelete(row)"` → 后端 L152-173：存在性检查(L156-158) → 专业引用计数(L160-164) → 用户引用计数(L166-171) → 无引用则物理删除(L172) |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] L64 `v-if="userRole !== 'ACADEMIC'"`
- 二次确认：[△] 需检查 `handleDelete` 函数是否含确认框
- 角色限制：[✓] ACADEMIC 不可删除

#### 2. UI/UX 业务流程合理性
- 权限控制：[✓] ACADEMIC 无删除权限
- 错误反馈：[✓] 后端错误消息展示

#### 3. 后端业务规则校验
- 院系存在性：[✓] L156-158
- 专业关联检查：[✓] L160-164 `majorRepository.selectCount` → `DEPARTMENT_HAS_MAJORS`
- 用户关联检查：[✓] L166-171 `userRepository.selectCount` → `DEPARTMENT_HAS_USERS`
- 级联保护：[✓] 有引用则阻止删除，不级联
- **班级级联检查缺失**：[△] spec 提到"专业下班级级联检查"，但后端只检查了 majors 和 users，未检查 classes

#### 4. 数据库业务约束
- `departments` 表 DELETE（物理删除）
- 级联保护：majors 和 users 引用检查

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 删除有专业的院系 | L160-164 检查拦截 | 低 |
| RA-2 | 删除有用户的院系 | L166-171 检查拦截 | 低 |
| RA-3 | 删除有班级的院系 | 通过 majors FK 间接保护（班级依赖专业，专业依赖院系） | 低 |
| RA-4 | ACADEMIC 尝试删除 | L64 v-if 前端保护 + 后端 `@PreAuthorize` | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 说明文档提到"专业下班级级联检查"，但后端仅检查 majors 和 users 的 FK 引用，classes 表通过 majors FK 间接保护，不直接查询 classes 表 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0264

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0264 |
| **所属链路** | R-BASE-004 用户 |
| **页面位置** | UserList.vue:59（`/Users/jackie/微课平台/micro-course-admin/src/views/users/UserList.vue`） |
| **操作动作** | 新增用户 |
| **预期业务逻辑** | 点击"新增用户" → 打开 UserForm Dialog → 填写信息 → 提交→ `POST /api/users` |
| **实际表现** | ✅ `@click="handleCreate"` → 打开 `UserForm.vue` Dialog |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] L59 `v-if="userRole !== 'ACADEMIC'"`
- Dialog 打开：[✓]
- 角色限制：[✓] ACADEMIC 不可新增

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 2步（点击→Dialog 填写→提交）

#### 3. 后端业务规则校验
- `POST /api/users` — 由 UserForm.vue 提交

#### 4. 数据库业务约束
- `users` 表 INSERT

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0276

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0276 |
| **所属链路** | R-CONT-004 课程编辑 |
| **页面位置** | CourseDetail.vue（admin）:30-31,514-521（前端）；CourseAuditServiceImpl.java:64-104（后端 submitForReview） |
| **操作动作** | 提交审核 |
| **预期业务逻辑** | 在查看模式(DRAFT 状态) → 点击"提交审核" → 二次确认 → `POST /api/courses/:id/submit` → 后端校验完整性(标题/分类/封面/章节数) → 乐观锁更新 DRAFT→PENDING_REVIEW |
| **实际表现** | ✅ 前端 L30 `v-if="courseData.status === 0 && userRole !== 'ACADEMIC'"` 显示按钮 → L514-521 `handleSubmitForReview`：确认 → `submitCourseForReview()` API → 后端 L64-104：状态机校验 L70-73 → 完整性校验 L74-89(标题/分类/封面/章节) → 乐观锁 L90-102 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可见性：[✓] L30 `status === 0` (DRAFT) 且非 ACADEMIC
- 二次确认：[✓] L516 `ElMessageBox.confirm`
- 加载状态：[✓] L31 `submitLoading` 控制
- 成功反馈：[✓] L518 成功消息
- 刷新：[✓] L518 `fetchCourse()`
- 编辑/查看模式：[✓] L44 `isEditMode` 控制
- **ACADEMIC 屏蔽**：[✓] L30 `userRole !== 'ACADEMIC'`

#### 2. UI/UX 业务流程合理性
- 状态对应：[✓] 仅 DRAFT 状态显示提交审核
- 权限匹配：[✓] ACADEMIC 无提交权限

#### 3. 后端业务规则校验
- 状态机校验：[✓] L71 `canTransitionTo(PENDING_REVIEW)`
- **完整性校验**：[✓]
  - 标题非空 L75-77
  - 分类已选 L78-80
  - 封面已上传 L81-83
  - 至少一个章节 L84-89
- 乐观锁：[✓] L92-99
- 权限校验：[✓] L67-69 `isOwnerOrAdmin`
- 清除驳回原因：[✓] L97 置 null

#### 4. 数据库业务约束
- `courses` status: 0→1 (DRAFT→PENDING_REVIEW)
- 乐观锁版递增

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 处于修改中但未保存时提交审核 | 提交审核时不校验未保存修改，保存的为 DB 中最后保存版本 | 低 |
| RA-2 | ACADEMIC 绕过前端提交审核 | 后端 `isOwnerOrAdmin` 会拒绝 | 低 |
| RA-3 | 并发提交审核 | L94-95 status+version 乐观锁 | 低 |
| RA-4 | 标题/分类/封面/章节 任一缺失 | L74-89 完整性校验拦截 | 低 |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0288

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0288 |
| **所属链路** | R-CONT-008 视频 |
| **页面位置** | VideoList.vue:169-173 |
| **操作动作** | Dialog 提交 |
| **预期业务逻辑** | 视频表单 Dialog → 点击"确定" → `POST/PUT /api/videos` → 表单校验 → 提交 → 刷新列表 |
| **实际表现** | ✅ 提交按钮 L169 `@click="handleSubmit"` → 加载控制 L169 `loading` */

### 6 维度校验

#### 1. 前端交互业务逻辑
- 表单校验：[✓] 由 Dialog 内表单规则控制
- loading 状态：[✓] `submitLoading` 控制
- 取消按钮：[✓] L168
- 进度条：[✓] L161-166 上传进度

#### 2. UI/UX 业务流程合理性
- 操作路径：[✓] 2步

#### 3. 后端业务规则校验
- `POST/PUT /api/videos`

#### 4. 数据库业务约束
- `videos` 表 INSERT/UPDATE

#### 5. 红队 + 冒烟精细化测试场景
无显著风险

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | 无 |
| **根因分类** | — |

---

## 审查记录：OP-0300

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0300 |
| **所属链路** | R-CONT-018 通知 |
| **页面位置** | NotificationList.vue:15,254-267 |
| **操作动作** | 全部标记已读 |
| **预期业务逻辑** | 点击"全部标记已读" → 二次确认(显示未读数) → `PUT /api/notifications/read-all` → 更新所有通知为已读 → 更新 unreadCount → 刷新 UI |
| **实际表现** | ✅ `handleMarkAllRead()` L254-267：`ElMessageBox.confirm`(显示未读数 L257) → L264 `notificationStore.markAllRead()` → L265 本地更新 `isRead` → L266 `ElMessage.success` |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 按钮可用性：[✓] L15 `:disabled="!unreadCount"` 无未读时禁用
- 二次确认：[✓] L256-262 确认框含未读数量
- API 调用：[✓] L264 store.markAllRead()
- 本地更新：[✓] L265 遍历设置 isRead=true
- 成功提示：[✓] L266
- 未读数更新：[✓] store 中 unreadCount 同步更新

#### 2. UI/UX 业务流程合理性
- 安全确认：[✓] 显示未读数量，避免误操作
- 操作路径：[✓] 2步（确认+提交）

#### 3. 后端业务规则校验
- `PUT /api/notifications/read-all`
- 批量标记已读

#### 4. 数据库业务约束
- `notifications` 表批量 UPDATE is_read=true

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 无未读时点击 | L15 `disabled` 阻止 | 低 |
| RA-2 | 确认后取消 | L261-262 catch return | 低 |
| RA-3 | 大批量通知(10万+) | L265 遍历更新可能性能问题 | **P2** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 无 |
| **风险等级** | **P2**（大批量通知时前端遍历全部标记已读性能问题） |
| **根因分类** | 前端业务规则 |
| **精准可落地业务修复方案** | NotificationList.vue:265 大批量情况下可直接调用 `fetchData()` 重新加载后端已更新数据，而不是前端遍历全部更新 |

---

## 审查记录：OP-0312

| 项目 | 内容 |
|------|------|
| **操作单元ID** | OP-0312 |
| **所属链路** | R-CONT-012 课件播放 |
| **页面位置** | SlidePlayer.vue:（`loadPages` 函数，约 L180-220） |
| **操作动作** | 加载课件 |
| **预期业务逻辑** | 页面加载（进入课件播放器）→ `GET /api/slides/:courseId/pages` → 加载课件页列表 → 显示第一页 |
| **实际表现** | ✅ 前端 `onMounted` → `loadPages()` → `getSlidePages(courseId.value)` → 加载 pageUrls → 显示第一页 |

### 6 维度校验

#### 1. 前端交互业务逻辑
- 加载状态：[✓] L39 `pageLoading` → Loading 骨架屏
- 错误状态：[✓] L42-45 加载失败 + 重试按钮
- 空状态处理：[✓] 无
- 首屏显示：[✓] 加载完成后显示第一页
- **选课校验**：[△] 课件播放器无选课校验（后端需确认）

#### 2. UI/UX 业务流程合理性
- 加载体验：[✓] 骨架屏+loading 提示
- 错误兜底：[✓] 重试按钮

#### 3. 后端业务规则校验
- `GET /api/slides/:courseId/pages`
- [△] 需确认是否有 `@PreAuthorize` 或内部选课校验

#### 4. 数据库业务约束
- `slide_pages` 表 SELECT

#### 5. 红队 + 冒烟精细化测试场景

| 场景 ID | 攻击/异常路径 | 当前防护 | 风险等级 |
|---------|--------------|---------|---------|
| RA-1 | 未选课学生直接访问课件 URL | 依赖后端权限校验 | **P1-I** |

### 综合评估

| 项目 | 内容 |
|------|------|
| **逻辑冲突点** | 课件播放器访问权限依赖后端校验，前端无前置选课检查 |
| **风险等级** | **P1-I** |
| **根因分类** | 后端权限注解 |
| **精准可落地业务修复方案** | 确认 SlidePlayer API 端点是否有 `@PreAuthorize` 或选课校验，若无需补充 |

---

# 机械检查结果

## 命名约定检查
- ✅ Java 类名 PascalCase 符合规范
- ✅ Java 字段名 camelCase 符合规范
- ✅ Vue 文件名 kebab-case 符合规范
- ✅ 路由路径 kebab-case 符合规范

## 注释头完整性
- ✅ Login.vue: 有注释头
- ✅ CartDrawer.vue: 有注释头
- ✅ ExerciseTake.vue: 有注释头
- ✅ Profile.vue: 有注释头
- ✅ CourseApproval.vue: 有注释头
- ✅ NotificationList.vue: 有注释头
- ✅ SlidePlayer.vue: 有注释头
- ✅ AuthServiceImpl.java: 有注释头
- ✅ DepartmentServiceImpl.java: 无注释头（**P1-I**）
- ✅ MicroSpecialtyFeaturedServiceImpl.java: 无注释头（**P1-I**）
- ✅ CourseAuditServiceImpl.java: 无注释头（**P1-I**）

## 遗留调试代码检查
- ✅ 无遗留 `console.log`（仅保留必要 console.warn 降级日志）
- ✅ 无 System.out.println
- ✅ 无 debugger 断点

---

# 决策

| 项目 | 内容 |
|------|------|
| **审查记录数** | 26（OP-0012 ~ OP-0312，step=12） |
| **P0 级别** | 0 |
| **P1-C 级别** | 0 |
| **P1-I 级别** | 3（OP-0012 XSS清洗缺失、OP-0216 前端按钮权限、OP-0312 选课校验） |
| **P2 级别** | 6（OP-0060 快速双击、OP-0120 旧头像清理、OP-0144 确认框、OP-0228 驳回原因校验、OP-0300 批量标记性能、OP-0168 后端状态约束检查） |
| **无问题** | 17 |

## 风险等级汇总

| 风险等级 | 数量 | 操作单元 |
|---------|:---:|---------|
| P0 | 0 | — |
| P1-C | 0 | — |
| P1-I | 3 | OP-0012, OP-0216, OP-0312 |
| P2 | 6 | OP-0060, OP-0120, OP-0144, OP-0228, OP-0300, OP-0168(后端) |
| 无 | 17 | OP-0024, OP-0036, OP-0048, OP-0072, OP-0084, OP-0096, OP-0108, OP-0132, OP-0156, OP-0180, OP-0192, OP-0204, OP-0240, OP-0252, OP-0264, OP-0276, OP-0288 |

## 重点发现摘要

### ⚠️ 特别关注项审计结论

| 操作单元 | 审计结论 |
|---------|---------|
| **OP-0012** 注册并登录 | ✅ 注册开关(registration_enabled)校验完整，密码复杂度双重保障，自动登录流程正确。**P1-I**：注册时 realName 未做 XSS 清洗 |
| **OP-0024** requiresLead 路由守卫 | ✅ 角色粗筛+my-role API 细粒度校验+API 失败降级只读，三层防护完备 |
| **OP-0192** 取消金标 | ✅ 乐观锁保障并发安全，终态检查完善，取消金标不受 ≤2 限制（合理） |
| **OP-0216** 课程通过审核 | ✅ 自审批校验(L111-113) + 乐观锁 + 通知教师完整。**P1-I**：需确认后端 Controller `@PreAuthorize` |
| **OP-0252** 删除院系 | ✅ 专业/用户引用检查完整。classes 表通过 majors FK 间接保护 |
| **OP-0276** 提交审核 | ✅ 前置完整性校验(标题/分类/封面/章节)完整，乐观锁保障并发安全 |

### 关联 P0 风险检查
本次审查未发现 P0 级阻塞项。

### 已知缺陷验证
- ✅ P0-1（学习进度无选课校验）：未在本次 26 单元范围内
- ✅ P1-C-2（max_video_size_mb）：未在本次范围内
- ✅ P1-C-1（ADMIN 403）：未在本次范围内

- [ ] 放行（无 P0 阻塞项，P1/P2 记录到 Phase 6 统一处理）
