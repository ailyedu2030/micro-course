<!--
  课程广场
  路由路径: /student/courses
  Phase 2
  Author: jackie
-->
<template>
  <div class="course-square role-student fade-in">
    <!-- PC 布局 (≥769px) -->
    <div v-if="!isMobile" class="pc-layout">
      <!-- Hero Section -->
      <div class="hero-section">
        <div class="hero-content">
          <h1 class="hero-title">发现优质课程</h1>
          <p class="hero-subtitle">开启你的学习之旅</p>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-card class="filter-card" shadow="never">
          <div class="filter-row">
            <!-- 关键字搜索 -->
            <el-input
              v-model="searchForm.keyword"
              placeholder="课程名称/教师"
              clearable
              class="keyword-input"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <!-- 难度下拉 -->
            <el-select
              v-model="searchForm.difficulty"
              placeholder="全部难度"
              clearable
              class="difficulty-select"
            >
              <el-option label="全部难度" value="" />
              <el-option label="初级" value="BEGINNER" />
              <el-option label="中级" value="INTERMEDIATE" />
              <el-option label="高级" value="ADVANCED" />
            </el-select>

            <!-- 分类横向滚动单选 -->
            <div class="category-scroll">
              <el-radio-group v-model="selectedCategoryId" class="category-radio-group" @change="handleCategoryChange">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button
                  v-for="cat in categoryList"
                  :key="cat.id"
                  :value="cat.id"
                >
                  {{ cat.name }}
                </el-radio-button>
              </el-radio-group>
            </div>

            <!-- 按钮 -->
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="handleReset">
              <el-icon><RefreshRight /></el-icon>
              重置
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- 主内容区 -->
      <div class="main-content">
        <!-- 课程网格 (75%) -->
        <div class="course-area">
          <!-- Loading 状态 -->
          <div v-if="loading" class="course-grid">
            <el-row :gutter="24">
              <el-col v-for="n in 8" :key="n" :span="8">
                <el-card class="course-card skeleton-card" shadow="never">
                  <el-skeleton animated>
                    <template #template>
                      <div class="skeleton-cover" />
                      <div class="skeleton-info">
                        <el-skeleton-item variant="text" class="skeleton-title" />
                        <el-skeleton-item variant="text" class="skeleton-meta" />
                        <el-skeleton-item variant="text" class="skeleton-price" />
                      </div>
                    </template>
                  </el-skeleton>
                </el-card>
              </el-col>
            </el-row>
          </div>

          <!-- 错误状态 -->
          <el-empty v-else-if="error" description="加载失败，请重试">
            <el-button type="primary" @click="fetchCourses">重新加载</el-button>
          </el-empty>

          <!-- 空状态 -->
          <el-empty
            v-else-if="courseList.length === 0"
            class="empty-state"
          >
            <template #image>
              <el-icon class="empty-icon"><VideoPlay /></el-icon>
            </template>
            <template #description>
              <p>暂无课程，换个筛选试试</p>
            </template>
          </el-empty>

          <!-- 课程列表 -->
          <div v-else class="course-grid">
            <el-row :gutter="24">
              <el-col
                v-for="course in courseList"
                :key="course.id"
                :span="8"
              >
                <el-card
                  class="course-card"
                  shadow="hover"
                  @click="handleCourseClick(course.id)"
                >
                  <!-- 封面 -->
                  <div class="course-cover">
                    <img
                      v-if="course.coverUrl"
                      :src="course.coverUrl"
                      :alt="course.title"
                    />
                    <div v-else class="cover-placeholder">
                      <el-icon :size="40"><VideoPlay /></el-icon>
                    </div>
                    <!-- 难度标签 -->
                    <el-tag
                      class="difficulty-chip"
                      :type="getDifficultyType(course.difficulty)"
                      effect="dark"
                    >
                      {{ getDifficultyLabel(course.difficulty) }}
                    </el-tag>
                  </div>

                  <!-- 课程信息 -->
                  <div class="course-info">
                    <h3 class="course-title">{{ course.title }}</h3>
                    <p class="course-meta">
                      <el-icon><User /></el-icon>
                      <span>{{ course.teacherName || '未知教师' }}</span>
                      <span class="separator">·</span>
                      <span>{{ course.categoryName || '未分类' }}</span>
                      <span class="separator">·</span>
                      <span>{{ course.studentCount || 0 }} 人学习</span>
                    </p>
                    <div class="course-footer">
                      <div class="rating">
                        <el-icon><Star /></el-icon>
                        <span>{{ course.avgRating ? course.avgRating.toFixed(1) : '0.0' }}</span>
                        <span class="rating-count">({{ course.ratingCount || 0 }})</span>
                      </div>
                      <div class="price">
                        ¥{{ course.price || 0 }}
                      </div>
                    </div>
                  </div>
                </el-card>
              </el-col>
            </el-row>

            <!-- 分页 -->
            <div v-if="totalElements > 0" class="pagination-wrap">
              <el-pagination
                v-model:current-page="page"
                v-model:page-size="size"
                :total="totalElements"
                :page-sizes="[12, 24, 48]"
                layout="total, sizes, prev, pager, next"
                background
                @size-change="handleSizeChange"
                @current-change="handlePageChange"
              />
            </div>
          </div>
        </div>

        <!-- 右侧边栏 (25%) -->
        <div class="sidebar">
          <!-- 热门课程 -->
          <el-card class="sidebar-card" shadow="never">
            <template #header>
              <div class="sidebar-header">
                <el-icon><HotWater /></el-icon>
                <span>热门课程</span>
              </div>
            </template>
            <div class="sidebar-list">
              <div
                v-for="(course, index) in hotCourses"
                :key="course.id"
                class="sidebar-item"
                @click="handleCourseClick(course.id)"
              >
                <span class="item-rank" :class="{ 'rank-top': index < 3 }">{{ index + 1 }}</span>
                <div class="item-info">
                  <p class="item-title">{{ course.title }}</p>
                  <p class="item-meta">
                    <el-icon><User /></el-icon>
                    {{ course.studentCount || 0 }} 人
                  </p>
                </div>
              </div>
              <el-empty v-if="hotCourses.length === 0" description="暂无数据" :image-size="60" />
            </div>
          </el-card>

          <!-- 最新课程 -->
          <el-card class="sidebar-card" shadow="never">
            <template #header>
              <div class="sidebar-header">
                <el-icon><Clock /></el-icon>
                <span>最新课程</span>
              </div>
            </template>
            <div class="sidebar-list">
              <div
                v-for="course in newestCourses"
                :key="course.id"
                class="sidebar-item"
                @click="handleCourseClick(course.id)"
              >
                <div class="item-info">
                  <p class="item-title">{{ course.title }}</p>
                  <p class="item-meta">
                    <el-icon><Calendar /></el-icon>
                    {{ formatDate(course.createdAt) }}
                  </p>
                </div>
              </div>
              <el-empty v-if="newestCourses.length === 0" description="暂无数据" :image-size="60" />
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- H5 布局 (≤768px) -->
    <div v-else class="h5-layout">
      <!-- Hero Section -->
      <div class="hero-section h5-hero">
        <h1 class="hero-title">发现优质课程</h1>
      </div>

      <!-- 筛选栏 (单行滚动 chip) -->
      <div class="h5-filter">
        <el-scroll-bar>
          <div class="h5-filter-chips">
            <el-tag
              :type="selectedCategoryId === '' ? 'primary' : 'info'"
              class="filter-chip"
              @click="handleCategorySelect('')"
            >
              全部
            </el-tag>
            <el-tag
              v-for="cat in categoryList"
              :key="cat.id"
              :type="selectedCategoryId === cat.id ? 'primary' : 'info'"
              class="filter-chip"
              @click="handleCategorySelect(cat.id)"
            >
              {{ cat.name }}
            </el-tag>
          </div>
        </el-scroll-bar>
      </div>

      <!-- 搜索框 -->
      <div class="h5-search">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索课程"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- Loading 状态 -->
      <div v-if="loading" class="h5-course-list">
        <el-card v-for="n in 4" :key="n" class="h5-course-card" shadow="never">
          <el-skeleton animated>
            <template #template>
              <div class="skeleton-cover h5-skeleton-cover" />
              <div class="skeleton-info">
                <el-skeleton-item variant="text" class="skeleton-title" />
                <el-skeleton-item variant="text" class="skeleton-meta" />
              </div>
            </template>
          </el-skeleton>
        </el-card>
      </div>

      <!-- 错误状态 -->
      <el-empty v-else-if="error" description="加载失败，请重试">
        <el-button type="primary" @click="fetchCourses">重新加载</el-button>
      </el-empty>

      <!-- 空状态 -->
      <el-empty
        v-else-if="courseList.length === 0"
        class="empty-state"
      >
        <template #image>
          <el-icon class="empty-icon"><VideoPlay /></el-icon>
        </template>
        <template #description>
          <p>暂无课程，换个筛选试试</p>
        </template>
      </el-empty>

      <!-- 课程列表 (瀑布流单列) -->
      <div v-else class="h5-course-list">
        <el-card
          v-for="course in courseList"
          :key="course.id"
          class="h5-course-card"
          shadow="hover"
          @click="handleCourseClick(course.id)"
        >
          <!-- 封面 16:9 -->
          <div class="h5-course-cover">
            <img
              v-if="course.coverUrl"
              :src="course.coverUrl"
              :alt="course.title"
            />
            <div v-else class="cover-placeholder">
              <el-icon :size="32"><VideoPlay /></el-icon>
            </div>
            <!-- 难度标签 -->
            <el-tag
              class="h5-difficulty-chip"
              :type="getDifficultyType(course.difficulty)"
              effect="dark"
            >
              {{ getDifficultyLabel(course.difficulty) }}
            </el-tag>
          </div>

          <!-- 课程信息 -->
          <div class="h5-course-info">
            <h3 class="h5-course-title">{{ course.title }}</h3>
            <p class="h5-course-meta">
              <el-icon><User /></el-icon>
              {{ course.teacherName || '未知教师' }}
            </p>
            <div class="h5-course-footer">
              <span class="h5-difficulty-text">{{ getDifficultyLabel(course.difficulty) }}</span>
              <span class="h5-price">¥{{ course.price || 0 }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 底部安全区 -->
      <div class="h5-bottom-safe" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search,
  RefreshRight,
  VideoPlay,
  User,
  Star,
  HotWater,
  Clock,
  Calendar
} from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getCategories } from '@/api/course-category'

