<!--
  讨论详情
  路由路径: /discussions/:id
  Phase 1
  Author: jackie
-->
<template>
  <div class="discussion-detail-page">
    <h1 class="sr-only">{{ postData.title ? $t('discussionDetail.titleWithName', { title: postData.title }) : $t('discussionDetail.title') }}</h1>
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('layout.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/discussions' }">{{ $t('route.DiscussionList') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('discussionDetail.title') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 帖子卡片 -->
    <el-card class="post-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('discussion.postDetail') }}</span>
          <div class="header-actions">
            <el-button v-if="postData.status === 'PENDING'" type="success" @click="handleApprove">{{ $t('course.statusApproved') }}</el-button>
            <el-button v-if="postData.status === 'PENDING'" type="danger" @click="handleReject">{{ $t('course.reject') }}</el-button>
            <el-button v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" type="danger" @click="handleDelete">{{ $t('app.delete') }}</el-button>
            <el-button @click="handleBack">{{ $t('app.back') }}</el-button>
          </div>
        </div>
      </template>

      <el-skeleton v-if="loading" :rows="6" animated />
      <template v-else>
        <div class="post-header">
          <h2 class="post-title">{{ postData.title }}</h2>
          <div class="post-meta">
            <span class="meta-item">{{ $t('discussion.author') }}：{{ postData.authorName || '-' }}</span>
            <span class="meta-item">{{ $t('course.title') }}：{{ postData.courseName || '-' }}</span>
            <span class="meta-item">{{ $t('discussion.publishedAt') }}：{{ formatDateTime(postData.createdAt) || '-' }}</span>
            <el-tag v-if="postData.status === 'PENDING'" type="warning" size="small">{{ $t('course.pendingReview') }}</el-tag>
            <el-tag v-else-if="postData.status === 'PUBLISHED'" type="success" size="small">{{ $t('course.published') }}</el-tag>
            <el-tag v-else-if="postData.status === 'DELETED'" type="info" size="small">{{ $t('course.deleted') }}</el-tag>
          </div>
        </div>
        <div class="post-content">{{ postData.content }}</div>
      </template>
    </el-card>

    <!-- 回复列表 -->
    <el-card class="reply-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('discussionDetail.replyList') }}</span>
          <span class="reply-count">{{ $t('discussionDetail.replyTotal', { count: replies.length }) }}</span>
        </div>
      </template>

      <div v-if="replies.length === 0" class="empty-replies">
        <el-empty :description="$t('discussionDetail.noReplies')" />
      </div>

      <div v-else class="reply-list">
        <div v-for="reply in replies" :key="reply.id" class="reply-item">
          <div class="reply-header">
            <span class="reply-author">{{ reply.authorName || '-' }}</span>
            <span class="reply-time">{{ formatDateTime(reply.createdAt) || '-' }}</span>
            <el-button v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" type="danger" link size="small" @click="handleDeleteReply(reply)">{{ $t('app.delete') }}</el-button>
          </div>
          <div class="reply-content">{{ reply.content }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDiscussionById, approveDiscussion, rejectDiscussion, deleteDiscussion, getComments, deleteComment } from '@/api/discussion'
import { formatDateTime } from '@/utils/format'
import { useUserStore } from '@/store/user'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
// P1-C 修复 (2026-08-04): userRole 未定义 → 管理员/教务删除帖子、回复按钮隐藏，违规内容无法清理
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const postData = ref({})
const replies = ref([])

const fetchPost = async () => {
  loading.value = true
  try {
    const { data } = await getDiscussionById(route.params.id)
    postData.value = data || {}
  } catch {
    ElMessage.error(t('discussionDetail.fetchDetailFailed'))
  } finally {
    loading.value = false
  }
}

const fetchReplies = async () => {
  try {
    const { data } = await getComments(route.params.id)
    // P1I-15: 后端返回直接数组（R.ok(list)），但做 safety check 兼容可能的分页格式
    replies.value = data?.items || data || []
  } catch {
    ElMessage.error(t('discussionDetail.fetchRepliesFailed'))
  }
}

const handleApprove = async () => {
  try {
    await ElMessageBox.confirm(t('discussionList.confirmApprove'), t('course.hintTitle'), { type: 'warning' })
    await approveDiscussion(route.params.id)
    ElMessage.success(t('discussionList.approveSuccess'))
    fetchPost()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('discussionList.operationFailed'))
    }
  }
}

const handleReject = async () => {
  let reason = ''
  try {
    await ElMessageBox.prompt(t('discussionDetail.rejectPromptMsg'), t('discussionList.rejectConfirmTitle'), {
      confirmButtonText: t('discussionList.confirmRejectBtn'),
      cancelButtonText: t('common.cancel'),
      inputType: 'textarea',
      inputPlaceholder: t('discussionList.rejectReasonPlaceholder'),
      inputValidator: (val) => !!val.trim() || t('discussionList.rejectReasonRequired')
    }).then(({ value }) => { reason = value })
    await rejectDiscussion(route.params.id, reason)
    ElMessage.success(t('discussionList.rejectSuccess'))
    fetchPost()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('discussionList.operationFailed'))
    }
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(t('discussionList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deleteDiscussion(route.params.id)
    ElMessage.success(t('discussionList.deleteSuccess'))
    router.push('/discussions')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('discussionList.deleteFailed'))
    }
  }
}

const handleDeleteReply = async (reply) => {
  try {
    await ElMessageBox.confirm(t('discussionDetail.confirmDeleteReply'), t('course.hintTitle'), { type: 'warning' })
    await deleteComment(reply.id)
    ElMessage.success(t('discussionList.deleteSuccess'))
    fetchReplies()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('discussionList.deleteFailed'))
    }
  }
}

const handleBack = () => {
  router.push('/discussions')
}

onMounted(() => {
  fetchPost()
  fetchReplies()
})
</script>

<style scoped>
.discussion-detail-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.post-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.reply-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
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
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
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
  color: var(--el-text-color-secondary);
}

.post-content {
  font-size: var(--text-base);
  color: var(--el-text-color-regular);
  line-height: 1.6;
  white-space: pre-wrap;
}

.reply-count {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
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
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
  transition: background-color var(--duration-fast) var(--ease-out);
}

.reply-item:hover {
  background-color: var(--el-fill-color);
}

.reply-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.reply-author {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.reply-time {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  flex: 1;
}

.reply-content {
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
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
