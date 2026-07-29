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
          <el-avatar v-if="row.avatar" :src="row.avatar" :size="40" class="clickable-avatar" title="点击上传头像" />
          <el-avatar v-else :size="40" class="clickable-avatar" title="点击上传头像">{{ row.realName?.charAt(0) || 'U' }}</el-avatar>
        </el-upload>
      </template>

      <!-- 状态插槽 -->
      <template #status="{ row }">
        <el-tag
          v-if="row.status === 1 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')"
          type="success" size="small" style="cursor:pointer"
          @click="handleToggleStatus(row, 2)"
        >
          正常
        </el-tag>
        <el-tag
          v-else-if="userRole === 'ADMIN' || userRole === 'ACADEMIC'"
          type="danger" size="small" style="cursor:pointer"
          @click="handleToggleStatus(row, 1)"
        >
          禁用
        </el-tag>
      </template>

      <!-- 操作栏插槽 -->
      <template #actions="{ row }">
        <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
        <el-button v-if="userRole === 'ADMIN'" type="danger" link size="small" @click="handleSoftDelete(row)">删除</el-button>
      </template>
    </UserTable>

    <!-- 编辑弹窗（按角色动态显示字段 — 页面特有，保持内联） -->
    <el-dialog v-model="dialogVisible" :title="`编辑用户 · ${formData.realName || formData.username}`" width="780px" @close="handleDialogClose" :close-on-press-escape="true" top="5vh">
      <div v-loading="dialogLoading">
        <!-- 基础信息 -->
        <el-divider content-position="left">基础信息</el-divider>
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="账号" prop="username">
                <el-input v-model="formData.username" :disabled="true" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="姓名" prop="realName">
                <el-input v-model="formData.realName" placeholder="请输入姓名" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="角色" prop="role">
                <el-select v-model="formData.role" :disabled="true" class="full-width">
                  <el-option label="学生" value="STUDENT" />
                  <el-option label="教师" value="TEACHER" />
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="教务" value="ACADEMIC" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态" prop="status">
                <el-select v-model="formData.status" :disabled="formData.id === userStore.userId" class="full-width">
                  <el-option label="启用" :value="1" />
                  <el-option label="禁用" :value="2" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="性别">
                <el-select v-model="formData.gender" placeholder="请选择" clearable class="full-width">
                  <el-option label="男" value="MALE" />
                  <el-option label="女" value="FEMALE" />
                  <el-option label="保密" value="SECRET" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="邮箱">
                <el-input v-model="formData.email" placeholder="请输入邮箱" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="手机">
                <el-input v-model="formData.phone" placeholder="请输入手机号" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="政治面貌">
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
                <el-form-item label="学号">
                  <el-input v-model="formData.studentNo" placeholder="请输入学号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="入学年份">
                  <el-input v-model="formData.enrollmentYear" placeholder="如：2024" @input="handleEnrollmentYearChange" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="年级">
                  <el-input v-model="formData.grade" placeholder="自动计算" :disabled="!formData.enrollmentYear">
                    <template #append>
                      <el-tag v-if="gradeHint" :type="gradeHintType" size="small">{{ gradeHint }}</el-tag>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="毕业年份">
                  <el-input v-model="formData.graduationYear" placeholder="如：2028（4 年制本科）" @input="handleGraduationYearChange">
                    <template #append>
                      <el-tag v-if="studyYearsHint" type="info" size="small">{{ studyYearsHint }}</el-tag>
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="院系">
                  <el-select v-model="formData.departmentId" placeholder="请选择院系" clearable class="full-width" @change="handleDialogDeptChange">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="专业">
                  <el-select v-model="formData.majorId" placeholder="请先选择院系" clearable class="full-width" :disabled="!formData.departmentId" @change="handleDialogMajorChange">
                    <el-option v-for="m in dialogCascade.majors.value" :key="m.id" :label="m.name" :value="m.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="班级">
                  <el-select v-model="formData.classId" placeholder="请先选择专业" clearable class="full-width" :disabled="!formData.majorId">
                    <el-option v-for="c in dialogCascade.classes.value" :key="c.id" :label="c.name" :value="c.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- 教师专属字段 -->
          <template v-if="formData.role === 'TEACHER'">
            <el-divider content-position="left">教师信息</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="工号">
                  <el-input v-model="formData.teacherNo" placeholder="请输入工号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="审核状态">
                  <el-select v-model="formData.teacherStatus" placeholder="请选择" class="full-width" :disabled="formData.id === userStore.userId">
                    <el-option label="待审核" :value="0" />
                    <el-option label="已通过" :value="1" />
                    <el-option label="已驳回" :value="2" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="院系">
                  <el-select v-model="formData.departmentId" placeholder="请选择院系" clearable class="full-width" @change="handleDialogDeptChange">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="专业">
                  <el-select v-model="formData.majorId" placeholder="可选" clearable class="full-width" :disabled="!formData.departmentId" @change="handleDialogMajorChange">
                    <el-option v-for="m in dialogCascade.majors.value" :key="m.id" :label="m.name" :value="m.id" />
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
                <el-form-item label="工号">
                  <el-input v-model="formData.teacherNo" placeholder="请输入工号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="管辖院系">
                  <el-select v-model="formData.departmentId" placeholder="请选择院系" clearable class="full-width">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </template>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogLoading" @click="handleDialogSave">保存</el-button>
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
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  email: [{ pattern: /^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/, message: '请输入正确的邮箱格式', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }],
  studentNo: [{ pattern: /^\w{4,20}$/, message: '学号格式不正确（4-20位字母/数字）', trigger: 'blur' }],
  enrollmentYear: [{ pattern: /^\d{4}$/, message: '请输入4位年份', trigger: 'blur' }]
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
    ElMessage.warning('毕业年份必须大于入学年份')
    formData.graduationYear = String(ey + STUDY_YEARS_DEFAULT)
  }
  formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
}

