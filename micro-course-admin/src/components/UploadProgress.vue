<!--
  D2: 全局上传进度浮窗
  显示在页面右下角，由 request.js 中的 globalUploadState 驱动
  任何 FormData 上传都会自动显示进度
-->
<template>
  <Teleport to="body">
    <Transition name="upload-progress">
      <div v-if="visible" class="upload-progress-float" role="status" aria-live="polite">
        <div class="progress-card">
          <div class="progress-header">
            <el-icon class="progress-icon" :class="{ 'is-done': state.percent >= 100 }">
              <Loading v-if="state.percent < 100" class="icon-spin" />
              <CircleCheck v-else />
            </el-icon>
            <span class="progress-title">{{ state.percent >= 100 ? '上传完成' : '正在上传' }}</span>
          </div>
          <div v-if="state.fileName" class="progress-file">{{ state.fileName }}</div>
          <el-progress
            :percentage="state.percent"
            :status="state.percent >= 100 ? 'success' : undefined"
            :stroke-width="6"
            :duration="0.3"
            class="progress-bar"
          />
          <div class="progress-percent">{{ state.percent }}%</div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
/**
 * D2: 全局上传进度浮窗
 * 自动捕获 request.js 中 globalUploadState 的变化
 */
import { computed, watch, ref } from 'vue'
import { Loading, CircleCheck } from '@element-plus/icons-vue'
import { globalUploadState } from '@/utils/request'

// 从全局状态读取
const state = globalUploadState

// 可见性逻辑：上传中或刚完成时显示
const visible = ref(false)
let hideTimer = null

watch(
  () => state.active,
  (active) => {
    if (active) {
      visible.value = true
      if (hideTimer) clearTimeout(hideTimer)
    }
  }
)

watch(
  () => state.percent,
  (pct) => {
    if (pct >= 100) {
      // 上传完成后延迟隐藏
      if (hideTimer) clearTimeout(hideTimer)
      hideTimer = setTimeout(() => {
        visible.value = false
      }, 2000)
    }
  }
)
</script>

<style scoped>
.upload-progress-float {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  animation: slideInUp var(--duration-normal) var(--ease-out);
}

.progress-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  min-width: 260px;
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(12px);
}

.progress-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.progress-icon {
  font-size: 18px;
  color: var(--role-primary);
}

.progress-icon.is-done {
  color: var(--el-color-success);
}

.progress-title {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
}

.progress-file {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-bar {
  margin-bottom: var(--space-1);
}

.progress-percent {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.icon-spin {
  animation: iconRotate 1s linear infinite;
}

@keyframes iconRotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes slideInUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

/* 渐隐过渡 */
.upload-progress-enter-active {
  transition: all var(--duration-normal) var(--ease-out);
}
.upload-progress-leave-active {
  transition: all var(--duration-slow) var(--ease-in);
}
.upload-progress-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.upload-progress-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
