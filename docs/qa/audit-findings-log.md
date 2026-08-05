# 全页面审查 · 问题发现-修复日志

> 每条记录按"根因分析五段式"：症状 / 直接原因 / 根本原因 / 横向扫描 / 防止再发。
> 与 `full-page-verification-matrix.md` 配套维护。

## 2026-08-04 · 学生答题题型渲染与评分链路（E8.1 / C6.2 补测）

### F-2026-08-04-01 · 判断题无选项渲染，学生无法作答（P1-C）

- **症状**：练习 10 第 3 题（判断题"0是自然数"）作答区空白，无"正确/错误"选项，无法作答。
- **直接原因**：答题页 `ExerciseTake.vue` 单选/判断分支按 `currentQuestion.options` 循环渲染选项，题目 9 的 `options` 字段为空（`null`）。
- **根本原因**：判断题创建链路缺失默认选项——`QuestionList.vue` 表单只提供"正确答案"radio（正确/错误），`formData.options` 始终为 `''`；后端 `QuestionServiceImpl.create/update/batchImport` 也未对 JUDGE/TRUE_FALSE 归一化默认选项。任何经 UI 创建的判断题都是"不可作答"的残次题。
- **横向扫描**：教师预览组件 `QuestionPreview.vue` 对判断题硬编码"正确/错误"（不受影响）；答题页 H5 分支同源缺陷；导入 Excel 判断题空选项同样受影响。
- **修复**：后端 create/update/import 统一 `normalizeJudgeOptions` 补默认选项 `[{"value":"true","label":"A","text":"正确"},{"value":"false","label":"B","text":"错误"}]`；`QuestionList.vue` 创建/编辑提交判断题时强制写入默认选项；`ExerciseTake.vue` 归一化时对 JUDGE 空选项兜底注入。已复测：判断题渲染"正确/错误"并正确判分。

### F-2026-08-04-02 · 简答题（SHORT_ANSWER）无作答区（P1-C）

- **症状**：练习第 1 题"简述极限的定义"（简答题）作答区空白，无输入框，无法作答，提交时提示"未作答"。
- **直接原因**：`ExerciseTake.vue` 桌面与 H5 两处模板仅处理 SINGLE/JUDGE/MULTIPLE/FILL/ESSAY，缺少 SHORT_ANSWER 分支。
- **根本原因**：题型枚举含 SHORT_ANSWER（后端校验白名单、教师预览组件均支持），但答题页渲染分支遗漏，属"类型支持矩阵不一致"缺陷。
- **横向扫描**：`QuestionPreview.vue` 简答题有 textarea（正常）；结果解析页 `formatUserAnswer` 对 SHORT_ANSWER 走 `answers[qId]` 文本路径（正常）；仅答题输入区缺失。
- **修复**：`ExerciseTake.vue` 两处模板将 SHORT_ANSWER 并入 FILL 的 textarea 分支（rows=3，placeholder"请输入您的答案"）。已复测：简答题可输入并随提交持久化。

### F-2026-08-04-03 · 多选题答案格式契约不一致 → 必判错（P1-C）

- **症状**：多选"以下哪些是偶数？"作答 A.2+C.4（正确答案 2,4），结果显示答案"2、4"但判错，选项标记 `option-wrong`，得分 20（正确答案 30）。
- **直接原因**：前端提交 `JSON.stringify(multipleAnswers.sort())` → `["2","4"]`；后端 `gradeQuestion` 对 MULTIPLE 用 `userAnswer.split(",")` 解析 → 元素带引号 `["2"`、`"4"]`，与正确答案集合 `{2,4}` 永不相等 → 判错/部分得分 0。
- **根本原因**：多选答案格式契约未统一：题库创建端答案存纯逗号分隔值（`correctOptions.join(',')`），学生答题端提交 JSON 数组，后端解析只支持逗号分隔。
- **横向扫描**：前端 `parseMultipleAnswer` 仅 `JSON.parse`，编辑端创建的多选（答案 `"2,4"`）解析失败返回空数组 → 结果页"正确答案"映射失败、`isMultipleCorrect` 全 false；同一缺陷影响客户端判分与高亮。
- **修复**：后端新增 `parseMultipleAnswerSet` 兼容 JSON 数组与逗号分隔两种格式；前端 `parseMultipleAnswer` JSON 解析失败时回退 `split(',')`。已复测：多选判正确，得分 30、答对 3/5、选项高亮 option-correct。

## 2026-08-04 · 教学班管理补测（D4）

### F-2026-08-04-04 · 教学班学生状态三层契约冲突 → 添加/改状态必失败（P1-C）

- **症状**：POST 添加学生返回 409"数据冲突"；UI 修改学生状态无响应。
- **直接原因**：DB `chk_tcs_status` 约束仅允许 ENROLLED/DROPPED/COMPLETED，服务插入 `EnrollmentStatus.APPROVED`（APPROVED）违反约束 → DataIntegrityViolation；前端修改状态弹窗传 ACTIVE/DISABLED/SUSPENDED，后端白名单（APPROVED/DROPPED/COMPLETED）拒绝 → 400。
- **根本原因**：教学班学生状态被错误复用课程选课域枚举（V148 迁移把 APPROVED 当作在读值），而该表自建表起约束即为 ENROLLED/DROPPED/COMPLETED，三层契约从未对齐。
- **横向扫描**：`selectActiveByClassId` 查 APPROVED（约束下永远为空）；`countActiveByClassId` 查 ENROLLED（不一致）；`getClassStudents` 只查 ENROLLED → 退课/结业学生从名单消失无法管理。
- **修复**：后端白名单与插入统一为 ENROLLED/DROPPED/COMPLETED（对齐 DB 约束）；仓库查询改 ENROLLED；名单接口改查全部成员；前端状态映射与弹窗选项对齐（在读/已退课/已结业）。已复测：添加学生 200、改状态在读↔已退课闭环、名单含已退课学生。

### F-2026-08-04-05 · 修改状态/移除学生传错 ID → 8001 选课记录不存在（P1-C）

