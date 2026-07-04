<!--
  章节快速练习入口（Round 11-3 从 LearningView.vue 拆分）
  ExerciseQuickPanel: 考试 Tab 的练习入口卡片 / 空状态
  Author: jackie
-->
<template>
  <div v-if="exerciseCount > 0" class="exam-entry-card">
    <div class="exam-header">
      <el-icon size="28" color="var(--indigo-600)"><Edit /></el-icon>
      <div class="exam-info">
        <h3>章节练习</h3>
        <p>共 {{ exerciseCount }} 个练习</p>
      </div>
    </div>
    <el-button type="primary" size="large" round @click="$emit('start-exercise')">
      <el-icon><CaretRight /></el-icon>
      开始练习
    </el-button>
  </div>
  <div v-else class="empty-state-card">
    <el-icon size="48" color="#CBD5E1"><Edit /></el-icon>
    <p class="empty-title">本章节暂无练习</p>
    <p class="empty-desc">完成视频学习后可获取练习资格</p>
  </div>
</template>

<script setup>
import { Edit, CaretRight } from '@element-plus/icons-vue'

defineProps({
  exerciseCount: { type: Number, default: 0 }
})

defineEmits(['start-exercise'])
</script>

<style scoped>
.exam-entry-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  padding: var(--space-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}
.exam-header { display: flex; align-items: center; gap: var(--space-3-5); }
.exam-info h3 { font-size: var(--text-md); font-weight: var(--weight-semibold); margin: 0 0 var(--space-1); }
.exam-info p { font-size: var(--text-sm); color: var(--el-text-color-secondary); margin: 0; }

.empty-state-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  padding: 64px var(--space-8);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  text-align: center;
}
.empty-title { font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); margin: 0; }
.empty-desc { font-size: var(--text-base); color: var(--el-text-color-secondary); margin: 0; }

@media (max-width: 768px) {
  .exam-entry-card { flex-direction: column; align-items: flex-start; }
}
</style>
