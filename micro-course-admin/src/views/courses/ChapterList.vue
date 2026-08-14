<!--
  章节列表
  路由路径: /courses/:courseId/chapters
  Phase 1
  Author: jackie
-->
<template>
  <div class="chapter-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('course.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.chapterMgmt') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('course.title')">
          <el-select v-model="searchForm.courseId" :placeholder="$t('videoList.selectCourse')" clearable class="filter-input-w240" :disabled="courseOptions.length <= 1" @change="handleSearch" :aria-label="$t('course.title')">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentCourse">
          <el-button @click="goBackToCourse">{{ $t('chapterList.backToCourseDetail') }}</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('videoList.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <span v-if="currentCourse">{{ $t('chapterList.titleWithCourse', { title: currentCourse.title }) }}</span>
            <span v-else>{{ $t('course.chapterMgmt') }}</span>
          </span>
          <el-button type="primary" :disabled="!searchForm.courseId" v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" @click="handleCreate">{{ $t('course.addChapter') }}</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="!searchForm.courseId" :description="$t('chapterList.selectCourseFirst')" />
      <el-empty v-else-if="tableData.length === 0" :description="$t('chapterList.noData')" />
      <el-table v-else :data="tableData" stripe border class="data-table" row-key="id">
        <el-table-column type="expand" width="40" :label="$t('chapterList.expand')">
          <template #default="{ row }">
            <div style="padding:12px 24px 12px 48px;background:var(--el-fill-color-lighter)">
              <div v-loading="sectionLoading[row.id]">
                <SectionList
                  :sections="sectionsByChapterId[row.id] || []"
                  @upload="(s) => handleSectionUpload(row, s)"
                  @edit="(s) => handleEditSection(row, s)"
                  @delete="(s) => handleDeleteSection(row, s)"
                />
                <div style="margin-top:8px">
                  <el-button size="small" type="primary" plain @click.stop="handleAddSection(row)">{{ $t('course.addSection') }}</el-button>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="sortOrder" :label="$t('course.sortOrder')" width="80" align="center" />
        <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="$t('course.sectionCount')" width="80" align="center">
          <template #default="{ row }">{{ (sectionsByChapterId[row.id] || []).length }}</template>
        </el-table-column>
        <el-table-column prop="duration" :label="$t('course.duration')" width="100" align="center">
          <template #default="{ row }">
            {{ row.duration ? $t('chapterList.minutes', { count: row.duration }) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('chapterList.description')" min-width="150" show-overflow-tooltip />
        <el-table-column :label="$t('app.operation')" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="!loading && tableData.length > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
        <div class="page-size-wrap">
          <label for="ch-page-size" class="sr-only">{{ $t('course.perPage') }}</label>
          <el-select id="ch-page-size" :model-value="size" class="page-size-select" @change="v => { size = v; handleSizeChange() }" :aria-label="$t('course.perPage')">
            <el-option v-for="s in [10, 20, 50, 100]" :key="s" :label="$t('course.perPageOption', { count: s })" :value="s" />
          </el-select>
        </div>
      </div>
    </el-card>

    <SectionEditDialog
      v-model="showSectionDialog"
      :section="editingSection"
      :is-edit="isEditSection"
      :loading="sectionSubmitLoading"
      @submit="handleSubmitSection"
    />

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item :label="$t('course.title')" prop="courseId">
          <el-select v-model="formData.courseId" @change="onCourseChange" :placeholder="$t('videoList.selectCourse')" class="full-width" :aria-label="$t('course.title')">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('chapterList.chapterTitle')" prop="title">
          <el-input v-model="formData.title" :placeholder="$t('chapterList.chapterTitlePlaceholder')" :aria-label="$t('chapterList.chapterTitle')" />
        </el-form-item>
        <div class="form-tip" style="margin-bottom:12px;color:var(--el-color-info);font-size:12px">
          {{ $t('course.chapterTypeHint') }}
        </div>
        <el-form-item :label="$t('course.sortOrder')" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" :aria-label="$t('course.sortOrder')" />
        </el-form-item>
        <el-form-item :label="$t('chapterList.durationLabel')" prop="duration">
          <el-input-number v-model="formData.duration" :min="0" class="full-width" :aria-label="$t('course.duration')" />
        </el-form-item>
        <el-form-item :label="$t('chapterList.description')" prop="description">
          <el-input v-model="formData.description" type="textarea" :placeholder="$t('chapterList.descriptionPlaceholder')" :rows="3" :aria-label="$t('chapterList.description')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getChapters, createChapter, updateChapter, deleteChapter } from '@/api/chapter'
import { getCourses } from '@/api/course'
import { listSections, createSection, updateSection, deleteSection } from '@/api/section'
import { fetchAllPages } from '@/utils/fetchAllPages'
import SectionList from '@/components/course/SectionList.vue'
import SectionEditDialog from '@/components/course/SectionEditDialog.vue'

const { t } = useI18n()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  courseId: null
})

const dialogVisible = ref(false)
const dialogTitle = ref(t('course.addChapter'))
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)
const courseOptions = ref([])
const currentCourse = computed(() => courseOptions.value.find(c => c.id === searchForm.courseId) || null)
const router = useRouter()

const sectionsByChapterId = reactive({})
const sectionLoading = reactive({})
const showSectionDialog = ref(false)
const editingSection = ref(null)
const isEditSection = ref(false)
const sectionSubmitLoading = ref(false)
const currentChapterForSection = ref(null)

