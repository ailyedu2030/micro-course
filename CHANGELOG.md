# Changelog

All notable changes to the 微课管理平台 (Micro-Course Management Platform) are documented here.

## [v1.13.0] — 2026-06-20

> Super-Fix 全量审计修复 · 59 个 P0-P2 问题归零 · 43 文件 / +302 -142

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
