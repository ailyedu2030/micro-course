<!--
  交互式课件播放器
  路由路径: /student/courses/:id/slides/player
  Phase 10
  Author: Phase10-Development-Team
-->
<template>
  <div class="slide-player" ref="playerRef" tabindex="0" @keydown="handleKeydown">
    <!-- Top Bar -->
    <header class="player-header">
      <button class="btn-icon" @click="$router.back()" aria-label="返回">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <div class="header-center">
        <span class="page-counter">{{ current + 1 }}<span class="counter-divider">/</span>{{ pages.length }}</span>
        <div class="page-thumb-strip">
          <button
v-for="(p, i) in pages" :key="i"
            class="thumb-dot" :class="{ active: i === current, 'has-audio': p.audioDuration }"
            @click="goTo(i)" :aria-label="'第' + (i + 1) + '页'"
            :title="pageDurationText(p)"
/>
        </div>
      </div>
      <div class="header-right">
        <button
          class="btn-icon" :class="{ active: showSubtitle }"
          @click="showSubtitle = !showSubtitle" :aria-label="showSubtitle ? '关闭讲述稿字幕' : '开启讲述稿字幕'"
          :title="showSubtitle ? '关闭讲述稿字幕' : '开启讲述稿字幕'"
        >
          <el-icon :size="16"><Document /></el-icon>
        </button>
        <button
class="btn-icon btn-auto" :class="{ active: autoMode }"
          @click="autoMode = !autoMode" :aria-label="autoMode ? '关闭自动播放' : '开启自动播放'"
          :title="autoMode ? '自动播放中' : '手动模式'"
>
          <el-icon :size="16"><VideoPlay v-if="autoMode" /><VideoPause v-else /></el-icon>
        </button>
        <button class="btn-icon" @click="toggleFullscreen" :aria-label="isFullscreen ? '退出全屏' : '全屏'">
          <el-icon :size="16"><FullScreen /></el-icon>
        </button>
      </div>
    </header>

    <!-- Loading / Error State -->
    <div v-if="pageLoading" class="player-loading" style="display:flex;align-items:center;justify-content:center;flex:1;color:var(--el-text-color-secondary)">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon><span style="margin-left:12px">加载幻灯片中...</span>
    </div>
    <div v-else-if="pageError" class="player-loading" style="display:flex;align-items:center;justify-content:center;flex:1;flex-direction:column;gap:12px">
      <span style="color:var(--el-text-color-secondary)">幻灯片加载失败</span>
      <el-button size="small" @click="loadPages">重试</el-button>
    </div>

    <!-- Main Content -->
    <section v-else class="player-main" role="region" aria-label="幻灯片内容">
      <!-- Slide Image Area -->
      <section class="slide-stage" @click="handleStageClick">
        <div class="slide-frame">
          <transition :name="transitionName" mode="out-in">
            <div class="slide-wrapper" :key="current">
              <!-- HTML 课时分支：iframe sandbox 渲染
                   sandbox="allow-scripts": 允许脚本执行（互动课件需要），
                   但未设置 allow-same-origin → iframe 为唯一 origin，
                   脚本无法访问平台 API/cookie/storage/DOM（浏览器原生隔离）。
                   安全论证见 docs/HTML课件播放能力增强要求.md。
                   注：srcdoc 与父页面 postMessage 不受 sandbox 限制 -->
              <iframe
                v-if="currentPage?.contentType === 'HTML_DIRECT' && currentPage?.htmlContent"
                :srcdoc="currentPage.htmlContent"
                sandbox="allow-scripts"
                ref="htmlIframeRef"
                :title="'第' + (current + 1) + '页课件内容'"
                class="slide-iframe"
                :key="'html-' + current"
                :aria-label="'第' + (current + 1) + '页'"
                @error="onHtmlIframeError"
                @load="onHtmlIframeLoad"
              />
              <!-- P2-7: HTML 课时下载按钮（sandbox 禁用右键保存，用临时 Blob 下载源码） -->
              <div v-if="currentPage?.contentType === 'HTML_DIRECT'" class="html-toolbar">
                <el-button size="small" text @click="downloadHtmlPage">
                  <el-icon><Download /></el-icon> 下载 HTML
                </el-button>
              </div>
              <!-- 正常渲染的图片 -->
              <img
                v-else-if="imageUrls[current] && !imageErrors[current]"
                :src="imageUrls[current]" class="slide-image"
                :alt="'第' + (current + 1) + '页'" loading="lazy"
                @error="imageErrors[current] = true"
              />
              <!-- 图片加载失败：占位图 + 重试按钮 -->
              <div v-else class="slide-placeholder">
                <el-icon :size="48" class="placeholder-icon"><PictureFilled /></el-icon>
                <span class="placeholder-text">图片加载失败</span>
                <el-button
                  size="small" type="primary" plain
                  :loading="imageRetrying[current]"
                  :icon="RefreshRight"
                  @click.stop="retryImage(current)"
                >
                  重试加载
                </el-button>
              </div>
              <div class="slide-gradient" />
            </div>
          </transition>

          <!-- Navigation Arrows -->
          <button v-if="current > 0" class="nav-arrow nav-prev" @click.stop="goTo(current - 1)" aria-label="上一页">
            <el-icon :size="24"><ArrowLeft /></el-icon>
          </button>
          <button v-if="current < pages.length - 1" class="nav-arrow nav-next" @click.stop="goTo(current + 1)" aria-label="下一页">
            <el-icon :size="24"><ArrowRight /></el-icon>
          </button>

          <!-- Auto-countdown badge -->
          <transition name="countdown-fade">
            <div v-if="autoCountdown > 0" class="countdown-badge">
              <span class="countdown-ring">{{ autoCountdown }}</span>
            </div>
          </transition>
        </div>
      </section>

      <!-- No narration panel (removed per UX feedback - irrelevant for students) -->
    </section>

    <!-- Bottom Controls -->
    <footer class="player-footer">
      <!-- Interactive Page Completion Mask -->
      <div v-if="interactiveWaiting" class="interactive-mask">
        <div class="interactive-content">
          <Clock :size="28" class="interactive-icon" />
          <p>请点击「完成」按钮继续</p>
          <button class="interactive-btn" @click="handleInteractiveComplete">完成</button>
        </div>
      </div>

      <!-- Audio Status Indicator（P0 R-3：PPT 页同样显示，避免 PENDING/ERROR 零提示） -->
      <div
        class="audio-status-bar"
        v-if="audioStatus !== 'none' || currentPage?.audio || currentPage?.segments?.length"
      >
        <div class="audio-status" :class="audioStatus">
          <span v-if="audioStatus === 'loading'" class="status-loading">
            <el-icon class="is-loading" :size="14"><Loading /></el-icon> 音频加载中...
          </span>
          <button
            v-else-if="audioStatus === 'ready'"
            type="button"
            class="status-ready audio-status-btn"
            aria-label="开始播放当前页面音频"
            @click="togglePlay"
          >
            <el-icon :size="14"><VideoPlay /></el-icon> ▶ 点击开始
          </button>
          <span v-else-if="audioStatus === 'pending'" class="status-pending">
            <el-icon :size="14"><Clock /></el-icon> 等待音频生成{{ pendingTimeoutWarning }}
          </span>
          <span v-else-if="audioStatus === 'error'" class="status-error">
            <el-icon :size="14"><Warning /></el-icon> 音频加载失败
          </span>
        </div>
      </div>

      <div class="control-bar">
        <button class="ctrl-btn" @click="goTo(Math.max(0, current - 1))" :disabled="current === 0" aria-label="上一页">
          <el-icon :size="20"><ArrowLeft /></el-icon>
        </button>
        <button class="ctrl-btn ctrl-btn-play" @click="togglePlay" :disabled="!segmentAudioMode && (audioStatus === 'pending' || audioStatus === 'none')" aria-label="播放/暂停">
          <el-icon :size="24"><VideoPause v-if="playing" /><VideoPlay v-else /></el-icon>
        </button>
        <button class="ctrl-btn" @click="goTo(Math.min(pages.length - 1, current + 1))" :disabled="current >= pages.length - 1" aria-label="下一页">
          <el-icon :size="20"><ArrowRight /></el-icon>
        </button>

        <div class="progress-area">
          <span class="time-label">{{ formatTime(audioTime) }}</span>
          <div
            v-if="audioStatus !== 'pending' && audioStatus !== 'none'"
            class="progress-track"
            role="slider"
            tabindex="0"
            aria-label="音频播放进度"
            :aria-valuemin="0"
            :aria-valuemax="Math.round(audioDuration || 0)"
            :aria-valuenow="Math.round(audioTime || 0)"
            @click="seekAudioByClick"
            @keydown="seekAudioByKeydown"
          >
            <!-- P2-4：HTML 分段边界刻度 -->
            <div v-if="segmentMode && segmentBoundaries.length > 1" class="progress-segments">
              <span
                v-for="(b, i) in segmentBoundaries"
                :key="i"
                class="segment-tick"
                :style="{ left: b + '%' }"
              />
            </div>
            <div class="progress-fill" :style="{ width: audioProgress + '%' }" />
            <div class="progress-thumb" :style="{ left: audioProgress + '%' }" />
          </div>
          <div class="progress-track progress-track--empty" v-else />
          <span class="time-label">{{ formatTime(audioDuration) }}</span>
        </div>

        <div class="speed-group">
          <button
