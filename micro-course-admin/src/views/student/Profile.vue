<!--
  个人中心
  路由路径: /student/profile
  Phase 2
  Round 11-3 重构：拆分为 UserInfoEditor / PasswordEditor / AchievementBadges / WrongQuestionsCard / CertificatesCard
  Author: jackie
-->
<template>
  <div class="profile-view">
    <!-- 骨架屏：userInfo 加载中 -->
    <template v-if="!userStore.userInfo">
      <div class="profile-skeleton">
        <el-skeleton animated :rows="1" style="margin-bottom: 20px">
          <template #template>
            <el-skeleton-item variant="text" style="height: 32px; width: 160px" />
          </template>
        </el-skeleton>
        <el-row :gutter="20">
          <el-col :span="16">
            <el-card class="profile-card" shadow="never">
              <el-skeleton animated :rows="4" />
            </el-card>
            <el-card class="profile-card" shadow="never" style="margin-top: 16px">
              <el-skeleton animated :rows="3" />
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card class="profile-card" shadow="never">
              <el-skeleton animated :rows="3" />
            </el-card>
          </el-col>
        </el-row>
      </div>
    </template>

    <!-- PC 端布局 -->
    <template v-else-if="!isMobile">
      <h2 class="page-title">个人中心</h2>

      <el-row :gutter="20">
        <el-col :span="16">
          <!-- 资料编辑 -->
          <UserInfoEditor :is-mobile="false" />

          <!-- 密码修改 -->
          <PasswordEditor :is-mobile="false" />
        </el-col>

        <el-col :span="8">
          <!-- 头像 -->
          <el-card class="profile-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span>头像设置</span>
              </div>
            </template>
            <div class="avatar-section">
              <el-upload
                class="avatar-uploader"
                :show-file-list="false"
                :auto-upload="false"
                accept="image/jpeg,image/png,image/webp"
                :on-change="handleAvatarChange"
              >
                <el-avatar :size="80" :src="avatarPreview || userStore.userInfo?.avatar" />
              </el-upload>
              <div class="avatar-tip">支持 JPG、PNG、WebP 格式，建议 200×200 像素，≤2MB</div>
              <div class="avatar-actions">
                <el-button
                  v-if="avatarPreview"
                  type="primary"
                  size="small"
                  :loading="avatarLoading"
                  @click="handleSaveAvatar"
                >
                  保存头像
                </el-button>
                <el-button
                  v-if="avatarPreview"
                  size="small"
                  @click="handleCancelAvatar"
                >
                  取消
                </el-button>
              </div>
            </div>
          </el-card>

          <!-- 账号信息 -->
          <el-card class="profile-card student-stat-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span>账号信息</span>
              </div>
            </template>
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">用户ID</span>
                <span class="info-value">{{ userStore.userInfo?.id }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">角色</span>
                <span class="info-value">{{ userStore.userInfo?.role }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">注册时间</span>
                <span class="info-value">{{ userStore.userInfo?.createdAt }}</span>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <template v-if="userStore.role === 'STUDENT'">
        <!-- 成就 -->
        <AchievementBadges :is-mobile="false" />

        <!-- 错题集 -->
        <WrongQuestionsCard :is-mobile="false" />

        <!-- 我的证书 -->
        <CertificatesCard :is-mobile="false" />
      </template>
    </template>

    <!-- 移动端布局 -->
    <template v-else>
      <h2 class="page-title page-title--mobile">个人中心</h2>

      <!-- 用户信息卡片 -->
      <el-card class="profile-card user-info-card" shadow="never">
        <div class="user-info-content">
          <el-upload
            class="avatar-uploader-mobile"
            :show-file-list="false"
            :auto-upload="false"
            accept="image/jpeg,image/png,image/webp"
            :on-change="handleAvatarChange"
          >
            <el-avatar :size="60" :src="avatarPreview || userStore.userInfo?.avatar" />
          </el-upload>
          <div class="user-info-text">
            <div class="user-info-name">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</div>
            <div class="user-info-role">{{ userStore.userInfo?.role }}</div>
          </div>
        </div>
        <div v-if="avatarPreview" class="avatar-actions-mobile">
          <el-button type="primary" size="small" :loading="avatarLoading" @click="handleSaveAvatar">保存头像</el-button>
          <el-button size="small" @click="handleCancelAvatar">取消</el-button>
        </div>
      </el-card>

      <!-- 基本资料 -->
      <UserInfoEditor :is-mobile="true" />

      <!-- 账号信息 -->
      <el-card class="profile-card student-stat-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>账号信息</span>
          </div>
        </template>
        <div class="info-list">
          <div class="info-item">
            <span class="info-label">用户ID</span>
            <span class="info-value">{{ userStore.userInfo?.id }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">注册时间</span>
            <span class="info-value">{{ userStore.userInfo?.createdAt }}</span>
          </div>
        </div>
      </el-card>

      <!-- 修改密码 -->
      <PasswordEditor :is-mobile="true" />

      <template v-if="userStore.role === 'STUDENT'">
        <!-- 成就 -->
        <AchievementBadges :is-mobile="true" />

        <!-- 错题集 -->
        <WrongQuestionsCard :is-mobile="true" />

        <!-- 我的证书 -->
        <CertificatesCard :is-mobile="true" />
      </template>
    </template>
    <!-- 骨架屏：移动端 userInfo 加载中 -->
    <template v-if="!userStore.userInfo && isMobile">
      <div class="profile-skeleton-mobile">
        <div class="profile-card" style="padding: 16px; background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); margin-bottom: 16px;">
          <el-skeleton animated :rows="2" />
        </div>
        <div class="profile-card" style="padding: 16px; background: var(--el-bg-color-overlay); border-radius: var(--radius-lg); margin-bottom: 16px;">
          <el-skeleton animated :rows="4" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { uploadAvatar } from '../../api/auth'
import UserInfoEditor from '@/components/profile/UserInfoEditor.vue'
import PasswordEditor from '@/components/profile/PasswordEditor.vue'
import AchievementBadges from '@/components/profile/AchievementBadges.vue'
import WrongQuestionsCard from '@/components/profile/WrongQuestionsCard.vue'
import CertificatesCard from '@/components/profile/CertificatesCard.vue'

const userStore = useUserStore()

// 头像上传
const avatarPreview = ref('')
const avatarLoading = ref(false)
const avatarFile = ref(null)
const avatarMaxSize = 2 * 1024 * 1024 // 2MB

const handleAvatarChange = (uploadFile) => {
  const file = uploadFile?.raw
  if (!file) return
  if (file.size > avatarMaxSize) {
    ElMessage.error('图片大小不能超过 2MB')
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.error('只支持 JPG、PNG、WebP 格式')
    return
  }
  avatarFile.value = file
  const reader = new FileReader()
  reader.onload = (e) => {
    avatarPreview.value = e.target.result
  }
  reader.readAsDataURL(file)
}

/**
 * Canvas 压缩头像到 200×200 JPEG
 */
const compressAvatar = (file) => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)  // 及时释放 blob URL
      const canvas = document.createElement('canvas')
      canvas.width = 200
      canvas.height = 200
      const ctx = canvas.getContext('2d')
      // 居中裁剪
      const size = Math.min(img.width, img.height)
      const sx = (img.width - size) / 2
      const sy = (img.height - size) / 2
      ctx.drawImage(img, sx, sy, size, size, 0, 0, 200, 200)
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(new File([blob], 'avatar.jpg', { type: 'image/jpeg' }))
          } else {
            reject(new Error('图片压缩失败'))
          }
        },
        'image/jpeg',
        0.8
      )
    }
    img.onerror = () => {
      URL.revokeObjectURL(img.src)  // 及时释放 blob URL
      reject(new Error('图片加载失败'))
    }
    img.src = URL.createObjectURL(file)
  })
}

