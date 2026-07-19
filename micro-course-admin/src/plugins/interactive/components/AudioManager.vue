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
      <el-button :icon="Plus" size="small" type="primary" plain @click="showGenerate = true">
        生成新音频
      </el-button>
    </div>

    <!-- PPT 单段模式 / HTML 多段模式 -->
    <div v-if="pageType === 'PPT' || (segments && segments.length === 1)" class="am-single">
      <AudioPanel
        :course-id="courseId"
        :script-id="effectiveScriptId"
        :token-loader="loadPptAudios"
        :audio-url-factory="pptAudioUrl"
        :audio-status="statusLabel"
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
            <el-option label="男声 (青年)" value="male-young" />
            <el-option label="男声 (中年)" value="male-mid" />
            <el-option label="女声 (青年)" value="female-young" />
            <el-option label="女声 (中年)" value="female-mid" />
          </el-select>
        </el-form-item>
        <el-form-item label="TTS 模型">
          <el-select v-model="generateModel" style="width:100%">
            <el-option label="MiniMax speech-01 (推荐)" value="MiniMax-speech-01" />
            <el-option label="MiniMax speech-02 (高清)" value="MiniMax-speech-02" />
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
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Headset, Plus } from '@element-plus/icons-vue'
import { listPptAudios, generatePptAudio } from '../api/pptCourseware'
import { listHtmlSegmentAudios, generateHtmlSegmentAudio } from '../api/htmlCourseware'
import { getAudioStreamUrl } from '../api/queryCourseware'
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
const generateVoice = ref('female-young')
const generateModel = ref('MiniMax-speech-01')
const activeSegmentIdx = ref(props.segments?.[0]?.idx ?? null)

const effectiveScriptId = computed(() => {
  if (props.pageType === 'PPT') return props.scriptId
  if (props.segments && props.segments.length === 1) return props.segments[0].segmentScriptId
  return null
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
if (props.pageType === 'PPT' && effectiveScriptId.value) {
  listPptAudios(props.courseId, effectiveScriptId.value).then(r => {
    audiosBySegment.value[0] = r.data || r
  })
}

// 生成新音频
async function handleGenerate() {
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
    // 刷新当前列表
    audiosBySegment.value[activeSegmentIdx.value || 0] = null
    if (props.pageType === 'PPT') {
      const r = await listPptAudios(props.courseId, effectiveScriptId.value)
      audiosBySegment.value[0] = r.data || r
    } else {
      const seg = props.segments.find(s => s.idx === activeSegmentIdx.value)
      const r = await listHtmlSegmentAudios(props.courseId, seg.segmentScriptId)
      audiosBySegment.value[activeSegmentIdx.value] = r.data || r
    }
  } catch (e) {
    ElMessage.error('生成失败: ' + (e.message || '未知错误'))
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.audio-manager { background: var(--el-fill-color-blank); border-radius: 8px; padding: 16px; }
.am-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.am-title { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.am-single, .am-multi { margin-top: 12px; }
</style>