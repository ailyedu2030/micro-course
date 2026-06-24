# 全项目 Service 层保护审计 · 批量修复计划 v1.0

> 定位：系统性修复 28 个 create + 12 个 delete 的 DataIntegrityViolationException 风险
> 基于 2026-06-24 3 Agent 交叉扫描结果
> 模式固化：`selectCount + BusinessException` 通用方案

---

## 0. 修复模式（通用模板）

### create 唯一性检查模板

```java
// 在 insert/repository.insert 之前
if (request.getField() != null && !request.getField().isBlank()) {
    long count = repository.selectCount(
            new LambdaQueryWrapper<Entity>()
                    .eq(Entity::getField, request.getField()));
    if (count > 0) {
        throw new BusinessException(ErrorCode.ENTITY_FIELD_EXISTS);
    }
}
```

### delete FK 检查模板

```java
// 在 deleteById 之前
long fkCount = fkRepository.selectCount(
        new LambdaQueryWrapper<FKEntity>()
                .eq(FKEntity::getFkField, id));
if (fkCount > 0) {
    throw new BusinessException(ErrorCode.ENTITY_HAS_CHILDREN);
}
```

---

## 1. P0 批次（数据完整性 · 必须优先修）

| 批次 | 文件 | 问题 | 修复内容 | 估算 |
|------|------|------|---------|:----:|
| **P0-A** | `CourseServiceImpl.delete()` | 删课程不检查 enrollment/course_favorite/course_review | 3 个 FK 检查 + ErrorCode + 前端 catch | 30 行 |
| **P0-B** | `MicroSpecialtyServiceImpl.delete()` | 删微专业不检查子表引用 | 3 个 FK 检查 | 25 行 |

**影响**：不修则删除主记录会 → 500 / 孤儿数据 / 数据库违反约束。

---

## 2. P1 批次（运行时 500 · 高优先级）

| 批次 | 文件 | 问题 | 修复内容 | 估算 |
|------|------|------|---------|:----:|
| **P1-A** | `TeachingClassServiceImpl.delete()` | 不检查 teaching_class_student | 1 个 FK 检查 | 10 行 |
| **P1-B** | `CourseBundleServiceImpl.delete()` | 不检查 bundle_item | 1 个 FK 检查 | 10 行 |
| **P1-C** | `LessonServiceImpl.delete()` | 不检查 learning_progress | 1 个 FK 检查 | 10 行 |
| **P1-D** | `VideoServiceImpl.delete()` | 不检查 video_transcode/video_bookmark | 2 个 FK 检查 | 15 行 |
| **P1-E** | `TagServiceImpl.delete()` | 不检查 course_tag_relation | 1 个 FK 检查 | 10 行 |
| **P1-F** | `DiscussionPostServiceImpl.delete()` | 不检查 comment | 1 个 FK 检查 + 级联 | 15 行 |

---

## 3. P2 批次（唯一性 · UX 改善）

| 批次 | 文件 | 检查字段 |
|------|------|---------|
| **P2-A** | `CourseServiceImpl.create()` | code 唯一性 |
| **P2-B** | `CourseCategoryServiceImpl.create()` | code 唯一性 |
| **P2-C** | `TagServiceImpl.create()` | name 唯一性 |
| **P2-D** | `CourseBundleServiceImpl.create()` | name 唯一性 |
| **P2-E** | `TeachingClassServiceImpl.create()` | name 唯一性 |
| **P2-F** | `MicroSpecialtyServiceImpl.create()` | name 唯一性 |
| **P2-G** | `BadgeServiceImpl.defineBadge()` | code 唯一性 |
| **P2-H** | `VideoServiceImpl.create()` | —（可选） |
| **P2-I** | `CourseChapterServiceImpl.create()` | title+courseId 组合 |

---

## 4. 执行建议

| 维度 | 建议 |
|------|------|
| **工具** | 复用 `scripts/phase14-autopilot/` 流程 |
| **并行度** | 每批次 3-5 文件并发（无依赖关系） |
| **验证** | 每批次后 `mvn compile` + curl 测试 + 前端刷新 |
| **commit** | 每批次独立 commit，标注 "fix(service-guard): ..." |

**最简继续指令（新会话）**：

```
我是 service-guard 批量修复编排器。
读 /Users/jackie/微课平台/docs/开发规划/service-guard-plan.md

从 P0-A 开始 (CourseServiceImpl.delete)：
1. 读文件 → 分析 FK 依赖
2. 加 selectCount + BusinessException 检查
3. 加 ErrorCode（如有必要）
4. 更新前端 handleDelete 的错误码
5. mvn compile + curl 验证
6. git commit
```

---

*计划版本：v1.0*
*签发：2026-06-24 总工程师*
*基于 3 Agent 并行交叉扫描结果*
