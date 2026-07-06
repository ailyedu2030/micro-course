# Tasks: 全量回归测试与缺陷闭环验证

> **OpenSpec Change**: `full-coverage-redteam-testing`
> **Schema**: spec-driven
> **进度追踪**: `- [ ]` 复选框格式 (OpenSpec apply 阶段自动识别)
> **单任务限制**: ≤ 2 小时

---

## 1. 阶段 0 — 环境修复

- [x] **1.1 修复 OpenSpec CLI 残缺 (simdutf)**
  - **验收**: `openspec --version` 返回 1.5.0 而非 Abort trap
  - **已完成**: 2026-07-06 18:13 sudo ln -sf 加 33.dylib 链接

- [ ] **1.2 修复 Redis 容器 requirepass 空密码**
  - **验收**: `docker ps` 看 micro-course-redis-1 状态 Up(healthy),重启次数 = 0
  - **修复点**: `docker-compose.yml` 第 18 行 `command: redis-server --requirepass ${REDIS_PASSWORD:-}` → 加默认值或删除 --requirepass (空密码)
  - **关联 BUG**: 容器已重启 3351 次

- [ ] **1.3 验证 application.yml / .env 中 REDIS_PASSWORD 与 docker-compose 一致**
  - **验收**: `grep -E "REDIS_PASSWORD|redis.*password" docker-compose.yml .env micro-course-api/src/main/resources/application.yml` 输出三处一致
  - **关联文件**: `micro-course-api/src/main/resources/application.yml`, `.env`, `docker-compose.yml`

- [ ] **1.4 跑 3 项冒烟测试**
  - **验收**: 3 项全绿
    - `openspec list --json` 返回有效 JSON
    - `docker ps` 无 Restarting 容器
    - `curl http://localhost:8080/actuator/health` 返回 `{"status":"UP"}`

---

## 2. 阶段 0.5 — 未提交改动处理

- [ ] **2.1 检查 3 个未提交改动文件**
  - **验收**: `git diff --stat` 输出 3 个文件 (UserList.vue / SecurityConfig.java / StorageApplicationController.java) 的 diff stat
  - **关联 commit**: 必须先 commit 现有改动再做测试修复,避免冲突

- [ ] **2.2 提交未提交改动 (作为 baseline)**
  - **验收**: 3 个文件 commit 成功,`git status` 干净
  - **commit 格式**: `chore(phase15): baseline commit before full-coverage testing`
  - **要求**: 不修改文件内容,只做快照

---

## 3. 阶段 1 — 全量代码库扫描 (产出 3 份清单)

- [ ] **3.1 扫描后端 62 Controller 提取 396 endpoint 全量清单**
  - **验收**: 产出 `openspec/changes/full-coverage-redteam-testing/.audit-cache/api-endpoints.json` 含 396 条 (Method/Path/Controller/Params/Roles),无遗漏
  - **方法**: `rg -oN '@(Get|Post|Put|Delete|Patch)Mapping\("([^"]+)"\)'` + 类级 @RequestMapping 拼接
  - **关联 spec**: `full-coverage-regression`

- [ ] **3.2 扫描前端 127 页面提取按钮/绑定事件清单**
  - **验收**: 产出 `.audit-cache/vue-pages.json` 含 127 条 (Path/Component/Buttons/Events/ApiCalls)
  - **方法**: 解析 .vue 文件 template + script,提取 `@click` 和 axios 调用
  - **关联 spec**: `full-coverage-regression`

- [ ] **3.3 对照路由 vs Controller 找"页面点不到 API"**
  - **验收**: 产出 `.audit-cache/orphan-endpoints.json` 含孤儿端点(Controller 有但前端没调)和孤儿按钮(前端有但后端没接口)
  - **关联 BUG 类型**: 按钮异常 / 功能残缺