- **症状**：UI 点击"修改状态/移除"均失败，API 报 8001。
- **直接原因**：前端传 `student.id`（teaching_class_students 记录 id），后端接口路径参数是 userId。
- **根本原因**：VO 同时含记录 id 与 userId，组件取字段时选错；与后端契约（/students/{userId}）不符。
- **横向扫描**：添加学生弹窗传 userId（正确）；仅修改状态与移除两处传错。
- **修复**：两处调用改为 `student.userId`。已复测：修改状态 UI 闭环生效。

### F-2026-08-04-06 · 教师添加学生搜索调用管理端接口 → 403（P1-C）

- **症状**：添加学生弹窗搜索提示"无权访问该资源"+"搜索学生失败"。
- **直接原因**：前端 `getUsers` 调 `GET /api/users`，该接口 `@PreAuthorize('ADMIN','ACADEMIC')`。
- **根本原因**：教师端功能缺少教师可用的学生搜索接口，复用了管理端接口。
- **横向扫描**：仅教学班添加学生弹窗使用该接口（教师端）；其他教师页面使用各自专用接口。
- **修复**：新增 `GET /api/users/students/search`（TEACHER/ADMIN/ACADEMIC，仅返回 id/realName/studentNo/avatar/status 最小字段），前端切到 `searchStudents`。已复测：教师搜索 teststudentx 命中并成功添加。

## 2026-08-04 · 线下课签到补测（D8/D9/E11）

### F-2026-08-04-08 · 服务端时区 UTC 导致签到窗口/日期错位 8 小时（P1-C）

- **症状**：本地 17:18（北京时间）学生端点击签到提示"不在签到时间窗口内"；服务端创建 09:10 场次反而可签到。
- **直接原因**：API 容器与 DB 时区均为 UTC；`OfflineSessionServiceImpl.checkin` 用 `LocalDate.now()/LocalTime.now()`（JVM 默认时区 = UTC）与用户录入的北京时间场次比较，窗口整体偏移 8 小时。
- **根本原因**：`spring.jackson.time-zone: Asia/Shanghai` 只作用于 Jackson 序列化，不改变 JVM 默认时区；容器无 `TZ` 环境变量。所有依赖 `LocalDate.now()/LocalTime.now()` 的业务（线下签到日期/窗口、考试/练习时间窗、周报边界等）均受影响；服务端生成的 created_at/签到时间按 UTC 落库并在 UI 显示（本地 17:10 的操作显示为 09:10）。
- **横向扫描**：`LocalDate.now()`/`LocalTime.now()` 无参调用分布在签到、考试、练习、报告、徽章等模块；DB 层 `now()` 默认值同样按 UTC。修复点：应用启动 static 块强制 `TimeZone.setDefault(Asia/Shanghai)`（覆盖容器内外全部启动方式）、compose 补 `TZ: Asia/Shanghai`、postgres 补 `timezone=Asia/Shanghai`、staging DB `ALTER DATABASE ... SET timezone`。
- **防止再发**：业务时区统一约定 Asia/Shanghai 并写入启动代码；新增任何"当前时间"判断必须使用同一时区来源；部署脚本（local-dev-deploy.sh）需在容器环境加 TZ（当前由应用 static 块兜底）。
- **验证**：重启后北京窗场次（17:15 开始）签到 200 成功、UTC 探针场次（09:10 开始）被拒；学生 UI 点击签到 → "签到成功" → ✅ 已签到。

## 2026-08-04 · 互动课件补测（D10/D11）

### F-2026-08-04-09 · 课件删除接口把 slideId 当 sectionId 查询 → 删除 100% 失效（P1-C）

- **症状**：课件列表点击"删除"→ 确认后报"未找到该课时的课件"，行不消失。
- **直接原因**：`DELETE /api/courses/{courseId}/slides/{slideId}` 控制器调用 `slideService.deleteSlide(courseId, slideId)`，该方法按 `section_id = ?` 查询课件（把 slideId 当 sectionId），永远查不到 → `SLIDE_NOT_FOUND`。
- **根本原因**：按 ID 删除与按课时删除两条路径共用同一服务方法，参数语义错配；前端 `deleteSlideById(row.courseId, row.id)` 传的是课件 ID。
- **横向扫描**：`DELETE /slides`（按 chapterId/lessonId 删除）语义正确保留；仅 `/{slideId}` 路径受影响。
- **修复**：新增 `deleteSlideById(courseId, slideId)` 按主键删除（含 slide_pages 级联、文件清理、音频清理），控制器改调新方法。已复测：UI 删除 → "课件已删除" → 列表清空。

## 2026-08-04 · 微专业补测（D13/D14/D15）

### F-2026-08-04-10 · 指派教师复用邀请接口 → 已接受团队成员永远无法指派（P1-C）

- **症状**：课程编排"指派教师"选择已接受团队成员后确认，报"教师已在微专业团队中"。
- **直接原因**：`handleAssignTeacher` 调用 `inviteTeacher`（POST /teachers），该接口对已存在团队成员抛 `MS_DUPLICATE_TEACHER`。
- **根本原因**："指派教师到课程"与"邀请新教师"是两个语义不同的操作，前端却复用同一接口；团队记录含 courseId 字段但无更新入口。
- **横向扫描**：仅课程编排页受影响；团队管理"邀请新教师"路径语义正确。
- **修复**：新增 `PUT /micro-specialties/{id}/teachers/{teacherId}/course`（assignTeacherToCourse：校验 ACTIVE 成员 + 课程归属 + 同课程仅一名授课教师），前端改用新接口。已复测：指派测试教师2 → "教师已指派" → 行内授课教师更新。

### F-2026-08-04-11 · 课程编排更新复用含 @NotNull courseId 的 DTO → 编辑（含排序）100% 失效（P1-C）

