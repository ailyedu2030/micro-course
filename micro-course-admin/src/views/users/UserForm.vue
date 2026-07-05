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
        v-loading="pageLoading"
        element-loading-text="加载用户数据中..."
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
                <el-select v-model="formData.gender" placeholder="请选择" class="full-width" clearable>
                  <el-option label="男" value="MALE" />
                  <el-option label="女" value="FEMALE" />
                  <el-option label="保密" value="SECRET" />
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
              <el-form-item label="政治面貌" prop="politicalStatus">
                <el-select v-model="formData.politicalStatus" placeholder="请选择" clearable class="full-width">
                  <el-option label="群众" value="群众" />
                  <el-option label="共青团员" value="共青团员" />
                  <el-option label="中共党员" value="中共党员" />
                  <el-option label="中共预备党员" value="中共预备党员" />
                  <el-option label="民革党员" value="民革党员" />
                  <el-option label="民盟盟员" value="民盟盟员" />
                  <el-option label="民建会员" value="民建会员" />
                  <el-option label="民进会员" value="民进会员" />
                  <el-option label="农工党党员" value="农工党党员" />
                  <el-option label="致公党党员" value="致公党党员" />
                  <el-option label="九三学社社员" value="九三学社社员" />
                  <el-option label="台盟盟员" value="台盟盟员" />
                  <el-option label="无党派人士" value="无党派人士" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 学生专属字段 -->
          <template v-if="formData.role === 'STUDENT'">
            <el-divider content-position="left">学生信息</el-divider>
<el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="学号" prop="studentNo">
                <el-input v-model="formData.studentNo" placeholder="请输入学号" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="入学年份" prop="enrollmentYear">
                <el-input
                  v-model="formData.enrollmentYear"
                  placeholder="如：2024"
                  @input="handleEnrollmentYearChange"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="年级" prop="grade">
                <el-input
                  v-model="formData.grade"
                  placeholder="自动计算"
                  :disabled="!formData.enrollmentYear"
                >
                  <template #append>
                    <el-tag v-if="gradeHint" :type="gradeHintType" size="small">{{ gradeHint }}</el-tag>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="毕业年份" prop="graduationYear">
                <el-input
                  v-model="formData.graduationYear"
                  placeholder="如：2028（4 年制本科）"
                  @input="handleGraduationYearChange"
                >
                  <template #append>
                    <el-tag v-if="studyYearsHint" type="info" size="small">{{ studyYearsHint }}</el-tag>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
          </template>

          <!-- 教师专属字段 -->
          <template v-if="formData.role === 'TEACHER'">
            <el-divider content-position="left">教师信息</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="工号" prop="teacherNo">
                  <el-input v-model="formData.teacherNo" placeholder="请输入工号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="审核状态" prop="teacherStatus">
                  <el-select v-model="formData.teacherStatus" class="full-width">
                    <el-option label="待审核" :value="0" />
                    <el-option label="已通过" :value="1" />
                    <el-option label="已驳回" :value="2" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- 教务专属字段 -->
          <template v-if="formData.role === 'ACADEMIC'">
            <el-divider content-position="left">教务信息</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="工号" prop="teacherNo">
                  <el-input v-model="formData.teacherNo" placeholder="请输入工号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="管辖院系">
                  <el-select v-model="formData.departmentId" placeholder="请选择" clearable class="full-width">
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
          </template>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
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
  graduationYear: '',
  studentNo: '',
  teacherNo: '',
  politicalStatus: '',
  teacherStatus: null,
  status: 1,
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

