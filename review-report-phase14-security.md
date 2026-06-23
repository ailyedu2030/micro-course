# 审查报告 — Phase 14 微专业安全与权限零信任穷举审查

## 审查范围
- **文件**: 13 个（6 Controller + 1 SecurityConfig + 5 Service + 1 Service 接口 + 1 Router）
- **设计文档参考**: `docs/开发规划/phase14-micro-specialty-spec.md` v1.1（§3.2 + §7 + §9.12 权限速查表）
- **权限矩阵参考**: `.claude/skills/microcourse/references/permission-matrix.md` §5（微专业角色权限）
- **审查类型**: 单文件审查，未执行跨文件检查（跨文件一致性通过 4 维交叉验证 R4 覆盖）

---

## 一、SecurityConfig 路径配置审查

### 1.1 逐项检查

| # | 检查项 | SecurityConfig:行号 | 预期 | 实际 | 结果 |
|---|--------|-------------------|------|------|------|
| 1 | `/api/micro-specialties/square` permitAll | L115 | `permitAll()` | `permitAll()` | ✅ PASS |
| 2 | `/api/micro-specialties/{id}` permitAll | L116 | `permitAll()` | `permitAll()` | ✅ PASS |
| 3 | `/api/micro-specialties/{id}/courses` permitAll | L117 | `permitAll()` | `permitAll()` | ✅ PASS |
| 4 | `/api/micro-specialties/{id}/teachers` permitAll | L118 | `permitAll()` | `permitAll()` | ✅ PASS |
| 5 | 规则在 `.anyRequest().authenticated()` 之前 | L98-142 | 顺序正确 | 顺序正确 | ✅ PASS |

**说明**: AntPathMatcher（Spring Security 默认）的 `{id}` 语法匹配任意单段路径（等效于 `*`），非字面量。已验证：`/api/micro-specialties/square`、`/api/micro-specialties/42` 均可被 `{id}` 模式匹配，且 `/api/micro-specialties/{id}/stats`（5 段）不会被 `{id}`（4 段）误匹配。

### SecurityConfig 结论: ✅ 无问题

---

## 二、Controller @PreAuthorize 逐项审查

### 2.1 MicroSpecialtyController

对照 spec §7.1 (API 权限) + §9.12 (权限速查表)

| # | 方法 | 行号 | @PreAuthorize | Spec 要求 | Service 层二次校验 | 结果 |
|---|------|------|--------------|-----------|-------------------|------|
| 1 | GET /page | L50-51 | `isAuthenticated()` | 已认证 | — | ✅ PASS |
| 2 | GET /square | L62 | `permitAll()` | 公开 | — | ✅ PASS |
| 3 | GET /{id} | L70 | `permitAll()` | 公开 | `getDetail()` 内 DRAFT/CANCELLED 过滤 | ✅ PASS |
| 4 | GET /{id}/stats | L78 | `isAuthenticated()` | 已认证 | — | ✅ PASS |
| 5 | GET /{id}/enrollments | L86 | `hasAnyRole('TEACHER','ACADEMIC')` | LEAD/ACADEMIC | `requireLeadOf()` 缩小为 LEAD | ✅ PASS |
| 6 | POST /create | L100 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 7 | PUT /{id} | L108 | `hasRole('TEACHER')` | LEAD/ADMIN | `requireLeadOf()` + isAdmin 后备 | ✅ PASS |
| 8 | DELETE /{id} | L117 | `hasRole('ADMIN')` | ADMIN | `requireOwnerOrLead()` + isAdmin 后备 | ✅ PASS |
| 9 | POST /{id}/submit | L127 | `hasRole('TEACHER')` | LEAD | `requireLeadOf()` + isAdmin 后备 | ✅ PASS |
| 10 | POST /{id}/approve | L135 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 11 | POST /{id}/reject | L143 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 12 | POST /{id}/open | L152 | `hasRole('TEACHER')` | LEAD | `requireLeadOf()` + isAdmin 后备 | ✅ PASS |
| 13 | POST /{id}/close | L160 | `hasRole('TEACHER')` | LEAD | `requireLeadOf()` + isAdmin 后备 | ✅ PASS |
| 14 | POST /{id}/cancel | L168 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 15 | POST /{id}/archive | L176 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 16 | GET /{id}/courses | L186 | `permitAll()` | 公开 | 无 DRAFT/CANCELLED 过滤 | ⚠️ P2 (见下) |
| 17 | POST /{id}/courses | L194 | `hasRole('TEACHER')` | LEAD | `requireLeadOf()` | ✅ PASS |
| 18 | PUT /{id}/courses/{itemId} | L203 | `hasRole('TEACHER')` | LEAD | `requireLeadOf()` | ✅ PASS |
| 19 | DELETE /{id}/courses/{itemId} | L213 | `hasRole('TEACHER')` | LEAD | `requireLeadOf()` | ✅ PASS |
| 20 | GET /{id}/teachers | L224 | `permitAll()` | 公开 | 不过滤 DECLINED/REMOVED | ⚠️ P2 (见下) |
| 21 | POST /{id}/teachers | L232 | `hasRole('TEACHER')` | LEAD | `requireLeadOf()` + `checkNotTerminal()` | ✅ PASS |
| 22 | DELETE /{id}/teachers/{teacherId} | L241 | `hasAnyRole('TEACHER','ADMIN')` | LEAD/ADMIN | `requireLeadOf()` | ✅ PASS |
| 23 | POST /transfer-leadership | L252 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |

