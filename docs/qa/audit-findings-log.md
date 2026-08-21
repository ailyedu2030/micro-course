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

> **2026-08-05 部署复核修正**：当前渲染链路为 Apache POI（`SlideRenderService`，JVM AWT 直接出图），代码中零 `soffice/libreoffice` 调用（全仓 grep 无命中，`ProcessBuilder` 仅用于 ffmpeg）。生产实证：无 LibreOffice 的生产镜像下 `course_slides` 111 条 status=2（渲染成功）。原"LibreOffice 必需"为环境误诊（真实缺口是中文字体）。**修正后的部署要求：容器镜像必须包含中文字体（`font-noto-cjk` + `wqy-zenhei`），LibreOffice 不安装。** 已在 `micro-course-api/Dockerfile` 固化。

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

### F-2026-08-05-42 · Element Plus 按需化尝试与结论（P2 性能调查，收尾）

- **尝试**：移除 main.js `app.use(ElementPlus)` 全量注册 + locale 改 el-config-provider；并试过 alias 桶→按需包装。
- **结果**：vendor-el 稳定在 ~943KB。根因：项目实际使用 67/124 个 el-* 组件（含日期/时间选择器等重型组件），EP 组件内部依赖图稠密（date-picker 内含 calendar 等），按需后并集接近全量体积；alias 方案零收益且破坏 unplugin resolver 生成的导入（SlideUploadZone 测试挂）→ 已回退 alias。
- **保留**：main.js 移除冗余全量注册（与 vite 注释"按需引入"意图一致）+ locale 经 el-config-provider（App 根包裹）——单元测试 207/207、6 页冒烟 0 警告。
- **结论**：vendor-el 为该项目 EP 使用密度的实际下限，不做 114 文件深导入重构（风险收益比不划算）。

### F-2026-08-05-43 · 通知分类映射漏 MS_ENROLLMENT_AUTO_ENROLL（P2，由回归测试发现）

- **症状**：新增的 TYPE_CATEGORY 完整性回归测试失败——`MS_ENROLLMENT_AUTO_ENROLL` 未进入任何分类映射。
- **直接原因**：映射中写成了 `ENROLLMENT_AUTO_ENROLL`（漏 MS_ 前缀），此类通知在任何筛选 tab 都不可见（仅"全部"可看）。
- **修复**：改为 `MS_ENROLLMENT_AUTO_ENROLL`；同时新增 5 个修复回归测试（软删收藏恢复/管理员建课教师校验/通知分类覆盖全枚举/考试 attempt 汇总/免费订单先建单后选课），防止同类问题复发。

## 2026-08-06 · 生产 console 400 排查（用户上报）

### F-2026-08-06-01 · 章节页课时列表 size=999 触后端上限 400，课时全部加载失败（P1-C）

- **症状**：课程 52 章节管理页（/chapters）浏览器 console 并发 8 个 400：`GET /api/courses/52/chapters/{144-151}/sections?page=0&size=999`。生产 nginx 日志佐证：真实用户（Mac/Chrome，referer /chapters）12:24 +08 请求全部 400，每章课时数为 0、课时管理不可用。
- **直接原因**：前端 `ChapterList.vue` `fetchSections` 传 `size: 999`；后端 `SectionController:23` R11 安全收敛后 `@Range(max=200)` 校验拦截 → 400（校验先于业务，`listByChapter` 本身忽略分页返回全量）。
- **根本原因**：R11 将 22 个控制器 size 上限从 10000 收敛到 100/200 时，未同步收敛前端所有"拉全量"调用（历史模式 `size: 999/1000/9999`），契约漂移；e2e 未覆盖章节页课时加载，回归漏检。
- **横向扫描**（全量映射前端 size>100 调用点 ↔ 后端上限）：
  - **唯一实际 400**：ChapterList listSections(999) → SectionController max=200 ✅已修
  - 其余全部安全：getCourses 无上限（CourseController:60）；getDepartments/getMajors/getClasses/getCategories/getChapters/getUsers/getMyEnrollments(/enrollments/my 无 size)/getAttendance 等 max=10000 或忽略 size；EnrollmentOverview `/enrollments` max=10000；DiscussionList 实为 getCourses
  - 治理项：前端 16 处 `size:999/1000/9999` 虽当前不报错，但依赖后端宽松上限，后续收紧即复发（P1-I，已登记本文档，未批量改动以免无谓 churn）
- **修复**：新增 `src/utils/fetchAllPages.js`（以 size=200 循环翻页直至收齐 totalElements，兼容"后端忽略分页返回全量"语义）；`ChapterList.vue` 改用之；新增 `src/__tests__/fetchAllPages.test.js` 5 用例（单页/多页/忽略分页/空/异常）。单测 212/212、ESLint 0 error、precheck 8/8。
- **防止再发**：① 前端"拉全量"统一走 fetchAllPages，size 恒 ≤ 后端上限；② 单测固化分页契约；③ 同步 `.agents/skills/microcourse/scripts/precheck.sh` 白名单（补 CourseNoteController/CourseCopyContentServiceImpl/VideoStreamServiceImpl，消除与 `.claude` 版的漂移）。

### F-2026-08-06-02 · 课件管理页预览功能消失：useFeatureFlag 暴露普通对象非 ref，v2 恒渲染/旧版头部恒隐藏（P1-C）

- **症状**：用户上报 `/teacher/courses/52/slides/manage?sectionId=573`（HTML 课件）"没有预览功能，之前是有大图预览"。生产 nginx 日志显示同一次访问同时请求旧版接口（/slides、/slides/pages）与 v2 接口（/courseware/573、/html/sections/573/unit）——新旧 UI 并存。
- **直接原因**：`useFeatureFlag.js` 把普通对象 `{ value: readPersisted() }` 作为 `coursewareV2` 暴露；模板中普通对象恒为 truthy → `v-if="coursewareV2 && sectionId"`（v2 工作台）永远成立、`v-if="!coursewareV2"`（旧版头部，含"预览/替换/更多"按钮）永远隐藏、`el-switch` v-model 失效。本地实测：localStorage `mc:feature:courseware_v2='false'` 时工作台仍渲染、头部按钮消失。
- **根本原因**：早期灰度开关代码未用 `ref()` 包裹（Vue3 模板自动解包语义缺失），属"响应式状态封装错误"模式；旧版 UI 各 section 仅头部受 coursewareV2 门控、workspace 未门控 → v2 开启时新旧 UI 并存。
- **横向扫描**：全仓 `useFeatureFlag` 仅此一处；旧版 UI 对 HTML 页预览为后端占位图（"第N页"灰色框）而非真实内容，属第二处预览缺陷（HTML 课件无可视化预览）。
- **修复**：① `useFeatureFlag.js` 改 `ref(readPersisted())`，恢复模板自动解包；② `SlideManage.vue` 旧版 upload-hero/processing/error/workspace 全部加 `!coursewareV2` 门控，v2 开启时不再与旧 UI 并存；③ 旧版编辑器预览区对 HTML_DIRECT 页改渲染 `iframe :srcdoc=htmlContent`（真实预览），缩略图跳过占位图请求并渲染 HTML 图标块；④ 新增 `useFeatureFlag.test.js` 3 用例（isRef/持久化/模板解包语义）。
- **复测**（本地 ego-browser 真实交互）：flag=false → 旧版头部"预览/替换/更多"恢复、v2 工作台隐藏、HTML 缩略图为专用块；点击缩略图 → 编辑器 iframe 渲染 HTML 内容（srcdoc 命中）；点击头部"预览" → 全屏 SlidePlayer iframe 渲染 HTML；切换 v2 开关 → 工作台 HTML 流程（HtmlBlockEditor"预览/保存"）出现、旧 UI 完全隐藏。
- **防止再发**：① 单测固化"暴露给模板的状态必须是 ref"；② 矩阵补录课件管理页 HTML 预览功能点；③ 旧版 HTML 预览缺口的同类页面（学生端 CourseDetail 播放）已由 SlidePlayer iframe 覆盖，无需重复修复。

### F-2026-08-06-03 · 教师预览 SlidePlayer 触发学习进度 403（P1-C 控制台噪音）

