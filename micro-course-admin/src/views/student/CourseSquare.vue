<!--
   课程广场 (H5 响应式) — 改版 v2
   路由: /student/courses
   参考: Coursera / Udemy 课程发现模式
 -->
<template>
  <div class="course-square fade-in">
<!-- ============ P0 闭环修复 Round 4: Banner 轮播（学生端首页） ============ -->
    <section v-if="banners.length > 0" class="banner-section" aria-label="运营 Banner">
      <el-carousel height="260px" trigger="click" arrow="always" indicator-position="outside" :interval="4000">
        <el-carousel-item v-for="banner in banners" :key="banner.id">
          <a
            :href="banner.linkUrl || '#'"
            class="banner-item"
            @click.prevent="handleBannerClick(banner)"
          >
            <img :src="banner.imageUrl" :alt="banner.title || 'banner'" class="banner-img" />
          </a>
        </el-carousel-item>
      </el-carousel>
    </section>

    <!-- ============ Hero + Search ============ -->
    <section class="hero-section" aria-label="课程发现">
      <div class="hero-content">
        <h1 class="hero-title">发现优质课程</h1>
        <p class="hero-subtitle">开启你的学习之旅</p>
        <div class="hero-search">
          <el-input
            v-model="searchForm.keyword" placeholder="搜索课程名称或教师" clearable
            class="hero-search-input" aria-label="搜索关键词"
            @keyup.enter="handleSearch"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
            <template #append>
              <el-button @click="handleSearch" :icon="Search">搜索</el-button>
            </template>
          </el-input>
        </div>
      </div>
    </section>

    <!-- ============ 分类 + 筛选 ============ -->
    <div class="filter-bar">
      <div class="filter-row">
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
        <el-button type="default" :icon="RefreshRight" size="small" class="reset-btn" @click="handleReset">重置</el-button>
      </div>
    </div>

    <!-- ============ 精选推荐 (横滑) — 仅在无搜索时显示,避免与搜索结果混淆 ============ -->
    <section v-if="recommendedCourses.length > 0 && !isSearchActive" class="section">
      <header class="section-header">
        <h2 class="section-title">精选推荐</h2>
      </header>
      <div class="rec-scroll-wrap">
        <div class="rec-scroll">
          <article
            v-for="(course, rIndex) in recommendedCourses" :key="'rec-'+course.id"
            class="rec-card" role="button" tabindex="0"
            :aria-label="`推荐课程 ${course.title}`"
            :style="{ '--rec-index': rIndex }"
            @click="handleCourseClick(course)" @keydown.enter="handleCourseClick(course)"
          >
            <div class="rec-cover">
              <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" loading="lazy" class="rec-cover-img" @error="handleImgError" />
              <div v-else class="rec-cover-placeholder"><el-icon :size="32"><VideoPlay /></el-icon></div>
              <span class="rec-badge">推荐</span>
            </div>
            <div class="rec-info">
              <h3 class="rec-title">{{ course.title }}</h3>
              <p class="rec-meta">{{ course.teacherName || '未知教师' }} · {{ formatStudentCount(course.studentCount) }}</p>
              <div class="rec-footer">
                <span class="rec-rating"><el-icon :size="12"><Star /></el-icon> {{ formatRating(course.avgRating) }}</span>
                <span class="rec-price" :class="{ 'rec-price--free': !displayPrice(course) }">{{ displayPrice(course) ? `¥${displayPrice(course)}` : '免费' }}</span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 全部课程 (含排序 Tab、加载、空态等) ============ -->
    <section class="section">
      <header class="section-header">
        <h2 class="section-title">全部课程</h2>
        <el-radio-group v-model="courseSort" size="small" class="sort-tabs">
          <el-radio-button value="">推荐</el-radio-button>
          <el-radio-button value="hot">热门</el-radio-button>
          <el-radio-button value="new">最新</el-radio-button>
        </el-radio-group>
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
              @click="handleCourseClick(course)"
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
              @click="handleCourseClick(course)" @keydown.enter="handleCourseClick(course)" @keydown.space.prevent="handleCourseClick(course)"