- **症状**：编辑课程（改排序/学分）点保存报"课程ID不能为空"，弹窗不关闭。
- **直接原因**：`MicroSpecialtyCourseRequest.courseId` 标注 `@NotNull`，更新接口复用该 DTO，而更新路径按 path 的 itemId 操作、请求体不含 courseId → 校验失败。
- **根本原因**：新增/更新共用 DTO，约束未按接口拆分。
- **修复**：移除 DTO 层 @NotNull，新增路径在服务层显式校验 courseId。已复测：编辑 sortOrder → "保存成功" → 列表按 sortOrder 重排。

### F-2026-08-04-12 · 团队"批量操作"空壳按钮 + 移除传记录 ID → 移除 100% 失效（P1-C）

- **症状**：批量操作仅切换按钮文案（无选择列/无批量能力）；单条与批量移除均报"资源不存在或已被删除"。
- **直接原因**：成员表无 `type="selection"` 列，批量模式无实际功能；移除调用传 `row.id`（teaching 记录 id）而接口路径参数为 teacherId（用户 id）。
- **根本原因**：批量操作功能未开发完整（UI 空壳）；VO 同时含记录 id 与 teacherId，取参错误。
- **横向扫描**：同源传参缺陷曾发生于教学班"修改状态/移除"（F-2026-08-04-05），本次为同类复发 → 已将该模式纳入问题模式库。
- **修复**：成员表 expelMode 时显示选择列 + "批量移除(N)"按钮 + `handleBatchRemoveMembers`（按 teacherId 循环移除，含成功/失败反馈）；单条移除统一传 teacherId。已复测：勾选 → 批量移除(1) → 确认 → "已批量移除" → 行消失。

## 2026-08-04 · 微专业申报补测（D16/G4）

### F-2026-08-04-13 · SignatureBlock 双向深 watch 回声死循环 → 页面主线程冻结（P1-C）

- **症状**：申报表模块 4 切换"图片签名"后页面完全卡死（连 `1+1` 求值都超时），两个标签页均冻结。
- **直接原因**：`storage/SignatureBlock.vue` 同时存在 `watch(() => props.modelValue, deep)`（父→子回写）与 `watch(localData, deep)`（子→父 emit），且 emit 时重建对象 `{...localData.value}`；父级 v-model 收到新对象引用后触发 props watch 再次赋回 → 无限回声循环，主线程死锁。
- **根本原因**：双向 v-model + 双向 deep watch + 对象重建，无内容级去重。
- **横向扫描**：common/SignatureBlock 无 watch（安全）；任何使用 storage/SignatureBlock 的交互（切模式/输入意见/选日期）都会触发该循环。
- **修复**：props 回写增加 JSON 内容级比较，内容未变不回写，打断回声链。已复测：切图片签名、选日期后页面均响应正常。

### F-2026-08-04-14 · 申报表签名/公章上传通道未接通 → 仅本地预览、永不落库（P1-C）

- **症状**：选择图片后只显示 blob 本地预览，无上传请求、无成功/失败提示。
- **直接原因**：父组件传 `:signature-uploader`/`:seal-uploader`，但 `storage/SignatureBlock.vue` 只声明 `uploadHandler` prop 且两个 SignatureUploader 均绑定 `uploadHandler`（undefined）→ 上传通道为空 → CommonSignatureUploader 走"未配置上传通道"分支。
- **根本原因**：父子 props 契约名不一致（signature-uploader vs uploadHandler），且未按 签名/公章 分别接线。
- **修复**：SignatureBlock 新增 `signatureUploader`/`sealUploader` props 并分别绑定；后端 `POST /storage-applications/{id}/upload-image` 已 curl 验证 200 返回 URL。已复测：切换图片签名不再冻结、上传控件正常渲染。

## 2026-08-04 · 登录/账号补测（A1）

### F-2026-08-04-15 · 忘记密码链路完全缺失 → 用户无法找回密码（P1-C，功能未开发）

- **症状**：登录页无"忘记密码"入口；全项目（前端/后端）无任何密码重置实现（仅 i18n 残留翻译键）。
- **直接原因**：未开发找回/重置密码功能。
- **根本原因**：产品规划缺失 + 后端无邮件基建（无 JavaMailSender/spring.mail），自助邮件重置不可行。
- **横向扫描**：管理员用户管理页亦无重置密码入口（用户被锁只能靠 DBA/直改 DB）。
- **修复**：按校园平台标准实现管理员"重置密码"兜底链路——后端 `PUT /api/users/{id}/password`（ADMIN，密码强度校验同注册）；前端 UserTable 增加"重置密码"行操作 + 弹窗（新密码/确认一致性校验）；登录页增加"忘记密码"链接 + 引导弹窗（联系管理员）。已复测：管理员重置后新密码登录 200、旧密码 1001；UI 弹窗提交"密码重置成功"。

### F-2026-08-04-16 · 发送测试邮件为前端模拟占位，后端无实际 SMTP 能力（P1-C，功能未开发）

- **症状**：点击"发送测试邮件"仅提示"当前为模拟测试，请手动发送邮件验证配置是否可用"。
- **直接原因**：后端无测试端点、无邮件依赖；前端 `handleTestMail` 为纯模拟 setTimeout 提示。
- **根本原因**：邮件功能（SMTP 配置）只有 UI 没有实现，配置后无法验证。
- **修复**：pom 增加 `spring-boot-starter-mail`；新增 `POST /api/admin/settings/send-test-email`（读取已存 SMTP 配置，JavaMailSender 自测发送到配置邮箱，返回真实成功/失败）；前端改为调用真实端点。已复测：空配置提示校验；保存配置后返回真实连接错误（UnknownHostException: smtp.example.com），证明 SMTP 链路实现。

## 2026-08-04 · 管理端补测（B13/B14/B20）

### F-2026-08-04-17 · 教师评级重算 500：MyBatis 无法实例化结果接口（P1-C）

- **症状**：教师评分管理"全部重新评级"返回 500"服务器内部错误"，列表一直为空。
- **直接原因**：`TeacherRatingRepository.TeacherRatingStatRow` 声明为 interface，MyBatis 结果映射需要可实例化的 POJO → `NoSuchMethodException: TeacherRatingStatRow.<init>()`。
- **根本原因**：统计行映射用接口声明，MyBatis 反射无法创建实例。
- **横向扫描**：`selectTeacherStats`/`selectTeacherStat` 两个查询均受影响（单教师重算同样 500）。
- **修复**：改为静态类（无参构造 + getter/setter）。已复测：recalculate-all → 200，3 位教师评级生成（SILVER 49.3 / BRONZE）；手动调级 GOLD→SILVER 闭环。

