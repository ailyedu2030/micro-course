<!--
  课程广场 (H5 响应式)
  路由: /student/courses
  规范: .playwright-mcp/course-square-spec/{01-IA,02-VISUAL,03-COMPONENTS,04-CHECKLIST}.md
  设计: ui-ux-pro-max 技能 + DESIGN.md v1.1
  角色: STUDENT → 主色 #6366f1
-->
<template>
  <div class="course-square fade-in">
<!-- ============ 面包屑 ============ -->
    <nav class="page-breadcrumb" aria-label="面包屑">
      <el-icon><HomeFilled /></el-icon>
      <span class="bc-sep">/</span>
      <span class="bc-current">课程广场</span>
    </nav>

    <!-- ============ Hero ============ -->
    <section class="hero-section" aria-label="课程发现">
      <div class="hero-decoration" aria-hidden="true">
        <div class="deco-circle deco-1" /><div class="deco-circle deco-2" />
        <div class="deco-circle deco-3" /><div class="deco-circle deco-4" />
        <div class="deco-circle deco-5" />
      </div>
      <div class="hero-noise" aria-hidden="true" />
      <div class="hero-shimmer" aria-hidden="true" />
      <div class="hero-content">
        <h1 class="hero-title">发现优质课程</h1>
        <p class="hero-subtitle">开启你的学习之旅</p>
      </div>
    </section>

    <!-- ============ 微专业专区 ============ -->
    <section class="micro-specialty-zone" aria-label="微专业专区">
      <div class="ms-zone-header">
        <div class="ms-zone-title-row">
          <h2 class="ms-zone-title">🎯 微专业 · 学校重点培养项目</h2>
          <el-button
            v-if="hasMSData"
            size="small"
            text
            type="primary"
            class="ms-view-all-btn"
            @click="showAllMS = true; fetchAllMS()"
          >
            查看更多 →
          </el-button>
        </div>
        <p class="ms-zone-desc">修读多门课程获得微专业结业证书</p>
      </div>

      <!-- Loading -->
      <div v-if="msLoading" class="ms-loading-row">
        <div v-for="n in 3" :key="n" class="ms-skeleton-card">
          <el-skeleton animated>
            <template #template>
              <div class="ms-skel-cover" />
              <div class="ms-skel-body">
                <el-skeleton-item variant="text" style="width: 70%; height: 18px;" />
                <el-skeleton-item variant="text" style="width: 50%; height: 14px; margin-top: 8px;" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <!-- Error -->
      <el-result
        v-else-if="msError" status="error" title="加载失败"
        sub-title="请检查网络后重试" class="ms-error"
      >
        <template #extra>
          <el-button type="primary" @click="fetchMicroSpecialties">重试</el-button>
        </template>
      </el-result>

      <!-- Empty -->
      <el-empty v-else-if="!hasMSData" class="ms-empty" description="暂无微专业项目" />

      <!-- Has Data -->
      <template v-else>
        <!-- 金标位 (isGoldFeatured=TRUE，最多 2 张) -->
        <div v-if="goldFeatured.length" class="ms-gold-row">
          <div
            v-for="item in goldFeatured" :key="'gold-'+item.id"
            class="ms-gold-card" role="button" tabindex="0"
            :aria-label="'微专业：' + item.title"
            @click="goMSDetail(item.id)"
            @keydown.enter="goMSDetail(item.id)"
          >
            <span class="gold-badge">🔥 学校重点推荐</span>
            <div class="gold-cover">
              <img
                v-if="item.coverUrl" :src="item.coverUrl"
                :alt="item.title" loading="lazy" class="gold-cover-img"
              />
              <div v-else class="gold-cover-placeholder">
                <el-icon :size="40"><Notebook /></el-icon>
              </div>
            </div>
            <div class="gold-info">
              <h3 class="gold-title">{{ item.title }}</h3>
              <p class="gold-meta">{{ item.departmentName }} · {{ item.leadTeacherName }}</p>
              <p class="gold-stats">{{ item.totalCredits || 0 }} 学分 · {{ item.courseCount || 0 }} 门课</p>
              <el-button type="primary" size="small" round class="gold-cta">立即了解 →</el-button>
            </div>
          </div>
        </div>

        <!-- 常规位横滑 -->
        <div v-if="featured.length" class="ms-scroll-wrap">
          <div class="ms-scroll">
            <div
              v-for="item in featured" :key="'feat-'+item.id"
              class="ms-card" role="button" tabindex="0"
              :aria-label="'微专业：' + item.title"
              @click="goMSDetail(item.id)"
              @keydown.enter="goMSDetail(item.id)"
            >
              <div class="ms-card-cover">
                <img
                  v-if="item.coverUrl" :src="item.coverUrl"
                  :alt="item.title" loading="lazy" class="ms-cover-img"
                />
                <div v-else class="ms-cover-placeholder">
                  <el-icon :size="28"><Notebook /></el-icon>
                </div>
                <span v-if="item.isNew" class="ms-new-badge">NEW</span>
              </div>
              <div class="ms-card-body">
                <h4 class="ms-card-title">{{ item.title }}</h4>
                <p class="ms-card-dept">{{ item.departmentName }}</p>
                <div v-if="item.qualityScore !== undefined" class="ms-quality-bar">
                  <div class="ms-quality-fill" :style="{ width: Math.min(item.qualityScore * 10, 100) + '%' }" />
                </div>
                <p class="ms-card-credits">{{ item.totalCredits || 0 }} 学分 · {{ item.courseCount || 0 }} 门课</p>
              </div>
            </div>
          </div>
        </div>
      </template>
    </section>

    <!-- 全部微专业弹窗 -->
    <el-dialog
      v-model="showAllMS"
      title="全部微专业"
      width="900px"
      :close-on-click-modal="true"
      class="ms-all-dialog"
    >
      <div v-loading="allMSLoading" class="ms-all-grid">
        <div
          v-for="item in allMSList"
          :key="'all-ms-' + item.id"
          class="ms-all-card"
          role="button"
          tabindex="0"
          @click="showAllMS = false; goMSDetail(item.id)"
          @keydown.enter="showAllMS = false; goMSDetail(item.id)"
        >
          <div class="ms-all-cover">
            <img
              v-if="item.coverUrl"
              :src="item.coverUrl"
              :alt="item.title"
              loading="lazy"
              class="ms-all-cover-img"
            />
            <div v-else class="ms-all-cover-placeholder">
              <el-icon :size="24"><Notebook /></el-icon>
            </div>
          </div>
          <div class="ms-all-info">
            <h4 class="ms-all-title">{{ item.title }}</h4>
            <p class="ms-all-dept">{{ item.departmentName || '—' }}</p>
            <p class="ms-all-stats">{{ item.totalCredits || 0 }} 学分 · {{ item.courseCount || 0 }} 门课</p>
          </div>
        </div>
      </div>
      <el-empty v-if="!allMSLoading && !allMSList.length" description="暂无微专业" />
    </el-dialog>

    <!-- ============ 筛选卡 (悬浮在 Hero 下方) ============ -->
    <div class="filter-bar">
      <el-card class="filter-card" shadow="never">
        <div class="filter-row">
          <el-input
