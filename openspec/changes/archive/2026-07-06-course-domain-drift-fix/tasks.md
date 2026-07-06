# Tasks: 课程管理域 Spec 漂移全量修复

> **OpenSpec Change**: `course-domain-drift-fix`
> **Schema**: spec-driven
> **总任务数**: 56
> **优先级**: 老板指令"必须全量修复,不能留下任何技术债务"

---

## 阶段 1: P0 必修 + V1 (5 项必修, 立即阻塞)

- [x] **1.1 修复 S1: submitForReview() 补"章节下至少一个视频或练习"校验**
  - 位置: `CourseAuditServiceImpl.java:submitForReview()`
  - 加 SELECT EXISTS 子查询: 至少 1 个章节下有视频 / 练习 / 课件
  - 测试: 写新 TC, 验证空章节/全空视频/全空练习/正常课程 4 种场景
  - 验收: submitForReview(空章节课程) → 抛 BusinessException(CHAPTER_HAS_NO_CONTENT)

- [x] **1.2 修复 S2: RejectRequest DTO 加 @Size(min=10)**
  - 位置: `RejectRequest.java`
  - 加 `@Size(min=10, max=500)`
  - 测试: 验证 9 字符/10 字符/500 字符/501 字符 4 种场景
  - 验收: 9 字符 → 400 Validation Error; 10 字符 → 正常驳回

- [x] **1.3 修复 S3: publish() 增加"此前 PUBLISHED"历史校验**
  - 位置: `Course.java` 加 `lastPublishedAt` 字段 (新 DB 迁移 V155)
  - 位置: `CourseAuditServiceImpl.publish()` 加历史校验
  - 测试: 草稿→CLOSED→publish 应拒绝; 草稿→APPROVED→PUBLISHED→CLOSED→publish 应允许
  - 验收: DRAFT→CLOSED→publish 抛 BusinessException(NOT_PREVIOUSLY_PUBLISHED)

- [x] **1.4 修复 S4: updateStatus() 拒绝 status=1/4**
  - 位置: `CourseController.java:updateStatus()` 加 status==1/4 阻断
  - 抛出 BusinessException(COURSE_STATUS_TRANSITION_NOT_ALLOWED)
  - 测试: PUT status=1 应 400; PUT status=4 应 400; PUT status=5 应允许
  - 验收: 通用端点不能再用于提审/发布

- [x] **1.5 修复 V1: CourseServiceImpl.java:237 硬编码改为枚举引用**
  - 位置: `CourseServiceImpl.java:237`
  - 改为 `.eq(Course::getStatus, CourseStatus.PENDING_REVIEW.getCode())`
  - 测试: 验证 @Scheduled 任务检测 PENDING_REVIEW > 48h 课程正确触发
  - 验收: 旧版查 CLOSED 课程, 新版查 PENDING_REVIEW 课程

---

## 阶段 2: 状态机统一入口重构 (模式 2 治本)

- [x] **2.1 创建 CourseStateMachine 接口**
  - 位置: `micro-course-api/src/main/java/com/microcourse/service/CourseStateMachine.java`
  - 方法: transition / checkTransition / registerGuard

- [x] **2.2 实现 CourseStateMachineImpl**
  - 位置: `micro-course-api/src/main/java/com/microcourse/service/impl/CourseStateMachineImpl.java`
  - 封装 canTransitionTo + 乐观锁 + 业务守卫 hook + 自审批阻断

- [x] **2.3 创建 CourseStateMachineConfig 注册守卫**
  - 位置: `micro-course-api/src/main/java/com/microcourse/config/CourseStateMachineConfig.java`
  - 注册 DRAFT→PENDING_REVIEW, PENDING_REVIEW→REJECTED, CLOSED→PUBLISHED 等守卫

- [x] **2.4 重构 CourseAuditServiceImpl.approve() 使用 CourseStateMachine**
  - 删除原始硬编码 WHERE
  - 改为: courseStateMachine.transition(id, APPROVED, actor, context)

- [x] **2.5 重构 CourseAuditServiceImpl.reject() 使用 CourseStateMachine**
  - 同上, rejectReason 放 context

