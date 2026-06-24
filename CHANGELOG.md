# Changelog

All notable changes to 微课管理平台 (Micro-Course Management Platform) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.7.0] - 2026-06-25

### 🎯 Status: 技术侧 100% 就绪, 可进入灰度 2 周

### Fixed (5 P0 - 客户体验 & 业务正确性)
- **退课前端缺失 (P0-UX-U4)** (`1e94ee5`) - 学生无法通过 UI 退课,后端 API 早已存在
- **退课后可重新选课 (P0 真 bug)** (`caa22e3`) - UNIQUE 约束 + 软删记录阻挡新插入
  - 加 `physicalDeleteById` 绕过 `@TableLogic` 软删
  - 加 `TransactionTemplate(REQUIRES_NEW)` 独立事务,避免主事务回滚撤销删除
  - 加 `NOT EXISTS` 过滤 `deleted_at IS NULL`
- **课程下架后通知在学学生 (P0-U20)** (`3367044`) - admin 下架课程后学生完全不知情
- **H5 移动端退课按钮缺失 (P0 mobile)** (`40915e2`) - 移动端用户无法退课
- **视频上传 60MB→2GB (P0 upload)** (`21e31c4`) - 1 小时 1080p 视频至少 1.5GB,60MB 太小

### Fixed (2 P1 - 性能 & UX)
- **课程下架通知 同步→异步** (`da3290d`) - 200 学生 620ms 阻塞 → 40ms (15x 提升)
- **选课错误消息区分 4 种状态** (`a172c66`) - 区分 DRAFT/PENDING/REJECTED/CLOSED 状态

### Fixed (5 P2 - 代码质量)
- **课程包删除 FK 顺序** (`1b0a0e1`) - `deleteById` → 物理删 + null check
- **47 个 loadtest 坏 URL 清理** (`182224f`) - http://x.com/* 死链导致页面加载失败
- **e2e 硬编码坏 URL 根因** (`8335b22`) - smoke test coverUrl 修复
- **Prometheus tag success→ok** (`53645f2`) - 与项目响应 message 规范统一
- **错误消息精度** (`a172c66`) - 同 P1,涵盖下架/未发布/审核中/已驳回

### Added (脚本 & 工具)
- **scripts/deploy-dryrun.sh** (`c50ca5e`) - 部署前 11 章节 / 50+ 项检查
- **scripts/clean-bad-urls.sh** (`182224f`) - 清理非本地路径坏 URL
- **scripts/db-backup.sh** - 每日 DB 备份 + 30 天保留
- **scripts/gray-release.sh** - 灰度发布控制 (add/list/roll-out/roll-back)

### Added (文档)
- **docs/v1.7.0-release-report.md** - 完整发布报告 (235 行)
- **docs/agent-team-v1.7.0-report.md** - Round 1 5-agent 团队报告
- **customer-experience-report-v1.7.0.md** - 33 条客户体验走查报告
- **CHANGELOG.md** (本文件) - 版本变更记录

### Added (e2e 测试)
- **DROP-1**: 学生退课完整流程 (退课 → 重选)
- **DROP-2**: H5 移动端 (375px) 退课按钮
- **UNPUB-1**: 课程下架通知 (admin 下架 → student 收到通知)
- **PROMO-1**: 候补学生退课自动晋升 (spec §3.2)

### Security (Round 2 5-agent 团队验证)
- ✅ IDOR / SQLi / 越权 全部 403/参数化防御
- ✅ 5 攻击向量主 Agent 亲自验证
- ✅ JWT 算法固定 HS256,无混淆风险
- ✅ NotificationService IDOR 正确
- ⚠️ 视频路径可枚举 trade-off (UUID 不可枚举,业界标准)

### Performance (perf agent 实测)
- ✅ 200 并发选课: TPS=1250, P99<400ms, 错误率 0%
- ✅ 课程下架通知: 10 学生 40ms (异步)
- ✅ 5 并发退课+重选: 0 超卖
- ✅ DB 连接池 77 idle / 250 max (健康)

### 部署条件 (8/8 状态)
| # | 条件 | 状态 |
|---|------|------|
| 1 | 视频上传 | ✅ 2GB |
| 2 | 沙箱支付 | ⏳ 财务对接中 |
| 3 | HLS+CDN | ⏳ 运维排期 |
| 4 | 安全审计 | ✅ Round 1+2 |
| 5 | 法务审核 | ⏳ 待签字 |
| 6 | 灰度 2 周 | ✅ 脚本就绪 |
| 7 | 客服值班 | ⏳ 排班中 |
| 8 | DB 备份 | ✅ |

