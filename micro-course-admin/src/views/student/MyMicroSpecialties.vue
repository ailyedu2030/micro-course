<!--
  我的微专业
  路由: /student/my-micro-specialties
  Phase 10
-->
<template>
  <div class="my-ms fade-in">
    <!-- Breadcrumb -->
    <nav class="page-breadcrumb" aria-label="面包屑">
      <span>我的微专业</span>
      <span class="sub-hint">专业修读记录</span>
    </nav>

    <!-- Loading -->
    <el-skeleton v-if="loading" :rows="6" animated class="my-ms-loading" />

    <!-- Content wrapper -->
    <template v-else>
    <!-- Error -->
    <el-result
      v-if="error"
      icon="error"
      title="加载失败"
      sub-title="网络异常，请稍后重试"
      class="my-ms-error"
    >
      <template #extra>
        <el-button type="primary" @click="fetchData">重试</el-button>
      </template>
    </el-result>

    <!-- Empty -->
    <template v-else-if="enrollments.length === 0">
      <el-empty description="暂未报名微专业" class="my-ms-empty">
        <template #image>
          <el-icon :size="80" style="opacity: .3;"><Notebook /></el-icon>
        </template>
        <el-button type="primary" @click="$router.push('/student/courses')">
          去课程广场看看
        </el-button>
      </el-empty>
    </template>

    <!-- Has Data -->
    <template v-else>
      <!-- Stats Cards -->
      <el-row :gutter="16" class="ms-stats-row">
        <el-col :span="8">
          <div class="ms-stat-card">
            <span class="ms-stat-label">已报名</span>
            <span class="ms-stat-value">{{ stats.enrolled }}</span>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="ms-stat-card ms-stat-card--progress">
            <span class="ms-stat-label">进行中</span>
            <span class="ms-stat-value">{{ stats.inProgress }}</span>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="ms-stat-card ms-stat-card--completed">
            <span class="ms-stat-label">已结业</span>
            <span class="ms-stat-value">{{ stats.completed }}</span>
          </div>
        </el-col>
      </el-row>

      <!-- Enrollment List -->
      <div class="ms-list">
        <el-card
          v-for="item in enrollments"
          :key="item.id"
          shadow="never"
          class="ms-item-card"
        >
          <div class="ms-item-row">
            <!-- Cover -->
            <div
              class="ms-item-cover"
              role="button"
              tabindex="0"
              @click="goDetail(item.microSpecialtyId)"
              @keydown.enter="goDetail(item.microSpecialtyId)"
            >
              <img
                v-if="item.coverUrl"
                :src="item.coverUrl"
                :alt="item.title"
                loading="lazy"
                class="ms-item-cover-img"
              />
              <div v-else class="ms-item-cover-placeholder">
                <el-icon :size="28"><Notebook /></el-icon>
              </div>
            </div>

            <!-- Info -->
            <div class="ms-item-body">
              <h3
                class="ms-item-title-link"
                role="button"
                tabindex="0"
                @click="goDetail(item.microSpecialtyId)"
                @keydown.enter="goDetail(item.microSpecialtyId)"
              >
                {{ item.title || item.microSpecialtyTitle || '未命名微专业' }}
              </h3>
              <div class="ms-item-meta">
                <span>{{ item.departmentName || '—' }}</span>
                <span class="meta-sep">·</span>
                <span>{{ item.totalCredits || 0 }} 学分</span>
              </div>

              <!-- Status Tag -->
              <el-tag :type="getStatusTagType(item.status)" size="small" class="ms-item-status">
                {{ getStatusLabel(item.status) }}
              </el-tag>

              <!-- Progress (only for IN_PROGRESS) -->
              <div v-if="item.status === 'IN_PROGRESS'" class="ms-item-progress">
                <el-progress
                  :percentage="item.progress || 0"
                  :stroke-width="6"
                  :show-text="true"
                  class="ms-progress-bar"
                />
                <span class="ms-progress-text">
                  {{ item.completedCredits || 0 }} / {{ item.totalCredits || 0 }} 学分
                </span>
              </div>

              <!-- Failed reason -->
              <div v-if="item.status === 'FAILED' && item.failReason" class="ms-item-fail">
                <el-alert
                  :title="'未通过原因：' + item.failReason"
                  type="error"
                  :closable="false"
                  show-icon
                />
              </div>
            </div>

            <!-- Actions -->
            <div class="ms-item-actions">
              <!-- IN_PROGRESS / ENROLLED -->
              <template v-if="['IN_PROGRESS', 'APPROVED'].includes(item.status)">
                <el-button size="small" type="primary" @click.stop="goContinueLearning(item)">
                  继续学习
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  @click.stop="handleDrop(item)"
                >
                  退出修读
                </el-button>
              </template>

              <!-- COMPLETED -->
              <template v-else-if="item.status === 'COMPLETED'">
                <el-button
                  v-if="item.certificateId"
                  size="small"
                  type="success"
                  @click.stop="viewCertificate(item)"
                >
                  查看证书
                </el-button>
                <el-button size="small" @click.stop="goDetail(item.microSpecialtyId)">
                  查看详情
                </el-button>
              </template>

              <!-- FAILED -->
              <template v-else-if="item.status === 'FAILED'">
                <el-button
                  size="small"
                  type="warning"
                  @click.stop="handleViewFailedCourses(item)"
                >
                  查看不合格课程
                </el-button>
                <el-button
                  size="small"
                  type="primary"
                  :loading="reapplying === item.id"
                  @click.stop="handleReapply(item)"
                >
                  重新申请
                </el-button>
                <el-button
                  size="small"
                  type="warning"
                  plain
                  @click.stop="contactAcademic"
                >
                  联系教务处
                </el-button>
              </template>

              <!-- DROPPED / REJECTED / WITHDRAWN -->
              <template v-else-if="['DROPPED', 'REJECTED'].includes(item.status)">
                <el-button
                  size="small"
                  type="primary"
                  :loading="reapplying === item.id"
                  @click.stop="handleReapply(item)"
                >
                  重新申请
                </el-button>
                <el-button size="small" @click.stop="goDetail(item.microSpecialtyId)">
                  查看详情
                </el-button>
              </template>

              <!-- PENDING -->
              <template v-else-if="item.status === 'PENDING'">
                <el-tag type="warning">审核中</el-tag>
              </template>

              <!-- Default -->
              <template v-else>
                <el-button size="small" @click.stop="goDetail(item.microSpecialtyId)">
                  查看详情
                </el-button>
              </template>
            </div>
          </div>
        </el-card>
      </div>
    </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Notebook } from '@element-plus/icons-vue'