- **症状**：用户打开课件管理页"预览"（SlidePlayer）后 console 报 `POST /api/learning-progress/progress 403 (Forbidden)` + `[SlidePlayer] ensureProgress failed`。与 F-2026-08-06-02 同一次修复后预览可用即暴露此问题。
- **直接原因**：`SlidePlayer.onMounted → ensureProgress()` 无条件上报进度；后端 `POST /learning-progress/progress` 为 `@PreAuthorize("hasRole('STUDENT')")`，教师/管理员预览（管理页"预览"按钮打开同一播放器）→ 403 → console.warn。
- **根本原因**：SlidePlayer 是学生播放器被教师预览复用，未按角色区分"上报进度"语义；后端 STUDENT-only 是正确契约（教师预览不应产生学习进度），前端缺角色守卫。
- **横向扫描**：`markSlideComplete`（翻至末页）同样无条件 PUT/POST 进度 → 教师预览同样触发（已静默 catch，但语义错误）；学生端正常路径不受影响（角色 STUDENT 守卫放行）。全仓仅 SlidePlayer 复用播放器于教师预览。
- **修复**：`SlidePlayer.vue` 引入 `useUserStore`，`ensureProgress`/`markSlideComplete` 增加 `isStudent` 守卫（非 STUDENT 直接跳过，不产生 403 与 console 噪音）。
- **复测**：本地 ego-browser 以 admin 打开预览 → 全屏 SlidePlayer 渲染 HTML ✅、本地 API 日志 3 分钟内 0 次 `POST /learning-progress/progress` ✅；新增 SlidePlayer.test.js 回归用例（TEACHER 角色挂载不调用 createLearningProgress），单测 216/216。
- **防止再发**：① 复用学生播放器/组件的教师预览路径统一加角色守卫；② 回归单测固化"非 STUDENT 不上报进度"；③ 模式库补"播放器跨角色复用需按角色守卫副作用调用"。

### F-2026-08-06-04 · SlidePlayer origin 守卫语义错误：iframe→父页消息全部被拒（P0，根因已锁定，待 P0 实施）

- **症状（用户核心诉求）**："现在还不能跟着 PPT 或 HTML 控制播放"——HTML 课件内点击段落/完成按钮/音频状态上报对播放器全部无效。
- **直接原因**：`SlidePlayer.vue:367` `if (event.origin !== null) return`。`MessageEvent.origin` 是 DOMString，srcdoc + sandbox（无 allow-same-origin）iframe 的 opaque origin 序列化为**字符串 `"null"`**；`"null" !== null` 恒为 true → iframe 发出的 `slide-audio-state` / `slide-interactive-complete` /（v2）`segment-active` 等消息全部在入口被丢弃。
- **根本原因**：`docs/postMessage-音频控制方案.md`（v1 设计文档）把「origin 为 null」写成 JS `null` 字面量并照搬到代码；测试未覆盖 sandboxed srcdoc postMessage 的 origin 序列化语义（`SlidePlayer.test.js` 无 postMessage 消息用例）。
- **横向扫描**：v1 协议文档 §安全校验、`SlidePlayer.onSlideAudioMessage`（唯一消息入口）为全部受影响面；父→iframe 方向 `postMessage(msg, '*')` 在 opaque origin 下正确，无需改。
- **修复（已入 `docs/design/2026-08-06-PPT-HTML-音频同步控制方案.md` §6.3/P0-1，待实施）**：守卫改 `event.origin === 'null'`；同步修正 v1 设计文档伪码；新增单测覆盖 sandboxed srcdoc 消息（origin="null" 放行、其他 origin 拒绝）。
- **防止再发**：① 协议实现必须对照浏览器 origin 序列化语义写单测；② 评审清单增加「postMessage origin 字符串 vs null」检查项。

## 2026-08-06 晚 · P0 实施闭环（PPT/HTML 音频同步控制方案 P0，R-1~R-16 主体）

### F-2026-08-06-05 · D9 修复：origin 守卫改 `event.origin === 'null'` + source 校验（P0）

- **症状**：HTML 课件内点击段落/完成按钮/音频状态上报对播放器全部无效（用户核心诉求"不能跟着 HTML 控制播放"）。
- **根因**：`SlidePlayer.vue` 旧守卫 `event.origin !== null`；`MessageEvent.origin` 是 DOMString，sandbox srcdoc opaque origin 序列化为 `"null"` → 恒 true → 全部拒收（Playwright+Chromium 实测 + WHATWG html#3585 双重确认）。
- **修复**：守卫改 `event.origin === 'null' && event.source === 当前 iframe.contentWindow`（R-1/H-1）；新增单测覆盖 origin 字符串与 source 不匹配两态；Playwright e2e 实证 iframe→父 `ready` 消息被接收并回发 `slide-audio-state-v2 loaded`（bridge 日志）。

### F-2026-08-06-06 · D1 修复：废除注入音频控制器，HTML 音频改父页 AudioHost（P0）

- **根因**：`SlideServiceImpl.buildSegmentControllerJs()` 拼接的 `<script>` 语法错误（Node `new Function()` 报 `Unexpected token ')'`），iframe 内分段音频控制器整体失效。
- **修复**：删除注入脚本与 `injectBeforeBodyEnd`；HTML 段音频由父页 `<audio>` 顺序播放（协议 v2），legacy `AUDIO_SEG_XX_URL` 占位符仅替换不注入；e2e 实证 HTML 段1→段2 自动续播。

### F-2026-08-06-07 · D3 修复：新建 TtsWorkerService 消费 v2 GENERATING→MiniMax→READY（P0，R-11）

- **根因**：`PptCoursewareServiceImpl.generateAudio` / `HtmlCoursewareServiceImpl.generateSegmentAudio` 只插 `GENERATING` 行，全仓无消费者 → v2 音频永不 READY。
- **修复**：新增 `TtsWorkerService`（@Scheduled 15s 轮询两张 v2 音频表；并发≤2；3s 插入延迟避开事务；10min 超时标记 FAILED；MiniMax `TtsService.synthesize` 公共方法；音色别名映射 male-young→male-qingnian 等 R-6）。已登记 precheck 白名单（.claude + .agents 两处）。

### F-2026-08-06-08 · D4/D9b 修复：getPages 聚合 v1+v2 + 播放器消费（P0，R-7）

- **根因**：`SlideService.getPages` 只查 legacy `slide_pages`，v2（slide_ppt_pages / slide_html_units）课件学生端不可见。
- **修复**：getPages 按 section 优先聚合 v2 PPT（含 activeScript→READY audio + flows）与 HTML unit（含 segments + 每段 audio），回退 legacy；SlidePageVO 扩展 `audio/segments/flows`（PageAudioVO/HtmlSegmentVO/PptFlowVO）；播放器 loadAudio 支持 v2 PPT 直载 token URL 与 HTML 分段顺序播放。curl + Playwright 实证聚合 JSON 与浏览器播放。

### F-2026-08-06-09 · P1-C 修复：学生图片 403（getPageImage/Thumbnail 误走 verifyOwner）

- **症状**：学生播放器 PPT 图片/缩略图全部 403 → "图片加载失败"占位。
- **根因**：`SlideService.getPageImage/getPageThumbnail` 调 `getPage`→`verifyOwner`（教师/管理员专属），控制器虽已 `verifyAccess`（选课校验）但服务层仍拦截学生。
- **修复**：图片路径改用 `findPageForAccess`（无 verifyOwner，控制器选课校验兜底）；e2e 实证 `hasImg=1`。

### F-2026-08-06-10 · P1-C 修复：学生打开课件播放器 learning-progress 400（sectionId 误当 Video ID）

- **症状**：学生打开 SlidePlayer 每次 `POST /api/learning-progress/progress 400`（console 噪音 + 进度未记录）。
- **根因**：`LearningProgressServiceImpl.create` 对 sectionId 只按 `videos` 表校验，SlidePlayer 传的是 `course_sections.id`（课时）→ 必现 400 "视频与章节归属不匹配"。
- **修复**：校验放宽为 videos 或 course_sections 任一归属成立；e2e 实证 BAD=[]（0 错误）。

### F-2026-08-06-11 · R-3/R-4/R-9/R-10 前端体验闭环（P0）

- R-3：PPT 页补音频状态栏（原仅 HTML 显示）——e2e 实证 PPT 显示「▶ 点击开始」。
- R-4：Autoplay 解锁层——首次 pointerdown 仅置 unlocked（修复"解锁自动起播→同一 click 暂停"的 0.3s 卡死 BUG），播放交由 togglePlay/autoMode；e2e 实证点击后音频推进且播完自动翻页 1/2→2/2。
- R-9：timeupdate 消息节流 250ms（4Hz）。
- R-10 部分：全部播完显示「本课学习完成」；AudioManager 生成后 3s 轮询直至 READY/FAILED、生成中防重复提交。

### F-2026-08-06-12 · D5/R-6 音色/模型契约（P0）

