<!--
  系统设置页面
  /admin/settings
  Phase 7 - 零信任修复 R4
-->
<template>
  <div class="admin-settings-container">
    <!-- 顶部说明卡片 -->
    <el-card class="info-card shadow-hover" shadow="never">
      <div class="info-content">
        <el-icon :size="20" class="info-icon"><InfoFilled /></el-icon>
        <div>
          <p class="info-title">系统设置管理</p>
          <p class="info-desc">管理平台运行时配置参数，修改后即时生效。</p>
        </div>
      </div>
    </el-card>

    <!-- 设置表格 -->
    <el-card class="settings-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>配置项列表</span>
          <el-button type="primary" size="small" :loading="saving" @click="handleSave">保存修改</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="settingsList" stripe border class="data-table">
        <el-table-column prop="settingKey" label="配置键" min-width="180" show-overflow-tooltip />
        <el-table-column prop="settingValue" label="配置值" min-width="200">
          <template #default="{ row }">
            <el-input
              v-model="row.settingValue"
              size="small"
              placeholder="请输入值"
              @change="handleValueChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="300" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.dirty" type="warning" size="small">已修改</el-tag>
            <el-tag v-else type="info" size="small">正常</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && settingsList.length === 0" description="暂无配置项" />
    </el-card>
  </div>
</template>

<script setup>
/**
 * 系统设置页面
 * Vue 3.4 Composition API + script setup
 * @author Claude Code Agent
 */
import { ref, reactive, onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSettings, updateSettings } from '@/api/admin-settings'

// 加载状态
const loading = ref(false)
const saving = ref(false)

// 设置列表
const settingsList = ref([])

// 脏数据标记
const dirtyKeys = new Set()

/**
 * 获取设置列表
 */
async function fetchSettings() {
  loading.value = true
  try {
    const res = await getSettings()
    // 后端返回 { items: [...] } 或直接是数组
    const items = res.data?.items || res.data || []
    settingsList.value = items.map(item => ({
      ...item,
      dirty: false
    }))
  } catch {
    ElMessage.error('获取设置列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 值变化时标记脏数据
 */
function handleValueChange(row) {
  row.dirty = true
  dirtyKeys.add(row.settingKey)
}

/**
 * 保存修改
 */
async function handleSave() {
  // 收集所有修改项
  const updates = settingsList.value
    .filter(item => item.dirty)
    .map(item => ({
      settingKey: item.settingKey,
      settingValue: item.settingValue
    }))

  if (updates.length === 0) {
    ElMessage.warning('没有修改的内容')
    return
  }

  saving.value = true
  try {
    await updateSettings(updates)
    ElMessage.success('保存成功')
    // 刷新列表
    await fetchSettings()
    dirtyKeys.clear()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchSettings()
})
</script>

<style scoped>
.admin-settings-container {
  padding: 20px;
}

.info-card {
  margin-bottom: 16px;
}

.info-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.info-icon {
  color: var(--el-color-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.info-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.info-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.settings-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .admin-settings-container {
    padding: 12px;
  }

  .info-card {
    margin-bottom: 12px;
  }

  .settings-card {
    margin-bottom: 12px;
  }
}

.data-table { width: 100%; }
</style>