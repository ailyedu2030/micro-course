<template>
  <div class="interactive-props">
    <div class="prop-row">
      <span class="prop-label">PPT文件</span>
      <span class="prop-value">{{ slide?.fileName || '未上传' }}</span>
    </div>
    <div class="prop-row">
      <span class="prop-label">总页数</span>
      <span class="prop-value">{{ slide?.totalPages || 0 }}</span>
    </div>
    <div class="prop-row">
      <span class="prop-label">已配音</span>
      <span class="prop-value">{{ audioReadyCount }}/{{ pages.length }}</span>
    </div>
    <el-progress v-if="pages.length > 0" :percentage="progressPercent" :stroke-width="6" color="#67c23a" />
  </div>
</template>

<script setup>
import { computed, watch, ref } from 'vue'

const props = defineProps({ courseId: { type: [Number, String], required: true } })
const pages = ref([])
const slide = ref(null)

const audioReadyCount = computed(() => pages.value.filter(p => p.narrationStatus === 'AUDIO_READY').length)
const progressPercent = computed(() => pages.value.length > 0 ? Math.round(audioReadyCount.value / pages.value.length * 100) : 0)

watch(() => props.courseId, async (id) => {
  if (!id) return
  try {
    const Slides = await import('@/plugins/interactive/api/slide')
    const s = await Slides.getSlides(id)
    slide.value = s.data
    if (s.data?.status === 2) {
      const p = await Slides.getSlidePages(id)
      pages.value = p.data || []
    }
  } catch {}
}, { immediate: true })
</script>

<style scoped>
.interactive-props { padding: 12px; }
.prop-row { display: flex; justify-content: space-between; font-size: 13px; padding: 6px 0; border-bottom: 1px solid #f5f5f5; }
.prop-label { color: #909399; }
.prop-value { color: #303133; }
</style>