import {
  getMyEnrollments,
  dropEnrollment,
  reapplyEnrollment
} from '@/api/microSpecialty'

const router = useRouter()

const loading = ref(false)
const error = ref(false)
const enrollments = ref([])
const reapplying = ref(null) // enrollment ID being reapplied, for loading state

const stats = computed(() => {
  const result = { enrolled: 0, inProgress: 0, completed: 0 }
  for (const e of enrollments.value) {
    if (['APPROVED', 'PENDING'].includes(e.status)) result.enrolled++
    if (e.status === 'IN_PROGRESS') result.inProgress++
    if (e.status === 'COMPLETED') result.completed++
  }
  return result
})

const STATUS_MAP = {
  PENDING: { label: '审核中', type: 'warning' },
  APPROVED: { label: '已报名', type: '' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  COMPLETED: { label: '已结业', type: 'success' },
  CERTIFIED: { label: '已认证', type: 'success' },
  FAILED: { label: '未通过', type: 'danger' },
  DROPPED: { label: '已退出', type: 'info' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

const getStatusLabel = (s) => STATUS_MAP[s]?.label || s || '—'
const getStatusTagType = (s) => STATUS_MAP[s]?.type || 'info'

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    const { data } = await getMyEnrollments()
    enrollments.value = data?.items || data || []
  } catch (e) {
    console.error('[MyMS] 加载修读记录失败:', e)
    error.value = true
  } finally {
    loading.value = false
  }
}

const goDetail = (id) => {
  if (!id) return
  router.push(`/student/micro-specialties/${id}`)
}

const goContinueLearning = (item) => {
  if (!item.microSpecialtyId) return
  router.push(`/student/micro-specialties/${item.microSpecialtyId}?tab=courses&goto=first`)
}

const handleDrop = async (item) => {
  try {
    await ElMessageBox.confirm(
      '确认退出该微专业修读？退出后可能需要重新申请。',
      '退出确认',
      { confirmButtonText: '确认退出', cancelButtonText: '取消', type: 'warning' }
    )
    await dropEnrollment(item.id, { reason: '主动退出' })
    ElMessage.success('已退出修读')
    await fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('[MyMS] 退出失败:', e)
      ElMessage.error(e?.response?.data?.message || '操作失败')
    }
  }
}

