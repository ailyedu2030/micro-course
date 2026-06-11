<template>
  <div class="discussion-detail">
    <!-- 返回 -->
    <div class="back-bar">
      <el-button link @click="router.push('/discussions')">&lt; 返回列表</el-button>
    </div>

    <!-- 帖子主体 -->
    <el-card v-loading="postLoading" class="post-card" shadow="never">
      <template v-if="post">
        <div class="post-header">
          <h2 class="post-title">{{ post.title }}</h2>
          <div class="post-meta">
            <span class="author">{{ post.authorName }}</span>
            <span class="separator">·</span>
            <span class="time">{{ formatTime(post.createdAt) }}</span>
            <span class="separator">·</span>
            <span class="chapter">{{ post.chapterTitle }}</span>
          </div>
        </div>
        <div class="post-body">{{ post.content }}</div>
        <div class="post-actions">
          <el-button type="primary" link @click="handleLikePost">
            👍点赞 {{ post.likeCount }}
          </el-button>
          <el-tag v-if="post.isPinned" type="success" size="small">置顶</el-tag>
          <el-tag v-if="post.isEssence" type="warning" size="small">加精</el-tag>
        </div>
      </template>
    </el-card>

    <!-- 评论区域 -->
    <el-card class="comment-card" shadow="never">
      <template #header>
        <span>评论 {{ comments.length }}</span>
      </template>
      <div v-if="comments.length === 0" class="empty-comments">
        <span>暂无评论，快来抢沙发吧~</span>
      </div>
      <div v-else class="comment-list">
        <CommentNode
          v-for="comment in comments"
          :key="comment.id"
          :comment="comment"
          :depth="0"
          @reply="handleReply"
          @like="handleLikeComment"
          @delete="handleDeleteComment"
        />
      </div>
    </el-card>

    <!-- 发布评论 -->
    <el-card class="input-card" shadow="never">
      <div class="input-area">
        <el-input
          v-model="commentContent"
          type="textarea"
          :rows="3"
          placeholder="写下你的评论..."
        />
        <div class="input-actions">
          <el-button type="primary" :loading="submitLoading" @click="handleSubmitComment">发表评论</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPostById, getComments, createComment, deleteComment, likeComment } from '@/api/discussion'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postLoading = ref(false)
const submitLoading = ref(false)
const post = ref(null)
const comments = ref([])
const commentContent = ref('')
const replyParentId = ref(null)

function formatTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const fetchPost = async () => {
  postLoading.value = true
  try {
    const { data } = await getPostById(route.params.id)
    post.value = data
  } catch (error) {
    ElMessage.error('获取帖子详情失败')
  } finally {
    postLoading.value = false
  }
}

const fetchComments = async () => {
  try {
    const { data } = await getComments(route.params.id)
    comments.value = data.items || []
  } catch (error) {
    ElMessage.error('获取评论失败')
  }
}

const handleLikePost = async () => {
  try {
    await likeComment(post.value.id)
    post.value.likeCount = (post.value.likeCount || 0) + 1
    ElMessage.success('点赞成功')
  } catch (error) {
    ElMessage.error('点赞失败')
  }
}

const handleReply = async ({ parentId, content }) => {
  try {
    await createComment({ postId: route.params.id, parentId, content })
    ElMessage.success('回复成功')
    fetchComments()
  } catch (error) {
    ElMessage.error('回复失败')
  }
}

const handleLikeComment = async (id) => {
  try {
    await likeComment(id)
    const comment = findComment(comments.value, id)
    if (comment) comment.likeCount = (comment.likeCount || 0) + 1
    ElMessage.success('点赞成功')
  } catch (error) {
    ElMessage.error('点赞失败')
  }
}

const findComment = (list, id) => {
  for (const c of list) {
    if (c.id === id) return c
    const found = findComment(c.children || [], id)
    if (found) return found
  }
  return null
}

