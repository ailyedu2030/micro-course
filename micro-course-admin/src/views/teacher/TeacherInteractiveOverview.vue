<template>
  <div class="interactive-overview">
    <header class="page-header">
      <button class="back-btn" @click="$router.push('/teacher/courses')" aria-label="返回">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <h1>互动课程管理</h1>
      <span class="page-subtitle">{{ courses.length }} 门互动课程</span>
    </header>

    <section class="content-card">
      <div class="empty-tip" v-if="!loading && courses.length === 0">
        <el-empty description="暂无互动课程。请前往「我的课程」创建互动课程后,即可在此管理课件。">
          <el-button type="primary" @click="goCreateCourse">前往创建课程</el-button>
        </el-empty>
      </div>

      <div v-else class="course-grid">
        <div
v-for="course in courses" :key="course.id" class="course-card"
          :class="{ 'has-slides': course.id in slidesMap }"
        >
          <div class="course-cover">
            <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" />
            <div v-else class="cover-placeholder">
              <el-icon :size="40"><Present /></el-icon>
            </div>
            <el-tag class="interactive-tag" type="success" size="small">互动课</el-tag>
          </div>
          <div class="course-info">
            <h3 class="course-title">{{ course.title || '加载中...' }}</h3>
            <div class="course-meta">
              <el-tag size="small" :type="statusType(course.status)">{{ statusLabel(course.status) }}</el-tag>
              <span v-if="course.id in slidesMap" class="slide-stat">
                {{ slidesMap[course.id].totalPages }} 页
              </span>
              <span v-else class="slide-stat missing">未上传课件</span>
            </div>
            <div class="course-actions">
              <el-button
                type="primary" link size="small"
                @click="goManageSlides(course.id)"
              >
                {{ course.id in slidesMap ? '编辑课件' : '上传课件' }}
              </el-button>
              <el-button link size="small" @click="goViewCourse(course.id)">查看</el-button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Present } from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getSlides } from '@/plugins/interactive/api/slide'

const router = useRouter()
const loading = ref(false)
const courses = ref([])
const slidesMap = ref({})

const statusMap = {
  0: { label: '草稿', type: 'info' },
  1: { label: '待审核', type: 'warning' },
  2: { label: '通过', type: 'success' },
  3: { label: '驳回', type: 'danger' },
  4: { label: '已发布', type: 'primary' },
  5: { label: '下架', type: 'warning' },
  6: { label: '归档', type: 'info' },
}

function statusLabel(status) { return statusMap[status]?.label || '未知' }
function statusType(status) { return statusMap[status]?.type || 'info' }

async function loadData() {
  loading.value = true
  try {
    const { data } = await getCourses({ courseType: 'INTERACTIVE', size: 100 })
    courses.value = data?.items || data?.content || data?.records || []
    const slidesPromises = courses.value.map(async c => {
      try {
        const r = await getSlides(c.id)
        return [c.id, r.data]
      } catch { return [c.id, null] }
    })
    const results = await Promise.all(slidesPromises)
    slidesMap.value = Object.fromEntries(
      results.filter(([_, v]) => v != null).map(([id, v]) => [id, v])
    )
  } catch (e) {
    console.warn('[InteractiveOverview] load error', e)
  } finally {
    loading.value = false
  }
}

function goManageSlides(courseId) {
  router.push(`/teacher/courses/${courseId}/slides/manage`)
}
function goViewCourse(courseId) {
  router.push(`/courses/${courseId}`)
}
function goCreateCourse() {
  router.push('/teacher/courses')
}

onMounted(loadData)
</script>

<style scoped>
.interactive-overview { padding: 24px; max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 600; margin: 0; }
.page-subtitle { color: var(--el-text-color-secondary); font-size: 14px; }
.back-btn { background: transparent; border: none; cursor: pointer; padding: 8px; border-radius: 6px; }
.back-btn:hover { background: var(--el-fill-color-light); }
.content-card { background: var(--el-bg-color); border-radius: 8px; padding: 24px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.course-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
.course-card {
  background: var(--el-bg-color-page);
  border-radius: 8px;
  overflow: hidden;
  transition: all 0.2s ease;
  border: 1px solid var(--el-border-color-lighter);
}
.course-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); border-color: var(--el-color-primary-light-5); }
.course-cover { position: relative; aspect-ratio: 16/9; background: var(--el-fill-color-blank); overflow: hidden; }
.course-cover img { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.interactive-tag { position: absolute; top: 8px; right: 8px; }
.course-info { padding: 14px; }
.course-title { font-size: 15px; font-weight: 600; margin: 0 0 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.course-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; font-size: 13px; }
.slide-stat { color: var(--el-text-color-secondary); }
.slide-stat.missing { color: var(--el-color-warning); }
.course-actions { display: flex; gap: 4px; }
.empty-tip { padding: 60px 0; }
</style>
