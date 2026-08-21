<!--
  讨论列表
  路由路径: /discussions
  Phase 1
  Author: jackie
-->
<template>
  <div class="discussion-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('layout.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('route.DiscussionList') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('course.keyword')">
          <el-input v-model="searchForm.keyword" :placeholder="$t('discussionList.keywordPlaceholder')" clearable class="filter-input-w160" :aria-label="$t('course.keyword')" />
        </el-form-item>
        <el-form-item :label="$t('course.title')">
          <el-select v-model="searchForm.courseId" :placeholder="$t('discussionList.selectCourse')" clearable class="filter-input-w180" :aria-label="$t('course.title')">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('app.status')">
          <el-select v-model="searchForm.status" :placeholder="$t('discussionList.selectStatus')" clearable class="filter-input-w120" :aria-label="$t('app.status')">
            <el-option :label="$t('course.pendingReview')" value="PENDING" />
            <el-option :label="$t('course.published')" value="PUBLISHED" />
            <el-option :label="$t('discussion.rejected')" value="REJECTED" />
            <el-option :label="$t('course.deleted')" value="DELETED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('discussionList.title') }}</span>
        </div>
      </template>
      <el-result v-if="error" icon="error" :title="$t('discussionList.loadFailed')" :sub-title="$t('discussionList.loadFailedSubtitle')">
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>
      <template v-else>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('discussionList.empty')" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="authorName" :label="$t('discussion.author')" width="120" />
        <el-table-column prop="courseName" :label="$t('course.title')" min-width="150" show-overflow-tooltip />
        <el-table-column prop="replyCount" :label="$t('discussion.replyCount')" width="100" align="center">
          <template #default="{ row }">
            {{ row.replyCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('discussion.publishedAt')" width="170" :formatter="$formatDateTime" />
        <el-table-column prop="status" :label="$t('app.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.statusStr === 'PENDING'" type="warning" size="small">{{ $t('course.pendingReview') }}</el-tag>
            <el-tag v-else-if="row.statusStr === 'APPROVED' || row.statusStr === 'PUBLISHED'" type="success" size="small">{{ $t('course.published') }}</el-tag>
            <el-tag v-else-if="row.statusStr === 'REJECTED'" type="danger" size="small">{{ $t('discussion.rejected') }}</el-tag>
            <el-tag v-else-if="row.statusStr === 'DELETED'" type="info" size="small">{{ $t('course.deleted') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ row.statusStr || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">{{ $t('course.view') }}</el-button>
            <el-button v-if="(row.status === 'PENDING' || row.statusStr === 'PENDING')" type="success" link size="small" @click="handleApprove(row)">{{ $t('course.statusApproved') }}</el-button>
            <el-button v-if="(row.status === 'PENDING' || row.statusStr === 'PENDING')" type="danger" link size="small" @click="handleReject(row)">{{ $t('course.reject') }}</el-button>
            <el-button v-if="row.status === 'PUBLISHED' || row.statusStr === 'PUBLISHED'" type="warning" link size="small" @click="handleTogglePin(row)">{{ row.isPinned ? $t('discussionList.unpin') : $t('discussionList.pin') }}</el-button>
            <el-button v-if="row.status === 'PUBLISHED' || row.statusStr === 'PUBLISHED'" type="success" link size="small" @click="handleToggleEssence(row)">{{ row.isEssence ? $t('discussionList.unessence') : $t('discussionList.essence') }}</el-button>
            <el-button v-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'" type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="!loading && tableData.length > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
        <div class="page-size-wrap">
          <label for="disc-list-page-size" class="sr-only">{{ $t('course.perPage') }}</label>
          <el-select id="disc-list-page-size" :model-value="size" class="page-size-select" @change="v => { size = v; handleSizeChange() }" :aria-label="$t('course.perPage')">
            <el-option v-for="s in [10, 20, 50, 100]" :key="s" :label="$t('course.perPageOption', { count: s })" :value="s" />
          </el-select>
        </div>
      </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getDiscussions, approveDiscussion, rejectDiscussion, deleteDiscussion, updatePostPin, updatePostEssence } from '@/api/discussion'
import { getCourses } from '@/api/course'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
// P1-C 修复 (2026-08-04): userRole 未定义 → 管理员/教务删除讨论按钮隐藏，
// 违规讨论无法清理
const userRole = computed(() => userStore.role)

const loading = ref(false)
const error = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

const searchForm = reactive({
  keyword: '',
  courseId: '',
  status: ''
})

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, searchForm, ['keyword', 'courseId', 'status'])

const fetchCourseOptions = async () => {
  try {
    const params = { page: 0, size: 100 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error(t('discussionList.fetchCourseOptionsFailed'))
  }
}

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    // P1I-16: 搜索提交 status 为字符串（PENDING/PUBLISHED/REJECTED/DELETED），后端期望一致
    // 后端返回中包含 status（字符串）和 statusStr（中文/展示用标签）两个字段
    const params = {
      page: page.value - 1,
      size: size.value,
      keyword: searchForm.keyword || undefined,
      courseId: searchForm.courseId || undefined,
      status: searchForm.status || undefined
    }
    const { data } = await getDiscussions(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    error.value = true
    ElMessage.error(t('discussionList.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.courseId = ''
  searchForm.status = ''
  page.value = 1
  fetchData()
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleView = (row) => {
  router.push(`/discussions/${row.id}`)
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(t('discussionList.confirmApprove'), t('course.hintTitle'), { type: 'warning' })
    await approveDiscussion(row.id)
    ElMessage.success(t('discussionList.approveSuccess'))
    fetchData()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) {
      ElMessage.error(t('discussionList.operationFailed'))
    }
  }
}

const handleReject = async (row) => {
  let reason = ''
  try {
    await ElMessageBox.prompt(t('discussionList.rejectPromptMsg'), t('discussionList.rejectConfirmTitle'), {
      confirmButtonText: t('discussionList.confirmRejectBtn'),
      cancelButtonText: t('common.cancel'),
      inputType: 'textarea',
      inputPlaceholder: t('discussionList.rejectReasonPlaceholder'),
      inputValidator: (val) => !!val.trim() || t('discussionList.rejectReasonRequired')
    }).then(({ value }) => { reason = value })
    await rejectDiscussion(row.id, reason)
    ElMessage.success(t('discussionList.rejectSuccess'))
    fetchData()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) {
      ElMessage.error(t('discussionList.operationFailed'))
    }
  }
}

const handleTogglePin = async (row) => {
  const newPinned = !row.isPinned
  try {
    await ElMessageBox.confirm(t(newPinned ? 'discussionList.confirmPin' : 'discussionList.confirmUnpin'), t('course.hintTitle'), { type: 'warning' })
    await updatePostPin(row.id, newPinned)
    ElMessage.success(t(newPinned ? 'discussionList.pinSuccess' : 'discussionList.unpinSuccess'))
    fetchData()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) {
      ElMessage.error(t('discussionList.operationFailed'))
    }
  }
}

const handleToggleEssence = async (row) => {
  const newEssence = !row.isEssence
  try {
    await ElMessageBox.confirm(t(newEssence ? 'discussionList.confirmEssence' : 'discussionList.confirmUnessence'), t('course.hintTitle'), { type: 'warning' })
    await updatePostEssence(row.id, newEssence)
    ElMessage.success(t(newEssence ? 'discussionList.essenceSuccess' : 'discussionList.unessenceSuccess'))
    fetchData()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) {
      ElMessage.error(t('discussionList.operationFailed'))
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('discussionList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deleteDiscussion(row.id)
    ElMessage.success(t('discussionList.deleteSuccess'))
    fetchData()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) {
      ElMessage.error(t('discussionList.deleteFailed'))
    }
  }
}

onMounted(() => {
  fetchCourseOptions()
  fetchData()
})
</script>

<style scoped>
.discussion-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.filter-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
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

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.filter-input-w160 {
  width: 160px;
}

.filter-input-w180 {
  width: 180px;
}

.search-input,
.filter-input {
  width: 160px;
  border-radius: var(--radius-md);
}

.search-select,
.filter-select {
  width: 160px;
}

.filter-input-w120 {
  width: 120px;
}

@media (max-width: 768px) {
  .discussion-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .filter-input-w160,
  .filter-input-w180,
  .filter-input-w120 {
    width: 100%;
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>
