# 微课平台 · 全量测试任务清单 v1.0

> 生成时间: 2026-08-03 · 执行人: 项目总负责人（AI 首席总工程师）
> 测试环境: `localhost:8088` (admin) + `localhost:8089` (api) + `localhost:5433/6380` (隔离 DB/Redis)
> 浏览器: ego-browser（独占、真实 Chromium），账号: admin/admin123 · academic1/password123 · teacher1/password123 · student1/password123
> 范围声明: 覆盖全部 135 条路由（含 9 条旧 redirect 路径）、4 角色全部页面、全部交互与弹窗、54 个前端 API 模块、全部状态渲染、console/network 实时监控、响应式、a11y。

---

## 0. 测试总纲（每一条都执行的公共断言）

1. **渲染校验**: 页面无白屏、无空白区、无错位；标题/面包屑/卡片/表格均渲染。
2. **布局校验**: 导航高亮正确、侧边栏/顶栏/底部 tab 与角色匹配、无横向溢出。
3. **样式校验**: 无样式错乱、无文字重叠、无颜色不可读（对比度≥4.5:1）。
4. **控制台监控**: `console.error` = 0、JS 异常（error 事件/unhandledrejection）= 0、无"未定义/类型错误/渲染异常"。
5. **网络监控**: 所有 `/api/*` 请求 2xx（4xx/5xx=FAIL）、无超时、无资源加载失败（非 2xx 静态资源=FAIL）、无参数错误。
6. **状态渲染**: loading 骨架屏、空态提示、错误态提示、401/403 重定向、token 静默刷新全部到位且无多余 toast。
7. **弹窗规范**: 创建/编辑/删除均走弹窗；删除必有确认框；弹窗关闭后状态复位、无残留遮罩。
8. **导航校验**: 每个菜单项点击可达；返回/前进不 404；未知路径弹"页面不存在"并回角色首页。

---

## A. 页面维度 · 全量路由巡检（每角色逐条执行）

> 每条 = 进入页面 → 等待渲染 → 采集 http 状态 / console / JS 错误 / API 4xx/5xx → 离开。

### A1. ADMIN（45 条）

- [ ] `/admin/dashboard` 数据总览（统计卡/ECharts/loading）
- [ ] `/admin/users` 用户管理（表格/搜索/分页）
- [ ] `/admin/logs` 操作日志（筛选/查询）
- [ ] `/admin/settings` 系统设置（邮件/安全/CAS/关于 Tab）
- [ ] `/admin/platform-share-config` 平台分账
- [ ] `/admin/teacher-ratings` 教师评级
- [ ] `/admin/revenue` 营收看板
- [ ] `/admin/banners` 轮播图管理（新增弹窗）
- [ ] `/admin/teaching-classes` 教学班管理（新增弹窗）
- [ ] `/admin/system-health` 系统状态
- [ ] `/admin/reports` 报表管理
- [ ] `/departments` 院系管理（新增/编辑/删除弹窗）
- [ ] `/majors` 专业管理
- [ ] `/classes` 班级管理
- [ ] `/users` 用户管理（含批量导入/教师审批弹窗）
- [ ] `/users/create` 用户新建表单
- [ ] `/users/:id/edit` 用户编辑表单
- [ ] `/courses` 全部课程（搜索/重置/新建）
- [ ] `/courses/create` 课程新建（表单校验）
- [ ] `/courses/review` 课程审核（Tab 切换）
- [ ] `/course-categories` 分类管理
- [ ] `/tags` 标签管理
- [ ] `/chapters` 章节管理
- [ ] `/videos` 视频管理（新增弹窗）
- [ ] `/enrollments` 选课管理（通过/拒绝操作）
- [ ] `/favorites` 收藏管理
- [ ] `/questions` 题库管理
- [ ] `/exercises` 练习管理
- [ ] `/discussions` 讨论管理
- [ ] `/notifications` 通知管理
- [ ] `/reviews` 评价管理
- [ ] `/bundles` 课程套餐
- [ ] `/academic/dashboard` 教务驾驶舱
- [ ] `/academic/stats` 学习数据分析
- [ ] `/academic/enrollments` 选课数据总览
- [ ] `/teacher/dashboard` 教师看板
- [ ] `/teacher/students` 学员管理
- [ ] `/teacher/grades` 成绩汇总
- [ ] `/teacher/teaching-classes` 教学班
- [ ] `/teacher/profile` 个人设置
- [ ] `/teacher/slides` 互动课件管理
- [ ] `/teacher/exams` 试卷管理
- [ ] `/teacher/offline-list` 线下课管理
- [ ] `/micro-specialties` 微专业广场（公共页）
- [ ] `/profile` 个人中心（资料/密码/错题）
- [ ] 旧 redirect 链: `/admin` `/admin/operation-logs` `/admin/roles` `/admin/courses` `/admin/videos` `/admin/chapters` `/admin/exercises` `/admin/questions` `/admin/bundles` `/admin/orders` `/admin/certificates` `/admin/badges` `/admin/notifications` `/admin/discussions`（不 404、不自循环）

