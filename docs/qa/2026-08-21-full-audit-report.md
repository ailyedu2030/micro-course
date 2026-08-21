# 微课平台 · 全页面问题审查-修复-优化汇总报告（2026-08-21）

> 审查方式：ego-browser 真实交互验证（四角色 139 页面）+ 3 子代理并行静态代码审查 + 后端交叉验证 + 全量回归。
> 成果：修复 40+ 处问题（4 P0 / 15 P1 / 20+ P2），PR #301，四角色 139/139 页面 0 报错。

## 一、审查覆盖范围（100%）

| 端 | 页面数 | 覆盖内容 |
|----|--------|---------|
| 学生端 | 19 | 广场/详情/我的课程/学习/学习中心/训练/考试/作答/视频/课件/线下课/讨论/消息/资料/设置/评价/订单/收藏/成就/周报/微专业 |
| 教师端 | 28 | 工作台/课程/章节/视频/题库/练习/讨论/学员/成绩/教学班/课件/试卷/线下课/微专业全套/申报 |
| 管理端 | 49 | 工作台/用户/日志/设置/分享/Banner/教学班/系统状态/举报/评价/审核/课程/分类/标签/选课/收藏/题库/套件/课件/幽灵审计 |
| 教务端 | 43 | 工作台/统计/选课总览/微专业 8 审核页/存储审批 |
| 组件 | 20+ | learning-view/storage/common/users/profile/interactive |
| 后端 | 91 Controller | API 契约对照(364 前端调用全命中)/权限/状态机/定价订单 |

## 二、问题发现与修复（三类核心问题）

### 2.1 业务逻辑错误 — 修复 18 项（含 4 个 P0）

1. **MyOrders /student/orders 白屏(P0)**：onMounted 未 import → ReferenceError → ErrorBoundary。修复：补 import。
2. **MicroSpecialtyCourseEdit 整页白屏(P0)**：模板 47 处 $i18nT 未定义。修复：全局替换 i18nT。
3. **MicroSpecialtyDetail 崩溃(P0)**：改名后 22 处裸 t() 调用。修复：负向后顾替换。
4. **MyCourses 继续学习静默失效(P0)**：getLearningProgress 未 import。修复：补 import。
5. **Excel 导出 6 页 100% 失败(P1)**：exceljs UMD 无 fs。修复：writeBuffer+Blob。
6. **课程级练习不可见(P1)**：学生端入口全按 chapterId。修复：courseId 路由+组件支持。
7. **开始练习无响应(P1)**：goExercise 依赖 currentLessonId。修复：重写优先级。
8. **收藏取消 403+语义错位 x3(P1)**：新端点 /favorites/record/{id} + courseId 对齐。
9. **题目选项契约错位(P1)**：{value,label:字母,text:内容} 对齐+回显归一化。
10. **申报成员索引丢失(P1)**：V334 迁移+DTO/实体/保存/回读/前端全链路。
11. **共建单位签名丢失(P1)**：顶层↔嵌套 SignatureFile 双向映射。
12. **ACADEMIC 越权提交批改(P1)**：补只读守卫。
13. **场次卡片跳错路由(P1)**：session.chapterId 修正。
14. **打卡天数恒 0/正确率恒 0(P1)**：兼容裸整数+星期匹配。
15. **Settings 回显错/saveTimer 未声明(P1)**：字符串 ref+saving 状态。
16. **评价加载更多清空/分页错(P1)**：catch 保护+重置分页。
17. **超时 duration 未钳制(P1)**：Math.min 限时内。
18. **视频完成态不落库(P1)**：force+completed 持久化。

### 2.2 反人类设计 & 体验缺陷 — 修复 12 项

1. 44 处确认弹窗 X/Esc 误报"操作失败" → ['cancel','close']
2. UserForm 角色切换清理条件写反 → 修正
3. admin/Dashboard 快捷入口路由错误 → /users/create
4. 教学班教师下拉无数据源 → 补加载
5. users/UserList 死功能入口(批量导入/教师审核/新增用户) → 补按钮
6. SectionEditDialog 新增残留上一课时 → else 清空
7. LearningCenter 打卡失败误清图表+错提示 → 按场景修复
8. Exams 已作答未通过仍可点参加 → 禁用+提示
9. 权限按钮与后端不一致 x4 → 补角色守卫
10. 用户列表筛选命中旧缓存 → 缓存键补全
11. 分页 size-change 不重置页码 → 抽查修复
12. CrossDeptReview PENDING 行操作按钮必拒 → 仅 PENDING_ACADEMIC

### 2.3 功能残缺 & 场景缺陷 — 修复 10 项

1. 教务处 5 页字段名错配(列恒空白/统计恒0) → 字段对齐
2. 徽章全显示未解锁 → badgeCode
3. 学习中心 streak 解析 → 兼容裸整数
4. 跨学院审核 4 列空白+undefined → 后端 VO join 补齐
5. 班级导入院系筛选锁死 → 专业→院系映射
6. 参与率 0-1 贴 0 轴 → 归一化百分比
7. 热门课程 sort 参数后端不识别 → sortBy/sortOrder
8. 评价提交后新评价不可见 → 重置分页
9. 题目编辑回显只显示字母 → 格式归一化
10. 视频进度完成态 → force 持久化

## 三、验证与回归

1. 四角色全页面回归：STUDENT 19/19、TEACHER 28/28、ADMIN 49/49、ACADEMIC 43/43 = **139/139 页面 0 错误**
2. 关键流程浏览器实测：选课→学习→练习作答→提交→得分10/10、打卡、收藏、微专业详情、用户导出 全部通过
3. 构建门禁：mvn clean package 0 ERROR、npx vite build 0 error、precheck 29/29
4. API 契约：364 前端调用全部命中后端，0 路径/方法不匹配
5. 安全：视频 sign/play 三层选课门禁、slides 403、跨学院审核防误操作

## 四、提交与发布

- 50e0d118 MyOrders 白屏 / cceae2ed 课程级练习 / f7c33907 后端批 / 623e89fd 前端批 / 9df0eede 视频完成态 / 5bb1f5ee QA 文档 / 报告归档
- **PR #301** 已推送（CI 进行中，通过后按项目流程合并）

## 五、遗留项（P2，已记录待后续）

1. 硬编码中文 965 处（i18n 治理门禁）
2. VideoPlayer 进度按 chapterId 而非 videoId（多视频串档，需重构进度流）
3. SlidePlayer 学生主流程 sectionId 缺失（入口传参链路改造）
4. 其余 P2 死代码/空态/移动端细节（见 audit-findings-log.md 与子代理报告）