>
              <div class="course-cover">
                <div class="cover-placeholder" aria-hidden="true"><el-icon :size="48"><VideoPlay /></el-icon></div>
                <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" loading="lazy" class="cover-img" @error="handleImgError" />
                <el-tag v-if="course.categoryName" class="category-chip" type="info" effect="plain" size="small">{{ course.categoryName }}</el-tag>
                <el-tag v-if="course.freeAccessScopeLabel" class="free-chip" type="success" effect="dark" size="small">{{ course.freeAccessScopeLabel }}</el-tag>
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
                  <div class="price" :class="{ 'price--free': !displayPrice(course) || course.isFree }">
                    <template v-if="course.freeAccessScopeLabel">
                      <el-tag size="small" type="success" effect="light" class="free-tag">{{ course.freeAccessScopeLabel }}</el-tag>
                    </template>
                    <template v-else-if="course.isFree || !displayPrice(course)">免费</template>
                    <template v-else>¥{{ displayPrice(course) }}</template>
                  </div>
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

    <!-- ============ 课程套件 — 同样在搜索时隐藏 ============ -->
    <section v-if="bundles.length > 0 && !isSearchActive" class="section">
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

    <!-- ============ 微专业 (加载完成时显示,有数据/无数据/加载中三态) ============ -->
    <section v-if="!isSearchActive" class="section micro-specialty-section" aria-label="微专业">
      <header class="section-header">
        <h2 class="section-title">微专业</h2>
        <el-button v-if="hasMSData" text type="primary" @click="showAllMS = true; fetchAllMS()">查看更多 →</el-button>
      </header>
      <div v-if="msLoading" class="rec-scroll-wrap">
        <div class="ms-loading-row">
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
      </div>
      <el-result v-else-if="msError" status="error" title="加载失败" sub-title="请检查网络后重试">
        <template #extra>
          <el-button type="primary" @click="fetchMicroSpecialties">重试</el-button>
        </template>
      </el-result>
      <template v-else-if="hasMSData">
        <div v-if="goldFeatured.length" class="ms-gold-row">
          <div
v-for="item in goldFeatured" :key="'gold-'+item.id"
            class="ms-gold-card" role="button" tabindex="0"
            :aria-label="item.title" @click="goMSDetail(item.id)" @keydown.enter="goMSDetail(item.id)"
>
            <span class="gold-badge">学校重点推荐</span>
            <div class="gold-cover">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" class="gold-cover-img" />
              <div v-else class="gold-cover-placeholder"><el-icon :size="40"><Notebook /></el-icon></div>
            </div>
            <div class="gold-info">
              <h3 class="gold-title">{{ item.title }}</h3>
              <p class="gold-meta">{{ item.departmentName }} {{ item.leadTeacherName }}</p>
              <p class="gold-stats">{{ item.totalCredits || 0 }} 学分 | {{ item.courseCount || 0 }} 门课</p>
              <el-button type="primary" size="small" round class="gold-cta">立即了解</el-button>
            </div>
          </div>
        </div>
        <div v-if="featured.length" class="rec-scroll-wrap">
          <div class="rec-scroll">
            <div
v-for="item in featured" :key="'feat-'+item.id"
              class="ms-card" role="button" tabindex="0"
              :aria-label="item.title" @click="goMSDetail(item.id)" @keydown.enter="goMSDetail(item.id)"
>
              <div class="ms-card-cover">
                <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" class="ms-cover-img" />
                <div v-else class="ms-cover-placeholder"><el-icon :size="28"><Notebook /></el-icon></div>
                <span v-if="item.isNew" class="ms-new-badge">NEW</span>
              </div>
              <div class="ms-card-body">
                <h4 class="ms-card-title">{{ item.title }}</h4>
                <p class="ms-card-dept">{{ item.departmentName }}</p>
              </div>
            </div>
          </div>
        </div>
      </template>
      <!-- P1-C-12-03 fix: 无数据时显示空态文案,不再整段隐藏 -->
      <el-empty v-else description="暂无微专业项目，敬请期待" />
    </section>

    <!-- 全部微专业弹窗 -->
    <el-dialog
v-model="showAllMS" title="全部微专业" width="900px"
      :close-on-click-modal="true" class="ms-all-dialog"
>
      <div class="ms-all-filter-bar mg-bottom-12">
        <el-radio-group v-model="dialogFilter" size="small">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="RECRUITING">招生中</el-radio-button>
          <el-radio-button label="APPROVED">报名中</el-radio-button>
          <el-radio-button label="COMPLETED">已结业</el-radio-button>
        </el-radio-group>
      </div>
      <div v-loading="allMSLoading" class="ms-all-grid">
        <div
v-for="item in filteredAllMS" :key="'all-ms-' + item.id"
          class="ms-all-card" role="button" tabindex="0"
          @click="showAllMS = false; goMSDetail(item.id)" @keydown.enter="showAllMS = false; goMSDetail(item.id)"
