<!--
  我的证书卡片（Round 11-3 从 Profile.vue 拆分）
  CertificatesCard: 证书列表展示 + 查看/下载，自包含 fetch
  Author: jackie
-->
<template>
  <el-card class="profile-card certificate-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>我的证书</span>
      </div>
    </template>

    <div v-loading="certLoading" :aria-busy="certLoading" :class="isMobile ? 'cert-list--mobile' : 'cert-grid'">
      <div
        v-for="cert in certificates"
        :key="cert.id"
        class="cert-item"
        :class="{ 'cert-item--mobile': isMobile }"
      >
        <div class="cert-icon">
          <el-icon color="var(--role-primary)" :size="isMobile ? 32 : 40"><Medal /></el-icon>
        </div>
        <div class="cert-info">
          <div class="cert-title">{{ cert.courseTitle }}</div>
          <div class="cert-meta">
            <span class="cert-date">颁发于 {{ formatDate(cert.issuedAt) }}</span>
            <span class="cert-code">{{ cert.certCode }}</span>
          </div>
        </div>
        <div class="cert-actions">
          <el-button type="primary" size="small" @click="handleViewCertificate(cert)">
            {{ isMobile ? '查看' : '查看证书' }}
          </el-button>
        </div>
      </div>
    </div>
    <el-empty v-if="!certLoading && certificates.length === 0" description="暂无证书记录，完成课程学习后可获得" :image-size="60" />
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Medal } from '@element-plus/icons-vue'
import { getMyCertificates, downloadCertificate } from '@/api/certificate'

defineProps({
  isMobile: { type: Boolean, default: false }
})

const certificates = ref([])
const certLoading = ref(false)

const fetchCertificates = async () => {
  certLoading.value = true
  try {
    const res = await getMyCertificates()
    certificates.value = res.data || []
  } catch {
    ElMessage.error('获取证书记录失败')
  } finally {
    certLoading.value = false
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}年${month}月${day}日`
}

const handleViewCertificate = async (cert) => {
  try {
    const res = await downloadCertificate(cert.id)
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch {
    ElMessage.error('查看证书失败')
  }
}

fetchCertificates()
</script>

<style scoped>
.profile-card {
  margin-bottom: var(--space-5);
  border-radius: var(--radius-lg);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}
.profile-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}
.card-header {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
}

.cert-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.cert-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}
.cert-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.cert-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--role-primary-light) 0%, var(--role-primary) 100%);
  border-radius: var(--radius-circle);
}

.cert-info {
  flex: 1;
  min-width: 0;
}
.cert-title {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-1);
}
.cert-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.cert-date {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}
.cert-code {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  font-family: "Courier New", monospace;
}

.cert-actions {
  flex-shrink: 0;
}
.cert-actions .el-button {
  cursor: pointer;
}

/* === Mobile Cert List === */
.cert-list--mobile {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.cert-list--mobile .cert-item {
  flex-direction: row;
  align-items: center;
}
.cert-list--mobile .cert-icon {
  width: 40px;
  height: 40px;
}
.cert-list--mobile .cert-actions {
  width: auto;
}
.cert-list--mobile .cert-actions .el-button {
  width: auto;
}

:deep(.el-button) { cursor: pointer; }

@media (max-width: 768px) {
  .profile-card { margin-bottom: var(--space-4); }

  .cert-item--mobile {
    flex-direction: column;
    align-items: flex-start;
  }
  .cert-list--mobile .cert-item {
    flex-direction: column;
    align-items: flex-start;
  }
  .cert-actions {
    width: 100%;
  }
  .cert-actions .el-button {
    width: 100%;
  }
}
</style>
