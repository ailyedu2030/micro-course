<!--
  AudioPanel.vue · 单脚本音频列表面板 (AudioManager 子组件)

  列出某个 script 的所有音频版本, 支持:
  1. 试听 (点 ▶️ 播放)
  2. A/B 对比 (同时显示多个 audio,可切换播放源)
  3. 状态显示 (GENERATING/READY/FAILED)
  4. 时长显示
  5. FAILED 状态可操作化 (L0): 分类错误原因 → 行动按钮 (重试/切换音色/联系充值)

  Props:
    courseId, scriptId, tokenLoader(fn), audioUrlFactory(fn), audioStatus(fn)
    retryingId (Number|null): 当前正在重试的 audio id (由 AudioManager 管理)

  Emits:
    retry({ audio, scriptId }): 一键重试 (复用失败音频的 voice/model)
    voice-settings(audio): 打开音色设置 (切换到默认音色重新生成)
-->
<template>
  <div class="audio-panel">
    <div v-if="loading" class="ap-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      {{ t('audio.panel.loading') }}
    </div>
    <!-- L0 U-音频空: 从未生成过音频 → 明确引导如何开始 -->
    <el-empty
      v-else-if="audios.length === 0"
      :description="t('audio.panel.empty')"
      :image-size="80"
    />
    <div v-else class="ap-list">
      <div
        v-for="audio in audios"
        :key="audio.id"
        class="ap-item"
        :class="{ 'ap-active': playingId === audio.id, 'ap-failed-item': audio.status === 'FAILED' }"
      >
        <div class="ap-row">
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
              {{ playingId === audio.id ? t('audio.panel.pause') : t('audio.panel.listen') }}
            </el-button>
            <span v-else-if="audio.status === 'GENERATING'" class="ap-pending">
              <el-icon class="is-loading"><Loading /></el-icon>
              {{ t('audio.panel.generating') }}
            </span>
            <span v-else class="ap-failed-badge">{{ t('audio.panel.failed') }}</span>
          </div>
        </div>

        <!-- L0 铁律: FAILED 状态 = 错误原因 + 该怎么办 + 行动按钮 -->
        <template v-if="audio.status === 'FAILED'">
          <el-alert
            :type="errorInfo(audio).alertType"
            :closable="false"
            show-icon
            class="ap-failed-alert"
          >
            <template #title>
              <span class="ap-failed-advice">{{ errorInfo(audio).advice }}</span>
            </template>
          </el-alert>
          <div class="ap-failed-actions">
            <el-button
              v-if="errorInfo(audio).action === 'recharge'"
              size="small"
              plain
              :disabled="retryingId !== null"
              @click="openRechargeTip"
            >
              {{ t('audio.panel.actionRecharge') }}
            </el-button>
            <el-button
              v-if="errorInfo(audio).action === 'voice'"
              size="small"
              plain
              :disabled="retryingId !== null"
              @click="emit('voice-settings', audio)"
            >
              {{ t('audio.panel.actionSwitchVoice') }}
            </el-button>
            <el-button
              v-if="errorInfo(audio).action === 'config'"
              size="small"
              plain
              :disabled="retryingId !== null"
              @click="openSupportTip"
            >
              {{ t('audio.panel.actionSupport') }}
            </el-button>
            <!-- G3-P1-C-2: 限流场景 → 5 分钟倒计时按钮（禁用 → 倒计时 → 自动启用）。
                 后端对 1002 限流立即置 FAILED，前端倒计时结束后才允许重试，避免无效点击 -->
            <el-button
              v-if="errorInfo(audio).action === 'ratelimit'"
              size="small"
              type="primary"
              plain
              :loading="retryingId === audio.id"
              :disabled="retryingId !== null || rateLimitRemaining(audio.id) > 0"
              @click="emit('retry', { audio, scriptId: props.scriptId })"
            >
              {{ rateLimitRemaining(audio.id) > 0 ? t('audio.panel.ratelimitWait', { time: formatRateLimit(rateLimitRemaining(audio.id)) }) : errorInfo(audio).actionLabel }}
            </el-button>
            <el-button
              v-if="errorInfo(audio).action !== 'ratelimit'"
              size="small"
              type="primary"
              plain
              :loading="retryingId === audio.id"
              :disabled="retryingId !== null"
              @click="emit('retry', { audio, scriptId: props.scriptId })"
            >
              {{ errorInfo(audio).actionLabel }}
            </el-button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import { classifyAudioError } from '../composables/useAudioError'

const { t } = useI18n()

const props = defineProps({
  courseId: { type: Number, required: true },
  scriptId: { type: Number, default: null },
  tokenLoader: { type: Function, required: true },
  audioUrlFactory: { type: Function, required: true },
  audioStatus: { type: Function, default: (a) => a.status },
  // 当前正在一键重试的 audio id (由 AudioManager 统一管理, 保证 loading 状态准确)
  retryingId: { type: Number, default: null }
})

const emit = defineEmits(['retry', 'voice-settings'])

const audios = ref([])
const loading = ref(true)
const playingId = ref(null)
const audioEl = ref(null)

// G3-P1-C-2: 限流 5 分钟倒计时（audioId → 剩余秒数）。倒计时归零后按钮自动启用。
const RATE_LIMIT_SECONDS = 300
const rateLimitCountdown = ref({})
const countdownTimers = {}

function rateLimitRemaining(audioId) {
  return rateLimitCountdown.value[audioId] || 0
}

