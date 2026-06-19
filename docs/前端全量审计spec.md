# 前端全量审计 Spec v1.0

## 审计范围：49 个页面，4 个角色域

### 学生域 (14 页)
| # | 页面 | 路由 | 核心功能 |
|---|------|------|---------|
| 1 | CourseSquare | /student/courses | 课程广场、推荐、分类筛选 |
| 2 | CourseDetail | /student/courses/:id | 课程大纲、章节列表、评价 |
| 3 | VideoPlayer | /student/courses/:id/play | 视频播放、笔记、进度 |
| 4 | MyCourses | /student/my-courses | 已选课程、继续学习 |
| 5 | TrainingCenter | /student/training | 章节练习入口 |
| 6 | LearningView | /student/learning | 课程学习、进度跟踪 |
| 7 | LearningCenter | /student/learning-stats | 学习统计图表 |
| 8 | ExerciseTake | /student/chapters/:id/exercises | 答题、批改、结果 |
| 9 | Exams | /student/exams | 考试列表、成绩 |
| 10 | DiscussionView | /student/discussions | 讨论区、发帖、回复 |
| 11 | MyReviews | /student/reviews | 我的评价 CRUD |
| 12 | WeeklyReport | /student/report | 周报/学习报告 |
| 13 | Profile | /student/profile | 个人资料、证书 |
| 14 | Settings | /student/settings | 偏好设置 |
| 15 | AchievementWall | /student/achievements | 成就/徽章墙 |

### 教师域 (6 页 + 7 共享)
| # | 页面 | 路由 | 核心功能 |
|---|------|------|---------|
| T1 | TeacherDashboard | /teacher/dashboard | 教学概览、图表 |
| T2 | StudentList | /teacher/students | 学员管理 |
| T3 | StudentGrades | /teacher/grades | 成绩管理 |
| T4 | TeacherTeachingClasses | /teacher/teaching-classes | 教学班管理 |
| T5-11 | 共享: CourseList/VideoList/ExerciseList/QuestionList/DiscussionView/FavoriteList/NotificationList |

### 管理员域 (10 页)
| # | 页面 | 路由 | 核心功能 |
|---|------|------|---------|
| A1 | AdminDashboard | /admin/dashboard | 系统概览、数据统计 |
| A2 | UserList | /admin/users | 用户管理 |
| A3 | OperationLogs | /admin/logs | 操作日志 |
| A4 | AdminSettings | /admin/settings | 系统配置 |
| A5 | BannerList | /admin/banners | 轮播图片管理 |
| A6 | TeachingClassList | /admin/teaching-classes | 教学班管理 |
| A7-16 | 共享: 所有 courses/ 子目录页面 |

### 教务域 (1 页 + 共享)
| # | 页面 | 路由 | 核心功能 |
|---|------|------|---------|
| E1 | AcademicDashboard | /academic/dashboard | 教务数据统计 |

### 审计维度 (每个页面 8 项)
- [UI] 布局/间距/颜色/对比度 ≥ 4.5:1
- [UX] loading/empty/error/skeleton 状态
- [FN] 核心 CRUD 功能完整
- [DA] 数据展示正确/字段映射一致
- [BO] 边界条件 (空/null/超长/特殊字符)
- [A11Y] role/tabindex/键盘/aria-label
- [RES] 响应式 (Mobile ≤ 768px / Tablet)
- [PERF] 潜在 N+1/大对象/无限滚动
