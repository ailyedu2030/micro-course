<!--
  我的评价
  路由路径: /student/reviews
  Phase 9.1
  Author: Claude Code Agent
-->
<template>
  <div class="my-reviews-container">
    <!-- PC Layout -->
    <template v-if="!isMobile">
      <!-- 面包屑导航 -->
      <el-breadcrumb class="page-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/student' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>我的评价</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="page-header">
        <h2 class="page-title">我的评价</h2>
        <span v-if="pagination.total > 0" class="count-badge">{{ pagination.total }}</span>
      </div>

      <!-- Filter bar -->
      <div class="filter-bar">
        <el-select
          v-model="filterCourseId"
          placeholder="筛选课程"
          clearable
          class="course-filter"
          @change="handleFilterChange"
        >
          <el-option
            v-for="item in courseOptions"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
      </div>

      <!-- Table card -->
      <el-card class="table-card" shadow="never">
        <el-skeleton :loading="loading" animated>
          <template #template>
            <el-skeleton-item variant="text" style="width: 100%; height: 40px;" />
            <el-skeleton-item variant="text" style="width: 100%; height: 40px; margin-top: var(--space-2);" />
            <el-skeleton-item variant="text" style="width: 100%; height: 40px; margin-top: var(--space-2);" />
          </template>
          <template #default>
            <el-table :data="reviews" border class="review-table">
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
                  <el-button type="danger" size="small" text class="btn-delete" @click="handleDelete(row)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-skeleton>

        <div v-if="reviews.length === 0 && !loading" class="empty-wrap">
          <el-empty description="暂无评价记录" />
        </div>

        <div v-if="errorState" class="error-wrap">
          <el-result icon="error" title="加载失败" sub-title="请稍后重试">
            <template #extra>
              <el-button type="primary" @click="fetchMyReviews">重新加载</el-button>
            </template>
          </el-result>
        </div>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="handlePageChange" aria-label="分页导航" />
        </div>
      </el-card>
    </template>

    <!-- H5 Layout -->
    <template v-else>
      <!-- 面包屑导航 -->
      <el-breadcrumb class="h5-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/student' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>我的评价</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="h5-header">
        <h2 class="h5-title">我的评价</h2>
        <span v-if="pagination.total > 0" class="h5-count-badge">{{ pagination.total }}</span>
      </div>

      <!-- Filter chips - horizontal scroll -->
      <div class="h5-filter-scroll">
        <div class="h5-filter-chips">
          <span
            v-for="item in courseOptions"
            :key="item.id"
            class="filter-chip"
            :class="{ active: filterCourseId === item.id }"
            role="button"
            tabindex="0"
            :aria-pressed="filterCourseId === item.id"
            :aria-label="`筛选课程 ${item.title}`"
            @click="handleChipClick(item.id)"
            @keydown.enter="handleChipClick(item.id)"
            @keydown.space.prevent="handleChipClick(item.id)"
          >
            {{ item.title }}
          </span>
        </div>
      </div>

      <!-- Card list -->
      <div class="h5-card-list">
        <el-skeleton :loading="loading" animated>
          <template #template>
            <div v-for="i in 3" :key="i" class="h5-skeleton-card">
              <el-skeleton-item variant="text" style="width: 60%; height: 20px;" />
              <el-skeleton-item variant="text" style="width: 40%; height: 16px; margin-top: var(--space-2);" />
              <el-skeleton-item variant="text" style="width: 100%; height: 60px; margin-top: var(--space-3);" />
            </div>
          </template>
          <template #default>
            <template v-if="reviews.length > 0">
              <div v-for="row in reviews" :key="row.id" class="review-card">
                <div class="review-card-header">
                  <router-link :to="`/student/courses/${row.courseId}`" class="h5-course-link">
                    {{ row.courseTitle || `课程 #${row.courseId}` }}
                  </router-link>
                  <el-button type="danger" size="small" text class="btn-delete" @click="handleDelete(row)">
                    删除
                  </el-button>
                </div>
                <div class="review-card-rating">
                  <el-rate v-model="row.rating" disabled size="small" />
                </div>
                <div class="review-card-content">
                  {{ row.content || '暂无评价内容' }}
                </div>
                <div class="review-card-footer">
                  <span class="review-card-time">{{ formatTime(row.createdAt) }}</span>
                </div>
              </div>
            </template>
            <div v-else-if="!errorState" class="h5-empty-wrap">
              <el-empty description="暂无评价记录" />
            </div>
          </template>
        </el-skeleton>

        <div v-if="errorState" class="h5-error-wrap">
          <el-result icon="error" title="加载失败" sub-title="请稍后重试">
            <template #extra>
              <el-button type="primary" size="small" @click="fetchMyReviews">重新加载</el-button>
            </template>
          </el-result>
        </div>
      </div>

      <!-- H5 Pagination -->
      <div v-if="pagination.total > 0" class="h5-pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" aria-label="分页导航" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReviews, getMyReviews } from '@/api/course-review'
import { deleteReview } from '@/api/review'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const loading = ref(false)
const reviews = ref([])
const errorState = ref(false)
const isMobile = ref(false)
const filterCourseId = ref(null)

const courseOptions = ref([])

const pagination = ref({
  page: 1,
  size: 10,
  total: 0
})

// Responsive flag with throttle
let resizeTimer = null
const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    isMobile.value = window.innerWidth < 768
  }, 200)
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const fetchCourseOptions = async () => {
  try {
    const { data } = await getMyReviews({ page: 1, size: 100 })
    const items = data?.items || data || []
    // Extract unique courses
    const courseMap = new Map()
    items.forEach(item => {
      if (item.courseId && item.courseTitle && !courseMap.has(item.courseId)) {
        courseMap.set(item.courseId, { id: item.courseId, title: item.courseTitle })
      }
    })
    courseOptions.value = Array.from(courseMap.values())
  } catch {
    // ignore
  }
}

