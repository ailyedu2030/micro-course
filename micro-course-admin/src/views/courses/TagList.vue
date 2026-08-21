<!--
  标签列表
  路由路径: /courses/tags
  Phase 1
  Author: jackie
-->
<template>
  <div class="tag-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('course.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('route.TagList') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">{{ $t('tagList.title') }}</span>
        <el-button type="primary" v-if="userRole === 'ADMIN'" @click="handleCreate">{{ $t('tagList.create') }}</el-button>
      </div>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('tagList.noData')" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="name" :label="$t('tagList.name')" min-width="150" />
        <el-table-column prop="color" :label="$t('tagList.color')" width="120" align="center">
          <template #default="{ row }">
            <span class="color-swatch" :style="{ backgroundColor: row.color || '#409eff' }"></span>
            <span class="color-value">{{ row.color || '#409eff' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <!-- P1-2026-08-21: 后端 Tag POST/PUT/DELETE 仅 ADMIN，编辑/删除按钮补角色守卫（ACADEMIC 点击必 403） -->
            <el-button v-if="userRole === 'ADMIN'" type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="userRole === 'ADMIN'" type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="!loading && tableData.length > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </el-card>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="$t(dialogTitle)" width="500px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item :label="$t('tagList.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('tagList.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('tagList.color')" prop="color">
          <div class="color-picker-row">
            <el-color-picker v-model="formData.color" />
            <el-input v-model="formData.color" placeholder="#409eff" class="color-input" />
          </div>
        </el-form-item>
        <el-form-item :label="$t('tagList.description')" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" :placeholder="$t('tagList.descriptionPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getTags, createTag, updateTag, deleteTag } from '@/api/tag'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const { t } = useI18n()

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('tagList.create')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  name: '',
  color: '#409eff'
})

const formRules = {
  name: [{ required: true, message: t('tagList.nameRequired'), trigger: 'blur' }],
  color: [{ required: true, message: t('tagList.colorRequired'), trigger: 'change' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    const { data } = await getTags(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error(t('tagList.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleCreate = () => {
  dialogTitle.value = 'tagList.create'
  isEdit.value = false
  currentId.value = null
  formData.name = ''
  formData.color = '#409eff'
  formData.description = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = 'tagList.edit'
  isEdit.value = true
  currentId.value = row.id
  formData.name = row.name
  formData.color = row.color || '#409eff'
  formData.description = row.description || ''
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('tagList.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deleteTag(row.id)
    ElMessage.success(t('tagList.deleteSuccess'))
    fetchData()
  } catch (error) {
    if (!['cancel', 'close'].includes(error)) {
      ElMessage.error(t('tagList.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      if (isEdit.value) {
        await updateTag(currentId.value, formData)
        ElMessage.success(t('tagList.editSuccess'))
      } else {
        await createTag(formData)
        ElMessage.success(t('tagList.createSuccess'))
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? t('tagList.editFailed') : t('tagList.createFailed'))
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.tag-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.toolbar-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
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

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9) !important;
}

.color-swatch {
  display: inline-block;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-sm);
  vertical-align: middle;
  margin-right: var(--space-1);
}

.color-value {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  font-family: monospace;
}

.color-picker-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.color-input {
  width: 120px;
}

.full-width {
  width: 100%;
}

@media (max-width: 768px) {
  .tag-list-page {
    padding: var(--space-3);
  }

  .toolbar-card {
    margin-bottom: var(--space-3);
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>