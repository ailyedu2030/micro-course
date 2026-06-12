<!--
  视频播放器
  路由路径: /student/video/:id
  Phase 2
  Author: jackie
-->
<template>
  <div class="video-player-page">
    <div class="player-wrapper" :class="{ 'mini-mode': isMiniMode }">
      <!-- 骨架屏 -->
      <div v-if="loading" class="skeleton-container">
        <el-skeleton :rows="8" animated />
      </div>

      <!-- 视频容器 -->
      <div v-show="!loading" class="video-container" :class="{ 'mini-mode': isMiniMode }">
        <video
          ref="videoRef"
          class="video-element"
          @canplay="onCanPlay"
          @timeupdate="onTimeUpdate"
          @ended="onEnded"
          @error="onError"
          @dblclick="handleDoubleTap"
          @touchstart="onTouchStart"
          @touchend="onTouchEnd"
        ></video>

        <!-- 顶部控制栏 -->
        <div class="top-controls">
          <div class="title-bar">
            <span class="video-title">{{ videoData.title || '视频加载中...' }}</span>
          </div>
          <div class="speed-control">
            <el-select
              v-model="playbackRate"
              size="small"
              placeholder="倍速"
              @change="changeSpeed"
            >
              <el-option label="0.75x" :value="0.75" />
              <el-option label="1x" :value="1" />
              <el-option label="1.25x" :value="1.25" />
              <el-option label="1.5x" :value="1.5" />
              <el-option label="2x" :value="2" />
            </el-select>
          </div>
        </div>

        <!-- 底部控制栏 -->
        <div class="bottom-controls">
          <div class="progress-bar-container">
            <span class="time-display">{{ formatTime(currentTime) }}</span>
            <el-slider
              v-model="sliderTime"
              :max="duration"
              :format-tooltip="formatTime"
              @change="onSliderChange"
            />
            <span class="time-display">{{ formatTime(duration) }}</span>
          </div>
          <div class="control-buttons">
            <el-button
              :icon="isMiniMode ? 'Close' : 'FullScreen'"
              size="small"
              circle
              @click="toggleFullscreen"
            >
              {{ isMiniMode ? '还原' : '全屏' }}
            </el-button>
            <el-button
              v-if="isMiniMode"
              size="small"
              circle
              @click="exitMiniMode"
            >
              关闭
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 小窗模式提示 -->
    <transition name="fade">
      <div v-if="showResumeTip" class="resume-tip">
        <span>上次看到 {{ formatTime(resumePosition) }}</span>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import Hls from 'hls.js'
import { getVideoById } from '@/api/video'
import request from '@/utils/request'

const props = defineProps({
  videoId: {
    type: [Number, String],
    required: true
  },
  userId: {
    type: [Number, String],
    default: null
  },
  courseId: {
    type: [Number, String],
    default: null
  },
  chapterId: {
    type: [Number, String],
    default: null
  }
})

// DOM refs
const videoRef = ref(null)

// State
const loading = ref(true)
const videoData = ref({})
const hlsInstance = ref(null)
const playbackRate = ref(1)
const currentTime = ref(0)
const duration = ref(0)
const sliderTime = ref(0)
const isMiniMode = ref(false)
const showResumeTip = ref(false)
const resumePosition = ref(0)

// Touch gesture state
const lastTouchX = ref(0)
const touchStartTime = ref(0)
const tapCount = ref(0)
let tapTimer = null

// Progress record ID from server, used for PUT reporting
const progressId = ref(null)

// Progress reporting interval
let progressInterval = null
let lastReportedProgress = 0

// Check if URL is HLS
const isHLS = (url) => {
  return url && (url.endsWith('.m3u8') || url.includes('.m3u8'))
}

// Load video
const loadVideo = async () => {
  try {
    loading.value = true
    const res = await getVideoById(props.videoId)
    videoData.value = res.data || res

    await nextTick()
    initPlayer()
    await loadProgress()
  } catch {
    ElMessage.error('视频加载失败')
  } finally {
    loading.value = false
  }
}

// Initialize player
const initPlayer = () => {
  const video = videoRef.value
  const url = videoData.value.hls_url || videoData.value.url

  if (!url) {
    ElMessage.error('视频地址无效')
    return
  }

  if (isHLS(url)) {
    if (Hls.isSupported()) {
      hlsInstance.value = new Hls()
      hlsInstance.value.loadSource(url)
      hlsInstance.value.attachMedia(video)
      hlsInstance.value.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => {})
      })
      hlsInstance.value.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          ElMessage.error('视频播放出错')
        }
      })
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari native HLS
      video.src = url
      video.play().catch(() => {})
    }
  } else {
    video.src = url
    video.load()
  }
}