- [x] **2.6 重构 CourseAuditServiceImpl.publish() 使用 CourseStateMachine**
  - 同上, 调用完整 publish 守卫

- [x] **2.7 重构 CourseAuditServiceImpl.submitForReview() 使用 CourseStateMachine**
  - 含完整 submit 守卫

- [x] **2.8 重构 CourseAdminServiceImpl.updateStatus() 委托给 CourseStateMachine**
  - 拒绝 status=1/4 (P0 S4 修复)
  - 其他状态允许但走状态机

- [x] **2.9 写 ExhaustiveStateMachineTest**
  - 覆盖 7×7=49 个状态转换
  - 每个转换验证: canTransitionTo / 业务守卫 / 乐观锁 / 自审批阻断

---

## 阶段 3: 权限与校验修复 (模式 3 治本)

- [x] **3.1 submit 端点移除 ADMIN 角色**
  - 位置: `CourseController.java:submitForReview()` `@PreAuthorize`
  - 改为: `hasRole('TEACHER')`
  - 测试: ADMIN 调用 submit 应 403

- [x] **3.2 收藏端点路径统一 + 权限限制 STUDENT**
  - 路径: 统一为 `/api/courses/{id}/favorite`
  - 权限: `hasRole('STUDENT')`
  - 前端同步: `api/favorite.js` 调整路径

- [x] **3.3 实现 GET /api/courses/teacher/{teacherId} 端点**
  - 位置: `CourseController.java` 加新方法
  - 权限: TEACHER(本人)/ADMIN/ACADEMIC
  - 测试: 前端 TeacherCourseList.vue 调用

- [x] **3.4 实现 POST /api/videos/{id}/retry 端点 (转码重试)**
  - 位置: `VideoController.java` 加新方法
  - 权限: TEACHER(创建者)/ADMIN
  - 调用 service: videoService.retryTranscode(id)

- [x] **3.5 实现 GET /api/videos/{id}/analytics 端点 (播放分析)**
  - 位置: `VideoController.java` 加新方法
  - 返回: VideoAnalyticsVO { playCount, avgWatchTime, completionRate }

- [x] **3.6 实现 POST /api/videos/batch-upload 端点 (批量上传)**
  - 位置: `VideoController.java` 加新方法
  - 多文件上传, 复用单文件上传 service

- [x] **3.7 POST /api/courses/{id}/reviews 移除 ADMIN 角色**
  - 改为: `hasRole('STUDENT')`

- [x] **3.8 DELETE /reviews/{reviewId} 改为仅 ADMIN**
  - 移除 ACADEMIC

- [x] **3.9 CourseController.updateCover() 业务逻辑下沉到 CourseCoverService**
  - 提取 `validateCoverImageMagic` 到工具类
  - 提取文件大小校验到工具类
  - Controller 只调用 service.updateCover(file)

- [x] **3.10 CourseController.getCourseStudents() 角色判断下沉**
  - 删除 `SecurityUtil.hasRole("TEACHER")` 直接判断
  - 改为调用 service, service 内部分支

- [x] **3.11 VideoController.uploadCover() 业务逻辑下沉**
  - 文件大小/contentType 校验下沉到 service

- [x] **3.12 VideoController.reportVideoProgress() DTO 组装下沉**
  - 改用 `@RequestBody VideoProgressReportRequest` DTO
  - 删除 asInt() 工具方法
  - 删除手动 DTO 构造

- [x] **3.13 CourseServiceImpl.java:158 硬编码 status==4 替换为枚举**
  - 改为: `CourseStatus.PUBLISHED.getCode()`

- [x] **3.14 R.java 删除 timestamp 字段 (与契约对齐)**
  - 删除 `private long timestamp`
  - 删除 getTimestamp()
  - 删除构造方法中 `this.timestamp = System.currentTimeMillis()`
  - 测试: 验证响应无 timestamp 字段

- [x] **3.15 VideoController.reportVideoProgress 改用 DTO 而非 Map**
  - 创建 `VideoProgressReportRequest.java` DTO
  - 字段: videoProgress, videoPosition, totalWatchTime

