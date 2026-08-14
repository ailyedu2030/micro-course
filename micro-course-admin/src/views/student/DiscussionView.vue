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
            <h1 class="page-title">{{ $t('discussion.chapterDiscussion') }}</h1>
            <div v-if="!chapterId" class="chapter-selector">
              <el-select v-model="selectedCourseId" :placeholder="$t('course.selectCourse')" clearable size="small" style="width:200px;margin-right:8px" :aria-label="$t('course.selectCourse')" @change="handleCourseChange">
                <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
              </el-select>
              <el-select v-model="routeQuery.chapterId" :placeholder="$t('course.selectChapter')" clearable size="small" style="width:200px" :disabled="!selectedCourseId" :aria-label="$t('course.selectChapter')" @change="handleChapterSelect">
                <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
              </el-select>
            </div>
          </div>
          <el-button type="primary" :disabled="!chapterId" @click="openPostDialog">{{ $t('discussion.publishPost') }}</el-button>
        </div>
      </el-card>

      <!-- 帖子列表 -->
      <el-card class="table-card" shadow="never">
        <el-table v-loading="loading" :aria-busy="loading" :data="tableData" class="data-table" stripe border>
          <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="180">
            <template #default="{ row }">
              <el-link type="primary" @click="viewDetail(row)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="authorName" :label="$t('discussion.author')" width="120" align="center">
            <template #default="{ row }">
              {{ row.isAnonymous ? $t('course.anonymousUser') : row.authorName }}
            </template>
          </el-table-column>
          <el-table-column prop="replyCount" :label="$t('discussion.replyCount')" width="100" align="center" />
          <el-table-column prop="likeCount" :label="$t('discussion.likeCount')" width="80" align="center" />
          <el-table-column prop="createdAt" :label="$t('discussion.publishedAt')" width="170" :formatter="$formatDateTime" />
        </el-table>
        <!-- P0-6: PC 端空状态 -->
        <el-empty v-if="!loading && tableData.length === 0" :description="$t('discussion.noPosts')" />
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="totalElements"
            :page-sizes="[10, 20, 50]"
            layout="total,prev,pager,next"
            @size-change="handleSizeChange"
            @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
          <div class="page-size-wrap">
            <label for="disc-pc-page-size" class="sr-only">{{ $t('course.perPage') }}</label>
            <el-select id="disc-pc-page-size" :model-value="size" class="page-size-select" @change="v => { size = v; handleSizeChange() }" :aria-label="$t('course.perPage')">
              <el-option v-for="s in [10, 20, 50]" :key="s" :label="$t('course.perPageOption', { count: s })" :value="s" />
            </el-select>
          </div>
        </div>
      </el-card>
    </template>

    <!-- H5 端布局 -->
    <template v-else>
      <!-- 紧凑顶栏 -->
      <div class="h5-toolbar">
        <h1 class="page-title">{{ $t('discussion.chapterDiscussion') }}</h1>
        <el-button type="primary" size="small" @click="openPostDialog">{{ $t('discussion.publishPost') }}</el-button>
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
            role="button"
            tabindex="0"
            :aria-label="$t('discussion.postCardAria', { title: row.title, count: row.replyCount })"
            @click="viewDetail(row)"
            @keydown.enter="viewDetail(row)"
            @keydown.space.prevent="viewDetail(row)"
          >
            <div class="post-card-title">{{ row.title }}</div>
            <div class="post-card-meta">
              <span class="author">{{ row.isAnonymous ? $t('course.anonymousUser') : row.authorName }}</span>
              <span class="reply-count">{{ $t('discussion.replyCountSuffix', { count: row.replyCount }) }}</span>
            </div>
          </el-card>
          <el-empty v-if="tableData.length === 0" :description="$t('discussion.noPosts')" />
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
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </template>

    <!-- 发帖弹窗 -->
    <el-dialog v-model="postDialogVisible" :title="$t('discussion.publishPost')" width="500px" @close="resetPostForm" :close-on-press-escape="true">
      <el-form :model="postForm" :rules="postRules" ref="postFormRef" label-width="80px">
        <el-form-item :label="$t('course.tableTitle')" prop="title">
          <el-input v-model="postForm.title" :placeholder="$t('discussion.inputTitlePlaceholder')" maxlength="200" show-word-limit :aria-label="$t('discussion.postTitle')" />
        </el-form-item>
        <el-form-item :label="$t('discussion.content')" prop="content">
          <el-input
            v-model="postForm.content"
            type="textarea"
            :rows="5"
            :placeholder="$t('discussion.inputContentPlaceholder')"
            maxlength="5000"
            show-word-limit
            :aria-label="$t('discussion.postContent')"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="postForm.isAnonymous">{{ $t('discussion.anonymousPublish') }}</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="postDialogVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmitPost" :loading="submitting">{{ $t('discussion.publish') }}</el-button>
      </template>
    </el-dialog>

    <!-- 帖子详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="isMobile ? '' : $t('discussion.postDetail')"
      :width="isMobile ? '90vw' : '600px'"
      @close="resetDetail"
     :close-on-press-escape="true"