v-model="searchForm.keyword" placeholder="搜索课程名称或教师" clearable
            class="keyword-input" aria-label="搜索关键词" @keyup.enter="handleSearch"
>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select
v-model="searchForm.difficulty" placeholder="全部难度" clearable
            class="difficulty-select" aria-label="难度筛选" @change="handleSearch"
>
            <el-option label="全部难度" value="" />
            <el-option label="初级" :value="1" />
            <el-option label="中级" :value="2" />
            <el-option label="高级" :value="3" />
          </el-select>
          <div class="category-scroll" v-loading="categoriesLoading">
            <el-radio-group
v-model="selectedCategoryId" class="category-chip-group"
              aria-label="课程分类" @change="handleCategoryChange"
>
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button v-for="cat in categoryList" :key="cat.id" :value="cat.id">
                {{ cat.name }}
              </el-radio-button>
            </el-radio-group>
          </div>
          <el-button
type="primary" :icon="Search" :loading="loading" class="search-btn"
            :aria-label="loading ? '搜索中，请稍候' : '搜索课程'" @click="handleSearch"
>
            搜索
          </el-button>
          <el-button type="default" :icon="RefreshRight" class="reset-btn" @click="handleReset">
            重置
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- ============ Section 精选推荐 ============ -->
    <section v-show="recommendedCourses.length > 0" class="section">
      <header class="section-header">
        <h2 class="section-title">精选推荐</h2>
      </header>
      <div class="course-grid">
        <el-row :gutter="24">
          <el-col
v-for="(course, rIndex) in recommendedCourses" :key="'rec-'+course.id"
            :xs="24" :sm="12" :md="8" :lg="6"
>
            <article
class="course-card" :style="{ '--card-index': rIndex }"
              role="button" tabindex="0" :aria-label="`推荐课程 ${course.title}`"
              @click="handleCourseClick(course.id)" @keydown.enter="handleCourseClick(course.id)"
>
              <div class="course-cover">
                <div class="cover-placeholder" aria-hidden="true"><el-icon :size="48"><VideoPlay /></el-icon></div>
                <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" loading="lazy" class="cover-img" @error="handleImgError" />
                <span class="recommend-badge">推荐</span>
                <span v-if="course.difficulty" class="difficulty-label" :class="'difficulty-label--' + getDifficultyType(course.difficulty)">{{ getDifficultyLabel(course.difficulty) }}</span>
              </div>
              <div class="course-info">
                <h3 class="course-title" :title="course.title">{{ course.title }}</h3>
                <p class="course-meta"><el-icon class="meta-icon"><User /></el-icon><span>{{ course.teacherName || '未知教师' }}</span><span class="sep">·</span><span>{{ formatStudentCount(course.studentCount) }}</span></p>
                <div class="course-footer">
                  <div class="rating"><el-icon class="rating-star"><Star /></el-icon><span class="rating-value">{{ formatRating(course.avgRating) }}</span></div>
                  <div class="price" :class="{ 'price--free': !course.price }">{{ course.price ? `¥${course.price}` : '免费' }}</div>
                </div>
              </div>
            </article>
          </el-col>
        </el-row>
      </div>
    </section>

    <!-- ============ Section 课程套件 ============ -->
    <section v-if="bundles.length > 0" class="section">
      <header class="section-header">
        <h2 class="section-title">课程套件</h2>
        <el-button text type="primary" @click="goBundles">查看全部 →</el-button>
      </header>
      <div class="bundle-scroll">
        <div
v-for="b in bundles" :key="b.id" class="bundle-chip" tabindex="0" role="button"
          :aria-label="'课程套件：' + b.title" @click="goBundle(b.id)"
          @keydown.enter="goBundle(b.id)" @keydown.space.prevent="goBundle(b.id)"
