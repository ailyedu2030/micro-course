<!--
  基本资料编辑器（Round 11-3 从 Profile.vue 拆分）
  自包含表单逻辑：profileForm / 校验 / 提交
  通过 isMobile prop 还原 PC / 移动端的 label-width 与 size 差异
  R4 修复: 所有 label/placeholder/message 走 i18n，英文用户也能用
  Author: jackie
-->
<template>
  <el-card class="profile-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>{{ $t('user.basicInfo') }}</span>
      </div>
    </template>
    <el-form
      :model="profileForm"
      :rules="profileRules"
      ref="profileFormRef"
      :label-width="isMobile ? '70px' : '80px'"
      :size="isMobile ? 'small' : ''"
    >
      <el-form-item :label="$t('user.username')">
        <el-input :model-value="userStore.userInfo?.username" disabled :aria-label="$t('user.username')" />
      </el-form-item>
      <el-form-item :label="$t('user.realName')" prop="realName">
        <el-input v-model="profileForm.realName" :placeholder="$t('user.pleaseInputRealName')" :aria-label="$t('user.realName')" />
      </el-form-item>
      <el-form-item :label="$t('user.email')" prop="email">
        <el-input v-model="profileForm.email" :placeholder="$t('user.pleaseInputEmail')" :aria-label="$t('user.email')" />
      </el-form-item>
      <el-form-item :label="$t('user.phone')" prop="phone">
        <el-input v-model="profileForm.phone" :placeholder="$t('user.pleaseInputPhone')" :aria-label="$t('user.phone')" />
      </el-form-item>
      <el-form-item :label="$t('user.gender')" prop="gender">
        <el-select v-model="profileForm.gender" :placeholder="$t('user.pleaseSelectGender')" :aria-label="$t('user.gender')">
          <el-option :label="$t('user.genderSecret')" value="SECRET" />
          <el-option :label="$t('user.genderMale')" value="MALE" />
          <el-option :label="$t('user.genderFemale')" value="FEMALE" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          @click="handleUpdateProfile"
          :loading="profileLoading"
          :size="isMobile ? 'default' : ''"
        >
          {{ $t('user.saveChanges') }}
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { updateProfile } from '@/api/auth'

const { t } = useI18n()

defineProps({
  isMobile: {
    type: Boolean,
    default: false
  }
})

const userStore = useUserStore()

const profileFormRef = ref(null)
const profileLoading = ref(false)

const profileForm = ref({
  realName: '',
  email: '',
  phone: '',
  gender: ''
})

const profileRules = {
  realName: [
    { required: true, message: () => t('user.pleaseInputRealName'), trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: () => t('user.pleaseInputValidEmail'), trigger: 'blur' }
  ],
  phone: [
    { pattern: /^\+?[1-9]\d{4,14}$/, message: () => t('user.pleaseInputValidPhone'), trigger: 'blur' }
  ]
}

// 表单初始化：监听 userInfo（immediate），等价于原 onMounted 中的填充逻辑
watch(
  () => userStore.userInfo,
  (info) => {
    if (info) {
      profileForm.value = {
        realName: info.realName || '',
        email: info.email || '',
        phone: info.phone || '',
        gender: info.gender || 'SECRET'
      }
    }
  },
  { immediate: true }
)

const handleUpdateProfile = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (profileLoading.value) return
  if (!profileFormRef.value) return
  profileLoading.value = true
  try {
    const valid = await profileFormRef.value.validate()
    if (!valid) { profileLoading.value = false; return }
  } catch { profileLoading.value = false; return }
  try {
    await updateProfile(profileForm.value)
    // P0-2: 后端已更新成功,但前端 store 需刷新才能显示新数据
    // 之前 getInfo 失败被 catch 吞掉,导致用户看到「成功」但页面是旧数据
    // 修复: getInfo 失败时明确告知用户,避免数据不一致错觉
    try {
      await userStore.getInfo()
      ElMessage.success(t('user.profileUpdateSuccess'))
    } catch (refreshErr) {
      console.warn('[Profile] 后端已更新但本地刷新失败', refreshErr)
      ElMessage.warning(t('user.profileUpdateNeedRefresh'))
      // 强制刷新页面,确保用户看到一致数据
      setTimeout(() => window.location.reload(), 1500)
    }
  } catch {
    // 拦截器已展示后端具体错误，此处不重复提示
  } finally {
    profileLoading.value = false
  }
}
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

:deep(.el-button) {
  cursor: pointer;
}

@media (max-width: 768px) {
  .profile-card {
    margin-bottom: var(--space-4);
  }
}
</style>