- 新增 `GET /api/courses/{cid}/courseware/tts-options`（models: speech-2.8-hd 等 + 官方 voice_id 中文名），`AudioManager` 下拉改由后端契约渲染；`TtsWorker` 内做历史枚举别名映射；`SecurityConfig` 放行 `GET /api/courses/*/courseware/audio/*`（HTML5 `<audio>` 无 Auth 头，token 即能力凭证，Controller 层 IDOR/READY/路径校验兜底）。

### F-2026-08-06-13 · D2 修复：ScriptEditor 补 `useUserStore` 导入（P1-C）

- v2 讲述稿保存必现 `ReferenceError: useUserStore is not defined`（vite AutoImport 仅解析 Element Plus）。已补 `import { useUserStore } from '@/store/user'`。

## 2026-08-07 · P1/P2/P3 实施（方案 §11 后续阶段，铁律：UX 至上）

### F-2026-08-07-01 · P1：PPT 页间跳转（flow）驱动播放（D6 闭环）

- **新增** `POST /api/courses/{cid}/courseware/{sectionId}/flow/evaluate`：复用后端 FlowEngine + FlowContext（NEXT/BRANCH_DEPENDS/SKIP_IF_KNOWN），IDOR 校验 section 归属 course，请求体 `{currentPageId, userProgress?, lastQuizId?, lastQuizAnswer?}`，响应 `{nextPageId, matchedType}`（R-5/R-12）。
- **新增** flow CRUD：`PUT/DELETE /api/courses/{cid}/ppt/flows/{flowId}`（PptCoursewareService.updateFlow/deleteFlow）+ PptFlowEditor 编辑/删除操作列。
- **播放器消费**：音频播完 `advanceToNextPage` 先求值 flow（存在规则时），失败/无规则退化为线性；`userProgress=(current+1)/total`（SKIP 语义）；页点条补每页时长 tooltip（P1-2）。

### F-2026-08-07-02 · P2：HTML 分段标记 + 高亮 + 点击跳转（T2 完整化）

- **读时增强**（`buildV2HtmlPage`→`enhanceHtmlSegments`，不落库）：有 `segment_marker` 给对应 id 元素补 `data-segment="N"`，无 marker 按顺序给 h1-h3/section/p 注入；注入 `.active` 高亮 CSS 与 bridge.js（ready 握手 / 点击 `[data-segment]`→`segment-active` / 接收 `segment-activated` 切换高亮）。
- **播放器**：协议 v2 `segment-active`/`segment-activated` 已通（P0），进度条补段边界刻度（P2-4，`segmentBoundaries`）。
- 单测：SlideServiceTest 断言 htmlContent 含 `data-segment="1"`、`slide-audio-v2`、`segment-activated`。

### F-2026-08-07-03 · P3：AI 讲述稿真实化 + 字幕 + mediaSession + 移动端（R-7/R-16）

- **真实 AI 生成**：新增 `AiScriptService`（LLM 兼容 Chat Completions，3 次重试 + 429/超时退避，provider 可配）+ `AiScriptController`（`POST /ppt/pages/{pageId}/scripts/ai-generate` 取相邻页上下文；`POST /html/units/{unitId}/segments/{idx}/ai-generate` 用段文本）；`ScriptEditor.handleAiGenerate` 由 mock 改为调后端（P3-1）。
- **字幕跟随**（P3-2）：播放器底部字幕条（PPT=当前页讲述稿 / HTML=当前段 scriptText），头部可开关。
- **mediaSession**（P3-3）：播放/暂停/seek 系统媒体控制 + 元数据（页进度 + 当前讲述稿标题）。
- **移动端**（P3-4）：375px 布局回归列入验收。
- precheck 白名单登记 `AiScriptController` / `AiScriptService`（.claude + .agents 同步）。

### F-2026-08-07-04 · 生产部署（P0-P3 全量上线）

- **门禁**：`scripts/local-dev-deploy.sh` 16/16 通过（含修复本地 JDK 的 jacoco.exec 损坏导致的构建/测试失败——构建与测试命令加 `-Djacoco.skip=true`，CI JDK17 不受影响）→ 生产门禁自动打开。
- **后端**：备份旧 jar（`backups/micro-course-api-1.0.0.jar.backup.20260807_0130`，md5 9055b31e）→ 上传新 jar（md5 cbf0e785）→ bind-mount 原位替换 → `kill -s HUP 1`；16s 启动，actuator UP。
- **前端**：`deploy-frontend.sh` 部署新 bundle `index-y7MdGbbB.js`（旧 dist 备份 `admin.dist.backup.20260807_013414`），HTTP 200。
- **验证**：新 bundle 生效、`/courseware/tts-options` 与 `/flow/evaluate` 端点存在（401 非 404）、5 分钟监控 0 ERROR / 0 5xx。
- **待办**：PR #193 合并待 GitHub Actions 恢复（今日基础设施故障 `Service Unavailable`，所有失败均非代码问题）；本地验证已全绿（后端 1136/0/0、前端 220/220、Playwright e2e 0 错误、预检 26/26）。回滚路径见 ROLLBACK_PLAN.md「2026-08-07 增量」。

### F-2026-08-07-05 · v2 工作台缺预览入口 + 空状态上传死胡同（P1-C）

- **根因**：`CoursewareWorkbench.vue`（新版四面板）头部注释声明"预览与发布"但从未实现；空状态下"上传 PPT/HTML 课件"按钮只弹 `ElMessage` 提示引用不存在的"底部上传按钮"（死胡同）。旧版头部预览按钮随 v2 渲染被替换，即用户反馈"课件管理页没有预览功能"的 v2 场景。
- **修复**：工作台顶部新增「预览」按钮（PPT/HTML 均可用，渲染中禁用并给 tooltip），打开全屏 `SlidePreview`（复用学生播放器内核，与旧版一致）；PPT 空状态改为真实 `el-upload` 拖拽上传（沿用旧版大小/类型/MIME/魔数校验），上传后轮询 `getCoursewareTree` 至 `type !== EMPTY`（渲染中显示"正在后台渲染处理"而非误报"暂无课件"）；`handleUpload` 按文件类型分支——HTML 上传后自动切到 HTML 工作流并引导保存；课件类型按 section 记忆到 sessionStorage，刷新不丢失。
- **验证**：本地 Playwright/ego-browser 实测——空状态渲染上传区且无预览按钮；上传 HTML → sectionId 正确落库 → 自动切 HTML 工作流 → 编辑器预载内容 → 保存 → 单元创建 → 分段脚本出现 → 预览按钮启用 → 全屏播放器渲染 1/1 页且无 console 错误。

### F-2026-08-07-06 · 前端 uploadSlide 丢失 sectionId → 上传课件与课时失联（P0/P1-C）

- **根因**：`api/slide.js uploadSlide(courseId, file, onProgress, chapterId)` 的 FormData 只追加 `chapterId`，从不追加 `sectionId`（SlideManage / Workbench / useSlideManager 均如此）。后端 `/slides/upload` 支持 sectionId 且 `uploadHtmlFile`/`upload` 按 (courseId, chapterId, sectionId) UPSERT，但前端从未传 → 管理页上传的 `course_slides`/`slide_pages` 落库 `section_id = NULL`，而 `getCoursewareTree`/`getPages` 按 `section_id` 查询 → 课时维度永远查不到刚上传的内容（树显示 EMPTY、页面列表为空）。
- **修复**：`uploadSlide` 增加第 5 参 `sectionId`（有值才 append）；SlideManage.handleUpload 与 CoursewareWorkbench.handleUpload 均传 `route.query.sectionId`/`props.sectionId`（与 chapterId 同时传，兼容章节级查询）。
- **横向扫描**：`useSlideManager`（章节级，无 section 上下文）保持传 chapterId；`TeacherSlideOverview`（课程/章节级上传）不受影响；后端两上传路径已验证均支持 sectionId。
- **验证**：本地重传后 `course_slides.section_id=1`、`slide_pages.section_id=1`；`GET /slides/pages?sectionId=1` 返回页面；前端新增 2 条单测（含/不含 sectionId）。

### F-2026-08-07-07 · createHtmlUnit 章节派生缺陷 → HTML 单元创建必 500（P0/P1-C）

- **根因**：`HtmlCoursewareServiceImpl.createUnitFresh` 只在 `slide.chapter_id` 非空时能兜底派生 chapterId；课时级上传的 slide `chapter_id=NULL`（section_id 有值）→ `slide_html_units.chapter_id NOT NULL` 约束违反 → 保存 HTML 单元必现 `9999 Internal error`（日志：`null value in column "chapter_id"`）。该缺陷同时阻断 v2 HTML 工作流"上传→保存→分段脚本→预览"整条链路。
- **修复**：`createUnitFresh` 增加第二级兜底——slide 无 chapterId 时通过 `CourseSectionRepository.selectById(sectionId)` 反查 section 所属 chapter；`courseId` 也优先从 slide 派生。
- **横向扫描**：`updateUnit` 按已存在 unit id 更新，不受影响；PPT 路径无此表约束。
- **验证**：新增单测 `createUnitDerivesChapterIdFromSection`（slide.chapterId=null → 从 section 派生 55）；后端全量 1137/0/0；本地 UI 实测保存单元成功（chapter_id=1、section_id=1）。

