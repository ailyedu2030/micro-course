# 微课平台 · 代码质量与用户体验问题总台账（首版）

> 建档日期：2026-07-23
> 基线提交：`dd46e9c3` (`main`)
> 最新主干：`6553f7cf`（合并 PR #93）
> 范围：`micro-course-api` + `micro-course-admin`
> 方法：后端高风险代码质量只读扫描 + 前端 UX/可访问性只读扫描 + 既有审计文档去重归并
> 目标：建立后续治理的唯一执行台账，所有问题必须进入“发现 → 定级 → 修复 → 验收 → 关闭”闭环

---

## 1. 分级标准

| 等级 | 含义 | 关闭门槛 |
|------|------|----------|
| **P0** | 安全边界、权限边界、核心数据一致性、认证/事务/锁失效等可演变为事故的问题 | 必须立即进入修复批次，且需要代码验证 + 回归测试 |
| **P1-C** | 用户可直接感知的功能、交互、性能、无障碍、状态一致性问题 | 必须进入最近修复批次，且需要 UX 验收 |
| **P1-I** | 内部可见的架构、职责、维护性、观测性问题，短期不一定直接伤害用户 | 必须进入治理批次，且需要技术验收 |
| **P2** | 优化项或长期整洁性工作 | 进入 backlog，按容量滚动消化 |

---

## 2. 总览统计

| 等级 | 数量 | 说明 |
|------|------|------|
| **P0** | 2 | 后端安全上下文 / Redis 安全降级 |
| **P1-C** | 12 | 用户可感知的数据一致性、任务可靠性、无障碍与交互问题 |
| **P1-I** | 8 | 架构过载、长事务、职责耦合、生命周期治理问题 |
| **P2** | 0 | 本轮只读扫描未单列 P2 |
| **合计** | **22** | 首版治理台账 |

### 按域分布

| 域 | P0 | P1-C | P1-I | 小计 |
|----|:--:|:----:|:----:|:----:|
| **后端** | 2 | 4 | 4 | 10 |
| **前端 / UX** | 0 | 8 | 4 | 12 |

---

## 3. 问题明细

### 3.1 P0（必须立即修复）

| 编号 | 领域 | 问题 | 证据文件 | 风险摘要 | 验收标准 | 批次 |
|------|------|------|----------|----------|----------|------|
| **QX-P0-001** | 后端安全 | 全局启用 `MODE_INHERITABLETHREADLOCAL`，认证上下文会继承到子线程 | `micro-course-api/src/main/java/com/microcourse/MicroCourseApplication.java` | 异步线程可能继承请求用户身份，造成权限串用与安全上下文泄漏 | 移除全局继承策略；异步链路改为显式传递最小必要上下文；补回归验证无线程继承副作用 | Wave A |
| **QX-P0-002** | 后端安全/稳定性 | Redis 认证、黑名单、限流、分布式锁大量 `catch Exception` 后静默放行 | `micro-course-api/src/main/java/com/microcourse/util/RedisUtil.java` | Redis 故障时安全能力可能整体失效但业务仍“表面正常” | 按能力分级改为 fail-closed / 熔断告警；认证、黑名单、锁相关路径不得无感放行；补单测/集成验证 | Wave A |

### 3.2 P1-C（客户可感知，近期必须闭环）

