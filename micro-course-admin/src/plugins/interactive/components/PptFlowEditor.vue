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
        PPT 页间跳转逻辑
        <el-tag size="small" type="info">{{ flows.length }} 条规则</el-tag>
      </h3>
      <el-button size="small" type="primary" plain :icon="Plus" @click="openCreate">新建跳转</el-button>
    </div>

    <el-table v-if="flows.length > 0" :data="flows" size="small" border>
      <el-table-column prop="flowType" label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="flowTypeTag(row.flowType)" size="small">{{ flowTypeLabel(row.flowType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="从 → 到" min-width="180">
        <template #default="{ row }">
          <span class="fe-from">{{ pageTitle(row.fromPageId) }}</span>
          <el-icon class="fe-arrow"><ArrowRight /></el-icon>
          <span class="fe-to">{{ row.toPageId ? pageTitle(row.toPageId) : '结束' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="条件/描述" min-width="200">
        <template #default="{ row }">
          <span v-if="row.conditionExpression" class="fe-condition">
            <code>{{ row.conditionExpression }}</code>
          </span>
          <span v-else-if="row.dependsOnQuizId" class="fe-condition">
            依赖 Quiz #{{ row.dependsOnQuizId }}
          </span>
          <span v-else class="fe-desc">{{ row.description || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="80" />
    </el-table>
    <el-empty v-else description="暂无跳转规则 (默认线性)" :image-size="80" />

    <!-- 新建对话框 -->
    <el-dialog v-model="showCreate" title="新建页间跳转规则" width="520px">
      <el-form label-position="top" :model="form">
        <el-form-item label="类型">
          <el-radio-group v-model="form.flowType">
            <el-radio-button value="NEXT">NEXT 线性</el-radio-button>
            <el-radio-button value="BRANCH_DEPENDS">BRANCH 分支</el-radio-button>
            <el-radio-button value="SKIP_IF_KNOWN">SKIP 跳过</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="从哪页">
          <el-select v-model="form.fromPageId" placeholder="选择起始页" style="width:100%">
            <el-option v-for="p in pages" :key="p.id" :label="`第 ${p.pageNumber} 页 - ${p.pageTitle || ''}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="跳转到">
          <el-select v-model="form.toPageId" placeholder="选择目标页 (留空=结束)" clearable style="width:100%">
            <el-option v-for="p in pages" :key="p.id" :label="`第 ${p.pageNumber} 页 - ${p.pageTitle || ''}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.flowType === 'BRANCH_DEPENDS'" label="依赖 Quiz ID">
          <el-input-number v-model="form.dependsOnQuizId" :min="1" />
        </el-form-item>
        <el-form-item v-if="form.flowType === 'SKIP_IF_KNOWN'" label="跳过条件表达式">
          <el-input v-model="form.conditionExpression" placeholder="例如: user_progress >= 0.8" />
        </el-form-item>
        <el-form-item label="优先级 (数字小优先)">
          <el-input-number v-model="form.priority" :min="0" />
        </el-form-item>
        <el-form-item label="描述 (可选)">
          <el-input v-model="form.description" placeholder="规则说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Plus, ArrowRight } from '@element-plus/icons-vue'
import { listPptFlows, createPptFlow } from '../api/pptCourseware'

const props = defineProps({
  courseId: { type: Number, required: true },
  sectionId: { type: Number, required: true },
  pages: { type: Array, default: () => [] }
})

const flows = ref([])
const showCreate = ref(false)
const saving = ref(false)
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
  form.value = { flowType: 'NEXT', fromPageId: null, toPageId: null, dependsOnQuizId: null, conditionExpression: '', priority: 0, description: '' }
  showCreate.value = true
}

async function handleCreate() {
  saving.value = true
  try {
    await createPptFlow(props.courseId, props.sectionId, form.value)
    ElMessage.success('跳转规则已创建')
    showCreate.value = false
    await load()
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

function pageTitle(pageId) {
  const p = props.pages.find(p => p.id === pageId)
  return p ? `第 ${p.pageNumber} 页` : `#${pageId}`
}

function flowTypeLabel(t) {
  return { NEXT: 'NEXT 线性', BRANCH_DEPENDS: 'BRANCH 分支', SKIP_IF_KNOWN: 'SKIP 跳过' }[t] || t
}

function flowTypeTag(t) {
  return { NEXT: 'primary', BRANCH_DEPENDS: 'warning', SKIP_IF_KNOWN: 'success' }[t] || 'info'
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