>
      <div v-if="currentPost" class="post-detail">
        <div class="post-header">
          <h2 class="post-title">{{ currentPost.title }}</h2>
          <div class="post-meta">
            <span>{{ currentPost.isAnonymous ? $t('course.anonymousUser') : currentPost.authorName }}</span>
            <span>{{ formatDateTime(currentPost.createdAt) }}</span>
            <el-tag v-if="currentPost.status === 0" type="warning" size="small">{{ $t('course.pendingReview') }}</el-tag>
            <el-tag v-else-if="currentPost.status === 2" type="info" size="small">{{ $t('discussion.rejected') }}</el-tag>
          </div>
        </div>
        <div class="post-content">{{ currentPost.content }}</div>

        <el-divider>{{ $t('discussion.commentsCount', { count: comments.length }) }}</el-divider>

        <!-- 评论树 -->
        <div class="comments-section">
          <CommentNode
            v-for="comment in comments"
            :key="comment.id"
            :comment="comment"
            :depth="0"
            :replying-id="replyingCommentId"
            @reply="handleReply"
            @like="handleLikeComment"
          />
          <el-empty v-if="comments.length === 0" :description="$t('discussion.noComments')" />
        </div>

        <!-- 回复输入框 -->
        <el-alert
          v-if="currentPost.status === 0 || currentPost.status === 2"
          :title="currentPost.status === 0 ? $t('discussion.commentPendingHint') : $t('discussion.postRejectedHint')"
          type="warning"
          :closable="false"
          show-icon
          class="reply-pending-hint"
        />
        <div v-if="currentPost.status === 1 || currentPost.status == null" class="reply-input-area">
          <el-input
            v-model="replyContent"
            type="textarea"
            :rows="3"
            :placeholder="$t('discussion.replyPlaceholder')"
            :aria-label="$t('discussion.replyContent')"
            @keyup.enter.ctrl="handleSubmitReply"
          />
          <div class="reply-input-footer">
            <el-checkbox v-model="replyAnonymous">{{ $t('discussion.anonymousReply') }}</el-checkbox>
            <el-button type="primary" @click="handleSubmitReply" :disabled="!replyContent.trim()" :loading="replySubmitting">
              {{ $t('discussion.sendReply') }}
            </el-button>
          </div>
        </div>
      </div>
      <template #footer v-if="currentPost">
        <div class="post-actions">
          <el-button v-if="currentPost?.isOwner || userStore.userInfo?.role === 'ADMIN'" type="danger" link size="small" @click="handleDeletePost">{{ $t('discussion.deletePost') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, onUnmounted, watch } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPosts, createPost, getPostById, getComments, createComment, likeComment, deletePost } from '@/api/discussion'
import { useUserStore } from '@/store/user'
import { getCourses } from '@/api/course'
import { getChapters, getChapterById } from '@/api/chapter'
import CommentNode from '@/components/CommentNode.vue'
import { formatDateTime } from '@/utils/format'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const userStore = useUserStore()

const route = useRoute()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const isMobile = ref(window.innerWidth <= 768)

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, null, [])

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
  title: [{ required: true, message: t('discussion.inputTitleRequired'), trigger: 'blur' }],
  content: [{ required: true, message: t('discussion.inputContentRequired'), trigger: 'blur' }]
}

// 帖子详情
const detailDialogVisible = ref(false)
const currentPost = ref(null)
const comments = ref([])
const replyContent = ref('')
const replyAnonymous = ref(false)
const replySubmitting = ref(false)
const replyingCommentId = ref(null)

const chapterId = computed(() => route.query.chapterId)
const router = useRouter()
const routeQuery = reactive({ chapterId: '' })
const selectedCourseId = ref(null)
const currentCourseId = ref(null)  // P0-1: courseId derived from chapter lookup
const courseOptions = ref([])
const chapterOptions = ref([])