const router = useRouter()

// 响应式设备检测
const isMobile = ref(window.innerWidth <= 768)
const updateDevice = () => {
  isMobile.value = window.innerWidth <= 768
}
onMounted(() => {
  window.addEventListener('resize', updateDevice)
})
onUnmounted(() => {
  window.removeEventListener('resize', updateDevice)
})

// 状态
const loading = ref(false)
const error = ref(false)
const courseList = ref([])
const categoryList = ref([])
const hotCourses = ref([])
const newestCourses = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(12)
const selectedCategoryId = ref('')

const searchForm = reactive({
  keyword: '',
  difficulty: ''
})

// 获取分类
const fetchCategories = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categoryList.value = data.items || data || []
  } catch {
    // 忽略分类加载错误
  }
}

// 获取课程列表
const fetchCourses = async () => {
  loading.value = true
  error.value = false
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
  } catch {
    error.value = true
    ElMessage.error('获取课程列表失败')
  } finally {
    loading.value = false
  }
}

// 获取热门/最新课程
const fetchSideCourses = async () => {
  try {
    // 热门课程：按学生数排序
    const hotParams = {
      page: 0,
      size: 5,
      sort: 'studentCount',
      order: 'desc'
    }
    const { data: hotData } = await getCourses(hotParams)
    hotCourses.value = (hotData.items || []).slice(0, 5)

    // 最新课程：按创建时间排序
    const newestParams = {
      page: 0,
      size: 5,
      sort: 'createdAt',
      order: 'desc'
    }
    const { data: newestData } = await getCourses(newestParams)
    newestCourses.value = (newestData.items || []).slice(0, 5)
  } catch {
    // 忽略侧边栏错误
  }
}

