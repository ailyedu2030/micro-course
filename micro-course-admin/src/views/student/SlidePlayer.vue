<!--
  交互式课件播放器
  路由路径: /student/courses/:id/slides/player
  Phase 10
  Author: Phase10-Development-Team
-->
<template>
  <div class="slide-player" ref="playerRef" tabindex="0" @keydown="handleKeydown">
    <!-- P1-C-7（L0 铁律）：教师/管理员预览态必须明确标识 —— 预览不记录进度，
         避免教师误以为学习进度已写入、或误判 quiz 分支行为与真实学生一致 -->
    <div v-if="inPreview || !isStudent" class="teacher-preview-banner" role="alert">
      <el-icon :size="14"><Warning /></el-icon>
      <span class="tpb-text">{{ $t('slidePlayer.previewBanner') }}</span>
      <button type="button" class="tpb-exit" @click="handleBack" :aria-label="$t('slidePlayer.exitPreview')">{{ $t('slidePlayer.exitPreview') }}</button>
    </div>
    <!-- Top Bar -->
    <header class="player-header">
      <button class="btn-icon" @click="handleBack" :aria-label="inPreview ? $t('slidePlayer.exitPreview') : $t('app.back')">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <div class="header-center">
        <span class="page-counter">{{ pages.length === 0 ? 0 : current + 1 }}<span class="counter-divider">/</span>{{ pages.length }}</span>
        <div class="page-thumb-strip" ref="thumbStripRef">
          <!-- F8（P2，设计 §7.1）：页点条缩略图 —— PPT 页拉真实缩略图 / HTML 页 SVG 占位，
               懒加载 + 缓存 + 失败回退色块；loading 中显示占位色块（aria 无"已加载"后缀） -->
          <button
            v-for="(p, i) in pages" :key="i"
            class="thumb" :class="{ active: i === current, 'has-audio': p.audioDuration }"
            :data-thumb-index="i"
            @click="goTo(i)"
            :aria-label="thumbAriaLabel(p, i)"
            :title="pageDurationText(p)"
          >
            <img v-if="thumbUrls[i]" :src="thumbUrls[i]" :alt="$t('slidePlayer.thumbAlt', { n: i + 1 })" class="thumb-img" @error="thumbLoadError(i)" />
            <!-- HTML 页无原生缩略图 → 第一段文字 + 图标占位（设计 §7.1 兜底方案） -->
            <span v-else-if="isHtmlPage(p)" class="thumb-html" aria-hidden="true">
              <el-icon :size="10"><Document /></el-icon>
              <span class="thumb-html-text">{{ htmlThumbText(p) }}</span>
            </span>
            <!-- loading 中 / 加载失败 → 色块兜底 -->
            <span v-else class="thumb-block" :class="{ 'thumb-block--error': thumbFailed[i] }" aria-hidden="true"></span>
          </button>
        </div>
      </div>
      <div class="header-right">
        <button
          class="btn-icon" :class="{ active: showSubtitle }"
          @click="showSubtitle = !showSubtitle" :aria-label="showSubtitle ? $t('slidePlayer.closeSubtitle') : $t('slidePlayer.openSubtitle')"
          :title="showSubtitle ? $t('slidePlayer.closeSubtitle') : $t('slidePlayer.openSubtitle')"
        >
          <el-icon :size="16"><Document /></el-icon>
        </button>
        <button
class="btn-icon btn-auto" :class="{ active: autoMode }"
          @click="autoMode = !autoMode" :aria-label="autoMode ? $t('slidePlayer.closeAutoPlay') : $t('slidePlayer.openAutoPlay')"
          :title="autoMode ? $t('slidePlayer.autoPlaying') : $t('slidePlayer.manualMode')"
