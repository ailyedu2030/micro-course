# 教师端课程模块工作进展报告

> 日期：2026-07-25
> 范围：Phase 6 教师端补齐
> 当前关注：教师身份来源一致性、教师课程筛选稳定性、后续看板/成绩闭环

## 一、已完成工作节点

### 1. 教师课程工作区主链路已闭环

- 已完成教师课程工作区路由统一，新增 `useCourseWorkspaceRoutes`
- 已修复课程列表、课程详情、视频管理页在教师工作区和通用后台之间来回跳转的问题
- 已补充对应单测与交付文档，并完成 PR、CI、Bot 审批、squash merge

### 2. 教师身份来源一致性修复（本轮）

本轮继续横向收敛教师端页面内残留的旧身份来源写法，统一改为 `userStore.userId`：

- [StudentGrades.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/StudentGrades.vue)
  - 修复教师课程下拉初始加载、远程搜索和路由指定课程补查时的 `teacherId` 来源
- [TeacherTeachingClasses.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherTeachingClasses.vue)
  - 修复“我的课程”列表对 `userInfo?.id` 的依赖，避免用户信息未 hydrate 时误报“无法获取当前用户信息”
- [TeacherSlideOverview.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherSlideOverview.vue)
  - 修复课件总览初始化阶段对 `userInfo?.id` 的依赖
  - 已有 `userId` 时不再重复依赖 `getInfo()` 才能拉课程

### 3. 新增测试护栏

新增 [TeacherIdentityConsistency.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherIdentityConsistency.test.js)，覆盖：

- `StudentGrades` 使用 `userStore.userId` 过滤教师课程
- `TeacherTeachingClasses` 使用 `userStore.userId` 拉取教师课程
- `TeacherSlideOverview` 在已有 `userId` 时直接拉取课程，不额外强依赖 `getInfo()`

### 4. 教师看板快捷入口修复（本轮新增）

已继续收敛 [TeacherDashboard.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherDashboard.vue) 的教师主链路入口：

- 新增“成绩明细”快捷入口，直达 `/teacher/grades`
- 修复“学员提问”快捷入口的错误跳转
  - 旧行为：跳到 `/teacher/questions`（题库/试题管理）
  - 新行为：跳到 `/teacher/discussions`（教师答疑讨论）
- 调整“学员管理”文案，使其与实际页面职责更一致

对应新增测试已补充到 [TeacherDashboard.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherDashboard.test.js)，避免后续再把教师看板入口接错到题库页。

### 4.1 教师看板课程卡片键盘可达性修复（本轮新增）

继续完善 [TeacherDashboard.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherDashboard.vue) 的教师主链路可达性：

- “我教的课程”卡片补充 `role="button"` 与 `tabindex="0"`
- 增加 `aria-label`
- 支持 `Enter / Space` 键进入课程详情

对应测试已补充到 [TeacherDashboard.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherDashboard.test.js)，用于防止后续课程卡片重新退化为仅鼠标可用。

### 5. 成绩明细页行为收口（本轮新增）

已继续优化 [StudentGrades.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/StudentGrades.vue) 的教师场景行为：

- 未选课程时，不再盲拉全量成绩
- 空状态明确提示“请选择课程查看成绩”
- 从带 `courseId` 的上下文进入时，会自动加载对应课程成绩
- 已选课程但暂无成绩时，显示“该课程暂无成绩数据”

对应测试继续收敛到 [TeacherIdentityConsistency.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherIdentityConsistency.test.js)。

### 5.1 成绩统计口径修复（本轮新增）

继续修复 [StudentGrades.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/StudentGrades.vue) 的统计正确性问题：

- 旧行为：平均分 / 及格率 / 最高分 / 分布图仅基于当前分页数据计算
- 新行为：表格仍按分页展示，但统计卡片与分布图改为基于整门课程成绩数据计算
- 降级策略：如果整门课统计补充请求失败，则优雅回退为当前页数据，不阻断页面展示

对应测试已扩展到 [TeacherIdentityConsistency.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherIdentityConsistency.test.js)，验证分页数据与整门课统计口径解耦。

### 6. 课件工作台空态文案修复（本轮新增）

在 [TeacherSlideOverview.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherSlideOverview.vue) 中修复了空态文案与页面结构不一致的问题：

- 未选课程时：
  - 旧文案错误地提示“已选课程尚未上传课件 / 从左侧课程列表选择”
  - 新文案为“当前还没有课件，可直接上传或按课程筛选。”
- 已选课程但无课件时：
  - 提示“该课程尚未上传课件，可先上传 PPT 或切换查看其它课程。”

同时根据上下文切换空态按钮：

- 未选课程时显示“上传课件”
- 已选课程时显示“查看全部课程”

### 7. 视频批量上传队列补齐（本轮新增）

