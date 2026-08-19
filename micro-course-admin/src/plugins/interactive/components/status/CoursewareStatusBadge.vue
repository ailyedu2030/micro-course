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
    <span v-if="showCount" class="cs-count">{{ t('courseware.status.audioReadyCount', { ready: audioReadyCount, total: totalCount }) }}</span>
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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
    case 'AUDIO_FAILED': return 'danger'
    case 'PENDING': return 'info'
    case 'AUDIO_PENDING': return 'info'
    case 'EMPTY': return 'info'
    default: return 'default'
  }
})

const label = computed(() => {
  switch (props.status) {
    case 'AUDIO_READY': return t('courseware.status.ready')
    case 'AUDIO_GENERATING': return t('courseware.status.generating')
    case 'AUDIO_FAILED': return t('courseware.status.failed')
    case 'PENDING': return t('courseware.status.pending')
    case 'AUDIO_PENDING': return t('courseware.status.pending')
    case 'EMPTY': return t('courseware.status.empty')
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
