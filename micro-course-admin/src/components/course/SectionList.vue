<template>
  <div class="section-list">
    <div v-for="section in sections" :key="section.id" class="section-item">
      <el-row :gutter="8" align="middle">
        <el-col :span="1">
          <el-tag :type="typeTag(section.sectionType)" size="small" effect="plain">
            {{ typeIcon(section.sectionType) }}
          </el-tag>
        </el-col>
        <el-col :span="7">
          <span class="section-title">{{ (section.sortOrder % 10000) || section.sortOrder }}. {{ section.title }}</span>
        </el-col>
        <el-col :span="2">
          <span class="muted">{{ section.duration ? `${section.duration}分钟` : '-' }}</span>
        </el-col>
        <el-col :span="3">
          <el-tag size="small" :type="section.slideCount > 0 ? 'success' : 'info'" effect="plain">
            {{ section.slideCount > 0 ? `课件${section.slideCount}` : '无课件' }}
          </el-tag>
        </el-col>
        <el-col :span="11" style="text-align:right">
          <el-button size="small" link type="primary" @click="$emit('upload', section)">课件</el-button>
          <el-button size="small" link type="primary" @click="$emit('edit', section)">编辑</el-button>
          <el-button size="small" link type="danger" @click="$emit('delete', section)">删除</el-button>
        </el-col>
      </el-row>
    </div>
    <el-empty v-if="sections.length === 0" description="暂无课时，点击上方「新增课时」添加" :image-size="80" />
  </div>
</template>

<script setup>
defineProps({ sections: { type: Array, default: () => [] } })
defineEmits(['upload', 'edit', 'delete'])
const typeIcon = (t) => ({ VIDEO: '📹', INTERACTIVE: '🎯', OFFLINE: '🏫', EXERCISE: '📝' }[t] || '📄')
const typeTag = (t) => ({ VIDEO: 'primary', INTERACTIVE: 'success', OFFLINE: 'info', EXERCISE: 'warning' }[t] || '')
</script>

<style scoped>
.section-item { padding: 6px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.section-item:last-child { border-bottom: none; }
.section-title { font-weight: 500; font-size: 14px; }
.muted { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
