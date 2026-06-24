# 功能清单全量补齐 · 开发规格说明书 v1.0

> 基线：功能清单 v2.0（158 功能）
> 当前完成：46/158 (29%) | 缺口：112/158 (71%)
> 本 spec 定义缺口全量补齐的精确任务、文件、API、验收标准

---

## Phase 5 · 学生前端核心（P0 缺口 41 功能）

### 5.1 课程广场（6 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 分类导航 | `src/views/student/CourseSquare.vue` | GET /api/course-categories | 树形分类筛选 |
| 全文搜索 | 同上 | GET /api/courses?keyword=&categoryId=&difficulty= | 模糊搜索+分类联动 |
| 课程卡片 | 同上（el-card 网格） | 同上 | 封面+标题+教师+评分+报名数 |
| 精选推荐 | 同上（isRecommended 排序） | GET /api/courses?recommended=true | 标注"推荐"角标 |
| 移动端瀑布流 | 同上 @media | 同上 | < 768px 单列 |

### 5.2 课程详情（5 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 课程信息 | `src/views/student/CourseDetail.vue` | GET /api/courses/{id} | 标题+简介+封面+教师 |
| 大纲列表 | 同上 | GET /api/chapters?courseId={id} | 章节+视频时长+练习数 |
| 教师信息 | 同上 | GET /api/users/{teacherId} | 教师头像+姓名+简介 |
| 报名按钮 | 同上 | POST /api/enrollments | 已选课→"继续学习"，未选→"报名" |
| 学习同伴提示 | 同上 | GET /api/enrollments/course/{id} | "当前 X 人在学习" |

### 5.3 我的课程（4 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 进行中列表 | `src/views/student/MyCourses.vue` | GET /api/enrollments/my?userId= | 进度条+继续学习 |
| 已完成列表 | 同上 | 同上(status=completed) | 已完成标记 |
| 最近学习排序 | 同上 | 同上 | 按 lastWatchAt DESC |
| 进度百分比 | 同上 | GET /api/learning-progress/progress?userId&courseId | 视频+练习进度 |

### 5.4 视频播放器（6 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| HLS 播放 | `src/views/student/VideoPlayer.vue` | GET /api/videos/{id} | hls.js 加载 m3u8 |
| 倍速播放 | 同上 | — | 0.75x-2x 选择 |
| 进度记忆 | 同上 | PUT /api/learning-progress/progress/{id} | 刷新回断点 |
| 全屏/小窗 | 同上 | — | video fullscreen API |
| 加载状态 | 同上 | — | 骨架屏+缓冲 |
| 进度上报 | 同上 | PUT /api/learning-progress/progress/{id} (每10s上报videoPosition) | 后端记录 |

### 5.5 随堂练习（7 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 练习入口 | `src/views/student/ExerciseTake.vue` | GET /api/exercises?chapterId= | 章节练习列表 |
| 答题界面 | 同上 | GET /api/questions/{id} | 单/多/判/填 四种渲染 |
| 进度指示器 | 同上 | — | "3/10" 题 |
| 自动批改 | 同上 | POST /api/exercise-records/submit | 即时得分 |
| 答案解析 | 同上 | 同上 (answers JSON) | 正确答案高亮 |
| 重做 | 同上 | 同上 (attemptNo++) | 限次数检查 |
| 错题入库 | 后端自动 | 同上 → wrong_questions upsert | 答错自动入错题集 |

### 5.6 学习中心（7 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 总学习时长 | `src/views/student/LearningCenter.vue` | GET /api/learning-progress/progress?userId= | 累计 |
| 完成课程数 | 同上 | GET /api/enrollments/my?completed=true | 计数 |
| 正确率趋势 | 同上 | GET /api/exercise-records/my | 折线图(ECharts) |
| 活跃日历 | 同上 | GET /api/check-ins/my?days=30 | GitHub热力图风格 |
| 打卡按钮 | 同上 | POST /api/check-ins | 每日一次 |
| 连续天数 | 同上 | GET /api/check-ins/streak | streak 数字 |
| 知识图谱 | 同上（P1） | — | 可选 |

### 5.7 学生讨论区（3 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 章节讨论列表 | `src/views/student/DiscussionView.vue` | GET /api/discussions/posts?chapterId= | 帖子列表 |
| 发帖/回复 | 同上 | POST /api/discussions/posts +/comments | 含匿名选项 |
| 回复树 | 同上（递归CommentNode） | GET /api/discussions/comments?postId= | 嵌套渲染 |

### 5.8 学生通知中心（2 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 通知列表 | `src/views/notifications/NotificationList.vue` | GET /api/notifications | 已读/未读区分 |
| 轮询更新 | `src/store/notification.js` | GET /api/notifications/unread-count | 30s轮询 |