const fetchMyReviews = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.getInfo()
  }
  const userId = userStore.userInfo?.id
  if (!userId) return

  loading.value = true
  errorState.value = false
  try {
    const { data } = await getMyReviews({
      ...pagination.value,
      courseId: filterCourseId.value
    })
    reviews.value = data?.items || data || []
    pagination.total = data?.totalElements || 0
  } catch {
    errorState.value = true
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
    // R1 P0 修复:必须真实调用删除 API
    await deleteReview(row.id)
    ElMessage.success('评价已删除')
    fetchMyReviews()
  } catch (err) {
    if (err === 'cancel') return
    ElMessage.error('删除评价失败,请稍后重试')
  }
}

const handleFilterChange = () => {
  pagination.value.page = 1
  fetchMyReviews()
}

const handleChipClick = (courseId) => {
  filterCourseId.value = filterCourseId.value === courseId ? null : courseId
  pagination.value.page = 1
  fetchMyReviews()
}

const handleSizeChange = () => {
  fetchMyReviews()
}

const handlePageChange = () => {
  fetchMyReviews()
}

onMounted(() => {
  isMobile.value = window.innerWidth < 768
  window.addEventListener('resize', handleResize)
  fetchMyReviews()
  fetchCourseOptions()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) clearTimeout(resizeTimer)
})
</script>

<style scoped>
/* ==================== PC Layout ==================== */
.my-reviews-container {
  padding: var(--space-4);
  max-width: 960px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

.page-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.page-title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 var(--space-2);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--el-color-white);
  background-color: var(--role-primary);
  border-radius: var(--radius-lg);
}

.filter-bar {
  margin-bottom: var(--space-4);
}

.course-filter {
  width: 200px;
}

.table-card {
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-base) ease;
}

.table-card:hover {
  box-shadow: var(--shadow-lg);
}

.review-table {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.course-link {
  color: var(--role-primary);
  text-decoration: none;
  font-weight: var(--weight-medium);
  cursor: pointer;
  transition: color var(--duration-base) ease;
}

.course-link:hover {
  text-decoration: underline;
  color: var(--role-primary-dark);
}

.btn-delete {
  cursor: pointer;
  color: var(--el-color-danger);
}

.empty-wrap {
  padding: var(--space-8) 0;
}

.error-wrap {
  padding: var(--space-6) 0;
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}

/* ==================== H5 Layout ==================== */
@media (max-width: 767px) {
  .my-reviews-container {
    padding: var(--space-3);
  }

  .h5-breadcrumb {
    margin-bottom: var(--space-3);
  }

  .h5-header {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-3);
  }

  .h5-title {
    margin: 0;
    font-size: var(--text-lg);
    font-weight: var(--weight-semibold);
    color: var(--el-text-color-primary);
  }

  .h5-count-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 20px;
    height: 20px;
    padding: 0 6px;
    font-size: var(--text-xs);
    font-weight: var(--weight-semibold);
    color: var(--el-color-white);
    background-color: var(--role-primary);
    border-radius: 10px;
  }

  .h5-filter-scroll {
    overflow-x: auto;
    margin-bottom: var(--space-3);
    -webkit-overflow-scrolling: touch;
  }

  .h5-filter-chips {
    display: flex;
    gap: var(--space-2);
    padding: 2px 0;
    width: max-content;
  }

  .filter-chip {
    flex-shrink: 0;
    padding: var(--space-1) var(--space-3);
    font-size: var(--text-sm);
    color: var(--el-text-color-regular);
    background-color: var(--el-fill-color-light);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--duration-base) ease;
    white-space: nowrap;
  }

  .filter-chip.active {
    color: var(--el-color-white);
    background-color: var(--role-primary);
  }

  .h5-card-list {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .h5-skeleton-card {
    padding: var(--space-4);
    background-color: var(--el-fill-color-light);
    border-radius: var(--radius-lg);
  }

  .review-card {
    padding: var(--space-4);
    background-color: var(--el-bg-color-overlay);
    border-radius: var(--radius-lg);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    transition: box-shadow var(--duration-base) ease;
  }

  .review-card:hover {
    box-shadow: var(--shadow-lg);
  }

  .review-card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: var(--space-2);
    margin-bottom: var(--space-2);
  }

  .h5-course-link {
    flex: 1;
    color: var(--role-primary);
    font-size: var(--text-base);
    font-weight: var(--weight-semibold);
    text-decoration: none;
    cursor: pointer;
  }

  .h5-course-link:hover {
    text-decoration: underline;
  }

  .review-card-rating {
    margin-bottom: var(--space-2);
  }

  .review-card-content {
    font-size: var(--text-sm);
    color: var(--el-text-color-regular);
    line-height: var(--leading-relaxed);
    margin-bottom: var(--space-2);
  }

  .review-card-footer {
    display: flex;
    justify-content: flex-end;
  }

  .review-card-time {
    font-size: var(--text-xs);
    color: var(--el-text-color-secondary);
  }

  .h5-empty-wrap {
    padding: var(--space-8) 0;
  }

  .h5-error-wrap {
    padding: var(--space-4) 0;
  }

  .h5-pagination {
    margin-top: var(--space-4);
    display: flex;
    justify-content: center;
  }

  .btn-delete {
    cursor: pointer;
    color: var(--el-color-danger);
    font-size: var(--text-xs);
    padding: 0;
  }
}

/* Desktop hover effects */
@media (min-width: 768px) {
  .table-card {
    border-radius: var(--radius-lg);
  }
}
</style>