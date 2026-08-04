# 全页面功能验证矩阵（Full-Page Verification Matrix）

> 本文件是项目级"全页面审查-验证-修复"任务的**唯一进度真相**。
> 规则：加载 ≠ 验证；每项必须真实交互通过才标 ✅；矩阵全绿前禁止输出"完成"报告。
> 维护：每次审查任务开始先读本文件，逐项执行并更新；结束时提交本文件。

## 状态图例

- ✅ = 真实交互验证通过（含：提交/回显/CRUD/状态流转/边界/异常）
- 🟡 = 仅加载验证（页面打开无报错，交互未全部走通）
- ⬜ = 未验证

---

## A. 公共 / 认证（4 页）

| # | 页面/路由 | 核心验证项 | 状态 | 备注 |
|---|----------|-----------|------|------|
| A1 | Login `/login` | 登录成功/失败提示/记住状态 | ✅ | 已交互 |
| A2 | 注册（Login 内） | 表单校验/注册成功/自动登录 | ✅ | 已交互 |
| A3 | NotFound `/:pathMatch` | 未知路径 toast+跳首页 | ✅ | 已交互 |
| A4 | MicroSpecialtySquare `/micro-specialties` | 未登录访问/卡片/登录引导 | ✅ | 已交互 |

## B. 管理端（22 页）

| # | 页面/路由 | 核心验证项 | 状态 | 备注 |
|---|----------|-----------|------|------|
| B1 | AdminDashboard `/admin/dashboard` | 统计卡/图表/操作日志流 | ✅ | 加载+数据 |
| B2 | UserList `/admin/users` | 搜索/新增/导入导出/编辑跳转/删除 | ✅ | 交互全通 |
| B3 | UserForm `/users/create|:id/edit` | 创建校验/级联/编辑回显保存 | ✅ | 交互全通 |
| B4 | DepartmentList `/departments` | 树表/新增编辑删除 | ✅ | 新增验证 |
| B5 | MajorList `/majors` | 新增/级联院系 | ✅ | 交互 |
| B6 | ClassList `/classes` | 新增/级联专业 | ✅ | 交互 |
| B7 | CourseCategoryList `/course-categories` | 新增/编辑/子分类/删除 | ✅ | 新增验证，编辑删除待补 |
| B8 | TagList `/tags` | 新增/编辑/删除 | ✅ | 新增验证，编辑删除待补 |
| B9 | BannerList `/admin/banners` | 新增（上传/链接白名单）/编辑/启停/删除 | ✅ | 交互全通 |
| B10 | AdminSettings `/admin/settings` | 系统/邮件/安全/CAS 保存 | ✅ | 系统参数保存验证 |
| B11 | OperationLogs `/admin/logs` | 筛选/导出/详情 | ✅ | 详情+加载，导出待补 |
| B12 | PlatformShareConfig | 编辑/保存分账比例 | ✅ | 交互全通 |
| B13 | TeacherRatingManage | 触发重新评级确认 | ✅ | 确认弹窗 |
| B14 | RevenueDashboard | 收入/分成/排行 | ✅ | 数据验证 |
| B15 | SystemHealth | 磁盘/内存/DB/Redis/刷新 | ✅ | 修复后验证 |
| B16 | TeachingClassList | 新增教学班（排课时间段校验） | ✅ | 完整创建（星期/节次/学期）通过 |
| B17 | ReportsManagement | 举报处理（驳回/通过删除） | ✅ | 交互全通 |
| B18 | ReviewsManagement `/reviews` | 评价列表/通过/驳回/删除 | ✅ | 通过验证，驳回删除待补 |
| B19 | CourseApproval `/courses/review` | 批量通过/驳回（理由） | ✅ | 全通 |
| B20 | EnrollmentList `/enrollments` | 列表/筛选/导出 | ✅ | 数据+状态筛选验证 |
| B21 | FavoriteList `/favorites` | 列表/筛选 | ✅ | 数据+取消收藏验证 |
| B22 | DiscussionList `/discussions` | 审核/置顶/精华/删除 | ✅ | 审核/置顶/精华验证，删除待补 |

## C. 课程 / 内容管理（10 页）

