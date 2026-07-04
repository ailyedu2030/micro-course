<!--
  课程列表
  路由路径: /courses
  Phase 1
  Author: jackie
-->
<template>
  <div class="course-list-page">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="/" class="page-breadcrumb">
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>课程列表</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="课程标题" clearable @clear="handleSearch" class="filter-input-w160" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.categoryId" placeholder="请选择分类" clearable class="filter-input-w160">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师">
          <el-input v-model="searchForm.teacherName" placeholder="教师姓名" clearable class="filter-input-w120" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable class="filter-input-w120">
            <el-option label="草稿" :value="0" />
            <el-option label="待审核" :value="1" />
            <el-option label="通过" :value="2" />
            <el-option label="驳回" :value="3" />
            <el-option label="已发布" :value="4" />
            <el-option label="下架" :value="5" />
            <el-option label="归档" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程类型">
          <el-select v-model="searchForm.courseType" placeholder="全部类型" clearable class="filter-input-w140" @change="handleSearch">
            <el-option label="视频课程" value="VIDEO" />
            <el-option label="互动课程" value="INTERACTIVE" />
            <el-option label="线下课程" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ pageTitle }}</span>
          <div class="header-actions">
            <el-button
              type="warning"
              size="default"
              :disabled="tableData.length === 0"
              @click="handleExport"
              aria-label="导出数据"
            >
              <el-icon><Download /></el-icon>导出
            </el-button>
            <el-button type="primary" v-if="userRole !== 'ACADEMIC' && !route.query.courseType" @click="handleCreate">新增课程</el-button>
            <el-button type="primary" v-if="route.query.courseType === 'OFFLINE'" @click="showOfflineDialog = true" :icon="Plus">新增安排</el-button>
            <el-button v-if="route.query.courseType" @click="router.push('/teacher/courses')">返回课程列表</el-button>
          </div>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" description="未找到匹配的课程，尝试更换筛选条件" :image-size="120">
        <template #default>
          <el-button type="primary" @click="handleReset">清除筛选</el-button>
        </template>
      </el-empty>
      <el-table v-else :data="tableData" stripe border class="data-table" ref="tableRef" @row-click="handleRowClick">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="封面" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              fit="cover"
              class="table-thumb"
              :preview-src-list="[row.coverUrl]"
              lazy
            />
            <span v-else class="no-thumb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.courseType === 'INTERACTIVE'" type="success" size="small" effect="plain">互动</el-tag>
            <el-tag v-else-if="row.courseType === 'OFFLINE'" type="info" size="small" effect="plain">线下</el-tag>
            <el-tag v-else type="primary" size="small" effect="plain">视频</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="teacherName" label="教师" width="100" />
        <el-table-column prop="studentCount" label="学员数" width="90" align="center" />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">草稿</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger" size="small">驳回</el-tag>
            <el-tag v-else-if="row.status === 4" type="success" size="small">已发布</el-tag>
            <el-tag v-else-if="row.status === 5" type="warning" size="small">下架</el-tag>
            <el-tag v-else type="info" size="small">归档</el-tag>
            <div v-if="row.status === 1" class="review-hint">审核中，预计48h</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.courseType === 'INTERACTIVE'" type="success" link size="small" @click.stop="goSlides(row)">课件</el-button>
            <el-button v-if="row.courseType === 'OFFLINE'" type="info" link size="small" @click.stop="handleView(row)">安排</el-button>
            <el-button v-if="row.status === 1 && userRole === 'ADMIN'" type="success" link size="small" :loading="actingId === row.id" @click.stop="handleApprove(row)">审核通过</el-button>
            <el-button v-if="row.status === 1 && userRole === 'ADMIN'" type="danger" link size="small" :loading="actingId === row.id" @click.stop="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 2 && userRole === 'ADMIN'" type="primary" link size="small" :loading="actingId === row.id" @click.stop="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 4 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')" type="warning" link size="small" :loading="actingId === row.id" @click.stop="handleUnpublish(row)">下架</el-button>
            <el-button type="info" link size="small" @click.stop="handleView(row)">查看</el-button>
            <el-button type="primary" link size="small" @click.stop="handleCopy(row)">复制</el-button>
            <el-button type="danger" link size="small" :loading="actingId === row.id" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" aria-label="分页导航"