// ============== 年级联动逻辑 ==============
const STUDY_YEARS_DEFAULT = 4
function parseYear(value) {
  const n = parseInt(value, 10)
  return Number.isFinite(n) && n > 1900 && n < 2200 ? n : null
}
const currentYear = new Date().getFullYear()
function calcGradeFromEnrollment(enrollmentYear, gradYear) {
  const ey = parseYear(enrollmentYear)
  if (ey === null) return ''
  const gy = parseYear(gradYear)
  const totalYears = gy !== null ? (gy - ey + 1) : STUDY_YEARS_DEFAULT
  const cur = currentYear - ey + 1
  if (cur <= 0) return '0'
  if (cur > totalYears) return String(totalYears)
  return String(cur)
}
function calcGradYearFromEnrollment(enrollmentYear) {
  const ey = parseYear(enrollmentYear)
  if (ey === null) return ''
  return String(ey + STUDY_YEARS_DEFAULT)
}
const handleEnrollmentYearChange = () => {
  const ey = parseYear(formData.enrollmentYear)
  if (ey === null) {
    formData.grade = ''
    return
  }
  if (!formData.graduationYear) {
    formData.graduationYear = calcGradYearFromEnrollment(formData.enrollmentYear)
  }
  formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
}
const handleGraduationYearChange = () => {
  const ey = parseYear(formData.enrollmentYear)
  const gy = parseYear(formData.graduationYear)
  if (ey === null || gy === null) return
  if (gy <= ey) {
    ElMessage.warning('毕业年份必须大于入学年份')
    formData.graduationYear = String(ey + STUDY_YEARS_DEFAULT)
  }
  formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
}
const gradeHint = computed(() => {
  const ey = parseYear(formData.enrollmentYear)
  if (ey === null) return ''
  if (formData.grade) return `当前${formData.grade}年级`
  return '自动计算'
})
const gradeHintType = computed(() => {
  const ey = parseYear(formData.enrollmentYear)
  const g = parseYear(formData.grade)
  if (ey === null) return 'info'
  if (g === null) return 'info'
  const cur = currentYear - ey + 1
  if (cur > (parseYear(formData.graduationYear) - ey + 1 || STUDY_YEARS_DEFAULT)) return 'warning'
  return 'success'
})
const studyYearsHint = computed(() => {
  const ey = parseYear(formData.enrollmentYear)
  const gy = parseYear(formData.graduationYear)
  if (ey === null || gy === null) return ''
  const years = gy - ey + 1
  if (years <= 0) return ''
  return `${years} 年制`
})
watch(() => formData.role, (newRole, oldRole) => {
  // 角色切换时清除上一角色的专属字段
  if (oldRole) {
    if (oldRole !== 'STUDENT') {
      formData.studentNo = ''
      formData.grade = ''
      formData.enrollmentYear = ''
      formData.graduationYear = ''
    }
    if (oldRole !== 'TEACHER' && oldRole !== 'ACADEMIC') {
      formData.teacherNo = ''
      formData.teacherStatus = null
    }
  }
  if (newRole === 'STUDENT' && formData.enrollmentYear) {
    formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
  }
})

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
      delete submitData.teacherStatus
      delete submitData.avatar
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
    formData.politicalStatus = data.politicalStatus || ''
    formData.departmentId = data.departmentId || ''
    formData.majorId = data.majorId || ''
    formData.classId = data.classId || ''
    formData.grade = data.grade || ''
    formData.enrollmentYear = data.enrollmentYear || ''
    formData.graduationYear = data.graduationYear || ''
    formData.studentNo = data.studentNo || ''
    formData.teacherNo = data.teacherNo || ''
    formData.teacherStatus = data.teacherStatus ?? null
    formData.status = data.status ?? 1
    formData.avatar = data.avatar || ''
    formData.role = data.role || ''

    // 联动重算：保证 grade 与 enrollmentYear 一致（防止历史脏数据）
    if (formData.role === 'STUDENT' && formData.enrollmentYear) {
      const expected = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
      if (expected) formData.grade = expected
    }

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

const pageLoading = ref(false)

onMounted(async () => {
  await fetchDepartments()
  if (isEdit.value) {
    pageLoading.value = true
    try {
      await loadUserData(route.params.id)
    } finally {
      pageLoading.value = false
    }
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