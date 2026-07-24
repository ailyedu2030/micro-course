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

### P1-I / 后续阶段任务

4. Phase 6 剩余能力补齐状态核对
   - 教师数据看板
   - 学员管理导出
   - 成绩明细可用性
   - 课程复制模板、章节排序、批量上传视频、视频封面自定义、审核时效提示

5. 横向扫描教师端其它残留旧身份来源
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
- 教师端剩余能力补齐状态核对

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

### 构建验证

- `npm run build`

结果：通过

### 本地隔离环境验证

- `PLAYWRIGHT_TEST=1 bash scripts/local-dev-deploy.sh`
- `bash scripts/local-dev-deploy.sh --skip-build --keep`

结果：

- 本地隔离环境 `15/15` 全通过
- 保留容器后已完成教师账号本地浏览器走查

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

## 七、下一步推进计划

### 下一优先级建议

1. 以 `TeacherDashboard.vue` 为入口做教师主链路联调
2. 继续复核 `StudentGrades.vue` 的统计、分页、批改态是否完全符合教师场景
3. 继续走查教师课程 -> 教学班 -> 学员管理 / 教师课程 -> 课件总览 -> 课件管理 的深一层交互

### 预期交付

- 教师端课程模块一批更稳定的真实使用路径
- 新一轮教师端工作进展报告
- 如变更范围足够完整，则进入提交、PR、CI、merge 闭环
