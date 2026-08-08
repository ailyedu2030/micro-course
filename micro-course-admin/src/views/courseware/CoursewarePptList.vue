<!--
  CoursewarePptList.vue · PPT 课件管理（V333 简化方案）
  复用 CourseList.vue，固定筛选 PPT_COURSEWARE，实现 PPT 课件独立管理。
-->
<template>
  <CourseList :fixed-course-type="'PPT_COURSEWARE'" />
</template>

<script setup>
import CourseList from '../courses/CourseList.vue'
</script>