>
          <div class="b-chip-icon"><el-icon :size="20"><FolderOpened /></el-icon></div>
          <div class="b-chip-info">
            <span class="b-chip-title">{{ b.title }}</span>
            <span class="b-chip-price" :class="{ free: b.isFree || !b.price }">{{ b.isFree || !b.price ? '免费' : '¥' + b.price }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Section 全部课程 (含 Loading / Error / Empty / Grid) ============ -->
    <section class="section">
      <header class="section-header">
        <h2 class="section-title">全部课程</h2>
      </header>

      <!-- Loading -->
      <div v-if="loading" class="course-grid" aria-label="加载中">
        <el-row :gutter="24">
          <el-col v-for="n in 8" :key="n" :xs="24" :sm="12" :md="8" :lg="8">
            <el-card class="course-card skeleton-card" shadow="never" :style="{ animationDelay: `${(n - 1) * 0.08}s` }">
              <el-skeleton animated>
                <template #template>
                  <div class="skeleton-cover" />
                  <div class="skeleton-info">
                    <el-skeleton-item variant="text" class="sk-title" />
                    <el-skeleton-item variant="text" class="sk-meta" />
                    <el-skeleton-item variant="text" class="sk-price" />
                  </div>
                </template>
              </el-skeleton>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- Error -->
      <el-empty v-else-if="error" class="state-block" description="课程加载失败">
        <template #image><el-icon :size="64" class="state-icon state-icon--error"><WarningFilled /></el-icon></template>
        <p class="state-detail">网络连接异常，请稍后重试</p>
        <el-button type="primary" :icon="Refresh" class="state-action" @click="fetchCourses">重新加载</el-button>
      </el-empty>

      <!-- Empty (无课程) -->
      <el-empty v-else-if="courseList.length === 0 && !isSearchActive" class="state-block" description="暂无课程">
        <template #image><el-icon :size="64" class="state-icon"><Notebook /></el-icon></template>
        <p class="state-detail">换个分类或筛选条件试试</p>
        <el-button type="primary" class="state-action" @click="handleReset">重置筛选</el-button>
      </el-empty>

      <!-- Empty (无搜索结果) -->
      <el-empty
v-else-if="courseList.length === 0 && isSearchActive" class="state-block"
        :description="`未找到与 '${searchForm.keyword || '当前筛选'}' 相关的课程`"
      >
        <template #image><el-icon :size="64" class="state-icon"><Search /></el-icon></template>
        <p class="state-detail">未找到匹配项，尝试更换筛选条件</p>
        <el-button type="primary" class="state-action" @click="handleReset">清除筛选</el-button>
        <!-- 搜索无结果时展示热门课程推荐 -->
        <div v-if="hotCourses.length > 0" class="search-recommend-section">
          <p class="recommend-hint">你可能感兴趣的课程：</p>
          <div class="search-recommend-grid">
            <div
              v-for="course in hotCourses.slice(0, 4)"
              :key="course.id"
              class="search-recommend-item"
              role="button"
              tabindex="0"
              @click="handleCourseClick(course.id)"
            >
              <div class="sr-cover">
                <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" loading="lazy" class="sr-cover-img" />
                <div v-else class="sr-cover-placeholder"><el-icon :size="24"><VideoPlay /></el-icon></div>
              </div>
              <div class="sr-info">
                <span class="sr-title">{{ course.title }}</span>
                <span class="sr-meta">{{ course.studentCount || 0 }}人在学</span>
              </div>
            </div>
          </div>
        </div>
      </el-empty>

      <!-- Normal Grid -->
      <div v-else class="course-grid">
        <el-row :gutter="24">
          <el-col v-for="(course, cIndex) in courseList" :key="course.id" :xs="24" :sm="12" :md="8" :lg="8">
            <article
class="course-card" :style="{ '--card-index': cIndex }" role="button" tabindex="0"
              :aria-label="`课程 ${course.title}，教师 ${course.teacherName || '未知'}，${course.studentCount || 0} 人学习`"
              @click="handleCourseClick(course.id)" @keydown.enter="handleCourseClick(course.id)" @keydown.space.prevent="handleCourseClick(course.id)"
>
              <div class="course-cover">
                <div class="cover-placeholder" aria-hidden="true"><el-icon :size="48"><VideoPlay /></el-icon></div>
                <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" loading="lazy" class="cover-img" @error="handleImgError" />
                <el-tag v-if="course.categoryName" class="category-chip" type="info" effect="plain" size="small">{{ course.categoryName }}</el-tag>
                <span v-if="course.difficulty" class="difficulty-label" :class="'difficulty-label--' + getDifficultyType(course.difficulty)">{{ getDifficultyLabel(course.difficulty) }}</span>
                <span
v-if="getCardTypeConfig(course.courseType)" class="course-type-badge"
                  :style="{ background: getCardTypeConfig(course.courseType).typeColor }"
>{{ getCardTypeConfig(course.courseType).typeLabel }}</span>
              </div>
              <div class="course-info">
                <h3 class="course-title" :title="course.title">{{ course.title }}</h3>
                <p class="course-meta"><el-icon class="meta-icon"><User /></el-icon><span>{{ course.teacherName || '未知教师' }}</span><span class="sep">·</span><span>{{ course.categoryName || '未分类' }}</span><span class="sep">·</span><span>{{ formatStudentCount(course.studentCount) }}</span></p>
                <div class="course-footer">
                  <div class="rating" :aria-label="`评分 ${formatRating(course.avgRating)}`">
                    <el-icon class="rating-star"><Star /></el-icon>
                    <span class="rating-value">{{ formatRating(course.avgRating) }}</span>
                    <span class="rating-count" v-if="course.ratingCount">({{ course.ratingCount }})</span>
                    <span class="rating-none" v-else>暂无评分</span>
                  </div>
                  <div class="price" :class="{ 'price--free': !course.price }">{{ course.price ? `¥${course.price}` : '免费' }}</div>
                </div>
              </div>
            </article>
          </el-col>
        </el-row>
        <div v-if="totalElements > 0" class="pagination-wrap">
          <el-pagination
v-model:current-page="page" v-model:page-size="size" :total="totalElements"
            :page-sizes="[12, 24, 48]" :disabled="loading" layout="total, sizes, prev, pager, next, jumper"
            background class="course-pagination" @size-change="handleSizeChange"
            @current-change="handlePageChange" aria-label="分页导航"
/>
        </div>
      </div>
    </section>

    <!-- ============ Section 热门课程 ============ -->
    <section v-show="hotCourses.length > 0" class="section">
      <header class="section-header">
        <h2 class="section-title">热门课程</h2>
        <el-button text type="primary" :icon="TrendCharts">查看全部 →</el-button>
      </header>
      <div class="hot-scroll">
        <div
v-for="(course, index) in hotCourses" :key="course.id" class="hot-card" role="button" tabindex="0"
          :style="{ '--hot-index': index }" :aria-label="`热门第 ${index + 1} 名：${course.title}`"
          @click="handleCourseClick(course.id)" @keydown.enter="handleCourseClick(course.id)"
>
          <div class="course-cover hot-card-cover">
            <div class="cover-placeholder" aria-hidden="true"><el-icon :size="28"><VideoPlay /></el-icon></div>
            <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" loading="lazy" class="cover-img" @error="handleImgError" />
            <span class="hot-rank" :class="{ 'hot-rank--top': index < 3 }">{{ index + 1 }}</span>
          </div>
          <div class="course-info">
            <h3 class="course-title" :title="course.title">{{ course.title }}</h3>
            <p class="course-meta"><el-icon class="meta-icon"><User /></el-icon><span>{{ formatStudentCount(course.studentCount) }}</span></p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ Section 最新课程 ============ -->
    <section v-show="newestCourses.length > 0" class="section">
      <header class="section-header">
        <h2 class="section-title">最新课程</h2>
      </header>
      <div class="newest-scroll">
        <div
v-for="(course, nIndex) in newestCourses" :key="course.id" class="newest-chip" role="button" tabindex="0"
          :aria-label="`最新课程：${course.title}`" @click="handleCourseClick(course.id)"
          @keydown.enter="handleCourseClick(course.id)"
>
          <div class="newest-chip-icon"><el-icon :size="18"><VideoPlay /></el-icon></div>
          <div class="newest-chip-info">
            <span class="newest-chip-title">{{ course.title }}</span>
            <span class="newest-chip-date">{{ formatDate(course.createdAt) }}</span>
          </div>
        </div>
      </div>
    </section>
</div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, onUnmounted } from 'vue'
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
  WarningFilled,
  Notebook,
  HomeFilled
} from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getCategories } from '@/api/course-category'
import { getBundles } from '@/api/bundle'
import { getSquareData, getMicroSpecialtyList } from '@/api/microSpecialty'
import { usePluginStore } from '@/store/plugins'

