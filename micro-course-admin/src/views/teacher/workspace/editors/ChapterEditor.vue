<template>
  <div class="chapter-editor">
    <div class="editor-form">
      <el-form :model="form" label-width="100px">
        <el-form-item label="章节名称">
          <el-input v-model="form.title" @blur="handleSave" />
        </el-form-item>
        <el-form-item label="章节描述">
          <el-input v-model="form.description" type="textarea" :rows="4" @blur="handleSave" />
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { getChapters, updateChapter } from '@/api/chapter'
import { ElMessage } from 'element-plus'

const props = defineProps({ chapterId: { type: [Number, String], required: true } })
const emit = defineEmits(['update'])
const form = ref({ title: '', description: '' })

onMounted(async () => {
  try {
    const res = await getChapters({ size: 200 })
    const ch = (res.data?.items || []).find(c => c.id == props.chapterId)
    if (ch) form.value = { title: ch.title || '', description: ch.description || '' }
  } catch {}
})

async function handleSave() {
  try { await updateChapter(props.chapterId, form.value); emit('update') }
  catch { ElMessage.error('保存失败') }
}
</script>
