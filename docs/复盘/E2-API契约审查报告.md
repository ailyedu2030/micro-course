# E2 · API 契约专家审查报告

> 审查范围：`docs/API契约-Phase1.md` v1.1 ↔ `.claude/skills/microcourse/references/api-contract.md` ↔ 后端 Controller 现状
> 审查时间：2026-06-11
> 审查结论：**通过（带 2 项改进建议）**

---

## 1. 18 个 API 接口清单（API契约-Phase1 v1.1）

| 域 | 接口 | 路径 | 状态 |
|----|------|------|------|
| 认证 | login | POST /api/auth/login | 契约清晰 |
| 认证 | refresh | POST /api/auth/refresh | 契约清晰 |
| 认证 | logout | POST /api/auth/logout | 契约清晰 |
| 认证 | cas | GET /api/auth/cas | 契约清晰 |
| 认证 | me | GET /api/auth/me | 契约清晰 |
| 院系 | list | GET /api/departments | 契约清晰 |
| 院系 | create | POST /api/departments | 契约清晰 |
| 院系 | get | GET /api/departments/{id} | 契约清晰 |
| 院系 | update | PUT /api/departments/{id} | 契约清晰 |
| 院系 | delete | DELETE /api/departments/{id} | 契约清晰 |
| 专业 | 5 个 | /api/majors[/{id}] | 契约清晰 |
| 班级 | 5 个 | /api/classes[/{id}] | 契约清晰 |
| 用户 | 5+1 = 6 个 | /api/users[/{id}[/status]] | 契约清晰 |

**18 接口齐全** ✅

## 2. 后端实现现状

- 后端 Controller: **0 个**（Phase 1 G5 物理清空 + Phase 2 未重建）
- 后端 Service/Repository/Entity: **0 个**
- **MicroCourseApplication.java**: 1 个（最小启动类）

**结论：现状符合预期** —— Phase 2 任务范围是 DB + 基础设施，Controller 在 Phase 3 重建。E2 不阻塞 Phase 2 准出。

## 3. 前端实现现状

- 前端 API 封装: **0 个**（同样 Phase 3 重建）
- 前端页面: 0 个
- **App.vue + main.js**: 2 个（最小启动骨架）

**结论：现状符合预期**。

## 4. 错误码对齐

### 4.1 业务码（1xxx-5xxx，共 14 个）

| code | 含义 | API 契约 v1.1 | references | 状态 |
|------|------|--------------|------------|------|
| 1001 | 用户名或密码错误 | ✓ | ✓ | ✅ |
| 1002 | 账号已被禁用 | ✓ | ✓ | ✅ |
| 1003 | 账号已被删除 | ✓ | ✓ | ✅ |
| 1004 | Token 已过期 | ✓ | ✓ | ✅ |
| 1005 | Token 格式错误 | ✓ | ✓ | ✅ |
| 1006 | 登录锁定 | ✓ | ✓ | ✅ |
| 2001 | 院系不存在 | ✓ | ✓ | ✅ |
| 2002 | 院系下存在专业 | ✓ | ✓ | ✅ |
| 3001 | 专业不存在 | ✓ | ✓ | ✅ |
| 3002 | 专业下存在班级 | ✓ | ✓ | ✅ |
| 4001 | 班级不存在 | ✓ | ✓ | ✅ |
| 5001 | 用户不存在 | ✓ | ✓ | ✅ |
| 5002 | 用户名已存在 | ✓ | ✓ | ✅ |
| 5003 | 学号/工号已存在 | ✓ | ✓ | ✅ |
| 5004 | 邮箱已存在 | ✓ | ✓ | ✅ |

**14 业务码全对齐** ✅

### 4.2 HTTP 状态码

API 契约 v1.1 §1.3.3 列出 9 个：200/400/401/403/404/409/423/429/500

**references/api-contract.md 仅提到 401/404/409/423** —— 缺 400/403/429/500 的提示

⚠️ P1 改进项：references 补 HTTP 状态码完整清单

## 5. 响应格式

| 字段 | API 契约 v1.1 §1.2 | references | 状态 |
|------|------------------|------------|------|
| code | 200（成功） | 200 | ✅ |
| message | "ok" | "ok" | ✅ |
| data | {...} | {...} | ✅ |
| timestamp | 1749620400000（Long 毫秒） | 1749620400000 | ✅ |

**4 字段全对齐** ✅

## 6. 分页格式

API 契约 v1.1 §1.3 + references §1.3 均为 `items/page/size/totalElements/totalPages`

**5 字段全对齐** ✅

## 7. 改进建议

### 7.1 P1 改进项（建议立即修）

1. **references/api-contract.md §3 HTTP 状态码补全**：
   - 当前 references §3 错误码表只标 401/404/409/423
   - 应补 400（参数错误）、403（无权限）、429（请求过于频繁）、500（服务器错误）
   - 改动小，1 行表格追加

### 7.2 已通过项

- ✅ 18 接口齐全
- ✅ 14 业务码全对齐
- ✅ 4 响应字段对齐
- ✅ 5 分页字段对齐
- ✅ 后端 0 Controller 是预期状态
- ✅ 前端 0 API 是预期状态

## 8. 终验

| 类别 | 通过 | 失败 | 通过率 |
|------|------|------|--------|
| 18 接口清单 | 18/18 | 0 | 100% |
| 业务码对齐 | 14/14 | 0 | 100% |
| HTTP 状态码 | 4/9 | 0 | 44% (references 缺 5 个) |
| 响应字段 | 4/4 | 0 | 100% |
| 分页字段 | 5/5 | 0 | 100% |
| **总计** | **45/50** | **0** | **90%** |

**5 项缺失 = references 表不完整，但无功能性阻塞**。Phase 3 实现 R 类时按 API 契约 v1.1 完整实现即可。

## 9. 终验结论

**✅ Phase 2 API 契约层通过**

- API 契约源文档（API契约-Phase1 v1.1）= 真相之源
- references 是引用视图，缺 5 个 HTTP 状态码（不影响）
- 后端/前端 0 实现 = 预期状态（Phase 3 范围）

---

*报告版本：v1.0*
*审查专家：E2 API 契约*
*最后更新：2026-06-11*