// Load progress from server (returns List, find by chapterId)
const loadProgress = async () => {
  if (!props.userId || !props.courseId) return

  try {
    const res = await request({
      method: 'GET',
      url: '/learning-progress/progress',
      params: {
        userId: props.userId,
        courseId: props.courseId
      }
    })

    const list = res.data || []
    const progressData = Array.isArray(list)
      ? list.find(p => Number(p.chapterId) === Number(props.chapterId)) || {}
      : {}

    if (progressData.id) {
      progressId.value = progressData.id
    }

    if (progressData.videoPosition > 0) {
      resumePosition.value = progressData.videoPosition
      showResumeTip.value = true
      setTimeout(() => {
        showResumeTip.value = false
      }, 3000)

      const video = videoRef.value
      if (video) {
        const setPosition = () => {
          if (video.duration) {
            video.currentTime = progressData.videoPosition
            video.removeEventListener('loadedmetadata', setPosition)
          }
        }
        video.addEventListener('loadedmetadata', setPosition)
      }
    }
  } catch {
    // Silently ignore progress loading failure
  }
}

const ensureProgressRecord = async () => {
  if (progressId.value) return true
  if (!props.userId || !props.courseId) return false

  try {
    const res = await request({
      method: 'POST',
      url: '/learning-progress/progress',
      data: {
        userId: props.userId,
        courseId: props.courseId,
        chapterId: props.chapterId,
        videoPosition: 0,
        videoProgress: 0
      }
    })
    progressId.value = (res.data || res).id
    return !!progressId.value
  } catch {
    return false
  }
}

// Report progress every 10 seconds
const startProgressReporting = () => {
  progressInterval = setInterval(async () => {
    const video = videoRef.value
    if (!video || !video.duration || video.paused) return

    const current = video.currentTime
    const total = video.duration
    const progressPercent = (current / total) * 100

    if (Math.abs(progressPercent - lastReportedProgress) < 1) return

    lastReportedProgress = progressPercent

    try {
      const hasRecord = await ensureProgressRecord()
      if (!hasRecord) return

      await request({
        method: 'PUT',
        url: `/learning-progress/progress/${progressId.value}`,
        data: {
          videoPosition: Math.floor(current),
          videoProgress: Math.round(progressPercent)
        }
      })
    } catch {
      // Silently ignore progress reporting failure
    }
  }, 10000)
}

// Event handlers
const onCanPlay = () => {
  const video = videoRef.value
  if (video) {
    duration.value = video.duration
  }
}

const onTimeUpdate = () => {
  const video = videoRef.value
  if (video) {
    currentTime.value = video.currentTime
    sliderTime.value = video.currentTime
  }
}

const onEnded = async () => {
  try {
    const hasRecord = await ensureProgressRecord()
    if (hasRecord) {
      await request({
        method: 'PUT',
        url: `/learning-progress/progress/${progressId.value}`,
        data: {
          videoPosition: Math.floor(duration.value),
          videoProgress: 100
        }
      })
    }
  } catch {
    // Silently ignore
  }
  ElMessage.success('视频播放完成')
}

const onError = () => {
  ElMessage.error('视频播放出错')
}

const changeSpeed = () => {
  const video = videoRef.value
  if (video) {
    video.playbackRate = playbackRate.value
  }
}

const onSliderChange = (val) => {
  const video = videoRef.value
  if (video) {
    video.currentTime = val
  }
}

const toggleFullscreen = async () => {
  const video = videoRef.value
  if (!video) return

  if (!isMiniMode.value) {
    try {
      if (video.requestFullscreen) {
        await video.requestFullscreen()
      } else if (video.webkitRequestFullscreen) {
        await video.webkitRequestFullscreen()
      }
    } catch {
      // Fallback to mini mode
      isMiniMode.value = true
    }
  } else {
    isMiniMode.value = false
  }
}

const exitMiniMode = () => {
  isMiniMode.value = false
}

// Touch gesture handlers
const onTouchStart = (e) => {
  const touch = e.touches ? e.touches[0] : e
  lastTouchX.value = touch.clientX
  touchStartTime.value = Date.now()
}

