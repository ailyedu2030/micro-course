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
          <button v-for="(p, i) in pages" :key="i"
            class="thumb-dot" :class="{ active: i === current, 'has-audio': p.audioDuration }"
            @click="goTo(i)" :aria-label="'第' + (i + 1) + '页'" />
        </div>
      </div>
      <div class="header-right">
        <button class="btn-icon btn-auto" :class="{ active: autoMode }"
          @click="autoMode = !autoMode" :aria-label="autoMode ? '关闭自动播放' : '开启自动播放'"
          :title="autoMode ? '自动播放中' : '手动模式'">
          <el-icon :size="16"><VideoPlay v-if="autoMode" /><VideoPause v-else /></el-icon>
        </button>
        <button class="btn-icon" @click="toggleFullscreen" aria-label="全屏">
          <el-icon :size="16"><FullScreen /></el-icon>
        </button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="player-main">
      <!-- Slide Image Area -->
      <section class="slide-stage" @click="handleStageClick">
        <div class="slide-frame">
          <transition :name="transitionName" mode="out-in">
            <div class="slide-wrapper" :key="current">
              <img :src="imageUrls[current] || ''" class="slide-image"
                :alt="'第' + (current + 1) + '页'" loading="eager" />
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

      <!-- Narration Panel -->
      <aside class="narration-panel" :class="{ collapsed: !showNarration }">
        <button class="narration-handle" @click="showNarration = !showNarration"
          :aria-label="showNarration ? '收起讲述稿' : '展开讲述稿'">
          <el-icon :size="14"><ArrowRight v-if="!showNarration" /><ArrowLeft v-else /></el-icon>
        </button>
        <div v-if="showNarration" class="narration-body">
          <div class="narration-label">
            <el-icon :size="14"><Notebook /></el-icon>
            <span>讲述稿</span>
          </div>
          <div class="narration-scroll" ref="narrationRef">
            <p class="narration-text">{{ currentPage?.narrationScript || '暂无讲述稿' }}</p>
          </div>
        </div>
      </aside>
    </main>

    <!-- Bottom Controls -->
    <footer class="player-footer">
      <div class="control-bar">
        <button class="ctrl-btn" @click="goTo(Math.max(0, current - 1))" :disabled="current === 0" aria-label="上一页">
          <el-icon :size="20"><ArrowLeft /></el-icon>
        </button>
        <button class="ctrl-btn ctrl-btn-play" @click="togglePlay" aria-label="播放/暂停">
          <el-icon :size="24"><VideoPause v-if="playing" /><VideoPlay v-else /></el-icon>
        </button>
        <button class="ctrl-btn" @click="goTo(Math.min(pages.length - 1, current + 1))" :disabled="current >= pages.length - 1" aria-label="下一页">
          <el-icon :size="20"><ArrowRight /></el-icon>
        </button>

        <div class="progress-area">
          <span class="time-label">{{ formatTime(audioTime) }}</span>
          <div class="progress-track" @click="seekAudioByClick">
            <div class="progress-fill" :style="{ width: audioProgress + '%' }" />
            <div class="progress-thumb" :style="{ left: audioProgress + '%' }" />
          </div>
          <span class="time-label">{{ formatTime(audioDuration) }}</span>
        </div>

        <div class="speed-group">
          <button v-for="s in speeds" :key="s"
            class="speed-chip" :class="{ active: speed === s }"
            :aria-label="s + '倍速'"
            @click="speed = s; setSpeed()">
            {{ s }}x
          </button>
        </div>
      </div>
    </footer>

    <!-- Hidden Audio -->
    <audio ref="audioRef" @timeupdate="onTimeUpdate" @ended="onAudioEnded" @loadedmetadata="onAudioLoaded" />

    <!-- Keyboard hint (first visit) -->
    <transition name="hint-fade">
      <div v-if="showKeyboardHint" class="keyboard-hint" @click="showKeyboardHint = false">
        <div class="hint-card">
          <div class="hint-row"><kbd>←</kbd><kbd>→</kbd> 翻页</div>
          <div class="hint-row"><kbd>Space</kbd> 下一页</div>
          <div class="hint-row"><kbd>F</kbd> 全屏</div>
          <div class="hint-row"><kbd>Esc</kbd> 退出全屏</div>
          <span class="hint-dismiss">点击任意处关闭</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getSlidePages } from '@/plugins/interactive/api/slide'