// 无 chapterId 时加载课程列表供选择
async function fetchCourses() {
  try {
    const params = { page: 0, size: 999 }
    if (userStore.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data?.items || []
  } catch { ElMessage.warning(t('discussion.courseListLoadFailed')) }
}
async function handleCourseChange(cid) {
  chapterOptions.value = []
  routeQuery.chapterId = ''
  if (!cid) return
  try {
    const { data } = await getChapters({ courseId: cid })
    chapterOptions.value = data?.items || []
  } catch { ElMessage.warning(t('discussion.chapterListLoadFailed')) }
}
function handleChapterSelect(chId) {
  if (chId) router.replace({ query: { ...route.query, chapterId: chId } })
}
// P1-修复: 添加 { immediate: false } 防止与 onMounted 重复触发
watch(() => route.query.chapterId, async (val) => {
  if (val) {
    // P0-1: 从 chapterId 查询 courseId
    try {
      const { data } = await getChapterById(val)
      if (data?.courseId) currentCourseId.value = data.courseId
    } catch { ElMessage.warning(t('discussion.chapterInfoLoadFailed')) }
    fetchData()
  }
}, { immediate: false })
onMounted(() => {
  if (!chapterId.value && !route.query.chapterId) fetchCourses()
})

const fetchData = async () => {
  if (!chapterId.value) {
    ElMessage.warning(t('discussion.missingChapterId'))
    return
  }
  loading.value = true
  try {
    const params = { chapterId: chapterId.value, page: page.value - 1, size: size.value }
    const res = await getPosts(params)
    tableData.value = res.data?.items || []
    totalElements.value = res.data?.totalElements || 0
  } catch (error) {
    const status = error?.response?.status
    if (status === 403) {
      ElMessage.warning(t('discussion.noAccess'))
    } else {
      const msg = error?.response?.data?.message || t('discussion.fetchPostsFailed')
      ElMessage.error(msg)
    }
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
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitting.value) return
  if (!postFormRef.value) return
  submitting.value = true
  try {
    const valid = await postFormRef.value.validate()
    if (!valid) { submitting.value = false; return }
  } catch { submitting.value = false; return }
  try {
    // P0-1: 确保 courseId 可用——若未预加载则实时查询章节获取
    let courseId = currentCourseId.value ? Number(currentCourseId.value) : undefined
    if (!courseId && chapterId.value) {
      try {
        const { data } = await getChapterById(chapterId.value)
        if (data?.courseId) {
          courseId = Number(data.courseId)
          currentCourseId.value = courseId
        }
      } catch (e) {
        console.warn('[DiscussionView] fetchCourses 获取课程列表失败', e)
        ElMessage.warning(t('discussion.courseListLoadFailed'))
      }
    }
    await createPost({
      title: postForm.value.title,
      content: postForm.value.content,
      isAnonymous: postForm.value.isAnonymous,
      chapterId: Number(chapterId.value) || null
    })
    ElMessage.success(t('discussion.publishSuccess'))
    postDialogVisible.value = false
    resetPostForm()
    page.value = 1
    fetchData()
  } catch (error) {
    const msg = error?.response?.data?.message || t('discussion.publishFailed')
    ElMessage.error(msg)
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
  } catch (error) {
    const msg = error?.response?.data?.message || t('discussion.fetchDetailFailed')
    ElMessage.error(msg)
  }
}

const resetDetail = () => {
  currentPost.value = null
  comments.value = []
  replyContent.value = ''
  replyAnonymous.value = false
}

const handleReply = async ({ parentId, content }) => {
  replyingCommentId.value = parentId
  try {
    await createComment({ postId: currentPost.value.id, parentId, content, isAnonymous: replyAnonymous.value })
    ElMessage.success(t('course.replySuccess'))
    // 刷新评论
    const commentRes = await getComments(currentPost.value.id)
    comments.value = commentRes.data || []
  } catch (error) {
    const msg = error?.response?.data?.message || t('discussion.replyFailed')
    ElMessage.error(msg)
  } finally {
    replyingCommentId.value = null
  }
}

const handleSubmitReply = async () => {
  if (!replyContent.value.trim()) return
  replySubmitting.value = true
  try {
    await createComment({ postId: currentPost.value.id, content: replyContent.value.trim(), isAnonymous: replyAnonymous.value })
    ElMessage.success(t('course.replySuccess'))
    replyContent.value = ''
    // 刷新评论
    const commentRes = await getComments(currentPost.value.id)
    comments.value = commentRes.data || []
  } catch (error) {
    const msg = error?.response?.data?.message || t('discussion.replyFailed')
    ElMessage.error(msg)
  } finally {
    replySubmitting.value = false
  }
}

const handleLikeComment = async (commentId) => {
  try {
    await likeComment(commentId)
    ElMessage.success(t('discussion.likeSuccess'))
    // 刷新评论
    const commentRes = await getComments(currentPost.value.id)
    comments.value = commentRes.data || []
  } catch (error) {
    const msg = error?.response?.data?.message || t('discussion.likeFailed')
    ElMessage.error(msg)
  }
}

const handleDeletePost = async () => {
  try {
    await ElMessageBox.confirm(t('discussion.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deletePost(currentPost.value.id)
    ElMessage.success(t('course.deleteSuccess'))
    detailDialogVisible.value = false
    page.value = 1
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      const msg = error?.response?.data?.message || t('course.deleteFailed')
      ElMessage.error(msg)
    }
  }
}

let fetchTimer = null
const handleSizeChange = () => {
  page.value = 1
  clearTimeout(fetchTimer)
  fetchTimer = setTimeout(fetchData, 200)
}

const handlePageChange = () => {
  fetchData()
}

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', handleResize)
  if (chapterId.value || route.query.chapterId) {
    // P0-1: 页面加载时从 chapterId 查询 courseId
    const chId = chapterId.value || route.query.chapterId
    try {
      const { data } = await getChapterById(chId)
      if (data?.courseId) currentCourseId.value = data.courseId
    } catch (e) {
      console.warn('[DiscussionView] handleCourseChange 获取章节列表失败', e)
      ElMessage.warning(t('discussion.chapterListLoadFailed'))
    }
    fetchData()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.discussion-view {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
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
