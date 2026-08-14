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
import { getChapterById, updateChapter } from '@/api/chapter'
import { ElMessage } from 'element-plus'

const props = defineProps({ chapterId: { type: [Number, String], required: true } })
const emit = defineEmits(['update'])
const formRef = ref(null)
const form = ref({ title: '', description: '' })
const loading = ref(true)
const initialForm = ref({ title: '', description: '' })
// 审计 2026-08-14 修复: 编辑模式加载失败的守卫标志。
// 加载失败时禁止 handleSave, 防止空 formData 覆盖已有章节内容
const loaded = ref(false)

const rules = {
  title: [{ required: true, message: '章节名称不能为空', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    // 审计 2026-08-14 修复: 改用 getChapterById 按 ID 精确加载,
    // 避免 getChapters({size:200}) 分页截断(>200 章节的课程查不到)
    const res = await getChapterById(props.chapterId)
    const ch = res?.data
    if (ch && ch.id) {
      form.value = { title: ch.title || '', description: ch.description || '' }
      initialForm.value = { ...form.value }
      loaded.value = true
    } else {
      loaded.value = false
      ElMessage.error('章节不存在或已被删除')
    }
  } catch {
    // 加载失败: 保持 loaded=false, 后续 blur 不会触发保存
    loaded.value = false
    ElMessage.error('加载章节信息失败')
  } finally {
    loading.value = false
  }
})

async function handleSave() {
  if (loading.value) return
  if (!loaded.value) return  // 加载失败时禁止保存, 避免空数据覆盖已有章节
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