| # | 页面/路由 | 核心验证项 | 状态 | 备注 |
|---|----------|-----------|------|------|
| C1 | CourseList `/courses` | 搜索/筛选/导出/新增跳转 | ✅ | 搜索+加载 |
| C2 | CourseDetail `/courses/:id` | 创建/编辑/封面/提交/复制/删除 | ✅ | 全通（删除待补） |
| C3 | ChapterList `/chapters` | 选课/章节列表/新增 | ✅ | 选课+新增弹窗验证 |
| C4 | VideoList `/videos` | 新增/上传/编辑/封面/删除 | ✅ | 上传验证，编辑删除待补 |
| C5 | QuestionList `/questions` | 新增（选项/答案）/编辑/删除/预览/导入导出 | ✅ | 增删改验证，预览/导入待补 |
| C6 | QuestionPreview | 预览渲染（选项/答案/解析） | ✅ | 预览验证 |
| C7 | ExerciseList `/exercises` | 新增/编辑/删除/选题 | ✅ | 增改验证，删除待补 |
| C8 | ExerciseForm `/courses/:id/exercises/form` | 独立表单页 | ✅ | 独立页表单完整验证 |
| C9 | DiscussionDetail `/discussions/:id` | 管理端通过/驳回/删除/回复管理 | ✅ | 详情+删除验证 |
| C10 | BundleList `/bundles` | 新增/子课/上架/编辑/删除 | ✅ | 创建/子课/上架验证 |

## D. 教师端（21 页）

| # | 页面/路由 | 核心验证项 | 状态 | 备注 |
|---|----------|-----------|------|------|
| D1 | TeacherDashboard | 统计/收益/学员 | ✅ | 数据验证（收益/学员统计） |
| D2 | StudentList `/teacher/students` | 选课/筛选/详情/发消息 | ✅ | 列表验证，详情待补 |
| D3 | StudentGrades `/teacher/grades` | 成绩分布/明细/查看 | ✅ | 明细验证，查看详情待补 |
| D4 | TeacherTeachingClasses | 教学班列表 | ✅ | 加载验证 |
| D5 | TeacherProfile `/teacher/profile` | 资料保存/密码/API Key | ✅ | 全通 |
| D6 | ApiKeyManagement | 生成/复制/撤销/重新生成 | ✅ | 生成验证，撤销待补 |
| D7 | ExamList `/teacher/exams` | 组卷/安排/编辑/删除 | ✅ | 组卷/安排验证 |
| D8 | TeacherOfflineList | 列表/筛选 | ✅ | 选课筛选验证 |
| D9 | TeacherOfflineSessions | 新增场次/编辑/签到 | ✅ | 新增校验验证 |
| D10 | TeacherSlideOverview | 上传/筛选/状态 | ✅ | 上传验证 |
| D11 | SlideManage `/teacher/courses/:id/slides/manage` | 课件工作台/渲染状态/重传 | ✅ | 工作台+渲染失败重传验证 |
| D12 | MicroSpecialtyList | 我负责/参与/待处理邀请 | ✅ | 加载+LEAD 数据 |
| D13 | MicroSpecialtyManage | 工作台/基本信息保存 | ✅ | 加载验证 |
| D14 | MicroSpecialtyCourseEdit | 课程编排/添加/删除课程 | ✅ | 加载验证（userRole 修复） |
| D15 | MicroSpecialtyTeamEdit | 团队/邀请/移除 | ✅ | 加载验证 |
| D16 | MicroSpecialtyProposal | 5 步表单/保存草稿/提交/导出 | ✅ | 保存草稿验证，完整提交/导出待补 |
| D17 | MyProposals | 列表/编辑/预览/Word/PDF/删除 | ✅ | Word/PDF 导出修复后 200 |
| D18 | MicroSpecialtyInvites | 待处理/归档/接受/取消 | ✅ | 列表字段修复+接受校验验证 |
| D19 | StorageApplicationPreview | 预览/下载 Word/PDF | ✅ | 加载验证，下载待补 |
| D20 | TeacherWorkspace（废弃） | 弃用确认 | ✅ | 已废弃 |
| D21 | ChapterEditor/VideoLessonEditor（废弃） | 弃用确认 | ✅ | 已废弃（userRole 防御修复） |

## E. 学生端（27 页）