const router = useRouter()
const pluginStore = usePluginStore()

// 状态
const loading = ref(false)
const error = ref(false)
const categoriesLoading = ref(false)
const courseList = ref([])
const categoryList = ref([])
const hotCourses = ref([])
const newestCourses = ref([])
const recommendedCourses = ref([])
const bundles = ref([])
// 微专业专区
const msLoading = ref(false)
const msError = ref(false)
const goldFeatured = ref([])
const featured = ref([])
const recruiting = ref([])
const hasMSData = computed(() => goldFeatured.value.length > 0 || featured.value.length > 0)
// 全部微专业弹窗
const showAllMS = ref(false)
const allMSList = ref([])
const allMSLoading = ref(false)
const totalElements = ref(0)
const page = ref(1)
const size = ref(12)
const selectedCategoryId = ref('')

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

const getCardTypeConfig = (courseType) => {
  if (!courseType || courseType === 'VIDEO') return null
  return pluginStore.getCourseCardConfig(courseType)
}

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
  categoriesLoading.value = true
  try {
    const { data } = await getCategories({ size: 100 })
    categoryList.value = data?.items || data || []
  } catch (e) {
    console.warn('[CourseSquare] 分类加载失败:', e)
    ElMessage.warning('分类加载失败')
  } finally {
    categoriesLoading.value = false
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
      status: 2
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (selectedCategoryId.value) params.categoryId = selectedCategoryId.value
    if (searchForm.difficulty) params.difficulty = searchForm.difficulty

    const { data } = await getCourses(params)
    courseList.value = data?.items || []
    totalElements.value = data?.totalElements || 0
  } catch (e) {
    console.error('[CourseSquare] 课程加载失败:', e)
    error.value = true
    ElMessage.error('课程加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 拉侧栏数据（热门 + 最新）
const fetchSideCourses = async () => {
  try {
    const [hotRes, newestRes] = await Promise.all([
      getCourses({ page: 0, size: 5, sortBy: 'studentCount', sortOrder: 'desc', status: 2 }),
      getCourses({ page: 0, size: 5, sortBy: 'createdAt', sortOrder: 'desc', status: 2 })
    ])
    hotCourses.value = (hotRes.data?.items || []).slice(0, 5)
    newestCourses.value = (newestRes.data?.items || []).slice(0, 5)
  } catch (e) {
    console.warn('[CourseSquare] 侧栏数据加载失败:', e)
    ElMessage.warning('侧栏数据加载失败')
  }
}

// 拉精选推荐
const loadRecommended = async () => {
  try {
    const { data } = await getCourses({ recommended: true, size: 8, status: 2 })
    recommendedCourses.value = data?.items || []
  } catch (e) {
    console.warn('[CourseSquare] 推荐课程加载失败:', e)
    recommendedCourses.value = []
    ElMessage.warning('推荐课程加载失败')
  }
}

// 搜索
const handleSearch = () => {
  // 清除防抖 timer，防止 Enter 和 watch 双重触发
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    debounceTimer = null
  }
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

const goBundle = (id) => router.push(`/student/bundles/${id}`)
const goBundles = () => router.push('/student/bundles')

// 微专业专区 — 数据拉取
const fetchMicroSpecialties = async () => {
  msLoading.value = true
  msError.value = false
  try {
    const { data } = await getSquareData()
    goldFeatured.value = data?.goldFeatured || []
    featured.value = data?.featured || []
    recruiting.value = data?.recruiting || []
  } catch (e) {
    console.error('[CourseSquare] 微专业加载失败:', e)
    msError.value = true
  } finally {
    msLoading.value = false
  }
}

const goMSDetail = (id) => {
  if (!id) return
  router.push(`/student/micro-specialties/${id}`)
}

const fetchAllMS = async () => {
  allMSLoading.value = true
  try {
    const { data } = await getMicroSpecialtyList({ page: 0, size: 50, status: 'OPEN' })
    allMSList.value = data?.items || []
  } catch (e) {
    console.warn('[CourseSquare] 全部微专业加载失败:', e)
    allMSList.value = []
  } finally {
    allMSLoading.value = false
  }
}

const loadBundles = async () => {
  try {
    const { data } = await getBundles({ size: 6 })
    bundles.value = data.items || []
  } catch (e) {
    console.warn('[CourseSquare] 加载套餐列表失败', e)
    ElMessage.warning('推荐课程加载失败')
  }
}

// 封面图加载失败兜底：隐藏 img，露出底层占位符
const handleImgError = (e) => {
  e.target.style.display = 'none'
}

// 防抖搜索 (300ms)
let debounceTimer = null

watch(() => searchForm.keyword, (newVal, oldVal) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  // 清空时立即搜索；输入中时至少 2 字符才触发防抖
  if (!newVal) {
    debounceTimer = setTimeout(() => {
      page.value = 1
      fetchCourses()
    }, 100)
  } else if (newVal.length >= 1) {
    debounceTimer = setTimeout(() => {
      page.value = 1
      fetchCourses()
    }, 300)
  }
})

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})

onMounted(async () => {
  await Promise.all([fetchCategories(), fetchCourses()])
  fetchSideCourses()
  loadRecommended()
  loadBundles()
  fetchMicroSpecialties()
})
</script>

<style scoped>
/* ==========================================================================
   Container — 单一容器，所有 section 共享
   ========================================================================== */
.course-square {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-4) var(--space-6) var(--space-8);
  min-height: 100dvh;
}

/* ==========================================================================
   Breadcrumb
   ========================================================================== */
.page-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-3);
}
.page-breadcrumb .bc-sep { color: var(--el-text-color-placeholder); }
.page-breadcrumb .bc-current { color: var(--el-text-color-primary); font-weight: var(--weight-medium); }

/* ==========================================================================
   Hero — 240px · 径向渐变装饰 · 浮动动画 · 光泽扫描
   ========================================================================== */