const handleSaveAvatar = async () => {
  if (!avatarFile.value) {
    ElMessage.warning('请先选择头像')
    return
  }
  avatarLoading.value = true
  try {
    const compressed = await compressAvatar(avatarFile.value)
    await uploadAvatar(compressed)
    await userStore.getInfo()
    avatarPreview.value = ''
    avatarFile.value = null
    ElMessage.success('头像更新成功')
  } catch (e) {
    const msg = e?.message || '头像更新失败'
    if (msg.includes('格式')) {
      ElMessage.error('图片格式不支持，请使用 JPG/PNG/WebP')
    } else if (msg.includes('大小') || msg.includes('2MB') || msg.includes('2 MB')) {
      ElMessage.error('图片过大，请使用不超过 2MB 的图片')
    } else {
      // 拦截器已展示后端错误，避免重复
    }
  } finally {
    avatarLoading.value = false
  }
}

const handleCancelAvatar = () => {
  avatarPreview.value = ''
  avatarFile.value = null
}

// 响应式布局
const isMobile = ref(window.innerWidth <= 768)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)

  // 确保 userInfo 已加载（账号信息卡片 + UserInfoEditor 依赖）
  if (!userStore.userInfo) {
    await userStore.getInfo()
  }
  // 成就/错题/证书数据由各自子组件自行加载
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
/* === Profile View === */
.profile-view {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
}

/* === Page Title === */
.page-title {
  margin: 0 0 var(--space-5) 0;
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.page-title--mobile {
  font-size: var(--text-lg);
  margin: 0 0 var(--space-4) 0;
}

/* === Cards === */
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

/* === Avatar Section === */
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.avatar-tip {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  text-align: center;
}

.avatar-uploader :deep(.el-upload) {
  cursor: pointer;
  border: 2px dashed var(--el-border-color);
  border-radius: var(--radius-pill);
  padding: 2px;
  transition: border-color var(--duration-base) var(--ease-out);
}

.avatar-uploader :deep(.el-upload:hover) {
  border-color: var(--role-primary);
}

.avatar-uploader-mobile :deep(.el-upload) {
  cursor: pointer;
}

.avatar-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: center;
}

.avatar-actions-mobile {
  display: flex;
  gap: var(--space-2);
  justify-content: center;
  margin-top: var(--space-3);
}

/* === Info List === */
.info-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.info-item {
  display: flex;
  justify-content: space-between;
  font-size: var(--text-sm);
}

.info-label {
  color: var(--el-text-color-secondary);
}

.info-value {
  color: var(--el-text-color-primary);
}

/* === Student Stat Card === */
.student-stat-card {
  background: linear-gradient(135deg, var(--role-primary-light-9) 0%, var(--role-primary-light-7) 100%);
  border: 1px solid rgba(99, 102, 241, 0.08);
}

.student-stat-card .info-item {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
  transition: background var(--duration-base) ease;
}

.student-stat-card .info-item:hover {
  background: rgba(255, 255, 255, 0.95);
}

/* === User Info Card (Mobile) === */
.user-info-card {
  margin-bottom: var(--space-4);
}

.user-info-content {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.user-info-text {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.user-info-name {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.user-info-role {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

/* === All Buttons Cursor === */
:deep(.el-button) {
  cursor: pointer;
}

/* === Profile Skeleton (PC) === */
.profile-skeleton {
  padding: var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
}

/* === Profile Skeleton (Mobile) === */
.profile-skeleton-mobile {
  padding: var(--space-3);
}

/* === Mobile Responsive === */
@media (max-width: 768px) {
  .profile-view {
    padding: var(--space-3);
  }

  .profile-card {
    margin-bottom: var(--space-4);
  }
}
</style>