### A2. ACADEMIC（43 条）

- [ ] `/academic/dashboard` 驾驶舱总览
- [ ] `/academic/stats` 学习数据分析
- [ ] `/academic/enrollments` 选课总览
- [ ] `/academic/micro-specialties/review` 已批准微专业
- [ ] `/academic/micro-specialties/proposals` 申报审批（Tab 切换）
- [ ] `/academic/micro-specialties/featured` 置顶审核
- [ ] `/academic/micro-specialties/cross-dept` 跨学院审核
- [ ] `/academic/micro-specialties/class-import` 班级导入（文件上传）
- [ ] `/academic/micro-specialties/gold` 金标管理
- [ ] `/academic/micro-specialties/storage-review` 存储申请表审批
- [ ] `/admin/dashboard` 数据总览
- [ ] `/admin/users` 用户管理
- [ ] `/admin/logs` 操作日志
- [ ] `/admin/settings` 系统设置
- [ ] `/admin/banners` 轮播图
- [ ] `/admin/teaching-classes` 教学班
- [ ] `/admin/revenue` 营收
- [ ] `/admin/reports` 报表
- [ ] `/departments` `/majors` `/classes` `/users` 基础数据
- [ ] `/courses` `/courses/create` `/courses/review` 课程
- [ ] `/course-categories` `/tags` `/bundles` `/banners` 课程配置
- [ ] `/chapters` `/videos` `/enrollments` `/favorites` `/questions` `/exercises` 内容/选课
- [ ] `/discussions` `/notifications` `/reviews` 互动管理
- [ ] `/teacher/dashboard` `/teacher/students` `/teacher/grades` `/teacher/offline-list` 教学数据
- [ ] `/micro-specialties` `/profile` 公共/个人

### A3. TEACHER（28 条 + 动态页）

- [ ] `/teacher/dashboard` 我的看板（统计卡/图表）
- [ ] `/teacher/courses` 我的课程（新建入口）
- [ ] `/teacher/videos` 视频管理（新增弹窗）
- [ ] `/teacher/exercises` 练习管理
- [ ] `/teacher/discussions` 讨论区
- [ ] `/teacher/favorites` 收藏
- [ ] `/teacher/questions` 题库
- [ ] `/teacher/students` 学员管理
- [ ] `/teacher/grades` 成绩明细
- [ ] `/teacher/teaching-classes` 教学班
- [ ] `/teacher/profile` 个人设置
- [ ] `/teacher/slides` 互动课件管理
- [ ] `/teacher/exams` 试卷管理
- [ ] `/teacher/offline-list` 线下课管理
- [ ] `/teacher/micro-specialties` 微专业列表（Tab: 我参与的/邀请）
- [ ] `/teacher/micro-specialties/invites` 邀请列表
- [ ] `/teacher/micro-specialties/proposals` 微专业申报（4 步向导）
- [ ] `/teacher/micro-specialties/my-proposals` 我的申报
- [ ] `/courses` `/courses/create` `/chapters` `/videos` `/questions` `/exercises` 内容管理复用
- [ ] `/discussions` `/notifications` `/bundles` `/profile` 复用
- [ ] 动态页: `/teacher/courses/:id` 课程详情 · `/teacher/courses/:courseId/slides/manage` 课件管理 · `/teacher/chapters/:chapterId/offline-sessions` · `/teacher/courses/:courseId/chapters/:chapterId/manage-videos|slides|offline|exam` · `/teacher/micro-specialties/:id/manage|courses|team` · `/teacher/micro-specialties/storage-preview/:id`