import { loadAuthImage, clearImageCache } from '@/utils/authImage'
import { ArrowLeft, ArrowRight, VideoPlay, VideoPause, FullScreen, Notebook } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = computed(() => route.params.id)

const pages = ref([])
const current = ref(0)
const showNarration = ref(true)
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
const playerRef = ref(null)
const audioRef = ref(null)
const imageUrls = ref({})
const audioBlobUrls = ref({})
const lastDirection = ref(1)
let countdownTimer = null

const currentPage = computed(() => pages.value[current.value] || null)

async function loadPages() {
  try {
    const res = await getSlidePages(courseId.value)
    pages.value = res.data || []
    for (const page of pages.value) {
      const relUrl = `/courses/${courseId.value}/slides/pages/${page.pageNumber}/image`
      const blobUrl = await loadAuthImage(relUrl)
      if (blobUrl) imageUrls.value[page.pageNumber - 1] = blobUrl
    }
  } catch {}
}

function goTo(index) {
  if (index < 0 || index >= pages.value.length) return
  lastDirection.value = index > current.value ? 1 : -1
  transitionName.value = index > current.value ? 'slide-next' : 'slide-prev'
  current.value = index
  audioTime.value = 0
  audioProgress.value = 0
  autoCountdown.value = 0
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  nextTick(() => { loadAudio(index); if (autoMode.value) playAudio() })
}

async function loadAudio(index) {
  const page = pages.value[index]
  if (!page?.narrationAudioUrl || !audioRef.value) return
  const relUrl = `/courses/${courseId.value}/slides/pages/${page.pageNumber}/audio`
  if (audioBlobUrls.value[relUrl]) { audioRef.value.src = audioBlobUrls.value[relUrl]; audioRef.value.load() }
  else {
    try {
      const blobUrl = await loadAuthImage(relUrl)
      if (blobUrl) { audioBlobUrls.value[relUrl] = blobUrl; audioRef.value.src = blobUrl; audioRef.value.load() }
    } catch {}
  }
  audioDuration.value = page.audioDuration || 0
}

function playAudio() {
  if (!audioRef.value) return
  audioRef.value.play().then(() => { playing.value = true }).catch(() => { playing.value = false })
}
function togglePlay() { playing.value ? (audioRef.value?.pause(), playing.value = false) : playAudio() }
function setSpeed() { if (audioRef.value) audioRef.value.playbackRate = speed.value }
function handleStageClick() { if (autoMode.value) autoMode.value = false }

function onTimeUpdate() {
  if (!audioRef.value) return
  audioTime.value = audioRef.value.currentTime
  if (audioDuration.value > 0) audioProgress.value = (audioTime.value / audioDuration.value) * 100
  if (autoMode.value && audioDuration.value > 0) {
    const remaining = Math.ceil(audioDuration.value - audioTime.value)
    autoCountdown.value = remaining <= 3 && remaining > 0 ? remaining : 0
  }
}
function onAudioLoaded() {
  if (audioRef.value) { audioDuration.value = audioRef.value.duration || audioDuration.value; audioRef.value.playbackRate = speed.value }
}
function onAudioEnded() {
  playing.value = false; autoCountdown.value = 0
  if (autoMode.value && current.value < pages.value.length - 1) goTo(current.value + 1)
}