.hero-section {
  position: relative;
  height: 240px;
  border-radius: var(--radius-2xl);
  background:
    radial-gradient(ellipse 80% 60% at 20% 80%, color-mix(in srgb, var(--role-primary-light-3) 15%, transparent) 0%, transparent 100%),
    radial-gradient(ellipse 60% 50% at 80% 20%, color-mix(in srgb, var(--el-color-white) 10%, transparent) 0%, transparent 100%),
    linear-gradient(155deg, var(--role-primary) 0%, var(--role-primary-dark) 40%, var(--role-primary-darkest) 100%);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  box-shadow: 0 8px 32px color-mix(in srgb, var(--role-primary) 20%, transparent), inset 0 1px 0 color-mix(in srgb, var(--el-color-white) 15%, transparent);
}
.hero-decoration { position: absolute; inset: 0; pointer-events: none; }
.hero-noise {
  position: absolute; inset: 0; pointer-events: none; opacity: 0.03;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 256px 256px;
}
.hero-shimmer {
  position: absolute; inset: 0; pointer-events: none;
  background: linear-gradient(105deg, transparent 40%, color-mix(in srgb, var(--el-color-white) 6%, transparent) 45%, transparent 50%);
  background-size: 200% 100%; animation: shimmer-sweep 6s ease-in-out infinite;
}
@keyframes shimmer-sweep { 0%,100% { background-position: 100% 0; } 50% { background-position: 0% 0; } }
.deco-circle { position: absolute; border-radius: 50%; }
.deco-1 { width: 320px; height: 320px; top: -100px; right: -80px; background: radial-gradient(circle, color-mix(in srgb, var(--el-color-white) 10%, transparent) 0%, transparent 70%); animation: float-drift 8s ease-in-out infinite; }
.deco-2 { width: 200px; height: 200px; bottom: -70px; left: 10%; background: radial-gradient(circle, color-mix(in srgb, var(--el-color-white) 6%, transparent) 0%, transparent 70%); animation: float-drift 10s ease-in-out infinite reverse; }
.deco-3 { width: 120px; height: 120px; top: 15%; right: 15%; background: radial-gradient(circle, color-mix(in srgb, var(--el-color-white) 7%, transparent) 0%, transparent 70%); animation: float-drift 6s ease-in-out infinite 1s; }
.deco-4 { width: 80px; height: 80px; bottom: 25%; left: 25%; background: radial-gradient(circle, color-mix(in srgb, var(--el-color-white) 5%, transparent) 0%, transparent 70%); animation: float-drift 7s ease-in-out infinite 2s; }
.deco-5 { width: 40px; height: 40px; top: 60%; right: 35%; background: radial-gradient(circle, color-mix(in srgb, var(--el-color-white) 8%, transparent) 0%, transparent 70%); animation: float-drift 9s ease-in-out infinite .5s; }
@keyframes float-drift { 0%,100% { transform: translate(0,0); } 25% { transform: translate(6px,-8px); } 50% { transform: translate(-4px,-12px); } 75% { transform: translate(8px,-4px); } }
.hero-content { position: relative; z-index: 1; text-align: center; color: var(--el-color-white); }
.hero-title { margin: 0; font-size: 40px; font-weight: var(--weight-bold); letter-spacing: var(--tracking-tight); line-height: 1.15; text-wrap: balance; }
.hero-subtitle { margin: var(--space-3) 0 0; font-size: var(--text-lg); opacity: .8; letter-spacing: var(--tracking-wide); font-weight: var(--weight-regular); }

/* ==========================================================================
   Filter Bar — 悬浮在 Hero 下方
   ========================================================================== */
.filter-bar { margin: calc(var(--space-9) * -1) 0 0; position: relative; z-index: 10; }
.filter-card {
  border-radius: var(--radius-xl) !important; border: none !important;
  background: rgba(255,255,255,.88) !important;
  backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  box-shadow: var(--shadow-tinted-md) !important;
  transition: box-shadow var(--duration-base) var(--ease-out), transform var(--duration-base) var(--ease-out);
}
.filter-card:hover { box-shadow: var(--shadow-tinted-lg), var(--shadow-md) !important; transform: translateY(-1px); }
.filter-card :deep(.el-card__body) { padding: var(--space-4) var(--space-6); }
.filter-row { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.keyword-input { width: 240px; flex-shrink: 0; }
.difficulty-select { width: 140px; flex-shrink: 0; }
.category-scroll { flex: 1; min-width: 200px; overflow-x: auto; -webkit-overflow-scrolling: touch; }
.category-scroll::-webkit-scrollbar { height: var(--space-1); }
.category-chip-group { display: flex; gap: var(--space-1); white-space: nowrap; }
.category-chip-group :deep(.el-radio-button__inner) {
  border-radius: var(--radius-pill) !important; padding: var(--space-2) var(--space-4);
  border: 1px solid var(--el-border-color) !important; box-shadow: none !important;
  transition: all var(--duration-base) var(--ease-out);
  font-weight: var(--weight-medium); font-size: var(--text-sm);
}
.category-chip-group :deep(.el-radio-button__inner:hover) { background: var(--role-primary-light-9); border-color: var(--role-primary-light-5) !important; color: var(--role-primary); }
.category-chip-group :deep(.el-radio-button.is-active .el-radio-button__inner) { background: var(--role-primary) !important; border-color: var(--role-primary) !important; color: #fff !important; box-shadow: var(--shadow-primary-soft) !important; }
.search-btn, .reset-btn { flex-shrink: 0; border-radius: var(--radius-md); transition: all var(--duration-base) var(--ease-out); }
.search-btn { background: linear-gradient(135deg, var(--role-primary), var(--role-primary-dark)); border: none; }
.search-btn:hover { background: linear-gradient(135deg, var(--role-primary-dark), var(--role-primary-darker)); transform: translateY(-1px); box-shadow: var(--shadow-primary); }
.search-btn:active { transform: translateY(0) scale(.97); }
.reset-btn:hover { border-color: var(--role-primary); color: var(--role-primary); }
.reset-btn:active { transform: scale(.97); }

/* ==========================================================================
   Section — 统一所有内容区
   ========================================================================== */
.section { margin-top: var(--space-7); }
.section + .section { margin-top: var(--space-6); }
.section-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: var(--space-4);
}

.section-title {
  font-size: var(--text-2xl); font-weight: var(--weight-bold);
  color: var(--el-text-color-primary); letter-spacing: var(--tracking-tight);
  text-wrap: balance; display: flex; align-items: center; gap: var(--space-3); margin: 0;
}
.section-title::before {
  content: ''; width: 5px; height: 22px;
  background: linear-gradient(180deg, var(--role-primary), var(--role-primary-dark));
  border-radius: var(--radius-pill); flex-shrink: 0;
}

/* ==========================================================================
   Course Grid & Card — 跨推荐/全部/热门复用
   ========================================================================== */
.course-grid { min-height: 400px; }
.course-card {
  border-radius: var(--radius-lg); background: var(--el-bg-color-overlay);
  box-shadow: var(--shadow-tinted-sm); margin-bottom: var(--space-4);
  overflow: hidden; cursor: pointer; outline: none; position: relative;
  transition: transform var(--duration-slow) var(--ease-out), box-shadow var(--duration-slow) var(--ease-out);
  animation: card-enter .6s var(--ease-out) both;
  animation-delay: calc(var(--card-index, 0) * .06s);
}
@keyframes card-enter { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }
.course-card::before {
  content: ''; position: absolute; inset: 0; border-radius: var(--radius-lg);
  pointer-events: none; z-index: 5; opacity: 0;
  transition: opacity var(--duration-slow) var(--ease-out);
  background: linear-gradient(135deg, color-mix(in srgb, var(--role-primary) 4%, transparent) 0%, transparent 50%, color-mix(in srgb, var(--role-primary-light-5) 3%, transparent) 100%);
}
.course-card:hover { transform: translateY(-6px); box-shadow: var(--shadow-tinted-lg), var(--shadow-xl); }
.course-card:hover::before { opacity: 1; }
.course-card:focus-visible { outline: 2px solid var(--role-primary); outline-offset: 2px; }
.course-card:active { transform: translateY(-3px) scale(.98); }

.course-cover {
  position: relative; width: 100%; aspect-ratio: 16 / 9; overflow: hidden;
  background: linear-gradient(145deg, var(--role-primary-light-9), var(--role-primary-light-7));
}
.course-cover::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(180deg, transparent 40%, rgba(0,0,0,.06) 100%);
  z-index: 2; pointer-events: none; transition: opacity var(--duration-slow) var(--ease-out);
}
.course-card:hover .course-cover::after { opacity: 0; }
.cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; z-index: 1; transition: transform var(--duration-slow) var(--ease-out); }
.course-card:hover .cover-img { transform: scale(1.08); }
.cover-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--role-primary); opacity: .4; z-index: 1; }

