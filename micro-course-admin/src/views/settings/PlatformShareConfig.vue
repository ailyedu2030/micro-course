<!--
  平台分账配置管理
  /admin/platform-share-config
  Author: AI
-->
<template>
  <div class="platform-share-config-container">
    <!-- 顶部说明卡片 -->
    <el-card class="info-card shadow-hover" shadow="never">
      <div class="info-content">
        <el-icon :size="20" class="info-icon"><Coin /></el-icon>
        <div>
          <p class="info-title">平台分账配置</p>
          <p class="info-desc">管理平台对各等级教师的分账比例，配置后即刻生效。</p>
        </div>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card" shadow="never" v-loading="loading" element-loading-text="加载中...">
      <el-table :data="configList" stripe style="width: 100%" @row-click="handleRowClick" empty-text="暂无配置">
        <el-table-column prop="configKey" label="配置标识" width="140">
          <template #default="{ row }">
            <el-tag :type="getKeyTagType(row.configKey)" size="small" effect="dark">
              {{ row.configKey }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="configValue" label="分账比例" width="120">
          <template #default="{ row }">
            <span class="value-text">{{ row.configValue }}<span class="percent-suffix">%</span></span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="active" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small">
              {{ row.active ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedBy" label="最后更新人" width="130" />
        <el-table-column prop="updatedAt" label="更新时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleEdit(row)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="编辑分账配置"
      width="500px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="editForm"
        :rules="formRules"
        label-width="100px"
        label-position="right"
      >
        <el-form-item label="配置标识">
          <el-tag :type="getKeyTagType(editForm.configKey)" effect="dark">
            {{ editForm.configKey }}
          </el-tag>
          <span class="form-hint">不可修改</span>
        </el-form-item>
        <el-form-item label="分账比例" prop="configValue">
          <el-input-number
            v-model="editForm.configValue"
            :min="0"
            :max="100"
            :precision="2"
            :step="0.5"
            controls-position="right"
          />
          <span class="form-unit">%</span>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="2"
            placeholder="请输入配置说明"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editForm.active" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Coin } from '@element-plus/icons-vue'
import { getPlatformShareConfigList, upsertPlatformShareConfig } from '@/api/platform-share-config'

const loading = ref(false)
const saving = ref(false)
const configList = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)

const editForm = reactive({
  configKey: '',
  configValue: 0,
  description: '',
  active: true
})

const formRules = {
  configValue: [
    { required: true, message: '请输入分账比例', trigger: 'blur' },
    { type: 'number', min: 0, max: 100, message: '分账比例应在 0-100 之间', trigger: 'blur' }
  ]
}

function getKeyTagType(key) {
  const colorMap = {
    DEFAULT: '',
    NEW: 'warning',
    BRONZE: '',
    SILVER: 'info',
    GOLD: 'warning',
    PLATINUM: 'danger'
  }
  return colorMap[key] || 'info'
}

function formatTime(time) {
  if (!time) return '-'
  try {
    const d = new Date(time)
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return time
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getPlatformShareConfigList()
    configList.value = res.data || []
  } catch (e) {
    console.warn('[PlatformShareConfig] fetch failed', e)
    ElMessage.error('加载配置列表失败')
  } finally {
    loading.value = false
  }
}

function handleRowClick(row) {
  handleEdit(row)
}

function handleEdit(row) {
  editForm.configKey = row.configKey
  editForm.configValue = Number(row.configValue) || 0
  editForm.description = row.description || ''
  editForm.active = row.active !== false
  dialogVisible.value = true
}

async function handleSave() {
  if (saving.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await upsertPlatformShareConfig(editForm.configKey, {
      configValue: String(editForm.configValue),
      description: editForm.description,
      active: editForm.active
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await fetchList()
  } catch (e) {
    console.warn('[PlatformShareConfig] save failed', e)
    ElMessage.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.platform-share-config-container {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1200px;
  margin: 0 auto;
}

.info-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.info-content {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.info-icon {
  color: var(--role-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.info-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.info-desc {
  margin: 0;
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.value-text {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-color-primary);
}

.percent-suffix {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-left: 2px;
}

.form-hint {
  margin-left: var(--space-2);
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
}

.form-unit {
  display: inline-flex;
  align-items: center;
  margin-left: var(--space-2);
  color: var(--el-text-color-secondary);
  font-size: var(--text-base);
}
</style>