### A4. STUDENT（20 条 + 动态页）

- [ ] `/student/courses` 课程广场（分类导航/搜索/卡片/推荐/瀑布流）
- [ ] `/student/bundles` 套餐广场
- [ ] `/student/my-courses` 我的课程（进行中/已完成/收藏 Tab）
- [ ] `/student/training` 训练中心
- [ ] `/student/learning` 学习中心（课程列表+学习视图）
- [ ] `/student/learning-stats` 学习统计（打卡/日历/趋势）
- [ ] `/student/notifications` 消息中心（全部已读）
- [ ] `/student/exams` 考试中心
- [ ] `/student/profile` 个人中心（资料/密码/错题/徽章/证书）
- [ ] `/student/report` 周报
- [ ] `/student/favorites` 收藏
- [ ] `/student/orders` 我的订单
- [ ] `/student/checkout` 结算页
- [ ] `/student/reviews` 我的评价
- [ ] `/student/settings` 设置
- [ ] `/student/achievements` 成就墙
- [ ] `/student/discussions` 讨论区
- [ ] `/student/my-micro-specialties` 我的微专业
- [ ] `/micro-specialties` 微专业广场
- [ ] `/profile` 个人中心
- [ ] 动态页: `/student/courses/:id` 课程详情 · `/student/courses/:id/play/:videoId?` 视频播放器 · `/student/courses/:courseId/slides/player` PPT 播放 · `/student/chapters/:chapterId/exercises` 随堂练习 · `/student/chapters/:chapterId/offline` 线下课报名 · `/student/micro-specialties/:id` 微专业详情 · `/student/bundles/:id` 套餐详情 · `/student/discussion/:chapterId` 重定向

### A5. 公共/认证/异常页

- [ ] `/login` 登录页（账号/密码校验、错误提示、回车提交、redirect 回跳）
- [ ] `/` 角色首页重定向（4 角色各自正确）
- [ ] `/micro-specialties` 未登录可访问
- [ ] 未知路径 `/xxx` → 提示"页面不存在"→ 回角色首页（4 角色）
- [ ] 未登录访问受保护页 → `/login?redirect=...` 且登录后回跳

---

## B. 模块维度 · 全模块交互清单（30 模块）

> 每条 = 列表加载 → 搜索/筛选/重置 → 分页 → 新增/编辑/删除弹窗 → Tab/状态切换 → 空/错/加载三态 → 权限可见性。

- [ ] B1 认证: 登录/登出/refresh 静默链/401 统一处理/权限矩阵
- [ ] B2 用户管理: 列表/关键字搜索/角色筛选/新增/编辑/重置密码/批量导入/教师审批/详情卡片
- [ ] B3 院系-专业-班级: 三表 CRUD + 级联（grade 级联依赖）
- [ ] B4 课程管理: 列表/搜索/分类联动/新建向导/编辑/封面上传/提交审核/审批流
- [ ] B5 章节管理: 课程筛选/章节 CRUD/排序/树结构
- [ ] B6 视频管理: 上传/编辑/删除/课程筛选/进度关联
- [ ] B7 题库: 题型筛选/新增/编辑/预览/批量
- [ ] B8 练习管理: 章节关联/题目编排/提交审核
- [ ] B9 选课管理: 通过/拒绝/筛选/批量操作
- [ ] B10 讨论区: 发帖/回复/匿名/嵌套树/章节关联/管理删除
- [ ] B11 评价管理: 列表/回复/隐藏/评分统计
- [ ] B12 通知管理: 已读/未读/全部已读/轮询/偏好设置
- [ ] B13 课程套餐: 打包/编辑/上下架/学生购买
- [ ] B14 购物车-订单-结算: 加购/购物车抽屉/结算/支付状态/订单列表
- [ ] B15 收藏: 收藏/取消/列表/课程关联
- [ ] B16 学习中心: 打卡/日历/连续天数/时长/趋势图/知识图谱
- [ ] B17 视频播放器: HLS/倍速/进度记忆/全屏/小窗/字幕/快捷键/上传队列
- [ ] B18 随堂练习: 四题型/进度/自动批改/解析/重做/错题入库
- [ ] B19 考试: 试卷管理/组卷/发布/学生考试/成绩
- [ ] B20 微专业: 广场/申报向导/审批(教务)/置顶/跨学院/金标/班级导入/工作台/课程编排/团队管理/邀请
- [ ] B21 存储申请: 申请表/审批流/预览
- [ ] B22 互动课件(Slides): 管理/上传/编辑器/预览/播放
- [ ] B23 轮播图: 新增/编辑/上下架/排序
- [ ] B24 教师评级/平台分账/营收看板: 列表/图表/统计
- [ ] B25 教学班-线下课: CRUD/报名/签到/离线会话
- [ ] B26 系统设置: 邮件/安全/CAS/关于 Tab
- [ ] B27 操作日志: 查询/导出/审计
- [ ] B28 个人中心: 资料/头像/密码/错题集/徽章/证书
- [ ] B29 报表/周报: 数据导出/图表/周报生成
- [ ] B30 全局组件: 布局导航/学生底部 Tab/错误边界/上传进度/富文本/动态表格/签名/日期选择(DatePickerYM/Year)/空态组件

