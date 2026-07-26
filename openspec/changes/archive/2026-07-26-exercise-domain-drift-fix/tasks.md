# Tasks: 练习题库域 Spec 漂移全量修复

> **OpenSpec Change**: `exercise-domain-drift-fix`
> **Schema**: spec-driven
> **进度追踪**: `- [ ]` 复选框格式
> **单任务限制**: ≤ 2 小时

---

## 1. 数据字典

- [x] **1.1 partialScore BOOLEAN 同步**
  - **验收**: docs/数据字典.md L585 `partialScore | partial_score | BOOLEAN` 与 DB V + Entity 一致
  - **文件**: docs/数据字典.md
  - **状态**: ✅ pre-existing 已修复（v0.7 数据字典修订记录已标注）

---

## 2. 编译验证

- [x] **2.1 mvn compile 验证**
  - **验收**: `mvn -DskipTests compile` 返回 SUCCESS
  - **执行**: 2026-07-27 接管后已验证（SUCCESS）
  - **状态**: ✅ 已通过

---

## 3. OpenSpec Archive

- [ ] **3.1 跑 `openspec validate exercise-domain-drift-fix --type change`**
  - **验收**: PASS

- [ ] **3.2 跑最终回归测试**
  - **验收**: mvn compile + 关键测试通过

- [ ] **3.3 跑 `openspec archive exercise-domain-drift-fix`**
  - **验收**: change 已归档

---

## 进度追踪

```
1. 数据字典:    1.1✅
2. 编译:        2.1✅
3. Archive:     3.1⬜ 3.2⬜ 3.3⬜
```

**总任务数**: 4
**已完成**: 2
**剩余**: 2 (3.x Archive)

---

*任务拆解: 总工程师(接管自 Claude Code)*
*日期: 2026-07-27*