- [ ] **3.4 业务域划分 + 12 分片分配矩阵**
  - **验收**: 产出 `.audit-cache/shard-matrix.json` 含 12 个分片,每个分片 ~26-27 个最小测试单元,跨 6+ 业务域
  - **分配规则**: round-robin 按 OP 单元 ID 离散分配,避免单 Agent 包揽整链路
  - **关联 spec**: `full-coverage-regression`

- [ ] **3.5 输出《全项目按钮 & 业务链路总清单》**
  - **验收**: 产出 `openspec/changes/full-coverage-redteam-testing/.audit-cache/01-全项目按钮业务链路总清单.md`,含代码溯源 (Controller:行号 + Vue:行号)

- [ ] **3.6 输出《最小测试单元总表》**
  - **验收**: 产出 `.audit-cache/02-最小测试单元总表.md`,每个单元有: ID / 页面+按钮 / 代码路径 / 业务分支 / 行业基线 / 风险等级

- [ ] **3.7 输出《12 测试 Agent 分片分配矩阵》**
  - **验收**: 产出 `.audit-cache/03-12Agent分片测试分配矩阵.md`,严格 round-robin,每分片标跨域数

---

## 4. 阶段 2 — 12 Agent 并行单元化红队测试

### 4.1 准备测试数据

- [ ] **4.1.1 验证 3 个测试账号 (admin/teacher/student) 存在且能登录**
  - **验收**: `curl -X POST /api/auth/login` 三个账号都返回 200 + JWT
  - **关联数据**: `test_data.sql`

- [ ] **4.1.2 创建测试专用数据 (隔离,带 `_test_` 前缀)**
  - **验收**: 至少创建 5 门 _test_ 课程 / 3 个 _test_ 班级 / 10 个 _test_ 学生
  - **写入位置**: 本机 PG 5433 端口的 test 容器
  - **回滚**: 任务结束后提供 SQL 回滚脚本

### 4.2-4.13 12 个分片 (每个 ≤ 2 小时)

> **重要**: 每个分片独立沙箱执行,产出独立 Agent{N}-Report.md 到 `.audit-cache/reports/`

- [ ] **4.2 分片 1: OP-0001/0013/0025/0037/... (~27 单元)**
  - **验收**: `.audit-cache/reports/Agent1-Report.md` 含 27 个单元测试结果,每单元 7 字段
  - **覆盖域**: 至少 6 个 (auth/stu-course/cart/...)
  - **不允许**: 整链路包揽 / 合并混测

- [ ] **4.3 分片 2: OP-0002/0014/0026/0038/... (~27 单元)**

- [ ] **4.4 分片 3: OP-0003/0015/0027/0039/... (~27 单元)**

- [ ] **4.5 分片 4: OP-0004/0016/0028/0040/... (~27 单元)**

- [ ] **4.6 分片 5: OP-0005/0017/0029/0041/... (~27 单元)**

- [ ] **4.7 分片 6: OP-0006/0018/0030/0042/... (~27 单元)**

- [ ] **4.8 分片 7: OP-0007/0019/0031/0043/... (~26 单元)**

- [ ] **4.9 分片 8: OP-0008/0020/0032/0044/... (~26 单元)**

- [ ] **4.10 分片 9: OP-0009/0021/0033/0045/... (~26 单元)**

- [ ] **4.11 分片 10: OP-0010/0022/0034/0046/... (~26 单元)**

- [ ] **4.12 分片 11: OP-0011/0023/0035/0047/... (~26 单元)**

- [ ] **4.13 分片 12: OP-0012/0024/0036/0048/... (~26 单元)**

### 4.14 缺陷修复 (按发现顺序,跨分片)

- [ ] **4.14.1 P0 缺陷 (0-10 项)**
  - **验收**: 全部 P0 修复 + 跑通回归 + commit (含根因 + 审计 ID)
  - **关联 spec**: `defect-closure-verification`
  - **不允许**: 标"待修"