### F-2026-08-04-18 · 营收看板无明细下钻入口（P1-C，功能未开发）

- **症状**：营收看板统计卡与教师排行均为静态展示，无法查看订单明细。
- **直接原因**：看板无任何点击交互；系统无管理端订单列表页（/admin/orders 重定向到学生订单）。
- **修复**：新增 `GET /api/orders/admin/list`（ADMIN，可按 teacherId 过滤，含下单用户名）；看板"付费订单"卡与教师排行行可点击 → 订单明细弹窗（订单号/学员/课程/金额/状态/支付方式/下单时间）。已复测：两个入口均弹出明细。

## 2026-08-04 · 完成课程链路补测（E20/H12）

### F-2026-08-04-19 · 完成课程 500：证书失败污染外层事务 + 证书校验 NPE（P1-C）

- **症状**：教师标记选课 completed=true → 接口 500"服务器内部错误"，课程无法完成、徽章不颁发。
- **直接原因（两个叠加）**：
  1. `issueCertificate` 在完成事务内直接调用，证书条件不满足抛异常虽被 catch，但共享事务已标记 rollback-only → 外层提交抛 UnexpectedRollback → 500；
  2. 改为 REQUIRES_NEW 后，内层事务读不到外层未提交的 completed=true → 证书仍不颁发；
  3. `validateCourseCompletion` 用 `groupingBy(chapterId)`，而 learning_progress 存在 chapter_id=NULL 的章节级聚合行，groupingBy 对 null key 执行 requireNonNull → NPE → 证书自动颁发必失败（fail-open 吞掉）。
- **根本原因**：事务边界与业务补偿设计不当（fail-open 依赖共享事务）；数据模型允许 NULL chapter_id 但校验未兼容。
- **修复**：证书颁发改为事务提交后（afterCommit）执行（可见 completed=true 且不污染外层事务）；徽章颁发改 REQUIRES_NEW；分组前过滤 NULL chapterId 行。已复测：完成课程 200 → 证书 MC-5-1-4339477E 落库 + FIRST_COURSE/ALL_COURSES 徽章颁发 + 成就墙 2/6 点亮，错误日志清零。

## 2026-08-05 · PPT 真实渲染与申报组件补测（E10.3/H18/G1.5/G3.x）

### F-2026-08-05-01 · 章节级课件页列表接口 NULL section 查询恒空 → 播放器图片加载失败（P1-C）

- **症状**：PPT 渲染成功（status=2、slide_pages 有页）后，`GET /slides/pages?chapterId=1` 返回空 → 学生播放器显示"1/0 图片加载失败"。
- **直接原因**：控制器把 chapterId 折叠成 `effectiveId` 传入 `getPages(courseId, sectionId)`，服务按 `section_id = ?` 过滤；章节级课件页记录 `section_id IS NULL`，`section_id = 1` 永假。
- **根本原因**：chapterId/sectionId 语义混淆；页查询未按 chapter_id 维度。
- **横向扫描**：单页/图片接口按 courseId+pageNumber 查询（正常）；Hermes 课件接口调用点同步修正。
- **修复**：`getPages` 增加 chapterId 参数：sectionId→eq；否则 chapterId→eq chapter_id；否则 isNull(section_id)。控制器不再折叠参数。已复测：页列表返回页1+图片 URL；学生播放器 1/1 显示渲染页。

### F-2026-08-05-02 · DynamicTableEditor 双向深 watch 回声死循环（P1-C，与 SignatureBlock 同型）

- **症状**：申报模块 3 点击"+ 新增行"→ 页面主线程冻结（连 1+1 求值都超时）。
- **直接原因**：storage/DynamicTableEditor 同时 `watch(props.modelValue, deep)` 回写 localData 与 `watch(localData, deep)` emit `JSON.parse(JSON.stringify(...))` 新数组 → 父级换新引用 → 无限回声。
- **根本原因**：双向 v-model + 双向 deep watch + emit 重建引用（横向扫描发现与 SignatureBlock 完全同型，均入模式库）。
- **修复**：props 回写做 JSON 内容级去重。已复测：新增行正常、max-rows=5 生效、页面不冻结。

### F-2026-08-05-03 · 环境供给：容器缺 LibreOffice/中文字体导致 PPT 渲染失败（环境项，已解决）

- **症状**：PPT 上传后"课件渲染失败"。
- **修复**：容器安装 libreoffice-impress 26.2.4 + fonts-noto-cjk（aliyun 镜像加速）。已复测：渲染 pages=1、status=2、学生端真实播放。**部署要求：容器镜像必须包含 LibreOffice + 中文字体，否则 PPT 课件渲染失败。**

### F-2026-08-05-04 · HTML 课件单元懒创建死链（P1-C，三处叠加）

- **症状**：HTML 课件上传后工作台 HTML 流程不可达；即使进入，保存报 500（`PUT /html/units/undefined`），单元永远无法创建。
- **直接原因（三处叠加）**：
  1. `CoursewareWorkbench` HTML 流程条件 `tree?.type === 'HTML'`，而 tree 依赖 htmlUnit → 首次上传无 unit 时编辑器不可达；
  2. `HtmlBlockEditor.load()` 用 `res.data || res`，后端 R 包装 data=null 时回退成整个响应对象（truthy）→ 误走 update 路径（unitId=undefined）；
  3. `createUnitFresh` 未填 `chapter_id`（slide_html_units 非空约束）→ 500；且上传端点硬编码 sectionId=null，课时级 HTML 无法上传。
- **修复**：工作台 HTML 流程改按 coursewareType 渲染 + 分段面板 null 守卫；load 正确解包 R 包装；后端 createUnit 从 slide 派生 chapter_id；上传端点支持 sectionId；保存后 emit unit-saved 触发父级重载 tree。已复测：课时级 HTML 上传→编辑→"已创建 unit id=2"→分段脚本 5 段渲染。

