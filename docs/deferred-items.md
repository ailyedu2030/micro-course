# Deferred Items 登记表

> 本文件登记发布时已评估但放行的 P1-I / P2 缺陷。
> 每次发布前必须清理上一版本的所有条目。

## v1.7.0 发布放行条目（更新于 R5 后）

### 本轮已修复（P0 / P1-C）

| 编号 | 来源 | 问题 | 修复 |
|------|------|------|------|
| P0-A | R5c | token 存储双源不一致（store 直接读 + utils/auth 封装 + Login 直接写） | 全部统一收敛到 utils/auth.js（localStorage） |
| P0-B | R5c | workspace.store.js 用裸 fetch 绕过 axios（401 不触发 refresh） | 改用 request.js 实例 |
| P1-C-1 | R5b/R5c | token 用 sessionStorage 违反 SKILL.md §106 | 全部迁到 localStorage |
| P1-C-2 | R5b | userRole 写 sessionStorage 死代码 | 删除写入/清理逻辑 |
| P1-C-3 | R5b | 注册流程绕过 setToken() 抽象 | 改用 setToken() + setRefreshToken() |
| P1-C-4 | R5a | AdminSettings.vue:224 "保存修改"按钮 aria-label="编辑" | 改为 "保存修改" |
| P1-C-5 | R5a | StudentList.vue 搜索/重置按钮 aria-label 互换 | 分别改为 "搜索" / "重置" |
| P1-C-6 | R5a | TeacherTeachingClasses.vue 添加学生/刷新按钮 aria-label 互换 | 分别改为 "添加学生" / "刷新" |
| P1-C-7 | R5c | CourseList/EnrollmentList clearable 不触发搜索 | 加 @clear="handleSearch" |

---

### P1-I（内部仅见，正常使用不可感知）

| # | 来源 | 描述 | 文件 | 理由 | 目标版本 |
|---|------|------|------|------|---------|
| 1 | R1 | Lombok 依赖在 pom.xml 中声明但主源码未使用 | pom.xml | 仅声明残留 | v1.8.0 |
| 2 | R1 | 4 个 .bak 备份文件未清理 | 各 .bak | 纯文件残留 | v1.8.0 |
| 3 | R2 | courses 表新增字段未同步到数据字典 | 数据字典.md §2.2 | 字段已在 DB 中 | v1.8.0 |
| 4 | R2 | 12 个字段未在字典注册 | 数据字典.md | DB 已存在 | v1.8.0 |
| 5 | R2 | 6 张新表未在字典注册 | 数据字典.md | 不影响运行 | v1.8.0 |
| 6 | R2 | exercise_records 约束标注错误 | 数据字典.md §4.4 | DB 实际正确 | v1.8.0 |
| 7 | R4 | ClassVO 缺少 contract 字段 | ClassVO.java | 前端未消费 | v1.8.0 |
| 8 | R4 | Controller 权限注解额外开放 ACADEMIC | DepartmentController.java:43 | 需更新契约 | v1.8.0 |
| 9 | R4 | refresh 端点路径硬编码 | request.js:86 | 不影响功能 | v1.8.0 |
| 10 | R4 | UserVO.bio 字段无对应 DB 列 | UserVO.java:15 | 衍生计算字段 | v1.8.0 |
| 11 | R1 | Department update 缺唯一性校验 | DepartmentServiceImpl.java:161 | 低概率 | v1.8.0 |
| 12 | R3 | 刷新令牌端点无速率限制 | SecurityConfig.java | 加固建议 | v1.8.0 |
| 13 | R3 | 用户状态缓存 TTL 30s | UserStatusCheckFilter.java | 可接受延迟 | v1.8.0 |
| 14 | R3 | Swagger 默认 permitAll | application.yml | prod 已关闭 | v1.8.0 |
| 15 | R3 | refresh token 与 access 共用密钥 | JwtUtil.java:53 | 加固建议 | v1.8.0 |
| 16 | R5a | CourseSquare 硬编码颜色 | CourseSquare.vue:901-937 | 不阻塞功能 | v1.9.0 |
| 17 | R5a | 4 处重复声明 --color-primary | LearningView 等 | 维护成本高 | v1.9.0 |
| 18 | R5a | VideoPlayer/SlidePlayer 主题未对齐 token | VideoPlayer.vue 等 | 视觉漂移 | v1.9.0 |
| 19 | R5a | UserList stripe/border 属性残留 | UserList.vue:78 | 调试残留 | v1.8.0 |
| 20 | R5c | API 契约 userId 与后端 id 文档漂移 | API契约.md vs UserVO | 功能 OK | v1.8.0 |
| 21 | R5c | getMyEnrollments 传 uid 参数无效 | CourseDetail.vue:382 | 不影响功能 | v1.8.0 |
| 22 | R5c | VideoPlayer 视频进度 localStorage 无清理 | VideoPlayer.vue | 长期累积 | v1.9.0 |
| 23 | R5c | plugins store 无持久化 | store/plugins.js | 非核心流程 | v1.9.0 |
| 24 | R5c | CourseSquare onMounted 串行 API | CourseSquare.vue | 加载慢但能用 | v1.9.0 |
| 25 | R5b | TEACHER 角色看到不可用按钮 | users/UserList.vue:57 | UI/UX 不一致 | v1.8.0 |

