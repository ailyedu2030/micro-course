<template>
  <div class="system-health">
    <div class="page-header"><h1>系统状态</h1><p class="page-desc">平台运行状况监控</p></div>

    <div v-loading="loading" class="health-grid">
      <el-row :gutter="20">
        <el-col :span="6" v-for="item in healthItems" :key="item.key">
          <el-card shadow="hover" class="health-card" :class="'health-card--' + item.status">
            <div class="hc-icon">
              <el-icon :size="32"><component :is="item.icon" /></el-icon>
            </div>
            <div class="hc-label">{{ item.label }}</div>
            <div class="hc-value">{{ item.value }}</div>
            <div class="hc-status" :class="'hc-status--' + item.status">{{ statusText(item.status) }}</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <el-card class="info-card" v-if="!loading">
      <template #header>
        <span>系统信息</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="版本">
          <el-tag type="primary" effect="plain">v1.19.0</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="运行时间">{{ healthInfo.uptime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="数据库">{{ healthInfo.db === 'OK' ? '已连接' : '异常' }}</el-descriptions-item>
        <el-descriptions-item label="Redis">{{ healthInfo.redis === 'OK' ? '已连接' : '异常' }}</el-descriptions-item>
        <el-descriptions-item label="磁盘使用率">{{ healthInfo.diskUsage || '—' }}</el-descriptions-item>
        <el-descriptions-item label="内存使用率">{{ healthInfo.memUsage || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="info-card">
      <template #header>
        <span>运维操作</span>
      </template>
      <el-row :gutter="16">
        <el-col :span="8">
          <el-button text type="primary" @click="handleViewLogs">操作日志</el-button>
        </el-col>
        <el-col :span="8">
          <el-button text type="primary" @click="handleViewDeployGuide">部署指南</el-button>
        </el-col>
        <el-col :span="8">
          <el-button text type="primary" @click="handleRefresh" :loading="refreshing">刷新状态</el-button>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHealth } from '@/api/admin-stats'
import { Connection, Monitor, Coin, DataBoard } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(true)
const refreshing = ref(false)
const healthInfo = reactive({
  db: '—', redis: '—', disk: '—', mem: '—',
  uptime: '—', diskUsage: '—', memUsage: '—'
})

const statusText = (s) => ({ ok: '正常', warn: '警告', error: '异常', unknown: '未知' }[s] || s)

const healthItems = computed(() => [
  { key: 'db', label: '数据库', value: healthInfo.db, status: healthInfo.db === 'OK' ? 'ok' : 'error', icon: Connection },
  { key: 'redis', label: 'Redis', value: healthInfo.redis, status: healthInfo.redis === 'OK' ? 'ok' : 'error', icon: Monitor },
  { key: 'disk', label: '磁盘', value: healthInfo.diskUsage, status: healthInfo.disk === 'OK' ? 'ok' : healthInfo.disk === 'WARN' ? 'warn' : 'unknown', icon: DataBoard },
  { key: 'mem', label: '内存', value: healthInfo.memUsage, status: healthInfo.mem === 'OK' ? 'ok' : healthInfo.mem === 'WARN' ? 'warn' : 'unknown', icon: Coin },
])

const fetchHealth = async () => {
  try {
    const { data } = await getHealth()
    Object.assign(healthInfo, data || {})
    if (data?.disk) {
      const total = parseFloat(data.diskTotal || '0')
      const free = parseFloat(data.diskFree || '0')
      if (total > 0) healthInfo.diskUsage = `${((total - free) / total * 100).toFixed(1)}%`
    }
    if (data?.mem) {
      if (data.memTotal && data.memFree) {
        healthInfo.memUsage = `${((data.memTotal - data.memFree) / data.memTotal * 100).toFixed(1)}%`
      }
    }
  } catch (e) {
    console.error('[SystemHealth] 加载失败:', e)
  } finally {
    loading.value = false
  }
}

const handleRefresh = async () => {
  refreshing.value = true
  await fetchHealth()
  refreshing.value = false
}

const handleViewLogs = () => router.push('/admin/logs')
const handleViewDeployGuide = () => window.open('/docs/operations/IT-部署指南.md', '_blank')

onMounted(fetchHealth)
</script>

<style scoped>
.system-health { padding: 24px; }
.health-grid { margin: 24px 0; }
.health-card { text-align: center; }
.health-card--ok { border-left: 4px solid #67c23a; }
.health-card--warn { border-left: 4px solid #e6a23c; }
.health-card--error { border-left: 4px solid #f56c6c; }
.health-card--unknown { border-left: 4px solid #909399; }
.hc-icon { margin-bottom: 12px; color: var(--el-text-color-secondary); }
.hc-label { font-size: 14px; color: #666; margin-bottom: 4px; }
.hc-value { font-size: 24px; font-weight: 700; margin-bottom: 8px; }
.hc-status { font-size: 12px; }
.hc-status--ok { color: #67c23a; }
.hc-status--warn { color: #e6a23c; }
.hc-status--error { color: #f56c6c; }
.info-card { margin-top: 24px; }
</style>
