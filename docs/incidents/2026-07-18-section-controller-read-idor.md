# 事故复盘 · 2026-07-18 Section/SectionSlide 读路径 IDOR 漏洞

## 事故概要

- **时间**: 2026-07-18 09:10 ~ 10:30 (CST)（Round 2 审计发现到修复完成）
- **影响范围**: `SectionController.listByChapter` / `getById` / `SectionSlideController.getSectionSlide`
- **业务影响**: 任何登录教师可越权枚举其他教师的课程结构、章节详情、课件内容
- **当前状态**: 已修复，待 PR + staging 验证

## 根因分析

### 直接原因

[SectionController.java](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/controller/SectionController.java#L19-L30) 类级别有 `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`，但具体读路径方法（listByChapter / getById）未做 ownership 校验。

[SectionSlideController.java 第 56-100 行](file:///Users/jackie/微课平台/micro-course-api/src/main/java/com/microcourse/controller/SectionSlideController.java#L56-L100) 同样问题：`@PreAuthorize("isAuthenticated()")` + API Key 路径，但 API Key 验证后未做 ownership 校验——任何 Hermes 客户端只要有有效 API Key 就能读任何 course 的 slide 课件。

### 直接后果

| 后果 | 严重度 |
|------|--------|
| 教师 A 可读教师 B 的课程结构（listByChapter） | 中 |
| 教师 A 可读教师 B 的章节详情（getById） | 中 |
| 任何 Hermes API Key 可读任何 course 的 HTML 课件内容 | 高 |
| 教学课件包含教育原创内容，可能涉及版权泄漏 | 高 |

### 根本原因

1. **写路径有 assertOwner，读路径缺**：create/update/delete 都有 `assertOwner(courseId)` 调用，list/getById 漏了
2. **SectionSlideController 没复用 SlideController 的 verifyAccess**：两个 controller 风格不一致
3. **类级别权限不等于方法 ownership**：类级别 `@PreAuthorize` 只校验角色身份，不校验资源所有权

### 横向扫描

| 路径 | 状态 |
|------|------|
| SectionServiceImpl.create | ✅ 有 assertOwner |
| SectionServiceImpl.update | ✅ 有 assertOwner |
| SectionServiceImpl.delete | ✅ 有 assertOwner |
| SectionServiceImpl.listByChapter | ❌ **本 PR 修复** |
| SectionServiceImpl.getById | ❌ **本 PR 修复** |
| SectionSlideController.getSectionSlide | ❌ **本 PR 修复** |
| SlideController 各方法 | ✅ 有 verifyAccess（course owner/admin/已选课学生） |
| CourseController 各方法 | ✅ 已审过（v1.22.0） |
| SlidePageController | ⏳ 待审 |
| ExerciseController | ⏳ 待审 |

## 修复方案

### 1. SectionServiceImpl.listByChapter / getById 加 assertOwner

```java
public PageResult<SectionDTO> listByChapter(Long chapterId, int page, int size) {
    CourseChapter chapter = chapterRepo.selectById(chapterId);
    if (chapter == null) throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
    assertOwner(chapter.getCourseId());  // P0-3 新增
    // ... 原逻辑
}

public SectionDTO getById(Long id) {
    CourseSection section = findOrThrow(id);
    assertOwner(section.getCourseId());  // P0-3 新增
    return toDTO(section);
}
```

### 2. SectionSlideController.getSectionSlide 加 ownership 校验

复用 `SecurityUtil` + `CourseRepository`：
- ADMIN 豁免
- TEACHER 必须 owner
- 其他角色（STUDENT）：本接口只供教师/AI 同步使用，统一拒绝

## 防止再发

### 已实施（P0-3）

1. ✅ 读路径调用 assertOwner（与写路径一致）
2. ✅ SectionSlideController 加 ownership 校验
3. ✅ 3 个单元测试覆盖正常/越权 list/越权 getById

### 推荐改进（下一版本）

1. **架构改进**：所有 controller 的 `@PreAuthorize("isAuthenticated()")` 应改成 `@PreAuthorize("@courseSecurity.canAccess(#courseId)")` 用 Spring EL 表达式
2. **precheck 扫描**：写脚本扫描所有 controller 的 GET 路径，验证是否走 ownership 校验路径
3. **强制 5 维审查**：R2 审查（安全视角）必须有 owner 校验矩阵

## Rollback

```bash
git revert 8273b75d --no-edit  # 或本次 PR commit
git push origin main
```

无 DB schema 变更，回滚仅应用层，立即生效。

## 修复证据

| 验证 | 结果 |
|------|------|
| 编译 | ✅ `mvn -q -DskipTests compile` 通过 |
| SectionOwnershipTest | ✅ 3/3 测试通过 |
| 全量 mvn test | ✅ 600/600 测试通过（含新增） |
| 横向扫描 | ⏳ SlidePage/Exercise 待审 |

## 关联

- PR: 待创建（Round 2 Section IDOR 修复）
- 事故关联: 与 `2026-07-18-micro-specialty-proposal-teacherid-placeholder-bug.md` 同期发现

---

**事故定级**: P1-C（业务可感知的越权读取）
**修复状态**: ✅ 代码完成，待 PR + staging 验证