/>
      </div>
    </el-card>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="课程标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入课程标题" />
        </el-form-item>
        <el-form-item label="课程类型" prop="courseType">
          <el-select v-model="formData.courseType" class="full-width">
            <el-option label="视频课程" value="VIDEO" />
            <el-option label="互动课程" value="INTERACTIVE" />
            <el-option label="线下课程" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="formData.categoryId" placeholder="请选择" class="full-width">
                <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授课教师" prop="teacherId">
              <el-select v-model="formData.teacherId" placeholder="请选择" class="full-width" filterable :disabled="userStore.role === 'TEACHER'">
                <el-option v-for="t in teacherOptions" :key="t.id" :label="t.realName || t.username" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程描述" prop="description">
          <div class="quill-editor-wrapper">
            <QuillEditor v-model:content="formData.description" content-type="html" toolbar="essential" placeholder="请输入课程描述..." :style="{ minHeight: '150px' }" />
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="学分">
              <el-input-number v-model="formData.creditHours" :min="0" :max="20" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学期">
              <el-input v-model="formData.semester" placeholder="如：2024春季" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="难度">
              <el-select v-model="formData.difficulty" placeholder="请选择" class="full-width" clearable>
                <el-option label="初级" :value="1" />
                <el-option label="中级" :value="2" />
                <el-option label="高级" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
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
              <el-select v-model="formData.freeAccessScope" class="full-width">
                <el-option label="无" value="none" />
                <el-option label="同院系" value="same_department" />
                <el-option label="同学院" value="same_college" />
                <el-option label="同学校" value="same_school" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优惠范围">
              <el-select v-model="formData.discountScope" class="full-width">
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
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="课程封面">
              <template v-if="!coverPreviewUrl">
                <el-upload ref="coverUploadRef" :auto-upload="false" :limit="1" accept="image/jpeg,image/png" :before-upload="handleBeforeCoverUpload" :on-change="handleCoverChange">
                  <el-button size="small" type="primary"><el-icon><Plus /></el-icon>选择图片</el-button>
                </el-upload>
                <div class="form-tip">1200×628px，JPG/PNG/GIF/WebP，≤2MB</div>
              </template>
              <div v-else class="cover-preview-wrap">
                <img :src="coverPreviewUrl" class="cover-preview-img" alt="封面预览" />
                <el-button size="small" @click="handleRemoveCover">删除</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 线下课新增安排弹窗 -->
    <el-dialog v-model="showOfflineDialog" title="新增线下安排" width="500px" @close="resetOfflineForm">
      <el-form ref="offlineFormRef" :model="offlineForm" :rules="offlineRules" label-width="100px">
        <el-form-item label="课程">
          <el-select v-model="offlineForm.courseId" placeholder="选择课程" class="full-width" filterable @change="onOfflineCourseChange">
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节" prop="chapterId">
          <el-select v-model="offlineForm.chapterId" placeholder="选择章节" class="full-width" :disabled="!offlineForm.courseId">
            <el-option v-for="ch in offlineChapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" prop="sessionDate">
          <el-date-picker v-model="offlineForm.sessionDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" class="full-width" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker v-model="offlineForm.startTime" placeholder="开始" value-format="HH:mm:ss" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker v-model="offlineForm.endTime" placeholder="结束" value-format="HH:mm:ss" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地点" prop="location">
          <el-input v-model="offlineForm.location" placeholder="如：教学楼 A-101" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="offlineForm.teacherNotes" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showOfflineDialog = false">取消</el-button>
        <el-button type="primary" :loading="offlineSubmitting" @click="submitOffline">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUrlPagination } from '@/composables/useUrlPagination'