const onTouchEnd = (e) => {
  const touch = e.changedTouches ? e.changedTouches[0] : e
  const deltaX = touch.clientX - lastTouchX.value
  const duration_ms = Date.now() - touchStartTime.value

  // Only treat as swipe if it's fast (< 500ms) and significant (> 50px)
  if (Math.abs(deltaX) > 50 && duration_ms < 500) {
    const video = videoRef.value
    if (video) {
      if (deltaX < 0) {
        // Left swipe - forward 10s
        video.currentTime = Math.min(video.currentTime + 10, video.duration)
      } else {
        // Right swipe - backward 10s
        video.currentTime = Math.max(video.currentTime - 10, 0)
      }
    }
  }
}

const handleDoubleTap = (e) => {
  const video = videoRef.value
  if (!video) return

  // Use tap timer to distinguish double-tap from single tap
  tapCount.value++
  if (tapCount.value === 1) {
    tapTimer = setTimeout(() => {
      tapCount.value = 0
    }, 300)
  } else if (tapCount.value === 2) {
    clearTimeout(tapTimer)
    tapCount.value = 0

    const rect = video.getBoundingClientRect()
    const tapX = e.clientX - rect.left
    const halfWidth = rect.width / 2

    if (tapX < halfWidth) {
      // Left half - rewind 10s
      video.currentTime = Math.max(video.currentTime - 10, 0)
    } else {
      // Right half - forward 10s
      video.currentTime = Math.min(video.currentTime + 10, video.duration)
    }
  }
}

const formatTime = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// Cleanup
const cleanup = () => {
  if (hlsInstance.value) {
    hlsInstance.value.destroy()
    hlsInstance.value = null
  }
  if (progressInterval) {
    clearInterval(progressInterval)
    progressInterval = null
  }
}

onMounted(async () => {
  await nextTick()
  loadVideo()
  startProgressReporting()
})

onBeforeUnmount(() => {
  cleanup()
})
</script>

<style scoped>
.video-player-page {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.player-wrapper {
  max-width: 960px;
  margin: 0 auto;
}

.skeleton-container {
  background: #1a1a1a;
  border-radius: 8px;
  padding: 40px;
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-container {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
}

.video-container.mini-mode {
  position: fixed;
  bottom: 20px;
  right: 20px;
  width: 320px;
  height: 180px;
  z-index: 9999;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.video-element {
  width: 100%;
  height: 100%;
  display: block;
}

.top-controls {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.7), transparent);
  z-index: 10;
}

.title-bar {
  flex: 1;
  min-width: 0;
}

.video-title {
  color: #f5f5f5;
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.speed-control {
  margin-left: 12px;
}

.speed-control :deep(.el-select) {
  width: 80px;
}

.speed-control :deep(.el-input__wrapper) {
  background-color: rgba(0, 0, 0, 0.5);
  border: none;
}

.speed-control :deep(.el-input__inner) {
  color: #f5f5f5;
}

.bottom-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
  z-index: 10;
}

.progress-bar-container {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.time-display {
  color: #f5f5f5;
  font-size: 12px;
  min-width: 45px;
  font-family: 'Helvetica Neue', monospace;
}

.control-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.control-buttons :deep(.el-button) {
  background-color: rgba(255, 255, 255, 0.2);
  border: none;
  color: #f5f5f5;
}

.control-buttons :deep(.el-button:hover) {
  background-color: rgba(255, 255, 255, 0.3);
}

.resume-tip {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  background-color: rgba(0, 0, 0, 0.8);
  color: #f5f5f5;
  padding: 10px 20px;
  border-radius: 20px;
  font-size: 14px;
  z-index: 9999;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* Mobile responsive */
@media (max-width: 768px) {
  .video-player-page {
    padding: 0;
  }

  .player-wrapper {
    max-width: 100%;
  }

  .video-container {
    border-radius: 0;
  }

  /* Portrait mode: 9:16 vertical video */
  @media (orientation: portrait) and (max-width: 768px) {
    .video-container:not(.mini-mode) {
      aspect-ratio: 9 / 16;
      max-height: 100vh;
    }
  }

  .video-container.mini-mode {
    width: 200px;
    height: 112px;
    bottom: 10px;
    right: 10px;
  }

  .top-controls {
    padding: 8px 12px;
  }

  .video-title {
    font-size: 12px;
  }

  .bottom-controls {
    padding: 8px 12px;
  }

  .time-display {
    font-size: 10px;
    min-width: 35px;
  }

  .speed-control :deep(.el-select) {
    width: 70px;
  }
}
</style>