>
          <el-icon :size="16"><VideoPlay v-if="autoMode" /><VideoPause v-else /></el-icon>
        </button>
        <button class="btn-icon" @click="toggleFullscreen" :aria-label="isFullscreen ? $t('video.exitFullscreen') : $t('video.fullscreen')">
          <el-icon :size="16"><FullScreen /></el-icon>
        </button>
      </div>
    </header>

    <!-- Loading / Error State -->
    <div v-if="pageLoading" class="player-loading" style="display:flex;align-items:center;justify-content:center;flex:1;color:var(--el-text-color-secondary)">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon><span style="margin-left:12px">{{ $t('slidePlayer.loadingSlides') }}</span>
    </div>
    <div v-else-if="pageError" class="player-loading" style="display:flex;align-items:center;justify-content:center;flex:1;flex-direction:column;gap:12px">
      <span style="color:var(--el-text-color-secondary)">{{ $t('slidePlayer.slideLoadFailed') }}</span>
      <el-button size="small" @click="loadPages">{{ $t('common.retry') }}</el-button>
    </div>

    <!-- G3-P0-6（L0 铁律）：0 页课件空状态 —— 明确告知"当前状况 + 该怎么办"。
         此前 pages.length===0 落入 slide-placeholder 显示"图片加载失败"+重试按钮、
         页计数器显示"1/0"，学生无法区分"图片挂了"与"课件没了"（误导）。
         空状态不指责用户，提供返回课程详情的明确出口。 -->
    <div v-else-if="pages.length === 0" class="player-empty" role="region" :aria-label="$t('slidePlayer.emptyAria')">
      <el-empty :image-size="140">
        <template #description>
          <span class="player-empty-title">{{ $t('slidePlayer.emptyTitle') }}</span>
        </template>
        <p class="player-empty-hint">{{ $t('slidePlayer.emptyHint') }}</p>
        <el-button type="primary" @click="goBackToCourse">{{ $t('slidePlayer.backToCourse') }}</el-button>
      </el-empty>
    </div>

    <!-- Main Content -->
    <section v-else class="player-main" role="region" :aria-label="$t('slidePlayer.contentAria')">
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
                :srcdoc="htmlSrcDoc"
                sandbox="allow-scripts"
                ref="htmlIframeRef"
                :title="$t('slidePlayer.pageContentTitle', { n: current + 1 })"
                class="slide-iframe"
                :key="'html-' + current"
                :aria-label="$t('slidePlayer.pageLabel', { n: current + 1 })"
                @error="onHtmlIframeError"
                @load="onHtmlIframeLoad"
              />
              <!-- P2-7: HTML 课时下载按钮（sandbox 禁用右键保存，用临时 Blob 下载源码）
                   F9：@click.stop 防止下载点击冒泡到 slide-stage 误触发 autoMode 切换 -->
              <div v-if="currentPage?.contentType === 'HTML_DIRECT'" class="html-toolbar">
                <el-button size="small" text @click.stop="downloadHtmlPage">
                  <el-icon><Download /></el-icon> {{ $t('slidePlayer.downloadHtml') }}
                </el-button>
              </div>
              <!-- 正常渲染的图片 -->
              <img
                v-else-if="imageUrls[current] && !imageErrors[current]"
                :src="imageUrls[current]" class="slide-image"
                :alt="$t('slidePlayer.pageLabel', { n: current + 1 })"
                @error="imageErrors[current] = true"
              />
              <!-- 图片加载失败：占位图 + 重试按钮 -->
              <div v-else class="slide-placeholder">
                <el-icon :size="48" class="placeholder-icon"><PictureFilled /></el-icon>
                <span class="placeholder-text">{{ $t('slidePlayer.imageLoadFailed') }}</span>
                <el-button
                  size="small" type="primary" plain
                  :loading="imageRetrying[current]"
                  :icon="RefreshRight"
                  @click.stop="retryImage(current)"
                >
                  {{ $t('slidePlayer.retryLoad') }}
                </el-button>
              </div>
              <div class="slide-gradient" />
            </div>
          </transition>

          <!-- Navigation Arrows -->
          <button v-if="current > 0" class="nav-arrow nav-prev" @click.stop="goTo(current - 1)" :aria-label="$t('slidePlayer.prevPage')">
            <el-icon :size="24"><ArrowLeft /></el-icon>
          </button>
          <button v-if="current < pages.length - 1" class="nav-arrow nav-next" @click.stop="goTo(current + 1)" :aria-label="$t('slidePlayer.nextPage')">
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
          <p>{{ $t('slidePlayer.interactiveHint') }}</p>
          <button class="interactive-btn" @click="handleInteractiveComplete">{{ $t('app.finish') }}</button>
        </div>
      </div>

      <!-- Audio Status Indicator（P0 R-3：PPT/HTML 页统一显示，避免 PENDING/ERROR/无音频零提示。
           L0 U-1：v-if 恒真 —— 完全无音频的页也显示「该页无讲解音频」灰色提示，
           学生明白当前页为什么播放按钮不可用，而非无声置灰） -->
      <div class="audio-status-bar" aria-live="polite">
        <div class="audio-status" :class="audioStatus">
          <span v-if="audioStatus === 'loading'" class="status-loading">
            <el-icon class="is-loading" :size="14"><Loading /></el-icon> {{ $t('slidePlayer.audioLoading') }}
          </span>
          <button
            v-else-if="audioStatus === 'ready'"
            type="button"
            class="status-ready audio-status-btn"
            :aria-label="$t('slidePlayer.startAudioAria')"
            @click="togglePlay"
          >
            <el-icon :size="14"><VideoPlay /></el-icon> {{ $t('slidePlayer.clickToStart') }}
          </button>
          <span v-else-if="audioStatus === 'pending'" class="status-pending">
            <el-icon :size="14"><Clock /></el-icon> {{ $t('slidePlayer.waitingAudioGen') }}{{ pendingTimeoutWarning }}
          </span>
          <!-- P1-C-5：v2 PPT GENERATING 页 / legacy PENDING 页均按 narrationStatus 正确提示 -->
          <span v-else-if="audioStatus === 'generating'" class="status-pending">
            <el-icon class="is-loading" :size="14"><Loading /></el-icon> {{ $t('slidePlayer.audioGenerating') }}
          </span>
          <!-- P1-C-3：AUDIO_FAILED 段/页 → 明确"生成失败"而非"无音频"，并提供重试入口 -->
          <span v-else-if="audioStatus === 'failed'" class="status-error">
            <el-icon :size="14"><Warning /></el-icon> {{ $t('slidePlayer.audioGenFailed') }}
            <button
              type="button"
              class="audio-status-btn status-failed-retry"
              :aria-label="$t('slidePlayer.reloadAudioStatusAria')"
              @click="handleAudioRetry"
            >{{ $t('common.retry') }}</button>
          </span>
          <span v-else-if="audioStatus === 'error'" class="status-error">
            <el-icon :size="14"><Warning /></el-icon> {{ $t('slidePlayer.audioLoadFailed') }}{{ audioErrorHint }}
            <!-- P0-G：error 态提供重试按钮，学生可即时重新加载音频（不再无限转圈） -->
            <button
              type="button"
              class="audio-status-btn status-failed-retry"
              :aria-label="$t('slidePlayer.reloadAudioAria')"
              @click="handleAudioRetry"
            >{{ $t('slidePlayer.reload') }}</button>
          </span>
          <!-- 加载/失败期间不显示"无音频"（避免初始 loading 闪一下误导） -->
          <span v-else-if="!pageLoading && !pageError" class="status-no-audio">
            <el-icon :size="14"><Mute /></el-icon> {{ $t('slidePlayer.noAudioOnPage') }}
          </span>
        </div>
      </div>

      <div class="control-bar">
        <button class="ctrl-btn" @click="goTo(Math.max(0, current - 1))" :disabled="current === 0" :aria-label="$t('slidePlayer.prevPage')">
          <el-icon :size="20"><ArrowLeft /></el-icon>
        </button>
        <!-- P2-2026-08-21: segmentAudioMode 恒 false(legacy 死标志)，移除恒真前缀 -->
        <button class="ctrl-btn ctrl-btn-play" @click="togglePlay" :disabled="audioStatus === 'pending' || audioStatus === 'none' || audioStatus === 'generating' || audioStatus === 'failed'" :aria-label="$t('slidePlayer.playPauseAria')">
          <el-icon :size="24"><VideoPause v-if="playing" /><VideoPlay v-else /></el-icon>
        </button>
        <button class="ctrl-btn" @click="goTo(Math.min(pages.length - 1, current + 1))" :disabled="current >= pages.length - 1" :aria-label="$t('slidePlayer.nextPage')">
          <el-icon :size="20"><ArrowRight /></el-icon>
        </button>

        <div class="progress-area">
          <span class="time-label">{{ formatTime(audioTime) }}</span>
          <div
            v-if="audioStatus !== 'pending' && audioStatus !== 'none' && audioStatus !== 'generating' && audioStatus !== 'failed'"
            class="progress-track"
            role="slider"
            tabindex="0"
            :aria-label="$t('slidePlayer.audioProgressAria')"
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
            :aria-label="$t('slidePlayer.speedAria', { speed: s })"
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
        <span class="subtitle-label">{{ $t('slidePlayer.subtitleLabel') }}</span>
        <span class="subtitle-text">{{ subtitleText }}</span>
      </div>
    </transition>

    <!-- Hidden Audio -->
    <audio
      ref="audioRef"
      @timeupdate="onTimeUpdate"
      @ended="onAudioEnded"
      @loadedmetadata="onAudioLoaded"
      @error="onAudioError"
      @waiting="onAudioWaiting"
      @stalled="onAudioStalled"
    />

    <!-- Keyboard hint (first visit) -->
    <transition name="hint-fade">
      <div
        v-if="showKeyboardHint"
        class="keyboard-hint"
        role="dialog"
        aria-modal="true"
        :aria-label="$t('slidePlayer.keyboardHintAria')"
        @click.self="dismissKeyboardHint"
      >
        <div class="hint-card">
          <div class="hint-row"><kbd>←</kbd><kbd>→</kbd> {{ $t('slidePlayer.kbPageNav') }}</div>
          <div class="hint-row"><kbd>Space</kbd> {{ $t('slidePlayer.kbPlayPause') }}</div>
          <div class="hint-row"><kbd>F</kbd> {{ $t('slidePlayer.kbFullscreen') }}</div>
          <div class="hint-row"><kbd>Esc</kbd> {{ $t('slidePlayer.kbExitFullscreen') }}</div>
          <button type="button" class="keyboard-hint-dismiss" @click="dismissKeyboardHint">{{ $t('slidePlayer.closeHint') }}</button>
          <span class="hint-dismiss">{{ $t('slidePlayer.dismissHint') }}</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getSlidePages } from '@/plugins/interactive/api/slide'
