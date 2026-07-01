<!--
  课程详情 / 编辑
  路由: /courses/:id  |  /courses/:id/edit
-->
<template>
  <div class="course-detail-page" v-loading="loading" element-loading-text="加载课程信息...">
    <!-- 面包屑 -->
    <div class="page-breadcrumb">
      <el-breadcrumb>
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/courses' }">课程管理</el-breadcrumb-item>
        <el-breadcrumb-item>{{ isEditMode ? '编辑课程' : (courseData.title || '课程详情') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- ========== 查看模式 ========== -->
    <template v-if="!isEditMode && !loading">
      <!-- 头部操作栏 -->
      <div class="action-bar">
        <h1 class="course-title">{{ courseData.title || '未命名课程' }}</h1>
        <div class="action-buttons">
          <template v-if="courseData.status === 0">
            <el-button type="primary" @click="handleSubmitForReview">提交审核</el-button>
          </template>
          <template v-if="courseData.status === 1">
            <el-button type="success" @click="handleApprove">审核通过</el-button>
            <el-button type="danger" @click="handleReject">驳回</el-button>
          </template>
          <template v-if="courseData.status === 2">
            <el-button type="primary" @click="handlePublish">发布</el-button>
          </template>
          <template v-if="courseData.status === 4">
            <el-button type="warning" @click="handleUnpublish">下架</el-button>
          </template>
          <el-button type="primary" plain @click="switchToEdit">编辑</el-button>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </div>

      <!-- 基本信息 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">基本信息</span></template>
        <div class="info-grid">
          <div class="info-item">
            <label>分类</label>
            <span>{{ courseData.categoryName || '-' }}</span>
          </div>
          <div class="info-item">
            <label>授课教师</label>
            <span>{{ courseData.teacherName || '-' }}</span>
          </div>
          <div class="info-item">
            <label>状态</label>
            <span>
              <el-tag v-if="courseData.status === 0" type="info" size="small">草稿</el-tag>
              <el-tag v-else-if="courseData.status === 1" type="warning" size="small">待审核</el-tag>
              <el-tag v-else-if="courseData.status === 2" type="success" size="small">已通过</el-tag>
              <el-tag v-else-if="courseData.status === 3" type="danger" size="small">驳回</el-tag>
              <el-tag v-else-if="courseData.status === 4" type="success" size="small">已发布</el-tag>
              <el-tag v-else-if="courseData.status === 5" type="warning" size="small">已下架</el-tag>
              <el-tag v-else type="info" size="small">已归档</el-tag>
            </span>
          </div>
          <div class="info-item">
            <label>学分</label>
            <span>{{ courseData.creditHours ?? '-' }}</span>
          </div>
          <div class="info-item">
            <label>学期</label>
            <span>{{ courseData.semester || '-' }}</span>
          </div>
          <div class="info-item">
            <label>难度</label>
            <span>
              <template v-if="courseData.difficulty === 1 || courseData.difficulty === 'BEGINNER'">初级</template>
              <template v-else-if="courseData.difficulty === 2 || courseData.difficulty === 'INTERMEDIATE'">中级</template>
              <template v-else-if="courseData.difficulty === 3 || courseData.difficulty === 'ADVANCED'">高级</template>
              <template v-else>-</template>
            </span>
          </div>
          <div class="info-item">
            <label>课程类型</label>
            <span>
              <el-tag v-if="courseData.courseType === 'VIDEO'" type="primary" size="small">视频课程</el-tag>
              <el-tag v-else-if="courseData.courseType === 'INTERACTIVE'" type="success" size="small">互动课程</el-tag>
              <span v-else>{{ courseData.courseType || '-' }}</span>
            </span>
          </div>
          <div class="info-item">
            <label>价格</label>
            <span class="price">{{ courseData.price ? '¥' + courseData.price : '免费' }}</span>
          </div>
          <div class="info-item">
            <label>学员数</label>
            <span>{{ courseData.studentCount ?? 0 }}</span>
          </div>
        </div>
      </el-card>

      <!-- 封面 -->
      <el-card shadow="never" class="info-card" v-if="courseData.coverUrl">
        <template #header><span class="card-title">课程封面</span></template>
        <el-image :src="courseData.coverUrl" fit="contain" class="cover-img" />
      </el-card>

      <!-- 课程描述 -->
      <el-card shadow="never" class="info-card" v-if="courseData.description">
        <template #header><span class="card-title">课程描述</span></template>
        <div class="description-html" v-html="courseData.description"></div>
      </el-card>

      <!-- 章节管理 -->
      <el-card shadow="never" class="chapter-card">
        <template #header>
          <div class="card-header-row">
            <span class="card-title">章节管理 <span class="hint">（可拖拽排序）</span></span>
            <el-button v-if="userRole !== 'ACADEMIC'" type="primary" size="small" @click="handleCreateChapter">新增章节</el-button>
          </div>
        </template>
        <el-table ref="chapterTableRef" v-loading="chapterLoading" :data="chapters" stripe row-key="id">
          <template #empty><el-empty description="暂无章节" /></template>
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="title" label="章节标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="内容形式" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.chapterType === 'VIDEO'" type="primary" size="small">📹 视频课</el-tag>
              <el-tag v-else-if="row.chapterType === 'INTERACTIVE'" type="success" size="small">🎯 互动课</el-tag>
              <el-tag v-else-if="row.chapterType === 'EXERCISE'" type="warning" size="small">📝 练习</el-tag>
              <el-tag v-else type="info" size="small">—</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="内容状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag v-if="(row.videoCount || 0) > 0" type="success" size="small">● {{ row.videoCount }} 个视频</el-tag>
              <el-tag v-else type="info" size="small">○ 待添加</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="时长" width="80" align="center">
            <template #default="{ row }">{{ row.duration || '-' }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
          <el-table-column label="操作" width="200" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditChapter(row)">编辑</el-button>
              <el-button type="success" link size="small" @click="handleManageChapterContent(row)">内容</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteChapter(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="chapters.length > 0" class="sort-bar">
          <el-button type="warning" size="small" :loading="saveSortLoading" @click="handleSaveSort">保存排序</el-button>
        </div>
      </el-card>
    </template>

    <!-- ========== 编辑模式 ========== -->
    <template v-if="isEditMode && !loading">
      <!-- 基本信息 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">基本信息</span></template>
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="edit-form">
          <el-form-item label="课程标题" prop="title">
            <el-input v-model="formData.title" placeholder="请输入课程标题" />
          </el-form-item>
          <el-form-item label="分类" prop="categoryId">
            <el-select v-model="formData.categoryId" placeholder="请选择分类" class="full-width">
              <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="授课教师">
            <el-input :model-value="teacherName" disabled />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="学分">
                <el-input-number v-model="formData.creditHours" :min="0" :max="20" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="学期">
                <el-input v-model="formData.semester" placeholder="如：2024春季" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="难度">
                <el-select v-model="formData.difficulty" placeholder="请选择" class="full-width" clearable>
                  <el-option label="初级" :value="1" />
                  <el-option label="中级" :value="2" />
                  <el-option label="高级" :value="3" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="价格(¥)">
                <el-input-number v-model="formData.price" :min="0" :precision="2" placeholder="0=免费" class="full-width" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">定价规则</el-divider>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="免费范围">
                <el-select v-model="formData.freeAccessScope" placeholder="选择免费范围" class="full-width">
                  <el-option label="无" value="none" />
                  <el-option label="同院系" value="same_department" />
                  <el-option label="同学院" value="same_college" />
                  <el-option label="同学校" value="same_school" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="优惠范围">
                <el-select v-model="formData.discountScope" placeholder="选择优惠范围" class="full-width">
                  <el-option label="无" value="none" />
                  <el-option label="同学院" value="same_college" />
                  <el-option label="同学校" value="same_school" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="优惠比例">
                <el-input-number v-model="formData.discountPercent" :min="0" :max="100" :step="5" class="full-width" />%
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>

      <!-- 课程描述 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">课程描述</span></template>
        <el-form label-width="100px">
          <el-form-item label="课程描述" prop="description">
            <div class="quill-editor-wrapper">
              <QuillEditor v-model:content="formData.description" content-type="html" toolbar="essential" placeholder="请输入课程描述..." :style="{ minHeight: '180px' }" />
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 封面 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">课程封面</span></template>
        <div class="cover-edit-area">
          <template v-if="!coverPreviewUrl">
            <el-upload ref="coverUploadRef" :auto-upload="false" :limit="1" accept="image/jpeg,image/png,image/gif,image/webp" :on-change="handleCoverChange" drag>
              <el-icon class="el-icon--upload"><i class="el-icon-upload" /></el-icon>
              <div class="el-upload__text">拖拽或<em>点击上传</em></div>
              <template #tip><div class="form-tip">建议 1200×628px，支持 JPG/PNG/GIF/WebP，最大 5MB</div></template>
            </el-upload>
          </template>
          <div v-else class="cover-preview-wrap">
            <img :src="coverPreviewUrl" class="cover-preview-img" alt="封面预览" />
            <el-button size="small" @click="handleRemoveCover">删除</el-button>
          </div>
        </div>
      </el-card>

      <!-- 操作按钮 -->
      <div class="submit-bar">
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
        <el-button @click="switchToView">取消</el-button>
      </div>
    </template>

    <!-- 章节弹窗 -->
    <el-dialog v-model="chapterDialogVisible" :title="chapterDialogTitle" width="480px" @close="handleChapterDialogClose" :close-on-press-escape="true">
      <el-form ref="chapterFormRef" :model="chapterFormData" :rules="chapterFormRules" label-width="80px">
        <el-form-item label="章节标题" prop="title">
          <el-input v-model="chapterFormData.title" placeholder="如：第一章 · 环境搭建" />
        </el-form-item>
        <el-form-item label="内容形式" prop="chapterType">
          <el-select v-model="chapterFormData.chapterType" placeholder="选择本章节的内容形式" class="full-width">
            <el-option label="📹 视频讲解" value="VIDEO" />
            <el-option label="🎯 互动课件" value="INTERACTIVE" />
            <el-option label="📝 随堂练习" value="EXERCISE" />
          </el-select>
          <div class="form-tip" style="margin-top:4px">
            <template v-if="chapterFormData.chapterType === 'VIDEO'">学生点击后进入视频播放器，教师需上传教学视频</template>
            <template v-else-if="chapterFormData.chapterType === 'INTERACTIVE'">学生点击后进入互动课件，教师需导入 PPT 并编辑</template>
            <template v-else-if="chapterFormData.chapterType === 'EXERCISE'">学生点击后开始答题，教师需提前创建练习题</template>
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="chapterFormData.sortOrder" :min="1" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计时长(分)">
              <el-input-number v-model="chapterFormData.duration" :min="0" placeholder="选填" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="handleChapterCancel">取消</el-button>
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
import { getCourseById, updateCourse, updateCourseStatus, approveCourse, rejectCourse, submitCourseForReview, updateCourseCover } from '@/api/course'
import { getChapters, createChapter, updateChapter, deleteChapter, sortChapters } from '@/api/chapter'
import { getCategories } from '@/api/course-category'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const courseId = computed(() => route.params.id)
const isEditMode = computed(() => route.path.includes('/edit'))

const loading = ref(true)
const submitLoading = ref(false)
const courseData = ref({})
const categories = ref([])

const formRef = ref(null)
const formData = reactive({
  title: '', categoryId: null, teacherId: null,
  description: '', creditHours: 1, semester: '',
  difficulty: null, courseType: 'VIDEO', price: null, isFree: true,
  freeAccessScope: 'none',
  freeDeptIds: '[]',
  discountScope: 'none',
  discountPercent: 0
})
const formRules = {
  title: [{ required: true, message: '请输入课程标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}
const teacherName = computed(() => courseData.value.teacherName || '')

const coverUploadRef = ref(null)
const coverPreviewUrl = ref('')
const coverFile = ref(null)

// ===== 章节 =====
const chapterLoading = ref(false)
const chapterSubmitLoading = ref(false)
const saveSortLoading = ref(false)
const chapters = ref([])
const chapterDialogVisible = ref(false)
const chapterDialogTitle = ref('新增章节')
const isChapterEdit = ref(false)
const currentChapterId = ref(null)
const chapterFormRef = ref(null)
const chapterTableRef = ref(null)

const chapterFormData = reactive({ title: '', sortOrder: 0, chapterType: 'VIDEO', duration: 0 })
const chapterFormRules = {
  title: [{ required: true, message: '请输入章节标题', trigger: 'blur' }],
  chapterType: [{ required: true, message: '请选择内容类型', trigger: 'change' }]
}
const onChapterTypeChange = () => {}

let sortableInstance = null

// ===== 数据加载 =====
const fetchCategories = async () => {
  try { const { data } = await getCategories({ size: 1000 }); categories.value = data.items || [] }
  catch { categories.value = [] }
}

const fetchCourse = async () => {
  if (!courseId.value) return
  loading.value = true
  try {
    const { data } = await getCourseById(courseId.value)
    courseData.value = data || {}
    if (isEditMode.value) {
      formData.title = data.title || ''
      formData.categoryId = data.categoryId || null
      formData.description = data.description || ''
      formData.creditHours = data.creditHours ?? 1
      formData.semester = data.semester || ''
      formData.difficulty = data.difficulty ?? null
      formData.courseType = data.courseType || 'VIDEO'
      formData.price = data.price ?? null
      formData.isFree = data.isFree !== false
      if (data.coverUrl) coverPreviewUrl.value = data.coverUrl
    }
  } catch (e) { ElMessage.error(e?.response?.data?.message || '获取课程信息失败') }
  finally { loading.value = false }
}

const fetchChapters = async () => {
  if (!courseId.value) return
  chapterLoading.value = true
  try { const { data } = await getChapters({ courseId: courseId.value, size: 999 }); chapters.value = data?.items || data || [] }
  catch { chapters.value = [] }
  finally { chapterLoading.value = false; await nextTick(); initSortable() }
}

const initSortable = () => {
  if (sortableInstance) sortableInstance.destroy()
  const el = chapterTableRef.value?.$el?.querySelector('.el-table__body-wrapper tbody')
  if (!el) return
  sortableInstance = Sortable.create(el, { handle: '.el-table__row', animation: 150, onEnd: () => {} })
}

// ===== 页面操作 =====
const handleBack = () => router.push('/courses')
const switchToEdit = () => router.push(`/courses/${courseId.value}/edit`)
const switchToView = () => router.push(`/courses/${courseId.value}`)

const handleSubmitForReview = async () => {
  try { await ElMessageBox.confirm('确定提交审核？', '提示', { type: 'info' }) } catch { return }
  try { await submitCourseForReview(courseId.value); ElMessage.success('已提交审核'); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}
const handleApprove = async () => {
  try { await ElMessageBox.confirm('确定审核通过？', '提示', { type: 'info' }) } catch { return }
  try { await approveCourse(courseId.value); ElMessage.success('审核通过'); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}
const handleReject = async () => {
  // eslint-disable-next-line no-useless-assignment -- reason 在 try 块内被用户输入覆盖
  let reason = ''
  try { const res = await ElMessageBox.prompt('请输入驳回原因', '驳回', { confirmButtonText: '确定', inputType: 'textarea' }); reason = res.value }
  catch { return }
  try { await rejectCourse(courseId.value, reason); ElMessage.success('已驳回'); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}
const handlePublish = async () => {
  try { await ElMessageBox.confirm('确定发布？', '提示', { type: 'info' }) } catch { return }
  try { await updateCourseStatus(courseId.value, 4); ElMessage.success('已发布'); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}
const handleUnpublish = async () => {
  try { await ElMessageBox.confirm('确定下架？', '提示', { type: 'info' }) } catch { return }
  try { await updateCourseStatus(courseId.value, 5); ElMessage.success('已下架'); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

// ===== 编辑提交 =====
const handleCoverChange = (file) => {
  if (file.raw && file.raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('封面图片不能超过 5MB')
    coverUploadRef.value?.clearFiles()
    return
  }
  coverFile.value = file.raw
  if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
  coverPreviewUrl.value = URL.createObjectURL(file.raw)
}
const handleRemoveCover = () => {
  if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
  coverPreviewUrl.value = ''; coverFile.value = null
  coverUploadRef.value?.clearFiles()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    const valid = await formRef.value.validate()
    if (!valid) return
  } catch { return }
  submitLoading.value = true
  try {
    await updateCourse(courseId.value, {
      title: formData.title, categoryId: formData.categoryId, teacherId: formData.teacherId,
      description: formData.description,
      creditHours: formData.creditHours, semester: formData.semester || undefined,
      difficulty: formData.difficulty, courseType: formData.courseType,
      price: formData.price, isFree: formData.isFree
    })
    if (coverFile.value) {
      try { await updateCourseCover(courseId.value, coverFile.value) }
      catch { ElMessage.warning('信息已保存，封面上传失败') }
    }
    ElMessage.success('保存成功')
    router.push(`/courses/${courseId.value}`)
  } catch (e) { ElMessage.error(e?.response?.data?.message || '保存失败') }
  finally { submitLoading.value = false }
}

// ===== 章节操作 =====
const handleCreateChapter = () => {
  chapterDialogTitle.value = '新增章节'; isChapterEdit.value = false
  chapterFormData.title = ''; chapterFormData.sortOrder = 0
  chapterFormData.chapterType = 'VIDEO'; chapterFormData.duration = 0
  chapterDialogVisible.value = true
}
const handleEditChapter = (row) => {
  chapterDialogTitle.value = '编辑章节'; isChapterEdit.value = true; currentChapterId.value = row.id
  chapterFormData.title = row.title || ''; chapterFormData.sortOrder = row.sortOrder ?? 0
  chapterFormData.chapterType = row.chapterType || 'VIDEO'; chapterFormData.duration = row.duration ?? 0
  chapterDialogVisible.value = true
}
const handleManageChapterContent = (row) => {
  const cid = courseId.value
  if (row.chapterType === 'INTERACTIVE') {
    router.push(`/teacher/courses/${cid}/slides/manage`)
  } else if (row.chapterType === 'EXERCISE') {
    router.push(`/courses/${cid}/exercises`)
  } else {
    router.push(`/courses/${cid}/videos?chapterId=${row.id}`)
  }
}
const handleDeleteChapter = async (row) => {
  try { await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' }) } catch { return }
  try { await deleteChapter(row.id); ElMessage.success('已删除'); fetchChapters() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '删除失败') }
}
const handleSaveSort = async () => {
  if (saveSortLoading.value) return
  saveSortLoading.value = true
  const sorts = chapters.value.map((c, i) => ({ id: c.id, sortOrder: i + 1 }))
  try { await sortChapters(sorts); ElMessage.success('排序已保存'); fetchChapters() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '保存排序失败') }
  finally { saveSortLoading.value = false }
}
const handleChapterSubmit = async () => {
  if (!chapterFormRef.value) return
  try { await chapterFormRef.value.validate() } catch { return }
  chapterSubmitLoading.value = true
  try {
    if (isChapterEdit.value) {
      const { title, sortOrder, chapterType, duration } = chapterFormData
      await updateChapter(currentChapterId.value, { title, sortOrder, chapterType, duration: duration || 0 })
    } else {
      await createChapter({ ...chapterFormData, courseId: Number(courseId.value) })
    }
    ElMessage.success(isChapterEdit.value ? '更新成功' : '创建成功')
    chapterDialogVisible.value = false
    await fetchChapters()
  } catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { chapterSubmitLoading.value = false }
}
const handleChapterCancel = () => { chapterDialogVisible.value = false }
const handleChapterDialogClose = () => { chapterFormRef.value?.resetFields() }

onMounted(() => {
  fetchCategories()
  fetchCourse().then(() => { if (!isEditMode.value) fetchChapters() })
})
onUnmounted(() => { if (sortableInstance) sortableInstance.destroy() })
</script>

<style scoped>
.course-detail-page {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
}
.page-breadcrumb { margin-bottom: 16px; }

/* 操作栏 */
.action-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.course-title { font-size: 22px; font-weight: 600; color: #303133; margin: 0; }
.action-buttons { display: flex; gap: 8px; flex-wrap: wrap; }

/* 信息卡片 */
.info-card { margin-bottom: 16px; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
.hint { font-size: 12px; color: #909399; font-weight: 400; }
.edit-card { margin-bottom: 16px; }
.submit-bar { margin-top: 16px; display: flex; gap: 12px; justify-content: flex-end; }
.cover-edit-area { max-width: 400px; }

/* 信息网格 */
.info-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px 24px; }
.info-item { display: flex; flex-direction: column; gap: 4px; }
.info-item label { font-size: 13px; color: #909399; }
.info-item span { font-size: 14px; color: #303133; word-break: break-word; }
.price { color: #e6a23c; font-weight: 600; }

/* 封面 */
.cover-img { max-width: 300px; border-radius: 6px; }

/* 描述 HTML */
.description-html { line-height: 1.8; color: #303133; font-size: 14px; }
.description-html :deep(ol), .description-html :deep(ul) { padding-left: 20px; margin: 8px 0; }
.description-html :deep(li) { margin-bottom: 4px; }
.description-html :deep(p) { margin: 8px 0; }

/* 章节 */
.chapter-card { margin-bottom: 16px; }
.sort-bar { margin-top: 12px; text-align: right; }

/* 编辑表单 */
.edit-form { max-width: 700px; }
.full-width { width: 100%; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
.cover-preview-wrap { display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.cover-preview-wrap img { max-width: 200px; max-height: 120px; border-radius: 6px; border: 1px solid #ebeef5; object-fit: cover; }

/* Quill */
.quill-editor-wrapper { width: 100%; border-radius: 4px; }
.quill-editor-wrapper :deep(.ql-toolbar) { border-radius: 4px 4px 0 0; background: #fafafa; }
.quill-editor-wrapper :deep(.ql-container) { border-radius: 0 0 4px 4px; font-size: 14px; }

/* 响应式 */
@media (max-width: 768px) {
  .course-detail-page { padding: 12px; }
  .info-grid { grid-template-columns: 1fr 1fr; }
  .cover-img { max-width: 100%; }
}
@media (max-width: 480px) {
  .info-grid { grid-template-columns: 1fr; }
  .action-bar { flex-direction: column; align-items: flex-start; }
}
</style>
