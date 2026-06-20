<template>
  <div class="slide-editor-panel">
    <div v-if="page?.extractedText" class="extracted-section">
      <span class="section-label">提取文本</span>
      <p class="extracted-text">{{ page.extractedText }}</p>
    </div>

    <div class="narration-section">
      <span class="section-label">讲述稿</span>
      <div class="action-bar">
        <el-button size="small" :loading="aiLoading" @click="$emit('generateAI')">
          <el-icon><MagicStick /></el-icon> AI 生成
        </el-button>
        <el-button size="small" type="success" :loading="ttsLoading" @click="$emit('generateTTS')" v-if="script">
          <el-icon><Headset /></el-icon> 生成音频
        </el-button>
      </div>
      <el-input v-model="script" type="textarea" :rows="6" @blur="$emit('saveScript', script)" resize="none" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ page: Object, aiLoading: Boolean, ttsLoading: Boolean })
defineEmits(['generateAI', 'generateTTS', 'saveScript'])
const script = computed({
  get: () => props.page?.narrationScript || '',
  set: () => {}
})
</script>

<style scoped>
.slide-editor-panel { padding: 12px; overflow-y: auto; }
.section-label { font-size: 11px; font-weight: 600; color: #909399; text-transform: uppercase; letter-spacing: 1px; display: block; margin-bottom: 6px; }
.extracted-text { font-size: 13px; color: #606266; line-height: 1.6; background: #f9fafb; padding: 8px; border-radius: 6px; max-height: 80px; overflow-y: auto; }
.narration-section { margin-top: 12px; }
.action-bar { display: flex; gap: 6px; margin-bottom: 8px; }
</style>
