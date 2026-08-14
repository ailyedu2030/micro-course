<template>
  <div class="bundle-list">
    <el-page-header @back="$router.push('/courses')" :content="$t('bundleList.title')" />

    <el-card shadow="never" class="mg-top-16">
      <template #header>
        <div class="card-header">
          <span>{{ $t('bundleList.list') }}</span>
          <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" size="small" :aria-label="$t('bundleList.create')" @click="showCreateDialog">{{ $t('bundleList.create') }}</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="bundles" stripe border>
        <template #empty><el-empty :description="$t('bundleList.empty')" /></template>
        <el-table-column prop="id" :label="$t('teachingClass.id')" width="80" />
        <el-table-column prop="title" :label="$t('bundleList.name')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="creatorName" :label="$t('bundleList.creator')" width="120" />
        <el-table-column :label="$t('course.priceLabelShort')" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.price" class="price-amount">¥{{ row.price }}</span>
            <span v-else class="price-free">{{ $t('app.free') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="studentCount" :label="$t('course.learnersCount')" width="100" align="center" />
        <el-table-column :label="$t('course.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">{{ $t('bundleList.published') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('course.draft') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">{{ $t('bundleList.manageItems') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" size="small" @click="showEditDialog(row)">{{ $t('app.edit') }}</el-button>
            <div v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" style="margin-top:4px">
              <el-button v-if="row.status === 1" size="small" type="warning" @click="handleUnpublish(row)">{{ $t('course.unpublish') }}</el-button>
              <el-button v-else size="small" type="success" @click="handlePublish(row)">{{ $t('bundleList.publish') }}</el-button>
            </div>
            <el-button v-if="(userRole === 'TEACHER' || userRole === 'ADMIN') && canDelete" size="small" type="danger" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination mg-top-12">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, prev, pager, next"
          @size-change="fetchBundles"
          @current-change="fetchBundles"
          :aria-label="$t('course.paginationAria')"
        />
        <div class="page-size-wrap">
          <label for="bundle-page-size" class="sr-only">{{ $t('course.perPage') }}</label>
          <el-select id="bundle-page-size" :model-value="size" class="page-size-select" @change="v => { size = v; fetchBundles() }" :aria-label="$t('course.perPage')">
            <el-option v-for="s in [10, 20, 50, 100]" :key="s" :label="$t('course.perPageOption', { count: s })" :value="s" />
          </el-select>
        </div>
      </div>
    </el-card>

    <!-- 创建/编辑套件 -->
    <el-dialog v-model="dialogVisible" :title="editingBundle ? $t('bundleList.edit') : $t('bundleList.create')" width="500px" @closed="resetForm">
      <el-form ref="bundleFormRef" :model="formData" :rules="bundleRules" label-width="80px">
        <el-form-item :label="$t('bundleList.name')" prop="title">
          <el-input v-model="formData.title" :placeholder="$t('bundleList.titlePlaceholder')" :aria-label="$t('bundleList.nameAria')" />
        </el-form-item>
        <el-form-item :label="$t('bundleList.description')">
          <el-input v-model="formData.description" type="textarea" :rows="2" :aria-label="$t('bundleList.descriptionAria')" />
        </el-form-item>
        <el-form-item :label="$t('bundleList.priceLabel')">
          <el-input-number v-model="formData.price" :min="0" :precision="2" :aria-label="$t('bundleList.priceAria')" />
          <span class="form-tip">{{ $t('bundleList.priceTip') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="handleSave">{{ $t('app.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 子课管理 -->
    <el-dialog v-model="itemDialog" :title="$t('bundleList.manageItemsTitle', { title: currentBundle?.title || '' })" width="700px">
      <el-table :data="bundleItems" border stripe>
        <el-table-column prop="sortOrder" :label="$t('bundleList.sortOrder')" width="60" />
        <el-table-column prop="courseTitle" :label="$t('course.courseName')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$t('app.type')" width="110">
          <template #default="{ row }">
            <el-tag v-if="isCoursewareCourseType(row.courseType)" type="success" size="small">{{ row.courseType === 'HTML_COURSEWARE' ? $t('course.typeHtmlCourseware') : $t('course.typePptCourseware') }}</el-tag>
            <el-tag v-else-if="row.courseType === 'OFFLINE'" type="info" size="small">{{ $t('exercise.typeOffline') }}</el-tag>
            <el-tag v-else type="primary" size="small">{{ $t('course.typeVideo') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" :label="$t('course.teacher')" width="100" />
        <el-table-column :label="$t('bundleList.required')" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isRequired" type="danger" size="small">{{ $t('bundleList.requiredShort') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('bundleList.electiveShort') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleRemoveItem(row)">{{ $t('teachingClass.remove') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="add-course-section mg-top-12">
        <el-select v-model="selectedCourseId" filterable :placeholder="$t('bundleList.searchAddCourse')" class="course-select" clearable :aria-label="$t('bundleList.searchAddCourse')">
          <el-option v-for="c in availableCourses" :key="c.id" :label="c.title" :value="c.id" />
        </el-select>
        <el-input-number v-model="newSortOrder" :min="0" :placeholder="$t('bundleList.sortOrder')" class="sort-input" />
        <el-checkbox v-model="newIsRequired" class="req-check">{{ $t('bundleList.required') }}</el-checkbox>
        <el-button type="primary" size="small" :disabled="!selectedCourseId" @click="handleAddItem">{{ $t('course.add') }}</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { isCoursewareCourseType } from '@/config/courseTypeConfig'
import { getBundles, getBundleById, createBundle, updateBundle, publishBundle, unpublishBundle, addBundleCourse, removeBundleCourse, deleteBundle } from '@/api/bundle'
import { getCourses } from '@/api/course'

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const userStore = useUserStore()
const bundles = ref([])
const page = ref(1)
const size = ref(20)
const totalElements = ref(0)

const userRole = computed(() => userStore.role)
const canManage = (row) => {
  // ADMIN 可管理所有，TEACHER 只可管理自己创建的
  return userStore.role === 'ADMIN' || (userStore.role === 'TEACHER' && row.creatorId === userStore.userId)
}
const canDelete = computed(() => userStore.role === 'ADMIN')

const dialogVisible = ref(false)
const editingBundle = ref(null)
const formData = ref({ title: '', description: '', price: null })
const bundleFormRef = ref(null)
const bundleRules = {
  title: [{ required: true, message: t('bundleList.titleRequired'), trigger: 'blur' }]
}

const itemDialog = ref(false)
const currentBundle = ref(null)
const bundleItems = ref([])
const selectedCourseId = ref(null)
const newSortOrder = ref(0)
const newIsRequired = ref(true)
const availableCourses = ref([])

const fetchBundles = async () => {
  loading.value = true
  try {
    const { data } = await getBundles({ page: page.value - 1, size: size.value })
    bundles.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('bundleList.fetchFailed')) }
  finally { loading.value = false }
}

const showCreateDialog = () => {
  editingBundle.value = null
  formData.value = { title: '', description: '', price: null }
  dialogVisible.value = true
}

const showEditDialog = async (row) => {
  // 先拉最新数据，再开 modal — 避免弹窗闪烁/旧数据先填充后被覆盖
  try {
    const { data } = await getBundleById(row.id)
    editingBundle.value = data
    formData.value = {
      title: data.title || '',
      description: data.description || '',
      price: data.price
    }
    dialogVisible.value = true
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('bundleList.fetchDetailFailed'))
  }
}

const resetForm = () => {
  formData.value = { title: '', description: '', price: null }
  editingBundle.value = null
  bundleFormRef.value?.clearValidate()
}

const handleSave = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (saving.value) return
  if (!bundleFormRef.value) return
  saving.value = true
  try {
    const valid = await bundleFormRef.value.validate()
    if (!valid) { saving.value = false; return }
  } catch { saving.value = false; return }
  try {
    if (editingBundle.value) {
      await updateBundle(editingBundle.value.id, { title: formData.value.title, description: formData.value.description, price: formData.value.price })
      ElMessage.success(t('course.updateSuccess'))
    } else {
      await createBundle({ title: formData.value.title, description: formData.value.description, price: formData.value.price })
      ElMessage.success(t('course.createSuccess'))
    }
    dialogVisible.value = false
    fetchBundles()
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.saveFailed')) }
  finally { saving.value = false }
}

const handlePublish = async (row) => {
  try {
    await ElMessageBox.confirm(t('bundleList.confirmPublish', { title: row.title }), t('course.hintTitle'), { type: 'info' })
  } catch { return }
  try {
    await publishBundle(row.id)
    ElMessage.success(t('bundleList.publishSuccess'))
    fetchBundles()
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('bundleList.publishFailed')) }
}

const handleUnpublish = async (row) => {
  try {
    await ElMessageBox.confirm(t('bundleList.confirmUnpublish', { title: row.title }), t('course.hintTitle'), { type: 'warning' })
  } catch { return }
  try {
    await unpublishBundle(row.id)
    ElMessage.success(t('bundleList.unpublishSuccess'))
    fetchBundles()
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('bundleList.unpublishFailed')) }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('bundleList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
  } catch { return }
  try {
    await deleteBundle(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchBundles()
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('course.deleteFailed')) }
}

const loadDetailData = async (bundleId) => {
  // 仅刷新数据，不重置 currentBundle（避免表单值丢失）
  const { data } = await getBundleById(bundleId)
  bundleItems.value = data.items || []
  const params = { size: 200 }
  if (userStore.role === 'TEACHER') params.teacherId = userStore.userId
  const { data: coursesData } = await getCourses(params)
  const existingIds = new Set(bundleItems.value.map(i => i.courseId))
  availableCourses.value = (coursesData.items || []).filter(c => !existingIds.has(c.id))
}

const openDetail = async (row) => {
  currentBundle.value = row
  try {
    await loadDetailData(row.id)
    itemDialog.value = true
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('bundleList.fetchItemsFailed')) }
}

const handleAddItem = async () => {
  if (!currentBundle.value || !selectedCourseId.value) return
  try {
    await addBundleCourse(currentBundle.value.id, { courseId: selectedCourseId.value, sortOrder: newSortOrder.value, isRequired: newIsRequired.value })
    ElMessage.success(t('bundleList.addSuccess'))
    await loadDetailData(currentBundle.value.id)
    selectedCourseId.value = null
    newSortOrder.value = (bundleItems.value.length || 0) + 1
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('bundleList.addFailed')) }
}

const handleRemoveItem = async (row) => {
  try {
    await ElMessageBox.confirm(t('bundleList.confirmRemoveItem', { title: row.courseTitle || row.courseId }), t('teachingClass.confirmRemoveTitle'), { type: 'warning' })
  } catch { return }
  try {
    await removeBundleCourse(currentBundle.value.id, row.id)
    ElMessage.success(t('teachingClass.removeSuccess'))
    await loadDetailData(currentBundle.value.id)
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('teachingClass.removeFailed')) }
}

onMounted(() => fetchBundles())
</script>

<style scoped>
.bundle-list { padding: var(--space-4); max-width: 1440px; margin: 0 auto; }
.mg-top-16 { margin-top: var(--space-4); }
.mg-top-12 { margin-top: var(--space-3); }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.full-width { width: 100%; }
.add-course-section { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-3); background: var(--el-fill-color-light); border-radius: var(--radius-md); }
.course-select { flex: 1; }
.sort-input { width: 80px; }
.req-check { margin: 0 var(--space-2); }
.pagination { display: flex; justify-content: flex-end; }
.price-amount { color: var(--el-color-danger); }
.price-free { color: var(--el-color-success); }
</style>
