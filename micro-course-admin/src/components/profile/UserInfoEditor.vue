<!--
  基本资料编辑器（Round 11-3 从 Profile.vue 拆分）
  自包含表单逻辑：profileForm / 校验 / 提交
  通过 isMobile prop 还原 PC / 移动端的 label-width 与 size 差异
  Author: jackie
-->
<template>
  <el-card class="profile-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>基本资料</span>
      </div>
    </template>
    <el-form
      :model="profileForm"
      :rules="profileRules"
      ref="profileFormRef"
      :label-width="isMobile ? '70px' : '80px'"
      :size="isMobile ? 'small' : ''"
    >
      <el-form-item label="用户名">
        <el-input :model-value="userStore.userInfo?.username" disabled />
      </el-form-item>
      <el-form-item label="姓名" prop="realName">
        <el-input v-model="profileForm.realName" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
      </el-form-item>
      <el-form-item label="性别" prop="gender">
        <el-select v-model="profileForm.gender" placeholder="请选择性别">
          <el-option label="保密" value="SECRET" />
          <el-option label="男" value="MALE" />
          <el-option label="女" value="FEMALE" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          @click="handleUpdateProfile"
          :loading="profileLoading"
          :size="isMobile ? 'default' : ''"
        >
          保存修改
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { updateProfile } from '@/api/auth'

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
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
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
  try {
    await profileFormRef.value.validate()
  } catch {
    return
  }
  profileLoading.value = true
  try {
    await updateProfile(profileForm.value)
    // P0-2: 后端已更新成功,但前端 store 需刷新才能显示新数据
    // 之前 getInfo 失败被 catch 吞掉,导致用户看到「成功」但页面是旧数据
    // 修复: getInfo 失败时明确告知用户,避免数据不一致错觉
    try {
      await userStore.getInfo()
      ElMessage.success('资料更新成功')
    } catch (refreshErr) {
      console.warn('[Profile] 后端已更新但本地刷新失败', refreshErr)
      ElMessage.warning('资料已保存,但页面需手动刷新才能看到最新信息')
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