.recommend-badge {
  position: absolute; top: var(--space-3); right: var(--space-3);
  padding: 3px 10px; font-size: 11px; font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wide); border-radius: var(--radius-sm); z-index: 2; line-height: 1.4;
  background: linear-gradient(135deg, var(--el-color-warning), var(--el-color-warning-dark-2)); color: #fff;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--el-color-warning) 30%, transparent);
}
.course-type-badge {
  position: absolute; top: var(--space-3); right: var(--space-3);
  padding: 2px 8px; border-radius: 4px; font-size: 11px; color: #fff; z-index: 2; line-height: 1.4;
}
.difficulty-label {
  position: absolute; top: var(--space-3); left: var(--space-3);
  padding: 3px 10px; font-size: 11px; font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wide); border-radius: var(--radius-sm); z-index: 2; line-height: 1.4;
}
.difficulty-label--success { background: color-mix(in srgb, var(--el-color-success) 12%, transparent); color: var(--el-color-success); }
.difficulty-label--warning { background: color-mix(in srgb, var(--el-color-warning) 12%, transparent); color: var(--el-color-warning); }
.difficulty-label--danger { background: color-mix(in srgb, var(--el-color-danger) 12%, transparent); color: var(--el-color-danger); }
.difficulty-label--info { background: color-mix(in srgb, var(--el-color-info) 12%, transparent); color: var(--el-color-info); }
.category-chip {
  position: absolute; bottom: var(--space-3); left: var(--space-3);
  border: none !important; border-radius: var(--radius-sm) !important;
  font-weight: var(--weight-medium); font-size: 11px; padding: 2px 8px;
  background: rgba(255,255,255,.88); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 1px 3px rgba(0,0,0,.06); color: var(--el-text-color-regular); z-index: 2;
}

