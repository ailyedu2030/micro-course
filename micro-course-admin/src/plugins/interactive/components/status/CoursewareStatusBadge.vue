<!--
  CoursewareStatusBadge.vue · 课件状态聚合 badge (spec 5.2 / status/)

  Props:
    status: PENDING | AUDIO_GENERATING | AUDIO_READY | ACTIVE | EMPTY
    audioReadyCount: number
    totalCount: number (optional)

  Usage:
    <CoursewareStatusBadge status="AUDIO_READY" :audio-ready-count="3" :total-count="5" />
-->
<template>
  <el-tag :type="tagType" :effect="effect" :size="size" class="cs-badge">
    <span class="cs-label">{{ label }}</span>
    <span v-if="showCount" class="cs-count">{{ audioReadyCount }} / {{ totalCount }} 音频就绪</span>
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: 'PENDING' },
  audioReadyCount: { type: Number, default: 0 },
  totalCount: { type: Number, default: 0 },
  size: { type: String, default: 'small' },
  effect: { type: String, default: 'plain' }
})

const tagType = computed(() => {
  switch (props.status) {
    case 'AUDIO_READY': return 'success'
    case 'AUDIO_GENERATING': return 'warning'
    case 'PENDING': return 'info'
    case 'EMPTY': return 'info'
    default: return 'default'
  }
})

const label = computed(() => {
  switch (props.status) {
    case 'AUDIO_READY': return '音频就绪'
    case 'AUDIO_GENERATING': return '生成中'
    case 'PENDING': return '待生成'
    case 'EMPTY': return '空'
    default: return props.status
  }
})

const showCount = computed(() => props.totalCount > 0)
</script>

<style scoped>
.cs-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.cs-count {
  font-size: 12px;
  opacity: 0.85;
}
</style>