## 2026-08-05 · 管理端基础功能补测（B2-B9）

### F-2026-08-05-05 · 用户创建表单缺邮箱/手机格式校验（P1-C，规则缺失）

- **症状**：创建用户填非法邮箱（not-an-email）提交无任何提示。
- **直接原因**：`UserForm.vue` 的 `formRules` 仅含 username/password/confirmPassword/realName/role，email/phone 表单项有 prop 但无规则。
- **修复**：补齐邮箱正则与手机号（1[3-9] 开头 11 位）格式规则。已复测：提交后提示"请输入正确的邮箱格式/手机号格式"。

## 2026-08-05 · 课程管理批次补测（C1-C10）

### F-2026-08-05-06 · 讨论详情 VO 缺 status → 通过/驳回按钮永不显示（P1-C）

- **症状**：管理端打开待审核讨论帖，详情页无"通过/驳回"按钮、无状态标签，只能删除，审核流程不可用。
- **直接原因**：`getById` 使用的 `convertToVO(post, userMap)` 与列表 `convertToVO(post)` 均未设置 `status` 字段（仅管理端列表转换 `convertToVOForAdmin` 有 int→string 映射）→ 前端 `postData.status` 为 undefined。
- **修复**：抽取 `applyPostStatus` 统一注入两个 convertToVO。已复测：详情返回 status=PENDING → 通过确认→"审核通过"→已发布+按钮消失。

## 2026-08-05 · 微专业团队补测（D15）

### F-2026-08-05-07 · 重邀功能不可达 + 参数错位（P1-C，两处叠加）

- **症状**：团队页无"重邀"按钮；即使后端直调也报"教师ID不能为空"。
- **直接原因**：
  1. 团队列表走公开端点 `listTeachers`（仅返回 ACTIVE，隐私设计）→ DECLINED/REMOVED 成员不显示 → 前端 `v-if="DECLINED||REMOVED"` 的重邀按钮永不可达；
  2. 前端 `handleReinvite` 发空 body，DTO 校验"教师ID不能为空"；
  3. 后端控制器把 `request.getTeacherId()` 传给了服务第 4 参 courseId（参数错位，重邀会把课程指派写成教师 ID）。
- **修复**：新增教师端 `GET /teachers/manage`（含全部状态）；前端发送 teacherId/role/courseId；DTO 补 courseId 字段、控制器映射修正、服务缺省复用原记录课程。已复测：重邀确认→"已重新邀请"→DECLINED→INVITED（已复原）。

## 2026-08-05 · 学生端批次补测（E 系列）

### F-2026-08-05-08 · 视频重试转码状态机错位 → 永远卡"转码中"（P1-C）

- **症状**：转码失败后点"重试"，视频停在"转码中"不再变化；新上传视频也因容器缺 ffmpeg 全部失败。
- **直接原因（两处叠加）**：
  1. `retryTranscode` 把 FAILED(3) 直接置为 TRANSCODING(1)，但转码任务 CAS 要求 UPLOADING(0)→TRANSCODING(1) → 重试任务被"已被其他转码任务接管"跳过，永远卡死；
  2. API 容器重建后丢失 ffmpeg（此前手工安装未固化到镜像）→ 新上传转码必失败。
- **修复**：retryTranscode 改为置 UPLOADING(0)；容器补装 ffmpeg 8.0.1（aliyun 镜像）。已复测：重试 → status=2 + HLS 生成；学生播放器 video 元素 readyState=4 加载成功。**部署要求：镜像必须包含 ffmpeg（含 HLS 转码）**。

### F-2026-08-05-09 · 课程评价姓名恒显"匿名用户"（P1-C，字段错配）

- **症状**：非匿名评价在课程详情"课程评价"tab 中作者显示"匿名用户"。
- **直接原因**：前端 `CourseDetail.vue` 读 `r.userRealName`/`r.userAvatar`，后端 `CourseReviewVO` 契约字段是 `realName`/`username`（无 avatar）→ 回退"匿名用户"。
- **修复**：改用 `r.realName`（后端对匿名评价置空，自动回退匿名）。已复测：评价列表显示"测试学生1"。

### F-2026-08-05-10 · 我的课程进度显示 0.33…% + 练习"false/0"（P1-C，双端）

- **症状**：MyCourses 卡片进度显示 `0.3333333333333333%`，练习统计渲染 `false/0 已完成`。
- **直接原因**：① completion 接口 `progress` 为 0-1 比例，前端直接当百分比显示；② `pdata.completedExercises ?? pdata.completed ?? 0` 把 Boolean `completed=false` 透传成 "false"；③ 后端 `batchGetByUserAndCourses` 未聚合练习统计（仅单课程接口聚合）。
- **修复**：前端比例×100（上限100）、移除 Boolean 回退；后端 batch 按课程聚合 completedExercises/totalExercises/completedVideos。已复测：33%、练习 0/2、视频 1/1；两接口口径一致=3。

### F-2026-08-05-11 · 取消收藏后重新收藏必现 409（P1-C，软删行唯一约束）

- **症状**：收藏→取消→再收藏返回 409"数据冲突"，用户无法重新收藏。
- **直接原因**：`uk_cf_user_course` 唯一约束包含软删行；`favorite()` 的 selectCount 被逻辑删除过滤=0 → insert 命中唯一键。
- **修复**：新增 `restoreByUserAndCourse`（UPDATE deleted_at=NULL），先恢复再 insert。已复测：三态流转 200，`alreadyFavorited=false`。

### F-2026-08-05-12 · 训练中心"进入章节练习"实际跳课程详情（P1-C，事件冒泡）

- **症状**：点章节练习按钮 URL 变成 /student/courses/1 而非 /student/chapters/1/exercises。
- **直接原因**：`.chapter-item` 的 `@click="goExercise"` 未 `.stop`，冒泡到 el-card `@click="goCourse"` 后者覆盖路由。
- **修复**：`@click.stop` / `@keydown.enter.stop`。已复测：跳转 /student/chapters/1/exercises（4 练习）。

