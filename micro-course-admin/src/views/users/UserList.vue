<!--
  用户列表
  路由路径: /users
  Phase 1
  使用 UserSearchBar + UserTable + BatchImportDialog + TeacherApprovalDialog + useGradeCascade
  Author: jackie
-->
<template>
  <div class="user-list">
    <!-- 搜索区（共享组件 + 院系级联） -->
    <UserSearchBar
      v-model="searchForm"
      :departments="departments"
      :majors="searchMajors"
      :classes="searchClasses"
      :show-dept-cascade="true"
      @search="handleSearch"
      @reset="handleReset"
      @department-change="handleSearchDeptChange"
      @major-change="handleSearchMajorChange"
    />

    <!-- 用户表格（共享组件） -->
    <UserTable
      :loading="loading"
      :error="error"
      :data="tableData"
      :total="totalElements"
      :page="page"
      :size="size"
      :show-avatar="true"
      :show-dept-detail="false"
      :status-clickable="userRole === 'ADMIN' || userRole === 'ACADEMIC'"
      @update:page="handlePageChange"
      @update:size="handleSizeChange"
      @retry="fetchData"
      @edit="handleEdit"
      @delete="handleSoftDelete"
      @toggle-status="handleToggleStatus"
    >
      <!-- 头像列插槽 -->
      <template #avatar="{ row }">
        <el-upload
          :show-file-list="false"
          :before-upload="(file) => handleAvatarUpload(file, row)"
          accept="image/*"
          class="avatar-uploader"
        >
          <el-avatar v-if="row.avatar" :src="row.avatar" :size="40" class="clickable-avatar" :title="$t('userList.uploadAvatarTitle')" />
          <el-avatar v-else :size="40" class="clickable-avatar" :title="$t('userList.uploadAvatarTitle')">{{ row.realName?.charAt(0) || 'U' }}</el-avatar>
        </el-upload>
      </template>

      <!-- 状态插槽 -->
      <template #status="{ row }">
        <el-tag
          v-if="row.status === 1 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')"
          type="success" size="small" style="cursor:pointer"
          @click="handleToggleStatus(row, 2)"
        >
          {{ $t('userList.statusNormal') }}
        </el-tag>
        <el-tag
          v-else-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'"
          type="danger" size="small" style="cursor:pointer"
          @click="handleToggleStatus(row, 1)"
        >
          {{ $t('userSearch.statusDisabled') }}
        </el-tag>
      </template>

      <!-- 操作栏插槽 -->
      <template #actions="{ row }">
        <el-button type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
        <el-button v-if="userRole === 'ADMIN'" type="danger" link size="small" @click="handleSoftDelete(row)">{{ $t('app.delete') }}</el-button>
      </template>
    </UserTable>

    <!-- 编辑弹窗（按角色动态显示字段 — 页面特有，保持内联） -->
    <el-dialog v-model="dialogVisible" :title="$t('userList.editDialogTitle', { name: formData.realName || formData.username })" width="780px" @close="handleDialogClose" :close-on-press-escape="true" top="5vh">
      <div v-loading="dialogLoading">
        <!-- 基础信息 -->
        <el-divider content-position="left">{{ $t('userList.basicInfo') }}</el-divider>
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('userList.account')" prop="username">
                <el-input v-model="formData.username" :disabled="true" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('user.realName')" prop="realName">
                <el-input v-model="formData.realName" :placeholder="$t('userList.realNamePlaceholder')" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('userSearch.role')" prop="role">
                <el-select v-model="formData.role" :disabled="true" class="full-width">
                  <el-option :label="$t('userSearch.student')" value="STUDENT" />
                  <el-option :label="$t('userSearch.teacher')" value="TEACHER" />
                  <el-option :label="$t('userSearch.admin')" value="ADMIN" />
                  <el-option :label="$t('userSearch.academic')" value="ACADEMIC" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('userSearch.status')" prop="status">
                <el-select v-model="formData.status" :disabled="formData.id === userStore.userId" class="full-width">
                  <el-option :label="$t('userSearch.statusActive')" :value="1" />
                  <el-option :label="$t('userSearch.statusDisabled')" :value="2" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('user.gender')">
                <el-select v-model="formData.gender" :placeholder="$t('userSearch.pleaseSelect')" clearable class="full-width">
                  <el-option :label="$t('user.genderMale')" value="MALE" />
                  <el-option :label="$t('user.genderFemale')" value="FEMALE" />
                  <el-option :label="$t('user.genderSecret')" value="SECRET" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('user.email')">
                <el-input v-model="formData.email" :placeholder="$t('user.pleaseInputEmail')" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('userList.phoneLabel')">
                <el-input v-model="formData.phone" :placeholder="$t('userList.phonePlaceholder')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('userList.politicalStatus')">
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
                <el-form-item :label="$t('userList.studentNo')">
                  <el-input v-model="formData.studentNo" :placeholder="$t('userList.studentNoPlaceholder')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userList.enrollmentYear')">
                  <el-input v-model="formData.enrollmentYear" :placeholder="$t('userList.enrollmentYearPlaceholder')" @input="handleEnrollmentYearChange" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$t('userList.grade')">
                  <el-input v-model="formData.grade" :placeholder="$t('userList.gradeAuto')" :disabled="!formData.enrollmentYear">
                    <template #append>
                      <el-tag v-if="gradeHint" :type="gradeHintType" size="small">{{ gradeHint }}</el-tag>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userList.graduationYear')">
                  <el-input v-model="formData.graduationYear" :placeholder="$t('userList.graduationYearPlaceholder')" @input="handleGraduationYearChange">
                    <template #append>
                      <el-tag v-if="studyYearsHint" type="info" size="small">{{ studyYearsHint }}</el-tag>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$t('userSearch.department')">
                  <el-select v-model="formData.departmentId" :placeholder="$t('userList.departmentPlaceholder')" clearable class="full-width" @change="handleDialogDeptChange">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userSearch.major')">
                  <el-select v-model="formData.majorId" :placeholder="$t('userList.majorSelectDeptFirst')" clearable class="full-width" :disabled="!formData.departmentId" @change="handleDialogMajorChange">
                    <el-option v-for="m in dialogCascade.majors.value" :key="m.id" :label="m.name" :value="m.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$t('userSearch.classLabel')">
                  <el-select v-model="formData.classId" :placeholder="$t('userList.classSelectMajorFirst')" clearable class="full-width" :disabled="!formData.majorId">
                    <el-option v-for="c in dialogCascade.classes.value" :key="c.id" :label="c.name" :value="c.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- 教师专属字段 -->
          <template v-if="formData.role === 'TEACHER'">
            <el-divider content-position="left">{{ $t('userList.teacherInfo') }}</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$t('userList.teacherNo')">
                  <el-input v-model="formData.teacherNo" :placeholder="$t('userList.teacherNoPlaceholder')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userList.teacherStatus')">
                  <el-select v-model="formData.teacherStatus" :placeholder="$t('userSearch.pleaseSelect')" class="full-width" :disabled="formData.id === userStore.userId">
                    <el-option :label="$t('course.pendingReview')" :value="0" />
                    <el-option :label="$t('course.approved')" :value="1" />
                    <el-option :label="$t('userList.teacherStatusRejected')" :value="2" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item :label="$t('userSearch.department')">
                  <el-select v-model="formData.departmentId" :placeholder="$t('userList.departmentPlaceholder')" clearable class="full-width" @change="handleDialogDeptChange">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userSearch.major')">
                  <el-select v-model="formData.majorId" :placeholder="$t('userList.optional')" clearable class="full-width" :disabled="!formData.departmentId" @change="handleDialogMajorChange">
                    <el-option v-for="m in dialogCascade.majors.value" :key="m.id" :label="m.name" :value="m.id" />
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
                <el-form-item :label="$t('userList.teacherNo')">
                  <el-input v-model="formData.teacherNo" :placeholder="$t('userList.teacherNoPlaceholder')" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="$t('userList.managedDepartments')">
                  <el-select v-model="formData.departmentId" :placeholder="$t('userList.departmentPlaceholder')" clearable class="full-width">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleDialogSave">{{ $t('app.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗（共享组件） -->
    <BatchImportDialog
      v-model:visible="batchImportVisible"
      @import-success="fetchData"
    />

    <!-- 教师审核弹窗（共享组件） -->
    <TeacherApprovalDialog
      v-model:visible="teacherApprovalVisible"
    />
  </div>
