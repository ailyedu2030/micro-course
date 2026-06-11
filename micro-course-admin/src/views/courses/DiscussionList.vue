<template>
  <div class="discussion-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="章节">
          <el-select v-model="searchForm.chapterId" placeholder="请选择章节" clearable class="search-input-w180">
            <el-option v-for="ch in chapters" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>讨论区</span>
          <el-button type="primary" @click="handleCreate">发帖</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="commentCount" label="评论数" width="90" align="center" />
        <el-table-column prop="likeCount" label="点赞数" width="90" align="center" />
        <el-table-column prop="createdAt" label="发布时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="isPinned" label="置顶" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPinned" type="success" size="small">已置顶</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="isEssence" label="加精" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isEssence" type="success" size="small">已加精</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
            <el-button type="warning" link size="small" @click="handleTogglePin(row)">
              {{ row.isPinned ? '取消置顶' : '置顶' }}
            </el-button>
            <el-button type="warning" link size="small" @click="handleToggleEssence(row)">
              {{ row.isEssence ? '取消加精' : '加精' }}
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 发帖弹窗 -->
    <el-dialog v-model="dialogVisible" title="发帖" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="章节" prop="chapterId">
          <el-select v-model="formData.chapterId" placeholder="请选择章节" class="full-width">
            <el-option v-for="ch in chapters" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="5" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="匿名">
          <el-checkbox v-model="formData.isAnonymous">匿名发布</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPosts, createPost, deletePost, updatePostPin, updatePostEssence } from '@/api/discussion'
import { getChapters } from '@/api/chapter'

const router = useRouter()

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const chapters = ref([])

const searchForm = reactive({
  chapterId: ''
})

const dialogVisible = ref(false)
const formRef = ref(null)
const formData = reactive({
  title: '',
  content: '',
  chapterId: '',
  isAnonymous: false
})

const formRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  chapterId: [{ required: true, message: '请选择章节', trigger: 'change' }]
}

function formatTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const fetchChapters = async () => {
  try {
    const { data } = await getChapters({ size: 1000 })
    chapters.value = data.items || []
  } catch (error) {
    // ignore
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      chapterId: searchForm.chapterId || undefined
    }
    const { data } = await getPosts(params)
    const items = data.items || []
    // Sort: pinned first, then by createdAt desc
    items.sort((a, b) => {
      if (b.isPinned !== a.isPinned) return b.isPinned ? 1 : -1
      return new Date(b.createdAt) - new Date(a.createdAt)
    })
    tableData.value = items
    totalElements.value = data.totalElements || 0
  } catch (error) {
    ElMessage.error('获取帖子列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.chapterId = ''
  page.value = 1
  fetchData()
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleCreate = () => {
  formData.title = ''
  formData.content = ''
  formData.chapterId = ''
  formData.isAnonymous = false
  dialogVisible.value = true
}

const handleView = (row) => {
  router.push(`/discussions/${row.id}`)
}

const handleTogglePin = async (row) => {
  try {
    await ElMessageBox.confirm(`确定${row.isPinned ? '取消置顶' : '置顶'}该帖子?`, '提示', { type: 'warning' })
    await updatePostPin(row.id, !row.isPinned)
    ElMessage.success('操作成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleToggleEssence = async (row) => {
  try {
    await ElMessageBox.confirm(`确定${row.isEssence ? '取消加精' : '加精'}该帖子?`, '提示', { type: 'warning' })
    await updatePostEssence(row.id, !row.isEssence)
    ElMessage.success('操作成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该帖子?', '提示', { type: 'warning' })
    await deletePost(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await createPost({
        title: formData.title,
        content: formData.content,
        chapterId: formData.chapterId,
        isAnonymous: formData.isAnonymous
      })
      ElMessage.success('发布成功')
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      ElMessage.error('发布失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchChapters()
  fetchData()
})
</script>

<style scoped>
.discussion-list {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.data-table { width: 100%; }
.full-width { width: 100%; }
.search-input-w180 { width: 180px; }
</style>