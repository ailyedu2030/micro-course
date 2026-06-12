<!--
  讨论区
  路由路径: /student/discussion/:courseId
  Phase 3
  Author: jackie
-->
<template>
  <div class="discussion-view">
    <!-- PC 端布局 -->
    <template v-if="!isMobile">
      <!-- 顶栏 -->
      <el-card class="toolbar-card" shadow="never">
        <div class="toolbar">
          <div class="left-info">
            <h3 class="page-title">章节讨论</h3>
          </div>
          <el-button type="primary" @click="openPostDialog">发布帖子</el-button>
        </div>
      </el-card>

      <!-- 帖子列表 -->
      <el-card class="table-card" shadow="never">
        <el-table v-loading="loading" :data="tableData" stripe border>
          <el-table-column prop="title" label="标题" min-width="180">
            <template #default="{ row }">
              <el-link type="primary" @click="viewDetail(row)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="authorName" label="作者" width="120" align="center">
            <template #default="{ row }">
              {{ row.isAnonymous ? '匿名用户' : row.authorName }}
            </template>
          </el-table-column>
          <el-table-column prop="replyCount" label="回复数" width="100" align="center" />
          <el-table-column prop="likeCount" label="点赞" width="80" align="center" />
          <el-table-column prop="createdAt" label="发布时间" width="170" />
        </el-table>
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="totalElements"
            :page-sizes="[10, 20, 50]"
            layout="total,sizes,prev,pager,next"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>
    </template>

    <!-- H5 端布局 -->
    <template v-else>
      <!-- 紧凑顶栏 -->
      <div class="h5-toolbar">
        <h3 class="page-title">章节讨论</h3>
        <el-button type="primary" size="small" @click="openPostDialog">发布帖子</el-button>
      </div>

      <!-- 卡片列表 -->
      <div class="post-list">
        <div v-if="loading" class="skeleton-wrap">
          <el-skeleton animated :rows="3" />
        </div>
        <template v-else>
          <el-card
            v-for="row in tableData"
            :key="row.id"
            class="post-card"
            shadow="never"
            @click="viewDetail(row)"
          >
            <div class="post-card-title">{{ row.title }}</div>
            <div class="post-card-meta">
              <span class="author">{{ row.isAnonymous ? '匿名用户' : row.authorName }}</span>
              <span class="reply-count">{{ row.replyCount }} 回复</span>
            </div>
          </el-card>
          <el-empty v-if="tableData.length === 0" description="暂无帖子" />
        </template>
      </div>

      <!-- 移动端分页 -->
      <div class="pagination-wrap h5-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50]"
          layout="total,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </template>

    <!-- 发帖弹窗 -->
    <el-dialog v-model="postDialogVisible" title="发布帖子" width="500px" @close="resetPostForm">
      <el-form :model="postForm" :rules="postRules" ref="postFormRef" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="postForm.title" placeholder="请输入帖子标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="postForm.content"
            type="textarea"
            :rows="5"
            placeholder="请输入帖子内容"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="postForm.isAnonymous">匿名发布</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="postDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitPost" :loading="submitting">发布</el-button>
      </template>
    </el-dialog>

    <!-- 帖子详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="isMobile ? '' : '帖子详情'"
      :width="isMobile ? '90vw' : '600px'"
      @close="resetDetail"
    >
      <div v-if="currentPost" class="post-detail">
        <div class="post-header">
          <h3 class="post-title">{{ currentPost.title }}</h3>
          <div class="post-meta">
            <span>{{ currentPost.isAnonymous ? '匿名用户' : currentPost.authorName }}</span>
            <span>{{ currentPost.createdAt }}</span>
          </div>
        </div>
        <div class="post-content">{{ currentPost.content }}</div>

        <el-divider>评论 ({{ comments.length }})</el-divider>

        <!-- 评论树 -->
        <div class="comments-section">
          <CommentNode
            v-for="comment in comments"
            :key="comment.id"
            :comment="comment"
            :depth="0"
            @reply="handleReply"
            @like="handleLikeComment"
          />
          <el-empty v-if="comments.length === 0" description="暂无评论，快来抢沙发吧" />
        </div>

        <!-- 回复输入框 -->
        <div class="reply-input-area">
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="3"
            placeholder="写下你的回复... (Ctrl+Enter 发送)"
            @keyup.enter.ctrl="handleSubmitReply"
          />
          <div class="reply-input-footer">
            <el-checkbox v-model="replyAnonymous">匿名回复</el-checkbox>
            <el-button type="primary" @click="handleSubmitReply" :disabled="!replyContent.trim()" :loading="replySubmitting">
              发送回复
            </el-button>
          </div>
        </div>
      </div>
      <template #footer v-if="currentPost && !isMobile">
        <div class="post-actions">
          <el-button type="danger" link size="small" @click="handleDeletePost">删除帖子</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPosts, createPost, getPostById, getComments, createComment, likeComment, deletePost } from '@/api/discussion'
import CommentNode from '@/components/CommentNode.vue'

const route = useRoute()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

const handleResize = () => {
  checkMobile()
}

// 发帖
const postDialogVisible = ref(false)
const postFormRef = ref(null)
const postForm = ref({ title: '', content: '', isAnonymous: false })
const submitting = ref(false)
const postRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