</template>

<script setup>
/**
 * 用户列表页
 * Vue 3.4 Composition API + script setup
 * 使用共享组件 + useGradeCascade 消除重复级联逻辑
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUrlPagination } from '@/composables/useUrlPagination'
import { swrCache } from '@/composables/useStaleWhileRevalidate'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getUsers, getUserById, updateUser, updateUserStatus, uploadAvatar } from '@/api/user'
import { getDepartments } from '@/api/department'
import { useGradeCascade } from '@/composables/useGradeCascade'
import UserSearchBar from '@/components/users/UserSearchBar.vue'
import UserTable from '@/components/users/UserTable.vue'
import BatchImportDialog from '@/components/users/BatchImportDialog.vue'
import TeacherApprovalDialog from '@/components/users/TeacherApprovalDialog.vue'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const userRole = computed(() => userStore.role)

// 加载状态
const loading = ref(false)
const error = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

// 院系列表（搜索栏 + 弹窗共用）
const departments = ref([])

// 搜索栏级联（独立的 useGradeCascade 实例）
const searchCascade = useGradeCascade()
const searchMajors = searchCascade.majors
const searchClasses = searchCascade.classes

// 弹窗级联（独立的 useGradeCascade 实例）
const dialogCascade = useGradeCascade()

// 编辑弹窗
const dialogVisible = ref(false)
const dialogLoading = ref(false)
const formRef = ref(null)

// 批量导入
const batchImportVisible = ref(false)

// 教师审核
const teacherApprovalVisible = ref(false)

// 搜索表单
const searchForm = reactive({
  keyword: '',
  role: '',
  departmentId: '',
  majorId: '',
  classId: '',
  status: ''
})

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, searchForm, ['keyword', 'role', 'departmentId', 'majorId', 'classId', 'status'])

// 编辑表单
const formData = reactive({
  id: '', username: '', realName: '', role: '', email: '', phone: '',
  gender: '', politicalStatus: '', studentNo: '', teacherNo: '',
  grade: '', enrollmentYear: '', graduationYear: '',
  departmentId: '', majorId: '', classId: '',
  teacherStatus: null, status: 1
})

const formRules = {
  username: [{ required: true, message: t('userList.accountRequired'), trigger: 'blur' }],
  realName: [{ required: true, message: t('userList.realNamePlaceholder'), trigger: 'blur' }],
  role: [{ required: true, message: t('userSearch.pleaseSelect'), trigger: 'change' }],
  email: [{ pattern: /^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/, message: t('user.pleaseInputValidEmail'), trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: t('userList.phoneFormat'), trigger: 'blur' }],
  studentNo: [{ pattern: /^\w{4,20}$/, message: t('userList.studentNoFormat'), trigger: 'blur' }],
  enrollmentYear: [{ pattern: /^\d{4}$/, message: t('userList.enrollmentYearFormat'), trigger: 'blur' }]
}

// ============== 年级联动逻辑（页面特有，保持内联） ==============
const STUDY_YEARS_DEFAULT = 4
const currentYear = new Date().getFullYear()

function parseYear(value) {
  const n = parseInt(value, 10)
  return Number.isFinite(n) && n > 1900 && n < 2200 ? n : null
}

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
  if (ey === null) { formData.grade = ''; return }
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
  return formData.grade ? t('userList.currentGrade', { grade: formData.grade }) : t('userList.gradeAuto')
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
  return years <= 0 ? '' : t('userList.yearsSystem', { years })
})

watch(() => formData.role, (newRole) => {
  if (newRole === 'STUDENT' && formData.enrollmentYear) {
    formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
  }
})

// ============== 数据获取 ==============
const fetchDepartments = async () => {
  try {
    const { data } = await getDepartments({ size: 1000 })
    departments.value = data.items || []
  } catch { departments.value = [] }
}

const fetchData = async () => {
  const params = {
    page: page.value - 1,
    size: size.value,
    keyword: searchForm.keyword || undefined,
    role: searchForm.role || undefined,
    departmentId: searchForm.departmentId || undefined,
  }
  const cacheKey = `UserList:${JSON.stringify(params)}`
  const cached = swrCache.get(cacheKey)
  if (cached && Date.now() - cached.ts < 30000) {
    tableData.value = cached.data.items || []
    totalElements.value = cached.data.totalElements || 0
    getUsers(params).then(({ data }) => {
      swrCache.set(cacheKey, { data, ts: Date.now() })
      tableData.value = data.items || []
      totalElements.value = data.totalElements || 0
    }).catch(() => {})
    return
  }
  loading.value = true
  error.value = false
  try {
    const fullParams = {
      ...params,
      majorId: searchForm.majorId || undefined,
      classId: searchForm.classId || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined
    }
    const { data } = await getUsers(fullParams)
    swrCache.set(cacheKey, { data, ts: Date.now() })
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    error.value = true
    ElMessage.error(t('userList.fetchFailed'))
  } finally {
    loading.value = false
  }
}

// ============== 搜索操作 ==============
const handleSearch = () => { page.value = 1; fetchData() }

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.role = ''
  searchForm.departmentId = ''
  searchForm.majorId = ''
  searchForm.classId = ''
  searchForm.status = ''
  searchCascade.majors.value = []
  searchCascade.classes.value = []
  page.value = 1
  fetchData()
}

const handleSearchDeptChange = (departmentId) => {
  searchForm.majorId = ''
  searchForm.classId = ''
  searchCascade.handleDeptChange(departmentId, searchForm)
  page.value = 1
  fetchData()
}

const handleSearchMajorChange = (majorId) => {
  searchForm.classId = ''
  searchCascade.handleMajorChange(majorId, searchForm)
  page.value = 1
  fetchData()
}

const handleSizeChange = (val) => { size.value = val; page.value = 1; fetchData() }
const handlePageChange = (val) => { page.value = val; fetchData() }

// ============== 编辑弹窗 ==============
const handleCreate = () => {
  sessionStorage.setItem('user_list_page', JSON.stringify({
    page: page.value, size: size.value, keyword: searchForm.keyword, role: searchForm.role,
    departmentId: searchForm.departmentId, majorId: searchForm.majorId,
    classId: searchForm.classId, status: searchForm.status
  }))
  router.push('/users/create')
}

const handleEdit = async (row) => {
  Object.assign(formData, {
    id: row.id, username: row.username, realName: row.realName, role: row.role,
    email: row.email || '', phone: row.phone || '', gender: row.gender || '',
    politicalStatus: row.politicalStatus || '', studentNo: row.studentNo || '',
    teacherNo: row.teacherNo || '', grade: row.grade || '',
    enrollmentYear: row.enrollmentYear || '', graduationYear: row.graduationYear || '',
    departmentId: row.departmentId || '', majorId: row.majorId || '', classId: row.classId || '',
    teacherStatus: row.teacherStatus ?? null, status: row.status ?? 1
  })
  if (formData.role === 'STUDENT' && formData.enrollmentYear) {
    const expected = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
    if (expected && expected !== formData.grade) formData.grade = expected
  }
  dialogVisible.value = true
  dialogLoading.value = true
  try {
    const { data } = await getUserById(row.id)
    Object.assign(formData, {
      email: data.email || '', phone: data.phone || '', gender: data.gender || '',
      politicalStatus: data.politicalStatus || '', studentNo: data.studentNo || '',
      teacherNo: data.teacherNo || '', grade: data.grade || '',
      enrollmentYear: data.enrollmentYear || '', graduationYear: data.graduationYear || '',
      departmentId: data.departmentId || '', majorId: data.majorId || '', classId: data.classId || '',
      teacherStatus: data.teacherStatus ?? null, status: data.status ?? 1
    })
    if (formData.role === 'STUDENT' && formData.enrollmentYear) {
      const expected = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
      if (expected && expected !== formData.grade) formData.grade = expected
    }
    // 加载级联选项
    if (formData.departmentId) {
      await dialogCascade.fetchMajors(formData.departmentId)
      if (formData.majorId) await dialogCascade.fetchClasses(formData.majorId)
    }
  } catch (err) {
    console.warn('[UserList] 加载完整用户信息失败', err)
  } finally {
    dialogLoading.value = false
  }
}

const handleDialogDeptChange = (val) => {
  dialogCascade.handleDeptChange(val, formData)
}

const handleDialogMajorChange = (val) => {
  dialogCascade.handleMajorChange(val, formData)
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  dialogCascade.majors.value = []
  dialogCascade.classes.value = []
}

const handleDialogSave = async () => {
  if (dialogLoading.value) return
  if (!formRef.value) return
  dialogLoading.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { dialogLoading.value = false; return }
  } catch { dialogLoading.value = false; return }
  try {
    const submitData = {}
    const fields = ['realName', 'email', 'phone', 'gender', 'politicalStatus',
                     'studentNo', 'teacherNo', 'grade', 'enrollmentYear', 'graduationYear']
    for (const f of fields) {
      const v = formData[f]
      if (v !== '' && v !== null && v !== undefined) submitData[f] = v
    }
    if (formData.departmentId !== '' && formData.departmentId !== null) submitData.departmentId = Number(formData.departmentId)
    if (formData.majorId !== '' && formData.majorId !== null) submitData.majorId = Number(formData.majorId)
    if (formData.classId !== '' && formData.classId !== null) submitData.classId = Number(formData.classId)
    if (formData.id !== userStore.userId) submitData.status = Number(formData.status)
    await updateUser(formData.id, submitData)
    ElMessage.success(t('course.saveSuccess'))
    dialogVisible.value = false
    fetchData()
  } catch (err) {
    console.warn('[UserList] 保存失败', err)
    ElMessage.error(t('userList.saveFailed'))
  } finally {
    dialogLoading.value = false
  }
}

// ============== 状态/删除操作 ==============
const handleToggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 2 : 1
  const actionText = newStatus === 1 ? t('userSearch.statusActive') : t('userSearch.statusDisabled')
  try {
    await ElMessageBox.confirm(t('userList.confirmStatusChange', { action: actionText }), t('userList.confirmTitle'), { confirmButtonText: t('course.dialogConfirm'), cancelButtonText: t('common.cancel'), type: 'warning' })
    await updateUserStatus(row.id, { status: newStatus })
    ElMessage.success(t('userList.actionSuccess', { action: actionText }))
    userStore.refreshUserInfo()
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || e?.message || t('userList.actionFailed', { action: actionText }))
    }
  }
}

const handleSoftDelete = async (row) => {
  const actionText = row.status === 3 ? t('userList.restore') : t('app.delete')
  try {
    await ElMessageBox.confirm(t('userList.confirmUserDelete', { action: actionText }), t('userList.confirmTitle'), { type: 'warning' })
    await updateUserStatus(row.id, { status: row.status === 3 ? 1 : 3 })
    ElMessage.success(t('userList.actionSuccess', { action: actionText }))
    userStore.refreshUserInfo()
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || e?.message || t('userList.actionFailed', { action: actionText }))
    }
  }
}

// ============== 头像上传 ==============
async function handleAvatarUpload(file, row) {
  const isImage = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif' || file.type === 'image/webp'
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error(t('userList.avatarFormat')); return false }
  if (!isLt2M) { ElMessage.error(t('userList.avatarTooLarge')); return false }
  try {
    const res = await uploadAvatar(row.id, file)
    row.avatar = (res.data || res) + '?t=' + Date.now()
    fetchData()
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('userList.uploadFailed')) }
  return false
}

onMounted(() => {
  document.title = `${t('admin.userManagement')} - ${t('app.title')}`
  fetchDepartments()
  const saved = sessionStorage.getItem('user_list_page')
  if (saved) {
    try {
      const state = JSON.parse(saved)
      page.value = state.page || page.value
      size.value = state.size || size.value
      searchForm.keyword = state.keyword || ''
      searchForm.role = state.role || ''
      searchForm.departmentId = state.departmentId || ''
      searchForm.majorId = state.majorId || ''
      searchForm.classId = state.classId || ''
      searchForm.status = state.status !== undefined ? state.status : ''
      sessionStorage.removeItem('user_list_page')
    } catch (e) { /* ignore */ }
  }
  fetchData()
})
</script>

