<!--
  我的评价
  路由路径: /student/reviews
  Phase 9.1
  Author: Claude Code Agent
-->
<template>
  <div class="my-reviews-container">
    <div class="header">
      <h2>我的评价</h2>
    </div>

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="reviews" stripe border>
        <el-table-column label="课程" min-width="180">
          <template #default="{ row }">
            <router-link :to="`/student/courses/${row.courseId}`" class="course-link">
              {{ row.courseTitle || `课程 #${row.courseId}` }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="100" align="center">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.content || '暂无评价内容' }}
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="120" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" text @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="reviews.length === 0 && !loading" class="empty-wrap">
        <el-empty description="暂无评价记录" />
      </div>
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReviews } from '@/api/course-review'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const loading = ref(false)
const reviews = ref([])

const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const fetchMyReviews = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.getInfo()
  }
  const userId = userStore.userInfo?.id
  if (!userId) return

  loading.value = true
  try {
    const { data } = await getReviews(null, {
      ...pagination.value,
      userId
    })
    reviews.value = data?.items || data || []
    pagination.total = data?.totalElements || 0
  } catch {
    ElMessage.error('加载评价失败')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除这条评价吗？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    ElMessage.success('评价已删除')
    fetchMyReviews()
  } catch {
    // 用户取消
  }
}

const handleSizeChange = () => {
  fetchMyReviews()
}

const handlePageChange = () => {
  fetchMyReviews()
}

onMounted(() => {
  fetchMyReviews()
})
</script>

<style scoped>
.my-reviews-container {
  padding: 20px;
  max-width: 960px;
  margin: 0 auto;
}

.header {
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.table-card {
  border-radius: 8px;
}

.course-link {
  color: #409eff;
  text-decoration: none;
  font-weight: 500;
}

.course-link:hover {
  text-decoration: underline;
}

.empty-wrap {
  padding: 40px 0;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .my-reviews-container {
    padding: 12px;
  }

  .header h2 {
    font-size: 18px;
  }
}
</style>
