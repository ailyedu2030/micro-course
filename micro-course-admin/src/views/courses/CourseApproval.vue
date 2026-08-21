<!--
  课程审核
  管理员/教务处 审核课程提交
  Route: /courses/review
-->
<template>
  <div class="approval-page">
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('courseApproval.title') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 状态 Tab -->
    <el-card shadow="never" class="filter-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane :label="$t('course.pendingReview')" name="pending" />
        <el-tab-pane :label="$t('course.approved')" name="approved" />
        <el-tab-pane :label="$t('courseApproval.rejected')" name="rejected" />
      </el-tabs>
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('course.keyword')">
          <el-input v-model="searchForm.keyword" :placeholder="$t('courseApproval.keywordPlaceholder')" clearable class="w160" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div v-if="activeTab === 'pending' && tableData.length > 0" class="batch-toolbar">
        <el-button type="success" :disabled="!selectedRows.length" @click="handleBatchApprove">
          {{ $t('courseApproval.batchApproveBtn', { count: selectedRows.length }) }}
        </el-button>
        <el-button type="danger" :disabled="!selectedRows.length" @click="handleBatchReject">
          {{ $t('courseApproval.batchReject') }}
        </el-button>
      </div>
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" :description="emptyDescription" :image-size="120" />
      <el-table v-else ref="tableRef" :data="tableData" stripe border class="data-table" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="title" :label="$t('course.courseName')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="teacherName" :label="$t('courseApproval.submitTeacher')" width="120" />
        <el-table-column prop="categoryName" :label="$t('course.category')" width="120" />
        <el-table-column :label="$t('app.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="warning">{{ $t('course.pendingReview') }}</el-tag>
            <el-tag v-else-if="row.status === 2" type="success">{{ $t('course.approved') }}</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger">{{ $t('courseApproval.rejected') }}</el-tag>
            <el-tag v-else-if="row.status === 4" type="primary">{{ $t('course.published') }}</el-tag>
            <el-tag v-else type="info">{{ $t('course.draft') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('courseApproval.submitTime')" width="170" :formatter="$formatDateTime" />
        <el-table-column :label="$t('app.operation')" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">{{ $t('course.view') }}</el-button>
            <el-button v-if="row.status === 1 && (userStore.role === 'ADMIN' || userStore.role === 'ACADEMIC')" type="success" link size="small" @click="handleApprove(row)">
              <el-icon><Select /></el-icon>{{ $t('courseApproval.pass') }}
            </el-button>
            <el-button v-if="row.status === 1 && (userStore.role === 'ADMIN' || userStore.role === 'ACADEMIC')" type="danger" link size="small" @click="handleReject(row)">
              <el-icon><Close /></el-icon>{{ $t('course.reject') }}
            </el-button>
            <!-- P0 修复：发布按钮仅 ADMIN 可见，后端 @PreAuthorize("hasRole('ADMIN')") 拒绝 ACADEMIC -->
            <el-button v-if="[2, 5].includes(row.status) && userStore.role === 'ADMIN'" type="primary" link size="small" @click="handlePublish(row)">
              {{ row.status === 5 ? $t('course.republish') : $t('course.publish') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50]"
          layout="total,sizes,prev,pager,next"
          @size-change="fetchData"
          @current-change="fetchData"
/>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourses, getPendingReviewCourses, approveCourse, rejectCourse, publishCourse, batchApproveCourses, batchRejectCourses } from '@/api/course'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const activeTab = ref('pending')

const searchForm = ref({ keyword: '' })

const selectedRows = ref([])
const tableRef = ref(null)

const statusMap = { pending: 1, approved: 2, rejected: 3 }

const emptyDescription = computed(() => {
  const map = { pending: t('courseApproval.emptyPending'), approved: t('courseApproval.emptyApproved'), rejected: t('courseApproval.emptyRejected') }
  return map[activeTab.value] || t('courseApproval.noData')
})

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function fetchData() {
  loading.value = true
  try {
    let data
    if (activeTab.value === 'pending') {
      // 待审核: 使用专用端点（含管理员专属过滤逻辑）
      const res = await getPendingReviewCourses({
        page: page.value - 1,
        size: size.value,
        keyword: searchForm.value.keyword || undefined,
      })
      data = res.data
    } else {
      const params = {
        page: page.value - 1,
        size: size.value,
        status: statusMap[activeTab.value],
        keyword: searchForm.value.keyword || undefined,
      }
      const res = await getCourses(params)
      data = res.data
    }
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('courseApproval.loadFailed')) }
  finally { loading.value = false }
}

function handleTabChange() { page.value = 1; fetchData() }
function handleSearch() { page.value = 1; fetchData() }
function handleReset() { searchForm.value.keyword = ''; page.value = 1; fetchData() }
function handleView(row) { router.push(`/courses/${row.id}`) }

async function handleApprove(row) {
  try { await ElMessageBox.confirm(t('course.confirmApproveCourse'), t('course.hintTitle'), { type: 'info' }) }
  catch { return }
  try { await approveCourse(row.id); ElMessage.success(t('course.approved')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}

async function handleReject(row) {
  let reason
  try {
    const res = await ElMessageBox.prompt(t('course.inputRejectReason'), t('course.rejectCourseTitle'), {
      confirmButtonText: t('course.confirmReject'), cancelButtonText: t('common.cancel'),
      inputValidator: v => v?.trim()?.length >= 10 || t('course.rejectReasonMin'),
      inputPlaceholder: t('course.rejectReasonPlaceholder'),
    })
    reason = res.value
  } catch { return }
  try { await rejectCourse(row.id, reason); ElMessage.success(t('course.rejectedSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.rejectFailed')) }
}

async function handlePublish(row) {
  try { await ElMessageBox.confirm(t('courseApproval.confirmPublish'), t('course.hintTitle'), { type: 'info' }) }
  catch { return }
  try { await publishCourse(row.id); ElMessage.success(t('course.published')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}

async function handleBatchApprove() {
  const ids = selectedRows.value.map(r => r.id)
  try {
    await ElMessageBox.confirm(t('courseApproval.confirmBatchApprove', { count: ids.length }), t('courseApproval.batchApproveTitle'), { type: 'warning' })
    const res = await batchApproveCourses(ids)
    ElMessage.success(t('courseApproval.batchApproveResult', { success: res.data.successCount, fail: res.data.failCount }))
    selectedRows.value = []
    fetchData()
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) ElMessage.error(e?.response?.data?.message || t('courseApproval.batchApproveFailed'))
  }
}

async function handleBatchReject() {
  let reason
  try {
    const res = await ElMessageBox.prompt(t('courseApproval.inputBatchRejectReason'), t('courseApproval.batchReject'), {
      confirmButtonText: t('course.confirmReject'), cancelButtonText: t('common.cancel'),
      inputValidator: v => v?.trim()?.length >= 10 || t('course.rejectReasonMin'),
      inputPlaceholder: t('course.rejectReasonPlaceholder'),
    })
    reason = res.value
  } catch { return }
  const ids = selectedRows.value.map(r => r.id)
  try {
    const result = await batchRejectCourses(ids, reason)
    ElMessage.success(t('courseApproval.batchRejectResult', { success: result.data.successCount, fail: result.data.failCount }))
    selectedRows.value = []
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('courseApproval.batchRejectFailed'))
  }
}

onMounted(fetchData)
</script>

<style scoped>
.approval-page { padding: var(--space-6); background: var(--el-bg-color-page); min-height: calc(100dvh - 120px); max-width: 1280px; margin: 0 auto; }
.page-breadcrumb { margin-bottom: var(--space-4); }
.filter-card { margin-bottom: var(--space-4); }
.table-card { margin-bottom: var(--space-4); }
.data-table { width: 100%; }
.w160 { width: 160px; }
.batch-toolbar { padding: var(--space-3) var(--space-4); display: flex; gap: var(--space-2); border-bottom: 1px solid var(--el-border-color-lighter); }
.pagination-wrap { margin-top: var(--space-4); display: flex; justify-content: center; padding: var(--space-4) 0; border-top: 1px solid var(--el-border-color-lighter); }
</style>