.course-info { padding: var(--space-4); }
.course-title {
  margin: 0 0 var(--space-2); font-size: var(--text-md); font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary); line-height: var(--leading-snug); height: 2.75em;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  transition: color var(--duration-base) var(--ease-out); text-wrap: pretty;
}
.course-card:hover .course-title { color: var(--role-primary); }
.course-meta {
  margin: 0 0 var(--space-3); font-size: var(--text-sm); color: var(--el-text-color-secondary);
  display: flex; align-items: center; gap: var(--space-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.meta-icon { font-size: var(--text-sm); flex-shrink: 0; }
.course-meta .sep { color: var(--el-border-color); margin: 0 2px; }
.course-footer {
  display: flex; align-items: center; justify-content: space-between;
  padding-top: var(--space-3); border-top: 1px solid var(--el-fill-color-lighter); margin-top: var(--space-1);
}
.rating { display: flex; align-items: center; gap: var(--space-1); font-size: var(--text-sm); font-variant-numeric: tabular-nums; }
.rating-star { color: var(--el-color-warning); font-size: var(--text-md); }
.rating-value { color: var(--el-text-color-primary); font-weight: var(--weight-semibold); }
.rating-count { color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.rating-none { color: var(--el-text-color-placeholder); font-size: var(--text-xs); }
.price { font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--role-primary); letter-spacing: var(--tracking-tight); transition: transform var(--duration-base) var(--ease-out); font-variant-numeric: tabular-nums; }
.course-card:hover .price { transform: scale(1.05); }
.price--free { font-size: var(--text-md); color: var(--el-color-success); font-weight: var(--weight-medium); }

/* ==========================================================================
   Bundle — 水平滚动卡片
   ========================================================================== */
.bundle-scroll { display: flex; gap: var(--space-4); overflow-x: auto; padding-bottom: var(--space-2); }
.bundle-chip {
  flex: 0 0 240px; display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4);
  background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); cursor: pointer;
  box-shadow: var(--shadow-tinted-sm); transition: all var(--duration-base) var(--ease-out);
}
.bundle-chip:hover { box-shadow: var(--shadow-tinted-lg), var(--shadow-md); transform: translateY(-3px); }
.b-chip-icon { width: 40px; height: 40px; border-radius: var(--radius-md); background: linear-gradient(135deg, var(--role-primary-light-9), var(--role-primary-light-7)); display: flex; align-items: center; justify-content: center; color: var(--role-primary); flex-shrink: 0; }
.b-chip-info { min-width: 0; }
.b-chip-title { display: block; font-size: var(--text-base); font-weight: var(--weight-medium); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.b-chip-price { font-size: var(--text-xs); color: var(--el-color-danger); font-weight: var(--weight-semibold); }
.b-chip-price.free { color: var(--el-color-success); }

/* ==========================================================================
   Hot — 水平滚动卡片 (复用 .course-cover / .course-info / .course-title)
   ========================================================================== */
.hot-scroll { display: flex; gap: var(--space-4); overflow-x: auto; padding-bottom: var(--space-3); scroll-snap-type: x mandatory; }
.hot-scroll::-webkit-scrollbar { height: 4px; }
.hot-scroll::-webkit-scrollbar-thumb { background: var(--role-primary-light-5); border-radius: var(--radius-pill); }
.hot-card {
  flex: 0 0 240px; scroll-snap-align: start;
  animation: hot-card-in .5s var(--ease-out) both;
  animation-delay: calc(var(--hot-index, 0) * .07s);
}
@keyframes hot-card-in { from { opacity: 0; transform: translateX(16px); } to { opacity: 1; transform: translateX(0); } }
.hot-card .course-cover { border-radius: var(--radius-lg) var(--radius-lg) 0 0; }
.hot-rank {
  position: absolute; top: var(--space-2); left: var(--space-2);
  width: 24px; height: 24px; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: var(--weight-bold); border-radius: var(--radius-sm); z-index: 2;
  background: rgba(255,255,255,.85); backdrop-filter: blur(4px); color: var(--el-text-color-secondary);
}
.hot-rank--top { background: linear-gradient(135deg, var(--role-primary), var(--role-primary-dark)); color: #fff; box-shadow: var(--shadow-primary); }

/* ==========================================================================
   Newest — 紧凑横滚条
   ========================================================================== */
.newest-scroll { display: flex; gap: var(--space-3); overflow-x: auto; padding-bottom: var(--space-2); scroll-snap-type: x mandatory; }
.newest-scroll::-webkit-scrollbar { height: 4px; }
.newest-chip {
  flex: 0 0 260px; display: flex; align-items: center; gap: var(--space-3);
  padding: var(--space-3) var(--space-4); background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg); cursor: pointer; outline: none;
  box-shadow: var(--shadow-tinted-sm); scroll-snap-align: start;
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}
.newest-chip:hover { transform: translateY(-2px); box-shadow: var(--shadow-tinted-lg), var(--shadow-md); }
.newest-chip:focus-visible { outline: 2px solid var(--role-primary); outline-offset: 2px; }
.newest-chip-icon { width: 36px; height: 36px; border-radius: var(--radius-md); background: var(--role-primary-light-9); display: flex; align-items: center; justify-content: center; color: var(--role-primary); flex-shrink: 0; }
.newest-chip-info { min-width: 0; flex: 1; }
.newest-chip-title { display: block; font-size: var(--text-sm); font-weight: var(--weight-medium); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.newest-chip-date { display: block; font-size: var(--text-xs); color: var(--el-text-color-placeholder); margin-top: 2px; }

/* ==========================================================================
   Pagination
   ========================================================================== */
.pagination-wrap { margin-top: var(--space-5); display: flex; justify-content: center; }
.course-pagination {
  --el-pagination-bg-color: transparent; padding: var(--space-3) var(--space-4);
  background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); box-shadow: var(--shadow-tinted-sm);
}

/* ==========================================================================
   State Block
   ========================================================================== */
.state-block { padding: var(--space-10) 0; background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); box-shadow: var(--shadow-tinted-sm); }
.state-icon { color: var(--el-text-color-placeholder); opacity: .6; }
.state-icon--error { color: var(--el-color-danger); opacity: .6; }
.state-detail { margin: var(--space-3) 0 var(--space-5); font-size: var(--text-sm); color: var(--el-text-color-secondary); line-height: var(--leading-relaxed); }
.state-action { min-width: 120px; border-radius: var(--radius-md); }