- [ ] **4.14.2 P1-C 缺陷 (10-50 项)**
  - **验收**: 全部 P1-C 修复 + 跑通回归 + commit

- [ ] **4.14.3 P1-I 缺陷 (10-100 项)**
  - **验收**: 全部 P1-I 修复 + commit

- [ ] **4.14.4 P2 缺陷 (0-100 项)**
  - **验收**: 全部 P2 修复 + commit

---

## 5. 阶段 3 — 退课链路补审 (ENR-005/006/008/009)

- [ ] **5.1 补审 ENR-005 学生退课按钮**
  - **验收**: 测试用例覆盖 (1) 弹窗确认 (2) 扣减优惠 (3) 触发退款 (4) 释放学习记录 (5) 通知教师
  - **关联文件**: `EnrollmentController.java`, `EnrollmentServiceImpl.java`, `OrderServiceImpl.java`
  - **关联 spec**: `enrollment-withdraw-audit`

- [ ] **5.2 补审 ENR-006 退课确认**
  - **验收**: 后端事务完整性 + 退款状态机测试通过

- [ ] **5.3 补审 ENR-008 批量退课**
  - **验收**: 事务边界 + 部分失败回滚测试通过

- [ ] **5.4 补审 ENR-009 退课审计**
  - **验收**: 审计日志写入 + 可查询
  - **如缺表**: 写 Flyway V167__add_withdraw_audit_log.sql

- [ ] **5.5 修复发现的退课缺陷 (0-N 项)**
  - **验收**: 全部修复 + 跑通 + commit

---

## 6. 阶段 4 — 双层交叉红队复测

- [ ] **6.1 横向: LD-002 进度永久丢失复测**
  - **验收**: 跑 5 步串联测试 (播放→上报失败→重置→重连→继续播放),进度 MUST 不丢失
  - **修复**: 如发现,改 `VideoPlayer.vue:1005-1012` + `LearningProgressServiceImpl`

- [ ] **6.2 横向: LD-005 批量支付假成功复测**
  - **验收**: 模拟部分支付失败,验证全链路状态正确 (不会显示"全成功")
  - **修复**: 如发现,改 `OrderServiceImpl.java:389-404` 事务边界

- [ ] **6.3 横向: LD-006 自审批绕过复测**
  - **验收**: 教师尝试审批自己课程/申报, MUST 拒绝
  - **修复**: 统一封装 `assertNotSelfApprove(principalId, ownerId)` 工具

- [ ] **6.4 横向: LD-009 双重失效模式复测**
  - **验收**: 禁用用户后,选课/学习/订单入口 MUST 阻断
  - **修复**: `EnrollmentController.java:41` 等增加 `userStatus == ACTIVE` 校验

- [ ] **6.5 横向: LD-011 计数器共享冲突复测**
  - **验收**: 登录 IP 防暴与 refresh 限流 MUST 用不同 Redis key
  - **修复**: 拆分 `AuthServiceImpl.java:139-195` 与 `JwtAuthenticationFilter`

- [ ] **6.6 横向: LD-015 退课审计盲区**
  - **验收**: 退课操作 MUST 写审计 (本任务 5.4 已覆盖,这里做回归确认)

---

## 7. 阶段 5 — 5 大件交付物

- [ ] **7.1 输出《全项目按钮 & 业务链路总清单 (代码溯源版)》**
  - **路径**: `openspec/changes/full-coverage-redteam-testing/deliverables/01-全项目按钮业务链路总清单-代码溯源版.md`
  - **内容**: 全 396 endpoint + 127 页面 + 路由对照表

- [ ] **7.2 输出《12 测试 Agent 分片测试分配矩阵》**
  - **路径**: `deliverables/02-12Agent分片测试分配矩阵.md`
  - **内容**: 12 分片每分片 27 单元 + 跨域数 + 风险等级

