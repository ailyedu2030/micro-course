<!--
  用户表单（新增/编辑）
  路由路径: /users/form/:id?
  Phase 1
  Author: jackie
-->
<template>
  <div class="user-form">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <el-button :icon="Back" text @click="router.back()">返回</el-button>
          <span>{{ isEdit ? '编辑用户' : '新增用户' }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
        class="user-form-body"
      >
        <!-- Section 1: 基础信息 -->
        <div class="form-section">
          <div class="form-section-title">基础信息</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="账号" prop="username">
                <el-input
                  v-model="formData.username"
                  :placeholder="isEdit ? '编辑时不可修改' : '请输入账号'"
                  :disabled="isEdit"
                />
              </el-form-item>
            </el-col>
            <el-col v-if="!isEdit" :span="12">
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="formData.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-if="!isEdit" :gutter="20">
            <el-col :span="12">
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input
                  v-model="formData.confirmPassword"
                  type="password"
                  placeholder="请再次输入密码"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- Section 2: 所属信息 -->
        <div class="form-section">
          <div class="form-section-title">所属信息</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="角色" prop="role">
                <el-select
                  v-model="formData.role"
                  :placeholder="isEdit ? '编辑时不可修改' : '请选择角色'"
                  :disabled="isEdit"
                  class="full-width"
                >
                  <el-option label="学生" value="STUDENT" />
                  <el-option label="教师" value="TEACHER" />
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="教务" value="ACADEMIC" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="院系" prop="departmentId">
                <el-select
                  v-model="formData.departmentId"
                  placeholder="请选择院系"
                  clearable
                  class="full-width"
                  @change="handleDepartmentChange"
                >
                  <el-option
                    v-for="dept in departments"
                    :key="dept.id"
                    :label="dept.name"
                    :value="dept.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="专业" prop="majorId">
                <el-select
                  v-model="formData.majorId"
                  placeholder="请先选择院系"
                  clearable
                  class="full-width"
                  :disabled="!formData.departmentId"
                  @change="handleMajorChange"
                >
                  <el-option
                    v-for="major in majors"
                    :key="major.id"
                    :label="major.name"
                    :value="major.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="班级" prop="classId">
                <el-select
                  v-model="formData.classId"
                  placeholder="请先选择专业"
                  clearable
                  class="full-width"
                  :disabled="!formData.majorId"
                >
                  <el-option
                    v-for="cls in classes"
                    :key="cls.id"
                    :label="cls.name"
                    :value="cls.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- Section 3: 个人信息 -->
        <div class="form-section">
          <div class="form-section-title">个人信息</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="姓名" prop="realName">
                <el-input v-model="formData.realName" placeholder="请输入姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="性别" prop="gender">
                <el-select v-model="formData.gender" placeholder="请选择" class="full-width">
                  <el-option label="男" value="MALE" />
                  <el-option label="女" value="FEMALE" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="formData.email" placeholder="请输入邮箱" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="手机" prop="phone">
                <el-input v-model="formData.phone" placeholder="请输入手机号" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="头像" prop="avatar">
                <el-input v-model="formData.avatar" placeholder="请输入头像URL" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="年级" prop="grade">
                <el-input v-model="formData.grade" placeholder="请输入年级" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="入学年份" prop="enrollmentYear">
                <el-input v-model="formData.enrollmentYear" placeholder="如: 2024" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <el-form-item class="form-actions">
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back } from '@element-plus/icons-vue'
import { getUserById, createUser, updateUser } from '@/api/user'
import { getDepartments } from '@/api/department'
import { getMajors } from '@/api/major'
import { getClasses } from '@/api/class'

const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const submitLoading = ref(false)
const departments = ref([])
const majors = ref([])
const classes = ref([])

const isEdit = computed(() => route.path.includes('/edit'))