import { swrCache } from '@/composables/useStaleWhileRevalidate'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import { useUserStore } from '@/store/user'
import { getCourses, createCourse, updateCourseStatus, deleteCourse, approveCourse, rejectCourse, copyCourse, updateCourseCover, publishCourse, unpublishCourse } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { createOfflineSession } from '@/api/offline-session'
import { getCategories } from '@/api/course-category'
import { getUsers } from '@/api/user'

const router = useRouter()
const route = useRoute()
const { bindToQuery } = useUrlPagination()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

// NN/g IA 原则: 标签精度比覆盖更重要。courseType filter 由 URL 驱动,
// 落地直接显示"我的视频课/互动课/线下课"避免泛词
const courseTypeLabels = { VIDEO: '视频课', INTERACTIVE: '互动课', OFFLINE: '线下课' }
const pageTitle = computed(() => {
  const base = userRole.value === 'TEACHER' ? '我的' : ''
  if (searchForm.courseType && courseTypeLabels[searchForm.courseType]) {
    return `${base}${courseTypeLabels[searchForm.courseType]}`
  }
  return `${base}课程`
})

const loading = ref(false)
const submitLoading = ref(false)
const actingId = ref(null)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const categories = ref([])
const teacherOptions = ref([])

const searchForm = reactive({
  keyword: '',
  categoryId: '',
  teacherName: '',
  status: '',
  courseType: ''
})

// P2-14: URL 分页同步
bindToQuery(page, size, searchForm, ['keyword', 'categoryId', 'teacherName', 'status', 'courseType'])

const dialogVisible = ref(false)
const dialogTitle = ref('新增课程')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({
  title: '',
  categoryId: null,
  teacherId: null,
  description: '',
  creditHours: 1,
  semester: '',
  difficulty: null,
  courseType: 'VIDEO',
  price: null,
  freeAccessScope: 'none',
  freeDeptIds: '[]',
  discountScope: 'none',
  discountPercent: 0
})

// 封面上传相关
const coverUploadRef = ref(null)
const coverPreviewUrl = ref('')
const coverFile = ref(null)

const formRules = {
  title: [{ required: true, message: '请输入课程标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  teacherId: [{ required: true, message: '请选择授课教师', trigger: 'change' }]
}

const fetchCategories = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categories.value = data.items || []
  } catch {
    ElMessage.error('获取分类列表失败')
  }
}
const fetchTeachers = async () => {
  // TEACHER 角色无需下拉选自己（也无权限查用户列表）
  if (userStore.role === 'TEACHER') { teacherOptions.value = []; return }
  try {
    const { data } = await getUsers({ role: 'TEACHER', size: 1000 })
    teacherOptions.value = data.items || []
  } catch { teacherOptions.value = [] }
}

