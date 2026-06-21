<!--
  密码修改器（Round 11-3 从 Profile.vue 拆分）
  自包含表单逻辑：passwordForm / 校验 / 提交
  通过 isMobile prop 还原 PC / 移动端的 label-width 与 size 差异
  Author: jackie
-->
<template>
  <el-card class="profile-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>修改密码</span>
      </div>
    </template>
    <el-form
      :model="passwordForm"
      :rules="passwordRules"
      ref="passwordFormRef"
      :label-width="isMobile ? '80px' : '100px'"
      :size="isMobile ? 'small' : ''"
    >
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          @click="handleChangePassword"
          :loading="passwordLoading"
          :size="isMobile ? 'default' : ''"
        >
          修改密码
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'

defineProps({
  isMobile: {
    type: Boolean,
    default: false
  }
})

const passwordFormRef = ref(null)
const passwordLoading = ref(false)

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.value.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码长度不能少于8位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)/, message: '密码需包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleChangePassword = async () => {
  passwordLoading.value = true
  try {
    await passwordFormRef.value.validate()
  } catch {
    passwordLoading.value = false
    return
  }
  try {
    await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功')
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    passwordFormRef.value?.resetFields()
  } catch {
    // 拦截器已展示后端具体错误（如"旧密码错误"），此处不重复提示
  } finally {
    passwordLoading.value = false
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
