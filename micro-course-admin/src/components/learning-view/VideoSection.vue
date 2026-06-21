<!--
  视频播放区（Round 11-3 从 LearningView.vue 拆分）
  VideoSection: 自定义视频播放器 + 控制栏 + 上/下一节导航
  视频播放状态自包含；通过 emit 将 currentTime / isPlaying / ended 同步给父级用于进度保存
  Author: jackie
-->
<template>
  <div class="video-section">
    <div class="video-container" @mousemove="onControlsMouseMove">
      <!-- 加载骨架屏 -->
      <div v-if="videoLoading" class="video-skeleton">
        <div class="skeleton-shimmer" />
        <el-icon class="loading-icon" size="48"><Loading /></el-icon>
      </div>
      <!-- 视频元素 -->
      <video
        v-else-if="currentVideo && !videoError"
        ref="videoRef"
        class="video-player"
        :src="currentVideo.url || currentVideo.playUrl"
        :poster="currentVideo.coverUrl"
        @loadedmetadata="onVideoLoaded"
        @timeupdate="onTimeUpdate"
        @play="onPlay"
        @pause="onPause"
        @ended="onVideoEnded"
        @waiting="isBuffering = true"
        @canplay="isBuffering = false"
        @error="onVideoError"
        playsinline
      />
      <!-- 缓冲提示 -->
      <div v-if="isBuffering && currentVideo && !videoError" class="buffering-overlay">
        <el-icon class="buffering-spinner" size="36"><Loading /></el-icon>
        <span>缓冲中...</span>
      </div>
      <!-- 视频加载失败 -->
      <div v-else-if="videoError" class="video-error">
        <el-icon size="48" color="#EF4444"><WarningFilled /></el-icon>
        <p>视频加载失败，请重试</p>
        <el-button type="primary" size="small" @click="retryVideo">重试</el-button>
      </div>
      <!-- 无视频占位 -->
      <div v-else class="video-empty">
        <el-icon size="48" color="#475569"><VideoCamera /></el-icon>
        <p>本章节暂无视频</p>
      </div>

      <!-- 自定义控制栏 -->
      <div v-if="currentVideo && !videoLoading && !videoError" class="video-controls" :class="{ visible: showControls || !isPlaying }">
        <!-- 进度条 -->
        <div class="progress-wrap" role="slider" tabindex="0" :aria-label="`视频进度 当前 ${Math.round(playPercent)}%`" :aria-valuemin="0" :aria-valuemax="100" :aria-valuenow="Math.round(playPercent)" @click="seekVideo" @mousemove="onProgressHover" @mouseleave="hoverTime = null" @keydown.left.prevent="seekRelative(-5)" @keydown.right.prevent="seekRelative(5)">
          <div class="progress-track">
            <div class="progress-buffered" :style="{ width: bufferedPercent + '%' }" />
            <div class="progress-filled" :style="{ width: playPercent + '%' }" />
            <div class="progress-thumb" :style="{ left: playPercent + '%' }" />
          </div>
          <div v-if="hoverTime !== null" class="hover-time" :style="{ left: hoverX + 'px' }">{{ formatTime(hoverTime) }}</div>
        </div>

        <!-- 控制按钮行 -->
        <div class="controls-row">
          <div class="controls-left">
            <!-- 播放/暂停 -->
            <button class="ctrl-btn play-btn" @click="togglePlay">
              <el-icon size="22"><component :is="isPlaying ? 'VideoPause' : 'VideoPlay'" /></el-icon>
            </button>
            <!-- 时间显示 -->
            <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
          </div>
          <div class="controls-right">
            <!-- 倍速选择 -->
            <el-dropdown trigger="click" @command="setPlaybackRate">
              <button class="ctrl-btn speed-btn">
                {{ playbackRate }}x
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="rate in playbackRates" :key="rate" :command="rate" :active="playbackRate === rate">
                    {{ rate }}x
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <!-- 全屏 -->
            <button class="ctrl-btn" @click="toggleFullscreen" title="全屏">
              <el-icon size="18"><FullScreen /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 上一节 / 下一节 导航 -->
    <div class="lesson-nav">
      <el-button
        v-if="prevLesson"
        class="nav-btn prev-btn"
        @click="$emit('go-to-lesson', prevLesson)"
       ><el-icon><ArrowLeft /></el-icon>
         上一节: {{ prevLesson.title }}
      </el-button>
      <div v-else />
      <el-button
        v-if="nextLesson"
        type="primary"
        class="nav-btn next-btn"
        @click="$emit('go-to-lesson', nextLesson)"
      >
        下一节: {{ nextLesson.title }}
        <el-icon><ArrowRight /></el-icon>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import {
  ArrowLeft, ArrowRight, VideoCamera, VideoPlay, VideoPause,
  FullScreen, Loading, WarningFilled
} from '@element-plus/icons-vue'

