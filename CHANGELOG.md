# Changelog

All notable changes to the 微课管理平台 (Micro-Course Management Platform) are documented here.

## [v1.16.0] — 2026-06-22

> 全量交叉验证收官 · CI/CD 零警告流水线 · 228/228 tests pass · GitHub Actions 全绿

### P0 必修修复 (7 项)
- **`TagController` 绕过 Service** — 直接调用 Mapper, 改走 `TagService` 业务层
- **JWT `generateRefreshToken` 缺 jti** — 补充 UUID 唯一标识
- **Course cover 端点缺失** — 补充 `PATCH /api/courses/{id}/cover` Controller 端点
- **`operation_logs` 双列 (action 列重复)** — V69 合并 `action` + `action_type` 单一列
- **`videos.file_size` 缺 NOT NULL** — V69 补充 NOT NULL 约束
- **`course.js` 重复方法定义** — 删除冗余 `getCourses` 重复声明
- **Redis 降级缺失** — 登录锁定 + JWT 黑名单 Redis 不可用时 try-catch 降级, 不影响主流程

### P1 关键修复 (28 项)
- **CSRF 路由 SecurityConfig 还原** — `.anyRequest().authenticated()` 未知路由 401 而非 404
- **生产 Swagger 禁用** — `application-prod.yml` `springdoc.api-docs.enabled=false`
- **视频签名密钥去除 fallback** — 强制从环境变量读取, 无空默认
- **权限矩阵同步 docs** — 角色权限矩阵与 SecurityConfig 严格对齐
- **密码校验对齐** — 8-32 位, 必须含字母+数字, 禁止纯字母/纯数字
- **3 个 N+1 查询** — 课程列表/详情/学习进度批量加载
- **6 个错误消息技术泄露** — `ExceptionUtils` + 业务异常白名单
- **6 个无上下文异常** — `BusinessException` 业务上下文 + 错误码
- **3 个依赖 CVE** — Tomcat / PostgreSQL Driver / POI 升级到无漏洞版本
- **9 个覆盖索引 (V70)** — `user_role`, `course_status_created`, `enrollment_user_status` 等
- **8 个 UX 修复** — Loading 状态、错误提示、表单校验、404 页面
- **数据库类型修复 (V71)** — `target_type` 长度, 重复索引清理, SMALLINT→INTEGER
- **数据字典补齐 7 表** — `users/departments/majors/classes` 之外的 7 张表全部补齐字段说明

### P2 体验提升 (10 项)
- **nginx HTTPS 注释块** — HTTPS server 块完整配置 + HTTP→HTTPS 重定向注释
- **Redis `mc:` 前缀** — 所有生产 key 加 `mc:` 命名空间, 避免与其他系统冲突
- **sourcemap 关闭** — 生产构建不再生成 sourcemap, 减少体积
- **分层违规修复** — 4 处 Service 误调 Controller/Util 改为调用 Service
- **`@PreAuthorize` CAS 控制** — `/api/cas/**` 需要 CAS 角色
- **空默认值** — 所有 `application.yml` 移除空默认密码/key, 强制环境变量
- **Login 校验** — 前端表单 min/max/pattern 校验
- **SecurityConfig 同步** — 与权限矩阵文档保持一致
- **Async 线程池** — 异步任务使用独立线程池, 不阻塞主流程
- **前端 chunk 拆分** — vendor-element 拆分为 base+icons, 1.15MB → 901kB+252kB

### P3 维护性 (6 项)
- **`@PreAuthorize` 全量** — 所有 Controller 都有权限注解
- **错误码唯一性** — ErrorCode 枚举严格唯一
- **`@JsonIgnore` 敏感字段** — 8 处敏感字段不在响应中暴露
- **空指针防御** — Optional + null check
- **日志脱敏** — 6 处敏感日志脱敏 (密码/token/手机号)
- **代码注释清理** — 移除 23 处冗余/TODO 注释

### 关键根因排查
- **Redis 降级 (P0)** — Redis 不可用时不影响主流程
- **未知路由 404 修复** — 还原 `.anyRequest().authenticated()` 换取认证完整性
- **`CourseServiceImpl` 1042 行提取 3 个方法** — `buildCategoryMap` / `buildTeacherMap` / `buildRatingCountMap`
- **前端 chunk 拆分** — vendor-element 1.15MB → base 901kB + icons 252kB