import { evaluateFlow } from '@/plugins/interactive/api/queryCourseware'
import { loadAuthResource, clearImageCache } from '@/utils/authImage'
import { getLearningProgress, createLearningProgress, updateLearningProgress, reportVideoProgress } from '@/api/learning-progress'
import { useUserStore } from '@/store/user'
import { ArrowLeft, ArrowRight, VideoPlay, VideoPause, FullScreen, Loading, RefreshRight, PictureFilled, Download, Clock, Warning, Document, Mute } from '@element-plus/icons-vue'
import { enhanceHtmlContentForA11y } from '@/plugins/interactive/composables/useHtmlSegmentBridge'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
// P1-C-7/P1-C-8：SlidePreview（教师预览 dialog）通过 in-preview 传入；courseId/sectionId
// props 优先（预览场景显式传入），route 兜底（学生端路由直接打开）
const props = defineProps({
  inPreview: { type: Boolean, default: false },
  courseId: { type: [String, Number], default: null },
  sectionId: { type: [String, Number], default: null }
})
const emit = defineEmits(['close'])
const courseId = computed(() => props.courseId ?? route.params.courseId)
const chapterId = computed(() => route.query.chapterId || route.params.chapterId || null)
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
// F8（P2）：页点条缩略图 —— PPT 页复用 /pages/{n}/image 拉真实缩略图，HTML 页用
// SVG 占位（第一段文字 + 图标）；IntersectionObserver 懒加载 + 缓存，失败回退色块。
// thumbUrls/thumbLoading/thumbFailed 均以 pageIndex 为键（与 pages 数组对齐）
const thumbStripRef = ref(null)
const thumbUrls = reactive({})         // { [pageIndex]: blobUrl } —— 已加载缩略图
const thumbLoading = reactive({})      // { [pageIndex]: true } —— 加载中（占位色块）
const thumbFailed = reactive({})       // { [pageIndex]: true } —— 加载失败（回退色块）
let thumbObserver = null
const lastDirection = ref(1)
let countdownTimer = null
let pendingTimer = null
let autoAdvanceTimer = null
let loadingTimer = null
const currentAudioSrcGen = ref(0)

// P1-C-5/P1-C-3: 'loading' | 'ready' | 'pending'(等待生成) | 'generating'(生成中) | 'failed'(生成失败) | 'none' | 'error'(加载失败)
const audioStatus = ref('none')
// P0-G：<audio> 加载错误分类（'network' | 'decode' | 'unsupported' | 'unknown'）→ error 态文案区分
const audioErrorType = ref('')
const audioErrorHint = computed(() => {
  switch (audioErrorType.value) {
    case 'network': return t('slidePlayer.audioErrNetwork')
    case 'decode': return t('slidePlayer.audioErrDecode')
    case 'unsupported': return t('slidePlayer.audioErrUnsupported')
    default: return ''
  }
})
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
// U-2（R-4 渐进增强）：getAutoplayPolicy 为实验性 API（MDN limited compatibility），
// 安全 try/catch —— 探测失败不影响主路径。'disallowed' 表示浏览器明确要求用户激活后才可播放。
const autoplayDisallowed = ref(false)
let lastStatePush = 0                // 父→iframe 时间消息节流（~4Hz，R-9）
let iframeReadyV2 = false            // v2 握手完成
let courseCompleted = false          // 全部播完（完成态，R-10）

