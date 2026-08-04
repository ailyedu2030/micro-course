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