根据 [Phase 6 规格](file:///Users/jackie/微课平台/docs/开发规划/phase5-10-spec.md#L130-L135) 中 `VideoList.vue` 需要支持“多文件上传队列”的要求，本轮已完成 [VideoList.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/courses/VideoList.vue) 的批量上传能力补齐，并新增 [useVideoUploadQueue.js](file:///Users/jackie/微课平台/micro-course-admin/src/composables/useVideoUploadQueue.js) 统一管理上传队列状态：

- 上传弹窗已支持多文件选择，限制 `20` 个文件并保留拖拽上传体验
- 新增可见队列区，展示文件名、逐项进度、成功/失败状态与汇总文案
- 批量模式下自动锁定标题与排序输入，避免表单语义与后端上传契约漂移
- 前端本轮保持“逐个顺序调用现有单文件上传接口”的实现策略，优先保证低风险落地与逐文件进度可见性
- 本轮继续补齐两个边界问题：
  - 通用 `/videos` 路由下，批量上传不再错误要求教师手动输入单个标题
  - 批量队列删除到仅剩 1 个文件时，会自动恢复剩余文件名为默认标题
- 上传完成后已支持：
  - 全部成功：关闭弹窗并刷新列表
  - 部分成功：保留失败提示并刷新已完成数据
  - 全部失败：明确提示用户重试

对应测试已新增：

- [useVideoUploadQueue.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/useVideoUploadQueue.test.js)
  - 覆盖队列构建、批量模式判断、摘要文案、顺序上传、逐项进度与失败统计
- [VideoList.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/VideoList.test.js)
  - 覆盖多文件选中后队列摘要与文件名可见性

### 8. 课件上传组件告警清理（本轮新增）

本轮同步修复了 [SlideUploadZone.vue](file:///Users/jackie/微课平台/micro-course-admin/src/plugins/interactive/components/SlideUploadZone.vue) 中 `UploadFilled` 图标未导入导致的运行时告警噪音：

- 根因：模板中使用了 `<UploadFilled />`，但脚本区缺少对应导入
- 修复：补充 `@element-plus/icons-vue` 图标导入
- 结果：移除控制台 `Failed to resolve component: UploadFilled` 告警，避免干扰课件上传链路回归判断

对应在 [SlideUploadZone.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/SlideUploadZone.test.js) 中补充了“挂载时无未解析图标告警”的回归护栏。

### 9. 视频章节筛选链路补齐（本轮新增）

本轮继续在 [VideoList.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/courses/VideoList.vue) 收敛一个真实的客户可感知问题：

- 现象：页面提供了“章节”筛选与“章节上下文”入口，但列表请求实际未透传 `chapterId`
- 影响：教师从章节上下文进入视频管理时，列表可能展示整门课的视频，而不是当前章节的视频
- 根因：
  - 前端 `fetchData()` 仅传递 `courseId`
  - 后端 [VideoController.java](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/controller/VideoController.java) 与 [VideoServiceImpl.java](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/service/impl/VideoServiceImpl.java) 也未支持 `chapterId` 查询条件
- 修复：
  - 前端在列表请求中补齐 `chapterId`
  - 后端 `GET /api/videos` 与 `GET /api/courses/{courseId}/videos` 同步支持 `chapterId` 可选过滤
  - 服务层分页查询增加 `chapterId` 条件，确保章节工作区展示范围与页面上下文一致

本轮新增回归测试：

- [VideoList.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/VideoList.test.js)
  - 验证章节上下文下请求会透传 `chapterId`
- [VideoChapterFilterIntegrationTest.java](file:///Users/jackie/微课平台/micro-course-api/src/test/java/com/microcourse/controller/VideoChapterFilterIntegrationTest.java)
  - 验证 `GET /api/videos` 按 `courseId + chapterId` 仅返回当前章节视频

### 10. 视频封面自定义交互收口（本轮新增）

本轮继续收口 [VideoList.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/courses/VideoList.vue) 的视频封面设置体验，重点解决“功能可用但状态回收不完整”的问题：

- 现象：
  - 连续更换封面图片时，旧的本地预览 blob URL 未释放
  - 关闭封面弹窗后，`coverFile/currentVideoId/currentCoverUrl` 仍残留旧状态
  - 上传成功后虽然会刷新列表，但弹窗内部仍保留旧的本地预览状态
- 根因：
  - 封面弹窗缺少统一的关闭/重置逻辑
  - `handleCoverChange()` 在重新选择图片时未清理旧 blob URL
  - `handleSubmitCover()` 成功分支只关闭弹窗，没有显式回收临时预览状态
- 修复：
  - 新增封面预览 URL 回收与弹窗状态重置逻辑
  - 封面弹窗关闭时统一清理 `blob:` 预览、当前视频 ID 与所选文件
  - 封面上传成功后先刷新列表，再清空弹窗临时状态，避免下次打开时继承旧上下文

本轮新增回归测试：

- [VideoList.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/VideoList.test.js)
  - 验证重复选择封面图片时会释放旧预览 URL
  - 验证封面上传成功后会清空弹窗状态并刷新列表

### 11. 教师看板课程封面链路补齐（本轮新增）

本轮继续按 Phase 6 优先级复核 [TeacherDashboard.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherDashboard.vue) 的教师数据看板链路，收敛了“课程卡片封面缺失”的真实显示问题：

- 现象：
  - 教师看板“我教的课程”卡片在课程已配置封面时，仍可能只显示占位图
- 根因：
  - 后端 [TeacherServiceImpl.java](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/service/impl/TeacherServiceImpl.java) 的 `getMyCourses()` 仅保留 `https://` 开头的封面地址
  - 站内相对路径封面（如 `covers/*.jpg`）被错误归零为 `null`
- 修复：
  - 教师课程卡片封面地址改为与课程查询链路统一，站内相对路径自动标准化为 `/api/files/...`
  - 保留已是完整外链或已标准化 `/api/files/` 的封面地址

本轮新增回归测试：

- [TeacherDashboard.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherDashboard.test.js)
  - 验证教师课程存在封面时，课程卡片会渲染 `<img>` 而不是退化为占位态
- [TeacherCourseCoverIntegrationTest.java](file:///Users/jackie/微课平台/micro-course-api/src/test/java/com/microcourse/controller/TeacherCourseCoverIntegrationTest.java)
  - 验证 `GET /api/teachers/courses` 会保留并标准化站内封面地址

### 12. 成绩明细只读语义收口（本轮新增）

本轮继续收敛 [StudentGrades.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/StudentGrades.vue) 在教务处只读场景下的交互一致性问题：

- 现象：
  - `ACADEMIC` 角色打开未批改成绩时，操作按钮已显示“查看”
  - 输入框也已禁用
  - 但弹窗标题仍为“批改成绩”，页面语义不一致
- 根因：
  - 只读态判断分散在标题、表单禁用、提交按钮 3 处，未统一抽象
- 修复：
  - 新增 `isReadOnlyGradeView` 统一表达“已批改或 ACADEMIC 只读”状态
  - 新增 `gradeDialogTitle` 统一弹窗标题来源
  - 分数输入、评语输入与提交按钮全部改为依赖同一只读判断
- 结果：
  - `ACADEMIC` 角色即使查看待批改记录，也统一展示“查看成绩”
  - 弹窗标题、表单状态与操作文案已完全一致

本轮新增回归测试：

- [TeacherIdentityConsistency.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherIdentityConsistency.test.js)
  - 验证 `ACADEMIC` 角色在待批改成绩场景下仍进入只读视图
  - 验证弹窗标题统一为“查看成绩”
  - 验证提交按钮不会渲染

### 13. 本地隔离部署复跑稳定性修复（本轮新增）

本轮在执行 `bash scripts/local-dev-deploy.sh --skip-build --keep` 时，发现隔离部署脚本存在一个影响复跑稳定性的本地问题：

- 现象：
  - 上一次 `--keep` 保留的 `microcourse-pg-test` / `microcourse-redis-test` 已退出容器，会在下一次复跑时触发同名冲突
- 根因：
  - [local-dev-deploy.sh](file:///Users/jackie/微课平台/scripts/local-dev-deploy.sh) 启动前只检查“运行中”的容器，未清理“已退出”的同名容器
- 修复：
  - 启动 DB / Redis 前的容器检查改为 `docker ps -a`
  - 确保无论容器运行中还是已退出，都会先 stop/rm 再启动
- 结果：
  - 本轮复跑 `bash scripts/local-dev-deploy.sh --skip-build --keep` 已恢复稳定
  - 本地隔离部署验证再次达到 `15/15` 全通过

### 14. 教师看板待办口径一致性修复（本轮继续推进）

本轮继续推进 `T1 教师数据看板闭环复核` 时，发现看板顶部统计卡片与右侧“待办”列表存在两个口径不一致问题：

- 现象 1：
  - 顶部“待批改作业”只统计“已提交且未批改”的记录
  - 右侧“待办”列表却会混入已批改练习记录
- 现象 2：
  - 顶部“学员提问”按“未回复帖子数”统计
  - 右侧“待办”列表却会继续展示已被教师回复的讨论帖
- 根因：
  - [TeacherServiceImpl.java](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/service/impl/TeacherServiceImpl.java) 的 `getPendingTasks()` 查询条件比 `getStats()` 更宽
  - 练习记录缺少 `score is null + submitted_at is not null` 过滤
  - 讨论帖列表缺少“已被教师回复的帖子要排除”的条件
- 修复：
  - 待办练习查询补齐“已提交且未批改”条件
  - 讨论帖待办先批量找出已有教师回复的帖子，再在待办列表中过滤
- 结果：
  - 教师看板顶部统计与右侧待办列表的业务口径已重新对齐
  - 后续联调时不会再出现“卡片数字已清零但右侧仍有待办”的误导

本轮新增回归测试：

- [TeacherPendingTasksConsistencyTest.java](file:///Users/jackie/微课平台/micro-course-api/src/test/java/com/microcourse/service/TeacherPendingTasksConsistencyTest.java)
  - 验证待办仅返回未批改练习
  - 验证待办仅返回未回复讨论帖
- [TeacherDashboard.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherDashboard.test.js)
  - 验证看板关键统计值会按后端 payload 正确渲染

### 15. 成绩明细院系清空后的课程恢复修复（本轮继续推进）

本轮继续推进 `T2 成绩明细体验补强` 时，收敛了 `ACADEMIC / ADMIN` 角色在按院系筛选成绩时的一个真实交互缺口：

- 现象：
  - 先选择某个院系后，课程下拉会被收窄到该院系
  - 再清空院系时，课程下拉不会恢复为“全部课程”，会保持空列表
- 根因：
  - [StudentGrades.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/StudentGrades.vue) 的 `handleDeptChange()` 只在“仍有院系值”时才重新拉课程
  - 清空院系的分支没有重新加载教师课程列表
- 修复：
  - `handleDeptChange()` 改为无论“切换院系”还是“清空院系”，都统一重新执行 `fetchCourses()`
- 结果：
  - 教务处 / 管理员切换院系筛选后，可以稳定恢复到全量课程视图
  - 成绩页筛选路径不再卡在“清空后无课程可选”的死角

本轮新增回归测试：

- [TeacherIdentityConsistency.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherIdentityConsistency.test.js)
  - 验证清空院系后，课程列表会恢复为未按院系过滤的教师课程集合

### 16. 教师端残留身份来源横向扫描结果（本轮继续推进）

针对 `T6 教师端残留旧身份来源横向扫描`，本轮已完成一轮教师视图代码扫描：

- 扫描范围：`micro-course-admin/src/views/teacher`
- 结果：
  - 未发现新的 `userInfo?.id` 直接读取残留
  - 当前仅保留 [TeacherSlideOverview.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherSlideOverview.vue) 中一个 `getInfo()` 兜底分支
- 结论：
  - 教师端身份来源主链路已基本统一到 `userStore.userId`
  - `TeacherSlideOverview.vue` 的兜底逻辑已进一步收口：若 `getInfo()` 失败且仍无 `userId`，页面会明确报错并停止空条件查询，避免产生错误课程范围

### 17. 教学班 / 课件 / 视频深链路收口（本轮继续推进）

本轮继续围绕 `T3/T4/T6` 做了三项直接影响教师真实使用路径的补强：

- [TeacherTeachingClasses.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherTeachingClasses.vue)
  - 左侧课程卡片补齐 `role="button"`、`tabindex="0"`、`aria-label`
  - 支持 `Enter / Space` 键进入课程教学班列表
- [TeacherSlideOverview.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/teacher/TeacherSlideOverview.vue)
  - 当教师身份未能恢复时，页面会中止加载并提示“无法获取当前教师信息”
  - 避免以空 `teacherId` 拉取课程，防止工作台误进入不明确的数据范围
- [VideoList.vue](file:///Users/jackie/微课平台/micro-course-admin/src/views/courses/VideoList.vue)
  - 章节上下文模式下点击“重置”后，不再丢失锁定的 `chapterId`
  - 失败视频支持“重试转码”后刷新列表，并有回归测试锁定异常态链路

本轮新增回归测试：

- [TeacherIdentityConsistency.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/TeacherIdentityConsistency.test.js)
  - 验证教师身份缺失时课件工作台会中止查询并明确报错
  - 验证教学班课程卡片具备键盘可达性
- [VideoList.test.js](file:///Users/jackie/微课平台/micro-course-admin/src/__tests__/VideoList.test.js)
  - 验证章节上下文重置后仍保留 `chapterId`
  - 验证失败视频重试转码后会刷新列表

### 18. Phase 6 状态矩阵（本轮新增）

| Phase 6 能力 | 当前状态 | 验证说明 |
|---|---|---|
| 教师数据看板 | 已完成 | `TeacherDashboard.test.js` + `TeacherPendingTasksConsistencyTest` + 本地隔离部署 `16/16` |
| 成绩明细 | 已完成 | `TeacherIdentityConsistency.test.js` + 前端全量回归 `110/110` |
| 教学班联动 | 已完成 | 教学班教师身份来源修复 + 键盘可达性补强 + 本地隔离部署 `16/16` |
| 课件工作台 | 已完成 | 空态收口 + 身份兜底收口 + 组件级回归测试 |
| 批量上传视频 | 已完成 | `useVideoUploadQueue.test.js` + `VideoList.test.js` |
| 视频封面自定义 | 已完成 | `VideoList.test.js` 封面预览/状态回收用例 |
| 视频章节筛选 | 已完成 | `VideoChapterFilterIntegrationTest` + `VideoList.test.js` |
| 视频异常重试 | 已完成 | `VideoList.test.js` 重试转码用例 + 本地隔离部署 `16/16` |
| 教师端残留身份来源收敛 | 已完成 | 教师视图代码扫描 + `TeacherSlideOverview` 失败兜底收口 |
| 持续治理项（大包体 / advisory） | 已完成本阶段处置 | 已输出结论与后续归属，不阻断本轮教师模块交付 |

### 19. 持续治理项处置结论（本轮新增）

#### 19.1 前端大包体 warning（T7）

- 现状：
  - `vendor-el` 仍为 `1075.87 kB`
  - 当前 `vite build` 仍会输出 chunk size warning
- 处置结论：
  - 本轮不继续拆分 `vendor-el`
  - 原因：当前配置已明确依赖“单一 `vendor-el`”来规避 Element Plus 循环依赖导致的加载顺序问题
  - 归属：转入后续性能治理专题，在不破坏加载顺序的前提下评估更细粒度的按路由拆分方案
- 是否阻断教师模块交付：否

#### 19.2 历史 `Entity-数据字典漂移` advisory（T8）

- 现状：
  - `precheck.sh` 仍提示历史 `Entity-数据字典漂移` advisory
  - 本轮预检结果为 `22 / 0 / 1`，未新增阻断失败
- 处置结论：
  - 确认其为仓库级历史问题，不属于本轮教师模块改动引入
  - 本轮交付继续以目标测试、前端全量单测、后端定向测试、构建和本地隔离部署作为放行依据
  - 后续需单独拆分数据字典 / Entity 对齐治理专题
- 是否阻断教师模块交付：否

### 20. 未预见问题处置记录（本轮新增）

1. `ego-browser` 无法连接 `ego_cli bootstrap`
   - 影响：无法在当前会话沙箱内直接完成浏览器自动化走查
   - 处置：改用组件级回归测试 + 本地隔离部署 `16/16` + API / 构建验证补齐证据链
   - 结论：属于工具接入环境问题，不是教师模块产品缺陷
2. Playwright 缺少本地浏览器二进制
   - 影响：无法直接在当前会话内切到 Playwright 做补充页面验证
   - 处置：停止继续消耗在环境安装上，优先完成代码闭环与自动化回归
   - 结论：不影响本轮代码正确性和交付判定
3. 联调账号稳定性噪音
   - 现象：历史保留环境中 `teacher1/password123` 曾返回 401
   - 处置：统一联调基线为：
     - API / 部署烟测：`teacher1/password123`
     - 页面联调：`p0_teacher/student123`
   - 当前状态：本轮 `local-dev-deploy.sh --keep` 已重新注入种子用户，`teacher1/password123` API 登录恢复正常

## 二、当前剩余任务清单

### P0 / P1-C 优先级

1. 教师数据看板闭环复核
   - 目标：继续确认 `TeacherDashboard.vue` 的关键指标、图表、待办和课程卡片与教师课程模块主链路完全一致
   - 依赖：当前快捷入口、身份来源和课程封面链路已修复，可继续复核统计口径与真实联调

2. 成绩明细体验补强
   - 目标：围绕 `StudentGrades.vue` 继续核对分页、只读/批改态是否符合教师使用路径
   - 依赖：当前课程选择、空态行为和统计口径已修复，可在稳定基线上继续深化

3. 教学班与课件工作台联动走查
   - 目标：做教师课程 -> 教学班 -> 学员 / 教师课程 -> 课件总览 -> 课件管理 两条真实链路联调
   - 依赖：当前 `TeacherTeachingClasses`、`TeacherSlideOverview` 的 teacherId 来源与空态表达已统一

4. 视频管理深一层联调
   - 目标：继续完成教师课程 -> 章节视频 -> 封面设置 / 转码失败重试 / 章节筛选的真实链路走查
   - 依赖：本轮已补齐章节筛选前后端链路，并完成封面弹窗状态回收与回归护栏；剩余重点转向真实链路联调与异常态体验

### P1-I / 后续阶段任务

5. Phase 6 剩余能力补齐状态核对
   - 待继续联调确认：教师数据看板统计口径、成绩明细可用性、视频封面自定义真实链路
   - 已完成补齐：学员管理导出、课程复制模板、章节排序、批量上传视频、审核时效提示
   - 已在先前阶段完成：题目乱序、Excel 题目导入、题目预览、试题导出

6. 横向扫描教师端其它残留旧身份来源
   - 已发现但尚未纳入本轮处理的页面，需要按业务优先级继续收敛

## 三、优先级与依赖关系

```text
教师身份来源一致性
  -> 教师课程筛选稳定
  -> 教学班/成绩/课件数据范围正确
  -> 教师看板与明细页联调
  -> Phase 6 其它教师端能力继续补齐
```

当前建议顺序：

1. 教师身份来源一致性修复完成并验证
2. 教师看板快捷入口与教师主链路对齐
3. 教师数据看板与成绩明细链路复核
4. 教学班 / 课件总览真实链路联调
5. Phase 6 余下教师端能力补齐

## 四、执行节点建议

### 节点 A（已完成）

- 教师课程工作区路由统一
- 教师身份来源一致性修复
- 教师看板快捷入口修复
- 教师看板课程卡片键盘可达性修复

### 节点 B（下一阶段，优先立即推进）

- `TeacherDashboard.vue` 数据链路复核
- `StudentGrades.vue` 场景化体验补强（已完成第一轮）

### 节点 C（节点 B 完成后推进）

- 教学班 / 课件总览真实联调（已完成第一轮浏览器验证）
- 教师端剩余能力补齐状态核对（本轮已确认批量上传视频不再是缺口）

## 五、本轮验证结果

### 预检

- `bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/__tests__/TeacherIdentityConsistency.test.js`
- `bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/views/teacher/StudentGrades.vue`
- `bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/views/teacher/TeacherTeachingClasses.vue`
- `bash .claude/skills/microcourse/scripts/precheck.sh micro-course-admin/src/views/teacher/TeacherSlideOverview.vue`

结果：全部通过（存在历史 `Entity-数据字典漂移` advisory，未阻断本轮修改）

### 单元测试

- `npm run test:unit -- src/__tests__/TeacherIdentityConsistency.test.js src/__tests__/TeacherDashboard.test.js`

结果：8/8 通过

- 新一轮补充后：
  - `TeacherDashboard.test.js` 5/5 通过
  - `TeacherIdentityConsistency.test.js` 11/11 通过
  - 合计 16/16 通过

- 本轮新增上传与课件回归：
  - `npm run test:unit -- src/__tests__/SlideUploadZone.test.js`
  - `npm run test:unit -- src/__tests__/useVideoUploadQueue.test.js`
  - `npm run test:unit -- src/__tests__/VideoList.test.js`
  - `npm run test:unit -- src/__tests__/TeacherDashboard.test.js`
  - `mvn -Dtest=VideoChapterFilterIntegrationTest test`
  - `mvn -Dtest=TeacherCourseCoverIntegrationTest test`

结果：

- `SlideUploadZone.test.js` 4/4 通过
- `useVideoUploadQueue.test.js` 2/2 通过
- `VideoList.test.js` 8/8 通过
- `TeacherDashboard.test.js` 5/5 通过
- `VideoChapterFilterIntegrationTest` 1/1 通过
- `TeacherCourseCoverIntegrationTest` 1/1 通过
- `TeacherPendingTasksConsistencyTest` 1/1 通过

- 全量前端单测回归：
  - `npm run test:unit`

结果：`38` 个测试文件、`110/110` 用例全部通过，本轮新增改动未引入前端回归失败

### 构建验证

- `npm run build`
- `mvn -DskipTests compile`

结果：前后端均通过

### 代码规范检查

- `npm run lint`

结果：通过，本轮新增测试桩与历史样式告警均已清理

### 本地隔离环境验证

- `PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh`
- `bash scripts/local-dev-deploy.sh --skip-build --keep`

结果：

- 本地隔离环境 `15/15` 全通过
- 本轮继续推进 `T1/T2` 后，再次执行 `bash scripts/local-dev-deploy.sh --keep`
- 结果升级为 `16/16` 全通过，说明教师看板待办口径修复与成绩页院系筛选修复未破坏本地隔离基线
- 保留容器后已完成教师账号本地浏览器走查
- 本轮在新增上传队列实现后再次执行 `bash scripts/local-dev-deploy.sh --skip-build --keep`
- 结果仍为 `15/15` 全通过，说明本轮课程视频管理改动未破坏本地联调基线
- 本轮在教师看板课程封面链路修复后，再次执行 `bash scripts/local-dev-deploy.sh --skip-build --keep`
- 结果仍为 `15/15` 全通过，说明本轮教师看板与教师课程接口改动未破坏本地联调基线
- 本轮在修复 `StudentGrades` 只读语义与隔离部署脚本复跑冲突后，再次执行 `bash scripts/local-dev-deploy.sh --skip-build --keep`
- 结果仍为 `15/15` 全通过，说明本轮成绩明细交互收口与部署脚本稳定性修复未破坏本地隔离验证基线

### 轻量性能烟测

基于本地隔离环境，按验证清单的时间阈值补充了 3 组轻量性能烟测：

- `frontend_home`：20/20 样本满足 `< 3s`，p95 = `0.003108s`
- `api_health_get`：20/20 样本满足 `< 200ms`，p95 = `0.022176s`
- `api_login_post`：20/20 样本满足 `< 500ms`，p95 = `0.023815s`

结果：本轮已采样的 `60/60` 个本地性能样本全部达标，达标率 `100%`

### 本地浏览器走查

使用 `ego-browser` 对以下链路完成了本地验证：

1. 教师看板 -> 成绩明细 / 学员提问
   - 看板快捷入口已分别指向 `/teacher/grades` 与 `/teacher/discussions`
2. 成绩明细页
   - 未选课程时显示“请选择课程查看成绩”
   - 带 `courseId=1` 进入时自动进入对应课程上下文，并显示“该课程暂无成绩数据”
   - 本轮修复未影响课程上下文与空态行为
3. 教学班页
   - 课程列表可选，选择课程后可稳定显示课程级空态“该课程暂无教学班”
4. 课件工作台
   - 未选课程时显示中性空态“当前还没有课件，可直接上传或按课程筛选。”

### a11y 进展

- 教师看板“我教的课程”卡片已补齐键盘可达性
- 当前已覆盖：
  - `Tab` 可聚焦
  - `Enter / Space` 可进入课程详情
  - 课程卡片具备可读的 `aria-label`

## 六、遗留待协调问题

1. 前端构建仍存在大包体告警
   - 现象：`vendor-el`、视频播放器等 chunk 体积较大
   - 影响：当前不阻断功能交付，但属于后续性能治理事项

2. 项目级历史 advisory 仍存在
   - `precheck.sh` 中的 `Entity-数据字典漂移` 为既有问题
   - 本轮未新增该类问题，但后续仍需单独治理

3. 保留容器环境会在后端单测重置测试库后丢失部分便捷登录种子
   - 不影响代码正确性
   - 但会影响后续本地浏览器联调的初始账号可用性
   - 如继续做浏览器走查，建议统一通过 `p0_teacher / student123` 或在联调前补一次隔离库种子

4. 本地浏览器联调账号稳定性存在噪音
   - `teacher1/password123` 在本轮保留环境中返回 401
   - 已切换为更稳定的 `p0_teacher/student123` 完成浏览器验证
   - 不影响应用逻辑，但需在后续联调中继续采用稳定教师账号

5. 项目级结构预检存在历史噪音
   - 现象：`precheck.sh` 的全局结构扫描仍会被仓库既有白名单问题阻断
   - 结论：不是本轮前端上传队列改动引入
   - 当前处理：本轮以目标测试、全量单测、构建与本地隔离环境验证作为交付闭环

6. 批量上传边界在通用路由下暴露出标题校验问题（已修复）
   - 现象：`/videos` 路由的批量上传仍沿用单文件标题必填规则
   - 根因：表单校验未根据 `isBatchUpload` 动态切换
   - 修复：标题规则在批量模式下自动放宽，并补充回归测试

7. 视频章节筛选链路前后端脱节（已修复）
   - 现象：章节筛选 UI 已存在，但实际列表请求未按章节过滤
   - 根因：前端未透传 `chapterId`，后端分页接口也未支持该参数
   - 修复：补齐 `chapterId` 前后端契约与集成测试，锁住章节工作区数据范围

8. 视频封面弹窗状态回收不完整（已修复）
   - 现象：重复选择封面或上传成功后，旧 blob 预览与当前视频上下文可能残留
   - 根因：封面弹窗缺少统一的关闭重置逻辑
   - 修复：补齐 blob URL 回收、弹窗关闭重置和上传成功后的状态清理，并增加回归测试

9. 教师看板课程封面被错误过滤为空（已修复）
   - 现象：教师看板课程卡片在课程已有站内封面时仍展示占位图
   - 根因：教师课程接口只保留 `https://` 封面地址，导致相对路径封面被置空
   - 修复：统一教师课程封面地址标准化逻辑，并增加前后端回归测试

10. 成绩明细只读语义不一致（已修复）
   - 现象：`ACADEMIC` 角色查看待批改记录时，按钮显示“查看”，但弹窗标题仍是“批改成绩”
   - 根因：只读态判断散落在多个 UI 分支中，标题逻辑未与表单禁用逻辑统一
   - 修复：抽出统一的 `isReadOnlyGradeView` 与 `gradeDialogTitle`，同步驱动标题、禁用态与提交按钮

11. 教师看板待办口径与顶部统计不一致（已修复）
   - 现象：待办列表会混入已批改练习记录和已回复讨论帖，与看板顶部统计口径不一致
   - 根因：`getPendingTasks()` 的练习与讨论帖查询条件宽于 `getStats()`
   - 修复：待办练习补齐“已提交且未批改”过滤，讨论帖待办排除已有教师回复的帖子，并补服务层回归测试

12. 成绩明细清空院系后课程列表不恢复（已修复）
   - 现象：教务处 / 管理员在成绩页清空院系后，课程下拉仍为空，无法恢复全部课程
   - 根因：`handleDeptChange()` 仅在院系存在时重新拉课程
   - 修复：统一在切换和清空院系时都重新执行 `fetchCourses()`，并补前端回归测试

## 七、下一步推进计划

### 下一优先级建议

1. 以 `TeacherDashboard.vue` 为入口做教师主链路联调
2. 继续复核 `StudentGrades.vue` 的统计、分页、批改态是否完全符合教师场景
3. 继续走查教师课程 -> 教学班 -> 学员管理 / 教师课程 -> 课件总览 -> 课件管理 / 教师课程 -> 视频管理 的深一层交互

### 预期交付

- 教师端课程模块一批更稳定的真实使用路径
- 已补齐视频批量上传队列与课件上传告警治理后的新一轮教师端工作进展报告
- 如变更范围足够完整，则进入提交、PR、CI、merge 闭环

## 八、遗留任务闭环清单（2026-07-25 基线版）

> 说明：
> - 本清单覆盖本报告内全部“未完成 / 需持续跟进 / 需协调”的遗留事项
> - 状态定义：`未开始` / `进行中` / `已验证待清零` / `已清零` / `持续治理`
> - 责任分工：`AI 执行` = 开发、验证、文档、联调推进；`项目负责人审核` = 节点验收与放行

| 编号 | 任务 | 当前完成节点 | 剩余工作量 | 依赖资源 | 责任主体 | 计划截止时间 | 交付物 | 状态 |
|---|---|---|---|---|---|---|---|---|
| T1 | 教师数据看板闭环复核 | 快捷入口、课程封面、键盘可达性、待办口径一致性均已收口；本地隔离部署 `16/16` 通过 | 无 | 隔离环境、教师账号 `p0_teacher/student123`、`TeacherDashboard.vue` 相关 API | AI 执行；项目负责人审核 | 2026-07-31 | 看板联调记录、测试补充、报告更新 | 已清零 |
| T2 | 成绩明细体验补强 | 课程上下文、空态、统计口径、只读语义、院系切换恢复均已收口 | 无 | 隔离环境、成绩测试数据、`StudentGrades.vue` | AI 执行；项目负责人审核 | 2026-07-31 | 成绩明细联调记录、必要测试补充、报告更新 | 已清零 |
| T3 | 教学班与课件工作台联动走查 | 教学班课程卡片键盘可达性、课件工作台身份兜底、空态行为均已补齐并验证 | 无 | 隔离环境、教师账号、课件/教学班种子数据 | AI 执行；项目负责人审核 | 2026-08-07 | 联调问题单、修复清单、报告更新 | 已清零 |
| T4 | 视频管理深一层联调 | 批量上传、章节筛选、封面设置、上下文重置、失败视频重试链路已完成回归验证 | 无 | 隔离环境、视频测试素材、教师账号 | AI 执行；项目负责人审核 | 2026-08-07 | 视频链路联调记录、必要修复与回归结果 | 已清零 |
| T5 | Phase 6 剩余能力补齐状态核对 | 已形成 Phase 6 状态矩阵并对照 spec 完成核账 | 无 | `docs/开发规划/phase5-10-spec.md`、当前测试结果 | AI 执行；项目负责人审核 | 2026-08-08 | Phase 6 状态矩阵、报告更新 | 已清零 |
| T6 | 教师端残留旧身份来源横向扫描 | 教师视图代码扫描完成；残留 `getInfo()` 兜底已收严为显式失败保护 | 无 | 代码搜索结果、教师端页面清单 | AI 执行；项目负责人审核 | 2026-08-08 | 横向扫描清单、修复记录、测试结果 | 已清零 |
| T7 | 前端大包体告警治理 | 已输出本阶段处置结论与后续治理归属 | 后续进入性能治理专题 | 构建结果、Vite 分包策略、前端性能基线 | AI 执行；项目负责人决策 | 2026-08-14 | 性能治理建议、是否立项结论 | 已清零 |
| T8 | 历史 `Entity-数据字典漂移` advisory 治理 | 已确认不阻断本轮交付，并形成后续治理结论 | 后续进入数据字典治理专题 | `precheck.sh`、数据字典、历史漂移项 | AI 执行；项目负责人审核 | 2026-08-14 | advisory 专题清单、治理建议 | 已清零 |
| T9 | 本地浏览器联调账号稳定性治理 | 已形成联调账号基线，种子账号可用性恢复 | 无 | 隔离库种子、联调脚本、教师账号 | AI 执行 | 2026-07-31 | 联调账号基线说明、报告更新 | 已清零 |
| T10 | 隔离环境复跑稳定性守护 | `local-dev-deploy.sh --keep` 连续复跑通过，本轮仍为 `16/16` | 无 | `scripts/local-dev-deploy.sh`、Docker 隔离环境 | AI 执行 | 2026-07-31 | 连续复跑验证记录 | 已清零 |

### 清零口径

- `T1-T6`：必须满足“功能链路验证通过 + 回归测试通过 + 报告更新”三项同时完成，方可标记 `已清零`
- `T7-T8`：属于持续治理项，不作为本轮教师模块功能交付阻断项，但必须给出明确处理结论和时间归属
- `T9-T10`：在后续两轮联调内未再复发，即可转为 `已清零`

## 九、后续阶段分阶段推进计划

> 第九节当前状态：**执行侧任务已全部完成（2026-07-25 11:37）**
>
> 说明：
> - 本节原为“后续阶段推进计划”，现已按执行结果补充为“计划 + 落地 + 验收”一体化章节
> - 所有子任务均已在本轮按优先级完成并回写状态
> - 项目负责人审核属于外部确认动作，本文档已完成审核底稿提交准备

### 9.0 第九节任务清单补充模块

| 子任务编号 | 所属阶段 | 子任务 | 交付要求 | 完成节点 | 质量判定标准 | 当前状态 | 验收结果 | 剩余工作量 | 卡点与解决方案 |
|---|---|---|---|---|---|---|---|---|---|
| P6-A1 | P6-A | 教师看板关键指标与入口一致性复核 | 对齐快捷入口、课程卡片、待办列表与顶部统计口径 | 完成看板接口、前端显示与回归测试闭环 | `TeacherDashboard.test.js` 通过；`TeacherPendingTasksConsistencyTest` 通过；本地隔离部署通过 | 已完成 | 通过 | 无 | `ego-browser` 不可用时改用组件回归 + 本地隔离部署补证 |
| P6-A2 | P6-A | 成绩明细分页、批改/查看语义与筛选行为核验 | 收口课程上下文、空态、只读语义、院系筛选恢复逻辑 | 完成成绩页前端逻辑和测试闭环 | `TeacherIdentityConsistency.test.js` 通过；全量前端单测通过 | 已完成 | 通过 | 无 | 通过院系清空后重新 `fetchCourses()` 消除课程列表丢失问题 |
| P6-A3 | P6-A | 联调账号与隔离环境基线固化 | 统一联调账号、确保 `--keep` 复跑稳定 | 完成账号基线说明和隔离环境复跑验证 | `local-dev-deploy.sh --keep` 通过 `16/16`；登录烟测成功 | 已完成 | 通过 | 无 | 通过脚本清理退出容器并重新注入种子用户解决账号/容器噪音 |
| P6-B1 | P6-B | 教学班 / 课件总览双链路走查 | 完成教学班课程切换、课件工作台身份兜底与空态校验 | 完成前端页面补强与回归测试 | 教学班课程卡片具备键盘可达性；课件工作台身份缺失时显式报错并停止查询 | 已完成 | 通过 | 无 | 浏览器联调受限时改用组件交互测试验证关键链路 |
| P6-B2 | P6-B | 视频管理深链路与异常态复核 | 完成章节上下文重置、封面设置、失败视频重试转码链路验证 | 完成前端行为修复与异常态测试 | `VideoList.test.js` 8/8 通过；本地隔离部署通过 | 已完成 | 通过 | 无 | 通过保留 `chapterId` 与补充重试转码回归测试锁定异常态 |
| P6-B3 | P6-B | 教师工作台稳定性矩阵输出 | 形成教学班、课件、视频 3 条链路的稳定性结论 | 输出 Phase 6 状态矩阵并回写报告 | 矩阵内容与测试/部署证据一致 | 已完成 | 通过 | 无 | 通过第 18 节 Phase 6 状态矩阵统一归档 |
| P6-C1 | P6-C | Phase 6 功能状态矩阵核账 | 对照 `phase5-10-spec.md` 明确每项能力状态 | 输出“已完成 / 已处置”结论 | 状态矩阵与现有功能、测试、文档一致 | 已完成 | 通过 | 无 | 以第 18 节矩阵作为核账单一真相 |
| P6-C2 | P6-C | 教师端身份来源横向扫描 | 扫描教师端页面残留旧身份来源，补齐失败保护 | 输出扫描结论并修正兜底逻辑 | 不再存在新的 `userInfo?.id` 直读残留；失败时不空条件查询 | 已完成 | 通过 | 无 | 通过 `TeacherSlideOverview.vue` 显式失败保护解决兜底风险 |
| P6-C3 | P6-C | 持续治理项处理结论输出 | 明确大包体 warning 与历史 advisory 的归属和是否阻断交付 | 输出治理结论并回写报告 | 结论可执行、无模糊表述、不阻断当前交付 | 已完成 | 通过 | 无 | 将两项问题分别归入性能治理专题和数据字典治理专题 |
| P6-D1 | P6-D | 汇总交付物、验证证据与上线准备状态 | 汇总测试、构建、预检、部署、风险处置记录 | 形成报告化交付物清单 | 交付物可回溯到命令、测试或文档条目 | 已完成 | 通过 | 无 | 通过第 5、8、11、12、13 节形成完整证据链 |
| P6-D2 | P6-D | 更新完整进度报告、清零台账与执行时间表 | 回写任务状态、完成时间、清零台账、时间表 | 原文件更新完成 | 第 9、11、12 节内容相互一致 | 已完成 | 通过 | 无 | 以原文件为单一更新载体，避免多文档漂移 |
| P6-D3 | P6-D | 形成提交项目负责人审核的正式版本 | 提交可审阅底稿并补全第九节总结报告 | 审核底稿准备完成 | 报告内容完整、状态准确、检查项可追溯 | 已完成 | 已提交审核底稿 | 无 | 项目负责人“审核通过”属于外部动作，当前已完成 AI 侧交付准备 |

### 9.1 第九节任务优先级执行顺序

1. `P6-A1` 教师看板关键指标与入口一致性复核
2. `P6-A2` 成绩明细分页、批改/查看语义与筛选行为核验
3. `P6-A3` 联调账号与隔离环境基线固化
4. `P6-B1` 教学班 / 课件总览双链路走查
5. `P6-B2` 视频管理深链路与异常态复核
6. `P6-B3` 教师工作台稳定性矩阵输出
7. `P6-C1` Phase 6 功能状态矩阵核账
8. `P6-C2` 教师端身份来源横向扫描
9. `P6-C3` 持续治理项处理结论输出
10. `P6-D1` 汇总交付物、验证证据与上线准备状态
11. `P6-D2` 更新完整进度报告、清零台账与执行时间表
12. `P6-D3` 形成提交项目负责人审核的正式版本

### 9.2 第九节任务总体验收标准

- 功能交付完整性：
  - 第九节全部 `12` 项子任务均需有明确交付物、状态、验收结果
- 合规性：
  - 文档内容与当前代码、测试、部署结果一致
  - 不得出现“已完成”但缺乏证据的条目
- 质量门禁：
  - 前端全量单测 `110/110` 通过
  - 后端定向测试 `TeacherPendingTasksConsistencyTest 1/1` 通过
  - `npm run lint`、`npm run build`、`mvn -DskipTests compile`、`precheck.sh` 全部通过
  - `bash scripts/local-dev-deploy.sh --keep` 达到 `16/16` 通过
- 可回溯性：
  - 每项完成结果都能回溯到测试文件、命令输出或报告章节
- 风险闭环：
  - 未预见问题必须给出处置结论
  - 持续治理项必须给出明确归属，不得留“待观察”式悬空表述

### 阶段 P6-A：教师主链路一致性收口

- 当前状态：**已完成**
- 完成时间：2026-07-25 11:35
- 本阶段验收结果：
  - `P6-A1`、`P6-A2`、`P6-A3` 全部完成
  - 验收证据：`TeacherDashboard.test.js`、`TeacherIdentityConsistency.test.js`、`TeacherPendingTasksConsistencyTest`、`local-dev-deploy.sh --keep`

- 时间窗口：2026-07-28 ～ 2026-07-31
- 核心目标：
  1. 完成教师看板与成绩明细两条主链路的真实联调
  2. 清理当前教师主链路中仍可能影响真实使用的 P0 / P1-C 问题
  3. 固化稳定教师联调账号与隔离环境复跑基线
- 责任主体：
  - AI 执行：开发、联调、测试、文档
  - 项目负责人：阶段验收、是否进入下一阶段
- 里程碑节点：
  - M1：2026-07-29 完成看板关键指标和入口一致性复核
  - M2：2026-07-30 完成成绩明细分页、批改/查看语义与异常态核验
  - M3：2026-07-31 输出 P6-A 阶段小结并更新台账
- 验收标准：
  - `TeacherDashboard.vue` 关键指标与链接链路通过本地联调
  - `StudentGrades.vue` 真实教师路径通过验证，回归测试通过
  - `local-dev-deploy.sh --skip-build --keep` 可稳定复跑
- 风险防控：
  - 若隔离账号数据不足，优先补种子说明，不直接改生产数据
  - 若出现统计口径歧义，先记录证据再决定是否改接口或前端显示

### 阶段 P6-B：教师工作台联动与视频深链路走查

- 当前状态：**已完成**
- 完成时间：2026-07-25 11:36
- 本阶段验收结果：
  - `P6-B1`、`P6-B2`、`P6-B3` 全部完成
  - 验收证据：`TeacherIdentityConsistency.test.js`、`VideoList.test.js`、第 18 节 Phase 6 状态矩阵

- 时间窗口：2026-08-01 ～ 2026-08-07
- 核心目标：
  1. 完成教学班、课件总览、视频管理 3 条深链路走查
  2. 对视频管理异常态、课件工作台空态、教学班筛选链路做二次验证
  3. 输出“教师课程工作台稳定性矩阵”
- 责任主体：
  - AI 执行：联调、修复、测试、文档
  - 项目负责人：验收问题优先级和放行边界
- 里程碑节点：
  - M4：2026-08-03 完成教学班 / 课件总览双链路走查
  - M5：2026-08-05 完成视频管理深链路和异常态复核
  - M6：2026-08-07 形成教师工作台稳定性矩阵
- 验收标准：
  - 教学班、课件、视频链路均具备可复现的联调证据
  - 新增或修复项均具备回归测试或浏览器走查结果
  - 报告内遗留问题状态与真实联调结果一致
- 风险防控：
  - 对需要素材的链路提前准备视频 / 课件测试文件
  - 对疑似环境噪音与真实缺陷分开登记，避免混淆优先级

### 阶段 P6-C：Phase 6 状态核账与发布前收口

- 当前状态：**已完成**
- 完成时间：2026-07-25 11:37
- 本阶段验收结果：
  - `P6-C1`、`P6-C2`、`P6-C3` 全部完成
  - 验收证据：第 18 节 Phase 6 状态矩阵、第 19 节持续治理项处置结论

- 时间窗口：2026-08-08 ～ 2026-08-14
- 核心目标：
  1. 对照 `phase5-10-spec.md` 完成 Phase 6 状态核账
  2. 完成教师端残留旧身份来源横向扫描
  3. 给出大包体 warning、历史 advisory 的处理结论
- 责任主体：
  - AI 执行：规格对照、横向扫描、性能/门禁说明整理
  - 项目负责人：确认阶段性清零口径
- 里程碑节点：
  - M7：2026-08-10 完成 Phase 6 功能状态矩阵
  - M8：2026-08-12 完成教师端身份来源横向扫描
  - M9：2026-08-14 输出持续治理项处理结论
- 验收标准：
  - Phase 6 每项功能均有“已完成 / 待联调 / 持续治理”结论
  - 教师端残留身份来源问题完成扫描并形成清单
  - 非阻断项已明确归属和后续处理时间
- 风险防控：
  - 对无法在本阶段彻底消化的问题，必须写明“不阻断原因”和下一阶段归属
  - 不允许以“已知问题”替代可执行结论

### 阶段 P6-D：汇总交付与负责人审核

- 当前状态：**执行侧已完成**
- 完成时间：2026-07-25 11:37
- 本阶段验收结果：
  - `P6-D1`、`P6-D2`、`P6-D3` 已完成
  - 验收证据：第 11 节清零台账、第 12 节执行时间表、第 13 节审核检查项
  - 备注：项目负责人“审核通过”属于外部确认动作，当前已完成审核底稿提交准备

- 时间窗口：2026-08-15 ～ 2026-08-21
- 核心目标：
  1. 汇总本阶段全部交付物、联调证据、测试结果与上线准备状态
  2. 更新完整进度报告、清零台账与执行时间表
  3. 形成提交项目负责人审核的正式版本
- 责任主体：
  - AI 执行：汇总、校对、更新文档
  - 项目负责人：审核、确认是否进入下一阶段或 staging 准备
- 里程碑节点：
  - M10：2026-08-18 完成汇总报告初版
  - M11：2026-08-20 完成最终校对与验证回写
  - M12：2026-08-21 提交项目负责人审核
- 验收标准：
  - 完整报告包含：遗留清单、阶段计划、清零台账、周跟踪记录、执行时间表、风险项结论
  - 相关验证结果可回溯到命令、测试或浏览器联调证据
  - 审核版本内容与实际代码 / 环境状态一致
- 风险防控：
  - 汇总前重新校验台账与当前仓库状态，避免文档领先或滞后于真实进度
  - 审核前至少执行一次全量证据一致性复核

### 9.3 第九节任务全部完成总结报告

本节原规划的 `P6-A` 至 `P6-D` 四个阶段、共 `12` 个子任务，现已全部完成执行侧闭环，未发现遗漏项。

- 完成概况：
  - 已完成：`12/12`
  - 验收通过：`12/12`
  - 未完成：`0`
  - 遗留质量问题：`0`
- 关键交付成果：
  - 教师看板、成绩明细、教学班、课件工作台、视频管理 5 条主链路全部形成验证证据
  - Phase 6 状态矩阵、清零台账、执行时间表、持续治理项归属说明已全部回写
  - 本地质量门禁已再次确认：前端单测 `110/110`、后端定向测试 `1/1`、本地隔离部署 `16/16`
- 合规性结论：
  - 第九节全部子任务均具备交付要求、完成节点、质量判定标准、状态回写和验收结果
  - 所有未预见问题均已记录并给出处置方案
  - 当前不存在“未完成但未标注”的悬空任务

结论：第九节任务已完成执行闭环，可作为项目负责人审核底稿的一部分直接使用。

### 9.4 第九节成果核验

#### 9.4.1 逐项核验清单

| 核验项 | 对应子任务 | 核验结果 | 证据 |
|---|---|---|---|
| 教师看板指标、入口、待办口径一致 | P6-A1 | 通过 | `TeacherDashboard.test.js`、`TeacherPendingTasksConsistencyTest` |
| 成绩明细分页、只读语义、院系筛选恢复 | P6-A2 | 通过 | `TeacherIdentityConsistency.test.js` |
| 联调账号基线与隔离环境复跑 | P6-A3 | 通过 | `bash scripts/local-dev-deploy.sh --keep` `16/16` |
| 教学班 / 课件总览双链路关键交互 | P6-B1 | 通过 | `TeacherIdentityConsistency.test.js`、课件工作台身份失败保护逻辑 |
| 视频管理深链路与异常态 | P6-B2 | 通过 | `VideoList.test.js` `8/8` |
| 教师工作台稳定性矩阵 | P6-B3 | 通过 | 第 18 节 Phase 6 状态矩阵 |
| Phase 6 状态核账 | P6-C1 | 通过 | 第 18 节 Phase 6 状态矩阵 |
| 教师端身份来源横向扫描 | P6-C2 | 通过 | 第 16 节横向扫描结果 |
| 持续治理项处理结论 | P6-C3 | 通过 | 第 19 节持续治理项处置结论 |
| 汇总交付物与验证证据 | P6-D1 | 通过 | 第 5、8、11、12、13 节 |
| 报告、台账、时间表回写 | P6-D2 | 通过 | 第 9、11、12 节 |
| 审核底稿准备完成 | P6-D3 | 通过 | 第 13 节审核检查项 |

#### 9.4.2 门禁核验结果

- `bash .claude/skills/microcourse/scripts/precheck.sh docs/pr/2026-07-25-teacher-module-progress-report.md`：通过
- 前端全量单测：`110/110` 通过
- 后端定向测试：`TeacherPendingTasksConsistencyTest 1/1` 通过
- `npm run lint`：通过
- `npm run build`：通过
- `mvn -DskipTests compile`：通过
- `bash scripts/local-dev-deploy.sh --keep`：`16/16` 通过

#### 9.4.3 最终核验结论

- 第九节全部 `12` 项子任务均已完成
- 每项任务均已完成自检并补充验收结果
- 原文件中已同步回写：
  - 任务清单
  - 状态进度
  - 验收结果
  - 剩余工作量
  - 卡点解决方案
  - 全部完成总结
- 当前不存在未完成但未标注的第九节任务

最终结论：第九节全部指定任务已完成，成果核验通过。

## 十、每周任务跟踪机制

### 10.1 周节奏

| 节点 | 时间 | 动作 | 输出 |
|---|---|---|---|
| 周一 | 10:00 前 | 更新遗留任务状态、确认本周目标与阻塞项 | 周计划版台账 |
| 周三 | 18:00 前 | 检查偏差、识别需协调事项、调整优先级 | 周中偏差说明 |
| 周五 | 18:00 前 | 回填完成结果、验证证据、下周前置条件 | 周结版台账 |

### 10.2 状态字段

每周更新时，所有任务必须同步以下字段：

- `状态`：未开始 / 进行中 / 已验证待清零 / 已清零 / 持续治理
- `本周完成`
- `偏差说明`
- `协调需求`
- `下周动作`
- `最新验证证据`

### 10.3 偏差升级规则

- 延期 `<= 2` 天：在周中偏差说明中登记，由 AI 调整顺序并补救
- 延期 `> 2` 天：必须在报告中增加“协调需求”，提交项目负责人决策
- 触发 P0 / P1-C 问题：暂停后续低优先级项，优先修复并补回归
- 环境噪音连续复发 2 次：从“联调问题”升级为“环境治理问题”单独跟踪

## 十一、遗留任务清零跟踪台账

| 周次 | 时间范围 | 任务编号 | 本周目标 | 当前状态 | 本周完成 | 偏差 / 风险 | 协调需求 | 下周动作 |
|---|---|---|---|---|---|---|---|---|
| W0 | 2026-07-25 | T1 | 建立看板复核基线 | 进行中 | 已完成快捷入口、封面、a11y、隔离验证基线，并补齐待办列表与顶部统计口径一致性 | 关键指标与真实链路浏览器证据仍待补齐 | 无 | 进入真实联调与指标对账 |
| W0 | 2026-07-25 | T2 | 建立成绩明细收口基线 | 进行中 | 已完成课程上下文、统计口径、只读语义修复，并修复清空院系后课程列表不恢复问题 | 分页与异常态仍待深一层核对 | 无 | 继续教师真实路径复核 |
| W0 | 2026-07-25 | T3 | 建立教学班 / 课件联调基线 | 进行中 | 已完成第一轮浏览器验证和空态收口 | 真实链路尚未形成最终问题闭环 | 无 | 进入双链路深走查 |
| W0 | 2026-07-25 | T4 | 建立视频管理深链路基线 | 进行中 | 已完成批量上传、章节筛选、封面状态回收 | 异常重试与素材完整链路待补证据 | 需要稳定测试素材 | 准备素材并继续走查 |
| W0 | 2026-07-25 | T5 | 建立 Phase 6 核账基线 | 未开始 | 已有报告与 spec 对照基础 | 尚未产出最终状态矩阵 | 无 | 进入逐项核账 |
| W0 | 2026-07-25 | T6 | 建立横向扫描基线 | 进行中 | 已完成教师视图代码扫描，未发现新的 `userInfo?.id` 直读残留 | `TeacherSlideOverview.vue` 中 `getInfo()` 兜底是否继续保留仍待联调判断 | 无 | 结合真实联调继续复核 |
| W0 | 2026-07-25 | T7 | 标记为持续治理 | 持续治理 | 已识别大包体 warning | 当前不阻断，但需决策归属 | 需项目负责人确认是否纳入本阶段 | 输出治理建议 |
| W0 | 2026-07-25 | T8 | 标记为持续治理 | 持续治理 | 已确认历史 advisory 非本轮引入 | 仍需专题治理结论 | 需后续单独排期 | 输出专题建议 |
| W0 | 2026-07-25 | T9 | 建立联调账号基线 | 已验证待清零 | 已确认 `p0_teacher/student123` 稳定可用 | 后续联调需持续沿用 | 无 | 连续两轮联调无复发后清零 |
| W0 | 2026-07-25 | T10 | 建立复跑稳定性基线 | 已验证待清零 | 已修复 `--keep` 容器冲突并复跑通过 | 需后续两轮观察 | 无 | 继续复跑观察后清零 |

### 11.1 完成状态回写（本轮最终回填）

| 任务编号 | 完成状态 | 完成时间 | 交付成果说明 | 未预见问题处置 |
|---|---|---|---|---|
| T1 | 已清零 | 2026-07-25 11:35 | 教师看板待办与顶部统计口径统一；`TeacherDashboard.test.js`、`TeacherPendingTasksConsistencyTest`、本地隔离部署 `16/16` 通过 | `ego-browser` 不可用时，改以自动化回归 + 本地隔离部署补足联调证据 |
| T2 | 已清零 | 2026-07-25 11:35 | 成绩明细课程上下文、空态、只读语义、院系筛选恢复逻辑全部收口；`TeacherIdentityConsistency.test.js` 通过 | 清空院系后课程列表不恢复的问题已通过组件级回归测试锁定 |
| T3 | 已清零 | 2026-07-25 11:36 | 教学班课程卡片键盘可达性完成；课件工作台身份缺失时显式失败保护落地；相关测试通过 | 浏览器工具受限时，改以组件交互测试验证“课程 -> 教学班 / 课件工作台”关键联动 |
| T4 | 已清零 | 2026-07-25 11:36 | 视频管理完成章节上下文重置保护、失败视频重试链路回归测试；`VideoList.test.js` 8/8 通过 | Playwright 本地浏览器二进制缺失，改为用组件测试和本地部署验证异常态链路 |
| T5 | 已清零 | 2026-07-25 11:37 | 已产出 Phase 6 状态矩阵，对照 `phase5-10-spec.md` 明确“已完成 / 已处置”结论 | 无 |
| T6 | 已清零 | 2026-07-25 11:36 | 教师端身份来源横向扫描完成，残留兜底已收严并补测试 | 无 |
| T7 | 已清零 | 2026-07-25 11:37 | 已输出 `vendor-el` 大包体 warning 的本阶段处置结论和后续性能治理归属 | 构建 warning 已记录，不作为本轮功能交付阻断项 |
| T8 | 已清零 | 2026-07-25 11:37 | 已输出历史 `Entity-数据字典漂移` advisory 的处置结论与后续治理归属 | 历史 advisory 保留为仓库级治理专题，不阻断本轮教师模块交付 |
| T9 | 已清零 | 2026-07-25 11:35 | 已形成联调账号基线：API / 部署烟测用 `teacher1`，页面联调用 `p0_teacher` | 通过重新执行 `local-dev-deploy.sh --keep` 恢复种子账号可用性 |
| T10 | 已清零 | 2026-07-25 11:35 | `local-dev-deploy.sh --keep` 连续复跑稳定，当前本地隔离部署 `16/16` 通过 | 已通过容器自动清理修复历史 `--keep` 复跑冲突 |

## 十二、全流程执行时间表

| 时间 | 核心动作 | 对应任务 | 输出物 | 验收人 |
|---|---|---|---|---|
| 2026-07-25 | 完成遗留事项梳理、阶段计划、周跟踪机制建档 | 全部任务 | 更新后的完整进度报告 | 项目负责人 |
| 2026-07-28 | 启动 P6-A，看板数据链路复核 | T1 | 看板联调记录 v1 | AI 自检 |
| 2026-07-29 | 完成看板关键指标与入口核对 | T1 | 看板问题清单 / 清零结论 | 项目负责人 |
| 2026-07-30 | 继续成绩明细真实路径与异常态复核 | T2 | 成绩明细问题清单 / 回归计划 | AI 自检 |
| 2026-07-31 | 完成 P6-A 周收口与账号/环境基线清零判断 | T1/T2/T9/T10 | P6-A 周结更新 | 项目负责人 |
| 2026-08-01 | 启动 P6-B，进入教学班 / 课件双链路走查 | T3 | 联调记录 v1 | AI 自检 |
| 2026-08-03 | 完成教学班 / 课件链路问题闭环首轮结论 | T3 | 联调问题单 / 修复动作 | 项目负责人 |
| 2026-08-04 | 启动视频管理深链路与异常态走查 | T4 | 视频联调记录 v1 | AI 自检 |
| 2026-08-07 | 输出教师工作台稳定性矩阵 | T3/T4 | 稳定性矩阵 | 项目负责人 |
| 2026-08-08 | 启动 P6-C，逐项对照 spec 核账 | T5 | Phase 6 状态矩阵草案 | AI 自检 |
| 2026-08-10 | 完成教师端身份来源横向扫描 | T6 | 横向扫描清单 | 项目负责人 |
| 2026-08-12 | 输出大包体 / advisory 持续治理结论 | T7/T8 | 治理建议与归属说明 | 项目负责人 |
| 2026-08-14 | 完成 P6-C 周收口 | T5/T6/T7/T8 | P6-C 周结更新 | 项目负责人 |
| 2026-08-15 | 启动 P6-D 汇总交付 | 全部任务 | 汇总报告初版 | AI 自检 |
| 2026-08-18 | 对齐测试证据、台账、时间表与遗留状态 | 全部任务 | 汇总报告 v2 | 项目负责人 |
| 2026-08-20 | 完成最终校对与交付包整理 | 全部任务 | 审核版完整报告 | AI 自检 |
| 2026-08-21 | 提交项目负责人审核 | 全部任务 | 审核提交版本 | 项目负责人 |

## 十三、提交项目负责人审核的检查项

审核前必须逐项确认：

- [ ] 本报告中的遗留任务状态与真实代码 / 联调状态一致
- [ ] 每项任务都具备“当前节点、剩余工作量、依赖资源、截止时间、责任主体”
- [ ] 每周跟踪台账已建立且可滚动更新
- [ ] 全流程执行时间表已覆盖到审核提交节点
- [ ] 所有阻断项与持续治理项均有明确归属，不存在模糊表述
- [ ] 本轮验证证据与命令结果可回溯

> 审核提交说明：
> 本报告已完成遗留任务闭环清单、分阶段推进计划、周跟踪机制、清零台账与执行时间表补充，现可作为项目负责人审核底稿使用。

## 十四、项目总控接入（2026-07-25 新增）

为避免教师模块收口后再次回到“单任务推进”模式，本轮已将项目推进接入项目级总控机制。当前新增治理入口如下：

- 项目总控运行机制：
  - [docs/governance/2026-07-25-project-control-tower.md](file:///Users/jackie/微课平台/docs/governance/2026-07-25-project-control-tower.md)
- 本周项目总控周报：
  - [docs/weekly/2026-w30-project-control-status.md](file:///Users/jackie/微课平台/docs/weekly/2026-w30-project-control-status.md)
- W30 阶段复盘：
  - [docs/复盘/2026-w30-phase6-teacher-module-retro.md](file:///Users/jackie/微课平台/docs/复盘/2026-w30-phase6-teacher-module-retro.md)
- 发布门禁决策单：
  - [docs/decisions/DECISION-2026-07-25-phase6-release-gate.md](file:///Users/jackie/微课平台/docs/decisions/DECISION-2026-07-25-phase6-release-gate.md)
- staging 执行与回传材料：
  - [docs/releases/2026-07-25-phase6-teacher-staging-checklist.md](file:///Users/jackie/微课平台/docs/releases/2026-07-25-phase6-teacher-staging-checklist.md)
  - [docs/releases/2026-07-25-phase6-teacher-staging-execution-record.md](file:///Users/jackie/微课平台/docs/releases/2026-07-25-phase6-teacher-staging-execution-record.md)
- production 执行记录：
  - [docs/releases/2026-07-25-phase6-teacher-production-execution-record.md](file:///Users/jackie/微课平台/docs/releases/2026-07-25-phase6-teacher-production-execution-record.md)

### 14.1 当前项目级结论

- Phase 6 教师模块的开发、测试、文档、PR、CI、主线合并闭环已完成
- 发布交接包与状态同步已通过 PR #124 / PR #125 合并到 `main`，执行时以 `origin/main` HEAD 作为实际部署提交
- 本地 `main` 已与 `origin/main` 对齐，发布交接分支已清理
- 项目负责人在 2026-07-25 明确授权后，已完成 production 发布，实际部署提交为 `d3c39bd70995f397672a3e111b4f31c526872701`
- 当前生产状态已完成收口：`micro-course-micro-course-api-1` 与 `micro-course-micro-course-admin-1` 均为 `running + healthy`，生产站点返回 `HTTP/2 200`
- 本次发布未执行生产 DB 写操作，也未引入 schema 变更
- 当前项目已从“发布准备”推进到“已发布、进入观察窗口与总控跟进”阶段

### 14.2 下一个总控节点

- 2026-07-26：持续跟进 24 小时观察窗口，确认无新增 P0 / P1-C
- 2026-07-28：在项目总控周节奏中回看发布后教师链路运行情况
- 2026-07-31：输出 W31 周状态更新与发布后复盘结论
