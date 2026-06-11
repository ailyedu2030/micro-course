<!--
  收藏列表
  路由路径: /courses/favorites
  Phase 1
  Author: jackie
-->
<template>
  <div class="favorite-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="search-input-w200">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>收藏列表</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column prop="courseId" label="课程ID" width="100" />
        <el-table-column prop="courseName" label="课程名" min-width="150">
          <template #default="{ row }">
            {{ row.courseName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="userName" label="用户名" min-width="120">
          <template #default="{ row }">
            {{ row.userName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="收藏时间" width="180" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getFavorites } from '@/api/favorite'
import { getCourses } from '@/api/course'

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

const searchForm = reactive({
  courseId: ''
})

const fetchCourses = async () => {
  try {
    const { data } = await getCourses({ size: 1000 })
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: searchForm.courseId || undefined
    }
    const { data } = await getFavorites(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取收藏列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = ''
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

onMounted(() => {
  fetchCourses()
  fetchData()
})
</script>

<style scoped>
.favorite-list {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.data-table { width: 100%; }
.search-input-w200 { width: 200px; }
</style>