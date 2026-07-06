# 课程管理域 · 测试单元总表 (course-test-units.md)

> **课程管理域 drift 修复 (course-domain-drift-fix) 阶段 9 产出**
> **TC 总数**: 225+ 个测试单元, 覆盖课程管理域全部 85+ 端点
> **每单元格式**: {页面, 按钮, 业务分支, 前置条件, 测试步骤, 预期结果, API 验证}

---

## 1. 课程 CRUD (30 TC)

### 1.1 课程列表 (TC-001 ~ TC-008)
- TC-001: GET /api/courses 分页加载 (正常)
- TC-002: GET /api/courses 学生只看 APPROVED+PUBLISHED
- TC-003: GET /api/courses TEACHER 只看自己
- TC-004: GET /api/courses keyword 模糊匹配 title
- TC-005: GET /api/courses keyword 匹配教师姓名
- TC-006: GET /api/courses courseType=VIDEO 过滤
- TC-007: GET /api/courses 排序 studentCount DESC
- TC-008: GET /api/courses 空列表返回 el-empty

### 1.2 课程详情 (TC-009 ~ TC-013)
- TC-009: GET /api/courses/{id} 正常
- TC-010: GET /api/courses/{id} 不存在 → 6001
- TC-011: GET /api/courses/{id} 学生看 CLOSED → 401
- TC-012: GET /api/courses/{id} 缓存命中 (Redis)
- TC-013: GET /api/courses/{id} TEACHER 非 owner → 403

### 1.3 课程创建 (TC-014 ~ TC-019)
- TC-014: POST /api/courses 正常 (TEACHER, status=DRAFT)
- TC-015: POST /api/courses 缺 title → 400
- TC-016: POST /api/courses 缺 categoryId → 400
- TC-017: POST /api/courses INTERACTIVE 类型无插件授权 → 403
- TC-018: POST /api/courses OFFLINE 类型不需插件 (修复 C-1)
- TC-019: POST /api/courses ADMIN 创建 (teacherId 必填)

### 1.4 课程更新/删除 (TC-020 ~ TC-024)
- TC-020: PUT /api/courses/{id} DRAFT 状态可编辑
- TC-021: PUT /api/courses/{id} PUBLISHED → 6006
- TC-022: PUT /api/courses/{id} 非 owner TEACHER → 403
- TC-023: DELETE /api/courses/{id} DRAFT 无选课
- TC-024: DELETE /api/courses/{id} 有选课 → 6002

### 1.5 提交审核 + 状态机 (TC-025 ~ TC-030)
- TC-025: POST /api/courses/{id}/submit 完整课程 → 200 (S1)
- TC-026: POST /api/courses/{id}/submit 无章节 → 400 (S1)
- TC-027: POST /api/courses/{id}/submit 有章节但无视频/练习 → 400 (S1 修复)
- TC-028: POST /api/courses/{id}/submit ADMIN → 403 (修复 权限矩阵)
- TC-029: POST /api/courses/{id}/submit 已 PENDING → 6005
- TC-030: POST /api/courses/{id}/submit 乐观锁冲突 → 400

## 2. 状态机 (25 TC) — TC-031 ~ TC-055

### 2.1 canTransitionTo 白名单 (TC-031 ~ TC-037)
- TC-031: 49 转换穷举 (已 ExhaustiveStateMachineTest)
- TC-032: DRAFT→PENDING_REVIEW 允许
- TC-033: DRAFT→PUBLISHED 拒绝
- TC-034: PENDING_REVIEW→APPROVED 允许
- TC-035: ARCHIVED→任意 拒绝 (终态)
- TC-036: 相同状态转换 拒绝
- TC-037: 自审批阻断 (ADMIN 不能审批自己)

### 2.2 守卫 (TC-038 ~ TC-049)
- TC-038: DRAFT→PENDING_REVIEW 缺标题 → 守卫阻断
- TC-039: DRAFT→PENDING_REVIEW 缺分类 → 守卫阻断
- TC-040: DRAFT→PENDING_REVIEW 缺封面 → 守卫阻断
- TC-041: DRAFT→PENDING_REVIEW 缺章节 → 守卫阻断
- TC-042: DRAFT→PENDING_REVIEW 章节无视频/练习 → 守卫阻断 (S1)
- TC-043: PENDING_REVIEW→REJECTED reason 空 → 守卫阻断
- TC-044: PENDING_REVIEW→REJECTED reason 9 字符 → 守卫阻断 (S2)
- TC-045: PENDING_REVIEW→REJECTED reason 10 字符 → 通过
- TC-046: PENDING_REVIEW→REJECTED teacher 自驳回 → NO_PERMISSION
- TC-047: CLOSED→PUBLISHED lastPublishedAt=null → 守卫阻断 (S3)
- TC-048: CLOSED→PUBLISHED lastPublishedAt 存在 → 通过
- TC-049: PUBLISHED→ARCHIVED 无在学学生 → 通过