### F-2026-08-05-13 · 考试通过后"已完成"tab 永不显示（P1-C，接口缺字段）

- **症状**：考试提交 100 分通过后，考试中心"已完成"tab 仍"暂无已完成的考试"。
- **直接原因**：`GET /exercise-records/my/{id}/attempt-count` 只返回 attemptCount，前端判 `passed`/`isPassed` 均为 undefined → 永不通过。
- **修复**：新增 `getAttemptSummary` 返回 {attemptCount, passed(任一次通过), score(最近)}。已复测：已完成 tab 显示"期中考试试卷A 已完成"。

### F-2026-08-05-14 · 学习视图"讨论"tab 空壳占位（P1-C，功能残缺）

- **症状**：学习视图点"讨论"只显示"暂无讨论/返回课程"，无发帖/浏览能力，与独立讨论页功能断层。
- **直接原因**：LearningView 的讨论 tab 渲染 NotesPanel 占位，未接入真实 DiscussionView。
- **修复**：占位改为"参与课程讨论/进入讨论区"按钮，携带当前章节跳 /student/discussions?chapterId=。已复测：跳转讨论页并列出帖子。

### F-2026-08-05-15 · 错题本入口跳个人中心不定位（P1-C，交互缺陷）

- **症状**：学习中心点"错题本"跳到 /student/profile 顶部，用户看不到错题集。
- **直接原因**：快捷入口 path 写死 /student/profile，且无滚动定位。
- **修复**：path=/student/profile?section=wrong-book，Profile 挂载后 scrollIntoView（多次重试适配异步卡片）。已复测：进入后错题集在视口内并渲染真实错题。

### F-2026-08-05-16 · 通知分类筛选恒空 + 类型标签全显"系统通知"（P1-C，双端契约错配）

- **症状**：通知页"课程/考试/讨论通知"筛选永远空态；所有行类型标签都是"系统通知"。
- **直接原因**：前端 tab 传短分类（ENROLLMENT 等），后端 `eq(type)` 精确匹配全量码（ENROLLMENT_SUCCESS…）→ 0 行；`getNotifTagLabel` 只映射 5 个精确类型 → 全量码回退"系统通知"。
- **修复**：后端 TYPE_CATEGORY 分类→码集合（in 查询）；前端按前缀归类标签。已复测：课程通知4条、讨论通知3条、标签正确。

### F-2026-08-05-17 · 我的评价课程筛选下拉恒空（P1-C，分页基不一致）

- **症状**：MyReviews"筛选课程"下拉无任何选项。
- **直接原因**：`fetchCourseOptions` 传 page=1，后端 0 基分页（page+1=2）→ items 空；主列表传 page=0 正常。
- **修复**：统一 page=0。已复测：下拉显示课程，筛选生效。

### F-2026-08-05-18 · 学生删除自己的评价 403（P1-C，权限缺失）

- **症状**：MyReviews 点"删除"→ 403。
- **直接原因**：`DELETE /api/reviews/{id}` 仅 ADMIN/ACADEMIC，无学生删除路径。
- **修复**：放开 STUDENT + 服务层归属校验（学生仅可删本人评价）。已复测：删除成功（软删）。

### F-2026-08-05-19 · 免费课程下单必现"非法支付来源"9005（P1-C，顺序颠倒）

- **症状**：免费/定价未审批课程下单（课程 4）返回 9005。
- **直接原因**：createOrder 免费分支先 autoEnroll（sourceChannel=PAYMENT 要求已存在 PENDING/PAID 订单）再插入免费 PAID 订单 → 校验必失败。
- **修复**：先落免费 PAID 订单再选课。已复测：下单 200（PAID，amount 0）+ 自动选课成功。

### F-2026-08-05-20 · 我的订单页无状态筛选（P2，功能未开发完整）

- **症状**：订单页只有列表+分页，无法按状态筛选（后端已支持 status/courseId）。
- **修复**：补全"全部/待支付/已支付/已取消/已退款"下拉筛选并接入 API。已复测：待支付→空态、已支付→1 单。

## 2026-08-05 · 教务/微专业审批批次补测（F 系列）

### F-2026-08-05-21 · 申报审批页无批量审批 UI（P2，功能未开发完整）

- **症状**：申报审批页仅逐条批准/驳回，无批量操作；后端 batch-approve/batch-reject 接口早已存在但无前端入口。
- **修复**：补全选择列（仅待审批可选）+ 批量批准/批量驳回按钮 + 统一驳回原因 prompt + 结果反馈（成功X失败Y）。已复测：勾选 2 条批量驳回→成功2失败0，DB 2 条 REJECTED + 统一原因。

### F-2026-08-05-22 · 跨学院审批驳回必现 409（P1-C，双 CHECK 约束叠加）

- **症状**：F7 跨学院审核点"驳回"→ 409 数据冲突，状态不变。
- **直接原因**：V153 创建 `chk_mst_invite_status`（不含 REJECTED）；V173 意图修复却新增第二个约束 `chk_ms_teacher_invite_status`（含 REJECTED）而非替换旧约束 → 双约束叠加，旧约束永远拦截 REJECTED。
- **修复**：V326 迁移 `DROP CONSTRAINT chk_mst_invite_status`（保留含 REJECTED 的新约束）。已复测：驳回 200 → invite_status=REJECTED。

### F-2026-08-05-23 · 班级导入 pending_courses jsonb 写入类型错误（P1-C）

- **症状**：班级导入失败，失败原因 `column "pending_courses" is of type jsonb but expression is of type character varying`。
- **直接原因**：实体 `MicroSpecialtyEnrollment.pendingCourses`（String）用 `JacksonTypeHandler`，对 String 字段写入按 varchar 绑定 → jsonb 列拒绝。
- **修复**：新增 `JsonbStringTypeHandler`（写入以 Types.OTHER 绑定，读取返回字符串）并替换实体注解。已复测：pending_courses JSON 正确落库。

