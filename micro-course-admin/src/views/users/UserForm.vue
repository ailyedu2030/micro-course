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
          <el-button :icon="Back" text @click="router.back()">{{ $t('app.back') }}</el-button>
          <span>{{ isEdit ? $t('userForm.editTitle') : $t('userForm.createTitle') }}</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
        class="user-form-body"
        v-loading="pageLoading"
        :element-loading-text="$t('userForm.loadingUserData')"
      >
        <!-- Section 1: 基础信息 -->
        <div class="form-section">
          <div class="form-section-title">{{ $t('userList.basicInfo') }}</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('userList.account')" prop="username">
                <el-input
                  v-model="formData.username"
                  :placeholder="isEdit ? $t('userForm.editLocked') : $t('userList.accountRequired')"
                  :disabled="isEdit"
                />
              </el-form-item>
            </el-col>
            <el-col v-if="!isEdit" :span="12">
              <el-form-item :label="$t('auth.password')" prop="password">
                <el-input
                  v-model="formData.password"
                  type="password"
                  :placeholder="$t('userForm.passwordPlaceholder')"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-if="!isEdit" :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('auth.confirmPassword')" prop="confirmPassword">
                <el-input
                  v-model="formData.confirmPassword"
                  type="password"
                  :placeholder="$t('userForm.confirmPasswordPlaceholder')"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- Section 2: 所属信息 -->
        <div class="form-section">
          <div class="form-section-title">{{ $t('userForm.departmentSection') }}</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('user.role')" prop="role">
                <el-select
                  v-model="formData.role"
                  :placeholder="isEdit ? $t('userForm.editLocked') : $t('userForm.selectRole')"
                  :disabled="isEdit"
                  class="full-width"
                >
                  <el-option :label="$t('userSearch.student')" value="STUDENT" />
                  <el-option :label="$t('userSearch.teacher')" value="TEACHER" />
                  <el-option :label="$t('userSearch.admin')" value="ADMIN" />
                  <el-option :label="$t('userSearch.academic')" value="ACADEMIC" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('userSearch.department')" prop="departmentId">
                <el-select
                  v-model="formData.departmentId"
                  :placeholder="$t('userList.departmentPlaceholder')"
                  clearable
                  class="full-width"
                  @change="(val) => handleDeptChange(val, formData)"
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
              <el-form-item :label="$t('userSearch.major')" prop="majorId">
                <el-select
                  v-model="formData.majorId"
                  :placeholder="$t('userList.majorSelectDeptFirst')"
                  clearable
                  class="full-width"
                  :disabled="!formData.departmentId"
                  @change="(val) => handleMajorChange(val, formData)"
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
              <el-form-item :label="$t('userSearch.classLabel')" prop="classId">
                <el-select
                  v-model="formData.classId"
                  :placeholder="$t('userList.classSelectMajorFirst')"
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
          <div class="form-section-title">{{ $t('userForm.personalInfo') }}</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('user.realName')" prop="realName">
                <el-input v-model="formData.realName" :placeholder="$t('userList.realNamePlaceholder')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('user.gender')" prop="gender">
                <el-select v-model="formData.gender" :placeholder="$t('userSearch.pleaseSelect')" class="full-width" clearable>
                  <el-option :label="$t('user.genderMale')" value="MALE" />
                  <el-option :label="$t('user.genderFemale')" value="FEMALE" />
                  <el-option :label="$t('user.genderSecret')" value="SECRET" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('user.email')" prop="email">
                <el-input v-model="formData.email" :placeholder="$t('user.pleaseInputEmail')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('userList.phoneLabel')" prop="phone">
                <el-input v-model="formData.phone" :placeholder="$t('userList.phonePlaceholder')" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('userForm.avatar')" prop="avatar">
                <el-input v-model="formData.avatar" :placeholder="$t('userForm.avatarUrlPlaceholder')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('userList.politicalStatus')" prop="politicalStatus">
                <el-select v-model="formData.politicalStatus" :placeholder="$t('userSearch.pleaseSelect')" clearable class="full-width">
                  <el-option :label="$t('userList.politicalMasses')" value="群众" />
                  <el-option :label="$t('userList.politicalCYL')" value="共青团员" />
                  <el-option :label="$t('userList.politicalCPC')" value="中共党员" />
                  <el-option :label="$t('userList.politicalCPCProbationary')" value="中共预备党员" />
                  <el-option :label="$t('userList.politicalRCC')" value="民革党员" />
                  <el-option :label="$t('userList.politicalCDL')" value="民盟盟员" />
                  <el-option :label="$t('userList.politicalCDNCA')" value="民建会员" />
                  <el-option :label="$t('userList.politicalCAPD')" value="民进会员" />
                  <el-option :label="$t('userList.politicalCPWDP')" value="农工党党员" />
                  <el-option :label="$t('userList.politicalZS')" value="致公党党员" />
                  <el-option :label="$t('userList.politicalJSS')" value="九三学社社员" />
                  <el-option :label="$t('userList.politicalTWM')" value="台盟盟员" />
                  <el-option :label="$t('userList.politicalNonPartisan')" value="无党派人士" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 学生专属字段 -->
          <template v-if="formData.role === 'STUDENT'">
            <el-divider content-position="left">{{ $t('userList.studentInfo') }}</el-divider>