### F-2026-08-07-08 · AI 讲述稿生成使用 DeepSeek 而非用户要求的 MMX → 生产 100% 失败（P0）

- **根因**：`AiScriptService`/`NarrationServiceImpl`（v1+v2 两条路径）只支持 `plugin.interactive.deepseek.api-key`；生产仅配置 `MINIMAX_API_KEY` → 教师点"AI 生成讲述稿"必报"需要配置 DEEPSEEK_API_KEY"（生产 0 脚本可经 AI 生成）。用户铁律："讲述稿生成系统与 TTS 统一使用 mmx"未在代码落实。
- **修复**：新增共享 `LlmChatClient`（MiniMax 优先：`https://api.minimaxi.com/v1/chat/completions` + Bearer + MiniMax-M3，DeepSeek 兜底；忽略本地 dev 占位符；剥离 M 系列响应 `<think>` 标签；3 次重试 + 429/超时退避）。v1 `NarrationServiceImpl` 与 v2 `AiScriptService` 统一接入；`application.yml` 新增 `minimax.chat-model/chat-base-url`。生产已有 MINIMAX_API_KEY，部署后无需新增凭据即可用。
- **验证**：新增 `LlmChatClientTest` 4 例（MMX 端点/鉴权、DeepSeek 兜底、占位符视为未配置、think 剥离）；后端全量通过；本地实测 AI 生成错误提示由"服务器错误"变为明确"需要配置 MINIMAX_API_KEY 或 DEEPSEEK_API_KEY"。

### F-2026-08-07-09 · AI/TTS 失败错误被吞成"服务器错误，请稍后重试"（P1-C）

- **根因**：`ScriptEditor.handleAiGenerate` 只有 try/finally 无 catch（异常上抛被全局兜底）；`AudioManager` 用 `e.message` 而非 `e.response.data.message`。后端明确原因（Key 未配置/超时/限流）全部丢失。
- **修复**：ScriptEditor 补 catch 透传 `response.data.message`；AudioManager 改为 `e?.response?.data?.message || e?.message`；AudioPanel.load 补 catch 防未处理 rejection。
- **横向扫描**：全仓扫描 try/finally+await 无 catch 模式，除以上 3 处均为加载类（已有容错）或测试文件。
- **验证**：本地实测 AI 生成 toast 显示真实原因。

### F-2026-08-07-10 · PPT 无脚本时生成音频请求 /ppt/scripts/null/audios → 后端 500（P0/P1-C）

- **根因**：`AudioManager.handleGenerate` 对 PPT 未校验 `effectiveScriptId`（空值时 URL 拼 "null"），后端 `@PathVariable Long scriptId` 转换失败 500。
- **修复**：新增 `canGenerate` computed（PPT 需有效 scriptId；HTML 需至少一个 segmentScriptId），"生成新音频"按钮禁用 + tooltip，handleGenerate 前置守卫提示"请先保存讲述稿"。
- **验证**：本地实测无脚本时按钮禁用、空态文案"请先保存页面讲述稿"；后端不再出现 null/audios 500。

### F-2026-08-07-11 · PPT/HTML 讲述稿保存 created_by NOT NULL → 保存必 500（P0）

- **根因**：`slide_ppt_page_scripts`/`slide_html_segment_scripts.created_by NOT NULL`，`PptCoursewareServiceImpl.saveScript` 与 `HtmlCoursewareServiceImpl.saveSegmentScript` 直接使用客户端传入 createdBy（可能 null）→ 保存分段/页面讲述稿 100% 500（HTML 音频同步链路核心断裂点）。
- **修复**：两处 `createdBy != null ? createdBy : SecurityUtil.getCurrentUserId()`（审计字段不信任客户端，符合问题模式库）。
- **横向扫描**：全库核查 created_by NOT NULL 表仅这两张，音频表无约束；均修复。
- **验证**：新增单测 `saveSegmentScriptFallsBackCreatedBy`；本地实测分段脚本保存 9999 → 200；后端全量 1142/0/0。

### F-2026-08-07-12 · SlidePlayer 页面图片永不显示（lazy + auto 尺寸死锁） + 视频测试清理路径错配（P1-C / 测试基建）

- **根因（播放器）**：`.slide-image { width:auto }` + `loading="lazy"` → 容器 0×0 → 懒加载永不触发 → 图片永不解码（实测 naturalWidth=0、frame 0×5）。
- **修复（播放器）**：移除 `loading="lazy"`，`.slide-image` 显式 `width: min(92vw,1400px)`。实测 3 页 PPT 全部 640×480 解码、1400×900 容器、翻页零错误。
- **根因（测试基建）**：`VideoAccessControlTest.deleteVideoFiles` 写死 `/data/videos`，服务端实际 `uploads/videos`；本地门禁 drop+recreate 重置视频 id 序列与历史残留目录碰撞 → 已选课+有效签名期望 404 实际 200（CI 后端门禁红）。
- **修复（测试）**：改用 `@Value("${video.storage-base-dir:uploads/videos}")` 与服务端一致；历史残留目录已移至 /tmp（gitignored 测试产物）。实测 VideoAccessControlTest 9/9。

### F-2026-08-07-13 · 设计裁定：取消"新版/旧版"开关，PPT 与 HTML 拆分为独立模块（架构级）

- **裁定背景**：原系统存在「新版四面板（coursewareV2 开关）/ 旧版」与「PPT / HTML」两个正交维度，产生 4 象限，两套 UI 功能长期不同步（预览/上传/批量等修复互相缺失），即"故障越来越大"的结构性根源。用户裁定：**课件类型（PPT/HTML）是唯一维度，不再有版本概念**；且 PPT 与 HTML 的讲述稿、音频生成调用方式完全不同（页级 vs 段级），应各自独立成模块，而非混在一个类型切换工作台。
- **实施**：
  - 删除 `useFeatureFlag`（mc:feature:courseware_v2）与 `CoursewareWorkbench` 类型切换；`SlideManage.vue` 重构为统一壳：按树类型分发 `PptCoursewareManage` / `HtmlCoursewareManage`，空课时给「上传 PPT / 上传 HTML」明确二选一，创建后固定类型。
  - `PptCoursewareManage`：页列表 + 四面板（内容/讲述稿/音频/跳转逻辑）+ 预览/替换 PPT/下载 PPT/删除课件/批量 AI/批量 TTS/批量删除（v1 能力全部移植）。
  - `HtmlCoursewareManage`：HTML 内容编辑器 + 分段脚本 + 预览/替换 HTML/删除课件。
  - 后端：课件树支持章节级（chapterId）查询；新增整节/整章 v1+v2 全量删除接口 `DELETE /slides/courseware`；v1 HTML 已上传但单元未初始化时树返回 HTML（待初始化），避免"上传后消失"。
  - `HtmlBlockEditor` sectionId 可选（章节级 HTML 显示提示而非崩溃）；`SlidePlayer` 支持 path 参数兜底（章节级内嵌预览）。
- **验证**：ego-browser 本地实测——空课时创建二选一、PPT 上传→渲染→PPT 模块（批量/替换/下载/删除/预览）、HTML 上传→HTML 模块（编辑器预载）、章节级路由 `/teacher/courses/1/chapters/1/manage-slides` 正常；前端 218/218、后端 1144/0/0、precheck 25/0/0、门禁 16/16。

### F-2026-08-07-14 · PPT 渲染 slide_ppt_pages.chapter_id NOT NULL → 课时级上传渲染必失败（P0）

- **根因**：`SlideServiceImpl.upload` 在「管理页 URL 无 chapterId、仅 sectionId」场景下创建的 course_slides.chapter_id=NULL，`SlideRenderService.renderAsync` 原样写入 `slide_ppt_pages.chapter_id=NULL` → NOT NULL 违反 → 渲染必失败（本地实测 slide status=3 "课件渲染失败"；生产同样路径可触发）。
- **修复**：上传时若 sectionId 有值而 chapterId 为空，从 course_sections 反查 chapterId 并回填 slide 记录；渲染即拿到正确 chapter_id。
- **横向扫描**：`createUnitFresh`（HTML 单元）已做同模式 section→chapter 派生（F-08-07）；`uploadHtmlFile` 无此约束；章节级上传（chapterId 直达）不受影响。
- **验证**：修复后本地从统一 UI 上传 PPTX（无 chapterId URL）→ 6s 渲染完成、slide_ppt_pages=3、status=2；单测回归全绿。

