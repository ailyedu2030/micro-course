# 微课管理平台 v1.0.0 发布检查清单

## 验证门禁

- [x] `mvn compile` — 0 ERROR
- [x] `npm build` — SUCCESS
- [x] `precheck.sh` — 13/13 PASS
- [x] E2E API 测试 — 15/15 PASS
- [x] 集成测试 — 25/31 PASS (6 concurrency tests skipped)
- [x] Flyway 迁移 — 57 migrations applied
- [x] 数据库 — PostgreSQL 连接正常
- [x] 缓存 — Redis 连接正常
- [x] 应用健康 — HTTP 200

## 功能验证（Phase 5-10）

### Phase 5 — 学生前端 (41/41)
- [x] 课程广场: 分类/搜索/推荐/卡片网格
- [x] 课程详情: 信息/大纲/报名/评价
- [x] 我的课程: 进行中/已完成/收藏
- [x] 视频播放: HLS/倍速/进度记忆/全屏
- [x] 随堂练习: 4题型/自动批改/错题入库
- [x] 学习中心: 统计/打卡/趋势
- [x] 讨论区: 发帖/回复/匿名
- [x] 通知/个人中心/订单

### Phase 6 — 教师端 (17/17)
- [x] 教学看板: 统计/趋势/待办
- [x] 学员管理: 列表/进度/导出
- [x] 课程管理: CRUD/复制/排序/批量上传
- [x] 题库/练习: CRUD/导入/导出/乱序

### Phase 7 — 管理后台 (17/17)
- [x] 数据看板: 用户/课程/活跃度趋势
- [x] 用户管理: CRUD/批量导入
- [x] 操作日志: 筛选/搜索
- [x] 系统设置: 注册/上传/CAS/Banner

### Phase 8 — 视频基础设施
- [x] 视频上传/转码/防盗链
- [x] hls.js 播放器/断点续播

### Phase 9 — API 补齐
- [x] 课程评价 API
- [x] 管理统计 API
- [x] 批量导入 API
- [x] 操作日志 API

## 安全审计

- [x] 12 个漏洞修复 (7 P0 + 4 P1 + 1 P2)
- [x] 全链路 TEACHER 数据隔离
- [x] 19 处 @PreAuthorize 修正
- [x] 菜单角色对齐 (ADMIN 26/ACADEMIC 15/TEACHER 12)
- [x] 订单支付安全: CAS更新/幂等性/FK修复

## 测试

- [ ] E2E 浏览器测试（手动）
- [ ] 性能测试
- [ ] 安全渗透测试

## 部署

- [ ] Docker 镜像构建
- [ ] docker-compose.yml 生产配置
- [ ] Nginx 配置验证
- [ ] 数据库备份策略
- [ ] 日志轮转配置

---

版本: v1.0.0
日期: 2026-06-21