// U-3（a11y）：srcdoc 在播放器侧再做一次"段元素键盘可达 + aria-current"增强
// （后端 enhanceHtmlSegments 已注入 data-segment / 高亮 CSS / 点击桥；此处补
//  tabindex/role/aria-label + Enter/Space keydown 桥 + aria-current 同步，见
//  composables/useHtmlSegmentBridge.js）。
const htmlSrcDoc = computed(() => {
  const page = currentPage.value
  if (page?.contentType !== 'HTML_DIRECT' || !page?.htmlContent) return ''
  return enhanceHtmlContentForA11y(page.htmlContent, page.segments || [])
})

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
    // F8（P2）：页点条缩略图懒加载 —— pages 就绪后观察缩略图条（IO 懒加载，非视野内不请求）
    observeThumbs()
    // P1I-015: 仅预加载前 3 页和相邻页，其余按需触发（preloadAdjacentImages 懒加载）
    const initialIndices = [0, 1, 2].filter(i => i < pages.value.length)
    await Promise.allSettled(initialIndices.map(idx => loadPageImage(idx)))
  } catch {
    pageError.value = true
    ElMessage.error(t('slidePlayer.loadSlidesFailed'))
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
      ElMessage.success(t('slidePlayer.imageLoadSuccess'))
    } else {
      ElMessage.error(t('slidePlayer.imageLoadFailedRetry'))
    }
  } catch {
    ElMessage.error(t('slidePlayer.imageLoadFailedNetwork'))
  } finally {
    delete imageRetrying[pageIndex]
  }
}

// ===== F8（P2）：页点条缩略图 =====
function isHtmlPage(p) { return p?.contentType === 'HTML_DIRECT' }

// a11y：缩略图 aria-label 携带加载状态（设计 §7.1：aria-label="第 N 页[已加载]"）
function thumbAriaLabel(p, i) {
  const loaded = Boolean(thumbUrls[i] || isHtmlPage(p))
  return loaded ? t('slidePlayer.thumbLoadedAria', { n: i + 1 }) : t('slidePlayer.pageLabel', { n: i + 1 })
}

// HTML 缩略图占位文本：取正文前 8 字（剥脚本/样式/标签），无正文回退 'HTML'
function htmlThumbText(p) {
  const raw = (p?.htmlContent || '')
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return raw.slice(0, 8) || 'HTML'
}

// 加载第 i 页缩略图（懒加载触发后调用；缓存避免重复请求；HTML 页直接走占位不发无效请求）
async function loadThumb(i) {
  const page = pages.value[i]
  if (!page || thumbUrls[i] || thumbLoading[i] || thumbFailed[i]) return
  if (isHtmlPage(page)) return
  thumbLoading[i] = true
  const relUrl = `/courses/${courseId.value}/slides/pages/${page.pageNumber}/image`
  try {
    const blobUrl = await loadAuthResource(relUrl)
    if (blobUrl) {
      thumbUrls[i] = blobUrl
      delete thumbFailed[i]
    } else {
      thumbFailed[i] = true
    }
  } catch {
    thumbFailed[i] = true
  } finally {
    thumbLoading[i] = false
  }
}

// 缩略图 blob 解码失败（如后端 404 返回非图片内容）→ 回退色块
function thumbLoadError(i) {
  delete thumbUrls[i]
  thumbFailed[i] = true
  thumbLoading[i] = false
}

// 页点条缩略图懒加载：仅视野内（条内横向滚动可见）的缩略图发起加载；
// 不支持 IntersectionObserver 的浏览器退化为全部加载（页数有限，数量可接受）
async function observeThumbs() {
  if (thumbObserver) { thumbObserver.disconnect(); thumbObserver = null }
  await nextTick() // 等待 v-for 渲染出缩略图按钮
  if (typeof IntersectionObserver === 'undefined' || !thumbStripRef.value) {
    pages.value.forEach((_, i) => loadThumb(i))
    return
  }
  thumbObserver = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const idx = Number(entry.target.getAttribute('data-thumb-index'))
        if (!Number.isNaN(idx)) loadThumb(idx)
      }
    }
  }, { root: playerRef.value, rootMargin: '120px 40px' })
  requestAnimationFrame(() => {
    const els = thumbStripRef.value?.querySelectorAll('[data-thumb-index]') || []
    els.forEach((el) => thumbObserver.observe(el))
  })
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
  ElMessage.error(t('slidePlayer.htmlLoadFailedRefresh'))
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

// U-6（清单 4-7）：evaluateFlow 用真实播放进度替代页序号估计。
// 原实现 userProgress=(current+1)/total 在 SKIP_IF_KNOWN（user_progress>=0.8）下几乎不命中：
// 例如第 5/10 页播完时序号进度仅 0.5，而实际"已听完该页"应为 1.0。
// 新实现按 已播时长/总时长 计算（后端已下发 audio.durationMs / segments[].durationMs）；
// 0% 起步（刚进入未播放）时返回 0 → 不会误触发 SKIP（需 userProgress > 0.5 才算"已听完"）。
function currentPlaybackProgress() {
  const page = currentPage.value
  if (!page) return 0
  // HTML 段模式：累计已播段时长 + 当前段已播时长 ÷ 总时长
  if (segmentMode.value) {
    const totalMs = segments.value.reduce((sum, s) => sum + (s.audio?.durationMs || 0), 0)
    if (totalMs <= 0) return 0
    const playedBeforeMs = segments.value
      .slice(0, activeSegmentIndex.value)
      .reduce((sum, s) => sum + (s.audio?.durationMs || 0), 0)
    const playedMs = playedBeforeMs + (audioTime.value * 1000)
    return Math.min(1, playedMs / totalMs)
  }
  // PPT / 整页单音频：已播 / 总时长
  if (audioDuration.value > 0) return Math.min(1, audioTime.value / audioDuration.value)
  // 无时长信息（异常兜底）：退化为页序号估计，保证请求体始终有值
  return Math.min(1, (current.value + 1) / Math.max(1, pages.value.length))
}