const props = defineProps({
  currentVideo: { type: Object, default: null },
  initialPosition: { type: Number, default: 0 },
  prevLesson: { type: Object, default: null },
  nextLesson: { type: Object, default: null }
})

const emit = defineEmits(['time-update', 'playing-change', 'ended', 'go-to-lesson'])

// ==================== 视频播放器状态 ====================
const videoRef = ref(null)
const isPlaying = ref(false)
const isBuffering = ref(false)
const showControls = ref(true)
const currentTime = ref(0)
const duration = ref(0)
const bufferedPercent = ref(0)
const hoverTime = ref(null)
const hoverX = ref(0)
const playbackRate = ref(1)
const playbackRates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const videoLoading = ref(true)
const videoError = ref(false)
let controlsTimer = null

const playPercent = computed(() => {
  return duration.value > 0 ? (currentTime.value / duration.value) * 100 : 0
})

// ==================== 工具函数 ====================
function formatTime(seconds) {
  if (!seconds || isNaN(seconds)) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// ==================== 视频切换：重置状态 ====================
// 等价于原 LearningView.selectLesson 中的视频重置逻辑
watch(() => props.currentVideo, () => {
  videoLoading.value = true
  videoError.value = false
  isPlaying.value = false
  emit('playing-change', false)
  currentTime.value = 0
  emit('time-update', 0)
  duration.value = 0
  // 播放进度恢复由 onVideoLoaded() 处理
  nextTick(() => { videoLoading.value = false })
})

// ==================== 视频控制 ====================
function togglePlay() {
  if (!videoRef.value) return
  if (isPlaying.value) {
    videoRef.value.pause()
  } else {
    videoRef.value.play().catch(() => { /* 忽略导航离开时的 AbortError */ })
  }
}

function onPlay() {
  isPlaying.value = true
  emit('playing-change', true)
}

function onPause() {
  isPlaying.value = false
  emit('playing-change', false)
}

function onVideoLoaded() {
  duration.value = videoRef.value.duration
  videoLoading.value = false
  // 恢复播放位置（由父级通过 initialPosition 传入）
  if (props.initialPosition) {
    videoRef.value.currentTime = props.initialPosition
  }
}

function onTimeUpdate() {
  if (!videoRef.value) return
  currentTime.value = videoRef.value.currentTime
  emit('time-update', currentTime.value)
  // 更新 buffer
  if (videoRef.value.buffered.length > 0) {
    bufferedPercent.value = (videoRef.value.buffered.end(videoRef.value.buffered.length - 1) / duration.value) * 100
  }
}

function onVideoEnded() {
  isPlaying.value = false
  emit('playing-change', false)
  // 标记完成（由父级处理）
  emit('ended')
}

function seekVideo(e) {
  if (!videoRef.value || !duration.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  const ratio = (e.clientX - rect.left) / rect.width
  videoRef.value.currentTime = ratio * duration.value
}

function onProgressHover(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  hoverTime.value = ratio * duration.value
  hoverX.value = e.clientX - rect.left
}

function setPlaybackRate(rate) {
  playbackRate.value = rate
  if (videoRef.value) videoRef.value.playbackRate = rate
}

function toggleFullscreen() {
  const container = document.querySelector('.video-container')
  if (!container) return
  if (!document.fullscreenElement) {
    container.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

function onControlsMouseMove() {
  showControls.value = true
  clearTimeout(controlsTimer)
  controlsTimer = setTimeout(() => {
    if (isPlaying.value) showControls.value = false
  }, 3000)
}

// P0-6: 视频加载错误处理
function onVideoError() {
  videoLoading.value = false
  videoError.value = true
}

function retryVideo() {
  videoError.value = false
  isPlaying.value = false
  emit('playing-change', false)
  currentTime.value = 0
  emit('time-update', 0)
  duration.value = 0
  videoLoading.value = true
  nextTick(() => { videoLoading.value = false })
}

function seekRelative(delta) {
  if (!videoRef.value) return
  videoRef.value.currentTime = Math.max(0, Math.min(duration.value, videoRef.value.currentTime + delta))
}

onUnmounted(() => {
  clearTimeout(controlsTimer)
})
</script>

<style scoped>
.video-section {
  --color-primary: #6366F1;

  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.video-container {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #0F172A;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-md);
}

.video-player {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0F172A;
  display: block;
}

.video-skeleton {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-text-color-primary);
  position: relative;
  overflow: hidden;
}
.skeleton-shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.05) 50%, transparent 100%);
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer {
  from { transform: translateX(-100%); }
  to { transform: translateX(100%); }
}
.loading-icon { color: rgba(255,255,255,0.3); animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.video-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  background: linear-gradient(145deg, var(--el-text-color-primary), #0F172A);
  color: var(--el-text-color-secondary);
}

/* P0-6: 视频加载失败状态 */
.video-error {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  background: linear-gradient(145deg, var(--el-text-color-primary), #0F172A);
  color: var(--el-color-danger);
}
.video-error p {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
  margin: var(--space-1) 0 var(--space-2);
}

/* P2-2: 缓冲提示 */
.buffering-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  color: rgba(255,255,255,0.7);
  font-size: var(--text-sm);
  pointer-events: none;
  z-index: 10;
}
.buffering-spinner {
  animation: spin 1s linear infinite;
}

/* ===== 自定义控制栏 ===== */
.video-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: var(--space-3) var(--space-4) var(--space-2-5);
  background: linear-gradient(transparent, rgba(0,0,0,0.8));
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out);
}
.video-controls.visible,
.video-container:hover .video-controls { opacity: 1; }

.progress-wrap {
  position: relative;
  padding: var(--space-2) 0;
  cursor: pointer;
}
.progress-track {
  position: relative;
  height: 4px;
  background: rgba(255,255,255,0.2);
  border-radius: 2px;
  overflow: visible;
}
.progress-buffered {
  position: absolute;
  height: 100%;
  background: rgba(255,255,255,0.35);
  border-radius: 2px;
  transition: width 0.3s;
}
.progress-filled {
  position: absolute;
  height: 100%;
  background: var(--color-primary);
  border-radius: 2px;
  transition: width 0.1s;
}
.progress-thumb {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  background: white;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 4px rgba(0,0,0,0.4);
  transition: left 0.1s;
}
.hover-time {
  position: absolute;
  bottom: 24px;
  transform: translateX(-50%);
  background: rgba(0,0,0,0.8);
  color: white;
  font-size: var(--text-xs);
  padding: 2px var(--space-1-5);
  border-radius: var(--radius-sm);
  pointer-events: none;
  white-space: nowrap;
}

.controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--space-1);
}
.controls-left,
.controls-right { display: flex; align-items: center; gap: var(--space-2-5); }

.ctrl-btn {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  padding: var(--space-1);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  transition: opacity var(--duration-base) var(--ease-out);
}
.ctrl-btn:hover { opacity: 0.8; }
.play-btn { padding: var(--space-1) var(--space-2); }
.speed-btn {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  padding: var(--space-1) var(--space-2);
  background: rgba(255,255,255,0.15);
  border-radius: var(--radius-sm);
}
.time-display {
  font-size: var(--text-sm);
  color: rgba(255,255,255,0.9);
  font-variant-numeric: tabular-nums;
}

/* 课时导航 */
.lesson-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
}
.nav-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  font-size: var(--text-sm);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .video-container { border-radius: var(--radius-md); }
  .lesson-nav .nav-btn { font-size: var(--text-xs); }
}
</style>
