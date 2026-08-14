<!--
  ScriptEditor.vue · 讲述稿编辑面板 (含版本历史)

  支持:
  1. 当前 active 脚本编辑 (textarea)
  2. 历史版本列表 (点击切换查看)
  3. 一键 AI 生成 (P3-1 后端真实 LLM, 生成后应用预览再保存)
  4. 保存即生成新版本, 旧版本永久保留 (V301 partial unique 兼容)
  5. P0-A: 新上传 PPT 页 (无 active script) 也可直接输入/保存 —— 首次保存自动创建 v1

  Props:
    courseId, pageType, pageId (PPT) 或 unitId+idx (HTML)
    scriptId - 当前 active script id (父组件传入)
    pageType - "PPT" | "HTML"

  Emits:
    save-success (newScriptId) - 新版本保存成功
-->
<template>
  <div class="script-editor">
    <div class="se-header">
      <h3 class="se-title">
        <el-icon><Document /></el-icon>
        {{ t('script.editor.title') }}
        <el-tag v-if="history.length > 1" size="small" type="info">{{ t('script.editor.historyCount', { count: history.length }) }}</el-tag>
      </h3>
      <div class="se-actions">
        <el-dropdown v-if="history.length > 1" trigger="click" @command="loadVersion">
          <el-button size="small" :icon="Clock">
            {{ t('script.editor.switchVersion') }}
            <span class="se-current">v{{ activeVersion }}</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="h in history"
                :key="h.id"
                :command="h"
                :class="{ 'is-active': h.id === currentScriptId }"
              >
                v{{ h.scriptVersion }} · {{ formatTime(h.createdAt) }}
                <el-tag v-if="h.isActive" size="small" type="success">active</el-tag>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <el-input
      v-model="scriptText"
      type="textarea"
      :rows="8"
      :placeholder="t('script.editor.placeholder')"
    />

    <div class="se-toolbar">
      <el-button :icon="MagicStick" plain size="small" @click="handleAiGenerate" :loading="aiLoading">
        {{ t('script.editor.aiGenerate') }}
      </el-button>
      <el-button type="primary" size="small" :icon="Check" @click="handleSave" :loading="saving" :disabled="!scriptText.trim()">
        {{ t('script.editor.saveWithVersion', { version: history.length + 1 }) }}
      </el-button>
    </div>

    <div v-if="aiPreview" class="se-ai-preview">
      <el-alert type="info" :closable="false" :title="t('script.editor.aiPreviewTitle')">
        <pre>{{ aiPreview }}</pre>
        <el-button size="small" type="primary" @click="applyAiPreview">{{ t('script.editor.applyVersion') }}</el-button>
        <el-button size="small" @click="aiPreview = null">{{ t('script.editor.cancel') }}</el-button>
      </el-alert>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, Check, MagicStick, Clock } from '@element-plus/icons-vue'
import { getActivePptScript, listPptScriptHistory, savePptScript } from '../api/pptCourseware'
import { getActiveHtmlSegment, saveHtmlSegmentScript } from '../api/htmlCourseware'
import { generatePptScriptAi, generateHtmlSegmentScriptAi } from '../api/queryCourseware'
import { useUserStore } from '@/store/user'

const { t, locale } = useI18n()

const props = defineProps({
  courseId: { type: Number, required: true },
  pageType: { type: String, default: 'PPT' },
  pageId: { type: Number, default: null },           // PPT
  unitId: { type: Number, default: null },            // HTML
  segmentIndex: { type: Number, default: null },      // HTML
  currentScriptId: { type: Number, default: null }
})

const emit = defineEmits(['save-success'])

const scriptText = ref('')
const history = ref([])
const currentScript = ref(null)
const saving = ref(false)
const aiLoading = ref(false)
const aiPreview = ref(null)

const activeVersion = ref(0)

async function loadActive() {
  // P0-A 修复: 新上传 PPT 页（无 currentScriptId）也允许进入加载流程 ——
  // getActivePptScript 无 active 脚本时后端返回 null → 空稿可输入,
  // 首次保存由 saveScript 契约自动创建 v1（PPT 与 HTML 行为一致）。
  try {
    if (props.pageType === 'PPT') {
      const [active, hist] = await Promise.all([
        getActivePptScript(props.courseId, props.pageId),
        listPptScriptHistory(props.courseId, props.pageId)
      ])
      currentScript.value = active.data || active
      history.value = hist.data || hist
      scriptText.value = currentScript.value?.scriptText || ''
      activeVersion.value = currentScript.value?.scriptVersion || 0
    } else {
      // HTML
      const active = await getActiveHtmlSegment(props.courseId, props.unitId, props.segmentIndex)
      currentScript.value = active.data || active
      history.value = currentScript.value ? [currentScript.value] : []
      scriptText.value = currentScript.value?.scriptText || ''
      activeVersion.value = currentScript.value?.scriptVersion || 0
    }
  } catch (e) {
    // P1-C 修复: 原 loadActive() 无 try/catch → API 失败时静默失败,
    // 编辑面板保持空白且无任何反馈
    console.error('Failed to load active script:', e)
    ElMessage.error(t('script.editor.loadFailed', { msg: e.message || t('script.editor.unknownError') }))
  }
}

