<!--
  PptFlowEditor.vue · PPT 页间跳转逻辑可视化编辑

  支持三种 flow_type (V306):
  - NEXT: 线性 (默认 page_number+1)
  - BRANCH_DEPENDS: 条件分支 (depends on quiz)
  - SKIP_IF_KNOWN: 智能跳过 (user_progress >= 阈值)

  Props: courseId, sectionId, pages [{id, pageNumber, pageTitle}]
-->
<template>
  <div class="flow-editor">
    <div class="fe-header">
      <h3 class="fe-title">
        <el-icon><Connection /></el-icon>
        {{ t('ppt.flow.title') }}
        <el-tag size="small" type="info">{{ t('ppt.flow.ruleCount', { count: flows.length }) }}</el-tag>
      </h3>
      <el-button size="small" type="primary" plain :icon="Plus" @click="openCreate">{{ t('ppt.flow.createNew') }}</el-button>
    </div>

    <el-table v-if="flows.length > 0" :data="flows" size="small" border>
      <el-table-column prop="flowType" :label="t('ppt.flow.colType')" width="120">
        <template #default="{ row }">
          <el-tag :type="flowTypeTag(row.flowType)" size="small">{{ flowTypeLabel(row.flowType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('ppt.flow.colFromTo')" min-width="180">
        <template #default="{ row }">
          <span class="fe-from">{{ pageTitle(row.fromPageId) }}</span>
          <el-icon class="fe-arrow"><ArrowRight /></el-icon>
          <span class="fe-to">{{ row.toPageId ? pageTitle(row.toPageId) : t('ppt.flow.end') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('ppt.flow.colCondition')" min-width="200">
        <template #default="{ row }">
          <span v-if="row.conditionExpression" class="fe-condition">
            <code>{{ row.conditionExpression }}</code>
          </span>
          <span v-else-if="row.dependsOnQuizId" class="fe-condition">
            {{ t('ppt.flow.dependsOnQuiz', { id: row.dependsOnQuizId }) }}
          </span>
          <span v-else class="fe-desc">{{ row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="priority" :label="t('ppt.flow.colPriority')" width="80" />
      <el-table-column :label="t('ppt.flow.colActions')" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">{{ t('ppt.flow.edit') }}</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row)">{{ t('ppt.flow.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else :description="t('ppt.flow.empty')" :image-size="80" />

    <!-- 新建对话框 -->
    <el-dialog v-model="showCreate" :title="editingId ? t('ppt.flow.editTitle') : t('ppt.flow.createTitle')" width="520px">
      <el-form label-position="top" :model="form">
        <el-form-item :label="t('ppt.flow.colType')">
          <el-radio-group v-model="form.flowType">
            <el-radio-button value="NEXT">{{ t('ppt.flow.typeNext') }}</el-radio-button>
            <el-radio-button value="BRANCH_DEPENDS">{{ t('ppt.flow.typeBranch') }}</el-radio-button>
            <el-radio-button value="SKIP_IF_KNOWN">{{ t('ppt.flow.typeSkip') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('ppt.flow.fromPage')">
          <el-select v-model="form.fromPageId" :placeholder="t('ppt.flow.selectStartPage')" style="width:100%">
            <el-option v-for="p in pages" :key="p.id" :label="t('ppt.flow.pageOption', { number: p.pageNumber, title: p.pageTitle || '' })" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('ppt.flow.toPage')">
          <el-select v-model="form.toPageId" :placeholder="t('ppt.flow.selectTargetPage')" clearable style="width:100%">
            <el-option v-for="p in pages" :key="p.id" :label="t('ppt.flow.pageOption', { number: p.pageNumber, title: p.pageTitle || '' })" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.flowType === 'BRANCH_DEPENDS'" :label="t('ppt.flow.quizIdLabel')">
          <el-input-number v-model="form.dependsOnQuizId" :min="1" />
        </el-form-item>
        <el-form-item v-if="form.flowType === 'SKIP_IF_KNOWN'" :label="t('ppt.flow.conditionLabel')">
          <el-input v-model="form.conditionExpression" :placeholder="t('ppt.flow.conditionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('ppt.flow.priorityLabel')">
          <el-input-number v-model="form.priority" :min="0" />
        </el-form-item>
        <el-form-item :label="t('ppt.flow.descLabel')">
          <el-input v-model="form.description" :placeholder="t('ppt.flow.descPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">{{ t('ppt.flow.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ editingId ? t('ppt.flow.save') : t('ppt.flow.create') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Connection, Plus, ArrowRight } from '@element-plus/icons-vue'
import { listPptFlows, createPptFlow, updatePptFlow, deletePptFlow } from '../api/pptCourseware'
import { ElMessageBox } from 'element-plus'

const { t } = useI18n()

const props = defineProps({
  courseId: { type: Number, required: true },
  sectionId: { type: Number, required: true },
  pages: { type: Array, default: () => [] }
})

const flows = ref([])
const showCreate = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = ref({
  flowType: 'NEXT',
  fromPageId: null,
  toPageId: null,
  dependsOnQuizId: null,
  conditionExpression: '',
  priority: 0,
  description: ''
})

async function load() {
  const res = await listPptFlows(props.courseId, props.sectionId)
  flows.value = res.data || res || []
}

function openCreate() {
  editingId.value = null
  form.value = { flowType: 'NEXT', fromPageId: null, toPageId: null, dependsOnQuizId: null, conditionExpression: '', priority: 0, description: '' }
  showCreate.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    flowType: row.flowType,
    fromPageId: row.fromPageId,
    toPageId: row.toPageId,
    dependsOnQuizId: row.dependsOnQuizId,
    conditionExpression: row.conditionExpression || '',
    priority: row.priority ?? 0,
    description: row.description || ''
  }
  showCreate.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (editingId.value) {
      await updatePptFlow(props.courseId, editingId.value, form.value)
      ElMessage.success(t('ppt.flow.updated'))
    } else {
      await createPptFlow(props.courseId, props.sectionId, form.value)
      ElMessage.success(t('ppt.flow.created'))
    }
    showCreate.value = false
    await load()
  } catch (e) {
    const action = editingId.value ? t('ppt.flow.update') : t('ppt.flow.create')
    ElMessage.error(t('ppt.flow.actionFailed', { action, msg: e.message || t('ppt.flow.unknownError') }))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(t('ppt.flow.confirmDelete', { id: row.id }), t('ppt.flow.confirmDeleteTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePptFlow(props.courseId, row.id)
    ElMessage.success(t('ppt.flow.deleted'))
    await load()
  } catch (e) {
    ElMessage.error(t('ppt.flow.deleteFailed', { msg: e.message || t('ppt.flow.unknownError') }))
  }
}

function pageTitle(pageId) {
  const p = props.pages.find(p => p.id === pageId)
  return p ? t('ppt.flow.pageTitle', { number: p.pageNumber }) : `#${pageId}`
}

function flowTypeLabel(tp) {
  return { NEXT: t('ppt.flow.typeNext'), BRANCH_DEPENDS: t('ppt.flow.typeBranch'), SKIP_IF_KNOWN: t('ppt.flow.typeSkip') }[tp] || tp
}

function flowTypeTag(tp) {
  return { NEXT: 'primary', BRANCH_DEPENDS: 'warning', SKIP_IF_KNOWN: 'success' }[tp] || 'info'
}

onMounted(load)
</script>

<style scoped>
.flow-editor { background: var(--el-fill-color-blank); border-radius: 8px; padding: 16px; }
.fe-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.fe-title { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.fe-from, .fe-to { font-weight: 500; }
.fe-arrow { margin: 0 6px; color: var(--el-color-primary); }
.fe-condition code { background: var(--el-fill-color-light); padding: 2px 6px; border-radius: 3px; font-size: 12px; }
.fe-desc { color: var(--el-text-color-secondary); }
</style>