### F-2026-08-05-24 · 班级导入共享事务 rollback-only（P1-C，fail-open 失效模式复发）

- **症状**：修复 jsonb 后导入仍失败，错误 `Transaction rolled back because it has been marked as rollback-only`。
- **直接原因**：classImport 在共享事务内调用 `enrollmentService.enroll()`（REQUIRED），内层抛 BusinessException（如付费课程未购买）被 catch 吞掉，但内层 @Transactional(rollbackFor=Exception) 已把共享事务标记 rollback-only → 外层提交抛 UnexpectedRollbackException。
- **修复**：`EnrollmentService` 新增 `enrollInNewTransaction`（REQUIRES_NEW 包装），classImport 与申报自动选课两处调用点改用之。已复测：导入成功 1 人 + pending_courses 记录。

### F-2026-08-05-25 · 班级导入去重漏 PENDING 致唯一约束冲突（P1-C）

- **症状**：导入含已有 PENDING 修读记录的学生时，insert 命中 `uk_mse_active` 唯一约束 → 该班级全部失败。
- **直接原因**：去重查询仅排除 APPROVED/IN_PROGRESS；`uk_mse_active` 是部分唯一索引（排除 REJECTED/DROPPED/FAILED 之外均唯一），PENDING 也在索引内。
- **修复**：去重与名额占用口径改为 notIn(REJECTED, DROPPED, FAILED)，与索引语义一致。已复测：PENDING 学生跳过、其余导入成功。

## 2026-08-05 · 组件/系统级批次补测（G/D7.5/H 系列）

### F-2026-08-05-26 · PPT 四面板管线缺 slide_ppt_pages 数据（P1-C，功能不可达）

- **症状**：新版课件四面板编辑器"面板暂不可达"（此前仅记录缺口）。
- **直接原因**：渲染服务只写 slide_pages（v1 表），v2 四面板读 slide_ppt_pages → 恒空。
- **修复**：SlideRenderService 渲染成功事务内同步写入 slide_ppt_pages（重渲染先删旧行防唯一冲突），字段与 slide_pages 同源。已复测：重新上传 PPTX → slide_ppt_pages 1 行 → 四面板(内容/讲述稿/音频/跳转逻辑)全部可达。

### F-2026-08-05-27 · AudioManager 缺 onMounted 导入致工作台整页崩溃（P1-C）

- **症状**：sectionId 模式打开课件管理页 → ErrorBoundary "页面出了点问题"。
- **直接原因**：`AudioManager.vue` 使用 `onMounted(loadAllAudios)` 但 import 仅 `{ref, computed, watch}` → ReferenceError。
- **修复**：补齐 onMounted 导入。已复测：工作台正常加载。

### F-2026-08-05-28 · 无脚本时音频面板以 null 调接口致 500（P1-C，三处叠加）

- **症状**：有页面无脚本时 AudioPanel 挂载 → GET /scripts/null/audios → 500 → 崩页。
- **直接原因**：① AudioPanel `scriptId` 必填 Number，父级 null 也渲染；② 后端 @PathVariable Long 解析 "null" 抛 MethodArgumentTypeMismatch → 500。
- **修复**：AudioManager 无脚本时渲染空态引导（v-if 守卫）+ AudioPanel 可选 prop/静默空 + 后端 scriptId 改 String 容错返回空列表。

### F-2026-08-05-29 · PPT 讲述稿/音频 jsonb+NOT NULL 写入失败（P1-C）

- **症状**：保存讲述稿 500（created_by NOT NULL）；生成音频 500（generation_params jsonb 类型错误，PPT/HTML 双实体）。
- **直接原因**：① saveScript 依赖客户端传 createdBy，缺失即 null；② 两个音频实体 generation_params 与 pending_courses 同型（JacksonTypeHandler 对 String 失效）。
- **修复**：saveScript 后端回退 SecurityUtil 当前用户；两个实体换 JsonbStringTypeHandler。已复测：脚本 v1 落库(created_by=3)、音频任务 GENERATING 落库、TTS 降级链路(Qwen3→mmx→key 缺失)日志完整。

### F-2026-08-05-30 · 试卷列表无编辑入口（P2，功能未开发完整）

- **症状**：教师试卷列表仅"删除"，无法编辑试卷（后端 PUT /exercises/{id} 早已存在，表单支持 exerciseId 回显）。
- **修复**：补"编辑"按钮 → /courses/{courseId}/exercises/form?exerciseId=。已复测：点击编辑→表单回显→改标题→保存→PUT 落库。

## 2026-08-05 · 全量自动化回归（后端 1125 单测 / 前端 207 单测）

### F-2026-08-05-31 · 导出快照事务测试陈旧断言（P2，测试未随 P1-C 修复同步）

- **症状**：`StorageApplicationExportServiceImplTest` 断言"快照查询必须使用只读事务"失败。
- **根因**：2026-08-04 P1-C 修复将导出快照事务从 readOnly=true 改为可写（SELECT FOR UPDATE
  在 PostgreSQL 只读事务中必失败，导出曾 100% 500），测试未同步更新，断言旧缺陷行为。
- **修复**：断言改为 `assertFalse(isReadOnly)`（事务必须可写），并注明原因。已复测 2 用例通过。

### F-2026-08-05-32 · 教学班学生状态测试用旧值 APPROVED（P2，测试未随 V316 同步）

- **症状**：`TeachingClassServicePermissionTest.approvedStatusShouldBeAccepted` 失败：
  "学生状态值无效，应为 ENROLLED/DROPPED/COMPLETED"。
- **根因**：V316 将 DB 约束/服务白名单从 APPROVED 改为 ENROLLED/DROPPED/COMPLETED
  （P1-C 修复），测试仍断言旧状态 APPROVED 合法。
- **修复**：测试改断言 ENROLLED（enrolledStatusShouldBeAccepted）。已复测 23 用例全通过。

## 2026-08-05 · 全量自动化回归修复（e2e/a11y/单测门禁全绿）

### F-2026-08-05-33 · 管理员创建课程必 409（P1-C，teacher_id NOT NULL）

