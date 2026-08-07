<!--
  AudioManager.vue · 音频管理面板 (客户体验核心)

  解决 7-19 P0 报告的根因: 音频元数据不可见 / 不可控.
  提供:
  1. 列出该课件/段落的所有音频版本 (按时间倒序)
  2. 试听对比 (A/B 切换)
  3. 一键生成新音色
  4. 显示状态 (GENERATING / READY / FAILED) + 时长

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
        音频管理
        <el-tag v-if="totalReady > 0" type="success" size="small">{{ totalReady }} 已就绪</el-tag>
        <el-tag v-else-if="hasGenerating" type="warning" size="small">生成中...</el-tag>
        <el-tag v-else type="info" size="small">暂无音频</el-tag>
      </h3>
      <el-tooltip :disabled="canGenerate" content="请先保存页面讲述稿，再生成音频" placement="top">
        <span>
          <el-button
            :icon="Plus"
            size="small"
            type="primary"
            plain
            :disabled="!canGenerate"
            @click="showGenerate = true"
          >
            生成新音频
          </el-button>
        </span>
      </el-tooltip>
    </div>

    <!-- PPT 单段模式 / HTML 多段模式 -->
    <div v-if="pageType === 'PPT' || (segments && segments.length === 1)" class="am-single">
      <AudioPanel
        v-if="effectiveScriptId"
        :course-id="courseId"
        :script-id="effectiveScriptId"
        :token-loader="loadPptAudios"
        :audio-url-factory="pptAudioUrl"
        :audio-status="statusLabel"
      />
      <el-empty
        v-else
        description="请先保存页面讲述稿，再生成音频"
        :image-size="60"
      />
    </div>

    <div v-else class="am-multi">
      <el-tabs v-model="activeSegmentIdx" type="card">
        <el-tab-pane
          v-for="seg in segments || []"
          :key="seg.idx"
          :name="seg.idx"
          :label="`第 ${seg.idx} 段`"
        >
          <AudioPanel
            :course-id="courseId"
            :script-id="seg.segmentScriptId"
            :token-loader="loadHtmlAudios"
            :audio-url-factory="htmlAudioUrl"
            :audio-status="statusLabel"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 生成新音频对话框 -->
    <el-dialog v-model="showGenerate" title="生成新音频" width="420px">
      <el-form label-position="top">
        <el-form-item label="音色">
          <el-select v-model="generateVoice" placeholder="选择音色" style="width:100%">
            <el-option
              v-for="v in voiceOptions"
              :key="v.id"
              :label="v.label"
              :value="v.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="TTS 模型">
          <el-select v-model="generateModel" style="width:100%">
            <el-option
              v-for="m in modelOptions"
              :key="m"
              :label="m === defaultTtsModel ? m + ' (推荐)' : m"
              :value="m"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerate = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Headset, Plus } from '@element-plus/icons-vue'
import { listPptAudios, generatePptAudio } from '../api/pptCourseware'
import { listHtmlSegmentAudios, generateHtmlSegmentAudio } from '../api/htmlCourseware'
import { getAudioStreamUrl, getTtsOptions } from '../api/queryCourseware'
import AudioPanel from './AudioPanel.vue'

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

const voiceOptions = computed(() => {
  if (ttsOptions.value?.voices?.length) return ttsOptions.value.voices
  return [
    { id: 'female-shaonv', label: '女声·甜美少女' },
    { id: 'female-qingxin', label: '女声·清新' },
    { id: 'female-yujie', label: '女声·御姐' },
    { id: 'female-warm', label: '女声·温暖' },
    { id: 'male-shaonian', label: '男声·少年' },
    { id: 'male-qingnian', label: '男声·青年' },
    { id: 'male-dashu', label: '男声·大叔' },
    { id: 'male-chengzhao', label: '男声·沉稳' }
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
    GENERATING: '生成中',
    READY: '就绪',
    FAILED: '失败'
  }
  return map[audio.status] || audio.status
}

// 统计 (顶部 tag 显示)
const audiosBySegment = ref({})  // { segmentIdx: AudioDTO[] }

const totalReady = computed(() => {
  const all = Object.values(audiosBySegment.value).flat()
  return all.filter(a => a.status === 'READY').length
})

const hasGenerating = computed(() => {
  const all = Object.values(audiosBySegment.value).flat()
  return all.some(a => a.status === 'GENERATING')
})

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
    ElMessage.warning('请先保存讲述稿，再生成音频')
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
    ElMessage.success('音频生成任务已提交,稍后刷新查看')
    showGenerate.value = false
    await refreshActiveAudios()
    pollUntilSettled() // R-10：3s 轮询直至 READY/FAILED
  } catch (e) {
    // F-2026-08-07-09：透传后端明确错误（如 TTS Key 未配置/超时），禁止吞成通用错误
    ElMessage.error('生成失败: ' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    generating.value = false
  }
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
        if (failed > 0) ElMessage.error(`${failed} 个音频生成失败，请查看列表`)
        else ElMessage.success('音频生成完成')
      } else if (pollTries > 100) {
        clearInterval(pollTimer)
        ElMessage.warning('生成时间较长，请稍后在列表中查看状态')
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
</style>