v-for="s in speeds" :key="s"
            class="speed-chip" :class="{ active: speed === s }"
            :aria-label="s + '倍速'"
            @click="speed = s; setSpeed()"
>
            {{ s }}x
          </button>
        </div>
      </div>
    </footer>

    <!-- P3-2：讲述稿字幕跟随 -->
    <transition name="hint-fade">
      <div v-if="showSubtitle && subtitleText" class="subtitle-bar" aria-live="polite">
        <span class="subtitle-label">讲述稿</span>
        <span class="subtitle-text">{{ subtitleText }}</span>
      </div>
    </transition>

    <!-- Hidden Audio -->
    <audio ref="audioRef" @timeupdate="onTimeUpdate" @ended="onAudioEnded" @loadedmetadata="onAudioLoaded" />

    <!-- Keyboard hint (first visit) -->
    <transition name="hint-fade">
      <div
        v-if="showKeyboardHint"
        class="keyboard-hint"
        role="dialog"
        aria-modal="true"
        aria-label="键盘操作提示"
        @click.self="dismissKeyboardHint"
      >
        <div class="hint-card">
          <div class="hint-row"><kbd>←</kbd><kbd>→</kbd> 翻页</div>
          <div class="hint-row"><kbd>Space</kbd> 播放/暂停</div>
          <div class="hint-row"><kbd>F</kbd> 全屏</div>
          <div class="hint-row"><kbd>Esc</kbd> 退出全屏</div>
          <button type="button" class="keyboard-hint-dismiss" @click="dismissKeyboardHint">关闭提示</button>
          <span class="hint-dismiss">点击遮罩或按关闭按钮可退出</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSlidePages } from '@/plugins/interactive/api/slide'
import { evaluateFlow } from '@/plugins/interactive/api/queryCourseware'
import { loadAuthResource, clearImageCache } from '@/utils/authImage'
import { getLearningProgress, createLearningProgress, updateLearningProgress } from '@/api/learning-progress'
import { useUserStore } from '@/store/user'
import { ArrowLeft, ArrowRight, VideoPlay, VideoPause, FullScreen, Loading, RefreshRight, PictureFilled, Download, Clock, Warning, Document } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const courseId = computed(() => route.params.courseId)
const chapterId = computed(() => route.query.chapterId)
// 学习进度仅对 STUDENT 上报；教师/管理员在管理页"预览"打开播放器时不写入进度（后端 hasRole('STUDENT') 会 403）
const isStudent = computed(() => userStore.role === 'STUDENT')

const pages = ref([])
const current = ref(0)
const pageLoading = ref(true)
const pageError = ref(false)
// 讲述稿已移除（对学生无意义）
const autoMode = ref(true)
const playing = ref(false)
const speed = ref(1.0)
const speeds = [0.75, 1.0, 1.25, 1.5, 2.0]
const audioTime = ref(0)
const audioDuration = ref(0)
const audioProgress = ref(0)
const autoCountdown = ref(0)
const transitionName = ref('slide-next')
const showKeyboardHint = ref(false)
const showSubtitle = ref(true)
const playerRef = ref(null)
const audioRef = ref(null)
const imageUrls = ref({})
const audioBlobUrls = ref({})
const imageErrors = reactive({})       // { [pageIndex]: true } 标记哪些图片加载失败
const imageRetrying = reactive({})     // { [pageIndex]: true } 正在重试中
const lastDirection = ref(1)
let countdownTimer = null
let pendingTimer = null
let autoAdvanceTimer = null
let loadingTimer = null
const currentAudioSrcGen = ref(0)

const audioStatus = ref('none') // 'loading' | 'ready' | 'pending' | 'none' | 'error'
const pendingStartTime = ref(null)
const pendingTimeoutWarning = ref('')
const interactiveWaiting = ref(false)  // 当前页是否等待用户点"完成"

const currentPage = computed(() => pages.value[current.value] || null)
const segmentAudioMode = ref(false)  // legacy 兼容标记（保留变量名，v1 HTML 消息处理用）
// P0（方案 §8）：父页 AudioHost 顺序播放 HTML 段；iframe 仅渲染内容与转发事件
const htmlIframeRef = ref(null)
const segments = ref([])             // 当前页 v2 HTML 段 [{index, marker, audio:{url,durationMs}}]
const activeSegmentIndex = ref(0)
const segmentMode = ref(false)       // true = 父页按段顺序播放
const unlocked = ref(false)          // autoplay 解锁（首次用户交互）
let lastStatePush = 0                // 父→iframe 时间消息节流（~4Hz，R-9）
let iframeReadyV2 = false            // v2 握手完成
let courseCompleted = false          // 全部播完（完成态，R-10）

// P3-2：讲述稿字幕（PPT=当前页讲述稿；HTML=当前段讲述稿）
const subtitleText = computed(() => {
  if (segmentMode.value) {
    const seg = segments.value[activeSegmentIndex.value]
    return seg?.scriptText || seg?.text || ''
  }
  return currentPage.value?.narrationScript || ''
})

// P2-4：HTML 段边界百分比刻度（基于各段 durationMs 累计）
const segmentBoundaries = computed(() => {
  const total = segments.value.reduce((sum, s) => sum + (s.audio?.durationMs || 0), 0)
  if (total <= 0) return []
  const out = []
  let acc = 0
  for (let i = 0; i < segments.value.length - 1; i++) {
    acc += (segments.value[i]?.audio?.durationMs || 0)
    out.push(Math.min(99, Math.round((acc / total) * 1000) / 10))
  }
  return out
})

let pageNavLock = false