| 编号 | 领域 | 问题 | 证据文件 | 风险摘要 | 验收标准 | 批次 |
|------|------|------|----------|----------|----------|------|
| **QX-P1C-001** | 后端事务 | Outbox worker 同类自调用 `@Transactional`，事务边界可能失效 | `micro-course-api/src/main/java/com/microcourse/event/OutboxPollerWorker.java` | 事件投递、重试、死信链路可能出现“部分成功、状态不一致” | 事务边界迁到独立 bean 或 `TransactionTemplate`；补事件成功/失败/重试回归测试 | Wave B |
| **QX-P1C-002** | 后端异步 | 视频转码异步链路中自调用更新状态，失败状态更新不可靠 | `micro-course-api/src/main/java/com/microcourse/service/impl/VideoServiceImpl.java` | 视频处理可能卡在错误状态，教师/学生看到“处理中”或失败状态异常 | 拆分异步 worker；状态迁移统一走可靠事务链；补成功/失败/重试测试 | Wave B |
| **QX-P1C-003** | 后端异步 | TTS 渲染靠线程池 + 内存态任务状态 + `sleep` 编排 | `micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/impl/TtsServiceImpl.java` | 重启丢任务、任务卡死、状态不可观测，直接影响教师课件生成体验 | 改为持久化任务状态；去掉 `sleep` 式编排；补任务恢复与幂等测试 | Wave B |
| **QX-P1C-004** | 后端一致性 | 课程删除事务提交前先删封面文件 | `micro-course-api/src/main/java/com/microcourse/service/impl/CourseAdminServiceImpl.java` | DB 回滚时文件已丢，用户看到课程仍在但封面损坏 | 文件删除改为 `afterCommit`；路径做规范化校验；补回滚一致性测试 | Wave B |
| **QX-P1C-005** | 前端交互/a11y | SlidePlayer 多个核心交互元素仅支持鼠标点击 | `micro-course-admin/src/views/student/SlidePlayer.vue` | 键盘用户与辅助技术用户无法顺畅开始播放、拖动进度、关闭提示层 | 所有核心交互元素具备按钮语义、键盘操作、焦点可见性；补 a11y 用例 | Wave C |
| **QX-P1C-006** | 前端交互/a11y | ExerciseTake 移动端答题卡入口与遮罩不可键盘操作 | `micro-course-admin/src/views/student/ExerciseTake.vue` | 高价值流程入口不可访问，影响题目导航与作答流畅度 | 入口与遮罩支持键盘/读屏；保留移动端触控体验；补交互测试 | Wave C |
| **QX-P1C-007** | 前端交互/a11y | CourseDetail 课程预览主 CTA 为纯 `div @click` | `micro-course-admin/src/views/student/CourseDetail.vue` | 首屏预览入口键盘不可达，影响试听与转化 | CTA 改为可访问按钮语义；Tab 顺序与回车/空格触发符合预期 | Wave C |
| **QX-P1C-008** | 前端交互/a11y | LearningCenter“继续学习”卡片不可键盘触达 | `micro-course-admin/src/views/student/LearningCenter.vue` | 高频学习闭环入口不可访问，影响继续学习任务完成率 | 卡片具备角色、焦点与键盘触发；补回归测试 | Wave C |
| **QX-P1C-009** | 前端一致性 | LearningView 与 VideoPlayer 存在重复进度保存链路 | `micro-course-admin/src/views/student/LearningView.vue`, `micro-course-admin/src/views/student/VideoPlayer.vue` | 双报、漏报、离开页补报行为可能不一致，直接影响学习进度体验 | 统一为单一 composable / 服务；补“定时上报 + 离开页补报”回归测试 | Wave D |
| **QX-P1C-010** | 前端可用性 | CourseList / StudentList 表格键盘增强绑定到原生 `tbody` 匿名监听 | `micro-course-admin/src/views/courses/CourseList.vue`, `micro-course-admin/src/views/teacher/StudentList.vue` | 重渲染后能力丢失或事件泄漏，键盘导航体验不稳定 | 抽统一表格键盘增强方案；确保销毁/重建无泄漏；补列表交互测试 | Wave D |
| **QX-P1C-011** | 前端可用性 | 上传进度浮窗在业务失败时不复位，导致进度卡死 | `micro-course-admin/src/utils/request.js` | 用户看到“上传中”但实际已失败，造成体验误导与操作阻塞 | 业务失败 / 网络失败 / 成功分支均复位；补 unit test 锁定复位行为 | Wave A2 |
| **QX-P1C-012** | 前端一致性/a11y | TeacherDashboard 使用 emoji 作为 icon，破坏一致性与读屏语义 | `micro-course-admin/src/views/teacher/TeacherDashboard.vue` | icon 体系不统一；读屏可能读出无意义 emoji；视觉层级不稳定 | 全部替换为 Element Plus Icons；关键标题用具名 slot + icon；补页面 mount 回归测试 | Wave A2 |

### 3.3 P1-I（内部问题，必须纳入治理批次）

