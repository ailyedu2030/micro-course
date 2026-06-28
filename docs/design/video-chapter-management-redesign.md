# 章节视频内容管理重构设计文档

> 文档版本: v1.0
> 设计日期: 2026-06-29
> 调研基础: 4 个并行 Agent 深度审计
> 调研对象: 后端能力 / 行业 UX / 前端路由 / 上传架构
> 状态: 已确认，可执行

## 一、问题陈述

**用户反馈**: "在这里上传视频，应该是这一个章节的视频，需要批量上传吗？需要选择章节吗？需要列表吗？"

**根本原因**: `CourseDetail.vue:466` 的 `handleManageChapterContent` 跳转到 `VideoList.vue` 时，**只传了 `courseId`，从未传 `chapterId`**。`VideoList.vue` 是同时支撑两种截然不同场景的共享组件，导致大量逻辑分支和混乱的 UX。

## 二、现状分析

### 2.1 痛点清单（按影响排序）

| 严重度 | 问题 | 位置 | 客户影响 |
|--------|------|------|----------|
| **P0** | 章节上下文丢失 | `CourseDetail.vue:466` | 跳转后必须重新选章节 |
| **P0** | 静默错误上传 | `VideoList.vue:52, 368` | 未选章节时 chapterId 传空串，DB 接收 null，归属丢失 |
| **P0** | 进度条永远 0% | `VideoList.vue:65` | 5 分钟上传看不到任何反馈，欺骗用户 |
| P1-C | 视频换章节不支持 | `VideoUpdateRequest` | 上传后无法重新分配章节 |
| P1-C | 章节列表无视频数 | `ChapterVO` | 只显示 ●/○，不知 1 个还是 10 个 |
| P1-I | 文件落盘与 DB 写入非原子 | `VideoController.upload` | DB 失败 → 磁盘孤儿文件 |

### 2.2 行业最佳实践对比

| 平台 | 关键模式 | 借鉴 |
|------|---------|------|
| Coursera | 章节内拖拽上传 | 上下文锁定 |
| Udemy | 章节内联上传 | 章节即容器 |
| **Tencent Classroom** | **章节行内拖入** | **最契合本项目** |
| Bilibili | 创作中心独立页 | 全局上传（管理员） |
| edX | 上传+后续分配 | 灵活但复杂 |

## 三、设计目标

1. **上下文不丢失**: 从章节进入时，章节是确定的
2. **无冗余操作**: 不让用户在同一操作中重复选择已确定的项
3. **零静默错误**: 不允许上传成功但归属丢失
4. **快速反馈**: 上传进度实时显示
5. **可恢复**: 视频可重新分配章节

## 四、最终设计

### 4.1 用户流程（单一流程）

```
教师访问课程详情 (/courses/22)
  │
  ▼
  看到章节表格，每行有"内容"按钮
  │
  └─[点击 Chapter 3 的"内容"]─→ /courses/22/videos?chapterId=45
                                │
                                ▼
                          ┌─────────────────────────┐
                          │  面包屑: 课程 → 第3章 · 名称 │
                          │  章节目录: [锁定第3章 ▼]   │
                          │  [批量上传视频]  [新增视频]  │
                          │                            │
                          │  本章节视频列表:            │
                          │  ┌──────────────────────┐ │
                          │  │ 缩略图 │ 标题 │ 状态 │ 操作 │ │
                          │  │  [图]  │ 1.mp4│ 完成 │[改][删]│ │
                          │  │  [图]  │ 2.mp4│ 上传中│[改][删]│ │
                          │  └──────────────────────┘ │
                          └─────────────────────────┘
                                │
                                └─[点"返回课程"]─→ /courses/22
```

### 4.2 URL 协议

| 场景 | URL | 含义 |
|------|-----|------|
| 全局（管理员） | `/videos` | 显示课程+章节选择器 |
| 章节上下文 | `/courses/:courseId/videos?chapterId=:chapterId` | 章节锁定，隐藏选择器 |

`chapterId` query 参数是章节上下文的唯一信源。

### 4.3 组件架构（保持 1 个组件，避免组件爆炸）

**`VideoList.vue` 两种模式合一**：
- `isContextualMode = computed(() => !!route.query.chapterId)`
- 通过 `v-if` 控制不同 UI
- 不新增文件，不抽组件

### 4.4 状态管理

**URL 优先**（无 Pinia store）：
- 章节上下文：`route.query.chapterId`
- 课程上下文：`route.params.courseId`
- 搜索/分页：`route.query`

## 五、Phase 1: 章节上下文锁定（最低风险）

### 5.1 `CourseDetail.vue`

**修改位置**: `handleManageChapterContent`（第 459-468 行）

**改动**:
```javascript
const handleManageChapterContent = (row) => {
  const cid = courseId.value
  if (row.chapterType === 'INTERACTIVE') {
    router.push(`/teacher/courses/${cid}/slides/manage`)
  } else if (row.chapterType === 'EXERCISE') {
    router.push(`/courses/${cid}/exercises`)
  } else {
    router.push(`/courses/${cid}/videos?chapterId=${row.id}`)  // 新增 chapterId
  }
}
```

### 5.2 `VideoList.vue` 改动

**新增状态**:
```javascript
const isContextualMode = computed(() => !!route.query.chapterId)
const lockedChapterId = computed(() => {
  const id = route.query.chapterId
  return id ? Number(id) : null
})
const chapterTitle = ref('')

// onMounted 加载章节标题
if (lockedChapterId.value) {
  const { data } = await getChapterById(lockedChapterId.value)
  chapterTitle.value = data?.title || ''
}
```