---

## C. 功能点维度 · 逐页交互（关键页细项）

### C1. 登录页
- [ ] 空提交 → 校验提示；错误账号 → 后端错误码 toast；成功 → 存 token+refresh+userInfo → 回跳 redirect
- [ ] 回车提交、按钮 loading 态、密码可见切换

### C2. 用户管理（/users, /admin/users）
- [ ] 搜索框输入→搜索→重置；角色/状态筛选联动
- [ ] 新增 → 弹窗表单（用户名/密码/姓名/角色/院系/专业/班级）→ 提交成功 toast + 列表刷新
- [ ] 编辑 → 预填数据 → 保存；删除 → 确认框 → 删除
- [ ] 批量导入 Excel → 进度 → 结果反馈（成功/失败行数）
- [ ] 教师审批弹窗 → 通过/驳回
- [ ] 详情卡片：基本信息/角色/所属组织
- [ ] 分页 total/sizes/prev/next；空数据空态

### C3. 课程管理（/courses, CourseDetail）
- [ ] 列表: 搜索/分类/难度/状态筛选/重置/分页
- [ ] 新建: 表单必填校验（标题/分类/学习模式/价格/类型）→ 保存 DRAFT
- [ ] 详情/编辑: 基本信息/封面上传/简介富文本/标签/推荐位/状态流转（提交审核/审核通过/发布/下架）
- [ ] 章节/视频/练习 Tab 内嵌管理；进度条与完成率展示

### C4. 学生课程广场/详情
- [ ] 分类导航树点击联动；搜索与分类组合；推荐角标；报名/继续学习按钮状态切换
- [ ] 详情: 课程信息/大纲列表/教师信息/报名按钮/学习同伴人数/评价 Tab

### C5. 视频播放器（/student/courses/:id/play/:videoId?）
- [ ] HLS m3u8 加载播放；倍速 0.75-2x；全屏；小窗；进度记忆断点；10s 进度上报；字幕；快捷键；上传队列状态

### C6. 学习视图（/student/learning?courseId=）
- [ ] 章节侧栏导航/视频区/练习快速面板/笔记面板/资源工具栏；心跳与完成流；路由上下文

### C7. 随堂练习（/student/chapters/:chapterId/exercises）
- [ ] 题目渲染四题型；3/10 进度；提交即时批改；答案解析高亮；重做限制；错题自动入库

### C8. 微专业申报向导（/teacher/micro-specialties/proposals）
- [ ] 4 步向导（基本信息/课程/团队/提交）下一步/上一步/校验/草稿保存；提交后进"我的申报"

### C9. 微专业工作台（/teacher/micro-specialties/:id/manage|courses|team）
- [ ] LEAD 权限校验（requiresLead + my-role API）；工作台 Tab；课程编排拖拽排序；团队增删；邀请

### C10. 学习统计（/student/learning-stats）
- [ ] 总时长/完成数/正确率趋势图/活跃日历/打卡按钮/连续天数

### C11. 结算/订单（/student/checkout, /student/orders）
- [ ] 购物车清单 → 结算 → 订单创建 → 支付状态展示；订单列表状态筛选

### C12. 互动课件（/teacher/slides, SlideManage, SlidePlayer）
- [ ] 列表/上传（PDF/PPTX）/解析进度/编辑器（块编辑/配音/属性）/预览/学生播放器全屏