### P2（代码整洁 / 安全加固 / 性能优化）

| # | 来源 | 描述 | 文件 | 目标版本 |
|---|------|------|------|---------|
| 1 | R3 | Server port 未通过环境变量暴露 | application.yml | v1.8.0 |
| 2 | R3 | CSP unsafe-inline/eval | nginx.conf | v1.9.0 |
| 3 | R3 | PostgreSQL 驱动版本未显式声明 | pom.xml | v1.8.0 |
| 4 | R3 | nginx HTTPS 配置手动启用 | nginx.conf | v1.8.0 |
| 5 | — | npm build chunk 体积 > 400kB | Vite output | v1.9.0 |
| 6 | R4 | microSpecialty.js camelCase 命名 | src/api/microSpecialty.js | v1.8.0 |
| 7 | R1 | GlobalExceptionHandler 日志级别 | GlobalExceptionHandler.java | v1.8.0 |
| 8 | R1 | 多数 CRUD 缺 @AuditedLog | 各 Controller | v1.9.0 |
| 9 | R2 | course_reviews.rating 双重 CHECK | V11.sql / V77.sql | v1.8.0 |
| 10 | R5a | ECharts 硬编码 Tailwind 颜色 | Dashboard 等 4 文件 | v1.9.0 |
| 11 | R5a | CartDrawer/SlidePreview 缺 aria-label | CartDrawer.vue / SlidePreview.vue | v1.8.0 |
| 12 | R5a | CourseDetail (管理端) img 缺 alt | CourseDetail.vue:116 | v1.8.0 |
| 13 | R5a | AdminSettings el-dialog border-radius 重复 | AdminSettings.vue | v1.9.0 |
| 14 | R5a | TeachingClassList 3 input 共享 label | TeachingClassList.vue | v1.9.0 |
| 15 | R5b | UserList.vue 模板测试数据硬编码 | UserList.vue | v1.8.0 |
| 16 | R5b | enrollment API 模板字符串拼接 | api/enrollment.js | v1.8.0 |
| 17 | R5b | notification 轮询无最大重连 | store/notification.js | v1.9.0 |
| 18 | R5c | request.js 上传进度 _skipAuth | request.js | v1.9.0 |
| 19 | R5c | CourseSquare keyword debounce 不一致 | CourseSquare.vue | v1.9.0 |

---

## 登记规则

- P1-I 放行条件：总工程师逐条评估，确认"客户在 100 次正常操作内不可感知"
- P2 放行条件：影响范围明确，修复成本可接受推迟
- 禁止批量放行：每条必须独立登记
- 超期处理：超过 1 个版本未处理的条目自动升级为 P1-C

## 本轮统计

| 来源 | P0 已修 | P1-C 已修 | P1-I 登记 | P2 登记 |
|------|:-------:|:---------:|:---------:|:-------:|
| R1 | 1 | 0 | 2 | 2 |
| R2 | 1 | 0 | 4 | 1 |
| R3 | 0 | 0 | 4 | 3 |
| R4 | 2 | 0 | 4 | 1 |
| R5a | 0 | 3 | 4 | 5 |
| R5b | 0 | 3 | 1 | 3 |
| R5c | 2 | 2 | 6 | 2 |
| 合计 | 6 | 8 | 25 | 17 |

*最后更新：2026-06-25*
*评估人：总工程师*
*下次清理：v1.8.0 发布前*