// 难度标签
const getDifficultyType = (difficulty) => {
  const map = {
    BEGINNER: 'success',
    INTERMEDIATE: 'warning',
    ADVANCED: 'danger'
  }
  return map[difficulty] || 'info'
}

const getDifficultyLabel = (difficulty) => {
  const map = {
    BEGINNER: '初级',
    INTERMEDIATE: '中级',
    ADVANCED: '高级'
  }
  return map[difficulty] || '全部'
}

// 日期格式化
const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

// 搜索
const handleSearch = () => {
  page.value = 1
  fetchCourses()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.difficulty = ''
  selectedCategoryId.value = ''
  page.value = 1
  fetchCourses()
}

// 分类选择
const handleCategoryChange = () => {
  page.value = 1
  fetchCourses()
}

const handleCategorySelect = (categoryId) => {
  selectedCategoryId.value = categoryId
  page.value = 1
  fetchCourses()
}

// 分页
const handleSizeChange = () => {
  page.value = 1
  fetchCourses()
}

const handlePageChange = () => {
  fetchCourses()
}

// 课程点击
const handleCourseClick = (id) => {
  router.push(`/student/courses/${id}`)
}

// 初始化
onMounted(() => {
  fetchCategories()
  fetchCourses()
  fetchSideCourses()
})
</script>

<style scoped>
/* ================================================
   PC 布局
   ================================================ */
.pc-layout {
  min-height: 100vh;
  background: var(--el-bg-color);
}