function loadVersion(h) {
  if (!h) return
  currentScript.value = h
  scriptText.value = h.scriptText || ''
  activeVersion.value = h.scriptVersion
}

async function handleSave() {
  if (!scriptText.value.trim()) {
    ElMessage.warning(t('script.editor.scriptRequired'))
    return
  }
  // 【BUG #2 修复】从用户 store 获取真实 createdBy (审计追踪)
  const userStore = useUserStore()
  const createdBy = userStore.userInfo?.id || userStore.userId || 0
  if (!createdBy) {
    ElMessage.warning(t('script.editor.notLoggedIn'))
    return
  }
  saving.value = true
  try {
    let res
    // R-6: 不再兜底非法枚举 (female-young / MiniMax-speech-01)。
    // voice/ttsModel 为空时透传 null —— TtsWorkerService 服务端解析为
    // 官方默认 (female-shaonv / speech-2.8-hd)，音色选择统一由 AudioManager 的 tts-options 契约负责。
    if (props.pageType === 'PPT') {
      res = await savePptScript(props.courseId, props.pageId, {
        scriptText: scriptText.value,
        voice: currentScript.value?.voice || null,
        ttsModel: currentScript.value?.ttsModel || null,
        createdBy
      })
    } else {
      res = await saveHtmlSegmentScript(props.courseId, props.unitId, props.segmentIndex, {
        scriptText: scriptText.value,
        voice: currentScript.value?.voice || null,
        ttsModel: currentScript.value?.ttsModel || null,
        // P0-E (F-2026-08-08): segmentMarker 为空时按段序号自动生成 seg-{idx}
        // （检测 marker 即 seg-{1基index}，保证 marker 数据链不断，播放器 marker 匹配路径生效）
        segmentMarker: currentScript.value?.segmentMarker || (props.segmentIndex ? `seg-${props.segmentIndex}` : null),
        createdBy
      })
    }
    ElMessage.success(t('script.editor.saved'))
    emit('save-success', res.data || res)
    await loadActive()
  } catch (e) {
    ElMessage.error(t('script.editor.saveFailed', { msg: e.message || t('script.editor.unknownError') }))
  } finally {
    saving.value = false
  }
}

async function handleAiGenerate() {
  aiLoading.value = true
  try {
    // P3-1：接后端真实 LLM 接口（复用 DeepSeek/MiniMax 兼容 Chat Completions）
    let res
    if (props.pageType === 'PPT') {
      res = await generatePptScriptAi(props.courseId, props.pageId)
    } else {
      res = await generateHtmlSegmentScriptAi(props.courseId, props.unitId, props.segmentIndex)
    }
    const script = (res.data || res)?.scriptText
    if (!script) {
      ElMessage.error(t('script.editor.aiEmpty'))
      return
    }
    aiPreview.value = script
  } catch (e) {
    // F-2026-08-07-09：透传后端明确错误（如 LLM Key 未配置），禁止吞成"服务器错误"
    ElMessage.error(e?.response?.data?.message || e?.message || t('script.editor.aiFailed'))
  } finally {
    aiLoading.value = false
  }
}

function applyAiPreview() {
  if (aiPreview.value) {
    scriptText.value = aiPreview.value
    aiPreview.value = null
    ElMessage.success(t('script.editor.aiApplied'))
  }
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString(locale.value, { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

watch(() => [props.currentScriptId, props.pageId, props.unitId, props.segmentIndex], loadActive, { immediate: true })
</script>

<style scoped>
.script-editor { background: var(--el-fill-color-blank); border-radius: 8px; padding: 16px; }
.se-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.se-title { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.se-actions { display: flex; gap: 8px; }
.se-current { margin-left: 6px; color: var(--el-color-primary); font-weight: 600; }
.se-toolbar { display: flex; gap: 8px; margin-top: 12px; }
.se-ai-preview { margin-top: 12px; }
.se-ai-preview pre { white-space: pre-wrap; font-size: 13px; margin: 8px 0; }
</style>
