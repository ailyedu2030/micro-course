<!--
  微专业课程编排（教师端）
  路由: /teacher/micro-specialties/:id/courses
-->
<template>
  <div class="ms-course-edit">
    <el-page-header @back="$router.back()" :content="$i18nT('microSpecialtyCourseEdit.pageHeader', { title: detail?.title || '' })" class="mg-bottom-16" />

    <div v-loading="loading">
      <el-result
        v-if="error"
        icon="error"
        :title="$i18nT('microSpecialtyCourseEdit.loadFailed')"
        :sub-title="$i18nT('microSpecialtyCourseEdit.loadFailedSubtitle')"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $i18nT('common.retry') }}</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && !detail" :description="$i18nT('microSpecialtyCourseEdit.notFound')" />

      <div v-if="detail">
        <el-card shadow="never" class="mg-bottom-16" v-loading="coursesLoading">
          <template #header>
            <div class="card-header">
              <span>{{ $i18nT('microSpecialtyCourseEdit.courseList', { count: courses.length }) }}</span>
              <el-button type="primary" size="small" @click="showAddDialog">{{ $i18nT('microSpecialtyCourseEdit.addCourse') }}</el-button>
            </div>
          </template>
          <el-table :data="courses" stripe border :empty-text="$i18nT('microSpecialtyCourseEdit.noCourses')">
            <el-table-column prop="sortOrder" :label="$i18nT('microSpecialtyCourseEdit.sortOrder')" width="70" align="center" />
            <el-table-column prop="courseTitle" :label="$i18nT('microSpecialtyCourseEdit.courseName')" min-width="200" show-overflow-tooltip />
            <el-table-column :label="$i18nT('microSpecialtyCourseEdit.teacher')" width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="getTeacherName(row.courseId)">{{ getTeacherName(row.courseId) }}</span>
                <span v-else class="no-teacher">{{ $i18nT('microSpecialtyCourseEdit.notAssigned') }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$i18nT('microSpecialtyCourseEdit.requiredElective')" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isRequired" type="danger" size="small">{{ $i18nT('microSpecialtyCourseEdit.required') }}</el-tag>
                <el-tag v-else type="info" size="small">{{ $i18nT('microSpecialtyCourseEdit.elective') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="credits" :label="$i18nT('microSpecialtyCourseEdit.credits')" width="80" align="center" />
            <el-table-column prop="hours" :label="$i18nT('microSpecialtyCourseEdit.hours')" width="80" align="center" />
            <el-table-column prop="minScore" :label="$i18nT('microSpecialtyCourseEdit.passScore')" width="90" align="center" />
            <el-table-column :label="$i18nT('app.operation')" width="200" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="showEditItem(row)">{{ $i18nT('app.edit') }}</el-button>
                <el-button size="small" @click="showAssignTeacher(row)">{{ $i18nT('microSpecialtyCourseEdit.assignTeacher') }}</el-button>
                <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" size="small" type="danger" @click="handleRemove(row)">{{ $i18nT('app.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <!-- 添加课程 Dialog -->
    <el-dialog v-model="addVisible" :title="$i18nT('microSpecialtyCourseEdit.addTitle')" width="560px" @closed="resetAddForm">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px" @submit.prevent>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.selectCourse')" prop="courseId">
          <el-select v-model="addForm.courseId" filterable :placeholder="$i18nT('microSpecialtyCourseEdit.searchCourse')" class="full-width">
            <el-option v-for="c in availableCourses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.sortOrder')">
          <el-input-number v-model="addForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.required')">
          <el-switch v-model="addForm.isRequired" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.credits')">
          <el-input-number v-model="addForm.credits" :min="0" :precision="1" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.hours')">
          <el-input-number v-model="addForm.hours" :min="0" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.passScore')">
          <el-input-number v-model="addForm.minScore" :min="0" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">{{ $i18nT('common.cancel') }}</el-button>
        <el-button type="primary" :loading="adding" :disabled="adding" @click="handleAdd">{{ $i18nT('microSpecialtyCourseEdit.addBtn') }}</el-button>
      </template>
    </el-dialog>

    <!-- 指派教师 Dialog -->
    <el-dialog v-model="assignVisible" :title="$i18nT('microSpecialtyCourseEdit.assignTitle')" width="480px" @closed="resetAssignForm">
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="100px">
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.course')">
          <span class="assign-course-name">{{ assignCourse?.courseTitle || '' }}</span>
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.teacher')" prop="teacherId">
          <el-select v-model="assignForm.teacherId" filterable :placeholder="$i18nT('microSpecialtyCourseEdit.selectTeacher')" class="full-width" clearable>
            <el-option v-for="t in availableTeachers" :key="t.teacherId" :label="t.teacherName" :value="t.teacherId" />
          </el-select>
        </el-form-item>
        <div class="assign-hint">{{ $i18nT('microSpecialtyCourseEdit.assignHint') }}</div>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">{{ $i18nT('common.cancel') }}</el-button>
        <el-button type="primary" :loading="assigning" :disabled="assigning" @click="handleAssignTeacher">{{ $i18nT('app.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑课程项 Dialog -->
    <el-dialog v-model="editVisible" :title="$i18nT('microSpecialtyCourseEdit.editTitle')" width="480px" @closed="resetEditForm">
      <el-form ref="editFormRef" :model="editForm" label-width="100px" @submit.prevent>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.sortOrder')">
          <el-input-number v-model="editForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.required')">
          <el-switch v-model="editForm.isRequired" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.credits')">
          <el-input-number v-model="editForm.credits" :min="0" :precision="1" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.hours')">
          <el-input-number v-model="editForm.hours" :min="0" />
        </el-form-item>
        <el-form-item :label="$i18nT('microSpecialtyCourseEdit.passScore')">
          <el-input-number v-model="editForm.minScore" :min="0" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ $i18nT('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editing" :disabled="editing" @click="handleEditSave">{{ $i18nT('app.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyDetail, getCourses, addCourse, updateCourseItem, removeCourse, getTeachers, assignTeacherToCourse } from '@/api/microSpecialty'
import { getCourses as getAllCourses } from '@/api/course'

const { t: i18nT } = useI18n()
const route = useRoute()
// P1-C 修复 (2026-08-04): userRole 未定义 → 课程编排删除按钮隐藏，负责人无法移除课程
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const msId = computed(() => route.params.id)
const loading = ref(true)
const error = ref(false)
const coursesLoading = ref(false)
const detail = ref(null)
const courses = ref([])

const addVisible = ref(false)
const adding = ref(false)
const addFormRef = ref(null)
const addForm = ref({ courseId: null, sortOrder: 0, isRequired: true, credits: 2, hours: 32, minScore: 60 })
const addRules = { courseId: [{ required: true, message: i18nT('microSpecialtyCourseEdit.selectCourseRequired'), trigger: 'change' }] }
const availableCourses = ref([])

const editVisible = ref(false)
const editing = ref(false)
const editFormRef = ref(null)
const editForm = ref({})
const editingItem = ref(null)

// 指派教师
const assignVisible = ref(false)
const assigning = ref(false)
const assignCourse = ref(null)
const assignForm = ref({ teacherId: null })
const teachers = ref([])

function getTeacherName(courseId) {
  const t = teachers.value.find(t => t.courseId === Number(courseId) && t.inviteStatus === 'ACTIVE')
  return t?.teacherName || null
}

const fetchData = async () => {
  error.value = false
  loading.value = true
  coursesLoading.value = true
  try {
    const [dRes, cRes, tRes] = await Promise.all([
      getMicroSpecialtyDetail(msId.value),
      getCourses(msId.value),
      getTeachers(msId.value)
    ])
    detail.value = dRes.data
    courses.value = (cRes.data?.items || cRes.data || [])
    const rawTeachers = tRes.data?.items || tRes.data || []
    teachers.value = rawTeachers
  } catch { error.value = true }
  finally { loading.value = false; coursesLoading.value = false }
}

const showAddDialog = async () => {
  const maxOrder = courses.value.length > 0 ? Math.max(...courses.value.map(c => c.sortOrder || 0)) : 0
  addForm.value = { courseId: null, sortOrder: maxOrder + 1, isRequired: true, credits: 2, hours: 32, minScore: 60 }
  try {
    const { data } = await getAllCourses({ page: 0, size: 1000 })
    availableCourses.value = data?.items || data || []
  } catch (e) {
    availableCourses.value = []
    ElMessage.error(i18nT('microSpecialtyCourseEdit.fetchCoursesFailed'))
  }
  addVisible.value = true
}
const resetAddForm = () => { addFormRef.value?.clearValidate() }

const handleAdd = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (adding.value) return
  if (!addFormRef.value) return
  adding.value = true
  try {
    const valid = await addFormRef.value.validate()
    if (!valid) { adding.value = false; return }
  } catch { adding.value = false; return }
  try { await addCourse(msId.value, addForm.value); ElMessage.success(i18nT('microSpecialtyCourseEdit.addSuccess')); addVisible.value = false; fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || i18nT('microSpecialtyCourseEdit.addFailed')) }
  finally { adding.value = false }
}

const showEditItem = (row) => {
  editingItem.value = row
  editForm.value = { sortOrder: row.sortOrder, isRequired: row.isRequired, credits: row.credits, hours: row.hours, minScore: row.minScore }
  editVisible.value = true
}
const resetEditForm = () => { editFormRef.value?.clearValidate() }

const handleEditSave = async () => {
  editing.value = true
  try { await updateCourseItem(msId.value, editingItem.value.id, editForm.value); ElMessage.success(i18nT('app.saveSuccess')); editVisible.value = false; fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || i18nT('microSpecialtyCourseEdit.saveFailed')) }
  finally { editing.value = false }
}

const handleRemove = async (row) => {
  try { await ElMessageBox.confirm(i18nT('microSpecialtyCourseEdit.confirmRemove', { title: row.courseTitle }), i18nT('app.confirm'), { type: 'warning' }) }
  catch { return }
  try { await removeCourse(msId.value, row.id); ElMessage.success(i18nT('microSpecialtyCourseEdit.removedSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || i18nT('microSpecialtyCourseEdit.removeFailed')) }
}

// 指派教师
const assignFormRef = ref(null)
const assignRules = { teacherId: [{ required: true, message: i18nT('microSpecialtyCourseEdit.selectTeacherRequired'), trigger: 'change' }] }

const availableTeachers = computed(() =>
  teachers.value.filter(t => t.inviteStatus === 'ACTIVE' && t.role !== 'LEAD')
)

function showAssignTeacher(row) {
  assignCourse.value = row
  const existing = teachers.value.find(t => t.courseId === Number(row.courseId) && t.inviteStatus === 'ACTIVE')
  assignForm.value = { teacherId: existing?.teacherId || null }
  assignVisible.value = true
}
function resetAssignForm() {
  assignFormRef.value?.clearValidate()
  assignCourse.value = null
  assignForm.value = { teacherId: null }
}

const handleAssignTeacher = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (assigning.value) return
  if (!assignFormRef.value) return
  assigning.value = true
  try {
    const valid = await assignFormRef.value.validate()
    if (!valid) { assigning.value = false; return }
  } catch { assigning.value = false; return }
  try {
    await assignTeacherToCourse(msId.value, assignForm.value.teacherId, Number(assignCourse.value.courseId))
    ElMessage.success(i18nT('microSpecialtyCourseEdit.assignSuccess'))
    assignVisible.value = false
    fetchData()
  } catch (e) { ElMessage.error(e?.response?.data?.message || i18nT('microSpecialtyCourseEdit.assignFailed')) }
  finally { assigning.value = false }
}

onMounted(fetchData)
</script>

<style scoped>
.ms-course-edit { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.full-width { width: 100%; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.no-teacher { color: var(--el-text-color-placeholder); font-size: var(--text-sm); }
.assign-course-name { font-weight: var(--weight-medium); }
.assign-hint { margin: var(--space-2) 0 0; font-size: var(--text-xs); color: var(--el-text-color-secondary); padding-left: 100px; }
</style>