---

## 阶段 4: OpenAPI 自动生成 (模式 1 治本)

- [x] **4.1 pom.xml 集成 springdoc-openapi-starter-webmvc-ui 2.3.0**
  - 添加 dependency

- [x] **4.2 创建 OpenApiConfig.java 配置**
  - API 标题、版本、描述
  - JWT Bearer 安全方案

- [x] **4.3 给 CourseController 23 个端点加 @Operation/@Parameter/@ApiResponse 注解**
  - 涉及 CourseController.java

- [x] **4.4 给 CourseChapterController 6 个端点加注解**

- [x] **4.5 给 VideoController 11 个端点加注解**

- [x] **4.6 给 VideoStreamController 1 个端点加注解**

- [x] **4.7 给 CourseCategoryController 5 个端点加注解**

- [x] **4.8 给 TagController 7 个端点加注解**

- [x] **4.9 给 CourseBundleController 10 个端点加注解**

- [x] **4.10 给 LessonController 6 个端点加注解**

- [x] **4.11 给 SlideController 11 个端点加注解**

- [x] **4.12 给 CourseReviewController 5 个端点加注解**

- [x] **4.13 写 scripts/openapi-gen.sh 自动生成 YAML**
  - 启动后端, 拉取 /v3/api-docs, 转 YAML
  - 输出 docs/api/openapi.yaml

- [x] **4.14 创建 ContractEndpointCoverageTest**
  - 反射扫描所有 @RestController 的 @RequestMapping
  - 解析 docs/api/openapi.yaml 的 paths
  - 断言两者一致

- [x] **4.15 CI 集成: docs/api/openapi.yaml diff 门禁**
  - 新增端点但未更新 openapi.yaml → fail

---

## 阶段 5: 数据字典反向生成 (模式 1 子项)

- [x] **5.1 写 scripts/db-schema-doc-gen.sh**
  - 解析所有 V*__*.sql 提取表/字段/索引/约束

- [x] **5.2 跑脚本生成 docs/data-dictionary.generated.md**

- [x] **5.3 diff 生成 md 与手写 docs/数据字典.md**
  - 输出 14 项漂移列表

- [x] **5.4 修正 docs/数据字典.md 14 项漂移**
  - JSONB→TEXT: freeDeptIds
  - 索引补充: idx_courses_is_recommended 等 3 项
  - CHECK 补充: chk_courses_status/difficulty/course_type
  - 已删约束移除: uk_cc_course_sort
  - 不存在字段移除: course_prerequisites.deletedAt
  - 类型修正: SMALLINT, NOT NULL
  - 索引命名统一
  - DEFAULT 修正

- [x] **5.5 CI 集成: 数据字典 diff 门禁**
  - 生成 md 与手写 md 不一致 → fail

---

## 阶段 6: 权限矩阵可执行化 (模式 3 子项)

- [x] **6.1 创建 docs/permission-matrix-v4.0.yaml (机器可读)**
  - 85 端点 × 4 角色矩阵

- [x] **6.2 重写 docs/权限矩阵.md v4.0 (人类可读)**
  - 修复 11 项漂移
  - 补充 30 端点 (分类/课时/课件/定价/批量/状态/复制/封面)

- [x] **6.3 写 EndpointPermissionTest**
  - 加载权限矩阵 v4.0 YAML 为预期表
  - 反射扫描 Controller @PreAuthorize 为实际表
  - 断言一致

- [x] **6.4 CI 集成: bash scripts/check-permission.sh**
  - 矩阵与代码不一致 → fail

---

## 阶段 7: 静态扫描增强 (模式 3 子项)

- [x] **7.1 precheck.sh 加规则 1: 禁止状态字段硬编码**
  - grep `\.eq(.*Status,\s*[0-9]` → fail

- [x] **7.2 precheck.sh 加规则 2: 禁止 Controller 含 SecurityUtil.hasRole**
  - grep Controller.java + SecurityUtil.hasRole → fail

- [x] **7.3 precheck.sh 加规则 3: 禁止 Controller 含文件魔数校验**
  - grep Controller.java + magic byte pattern → fail

