<!--
  学习数据分析
  教务处/管理员 全校学习数据深度分析
  Route: /academic/stats
-->
<template>
  <div class="analytics-page">
    <el-breadcrumb separator="/" class="page-breadcrumb">
      <el-breadcrumb-item>运营监控</el-breadcrumb-item>
      <el-breadcrumb-item>学习数据分析</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 概览指标 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6" v-for="stat in overviewStats" :key="stat.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
          <div class="stat-trend" v-if="stat.trend !== undefined">
            <el-icon :color="stat.trend >= 0 ? '#67c23a' : '#f56c6c'">
              <Top v-if="stat.trend >= 0" /><Bottom v-else />
            </el-icon>
            <span :style="{ color: stat.trend >= 0 ? '#67c23a' : '#f56c6c' }">
              {{ Math.abs(stat.trend) }}%
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 院系统计 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span>院系学习数据</span>
          <el-select v-model="sortBy" size="small" style="width:140px" @change="sortDepartments">
            <el-option label="按完成率排序" value="completion" />
            <el-option label="按正确率排序" value="accuracy" />
            <el-option label="按选课人次排序" value="enrollments" />
          </el-select>
        </div>
      </template>
      <el-skeleton v-if="deptLoading" :rows="5" animated />
      <el-empty v-else-if="departmentStats.length === 0" description="暂无数据" :image-size="80" />
      <div v-else class="dept-list">
        <div v-for="(dept, idx) in departmentStats" :key="dept.id" class="dept-row" @click="selectDept(dept)" role="button" tabindex="0" @keydown.enter="selectDept(dept)">
          <div class="dept-rank">#{{ idx + 1 }}</div>
          <div class="dept-info">
            <div class="dept-name">{{ dept.name }}</div>
            <div class="dept-meta">选课 {{ dept.totalEnrollments }} 人次</div>
          </div>
          <div class="dept-metrics">
            <div class="metric">
              <div class="metric-label">完成率</div>
              <el-progress :percentage="dept.avgCompletionRate || 0" :stroke-width="6" :color="completionColor(dept.avgCompletionRate)" />
            </div>
            <div class="metric">
              <div class="metric-label">正确率</div>
              <el-progress :percentage="dept.avgAccuracyRate || 0" :stroke-width="6" :color="accuracyColor(dept.avgAccuracyRate)" />
            </div>
          </div>
          <div class="dept-action">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 预警列表 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <span><el-icon color="#e6a23c"><WarningFilled /></el-icon> 完成率预警</span>
        </div>
      </template>
      <el-skeleton v-if="warnLoading" :rows="3" animated />
      <el-empty v-else-if="warnings.length === 0" description="暂无预警，所有院系完成率正常" :image-size="80" />
      <el-table v-else :data="warnings" stripe size="small">
        <el-table-column prop="name" label="院系" min-width="140" />
        <el-table-column label="完成率" width="200">
          <template #default="{ row }">
            <el-progress :percentage="row.completionRate || 0" :stroke-width="10" :status="row.completionRate < 30 ? 'exception' : 'warning'" />
          </template>
        </el-table-column>
        <el-table-column prop="enrollmentCount" label="选课人数" width="100" align="center" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAcademicOverview, getDepartmentStats, getCompletionWarnings } from '@/api/academic-stats'
import { Top, Bottom, ArrowRight, WarningFilled } from '@element-plus/icons-vue'

const router = useRouter()
const overviewStats = ref([])
const departmentStats = ref([])
const warnings = ref([])
const deptLoading = ref(false)
const warnLoading = ref(false)
const sortBy = ref('completion')

function completionColor(rate) {
  if (rate >= 80) return '#67c23a'
  if (rate >= 50) return '#409eff'
  if (rate >= 30) return '#e6a23c'
  return '#f56c6c'
}
function accuracyColor(rate) {
  if (rate >= 70) return '#67c23a'
  if (rate >= 50) return '#409eff'
  return '#e6a23c'
}

function sortDepartments() {
  const key = sortBy.value === 'completion' ? 'avgCompletionRate'
    : sortBy.value === 'accuracy' ? 'avgAccuracyRate'
    : 'totalEnrollments'
  departmentStats.value.sort((a, b) => (b[key] || 0) - (a[key] || 0))
}

async function fetchOverview() {
  try {
    const { data } = await getAcademicOverview()
    overviewStats.value = [
      { label: '总课程数', value: data.totalCourses ?? '—', color: '#409eff' },
      { label: '选课人次', value: data.totalEnrollments ?? '—', color: '#67c23a' },
      { label: '平均完成率', value: (data.avgCompletionRate ?? '—') + '%', color: '#e6a23c', trend: data.completionTrend },
      { label: '平均正确率', value: (data.avgAccuracyRate ?? '—') + '%', color: '#f56c6c', trend: data.accuracyTrend },
    ]
  } catch { /* ignore */ }
}

async function fetchDeptStats() {
  deptLoading.value = true
  try {
    const { data } = await getDepartmentStats()
    departmentStats.value = (data || []).sort((a, b) => (b.avgCompletionRate || 0) - (a.avgCompletionRate || 0))
  } catch { ElMessage.error('加载院系统计失败') }
  finally { deptLoading.value = false }
}

async function fetchWarnings() {
  warnLoading.value = true
  try {
    const { data } = await getCompletionWarnings()
    warnings.value = data?.items || []
  } catch { /* ignore */ }
  finally { warnLoading.value = false }
}

function selectDept(dept) {
  router.push(`/academic/dashboard?departmentId=${dept.id}`)
}

onMounted(() => {
  fetchOverview()
  fetchDeptStats()
  fetchWarnings()
})
</script>

<style scoped>
.analytics-page { padding: var(--space-6); background: var(--el-bg-color-page); min-height: calc(100dvh - 120px); max-width: 1280px; margin: 0 auto; }
.page-breadcrumb { margin-bottom: var(--space-4); }

.stats-row { margin-bottom: var(--space-4); }
.stat-card { text-align: center; padding: var(--space-4) 0; border-radius: var(--radius-lg); }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: var(--text-sm); color: var(--el-text-color-secondary); margin-top: var(--space-1); }
.stat-trend { margin-top: var(--space-1); display: flex; align-items: center; justify-content: center; gap: 2px; font-size: var(--text-xs); }

.section-card { margin-bottom: var(--space-4); border-radius: var(--radius-lg); }
.section-header { display: flex; justify-content: space-between; align-items: center; font-weight: var(--weight-semibold); }

.dept-list { display: flex; flex-direction: column; gap: var(--space-1); }
.dept-row { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-3) var(--space-3); border-radius: var(--radius-md); cursor: pointer; transition: background var(--duration-base); }
.dept-row:hover { background: var(--el-fill-color-light); }
.dept-rank { width: 32px; font-size: var(--text-sm); font-weight: var(--weight-bold); color: var(--el-text-color-secondary); text-align: center; }
.dept-info { min-width: 140px; }
.dept-name { font-weight: var(--weight-medium); font-size: var(--text-base); }
.dept-meta { font-size: var(--text-xs); color: var(--el-text-color-secondary); }
.dept-metrics { flex: 1; display: flex; gap: var(--space-6); }
.metric { flex: 1; max-width: 240px; }
.metric-label { font-size: var(--text-xs); color: var(--el-text-color-secondary); margin-bottom: 2px; }
.dept-action { color: var(--el-text-color-placeholder); }
</style>