### 依赖升级
- **JaCoCo** 0.8.11 → **0.8.14** (修复 CVE)
- **Flyway** 9.22.3 → **10.20.1** (PG17 兼容性, 需新增 `flyway-database-postgresql`)

### CI/CD 流水线 (新增)
- **`.github/workflows/ci.yml`** — Backend (228 tests + JaCoCo) / Frontend (Lint + Build) / Docker (build) 三 job 并行
- **`precheck.sh` 13/13 PASS** — 13 项项目门禁全部通过
- **数据库测试隔离** — 每次 CI 启动全新 PG17 容器, 跑完销毁
- **228/228 tests pass** — backend 全量测试通过, 0 failures / 0 errors
- **TypeScript / ESLint 零警告** — 前端代码质量 100% 通过

### Quality Gates
```
Precheck:         13/13 PASS  ✅
Backend tests:    228/228 PASS ✅  (0 failures / 0 errors)
JaCoCo coverage:  ✅ 完整覆盖率
Frontend lint:    0 errors    ✅
Frontend build:   SUCCESS     ✅
CI overall:       SUCCESS     ✅
```

---

## [v1.15.0] — 2026-06-20

> 全量审计第二波 · 运行时错误归零 · 讲述稿设置功能 · Super-Fix 框架落地

### 运行时错误修复 (6 项)
- **`@Range(max=200)` 拒绝 `size=1000`** — 22 Controller, 27 处限制放宽到 `max=10000`
- **Redis 缓存 null 值崩溃** — `AdminSettingServiceImpl` @Cacheable 添加 `unless="#result == null"`
- **CAS 设置 500** — `@Cacheable(sync=true)` 与 `unless` 互斥, 删除 `sync=true`
- **学生账号被禁用导致 401** — 通过 API 启用 student/teacher/academic 三个账号
- **幻灯片上传 400 (not a multipart request)** — `slide.js` 删除手动设置的 `Content-Type` 头
- **幻灯片上传 500 (存储路径不存在)** — 默认存储路径从 `/data/slides` 改为 `uploads/slides`

### 讲述稿生成设置 (新功能)
- **V55 migration** — `narration_settings` 表 (演讲人/受众/风格/总时长)
- **后端全栈** — Entity + Repository + Service + Controller + DTO
- **前端设置界面** — `NarrationSettingsDialog.vue` 演讲人/受众/风格/时长自定义
- **连贯性改造** — `generateAll()` 一次性发送全部页面内容到 DeepSeek, AI 生成连贯讲述稿后按 `【第N页】` 标记拆分回写
- **单页上下文** — 单页生成时自动包含上一页内容, 保证过渡衔接
- **总时长分配** — 由 AI 根据各页内容重要性自动分配时间, 非均分

### 多智能体交叉审查 (4 轮)
- **400 错误 4 层根因链** — YAML 缩进错误 → Content-Type boundary → 存储路径不存在 → 前端提示不透明
- **500 错误根因** — DEEPSEEK_API_KEY 未配置 + 批量生成无效循环 N 次
- **前端 SlideManage handleUpload 缺失** — 函数完全未实现
- **上传区缺少 @click** — 原生 upload zone 无法点击选文件

### Super-Fix 框架落地
- **`finding.schema.json`** — 88 个发现全部通过 schema 校验
- **`convergence-check.sh`** — 5 维度收敛检查脚本
- **`smoke-test.sh`** — 14 项 API 烟雾测试 (4 角色 × 5 维度)
- **`sed-mutation-test.sh`** — 8 种变异算子测试覆盖率
- **`gate-check.sh`** — 9 道门禁分阶段校验 (全 PASS)
- **88/88 发现统一格式** — 全部补充 hash + causal_chain + root_cause
- **预检白名单更新** — 新增 NarrationSetting 相关类

### Quality Gates
```
Convergence check: 5/5 PASS    ✅
Smoke test:        14/14 PASS  ✅
Gate checks:       9/9 PASS    ✅
Precheck:          13/13 PASS  ✅
```