async function loadPages() {
  pageLoading.value = true
  pageError.value = false
  try {
    const res = await getSlidePages(courseId.value, chapterId.value, sectionId.value)
    pages.value = res.data || []
    // P1I-015: 仅预加载前 3 页和相邻页，其余按需触发（preloadAdjacentImages 懒加载）
    const initialIndices = [0, 1, 2].filter(i => i < pages.value.length)
    await Promise.allSettled(initialIndices.map(idx => loadPageImage(idx)))
  } catch {
    pageError.value = true
    ElMessage.error('加载幻灯片失败')
  } finally {
    pageLoading.value = false
  }
}

async function loadPageImage(idx) {
  const page = pages.value[idx]
  if (!page || imageUrls.value[idx] || imageErrors[idx]) return
  const relUrl = `/courses/${courseId.value}/slides/pages/${page.pageNumber}/image`
  try {
    const blobUrl = await loadAuthResource(relUrl)
    if (blobUrl) {
      imageUrls.value[idx] = blobUrl
      delete imageErrors[idx]
    } else {
      imageErrors[idx] = true
    }
  } catch {
    imageErrors[idx] = true
  }
}

// 重试加载单张图片
async function retryImage(pageIndex) {
  if (imageRetrying[pageIndex]) return
  const page = pages.value[pageIndex]
  if (!page) return
  imageRetrying[pageIndex] = true
  const relUrl = `/courses/${courseId.value}/slides/pages/${page.pageNumber}/image`
  try {
    const blobUrl = await loadAuthResource(relUrl)
    if (blobUrl) {
      imageUrls.value[pageIndex] = blobUrl
      delete imageErrors[pageIndex]
      ElMessage.success('图片加载成功')
    } else {
      ElMessage.error('图片加载失败，请稍后重试')
    }
  } catch {
    ElMessage.error('图片加载失败，请检查网络连接')
  } finally {
    delete imageRetrying[pageIndex]
  }
}

// HTML iframe 事件处理（修复 P0 iframe sandbox 安全配置后的辅助方法）
// 注：sandbox="" 完全禁用 iframe 内 JS，srcdoc 加载失败率极低
function onHtmlIframeLoad() {
  // 加载完成：重置 v2 握手；音频就绪时主动下发段元数据（协议 v2 loaded）
  iframeReadyV2 = false
  if (segmentMode.value && audioStatus.value === 'ready') {
    sendLoadedV2()
  }
}

function onHtmlIframeError() {
  // 加载失败（罕见）：提示用户刷新
  ElMessage.error('HTML 课件加载失败，请刷新重试')
}

// ==================== postMessage 音频控制（协议 v1 兼容 + v2，方案 §6） ====================
// v1（docs/postMessage-音频控制方案.md）：HTML 内 postMessage 指挥平台播放器
// v2（player.js 风格）：ready 握手 / 段级控制 / segment-activated 高亮 / blocked 提示
// 安全校验（R-1/H-1）：sandbox srcdoc iframe 的 MessageEvent.origin 是字符串 "null"
// （opaque origin 序列化，WHATWG html#3585）；同时校验 event.source === 当前 iframe，
// 防同页多 sandbox iframe 伪消息（D9 修复）。

function currentHtmlIframe() {
  return htmlIframeRef.value
}

function sendMessageToHtmlIframe(msg) {
  const iframe = currentHtmlIframe()
  if (!iframe?.contentWindow) return
  iframe.contentWindow.postMessage(msg, '*')
}

function sendStateV2(payload) {
  sendMessageToHtmlIframe({ type: 'slide-audio-state-v2', version: 2, ...payload })
}

function sendSegmentActivated(index) {
  // 协议统一使用 1 基段号（与 data-segment / segment.index 一致）
  sendStateV2({ state: 'segment-activated', index: posToSegIndex(index) })
}

// 数组位置(0 基) ↔ 段号(1 基) 换算
function segIndexToPos(segIndex) {
  if (segIndex == null) return -1
  return segments.value.findIndex(s => s.index === segIndex)
}
function posToSegIndex(pos) {
  return segments.value[pos]?.index ?? pos
}

function sendLoadedV2() {
  if (!segmentMode.value) {
    sendStateV2({ state: 'loaded', segments: [], totalDurationMs: (audioDuration.value || 0) * 1000 })
    return
  }
  sendStateV2({
    state: 'loaded',
    segments: segments.value.map(s => ({
      index: s.index,
      url: s.audio?.url || null,
      durationMs: s.audio?.durationMs || 0
    })),
    totalDurationMs: segments.value.reduce((sum, s) => sum + (s.audio?.durationMs || 0), 0)
  })
}

function pageDurationText(p) {
  const ms = p?.audio?.durationMs || (p?.audioDuration != null ? p.audioDuration * 1000 : 0)
  if (!ms) return ''
  return formatTime(ms / 1000)
}

// P1-1：flow 求值后翻页（NEXT/BRANCH_DEPENDS/SKIP_IF_KNOWN），失败退化为线性
async function advanceToNextPage(expectedGen) {
  const page = currentPage.value
  if (page?.flows?.length && page.id != null && sectionId.value != null) {
    try {
      const res = await evaluateFlow(courseId.value, sectionId.value, {
        currentPageId: page.id,
        userProgress: Math.min(1, (current.value + 1) / Math.max(1, pages.value.length))
      })
      const nextId = res.data?.nextPageId
      if (nextId != null) {
        const idx = pages.value.findIndex(p => p.id === nextId)
        if (idx >= 0 && idx !== current.value) {
          goTo(idx)
          return
        }
      }
    } catch { /* 求值失败退化为线性 */ }
  }
  if (expectedGen !== currentAudioSrcGen.value) return
  goTo(current.value + 1)
}

// P3-3：系统媒体会话（锁屏/系统媒体面板控制）
function setupMediaSession() {
  if (!('mediaSession' in navigator)) return
  try {
    navigator.mediaSession.setActionHandler('play', () => playAudio())
    navigator.mediaSession.setActionHandler('pause', () => pauseAudio())
    navigator.mediaSession.setActionHandler('seekto', (e) => {
      if (audioRef.value && e.seekTime != null) audioRef.value.currentTime = e.seekTime
    })
    navigator.mediaSession.setActionHandler('seekbackward', () => {
      if (audioRef.value) audioRef.value.currentTime = Math.max(0, audioRef.value.currentTime - 10)
    })
    navigator.mediaSession.setActionHandler('seekforward', () => {
      if (audioRef.value) audioRef.value.currentTime = Math.min(audioDuration.value, audioRef.value.currentTime + 10)
    })
  } catch { /* 部分浏览器不支持个别 action，忽略 */ }
}

function updateMediaSession() {
  if (!('mediaSession' in navigator) || !navigator.mediaSession) return
  try {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: `${current.value + 1}/${pages.value.length} · ${(subtitleText.value || '课件播放').slice(0, 60)}`,
      artist: '微课平台'
    })
    navigator.mediaSession.playbackState = playing.value ? 'playing' : 'paused'
  } catch { /* 忽略 */ }
}