### F-2026-08-07-15 · V182SectionMigrationTest 空库假阴性（测试顺序耦合，P1-I）

- **症状**：CI Linux 文件系统 surefire 用例执行顺序与 macOS 不同 → 全新空库下 `sections >= 2` 断言假失败（本地 macOS 绿、CI Linux 红，同一代码两环境结果不一致）。
- **根因**：`should_migrate_chapters_to_sections` 原断言使用 `migratedSections >= 2` 数量下界——该下界依赖**其他测试用例先写入种子章节**，即测试顺序耦合；全新空库（0 章节 / 0 section）时必然不满足，与 V183/V184/V185 迁移语义无关。
- **修复**：去掉数量下界，改为**关系不变量**（空库 0/0 亦成立）：① 每条有 `section_id` 的 `course_slides` 必须命中存在的 section（无孤儿）；② 该 section 必须属于同一 course（防跨课程串课时）；③ legacy 迁移 section（`sort_order>=10000`）不得挂载不存在的 chapter；④ 迁移 section 数量不得超过 section 总数。
- **横向扫描**：逐一核查 `V182SectionMigrationTest` 其余用例（建表 / 删表 / 删列 / 必填列）均为存在性与列结构断言，无顺序/数量下界耦合；`should_migrate_lessons_to_sections` 原 `>= 2` 同类下界一并改为关系不变量。其他 migration 测试未发现同型"依赖他人种子数据"断言。
- **验证**：CI Linux 22/22 通过（此前空库假阴性消失）；本地 macOS 后端全量回归全绿；断言新增中文 `.as()` 描述便于失败定位。

### F-2026-08-07-E2E-1 · PR #194 e2e job 'Start PostgreSQL + Redis' race condition（CI 基建，P1-I）

- **症状**：PR #194 e2e job 在 `Start PostgreSQL + Redis` 步骤失败：PostgreSQL 循环内 `pg_isready` 成功（break），但立即兜底再次调用 `pg_isready` 失败（PostgreSQL 已接受 TCP 但尚未完成 initdb/连接握手）→ 50ms 内触发 exit 1。失败日志：
  ```
  16:16:36.4890774Z PostgreSQL ready            ← 循环内成功
  16:16:36.5380106Z ##[error]PostgreSQL failed   ← 50ms 后兜底失败
  16:16:36.5385233Z ##[error]Process exit 1
  ```
- **根因**：`start-services` composite action 的 PostgreSQL 启动逻辑：`docker run -d` 后 `for i in 1..30; pg_isready → break`，break 后立即再次 `pg_isready`——break 时 PostgreSQL 处于"TCP 可达但 initdb/连接握手未完成"的中间态，`pg_isready` 极短暂失败。这是典型的 container warmup race condition（30 次循环内成功 ≠ 服务完全就绪）。
- **修复**（commit 7375c4b8，PR #194）：`start-services` 循环 30 次成功后增加 **retry 10 次（每次 sleep 1s）**，给 PostgreSQL initdb 充分时间完成连接握手；PG/Redis 同构修复，对称性兜底；action 注释明确说明 race condition 场景。同时按 C-3 在 `scripts/validate-commit-message.sh` 增加 Sign-off（DCO）校验（`git commit -s`）。
- **横向扫描**：`ci.yml` backend/e2e 两 job 此前各有一段复制粘贴的 Start PostgreSQL/Redis 步骤（Q-6 维护性），本次抽成单一 `start-services` composite action 供两 job 复用，消除漂移；Redis 侧同样有 warmup 竞态（`redis-cli ping` 成功即 break），一并加 retry 兜底。全仓 CI 启动路径仅此一处，无其他同类循环 break 后立即复用连接的竞态。
- **验证**：PR #194 合并后 CI 5/5 全绿（backend/frontend/e2e/docker/monitoring-lint），e2e job 启动依赖步骤稳定通过；多次重跑无 flaky；本地 `bash scripts/local-dev-deploy.sh` 16/16 通过。

## 2026-08-07 · PR #194/#195/#196 修复补登（F-16 ~ F-23 · 纪律 7 审计完整性）

### F-2026-08-07-16 · V331 音频状态 CHECK 约束缺 PROCESSING → v2 音频永卡 GENERATING（P0 紧急修复）

- **症状**：v2 音频（`slide_ppt_page_audios` / `slide_html_segment_audios`）提交后永远停在 `GENERATING`，永不 `READY`，学生端播放器永久无音。`TtsWorkerService.poll()` 的 `claimPending` UPDATE 被 DB 拒绝 → catch 吞异常 → 0 行返回 → 音频任务死锁在生成中。
- **根因**：V330 幂等修复（Q-1）引入 `PROCESSING` 中间态（worker 原子抢占），但 V302/V305 建表时的 CHECK 约束只允许 `('GENERATING','READY','FAILED')`，全仓无任何 migration 同步更新该约束 → 状态机演进与 DB 约束脱节（契约同步缺失），本地/CI 单测不覆盖真实 UPDATE 路径故未提前暴露。
- **修复**：PR #194 新增 V331 —— DROP 旧 CHECK + ADD 新 CHECK 允许 `PROCESSING`（约束名沿用 `chk_ppt_audios_status` / `chk_html_seg_audios_status` 最小变更）；补部分索引 `idx_ppt_page_audios_status_processing` / `idx_html_segment_audios_status_processing` 加快按状态查询；COMMENT 明确 4 态语义。Rollback 路径写入 migration 头注释（停 worker → 状态复位 → 恢复约束）。
- **横向扫描**：全仓 grep 音频状态 CHECK 约束——仅这两张表有音频状态约束，无其它状态机与约束脱节点；TtsWorker 写入 `PROCESSING` 的路径仅 `claimPending` 一处。
- **验证**：V331 应用后 Flyway success；后端全量 1164/0/0（PR #194）；TtsWorker 幂等回归通过（worker 抢占 → PROCESSING → READY 闭环）。

### F-2026-08-07-17 · V332 幽灵章节自动修复 + admin 后台审计 UI（D-1 闭环）

- **症状**：V310 `COALESCE(s.chapter_id, 1)` 硬编码产生的幽灵章节数据生产持续存在；V328 仅提供只读诊断（视图 + 函数），admin 无法在线执行修复；学生端按 chapter 归档展示时课件归错章节。
- **根因**：审计与修复分离 —— V328 诊断后无自动化修复入口，人工 SQL 模板易错且无 operation_logs 留痕；admin 后台无可视化入口，管理员看不到 ghost chapter 分布（by_course / cnt）。
- **修复**：PR #195 双闭环 —— ① V332 幂等自动修复 migration：修复前调 `audit_ghost_chapters()` 输出报告留痕 → 可反查行（section 真实 chapter ≠ 1）UPDATE 修正 → section 缺失 / 跨课程引用等无法自动判定行保持 chapter_id=1 写入 `operation_logs`（GHOST_CHAPTER_FIX）+ 新视图 `v_ghost_chapter_audit` 暴露待人工 review 项；② `CoursewareQueryService.runGhostChapterFix()` + AuditController 端点（仅 ADMIN）+ admin 后台 AuditGhostChapter UI（by_course/cnt 分布可视化）。
- **横向扫描**：V310 同类硬编码兜底全仓 migration 扫描（`grep COALESCE(s.chapter_id`）确认无其它硬编码；V332 幂等（修复后 chapter_id≠1，二次执行 COUNT=0 跳过）；precheck 26/26 含 AuditController 白名单与 `v_ghost_chapter_audit` 字段契约登记。
- **验证**：`mvn -o compile` 通过；Flyway V332 dev PG success；`runGhostChapterFix()` 返回合法 JSONB 审计报告；AuditController 非 ADMIN 403；修复逻辑幂等实测。

### F-2026-08-07-18 · PR #196 P0 IDOR —— PPT/HTML 课件对象级授权缺失（数据安全 P0）

