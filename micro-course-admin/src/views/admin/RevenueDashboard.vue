<!--
  管理员 - 营收看板
  /admin/revenue
-->
<template>
  <div class="revenue-dashboard">
    <el-card class="info-card shadow-hover" shadow="never">
      <div class="info-content">
        <el-icon :size="20" class="info-icon"><TrendCharts /></el-icon>
        <div>
          <p class="info-title">平台营收看板</p>
          <p class="info-desc">查看平台整体收入、月度趋势、教师收入排行。</p>
        </div>
      </div>
    </el-card>

    <!-- 核心指标 -->
    <el-row :gutter="16" class="stats-row" v-loading="loading">
      <el-col :xs="12" :md="6">
        <div class="stat-card">
          <span class="stat-label">平台总收入</span>
          <span class="stat-value primary">¥{{ fmt(data.totalRevenue) }}</span>
        </div>
      </el-col>
      <el-col :xs="12" :md="6">
        <div class="stat-card">
          <span class="stat-label">平台分成收入</span>
          <span class="stat-value muted">¥{{ fmt(data.platformShareTotal) }}</span>
        </div>
      </el-col>
      <el-col :xs="12" :md="6">
        <div class="stat-card">
          <span class="stat-label">教师待结算</span>
          <span class="stat-value success">¥{{ fmt(data.teacherPayoutTotal) }}</span>
        </div>
      </el-col>
      <el-col :xs="12" :md="6">
        <div class="stat-card">
          <span class="stat-label">付费订单</span>
          <span class="stat-value">{{ data.totalOrders ?? 0 }}</span>
          <span class="stat-unit">单 / {{ data.paidStudentCount ?? 0 }} 学员</span>
        </div>
      </el-col>
    </el-row>

    <!-- 月度趋势 + 教师排行 -->
    <el-row :gutter="16" class="detail-row">
      <!-- 月度趋势 -->
      <el-col :xs="24" :md="14">
        <el-card class="section-card" shadow="never" v-loading="loading">
          <template #header><span>月度营收趋势</span></template>
          <div v-if="data.monthlyTrend && data.monthlyTrend.length > 0" class="monthly-list">
            <div v-for="m in data.monthlyTrend" :key="m.month" class="month-row">
              <span class="month-label">{{ m.month }}</span>
              <div class="month-bar-wrap">
                <div class="month-bar" :style="{ width: barWidth(m.revenue) + '%' }" />
              </div>
              <span class="month-revenue">¥{{ fmt(m.revenue) }}</span>
              <span class="month-orders">{{ m.orderCount }} 单</span>
            </div>
          </div>
          <el-empty v-else description="暂无月度数据" />
        </el-card>
      </el-col>

      <!-- 教师排行 -->
      <el-col :xs="24" :md="10">
        <el-card class="section-card" shadow="never" v-loading="loading">
          <template #header><span>教师收入排行 (Top 10)</span></template>
          <div v-if="data.topTeachers && data.topTeachers.length > 0" class="teacher-rank">
            <div v-for="(t, i) in data.topTeachers" :key="t.teacherId" class="rank-row">
              <span class="rank-num">{{ i + 1 }}</span>
              <span class="rank-name">{{ t.teacherName || '未知' }}</span>
              <span class="rank-revenue">¥{{ fmt(t.revenue) }}</span>
              <span class="rank-orders">{{ t.orderCount }} 单</span>
            </div>
          </div>
          <el-empty v-else description="暂无教师收入数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { TrendCharts } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getRevenueStats } from '@/api/admin-stats'

const loading = ref(false)
const data = ref({})

function fmt(val) {
  if (!val && val !== 0) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const maxRevenue = ref(0)
function barWidth(rev) {
  if (!maxRevenue.value || !rev) return 0
  return (rev / maxRevenue.value) * 100
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getRevenueStats()
    data.value = res.data || {}
    // 计算最大月度营收（用于柱状图比例）
    if (data.value.monthlyTrend && data.value.monthlyTrend.length > 0) {
      maxRevenue.value = Math.max(...data.value.monthlyTrend.map(m => m.revenue || 0))
    }
  } catch (e) {
    console.warn('[RevenueDashboard] fetch failed', e)
    ElMessage.warning('营收数据加载失败，请稍后重试')
    data.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.revenue-dashboard {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1200px;
  margin: 0 auto;
}
.info-card { margin-bottom: var(--space-6); border-radius: var(--radius-lg); }
.info-content { display: flex; align-items: flex-start; gap: var(--space-3); }
.info-icon { color: var(--role-primary); flex-shrink: 0; margin-top: 2px; }
.info-title { margin: 0 0 var(--space-1); font-size: var(--text-md); font-weight: var(--weight-semibold); }
.info-desc { margin: 0; font-size: var(--text-base); color: var(--el-text-color-secondary); }

.stats-row { margin-bottom: var(--space-6); }
.stat-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  text-align: center;
  box-shadow: var(--shadow-xs);
}
.stat-label { display: block; font-size: var(--text-xs); color: var(--el-text-color-secondary); margin-bottom: var(--space-1); }
.stat-value { display: block; font-size: var(--text-2xl); font-weight: var(--weight-bold); font-variant-numeric: tabular-nums; }
.stat-value.primary { color: var(--role-primary); }
.stat-value.muted { color: var(--el-text-color-secondary); }
.stat-value.success { color: var(--el-color-success); }
.stat-unit { display: block; font-size: var(--text-xs); color: var(--el-text-color-secondary); margin-top: var(--space-1); }

.detail-row { margin-bottom: var(--space-6); }
.section-card { margin-bottom: var(--space-4); border-radius: var(--radius-lg); }

.monthly-list { display: flex; flex-direction: column; gap: var(--space-2); }
.month-row { display: flex; align-items: center; gap: var(--space-3); font-size: var(--text-sm); }
.month-label { min-width: 60px; font-weight: var(--weight-medium); }
.month-bar-wrap { flex: 1; height: 20px; background: var(--el-fill-color-lighter); border-radius: var(--radius-sm); overflow: hidden; }
.month-bar { height: 100%; background: var(--role-primary); border-radius: var(--radius-sm); transition: width 0.6s ease; min-width: 2px; }
.month-revenue { min-width: 80px; text-align: right; font-weight: var(--weight-medium); font-variant-numeric: tabular-nums; }
.month-orders { min-width: 40px; text-align: right; color: var(--el-text-color-secondary); }

.teacher-rank { display: flex; flex-direction: column; }
.rank-row { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) 0; border-bottom: 1px solid var(--el-border-color-lighter); font-size: var(--text-sm); }
.rank-row:last-child { border-bottom: none; }
.rank-num { width: 20px; text-align: center; font-weight: var(--weight-bold); color: var(--el-text-color-secondary); }
.rank-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-revenue { min-width: 80px; text-align: right; font-weight: var(--weight-medium); font-variant-numeric: tabular-nums; }
.rank-orders { min-width: 40px; text-align: right; color: var(--el-text-color-secondary); }
</style>