const handleReapply = async (item) => {
  reapplying.value = item.id
  try {
    await ElMessageBox.confirm(
      '确认重新申请该微专业？',
      '重新申请',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
    )
    await reapplyEnrollment(item.id || item.microSpecialtyId)
    ElMessage.success('已重新申请')
    await fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('[MyMS] 重新申请失败:', e)
      ElMessage.error(e?.response?.data?.message || '操作失败')
    }
  } finally {
    reapplying.value = null
  }
}

const viewCertificate = (item) => {
  if (item.certificateId) {
    // Open certificate download/view using certificate ID
    window.open(`/api/certificates/${item.certificateId}/download`, '_blank')
  }
}

const handleViewFailedCourses = (item) => {
  // Navigate to detail page with failed courses tab
  router.push(`/student/micro-specialties/${item.microSpecialtyId}?tab=courses&focus=failed`)
}

const contactAcademic = () => {
  ElMessageBox.alert(
    '如有疑问，请联系教务处：\n电话：010-12345678\n邮箱：academic@example.edu.cn\n办公时间：周一至周五 9:00-17:00',
    '联系教务处',
    { confirmButtonText: '知道了', type: 'info' }
  )
}

onMounted(() => fetchData())
</script>

<style scoped>
.my-ms {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--space-4) var(--space-6) var(--space-8);
  min-height: 100dvh;
}
/* Phase 2: 键盘可访问性 — 按钮焦点样式 */
.el-button:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 2px;
}
/* Breadcrumb */
.page-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-5);
}
.sub-hint {
  font-size: var(--text-sm);
  font-weight: var(--weight-regular);
  color: var(--el-text-color-secondary);
}
/* Loading / Error */
.my-ms-loading {
  min-height: 400px;
}
.sk-stat-card {
  padding: var(--space-5);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
}
.my-ms-error {
  padding: 80px 0;
}
.my-ms-empty {
  padding: 100px 0;
}
/* Stats Cards */
.ms-stats-row {
  margin-bottom: var(--space-5);
}
.ms-stat-card {
  text-align: center;
  padding: var(--space-5) var(--space-4);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-tinted-sm);
  transition: transform var(--duration-base) var(--ease-out);
}
.ms-stat-card:hover {
  transform: translateY(-2px);
}
.ms-stat-label {
  display: block;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-2);
}
.ms-stat-value {
  display: block;
  font-size: 32px;
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
}
.ms-stat-card--progress .ms-stat-value {
  color: var(--el-color-primary);
}
.ms-stat-card--completed .ms-stat-value {
  color: var(--el-color-success);
}
/* List */
.ms-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.ms-item-card {
  transition: box-shadow var(--duration-base) var(--ease-out);
}
.ms-item-card:hover {
  box-shadow: var(--shadow-tinted-md);
}
.ms-item-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
}
/* Cover */
.ms-item-cover {
  width: 140px;
  min-width: 140px;
  height: 88px;
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
}
.ms-item-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ms-item-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a5b4fc;
}
/* Body */
.ms-item-body {
  flex: 1;
  min-width: 0;
}
.ms-item-title-link {
  margin: 0 0 4px;
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ms-item-title-link:hover {
  color: var(--role-primary);
}
.ms-item-meta {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.meta-sep {
  margin: 0 4px;
  color: var(--el-border-color);
}
.ms-item-status {
  margin-bottom: 8px;
}
/* Progress */
.ms-item-progress {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: 4px;
}
.ms-progress-bar {
  flex: 1;
}
.ms-progress-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
/* Failed */
.ms-item-fail {
  margin-top: var(--space-2);
}
/* Actions */
.ms-item-actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: flex-end;
  flex-shrink: 0;
}
/* H5 */
@media (max-width: 768px) {
  .ms-item-row {
    flex-direction: column;
  }
  .ms-item-cover {
    width: 100%;
    height: 160px;
  }
  .ms-item-actions {
    flex-direction: row;
    width: 100%;
    justify-content: flex-end;
  }
  .ms-stat-value {
    font-size: 24px;
  }
}
</style>
