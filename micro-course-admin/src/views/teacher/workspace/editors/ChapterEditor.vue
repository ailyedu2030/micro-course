<template>
  <div class="chapter-editor">
    <div class="editor-form">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="章节名称" prop="title">
          <el-input v-model="form.title" :disabled="loading" @blur="handleSave" />
        </el-form-item>
        <el-form-item label="章节描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" :disabled="loading" @blur="handleSave" />
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getChapters, updateChapter } from '@/api/chapter'
import { ElMessage } from 'element-plus'

const props = defineProps({ chapterId: { type: [Number, String], required: true } })
const emit = defineEmits(['update'])
const formRef = ref(null)
const form = ref({ title: '', description: '' })
const loading = ref(true)
const initialForm = ref({ title: '', description: '' })

const rules = {
  title: [{ required: true, message: '章节名称不能为空', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const res = await getChapters({ size: 200 })
    const ch = (res.data?.items || []).find(c => c.id === props.chapterId)
    if (ch) {
      form.value = { title: ch.title || '', description: ch.description || '' }
      initialForm.value = { ...form.value }
    }
  } catch {
    ElMessage.error('加载章节信息失败')
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  if (loading.value) return
  if (form.value.title === initialForm.value.title && form.value.description === initialForm.value.description) return
  try {
    await formRef.value?.validate()
  } catch { return }
  try {
    await updateChapter(props.chapterId, { title: form.value.title, description: form.value.description })
    initialForm.value = { ...form.value }
    emit('update')
    ElMessage.success('章节已保存')
  } catch (e) { ElMessage.error(e?.response?.data?.message || '保存失败') }
}
</script>

<style scoped>
.chapter-editor {
  padding: var(--space-5);
}

.editor-form {
  max-width: 600px;
}
</style>
