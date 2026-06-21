<!--
  学习页顶部工具栏（Round 11-3 从 LearningView.vue 拆分）
  ResourceToolbar: 返回 / 课程标题 / 总进度 / 笔记 / 收藏
  Author: jackie
-->
<template>
  <header class="learning-header">
    <div class="header-left">
      <el-button text class="back-btn" @click="$emit('back')">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </el-button>
    </div>
    <div class="header-center">
      <h1 class="course-title">{{ courseTitle || '加载中...' }}</h1>
    </div>
    <div class="header-right">
      <!-- 总体进度 (UX-NEW-5:加载完成前隐藏,防 0% 闪烁) -->
      <div v-if="!loading" class="progress-indicator" role="button" tabindex="0" aria-label="查看课程进度" @click="$emit('show-notes')" @keydown.enter="$emit('show-notes')" title="查看课程进度">
        <span class="progress-dots">
          <span
            v-for="i in 10"
            :key="i"
            class="dot"
            :class="{ filled: i <= Math.ceil(totalProgress / 10) }"
          />
        </span>
        <span class="progress-text">{{ totalProgress }}%</span>
      </div>
      <!-- 笔记按钮 -->
      <el-button text class="header-btn" @click="$emit('show-notes')" title="笔记">
        <el-icon><Edit /></el-icon>
        <span>笔记</span>
      </el-button>
      <!-- 收藏按钮 -->
      <el-button text class="header-btn" :class="{ 'is-favorited': isFavorited }" @click="$emit('toggle-favorite')" :title="isFavorited ? '取消收藏' : '收藏课程'">
        <el-icon><Star /></el-icon>
        <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
      </el-button>
    </div>
  </header>
</template>

<script setup>
import { ArrowLeft, Edit, Star } from '@element-plus/icons-vue'

defineProps({
  courseTitle: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  totalProgress: { type: Number, default: 0 },
  isFavorited: { type: Boolean, default: false }
})

defineEmits(['back', 'toggle-favorite', 'show-notes'])
</script>

<style scoped>
.learning-header {
  --color-primary: #6366F1;
  --color-primary-dark: #4F46E5;

  display: flex;
  align-items: center;
  padding: var(--space-4) var(--space-6);
  background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
  color: white;
  gap: var(--space-4);
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 4px 20px rgba(79,70,229,0.15);
}

.header-left,
.header-right { flex: 1; display: flex; align-items: center; }
.header-right { gap: var(--space-2); justify-content: flex-end; }
.header-center { flex: 2; text-align: center; }

.back-btn,
.header-btn {
  color: rgba(255,255,255,0.9) !important;
  font-size: var(--text-base);
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1-5) var(--space-2-5);
  border-radius: var(--radius-sm);
  transition: background var(--duration-base) var(--ease-out);
}
.back-btn:hover,
.header-btn:hover { background: rgba(255,255,255,0.15) !important; }
.back-btn .el-icon,
.header-btn .el-icon { font-size: 15px; }
.header-btn.is-favorited { color: #FCD34D !important; }

.course-title {
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  margin: 0;
  color: white;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 进度指示器 */
.progress-indicator {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  padding: var(--space-1) var(--space-2-5);
  border-radius: 20px;
  background: rgba(255,255,255,0.15);
  transition: background var(--duration-base) var(--ease-out);
}
.progress-indicator:hover { background: rgba(255,255,255,0.25); }
.progress-dots { display: flex; gap: 3px; }
.dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: rgba(255,255,255,0.35);
  transition: background var(--duration-base) var(--ease-out);
}
.dot.filled { background: white; }
.progress-text { font-size: var(--text-sm); font-weight: var(--weight-semibold); }

@media (max-width: 768px) {
  .learning-header {
    padding: var(--space-2-5) var(--space-4);
    gap: var(--space-2);
  }
  .course-title { font-size: var(--text-md); }
  .progress-text { display: none; }
  .header-btn span { display: none; }
  .header-btn { padding: var(--space-1-5); }
}
</style>