<el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('userList.studentNo')" prop="studentNo">
                <el-input v-model="formData.studentNo" :placeholder="$t('userList.studentNoPlaceholder')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('userList.enrollmentYear')" prop="enrollmentYear">
                <el-input
                  v-model="formData.enrollmentYear"
                  :placeholder="$t('userList.enrollmentYearPlaceholder')"
                  @input="handleEnrollmentYearChange"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('userList.grade')" prop="grade">
                <el-input
                  v-model="formData.grade"
                  :placeholder="$t('userList.gradeAuto')"
                  :disabled="!formData.enrollmentYear"
                >
                  <template #append>
                    <el-tag v-if="gradeHint" :type="gradeHintType" size="small">{{ gradeHint }}</el-tag>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('userList.graduationYear')" prop="graduationYear">
                <el-input
                  v-model="formData.graduationYear"
                  :placeholder="$t('userList.graduationYearPlaceholder')"
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
            <el-divider content-position="left">{{ $t('userList.teacherInfo') }}</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$t('userList.teacherNo')" prop="teacherNo">
                  <el-input v-model="formData.teacherNo" :placeholder="$t('userList.teacherNoPlaceholder')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userList.teacherStatus')" prop="teacherStatus">
                  <el-select v-model="formData.teacherStatus" class="full-width">
                    <el-option :label="$t('userForm.teacherStatusPending')" :value="0" />
                    <el-option :label="$t('userForm.teacherStatusApproved')" :value="1" />
                    <el-option :label="$t('userList.teacherStatusRejected')" :value="2" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- 教务专属字段 -->
          <template v-if="formData.role === 'ACADEMIC'">
            <el-divider content-position="left">{{ $t('userList.academicInfo') }}</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$t('userList.teacherNo')" prop="teacherNo">
                  <el-input v-model="formData.teacherNo" :placeholder="$t('userList.teacherNoPlaceholder')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userList.managedDepartments')">
                  <el-select v-model="formData.departmentId" :placeholder="$t('userSearch.pleaseSelect')" clearable class="full-width">
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
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('app.save') }}</el-button>
          <el-button @click="handleCancel">{{ $t('app.cancel') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Back } from '@element-plus/icons-vue'
import { getUserById, createUser, updateUser } from '@/api/user'
import { getDepartments } from '@/api/department'
import { useGradeCascade } from '@/composables/useGradeCascade'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const formRef = ref(null)
const submitLoading = ref(false)

// 使用 useGradeCascade 统一级联逻辑（院系→专业→班级）
const { majors, classes, fetchMajors, fetchClasses, handleDeptChange, handleMajorChange } = useGradeCascade()
const departments = ref([])

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
    callback(new Error(t('userForm.confirmPasswordPlaceholder')))
  } else if (value !== formData.password) {
    callback(new Error(t('userForm.passwordMismatch')))
  } else {
    callback()
  }
}

const formRules = {
  username: [
    { required: true, message: t('userList.accountRequired'), trigger: ['blur', 'change'] },
    { min: 3, max: 50, message: t('userForm.usernameLength'), trigger: ['blur', 'change'] }
  ],
  password: [
    { required: true, message: t('userForm.passwordPlaceholder'), trigger: ['blur', 'change'] },
    { min: 6, max: 100, message: t('userForm.passwordLength'), trigger: ['blur', 'change'] }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: ['blur', 'change'] }
  ],
  realName: [{ required: true, message: t('userList.realNamePlaceholder'), trigger: ['blur', 'change'] }],
  role: [{ required: true, message: t('userForm.selectRole'), trigger: ['blur', 'change'] }],
  // P1-C 修复：B3.7 邮箱/手机格式校验此前完全缺失（非法值静默通过），补齐格式规则
  email: [
    { pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: t('user.pleaseInputValidEmail'), trigger: ['blur', 'change'] }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: t('userList.phoneFormat'), trigger: ['blur', 'change'] }
  ]
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
    ElMessage.warning(t('userList.graduationAfterEnrollment'))
    formData.graduationYear = String(ey + STUDY_YEARS_DEFAULT)
  }
  formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
}
const gradeHint = computed(() => {
  const ey = parseYear(formData.enrollmentYear)
  if (ey === null) return ''
  if (formData.grade) return t('userList.currentGrade', { grade: formData.grade })
  return t('userList.gradeAuto')
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
  return t('userList.yearsSystem', { years })
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
    const { data } = await getDepartments({ size: 100 })
    departments.value = data.items || []
  } catch {
    ElMessage.error(t('userForm.fetchDepartmentsFailed'))
  }
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      const submitData = { ...formData }
      // P1 修复: teacherStatus 保留在 payload 中，允许编辑时更新教师审核状态
      delete submitData.avatar
      if (isEdit.value) {
        delete submitData.username
        delete submitData.password
        delete submitData.confirmPassword
        delete submitData.role
        await updateUser(route.params.id, submitData)
        ElMessage.success(t('common.success'))
      } else {
        delete submitData.confirmPassword
        await createUser(submitData)
        ElMessage.success(t('common.success'))
      }
      router.push('/users')
    } catch {
      ElMessage.error(isEdit.value ? t('userForm.editFailedRetry') : t('userForm.createFailedRetry'))
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
    ElMessage.error(t('userForm.fetchUserFailed'))
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