### 5.9 学生个人中心（3 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 资料编辑 | `src/views/student/Profile.vue` | PUT /api/auth/me | 头像+姓名+邮箱 |
| 密码修改 | 同上 | PUT /api/auth/me/password | 旧密码验证 |
| 错题集 | 同上 | GET /api/wrong-questions/my?courseId= | 分类筛选 |

### 5.10 学生路由+导航

| 功能 | 文件 | 内容 |
|------|------|------|
| 学生路由 | `src/router/index.js` | 新增 10+ 条 student 路由 |
| 底部导航 | `src/components/StudentLayout.vue` | 5 tab: 广场/课程/学习/消息/我的 |
| 角色分离 | `src/App.vue` | role===STUDENT→StudentLayout, else→Layout |

---

## Phase 6 · 教师端补齐（P0 缺口 17 功能）

### 6.1 教师数据看板（4 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 选课学生数 | `src/views/teacher/TeacherDashboard.vue` | GET /api/enrollments/course/{id} | 每课人数 |
| 完成率 | 同上 | GET /api/learning-progress/completion | 百分比 |
| 平均分 | 同上 | GET /api/exercise-records | AVG(score) |
| 成绩明细 | `src/views/teacher/StudentGrades.vue` | GET /api/enrollments/course/{id} | 表格 |

### 6.2 学员管理（3 功能）

| 功能 | 实现文件 | API 依赖 | 验收 |
|------|---------|---------|------|
| 学员列表 | `src/views/teacher/StudentList.vue` | GET /api/enrollments/course/{id} | 学生+进度+成绩 |
| 学习进度 | 同上 | GET /api/learning-progress?userId=&courseId= | 每个学生 |
| 导出Excel | 同上 | （前端xlsx库） | 下载 |

### 6.3 其他教师功能（10 功能）

| 功能 | 实现位置 | 说明 |
|------|---------|------|
| 课程复制模板 | CourseList.vue + POST /api/courses?copyFrom= | 增复制按钮 |
| 拖拽章节排序 | CourseDetail.vue | el-table row-drag / 增 PUT /api/chapters/sort |
| 批量上传视频 | VideoList.vue | 多文件上传队列 |
| 视频封面自定义 | VideoList.vue 弹窗 | 图片上传 |
| 审核时效提示 | CourseList.vue | "预计48h审核" 文字 |
| 题目乱序 | ExerciseForm | shuffleQuestions 开关 |
| Excel题目导入 | QuestionList.vue | 上传+解析 |
| 题目预览 | ExerciseForm | 逐题预览模式 |
| 试题导出 | QuestionList.vue | 前端xlsx导出 |

---

## Phase 7 · 管理后台补齐（P0 缺口 17 功能）

### 7.1 数据统计看板（9 功能，优先 4 P0）

| 功能 | 实现文件 | API 依赖 | 说明 |
|------|---------|---------|------|
| 用户总数+趋势 | `src/views/admin/Dashboard.vue` | 新增 GET /api/admin/stats/overview | ECharts |
| 课程总数+趋势 | 同上 | 同上 | ECharts |
| 活跃用户 | 同上 | 同上 | DAU/WAU |
| 学习行为 | 同上 | 同上 | 视频+练习次数 |

### 7.2 批量导入用户（1 功能）

| 功能 | 实现文件 | API 依赖 |
|------|---------|---------|
| Excel导入 | UserList.vue + POST /api/users/batch | Excel 解析+批量insert |

### 7.3 操作日志（2 功能）

| 功能 | 实现文件 | API 依赖 |
|------|---------|---------|
| 操作日志列表 | `src/views/admin/OperationLogs.vue` | GET /api/operation-logs?page&size |
| 筛选查找 | 同上 | 按userId/action/时间 |

### 7.4 系统设置（5 功能，P1+）

| 功能 | 说明 | API |
|------|------|-----|
| 平台信息设置 | 名称/Logo/备案号 | POST /api/admin/settings |
| 注册开关 | 开放/关闭注册 | PUT /api/admin/settings/register |
| 上传限制 | 视频文件大小上限 | PUT /api/admin/settings/upload |
| CAS配置 | CAS对接参数 | PUT /api/admin/settings/cas |
| 轮播图管理 | 首页图片 | POST /api/admin/banners |

---

## Phase 8 · 视频基础设施（P0 缺口 5 功能）

### 8.1 视频上传（后端）

| 任务 | 文件 | 说明 |
|------|------|------|
| MultipartFile 接收 | `controller/VideoController.java` | +@PostMapping("/upload") @RequestParam MultipartFile |
| 文件存储 | `service/VideoService.java` | 存本地 /data/videos/{courseId}/{videoId}.mp4 |
| 文件校验 | 同上 | 类型(mp4/mov)+大小 ≤2GB |

### 8.2 FFmpeg 转码

