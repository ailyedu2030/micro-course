<!--
  课程广场 (PC 端，不做 H5)
  路由: /student/courses
  规范: .playwright-mcp/course-square-spec/{01-IA,02-VISUAL,03-COMPONENTS,04-CHECKLIST}.md
  设计: ui-ux-pro-max 技能 + DESIGN.md v1.1
  角色: STUDENT → 主色 #6366f1
-->
<template>
  <div class="course-square fade-in">
    <!-- ============ Page Header (面包屑) ============ -->
    <nav class="page-breadcrumb" aria-label="面包屑">
      <el-icon><HomeFilled /></el-icon>
      <span class="bc-sep">/</span>
      <span class="bc-current">课程广场</span>
    </nav>

    <!-- ============ Hero Section (160px 3 段渐变 + 装饰圆) ============ -->
    <section class="hero-section" aria-label="课程发现">
      <div class="hero-decoration" aria-hidden="true">
        <div class="deco-circle deco-1" />
        <div class="deco-circle deco-2" />
        <div class="deco-circle deco-3" />
      </div>
      <div class="hero-content">
        <h1 class="hero-title">发现优质课程</h1>
        <p class="hero-subtitle">开启你的学习之旅</p>
      </div>
    </section>

    <!-- ============ 筛选卡 (-28px 上提浮在 Hero 下方) ============ -->
    <div class="filter-bar">
      <el-card class="filter-card" shadow="never">
        <div class="filter-row">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索课程名称或教师"
            clearable
            class="keyword-input"
            aria-label="搜索关键词"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select
            v-model="searchForm.difficulty"
            placeholder="全部难度"
            clearable
            class="difficulty-select"
            aria-label="难度筛选"
            @change="handleSearch"
          >
            <el-option label="全部难度" value="" />
            <el-option label="初级" :value="1" />
            <el-option label="中级" :value="2" />
            <el-option label="高级" :value="3" />
          </el-select>

          <div class="category-scroll">
            <el-radio-group
              v-model="selectedCategoryId"
              class="category-chip-group"
              aria-label="课程分类"
              @change="handleCategoryChange"
            >
              <el-radio-button :value="null">全部</el-radio-button>
              <el-radio-button
                v-for="cat in categoryList"
                :key="cat.id"
                :value="cat.id"
              >
                {{ cat.name }}
              </el-radio-button>
            </el-radio-group>
          </div>

          <el-button
            type="primary"
            :icon="Search"
            :loading="loading"
            class="search-btn"
            @click="handleSearch"
          >
            搜索
          </el-button>
          <el-button type="default" :icon="RefreshRight" class="reset-btn" @click="handleReset">
            重置
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- ============ Main Content (75% / 25%) ============ -->
    <div class="main-content">
      <!-- 课程区 -->
      <main class="course-area" aria-busy="loading">
        <!-- ============ Loading ============ -->
        <div v-if="loading" class="course-grid" aria-label="加载中">
          <el-row :gutter="24">
            <el-col v-for="n in 8" :key="n" :xs="24" :sm="12" :md="8" :lg="8">
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

        <!-- ============ Error ============ -->
        <el-empty
          v-else-if="error"
          class="state-block"
          description="课程加载失败"
        >
          <template #image>
            <el-icon :size="64" class="state-icon state-icon--error">
              <WarningFilled />
            </el-icon>
          </template>
          <p class="state-detail">网络连接异常，请稍后重试</p>
          <el-button type="primary" :icon="Refresh" class="state-action" @click="fetchCourses">
            重新加载
          </el-button>
        </el-empty>

        <!-- ============ Empty (no data) ============ -->
        <el-empty
          v-else-if="courseList.length === 0 && !isSearchActive"
          class="state-block"
          description="暂无课程"
        >
          <template #image>
            <el-icon :size="64" class="state-icon">
              <Notebook />
            </el-icon>
          </template>
          <p class="state-detail">换个分类或筛选条件试试</p>
          <el-button type="primary" class="state-action" @click="handleReset">
            重置筛选
          </el-button>
        </el-empty>

        <!-- ============ Empty (no search result) ============ -->
        <el-empty
          v-else-if="courseList.length === 0 && isSearchActive"
          class="state-block"
          :description="`未找到与 '${searchForm.keyword || '当前筛选'}' 相关的课程`"
        >
          <template #image>
            <el-icon :size="64" class="state-icon">
              <Search />
            </el-icon>
          </template>
          <el-button type="primary" class="state-action" @click="handleReset">
            清除筛选
          </el-button>
        </el-empty>

        <!-- ============ Normal (数据展示) ============ -->
        <div v-else class="course-grid">
          <el-row :gutter="24">
            <el-col
              v-for="course in courseList"
              :key="course.id"
              :xs="24"
              :sm="12"
              :md="8"
              :lg="8"
            >
              <article
                class="course-card"
                role="button"
                tabindex="0"
                :aria-label="`课程 ${course.title}，教师 ${course.teacherName || '未知'}，${course.studentCount || 0} 人学习`"
                @click="handleCourseClick(course.id)"
                @keydown.enter="handleCourseClick(course.id)"
                @keydown.space.prevent="handleCourseClick(course.id)"
              >
                <!-- 缩略图 16:9 -->
                <div class="course-cover">
                  <img
                    v-if="course.coverUrl"
                    :src="course.coverUrl"
                    :alt="course.title"
                    loading="lazy"
                    class="cover-img"
                  />
                  <div v-else class="cover-placeholder" aria-hidden="true">
                    <el-icon :size="48"><VideoPlay /></el-icon>
                  </div>
                  <el-tag
                    v-if="course.difficulty"
                    class="difficulty-chip"
                    :type="getDifficultyType(course.difficulty)"
                    effect="dark"
                    size="small"
                  >
                    {{ getDifficultyLabel(course.difficulty) }}
                  </el-tag>
                </div>

                <!-- 课程信息 -->
                <div class="course-info">
                  <h3 class="course-title" :title="course.title">{{ course.title }}</h3>
                  <p class="course-meta">
                    <el-icon class="meta-icon"><User /></el-icon>
                    <span>{{ course.teacherName || '未知教师' }}</span>
                    <span class="separator" aria-hidden="true">·</span>
                    <span>{{ course.categoryName || '未分类' }}</span>
                    <span class="separator" aria-hidden="true">·</span>
                    <span>{{ formatStudentCount(course.studentCount) }}</span>
                  </p>
                  <div class="course-footer">
                    <div class="rating" :aria-label="`评分 ${formatRating(course.avgRating)}`">
                      <el-icon class="rating-star"><Star /></el-icon>
                      <span class="rating-value">{{ formatRating(course.avgRating) }}</span>
                      <span class="rating-count" v-if="course.ratingCount">
                        ({{ course.ratingCount }})
                      </span>
                      <span class="rating-none" v-else>暂无评分</span>
                    </div>
                    <div class="price" :class="{ 'price--free': !course.price }">
                      {{ course.price ? `¥${course.price}` : '免费' }}
                    </div>
                  </div>
                </div>
              </article>
            </el-col>
          </el-row>

          <!-- 分页 -->
          <div v-if="totalElements > 0" class="pagination-wrap">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="totalElements"
              :page-sizes="[12, 24, 48]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              class="course-pagination"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </div>
      </main>

      <!-- ============ 侧边栏 (25%) ============ -->
      <aside class="sidebar" aria-label="侧栏">
        <!-- 热门课程 -->
        <section class="sidebar-card" aria-label="热门课程">
          <header class="sidebar-header">
            <el-icon class="sidebar-icon"><TrendCharts /></el-icon>
            <h2 class="sidebar-title">热门课程</h2>
          </header>
          <div class="sidebar-list">
            <div
              v-for="(course, index) in hotCourses"
              :key="course.id"
              class="sidebar-item"
              role="button"
              tabindex="0"
              :aria-label="`热门第 ${index + 1} 名：${course.title}`"
              @click="handleCourseClick(course.id)"
              @keydown.enter="handleCourseClick(course.id)"
            >
              <span
                class="item-rank"
                :class="{ 'item-rank--top': index < 3 }"
                :aria-hidden="true"
              >{{ index + 1 }}</span>
              <div class="item-info">
                <p class="item-title">{{ course.title }}</p>
                <p class="item-meta">
                  <el-icon class="meta-icon"><User /></el-icon>
                  <span>{{ formatStudentCount(course.studentCount) }}</span>
                </p>
              </div>
            </div>
            <el-empty
              v-if="hotCourses.length === 0 && !sidebarLoading"
              :image-size="60"
              description="暂无数据"
              class="sidebar-empty"
            />
          </div>
        </section>

        <!-- 最新课程 -->
        <section class="sidebar-card" aria-label="最新课程">
          <header class="sidebar-header">
            <el-icon class="sidebar-icon"><Clock /></el-icon>
            <h2 class="sidebar-title">最新课程</h2>
          </header>
          <div class="sidebar-list">
            <div
              v-for="course in newestCourses"
              :key="course.id"
              class="sidebar-item"
              role="button"
              tabindex="0"
              :aria-label="`最新课程：${course.title}`"
              @click="handleCourseClick(course.id)"
              @keydown.enter="handleCourseClick(course.id)"
            >
              <div class="item-info item-info--no-rank">
                <p class="item-title">{{ course.title }}</p>
                <p class="item-meta">
                  <el-icon class="meta-icon"><Calendar /></el-icon>
                  <span>{{ formatDate(course.createdAt) }}</span>
                </p>
              </div>
            </div>
            <el-empty
              v-if="newestCourses.length === 0 && !sidebarLoading"
              :image-size="60"
              description="暂无数据"
              class="sidebar-empty"
            />
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search,
  Refresh,
  RefreshRight,
  VideoPlay,
  User,
  Star,
  TrendCharts,
  Clock,
  Calendar,
  WarningFilled,
  Notebook,
  HomeFilled
} from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getCategories } from '@/api/course-category'