| 编号 | 领域 | 问题 | 证据文件 | 风险摘要 | 验收标准 | 批次 |
|------|------|------|----------|----------|----------|------|
| **QX-P1I-001** | 后端架构 | HermesWebhookController 职责严重过载且手工操纵 `SecurityContext` | `micro-course-api/src/main/java/com/microcourse/controller/HermesWebhookController.java` | 控制器同时承担鉴权、文件、脚本、删除、映射，演化成本高且易再出安全遗漏 | 拆成认证适配层 + 应用服务层；控制器禁止直接操纵安全上下文 | Wave E |
| **QX-P1I-002** | 后端性能/事务 | 导出服务在只读事务内做长时间文档生成 | `micro-course-api/src/main/java/com/microcourse/service/impl/StorageApplicationExportServiceImpl.java` | 长事务占连接池，放大锁等待与吞吐抖动 | 事务内只取快照 DTO，事务外生成文档；补性能回归验证 | Wave E |
| **QX-P1I-003** | 后端架构 | AuthServiceImpl 聚合认证、CAS、头像文件、上下文读取等多重职责 | `micro-course-api/src/main/java/com/microcourse/service/impl/AuthServiceImpl.java` | 可测试性差、演进易回归、审查成本高 | 拆分 AuthCore / CasClient / ProfileAssetService；统一 HTTP client 注入 | Wave F |
| **QX-P1I-004** | 后端架构 | StorageApplicationServiceImpl 事务内做图片缩放、落盘、旧文件清理 | `micro-course-api/src/main/java/com/microcourse/service/impl/StorageApplicationServiceImpl.java` | 文件系统与事务强耦合，维护与回滚风险高 | 拆分命令/查询/文件存储适配层；统一绝对根目录与 after-commit 清理 | Wave F |
| **QX-P1I-005** | 前端可维护性 | VideoPlayer 单文件超过 1600 行，承担播放、缓存、定时器、全屏等全部职责 | `micro-course-admin/src/views/student/VideoPlayer.vue` | 任一点修改都容易引发连锁回归 | 拆出播放控制、进度上报、UI 层 composable/子组件；补关键流程测试 | Wave G |
| **QX-P1I-006** | 前端生命周期 | App 根组件注册多个全局事件监听且未配套清理 | `micro-course-admin/src/App.vue` | 生命周期副作用与泄漏风险长期积累 | 所有监听具备命名 handler 与卸载清理；补生命周期测试 | Wave G |
| **QX-P1I-007** | 前端复用 | `SignatureUploader` / `DynamicTableEditor` 存在双份实现分叉 | `micro-course-admin/src/components/common/*`, `micro-course-admin/src/components/storage/*` | 相同交互分叉维护，修一处不等于全站修好 | 明确共用版与特化版边界，收敛重复实现；补组件契约测试 | Wave G |
| **QX-P1I-008** | 前端稳定性 | 学习进度、播放器、页面层之间存在重复状态源 | `micro-course-admin/src/views/student/LearningView.vue`, `micro-course-admin/src/views/student/VideoPlayer.vue` | 维护成本高，后续极易再生出体验漂移 | 统一状态源与事件边界；建立播放器领域约束文档 | Wave G |

---

## 4. 治理批次

| 批次 | 目标 | 范围 | 完成定义 |
|------|------|------|----------|
| **Wave A** | 先清安全与 fail-open | `QX-P0-001` ~ `QX-P0-002` | P0 清零，补回归测试 |
| **Wave A2** | 前端 UX 快速修复（不等下一波） | `QX-P1C-011` ~ `QX-P1C-012` | 进度状态一致；icon 体系统一；补单测 |
| **Wave B** | 修后端事务与异步一致性 | `QX-P1C-001` ~ `QX-P1C-004` | 核心异步链路状态一致，失败可恢复 |
| **Wave C** | 修高频学习链路可访问性 | `QX-P1C-005` ~ `QX-P1C-008` | 高频入口支持键盘 / 辅助技术 |
| **Wave D** | 收敛前端交互一致性 | `QX-P1C-009` ~ `QX-P1C-010` | 学习进度与列表交互稳定一致 |
| **Wave E** | 拆高风险后端职责耦合 | `QX-P1I-001` ~ `QX-P1I-002` | 控制器/导出长事务风险下降 |
| **Wave F** | 重构认证与申报文件链路 | `QX-P1I-003` ~ `QX-P1I-004` | 高复杂服务完成职责拆分 |
| **Wave G** | 统一前端基础能力 | `QX-P1I-005` ~ `QX-P1I-008` | 前端播放器/全局监听/重复组件收敛 |

---

## 5. 双重验收标准

### 5.1 技术负责人验收

- 根因分析完整：症状、直接原因、根本原因、横向扫描、防再发
- `GetDiagnostics` 为 0 或新增告警为 0
- 涉及链路的单元测试 / 集成测试 / E2E 测试补齐并通过
- 同类问题横向扫描完成，不允许只修单点表象
- PR 门禁全绿，fix commit 含完整根因与验证段落

### 5.2 用户体验验收