### 2.2 MicroSpecialtyEnrollmentController

对照 spec §7.5

| # | 方法 | 行号 | @PreAuthorize | Spec 要求 | Service 层二次校验 | 结果 |
|---|------|------|--------------|-----------|-------------------|------|
| 1 | POST /apply | L31 | `hasRole('STUDENT')` | STUDENT | — | ✅ PASS |
| 2 | POST /{id}/approve | L42 | `hasAnyRole('TEACHER','ACADEMIC')` | LEAD/ACADEMIC | `isLeadOf()` + isAdmin 后备 | ✅ PASS |
| 3 | POST /{id}/reject | L50 | `hasAnyRole('TEACHER','ACADEMIC')` | LEAD/ACADEMIC | `isLeadOf()` + isAdmin 后备 | ✅ PASS |
| 4 | POST /class-import | L59 | `hasAnyRole('ACADEMIC','ADMIN')` | ACADEMIC/ADMIN | — | ✅ PASS |
| 5 | POST /{id}/drop | L73 | `hasAnyRole('STUDENT','ADMIN')` | STUDENT(本人)/ADMIN | `getUserId().equals(en.getUserId())` 本人校验 | ✅ PASS |
| 6 | POST /{id}/reapply | L85 | `hasRole('STUDENT')` | STUDENT(本人) | `getUserId().equals(en.getUserId())` 本人校验 | ✅ PASS |
| 7 | GET /my | L93 | `hasRole('STUDENT')` | STUDENT | — | ✅ PASS |
| 8 | POST /{id}/issue-certificate | L101 | `hasAnyRole('TEACHER','ACADEMIC')` | LEAD/ACADEMIC | `requireLeadOf()` 缩小为 LEAD | ✅ PASS |

### 2.3 MicroSpecialtyProposalController

对照 spec §7.2

| # | 方法 | 行号 | @PreAuthorize | Spec 要求 | Service 层二次校验 | 结果 |
|---|------|------|--------------|-----------|-------------------|------|
| 1 | POST /submitProposal | L32 | `hasRole('TEACHER')` | TEACHER | — | ✅ PASS |
| 2 | GET /my | L40 | `hasRole('TEACHER')` | TEACHER | `proposerId = userId` 自动过滤 | ✅ PASS |
| 3 | GET / (pending) | L50 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 4 | POST /{id}/approve | L60 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 5 | POST /{id}/reject | L68 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 6 | POST /{id}/withdraw | L77 | `hasRole('TEACHER')` | TEACHER(本人) | `proposerId == userId` 本人校验 | ✅ PASS |
| 7 | POST /{id}/resubmit | L85 | `hasRole('TEACHER')` | TEACHER(本人) | `proposerId == userId` 本人校验 | ✅ PASS |

### 2.4 MicroSpecialtyTeacherController

对照 spec §7.4