const fetchData = async () => {
  const params = {
    page: page.value - 1,
    size: size.value,
    keyword: searchForm.keyword || undefined,
    categoryId: searchForm.categoryId || undefined,
    teacherName: searchForm.teacherName || undefined,
    status: searchForm.status !== '' ? searchForm.status : undefined,
    courseType: searchForm.courseType !== '' ? searchForm.courseType : undefined,
    // 教师自动过滤为自己的课程
    teacherId: userStore.role === 'TEACHER' ? userStore.userId : null
  }
  // P2-17: SWR 模式 — 如果有缓存数据立即显示（无 loading），后台刷新
  // P2-11: 缓存键加入角色前缀，避免不同角色混用缓存
  const cacheKey = `courses:${userStore.role}:${JSON.stringify(params)}`
  const cached = swrCache.get(cacheKey)
  if (cached && Date.now() - cached.ts < 30000) {
    tableData.value = cached.data.items || []
    totalElements.value = cached.data.totalElements || 0
    // 后台静默刷新
    getCourses(params).then(({ data }) => {
      swrCache.set(cacheKey, { data, ts: Date.now() })
      tableData.value = data.items || []
      totalElements.value = data.totalElements || 0
    }).catch(() => {})
    return
  }
  loading.value = true
  try {
    const { data } = await getCourses(params)
    swrCache.set(cacheKey, { data, ts: Date.now() })
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取课程列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.categoryId = ''
  searchForm.teacherName = ''
  searchForm.status = ''
  searchForm.courseType = ''
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

const handleRowClick = (row) => {
  // 预留：可点击行查看详情
}

// a11y:让 el-table 行可被键盘聚焦,Enter 触发 handleRowClick(A11Y-016)
const tableRef = ref(null)
let _keydownBound = false
const bindTableKeyboard = () => {
  // 在表格 tbody 上挂 keydown 监听,聚焦行后 Enter/Space 等价于点击
  if (_keydownBound) return
  const tbody = tableRef.value?.$el?.querySelector('tbody')
  if (!tbody) return
  tbody.addEventListener('keydown', (e) => {
    const tr = e.target.closest('tr')
    if (!tr) return
    if (e.key !== 'Enter' && e.key !== ' ') return
    const idx = Array.from(tbody.querySelectorAll('tr')).indexOf(tr)
    const row = tableData.value?.[idx]
    if (row) {
      e.preventDefault()
      handleRowClick(row)
    }
  })
  tbody.querySelectorAll('tr').forEach((tr, idx) => {
    tr.setAttribute('tabindex', '0')
    tr.setAttribute('role', 'button')
    tr.setAttribute('aria-label', `选择课程 ${tableData.value?.[idx]?.title || ''}`)
  })
  _keydownBound = true
}
onMounted(() => nextTick(bindTableKeyboard))

const handleCreate = () => {
  dialogTitle.value = '新增课程'
  isEdit.value = false
  formData.title = ''
  formData.categoryId = null
  formData.teacherId = userStore.role === 'TEACHER' && userStore.userId
    ? Number(userStore.userId)
    : null
  formData.description = ''
  formData.creditHours = 1
  formData.semester = ''
  formData.difficulty = null
  // 重置封面
  handleRemoveCover()
  dialogVisible.value = true
  fetchTeachers()
}

// 封面上传：选文件后本地预览（不立即上传）
const handleBeforeCoverUpload = (file) => {
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('封面文件不能超过 2MB')
    return false
  }
  if (!/^image\/(jpeg|jpg|png|gif)$/.test(file.type)) {
    ElMessage.error('仅支持 JPG/PNG/GIF 格式')
    return false
  }
  return true
}

const handleCoverChange = (file) => {
  if (file && file.raw) {
    coverFile.value = file.raw
    if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
    coverPreviewUrl.value = URL.createObjectURL(file.raw)
  }
}

const handleRemoveCover = () => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
  coverPreviewUrl.value = ''
  coverFile.value = null
  // 清空 el-upload 内部文件列表
  if (coverUploadRef.value) {
    coverUploadRef.value.clearFiles()
  }
}

const handleEdit = (row) => {
  router.push(`/courses/${row.id}/edit`)
}

const handleView = (row) => {
  router.push(`/courses/${row.id}`)
}

