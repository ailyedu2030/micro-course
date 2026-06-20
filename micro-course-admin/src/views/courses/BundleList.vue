<template>
  <div class="bundle-list">
    <el-page-header @back="$router.push('/courses')" content="课程套件管理" />

    <el-card shadow="never" class="mg-top-16">
      <template #header>
        <div class="card-header">
          <span>套件列表</span>
          <el-button type="primary" size="small" @click="showCreateDialog">创建套件</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="bundles" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="creatorName" label="创建者" width="120" />
        <el-table-column label="价格" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.price" style="color:#f56c6c">¥{{ row.price }}</span>
            <span v-else style="color:#67c23a">免费</span>
          </template>
        </el-table-column>
        <el-table-column prop="studentCount" label="学习人数" width="100" align="center" />
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">管理子课</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination mg-top-12">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchBundles"
        />
      </div>
    </el-card>

    <!-- 创建/编辑套件 -->
    <el-dialog v-model="dialogVisible" :title="editingBundle ? '编辑套件' : '创建套件'" width="500px" @closed="resetForm">
      <el-form :model="formData" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="formData.title" placeholder="如：英语四级通关" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="价格(¥)">
          <el-input-number v-model="formData.price" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 子课管理 -->
    <el-dialog v-model="itemDialog" :title="'管理子课 · ' + (currentBundle?.title || '')" width="700px">
      <el-table :data="bundleItems" border stripe>
        <el-table-column prop="sortOrder" label="顺序" width="60" />
        <el-table-column prop="courseTitle" label="课程名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.courseType === 'INTERACTIVE'" type="success" size="small">互动</el-tag>
            <el-tag v-else type="primary" size="small">视频</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" label="教师" width="100" />
        <el-table-column label="必修" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isRequired" type="danger" size="small">必</el-tag>
            <el-tag v-else type="info" size="small">选</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleRemoveItem(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="add-course-section mg-top-12">
        <el-select v-model="selectedCourseId" filterable placeholder="搜索并添加课程" class="course-select" clearable>
          <el-option v-for="c in availableCourses" :key="c.id" :label="c.title" :value="c.id" />
        </el-select>
        <el-input-number v-model="newSortOrder" :min="0" placeholder="顺序" class="sort-input" />
        <el-checkbox v-model="newIsRequired" class="req-check">必修</el-checkbox>
        <el-button type="primary" size="small" :disabled="!selectedCourseId" @click="handleAddItem">添加</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBundles, getBundleById, createBundle, addBundleCourse, removeBundleCourse, deleteBundle } from '@/api/bundle'
import { getCourses } from '@/api/course'

const loading = ref(false)
const saving = ref(false)
const bundles = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const dialogVisible = ref(false)
const editingBundle = ref(null)
const formData = ref({ title: '', description: '', price: null })

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
    const { data } = await getBundles({ page: page.value, size: size.value })
    bundles.value = data.items || []
    total.value = data.totalElements || 0
  } catch { ElMessage.error('加载套件列表失败') }
  finally { loading.value = false }
}

const showCreateDialog = () => {
  editingBundle.value = null
  formData.value = { title: '', description: '', price: null }
  dialogVisible.value = true
}

const resetForm = () => {
  formData.value = { title: '', description: '', price: null }
  editingBundle.value = null
}

const handleSave = async () => {
  if (!formData.value.title) { ElMessage.warning('请输入套件名称'); return }
  saving.value = true
  try {
    await createBundle({ title: formData.value.title, description: formData.value.description, price: formData.value.price, isFree: !formData.value.price })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchBundles()
  } catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该套件?', '提示', { type: 'warning' })
    await deleteBundle(row.id)
    ElMessage.success('删除成功')
    fetchBundles()
  } catch { /* cancelled */ }
}

const openDetail = async (row) => {
  currentBundle.value = row
  try {
    const { data } = await getBundleById(row.id)
    bundleItems.value = data.items || []
    const { data: coursesData } = await getCourses({ size: 200 })
    const existingIds = new Set(bundleItems.value.map(i => i.courseId))
    availableCourses.value = (coursesData.items || []).filter(c => !existingIds.has(c.id))
    itemDialog.value = true
  } catch { ElMessage.error('加载子课失败') }
}

const handleAddItem = async () => {
  if (!currentBundle.value || !selectedCourseId.value) return
  try {
    await addBundleCourse(currentBundle.value.id, { courseId: selectedCourseId.value, sortOrder: newSortOrder.value, isRequired: newIsRequired.value })
    ElMessage.success('添加成功')
    await openDetail(currentBundle.value)
    selectedCourseId.value = null
    newSortOrder.value = (bundleItems.value.length || 0) + 1
  } catch { ElMessage.error('添加失败') }
}

const handleRemoveItem = async (row) => {
  try {
    await removeBundleCourse(currentBundle.value.id, row.id)
    ElMessage.success('移除成功')
    await openDetail(currentBundle.value)
  } catch { ElMessage.error('移除失败') }
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
</style>