const fetchSections = async (chapterId) => {
  if (!searchForm.courseId || !chapterId) return
  sectionLoading[chapterId] = true
  try {
    // R11 后端 size 上限 200：size=999 会 400，改为分页循环拉全量
    sectionsByChapterId[chapterId] = await fetchAllPages(
      (params) => listSections(searchForm.courseId, chapterId, params),
      { page: 0 },
      200
    )
  } catch {
    sectionsByChapterId[chapterId] = []
  } finally {
    sectionLoading[chapterId] = false
  }
}

const fetchAllSections = () => {
  tableData.value.forEach(ch => fetchSections(ch.id))
}

const handleAddSection = (chapter) => {
  currentChapterForSection.value = chapter
  editingSection.value = null
  isEditSection.value = false
  showSectionDialog.value = true
}

const handleEditSection = (chapter, section) => {
  currentChapterForSection.value = chapter
  editingSection.value = { ...section }
  isEditSection.value = true
  showSectionDialog.value = true
}

const handleSectionUpload = (chapter, section) => {
  // 【D-3 修复】SectionList「课件」按钮 @upload 死按钮 → 打开该课时的课件管理（上传/编辑入口）
  if (!searchForm.courseId) return
  router.push({ path: `/teacher/courses/${searchForm.courseId}/slides/manage`, query: { sectionId: section.id } })
}

const handleDeleteSection = async (chapter, section) => {
  try {
    await ElMessageBox.confirm(t('course.confirmDeleteSection', { title: section.title }), t('course.hintTitle'), { type: 'warning' })
    await deleteSection(searchForm.courseId, chapter.id, section.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchSections(chapter.id)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(t('course.deleteFailed'))
  }
}

const handleSubmitSection = async (formData) => {
  if (!currentChapterForSection.value) return
  sectionSubmitLoading.value = true
  try {
    const ch = currentChapterForSection.value
    if (isEditSection.value) {
      await updateSection(searchForm.courseId, ch.id, editingSection.value.id, formData)
      ElMessage.success(t('course.updateSuccess'))
    } else {
      await createSection(searchForm.courseId, ch.id, formData)
      ElMessage.success(t('course.createSuccess'))
    }
    showSectionDialog.value = false
    fetchSections(ch.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('course.operationFailed'))
  } finally {
    sectionSubmitLoading.value = false
  }
}

const formData = reactive({
  courseId: null,
  title: '',
  sortOrder: 0,
  duration: 0,
  description: ''
})
const formRules = {
  courseId: [{ required: true, message: t('videoList.selectCourse'), trigger: 'change' }],
  title: [{ required: true, message: t('chapterList.chapterTitlePlaceholder'), trigger: 'blur' }],
  sortOrder: [{ required: true, message: t('chapterList.sortOrderRequired'), trigger: 'blur' }, { type: 'number', min: 0, message: t('chapterList.minZero'), trigger: 'blur' }]
}

const fetchData = async () => {
  if (!searchForm.courseId) {
    tableData.value = []
    totalElements.value = 0
    return
  }
  loading.value = true
  try {
    const params = {
      courseId: searchForm.courseId,
      page: page.value - 1,
      size: size.value
    }
    const { data } = await getChapters(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
    fetchAllSections()
  } catch {
    ElMessage.error(t('chapterList.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

 const fetchCourseOptions = async () => {
  try {
    const params = { page: 0, size: 1000 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
    if (!searchForm.courseId && courseOptions.value.length === 1) {
      searchForm.courseId = courseOptions.value[0].id
      handleSearch()
    }
  } catch {
    ElMessage.error(t('course.fetchCoursesFailed'))
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = null
  page.value = 1
  tableData.value = []
  totalElements.value = 0
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const goBackToCourse = () => {
  if (searchForm.courseId) router.push(`/teacher/courses/${searchForm.courseId}`)
}
const handleCreate = () => {
  dialogTitle.value = t('course.addChapter')
  isEdit.value = false
  currentId.value = null
  formData.courseId = searchForm.courseId
  formData.title = ''
  formData.sortOrder = 0
  formData.duration = 0
  formData.description = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = t('course.chapterEdit')
  isEdit.value = true
  currentId.value = row.id
  formData.courseId = row.courseId
  formData.title = row.title
  formData.sortOrder = row.sortOrder
  formData.duration = row.duration || 0
  formData.description = row.description || ''
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('chapterList.confirmDeleteChapter'), t('course.hintTitle'), { type: 'warning' })
    await deleteChapter(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('course.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value) return
  submitLoading.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { submitLoading.value = false; return }
  } catch { submitLoading.value = false; return }
  try {
    if (isEdit.value) {
      await updateChapter(currentId.value, formData)
      ElMessage.success(t('chapterList.editSuccess'))
    } else {
      await createChapter(formData)
      ElMessage.success(t('course.createSuccess'))
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || (isEdit.value ? t('chapterList.editFailed') : t('course.createFailed')))
  } finally {
    submitLoading.value = false
  }
}

const onCourseChange = (val) => {
  // 课程切换—预留联动逻辑
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchCourseOptions()
})
</script>

<style scoped>
.chapter-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
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
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.full-width {
  width: 100%;
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

.filter-input-w240 {
  width: 240px;
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .chapter-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .filter-input-w240 {
    width: 100%;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>
