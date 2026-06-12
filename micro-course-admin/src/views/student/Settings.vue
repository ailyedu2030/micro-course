<!--
  偏好设置
  路由路径: /student/settings
  Phase 9.1
  Author: Claude Code Agent
-->
<template>
  <div class="settings-container">
    <div class="header">
      <h2>偏好设置</h2>
    </div>

    <el-card class="settings-card" shadow="never">
      <el-form label-width="120px" label-position="left">
        <el-form-item label="播放倍速">
          <el-select v-model="settings.playbackSpeed" @change="handleSave">
            <el-option label="0.75x" value="0.75" />
            <el-option label="1.0x" value="1" />
            <el-option label="1.25x" value="1.25" />
            <el-option label="1.5x" value="1.5" />
            <el-option label="2.0x" value="2" />
          </el-select>
        </el-form-item>

        <el-form-item label="通知提醒">
          <el-switch
            v-model="settings.notificationEnabled"
            active-text="开"
            inactive-text="关"
            @change="handleSave"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSave">保存设置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const STORAGE_KEY = 'micro_course_settings'

const settings = ref({
  playbackSpeed: '1',
  notificationEnabled: true
})

const loadSettings = () => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const parsed = JSON.parse(stored)
      settings.value = {
        playbackSpeed: parsed.playbackSpeed || '1',
        notificationEnabled: parsed.notificationEnabled !== false
      }
    }
  } catch {
    settings.value = { playbackSpeed: '1', notificationEnabled: true }
  }
}

const handleSave = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
    ElMessage.success('设置已保存')
  } catch {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.settings-container {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
}

.header {
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.settings-card {
  border-radius: 8px;
  transition: box-shadow 0.2s ease;
}

.settings-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

:deep(.el-button) {
  cursor: pointer;
}

@media (max-width: 768px) {
  .settings-container {
    padding: 12px;
  }

  .header h2 {
    font-size: 18px;
  }

  .settings-card {
    border-radius: 8px;
  }

  :deep(.el-form-item__label) {
    width: 100px;
  }

  :deep(.el-form-item) {
    display: block;
  }

  :deep(.el-button) {
    cursor: pointer;
    width: 100%;
  }
}
</style>
