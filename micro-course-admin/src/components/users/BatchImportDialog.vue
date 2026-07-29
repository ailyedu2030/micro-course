<!--
  BatchImportDialog — 批量导入用户弹窗
  从 users/UserList.vue 提取
-->
<template>
  <el-dialog
    :model-value="visible"
    title="批量导入用户"
    width="500px"
    :close-on-press-escape="true"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div class="batch-import-tip">
      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          请按模板格式填写后上传。支持 .xlsx 格式文件。
        </template>
      </el-alert>
    </div>
    <div class="download-template">
      <el-button size="small" @click="handleDownloadTemplate">
        <el-icon><Download /></el-icon> 下载样表
      </el-button>
    </div>
    <el-upload
      ref="uploadRef"
      class="batch-upload"
      drag
      accept=".xlsx,.xls"
      :auto-upload="false"
      :limit="1"
      :on-change="handleFileChange"
      :on-remove="handleFileRemove"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="upload-tip">只能上传 xlsx/xls 文件，且不超过 10MB</div>
      </template>
    </el-upload>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="importLoading" :disabled="!uploadFile" @click="handleBatchImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Download } from '@element-plus/icons-vue'
import { batchImportUsers } from '@/api/user'
import * as XLSX from 'xlsx'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'import-success'])

const uploadRef = ref(null)
const uploadFile = ref(null)
const importLoading = ref(false)

function handleFileChange(file) {
  uploadFile.value = file.raw
}

function handleFileRemove() {
  uploadFile.value = null
}

function handleDownloadTemplate() {
  const wsData = [
    ['username', 'realName', 'password', 'email', 'role', 'departmentId', 'majorId', 'classId', 'studentNo', 'teacherNo'],
    ['zhangsan', '张三', 'Abc123456', 'zhangsan@school.edu.cn', 'STUDENT', '1', '1', '1', 'S2024001', ''],
    ['lisi', '李四', 'Abc123456', 'lisi@school.edu.cn', 'TEACHER', '1', '', '', '', 'T001'],
    ['wangwu', '王五', 'Abc123456', 'wangwu@school.edu.cn', 'ADMIN', '1', '', '', '', '']
  ]
  const wb = XLSX.utils.book_new()
  const ws = XLSX.utils.aoa_to_sheet(wsData)
  ws['!cols'] = [{ wch: 15 }, { wch: 12 }, { wch: 15 }, { wch: 28 }, { wch: 10 }, { wch: 12 }, { wch: 10 }, { wch: 8 }, { wch: 12 }, { wch: 10 }]
  XLSX.utils.book_append_sheet(wb, ws, '用户导入')
  XLSX.writeFile(wb, '用户导入样表.xlsx')
}

async function handleBatchImport() {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    await batchImportUsers(formData)
    ElMessage.success('导入成功')
    emit('update:visible', false)
    uploadFile.value = null
    uploadRef.value?.clearFiles()
    emit('import-success')
  } catch {
    ElMessage.error('导入失败，请检查文件格式')
  } finally {
    importLoading.value = false
  }
}
</script>

<style scoped>
.batch-import-tip { margin-bottom: var(--space-4); }
.download-template { margin: var(--space-3) 0; }
.batch-upload { width: 100%; }
.upload-icon { font-size: 32px; color: var(--el-text-color-secondary); margin-bottom: var(--space-2); }
.upload-text { color: var(--el-text-color-regular); }
.upload-text em { color: var(--role-primary); font-style: normal; }
.upload-tip { color: var(--el-text-color-secondary); font-size: var(--text-xs); }
</style>
