# Tasks: 讨论/评价域 Spec 漂移全量修复

> **OpenSpec Change**: `discussion-domain-drift-fix`
> **Schema**: spec-driven
> **进度追踪**: `- [ ]` 复选框格式 (OpenSpec apply 阶段自动识别)
> **单任务限制**: ≤ 2 小时

---

## 1. 数据字典补充

- [x] **1.1 补充 discussion_comment_likes 到数据字典**
  - **验收**: §6.3 discussion_comment_likes 表完整定义（含字段/索引/约束）
  - **文件**: docs/数据字典.md §6.3
  - **状态**: ✅ 已修复 (2026-07-27 v1.x 同步)

- [x] **1.2 修复 course_review_logs 类型 (SMALLINT→Integer)**
  - **验收**: 数据字典 + DB + Entity 三处一致
  - **文件**: docs/数据字典.md + V71 migration
  - **状态**: ✅ pre-existing 已修复（仅需更新 tasks.md）

- [x] **1.3 补充 discussion_comments.is_anonymous 字段到数据字典**
  - **验收**: §6.2 表加 `isAnonymous | is_anonymous | Boolean | default false` 行
  - **文件**: docs/数据字典.md §6.2
  - **状态**: ✅ 已修复 (2026-07-27 v1.x 同步)

---

## 2. 状态机枚举化

- [x] **2.1 DiscussionPostStatus 枚举 + canTransitionTo**
  - **验收**: enums/DiscussionPostStatus.java 存在，含 PENDING/PUBLISHED/REJECTED/DELETED + canTransitionTo + fromCode
  - **文件**: micro-course-api/src/main/java/com/microcourse/enums/DiscussionPostStatus.java
  - **状态**: ✅ pre-existing 已实现（仅需更新 tasks.md）

- [x] **2.2 CourseReviewStatus 枚举 + canTransitionTo**
  - **验收**: enums/CourseReviewStatus.java 存在，含 PENDING/APPROVED/REJECTED + canTransitionTo + fromCode
  - **文件**: micro-course-api/src/main/java/com/microcourse/enums/CourseReviewStatus.java
  - **状态**: ✅ pre-existing 已实现（仅需更新 tasks.md）

---

## 3. API 契约同步

- [x] **3.1 API 契约-Phase1.md 附录 A 由"Phase 2 预留"改为"已实现"**
  - **验收**: 附录 A 列全 15 个讨论端点（POST/GET/PUT/DELETE + like/pin/essence），含权限要求、响应格式、状态机引用
  - **文件**: docs/API契约-Phase1.md 附录 A + 修订记录（v1.8）
  - **状态**: ✅ 已修复 (2026-07-27 v1.8 同步)

---

## 4. 状态机设计文档补充（可选，不阻塞合并）

- [ ] **4.1 docs/状态机设计.md 补 §5.X 讨论状态机章节**
  - **验收**: DiscussionPostStatus / DiscussionComment 状态流转规则
  - **工作量**: 1h
  - **状态**: ⏳ backlog

---

## 5. OpenSpec Archive

- [ ] **5.1 跑 `openspec validate discussion-domain-drift-fix --type change`**
  - **验收**: PASS

- [ ] **5.2 跑最终回归测试**
  - **验收**: mvn compile + 关键测试通过

- [ ] **5.3 跑 `openspec archive discussion-domain-drift-fix`**
  - **验收**: change 已归档

---

## 进度追踪

```
1. 数据字典:    1.1✅ 1.2✅ 1.3✅
2. 状态机:      2.1✅ 2.2✅
3. API 契约:    3.1✅
4. 状态机设计:  4.1⬜ (backlog)
5. Archive:     5.1⬜ 5.2⬜ 5.3⬜
```

**总任务数**: 8
**已完成**: 6
**剩余**: 2 (4.1 backlog + 5.x Archive)

---

*任务拆解: 总工程师(接管自 Claude Code)*
*日期: 2026-07-27*