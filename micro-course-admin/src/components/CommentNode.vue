<!--
  评论节点组件
  路由路径: (component)
  Phase 3
  Author: jackie
-->
<template>
  <div class="comment-node" :style="{ marginLeft: depth > 0 ? 'var(--space-6)' : '0' }">
    <!-- 节点卡片 -->
    <div class="comment-card" :class="{ 'is-child': depth > 0 }">
      <!-- 头像 + 用户名 + 时间 + 角色标签 -->
      <div class="comment-header">
        <el-avatar :size="28" :icon="UserIcon" class="comment-avatar" />
        <div class="comment-meta">
          <div class="comment-title-row">
            <span class="comment-author">{{ displayName }}</span>
            <el-tag v-if="comment.isAnonymous" type="info" size="small" class="author-tag">匿名</el-tag>
            <el-tag v-else-if="comment.roleTag === 'TEACHER'" type="success" size="small" class="author-tag">教师</el-tag>
            <el-tag v-else-if="comment.roleTag === 'ADMIN'" type="danger" size="small" class="author-tag">管理员</el-tag>
            <el-tag v-else-if="comment.isOp" type="warning" size="small" class="author-tag">楼主</el-tag>
          </div>
          <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
        </div>
      </div>

      <!-- 评论内容 -->
      <div class="comment-body">{{ comment.content }}</div>

      <!-- 操作按钮行 -->
      <div class="comment-actions">
        <el-button
          link
          size="small"
          :type="liked ? 'primary' : 'default'"
          class="action-btn"
          @click="$emit('like', comment.id)"
        >
          <el-icon class="action-icon"><Select /></el-icon>
          <span class="action-count">{{ comment.likeCount || 0 }}</span>
        </el-button>

        <el-button link size="small" class="action-btn" @click="handleToggleReply">
          <el-icon class="action-icon"><ChatLineRound /></el-icon>
          <span class="action-label">回复</span>
        </el-button>

        <el-button
          v-if="hasChildren"
          link
          size="small"
          class="action-btn collapse-btn"
          @click="showChildren = !showChildren"
        >
          <el-icon class="action-icon"><ArrowUp v-if="showChildren" /><ArrowDown v-else /></el-icon>
          <span class="action-label">{{ showChildren ? '收起' : `展开 ${childrenCount} 条回复` }}</span>
        </el-button>
      </div>

      <!-- 回复框 -->
      <div v-if="showReply" class="reply-box">
        <el-input
          v-model="replyContent"
          :placeholder="replyPlaceholder"
          type="textarea"
          :rows="2"
          class="reply-input"
          @keyup.enter.ctrl="handleReply"
        />
        <div class="reply-actions">
          <el-button size="small" @click="handleCancelReply">取消</el-button>
          <el-button type="primary" size="small" :disabled="!replyContent.trim()" @click="handleReply">发送</el-button>
        </div>
      </div>
    </div>

    <!-- 子评论递归 -->
    <template v-if="hasChildren && showChildren">
      <CommentNode
        v-for="child in comment.children"
        :key="child.id"
        :comment="child"
        :depth="depth + 1"
        @reply="$emit('reply', $event)"
        @like="$emit('like', $event)"
      />
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { User, Select, ChatLineRound, ArrowUp, ArrowDown } from '@element-plus/icons-vue'

const UserIcon = User

const props = defineProps({
  comment: { type: Object, required: true },
  depth: { type: Number, default: 0 }
})

const emit = defineEmits(['reply', 'like'])

const showReply = ref(false)
const showChildren = ref(true)
const replyContent = ref('')
const liked = ref(false)

const hasChildren = computed(() => props.comment.children && props.comment.children.length > 0)
const childrenCount = computed(() => props.comment.children?.length || 0)

const displayName = computed(() => {
  if (props.comment.isAnonymous) return '匿名用户'
  return props.comment.authorName || props.comment.userName || '未知'
})

const replyPlaceholder = computed(() => `回复 ${props.comment.isAnonymous ? '匿名用户' : (props.comment.authorName || props.comment.userName || '未知')}…`)

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const handleToggleReply = () => {
  showReply.value = !showReply.value
  if (!showReply.value) replyContent.value = ''
}

const handleCancelReply = () => {
  showReply.value = false
  replyContent.value = ''
}

const handleReply = () => {
  if (!replyContent.value.trim()) return
  emit('reply', { parentId: props.comment.id, content: replyContent.value.trim() })
  replyContent.value = ''
  showReply.value = false
}
</script>

<style scoped>
.comment-node {
  margin-bottom: var(--space-4);
}

.comment-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.comment-card:hover {
  box-shadow: var(--shadow-sm);
}

.comment-card.is-child {
  border-left: 2px solid var(--role-primary-light-5);
  border-radius: var(--radius-sm);
  padding: var(--space-3);
  background: var(--role-primary-light-9);
}

.comment-header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.comment-avatar {
  flex-shrink: 0;
  background: var(--role-primary-light-7);
  color: var(--role-primary);
}

.comment-meta {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.comment-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.comment-author {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
}

.author-tag {
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
}

.comment-time {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.comment-body {
  font-size: var(--text-base);
  color: var(--el-text-color-regular);
  line-height: var(--leading-relaxed);
  margin-bottom: var(--space-3);
  word-break: break-word;
}

.comment-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-wrap: wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--el-text-color-secondary);
  transition: color var(--duration-base) var(--ease-out);
  cursor: pointer;
}

.action-btn:hover {
  color: var(--role-primary);
}

.action-icon {
  font-size: var(--text-sm);
}

.action-count,
.action-label {
  font-size: var(--text-sm);
}

.collapse-btn {
  margin-left: auto;
}

.reply-box {
  margin-top: var(--space-3);
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: var(--space-3);
}

.reply-input {
  border-radius: var(--radius-sm);
}

.reply-input :deep(.el-textarea__inner) {
  border-radius: var(--radius-sm);
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
</style>