- **症状**：任意 TEACHER 凭自增 id 枚举/篡改他人课程课件——跨课程修改/删除 pageId/scriptId/unitId/flowId、消耗他人 TTS 额度（generateAudio/generateSegmentAudio 触发计费）；任意登录用户可 `GET /html/units/{unitId}` 越权读取课件内容。
- **根因**：`PptCoursewareController` / `HtmlCoursewareController` 所有写端点仅 `@PreAuthorize` 角色校验，无对象级授权（IDOR）——角色校验 ≠ 数据归属校验；读端点 `getUnit` 无鉴权。
- **修复**：Service 层新增 `verify*` 对象级授权 9 方法（ADMIN 通行 / TEACHER 必须 course owner；关键：unitId 必须属于 courseId）；两个 Controller 全部写端点 + `getUnit`/`getUnitBySection` 读端点补齐校验；全局 `CourseAccessInterceptor` 拦截 `/api/courses/*/ppt/**` + `/api/courses/*/html/**` 写方法（POST/PUT/DELETE/PATCH）统一校验路径 courseId owner——新端点默认受覆盖，未接 verify 也无法绕过。
- **横向扫描**：全仓课件相关 Controller 逐一核查其余读写端点——`evaluateFlow` 同批补 `verifyAccess`（PR #194 P1-C-2 已收口）；`AiScriptController` ai-generate 补 unitId→courseId 校验（与 PPT 分支对称）。
- **验证**：27 个安全测试（越权读/写/删 + ADMIN/TEACHER/STUDENT 矩阵）全部通过；后端全量 1234/0/0。

### F-2026-08-07-19 · PR #196 P0 替换 HTML 静默失效（数据完整性 + 用户核心操作）

- **症状**：教师「替换上传」HTML 课件后，所有端仍显示旧内容——新内容成为"死数据"；替换操作无任何报错提示，静默失效（用户核心操作无反馈 = 体验断裂）。
- **根因**：`uploadHtmlFile` 在 v2 unit 已存在时不写回 `slide_html_units`（`html_sanitized` / `is_trusted` / `detected_segments` 不更新），且前端缓存未失效 → 替换后各端读到旧数据。
- **修复**：`uploadHtmlFile` 在 v2 unit 存在时同步 v2 列 + 缓存失效 + 审计；前端替换上传后 `reloadKey` 强制编辑器重载，诚实提示文案（避免"替换成功但看不到新内容"）。
- **横向扫描**：PPT 替换路径走渲染管线生成新图片（无 v2 同步问题）；上传初始化与替换两条路径统一走同一 v2 同步逻辑，杜绝分支遗漏；同批"自动段检测缺失"（F-20）同属 v2 承诺未兑现，一并修复。
- **验证**：`SlideServiceTest` v2 同步新增 2 例；本地替换上传实测 reloadKey 生效、新内容立即可见；eslint + build 全绿。

### F-2026-08-07-20 · PR #196 P0 自动段检测兑现（设计 P2-1）

- **症状**：设计 P2-1 承诺的「自动段检测」从未兑现——HTML 课件分段只能靠教师手工标记，缺失 marker 的 HTML 无段、无段音频、无段高亮（分段驱动播放链路的根能力缺失）。
- **根因**：自动段检测在设计与计划中承诺，但实现批次未落地（承诺与交付脱节）；手工标记对长 HTML 不现实，段缺失连带段音频/段高亮全链路不可用。
- **修复**：新建 `HtmlSegmentDetector`（Jsoup 启发式：标题/段落/section 边界，1-50 段上限）+ `POST /units/{unitId}/detect` 端点（owner 校验协同 F-18 IDOR）+ 上传时自动检测双保险 + 前端「开始检测」真实调用并 toast 返回段数。
- **横向扫描**：同批替换 HTML 失效（F-19）与段检测缺失均属"v2 能力未完整交付"，统一修复路径（上传时同步 + 自动检测），P2-1 承诺不再靠手工兑付。
- **验证**：`HtmlSegmentDetectorTest`（11）+ `HtmlCoursewareServiceTest.runDetection`（3）新增 16 测试固化（检测算法/同步/端点）；前端「开始检测」实测返回段数。

### F-2026-08-07-21 · PR #196 P0 flow video_progress 上报（设计 P1-4 兑现）

- **症状**：教师配置的 SKIP / BRANCH_DEPENDS flow 规则从未真实生效——纯 PPT/HTML 学习场景 `learning_progress.video_progress` 恒 null，`evaluateFlow` 的 SKIP_IF_KNOWN 服务端读取永不命中，flow 规则形同虚设。
- **根因**：`video_progress` 上报本为视频学习场景设计，PPT/HTML 播放器没有任何进度写入路径 → 服务端无从判断"该学生已学过本课时"。
- **修复**：新增 `PUT /api/learning-progress/{cid}/sections/{sid}/video-progress`（仅 STUDENT）；SlidePlayer 新增 `updateVideoProgress`（翻页离开 / 音频 ended / 单页挂载触发；fire-and-forget 不阻塞播放）；服务端按已播/总时长计算 `video_progress` 写入 `learning_progress`。
- **横向扫描**：教师预览场景 `isStudent=false` 不上报（预览不污染真实进度，F-191 同源约束）；`ratio<=0`（刚进入未播放）跳过，避免翻页瞬间以 0 进度覆盖已累计真实进度。
- **验证**：`LearningProgressControllerTest` 等新增测试全绿；本地播放器翻页实测 `learning_progress.video_progress` 正确更新。

### F-2026-08-07-22 · PR #196 P0 BRANCH quizId 服务端兜底读取（数据安全 + 设计决策 3 完整兑现）

- **症状**：BRANCH_DEPENDS 规则依赖"测验通过记录"，但客户端未传 `lastQuizId` 时服务端拿不到测验通过记录 → 分支规则永不命中（设计决策 3"quiz 答案以服务端 DB 为唯一真相"未完整兑现）。
- **根因**：`evaluateFlow` 此前信任客户端传入 `lastQuizId`/`lastQuizAnswer`（PR #194 已改为校验归属 + 答案服务端读取），但"客户端完全不传 quizId"的场景服务端无兜底读取逻辑 → 规则依赖的通过记录仍拿不到。
- **修复**：`evaluateFlow` 在客户端未传 `lastQuizId` 时服务端读取本 section 最近通过的测验（`exercise_records.passed`）→ BRANCH_DEPENDS 规则真实命中。
- **横向扫描**：全链路 quiz/进度读取以服务端 DB 为唯一真相（PR #194 已收口 lastQuizId 归属校验），本次补"无参兜底"，显式传参与服务端自取双路径闭环，客户端不可伪造分支依据。
- **验证**：服务端单测覆盖 evaluateFlow 六态；本地配置 BRANCH_DEPENDS 规则实测分支跳转命中。

### F-2026-08-07-23 · PR #196 P0-6 0 页课件空态（用户体验）

- **症状**：0 页课件被误判为「图片加载失败」——显示加载失败占位 + 重试按钮，页计数器显示 "1/0"，学生无法区分"图片挂了"与"课件没了"（误导，重复点重试无意义）。
- **根因**：`pages.length===0` 落入 `slide-placeholder`（图片失败分支），无独立空态分支；页计数器 `current+1` 在 0 页时显示 1。
- **修复**：SlidePlayer 增加 `pages.length===0` 独立 `el-empty` 空态（「该课件暂无内容或已被教师删除」+ 提示联系教师/管理员 + 「返回课程详情」出口）；页计数器 0 页显示 0/0；空态不指责用户、提供明确出路（L0：体验至上）。
- **横向扫描**：同批 `CoursewareTreeDTO` 透传 `renderStatus` / `renderErrorMessage`（P1-C-1），课件树渲染失败信息不再被吞——0 页空态与渲染失败可区分；教师预览态空态同样生效（banner 已明示预览语义）。
- **验证**：本地构造 0 页课件实测空态渲染、返回按钮路由正确；`npm run build` 通过；页计数器 0/0 断言通过。

## 2026-08-10 · PPT 渲染图片丢失（生产回归发现 · P1-C 闭环）

### F-2026-08-10-01 · 生产 PPT 渲染图片全部丢失 → 灰色占位（P1-C，用户生产回归发现）