- **症状**：ADMIN 调 POST /api/courses 创建课程 → 409 数据冲突。
- **直接原因**：create() 仅对 TEACHER 填充 teacherId；ADMIN 未指定时 teacher_id 为 null → NOT NULL 冲突。
- **修复**：管理员/教务创建必须显式指定授课教师，否则返回明确业务错误。已复测：admin+teacherId 创建 200。

### F-2026-08-05-34 · 锁定徽章整卡透明度致文本对比度不足（P1-C，a11y serious）

- **症状**：student 账号（无徽章）在个人中心/成就墙，axe 报 badge-tip/badge-desc color-contrast 2.99~3.76:1。
- **直接原因**：AchievementBadges/AchievementWall 的锁定徽章卡 `opacity:0.7/0.8`，axe 按透明度混合后有效对比度骤降（真实缺陷，非误报）。
- **修复**：移除透明度，锁定态改用浅灰底+虚线边框区分；同时 badge-desc/criteria 从 secondary/placeholder 提升至 regular、学生主色 #6366f1→#5b60ea（白字4.88:1）、互动课徽章 #67c23a→#2e7d32（5.13:1）、通知未读标题改用 primary-dark。已复测：profile/settings/achievements/my-courses/courses/learning/notifications 全部 0 critical/serious。

### F-2026-08-05-35 · XSS e2e 用 page.request 不带 token 必 401（P2，测试缺陷）

- **直接原因**：应用是 localStorage Bearer 鉴权，page.request 不携带 → 上传 401。
- **修复**：登录后从 localStorage 取 token 注入 Authorization 头；同时课程夹具归属 p0_teacher + SLIDES_HTML_WHITELIST 含 6。已复测 A1/A2 200。

### F-2026-08-05-36 · e2e 依赖数据/浏览器环境（P2，门禁基础设施）

- **根因**：隔离库无课程/课件数据（XSS B/C、phase11 需 course 1/133 + slide + 选课）；chromium 未安装；teacher-audit.spec.js 两处 describe 被注释致整文件语法错误；C2 单测 10 payload 超 30s 预算。
- **修复**：新增 scripts/seed-e2e-fixtures.sh（课程1 VIDEO 归 p0_teacher + 课程133 INTERACTIVE 强制 id=133 + 章节/HTML 课件/选课）；门禁接入种子、白名单 env、chromium 自安装（npmmirror 回退）、--timeout=240000；修复 e2e 语法。已复测：chromium-desktop 37/37 通过（4.0m）。

### F-2026-08-05-37 · 管理端建课表单缺授课教师选择器（P1-C，前端功能缺口）

- **症状**：ADMIN 在 /courses/create 建课：后端修复前 409，修复后提示"必须指定授课教师"，但表单根本没有教师字段 → 管理员 UI 建课不可用。
- **直接原因**：CourseDetail.vue 建课表单仅显示禁用教师名输入框，teacherId 只在请求体里透传，管理员无渠道填写。
- **修复**：创建模式 + ADMIN/教务角色显示教师下拉（getUsers role=TEACHER 加载，过滤可搜索），teacherId 必填校验；后端已有明确错误兜底。已复测：选测试教师1 → POST teacherId=3 → 200 → 跳 /courses/7（测试课程已清理）。

### F-2026-08-05-38 · 视频倍速按钮 aria-label 引用缺失 i18n key（P3，无障碍）

- **症状**：VideoPlayer 三处倍速按钮 `$t('video.playbackSpeed', {speed})`，但语言包只有 `video.speed`，aria-label 渲染为原始 key "video.playbackSpeed"。
- **修复**：zh/en 补 `playbackSpeed: '倍速 {speed}' / 'Speed {speed}'`；i18n 完整性扫描清零（静态 key 全覆盖，无动态拼接 key）。

### F-2026-08-05-39 · 管理端/教务端 a11y：封面图缺 alt + 轮播开关缺 label（P2，无障碍）

- **症状**：admin /courses 封面 el-image 无 alt（image-alt serious）；/admin/banners 两个 el-switch 无 label。
- **修复**：CourseList/VideoList/FavoriteList/StudentFavorites/Checkout 封面图补 alt；BannerList 两个开关补 aria-label；横向扫描 4 个含 el-image 无 alt 的文件一次补齐。
- **验证**：admin 6 页 + academic 5 页 + 额外管理 7 页 axe 全部 0 critical/serious。

### F-2026-08-05-40 · 管理/教务端 a11y 未入持久回归（P2，测试覆盖缺口）

- **背景**：e2e a11y 仅覆盖学生/教师端；管理/教务端此前为一次性手工 axe 扫描。
- **修复**：新增 e2e/admin-audit.spec.js（管理端 7 页 + 教务端 5 页，账号默认对齐门禁种子 admin/admin123、academic1/password123）。staging 12/12 通过；全量套件将扩至 49 项。
- **附注**：staging 上用 gate 专用账号（student/student123 等）登录会触发登录锁定（5 次失败锁），验证了锁定机制生效；锁已清除。门禁专用账号不应在 staging 使用。

### F-2026-08-05-41 · 登录首屏误载 523KB 视频库（P2，性能优化）

- **症状**：Lighthouse 登录页性能 63（LCP ~6s）；index.html 首屏预加载 vendor-video-player(692KB)。
- **直接原因**：vite manualChunks 规则 `id.includes('video.js')` 子串误命中 `src/api/video.js` 等模块 id，将视频库/相关源码强行并入独立 vendor 块并被入口静态引用 → 全站（含登录页）首屏下载。
- **修复**：规则收紧为 `id.includes('node_modules/hls.js')`，视频库回到懒加载图。匿名登录页 JS 从 6 块降至 5 块（视频块 161KB gzip 不再下载）；视频播放复测 readyState=4 无报错；单测 207/207、bundle 门禁、precheck 8/8 全绿。
- **遗留优化点**：vendor-el 943KB（main.js `app.use(ElementPlus)` 全量注册，与 vite 注释"按需引入"不符）——移除需全量回归验证，列为 P2 后续项。