| # | 方法 | 行号 | @PreAuthorize | Spec 要求 | Service 层二次校验 | 结果 |
|---|------|------|--------------|-----------|-------------------|------|
| 1 | GET /pending-invites | L29 | `hasRole('TEACHER')` | TEACHER | `teacherId = userId` 自动过滤 | ✅ PASS |
| 2 | POST /{inviteId}/accept | L39 | `hasRole('TEACHER')` | TEACHER(本人) | `userId.equals(record.getTeacherId())` | ✅ PASS |
| 3 | POST /{inviteId}/decline | L47 | `hasRole('TEACHER')` | TEACHER(本人) | `userId.equals(record.getTeacherId())` | ✅ PASS |
| 4 | POST /{inviteId}/leave | L55 | `hasRole('TEACHER')` | TEACHER(本人) | `record.getTeacherId().equals(userId)` | ✅ PASS |
| 5 | POST /{inviteId}/review-cross-dept | L63 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 6 | POST /{inviteId}/reinvite | L76 | `hasRole('TEACHER')` | LEAD | `leadTeacherId` 比对（见 IDOR ⚠️） | ⚠️ P1 (见下) |

### 2.5 MicroSpecialtyFeaturedController

对照 spec §7.6

| # | 方法 | 行号 | @PreAuthorize | Spec 要求 | Service 层二次校验 | 结果 |
|---|------|------|--------------|-----------|-------------------|------|
| 1 | POST /{id}/apply-featured | L30 | `hasRole('TEACHER')` | LEAD | `isLeadOf()` + isAdmin | ✅ PASS |
| 2 | POST /{id}/approve-featured | L39 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 3 | POST /{id}/reject-featured | L47 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 4 | POST /{id}/unset-featured | L57 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 5 | POST /{id}/set-gold-featured | L65 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |
| 6 | POST /{id}/unset-gold-featured | L73 | `hasRole('ACADEMIC')` | ACADEMIC | — | ✅ PASS |

---

## 三、Service 层二次鉴权审查

### 3.1 鉴权方法实现

| # | 方法 | 文件:行号 | 实现 | 是否正确 | 结果 |
|---|------|----------|------|---------|------|
| 1 | `isLeadOf(msId, userId)` | MicroSpecialtyServiceImpl:983-990 | `role='LEAD' AND invite_status='ACTIVE'` | ✅ 与 §9.12 一致 | ✅ PASS |
| 2 | `isMemberOf(msId, userId)` | MicroSpecialtyServiceImpl:993-1000 | `role IN ('MEMBER','ASSISTANT') AND invite_status='ACTIVE'` | ✅ 与 §9.12 一致 | ✅ PASS |
| 3 | `requireLeadOf(msId)` | MicroSpecialtyServiceImpl:1011-1016 | `isLeadOf() OR isAdmin()` | ✅ 含 ADMIN 后备 | ✅ PASS |
| 4 | `isOwnerOrLead(msId, userId)` | MicroSpecialtyServiceImpl:1003-1007 | `isLeadOf() OR creatorId == userId` | ✅ | ✅ PASS |
| 5 | `requireOwnerOrLead(msId)` | MicroSpecialtyServiceImpl:1019-1024 | `isOwnerOrLead() OR isAdmin()` | ✅ 含 ADMIN 后备 | ✅ PASS |
| 6 | `checkNotTerminal(ms)` | MicroSpecialtyServiceImpl:1027-1031 | 屏蔽 CANCELLED/ARCHIVED | ✅ | ✅ PASS |

### 3.2 "本人"操作 IDOR 校验

| # | 操作 | Service 校验 | 文件:行号 | 结果 |
|---|------|-------------|----------|------|
| 1 | acceptInvite | `userId.equals(record.getTeacherId())` | InviteServiceImpl:66 | ✅ PASS |
| 2 | declineInvite | `userId.equals(record.getTeacherId())` | InviteServiceImpl:157 | ✅ PASS |
| 3 | leaveTeam | `record.getTeacherId().equals(userId)` | InviteServiceImpl:187 | ✅ PASS |
| 4 | drop (enrollment) | `en.getUserId().equals(userId)` + isAdmin | EnrollmentServiceImpl:540 | ✅ PASS |
| 5 | reapply (enrollment) | `en.getUserId().equals(userId)` + isAdmin | EnrollmentServiceImpl:598 | ✅ PASS |
| 6 | withdrawProposal | `proposal.getProposerId().equals(currentUserId)` | ProposalServiceImpl:184 | ✅ PASS |
| 7 | resubmitProposal | `proposal.getProposerId().equals(currentUserId)` | ProposalServiceImpl:204 | ✅ PASS |