| 任务 | 文件 | 说明 |
|------|------|------|
| FFmpeg 调用 | `service/VideoTranscodeService.java` | ProcessBuilder("ffmpeg", "-i", input, "-hls_time", "10", ...) |
| HLS 输出 | 同上 | m3u8 + ts 分片 → /data/videos/{courseId}/{videoId}/ |
| 异步执行 | 同上 | @Async 或线程池 |
| 转码状态 | 同上 | Video.status: 0UPLOADING→1TRANSCODING→2COMPLETED/3FAILED |
| 进度回调 | 同上 | 每隔 10% 更新 Video.progress |

### 8.3 视频防盗链

| 任务 | 文件 | 说明 |
|------|------|------|
| URL 签名 | `util/VideoSignUtil.java` | JWT(exp=当前+2h, videoId) → sign |
| 播放验证 | `controller/VideoController.java` | GET /api/videos/{id}/play → 验证 sign → 302 到真实 URL |
| Nginx 配置 | `nginx.conf` | internal + secure_link 模块 |

### 8.4 前端视频播放器

| 任务 | 文件 | 说明 |
|------|------|------|
| hls.js 集成 | `src/views/student/VideoPlayer.vue` | import Hls from 'hls.js' |
| 倍速控制 | 同上 | 0.75x/1x/1.25x/1.5x/2x |
| 进度保存 | 同上 | 每 10s PUT /api/learning-progress/progress/{id} |
| 断点续播 | 同上 | 从 learning_progress.videoPosition 恢复 |
| 移动端手势 | 同上 | 双击快进/快退，竖屏滑动调音量/亮度 |

---

## Phase 9 · 缺失数据表+API（P0 缺口 8 功能）

### 9.1 新增表

| 表 | Flyway | 字段 | 用途 |
|----|--------|------|------|
| course_reviews | V11 | id/course_id/user_id/rating(1-5)/content/is_anonymous/created_at/updated_at | 课程评价 |
| admin_settings | V12 | id/key/value/updated_at | 系统设置 KV |

### 9.2 新增 API

| API | Controller | 用途 |
|-----|-----------|------|
| POST /api/courses/{id}/reviews | CourseReviewController | 提交评价 |
| GET /api/courses/{id}/reviews | 同上 | 评价列表 |
| GET /api/admin/stats/overview | AdminStatsController | 平台总览 |
| GET /api/admin/stats/users | 同上 | 用户趋势 |
| GET /api/admin/stats/courses | 同上 | 课程趋势 |
| POST /api/users/batch | UserController | Excel批量导入 |
| GET /api/operation-logs | OperationLogController | 操作日志列表 |
| GET /api/admin/settings | AdminSettingsController | 系统设置 |
| PUT /api/admin/settings | 同上 | 更新设置 |

### 9.3 CAS 对接

| 任务 | 文件 | 说明 |
|------|------|------|
| CAS Client 依赖 | pom.xml | spring-security-cas |
| CAS 配置 | application.yml | cas.server-url-prefix + cas.server-login-url |
| AuthController 改造 | `controller/AuthController.java` | 替换 casLogin stub 为真实验证 |

---

## Phase 10 · 全栈联调+端到端验证

### 10.1 学生端 4 流程集成测试

```
流程A: 课程发现
  学生登录 → 广场浏览 → 搜索 → 课程详情 → 报名

流程B: 视频学习
  我的课程 → 进入课程 → 视频播放 → 倍速切换 → 进度保存 → 刷新恢复

流程C: 随堂练习
  章节练习 → 答题界面(4题型) → 提交 → 自动批改 → 查看解析 → 错题入集

流程D: 讨论互动
  章节讨论区 → 发帖(匿名) → 回复 → 树形展示
```

### 10.2 视频全链路测试

```
教师上传 → 分片上传 → 后端接收 → FFmpeg转码 → 状态更新 → 学生播放(防盗链)
```

### 10.3 数据看板验证

```
学生端进度 ←→ 教师端数据 ←→ 管理后台统计 —— 三端数据一致性
```

### 10.4 全量交叉验证

按 SKILL.md §4.1：R1-R4 4 维审查，全 PASS

### 10.5 git tag v1.0.0

---

## 验收矩阵

| Phase | P0 功能 | P1 功能 | P2 功能 |
|-------|:------:|:------:|:------:|
| Phase 5 学生前端 | 41 | 0 | 0 |
| Phase 6 教师端 | 17 | 0 | 0 |
| Phase 7 管理后台 | 17 | 0 | 0 |
| Phase 8 视频基础 | 5 | 0 | 0 |
| Phase 9 缺失API | 8 | 0 | 0 |
| Phase 10 联调 | — | — | — |
| **合计** | **88** | **0** | **0** |
| 注意：仅覆盖 P0 缺口（44 P0 已部分完成，实际新增 88 任务） | | | |

> P1(40)/P2(28) 不在 spec 范围内，标记为 v1.1 backlog。

---

*文档版本：v1.0*
*日期：2026-06-12*
*基线：docs/功能清单.md v2.0*