- [ ] **7.3 合并 12 份 Agent 独立报告**
  - **路径**: `deliverables/03-Agent独立测试报告/Agent{1-12}-Report.md`
  - **内容**: 每分片 27 单元测试结果 (7 字段)

- [ ] **7.4 输出《双层交叉复测补充缺陷台账》**
  - **路径**: `deliverables/04-双层交叉复测补充缺陷台账.md`
  - **内容**: 6 个 LD 缺陷复测 + 新发现联动缺陷

- [ ] **7.5 输出《项目缺陷总整改表》**
  - **路径**: `deliverables/05-项目缺陷总整改表.md`
  - **结构**: 4 大类分区 (按钮异常 / 业务常识错误 / 交互错乱 / 功能残缺),每类按 P0/P1-C/P1-I/P2 分级
  - **要求**: 每项 MUST 标"已修"或"未修" (不允许"待修")

---

## 8. 阶段 6 — 文档同步

- [ ] **8.1 更新 `docs/数据字典.md` v0.5 → v0.6**
  - **触发**: 阶段 2-4 发现新表/新字段
  - **验收**: 新增/修改字段已登记

- [ ] **8.2 更新 `docs/API契约-Phase1.md` v1.2 → v1.3**
  - **触发**: 发现新接口/新错误码
  - **验收**: 新增/修改接口已登记

- [ ] **8.3 更新 `docs/权限矩阵.md` v2.0 → v2.x**
  - **触发**: 发现新权限注解问题
  - **验收**: 权限注解与代码 100% 一致

- [ ] **8.4 更新 `docs/状态机设计.md` v1.0 → v1.x**
  - **触发**: 发现新状态机问题(如退课状态机)
  - **验收**: 状态机定义完整

- [ ] **8.5 更新 `docs/开发规范.md` v1.4 → v1.5**
  - **触发**: 发现新规范违反
  - **验收**: 25 条禁止项检查全过

---

## 9. 阶段 7 — OpenSpec Archive (收尾)

- [ ] **9.1 跑 `openspec validate full-coverage-redteam-testing --type change`**
  - **验收**: PASS

- [ ] **9.2 跑最终回归测试 (后端 + 前端)**
  - **验收**: 全部测试套件 PASS,无 P0/P1 残留

- [ ] **9.3 跑 `openspec archive full-coverage-redteam-testing`**
  - **验收**: change 归档到 `openspec/changes/archive/2026-07-06-full-coverage-redteam-testing/`
  - **关联**: 同步更新 `openspec/specs/` (如有新能力)

- [ ] **9.4 老板签字确认 (本任务完成)**
  - **验收**: 老板在微信确认 5 大件 + 修复成果

---

## 进度追踪 (OpenSpec apply 阶段自动更新)

```
阶段 0: 1.1✅ 1.2⬜ 1.3⬜ 1.4⬜
阶段 0.5: 2.1⬜ 2.2⬜
阶段 1: 3.1⬜ 3.2⬜ 3.3⬜ 3.4⬜ 3.5⬜ 3.6⬜ 3.7⬜
阶段 2: 4.1.1⬜ 4.1.2⬜ 4.2-4.13⬜ 4.14.1-4.14.4⬜
阶段 3: 5.1⬜ 5.2⬜ 5.3⬜ 5.4⬜ 5.5⬜
阶段 4: 6.1⬜ 6.2⬜ 6.3⬜ 6.4⬜ 6.5⬜ 6.6⬜
阶段 5: 7.1⬜ 7.2⬜ 7.3⬜ 7.4⬜ 7.5⬜
阶段 6: 8.1⬜ 8.2⬜ 8.3⬜ 8.4⬜ 8.5⬜
阶段 7: 9.1⬜ 9.2⬜ 9.3⬜ 9.4⬜
```

**总任务数**: 47 个 (不含 4.2-4.13 的 12 个分片)
**已完成**: 1
**总耗时预估**: 1-3 天连续执行