const router = useRouter()

// 状态
const loading = ref(false)
const error = ref(false)
const sidebarLoading = ref(false)
const courseList = ref([])
const categoryList = ref([])
const hotCourses = ref([])
const newestCourses = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(12)
const selectedCategoryId = ref(null)

const searchForm = reactive({
  keyword: '',
  difficulty: ''
})

// 是否有筛选条件（区分两种空状态）
const isSearchActive = computed(
  () => !!(searchForm.keyword || searchForm.difficulty || selectedCategoryId.value)
)

// 难度映射（1 初级 / 2 中级 / 3 高级）
const DIFFICULTY_MAP = {
  1: { label: '初级', type: 'success' },
  2: { label: '中级', type: 'warning' },
  3: { label: '高级', type: 'danger' }
}
const getDifficultyLabel = (d) => DIFFICULTY_MAP[d]?.label || '全部'
const getDifficultyType = (d) => DIFFICULTY_MAP[d]?.type || 'info'

// 学员数格式化（>1000 显示 k）
const formatStudentCount = (n) => {
  const count = n || 0
  if (count === 0) return '首学者'
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k 人学习`
  return `${count} 人学习`
}

// 评分格式化
const formatRating = (r) => (r ? Number(r).toFixed(1) : '—')

// 日期格式化
const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

// 拉分类
const fetchCategories = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categoryList.value = data?.items || data || []
  } catch (e) {
    console.warn('分类加载失败:', e)
  }
}

// 拉课程
const fetchCourses = async () => {
  loading.value = true
  error.value = false
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      status: 4
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (selectedCategoryId.value) params.categoryId = selectedCategoryId.value
    if (searchForm.difficulty) params.difficulty = searchForm.difficulty

    const { data } = await getCourses(params)
    courseList.value = data?.items || []
    totalElements.value = data?.totalElements || 0
  } catch (e) {
    console.error('课程加载失败:', e)
    error.value = true
    ElMessage.error('课程加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 拉侧栏
const fetchSideCourses = async () => {
  sidebarLoading.value = true
  try {
    const { data: hotData } = await getCourses({
      page: 0,
      size: 5,
      sort: 'studentCount,desc'
    })
    hotCourses.value = (hotData?.items || []).slice(0, 5)

    const { data: newestData } = await getCourses({
      page: 0,
      size: 5,
      sort: 'createdAt,desc'
    })
    newestCourses.value = (newestData?.items || []).slice(0, 5)
  } catch (e) {
    console.warn('侧栏加载失败:', e)
  } finally {
    sidebarLoading.value = false
  }
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
  selectedCategoryId.value = null
  page.value = 1
  fetchCourses()
}

// 分类切换
const handleCategoryChange = () => {
  page.value = 1
  fetchCourses()
}

// 翻页
const handleSizeChange = (newSize) => {
  size.value = newSize
  page.value = 1
  fetchCourses()
}

const handlePageChange = (newPage) => {
  page.value = newPage
  fetchCourses()
}

// 跳详情
const handleCourseClick = (id) => {
  if (!id) return
  router.push(`/student/courses/${id}`)
}

onMounted(() => {
  fetchCategories()
  fetchCourses()
  fetchSideCourses()
})
</script>

<style scoped>
/* ================================================
   Root
   ================================================ */
.course-square {
  min-height: 100vh;
  background: var(--el-bg-color);
  padding-bottom: var(--space-8);
}

/* ================================================
   Breadcrumb
   ================================================ */
.page-breadcrumb {
  max-width: var(--content-max-width);
  margin: 0 auto;
  padding: var(--space-4) var(--space-6) 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.page-breadcrumb .bc-sep {
  color: var(--el-text-color-placeholder);
}

.page-breadcrumb .bc-current {
  color: var(--el-text-color-primary);
  font-weight: var(--weight-medium);
}

/* ================================================
   Hero (160px 3 段渐变 + 装饰圆)
   ================================================ */
:root {
  --content-max-width: 1200px;
}

.hero-section {
  position: relative;
  height: 160px;
  max-width: var(--content-max-width);
  margin: var(--space-3) auto 0;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--role-primary) 0%, var(--role-primary-dark) 50%, var(--role-primary-darkest) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.15);
}

.hero-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.deco-1 {
  width: 200px;
  height: 200px;
  top: -60px;
  right: -40px;
  background: rgba(255, 255, 255, 0.08);
}

.deco-2 {
  width: 120px;
  height: 120px;
  bottom: -40px;
  left: 20%;
  background: rgba(255, 255, 255, 0.05);
}

.deco-3 {
  width: 80px;
  height: 80px;
  top: 30%;
  right: 25%;
  background: rgba(255, 255, 255, 0.06);
}

.hero-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: var(--el-color-white);
}

.hero-title {
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-wider);
  line-height: var(--leading-tight);
}

.hero-subtitle {
  margin: var(--space-2) 0 0;
  font-size: var(--text-base);
  opacity: 0.9;
  letter-spacing: var(--tracking-wide);
}

/* ================================================
   Filter Bar (-28px 上提)
   ================================================ */
.filter-bar {
  max-width: var(--content-max-width);
  margin: calc(var(--space-7) * -1) auto 0;
  padding: 0 var(--space-6);
  position: relative;
  z-index: 10;
}

.filter-card {
  border-radius: var(--radius-lg);
  border: none;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.08) !important;
}

.filter-card :deep(.el-card__body) {
  padding: var(--space-4) var(--space-5);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.keyword-input {
  width: 240px;
  flex-shrink: 0;
}

.difficulty-select {
  width: 140px;
  flex-shrink: 0;
}

.category-scroll {
  flex: 1;
  min-width: 200px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.category-scroll::-webkit-scrollbar {
  height: 4px;
}

.category-chip-group {
  display: flex;
  gap: var(--space-2);
  white-space: nowrap;
}

.category-chip-group :deep(.el-radio-button__inner) {
  border-radius: var(--radius-pill) !important;
  padding: 6px var(--space-4);
  border: 1px solid var(--el-border-color) !important;
  box-shadow: none !important;
  transition: all var(--duration-base) var(--ease-out);
}

.category-chip-group :deep(.el-radio-button__inner:hover) {
  background-color: var(--el-fill-color-light);
  border-color: var(--role-primary) !important;
  color: var(--role-primary);
}

.category-chip-group :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background-color: var(--role-primary) !important;
  border-color: var(--role-primary) !important;
  color: var(--el-color-white) !important;
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.3) !important;
}

.search-btn,
.reset-btn {
  flex-shrink: 0;
  border-radius: var(--radius-md);
  transition: all var(--duration-base) var(--ease-out);
}

.search-btn {
  background: linear-gradient(135deg, var(--role-primary), var(--role-primary-dark));
  border: none;
}

.search-btn:hover {
  background: linear-gradient(135deg, var(--role-primary-dark), var(--role-primary-darker));
  transform: translateY(-1px);
  box-shadow: var(--shadow-primary);
}

.search-btn:active {
  transform: translateY(0) scale(0.97);
}

.reset-btn:hover {
  background-color: var(--el-fill-color-light);
  border-color: var(--role-primary);
  color: var(--role-primary);
}

.reset-btn:active {
  transform: scale(0.97);
}

/* ================================================
   Main Content (75% / 25%)
   ================================================ */
.main-content {
  max-width: var(--content-max-width);
  margin: var(--space-5) auto 0;
  padding: 0 var(--space-6);
  display: flex;
  gap: var(--space-5);
  align-items: flex-start;
}

.course-area {
  flex: 0 0 75%;
  min-width: 0;
}

/* ================================================
   Course Grid & Card
   ================================================ */
.course-grid {
  min-height: 400px;
}

.course-card {
  border-radius: var(--radius-lg);
  background: var(--el-bg-color-overlay);
  box-shadow: var(--shadow-md);
  margin-bottom: var(--space-4);
  overflow: hidden;
  cursor: pointer;
  outline: none;
  transition:
    transform var(--duration-base) var(--ease-out),
    box-shadow var(--duration-base) var(--ease-out);
}

.course-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-xl) !important;
}

.course-card:focus-visible {
  outline: 2px solid var(--role-primary);
  outline-offset: 2px;
}

.course-card:active {
  transform: translateY(-2px);
}

/* 缩略图 16:9 */
.course-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: linear-gradient(135deg, var(--role-primary-light), var(--role-primary-light-7));
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--duration-slow) var(--ease-out);
}

.course-card:hover .cover-img {
  transform: scale(1.05);
}

.cover-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--role-primary);
  opacity: 0.6;
}

/* 难度 chip (浮在缩略图左上) */
.difficulty-chip {
  position: absolute;
  top: var(--space-3);
  left: var(--space-3);
  border: none;
  border-radius: var(--radius-pill) !important;
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wide);
  box-shadow: var(--shadow-chip);
}

/* 课程信息 */
.course-info {
  padding: var(--space-4);
}

.course-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  line-height: var(--leading-snug);
  height: 2.75em;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  transition: color var(--duration-base) var(--ease-out);
}

.course-card:hover .course-title {
  color: var(--role-primary);
}

.course-meta {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-icon {
  font-size: var(--text-sm);
  flex-shrink: 0;
}

.course-meta .separator {
  color: var(--el-border-color);
  margin: 0 2px;
}

.course-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: var(--space-2);
  border-top: 1px solid var(--el-fill-color-lighter);
}

.rating {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
}

.rating-star {
  color: var(--el-color-warning);
  font-size: var(--text-md);
}

.rating-value {
  color: var(--el-text-color-primary);
  font-weight: var(--weight-semibold);
}

.rating-count {
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
}

.rating-none {
  color: var(--el-text-color-placeholder);
  font-size: var(--text-xs);
}

.price {
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  color: var(--role-primary);
  letter-spacing: var(--tracking-tight);
  transition: transform var(--duration-base) var(--ease-out);
}

.course-card:hover .price {
  transform: scale(1.05);
}

.price--free {
  font-size: var(--text-md);
  color: var(--el-color-success);
}

/* ================================================
   Pagination
   ================================================ */
.pagination-wrap {
  margin-top: var(--space-5);
  display: flex;
  justify-content: center;
}

.course-pagination {
  --el-pagination-bg-color: transparent;
  padding: var(--space-3) var(--space-4);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

/* ================================================
   State Block (Error/Empty)
   ================================================ */
.state-block {
  padding: var(--space-9) 0;
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.state-icon {
  color: var(--el-text-color-placeholder);
  opacity: 0.8;
}

.state-icon--error {
  color: var(--el-color-danger);
  opacity: 0.7;
}

.state-detail {
  margin: var(--space-3) 0 var(--space-5);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.state-action {
  min-width: 120px;
}

/* ================================================
   Sidebar
   ================================================ */
.sidebar {
  flex: 0 0 calc(25% - var(--space-5));
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  position: sticky;
  top: var(--space-5);
}

.sidebar-card {
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: var(--space-4);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.sidebar-card:hover {
  box-shadow: var(--shadow-md);
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-3);
  border-bottom: 1px solid var(--el-fill-color-lighter);
}

.sidebar-icon {
  color: var(--role-primary);
  font-size: var(--text-md);
}

.sidebar-title {
  margin: 0;
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.sidebar-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-2);
  border-radius: var(--radius-md);
  cursor: pointer;
  outline: none;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.sidebar-item:hover {
  background-color: var(--el-fill-color-lighter);
}

.sidebar-item:focus-visible {
  outline: 2px solid var(--role-primary);
  outline-offset: -2px;
}

.item-rank {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  transition: transform var(--duration-base) var(--ease-out);
}

.item-rank--top {
  background: linear-gradient(135deg, var(--role-primary), var(--role-primary-dark));
  color: var(--el-color-white);
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.3);
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-info--no-rank {
  padding-left: 0;
}

.item-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--duration-base) var(--ease-out);
}

.sidebar-item:hover .item-title {
  color: var(--role-primary);
}

.item-meta {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.sidebar-empty {
  padding: var(--space-4) 0;
}

/* ================================================
   Skeleton
   ================================================ */
.skeleton-card {
  background: var(--el-bg-color-overlay);
  box-shadow: var(--shadow-sm) !important;
}

.skeleton-cover {
  aspect-ratio: 16 / 9;
  background: var(--el-fill-color-lighter);
}

.skeleton-info {
  padding: var(--space-4);
}

.skeleton-title {
  height: 18px;
  width: 80%;
  margin-bottom: var(--space-3);
}

.skeleton-meta {
  height: 14px;
  width: 60%;
  margin-bottom: var(--space-2);
}

.skeleton-price {
  height: 18px;
  width: 30%;
}

/* ================================================
   Focus Ring (全局兜底)
   ================================================ */
:deep(button:focus-visible),
:deep(input:focus-visible),
:deep([tabindex]:focus-visible) {
  outline: 2px solid var(--role-primary);
  outline-offset: 2px;
}

/* ================================================
   Reduced Motion
   ================================================ */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}
</style>