---

## [v1.14.0] — 2026-06-20

> Super-Fix 穷举审计修复 · 88 项发现 100% 修复 · 44 文件 / +1022 -1207 · 4 角色全覆盖

### 审计总览
- **审计范围**：互动课程模块及所有关联功能
- **角色覆盖**：学生 / 教师 / 管理员 / 教务处
- **审查视角**：Security(9) · Concurrency(10) · Dataflow(5) · Error(10) · Resource(8) · A11Y(30) · Performance(16) · UX
- **审计发现**：88 项 (P0:17, P1:37, P2:30, P3:4) — **100% 修复**

### Security (9)
- **IDOR 防护**（P1）：SlideController 所有 GET 端点添加 `verifyAccess()` 四角色分权校验（ADMIN/ACADEMIC/TEACHER/STUDENT 选课检查）
- **@Async SecurityContext 传播**（P1）：SlideRenderService 抽取独立 @Service，SecurityContext 不再跨线程丢失
- **硬编码路径修复**（P1）：TtsController `storagePath` 从 `/data/slides` 硬编码改为 `@Value` 配置注入
- **SpEL 表达式修复**（P1）：`authentication.principal.id` → `authentication.principal`（Long 直接比较）
- **Nginx 安全头**（P1）：CSP/XSS/ContentType/HSTS/ReferrerPolicy 全部配置
- **登录限流**（P1）：Nginx `limit_req_zone` 登录端点 5r/m + burst 10
- **HEALTHCHECK 凭据移除**（P1）：改用无凭据 `/api/admin/stats/health` 端点

### Concurrency (10)
- **TOCTOU 选课**（P0）：EnrollmentServiceImpl `DuplicateKeyException` 幂等兜底
- **双 ffmpeg 进程**（P0）：VideoTranscodeServiceImpl CAS 状态检查 `UPDATE ... WHERE status=0`
- **学习进度丢失更新**（P0）：`total_watch_time` 改为 `COALESCE(total_watch_time,0) + delta` 增量 SQL
- **ExerciseRecord attemptNo 竞态**（P1）：`COALESCE(MAX(attempt_no), 0) + 1` 原子计算 + `DuplicateKeyException` 兜底
- **WrongQuestion 计数丢失**（P1）：`wrong_count = wrong_count + 1` 原子 SQL
- **签到 TOCTOU**（P1）：`DuplicateKeyException` 幂等 + 连续天数实时计算
- **Redis INCR+EXPIRE**（P2）：Lua 脚本原子执行
- **通知轮询堆叠**（P2）：`setInterval` → 递归 `setTimeout` 防请求堆积

### Dataflow (5)
- **LIMIT SQL 拼接**（P1）：`EnrollmentServiceImpl.getCourseRanking` → MyBatis-Plus Page 参数化
- **Self-invocation @Async**（P0）：SlideServiceImpl → SlideRenderService 独立 @Service，@Async/@Transactional 正确代理
- **Self-invocation @Transactional**（P0）：Narration/TTS → 抽取 NarationTaskService/TtsTaskService 独立 @Service
- **LIKE 通配符注入**（P2）：5 Service (User/Course/Enrollment/CourseFavorite/DiscussionPost) 转义 `%` 和 `_`
- **课程状态机**（P0）：submitForReview 支持 `REJECTED→PENDING`；delete 支持 `DRAFT/REJECTED→CLOSED` 跳过校验

### Error Handling (10)
- **XMLSlideShow 资源泄漏**（P0）：try-with-resources 自动关闭
- **RestTemplate 无超时**（P0）：connectTimeout=5000, readTimeout=60000 配置
- **ProcessBuilder deadlock**（P1）：`redirectErrorStream(true)` 防 stderr 满阻塞
- **JSON 序列化失败**（P1）：`ExerciseRecordServiceImpl` 空数组 → throw BusinessException
- **健康检查暴露异常**（P1）：`AdminStatsServiceImpl.getHealth()` 返回 "ERROR" 固定字符串
- **GlobalExceptionHandler**（P2）：`ConstraintViolationException` + `HandlerMethodValidationException` 专用处理器

