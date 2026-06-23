<!--
  微专业课程编排（教师端）
  路由: /teacher/micro-specialties/:id/courses
-->
<template>
  <div class="ms-course-edit">
    <el-page-header @back="$router.back()" :content="'课程编排 · ' + (detail?.title || '')" class="mg-bottom-16" />

    <div v-loading="loading">
      <el-result
        v-if="error"
        icon="error"
        title="加载失败"
        sub-title="请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">重试</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && !detail" description="微专业不存在" />

      <div v-if="detail">
        <el-card shadow="never" class="mg-bottom-16" v-loading="coursesLoading">
          <template #header>
            <div class="card-header">
              <span>课程列表（{{ courses.length }} 门）</span>
              <el-button type="primary" size="small" @click="showAddDialog">添加课程</el-button>
            </div>
          </template>
          <el-table :data="courses" stripe border>
            <template #empty><el-empty description="暂未编排课程" /></template>
            <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
            <el-table-column prop="courseTitle" label="课程名称" min-width="200" show-overflow-tooltip />
            <el-table-column label="必修/选修" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isRequired" type="danger" size="small">必修</el-tag>
                <el-tag v-else type="info" size="small">选修</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="credits" label="学分" width="80" align="center" />
            <el-table-column prop="hours" label="学时" width="80" align="center" />
            <el-table-column prop="minScore" label="通过分" width="90" align="center" />
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="showEditItem(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleRemove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <!-- 添加课程 Dialog -->
    <el-dialog v-model="addVisible" title="添加课程" width="560px" @closed="resetAddForm">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px" @submit.prevent>
        <el-form-item label="选择课程" prop="courseId">
          <el-select v-model="addForm.courseId" filterable placeholder="搜索课程" class="full-width">
            <el-option v-for="c in availableCourses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="addForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="必修">
          <el-switch v-model="addForm.isRequired" />
        </el-form-item>
        <el-form-item label="学分">
          <el-input-number v-model="addForm.credits" :min="0" :precision="1" />
        </el-form-item>
        <el-form-item label="学时">
          <el-input-number v-model="addForm.hours" :min="0" />
        </el-form-item>
        <el-form-item label="通过分">
          <el-input-number v-model="addForm.minScore" :min="0" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">添加</el-button>
      </template>
    </el-dialog>

    <!-- 编辑课程项 Dialog -->
    <el-dialog v-model="editVisible" title="编辑课程" width="480px" @closed="resetEditForm">
      <el-form ref="editFormRef" :model="editForm" label-width="100px" @submit.prevent>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="必修">
          <el-switch v-model="editForm.isRequired" />
        </el-form-item>
        <el-form-item label="学分">
          <el-input-number v-model="editForm.credits" :min="0" :precision="1" />
        </el-form-item>
        <el-form-item label="学时">
          <el-input-number v-model="editForm.hours" :min="0" />
        </el-form-item>
        <el-form-item label="通过分">
          <el-input-number v-model="editForm.minScore" :min="0" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyDetail, getCourses, addCourse, updateCourseItem, removeCourse } from '@/api/microSpecialty'

const route = useRoute()
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
const addRules = { courseId: [{ required: true, message: '请选择课程', trigger: 'change' }] }
const availableCourses = ref([])

const editVisible = ref(false)
const editing = ref(false)
const editFormRef = ref(null)
const editForm = ref({})
const editingItem = ref(null)

const fetchData = async () => {
  error.value = false
  loading.value = true
  coursesLoading.value = true
  try {
    const { data: d } = await getMicroSpecialtyDetail(msId.value)
    detail.value = d
    const { data: c } = await getCourses(msId.value)
    courses.value = c.items || c || []
  } catch { error.value = true }
  finally { loading.value = false; coursesLoading.value = false }
}

const showAddDialog = () => {
  const maxOrder = courses.value.length > 0 ? Math.max(...courses.value.map(c => c.sortOrder || 0)) : 0
  addForm.value = { courseId: null, sortOrder: maxOrder + 1, isRequired: true, credits: 2, hours: 32, minScore: 60 }
  availableCourses.value = []
  addVisible.value = true
}
const resetAddForm = () => { addFormRef.value?.clearValidate() }

const handleAdd = async () => {
  if (!addFormRef.value) return
  try { await addFormRef.value.validate() } catch { return }
  adding.value = true
  try { await addCourse(msId.value, addForm.value); ElMessage.success('添加成功'); addVisible.value = false; fetchData() }
  catch { ElMessage.error('添加失败') }
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
  try { await updateCourseItem(msId.value, editingItem.value.id, editForm.value); ElMessage.success('保存成功'); editVisible.value = false; fetchData() }
  catch { ElMessage.error('保存失败') }
  finally { editing.value = false }
}

const handleRemove = async (row) => {
  try { await ElMessageBox.confirm(`确定移除「${row.courseTitle}」？`, '确认', { type: 'warning' }) }
  catch { return }
  try { await removeCourse(msId.value, row.id); ElMessage.success('已移除'); fetchData() }
  catch { ElMessage.error('移除失败') }
}

onMounted(fetchData)
</script>

<style scoped>
.ms-course-edit { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.full-width { width: 100%; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