// P1-1：flow 求值后翻页（NEXT/BRANCH_DEPENDS/SKIP_IF_KNOWN），失败退化为线性
// P0-F (I2)：学生端入口（学习中心/课程广场/继续学习/课程详情 goLearn）不带 sectionId 参数，
// 但页面 VO 已从后端携带 sectionId（buildV2PptPages 设置 vo.sectionId）→ 从页面推断，
// 使学生端 BRANCH/SKIP 规则真实生效（不再恒线性）。
async function advanceToNextPage(expectedGen) {
  const page = currentPage.value
  const effectiveSectionId = sectionId.value ?? page?.sectionId ?? null
  if (page?.flows?.length && page.id != null && effectiveSectionId != null) {
    try {
      const res = await evaluateFlow(courseId.value, effectiveSectionId, {
        currentPageId: page.id,
        userProgress: currentPlaybackProgress()
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
      title: `${current.value + 1}/${pages.value.length} · ${(subtitleText.value || t('slidePlayer.coursewarePlayback')).slice(0, 60)}`,
      artist: t('app.title')
    })
    navigator.mediaSession.playbackState = playing.value ? 'playing' : 'paused'
  } catch { /* 忽略 */ }
}

function onSlideAudioMessage(event) {
  // D9（P0 修复）：origin 是字符串 "null" 而非 JS null；source 必须为当前 iframe
  if (event.origin !== 'null') return
  const iframe = currentHtmlIframe()
  // P2-2026-08-21 安全加固: 无 iframe 时直接拒绝(原仅凭 origin==null 放行，页面内其它 sandbox iframe 可注入指令)
  if (!iframe || event.source !== iframe.contentWindow) return
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
    // P1-C-3/P0-H：按段音频真实状态诚实提示 —— GENERATING/PROCESSING → 生成中；
    // FAILED → 生成失败（附后端 errorMessage）；PENDING/无音频记录 → 尚未生成
    const segStatus = seg.audio?.status
    if (segStatus === 'GENERATING' || segStatus === 'PROCESSING') {
      audioStatus.value = 'generating'
      ElMessage.warning(t('slidePlayer.segmentGenerating'))
    } else if (segStatus === 'FAILED') {
      audioStatus.value = 'failed'
      ElMessage.warning(t('slidePlayer.segmentGenFailed', { msg: seg.audio?.errorMessage || t('slidePlayer.teacherRegenAudio') }))
    } else {
      audioStatus.value = 'error'
      ElMessage.warning(t('slidePlayer.segmentNotGenerated'))
    }
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

// U-2（R-4 渐进增强）：探测浏览器自动播放策略。
// - 'disallowed' → iOS/Safari 等严格策略：用户激活前一切 play() 都会被拒 →
//   解锁前不发起自动播放，直接展示「▶ 点击开始」（不等首次交互）；
// - 'allowed' / undefined / 抛异常 → 保持现有"首次交互解锁"逻辑
//   （play().catch(NotAllowedError) 已在 playAudio/playSegment 兜底）。
function probeAutoplayPolicy() {
  try {
    if (typeof navigator.getAutoplayPolicy === 'function') {
      autoplayDisallowed.value = navigator.getAutoplayPolicy('media') === 'disallowed'
    }
  } catch { /* 实验性 API 失败不影响主路径 */ }
}

// 自动起播前置判断：策略明确 disallowed 时，用户激活前一律不自动起播。
function canAttemptAutoplay() {
  return !autoplayDisallowed.value || unlocked.value
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
    ElMessage.warning(t('slidePlayer.noHtmlContent'))
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
  // G3-P0-5: 离开当前页前上报本页播放进度（此时 current/audioTime 仍是旧页值，
  // currentPlaybackProgress 计算的是"已离开页"的真实进度；0 进度自动跳过）
  updateVideoProgress()
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
    // U-2：策略明确 disallowed 且用户未激活时不自动起播（立即显示「▶ 点击开始」）
    if (autoMode.value && canAttemptAutoplay()) playAudio()
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
    const hasGenerating = segments.value.some(s => s.audio?.status === 'GENERATING' || s.audio?.status === 'PROCESSING')
    // P1-C-3：FAILED 段不再归入"无音频"—— 明确提示生成失败并提供重试入口
    const hasFailed = segments.value.some(s => s.audio?.status === 'FAILED')
    const allReady = hasReady && segments.value.every(s => s.audio?.url)
    // P0-H：混合状态页级取"最差"（FAILED > GENERATING/PROCESSING > 部分 READY+PENDING > 全无）
    if (hasFailed) {
      audioStatus.value = 'failed'
    } else if (hasGenerating) {
      audioStatus.value = 'generating'
    } else if (allReady) {
      audioStatus.value = 'ready'
    } else if (hasReady) {
      audioStatus.value = 'pending' // 部分 READY + 部分 PENDING/无音频
      // P2-2026-08-21: 与 PPT pending 分支(:1183)对齐，触发"生成耗时较长"提示计时
      pendingStartTime.value = Date.now()
      pendingTimeoutWarning.value = ''
      startPendingTimer()
    } else {
      audioStatus.value = 'none'
    }
    audioDuration.value = 0
    audioTime.value = 0
    audioProgress.value = 0
    if (audioRef.value) { audioRef.value.pause(); audioRef.value.src = '' }
    playing.value = false
    checkHtmlInteractive(page)
    if (unlocked.value && autoMode.value && audioStatus.value === 'ready' && canAttemptAutoplay()) playSegment(0)
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
    // R-7：legacy HTML 仅有分段音频元数据 → 退化为整页单音频（父页宿主真正加载播放，
    // 不再置 none+pause 导致"分段为空但不播"）
    clearPendingTimer()
    clearTimeout(loadingTimer)
    audioStatus.value = 'loading'
    audioDuration.value = page.segmentAudio.duration || 0
    audioTime.value = 0
    audioProgress.value = 0
    const segUrl = page.segmentAudio.url
    if (segUrl.includes('token=') || segUrl.startsWith('/api/courses/')) {
      // token URL（能力凭证，如 /api/courses/{cid}/slides/pages/N/audio?token=...）可无鉴权头直载
      audioRef.value.src = segUrl
      audioRef.value.load()
    } else {
      // 旧式鉴权 URL → 走带 Authorization 的 blob 加载（同 narrationAudioUrl 路径）
      const blobUrl = await loadAuthResource(segUrl.replace(/^\/api/, ''))
      if (gen !== currentAudioSrcGen.value) return
      if (blobUrl) {
        audioRef.value.src = blobUrl
        audioRef.value.load()
      } else {
        audioStatus.value = 'error'
      }
    }
    loadingTimer = setTimeout(() => {
      if (audioStatus.value === 'loading' && gen === currentAudioSrcGen.value) {
        audioStatus.value = 'error'
      }
    }, 10000)
    checkHtmlInteractive(page)
    return
  }

  // P1-C-5：判断顺序调整 —— 先按 narrationStatus 精确提示，
  // 再兜底"无音频"。v2 PPT GENERATING 页（pickReadyAudio 返回 null →
  // audio.url / narrationAudioUrl 均为 null）与 legacy PENDING 页若先判
  // narrationAudioUrl 会全部落入「该页无讲解音频」，而非真实状态。
  if (page?.narrationStatus === 'PENDING' || page?.narrationStatus === 'AUDIO_PENDING') {
    audioStatus.value = 'pending'
    pendingStartTime.value = Date.now()
    pendingTimeoutWarning.value = ''
    audioDuration.value = 0
    audioRef.value.src = ''
    startPendingTimer()
    checkHtmlInteractive(page)
    return
  }

  if (page?.narrationStatus === 'GENERATING' || page?.narrationStatus === 'AUDIO_GENERATING') {
    audioStatus.value = 'generating'
    audioDuration.value = 0
    audioRef.value.src = ''
    checkHtmlInteractive(page)
    return
  }

  // P1-C-3：AUDIO_FAILED（G3 后端新枚举）→ 显示「音频生成失败 [重试]」，不再落入"无音频"
  if (page?.narrationStatus === 'FAILED' || page?.narrationStatus === 'AUDIO_FAILED') {
    audioStatus.value = 'failed'
    audioDuration.value = 0
    audioRef.value.src = ''
    checkHtmlInteractive(page)
    return
  }

  if (!page?.narrationAudioUrl) {
    audioStatus.value = 'none'
    audioDuration.value = 0
    audioRef.value.src = ''
    return
  }

  // P2-2026-08-21: 严格枚举比较会把 legacy(状态缺失/v1 旧值)但有 narrationAudioUrl 的页误判"无讲解音频"；
  // 前面已显式处理 PENDING/GENERATING/FAILED，此处仅拒绝明确非就绪状态
  if (page?.narrationStatus && page.narrationStatus !== 'AUDIO_READY' && page.narrationStatus !== 'READY') {
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
      pendingTimeoutWarning.value = t('slidePlayer.pendingTimeoutLong')
      clearPendingTimer()
    } else if (elapsed >= 240) {
      pendingTimeoutWarning.value = t('slidePlayer.pendingTimeoutSoon')
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
  if (audioStatus.value === 'pending' || audioStatus.value === 'none' || audioStatus.value === 'generating' || audioStatus.value === 'failed') return
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
// F9（L0 铁律）：舞台点击 = 自动播放 toggle，不再静默单向关闭 —— 行为变化必须告知用户。
// 学生误触舞台即静默关闭 autoMode 且无恢复路径 = 体验断裂（后续页不再自动翻却无提示）。
// 单击页面区域仅切换 autoMode；导航箭头 / HTML 下载按钮等已 @click.stop 隔离，互不影响；
// toast 1.5s 明确告知当前状态，再点一次可恢复 autoMode（双态可逆，L0：不允许不可逆的静默行为变更）。
function handleStageClick() {
  autoMode.value = !autoMode.value
  if (autoMode.value) {
    ElMessage.success({ message: t('slidePlayer.autoPlayOn'), duration: 1500 })
  } else {
    ElMessage.info({ message: t('slidePlayer.autoPlayOff'), duration: 1500 })
  }
}

function onTimeUpdate() {
  if (!audioRef.value) return
  // P0-G：缓冲恢复（waiting/stalled 期间置 loading）→ 播放中自动回 ready，避免 UI 卡在"加载中"
  if (audioStatus.value === 'loading' && playing.value) audioStatus.value = 'ready'
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

// P0-G：<audio> 加载/解码错误 → 即时可感知错误态（L0：错误不许静默卡死/无限转圈）
// P14-C (空 src 误导): 重置音频时 src='' 按 HTML spec 会触发一次文档 URL 的媒体加载,
// 触发 onAudioError（MEDIA_ERR_SRC_NOT_SUPPORTED），无条件置 error 会覆盖诚实状态
// （pending/generating/none/failed）。reset 行为必须被识别并忽略。
function onAudioError(e) {
  // 空 src 触发的是 reset 行为，不应被当作错误
  const rawSrc = e?.target?.getAttribute?.('src')
  const resolvedSrc = e?.target?.src || e?.target?.currentSrc
  if (!rawSrc || resolvedSrc === window.location.href) {
    return
  }
  const err = e?.target?.error
  // MediaError codes: 2=MEDIA_ERR_NETWORK 3=MEDIA_ERR_DECODE 4=MEDIA_ERR_SRC_NOT_SUPPORTED
  if (err?.code === 2) {
    audioErrorType.value = 'network'
  } else if (err?.code === 3) {
    audioErrorType.value = 'decode'
  } else if (err?.code === 4) {
    audioErrorType.value = 'unsupported'
  } else {
    audioErrorType.value = 'unknown'
  }
  console.warn('[audio] error:', err)
  clearTimeout(loadingTimer)
  audioStatus.value = 'error'
  audioTime.value = 0
  audioProgress.value = 0
  playing.value = false
  updateMediaSession()
  ElMessage.error(t('slidePlayer.audioLoadFailedNetwork'))
}

function onAudioWaiting() {
  // 播放中缓冲中断 → 暂置 loading（诚实提示）；恢复由 onTimeUpdate 兜底回 ready
  if (audioStatus.value === 'ready' && playing.value) audioStatus.value = 'loading'
}

function onAudioStalled() {
  // stalled 通常伴随 waiting；一致处理（10s loadingTimer 与 @error 兜底网络中断）
  if (audioStatus.value === 'ready' && playing.value) audioStatus.value = 'loading'
}

function onAudioEnded() {
  const expectedGen = currentAudioSrcGen.value
  playing.value = false; autoCountdown.value = 0
  // G3-P0-5: 整页音频播放结束 → 上报本页进度（audioTime=duration → ratio=1.0 → video_progress=100）
  updateVideoProgress()
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
          // P1-C-8：教师预览零记录 → 文案必须诚实（预览结束 ≠ 学习完成）
          notifyCourseCompleted()
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
    notifyCourseCompleted()
  }
}

// P1-C-8：完成/结束通知 —— 按角色诚实区分：
// 学生 =「本课学习完成」；教师/管理员预览 =「课件预览结束」（isStudent=false 不写进度）
function notifyCourseCompleted() {
  courseCompleted = true
  ElMessage.success(isStudent.value ? t('slidePlayer.courseCompleted') : t('slidePlayer.previewEnded'))
}

// P1-C-3：音频生成失败 → 重试入口（重新加载该页音频状态；生成操作本身在教师端）
// P0-G：error（加载失败）→ 直接重载音频资源，给学生即时恢复路径；failed（生成失败）→ 引导教师重新生成
function handleAudioRetry() {
  if (audioStatus.value === 'error') {
    ElMessage.info(t('slidePlayer.reloadingAudio'))
  } else if (isStudent.value) {
    ElMessage.info(t('slidePlayer.audioGenFailedHint'))
  } else {
    ElMessage.info(t('slidePlayer.regenInAudioPanel'))
  }
  loadAudio(current.value)
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

// P1-C-8：退出语义 —— 预览 dialog 内返回按钮 emit 'close'（关闭预览 dialog，保留管理页上下文），
// 而非 $router.back()（会误退整个管理页）；学生端路由直接打开时才 router.back()
function handleBack() {
  if (props.inPreview) {
    emit('close')
  } else {
    router.back()
  }
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

// P1I-016: 在进度上报时增加 sectionId 参数（query 优先，path 兜底——支持章节级管理页内嵌预览）；
// P1-C-7: 预览场景 SlidePreview 显式传入 sectionId（props 优先）
const sectionId = computed(() => props.sectionId ?? (route.query.sectionId || route.params.sectionId || null))

// P0-2: 创建/获取进度记录
async function ensureProgress() {
  if (!courseId.value || !isStudent.value) return
  try {
    // P1-2026-08-21: 主流程无 chapterId 时 Number(null)=0 永不匹配 null 记录 → 每次新建重复记录；
    // 用页面派生的 effective ids 并按 sectionId 匹配
    const page = currentPage.value
    const effSectionId = sectionId.value ?? page?.sectionId ?? null
    const effChapterId = chapterId.value ?? page?.chapterId ?? null
    const existing = await getLearningProgress({ courseId: courseId.value })
    const records = existing.data || []
    const slideRecord = effSectionId != null
      ? records.find(r => r.sectionId != null && Number(r.sectionId) === Number(effSectionId))
      : records.find(r => r.chapterId != null && Number(r.chapterId) === Number(effChapterId))
    if (!slideRecord) {
      await createLearningProgress({
        courseId: courseId.value,
        chapterId: effChapterId != null ? Number(effChapterId) : undefined,
        sectionId: effSectionId != null ? Number(effSectionId) : undefined,
      })
    }
  } catch (e) { if (courseId.value) console.warn('[SlidePlayer] ensureProgress failed', e.message) }
}

// P0-2: 翻到最后一页时标记完成
async function markSlideComplete() {
  if (!courseId.value || !isStudent.value) return
  try {
    // P1-2026-08-21: 与 ensureProgress 一致的 effective ids + 按 sectionId 匹配
    const page = currentPage.value
    const effSectionId = sectionId.value ?? page?.sectionId ?? null
    const effChapterId = chapterId.value ?? page?.chapterId ?? null
    const existing = await getLearningProgress({ courseId: courseId.value })
    const records = existing.data || []
    const slideRecord = effSectionId != null
      ? records.find(r => r.sectionId != null && Number(r.sectionId) === Number(effSectionId))
      : records.find(r => r.chapterId != null && Number(r.chapterId) === Number(effChapterId))
    if (slideRecord?.id) {
      await updateLearningProgress(slideRecord.id, { completed: true })
    } else {
      await createLearningProgress({
        courseId: courseId.value,
        chapterId: effChapterId != null ? Number(effChapterId) : undefined,
        sectionId: effSectionId != null ? Number(effSectionId) : undefined,
        completed: true,
      })
    }
  } catch { /* 静默 */ }
}

// G3-P0-5（P0-5 flow 端到端）：上报本课时播放进度（翻页离开时 / 音频 ended / 单页挂载时触发）。
// 服务端计算 video_progress = 已播/总时长 写入 learning_progress，供 evaluateFlow 的
// SKIP_IF_KNOWN 服务端读取 —— 此前纯 PPT/HTML 学习场景该字段恒 null → SKIP 规则永不命中。
// fire-and-forget：失败静默，绝不阻塞播放（L0：流畅体验优先）。
// 用 currentPlaybackProgress()（已播/总时长比例，0.0-1.0）换算虚拟秒数（total=1000），
// 服务端算出的 video_progress 百分比与真实播放进度一致；ratio<=0（刚进入未播放）跳过，
// 避免翻页瞬间以 0 进度覆盖已累计的真实进度。
function updateVideoProgress() {
  // P1-2026-08-21: 学生主入口不带 sectionId 参数 → 原恒早退致 video_progress 永不落库；
  // 与 advanceToNextPage 一致，从当前页 VO 派生 effectiveSectionId
  const page = currentPage.value
  const effectiveSectionId = sectionId.value ?? page?.sectionId ?? null
  if (!courseId.value || !effectiveSectionId || !isStudent.value) return
  if (!page) return
  const ratio = currentPlaybackProgress()
  if (ratio <= 0) return
  reportVideoProgress(courseId.value, effectiveSectionId, Math.round(ratio * 1000), 1000)
    .catch(() => { /* fire-and-forget：上报失败不影响播放 */ })
}

// G3-P0-6：0 页课件空状态 → 返回课程详情（学生 CourseDetail 路由 /student/courses/:id）
function goBackToCourse() {
  if (courseId.value) {
    router.push(`/student/courses/${courseId.value}`)
  } else {
    router.back()
  }
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
  // U-2：渐进增强 —— 探测自动播放策略（实验性 API，安全兜底）
  probeAutoplayPolicy()
  setupMediaSession()
  await ensureProgress()
  await loadPages()
  if (pages.value.length > 0) {
    await loadAudio(0)
    // P1-C-6：单页课件 current 恒 0，watch(current) 永不触发 → 挂载后立即标记完成；
    // 无音频单页无 ended 事件 → 同步触发完成/预览结束通知（有音频单页由 ended 自然触发）
    if (pages.value.length === 1) {
      if (audioStatus.value === 'none' || audioStatus.value === 'failed') {
        notifyCourseCompleted()
      }
      markSlideComplete()
      // G3-P0-5: 单页课件无翻页/ended 触发 → 挂载后上报一次进度（页序号退化 ratio=1.0）
      updateVideoProgress()
    }
  }
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
  // F8（P2）：断开缩略图懒加载观察器，避免组件卸载后 IO 回调泄漏
  if (thumbObserver) { thumbObserver.disconnect(); thumbObserver = null }
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

/* P1-C-7（L0 铁律）：教师预览态 banner —— warning 黄 + 居中 + 退出按钮，
   教师打开预览瞬间即可确认「这是预览、进度不被记录、quiz 不影响分支」 */
.teacher-preview-banner {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  padding: 8px 16px; min-height: 42px; flex-shrink: 0;
  background: rgba(245, 158, 11, 0.16); color: #fbbf24;
  border-bottom: 1px solid rgba(245, 158, 11, 0.35);
  font-size: 13px; z-index: 110; text-align: center;
}
.tpb-text { line-height: 1.5; }
.tpb-exit {
  border: 1px solid rgba(245, 158, 11, 0.55); border-radius: 6px;
  background: transparent; color: #fbbf24; font-size: 12px;
  padding: 3px 12px; cursor: pointer; flex-shrink: 0;
}
.tpb-exit:hover { background: rgba(245, 158, 11, 0.2); }
.tpb-exit:focus-visible { outline: 3px solid #facc15; outline-offset: 2px; }

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
/* F8（P2，设计 §7.1）：页点条缩略图 —— 由纯色圆块升级为真实缩略图
   （PPT 图片 / HTML SVG 占位）；条内横向滚动，非视野内缩略图不加载（IO 懒加载） */
.page-thumb-strip {
  display: flex; gap: 6px; align-items: center;
  max-width: 420px; overflow-x: auto; scrollbar-width: none;
  padding: 2px 0;
}
.page-thumb-strip::-webkit-scrollbar { display: none; }
.thumb {
  position: relative; flex-shrink: 0; width: 44px; height: 28px;
  border-radius: 6px; overflow: hidden; padding: 0; cursor: pointer;
  background: rgba(255,255,255,.12); border: 1px solid rgba(255,255,255,.18);
  transition: all var(--duration-base) ease;
}
.thumb.active {
  border-color: var(--player-accent);
  box-shadow: 0 0 8px var(--player-accent-glow);
  transform: scale(1.08);
}
.thumb.has-audio::after {
  content: ''; position: absolute; bottom: 2px; right: 2px;
  width: 5px; height: 5px; border-radius: 50%; background: #22c55e;
}
.thumb:hover { border-color: rgba(255,255,255,.45); }
.thumb-img { display: block; width: 100%; height: 100%; object-fit: cover; }
.thumb-block { display: block; width: 100%; height: 100%; background: rgba(255,255,255,.12); }
.thumb-block--error { background: rgba(239,68,68,.28); }
.thumb-html {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px; width: 100%; height: 100%; color: rgba(148,163,184,.95);
}
.thumb-html-text {
  max-width: 92%; overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; font-size: 8px; line-height: 1.2;
}
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

/* G3-P0-6: 0 页课件空状态 —— 明确告知当前状况 + 该怎么办（L0） */
.player-empty {
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 24px; background: var(--player-bg);
}
.player-empty-title { font-size: 16px; font-weight: 600; color: var(--player-text); }
.player-empty-hint { margin: 8px 0 16px; font-size: 13px; color: var(--player-text-secondary); }

/* --- Slide Stage --- */
.slide-stage { flex: 1; display: flex; align-items: center; justify-content: center; position: relative; cursor: pointer; padding: 12px 0; }
.slide-container { position: relative; display: flex; align-items: center; justify-content: center; }
.slide-wrapper { line-height: 0; position: relative; }
.slide-image {
  width: min(92vw, 1400px); max-width: min(92vw, 1400px); max-height: min(82vh, 900px); height: auto;
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
.audio-status.generating { color: #f59e0b; }
.audio-status.failed { color: var(--player-danger); }
.audio-status.error { color: var(--player-danger); }
/* L0 U-1：无音频页状态栏恒显 —— 灰色中性提示（区别于 error 红色），
   让学生明白"该页无讲解音频"是内容属性而非故障 */
.audio-status.none { color: #909399; }
.status-no-audio { display: inline-flex; align-items: center; gap: 4px; }
.status-failed-retry { text-decoration: underline; margin-left: 2px; }
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
.thumb:focus-visible {
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

/* F10（P2 移动端）：480px 专项断点 —— 页点缩略图条过挤隐藏、header-center 收窄、
   speed-group 兜底隐藏（768 断点已隐藏，此处自文档化） */
@media (max-width: 480px) {
  .narration-panel { position: absolute; right: 0; top: 48px; bottom: 60px; z-index: 50; width: 260px; }
  .narration-panel.collapsed { width: 24px; }
  .page-thumb-strip { display: none; }
  .header-center { gap: 2px; }
  .page-counter { font-size: 12px; }
  .speed-group { display: none; }
}

/* F10（P2 移动端，L0：移动端必须可用）：375px 专项断点 —— 触屏友好 + 防水平滚动。
   进度条可点击区域扩大（触屏手指精度）、control-bar 紧凑、音频状态条文字缩小 */
@media (max-width: 375px) {
  .control-bar { gap: 4px; }
  .ctrl-btn { width: 32px; height: 32px; }
  .ctrl-btn-play { width: 38px; height: 38px; }
  .progress-area { gap: 4px; }
  .progress-track { height: 10px; margin: 3px 0; }
  .progress-track:hover,
  .progress-track:focus-visible { height: 10px; }
  .time-label { font-size: 11px; min-width: 30px; }
  .audio-status { font-size: 11px; padding: 2px 8px; }
  .slide-stage { padding: 8px 0; }
  .nav-arrow { width: 32px; height: 32px; }
  .player-footer { padding: 8px 10px; }
  .subtitle-bar { margin: 0 10px 6px; padding: 8px 12px; }
  .keyboard-hint .hint-card { padding: 20px; max-width: 86vw; }
}
</style>