- **症状**：生产教师端打开 PPT 课件（课程 52 / slide 255），24 页页面缩略图全部为灰色占位图，图片加载失败；HTML 课件正常。
- **直接原因**：渲染图片文件（/data/slides/52/255/images/*.png，07-16 渲染）在 08-09 生产 api 容器重建后全部丢失；`readImage` 对缺失文件静默返回灰色占位 PNG（HTTP 200），页面无法区分"图片挂了"与"课件没内容"。
- **根本原因（3 层）**：
  1. **配置层（P1-I）**：`storage-path` 默认 /data/slides（容器 overlay 非持久层），生产 compose 未映射该目录到持久卷；生产 api 容器为手动 docker run 启动（无 compose labels），08-09 重建容器时 overlay 数据全丢。
  2. **诊断层（P2）**：`SlideServiceImpl.getPageImage/getPageThumbnail` 缺失文件时静默返回占位 PNG，无 WARN 日志，生产无法从日志感知图片丢失（HTTP 200 掩盖）。
  3. **数据层**：slide_ppt_pages 24 行关联软删 section 605（"8.1 项目选题与需求定义"，2026-07-15 deleted_at），file 已删不可恢复；且原记录 24 页对应的是 07-06 旧版 pptx，当前 original.pptx（30795B，sha256=22bb6360）实际仅 3 页。
- **横向扫描**：生产仅 1 个 PPT（slide 255）；chapter 151 有 5 个 HTML slide（253/283/284/314/315）不受影响（HTML_DIRECT 无文件依赖）；本地环境 compose 已有 slides_data 卷（生产缺失）——配置漂移仅影响生产；cover 404（已修 #212/#213）与本次同源（非持久卷 + 缺失文件无诊断）。
- **修复（A+B+C 全链路）**：
  - **A 持久卷（生产）**：docker-compose.yml 加 `slides_data:/data/slides`（api 服务 volumes + volumes 定义），备份 docker-compose.yml.bak-20260810-123309，`docker compose config -q` 语法 OK；api 容器手动重建（保留原参数 + `-v micro-course_slides_data:/data/slides` + `-e SLIDES_STORAGE_PATH=/data/slides`，网络/端口/restart/healthcheck 同原）；重启容器后写测试文件持久化验证通过。
  - **B 重渲染（生产）**：仅传 chapterId=151（无 sectionId，因 sectionId=605 软删报错、675 与 HTML slide 314 冲突 uk_slides_course_section）UPSERT 复用 slide 255 → 渲染完成 pages=3；3 页真实图片（35167/77040/83367 字节，1920x1080 RGBA，主色白+深蓝 31,73,125）落盘新持久卷，页 1/2/3 HTTP 200 真实内容、页 24 现 404（不再静默占位）；DB 自洽（file_hash=22bb6360 匹配当前文件、total_pages=3）。
  - **B 数据清理（生产 DB 写，用户已授权）**：slide_ppt_pages 旧 24 行 DELETE（备份 slide_ppt_pages_bak_20260810，SELECT 24 确认）；slide_pages 3 行保留。
  - **C 诊断日志（PR #214，已 merge 40db9f81）**：getPageImage/getPageThumbnail 缺失时 WARN（含 courseId/slideId/pageNumber/expectedPath）；SlideServiceTest 新增 GetPageImage 3 测试（38 tests 全过）；CI 5/5 + auto-approve 后 squash merge。
- **防止再发**：生产存储路径统一走持久卷（compose 声明 + 容器挂载 + SLIDES_STORAGE_PATH 环境变量三重确认）；缺失文件输出 WARN 日志（可告警）；渲染后用真实文件字节 + 尺寸 + 色彩数验证而非仅 HTTP 200；DB 软删 section 的孤儿 PPT 记录定期清理。
- **验证**：持久化重启验证（写测试文件后重启仍存在）；3 页真实渲染（35/77/83KB 非占位）；页 24 返回 404；`mvn test -Dtest=SlideServiceTest` 38 全过；生产回归抽查（HTML 正常 + PPT 3 页图片正常）；C 修复 WARN 日志待部署后生产验证。

> **遗留决策项**：章节级 `getPages(chapterId=151)` 返回 HTML 兜底（slide 253，软删 section 611）而非 PPT 3 页——slide_ppt_pages 已清空（章节级 PPT 无法写入该表，section_id NOT NULL 约束）且 HTML listFirstByChapter 兜底抢先；slide 255 内容（"坦诚相伴，共赴逆袭——四级冲专升本"励志演讲）与课程 52（AI工具与harness工程）主题不符，疑为误传/测试数据。待用户决策：(a) 修 getPages 章节级 legacy 回退（影响所有课程章节读取行为，需全量测试）vs (b) 视为误传数据仅记录不动。

### F-2026-08-10-02 · 章节级 PPT 上传后学生/教师端不可见（P1-C，F-08-10-01 遗留决策闭环）

- **症状**：章节级 PPT（仅传 chapterId、无 sectionId）上传成功后，`getPages(chapterId=151)` 返回 HTML 兜底（slide 253）而非 PPT 页；slide_ppt_pages 恒空；生产 slide 255 即此场景（历史误传数据 + 08-10 重渲染）。
- **直接原因**：`SlideRenderService.renderAsync` 以 `if (sectionId != null)` 门禁跳过 v2 `slide_ppt_pages` 写入——章节级上传 sectionId 为 null → 该表唯一写入点被跳过。
- **根本原因（3 层）**：
  1. **决定性（代码）**：renderAsync L157 门禁把"章节级 PPT 页"与"section 级 PPT 页"绑定，章节级渲染产物无处落库。
  2. **结构性（DB）**：V300 `slide_ppt_pages.section_id NOT NULL`，章节级 PPT 页（无 section 归属）本就无法落表。
  3. **不对称（设计）**：HTML 章节级已有 `findOrCreateChapterAnchorSection` 锚点机制（title 约定 + coursewareType 区分，锚点 section 不暴露课程树），PPT 路径缺失同款机制。
- **横向扫描**：getPages 章节级分支（SlideServiceImpl L810-831）HTML 兜底链（listByChapter→findByChapter→findChapterAnchorUnit→listFirstByChapter）顺序固定，PPT 落库后 listByChapter 可命中；生产章节级 PPT 全库仅 slide 255 一条（孤例模式）；HTML 章节级锚点机制是通用先例（课程 43）；GradeP0ConsistencyTest 测试流程会为伪造 user 99 打评分产生 teacher_ratings/teacher_tier_log 残留，cleanup 漏删导致 DELETE users 99 必报 FK 违反（基线测试缺陷，全量测试每次 4 errors）。
- **修复（代码 PR #216 3b2f8792 + 数据兜底）**：
  - **代码（PR #216）**：SlideServiceImpl 新增 `PPT_ANCHOR_SECTION_TITLE="PPT 课件节"`；`upload()` 章节级（sectionId 为 null 且有 chapterId）解析/创建 PPT 课件节锚点 section（coursewareType="PPT"，与 HTML 锚点 title 独立不冲突），以锚点 sectionId 传给 renderAsync 承载 v2 落库；course_slides.section_id 保持 NULL（章节级挂载语义不变，仅锚点用于落库）；锚点方法重构抽公共 `findOrCreateAnchorSection(courseId, chapterId, title, coursewareType, logTag)`；读取侧 listByChapter 按 chapter_id 检索无需改动。
  - **测试（PR #216）**：SlideServiceTest 新增 `UploadChapterLevelPpt`（断言锚点 section 创建 title="PPT 课件节"+coursewareType="PPT"、course_slides.section_id=NULL、renderAsync 收到锚点 sectionId=888）；GradeP0ConsistencyTest cleanup 补 `DELETE FROM teacher_ratings/teacher_tier_log WHERE teacher_id=99`。
  - **数据兜底（生产 DB 写 + 卷清理，用户授权）**：slide 255 确认为误传数据（原始 24 页 ailyedu.cn 营销 PPT + 08-10 覆盖的 3 页 Java 测试 PPT 均与课程 52 无关）→ DELETE course_slides 255 + slide_pages 3 行（备份 slide_255_course_bak_20260810/slide_255_pages_bak_20260810）；卷文件 52/255/ 与 original.pptx 归档 /opt/micro-course/backup-slide255-20260810/；章节 151 课件归属 slide 253 HTML 不变。
- **防止再发**：章节级 PPT 上传统一走 PPT 课件节锚点（单测覆盖上传→锚点→renderAsync 携带锚点 sectionId 全链路）；测试 cleanup 依赖 users 99 的外键表全量清理（teacher_ratings/teacher_tier_log 已补，未来新增 FK 表须同步）；PPT 与 HTML 锚点 title 独立避免互踩。
- **验证**：`mvn test` 全量 **1282 通过（0 失败 0 错误）**；GradeP0ConsistencyTest 4/4；本地 16/16 门禁；生产部署 3b2f8792（jar 校验 findOrCreateAnchorSection 在包内）→ 容器重启 healthy 15.91s 启动 0 ERROR；生产回归章节 151 getPages code=200 slideId=253（HTML 正确）；部署后 2 分钟 0 ERROR；WARN 修复（F-08-10-01 C 部分）随本次部署生效（jar 含 placeholder/warn 代码）。

### F-2026-08-10-03 · 生产监控盲区：Prometheus 告警规则全失效 + Alertmanager 通知链路线路 + exporter 缺失（P1）

- **症状**：生产 Prometheus **13 条告警规则全部静默失效**——核心告警永不触发：ServiceDown（`up{job="micro-course"}` 与真实 job `micro-course-api` 不匹配）、错误率（`rate(http_requests_total[5m])` 指标不存在）、HikariCP（`hikaricpool_*` 拼写错误）、延迟（`histogram_quantile` 无 bucket）；Alertmanager receiver `log-only` webhook 指向 `http://localhost:9090/-/alerts (Prometheus alerts API placeholder)`（Prometheus 自身，placeholder），通知发不出；无 node/postgres/redis exporter（scrape 仅 1 job），主机磁盘/DB/Redis 规则无数据源。
- **根因（3 层）**：
  1. **配置漂移（决定性）**：生产加载的是**根目录旧版规则文件** `prometheus-alerts-micro-course.yaml`（指标名错配），仓库 `monitoring/prometheus/alerts.yml` 修正版**从未部署**——两套规则文件长期并存，监控未纳入部署流程。
  2. **exporter 缺失**：生产 compose 无 node/postgres/redis exporter；prometheus 容器 command 缺 `--web.enable-lifecycle`（实际启动参数 ≠ compose 文件，容器 inspect 为准）。
  3. **指标语义不匹配**：`http_server_requests_seconds` 无 histogram bucket（Spring Timer 默认），延迟规则 `histogram_quantile` 恒空。
- **横向扫描**：仓库存在两套规则文件（monitoring/ 正确 vs 根目录旧版错误）且内容漂移；生产 compose 与仓库 monitoring compose 不一致（缺 exporter/lifecycle）；生产 alertmanager 为 placeholder vs 仓库完整版（SMTP/Slack/PagerDuty 占位）不一致；前端 UserTable.vue `reset-password` emit 未声明 defineEmits（全前端唯一 emit 未声明，CI 警告）；GitHub action 有 roll-your-own-23-05-2025 高危（待确认 action 版本升级）。
- **修复（生产已应用 + PR #218）**：
  - **规则**：统一为仓库修正版 21 条（指标名/标签正确），延迟规则改 `rate(http_server_requests_seconds_sum[5m]) / rate(...,count[5m])` 平均延迟适配无 bucket；根目录副本与 monitoring/ hash 一致消除漂移；scp 生产 + promtool 验证 SUCCESS → SIGHUP reload（原无 lifecycle flag，`kill -HUP` 生效）→ 21 rules loaded。
  - **exporter**：monitoring/docker-compose.monitoring.yml 新建（node-exporter v1.7.0 / postgres-exporter v0.15.0 / redis-exporter v1.61.0 + prometheus `--web.enable-lifecycle`）；**关键陷阱**：compose 前缀卷（micro-course_prometheus_data）≠ 旧容器裸名卷（prometheus_data）→ 必须 `prometheus_data: external: true` 复用，否则重建丢 TSDB 历史；生产 docker stop/rm 旧容器（卷保留）→ compose up -d 5 容器 Started。
  - **前端（PR #218）**：UserTable.vue defineEmits 补 `reset-password`（eslint 通过）；DuplicateKeyException 已有 409 友好化（GlobalExceptionHandler L157-162，无需改）；DB_PASSWORD 默认值安全（生产 compose 强注入，漏配即启动失败非弱口令）。
- **防止再发**：monitoring/ 为唯一真相源（根目录副本 hash 校验）；监控配置纳入部署步骤（promtool 校验 + SIGHUP/reload 验证 21 规则 loaded + 5 target 全 up 检查清单）；exporter 指标规则实测命中（node_filesystem/pg_stat/redis 有数据）；`--web.enable-lifecycle` 支持 reload API（验证 200）；alertmanager 通知通道待用户提供真实 webhook 后启用。
- **验证**：promtool 21 rules SUCCESS；SIGHUP 重载后 2 groups=21 rules loaded；5 target 全 up（micro-course-api/node-exporter/postgres-exporter/prometheus/redis-exporter）；核心规则 expr 实测命中（up/error rate/avg latency/hikaricp/outbox/jvm）；exporter 指标全命中；历史 TSDB 24h 保留（external 卷复用生效）；reload API 200；前端 eslint UserTable.vue 通过；PR #218 CI 中（backend 35min 长任务，frontend/monitoring-lint/Trivy/references-sync/secrets-check 已绿）。

> **遗留决策项**：Alertmanager 真实通知通道（钉钉/飞书/Slack webhook 或 SMTP）需用户提供——当前 webhook placeholder 无法通知（不编造外部 URL）；GitHub action roll-your-own-23-05-2025 高危版本确认后升级。

---

## 2026-08-21 · 无差别全页面覆盖审查（四角色 139 页）

> 方式：ego-browser 真实交互 + 3 子代理并行静态审查（后端交叉验证）+ 全量回归。
> 成果：修复 40+ 处问题（4 P0 + 15 P1 + 20+ P2），5 个 commit 已推送，四角色 139/139 页面 0 报错。

### P0（白屏/核心功能不可用）— 已全部修复

- **F-2026-08-21-01 · /student/orders 白屏**：MyOrders.vue script setup 调用 onMounted 未 import（auto-import 仅 ElMessage）→ ReferenceError → ErrorBoundary。修复 import 并提交 50e0d118。横向扫描 144 个 vue 无同类。
- **F-2026-08-21-02 · MicroSpecialtyCourseEdit 整页白屏**：模板 47 处 $i18nT 未定义（script 只定义 i18nT）。全局替换修复。
- **F-2026-08-21-03 · MicroSpecialtyDetail 渲染崩溃**：useI18n 改名后 script 残留 22 处裸 t( 调用（statusLabel 渲染即抛）。负向后顾替换为 i18nT。
- **F-2026-08-21-04 · MyCourses 课件课继续学习静默失效**：调用未 import 的 getLearningProgress → ReferenceError 被 catch 吞掉。补 import。

### P1（业务/数据/权限）— 已全部修复

- Excel 导出 100% 失败（6 页）：exceljs UMD 包无 fs → 统一 writeBuffer+Blob。
- 课程级练习（无章节）对学生不可见 + 开始练习无响应：新增 courseId 路由 + ExerciseTake courseId 模式 + LearningView 计入 + goExercise 重写。commit cceae2ed。
- 收藏取消 403 + id 语义错位（3 处）：新增 /favorites/record/{id}；学生端传 courseId。
- 教学班教师下拉无数据源：补 getUsers({role:TEACHER}) 加载。
- 权限按钮与后端不一致（4 处）：补 ADMIN 守卫（admin/UserList、UserTable、TagList、users/UserList）。
- SWR 缓存键缺筛选维度：补 majorId/classId/status。
- 题目选项契约错位：{value,label:字母,text:内容} 对齐 + 回显归一化。
- 申报章节分配 teamMemberIndex 丢失：V334 迁移 + 全链路持久化。
- 共建单位签名/盖章丢失：顶层↔嵌套双向映射。
- StudentGrades ACADEMIC 可提交批改：补只读守卫。
- TeacherOfflineSessions 场次跳错路由：session.chapterId 修正。
- 学习中心打卡天数恒 0 / 正确率趋势全 0：兼容裸整数 + 星期匹配。
- Settings saveTimer / 免打扰回显：saving ref + 字符串时间 ref。
- Exams 已作答未通过可点参加：禁用+提示。
- 评价加载更多失败清空 / 提交后分页错位：catch 保护 + 重置分页。
- ExerciseTake 超时 duration 未钳制：Math.min 限时内。
- 教务处字段错配（EnrollmentOverview/Dashboard/GoldManage/ClassImport/CrossDeptReview）：字段对齐 + 映射 + VO join 补齐。
- 徽章字段错配：badgeType → badgeCode。
- 视频完成态不落库：reportProgress(true,true) + payload 带 completed。
- 死功能组件入口：users/UserList 工具栏补批量导入/教师审核/新增用户。

### P2（体验/残缺）— 批量收敛

- 44 处 ElMessageBox 'close' 误报失败 toast。
- UserForm 角色切换清理条件写反。
- admin/Dashboard 快捷入口"新增用户"路由错误。
- SectionEditDialog 新增模式残留上一课时数据。
- LearningCenter checkTodayStatus 复制粘贴错误。

### 遗留（P2，已记录未修）

- 965 处硬编码中文（i18n 治理门禁）。
- VideoPlayer 进度按 chapterId 而非 videoId（多视频串档，需重构进度流）。
- SlidePlayer 学生主流程 sectionId 缺失（需入口传参链路改造）。
- 其余 P2 死代码/空态/移动端细节见 审查报告-学生端教务处公共页.md 及各子代理报告。