function seekAudioByClick(e) {
  if (!audioRef.value || !audioDuration.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  audioRef.value.currentTime = ((e.clientX - rect.left) / rect.width) * audioDuration.value
}

function handleKeydown(e) {
  if (e.key === 'ArrowRight' || e.key === ' ') { e.preventDefault(); goTo(Math.min(pages.value.length - 1, current.value + 1)) }
  if (e.key === 'ArrowLeft') { e.preventDefault(); goTo(Math.max(0, current.value - 1)) }
  if (e.key === 'f' || e.key === 'F') toggleFullscreen()
  if (e.key === 'Escape' && document.fullscreenElement) document.exitFullscreen()
}
function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen()
  else playerRef.value?.requestFullscreen()
}
function formatTime(s) {
  if (!s || isNaN(s)) return '0:00'
  return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`
}

onMounted(async () => {
  await loadPages()
  if (pages.value.length > 0) loadAudio(0)
  playerRef.value?.focus()
  if (!sessionStorage.getItem('slide-player-hint-shown')) {
    showKeyboardHint.value = true
    sessionStorage.setItem('slide-player-hint-shown', '1')
  }
})
onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (audioRef.value) { audioRef.value.pause(); audioRef.value.src = '' }
  clearImageCache()
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
  display: flex; flex-direction: column; height: 100vh; width: 100vw;
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
.page-counter { font-size: 13px; font-weight: 600; color: var(--player-text); letter-spacing: 0.5px; }
.counter-divider { color: var(--player-text-secondary); margin: 0 1px; }
.page-thumb-strip { display: flex; gap: 5px; align-items: center; }
.thumb-dot {
  width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,.15);
  border: none; cursor: pointer; transition: all 200ms ease; padding: 0;
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
.slide-stage { flex: 1; display: flex; align-items: center; justify-content: center; position: relative; cursor: pointer; }
.slide-frame { position: relative; max-width: 100%; max-height: 100%; display: flex; align-items: center; justify-content: center; }
.slide-wrapper { position: relative; }
.slide-image {
  max-width: 92vw; max-height: calc(100vh - 120px); object-fit: contain;
  border-radius: 4px; box-shadow: 0 8px 40px rgba(0,0,0,.5);
}
.slide-gradient {
  position: absolute; inset: 0; border-radius: 4px;
  background: linear-gradient(180deg, transparent 85%, rgba(10,10,15,.4) 100%);
  pointer-events: none;
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
  font-size: 15px; font-weight: 700; box-shadow: 0 2px 12px var(--player-accent-glow);
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

/* ========= NARRATION PANEL ========= */
.narration-panel {
  width: 340px; background: var(--player-surface); border-left: 1px solid var(--player-border);
  display: flex; position: relative; transition: width 300ms cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}
.narration-panel.collapsed { width: 32px; }
.narration-handle {
  position: absolute; left: 0; top: 50%; transform: translateY(-50%);
  width: 24px; height: 56px; display: flex; align-items: center; justify-content: center;
  background: var(--player-surface); border: 1px solid var(--player-border); border-left: none;
  border-radius: 0 6px 6px 0; cursor: pointer; color: var(--player-text-secondary);
  transition: all 200ms ease;
}
.narration-handle:hover { background: rgba(255,255,255,.05); color: var(--player-text); }
.narration-body { display: flex; flex-direction: column; flex: 1; overflow: hidden; padding-left: 32px; }
.narration-label {
  display: flex; align-items: center; gap: 6px; padding: 14px 16px 8px;
  font-size: 12px; font-weight: 600; color: var(--player-text-secondary);
  text-transform: uppercase; letter-spacing: 1px;
}
.narration-scroll { flex: 1; overflow-y: auto; padding: 0 16px 16px; }
.narration-text {
  font-size: 14px; line-height: 2; color: var(--player-text);
  white-space: pre-wrap; margin: 0;
}

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
.progress-track:hover { height: 6px; }
.progress-track:hover .progress-thumb { opacity: 1; transform: scale(1); }
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

.speed-group { display: flex; gap: 2px; background: rgba(255,255,255,.05); border-radius: var(--radius-sm); padding: 2px; }
.speed-chip {
  padding: 3px 8px; border: none; background: transparent; color: var(--player-text-secondary);
  font-size: 12px; font-weight: 600; border-radius: 4px; cursor: pointer;
  transition: all 200ms ease;
}
.speed-chip.active { background: var(--player-accent); color: #fff; }
.speed-chip:hover:not(.active) { color: var(--player-text); }

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