>
          <div class="ms-all-cover">
            <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" class="ms-all-cover-img" />
            <div v-else class="ms-all-cover-placeholder"><el-icon :size="24"><Notebook /></el-icon></div>
          </div>
          <div class="ms-all-info">
            <h4 class="ms-all-title">{{ item.title }}</h4>
            <p class="ms-all-dept">{{ item.departmentName || '-' }}</p>
            <p class="ms-all-stats">{{ item.totalCredits || 0 }} 学分 | {{ item.courseCount || 0 }} 门课</p>
          </div>
        </div>
      </div>
      <el-empty v-if="!allMSLoading && !allMSList.length" description="暂无微专业" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, onUnmounted } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
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
import { getActiveBanners } from '@/api/bannerPublic'
import { getSquareData, getMicroSpecialtyList } from '@/api/microSpecialty'
import { usePluginStore } from '@/store/plugins'
import { useUserStore } from '@/store/user'
import { getDefaultCover } from '@/utils/coverHelper'

const router = useRouter()
const pluginStore = usePluginStore()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

// Phase 2: 错误消息常量
const ERROR_MESSAGES = {
  LOAD_FAILED: '加载失败，请稍后重试',
  NETWORK_ERROR: '网络错误，请检查连接',
  AUTH_FAILED: '登录已过期，请重新登录'
}

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
// P0 闭环修复 Round 4: 学生端首页 Banner 轮播
const banners = ref([])

const fetchBanners = async () => {
  try {
    const { data } = await getActiveBanners()
    banners.value = Array.isArray(data) ? data : []
  } catch (e) {
    // Banner 失败不阻塞页面
    console.warn('[CourseSquare] Banner 加载失败:', e)
    banners.value = []
  }
}

const handleBannerClick = (banner) => {
  if (!banner.linkUrl) return
  if (/^https?:\/\//.test(banner.linkUrl)) {
    window.open(banner.linkUrl, '_blank', 'noopener,noreferrer')
  } else {
    router.push(banner.linkUrl)
  }
}
// 微专业专区
const msLoading = ref(false)
const msError = ref(false)
const goldFeatured = ref([])
const featured = ref([])
const recruiting = ref([])
const hasMSData = computed(() => goldFeatured.value?.length > 0 || featured.value?.length > 0 || recruiting.value?.length > 0)
// 全部微专业弹窗
const showAllMS = ref(false)
const allMSList = ref([])
const allMSLoading = ref(false)
const dialogFilter = ref('')
const filteredAllMS = computed(() => {
  if (!dialogFilter.value) return allMSList.value
  return allMSList.value.filter(item => item.status === dialogFilter.value)
})
const totalElements = ref(0)
const page = ref(1)
const size = ref(12)
const selectedCategoryId = ref('')

const searchForm = reactive({
  keyword: '',
  difficulty: ''
})

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, searchForm, ['keyword', 'difficulty'])

// 课程排序
const courseSort = ref('')

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

/** P0 修复: 使用 listPrice 作为标价展示, 兼容旧 price 字段 */
const displayPrice = (course) => course?.listPrice || course?.price || 0

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
    ElMessage.warning(ERROR_MESSAGES.LOAD_FAILED)
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
        status: undefined
    }
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (selectedCategoryId.value) params.categoryId = selectedCategoryId.value
    if (searchForm.difficulty) params.difficulty = searchForm.difficulty
    if (courseSort.value === 'hot') { params.sortBy = 'studentCount'; params.sortOrder = 'desc' }
    if (courseSort.value === 'new') { params.sortBy = 'createdAt'; params.sortOrder = 'desc' }

    const { data } = await getCourses(params)
    // 兜底: 数据库 coverUrl 通常为 null, 用类别感知的默认封面补全
    courseList.value = (data?.items || []).map(c => ({
      ...c,
      coverUrl: c.coverUrl || getDefaultCover(c)
    }))
    totalElements.value = data?.totalElements || 0
  } catch (e) {
    console.error('[CourseSquare] 课程加载失败:', e)
    error.value = true
    ElMessage.error(ERROR_MESSAGES.LOAD_FAILED)
  } finally {
    loading.value = false
  }
}

// 拉侧栏数据（热门 + 最新）
const fetchSideCourses = async () => {
  try {
    const [hotRes, newestRes] = await Promise.all([
      getCourses({ page: 0, size: 5, sortBy: 'studentCount', sortOrder: 'desc', status: undefined }),
      getCourses({ page: 0, size: 5, sortBy: 'createdAt', sortOrder: 'desc', status: undefined })
    ])
    hotCourses.value = (hotRes.data?.items || []).slice(0, 5)
    newestCourses.value = (newestRes.data?.items || []).slice(0, 5)
  } catch (e) {
    console.warn('[CourseSquare] 侧栏数据加载失败:', e)
    ElMessage.warning(ERROR_MESSAGES.LOAD_FAILED)
  }
}