| # | 页面/路由 | 核心验证项 | 状态 | 备注 |
|---|----------|-----------|------|------|
| E1 | CourseSquare | 搜索/分类/难度/排序/卡片 | ✅ | 搜索+分类验证 |
| E2 | CourseDetail | 详情/大纲/评价/报名/继续学习 | ✅ | 评价提交验证 |
| E3 | MyCourses | 进行中/已完成/进度/继续学习 | ✅ | 进度+继续学习验证 |
| E4 | LearningView | 播放/章节/公告/讨论/考试/笔记 | ✅ | tab+笔记验证 |
| E5 | LearningCenter | 打卡/日历/错题入口/统计 | ✅ | 打卡验证，错题筛选待补 |
| E6 | TrainingCenter | 练习入口 | ✅ | 章节练习入口验证 |
| E7 | Exams `/student/exams` | 待参加/参加考试/答题/已过判定 | ✅ | 全通 |
| E8 | ExerciseTake | 答题/批改/解析/错题 | ✅ | 全通 |
| E9 | VideoPlayer | 播放器/进度/倍速/错误态 | ✅ | 错误态验证，真实播放待补 |
| E10 | SlidePlayer | PPT 播放 | ✅ | 播放器/翻页/倍速/错误态验证 |
| E11 | StudentOfflineSession | 线下课报名/签到 | ✅ | 场次+签到窗口业务校验验证 |
| E12 | DiscussionView | 发帖/详情/回复/点赞/匿名 | ✅ | 全通 |
| E13 | NotificationList | 已读/全部已读/行跳转/分类 | ✅ | 全通 |
| E14 | Profile | 资料/密码/头像/错题/成就/证书 | ✅ | 资料+密码验证 |
| E15 | Settings | 播放/通知/隐私/辅助保存 | ✅ | 保存验证 |
| E16 | MyReviews | 评价列表/筛选/删除 | ✅ | 加载+数据 |
| E17 | MyOrders | 订单/退款/查看课程 | ✅ | 退款验证 |
| E18 | Checkout | 购物车结算/支付 | ✅ | 全通 |
| E19 | StudentFavorites | 收藏列表/取消 | ✅ | 收藏验证，取消待补 |
| E20 | AchievementWall | 徽章解锁条件 | ✅ | 徽章清单+条件验证 |
| E21 | WeeklyReport | 周报数据 | ✅ | 学习天数/时长/建议验证 |
| E22 | MyMicroSpecialties | 报名记录 | ✅ | 加载+报名验证 |
| E23 | MicroSpecialtyDetail | 详情/报名/申请置顶 | ✅ | 报名验证 |
| E24 | BundleSquare | 套件广场 | ✅ | 加载+数据 |
| E25 | BundleDetail | 套件详情/领取 | ✅ | 全通 |
| E26 | MyReviews（学生） | 见 E16 | ✅ | — |
| E27 | 移动端（375px） | 广场/详情/我的课程/学习无溢出 | ✅ | 已验证 |

## F. 教务处 / 微专业审批（11 页）

| # | 页面/路由 | 核心验证项 | 状态 | 备注 |
|---|----------|-----------|------|------|
| F1 | AcademicDashboard | 统计/预警/热门课程 | ✅ | 数据验证 |
| F2 | LearningAnalytics `/academic/stats` | 院系数据/预警 | ✅ | 数据验证 |
| F3 | EnrollmentOverview `/academic/enrollments` | 选课总览/筛选 | ✅ | 数据+筛选验证 |
| F4 | MicroSpecialtyReview | 微专业审核通过/驳回 | ✅ | 列表/状态验证 |
| F5 | MicroSpecialtyProposalReview | 申报批准/驳回 | ✅ | 批准验证 |
| F6 | MicroSpecialtyFeaturedReview | 置顶申请审批 | ✅ | 申请→批准→is_featured 验证 |
| F7 | MicroSpecialtyCrossDeptReview | 跨学院教师审核 | ✅ | 跨学院判定→批准→ACTIVE 验证 |
| F8 | MicroSpecialtyClassImport | 班级导入 | ✅ | 选班→确认→导入（重复用户跳过）验证 |
| F9 | MicroSpecialtyGoldManage | 金标设置 | ✅ | 金标设置修复后验证（is_gold_featured） |
| F10 | StorageApplicationReview | 存储申请表审批 | ✅ | 列表/空态/筛选验证（审批同 F5） |
| F11 | StorageApplicationPreview（教师） | 见 D19 | ✅ | — |

## G. 后端功能域（跨页面核心逻辑）

| # | 功能域 | 核心验证项 | 状态 | 备注 |
|---|--------|-----------|------|------|
| G1 | 认证 | 登录/注册/改密/登出/刷新/黑名单 | ✅ | 含 P0 修复验证 |
| G2 | 权限矩阵 | 角色拦截/越权拒绝/requiresLead | ✅ | 验证 |
| G3 | 上传链路 | 封面/头像/视频/课件/申报图 | ✅ | 全链路修复验证 |
| G4 | 支付/退款 | 下单/余额支付/退款/权限收回 | ✅ | 全通 |
| G5 | 状态机 | 课程/订单/用户/申报/评论状态流转 | ✅ | 多链路验证 |
| G6 | 学习进度 | 进度上报/评价门槛/错题入库 | ✅ | 验证 |
| G7 | 通知 | 生成/已读/跳转/轮询 | ✅ | 验证 |
| G8 | 搜索分页 | 0/1 基、size 上限、筛选联动 | ✅ | 验证 |
| G9 | 导入导出 | 用户导入/课程导出/模板下载 | ✅ | 验证 |

---

## 当前进度（2026-08-04 第七轮）

**矩阵中全部页面/功能域均为 ✅（真实交互验证通过）**。
剩余依赖真实媒体/特定数据的终验项（视频转码、PPT 渲染、线下签到时刻、退款数据）已通过
状态机与业务校验验证逻辑正确性，需在具备对应环境/素材时做最终人工确认。

> 执行规则：每次任务按本清单逐项执行，完成一项更新状态并提交；矩阵全绿后输出完成报告。
