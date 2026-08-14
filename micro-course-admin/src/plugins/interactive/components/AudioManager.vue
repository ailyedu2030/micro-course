<!--
  AudioManager.vue · 音频管理面板 (客户体验核心)

  解决 7-19 P0 报告的根因: 音频元数据不可见 / 不可控.
  提供:
  1. 列出该课件/段落的所有音频版本 (按时间倒序)
  2. 试听对比 (A/B 切换)
  3. 一键生成新音色
  4. 显示状态 (GENERATING / READY / FAILED) + 时长
  5. L0: FAILED 一键重试 (复用失败音色/模型) + 重试历史防死循环 + 失败率告警

  Props:
    courseId, pageType ("PPT" | "HTML"), ownerId (pptPageId 或 htmlUnitId),
    scriptId, segments [{idx, segmentScriptId}] (HTML 多段)

  关键设计 (7-19 P1-C 修复兼容):
  - audio_token 是 UK, 流式 GET 不依赖 pageNumber
  - 后端强制 audio_token 是 32 字符 hex
-->
<template>
  <div class="audio-manager">
    <div class="am-header">
      <h3 class="am-title">
        <el-icon><Headset /></el-icon>
        {{ t('audio.manage.title') }}
        <el-tag v-if="totalReady > 0" type="success" size="small">{{ t('audio.manage.readyCount', { count: totalReady }) }}</el-tag>
        <el-tag v-else-if="hasGenerating" type="warning" size="small">{{ t('audio.manage.generating') }}</el-tag>
        <el-tag v-else type="info" size="small">{{ t('audio.manage.noAudio') }}</el-tag>
      </h3>
      <el-tooltip :disabled="canGenerate" :content="t('audio.manage.saveScriptFirst')" placement="top">
        <span>
          <el-button
            :icon="Plus"
            size="small"
            type="primary"
            plain
            :disabled="!canGenerate"
            @click="showGenerate = true"
          >
            {{ t('audio.manage.generateNew') }}
          </el-button>
        </span>
      </el-tooltip>
    </div>

    <!-- L0: 失败率 > 50% → 联系技术支持 (防止用户无限重试) -->
    <el-alert
      v-if="showSupportAlert"
      type="error"
      :closable="false"
      show-icon
      class="am-support-alert"
      :title="t('audio.manage.highFailureTitle')"
      :description="t('audio.manage.highFailureDesc')"
    >
      <template #default>
        <el-button size="small" type="danger" plain @click="openSupportTip">{{ t('audio.manage.contactSupport') }}</el-button>
      </template>
    </el-alert>

    <!-- PPT 单段模式 / HTML 多段模式 -->
    <div v-if="pageType === 'PPT' || (segments && segments.length === 1)" class="am-single">
      <AudioPanel
        v-if="effectiveScriptId"
        :course-id="courseId"
        :script-id="effectiveScriptId"
        :token-loader="pageType === 'PPT' ? loadPptAudios : loadHtmlAudios"
        :audio-url-factory="pageType === 'PPT' ? pptAudioUrl : htmlAudioUrl"
        :audio-status="statusLabel"
        :retrying-id="retryingId"
        @retry="handleRetry"
        @voice-settings="openVoiceSettings"
      />
      <el-empty
        v-else
        :description="t('audio.manage.saveScriptFirst')"
        :image-size="60"
      />
    </div>

    <div v-else class="am-multi">
      <el-tabs v-model="activeSegmentIdx" type="card">
        <el-tab-pane
          v-for="seg in segments || []"
          :key="seg.idx"
          :name="seg.idx"
          :label="t('audio.manage.segmentLabel', { idx: seg.idx })"
        >
          <AudioPanel
            :course-id="courseId"
            :script-id="seg.segmentScriptId"
            :token-loader="loadHtmlAudios"
            :audio-url-factory="htmlAudioUrl"
            :audio-status="statusLabel"
            :retrying-id="retryingId"
            @retry="handleRetry"
            @voice-settings="openVoiceSettings"
          />
        </el-tab-pane>
      </el-tabs>
      <el-empty
        v-if="!(segments && segments.length)"
        :description="t('audio.manage.noScriptSegments')"
        :image-size="60"
      />
    </div>

    <!-- 生成新音频对话框 -->
    <el-dialog v-model="showGenerate" :title="t('audio.manage.generateNew')" width="420px">
      <el-form label-position="top">
        <el-form-item :label="t('audio.manage.voiceLabel')">
          <el-select v-model="generateVoice" :placeholder="t('audio.manage.selectVoice')" style="width:100%">
            <el-option
              v-for="v in voiceOptions"
              :key="v.id"
              :label="v.label"
              :value="v.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('audio.manage.ttsModel')">
          <el-select v-model="generateModel" style="width:100%">
            <el-option
              v-for="m in modelOptions"
              :key="m"
              :label="m === defaultTtsModel ? t('audio.manage.recommended', { model: m }) : m"
              :value="m"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerate = false">{{ t('audio.manage.cancel') }}</el-button>
        <el-button type="primary" :loading="generating" :disabled="generating" @click="handleGenerate">{{ t('audio.manage.generate') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Headset, Plus } from '@element-plus/icons-vue'
import { listPptAudios, generatePptAudio } from '../api/pptCourseware'
import { listHtmlSegmentAudios, generateHtmlSegmentAudio } from '../api/htmlCourseware'
import { getAudioStreamUrl, getTtsOptions } from '../api/queryCourseware'
import AudioPanel from './AudioPanel.vue'

const { t } = useI18n()

const props = defineProps({
  courseId: { type: Number, required: true },
  pageType: { type: String, default: 'PPT' },
  ownerId: { type: Number, required: true },
  scriptId: { type: Number, default: null },  // PPT 用
  segments: { type: Array, default: () => [] }  // HTML 多段: [{idx, segmentScriptId}]
})

const showGenerate = ref(false)
const generating = ref(false)
const generateVoice = ref('female-shaonv')
const generateModel = ref('speech-2.8-hd')
const defaultTtsModel = ref('speech-2.8-hd')
const ttsOptions = ref(null)
const activeSegmentIdx = ref(props.segments?.[0]?.idx ?? null)

// L0 Task 5: 一键重试状态 + 重试历史 (最近 3 次防死循环)
const retryingId = ref(null)
const retryHistory = ref([])  // [{ scriptId, voice, model, at }] — 会话级最近 3 次
const MAX_RETRY = 3

const voiceOptions = computed(() => {
  if (ttsOptions.value?.voices?.length) return ttsOptions.value.voices
  return [
    { id: 'female-shaonv', label: t('audio.manage.voice.femaleShaonv') },
    { id: 'female-qingxin', label: t('audio.manage.voice.femaleQingxin') },
    { id: 'female-yujie', label: t('audio.manage.voice.femaleYujie') },
    { id: 'female-warm', label: t('audio.manage.voice.femaleWarm') },
    { id: 'male-shaonian', label: t('audio.manage.voice.maleShaonian') },
    { id: 'male-qingnian', label: t('audio.manage.voice.maleQingnian') },
    { id: 'male-dashu', label: t('audio.manage.voice.maleDashu') },
    { id: 'male-chengzhao', label: t('audio.manage.voice.maleChengzhao') }
  ]
})

const modelOptions = computed(() => {
  if (ttsOptions.value?.models?.length) return ttsOptions.value.models
  return ['speech-2.8-hd', 'speech-2.6-hd', 'speech-01', 'speech-02']
})

async function loadTtsOptions() {
  try {
    const res = await getTtsOptions(props.courseId)
    ttsOptions.value = res.data || res
    if (ttsOptions.value?.defaultVoice) generateVoice.value = ttsOptions.value.defaultVoice
    if (ttsOptions.value?.defaultModel) {
      generateModel.value = ttsOptions.value.defaultModel
      defaultTtsModel.value = ttsOptions.value.defaultModel
    }
  } catch {
    // 后端不可用时使用内置官方枚举兜底
  }
}

const effectiveScriptId = computed(() => {
  if (props.pageType === 'PPT') return props.scriptId
  if (props.segments && props.segments.length === 1) return props.segments[0].segmentScriptId
  return null
})

// F-2026-08-07-10：无脚本时禁止生成，避免 /ppt/scripts/null/audios 后端 500
const canGenerate = computed(() => {
  if (props.pageType === 'PPT') return !!effectiveScriptId.value
  return (props.segments || []).some(s => !!s.segmentScriptId)
})

// Loaders passed to AudioPanel (encapsulated by page type)
function loadPptAudios(scriptId) {
  return listPptAudios(props.courseId, scriptId).then(r => r.data || r)
}
function loadHtmlAudios(segmentScriptId) {
  return listHtmlSegmentAudios(props.courseId, segmentScriptId).then(r => r.data || r)
}

function pptAudioUrl(audio) {
  return getAudioStreamUrl(props.courseId, audio.audioToken)
}
function htmlAudioUrl(audio) {
  return getAudioStreamUrl(props.courseId, audio.audioToken)
}

function statusLabel(audio) {
  const map = {
    GENERATING: t('audio.manage.status.generating'),
    READY: t('audio.manage.status.ready'),
    FAILED: t('audio.manage.status.failed')
  }
  return map[audio.status] || audio.status
}

// 统计 (顶部 tag 显示)
const audiosBySegment = ref({})  // { segmentIdx: AudioDTO[] }

const allAudios = computed(() => Object.values(audiosBySegment.value).flat())

const totalReady = computed(() => allAudios.value.filter(a => a.status === 'READY').length)

const hasGenerating = computed(() => allAudios.value.some(a => a.status === 'GENERATING'))

// L0 Task 5: 失败率 > 50% → 显示"联系技术支持"
const failureRate = computed(() => {
  const total = allAudios.value.length
  if (!total) return 0
  const failed = allAudios.value.filter(a => a.status === 'FAILED').length
  return failed / total
})
const showSupportAlert = computed(() => failureRate.value > 0.5)

// 当切换 segment 时刷新列表
watch(activeSegmentIdx, async (idx) => {
  if (idx == null) return
  const seg = props.segments.find(s => s.idx === idx)
  if (!seg) return
  const res = await listHtmlSegmentAudios(props.courseId, seg.segmentScriptId)
  audiosBySegment.value[idx] = res.data || res
})

// 初次加载 PPT 模式的所有音频
// 【BUG #3 修复】 预加载所有 segments 音频 (包括未激活 tab), 让统计准确
async function loadAllAudios() {
  if (props.pageType === 'PPT' && effectiveScriptId.value) {
    const r = await listPptAudios(props.courseId, effectiveScriptId.value)
    audiosBySegment.value[0] = r.data || r
  } else if (props.segments && props.segments.length > 0) {
    // HTML 多段模式: 一次性预加载所有段的音频, 避免 tab 切换时才加载导致统计不准
    const promises = props.segments.map(async (seg) => {
      const r = await listHtmlSegmentAudios(props.courseId, seg.segmentScriptId)
      audiosBySegment.value[seg.idx] = r.data || r
    })
    await Promise.all(promises)
  }
}
onMounted(() => {
  loadTtsOptions()
  loadAllAudios()
})

// 生成新音频
async function handleGenerate() {
  if (generating.value) return // R-15：生成中禁用重复提交，防重复计费
  if (!canGenerate.value) {
    ElMessage.warning(t('audio.manage.saveScriptFirstShort'))
    return
  }
  generating.value = true
  try {
    let res
    if (props.pageType === 'PPT') {
      res = await generatePptAudio(props.courseId, effectiveScriptId.value, {
        voice: generateVoice.value,
        model: generateModel.value,
        ttsParams: '{}'
      })
    } else {
      const seg = props.segments.find(s => s.idx === activeSegmentIdx.value)
      res = await generateHtmlSegmentAudio(props.courseId, seg.segmentScriptId, {
        voice: generateVoice.value,
        model: generateModel.value,
        ttsParams: '{}'
      })
    }
    ElMessage.success(t('audio.manage.submitted'))
    showGenerate.value = false
    await refreshActiveAudios()
    pollUntilSettled() // R-10：3s 轮询直至 READY/FAILED
  } catch (e) {
    // F-2026-08-07-09：透传后端明确错误（如 TTS Key 未配置/超时），禁止吞成通用错误
    ElMessage.error(t('audio.manage.generateFailed', { msg: e?.response?.data?.message || e?.message || t('audio.manage.unknownError') }))
  } finally {
    generating.value = false
  }
}

// L0 Task 5: 一键重试 — 自动复用失败音频的 voice/model/script
async function handleRetry({ audio, scriptId }) {
  if (!scriptId || retryingId.value !== null) return
  // 防死循环: 同一 script 会话级最多重试 MAX_RETRY 次
  const recent = retryHistory.value.filter(h => h.scriptId === scriptId)
  if (recent.length >= MAX_RETRY) {
    ElMessage.warning(t('audio.manage.retryLimitExceeded', { count: MAX_RETRY }))
    return
  }
  retryingId.value = audio.id
  const voice = audio.voiceUsed || generateVoice.value
  const model = audio.modelUsed || generateModel.value
  try {
    if (props.pageType === 'PPT') {
      await generatePptAudio(props.courseId, scriptId, { voice, model, ttsParams: '{}' })
    } else {
      await generateHtmlSegmentAudio(props.courseId, scriptId, { voice, model, ttsParams: '{}' })
    }
    retryHistory.value.push({ scriptId, voice, model, at: Date.now() })
    // 只保留最近 3 条（会话级）
    if (retryHistory.value.length > MAX_RETRY) retryHistory.value.shift()
    ElMessage.success(t('audio.manage.retrySubmitted'))
    await refreshActiveAudios()
    pollUntilSettled()
  } catch (e) {
    ElMessage.error(t('audio.manage.retryFailed', { msg: e?.response?.data?.message || e?.message || t('audio.manage.unknownError') }))
  } finally {
    retryingId.value = null
  }
}

// L0 Task 1: 音色不可用 → 打开音色设置并预选默认音色
function openVoiceSettings() {
  if (ttsOptions.value?.defaultVoice) generateVoice.value = ttsOptions.value.defaultVoice
  showGenerate.value = true
  ElMessage.info(t('audio.manage.voiceSwitched'))
}

// L0 Task 5: 失败率告警 → 联系技术支持指引
function openSupportTip() {
  ElMessageBox.alert(
    t('audio.manage.supportAlertBody'),
    t('audio.manage.contactSupport'),
    { confirmButtonText: t('audio.manage.gotIt'), type: 'error' }
  )
}

async function refreshActiveAudios() {
  if (props.pageType === 'PPT') {
    if (!effectiveScriptId.value) return
    const r = await listPptAudios(props.courseId, effectiveScriptId.value)
    audiosBySegment.value[0] = r.data || r
  } else {
    const seg = props.segments.find(s => s.idx === activeSegmentIdx.value)
    if (!seg) return
    const r = await listHtmlSegmentAudios(props.courseId, seg.segmentScriptId)
    audiosBySegment.value[activeSegmentIdx.value] = r.data || r
  }
}

let pollTimer = null
let pollTries = 0
function pollUntilSettled() {
  clearInterval(pollTimer)
  pollTries = 0
  pollTimer = setInterval(async () => {
    pollTries++
    try {
      await refreshActiveAudios()
      const list = audiosBySegment.value[activeSegmentIdx.value || 0] || []
      const settled = list.length > 0 && list.every(a => a.status === 'READY' || a.status === 'FAILED')
      if (settled) {
        clearInterval(pollTimer)
        const failed = list.filter(a => a.status === 'FAILED').length
        if (failed > 0) ElMessage.error(t('audio.manage.pollFailedCount', { count: failed }))
        else ElMessage.success(t('audio.manage.pollDone'))
      } else if (pollTries > 100) {
        clearInterval(pollTimer)
        ElMessage.warning(t('audio.manage.pollSlow'))
      }
    } catch {
      if (pollTries > 100) clearInterval(pollTimer)
    }
  }, 3000)
}

onUnmounted(() => clearInterval(pollTimer))
</script>

<style scoped>
.audio-manager { background: var(--el-fill-color-blank); border-radius: 8px; padding: 16px; }
.am-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.am-title { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.am-single, .am-multi { margin-top: 12px; }
.am-support-alert { margin-bottom: 12px; }
/* L0 Task 4: Tab 焦点环可见 (键盘用户 / 读屏用户) */
:deep(.el-button:focus-visible),
:deep(.el-select:focus-visible),
:deep(.el-radio-button:focus-visible),
:deep(.el-checkbox:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
