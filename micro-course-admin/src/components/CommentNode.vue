<!--
  评论节点组件
  路由路径: (component)
  Phase 3
  Author: jackie
-->
<template>
  <div class="comment-node" :style="{ marginLeft: depth * 16 + 'px' }">
    <div class="comment-header">
      <span class="comment-author">{{ comment.isAnonymous ? '匿名用户' : (comment.authorName || comment.userName || '未知') }}</span>
      <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
    </div>
    <div class="comment-body">{{ comment.content }}</div>
    <div class="comment-actions">
      <el-button link size="small" @click="handleToggleReply">回复</el-button>
      <el-button link size="small" @click="$emit('like', comment.id)">👍 {{ comment.likeCount || 0 }}</el-button>
    </div>
    <div v-if="showReply" class="reply-box">
      <el-input
        v-model="replyContent"
        :placeholder="replyPlaceholder"
        type="textarea"
        :rows="2"
        @keyup.enter.ctrl="handleReply"
      />
      <div class="reply-actions">
        <el-button size="small" @click="showReply = false">取消</el-button>
        <el-button type="primary" size="small" @click="handleReply" :disabled="!replyContent.trim()">发送</el-button>
      </div>
    </div>
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
import { ref, computed } from 'vue'

const props = defineProps({
  comment: { type: Object, required: true },
  depth: { type: Number, default: 0 }
})

const emit = defineEmits(['reply', 'like'])

const showReply = ref(false)
const replyContent = ref('')

const replyPlaceholder = computed(() => `回复 ${props.comment.isAnonymous ? '匿名用户' : (props.comment.authorName || '未知')}...`)

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const handleToggleReply = () => {
  showReply.value = !showReply.value
  if (!showReply.value) replyContent.value = ''
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
  border-left: 2px solid var(--el-border-color-light);
  padding-left: 12px;
  margin-bottom: 16px;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.comment-author {
  font-weight: 500;
  color: #303133;
  font-size: 14px;
}

.comment-time {
  font-size: 12px;
  color: #909399;
}

.comment-body {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 8px;
}

.comment-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-actions :deep(.el-button) {
  cursor: pointer;
}

.reply-box {
  margin-top: 8px;
  margin-bottom: 8px;
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>