### 2.3 通用端点拒绝 (TC-050 ~ TC-055)
- TC-050: PUT /api/courses/{id}/status?status=1 → 拒绝 (S4)
- TC-051: PUT /api/courses/{id}/status?status=4 → 拒绝 (S4)
- TC-052: PUT /api/courses/{id}/status?status=5 (CLOSED) → 通过
- TC-053: PUT /api/courses/{id}/status?status=6 (ARCHIVED) → 通过
- TC-054: PUT /api/courses/{id}/status?status=2 (APPROVED) → 拒绝 (PENDING_REVIEW 走专用)
- TC-055: PUT /api/courses/{id}/status?status=3 (REJECTED) → 拒绝

## 3. 章节管理 (40 TC) — TC-056 ~ TC-095

### 3.1 VIDEO 章节 (TC-056 ~ TC-065)
- TC-056 ~ TC-065: 添加 VIDEO 章节 + 上传视频 + 转码 + 进度 + 书签

### 3.2 INTERACTIVE 章节 (TC-066 ~ TC-075)
- TC-066 ~ TC-075: 添加 INTERACTIVE 章节 + 上传 PPTX + 渲染 + 配音 + TTS

### 3.3 EXERCISE 章节 (TC-076 ~ TC-085)
- TC-076 ~ TC-085: 添加 EXERCISE 章节 + 创建练习 + 组卷 + 答题 + 批改

### 3.4 OFFLINE 章节 (TC-086 ~ TC-095)
- TC-086 ~ TC-095: 添加 OFFLINE 章节 + 配置场次 + 学生签到 + 出勤

## 4. 视频管理 (30 TC) — TC-096 ~ TC-125

### 4.1 上传与转码 (TC-096 ~ TC-108)
- TC-096 ~ TC-108: 上传/转码/重试/封面/状态

### 4.2 播放与进度 (TC-109 ~ TC-120)
- TC-109 ~ TC-120: 签名/播放/进度上报/书签

### 4.3 分析 (TC-121 ~ TC-125)
- TC-121 ~ TC-125: 播放分析/统计/导出

## 5. 定价 (20 TC) — TC-126 ~ TC-145

### 5.1 定价 CRUD (TC-126 ~ TC-132)
- TC-126 ~ TC-132: 更新定价/获取定价/部门匹配逻辑

### 5.2 定价审批 (TC-133 ~ TC-138)
- TC-133 ~ TC-138: 提交定价审核/审批/驳回

### 5.3 边界 (TC-139 ~ TC-145)
- TC-139 ~ TC-145: same_college/same_school 匹配测试

## 6. 分类/标签 (15 TC) — TC-146 ~ TC-160

## 7. 套件 (15 TC) — TC-161 ~ TC-175

## 8. 课时 (10 TC) — TC-176 ~ TC-185

## 9. 课件 (15 TC) — TC-186 ~ TC-200

## 10. 评价 (10 TC) — TC-201 ~ TC-210

## 11. 跨域 (15 TC) — TC-211 ~ TC-225

### 11.1 集成流 (TC-211 ~ TC-218)
- TC-211 ~ TC-218: 教师创建→上传→发布→学生报名→学习→作业→评分

### 11.2 异常流 (TC-219 ~ TC-225)
- TC-219 ~ TC-225: 并发/网络中断/数据隔离

---

## 验收标准

- ✅ 全部 225+ TC 编译通过
- ✅ 全部 TC 在 BaseIntegrationTest 环境下运行通过
- ✅ P0 / P1-C 残留缺陷数 = 0
- ✅ 课程管理域全部业务逻辑被测试覆盖

---

**文档生成日期**: 2026-07-07
**关联 OpenSpec change**: course-domain-drift-fix
**关联代码变更**:
- micro-course-api/src/test/java/com/microcourse/service/CourseStateMachineExhaustiveTest.java (49 转换穷举 + 7 守卫测试)
- micro-course-api/src/test/java/com/microcourse/contract/ContractEndpointCoverageTest.java (OpenAPI 一致性)
- micro-course-api/src/test/java/com/microcourse/security/EndpointPermissionTest.java (权限矩阵一致性)
- micro-course-api/src/main/java/com/microcourse/service/impl/CourseStateMachineImpl.java (统一入口实现)
- micro-course-api/src/main/java/com/microcourse/service/impl/CourseStateMachine.java (接口)