function startRateLimitCountdown(audioId) {
  if (rateLimitRemaining(audioId) > 0) return
  rateLimitCountdown.value = { ...rateLimitCountdown.value, [audioId]: RATE_LIMIT_SECONDS }
  countdownTimers[audioId] = setInterval(() => {
    const next = (rateLimitCountdown.value[audioId] || 0) - 1
    if (next <= 0) {
      clearInterval(countdownTimers[audioId])
      delete countdownTimers[audioId]
      rateLimitCountdown.value = { ...rateLimitCountdown.value }
      delete rateLimitCountdown.value[audioId]
    } else {
      rateLimitCountdown.value = { ...rateLimitCountdown.value, [audioId]: next }
    }
  }, 1000)
}

function formatRateLimit(seconds) {
  const m = Math.floor(seconds / 60)
  const s = String(seconds % 60).padStart(2, '0')
  return `${m}:${s}`
}

// FAILED + 限流 → 启动倒计时（L0：明确告知"等多久"，倒计时结束按钮自动可用）
function maybeStartRateLimitCountdown(audiosList) {
  audiosList.forEach((a) => {
    if (a.status === 'FAILED' && classifyAudioError(a.errorMessage).action === 'ratelimit') {
      startRateLimitCountdown(a.id)
    }
  })
}

async function load() {
  loading.value = true
  try {
    // 无脚本时静默置空，避免以 null 请求后端（此前必现 500）
    audios.value = props.scriptId ? await props.tokenLoader(props.scriptId) : []
    maybeStartRateLimitCountdown(audios.value)
  } catch (e) {
    // F-2026-08-07-09：加载失败给出明确提示，避免未处理 rejection
    ElMessage.warning(t('audio.panel.loadFailed', { msg: e?.response?.data?.message || e?.message || t('audio.panel.unknownError') }))
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
    ElMessage.error(t('audio.panel.playFailed', { msg: err?.message || t('audio.panel.loadFailedRetry') }))
    playingId.value = null
  })
  audioEl.value.onerror = () => {
    ElMessage.error(t('audio.panel.fileCorrupted', { token: audio.audioToken?.substring(0, 8) }))
    playingId.value = null
  }
  playingId.value = audio.id
  audioEl.value.onended = () => { playingId.value = null }
}

// L0 铁律: 错误分类 → "该怎么办" + 行动按钮 (classifyAudioError 返回 action, 前端 i18n 化文案)
function errorInfo(audio) {
  const info = classifyAudioError(audio.errorMessage || '')
  const adviceMap = {
    retry: t('audio.panel.adviceRetry'),
    recharge: t('audio.panel.adviceRecharge'),
    ratelimit: t('audio.panel.adviceRatelimit'),
    voice: t('audio.panel.adviceVoice'),
    config: t('audio.panel.adviceConfig')
  }
  const labelMap = {
    retry: t('audio.panel.actionRetry'),
    recharge: t('audio.panel.actionRecharge'),
    ratelimit: t('audio.panel.actionRetry'),
    voice: t('audio.panel.actionSwitchVoice'),
    config: t('audio.panel.actionRetry')
  }
  return {
    ...info,
    advice: adviceMap[info.action] || info.advice,
    actionLabel: labelMap[info.action] || info.actionLabel
  }
}

// 余额不足: 平台 TTS 由管理员统一充值, 给出明确指引
function openRechargeTip() {
  ElMessageBox.alert(
    t('audio.panel.rechargeAlertBody'),
    t('audio.panel.rechargeAlertTitle'),
    { confirmButtonText: t('audio.panel.gotIt'), type: 'warning' }
  )
}

// 配置/服务端异常: 引导联系技术支持
function openSupportTip() {
  ElMessageBox.alert(
    t('audio.panel.supportAlertBody'),
    t('audio.panel.supportAlertTitle'),
    { confirmButtonText: t('audio.panel.gotIt'), type: 'error' }
  )
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
  // G3-P1-C-2: 清理限流倒计时定时器
  Object.values(countdownTimers).forEach(clearInterval)
})
</script>

<style scoped>
.audio-panel { width: 100%; }
.ap-loading { display: flex; gap: 8px; padding: 20px; align-items: center; color: var(--el-text-color-secondary); }
.ap-list { display: flex; flex-direction: column; gap: 8px; }
.ap-item {
  padding: 10px 14px; border-radius: 6px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.2s;
}
.ap-item:hover { border-color: var(--el-color-primary); }
.ap-item.ap-active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.ap-item.ap-failed-item { border-color: var(--el-color-danger); }
.ap-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.ap-meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.ap-voice { font-weight: 500; }
.ap-model { font-size: 12px; color: var(--el-text-color-secondary); }
.ap-duration, .ap-size { font-size: 12px; color: var(--el-text-color-secondary); }
.ap-controls { display: flex; gap: 8px; align-items: center; flex-shrink: 0; }
.ap-pending { display: inline-flex; gap: 4px; align-items: center; color: var(--el-color-warning); font-size: 13px; }
.ap-failed-badge { display: inline-flex; gap: 4px; align-items: center; color: var(--el-color-danger); font-size: 13px; }
/* L0: FAILED 状态可操作化 — 错误提示 + 行动按钮 */
.ap-failed-alert { margin-top: 10px; }
.ap-failed-advice { font-size: 13px; }
.ap-failed-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
</style>
