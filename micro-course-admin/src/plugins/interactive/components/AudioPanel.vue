<!--
  AudioPanel.vue · 单脚本音频列表面板 (AudioManager 子组件)

  列出某个 script 的所有音频版本, 支持:
  1. 试听 (点 ▶️ 播放)
  2. A/B 对比 (同时显示多个 audio,可切换播放源)
  3. 状态显示 (GENERATING/READY/FAILED)
  4. 时长显示

  Props:
    courseId, scriptId, tokenLoader(fn), audioUrlFactory(fn), audioStatus(fn)
-->
<template>
  <div class="audio-panel">
    <div v-if="loading" class="ap-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      加载音频列表...
    </div>
    <el-empty v-else-if="audios.length === 0" description="暂无音频,点击右上角'生成新音频'" :image-size="80" />
    <div v-else class="ap-list">
      <div
        v-for="audio in audios"
        :key="audio.id"
        class="ap-item"
        :class="{ 'ap-active': playingId === audio.id }"
      >
        <div class="ap-meta">
          <el-tag :type="statusType(audio.status)" size="small">{{ statusLabel(audio) }}</el-tag>
          <span class="ap-voice">{{ audio.voiceUsed }}</span>
          <span class="ap-model">{{ audio.modelUsed }}</span>
          <span v-if="audio.audioDurationMs" class="ap-duration">
            {{ formatDuration(audio.audioDurationMs) }}
          </span>
          <span v-if="audio.fileSizeBytes" class="ap-size">
            {{ formatSize(audio.fileSizeBytes) }}
          </span>
        </div>
        <div class="ap-controls">
          <el-button
            v-if="audio.status === 'READY'"
            :icon="playingId === audio.id ? VideoPause : VideoPlay"
            size="small"
            type="primary"
            plain
            @click="togglePlay(audio)"
          >
            {{ playingId === audio.id ? '暂停' : '试听' }}
          </el-button>
          <span v-else-if="audio.status === 'GENERATING'" class="ap-pending">
            <el-icon class="is-loading"><Loading /></el-icon>
            生成中
          </span>
          <span v-else class="ap-failed">失败</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, VideoPlay, VideoPause } from '@element-plus/icons-vue'

const props = defineProps({
  courseId: { type: Number, required: true },
  scriptId: { type: Number, required: true },
  tokenLoader: { type: Function, required: true },
  audioUrlFactory: { type: Function, required: true },
  audioStatus: { type: Function, default: (a) => a.status }
})

const audios = ref([])
const loading = ref(true)
const playingId = ref(null)
const audioEl = ref(null)

async function load() {
  loading.value = true
  try {
    audios.value = await props.tokenLoader(props.scriptId)
  } finally {
    loading.value = false
  }
}

function togglePlay(audio) {
  if (playingId.value === audio.id) {
    audioEl.value?.pause()
    playingId.value = null
    return
  }
  if (audioEl.value) {
    audioEl.value.pause()
  }
  const url = props.audioUrlFactory(audio)
  audioEl.value = new Audio(url)
  audioEl.value.play().catch(err => {
    // 【BUG #11 修复】 用户可见错误提示 (不只 console.warn)
    console.warn('[AudioPanel] play failed', err)
    ElMessage.error('试听失败: ' + (err?.message || '音频加载失败,请检查网络或重试'))
    playingId.value = null
  })
  audioEl.value.onerror = () => {
    ElMessage.error('音频文件损坏或不存在 (token: ' + audio.audioToken?.substring(0, 8) + '...)')
    playingId.value = null
  }
  playingId.value = audio.id
  audioEl.value.onended = () => { playingId.value = null }
}

function statusType(status) {
  return { GENERATING: 'warning', READY: 'success', FAILED: 'danger' }[status] || 'info'
}

function statusLabel(audio) {
  return props.audioStatus(audio)
}

function formatDuration(ms) {
  const s = Math.floor(ms / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

onMounted(load)
onUnmounted(() => {
  audioEl.value?.pause()
})
</script>

<style scoped>
.audio-panel { width: 100%; }
.ap-loading { display: flex; gap: 8px; padding: 20px; align-items: center; color: var(--el-text-color-secondary); }
.ap-list { display: flex; flex-direction: column; gap: 8px; }
.ap-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 14px; border-radius: 6px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.2s;
}
.ap-item:hover { border-color: var(--el-color-primary); }
.ap-item.ap-active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.ap-meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.ap-voice { font-weight: 500; }
.ap-model { font-size: 12px; color: var(--el-text-color-secondary); }
.ap-duration, .ap-size { font-size: 12px; color: var(--el-text-color-secondary); }
.ap-controls { display: flex; gap: 8px; align-items: center; }
.ap-pending { display: inline-flex; gap: 4px; align-items: center; color: var(--el-color-warning); font-size: 13px; }
.ap-failed { color: var(--el-color-danger); font-size: 13px; }
</style>