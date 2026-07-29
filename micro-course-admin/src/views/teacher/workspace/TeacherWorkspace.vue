<!-- TeacherWorkspace.vue 已废弃，功能由 SlideManage.vue 替代 -->
<template><div /></template>

<script setup>
/**
 * @deprecated 此文件已废弃，功能由 SlideManage.vue 替代。
 * 以下两个函数（saveOutline、replacePPTX）仅为兼容旧引用保留，新代码请直接使用 SlideManage.vue。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { updateSlidePage } from '@/plugins/interactive/api/slide'

const route = useRoute()
const courseId = computed(() => route.params.id || route.params.courseId)

/**
 * 保存页面标题 — 使用 updateSlidePage API 持久化 pageTitle
 */
async function saveOutline(page) {
  if (!page?.pageNumber) {
    ElMessage.warning('无效的页面数据')
    return
  }
  try {
    await updateSlidePage(courseId.value, page.pageNumber, {
      pageTitle: page.pageTitle || `课时 ${page.pageNumber}`
    })
    ElMessage.success('页面标题已保存')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存页面标题失败，请稍后重试')
  }
}

/**
 * 替换 PPT 文件 — 清空当前课件状态，触发重新上传
 * 旧版 SlideManage.vue 使用 <el-upload :before-upload="handleUpload"> 的组件内实现，
 * 此函数仅为兼容旧模板引用保留。
 */
function replacePPTX() {
  // 清空状态以显示上传区域
  // 实际文件上传由模板中的 <el-upload> 或拖拽区 handleUpload 完成
  // 触发原生文件选择器
  const fileInput = document.querySelector('.native-upload-zone input[type="file"], .replace-upload input[type="file"]')
  if (fileInput) {
    fileInput.value = ''
    fileInput.click()
  }
}
</script>