const handleDeleteComment = async (id) => {
  try {
    await deleteComment(id)
    ElMessage.success('删除成功')
    fetchComments()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const handleSubmitComment = async () => {
  if (!commentContent.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  submitLoading.value = true
  try {
    await createComment({ postId: route.params.id, content: commentContent.value })
    ElMessage.success('发表评论成功')
    commentContent.value = ''
    fetchComments()
  } catch (error) {
    ElMessage.error('发表评论失败')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  fetchPost()
  fetchComments()
})
</script>

<script>
// Recursive CommentNode component defined inline
import { defineComponent, ref, defineProps, defineEmits } from 'vue'

const CommentNode = defineComponent({
  name: 'CommentNode',
  props: {
    comment: { type: Object, required: true },
    depth: { type: Number, default: 0 }
  },
  emits: ['reply', 'like', 'delete'],
  setup(props, { emit }) {
    const showReply = ref(false)
    const replyContent = ref('')

    const handleReply = () => {
      emit('reply', { parentId: props.comment.id, content: replyContent.value })
      replyContent.value = ''
      showReply.value = false
    }

    const handleDelete = () => {
      emit('delete', props.comment.id)
    }

    const formatTime = (dateStr) => {
      if (!dateStr) return '-'
      const d = new Date(dateStr)
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    }

    return { showReply, replyContent, handleReply, handleDelete, formatTime }
  },
  template: `
    <div class="comment-node" :style="{ marginLeft: depth * 16 + 'px' }">
      <div class="comment-header">
        <span class="comment-author">{{ comment.authorName }}</span>
        <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
      </div>
      <div class="comment-body">{{ comment.content }}</div>
      <div class="comment-actions">
        <el-button link size="small" @click="showReply = !showReply">回复</el-button>
        <el-button link size="small" @click="$emit('like', comment.id)">👍 {{ comment.likeCount || 0 }}</el-button>
        <el-button v-if="comment.authorName === $attrs.currentUserName" type="danger" link size="small" @click="handleDelete">删除</el-button>
      </div>
      <div v-if="showReply" class="reply-box">
        <el-input v-model="replyContent" placeholder="写下回复..." class="reply-input" />
        <el-button size="small" type="primary" @click="handleReply">发送</el-button>
        <el-button size="small" @click="showReply = false">取消</el-button>
      </div>
      <CommentNode
        v-for="child in comment.children"
        :key="child.id"
        :comment="child"
        :depth="depth + 1"
        :current-user-name="$attrs.currentUserName"
        @reply="$emit('reply', $event)"
        @like="$emit('like', $event)"
        @delete="$emit('delete', $event)"
      />
    </div>
  `
})

export default {
  components: { CommentNode }
}
</script>

<style scoped>
.discussion-detail {
  padding: 20px;
}

.back-bar {
  margin-bottom: 12px;
}

.post-card {
  margin-bottom: 16px;
}

.post-header {
  margin-bottom: 16px;
}

.post-title {
  margin: 0 0 12px 0;
  font-size: 20px;
  color: #303133;
}

.post-meta {
  color: #909399;
  font-size: 14px;
}

.post-meta .separator {
  margin: 0 6px;
}

.post-body {
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
  margin-bottom: 16px;
}

.post-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.comment-card {
  margin-bottom: 16px;
}

.empty-comments {
  color: #909399;
  text-align: center;
  padding: 30px 0;
}

.comment-list {
  padding: 0;
}

.input-card {
  margin-bottom: 20px;
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}

/* CommentNode styles — must be unscoped to apply to recursive component */
:deep(.comment-node) {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

:deep(.comment-node:last-child) {
  border-bottom: none;
}

:deep(.comment-header) {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

:deep(.comment-author) {
  font-weight: 600;
  color: #303133;
}

:deep(.comment-time) {
  color: #909399;
  font-size: 12px;
}

:deep(.comment-body) {
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
  margin-bottom: 8px;
}

:deep(.comment-actions) {
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.reply-box) {
  margin-top: 8px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.reply-input { margin-bottom: 8px; }
</style>