### Resource / Performance (24)
- **@Async 无限线程**（P0）：AsyncConfig ThreadPoolTaskExecutor (core=CPU×2, max=32, queue=200)
- **ForkJoinPool 滥用**（P0）：`videoUploadExecutor` 专用线程池 (core=2, max=8)
- **isFfmpegAvailable 流泄漏**（P1）：try-with-resources 消费 stdout/stderr
- **Grade N+1**（P0）：`batchConvertToVO` 批量 selectBatchIds 消除 4×N 查询
- **TeachingClass N+1**（P1）：courseMap/teacherMap 批量预加载
- **Enrollment N+1**（P1）：courseMap/userMap/teacherMap 批量预加载
- **CourseFavorite N+1**（P1）：selectBatchIds + 分页控制
- **AdminStats N+1**（P1）：GROUP BY DATE 替代逐日循环
- **DiscussionPost LIMIT**（P1）：评论查询 +200 LIMIT
- **视频转码进度**（P1）：FFmpeg Duration 解析 + 时间比例估算

### A11Y — 无障碍 (30)
- **P0 键盘可访问**（8）：TrainingCenter/CourseDetail/MyCourses/Profile/LearningView/StudentList 等 div→button role/tabindex/keydown
- **P1 ECharts 描述**（5）：admin/academic/teacher/student 仪表板图表添加 aria-label
- **P1 弹窗焦点**（2）：el-dialog focus-trap + escape 声明
- **P1 图标按钮**（3）：aria-label 标签补全

### Frontend UX (10)
- **saveOutline 空函数**（P0）：实现教师大纲标题持久化
- **replacePPTX 空函数**（P0）：实现课件文件替换逻辑
- **Space 键冲突**（P0）：SlidePlayer Space→播放, ←→翻页
- **course-type-badge 重复**（P0）：CourseSquare 精选推荐去重
- **课程类型筛选**（P1）：课程广场添加互动/视频筛选下拉框
- **ACADEMIC 侧栏权限**（P1）：Layout 审核菜单 ACADEMIC 可见
- **审批按钮角色校验**（P1）：CourseDetail v-if="role==='ADMIN'"
- **PPT 上传前端校验**（P2）：50MB 限制 + .pptx 格式验证
- **渲染轮询超时**（P2）：最大 60 次轮询计数器 + 超时提示
- **数据加载瀑布流**（P2）：Promise.all 并行加载优化

### Migration & Infrastructure
- **V54__super_fix_fk_repairs.sql**：16 FK 约束修复 (badges/certificates/grades/question_tag_relations/user_follows/score_histories/course_notes/video_bookmarks/attachments/course_slides/slide_pages) + 2 NOT NULL 修复 (banners created_at/updated_at)
- **.dockerignore**：API + Admin 各创建 .dockerignore
- **client_max_body_size**：2G 适应视频上传
- **Gzip 压缩**：Nginx 启用 gzip 压缩 JSON/JS/CSS
- **JWT 密钥**：`application.yml` `${JWT_SECRET}` 无 fallback，启动时强制配置

### Quality Gates
```
mvn compile:    0 ERROR    ✅
npm run build:  SUCCESS    ✅
precheck.sh:    13/13 PASS ✅
API smoke:      200 (8/8)  ✅
E2E tests:      4 spec     ✅
```

---

## [v1.13.0] — 2026-06-20

### Security (6)
- **HLS 视频流认证修复**：SecurityConfig `permitAll()` → `authenticated()`；hls.js 注入 `xhrSetup` 带 JWT Token 请求每个 `.ts` 分片
- **IDOR 防护**：CertificateController `getById`/`download` 添加用户归属检查
- **响应安全头**：Referrer-Policy (`strict-origin-when-cross-origin`) + Permissions-Policy (`camera=(), microphone=(), geolocation=(), payment=()`) + 已有 CSP/XSS/Frame/ContentType
- **启动告警**：`VIDEO_SIGN_SECRET` 未设置或等于 `JWT_SECRET` 时 `log.warn`