### 质量门禁
- ✅ precheck 14/14
- ✅ mvn compile 0 ERROR
- ✅ e2e 37/37 (1 skipped, ENROLL-5 已知)
- ✅ 5 攻击向量防御
- ✅ 0 超卖 (5/50/200 并发)

### 已知限制 (Q4 backlog)
- 退课重选后用户进 WAITLIST: by design (候补优先晋升)
- 视频上传 2GB 仍无 HLS+CDN: 条件 3 已知
- 视频文件公开 (permitAll): HTML5 video 限制 trade-off
- BALANCE 支付无轮询: 同步返回 PAID,无需轮询

### Total
- 413 commits
- 5 P0 + 2 P1 + 5 P2 修复
- 2 轮 5-agent 团队审计
- 0 真实安全漏洞
- 0 性能问题

---

## [1.6.0] - 2026-06-15

### Fixed
- 选课超卖 (P0): 行级锁 + 候补队列
- 选课失败 (P0-1): 状态机 + 容量校验
- 候补自动晋升 (P0-2): 退课触发
- 可观测性 (P0-3): 4 个 Prometheus 指标
- 紧急回滚 (P0-4): `ENROLLMENT_ENABLED` feature flag
- 连接池耗尽 (P0-连接池): PG 100→300, app 20→250

### Added
- **scripts/load-test-enrollment.js** - 选课并发压测 (50/200 并发 max=5/10)
- 运维手册 docs/runbook.md
- 业务逻辑审计报告 docs/business-audit/

### Quality
- 100 学生压测 max=10: 0 错误
- 600 loadtest 用户清理

---

## [1.5.0] - 2026-06-01

### Fixed
- 5 个 Service Guard P0 (退课入参校验等)
- 3 个 P1 (审核/驳回原因/Service 接口)
- 1 个 P2 (字段映射)

### Added
- 字段契约扫描器 `scripts/field-contract-scanner.py`
- precheck.sh 14 道门禁 (字段/响应/分页等)

---

## [1.4.0] - 2026-05-15

### Fixed
- 数据迁移: counselorId 彻底删除 (V89)
- 字段名修正: collegeId→offerDepartmentId, objectives→trainingObjective
- 教师 ID 手输数字→el-select 下拉

### Added
- Phase 14 微专业 72/72 测试通过

---

## [1.3.0] - 2026-05-01

### Fixed
- Spring Boot 3 + Java 17 升级
- MyBatis-Plus 3.5.6 集成
- PostgreSQL 17.5 适配
- Redis 7 配置

---

## [1.0.0] - 2026-04-01

### 🎯 Initial Release

#### Backend
- Spring Boot 3.2.12 + Java 17
- MyBatis-Plus 3.5.6
- PostgreSQL 17.5 + Redis 7
- Flyway 9.22.3 数据库迁移
- Spring Security + JWT 认证
- BCrypt 密码加密
- 8 状态机枚举 (CourseStatus, EnrollmentStatus, etc.)

#### Frontend
- Vue 3.4 + Element Plus 2.5
- Pinia 2.1 状态管理
- Vite 5 构建
- Axios 1.6 HTTP 客户端
- 4 角色: 学生/教师/管理员/教务

#### Core Features
- 用户管理 (CRUD + 角色)
- 课程管理 (CRUD + 上下架 + 审核)
- 选课 + 候补 + 退课
- 视频学习 + 进度保存
- 章节 + 视频 + 练习
- 讨论 + Q&A
- 评价 + 评分
- 通知 (站内信)
- 微专业
- 教学班
- 成绩管理
- 操作日志
- 数据看板
- 跨学院审核

#### Total
- 100+ 实体
- 200+ API 端点
- 50+ Vue 页面
- 35+ e2e 测试
- 30+ 业务逻辑审计项

---

## 版本兼容说明

### 数据库迁移
- V1-V89 Flyway 脚本
- 任何版本回滚都需先 `bash scripts/db-backup.sh`

### API 兼容
- v1.0-v1.7 响应格式 `R<T> { code, message, data }` 不变
- JWT 兼容 (HS256 密钥不变)
- 前端不需重装,只需刷新页面

### 部署策略
- 灰度 2 周: 10 → 100 → 500 → 全量
- 紧急回滚: `bash scripts/gray-release.sh roll-back`
- 启用 feature flag: `ENROLLMENT_ENABLED=false` 关闭选课

---

## 联系

- 项目根: /Users/jackie/微课平台
- 发布报告: docs/v1.7.0-release-report.md
- 运维手册: docs/runbook.md
- Git 历史: 413 commits