---

## 四、IDOR 保护深度审查

### 4.1 inviteTeacher — Service requireLeadOf ✅

### 4.2 reinviteTeacher ⚠️ P1

**文件**: InviteServiceImpl:288-292
**实现**:
```java
Long currentUserId = SecurityUtil.getCurrentUserId();
MicroSpecialty msForCheck = msRepository.selectById(record.getMicroSpecialtyId());
if (msForCheck != null && !currentUserId.equals(msForCheck.getLeadTeacherId()) && !SecurityUtil.isAdmin()) {
    throw new BusinessException(ErrorCode.NO_PERMISSION, "仅微专业负责人可执行此操作");
}
```
**问题**: 使用 `ms.getLeadTeacherId()` 字段校验而非 `isLeadOf()` 方法。  
- `lead_teacher_id` 是冗余字段，可能在 LEAD 继任事务中存在短暂不一致窗口（虽然后续也同步更新）
- `isLeadOf()` 直接从教师表查询 `role='LEAD' AND invite_status='ACTIVE'`，是权威数据源
- 应改为 `msService.isLeadOf(record.getMicroSpecialtyId(), currentUserId)`

### 4.3 listEnrollments — Service requireLeadOf ✅
**文件**: EnrollmentServiceImpl:665-667 — 非 ADMIN/ACADEMIC 时调 `requireLeadOf()`

### 4.4 issueCertificate — Service requireLeadOf ✅
**文件**: EnrollmentServiceImpl:684-686 — 非 ADMIN/ACADEMIC 时调 `requireLeadOf()`

### 4.5 withdrawProposal — proposer 本人校验 ✅

### 4.6 resubmitProposal — proposer 本人校验 ✅

### 4.7 approveEnrollment — isLeadOf + isAdmin ✅
**文件**: EnrollmentServiceImpl:133

### 4.8 rejectEnrollment — isLeadOf + isAdmin ✅
**文件**: EnrollmentServiceImpl:286

---

## 五、信息泄露风险评估

### 5.1 DRAFT/CANCELLED 状态过滤 — listCourses/listTeachers ⚠️ P2

**文件**: MicroSpecialtyServiceImpl:716-722 (listCourses) / MicroSpecialtyController:186 (Mapping)
**文件**: MicroSpecialtyServiceImpl:799-805 (listTeachers) / MicroSpecialtyController:224 (Mapping)

**问题**: `getDetail()`（Controller L70）在 Service 层 204-212 行做 DRAFT/CANCELLED 权限过滤，但 `listCourses()` 和 `listTeachers()` 作为独立公开端点不做任何状态过滤。任何知道 DRAFT 微专业 ID 的人可直接调用 `GET /api/micro-specialties/{id}/courses` 和 `GET /api/micro-specialties/{id}/teachers` 获取课程编排和教师信息。

**影响**: 低 - 仅暴露课程标题和教师姓名等非敏感信息
**建议**: 在 `listCourses()` 和 `listTeachers()` 中增加对 DRAFT/CANCELLED 状态的过滤，或在 Controller 的 `permitAll()` 基础上增加安全检查

### 5.2 listTeachers 不过滤 DECLINED/REMOVED 邀请 ⚠️ P2

**文件**: MicroSpecialtyServiceImpl:800-804
**实现**: 仅过滤 `DECLINED` 和 `REMOVED` 状态，暴露 INVITED/PENDING_ACADEMIC 状态的待处理邀请
**对比**: `getDetail()` 在 L236-239 仅查 `invite_status='ACTIVE'` 的教师
**建议**: `listTeachers()` 应统一为仅返回 `ACTIVE` 状态的教师

---

## 六、前端路由防护审查

### 6.1 检查 router/index.js

对照 spec §5.1 路由表 & §3.2 角色映射