### Concurrency (8)
- **Course 实体**：`@Version` 乐观锁（`OptimisticLockerInnerInterceptor` 已注册）
- **证书表唯一索引**：`V47__certificates_unique_index.sql` — `UNIQUE(user_id, course_id)` 防并发双发
- **BadgeServiceImpl**：`awardBadge()` 添加 `DuplicateKeyException` 兜底（并发颁发防重复）
- **avgRating**：`updateCourseAvgRating()` 在 `@Transactional` 内通过 SQL `COALESCE(AVG(rating), 0)` 原子计算

### Dataflow / Performance (9)
- **Certificate N+1 消除**：`getMyCertificates()` 改为批量预加载 courseMap + userMap
- **CourseReview N+1 消除**：`listByCourse`/`getMyReviews`/`listAll` 通过 `buildUserMap()` 批量加载用户
- **TeachingClass 分页**：`setPage()` MyBatis-Plus 1-based → 0-based（`getCurrent() - 1`）
- **响应契约统一**：CertificateController/BadgeController `ResponseEntity<T>` → `R<T>`（修复前端 Axios 拦截器 `res.code !== 200` 误判）
- **AchievementVO**：`getName()` → `@JsonProperty("name") getBadgeName()` 显式序列化名
- **EnrollmentUpdateRequest**：`@Min(0) @Max(100) progress` + `@DecimalMin(0) @DecimalMax(100) finalScore`

### Error Handling (12)
- **PDF 资源泄漏**：`CertificateServiceImpl` PDF Document `.close()` 移至 `finally` 块
- **静默异常**：EnrollmentServiceImpl 证书/徽章自动颁发 `catch (Exception ignored)` → `log.warn()`
- **GlobalExceptionHandler**：新增 `DataIntegrityViolationException` 专用处理器（409）
- **类型转换安全**：AdminSettingsController `(Boolean) body.get("enabled")` → `instanceof Boolean` 校验
- **BadgeServiceImpl**：徽章定义不存在 `return null` → `throw new BusinessException(BADGE_NOT_FOUND)`
- **6 处 catch 日志补全**：UserServiceImpl 头像上传 / VideoController 存储目录/文件保存 / AdminBannerController 创建/更新图片上传

### Frontend Fixed (24)
- **19 处路由断裂修复**：`/courses/discussions` → `/discussions`；12 处 `/admin` → `/admin/dashboard`；8 处 `/student` → `/student/courses`；Academic Dashboard 4 个快捷入口 → 正确路由；`/admin` + `/student` 兜底重定向
- **axios 拦截器**：`responseType: 'blob'` 跳过 `res.code` 检测（修复证书 PDF 下载）
- **LearningCenter**：证书统计硬编码 `0` → `getMyCertificates()` API 真实数据
- **TrainingCenter**：`chapter-item` 添加 `role/tabindex/keydown.enter/space` 键盘支持
- **Profile**：证书查看 `const html = res.data` HTML iframe → `URL.createObjectURL(blob)` PDF 直出
- **Profile**：SEC-001 DOM XSS 已修复（此前审计）

### Deployment (7 new files)
- `Dockerfile`（后端 multi-stage Maven + JRE17-alpine + HEALTHCHECK）
- `Dockerfile`（前端 multi-stage Node + nginx-alpine + gzip + API 代理）
- `nginx.conf`（SPA fallback + `/api/` 反向代理 + 静态资源 1y 缓存）
- `docker-compose.yml`（PostgreSQL 17 + Redis 7 + API + Admin，健康检查依赖链）
- `.github/workflows/ci.yml`（precheck → mvn compile → npm build → docker push）
- `.env.example`（DB/JWT/VideoSign/Redis/CORS 模板）
- `.dockerignore` ×2（排除 `target/node_modules/.git`）

### UX P0 修复 (额外轮次)
- **练习入口弹窗**：视频播放完 `ElMessageBox.confirm` 弹窗提示"开始练习"
- **骨架屏**：`VideoPlayer.vue` 旋转加载器 → 视频占位+进度条+侧栏章节完整骨架屏
- **CourseSquare**：注释修正 "PC端" → "H5响应式"

交叉验证通过(R1-R4)

---

## [v1.12.2] — 2026-06-20

> Phase 13 · Badge 后端完整实现

