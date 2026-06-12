<!--
  讨论详情
  路由路径: /courses/discussions/:id
  Phase 1
  Author: jackie
-->
<template>
  <div class="discussion-detail-page">
    <!-- 帖子卡片 -->
    <el-card class="post-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">帖子详情</span>
          <div class="header-actions">
            <el-button v-if="postData.status === 'PENDING'" type="success" @click="handleApprove">通过</el-button>
            <el-button v-if="postData.status === 'PENDING'" type="danger" @click="handleReject">驳回</el-button>
            <el-button type="danger" @click="handleDelete">删除</el-button>
            <el-button @click="handleBack">返回</el-button>
          </div>
        </div>
      </template>

      <div v-loading="loading">
        <div class="post-header">
          <h2 class="post-title">{{ postData.title }}</h2>
          <div class="post-meta">
            <span class="meta-item">作者：{{ postData.authorName || '-' }}</span>
            <span class="meta-item">课程：{{ postData.courseName || '-' }}</span>
            <span class="meta-item">发布时间：{{ postData.createdAt || '-' }}</span>
            <el-tag v-if="postData.status === 'PENDING'" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="postData.status === 'PUBLISHED'" type="success" size="small">已发布</el-tag>
            <el-tag v-else-if="postData.status === 'DELETED'" type="info" size="small">已删除</el-tag>
          </div>
        </div>
        <div class="post-content">{{ postData.content }}</div>
      </div>
    </el-card>

    <!-- 回复列表 -->
    <el-card class="reply-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">回复列表</span>
          <span class="reply-count">共 {{ replies.length }} 条回复</span>
        </div>
      </template>

      <div v-if="replies.length === 0" class="empty-replies">
        <el-empty description="暂无回复" />
      </div>

      <div v-else class="reply-list">
        <div v-for="reply in replies" :key="reply.id" class="reply-item">
          <div class="reply-header">
            <span class="reply-author">{{ reply.authorName || '-' }}</span>
            <span class="reply-time">{{ reply.createdAt || '-' }}</span>
            <el-button type="danger" link size="small" @click="handleDeleteReply(reply)">删除</el-button>
          </div>
          <div class="reply-content">{{ reply.content }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDiscussionById, approveDiscussion, rejectDiscussion, deleteDiscussion, getComments, deleteComment } from '@/api/discussion'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const postData = ref({})
const replies = ref([])

const fetchPost = async () => {
  loading.value = true
  try {
    const { data } = await getDiscussionById(route.params.id)
    postData.value = data || {}
  } catch {
    ElMessage.error('获取帖子详情失败')
  } finally {
    loading.value = false
  }
}

const fetchReplies = async () => {
  try {
    const { data } = await getComments({ postId: route.params.id })
    replies.value = data || []
  } catch {
    ElMessage.error('获取回复列表失败')
  }
}

const handleApprove = async () => {
  try {
    await ElMessageBox.confirm('确定通过该讨论?', '提示', { type: 'warning' })
    await approveDiscussion(route.params.id)
    ElMessage.success('审核通过')
    fetchPost()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleReject = async () => {
  try {
    await ElMessageBox.confirm('确定驳回该讨论?', '提示', { type: 'warning' })
    await rejectDiscussion(route.params.id)
    ElMessage.success('驳回成功')
    fetchPost()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定删除该讨论?', '提示', { type: 'warning' })
    await deleteDiscussion(route.params.id)
    ElMessage.success('删除成功')
    router.push('/courses/discussions')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleDeleteReply = async (reply) => {
  try {
    await ElMessageBox.confirm('确定删除该回复?', '提示', { type: 'warning' })
    await deleteComment(reply.id)
    ElMessage.success('删除成功')
    fetchReplies()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleBack = () => {
  router.push('/courses/discussions')
}

onMounted(() => {
  fetchPost()
  fetchReplies()
})
</script>

<style scoped>
.discussion-detail-page {
  padding: var(--space-5);
}

.post-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-md);
}

.reply-card {
  border-radius: var(--radius-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

.post-header {
  margin-bottom: var(--space-4);
}

.post-title {
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 var(--space-2) 0;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.meta-item {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.post-content {
  font-size: var(--text-base);
  color: var(--color-text-regular);
  line-height: 1.6;
  white-space: pre-wrap;
}

.reply-count {
  font-size: var(--text-sm);
  color: var(--color-text-secondary);
}

.empty-replies {
  padding: var(--space-4);
}

.reply-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.reply-item {
  padding: var(--space-3);
  background: var(--color-bg-page);
  border-radius: var(--radius-sm);
  transition: background-color 0.2s ease;
}

.reply-item:hover {
  background-color: var(--color-bg-page);
}

.reply-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.reply-author {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.reply-time {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  flex: 1;
}

.reply-content {
  font-size: var(--text-sm);
  color: var(--color-text-regular);
  line-height: 1.5;
}

@media (max-width: 768px) {
  .discussion-detail-page {
    padding: var(--space-3);
  }

  .post-card,
  .reply-card {
    margin-bottom: var(--space-3);
  }

  .header-actions {
    flex-wrap: wrap;
  }

  .post-meta {
    gap: var(--space-2);
  }
}
</style>