<style scoped>
.user-list {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.filter-card {
  margin-bottom: var(--space-6);
  padding: var(--space-4) var(--space-5);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.search-card .el-form-item {
  margin-bottom: 0;
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.filter-input {
  width: 160px;
  border-radius: var(--radius-md);
}

.filter-select {
  width: 160px;
  border-radius: var(--radius-md);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.data-table :deep(.el-table__row) {
  transition: background var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__body tr) {
  background: var(--el-fill-color-blank);
}

.data-table :deep(.el-table__body tr:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.pagination-wrap {
  margin-top: var(--space-6);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.full-width {
  width: 100%;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.batch-import-tip {
  margin-bottom: var(--space-4);
}

.download-template {
  margin: var(--space-3) 0;
}

.template-tip {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

.batch-upload {
  width: 100%;
}

.upload-icon {
  font-size: 32px;
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-2);
}

.upload-text {
  color: var(--el-text-color-regular);
}

.upload-text em {
  color: var(--role-primary);
  font-style: normal;
}

.upload-tip {
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
  margin-top: var(--space-2);
}

.empty-tip {
  padding: var(--space-4) 0;
}

/* 角色标签颜色系统 */
:deep(.el-tag--danger) {
  --el-tag-bg-color: rgba(79, 70, 229, 0.1);
  --el-tag-text-color: #4F46E5;
  --el-tag-border-color: rgba(79, 70, 229, 0.2);
}
:deep(.el-tag--warning) {
  --el-tag-bg-color: rgba(245, 158, 11, 0.1);
  --el-tag-text-color: #F59E0B;
  --el-tag-border-color: rgba(245, 158, 11, 0.2);
}
:deep(.el-tag--success) {
  --el-tag-bg-color: rgba(16, 185, 129, 0.1);
  --el-tag-text-color: #10B981;
  --el-tag-border-color: rgba(16, 185, 129, 0.2);
}
:deep(.el-tag--primary) {
  --el-tag-bg-color: rgba(59, 130, 246, 0.1);
  --el-tag-text-color: #3B82F6;
  --el-tag-border-color: rgba(59, 130, 246, 0.2);
}

/* 弹窗 border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}
:deep(.el-dialog__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:deep(.el-dialog__body) {
  padding: var(--space-5);
}
:deep(.el-dialog__footer) {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.avatar-uploader { display: inline-block; cursor: pointer; }
.clickable-avatar { cursor: pointer; transition: opacity var(--duration-fast) var(--ease-out); }
.clickable-avatar:hover { opacity: 0.8; }
</style>