const gradeHint = computed(() => {
  const ey = parseYear(formData.enrollmentYear)
  if (ey === null) return ''
  return formData.grade ? `当前${formData.grade}年级` : '自动计算'
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
  return years <= 0 ? '' : `${years} 年制`
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
    ElMessage.error('获取用户列表失败')
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
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch (err) {
    console.warn('[UserList] 保存失败', err)
    ElMessage.error('保存失败，请检查表单')
  } finally {
    dialogLoading.value = false
  }
}

// ============== 状态/删除操作 ==============
const handleToggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 2 : 1
  const actionText = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${actionText}该用户吗？`, '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await updateUserStatus(row.id, { status: newStatus })
    ElMessage.success(`${actionText}成功`)
    userStore.refreshUserInfo()
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || e?.message || `${actionText}失败`)
    }
  }
}

const handleSoftDelete = async (row) => {
  const actionText = row.status === 3 ? '恢复' : '删除'
  try {
    await ElMessageBox.confirm(`确定${actionText}该用户？`, '提示', { type: 'warning' })
    await updateUserStatus(row.id, { status: row.status === 3 ? 1 : 3 })
    ElMessage.success(`${actionText}成功`)
    userStore.refreshUserInfo()
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || e?.message || `${actionText}失败`)
    }
  }
}

// ============== 头像上传 ==============
async function handleAvatarUpload(file, row) {
  const isImage = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif' || file.type === 'image/webp'
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error('头像仅支持 JPG/PNG/GIF/WebP 格式'); return false }
  if (!isLt2M) { ElMessage.error('头像大小不能超过 2MB'); return false }
  try {
    const res = await uploadAvatar(row.id, file)
    row.avatar = (res.data || res) + '?t=' + Date.now()
    fetchData()
  } catch (e) { ElMessage.error(e?.response?.data?.message || '上传失败') }
  return false
}

onMounted(() => {
  document.title = '用户管理 - 微课平台'
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