const handleApprove = async (row) => {
  try { await ElMessageBox.confirm('确定审核通过该课程?', '提示', { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await approveCourse(row.id); ElMessage.success('审核通过成功'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '审核通过失败') }
  finally { actingId.value = null }
}

const handleReject = async (row) => {
  let value
  try {
    const res = await ElMessageBox.prompt('请输入驳回原因（选填）', '驳回课程', {
      confirmButtonText: '确定驳回', cancelButtonText: '取消',
      inputType: 'textarea', inputPlaceholder: '请填写驳回原因，以便教师修改后重新提交'
    })
    value = res.value
  } catch { return }
  actingId.value = row.id
  try { await rejectCourse(row.id, value || ''); ElMessage.success('驳回成功'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '驳回失败') }
  finally { actingId.value = null }
}

const handlePublish = async (row) => {
  try { await ElMessageBox.confirm('确定发布该课程?', '提示', { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await publishCourse(row.id); ElMessage.success('发布成功'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '发布失败') }
  finally { actingId.value = null }
}

const handleUnpublish = async (row) => {
  try { await ElMessageBox.confirm('确定下架该课程?', '提示', { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await unpublishCourse(row.id); ElMessage.success('下架成功'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '下架失败') }
  finally { actingId.value = null }
}

const handleDelete = async (row) => {
  try { await ElMessageBox.confirm('确定删除该课程?', '提示', { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await deleteCourse(row.id); ElMessage.success('删除成功'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '删除失败') }
  finally { actingId.value = null }
}

const handleCopy = async (row) => {
  actingId.value = row.id
  try {
    const { data } = await copyCourse(row.id)
    const newId = data?.id || data
    ElMessage.success('复制成功，即将跳转到编辑页面')
    router.push(`/courses/${newId}/edit`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '复制失败')
  } finally { actingId.value = null }
}

const handleExport = async () => {
  if (tableData.value.length === 0) {
    ElMessage.warning('无可导出数据')
    return
  }
  // P2: 导出数量限制，最多 5000 条
  if (totalElements.value > 5000) {
    try {
      await ElMessageBox.confirm(`当前共 ${totalElements.value} 条数据，仅导出前 5000 条，继续？`, '提示', { type: 'warning' })
    } catch { return }
  }
  try {
    ElMessage.info('正在获取全部数据，请稍候…')
    // P2-12: 导出全量筛选结果而非当前页，保持筛选条件不变，size 设为 5000(上限)
    const exportParams = {
      page: 0,
      size: 5000,
      keyword: searchForm.keyword || undefined,
      categoryId: searchForm.categoryId || undefined,
      teacherName: searchForm.teacherName || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined,
      courseType: searchForm.courseType !== '' ? searchForm.courseType : undefined,
      teacherId: userStore.role === 'TEACHER' ? userStore.userId : null
    }
    const { data } = await getCourses(exportParams)
    const allData = data.items || []
    const exportData = allData.map((item, index) => ({
      '序号': index + 1,
      '标题': item.title || '',
      '类型': item.courseType === 'VIDEO' ? '视频' : item.courseType === 'INTERACTIVE' ? '互动' : '线下',
      '分类': item.categoryName || '',
      '教师': item.teacherName || '',
      '学员数': item.studentCount || 0,
      '状态': getStatusLabel(item.status)
    }))
    const ws = XLSX.utils.json_to_sheet(exportData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '课程列表')
    XLSX.writeFile(wb, `课程导出_${Date.now()}.xlsx`)
    ElMessage.success(`导出成功，共 ${exportData.length} 条`)
  } catch {
    ElMessage.error('导出失败')
  }
}

function getStatusLabel(status) {
  const map = { 0: '草稿', 1: '待审核', 2: '通过', 3: '驳回', 4: '已发布', 5: '下架', 6: '归档' }
  return map[status] || '未知'
}

const goSlides = (row) => {
  router.push(`/teacher/courses/${row.id}/slides/manage`)
}
const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    const valid = await formRef.value.validate()
    if (!valid) return
  } catch {
    return
  }
  submitLoading.value = true
  try {
    const res = await createCourse({
      title: formData.title,
      categoryId: formData.categoryId,
      teacherId: formData.teacherId,
      subtitle: formData.subtitle || '',
      summary: formData.summary || '',
      description: formData.description || '',
      coverUrl: formData.coverUrl || '',
      semester: formData.semester || '',
      difficulty: formData.difficulty,
      courseType: formData.courseType || 'VIDEO',
      creditHours: formData.creditHours || 0,
      price: formData.price || 0,
      freeAccessScope: formData.freeAccessScope,
      freeDeptIds: formData.freeDeptIds,
      discountScope: formData.discountScope,
      discountPercent: formData.discountPercent
    })
    const newCourseId = res?.data?.id
    if (newCourseId && coverFile.value) {
      try {
        await updateCourseCover(newCourseId, coverFile.value)
        ElMessage.success('创建成功，封面已上传')
      } catch {
        ElMessage.warning('课程已创建，但封面上传失败，请稍后到编辑页重试')
      }
    } else {
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  handleRemoveCover()
}
// 线下课新增安排
const showOfflineDialog = ref(false)
const offlineSubmitting = ref(false)
const offlineFormRef = ref(null)
const offlineChapterOptions = ref([])
const courseOptions = ref([])  // 线下课课程选择器
const offlineForm = reactive({
  courseId: null, chapterId: null, sessionDate: '', startTime: '', endTime: '', location: '', teacherNotes: ''
})
const offlineRules = {
  chapterId: [{ required: true, message: '请选择章节', trigger: 'change' }],
  sessionDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }],
}
async function onOfflineCourseChange(courseId) {
  offlineForm.chapterId = null
  if (!courseId) { offlineChapterOptions.value = []; return }
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    offlineChapterOptions.value = (data?.items || []).filter(ch => ch.chapterType === 'OFFLINE')
  } catch { offlineChapterOptions.value = [] }
}
function resetOfflineForm() {
  offlineForm.courseId = null; offlineForm.chapterId = null; offlineForm.sessionDate = ''
  offlineForm.startTime = ''; offlineForm.endTime = ''; offlineForm.location = ''; offlineForm.teacherNotes = ''
  offlineChapterOptions.value = []
  offlineFormRef.value?.resetFields()
  // 加载课程列表
  getCourses({ size: 200, courseType: 'OFFLINE' }).then(({ data }) => {
    courseOptions.value = data?.items || []
  }).catch(() => {})
}
async function submitOffline() {
  if (!offlineFormRef.value) return
  try { const v = await offlineFormRef.value.validate(); if (!v) return } catch { return }
  if (!offlineForm.chapterId) { ElMessage.warning('请选择章节'); return }
  offlineSubmitting.value = true
  try {
    await createOfflineSession(offlineForm.chapterId, {
      sessionDate: offlineForm.sessionDate,
      startTime: offlineForm.startTime,
      endTime: offlineForm.endTime,
      location: offlineForm.location,
      teacherNotes: offlineForm.teacherNotes || undefined,
      sortOrder: 1
    })
    ElMessage.success('线下安排已创建')
    showOfflineDialog.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    offlineSubmitting.value = false
  }
}

onMounted(() => {
  fetchCategories()
  fetchData()
})
</script>

<style scoped>
.course-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.cover-preview-wrap { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.cover-preview-wrap img { max-width: 150px; max-height: 80px; border-radius: 4px; border: 1px solid #ebeef5; object-fit: cover; }
.cover-actions { display: flex; gap: 4px; }

.quill-editor-wrapper {
  width: 100%;
  border-radius: 4px;
}

.quill-editor-wrapper :deep(.ql-toolbar) {
  border-radius: 4px 4px 0 0;
  background: #fafafa;
}

.quill-editor-wrapper :deep(.ql-container) {
  border-radius: 0 0 4px 4px;
  font-size: 14px;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

.filter-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.table-thumb {
  width: 48px;
  height: 32px;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.no-thumb {
  color: var(--el-text-color-placeholder);
}

.review-hint {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
  line-height: 1.2;
}

.filter-input-w160 {
  width: 160px;
}

.search-input,
.filter-input {
  width: 160px;
  border-radius: var(--radius-md);
}

.search-select,
.filter-select {
  width: 160px;
}

.filter-input-w120 {
  width: 120px;
}

.full-width {
  width: 100%;
}

/* Button border-radius */
:deep(.el-button) {
  border-radius: var(--radius-md);
}

/* Dialog border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .course-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }
}
</style>