function onSlideAudioMessage(event) {
  // D9（P0 修复）：origin 是字符串 "null" 而非 JS null；source 必须为当前 iframe
  if (event.origin !== 'null') return
  const iframe = currentHtmlIframe()
  if (iframe && event.source !== iframe.contentWindow) return
  const msg = event.data
  if (!msg || typeof msg !== 'object') return

  if (msg.type === 'slide-interactive-complete') {
    handleInteractiveComplete()
    return
  }

  if (msg.type === 'slide-audio-state') {
    handleAudioStateUpdate(msg)
    return
  }

  // ---- 协议 v2（方案 §6.1）----
  if (msg.type === 'slide-audio-v2') {
    if (msg.version !== 2) return
    iframeReadyV2 = true
    switch (msg.action) {
      case 'ready':
        sendLoadedV2()
        break
      case 'play':
        playSegment(msg.index != null ? segIndexToPos(msg.index) : (activeSegmentIndex.value || 0))
        break
      case 'pause':
        pauseAudio()
        break
      case 'seek':
        playSegment(msg.index != null ? segIndexToPos(msg.index) : (activeSegmentIndex.value || 0), msg.time)
        break
      case 'set-speed':
        if (msg.rate !== undefined) setSpeed(msg.rate)
        break
      case 'segment-active':
        // P2-3：iframe 点击段 → 先高亮反馈（即使未解锁），再按解锁状态播放
        {
          const pos = segIndexToPos(msg.index)
          if (segmentMode.value && pos >= 0) {
            activeSegmentIndex.value = pos
            sendSegmentActivated(pos)
            if (unlocked.value) {
              playSegment(pos)
            } else {
              audioStatus.value = 'ready'
            }
          }
        }
        break
      case 'get-state':
        sendStateV2({
          state: playing.value ? 'playing' : 'paused',
          segmentIndex: activeSegmentIndex.value,
          time: audioTime.value,
          duration: audioDuration.value
        })
        break
    }
    return
  }

  // ---- 协议 v1（兼容旧 HTML 课件；音频统一由父页宿主，不再依赖 iframe 内播放）----
  if (msg.type !== 'slide-audio') return
  switch (msg.action) {
    case 'get-segments': {
      const page = currentPage.value
      if (page?.segmentAudio) {
        sendMessageToHtmlIframe({
          type: 'slide-audio-segments',
          pageNumber: page.pageNumber,
          url: page.segmentAudio.url,
          duration: page.segmentAudio.duration,
          mergedUrl: page.narrationAudioUrl
        })
      } else {
        sendMessageToHtmlIframe({ type: 'slide-audio-segments', pageNumber: null, url: null })
      }
      break
    }
    case 'container-control': {
      if (msg.command === 'pause') {
        pauseAudio()
      } else if (msg.command === 'resume') {
        playAudio()
      }
      break
    }
    case 'play':
      playAudio()
      break
    case 'pause':
      pauseAudio()
      break
    case 'seek':
      if (segmentMode.value && msg.page != null) {
        playSegment(msg.page - 1, msg.time)
      } else if (audioRef.value && msg.time !== undefined) {
        audioRef.value.currentTime = msg.time
      }
      break
    case 'speed':
      if (msg.rate !== undefined) setSpeed(msg.rate)
      break
    case 'get-state':
      sendMessageToHtmlIframe({
        type: 'slide-audio-state', state: playing.value ? 'playing' : 'paused',
        time: audioTime.value, duration: audioDuration.value
      })
      sendStateV2({
        state: playing.value ? 'playing' : 'paused',
        segmentIndex: posToSegIndex(activeSegmentIndex.value),
        time: audioTime.value,
        duration: audioDuration.value
      })
      break
  }
}

// ---- P0 AudioHost 段播放（方案 §8.1）----

function playSegment(index, time) {
  if (!segmentMode.value) {
    playAudio()
    return
  }
  const seg = segments.value[index]
  if (!seg) return
  if (!seg.audio?.url) {
    audioStatus.value = seg.audio?.status === 'GENERATING' ? 'pending' : 'error'
    if (audioStatus.value === 'error') ElMessage.warning('该段音频尚未生成，请教师先生成音频')
    return
  }
  if (!unlocked.value) {
    audioStatus.value = 'ready'
    return
  }
  interactiveWaiting.value = false
  activeSegmentIndex.value = index
  const audioEl = audioRef.value
  if (!audioEl) return
  if (audioEl.src !== seg.audio.url) {
    audioEl.src = seg.audio.url
    audioEl.load()
  }
  if (time != null && !Number.isNaN(time)) audioEl.currentTime = time
  audioStatus.value = 'loading'
  audioEl.play().then(() => {
    playing.value = true
    audioStatus.value = 'ready'
    updateMediaSession()
    sendStateV2({
      state: 'playing',
      segmentIndex: posToSegIndex(index),
      time: time || 0,
      duration: seg.audio.durationMs ? seg.audio.durationMs / 1000 : 0
    })
    sendSegmentActivated(index)
  }).catch(() => {
    playing.value = false
    audioStatus.value = 'ready'
  })
}

function pauseAudio() {
  if (audioRef.value) audioRef.value.pause()
  playing.value = false
  updateMediaSession()
  sendMessageToHtmlIframe({ type: 'slide-audio-state', state: 'paused', time: audioTime.value, duration: audioDuration.value })
  sendStateV2({
    state: 'paused',
    segmentIndex: posToSegIndex(activeSegmentIndex.value),
    time: audioTime.value,
    duration: audioDuration.value
  })
}

function setSpeed(rate = speed.value) {
  speed.value = rate
  if (audioRef.value) audioRef.value.playbackRate = rate
  sendMessageToHtmlIframe({ type: 'slide-audio-state', state: 'speed-changed', rate })
  sendStateV2({ state: 'speed-changed', rate })
}

function computeActiveSegmentIndex() {
  if (!segmentMode.value || audioTime.value <= 0) return activeSegmentIndex.value
  let acc = 0
  for (let i = 0; i < segments.value.length; i++) {
    const dur = (segments.value[i]?.audio?.durationMs || 0) / 1000
    if (dur > 0 && audioTime.value < acc + dur) return i
    acc += dur
  }
  return segments.value.length - 1
}

function unlockAutoplay() {
  // R-4：首次用户交互仅解锁标志。起播交给正常播放路径（togglePlay / autoMode 的 playAudio），
  // 避免 pointerdown 自动起播后同一 click 的 togglePlay 立即暂停（0.3s 卡住 BUG）。
  unlocked.value = true
}
function handleAudioStateUpdate(msg) {
  switch (msg.state) {
    case 'playing':
      playing.value = true
      break
    case 'paused':
      playing.value = false
      break
    case 'ended':
      playing.value = false
      autoCountdown.value = 0
      if (segmentAudioMode.value && autoMode.value && current.value < pages.value.length - 1) {
        const endedGen = currentAudioSrcGen.value
        autoAdvanceTimer = setTimeout(() => {
          if (endedGen !== currentAudioSrcGen.value) return
          goTo(current.value + 1)
        }, 1500)
      }
      break
    case 'loaded':
      if (msg.duration !== undefined) audioDuration.value = msg.duration
      audioStatus.value = 'ready'
      break
    case 'time-update':
      if (msg.time !== undefined) audioTime.value = msg.time
      if (msg.duration !== undefined) audioDuration.value = msg.duration
      if (audioDuration.value > 0) audioProgress.value = (audioTime.value / audioDuration.value) * 100
      if (autoMode.value && audioDuration.value > 0) {
        const remaining = Math.ceil(audioDuration.value - audioTime.value)
        autoCountdown.value = remaining <= 3 && remaining > 0 ? remaining : 0
      }
      break
  }
}

function handleInteractiveComplete() {
  if (!interactiveWaiting.value) return
  interactiveWaiting.value = false
  if (autoMode.value) {
    if (segmentMode.value) {
      // HTML 分段：互动完成后继续当前段（未播完）或进入下一段
      if (playing.value) return
      const next = activeSegmentIndex.value + 1
      if (next < segments.value.length && audioTime.value > 0) {
        playSegment(next)
      } else {
        playSegment(activeSegmentIndex.value)
      }
    } else if (current.value < pages.value.length - 1) {
      goTo(current.value + 1)
    } else {
      playAudio()
    }
  }
}

