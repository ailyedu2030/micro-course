# Hermes API 修复建议（完整版）

> 基于 2026-07-13 生产对接全量排查
> 通信方式：仅通过 `X-API-Key` Header 鉴权，不引入其他权限机制

---

## 🔴 P0：`GET /courses` 返回全量课程

### 问题

当前 `GET /courses` 只返回 `hermes_course_mapping` 表中有映射记录的课程。平台上通过管理后台直接创建的课程（无 Hermes 映射）不可见，无法管理。

### 修复

```java
// HermesWebhookController.java

@GetMapping("/courses")
public R<List<HermesCourseListVO>> listCourses(
        @RequestHeader("X-API-Key") String apiKey) {
    User caller = authenticate(apiKey);  // API Key 即授权凭证

    // Before: 查 hermes_course_mapping → 只返回有映射的
    // List<HermesCourseMapping> mappings = mappingRepository.selectList(...);

    // After: 直接查 courses 表，返回全量课程
    List<Course> allCourses = courseRepository.selectList(
        new LambdaQueryWrapper<Course>()
            .eq(Course::getDeletedAt, null)
            .orderByDesc(Course::getCreatedAt));
    
    // 转换为 HermesCourseListVO（含 courseId、title、status 等）
    return R.ok(convertToVO(allCourses));
}
```

### 影响

- ✅ Hermes 可以看到平台上所有课程
- ✅ 知道每个课程的内部 `courseId`
- ✅ 后续可以用 `courseId` 做删除/更新操作

---

## 🔴 P0：新增 `DELETE /courses/by-course-id/{courseId}`

### 问题

当前 `DELETE /courses/{hermesCourseId}` 只能删除有 Hermes 映射的课程。大量通过管理后台创建的脏数据无法清理。

### 修复

```java
// 新增端点：按内部 courseId 删除，不依赖 Hermes 映射
@DeleteMapping("/courses/by-course-id/{courseId}")
public R<?> deleteByCourseId(
        @RequestHeader("X-API-Key") String apiKey,
        @PathVariable Long courseId) {
    authenticate(apiKey);  // API Key 即授权凭证

    // 级联删除：course → chapters → sections → slides → slide_pages
    courseService.deleteCourse(courseId);
    // 同时清理可能存在的 Hermes 映射
    mappingRepository.delete(
        new LambdaQueryWrapper<HermesCourseMapping>()
            .eq(HermesCourseMapping::getCourseId, courseId));
    return R.ok();
}
```

### 保留原有端点

```java
// 保留：按 hermesCourseId 删除（已有映射的课程）
@DeleteMapping("/courses/{hermesCourseId}")
public R<?> deleteByHermesId(...) { ... }
```

### 使用方式

```bash
# 方式 1：通过 Hermes ID 删除（已有映射）
DELETE /courses/ai-harness-engineering

# 方式 2：通过内部 courseId 删除（任意课程）
DELETE /courses/by-course-id/44
```

---

## 🔴 P0：`POST /courses` 关联 slide 到 section

### 问题

`POST /courses/{id}/lessons/{lid}/slide` 上传 HTML 课件后，`course_slides` 表有记录，但 `course_sections.content_url` 字段为空。导致查看课时时读不到课件内容。

### 修复

```java
// HermesWebhookController.java — uploadSlide() 方法

// uploadHtmlFile 成功后，回写 section.content_url
resp = slideService.uploadHtmlFile(courseId, file, chapterId);

// ✅ 新增：将 slide URL 写入 section
if (resp != null && resp.getFileUrl() != null) {
    CourseSection section = sectionRepository.selectById(lessonId);
    if (section != null) {
        section.setContentUrl(resp.getFileUrl());
        sectionRepository.updateById(section);
    }
}
```

---

## 🟡 P1：`POST /courses` teacherId 自动填充

### 问题

当前 `POST /courses` 强制要求 `teacherId` 等于 API Key 所属用户。API Key 本身就是 Hermes 授权凭证，不应再要求额外传参。

### 修复

```java
// POST /courses — buildCourse() 方法

// teacherId 不传则自动使用 API Key 对应的用户 ID
if (dto.getTeacherId() == null) {
    dto.setTeacherId(caller.getId());
}
// 移除 teacherId != caller.id 的 403 校验
```

---

## 🟡 P1：Section API 补充 contentUrl 返回

### 问题

`GET /courses/{id}/sections` 当前返回字段中不包含 `hasSlide` 和 `contentUrl`，无法判断课时是否有课件。

### 修复

```java
// SectionVO 补充字段
public class SectionVO {
    // ... 现有字段
    private Boolean hasSlide;     // 是否有课件
    private String contentUrl;    // 课件 URL
}
```

---

## 总结：改动清单

| 优先级 | 端点 | 改动 | 关联文件 |
|--------|------|------|---------|
| 🔴 P0 | `GET /courses` | 改为查询 `courses` 全表，不过滤 mapping | `HermesWebhookController.java` |
| 🔴 P0 | `DELETE /courses/by-course-id/{id}` | 新增，按内部 courseId 直接删除 | `HermesWebhookController.java` |
| 🔴 P0 | `POST /lessons/{lid}/slide` | 上传后回写 `section.content_url` | `HermesWebhookController.java` |
| 🟡 P1 | `POST /courses` | teacherId 可选，自动填充 | `HermesWebhookController.java` |
| 🟡 P1 | `GET /sections` | SectionVO 增加 `hasSlide`/`contentUrl` | `SectionVO.java` |