- 高频入口支持键盘与读屏，不得只支持鼠标点击
- 错误提示必须清晰、可执行、对用户友好
- 页面主任务路径不得出现卡顿、双报、漏报、状态漂移
- 关键操作反馈（加载、成功、失败、空态）完整
- 老年 / 慢网 / 异常场景至少覆盖一条验证证据

---

## 6. 执行纪律

- 本台账是后续治理的唯一入口，新增问题必须先登记再修复
- 每完成一项，必须更新状态、证据、关联 PR 和验收结果
- P0 与 P1-C 不得以“复杂度高”或“先记着”方式跳过
- 如受当前工作区未提交改动影响，需先隔离风险后再进入对应批次

---

## 7. 已完成项（关闭记录）

| 编号 | 关闭方式 | 关联 PR | 关联提交 | 验收结果 |
|------|----------|---------|----------|----------|
| QX-P0-001 | 移除全局继承策略 + 回归测试 | #73 | `ff9a56ee` | CI 全绿；回归测试锁住“子线程不继承认证上下文” |
| QX-P0-002 | Redis 安全能力 fail-closed + 过滤器与业务回归测试 | #73 | `ff9a56ee` | CI 全绿；认证关键路径 Redis 故障不再放行 |
| QX-P1C-011 | 响应拦截器统一复位上传进度 + unit test | #74 | `f927bada` | CI 全绿；`code != 200` 不再卡死进度 |
| QX-P1C-012 | TeacherDashboard emoji → Element Plus Icons + 页面回归测试 | #74 | `f927bada` | CI 全绿；图标语义与一致性对齐 |
| QX-P1C-001 | `TransactionTemplate` 重建 outbox 单条事务边界 + affectedRows 强校验 | #85 | `50e88099` | CI 全绿；事件投递/重试/死信链路不再依赖失效的同类自调用事务 |
| QX-P1C-002 | 上传/重试统一 after-commit 提交转码 + 重试真正重入队 | #86 | `059df304` | CI 全绿；转码失败后可恢复，状态推进与异步提交保持一致 |
| QX-P1C-003 | section/admin TTS 任务状态持久化 + 移除 sleep 编排 | #87 / #88 | `6292b142` / `a8e7a586` | CI 全绿；TTS 任务跨重启可恢复，教师批量生成状态不再丢失 |
| QX-P1C-004 | 课程封面删除改为 after-commit + 路径规范化校验 + 回归测试 | #89 | `e02b6e96` | CI 全绿；DB 回滚不再导致课程仍在但封面已丢失，集成测试缓存串扰同步清除 |
| QX-P1C-005 | SlidePlayer 核心交互按钮化 + 进度条 slider 语义 + 键盘提示层关闭路径 | #91 | `aeb9373a` | CI 全绿；播放、拖动进度、关闭提示层均支持键盘与焦点可见性 |
| QX-P1C-006 | ExerciseTake 答题卡入口/题号按钮化 + 对话框语义 + `Escape` 关闭 | #91 | `aeb9373a` | CI 全绿；移动端答题卡入口与导航对键盘/读屏用户可用 |
| QX-P1C-007 | CourseDetail 预览 CTA 改为原生按钮并补充可访问名称 | #91 | `aeb9373a` | CI 全绿；课程预览首屏入口可聚焦、可键盘触发，试听转化路径恢复可访问性 |
| QX-P1C-008 | LearningCenter 继续学习/最近学习卡片按钮化 + 回归测试 | #91 | `aeb9373a` | CI 全绿；高频继续学习入口支持键盘触达与清晰焦点反馈 |
| QX-P1C-009 | 抽离统一学习进度上报 composable，收敛定时上报/补报/冲突回补路径 | #93 | `6553f7cf` | CI 全绿；LearningView 与 VideoPlayer 进度保存链路统一，定时上报与离页补报走同一能力 |
| QX-P1C-010 | 抽离统一表格键盘增强 composable，补重渲染刷新与卸载清理 | #93 | `6553f7cf` | CI 全绿；CourseList / StudentList 行语义、键盘触发与监听清理保持一致 |

---

## 8. 下一步

- **Wave D 已完成**：`QX-P1C-009` ~ `QX-P1C-010` 全部关闭，学习进度上报与列表键盘增强已收敛到共享能力
- 进入 **Wave E**：优先处理 `QX-P1I-001` ~ `QX-P1I-002`（HermesWebhookController 职责拆分 + 导出长事务收敛）
- Wave E 完成标准：高风险控制器职责下降、导出链路事务边界收口，且补齐回归验证与文档基线