// P2-7: 下载当前 HTML 页面源码（绕过 sandbox 禁用右键保存的限制）
function downloadHtmlPage() {
  const page = currentPage.value
  if (!page?.htmlContent) {
    ElMessage.warning('当前页面无 HTML 内容')
    return
  }
  const blob = new Blob([page.htmlContent], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `html-page-${(page.pageNumber || 1)}.html`
  a.click()
  URL.revokeObjectURL(url)
}

function goTo(index) {
  if (index < 0 || index >= pages.value.length) return
  if (pageNavLock) return
  pageNavLock = true
  lastDirection.value = index > current.value ? 1 : -1
  transitionName.value = index > current.value ? 'slide-next' : 'slide-prev'
  current.value = index
  audioTime.value = 0
  audioProgress.value = 0
  autoCountdown.value = 0
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  clearPendingTimer()
  pendingStartTime.value = null
  nextTick(async () => {
    await loadAudio(index)
    preloadAdjacentImages(index)
    if (autoMode.value) playAudio()
    pageNavLock = false
  })
}

function preloadAdjacentImages(currentIdx) {
  const indices = [currentIdx + 1, currentIdx + 2].filter(i => i < pages.value.length)
  Promise.allSettled(indices.map(idx => loadPageImage(idx)))
}

async function loadAudio(index) {
  const page = pages.value[index]
  if (!audioRef.value) return

  cleanAudioBlobCache(index)
  clearTimeout(autoAdvanceTimer)
  clearPendingTimer()
  interactiveWaiting.value = false
  courseCompleted = false
  iframeReadyV2 = false

  const gen = ++currentAudioSrcGen.value

  const isHtmlPage = page?.contentType === 'HTML_DIRECT'
  const hasSegments = isHtmlPage && Array.isArray(page?.segments) && page.segments.length > 0
  segmentMode.value = hasSegments
  segments.value = hasSegments ? page.segments : []
  activeSegmentIndex.value = 0
  segmentAudioMode.value = false // 父页宿主；legacy HTML 不再依赖 iframe 内音频（D1 已废除）

  if (hasSegments) {
    const hasReady = segments.value.some(s => s.audio?.url)
    const hasPending = segments.value.some(s => s.audio?.status === 'GENERATING')
    audioStatus.value = hasReady ? 'ready' : (hasPending ? 'pending' : 'none')
    audioDuration.value = 0
    audioTime.value = 0
    audioProgress.value = 0
    if (audioRef.value) { audioRef.value.pause(); audioRef.value.src = '' }
    playing.value = false
    checkHtmlInteractive(page)
    if (unlocked.value && autoMode.value && audioStatus.value === 'ready') playSegment(0)
    return
  }

  // P0-4：v2 PPT 页直接加载 token 流式 URL（token 即能力凭证，无鉴权头可加载）
  if (page?.audio?.url) {
    clearPendingTimer()
    clearTimeout(loadingTimer)
    audioStatus.value = 'loading'
    audioDuration.value = (page.audio.durationMs || 0) / 1000
    audioRef.value.src = page.audio.url
    audioRef.value.load()
    loadingTimer = setTimeout(() => {
      if (audioStatus.value === 'loading' && gen === currentAudioSrcGen.value) {
        audioStatus.value = 'error'
      }
    }, 10000)
    checkHtmlInteractive(page)
    return
  }

  if (isHtmlPage && !!page?.segmentAudio?.url && !page?.narrationAudioUrl) {
    // legacy HTML 仅有分段音频元数据：退化为整页单音频（R-7），由父页播放
    audioStatus.value = 'none'
    audioDuration.value = 0
    audioTime.value = 0
    audioProgress.value = 0
    if (audioRef.value) audioRef.value.pause()
    playing.value = false
    checkHtmlInteractive(page)
    return
  }

  if (!page?.narrationAudioUrl) {
    audioStatus.value = 'none'
    audioDuration.value = 0
    audioRef.value.src = ''
    return
  }

  if (page.narrationStatus === 'PENDING') {
    audioStatus.value = 'pending'
    pendingStartTime.value = Date.now()
    pendingTimeoutWarning.value = ''
    audioDuration.value = 0
    audioRef.value.src = ''
    startPendingTimer()
    checkHtmlInteractive(page)
    return
  }

  if (page.narrationStatus !== 'AUDIO_READY') {
    audioStatus.value = 'none'
    audioDuration.value = 0
    audioRef.value.src = ''
    return
  }

  clearPendingTimer()
  clearTimeout(loadingTimer)
  audioStatus.value = 'loading'
  loadingTimer = setTimeout(() => {
    if (audioStatus.value === 'loading' && gen === currentAudioSrcGen.value) {
      audioStatus.value = 'error'
    }
  }, 10000)

  const secParam = page.sectionId ? `?sectionId=${page.sectionId}` : ''
  const relUrl = `/courses/${courseId.value}/slides/pages/${page.pageNumber}/audio${secParam}`

  if (audioBlobUrls.value[relUrl]) {
    audioRef.value.src = audioBlobUrls.value[relUrl]
    audioRef.value.load()
  } else {
    try {
      const blobUrl = await loadAuthResource(relUrl)
      if (gen !== currentAudioSrcGen.value) return
      if (blobUrl) {
        audioBlobUrls.value[relUrl] = blobUrl
        audioRef.value.src = blobUrl
        audioRef.value.load()
      } else {
        audioStatus.value = 'error'
      }
    } catch {
      if (gen !== currentAudioSrcGen.value) return
      audioStatus.value = 'error'
    }
  }

  audioDuration.value = page.audioDuration || 0
  checkHtmlInteractive(page)
}

function checkHtmlInteractive(page) {
  if (page?.contentType !== 'HTML_DIRECT' || !page?.htmlContent) {
    interactiveWaiting.value = false
    return
  }
  const match = page.htmlContent.match(/data-interactive=["']true["']/)
  interactiveWaiting.value = !!match
  if (interactiveWaiting.value) {
    if (segmentMode.value || audioRef.value) pauseAudio()
    playing.value = false
  }
}

function startPendingTimer() {
  clearPendingTimer()
  pendingTimer = setInterval(() => {
    if (!pendingStartTime.value) return
    const elapsed = Math.floor((Date.now() - pendingStartTime.value) / 1000)
    if (elapsed >= 300) {
      pendingTimeoutWarning.value = '（已超过5分钟，请联系教师）'
      clearPendingTimer()
    } else if (elapsed >= 240) {
      pendingTimeoutWarning.value = '（即将超时，请联系教师）'
    }
  }, 5000)
}

function clearPendingTimer() {
  if (pendingTimer) { clearInterval(pendingTimer); pendingTimer = null }
}

function cleanAudioBlobCache(currentIdx) {
  const keepRange = new Set([currentIdx - 1, currentIdx, currentIdx + 1, currentIdx + 2].filter(i => i >= 0))
  for (const url of Object.keys(audioBlobUrls.value)) {
    const pathPart = url.split('?')[0]
    const pageMatch = pathPart.match(/\/pages\/(\d+)\/audio$/)
    if (pageMatch) {
      const pageNum = parseInt(pageMatch[1])
      const zeroIdx = pageNum - 1
      if (!keepRange.has(zeroIdx)) {
        URL.revokeObjectURL(audioBlobUrls.value[url])
        delete audioBlobUrls.value[url]
      }
    }
  }
}

function playAudio() {
  if (!audioRef.value) return
  if (segmentMode.value) { playSegment(activeSegmentIndex.value); return }
  if (audioStatus.value === 'pending' || audioStatus.value === 'none') return
  if (!unlocked.value) { audioStatus.value = 'ready'; return }
  audioRef.value.play().then(() => {
    playing.value = true
    updateMediaSession()
    sendMessageToHtmlIframe({ type: 'slide-audio-state', state: 'playing', time: audioTime.value, duration: audioDuration.value })
    sendStateV2({
      state: 'playing',
      segmentIndex: posToSegIndex(activeSegmentIndex.value),
      time: audioTime.value,
      duration: audioDuration.value
    })
  }).catch(() => { playing.value = false })
}
function togglePlay() {
  if (playing.value) {
    pauseAudio()
  } else {
    playAudio()
  }
}
function handleStageClick() { if (autoMode.value) autoMode.value = false }

function onTimeUpdate() {
  if (!audioRef.value) return
  audioTime.value = audioRef.value.currentTime
  if (audioDuration.value > 0) audioProgress.value = (audioTime.value / audioDuration.value) * 100
  if (autoMode.value && audioDuration.value > 0) {
    const remaining = Math.ceil(audioDuration.value - audioTime.value)
    autoCountdown.value = remaining <= 3 && remaining > 0 ? remaining : 0
  }
  // R-9：timeupdate 实际 4-66Hz，父→iframe 消息节流 ~4Hz（250ms）
  const now = Date.now()
  if (now - lastStatePush < 250) return
  lastStatePush = now
  sendMessageToHtmlIframe({ type: 'slide-audio-state', state: 'time-update', time: audioTime.value, duration: audioDuration.value })
  if (segmentMode.value) {
    const idx = computeActiveSegmentIndex()
    if (idx !== activeSegmentIndex.value) {
      activeSegmentIndex.value = idx
      sendSegmentActivated(idx)
    }
    sendStateV2({
      state: 'time-update',
      segmentIndex: posToSegIndex(activeSegmentIndex.value),
      time: audioTime.value,
      duration: audioDuration.value,
      progress: audioProgress.value
    })
  }
}
function onAudioLoaded() {
  const expectedGen = currentAudioSrcGen.value
  clearTimeout(loadingTimer)
  if (audioRef.value) {
    if (expectedGen !== currentAudioSrcGen.value) return
    if (!segmentMode.value) {
      audioDuration.value = audioRef.value.duration || audioDuration.value
    } else {
      const seg = segments.value[activeSegmentIndex.value]
      if (seg?.audio?.durationMs) audioDuration.value = seg.audio.durationMs / 1000
      else audioDuration.value = audioRef.value.duration || audioDuration.value
    }
    audioRef.value.playbackRate = speed.value
    audioStatus.value = 'ready'
    sendMessageToHtmlIframe({ type: 'slide-audio-state', state: 'loaded', duration: audioDuration.value })
    sendLoadedV2()
    updateMediaSession()
  }
}
function onAudioEnded() {
  const expectedGen = currentAudioSrcGen.value
  playing.value = false; autoCountdown.value = 0
  sendMessageToHtmlIframe({ type: 'slide-audio-state', state: 'ended' })

  // P0 AudioHost：HTML 分段顺序播放（方案 §8.1）
  if (segmentMode.value) {
    const next = activeSegmentIndex.value + 1
    if (next < segments.value.length) {
      sendStateV2({ state: 'ended', segmentIndex: posToSegIndex(activeSegmentIndex.value), nextIndex: posToSegIndex(next) })
      activeSegmentIndex.value = next
      if (autoMode.value && unlocked.value) {
        autoAdvanceTimer = setTimeout(() => {
          if (expectedGen !== currentAudioSrcGen.value) return
          playSegment(next)
        }, 500)
      }
    } else {
      sendStateV2({ state: 'ended', segmentIndex: posToSegIndex(activeSegmentIndex.value), nextIndex: null })
      if (autoMode.value) {
        if (current.value < pages.value.length - 1) {
          autoAdvanceTimer = setTimeout(() => {
            if (expectedGen !== currentAudioSrcGen.value) return
            advanceToNextPage(expectedGen)
          }, 1500)
        } else if (!courseCompleted) {
          courseCompleted = true
          ElMessage.success('本课学习完成')
        }
      }
    }
    return
  }

  if (interactiveWaiting.value) return
  if (expectedGen !== currentAudioSrcGen.value) return
  if (autoMode.value && current.value < pages.value.length - 1) {
    autoAdvanceTimer = setTimeout(() => {
      if (expectedGen !== currentAudioSrcGen.value) return
      advanceToNextPage(expectedGen)
    }, 1500)
  } else if (autoMode.value && !courseCompleted) {
    courseCompleted = true
    ElMessage.success('本课学习完成')
  }
}

function seekAudioByClick(e) {
  if (!audioRef.value || !audioDuration.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  audioRef.value.currentTime = ((e.clientX - rect.left) / rect.width) * audioDuration.value
}

function seekAudioByKeydown(e) {
  if (!audioRef.value || !audioDuration.value) return
  const step = Math.max(5, Math.round(audioDuration.value / 20))
  if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
    e.preventDefault()
    audioRef.value.currentTime = Math.min(audioDuration.value, audioRef.value.currentTime + step)
  } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
    e.preventDefault()
    audioRef.value.currentTime = Math.max(0, audioRef.value.currentTime - step)
  } else if (e.key === 'Home') {
    e.preventDefault()
    audioRef.value.currentTime = 0
  } else if (e.key === 'End') {
    e.preventDefault()
    audioRef.value.currentTime = audioDuration.value
  }
}

function dismissKeyboardHint() {
  showKeyboardHint.value = false
}

function handleKeydown(e) {
  if (showKeyboardHint.value && e.key === 'Escape') {
    e.preventDefault()
    dismissKeyboardHint()
    return
  }
  if (e.key === 'ArrowRight') { e.preventDefault(); goTo(Math.min(pages.value.length - 1, current.value + 1)) }
  if (e.key === ' ') { e.preventDefault(); togglePlay() }
  if (e.key === 'ArrowLeft') { e.preventDefault(); goTo(Math.max(0, current.value - 1)) }
  if (e.key === 'f' || e.key === 'F') toggleFullscreen()
  if (e.key === 'Escape' && document.fullscreenElement) document.exitFullscreen()
}
function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen()
  else playerRef.value?.requestFullscreen()
}

// 监听全屏状态变化，动态更新 aria-label
const isFullscreen = ref(false)
function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}
function formatTime(s) {
  if (!s || isNaN(s)) return '0:00'
  return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`
}

// P1I-016: 在进度上报时增加 sectionId 参数（从 route.query 获取）
const sectionId = computed(() => route.query.sectionId)

// P0-2: 创建/获取进度记录
async function ensureProgress() {
  if (!courseId.value || !isStudent.value) return
  try {
    const existing = await getLearningProgress({ courseId: courseId.value })
    const records = existing.data || []
    const slideRecord = records.find(r => r.chapterId === Number(chapterId.value))
    if (!slideRecord) {
      await createLearningProgress({
        courseId: courseId.value,
        chapterId: chapterId.value ? Number(chapterId.value) : undefined,
        sectionId: sectionId.value ? Number(sectionId.value) : undefined,
      })
    }
  } catch (e) { if (courseId.value) console.warn('[SlidePlayer] ensureProgress failed', e.message) }
}

// P0-2: 翻到最后一页时标记完成
async function markSlideComplete() {
  if (!courseId.value || !isStudent.value) return
  try {
    const existing = await getLearningProgress({ courseId: courseId.value })
    const records = existing.data || []
    const slideRecord = records.find(r => r.chapterId === Number(chapterId.value))
    if (slideRecord?.id) {
      await updateLearningProgress(slideRecord.id, { completed: true })
    } else {
      await createLearningProgress({
        courseId: courseId.value,
        chapterId: chapterId.value ? Number(chapterId.value) : undefined,
        sectionId: sectionId.value ? Number(sectionId.value) : undefined,
        completed: true,
      })
    }
  } catch { /* 静默 */ }
}

// P0-2: 翻到最后一页时触发完成标记
// P1C-019: 单页课件浏览后也应标记完成 — 移除 pages.length <= 1 的提前返回
watch(current, (newVal) => {
  updateMediaSession()
  if (newVal >= pages.value.length - 1) markSlideComplete()
})

onMounted(async () => {
  // R-4：首次用户交互解锁自动播放（沙箱 iframe 内点击不构成父页激活，必须父页捕获）
  playerRef.value?.addEventListener('pointerdown', unlockAutoplay, { once: true })
  playerRef.value?.addEventListener('keydown', unlockAutoplay, { once: true })
  setupMediaSession()
  await ensureProgress()
  await loadPages()
  if (pages.value.length > 0) loadAudio(0)
  playerRef.value?.focus()
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('message', onSlideAudioMessage)
  if (!sessionStorage.getItem('slide-player-hint-shown')) {
    showKeyboardHint.value = true
    sessionStorage.setItem('slide-player-hint-shown', '1')
  }
})
onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (audioRef.value) { audioRef.value.pause(); audioRef.value.src = '' }
  playerRef.value?.removeEventListener('pointerdown', unlockAutoplay)
  playerRef.value?.removeEventListener('keydown', unlockAutoplay)
  clearImageCache()
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('message', onSlideAudioMessage)
})
</script>

<style scoped>
/* ========= ROOT ========= */
.slide-player {
  --player-bg: #0a0a0f;
  --player-surface: #14141f;
  --player-border: rgba(255, 255, 255, 0.06);
  --player-text: #e4e4e7;
  --player-text-secondary: #a1a1aa;
  --player-accent: #6366f1;
  --player-accent-glow: rgba(99, 102, 241, 0.3);
  --player-danger: #ef4444;
  --radius: 10px;
  --radius-sm: 6px;
  display: flex; flex-direction: column; height: 100dvh; width: 100dvw;
  background: var(--player-bg); color: var(--player-text);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  overflow: hidden; outline: none; user-select: none;
}

/* ========= HEADER ========= */
.player-header {
  display: flex; align-items: center; justify-content: space-between;
  height: 48px; padding: 0 16px;
  background: rgba(10, 10, 15, 0.92);
  backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--player-border); z-index: 100;
}
.header-center { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.page-counter { font-size: 13px; font-weight: var(--weight-semibold); color: var(--player-text); letter-spacing: 0.5px; }
.counter-divider { color: var(--player-text-secondary); margin: 0 1px; }
.page-thumb-strip { display: flex; gap: 5px; align-items: center; }
.thumb-dot {
  width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,.15);
  border: none; cursor: pointer; transition: all var(--duration-base) ease; padding: 0;
}
.thumb-dot.active { background: var(--player-accent); box-shadow: 0 0 6px var(--player-accent-glow); width: 18px; border-radius: 10px; }
.thumb-dot.has-audio { background: rgba(99,102,241,.35); }
.thumb-dot:hover { background: rgba(255,255,255,.35); }
.header-right { display: flex; gap: 4px; }

.btn-icon {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  background: transparent; border: none; border-radius: var(--radius-sm);
  color: var(--player-text-secondary); cursor: pointer; transition: all 200ms ease;
}
.btn-icon:hover { background: rgba(255,255,255,.08); color: var(--player-text); }
.btn-icon.active { color: var(--player-accent); }
.btn-auto.active::after { content: ''; position: absolute; bottom: 6px; width: 4px; height: 4px; border-radius: 50%; background: #22c55e; }

/* ========= MAIN ========= */
.player-main { flex: 1; display: flex; overflow: hidden; }

/* --- Slide Stage --- */
.slide-stage { flex: 1; display: flex; align-items: center; justify-content: center; position: relative; cursor: pointer; padding: 12px 0; }
.slide-container { position: relative; display: flex; align-items: center; justify-content: center; }
.slide-wrapper { line-height: 0; position: relative; }
.slide-image {
  max-width: min(92vw, 1400px); max-height: min(82vh, 900px); width: auto; height: auto;
  object-fit: contain; border-radius: 4px; box-shadow: 0 8px 40px rgba(0,0,0,.5);
}
.slide-iframe {
  width: min(92vw, 1400px); aspect-ratio: 16 / 9;
  max-height: min(82vh, 900px); border: none; border-radius: 4px;
  box-shadow: 0 8px 40px rgba(0,0,0,.5); background: #fff;
}

/* 全屏模式：去掉 max-height 限制，填充可用空间 */
:fullscreen .slide-image,
:fullscreen .slide-iframe {
  max-height: calc(100vh - 120px); max-width: 98vw;
}
/* HTML 课时工具栏：下载按钮（sandbox 阻止右键保存时的替代方案） */
.html-toolbar { text-align: center; margin-top: 8px; }
.slide-gradient {
  position: absolute; inset: 0; border-radius: 4px;
  background: linear-gradient(180deg, transparent 85%, rgba(10,10,15,.4) 100%);
  pointer-events: none;
}

/* 图片加载失败占位 */
.slide-placeholder {
  width: 640px;
  max-width: 80vw;
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: var(--player-surface);
  border: 1px dashed rgba(255,255,255,.12);
  border-radius: 8px;
  color: var(--player-text-secondary);
}

.placeholder-icon {
  color: rgba(255,255,255,.1);
}

.placeholder-text {
  font-size: 14px;
  color: var(--player-text-secondary);
}

/* Navigation Arrows */
.nav-arrow {
  position: absolute; top: 50%; transform: translateY(-50%); z-index: 10;
  width: 44px; height: 44px; border-radius: 50%; border: none;
  background: rgba(20,20,31,.85); color: var(--player-text);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all 200ms ease; opacity: 0;
  backdrop-filter: blur(10px);
}
.slide-frame:hover .nav-arrow, .nav-arrow:focus-visible { opacity: 1; }
.nav-arrow:hover { background: var(--player-accent); transform: translateY(-50%) scale(1.1); }
.nav-prev { left: 16px; }
.nav-next { right: 16px; }

/* Countdown */
.countdown-badge {
  position: absolute; bottom: 20px; right: 20px; z-index: 10;
}
.countdown-ring {
  display: flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 50%;
  background: var(--player-accent); color: #fff;
  font-size: 15px; font-weight: var(--weight-bold); box-shadow: 0 2px 12px var(--player-accent-glow);
}
.countdown-fade-enter-active { transition: all .2s ease; }
.countdown-fade-leave-active { transition: all .3s ease; }
.countdown-fade-enter-from, .countdown-fade-leave-to { opacity: 0; transform: scale(.8); }

/* Slide Transitions */
.slide-next-enter-active, .slide-next-leave-active,
.slide-prev-enter-active, .slide-prev-leave-active { transition: all 350ms cubic-bezier(0.4, 0, 0.2, 1); }
.slide-next-enter-from { opacity: 0; transform: translateX(40px); }
.slide-next-leave-to { opacity: 0; transform: translateX(-40px); }
.slide-prev-enter-from { opacity: 0; transform: translateX(-40px); }
.slide-prev-leave-to { opacity: 0; transform: translateX(40px); }

/* No narration panel (removed per UX feedback) */

/* ========= FOOTER ========= */
.player-footer {
  background: rgba(10, 10, 15, 0.95); backdrop-filter: blur(20px);
  border-top: 1px solid var(--player-border); padding: 10px 24px; z-index: 100;
}
.control-bar {
  display: flex; align-items: center; gap: 12px; max-width: 1200px; margin: 0 auto;
}
.ctrl-btn {
  width: 40px; height: 40px; display: flex; align-items: center; justify-content: center;
  background: transparent; border: none; border-radius: var(--radius-sm);
  color: var(--player-text-secondary); cursor: pointer; transition: all 200ms ease;
}
.ctrl-btn:hover:not(:disabled) { background: rgba(255,255,255,.08); color: var(--player-text); }
.ctrl-btn:disabled { opacity: .3; cursor: default; }
.ctrl-btn-play {
  width: 48px; height: 48px; border-radius: 50%; background: var(--player-accent);
  color: #fff; box-shadow: 0 4px 16px var(--player-accent-glow);
}
.ctrl-btn-play:hover { background: #4f46e5; transform: scale(1.05); }

.progress-area { flex: 1; display: flex; align-items: center; gap: 10px; }
.time-label { font-size: 12px; color: var(--player-text-secondary); min-width: 40px; text-align: center; font-variant-numeric: tabular-nums; }
.progress-track {
  flex: 1; height: 4px; background: rgba(255,255,255,.1); border-radius: 4px;
  position: relative; cursor: pointer;
}
.progress-track:hover,
.progress-track:focus-visible { height: 6px; }
.progress-track:hover .progress-thumb,
.progress-track:focus-visible .progress-thumb { opacity: 1; transform: scale(1); }
.progress-fill {
  height: 100%; border-radius: 4px; background: var(--player-accent);
  transition: width 100ms linear;
}
.progress-thumb {
  position: absolute; top: 50%; width: 12px; height: 12px;
  border-radius: 50%; background: var(--player-accent); border: 2px solid #fff;
  transform: translate(-50%, -50%) scale(.6); opacity: 0;
  transition: all 200ms ease;
}
.progress-segments { position: absolute; inset: 0; pointer-events: none; }
.segment-tick {
  position: absolute; top: 0; bottom: 0; width: 1px;
  background: rgba(255,255,255,.5); transform: translateX(-.5px);
}

/* P3-2 讲述稿字幕 */
.subtitle-bar {
  display: flex; align-items: flex-start; gap: 10px;
  margin: 0 24px 8px; padding: 10px 16px; border-radius: 10px;
  background: rgba(10,10,15,.88); border: 1px solid var(--player-border);
  backdrop-filter: blur(12px); max-height: 120px; overflow-y: auto;
}
.subtitle-label {
  flex-shrink: 0; font-size: 12px; color: var(--player-accent);
  font-weight: 600; padding-top: 2px; user-select: none;
}
.subtitle-text { font-size: 13px; line-height: 1.7; color: var(--player-text); white-space: pre-wrap; }

.speed-group { display: flex; gap: 2px; background: rgba(255,255,255,.05); border-radius: var(--radius-sm); padding: 2px; }
.speed-chip {
  padding: 3px 8px; border: none; background: transparent; color: var(--player-text-secondary);
  font-size: 12px; font-weight: var(--weight-semibold); border-radius: 4px; cursor: pointer;
  transition: all var(--duration-base) ease;
}
.speed-chip.active { background: var(--player-accent); color: #fff; }
.speed-chip:hover:not(.active) { color: var(--player-text); }

/* Audio Status Bar */
.audio-status-bar { padding: 2px 0 4px; display: flex; align-items: center; }
.audio-status {
  font-size: 12px; display: flex; align-items: center; gap: 4px;
  border-radius: 20px; padding: 2px 10px;
}
.audio-status.loading { color: var(--player-text-secondary); }
.audio-status.ready { color: var(--player-accent); }
.audio-status.pending { color: #f59e0b; }
.audio-status.error { color: var(--player-danger); }
.audio-status.none { display: none; }
.audio-status-btn {
  border: none;
  background: transparent;
  color: inherit;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  cursor: pointer;
}
.status-ready:hover,
.status-ready:focus-visible { opacity: 0.8; }
.progress-track--empty { cursor: default; background: rgba(255,255,255,.05); }

/* Interactive Page Mask */
.interactive-mask {
  position: absolute; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,.7); display: flex; align-items: center; justify-content: center;
  z-index: 200; border-radius: 12px;
}
.interactive-content { text-align: center; color: #fff; }
.interactive-icon { margin-bottom: 8px; opacity: .8; }
.interactive-content p { margin: 0 0 12px; font-size: 14px; opacity: .9; }
.interactive-btn {
  background: var(--player-accent); color: #fff; border: none;
  border-radius: 20px; padding: 8px 24px; font-size: 14px; cursor: pointer;
}
.interactive-btn:hover { opacity: .9; }

/* ========= KEYBOARD HINT ========= */
.keyboard-hint {
  position: fixed; inset: 0; z-index: 1000; background: rgba(0,0,0,.7);
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(4px);
}
.hint-card {
  background: var(--player-surface); border: 1px solid var(--player-border);
  border-radius: var(--radius); padding: 28px 32px; max-width: 300px;
  box-shadow: 0 20px 60px rgba(0,0,0,.5);
}
.hint-row { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; font-size: 14px; color: var(--player-text); }
kbd {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 28px; height: 24px; padding: 0 6px; border-radius: 4px;
  background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.12);
  font-size: 11px; font-family: inherit; color: var(--player-text-secondary);
}
.hint-dismiss { display: block; margin-top: 16px; font-size: 12px; color: var(--player-text-secondary); text-align: center; }
.keyboard-hint-dismiss {
  width: 100%;
  margin-top: 16px;
  min-height: 44px;
  border: 1px solid var(--player-border);
  border-radius: 10px;
  background: rgba(255,255,255,.06);
  color: var(--player-text);
  cursor: pointer;
}
.keyboard-hint-dismiss:focus-visible,
.progress-track:focus-visible,
.audio-status-btn:focus-visible,
.interactive-btn:focus-visible,
.ctrl-btn:focus-visible,
.speed-chip:focus-visible,
.btn-icon:focus-visible,
.thumb-dot:focus-visible {
  outline: 3px solid #facc15;
  outline-offset: 2px;
}
.hint-fade-enter-active, .hint-fade-leave-active { transition: opacity .25s ease; }
.hint-fade-enter-from, .hint-fade-leave-to { opacity: 0; }

/* ========= RESPONSIVE ========= */
@media (max-width: 768px) {
  .narration-panel { width: 280px; }
  .player-footer { padding: 8px 12px; }
  .control-bar { gap: 6px; }
  .speed-group { display: none; }
  .ctrl-btn { width: 34px; height: 34px; }
  .ctrl-btn-play { width: 42px; height: 42px; }
  .progress-area { gap: 6px; }
  .nav-arrow { width: 36px; height: 36px; opacity: 1; }
}

@media (max-width: 480px) {
  .narration-panel { position: absolute; right: 0; top: 48px; bottom: 60px; z-index: 50; width: 260px; }
  .narration-panel.collapsed { width: 24px; }
}
</style>