// 拉精选推荐
const loadRecommended = async () => {
  try {
    const { data } = await getCourses({ recommended: true, size: 8, status: undefined })
    recommendedCourses.value = (data?.items || []).map(c => ({
      ...c,
      coverUrl: c.coverUrl || getDefaultCover(c)
    }))
  } catch (e) {
    console.warn('[CourseSquare] 推荐课程加载失败:', e)
    recommendedCourses.value = []
    ElMessage.warning(ERROR_MESSAGES.LOAD_FAILED)
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

// 跳详情/学习
const handleCourseClick = (course) => {
  if (!course?.id) return
  // 如果有 enrolled 标记，直接跳转到学习页面
  if (course.enrolled) {
    const path = course.courseType === 'INTERACTIVE'
      ? `/student/courses/${course.id}/slides/player`
      : `/student/learning?courseId=${course.id}`
    router.push(path)
  } else {
    router.push(`/student/courses/${course.id}`)
  }
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
    const { data } = await getMicroSpecialtyList({ page: 0, size: 50 })
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
    ElMessage.warning(ERROR_MESSAGES.LOAD_FAILED)
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
  } else if (newVal.length >= 2) {
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
  // P0-1: 全部 7 个 API 并行 - 减少 80% 首屏白屏时间
  // 客户体验: 之前分 2 批 (Promise.all 2 + 串行 5),首屏 1s+
  //          现在全部 Promise.all,首屏 ≤ 200ms (取决于最慢的一个)
  // 容错策略: 单个 API 失败不影响其他,每个 fetch 内部 catch 已处理
  await Promise.all([
    fetchCategories(),
    fetchCourses(),
    fetchSideCourses(),
    loadRecommended(),
    loadBundles(),
    fetchMicroSpecialties(),
    fetchBanners()  // P0 闭环修复 Round 4: 学生端 Banner 轮播
  ])
})
</script>

<style scoped>
/* ==========================================================================
   Container
   ========================================================================== */
.course-square {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-4) var(--space-6) var(--space-8);
  min-height: 100dvh;
}

/* ==========================================================================
   Hero — 100px · 内置搜索
   ========================================================================== */
