<template>
  <div class="slide-preview">
    <el-button class="close-btn" @click="$emit('close')" circle aria-label="关闭预览">
      <el-icon><Close /></el-icon>
    </el-button>
    <!-- P1-C-7/P1-C-8：in-preview 标记教师预览态（播放器显示预览 banner + 退出语义 emit close） -->
    <SlidePlayerCore :course-id="courseId" :section-id="sectionId" in-preview @close="$emit('close')" />
  </div>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'
import SlidePlayerCore from '@/views/student/SlidePlayer.vue'

defineProps({ 
  courseId: { type: [String, Number], required: true },
  sectionId: { type: [String, Number], default: null }
})
defineEmits(['close'])
</script>

<style scoped>
.slide-preview { position: fixed; inset: 0; z-index: 2000; background: #000; }
.close-btn { position: absolute; top: 12px; right: 12px; z-index: 10; }
</style>
