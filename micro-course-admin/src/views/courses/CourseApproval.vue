<!--
  课程审核
  管理员/教务处 审核课程提交
  Route: /courses/review
-->
<template>
  <div class="approval-page">
    <el-breadcrumb separator="/" class="page-breadcrumb">
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>课程审核</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 状态 Tab -->
    <el-card shadow="never" class="filter-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="待审核" name="pending" />
        <el-tab-pane label="已通过" name="approved" />
        <el-tab-pane label="已驳回" name="rejected" />
      </el-tabs>
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="课程标题/教师" clearable class="w160" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无待审核课程" :image-size="120" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="title" label="课程名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="提交教师" width="120" />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="warning">待审核</el-tag>
            <el-tag v-else-if="row.status === 2" type="success">已通过</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger">已驳回</el-tag>
            <el-tag v-else-if="row.status === 4" type="primary">已发布</el-tag>
            <el-tag v-else type="info">草稿</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 1" type="success" link size="small" @click="handleApprove(row)">
              <el-icon><Select /></el-icon>通过
            </el-button>
            <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleReject(row)">
              <el-icon><Close /></el-icon>驳回
            </el-button>
            <el-button v-if="row.status === 2" type="primary" link size="small" @click="handlePublish(row)">
              发布
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
          @current-change="fetchData" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourses, approveCourse, rejectCourse, publishCourse } from '@/api/course'
import { Select, Close } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const activeTab = ref('pending')

const searchForm = ref({ keyword: '' })

const statusMap = { pending: 1, approved: 2, rejected: 3 }

async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      status: statusMap[activeTab.value],
      keyword: searchForm.value.keyword || undefined,
    }
    const { data } = await getCourses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

function handleTabChange() { page.value = 1; fetchData() }
function handleSearch() { page.value = 1; fetchData() }
function handleReset() { searchForm.value.keyword = ''; page.value = 1; fetchData() }
function handleView(row) { router.push(`/courses/${row.id}`) }

async function handleApprove(row) {
  try { await approveCourse(row.id); ElMessage.success('已通过'); fetchData() }
  catch { ElMessage.error('操作失败') }
}

async function handleReject(row) {
  try {
    await ElMessageBox.confirm('确定驳回该课程？', '提示', { type: 'warning' })
    await rejectCourse(row.id, { reason: '管理员驳回' })
    ElMessage.success('已驳回'); fetchData()
  } catch { /* cancel */ }
}

async function handlePublish(row) {
  try { await publishCourse(row.id); ElMessage.success('已发布'); fetchData() }
  catch { ElMessage.error('操作失败') }
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
.pagination-wrap { margin-top: var(--space-4); display: flex; justify-content: center; padding: var(--space-4) 0; border-top: 1px solid var(--el-border-color-lighter); }
</style>