### Badge 后端完整实现

- `V37__badge_definitions.sql`：badge_definitions 表 + 3 个内置徽章种子数据（FIRST_COURSE / ALL_COURSES / SEVEN_DAY_STREAK）
- `V38__achievements.sql`：achievements 表（替代 V18 单表 badges）
- `BadgeDefinition.java` + `BadgeDefinitionRepository.java`：徽章定义实体
- `Achievement.java` + `AchievementRepository.java`：用户成就实体
- `BadgeDefinitionVO.java` + `AchievementVO.java`：API 响应 DTO
- `BadgeServiceImpl`：颁发/列表/定义查询；防重复颁发（UNIQUE 兜底）
- `BadgeController`：`GET /api/badges/definitions` + `GET /api/badges/my` + `GET /api/badges/achievements`
- **自动颁发**：EnrollmentServiceImpl 课程完成时 → FIRST_COURSE / ALL_COURSES 徽章
- **自动颁发**：CheckInServiceImpl 打卡成功时 → SEVEN_DAY_STREAK 徽章（连续 ≥ 7 天）
- **防循环依赖**：checkAndAwardCourseCompletion(counts) 接受外部计算的 enrollments count

---

## [v1.12.1] — 2026-06-20

> Phase 11–12 · Certificate 后端完整实现

### Phase 11 — 遗留 backlog 修复

- `CourseServiceImpl`：ratingCount 批量预加载（`CourseReviewRepository.countByCourseIds`）消除 N+1
- `AdminSettingsController`：扩展 `PUT /api/admin/settings/cas` + 新增 `GET /api/admin/settings/cas`
- `CasSettingsDTO`：完整字段 DTO（enabled/serverUrl/serviceUrl/version/adminUsername/superAdmins/validateSsl）
- `AdminSettings.vue`：`localStorage` mock → 真实 API 调用

### Phase 12 — 证书后端完整实现

- `Certificate.java`：`@TableName("certificates")` Entity（含 `certCode` 唯一索引）
- `CertificateRepository.java`：MyBatis-Plus BaseMapper
- `CertificateServiceImpl`：颁发/查询/下载 PDF（含 OpenPDF 生成精美证书）
- `CertificateController`：`GET /api/certificates/my` + `GET /api/certificates/{id}/download` + `POST /api/certificates/issue`
- `CertificateVO`：课程名 + 学生姓名 + 证书编号 + 颁发时间
- `ErrorCode`：新增 `CERTIFICATE_NOT_ELIGIBLE(13003)`
- **自动颁发**：EnrollmentServiceImpl 完成课程时自动颁发证书（try/catch 静默处理）
- `pom.xml`：新增 OpenPDF 1.3.35（证书 PDF 生成）

---

## [v1.12.0] — 2026-06-20

> Phase 5–10 Super-Fix 完整交付 · 282 issues resolved

### Phase 5 — 学生前端核心 Super-Fix
**9 个页面** · 45 P0 + 93 P1-P3

- `LearningView.vue` + `LearningCenter.vue`：视频播放器进度记忆/倍速/全屏/讨论区/笔记持久化
- `CourseSquare.vue`：`getCourses` 参数修正、排序参数对齐后端
- `CourseDetail.vue`：`goLearn()` 路由死循环修复、UserController `/public-profile` 端点
- `MyCourses.vue`：`CourseFavoriteVO` 字段补全、进行中/已完成 tabs
- `ExerciseTake.vue`：`QuestionController @PreAuthorize` 修正为 `isAuthenticated()`
- `DiscussionView.vue`：`V46` 迁移 — `is_anonymous` 列 + `discussion_comment_likes` 表；匿名回复全链路支持
- `NotificationList.vue`：`store/notification.js` 重写 — visibility 暂停/指数退避/401 熔断
- `Profile.vue`：头像 Base64 → multipart 文件上传（`POST /api/auth/avatar`）；`OLD_PASSWORD_INCORRECT` 错误码
- `router/index.js` + `StudentLayout.vue`：权限守卫补充/双源角色统一/空路由移除

### Phase 6 — 教师端补齐 Super-Fix
**4 个页面** · 18 P0 + 40 P1-P3