.hero-section {
  position: relative;
  padding: var(--space-6) var(--space-8);
  border-radius: var(--radius-2xl);
  background: linear-gradient(155deg, var(--role-primary) 0%, var(--role-primary-dark) 50%, var(--role-primary-darkest) 100%);
  box-shadow: 0 8px 32px color-mix(in srgb, var(--role-primary) 20%, transparent);
}
.hero-content { position: relative; z-index: 1; }
.hero-title { margin: 0; font-size: 28px; font-weight: var(--weight-bold); color: #fff; letter-spacing: var(--tracking-tight); }
.hero-subtitle { margin: var(--space-1) 0 0; font-size: var(--text-sm); color: rgba(255,255,255,.7); }
.hero-search { margin-top: var(--space-4); max-width: 560px; }
.hero-search-input { --el-input-bg-color: rgba(255,255,255,.15); --el-input-text-color: #fff; --el-input-border-color: rgba(255,255,255,.25); --el-input-hover-border-color: rgba(255,255,255,.5); --el-input-focus-border-color: rgba(255,255,255,.7); --el-input-placeholder-color: rgba(255,255,255,.5); }
.hero-search-input :deep(.el-input-group__append) { background: rgba(255,255,255,.2); border: none; }
.hero-search-input :deep(.el-input-group__append .el-button) { color: #fff; border: none; }
.hero-search-input :deep(.el-input__wrapper) { box-shadow: none; }
.hero-search-input :deep(.el-input__wrapper:hover) { box-shadow: none; }

/* ==========================================================================
   Banner 轮播 — 横向滑动
   ========================================================================== */
.banner-section {
  margin-bottom: var(--space-6);
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,.06);
}
.banner-section :deep(.el-carousel__container) {
  border-radius: var(--radius-xl);
  overflow: hidden;
}
.banner-item {
  display: block;
  width: 100%;
  height: 100%;
}
.banner-item .banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.banner-section :deep(.el-carousel__indicator) {
  padding: 6px 4px;
}
.banner-section :deep(.el-carousel__button) {
  width: 24px;
  height: 4px;
  border-radius: 2px;
  background: var(--el-color-primary);
  opacity: .4;
}
.banner-section :deep(.el-carousel__indicator--active .el-carousel__button) {
  opacity: 1;
  width: 32px;
}

/* ==========================================================================
   Filter Bar — 分类 + 难度
   ========================================================================== */
.filter-bar { margin-top: var(--space-4); }
.filter-row { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.difficulty-select { width: 130px; flex-shrink: 0; }
.category-scroll { flex: 1; min-width: 200px; overflow-x: auto; -webkit-overflow-scrolling: touch; }
.category-scroll::-webkit-scrollbar { height: 4px; }
.category-chip-group { display: flex; gap: var(--space-1); white-space: nowrap; }
.category-chip-group :deep(.el-radio-button__inner) {
  border-radius: var(--radius-pill) !important; padding: var(--space-1) var(--space-4);
  border: 1px solid var(--el-border-color) !important; box-shadow: none !important;
  font-weight: var(--weight-medium); font-size: var(--text-sm);
  transition: all var(--duration-base) var(--ease-out);
}
.category-chip-group :deep(.el-radio-button__inner:hover) { background: var(--role-primary-light-9); border-color: var(--role-primary-light-5) !important; color: var(--role-primary); }
.category-chip-group :deep(.el-radio-button.is-active .el-radio-button__inner) { background: var(--role-primary) !important; border-color: var(--role-primary) !important; color: #fff !important; }
.reset-btn { flex-shrink: 0; }

/* ==========================================================================
   Section
   ========================================================================== */
.section { margin-top: var(--space-6); }
.section-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: var(--space-3);
}
.section-title {
  font-size: var(--text-xl); font-weight: var(--weight-bold);
  color: var(--el-text-color-primary); margin: 0;
  display: flex; align-items: center; gap: var(--space-2);
}
.section-title::before {
  content: ''; width: 4px; height: 20px;
  background: var(--role-primary);
  border-radius: var(--radius-pill); flex-shrink: 0;
}

/* ==========================================================================
   Sort Tabs — 全部课程排序
   ========================================================================== */
.sort-tabs :deep(.el-radio-button__inner) {
  border-radius: var(--radius-pill) !important;
  padding: var(--space-1) var(--space-4);
  border: 1px solid var(--el-border-color) !important;
  box-shadow: none !important;
  font-size: var(--text-sm);
  transition: all var(--duration-base) var(--ease-out);
}
.sort-tabs :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: var(--role-primary) !important;
  border-color: var(--role-primary) !important;
  color: #fff !important;
}

/* ==========================================================================
   Course Grid & Card
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
.course-card:hover { transform: translateY(-6px); box-shadow: var(--shadow-tinted-lg), var(--shadow-xl); }
.course-card:focus-visible { outline: 2px solid var(--role-primary); outline-offset: 2px; }
.course-card:active { transform: translateY(-3px) scale(.98); }

.course-cover {
  position: relative; width: 100%; aspect-ratio: 16 / 9; overflow: hidden;
  background: linear-gradient(145deg, var(--role-primary-light-9), var(--role-primary-light-7));
}
.cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; z-index: 1; transition: transform var(--duration-slow) var(--ease-out); }
.course-card:hover .cover-img { transform: scale(1.08); }
.cover-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--role-primary); opacity: .4; z-index: 1; }

.course-type-badge {
  position: absolute; top: var(--space-3); right: var(--space-3);
  padding: 2px 8px; border-radius: 4px; font-size: 11px; color: #fff; z-index: 2;
}
.difficulty-label {
  position: absolute; top: var(--space-3); left: var(--space-3);
  padding: 3px 10px; font-size: 11px; font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wide); border-radius: var(--radius-sm); z-index: 2;
}
.difficulty-label--success { background: color-mix(in srgb, var(--el-color-success) 12%, transparent); color: var(--el-color-success); }
.difficulty-label--warning { background: color-mix(in srgb, var(--el-color-warning) 12%, transparent); color: var(--el-color-warning); }
.difficulty-label--danger { background: color-mix(in srgb, var(--el-color-danger) 12%, transparent); color: var(--el-color-danger); }
.difficulty-label--info { background: color-mix(in srgb, var(--el-color-info) 12%, transparent); color: var(--el-color-info); }
.category-chip {
  position: absolute; bottom: var(--space-3); left: var(--space-3);
  border: none !important; border-radius: var(--radius-sm) !important;
  font-weight: var(--weight-medium); font-size: 11px; padding: 2px 8px;
  background: rgba(255,255,255,.88); backdrop-filter: blur(8px);
  color: var(--el-text-color-regular); z-index: 2;
}

.free-chip {
  position: absolute; bottom: var(--space-3); right: var(--space-3);
  border: none !important; border-radius: var(--radius-sm) !important;
  font-weight: var(--weight-medium); font-size: 11px; padding: 2px 8px;
  z-index: 2;
}

.course-info { padding: var(--space-4); }
.course-title {
  margin: 0 0 var(--space-2); font-size: var(--text-md); font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary); line-height: var(--leading-snug); height: 2.75em;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
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
  padding-top: var(--space-3); border-top: 1px solid var(--el-fill-color-lighter);
}
.rating { display: flex; align-items: center; gap: var(--space-1); font-size: var(--text-sm); }
.rating-star { color: var(--el-color-warning); font-size: var(--text-md); }
.rating-value { color: var(--el-text-color-primary); font-weight: var(--weight-semibold); }
.rating-count { color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.rating-none { color: var(--el-text-color-placeholder); font-size: var(--text-xs); }
.price { font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--role-primary); }
.price--free { font-size: var(--text-md); color: var(--el-color-success); font-weight: var(--weight-medium); }

/* ==========================================================================
   推荐水平横滑
   ========================================================================== */
.rec-scroll-wrap { overflow: hidden; margin: 0 calc(var(--space-6) * -1); padding: 0 var(--space-6); }
.rec-scroll { display: flex; gap: var(--space-4); overflow-x: auto; padding-bottom: var(--space-2); scroll-snap-type: x mandatory; }
.rec-scroll::-webkit-scrollbar { height: 4px; }
.rec-scroll::-webkit-scrollbar-thumb { background: var(--role-primary-light-5); border-radius: var(--radius-pill); }
.rec-card {
  flex: 0 0 280px; border-radius: var(--radius-lg); overflow: hidden; cursor: pointer;
  background: var(--el-bg-color-overlay); box-shadow: var(--shadow-tinted-sm); outline: none;
  scroll-snap-align: start;
  animation: rec-in .5s var(--ease-out) both;
  animation-delay: calc(var(--rec-index, 0) * .06s);
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}
@keyframes rec-in { from { opacity: 0; transform: translateX(16px); } to { opacity: 1; transform: translateX(0); } }
.rec-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-tinted-lg), var(--shadow-md); }
.rec-card:focus-visible { outline: 2px solid var(--role-primary); outline-offset: 2px; }
.rec-cover { position: relative; width: 100%; aspect-ratio: 16 / 9; overflow: hidden; background: linear-gradient(145deg, var(--role-primary-light-9), var(--role-primary-light-7)); }
.rec-cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; transition: transform var(--duration-slow) var(--ease-out); }
.rec-card:hover .rec-cover-img { transform: scale(1.08); }
.rec-cover-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--role-primary); opacity: .4; }
.rec-badge {
  position: absolute; top: var(--space-2); right: var(--space-2);
  padding: 2px 8px; font-size: 11px; font-weight: var(--weight-semibold);
  border-radius: var(--radius-sm); z-index: 2;
  background: linear-gradient(135deg, var(--el-color-warning), var(--el-color-warning-dark-2)); color: #fff;
}
.rec-info { padding: var(--space-3); }
.rec-title { margin: 0 0 var(--space-1); font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rec-meta { margin: 0 0 var(--space-2); font-size: var(--text-xs); color: var(--el-text-color-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rec-footer { display: flex; align-items: center; justify-content: space-between; }
.rec-rating { font-size: var(--text-xs); color: var(--el-text-color-secondary); display: flex; align-items: center; gap: 2px; }
.rec-price { font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--role-primary); }
.rec-price--free { color: var(--el-color-success); }

/* ==========================================================================
   Bundle
   ========================================================================== */
.bundle-scroll { display: flex; gap: var(--space-3); overflow-x: auto; padding-bottom: var(--space-2); }
.bundle-chip {
  flex: 0 0 220px; display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3);
  background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); cursor: pointer;
  box-shadow: var(--shadow-tinted-sm); transition: all var(--duration-base) var(--ease-out);
}
.bundle-chip:hover { box-shadow: var(--shadow-tinted-md); transform: translateY(-2px); }
.b-chip-icon { width: 36px; height: 36px; border-radius: var(--radius-md); background: var(--role-primary-light-9); display: flex; align-items: center; justify-content: center; color: var(--role-primary); flex-shrink: 0; }
.b-chip-info { min-width: 0; }
.b-chip-title { display: block; font-size: var(--text-sm); font-weight: var(--weight-medium); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.b-chip-price { font-size: var(--text-xs); color: var(--el-color-danger); font-weight: var(--weight-semibold); }
.b-chip-price.free { color: var(--el-color-success); }

/* ==========================================================================
   Pagination
   ========================================================================== */
.pagination-wrap { margin-top: var(--space-5); display: flex; justify-content: center; }
.course-pagination {
  --el-pagination-bg-color: transparent; padding: var(--space-2) var(--space-4);
  background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); box-shadow: var(--shadow-tinted-sm);
}

