<!--
  API Key 管理组件（教师个人设置）
  每个教师独立的 API Key，供 Hermes / 第三方系统调用 webhook 时认证。
  - 生成/重新生成：返回明文（仅此一次）
  - 查看：返回脱敏
  - 撤销：清空
-->
<template>
  <el-card class="api-key-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span class="card-title">API Key（第三方系统集成）</span>
        <el-tag v-if="hasKey" type="success" size="small">已生成</el-tag>
        <el-tag v-else type="info" size="small">未生成</el-tag>
      </div>
    </template>

    <div class="api-key-content">
      <p class="api-key-desc">
        生成 API Key 后，您可以在 Hermes 等第三方系统中通过 <code>X-API-Key</code> Header 同步管理您的课程。
        每个 Key 与您的教师身份绑定，可操作您的全部课程权限。
      </p>

      <!-- 未生成 -->
      <div v-if="!hasKey && !loading" class="api-key-empty">
        <el-button type="primary" :icon="Key" @click="onGenerate">
          生成 API Key
        </el-button>
      </div>

      <!-- 已生成：脱敏展示 -->
      <div v-else-if="hasKey && !newlyGeneratedKey" class="api-key-display">
        <el-input
          v-model="maskedKey"
          readonly
          class="api-key-input"
        >
          <template #append>
            <el-button @click="onCopy(maskedKey)">
              <el-icon><CopyDocument /></el-icon>
              <span>复制</span>
            </el-button>
          </template>
        </el-input>
        <div class="api-key-meta">
          <span v-if="createdAt">生成时间：{{ createdAt }}</span>
          <span class="api-key-warn">出于安全，仅首次生成时显示完整 Key</span>
        </div>
        <div class="api-key-actions">
          <el-button type="warning" plain @click="onRegenerate">
            <el-icon><Refresh /></el-icon>
            <span>重新生成</span>
          </el-button>
          <el-button type="danger" plain @click="onRevoke">
            <el-icon><Delete /></el-icon>
            <span>撤销</span>
          </el-button>
        </div>
      </div>

      <!-- 新生成的 Key（明文一次性显示） -->
      <div v-else-if="newlyGeneratedKey" class="api-key-new">
        <el-alert type="warning" :closable="false" class="api-key-alert">
          <strong>请立即保存！</strong> 这是您唯一一次看到完整 Key 的机会，关闭后将无法再次查看。
        </el-alert>
        <el-input
          v-model="newlyGeneratedKey"
          readonly
          class="api-key-input"
          data-testid="new-api-key-input"
        >
          <template #append>
            <el-button type="primary" @click="onCopy(newlyGeneratedKey)">
              <el-icon><CopyDocument /></el-icon>
              <span>复制</span>
            </el-button>
          </template>
        </el-input>
        <div class="api-key-actions">
          <el-button type="primary" @click="onAcknowledge">我已保存，关闭提示</el-button>
        </div>
      </div>

      <div v-if="loading" class="api-key-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Key, CopyDocument, Refresh, Delete, Loading } from '@element-plus/icons-vue'
import { getMyApiKey, generateMyApiKey, revokeMyApiKey } from '@/api/user'

const loading = ref(false)
const hasKey = ref(false)
const maskedKey = ref('')
const createdAt = ref('')
const newlyGeneratedKey = ref('')

async function loadKey() {
  loading.value = true
  try {
    const res = await getMyApiKey()
    if (res.data) {
      hasKey.value = true
      maskedKey.value = res.data.maskedKey
      createdAt.value = res.data.createdAt || ''
    } else {
      hasKey.value = false
      maskedKey.value = ''
      createdAt.value = ''
    }
  } catch (err) {
    ElMessage.error('获取 API Key 失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function onGenerate() {
  try {
    await ElMessageBox.confirm(
      '生成后，旧 Key（如果有）将立即失效。是否继续？',
      '生成 API Key',
      { confirmButtonText: '生成', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  loading.value = true
  try {
    const res = await generateMyApiKey()
    newlyGeneratedKey.value = res.data.apiKey
    maskedKey.value = res.data.maskedKey
    createdAt.value = res.data.createdAt || ''
    hasKey.value = true
    ElMessage.success('API Key 已生成，请立即保存！')
  } catch (err) {
    ElMessage.error('生成失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function onRegenerate() {
  await onGenerate()
}

async function onRevoke() {
  try {
    await ElMessageBox.confirm(
      '撤销后，所有使用此 Key 的第三方系统将立即无法访问。是否继续？',
      '撤销 API Key',
      { confirmButtonText: '撤销', cancelButtonText: '取消', type: 'error' }
    )
  } catch { return }

  loading.value = true
  try {
    await revokeMyApiKey()
    hasKey.value = false
    maskedKey.value = ''
    createdAt.value = ''
    newlyGeneratedKey.value = ''
    ElMessage.success('API Key 已撤销')
  } catch (err) {
    ElMessage.error('撤销失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function onCopy(text) {
  if (!text) return
  navigator.clipboard.writeText(text)
    .then(() => ElMessage.success('已复制到剪贴板'))
    .catch(() => ElMessage.error('复制失败，请手动复制'))
}

function onAcknowledge() {
  newlyGeneratedKey.value = ''
  ElMessage.info('提示已关闭。如 Key 遗失，请重新生成。')
}

onMounted(() => {
  loadKey()
})
</script>

<style scoped>
.api-key-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-weight: 600;
  font-size: 15px;
}
.api-key-desc {
  color: #666;
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 16px;
}
.api-key-desc code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  color: #d63384;
}
.api-key-empty {
  text-align: center;
  padding: 24px 0;
}
.api-key-display,
.api-key-new {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.api-key-input {
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
}
.api-key-input :deep(input) {
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  letter-spacing: 0.5px;
}
.api-key-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}
.api-key-warn {
  color: #e6a23c;
}
.api-key-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.api-key-alert {
  margin-bottom: 4px;
}
.api-key-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #999;
  padding: 16px 0;
}
</style>