/* 搜索无结果推荐 */
.search-recommend-section {
  margin-top: var(--space-6);
  text-align: left;
  padding: 0 var(--space-6);
}
.recommend-hint {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin: 0 0 var(--space-3);
  text-align: center;
}
.search-recommend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--space-3);
}
.search-recommend-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-out);
}
.search-recommend-item:hover { background: var(--el-fill-color-lighter); }
.sr-cover {
  width: 64px;
  height: 40px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--role-primary-light-9), var(--role-primary-light-7));
}
.sr-cover-img { width: 100%; height: 100%; object-fit: cover; }
.sr-cover-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: var(--role-primary); opacity: .5;
}
.sr-info { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.sr-title { font-size: var(--text-sm); font-weight: var(--weight-medium); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sr-meta { font-size: var(--text-xs); color: var(--el-text-color-secondary); }

/* ==========================================================================
   Skeleton
   ========================================================================== */
.skeleton-card { background: var(--el-bg-color-overlay); box-shadow: var(--shadow-tinted-sm) !important; animation: sk-fade-in .6s var(--ease-out) both; transition: opacity var(--duration-slow) var(--ease-out), transform var(--duration-base) var(--ease-out); }
@keyframes sk-fade-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.skeleton-cover { aspect-ratio: 16 / 9; background: linear-gradient(110deg, var(--el-fill-color-lighter) 30%, var(--el-fill-color) 50%, var(--el-fill-color-lighter) 70%); background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.skeleton-info { padding: var(--space-4); }
.sk-title { height: 18px; width: 80%; margin-bottom: var(--space-3); border-radius: var(--radius-sm); }
.sk-meta { height: 14px; width: 60%; margin-bottom: var(--space-2); border-radius: var(--radius-sm); }
.sk-price { height: 18px; width: 30%; border-radius: var(--radius-sm); }

/* ==========================================================================
   Micro-Specialty Zone — 微专业专区
   渐变紫背景 · 金标主推卡 · 常规横滑卡
   ========================================================================== */
.micro-specialty-zone {
  margin: var(--space-7) 0 0;
  padding: 32px 40px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a78bfa 100%);
  border-radius: var(--radius-2xl);
  overflow: hidden;
  position: relative;
}
.ms-zone-header {
  margin-bottom: 24px;
  text-align: center;
}
.ms-zone-title-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
}
.ms-zone-title {
  font-size: 22px;
  font-weight: var(--weight-bold);
  color: #fff;
  margin: 0 0 8px;
  letter-spacing: var(--tracking-tight);
}
.ms-view-all-btn {
  color: rgba(255, 255, 255, 0.9) !important;
  font-weight: var(--weight-medium);
  flex-shrink: 0;
}
.ms-view-all-btn:hover {
  color: #fff !important;
  background: rgba(255, 255, 255, 0.12) !important;
}
.ms-zone-desc {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
  margin: 0;
  font-weight: var(--weight-regular);
}
/* Loading */
.ms-loading-row {
  display: flex;
  gap: 16px;
}
.ms-skeleton-card {
  flex: 1;
  background: rgba(255, 255, 255, 0.12);
  border-radius: var(--radius-lg);
  padding: 16px;
  min-height: 200px;
}
.ms-skel-cover {
  aspect-ratio: 16 / 9;
  background: rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
}
.ms-skel-body {
  padding: 0 4px;
}
/* Error / Empty */
.ms-error,
.ms-empty {
  padding: 40px 0;
}
.ms-empty :deep(.el-empty__description) {
  color: rgba(255, 255, 255, 0.7);
}
/* 金标主推卡 */
.ms-gold-row {
  display: flex;
  gap: 20px;
  margin-bottom: 24px;
}
.ms-gold-card {
  flex: 1;
  display: flex;
  gap: 20px;
  background: #fff;
  border-radius: var(--radius-xl);
  padding: 20px;
  cursor: pointer;
  outline: none;
  box-shadow: 0 4px 24px rgba(99, 102, 241, 0.25);
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
  position: relative;
  overflow: hidden;
  min-height: 200px;
}
.ms-gold-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border: 2px solid transparent;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, #f59e0b, #fbbf24) border-box;
  -webkit-mask: linear-gradient(#fff 0 0) padding-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  pointer-events: none;
}
.ms-gold-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.35);
}
.ms-gold-card:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}
.gold-badge {
  position: absolute;
  top: 8px;
  right: -8px;
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
  color: #fff;
  font-size: 12px;
  font-weight: var(--weight-bold);
  padding: 4px 16px 4px 12px;
  border-radius: 0 0 0 12px;
  z-index: 2;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.35);
  letter-spacing: var(--tracking-wide);
}
.gold-cover {
  width: 200px;
  min-width: 200px;
  height: 140px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
}
.gold-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--duration-slow) var(--ease-out);
}
.ms-gold-card:hover .gold-cover-img {
  transform: scale(1.05);
}
.gold-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a5b4fc;
}
.gold-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}
.gold-title {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gold-meta {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin: 0 0 4px;
}
.gold-stats {
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
  margin: 0 0 12px;
  font-weight: var(--weight-medium);
}
.gold-cta {
  align-self: flex-start;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: none;
}
.gold-cta:hover {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
}
/* 常规横滑卡 */
.ms-scroll-wrap {
  position: relative;
}
.ms-scroll {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scroll-behavior: smooth;
  padding-bottom: 4px;
}
.ms-scroll::-webkit-scrollbar {
  height: 4px;
}
.ms-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.3);
  border-radius: var(--radius-pill);
}
.ms-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.ms-card {
  flex: 0 0 248px;
  background: #fff;
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  outline: none;
  scroll-snap-align: start;
  box-shadow: 0 2px 12px rgba(99, 102, 241, 0.15);
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}
.ms-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.25);
}
.ms-card:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}
.ms-card-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
}
.ms-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--duration-slow) var(--ease-out);
}
.ms-card:hover .ms-cover-img {
  transform: scale(1.08);
}
.ms-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a5b4fc;
}
.ms-new-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #10b981;
  color: #fff;
  font-size: 11px;
  font-weight: var(--weight-bold);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  z-index: 2;
  letter-spacing: var(--tracking-wide);
}
.ms-card-body {
  padding: 12px 14px 14px;
}
.ms-card-title {
  margin: 0 0 4px;
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ms-card-dept {
  margin: 0 0 8px;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}
.ms-quality-bar {
  height: 4px;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 2px;
  margin-bottom: 8px;
  overflow: hidden;
}
.ms-quality-fill {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, #ef4444 0%, #f59e0b 40%, #22c55e 100%);
  transition: width var(--duration-slow) var(--ease-out);
}
.ms-card-credits {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  font-weight: var(--weight-medium);
}

/* 全部微专业弹窗 */
.ms-all-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 20px 24px 16px;
}
.ms-all-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  min-height: 200px;
  max-height: 60vh;
  overflow-y: auto;
  padding: 4px 0;
}
.ms-all-card {
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  outline: none;
  box-shadow: var(--shadow-tinted-sm);
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}
.ms-all-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-tinted-md);
}
.ms-all-cover {
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
}
.ms-all-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--duration-slow) var(--ease-out);
}
.ms-all-card:hover .ms-all-cover-img {
  transform: scale(1.05);
}
.ms-all-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a5b4fc;
}
.ms-all-info {
  padding: 12px 14px;
}
.ms-all-title {
  margin: 0 0 4px;
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ms-all-dept {
  margin: 0 0 4px;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}
.ms-all-stats {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--el-text-color-regular);
  font-weight: var(--weight-medium);
}

/* ==========================================================================
   Utilities
   ========================================================================== */
:deep(html) { scroll-behavior: smooth; }
:deep(button:focus-visible), :deep(input:focus-visible), :deep([tabindex]:focus-visible) { outline: 2px solid var(--role-primary); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { *,*::before,*::after { transition-duration: .01ms !important; animation-duration: .01ms !important; } }

/* ==========================================================================
   H5
   ========================================================================== */
@media (max-width: 768px) {
  /* 真 CSS columns 瀑布流：移动端取消 el-row 的 flex 等高栅格，
     改为多列流式布局，卡片按列从上到下填充，消除栅格空白 */
  .course-grid :deep(.el-row) {
    display: block;
    column-count: 2;
    column-gap: 12px;
    margin: 0 !important;
  }
  .course-grid :deep(.el-col) {
    display: inline-block;
    width: 100%;
    max-width: 100%;
    flex: none;
    break-inside: avoid;      /* 防止卡片被截断到下一列 */
    -webkit-column-break-inside: avoid;
    margin-bottom: 12px;
    padding: 0 !important;    /* 覆盖 el-col gutter padding，改用 column-gap 控制间距 */
  }
  .hot-card { flex: 0 0 200px; }
  .newest-chip { flex: 0 0 220px; }
}

@media (max-width: 480px) {
  /* 超小屏单列瀑布流 */
  .course-grid :deep(.el-row) { column-count: 1; }
}</style>