// 帖子详情
const detailDialogVisible = ref(false)
const currentPost = ref(null)
const comments = ref([])
const replyContent = ref('')
const replyAnonymous = ref(false)
const replySubmitting = ref(false)

const chapterId = computed(() => route.query.chapterId)

const fetchData = async () => {
  if (!chapterId.value) {
    ElMessage.warning('缺少章节ID参数')
    return
  }
  loading.value = true
  try {
    const params = { chapterId: chapterId.value, page: page.value - 1, size: size.value }
    const res = await getPosts(params)
    tableData.value = res.data?.items || []
    totalElements.value = res.data?.totalElements || 0
  } catch {
    ElMessage.error('获取帖子列表失败')
  } finally {
    loading.value = false
  }
}

const openPostDialog = () => {
  postDialogVisible.value = true
}

const resetPostForm = () => {
  postForm.value = { title: '', content: '', isAnonymous: false }
  postFormRef.value?.resetFields()
}

const handleSubmitPost = async () => {
  try {
    await postFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await createPost({ ...postForm.value, chapterId: chapterId.value })
    ElMessage.success('发布成功')
    postDialogVisible.value = false
    resetPostForm()
    page.value = 1
    fetchData()
  } catch {
    ElMessage.error('发布失败')
  } finally {
    submitting.value = false
  }
}

const viewDetail = async (row) => {
  try {
    const res = await getPostById(row.id)
    currentPost.value = res.data
    //加载评论
    const commentRes = await getComments(row.id)
    comments.value = commentRes.data || []
    detailDialogVisible.value = true
  } catch {
    ElMessage.error('加载帖子详情失败')
  }
}

const resetDetail = () => {
  currentPost.value = null
  comments.value = []
  replyContent.value = ''
}

const handleReply = async ({ parentId, content }) => {
  try {
    await createComment({ postId: currentPost.value.id, parentId, content, isAnonymous: replyAnonymous.value })
    ElMessage.success('回复成功')
    // 刷新评论
    const commentRes = await getComments(currentPost.value.id)
    comments.value = commentRes.data || []
  } catch {
    ElMessage.error('回复失败')
  }
}

const handleSubmitReply = async () => {
  if (!replyContent.value.trim()) return
  replySubmitting.value = true
  try {
    await createComment({ postId: currentPost.value.id, content: replyContent.value.trim(), isAnonymous: replyAnonymous.value })
    ElMessage.success('回复成功')
    replyContent.value = ''
    // 刷新评论
    const commentRes = await getComments(currentPost.value.id)
    comments.value = commentRes.data || []
  } catch {
    ElMessage.error('回复失败')
  } finally {
    replySubmitting.value = false
  }
}

const handleLikeComment = async (commentId) => {
  try {
    await likeComment(commentId)
    ElMessage.success('点赞成功')
    // 刷新评论
    const commentRes = await getComments(currentPost.value.id)
    comments.value = commentRes.data || []
  } catch {
    ElMessage.error('点赞失败')
  }
}

const handleDeletePost = async () => {
  try {
    await ElMessageBox.confirm('确定要删除此帖子吗？', '提示', { type: 'warning' })
    await deletePost(currentPost.value.id)
    ElMessage.success('删除成功')
    detailDialogVisible.value = false
    page.value = 1
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', handleResize)
  fetchData()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.discussion-view {
  padding: var(--space-5);
}

/* ========== PC Layout ========== */
.toolbar-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-base) ease;
}
.toolbar-card:hover {
  box-shadow: var(--shadow-lg);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.table-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}

/* ========== Post Detail ========== */
.post-detail {
  max-height: 60vh;
  overflow-y: auto;
}

.post-header {
  margin-bottom: var(--space-4);
}

.post-title {
  margin: 0 0 var(--space-2) 0;
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.post-meta {
  display: flex;
  gap: var(--space-4);
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.post-content {
  font-size: var(--text-base);
  color: var(--el-text-color-regular);
  line-height: 1.8;
  padding: var(--space-4);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
  white-space: pre-wrap;
  margin-bottom: var(--space-4);
}

.comments-section {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: var(--space-4);
}

.reply-input-area {
  border-top: 1px solid var(--el-border-color);
  padding-top: var(--space-4);
}

.reply-input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-2);
}

.post-actions {
  display: flex;
  justify-content: flex-start;
}

/* ========== H5 Layout ========== */
.h5-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--el-color-white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.h5-toolbar .page-title {
  font-size: var(--text-md);
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.skeleton-wrap {
  padding: var(--space-4);
  background: var(--el-color-white);
  border-radius: var(--radius-lg);
}

.post-card {
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: box-shadow var(--duration-base) ease;
}
.post-card:hover {
  box-shadow: var(--shadow-lg);
}

.post-card-title {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-card-meta {
  display: flex;
  justify-content: space-between;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.h5-pagination {
  justify-content: center;
}

/* ========== Global Elements ========== */
:deep(.el-button) {
  cursor: pointer;
}

:deep(.el-card) {
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-base) ease;
}
:deep(.el-card:hover) {
  box-shadow: var(--shadow-lg);
}

:deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
  max-width: 600px;
}
</style>