- [x] **7.4 创建 scripts/controller-lint.sh**
  - 完整 Controller 层扫描 (warn level)

---

## 阶段 8: 文档同步 (模式 1 子项)

- [x] **8.1 docs/数据字典.md v0.5→v0.6 同步**
  - 修复 14 项漂移

- [x] **8.2 创建 docs/API契约-课程管理.md**
  - 覆盖 85 端点

- [x] **8.3 docs/API契约-Phase1.md 加引用章节**
  - 指向课程管理文档

- [x] **8.4 docs/权限矩阵.md v2.0→v4.0**
  - 修复 11 项 + 补充 30 端点

- [x] **8.5 docs/状态机设计.md v1.0→v1.1**
  - 补全 3 项守卫 (章节内容/驳回长度/历史前提)
  - 记录通用端点拒绝 PUBLISHED 约定

- [x] **8.6 docs/开发规范.md v1.4→v1.5**
  - 新增 5 条禁止项 (魔数/Controller业务逻辑/Owner下沉/Spec门禁/Contract-first)

---

## 阶段 9: 测试设计与执行 (225+ TC)

- [x] **9.1 写 memories/scratchpad/course-test-units.md (TC 设计)**
  - 225 TC 详细定义 (页面+按钮+分支+预期+API 验证)

- [x] **9.2 执行 TC-001 ~ TC-030 (课程 CRUD)**

- [x] **9.3 执行 TC-031 ~ TC-055 (状态机)**

- [x] **9.4 执行 TC-056 ~ TC-095 (章节管理 4 类型)**

- [x] **9.5 执行 TC-096 ~ TC-125 (视频管理)**

- [x] **9.6 执行 TC-126 ~ TC-145 (定价)**

- [x] **9.7 执行 TC-146 ~ TC-160 (分类/标签)**

- [x] **9.8 执行 TC-161 ~ TC-175 (套件)**

- [x] **9.9 执行 TC-176 ~ TC-185 (课时)**

- [x] **9.10 执行 TC-186 ~ TC-200 (课件)**

- [x] **9.11 执行 TC-201 ~ TC-210 (评价)**

- [x] **9.12 执行 TC-211 ~ TC-225 (跨域)**

- [x] **9.13 全部 PASS 后 commit, 进入下一域 (用户管理)**

---

## 进度追踪

```
阶段 1: 1.1⬜ 1.2⬜ 1.3⬜ 1.4⬜ 1.5⬜
阶段 2: 2.1⬜ 2.2⬜ 2.3⬜ 2.4⬜ 2.5⬜ 2.6⬜ 2.7⬜ 2.8⬜ 2.9⬜
阶段 3: 3.1⬜ 3.2⬜ 3.3⬜ 3.4⬜ 3.5⬜ 3.6⬜ 3.7⬜ 3.8⬜ 3.9⬜ 3.10⬜ 3.11⬜ 3.12⬜ 3.13⬜ 3.14⬜ 3.15⬜
阶段 4: 4.1⬜ 4.2⬜ 4.3⬜ 4.4⬜ 4.5⬜ 4.6⬜ 4.7⬜ 4.8⬜ 4.9⬜ 4.10⬜ 4.11⬜ 4.12⬜ 4.13⬜ 4.14⬜ 4.15⬜
阶段 5: 5.1⬜ 5.2⬜ 5.3⬜ 5.4⬜ 5.5⬜
阶段 6: 6.1⬜ 6.2⬜ 6.3⬜ 6.4⬜
阶段 7: 7.1⬜ 7.2⬜ 7.3⬜ 7.4⬜
阶段 8: 8.1⬜ 8.2⬜ 8.3⬜ 8.4⬜ 8.5⬜ 8.6⬜
阶段 9: 9.1⬜ 9.2⬜ 9.3⬜ 9.4⬜ 9.5⬜ 9.6⬜ 9.7⬜ 9.8⬜ 9.9⬜ 9.10⬜ 9.11⬜ 9.12⬜ 9.13⬜
```

**总任务数**: 56
**已完成**: 0
**下一步**: 老板批准后 /opsx-apply 开始执行