/* Hero Section */
.hero-section {
  height: 120px;
  background: linear-gradient(135deg, var(--role-primary) 0%, var(--role-primary-dark) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-content {
  text-align: center;
  color: var(--el-color-white);
}

.hero-title {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  letter-spacing: 2px;
}

.hero-subtitle {
  margin: var(--space-2) 0 0;
  font-size: var(--text-base);
  opacity: 0.9;
}

/* 筛选栏 */
.filter-bar {
  margin: calc(var(--space-4) * -1) var(--space-5) var(--space-4);
  position: relative;
  z-index: 10;
}

.filter-card :deep(.el-card__body) {
  padding: var(--space-4);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.keyword-input {
  width: 240px;
}

.difficulty-select {
  width: 140px;
}

.category-scroll {
  flex: 1;
  overflow-x: auto;
}

.category-radio-group {
  display: flex;
  gap: var(--space-2);
  white-space: nowrap;
}

.category-radio-group :deep(.el-radio-button__inner) {
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-4);
}

/* 主内容区 */
.main-content {
  display: flex;
  gap: var(--space-5);
  padding: 0 var(--space-5);
  margin-top: var(--space-4);
}

.course-area {
  flex: 0 0 75%;
}

.sidebar {
  flex: 0 0 25%;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* 课程网格 */
.course-grid {
  min-height: 400px;
}

.course-card {
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
  margin-bottom: var(--space-4);
  overflow: hidden;
}

.course-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg) !important;
}

.course-cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  background: var(--role-primary-light);
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
  color: var(--role-primary-light-5);
}

.difficulty-chip {
  position: absolute;
  top: var(--space-3);
  left: var(--space-3);
}

.course-info {
  padding: var(--space-4);
}

.course-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: var(--leading-snug);
  height: 2.75em;
}

.course-meta {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.course-meta .separator {
  color: var(--el-border-color);
}

.course-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rating {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--el-color-warning);
  font-size: var(--text-sm);
}

.rating-count {
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
}

.price {
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  color: var(--role-primary);
}

/* 分页 */
.pagination-wrap {
  margin-top: var(--space-5);
  display: flex;
  justify-content: center;
}

/* 侧边栏 */
.sidebar-card {
  border-radius: var(--radius-lg);
}

.sidebar-card :deep(.el-card__header) {
  padding: var(--space-4);
  font-weight: var(--weight-semibold);
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--el-text-color-primary);
}

.sidebar-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.sidebar-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  cursor: pointer;
  padding: var(--space-2);
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-out);
}

.sidebar-item:hover {
  background: var(--el-fill-color-lighter);
}

.item-rank {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.item-rank.rank-top {
  background: var(--role-primary);
  color: var(--el-color-white);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

/* ================================================
   H5 布局
   ================================================ */
.h5-layout {
  min-height: 100vh;
  background: var(--el-bg-color);
  padding-bottom: 56px;
}

/* Hero */
.h5-hero {
  height: 80px;
}

.h5-hero .hero-title {
  font-size: var(--text-lg);
}

/* 筛选 */
.h5-filter {
  padding: var(--space-3) var(--space-4);
  background: var(--el-bg-color-overlay);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.h5-filter-chips {
  display: flex;
  gap: var(--space-2);
  white-space: nowrap;
}

.filter-chip {
  cursor: pointer;
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-4);
}

/* 搜索 */
.h5-search {
  padding: 0 var(--space-4) var(--space-3);
  background: var(--el-bg-color-overlay);
}

/* 课程列表 */
.h5-course-list {
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.h5-course-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.h5-course-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg) !important;
}

.h5-course-cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  background: var(--role-primary-light);
}

.h5-course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.h5-difficulty-chip {
  position: absolute;
  top: var(--space-2);
  left: var(--space-2);
  font-size: var(--text-xs);
  padding: 2px 6px;
}

.h5-course-info {
  padding: var(--space-3);
}

.h5-course-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.h5-course-meta {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.h5-course-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.h5-difficulty-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.h5-price {
  font-size: var(--text-md);
  font-weight: var(--weight-bold);
  color: var(--role-primary);
}

.h5-bottom-safe {
  height: 56px;
}

/* ================================================
   Skeleton Loading
   ================================================ */
.skeleton-card {
  border-radius: var(--radius-lg);
}

.skeleton-cover {
  aspect-ratio: 16 / 9;
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.h5-skeleton-cover {
  margin-bottom: var(--space-3);
}

.skeleton-info {
  padding: var(--space-4);
}

.skeleton-title {
  height: 20px;
  margin-bottom: var(--space-2);
  width: 80%;
}

.skeleton-meta {
  height: 14px;
  margin-bottom: var(--space-2);
  width: 60%;
}

.skeleton-price {
  height: 16px;
  width: 40%;
}

/* ================================================
   Empty State
   ================================================ */
.empty-state {
  padding: var(--space-8) 0;
}

.empty-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
}
</style>