const formData = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  email: '',
  phone: '',
  role: '',
  gender: '',
  departmentId: '',
  majorId: '',
  classId: '',
  grade: '',
  enrollmentYear: '',
  avatar: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== formData.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const formRules = {
  username: [
    { required: true, message: '请输入账号', trigger: ['blur', 'change'] },
    { min: 3, max: 50, message: '账号长度为3-50个字符', trigger: ['blur', 'change'] }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: ['blur', 'change'] },
    { min: 6, max: 100, message: '密码长度为6-100个字符', trigger: ['blur', 'change'] }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: ['blur', 'change'] }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: ['blur', 'change'] }],
  role: [{ required: true, message: '请选择角色', trigger: ['blur', 'change'] }]
}

const fetchDepartments = async () => {
  try {
    const { data } = await getDepartments({ size: 1000 })
    departments.value = data.items || []
  } catch {
    ElMessage.error('获取院系列表失败')
  }
}

const fetchMajors = async (departmentId) => {
  if (!departmentId) {
    majors.value = []
    formData.majorId = ''
    classes.value = []
    formData.classId = ''
    return
  }
  try {
    const { data } = await getMajors({ departmentId, size: 1000 })
    majors.value = data.items || []
  } catch {
    ElMessage.error('获取专业列表失败')
  }
}

const fetchClasses = async (majorId) => {
  if (!majorId) {
    classes.value = []
    formData.classId = ''
    return
  }
  try {
    const { data } = await getClasses({ majorId, size: 1000 })
    classes.value = data.items || []
  } catch {
    ElMessage.error('获取班级列表失败')
  }
}

const handleDepartmentChange = (departmentId) => {
  formData.majorId = ''
  formData.classId = ''
  classes.value = []
  fetchMajors(departmentId)
}

const handleMajorChange = (majorId) => {
  formData.classId = ''
  fetchClasses(majorId)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const submitData = { ...formData }
      if (isEdit.value) {
        delete submitData.username
        delete submitData.password
        delete submitData.confirmPassword
        delete submitData.role
        await updateUser(route.params.id, submitData)
        ElMessage.success('操作成功')
      } else {
        delete submitData.confirmPassword
        await createUser(submitData)
        ElMessage.success('操作成功')
      }
      router.push('/users')
    } catch {
      ElMessage.error(isEdit.value ? '编辑失败，请稍后重试' : '创建失败，请稍后重试')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleCancel = () => {
  formRef.value?.resetFields()
  router.back()
}

const loadUserData = async (id) => {
  try {
    const { data } = await getUserById(id)
    formData.username = data.username || ''
    formData.realName = data.realName || ''
    formData.email = data.email || ''
    formData.phone = data.phone || ''
    formData.gender = data.gender || ''
    formData.departmentId = data.departmentId || ''
    formData.majorId = data.majorId || ''
    formData.classId = data.classId || ''
    formData.grade = data.grade || ''
    formData.enrollmentYear = data.enrollmentYear || ''
    formData.avatar = data.avatar || ''
    formData.role = data.role || ''

    if (data.departmentId) {
      await fetchMajors(data.departmentId)
    }
    if (data.majorId) {
      await fetchClasses(data.majorId)
    }
  } catch {
    ElMessage.error('获取用户信息失败')
  }
}

onMounted(async () => {
  await fetchDepartments()
  if (isEdit.value) {
    await loadUserData(route.params.id)
  }
})
</script>

<style scoped>
.user-form {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.user-form :deep(.el-card) {
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.user-form :deep(.el-card:hover) {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.user-form-body {
  max-width: 600px;
  padding: var(--space-5) 0;
}

.form-section {
  margin-bottom: var(--space-6);
}

.form-section-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-4);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--el-border-color-lighter);
  letter-spacing: var(--tracking-tight);
}

.full-width {
  width: 100%;
}

.form-actions {
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 1px solid var(--el-border-color-lighter);
}

.user-form :deep(.el-form-item__label) {
  font-weight: var(--weight-medium);
  color: var(--el-text-color-regular);
}

.user-form :deep(.el-input__wrapper),
.user-form :deep(.el-select__wrapper) {
  border-radius: var(--radius-sm);
}
</style>