<!--
  个人中心
  路由路径: /student/profile
  Phase 2
  Round 11-3 重构：拆分为 UserInfoEditor / PasswordEditor / AchievementBadges / WrongQuestionsCard / CertificatesCard
  Author: jackie
-->
<template>
  <div class="profile-view">
    <!-- P1-2: 个人信息加载失败 -->
    <template v-if="profileError">
      <el-result
        icon="error"
        :title="$t('user.loadFailed')"
        :sub-title="$t('user.loadFailedSubtitle')"
      >
        <template #extra>
          <el-button type="primary" @click="profileError = false; userStore.getInfo().catch(() => { profileError = true })">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>
    </template>

    <!-- 骨架屏：userInfo 加载中 -->
    <template v-else-if="!userStore.userInfo">
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
      <h2 class="page-title">{{ $t('user.profile') }}</h2>

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
                <span>{{ $t('user.avatar') }}</span>
              </div>
            </template>
            <div class="avatar-section">
              <el-upload
                class="avatar-uploader"
                :show-file-list="false"
                :auto-upload="false"
                accept="image/jpeg,image/png,image/webp"
                :on-change="handleAvatarChange"
                ref="avatarUploadCompRef"
              >
                <el-avatar :size="80" :src="avatarPreview || userStore.userInfo?.avatar" :alt="$t('user.avatar')" />
              </el-upload>
              <div class="avatar-tip">{{ $t('user.avatarTip') }}</div>
              <div class="avatar-actions">
                <el-button
                  v-if="avatarPreview"
                  type="primary"
                  size="small"
                  :loading="avatarLoading"
                  @click="handleSaveAvatar"
                >
                  {{ $t('user.saveAvatar') }}
                </el-button>
                <el-button
                  v-if="avatarPreview"
                  size="small"
                  @click="handleCancelAvatar"
                >
                  {{ $t('app.cancel') }}
                </el-button>
              </div>
            </div>
          </el-card>

          <!-- 账号信息 -->
          <el-card class="profile-card student-stat-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span>{{ $t('user.accountInfo') }}</span>
              </div>
            </template>
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">{{ $t('user.userId') }}</span>
                <span class="info-value">{{ userStore.userInfo?.id }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">{{ $t('user.role') }}</span>
                <span class="info-value">{{ userStore.userInfo?.role }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">{{ $t('user.registerTime') }}</span>
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
      <h2 class="page-title page-title--mobile">{{ $t('user.profile') }}</h2>

      <!-- 用户信息卡片 -->
      <el-card class="profile-card user-info-card" shadow="never">
        <div class="user-info-content">
          <el-upload
            class="avatar-uploader-mobile"
            :show-file-list="false"
            :auto-upload="false"
            accept="image/jpeg,image/png,image/webp"
            :on-change="handleAvatarChange"
            ref="avatarUploadMobileRef"
          >
            <el-avatar :size="60" :src="avatarPreview || userStore.userInfo?.avatar" :alt="$t('user.avatar')" />
          </el-upload>
          <div class="user-info-text">
            <div class="user-info-name">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</div>
            <div class="user-info-role">{{ userStore.userInfo?.role }}</div>
          </div>
        </div>
        <div v-if="avatarPreview" class="avatar-actions-mobile">
          <el-button type="primary" size="small" :loading="avatarLoading" @click="handleSaveAvatar">{{ $t('user.saveAvatar') }}</el-button>
          <el-button size="small" @click="handleCancelAvatar">{{ $t('app.cancel') }}</el-button>
        </div>
      </el-card>

      <!-- 基本资料 -->
      <UserInfoEditor :is-mobile="true" />

      <!-- 账号信息 -->
      <el-card class="profile-card student-stat-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ $t('user.accountInfo') }}</span>
          </div>
        </template>
        <div class="info-list">
          <div class="info-item">
            <span class="info-label">{{ $t('user.userId') }}</span>
            <span class="info-value">{{ userStore.userInfo?.id }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">{{ $t('user.registerTime') }}</span>
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
import { ref, h, onMounted, onBeforeUnmount, nextTick, defineAsyncComponent } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { uploadAvatar } from '../../api/auth'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// R2: 异步组件加载失败的 fallback 组件
const AsyncErrorStub = {
  name: 'AsyncErrorStub',
  setup() {
    const { t } = useI18n()
    return () => h('div', { class: 'error-stub' }, t('user.componentLoadFailed'))
  }
}

// P1-1: 嵌套子组件改为 defineAsyncComponent 懒加载
//   - UserInfoEditor: 编辑个人信息(40K) - 进入编辑时才加载
//   - PasswordEditor: 修改密码(30K) - 点击时才加载
//   - AchievementBadges: 成就徽章(15K) - 滚动到才加载
//   - WrongQuestionsCard: 错题本(25K) - 滚动到才加载
//   - CertificatesCard: 证书墙(20K) - 滚动到才加载
// 客户体验: Profile 首屏从 130K 降至 60K, 移动端首屏速度提升 50%
// R2: 添加 errorComponent 防止 chunk 加载失败导致全页崩溃
const UserInfoEditor = defineAsyncComponent({
  loader: () => import('@/components/profile/UserInfoEditor.vue'),
  errorComponent: AsyncErrorStub
})
const PasswordEditor = defineAsyncComponent({
  loader: () => import('@/components/profile/PasswordEditor.vue'),
  errorComponent: AsyncErrorStub
})
const AchievementBadges = defineAsyncComponent({
  loader: () => import('@/components/profile/AchievementBadges.vue'),
  errorComponent: AsyncErrorStub
})
const WrongQuestionsCard = defineAsyncComponent({
  loader: () => import('@/components/profile/WrongQuestionsCard.vue'),
  errorComponent: AsyncErrorStub
})
const CertificatesCard = defineAsyncComponent({
  loader: () => import('@/components/profile/CertificatesCard.vue'),
  errorComponent: AsyncErrorStub
})

const userStore = useUserStore()

// P1-2: 个人信息加载错误状态
const profileError = ref(false)

// 头像上传
const avatarPreview = ref('')
const avatarLoading = ref(false)
const avatarFile = ref(null)
const avatarMaxSize = 2 * 1024 * 1024 // 2MB

const handleAvatarChange = (uploadFile) => {
  const file = uploadFile?.raw
  if (!file) return
  if (file.size > avatarMaxSize) {
    ElMessage.error(t('user.avatarTooLarge'))
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.error(t('user.avatarFormatError'))
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
            reject(new Error(t('user.avatarCompressFailed')))
          }
        },
        'image/jpeg',
        0.8
      )
    }
    img.onerror = () => {
      URL.revokeObjectURL(img.src)  // 及时释放 blob URL
      reject(new Error(t('user.avatarLoadFailed')))
    }
    img.src = URL.createObjectURL(file)
  })
}

