# Tasks: 教学班/线下域 Spec 漂移全量修复

> **OpenSpec Change**: `teaching-domain-drift-fix`
> **Schema**: spec-driven
> **进度追踪**: `- [ ]` 复选框格式
> **单任务限制**: ≤ 2 小时

---

## 1. 数据字典补充

- [x] **1.1 补充 chapter_offline_sessions 到数据字典 §2.6.1**
  - **验收**: §2.6.1 chapter_offline_sessions 表完整定义（含 12 字段 + 2 索引）
  - **字段**: id / chapterId(FK) / sessionDate / startTime / endTime / location / teacherNotes / sortOrder / createdAt / updatedAt / version / deletedAt
  - **索引**: idx_cos_chapter + uk_cos_chapter_sort
  - **文件**: docs/数据字典.md §2.6.1
  - **状态**: ✅ 已修复 (2026-07-27 v1.x 同步)

- [x] **1.2 补充 attendance_records 到数据字典 §2.6.2**
  - **验收**: §2.6.2 attendance_records 表完整定义（含 8 字段 + 3 索引）
  - **字段**: id / sessionId(FK) / userId(FK) / status(String NOT NULL default 'ABSENT') / checkinTime / updatedBy(FK) / createdAt / updatedAt
  - **索引**: idx_ar_session + idx_ar_user + uk_ar_session_user
  - **文件**: docs/数据字典.md §2.6.2
  - **状态**: ✅ 已修复 (2026-07-27 v1.x 同步)

---

## 2. 验证

- [x] **2.1 grep 验证两表名已加入数据字典**
  - **验收**: `grep -n "chapter_offline_sessions\|attendance_records" docs/数据字典.md` 命中 §2.6.1/§2.6.2
  - **执行**: 2026-07-27 已验证
  - **状态**: ✅ 已通过

- [x] **2.2 Entity 字段一致性验证**
  - **验收**: 数据字典字段与 Entity `@TableField` 注解一致
  - **状态**: ✅ 已通过

---

## 3. OpenSpec Archive

- [ ] **3.1 跑 `openspec validate teaching-domain-drift-fix --type change`**
  - **验收**: PASS

- [ ] **3.2 跑最终回归测试**
  - **验收**: mvn compile + 关键测试通过

- [ ] **3.3 跑 `openspec archive teaching-domain-drift-fix`**
  - **验收**: change 已归档

---

## 进度追踪

```
1. 数据字典:    1.1✅ 1.2✅
2. 验证:        2.1✅ 2.2✅
3. Archive:     3.1⬜ 3.2⬜ 3.3⬜
```

**总任务数**: 6
**已完成**: 4
**剩余**: 2 (3.x Archive)

---

*任务拆解: 总工程师(接管自 Claude Code)*
*日期: 2026-07-27*