**模板改动**:
- 课程选择器: `v-if="!isContextualMode"`
- 章节选择器: 锁定模式下 `:disabled="isContextualMode"`
- 上传按钮: `{{ isContextualMode ? '上传本章节视频' : '批量上传视频' }}`
- 面包屑: `v-if="isContextualMode"` 显示 "课程 → 第N章"

**上传逻辑**:
```javascript
const handleBatchUpload = async ({ file }) => {
  const courseId = lockedChapterId.value || searchForm.courseId
  const chapterId = lockedChapterId.value || searchForm.chapterId
  // 优先使用锁定的 chapterId（URL）
  // 锁定模式下不上传未锁定章节的文件
  formData.append('courseId', courseId)
  formData.append('chapterId', chapterId)
  await uploadVideo(formData)
}
```

## 六、Phase 2: 视频可调整章节

### 6.1 后端 `VideoUpdateRequest`

**文件**: `micro-course-api/src/main/java/com/microcourse/dto/VideoUpdateRequest.java`

**新增字段**:
```java
private Long chapterId;  // 允许编辑时换章节（null 表示不修改）
```

**后端 `VideoServiceImpl.update` 改动**:
```java
if (request.getChapterId() != null) {
    // 校验新章节属于同一课程
    assertChapterBelongsToCourse(request.getChapterId(), video.getCourseId());
    video.setChapterId(request.getChapterId());
}
```

### 6.2 前端 `VideoList.vue` 编辑弹窗

**改动**:
- 在编辑弹窗中增加"所属章节"下拉
- 用户可调整视频到其他章节
- 提交时携带 `chapterId`（仅当用户改动时）

## 七、Phase 3: 视频计数 + 进度条

### 7.1 后端 `ChapterVO`

**新增字段**:
```java
private Integer videoCount;
```

**后端 `CourseChapterServiceImpl.page` 改动**:
```sql
SELECT cc.*, COUNT(v.id) as video_count
FROM course_chapters cc
LEFT JOIN videos v ON v.chapter_id = cc.id AND v.deleted_at IS NULL
WHERE cc.course_id = ?
GROUP BY cc.id
```

### 7.2 前端 `CourseDetail.vue` 章节表格

**改动**:
- "内容状态"列改为: `{{ row.videoCount > 0 ? '●' + row.videoCount + '个视频' : '○ 待添加' }}`

### 7.3 前端 `VideoList.vue` 进度条

**改动**:
- 实现真正的进度回调
- 通过 `onUploadProgress` 更新 `queueItem.percentage`

## 八、风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| 破坏现有 `/videos` 全局访问 | 低 | P1-C | `isContextualMode` 守卫，未带 chapterId 走原逻辑 |
| 上传原子性问题（DB 失败磁盘泄漏） | 中 | P0 | 本次不解决，独立工单（要求事务包裹） |
| 视频编辑后章节 ID 类型不匹配 | 低 | P1-C | 前端统一用 `Number()` 转换 |
| 章节不存在（用户手动改 URL） | 低 | P1-C | `getChapterById` 404 时显示错误状态 |

## 九、Phase 划分

| Phase | 范围 | 工期估计 |
|-------|------|---------|
| Phase 1 | CourseDetail 传 chapterId + VideoList 锁定章节 | 1 小时 |
| Phase 2 | 后端 VideoUpdateRequest + 前端编辑弹窗 | 1.5 小时 |
| Phase 3 | ChapterVO videoCount + 进度条修复 | 1.5 小时 |
| **总计** | | **4 小时** |

## 十、验收清单

### Phase 1 验收
- [ ] 从课程详情点击"内容"→ URL 包含 `?chapterId=X`
- [ ] VideoList 页面章节下拉自动选中并禁用
- [ ] 课程下拉在上下文模式下隐藏
- [ ] 上传按钮显示"上传本章节视频"
- [ ] 面包屑显示"课程 → 第N章"
- [ ] 移除 URL 中的 chapterId 恢复全局模式
- [ ] 不带 chapterId 的 `/courses/22/videos` 仍可访问

### Phase 2 验收
- [ ] 编辑视频时显示当前章节
- [ ] 可选择其他章节
- [ ] 保存后视频归属更新
- [ ] 后端校验新章节属于同一课程

### Phase 3 验收
- [ ] 章节列表显示视频数
- [ ] 上传进度条实时更新
- [ ] 文件大于 2GB 立即拒绝（前端预检）

## 十一、文件改动清单

### 前端
- `micro-course-admin/src/views/courses/CourseDetail.vue`（Phase 1, 3）
- `micro-course-admin/src/views/courses/VideoList.vue`（Phase 1, 2, 3）

### 后端
- `micro-course-api/src/main/java/com/microcourse/dto/VideoUpdateRequest.java`（Phase 2）
- `micro-course-api/src/main/java/com/microcourse/service/impl/VideoServiceImpl.java`（Phase 2）
- `micro-course-api/src/main/java/com/microcourse/dto/ChapterVO.java`（Phase 3）
- `micro-course-api/src/main/java/com/microcourse/service/impl/CourseChapterServiceImpl.java`（Phase 3）

### 数据库
- 无（无需 schema 变更）

## 十二、不做清单（明确边界）

- ❌ 分片上传（过大改动，独立工单）
- ❌ Pinia store（URL 足够）
- ❌ 抽离新组件（保持单文件两模式）
- ❌ 上传原子性（独立工单）
- ❌ 秒传（独立工单）
- ❌ 断点续传（独立工单）

## 十三、参考资料

- 后端能力审计: 4 个并行 Agent 输出
- 行业 UX 调研: Coursera / Udemy / edX / Bilibili / Tencent Classroom
- 当前代码: `/Users/jackie/微课平台/`