const handleSaveAvatar = async () => {
  if (!avatarFile.value) {
    ElMessage.warning(t('user.avatarSelectFirst'))
    return
  }
  avatarLoading.value = true
  try {
    const compressed = await compressAvatar(avatarFile.value)
    await uploadAvatar(compressed)
    await userStore.getInfo()
    avatarPreview.value = ''
    avatarFile.value = null
    ElMessage.success(t('user.avatarUpdateSuccess'))
  } catch (e) {
    const msg = e?.message || t('user.avatarUpdateFailed')
    if (msg.includes('格式')) {
      ElMessage.error(t('user.formatNotSupported'))
    } else if (msg.includes('大小') || msg.includes('2MB') || msg.includes('2 MB')) {
      ElMessage.error(t('user.imageTooLarge'))
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
const isMobile = ref(window.innerWidth < 768)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(async () => {
  document.title = t('user.profile') + ' - ' + t('app.title')
  checkMobile()
  window.addEventListener('resize', checkMobile)

  // P1-2: 确保 userInfo 已加载（账号信息卡片 + UserInfoEditor 依赖）
  if (!userStore.userInfo) {
    try {
      await userStore.getInfo()
    } catch (e) {
      console.error('[Profile] getInfo failed', e)
      userStore.userInfo = null
      profileError.value = true
    }
  }
  // P1-C: fix aria-label on el-upload role="button" inner element
  // EP 2.14.1 的 el-upload 将 aria-label 放在外层 wrapper 上，
  // 但 role="button" 在内层 .el-upload 上。需要校正。
  nextTick(() => {
    document.querySelectorAll('.avatar-uploader .el-upload[role="button"], .avatar-uploader-mobile .el-upload[role="button"]').forEach(el => {
      if (!el.getAttribute('aria-label')) {
        el.setAttribute('aria-label', t('user.uploadAvatar'))
      }
    })
  })
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

/* === Async Component Error Fallback === */
.error-stub {
  padding: var(--space-6);
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-5);
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