/* ==========================================================================
   State Block
   ========================================================================== */
.state-block { padding: var(--space-10) 0; background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); box-shadow: var(--shadow-tinted-sm); }
.state-icon { color: var(--el-text-color-placeholder); opacity: .6; }
.state-icon--error { color: var(--el-color-danger); opacity: .6; }
.state-detail { margin: var(--space-3) 0 var(--space-5); font-size: var(--text-sm); color: var(--el-text-color-secondary); }
.state-action { min-width: 120px; border-radius: var(--radius-md); }
.search-recommend-section { margin-top: var(--space-6); text-align: left; padding: 0 var(--space-6); }
.recommend-hint { font-size: var(--text-sm); color: var(--el-text-color-secondary); margin: 0 0 var(--space-3); text-align: center; }
.search-recommend-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: var(--space-3); }
.search-recommend-item { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2); border-radius: var(--radius-md); cursor: pointer; transition: background var(--duration-base) var(--ease-out); }
.search-recommend-item:hover { background: var(--el-fill-color-lighter); }
.sr-cover { width: 64px; height: 40px; border-radius: var(--radius-sm); overflow: hidden; flex-shrink: 0; background: linear-gradient(135deg, var(--role-primary-light-9), var(--role-primary-light-7)); }
.sr-cover-img { width: 100%; height: 100%; object-fit: cover; }
.sr-cover-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: var(--role-primary); opacity: .5; }
.sr-info { min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.sr-title { font-size: var(--text-sm); font-weight: var(--weight-medium); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sr-meta { font-size: var(--text-xs); color: var(--el-text-color-secondary); }

/* ==========================================================================
   Skeleton
   ========================================================================== */
.skeleton-card { background: var(--el-bg-color-overlay); box-shadow: var(--shadow-tinted-sm) !important; animation: sk-fade-in .6s var(--ease-out) both; }
@keyframes sk-fade-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.skeleton-cover { aspect-ratio: 16 / 9; background: linear-gradient(110deg, var(--el-fill-color-lighter) 30%, var(--el-fill-color) 50%, var(--el-fill-color-lighter) 70%); background-size: 200% 100%; animation: shimmer 1.8s ease-in-out infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.skeleton-info { padding: var(--space-4); }
.sk-title { height: 18px; width: 80%; margin-bottom: var(--space-3); border-radius: var(--radius-sm); }
.sk-meta { height: 14px; width: 60%; margin-bottom: var(--space-2); border-radius: var(--radius-sm); }
.sk-price { height: 18px; width: 30%; border-radius: var(--radius-sm); }

/* ==========================================================================
   Micro-Specialty — 简化为标准 section
   ========================================================================== */
.ms-loading-row { display: flex; gap: 16px; }
.ms-skeleton-card { flex: 1; background: var(--el-fill-color-lighter); border-radius: var(--radius-lg); padding: 16px; min-height: 180px; }
.ms-skel-cover { aspect-ratio: 16 / 9; background: var(--el-fill-color); border-radius: var(--radius-md); margin-bottom: 12px; }
.ms-skel-body { padding: 0 4px; }
.ms-gold-row { display: flex; gap: 20px; margin-bottom: 20px; }
.ms-gold-card {
  flex: 1; display: flex; gap: 20px;
  background: linear-gradient(135deg, #fefce8, #fef9c3);
  border: 1px solid #fde68a;
  border-radius: var(--radius-xl); padding: 20px; cursor: pointer; outline: none;
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
  position: relative; overflow: hidden; min-height: 180px;
}
.ms-gold-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-tinted-lg); }
.ms-gold-card:focus-visible { outline: 2px solid var(--role-primary); outline-offset: 2px; }
.gold-badge {
  position: absolute; top: 8px; right: -8px;
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
  color: #fff; font-size: 11px; font-weight: var(--weight-bold);
  padding: 3px 14px 3px 10px; border-radius: 0 0 0 10px; z-index: 2;
  letter-spacing: var(--tracking-wide);
}
.gold-cover { width: 180px; min-width: 180px; height: 130px; border-radius: var(--radius-lg); overflow: hidden; background: linear-gradient(135deg, #eef2ff, #e0e7ff); }
.gold-cover-img { width: 100%; height: 100%; object-fit: cover; transition: transform var(--duration-slow) var(--ease-out); }
.ms-gold-card:hover .gold-cover-img { transform: scale(1.05); }
.gold-cover-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #a5b4fc; }
.gold-info { flex: 1; display: flex; flex-direction: column; justify-content: center; min-width: 0; }
.gold-title { font-size: var(--text-lg); font-weight: var(--weight-bold); color: var(--el-text-color-primary); margin: 0 0 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gold-meta { font-size: var(--text-xs); color: var(--el-text-color-secondary); margin: 0 0 4px; }
.gold-stats { font-size: var(--text-sm); color: var(--el-text-color-regular); margin: 0 0 10px; font-weight: var(--weight-medium); }
.gold-cta { align-self: flex-start; }
.ms-card {
  flex: 0 0 220px; background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg); overflow: hidden; cursor: pointer; outline: none;
  scroll-snap-align: start; box-shadow: var(--shadow-tinted-sm);
  transition: transform var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
}
.ms-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-tinted-lg); }
.ms-card:focus-visible { outline: 2px solid var(--role-primary); outline-offset: 2px; }
.ms-card-cover { position: relative; width: 100%; aspect-ratio: 16 / 9; overflow: hidden; background: linear-gradient(135deg, #eef2ff, #e0e7ff); }
.ms-cover-img { width: 100%; height: 100%; object-fit: cover; transition: transform var(--duration-slow) var(--ease-out); }
.ms-card:hover .ms-cover-img { transform: scale(1.08); }
.ms-cover-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #a5b4fc; }
.ms-new-badge { position: absolute; top: 6px; right: 6px; background: #10b981; color: #fff; font-size: 10px; font-weight: var(--weight-bold); padding: 2px 6px; border-radius: var(--radius-sm); z-index: 2; }
.ms-card-body { padding: 10px 12px; }
.ms-card-title { margin: 0 0 2px; font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ms-card-dept { margin: 0; font-size: var(--text-xs); color: var(--el-text-color-secondary); }

/* 全部微专业弹窗 */
.ms-all-dialog :deep(.el-dialog__header) { border-bottom: 1px solid var(--el-border-color-lighter); padding: 16px 24px; }
.ms-all-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; min-height: 200px; max-height: 60vh; overflow-y: auto; padding: 4px 0; }
.ms-all-card { background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); overflow: hidden; cursor: pointer; outline: none; box-shadow: var(--shadow-tinted-sm); transition: transform var(--duration-base) var(--ease-out); }
.ms-all-card:hover { transform: translateY(-2px); }
.ms-all-cover { width: 100%; aspect-ratio: 16 / 9; overflow: hidden; background: linear-gradient(135deg, #eef2ff, #e0e7ff); }
.ms-all-cover-img { width: 100%; height: 100%; object-fit: cover; }
.ms-all-cover-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #a5b4fc; }
.ms-all-info { padding: 10px 12px; }
.ms-all-title { margin: 0 0 2px; font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ms-all-dept { margin: 0 0 2px; font-size: var(--text-xs); color: var(--el-text-color-secondary); }
.ms-all-stats { margin: 0; font-size: var(--text-xs); color: var(--el-text-color-regular); font-weight: var(--weight-medium); }

/* ==========================================================================
   Utilities
   ========================================================================== */
.mg-bottom-12 { margin-bottom: var(--space-3); }
.ms-all-filter-bar { display: flex; justify-content: center; }
@media (prefers-reduced-motion: reduce) { *,*::before,*::after { transition-duration: .01ms !important; animation-duration: .01ms !important; } }

/* ==========================================================================
   H5
   ========================================================================== */
@media (max-width: 768px) {
  .course-grid :deep(.el-row) { display: block; column-count: 2; column-gap: 12px; margin: 0 !important; }
  .course-grid :deep(.el-col) { display: inline-block; width: 100%; max-width: 100%; flex: none; break-inside: avoid; -webkit-column-break-inside: avoid; margin-bottom: 12px; padding: 0 !important; }
  .rec-card { flex: 0 0 240px; }
  .hero-title { font-size: 22px; }
  .hero-section { padding: var(--space-4); }
  .banner-section :deep(.el-carousel) { height: 160px; }
  .banner-section :deep(.el-carousel__container) { height: 160px; }
  .banner-section { margin-bottom: var(--space-4); }
  .course-square { padding: var(--space-3); }
  /* 客户体验修复 v1.7.0: 移动端禁用负 margin 扩展,防止横向滚动 */
  .rec-scroll-wrap { margin: 0 !important; padding: 0 !important; }
  /* 移动端分类筛选改为多行展示,避免单行横向溢出 */
  .filter-row { flex-direction: column; align-items: stretch; }
  .difficulty-select { width: 100% !important; }
  .category-scroll { min-width: 0 !important; width: 100%; }
  .category-chip-group { flex-wrap: wrap; }
  .reset-btn { align-self: flex-end; }
}

@media (max-width: 480px) {
  .course-grid :deep(.el-row) { column-count: 1; }
}</style>