- `TeacherDashboard.vue`：`pendingHomework` 死代码 → 真实查询；`setInterval` → `setTimeout` 递归；`statsError` 错误态
- `StudentList.vue`：发消息 `type/title` 字段补全；`EnrollmentVO` 新增 `username/realName/className/majorName`；客户端 filter 破坏分页 → 删除；IDOR 权限收紧
- `StudentGrades.vue`：`comment` 全链路补全（DTO→Service→VO）；`GradeTeacherSubmitRequest` 新端点 `POST /api/grades/teacher-grade`；数据隔离 + 越权修复
- `TeacherTeachingClasses.vue`：`prop="username"` → `prop="studentNo"`；`AddStudentRequest`/`UpdateStudentStatusRequest` DTO 替代 Map；越权漏洞修复

### Phase 7 — 管理后台 Super-Fix
**3 个页面** · 12 P0 + 26 P1-P3

- `Dashboard.vue`：`loadStats` 按 type 分类取值替代 undefined 字段；`DailyActivityVO` + `getDailyActivity` 新接口；`certificatesIssued` 补字段；`ElMessage` 导入修复
- `OperationLogs.vue`：日期格式 `LocalDate` + `atStartOfDay()`；status 整数 0/1 判断；`username/module/targetId` 后端参数补全；`duration/module/method/path` VO 字段补全；防抖 + 请求序列号
- `AdminSettings.vue`：4 个接口 `settingKey`/`key` 字段对齐 `@JsonProperty`；`params` → `data`；PostgreSQL `ON CONFLICT` 原子 upsert；`btoa` 敏感字段编码

### Phase 8 — 视频基础设施 Super-Fix
**视频后端 + 播放器** · 11 P0 + 26 P1-P3

后端：
- `VideoStreamController`：HLS 流式代理端点替代 filesystem 路径 302 重定向
- `upload()`：课程 Owner 校验（`assertCourseOwnership`）
- 封面 URL → `/api/files/covers/` 可访问路径 + `WebMvcConfig`
- `delete()`：增加 `cleanupDiskFiles()` 清理 HLS/原始文件/封面
- `VideoBookmarkController` + `Service` + `ServiceImpl` + `Repository` + DTO×2：完整 CRUD
- `VideoStatus` 枚举替代魔法数字
- `@Version` 乐观锁 + `OptimisticLockerInnerInterceptor`
- `video.sign.secret` 独立密钥配置
- FFmpeg 超时可配置（`video.transcode.timeout-minutes`）
- Nginx `/hls/` `internal` + `valid_referers` 防盗链

播放器 `VideoPlayer.vue`：
- 位置恢复移至 `onCanPlay`（duration 确定后）
- 全屏改为对 `video-container` 调用
- `seekRelative(delta)` 实现
- `switchChapter` 加 `await` + 重置 progressId
- 笔记 `localStorage` 持久化
- PiP 监听先 `removeEventListener` 再 `add`
- 进度上报定时器按 `isPlaying` 启停

### Phase 9 — 缺失 API + CAS 真实集成
**批量导入 + CAS** · 4 P0 + 7 P1-P3

- 批量导入：`UserBatchImportDTO` 改为 `departmentName/majorName/className` + `password`；后端 name→ID 映射查找；预加载所有 dept/major/class name→ID Map；XLSX 模板下载（`xlsx` 库）；`BatchImportResultVO.ImportErrorItem` 结构化错误；`SecureRandom` 随机密码；批量 SQL 插入
- CAS 真实集成：`AuthServiceImpl.casLogin()` 重写 — RestTemplate 调用 `/serviceValidate` XML；DOM 解析（XXE 防护）；自动注册 CAS 用户；`ErrorCode`: `CAS_NOT_CONFIGURED`(1008)/`CAS_VALIDATION_FAILED`(1009)

### Phase 10 — 交叉验证 + 交付
- `precheck.sh`：新增 `VideoBookmarkController`/`VideoStreamController`/`VideoBookmarkService` 白名单
- `git tag v1.12.0`
- `git push origin main`

---

## [v1.11.0] — 2026-06-19 (prior release)
See git log `v1.11.0..v1.12.0` for details.
