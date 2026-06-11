# Gate 3 前端模式参考

> 涵盖讨论区、通知中心、学习进度等 Gate 3 特有 UI 模式
> 与 references/ 下其他文件互补（通用 CRUD 模式见 frontend-api-usage.md）

---

## 1. 讨论区 · 嵌套评论树

### 1.1 递归组件模板

```vue
<!-- components/CommentNode.vue — 递归渲染单条评论及其子回复 -->
<template>
  <div class="comment-node" :style="{ marginLeft: depth * 16 + 'px' }">
    <div class="comment-header">
      <span class="comment-author">{{ comment.authorName }}</span>
      <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
    </div>
    <div class="comment-body">{{ comment.content }}</div>
    <div class="comment-actions">
      <el-button link size="small" @click="showReply = !showReply">回复</el-button>
      <el-button link size="small" @click="$emit('like', comment.id)">👍 {{ comment.likeCount }}</el-button>
    </div>
    <!-- 回复输入框 -->
    <div v-if="showReply" class="reply-box">
      <el-input v-model="replyContent" placeholder="写下回复..." @keyup.enter="handleReply" />
    </div>
    <!-- 递归渲染子回复 -->
    <CommentNode
      v-for="child in comment.children"
      :key="child.id"
      :comment="child"
      :depth="depth + 1"
      @reply="$emit('reply', $event)"
      @like="$emit('like', $event)"
    />
  </div>
</template>
<script setup>
import { ref } from 'vue'
const props = defineProps({ comment: Object, depth: { type: Number, default: 0 } })
const showReply = ref(false)
const replyContent = ref('')
</script>
```

**使用方式**：在帖子详情页加载顶层评论列表，每个 comment 对象需含 `children` 数组（后端在 GET post 时返回树结构）。

### 1.2 帖子列表模式

帖子列表使用标准 `el-table` + 分页，与 CourseList.vue 一致。唯一差异：帖子标题需链接到详情页。

```
GET /api/discussions/posts?chapterId=x&page=0&size=20  →  data.items
```

---

## 2. 通知中心

### 2.1 轮询模式

```javascript
// store/notification.js — Pinia store
import { defineStore } from 'pinia'
import { getNotifications, markAsRead } from '@/api/notification'

export const useNotificationStore = defineStore('notification', {
  state: () => ({ list: [], unreadCount: 0, polling: false }),
  actions: {
    async fetch() {
      const res = await getNotifications({ page: 0, size: 20 })
      this.list = res.data.items
      this.unreadCount = res.data.items.filter(n => !n.isRead).length
    },
    startPolling(intervalMs = 30000) {
      if (this.polling) return
      this.polling = true
      this.fetch()
      this.timer = setInterval(() => this.fetch(), intervalMs)
    },
    stopPolling() { clearInterval(this.timer); this.polling = false },
    async markRead(id) {
      await markAsRead(id)
      const n = this.list.find(n => n.id === id)
      if (n) { n.isRead = true; this.unreadCount-- }
    }
  }
})
```

### 2.2 顶栏未读角标

在 Layout.vue 顶栏中添加通知铃铛：
```html
<el-badge :value="notificationStore.unreadCount" :hidden="!notificationStore.unreadCount">
  <el-icon @click="showNotifications" style="cursor:pointer"><Bell /></el-icon>
</el-badge>
```

**启动轮询**：Layout.vue `onMounted` 中调用 `notificationStore.startPolling()`，`onUnmounted` 调用 `stopPolling()`。

### 2.3 通知类型路由

| notification_type | 点击跳转 | 示例消息 |
|------------------|---------|---------|
| `COURSE_PUBLISHED` | `/courses/${targetId}` | "课程《数据结构》已发布" |
| `POST_REPLY` | `/courses/${targetId}?postId=xx` | "有人回复了你的讨论帖" |
| `EXERCISE_REVIEWED` | `/exercises?courseId=${targetId}` | "你的练习已批改完成" |
| `SYSTEM_ANNOUNCE` | 弹窗 | "系统将于今晚升级" |

---

## 3. 学习进度 · 可视化

### 3.1 进度条

```vue
<el-progress :percentage="progress" :color="progress >= 80 ? '#67c23a' : '#409eff'" :stroke-width="12" />
```

### 3.2 打卡日历热力图

使用 `el-calendar` 组件 + 自定义 cell 渲染。打卡日期用绿色背景标记，连续打卡天数（streak）在顶部数字显示。

### 3.3 学习时长格式化

```javascript
function formatDuration(seconds) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return h > 0 ? `${h}小时${m}分钟` : `${m}分钟`
}
```

---

## 4. Gate 3 前端路由规划

```
/discussions?chapterId=x     — 章节讨论帖列表
/discussions/:id              — 帖子详情（含嵌套评论）
/notifications                — 通知列表
/learning-progress?courseId=x — 学习进度详情
/check-in                     — 打卡日历
/review                       — 课程审核列表（管理员）
```

---

## 5. 后端 API 速查（Gate 3）

| 域 | API | 说明 |
|----|-----|------|
| 讨论帖 | GET/POST /api/discussions/posts | 帖子列表+发帖 |
| 讨论帖 | GET /api/discussions/posts/{id} | 帖子详情（含树结构评论） |
| 回复 | POST /api/discussions/comments | 发回帖 |
| 回复 | POST /api/discussions/comments/{id}/like | 点赞 |
| 通知 | GET /api/notifications | 通知列表 |
| 通知 | PUT /api/notifications/{id}/read | 标记已读 |
| 进度 | GET /api/learning-progress | 学习进度 |
| 进度 | GET /api/learning-progress/check-in | 打卡记录 |

---

*视图版本：v1.0 · 最后更新：2026-06-11*
