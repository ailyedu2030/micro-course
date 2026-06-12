<!--
  课程详情管理
  路由路径: /courses/:id
  Phase 1
  Author: jackie
-->
<template>
  <div class="course-detail-page">
    <!-- 课程信息卡 -->
    <el-card class="info-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ isEditMode ? '编辑课程' : '课程详情' }}</span>
          <div class="header-actions">
            <el-button v-if="!isEditMode && courseData.status === 0" type="primary" @click="handleSubmitForReview">提交审核</el-button>
            <el-button v-if="!isEditMode && courseData.status === 1" type="success" @click="handleApprove">审核通过</el-button>
            <el-button v-if="!isEditMode && courseData.status === 1" type="danger" @click="handleReject">驳回</el-button>
            <el-button v-if="!isEditMode && courseData.status === 2" type="primary" @click="handlePublish">发布</el-button>
            <el-button v-if="!isEditMode && courseData.status === 4" type="warning" @click="handleUnpublish">下架</el-button>
            <el-button v-if="!isEditMode" type="primary" @click="switchToEdit">编辑</el-button>
            <el-button @click="handleBack">返回</el-button>
          </div>
        </div>
      </template>

      <!-- 查看模式 -->
      <div v-if="!isEditMode" class="course-view">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="课程标题">{{ courseData.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ courseData.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="教师ID">{{ courseData.teacherId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="courseData.status === 0" type="info" size="small">草稿</el-tag>
            <el-tag v-else-if="courseData.status === 1" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="courseData.status === 2" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="courseData.status === 3" type="danger" size="small">驳回</el-tag>
            <el-tag v-else-if="courseData.status === 4" type="success" size="small">已发布</el-tag>
            <el-tag v-else-if="courseData.status === 5" type="warning" size="small">下架</el-tag>
            <el-tag v-else type="info" size="small">归档</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="学分">{{ courseData.creditHours ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="学期">{{ courseData.semester || '-' }}</el-descriptions-item>
          <el-descriptions-item label="难度">
            <el-tag v-if="courseData.difficulty === 'BEGINNER'" size="small">初级</el-tag>
            <el-tag v-else-if="courseData.difficulty === 'INTERMEDIATE'" size="small">中级</el-tag>
            <el-tag v-else-if="courseData.difficulty === 'ADVANCED'" size="small">高级</el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="评分">{{ courseData.rating ? courseData.rating.toFixed(1) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="学生数">{{ courseData.studentCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="课程描述" :span="2">{{ courseData.description || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 编辑模式 -->
      <el-form v-else ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="form-container">
        <el-form-item label="课程标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入课程标题" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="formData.categoryId" placeholder="请选择分类" class="full-width">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师ID" prop="teacherId">
          <el-input v-model.number="formData.teacherId" placeholder="请输入教师ID" type="number" />
        </el-form-item>
        <el-form-item label="课程描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入课程描述" />
        </el-form-item>
        <el-form-item label="学分" prop="creditHours">
          <el-input-number v-model="formData.creditHours" :min="0" :max="20" class="full-width" />
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <el-input v-model="formData.semester" placeholder="如：2024春季" />
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="formData.difficulty" placeholder="请选择难度" class="full-width">
            <el-option label="初级" value="BEGINNER" />
            <el-option label="中级" value="INTERMEDIATE" />
            <el-option label="高级" value="ADVANCED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
          <el-button @click="switchToView">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 章节管理卡 -->
    <el-card v-if="!isEditMode" class="chapter-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">章节管理 <span class="drag-hint">(可拖拽排序)</span></span>
          <el-button type="primary" size="small" v-if="userRole !== 'ACADEMIC'" @click="handleCreateChapter">新增章节</el-button>
        </div>
      </template>
      <el-table ref="chapterTableRef" v-loading="chapterLoading" :data="chapters" stripe border class="data-table">
        <template #empty>
          <el-empty description="暂无章节数据" />
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="chapterType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.chapterType === 'VIDEO'" type="primary" size="small">视频</el-tag>
            <el-tag v-else-if="row.chapterType === 'DOCUMENT'" type="info" size="small">文档</el-tag>
            <el-tag v-else-if="row.chapterType === 'QUIZ'" type="warning" size="small">测验</el-tag>
            <el-tag v-else type="info" size="small">{{ row.chapterType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长(分钟)" width="120" align="center">
          <template #default="{ row }">
            {{ row.duration ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditChapter(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteChapter(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="chapters.length > 0" class="sort-actions">
        <el-button type="warning" size="small" @click="handleSaveSort">保存排序</el-button>
      </div>
    </el-card>

    <!-- 章节弹窗 -->
    <el-dialog v-model="chapterDialogVisible" :title="chapterDialogTitle" width="500px" @close="handleChapterDialogClose">
      <el-form ref="chapterFormRef" :model="chapterFormData" :rules="chapterFormRules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="chapterFormData.title" placeholder="请输入章节标题" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="chapterFormData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="类型" prop="chapterType">
          <el-select v-model="chapterFormData.chapterType" placeholder="请选择类型" class="full-width">
            <el-option label="视频" value="VIDEO" />
            <el-option label="文档" value="DOCUMENT" />
            <el-option label="测验" value="QUIZ" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长(分钟)" prop="duration">
          <el-input-number v-model="chapterFormData.duration" :min="0" class="full-width" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="chapterSubmitLoading" @click="handleChapterSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Sortable from 'sortablejs'
import { useUserStore } from '@/store/user'
import { getCourseById, updateCourse, updateCourseStatus, submitCourseForReview } from '@/api/course'
import { getChapters, createChapter, updateChapter, deleteChapter, sortChapters } from '@/api/chapter'
import { getCategories } from '@/api/course-category'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const courseId = computed(() => route.params.id)
const isEditMode = computed(() => route.path.includes('/edit'))

const loading = ref(false)
const submitLoading = ref(false)
const courseData = ref({})
const categories = ref([])

const formRef = ref(null)
const formData = reactive({
  title: '',
  categoryId: '',
  teacherId: '',
  description: '',
  creditHours: 1,
  semester: '',
  difficulty: ''
})

const formRules = {
  title: [{ required: true, message: '请输入课程标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  teacherId: [{ required: true, message: '请输入教师ID', trigger: 'blur' }]
}

const chapterLoading = ref(false)
const chapterSubmitLoading = ref(false)
const chapters = ref([])
const chapterDialogVisible = ref(false)
const chapterDialogTitle = ref('新增章节')
const isChapterEdit = ref(false)
const currentChapterId = ref(null)
const chapterFormRef = ref(null)
const chapterTableRef = ref(null)

const chapterFormData = reactive({
  title: '',
  sortOrder: 0,
  chapterType: 'VIDEO',
  duration: 0
})

const chapterFormRules = {
  title: [{ required: true, message: '请输入章节标题', trigger: 'blur' }],
  chapterType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

let sortableInstance = null

const fetchCategories = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categories.value = data.items || []
  } catch {
    ElMessage.error('获取分类列表失败')
  }
}

const fetchCourse = async () => {
  if (!courseId.value) return
  loading.value = true
  try {
    const { data } = await getCourseById(courseId.value)
    courseData.value = data || {}
    if (isEditMode.value) {
      formData.title = data.title || ''
      formData.categoryId = data.categoryId || ''
      formData.teacherId = data.teacherId || ''
      formData.description = data.description || ''
      formData.creditHours = data.creditHours || 1
      formData.semester = data.semester || ''
      formData.difficulty = data.difficulty || ''
    }
  } catch {
    ElMessage.error('获取课程信息失败')
  } finally {
    loading.value = false
  }
}

const fetchChapters = async () => {
  if (!courseId.value) return
  chapterLoading.value = true
  try {
    const { data } = await getChapters({ courseId: courseId.value })
    chapters.value = data.items || []
    await nextTick()
    initSortable()
  } catch {
    ElMessage.error('获取章节列表失败')
  } finally {
    chapterLoading.value = false
  }
}

const initSortable = () => {
  if (!chapterTableRef.value || sortableInstance) return
  const el = chapterTableRef.value.$el.querySelector('.el-table__body-wrapper tbody')
  if (!el) return
  sortableInstance = Sortable.create(el, {
    handle: '.el-table__row',
    animation: 150,
    onEnd: ({ oldIndex, newIndex }) => {
      const movedItem = chapters.value.splice(oldIndex, 1)[0]
      chapters.value.splice(newIndex, 0, movedItem)
      chapters.value.forEach((item, idx) => {
        item.sortOrder = idx + 1
      })
    }
  })
}

const handleSaveSort = async () => {
  try {
    const sorted = chapters.value.map((c, i) => ({
      id: c.id,
      sortOrder: i + 1
    }))
    await sortChapters(sorted)
    ElMessage.success('排序已保存')
  } catch {
    ElMessage.error('排序保存失败')
  }
}

const switchToEdit = () => {
  router.push(`/courses/${courseId.value}/edit`)
}

const switchToView = () => {
  router.push(`/courses/${courseId.value}`)
}

const handleBack = () => {
  router.push('/courses')
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await updateCourse(courseId.value, formData)
      ElMessage.success('保存成功')
      router.push(`/courses/${courseId.value}`)
    } catch {
      ElMessage.error('保存失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleApprove = async () => {
  try {
    await ElMessageBox.confirm('确定审核通过该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(courseId.value, 2)
    ElMessage.success('审核通过成功')
    fetchCourse()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleReject = async () => {
  try {
    await ElMessageBox.confirm('确定驳回该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(courseId.value, 3)
    ElMessage.success('驳回成功')
    fetchCourse()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handlePublish = async () => {
  try {
    await ElMessageBox.confirm('确定发布该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(courseId.value, 4)
    ElMessage.success('发布成功')
    fetchCourse()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleUnpublish = async () => {
  try {
    await ElMessageBox.confirm('确定下架该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(courseId.value, 5)
    ElMessage.success('下架成功')
    fetchCourse()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleSubmitForReview = async () => {
  try {
    await ElMessageBox.confirm('确定提交该课程进行审核?', '提示', { type: 'warning' })
    await submitCourseForReview(courseId.value)
    ElMessage.success('提交成功，课程已进入审核流程')
    fetchCourse()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('提交失败')
    }
  }
}

const handleCreateChapter = () => {
  chapterDialogTitle.value = '新增章节'
  isChapterEdit.value = false
  currentChapterId.value = null
  chapterFormData.title = ''
  chapterFormData.sortOrder = 0
  chapterFormData.chapterType = 'VIDEO'
  chapterFormData.duration = 0
  chapterDialogVisible.value = true
}

const handleEditChapter = (row) => {
  chapterDialogTitle.value = '编辑章节'
  isChapterEdit.value = true
  currentChapterId.value = row.id
  chapterFormData.title = row.title
  chapterFormData.sortOrder = row.sortOrder || 0
  chapterFormData.chapterType = row.chapterType || 'VIDEO'
  chapterFormData.duration = row.duration || 0
  chapterDialogVisible.value = true
}

const handleDeleteChapter = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该章节?', '提示', { type: 'warning' })
    await deleteChapter(row.id)
    ElMessage.success('删除成功')
    fetchChapters()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleChapterSubmit = async () => {
  if (!chapterFormRef.value) return
  await chapterFormRef.value.validate(async (valid) => {
    if (!valid) return
    chapterSubmitLoading.value = true
    try {
      const submitData = {
        courseId: courseId.value,
        title: chapterFormData.title,
        sortOrder: chapterFormData.sortOrder,
        chapterType: chapterFormData.chapterType,
        duration: chapterFormData.duration
      }
      if (isChapterEdit.value) {
        await updateChapter(currentChapterId.value, submitData)
        ElMessage.success('编辑成功')
      } else {
        await createChapter(submitData)
        ElMessage.success('创建成功')
      }
      chapterDialogVisible.value = false
      fetchChapters()
    } catch {
      ElMessage.error(isChapterEdit.value ? '编辑失败' : '创建失败')
    } finally {
      chapterSubmitLoading.value = false
    }
  })
}

const handleChapterDialogClose = () => {
  chapterFormRef.value?.resetFields()
}

onMounted(() => {
  fetchCategories()
  fetchCourse()
  if (!isEditMode.value) {
    fetchChapters()
  }
})

onUnmounted(() => {
  sortableInstance?.destroy()
})
</script>

<style scoped>
.course-detail-page {
  padding: var(--space-5);
}

.info-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-md);
}

.chapter-card {
  border-radius: var(--radius-md);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

.drag-hint {
  font-size: var(--text-xs);
  color: var(--color-text-secondary);
  font-weight: normal;
}

.sort-actions {
  margin-top: var(--space-3);
  display: flex;
  justify-content: flex-end;
}

.course-view {
  max-width: 900px;
}

.form-container {
  max-width: 800px;
}

.data-table {
  width: 100%;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.data-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--color-bg-page);
}

.full-width {
  width: 100%;
}

@media (max-width: 768px) {
  .course-detail-page {
    padding: var(--space-3);
  }

  .info-card,
  .chapter-card {
    margin-bottom: var(--space-3);
  }

  .header-actions {
    flex-wrap: wrap;
  }
}
</style>