### C13. 考试（/teacher/exams, /student/exams）
- [ ] 试卷 CRUD/组卷（题库选题）/发布/学生答题/自动判分/成绩

### C14. 线下课/离线会话（/teacher/offline-list, TeacherOfflineSessions, /student/chapters/:chapterId/offline）
- [ ] 线下课 CRUD/报名名单/签到/离线会话生成

### C15. 个人中心（/profile, /student/profile）
- [ ] 资料编辑保存/头像上传/旧密码校验修改/错题集筛选/徽章/证书

### C16. 各管理列表页通用
- [ ] 新增/编辑弹窗开关与复位；删除确认；搜索/重置；分页；三态；权限按钮可见性

---

## D. 接口维度 · 54 个前端 API 模块全量核对

> 每条 = 该模块端点随页面访问自动请求；检查 2xx、返回结构（code/data/分页 5 字段）、错误码语义、鉴权头。

- [ ] auth.js 登录/登出/me/refresh/密码
- [ ] user.js department.js major.js class.js（基础 CRUD + 分页）
- [ ] course.js course-category.js tag.js chapter.js video.js section.js（课程域）
- [ ] question.js exercise.js exercise-record.js grade.js exam.js（练习考试域）
- [ ] enrollment.js favorite.js cart.js order.js certificate.js badge.js（选课电商域）
- [ ] discussion.js review.js course-review.js teacher-rating.js（互动评价域）
- [ ] notification.js notification-preference.js error-report.js（消息域）
- [ ] bundle.js learning-progress.js checkin.js wrong-question.js（学习域）
- [ ] microSpecialty.js storageApplication.js slide.js（微专业/课件域）
- [ ] admin-stats.js academic-stats.js revenue/operation-log.js admin-banner.js admin-settings.js（管理域）
- [ ] teacher.js teaching-class.js offline-session.js platform-share-config.js weekly report 相关（教学域）
- [ ] 全部响应为 `{code, message, data}`；分页为 `{items,totalElements,totalPages,page,size}`；错误码与契约一致

---

## E. 状态渲染维度

- [ ] E1 loading: 每个列表页首次加载有 v-loading/骨架屏
- [ ] E2 empty: 空列表有"暂无数据"空态，无控制台错误
- [ ] E3 error: 接口失败有 toast/错误提示，不白屏
- [ ] E4 401/403: 统一拦截 → 静默刷新 → 重放；失败 → 回登录并带 redirect；不弹冗余 toast 雪崩
- [ ] E5 token 双存储一致（store + localStorage）；refresh token 持久化
- [ ] E6 只读模式: 微专业权限 API 失败 → `_readonly=1` 降级只读提示
- [ ] E7 组件生命周期: 路由切换无"组件已卸载仍 setState"类警告/错误；定时器/轮询正常清理
- [ ] E8 i18n: 中/英切换（若启用）无缺 key

---

## F. 专项维度

- [ ] F1 Console: 全程 console.error/warn 捕获，error=0（warn 记录但不计失败）
- [ ] F2 Network: 全程 XHR/fetch 捕获，API 4xx/5xx=0；静态资源（js/css/img/video）加载失败=0
- [ ] F3 JS 错误: error 事件 + unhandledrejection = 0
- [ ] F4 响应式: STUDENT/TEACHER 关键页 × desktop 1440 / tablet 1024 / mobile 390 三档，无横向滚动/布局崩坏
- [ ] F5 a11y: axe-core 扫描关键页（serious/critical=0）；Tab 焦点可见；aria-label 齐备
- [ ] F6 业务 E2E: 课程生命周期（教师建课→提交→教务审批→发布→学生选课）；练习答题批改；微专业申报→审批；订单→支付状态流转
- [ ] F7 回归: 修复后全量重跑 A-F，0 失败

---

## G. 执行顺序（不可逆）

1. A1-A5 路由巡检（qa-browser-driver × 4 角色）
2. B/C 交互测试（qa-interaction-driver + 自定义深度交互 heredoc）
3. D 接口核对（随 A/B 采集 + qa-e2e-business）
4. E 状态渲染专项（自定义 heredoc：登出/刷新/只读/空数据）
5. F1-F5 专项（console/network 采集 + a11y + responsive）
6. 全部结果汇总 → 问题定位 → 根因分析 → 修复 → F7 全量回归