| # | 路由路径 | 行号 | meta.roles | Spec 角色 | 结果 |
|---|---------|------|-----------|----------|------|
| 1 | /student/micro-specialties/:id | L91 | `['STUDENT']` | STUDENT | ✅ PASS |
| 2 | /student/my-micro-specialties | L92 | `['STUDENT']` | STUDENT | ✅ PASS |
| 3 | /teacher/micro-specialties | L93 | `['TEACHER','ADMIN']` | TEACHER/LEAD | ✅ PASS |
| 4 | /teacher/micro-specialties/:id/manage | L94 | `['TEACHER','ADMIN']` + `requiresLead:true` | LEAD | ⚠️ P2 |
| 5 | /teacher/micro-specialties/:id/courses | L95 | `['TEACHER','ADMIN']` + `requiresLead:true` | LEAD | ⚠️ P2 |
| 6 | /teacher/micro-specialties/:id/team | L96 | `['TEACHER','ADMIN']` + `requiresLead:true` | LEAD | ⚠️ P2 |
| 7 | /teacher/micro-specialties/invites | L97 | `['TEACHER']` | TEACHER | ✅ PASS |
| 8 | /teacher/micro-specialties/proposals | L98 | `['TEACHER']` | TEACHER | ✅ PASS |
| 9 | /teacher/micro-specialties/my-proposals | L99 | `['TEACHER']` | TEACHER | ✅ PASS |
| 10 | /academic/micro-specialties/review | L100 | `['ACADEMIC','ADMIN']` | ACADEMIC | ✅ PASS |
| 11 | /academic/micro-specialties/proposals | L101 | `['ACADEMIC','ADMIN']` | ACADEMIC | ✅ PASS |
| 12 | /academic/micro-specialties/featured | L102 | `['ACADEMIC','ADMIN']` | ACADEMIC | ✅ PASS |
| 13 | /academic/micro-specialties/cross-dept | L103 | `['ACADEMIC','ADMIN']` | ACADEMIC | ✅ PASS |
| 14 | /academic/micro-specialties/class-import | L104 | `['ACADEMIC','ADMIN']` | ACADEMIC | ✅ PASS |
| 15 | /academic/micro-specialties/gold | L105 | `['ACADEMIC','ADMIN']` | ACADEMIC | ✅ PASS |

### 6.2 requiresLead 未在 beforeEach 中校验 ⚠️ P2

**问题**: 路由 #4/#5/#6 设置了 `requiresLead: true` 元标记，但 `router.beforeEach` 守卫（L148-185）仅检查 `meta.roles`，**未检查 `meta.requiresLead`**。这意味着任意 TEACHER（含非 LEAD）可导航到管理/编排/团队页面。虽然后端 API 会拒绝操作（403），但前端页面会加载并显示错误状态。

**影响**: 低 - 仅影响 UX（页面加载后 API 失败），不构成数据泄露因为后端正确拦截
**建议**: 如果前端有能力判断当前用户是否为特定微专业的 LEAD（需要 API 支持），可在路由守卫中增加 `requiresLead` 检查。当前无此能力的情况下，依赖后端防护是可接受的。

---

## 七、机械检查结果

（单文件审查，未委派 scout。以下为手动模式匹配检查）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 命名约定 | ✅ PASS | Controller/Service/DTO 遵循项目命名规范 |
| 注释头完整性 | ✅ PASS | 各文件含必要的 Javadoc/Swagger 注释 |
| 缩进/格式 | ✅ PASS | 缩进一致，无混合 tab/space |
| 遗留调试代码 | ✅ PASS | 未发现未删除的 `System.out`/`console.log` |
| 异常处理 | ✅ PASS | 所有 UPDATE 含乐观锁 version 校验 + affected==0 抛异常 |

---

## 八、决策

- [ ] **放行** — 存在 P1 项，需修复后重新审查
- [x] **混合** — 存在 P0 项（无），P1（1项）+ P2（3项）
  - P0 阻塞项: 无
  - P1 建议修复 (1项): 记录到 Phase 6
  - P2 可优化项 (3项): 记录到 Phase 6

**汇总**:
- **P0 阻塞**: 0 项 ✅
- **P1 强烈建议修复**: 1 项
- **P2 可选优化**: 3 项
- **总 PASS**: 全部 Controller 权限+Service 鉴权+IDOR 保护已通过
