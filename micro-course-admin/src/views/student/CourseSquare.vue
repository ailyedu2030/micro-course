<template>
  <div class="course-square">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            placeholder="课程名称/教师"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="searchForm.difficulty" placeholder="全部难度" clearable style="width: 120px">
            <el-option label="初级" value="BEGINNER" />
            <el-option label="中级" value="INTERMEDIATE" />
            <el-option label="高级" value="ADVANCED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="20">
      <!-- 左侧分类导航 -->
      <el-col :xs="24" :sm="24" :md="6" :lg="5">
        <el-card class="category-card" shadow="never">
          <template #header>
            <span>课程分类</span>
          </template>
          <el-scrollbar height="400px">
            <el-radio-group v-model="selectedCategoryId" class="category-group" @change="handleCategoryChange">
              <el-radio label="" style="width: 100%; margin-bottom: 8px">全部课程</el-radio>
              <el-radio
                v-for="cat in categoryTree"
                :key="cat.id"
                :label="String(cat.id)"
                style="width: 100%; margin-bottom: 8px"
              >
                {{ cat.name }}
              </el-radio>
            </el-radio-group>
          </el-scrollbar>
        </el-card>
      </el-col>

      <!-- 右侧课程网格 -->
      <el-col :xs="24" :sm="24" :md="18" :lg="19">
        <div v-loading="loading" class="course-grid">
          <el-row :gutter="16">
            <el-col
              v-for="(course, index) in courseList"
              :key="course.id"
              :xs="24"
              :sm="12"
              :md="12"
              :lg="8"
              :xl="6"
            >
              <el-card
                class="course-card"
                shadow="hover"
                @click="handleCourseClick(course.id)"
              >
                <div class="course-cover">
                  <el-tag
                    v-if="isRecommended(course, index)"
                    type="danger"
                    effect="dark"
                    class="recommend-tag"
                  >
                    推荐
                  </el-tag>
                  <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" />
                  <div v-else class="cover-placeholder">
                    <el-icon :size="40"><VideoCamera /></el-icon>
                  </div>
                </div>
                <div class="course-info">
                  <h3 class="course-title" :title="course.title">{{ course.title }}</h3>
                  <p class="course-teacher">
                    <el-icon><User /></el-icon>
                    {{ course.teacherName || '未知教师' }}
                  </p>
                  <p class="course-category">
                    <el-tag size="small" type="info">{{ course.categoryName || '未分类' }}</el-tag>
                  </p>
                  <div class="course-stats">
                    <span class="rating">
                      <el-icon><Star /></el-icon>
                      {{ course.avgRating ? course.avgRating.toFixed(1) : '0.0' }}
                    </span>
                    <span class="student-count">
                      <el-icon><UserFilled /></el-icon>
                      {{ course.studentCount || 0 }} 人
                    </span>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 空状态 -->
          <el-empty v-if="!loading && courseList.length === 0" description="暂无课程" />
        </div>

        <!-- 分页 -->
        <div v-if="totalElements > 0" class="pagination-wrap">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="totalElements"
            :page-sizes="[12, 24, 48]"
            layout="total,sizes,prev,pager,next"
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VideoCamera, User, Star, UserFilled } from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getCategories } from '@/api/course-category'

const router = useRouter()

const loading = ref(false)
const courseList = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(12)
const categoryTree = ref([])

const selectedCategoryId = ref('')

const searchForm = reactive({
  keyword: '',
  difficulty: ''
})

const fetchCategories = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    // 后端返回 { items: [...] } 或直接是数组
    categoryTree.value = data.items || data || []
  } catch (error) {
  }
}

const fetchCourses = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      keyword: searchForm.keyword || undefined,
      categoryId: selectedCategoryId.value || undefined,
      difficulty: searchForm.difficulty || undefined
    }
    const { data } = await getCourses(params)
    courseList.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
    ElMessage.error('获取课程列表失败')
  } finally {
    loading.value = false
  }
}

const isRecommended = (course, index) => {
  // 如果后端返回 isRecommended 字段则用该字段
  if ('isRecommended' in course) {
    return course.isRecommended
  }
  // 否则取 studentCount 排名前3
  if (index < 3) {
    return true
  }
  return false
}

const handleSearch = () => {
  page.value = 1
  fetchCourses()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.difficulty = ''
  selectedCategoryId.value = ''
  page.value = 1
  fetchCourses()
}

const handleCategoryChange = () => {
  page.value = 1
  fetchCourses()
}

const handleSizeChange = () => {
  page.value = 1
  fetchCourses()
}

const handlePageChange = () => {
  fetchCourses()
}

const handleCourseClick = (id) => {
  router.push(`/student/courses/${id}`)
}

onMounted(() => {
  fetchCategories()
  fetchCourses()
})
</script>

<style scoped>
.course-square {
  padding: 20px;
  background: #f0f2f5;
  min-height: calc(100vh - 60px);
}

.search-card {
  margin-bottom: 20px;
}

.category-card {
  margin-bottom: 20px;
}

.category-card :deep(.el-card__header) {
  padding: 12px 20px;
  font-weight: 600;
}

.category-group {
  display: flex;
  flex-direction: column;
}

.category-group :deep(.el-radio) {
  height: 36px;
  line-height: 36px;
}

.course-grid {
  min-height: 400px;
}

.course-card {
  margin-bottom: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.course-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.course-cover {
  position: relative;
  height: 160px;
  overflow: hidden;
  border-radius: 8px 8px 0 0;
  background: #409eff;
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.recommend-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1;
}

.course-info {
  padding: 12px;
}

.course-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-teacher {
  margin: 0 0 6px;
  font-size: 13px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.course-category {
  margin: 0 0 8px;
}

.course-stats {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.rating {
  display: flex;
  align-items: center;
  gap: 2px;
  color: #f56c6c;
}

.student-count {
  display: flex;
  align-items: center;
  gap: 2px;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

/* 移动端瀑布流 */
@media (max-width: 767px) {
  .course-square {
    padding: 12px;
  }

  .category-card {
    margin-bottom: 12px;
  }

  .course-grid :deep(.el-row) {
    display: block;
  }

  .course-grid :deep(.el-col) {
    width: 100%;
    max-width: 100%;
  }

  .course-cover {
    height: 180px;
  }
}
</style>