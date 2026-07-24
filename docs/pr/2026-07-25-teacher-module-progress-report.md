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

## 二、当前剩余任务清单

### P0 / P1-C 优先级

1. 教师数据看板闭环复核
   - 目标：继续确认 `TeacherDashboard.vue` 的关键指标、图表、待办和课程卡片与教师课程模块主链路完全一致
   - 依赖：当前快捷入口与身份来源修复已完成，可继续复核看板数据映射与真实联调

2. 成绩明细体验补强
   - 目标：围绕 `StudentGrades.vue` 继续核对分页、只读/批改态是否符合教师使用路径
   - 依赖：当前课程选择、空态行为和统计口径已修复，可在稳定基线上继续深化

3. 教学班与课件工作台联动走查
   - 目标：做教师课程 -> 教学班 -> 学员 / 教师课程 -> 课件总览 -> 课件管理 两条真实链路联调
   - 依赖：当前 `TeacherTeachingClasses`、`TeacherSlideOverview` 的 teacherId 来源与空态表达已统一

4. 视频管理深一层联调
   - 目标：继续完成教师课程 -> 章节视频 -> 封面设置 / 转码失败重试 / 章节筛选的真实链路走查
   - 依赖：本轮已补齐章节筛选前后端链路，剩余重点转向封面设置与异常态体验

### P1-I / 后续阶段任务

5. Phase 6 剩余能力补齐状态核对
   - 待继续联调确认：教师数据看板、成绩明细可用性、视频封面自定义
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
  - `TeacherDashboard.test.js` 3/3 通过
  - `TeacherIdentityConsistency.test.js` 7/7 通过
  - 合计 10/10 通过

- 本轮新增上传与课件回归：
  - `npm run test:unit -- src/__tests__/SlideUploadZone.test.js`
  - `npm run test:unit -- src/__tests__/useVideoUploadQueue.test.js`
  - `npm run test:unit -- src/__tests__/VideoList.test.js`
  - `mvn -Dtest=VideoChapterFilterIntegrationTest test`

结果：

- `SlideUploadZone.test.js` 4/4 通过
- `useVideoUploadQueue.test.js` 2/2 通过
- `VideoList.test.js` 4/4 通过
- `VideoChapterFilterIntegrationTest` 1/1 通过

- 全量前端单测回归：
  - `npm run test:unit`

结果：`38` 个测试文件、`100/100` 用例全部通过，本轮新增改动未引入前端回归失败

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
- 保留容器后已完成教师账号本地浏览器走查
- 本轮在新增上传队列实现后再次执行 `bash scripts/local-dev-deploy.sh --skip-build --keep`
- 结果仍为 `15/15` 全通过，说明本轮课程视频管理改动未破坏本地联调基线

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

## 七、下一步推进计划

### 下一优先级建议

1. 以 `TeacherDashboard.vue` 为入口做教师主链路联调
2. 继续复核 `StudentGrades.vue` 的统计、分页、批改态是否完全符合教师场景
3. 继续走查教师课程 -> 教学班 -> 学员管理 / 教师课程 -> 课件总览 -> 课件管理 / 教师课程 -> 视频管理 的深一层交互

### 预期交付

- 教师端课程模块一批更稳定的真实使用路径
- 已补齐视频批量上传队列与课件上传告警治理后的新一轮教师端工作进展报告
- 如变更范围足够完整，则进入提交、PR、CI、merge 闭环
