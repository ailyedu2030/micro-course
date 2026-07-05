<!--
  存储申请表审批（教务处端）
  路由: /academic/micro-specialties/storage-review
  P1C-091: 审批通道修复
-->
<template>
  <div class="storage-review-page">
    <el-page-header @back="$router.back()" content="存储申请表审批" class="mg-bottom-16" />

    <el-card shadow="never">
      <el-alert v-if="error" title="加载失败" type="error" show-icon :closable="false" class="mg-bottom-12">
        <template #default><el-button size="small" @click="fetchData">重试</el-button></template>
      </el-alert>
      <el-table v-loading="loading" :data="items" stripe border>
        <template #empty>
          <el-empty description="暂无待审批的申请表" />
        </template>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="proposerName" label="申请人" width="100" />
        <el-table-column prop="departmentName" label="学院" width="120" />
        <el-table-column prop="type" label="类型" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PENDING_REVIEW'" type="warning" size="small">待审批</el-tag>
            <el-tag v-else-if="row.status === 'APPROVED'" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="row.status === 'REJECTED'" type="danger" size="small">已驳回</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="提交时间" width="130" align="center">
          <template #default="{ row }">{{ row.updatedAt?.slice(0, 10) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" type="primary" @click="goPreview(row)">预览</el-button>
              <el-button size="small" type="success" :loading="actingId === row.id" @click="handleApprove(row)">批准</el-button>
              <el-button size="small" type="danger" :loading="actingId === row.id" @click="handleReject(row)">驳回</el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="goPreview(row)">查看</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination mg-top-12">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 驳回原因 Dialog -->
    <el-dialog v-model="rejectVisible" title="驳回原因" width="480px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" maxlength="500" show-word-limit />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="actingId !== null" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getPendingStorageApplications, approveStorageApplication, rejectStorageApplication } from '@/api/storageApplication'

const router = useRouter()
const loading = ref(false)
const error = ref(false)
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const actingId = ref(null)
const rejectVisible = ref(false)
const rejectReason = ref('')
let rejectTarget = null

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    const { data } = await getPendingStorageApplications({ page: page.value - 1, size: size.value })
    items.value = data.items || []
    total.value = data.totalElements || 0
  } catch (e) {
    error.value = true
    ElMessage.error('加载申请表列表失败')
  } finally {
    loading.value = false
  }
}

const goPreview = (row) => {
  router.push(`/teacher/micro-specialties/storage-preview/${row.id}`)
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认批准申请表《${row.title}》？`, '确认', {
      confirmButtonText: '批准',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await approveStorageApplication(row.id)
    ElMessage.success('已批准')
    fetchData()
  } catch (e) {
    ElMessage.error(e.message || '审批失败')
  } finally {
    actingId.value = null
  }
}

const handleReject = (row) => {
  rejectTarget = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  actingId.value = rejectTarget.id
  try {
    await rejectStorageApplication(rejectTarget.id, rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e.message || '驳回失败')
  } finally {
    actingId.value = null
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.storage-review-page {
  padding: 24px;
  background: var(--el-bg-color-page);
  min-height: 100dvh;
}
.storage-review-page :deep(.el-card) {
  background: var(--el-fill-color-blank);
  border-radius: 8px;
}
.mg-bottom-16 { margin-bottom: 16px; }
.mg-bottom-12 { margin-bottom: 12px; }
.mg-top-12 { margin-top: 12px; }
.pagination {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
