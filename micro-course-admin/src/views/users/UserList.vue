<!--
  用户列表
  路由路径: /users
  Phase 1
  Author: jackie
-->
<template>
  <div class="user-list">
    <!-- 搜索区 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="账号/姓名" clearable class="filter-input" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="请选择" clearable class="filter-select">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教务" value="ACADEMIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="院系">
          <el-select v-model="searchForm.departmentId" placeholder="请选择院系" clearable class="filter-select" @change="handleDepartmentChange">
            <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="searchForm.majorId" placeholder="请选择专业" clearable class="filter-select" :disabled="!searchForm.departmentId" @change="handleMajorChange">
            <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="searchForm.classId" placeholder="请选择班级" clearable class="filter-select" :disabled="!searchForm.majorId">
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable class="filter-select">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">用户列表</span>
          <div class="header-actions">
            <el-button type="warning" @click="teacherApprovalVisible = true">教师审核</el-button>
            <el-button type="success" v-if="userRole !== 'ACADEMIC'" @click="batchImportVisible = true">批量导入</el-button>
            <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增用户</el-button>
          </div>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-result
        v-else-if="error"
        icon="error"
        title="数据加载失败"
        sub-title="请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">重试</el-button>
        </template>
      </el-result>
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        description="暂无用户数据"
        :image-size="120"
      />
      <template v-else>
        <el-table :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="头像" width="80" align="center">
          <template #default="{ row }">
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
        </el-table-column>
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.role === 'ADMIN'" type="danger" size="small">管理员</el-tag>
            <el-tag v-else-if="row.role === 'ACADEMIC'" type="warning" size="small">教务</el-tag>
            <el-tag v-else-if="row.role === 'TEACHER'" type="success" size="small">教师</el-tag>
            <el-tag v-else type="primary" size="small">学生</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门/专业/班级" min-width="180">
          <template #default="{ row }">
            <span>{{ row.departmentName }}</span>
            <span v-if="row.majorName"> / {{ row.majorName }}</span>
            <span v-if="row.className"> / {{ row.className }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status"
              :active-value="1"
              :inactive-value="2"
              @change="(val) => handleToggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleSoftDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" aria-label="分页导航"
        />
      </div>
      </template>
    </el-card>

    <!-- 编辑弹窗（按角色动态显示字段） -->
    <el-dialog v-model="dialogVisible" :title="`编辑用户 · ${formData.realName || formData.username}`" width="780px" @close="handleDialogClose" :close-on-press-escape="true" top="5vh">
      <div v-loading="dialogLoading">
        <!-- 基础信息（所有角色都有） -->
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
                <el-form-item label="年级">
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
                <el-form-item label="毕业年份">
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
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="院系">
                  <el-select v-model="formData.departmentId" placeholder="请选择院系" clearable class="full-width" @change="handleDeptChange">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="专业">
                  <el-select v-model="formData.majorId" placeholder="请先选择院系" clearable class="full-width" :disabled="!formData.departmentId" @change="handleDialogMajorChange">
                    <el-option v-for="m in dialogMajors" :key="m.id" :label="m.name" :value="m.id" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="班级">
                  <el-select v-model="formData.classId" placeholder="请先选择专业" clearable class="full-width" :disabled="!formData.majorId">
                    <el-option v-for="c in dialogClasses" :key="c.id" :label="c.name" :value="c.id" />
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
                  <el-select v-model="formData.departmentId" placeholder="请选择院系" clearable class="full-width" @change="handleDeptChange">
                    <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="专业">
                  <el-select v-model="formData.majorId" placeholder="可选" clearable class="full-width" :disabled="!formData.departmentId" @change="handleDialogMajorChange">
                    <el-option v-for="m in dialogMajors" :key="m.id" :label="m.name" :value="m.id" />
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

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="batchImportVisible" title="批量导入用户" width="500px" :close-on-press-escape="true">
      <div class="batch-import-tip">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            请按模板格式填写后上传。支持 .xlsx 格式文件。
          </template>
        </el-alert>
      </div>
      <div class="download-template">
        <el-button size="small" @click="handleDownloadTemplate">
          <el-icon><Download /></el-icon> 下载样表
        </el-button>
      </div>
      <el-upload
        ref="uploadRef"
        class="batch-upload"
        drag
        accept=".xlsx,.xls"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">只能上传 xlsx/xls 文件，且不超过 10MB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="batchImportVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!uploadFile" @click="handleBatchImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 教师审核弹窗 -->
    <el-dialog v-model="teacherApprovalVisible" title="教师入驻审核" width="700px" destroy-on-close :close-on-press-escape="true">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--space-4)">
        <template #title>
          待审核教师列表。审核通过后，教师将获得创建课程的权限。
        </template>
      </el-alert>
      <el-table v-loading="teacherLoading" :aria-busy="teacherLoading" :data="pendingTeachers" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="teacherNo" label="教师编号" min-width="120" />
        <el-table-column prop="departmentName" label="院系" min-width="120" />
        <el-table-column prop="createdAt" label="申请时间" min-width="160" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="handleApproveTeacher(row)">通过</el-button>
            <el-button type="danger" size="small" @click="handleRejectTeacher(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="pendingTeachers.length === 0 && !teacherLoading" class="empty-tip">
        <el-empty description="暂无待审核教师" :image-size="80" />
      </div>
      <template #footer>
        <el-button @click="teacherApprovalVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 用户列表页
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Download } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import * as XLSX from 'xlsx'
import { getUsers, getUserById, updateUser, updateUserStatus, updateTeacherStatus, batchImportUsers, uploadAvatar } from '@/api/user'
import { getDepartments } from '@/api/department'
import { getMajors } from '@/api/major'
import { getClasses } from '@/api/class'

const router = useRouter()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const error = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const departments = ref([])
const majors = ref([])
const classes = ref([])

const dialogVisible = ref(false)
const dialogLoading = ref(false)
const formRef = ref(null)
const dialogMajors = ref([])
const dialogClasses = ref([])

// 批量导入
const batchImportVisible = ref(false)
const uploadRef = ref(null)
const uploadFile = ref(null)
const importLoading = ref(false)

// 教师审核
const teacherApprovalVisible = ref(false)
const teacherLoading = ref(false)
const pendingTeachers = ref([])

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

const formData = reactive({
  id: '',
  username: '',
  realName: '',
  role: '',
  email: '',
  phone: '',
  gender: '',
  politicalStatus: '',
  studentNo: '',
  teacherNo: '',
  grade: '',
  enrollmentYear: '',
  graduationYear: '',
  departmentId: '',
  majorId: '',
  classId: '',
  teacherStatus: null,
  status: 1
})

const formRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  email: [
    { pattern: /^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/, message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ],
  studentNo: [
    { pattern: /^\w{4,20}$/, message: '学号格式不正确（4-20位字母/数字）', trigger: 'blur' }
  ],
  enrollmentYear: [
    { pattern: /^\d{4}$/, message: '请输入4位年份', trigger: 'blur' }
  ]
}

// ============== 年级联动逻辑 ==============
// 入学年份变更 → 自动算当前年级 + 毕业年份（默认 4 年制）
const STUDY_YEARS_DEFAULT = 4

function parseYear(value) {
  const n = parseInt(value, 10)
  return Number.isFinite(n) && n > 1900 && n < 2200 ? n : null
}

const currentYear = new Date().getFullYear()

// 联动：根据 enrollmentYear 自动计算 grade
function calcGradeFromEnrollment(enrollmentYear, gradYear) {
  const ey = parseYear(enrollmentYear)
  if (ey === null) return ''
  // 用毕业年份反推学制
  const gy = parseYear(gradYear)
  const totalYears = gy !== null ? (gy - ey + 1) : STUDY_YEARS_DEFAULT
  // 当前年级 = 当前年 - 入学年 + 1（限制在 1..totalYears）
  const cur = currentYear - ey + 1
  if (cur <= 0) return '0' // 还未入学
  if (cur > totalYears) return String(totalYears) // 已毕业
  return String(cur)
}

// 联动：根据 enrollmentYear + grade 计算毕业年份
function calcGradYearFromEnrollment(enrollmentYear) {
  const ey = parseYear(enrollmentYear)
  if (ey === null) return ''
  return String(ey + STUDY_YEARS_DEFAULT)
}

// 用户改入学年份时：自动算当前年级 + 同步毕业年份
const handleEnrollmentYearChange = () => {
  const ey = parseYear(formData.enrollmentYear)
  if (ey === null) {
    formData.grade = ''
    return
  }
  // 如果毕业年份为空，或者和当前一致（4 年制），则同步更新
  if (!formData.graduationYear) {
    formData.graduationYear = calcGradYearFromEnrollment(formData.enrollmentYear)
  }
  formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
}

// 用户改毕业年份时：自动反推学制 + 重新算当前年级
const handleGraduationYearChange = () => {
  const ey = parseYear(formData.enrollmentYear)
  const gy = parseYear(formData.graduationYear)
  if (ey === null || gy === null) return
  // 毕业年份必须 ≥ 入学年份 + 1
  if (gy <= ey) {
    ElMessage.warning('毕业年份必须大于入学年份')
    formData.graduationYear = String(ey + STUDY_YEARS_DEFAULT)
  }
  formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
}

// 智能提示（computed）
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

// 监听 enrollmentYear / graduationYear / role 变化，role 变 STUDENT 时自动重算
watch(() => formData.role, (newRole) => {
  if (newRole === 'STUDENT' && formData.enrollmentYear) {
    formData.grade = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
  }
})

const fetchDepartments = async () => {
  try {
    const { data } = await getDepartments({ size: 1000 })
    departments.value = data.items || []
  } catch {
    departments.value = []
  }
}

const fetchMajors = async (departmentId) => {
  if (!departmentId) {
    majors.value = []
    return
  }
  try {
    const { data } = await getMajors({ departmentId, size: 1000 })
    majors.value = data.items || []
  } catch {
    majors.value = []
  }
}

const fetchClasses = async (majorId) => {
  if (!majorId) {
    classes.value = []
    return
  }
  try {
    const { data } = await getClasses({ majorId, size: 1000 })
    classes.value = data.items || []
  } catch {
    classes.value = []
  }
}

const fetchData = async () => {
  // P2-17: SWR 模式 — 缓存 30s 内请求，立即显示旧数据 + 后台静默刷新
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
    // 完整 params（含 majorId/classId/status）— SWR 已用基础版本
    const fullParams = { ...params, majorId: searchForm.majorId || undefined, classId: searchForm.classId || undefined, status: searchForm.status !== '' ? searchForm.status : undefined }
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

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.role = ''
  searchForm.departmentId = ''
  searchForm.majorId = ''
  searchForm.classId = ''
  searchForm.status = ''
  majors.value = []
  classes.value = []
  page.value = 1
  fetchData()
}

const handleDepartmentChange = () => {
  searchForm.majorId = ''
  searchForm.classId = ''
  majors.value = []
  classes.value = []
  if (searchForm.departmentId) {
    fetchMajors(searchForm.departmentId)
  }
  page.value = 1
  fetchData()
}

const handleMajorChange = () => {
  searchForm.classId = ''
  classes.value = []
  if (searchForm.majorId) {
    fetchClasses(searchForm.majorId)
  }
  page.value = 1
  fetchData()
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleCreate = () => {
  // OP-0272: 保存当前分页上下文到 sessionStorage，返回后恢复
  sessionStorage.setItem('user_list_page', JSON.stringify({
    page: page.value,
    size: size.value,
    keyword: searchForm.keyword,
    role: searchForm.role,
    departmentId: searchForm.departmentId,
    majorId: searchForm.majorId,
    classId: searchForm.classId,
    status: searchForm.status
  }))
  router.push('/users/create')
}

const handleEdit = async (row) => {
  // 先用行数据填表（响应快），然后异步加载完整用户信息
  Object.assign(formData, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    role: row.role,
    email: row.email || '',
    phone: row.phone || '',
    gender: row.gender || '',
    politicalStatus: row.politicalStatus || '',
    studentNo: row.studentNo || '',
    teacherNo: row.teacherNo || '',
    grade: row.grade || '',
    enrollmentYear: row.enrollmentYear || '',
    graduationYear: row.graduationYear || '',
    departmentId: row.departmentId || '',
    majorId: row.majorId || '',
    classId: row.classId || '',
    teacherStatus: row.teacherStatus ?? null,
    status: row.status ?? 1
  })
  // 联动校验：确保 grade 与 enrollmentYear 一致（防止历史脏数据）
  if (formData.role === 'STUDENT' && formData.enrollmentYear) {
    const expected = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
    if (expected && expected !== formData.grade) {
      // 如果数据库的 grade 不匹配，按当前日期重算并提示
      formData.grade = expected
    }
  }
  dialogVisible.value = true
  // 异步加载完整信息（含 majors/classes）
  dialogLoading.value = true
  try {
    const { data } = await getUserById(row.id)
    Object.assign(formData, {
      email: data.email || '',
      phone: data.phone || '',
      gender: data.gender || '',
      politicalStatus: data.politicalStatus || '',
      studentNo: data.studentNo || '',
      teacherNo: data.teacherNo || '',
      grade: data.grade || '',
      enrollmentYear: data.enrollmentYear || '',
      graduationYear: data.graduationYear || '',
      departmentId: data.departmentId || '',
      majorId: data.majorId || '',
      classId: data.classId || '',
      teacherStatus: data.teacherStatus ?? null,
      status: data.status ?? 1
    })
    // 再次联动校验（用最新数据）
    if (formData.role === 'STUDENT' && formData.enrollmentYear) {
      const expected = calcGradeFromEnrollment(formData.enrollmentYear, formData.graduationYear)
      if (expected && expected !== formData.grade) {
        formData.grade = expected
      }
    }
    // 加载级联选项
    if (formData.departmentId) {
      await loadDialogMajors(formData.departmentId)
      if (formData.majorId) {
        await loadDialogClasses(formData.majorId)
      }
    }
  } catch (err) {
    console.warn('[UserList] 加载完整用户信息失败', err)
  } finally {
    dialogLoading.value = false
  }
}

const loadDialogMajors = async (departmentId) => {
  if (!departmentId) {
    dialogMajors.value = []
    return
  }
  try {
    const { data } = await getMajors({ departmentId, size: 1000 })
    dialogMajors.value = data.items || []
  } catch {
    dialogMajors.value = []
  }
}

const loadDialogClasses = async (majorId) => {
  if (!majorId) {
    dialogClasses.value = []
    return
  }
  try {
    const { data } = await getClasses({ majorId, size: 1000 })
    dialogClasses.value = data.items || []
  } catch {
    dialogClasses.value = []
  }
}

const handleDeptChange = (val) => {
  formData.majorId = ''
  formData.classId = ''
  dialogMajors.value = []
  dialogClasses.value = []
  loadDialogMajors(val)
}

const handleDialogMajorChange = (val) => {
  formData.classId = ''
  dialogClasses.value = []
  loadDialogClasses(val)
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  dialogMajors.value = []
  dialogClasses.value = []
}

const handleDialogSave = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    dialogLoading.value = true
    // 组装提交数据：剔除空字符串，转换为数字类型
    const submitData = {}
    const fields = ['realName', 'email', 'phone', 'gender', 'politicalStatus',
                     'studentNo', 'teacherNo', 'grade', 'enrollmentYear', 'graduationYear']
    for (const f of fields) {
      const v = formData[f]
      if (v !== '' && v !== null && v !== undefined) submitData[f] = v
    }
    // 数字字段
    if (formData.departmentId !== '' && formData.departmentId !== null) submitData.departmentId = Number(formData.departmentId)
    if (formData.majorId !== '' && formData.majorId !== null) submitData.majorId = Number(formData.majorId)
    if (formData.classId !== '' && formData.classId !== null) submitData.classId = Number(formData.classId)
    // 状态字段（不能禁用自己）
    if (formData.id !== userStore.userId) {
      submitData.status = Number(formData.status)
    }
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

const handleToggleStatus = async (row, newStatus) => {
  const actionText = newStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定${actionText}该用户吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await updateUserStatus(row.id, { status: newStatus })
    ElMessage.success(`${actionText}成功`)
    userStore.refreshUserInfo()
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      const msg = e?.response?.data?.message || e?.message || `${actionText}失败`
      ElMessage.error(msg)
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
      const msg = e?.response?.data?.message || e?.message || `${actionText}失败`
      ElMessage.error(msg)
    }
  }
}

// 批量导入
const handleDownloadTemplate = () => {
  const wsData = [
    ['username', 'realName', 'password', 'email', 'role', 'departmentId', 'majorId', 'classId', 'studentNo', 'teacherNo'],
    ['zhangsan', '张三', 'Abc123456', 'zhangsan@school.edu.cn', 'STUDENT', '1', '1', '1', 'S2024001', ''],
    ['lisi', '李四', 'Abc123456', 'lisi@school.edu.cn', 'TEACHER', '1', '', '', '', 'T001'],
    ['wangwu', '王五', 'Abc123456', 'wangwu@school.edu.cn', 'ADMIN', '1', '', '', '', ''],
  ]
  const wb = XLSX.utils.book_new()
  const ws = XLSX.utils.aoa_to_sheet(wsData)
  ws['!cols'] = [{ wch: 15 }, { wch: 12 }, { wch: 15 }, { wch: 28 }, { wch: 10 }, { wch: 12 }, { wch: 10 }, { wch: 8 }, { wch: 12 }, { wch: 10 }]
  XLSX.utils.book_append_sheet(wb, ws, '用户导入')
  XLSX.writeFile(wb, '用户导入样表.xlsx')
}

const handleFileChange = (file) => {
  uploadFile.value = file.raw
}

const handleFileRemove = () => {
  uploadFile.value = null
}

const handleBatchImport = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadFile.value)
    await batchImportUsers(formData)
    ElMessage.success('导入成功')
    batchImportVisible.value = false
    uploadFile.value = null
    uploadRef.value?.clearFiles()
    fetchData()
  } catch {
    ElMessage.error('导入失败，请检查文件格式')
  } finally {
    importLoading.value = false
  }
}

// 教师审核 - 打开弹窗时加载待审核教师
const loadPendingTeachers = async () => {
  teacherLoading.value = true
  try {
    const { data } = await getUsers({
      role: 'TEACHER',
      teacherStatus: 0,
      size: 100
    })
    const items = data.items || []
    pendingTeachers.value = items
      .filter(t => t.teacherStatus === 0)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      .slice(0, 20)
    if (pendingTeachers.value.length === 0) {
      ElMessage.info('暂无待审核教师')
    }
  } catch {
    pendingTeachers.value = []
    ElMessage.error('获取待审核教师列表失败')
  } finally {
    teacherLoading.value = false
  }
}

// 监听教师审核弹窗打开
watch(teacherApprovalVisible, (val) => {
  if (val) {
    loadPendingTeachers()
  }
})

// 通过教师
const handleApproveTeacher = async (row) => {
  try {
    await updateTeacherStatus(row.id, { teacherStatus: 1, reason: '' })
    ElMessage.success(`教师 ${row.realName} 审核通过`)
    loadPendingTeachers()
  } catch {
    ElMessage.error('操作失败')
  }
}

// 驳回教师
const handleRejectTeacher = async (row) => {
  try {
    await updateTeacherStatus(row.id, { teacherStatus: 2, reason: '' })
    ElMessage.success(`教师 ${row.realName} 已驳回`)
    loadPendingTeachers()
  } catch {
    ElMessage.error('操作失败')
  }
}

// 头像上传
async function handleAvatarUpload(file, row) {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif' || file.type === 'image/webp'
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isJPG) { ElMessage.error('头像仅支持 JPG/PNG/GIF/WebP 格式'); return false }
  if (!isLt2M) { ElMessage.error('头像大小不能超过 2MB'); return false }
  try {
    const res = await uploadAvatar(row.id, file)
    const avatarUrl = res.data || res
    row.avatar = avatarUrl + '?t=' + Date.now()
    fetchData()
  } catch (e) { ElMessage.error(e?.response?.data?.message || '上传失败') }
  return false
}

onMounted(() => {
  fetchDepartments()
  // OP-0272: 从 sessionStorage 恢复分页上下文（从 UserForm 返回时）
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
    } catch (e) { /* ignore parse error */ }
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
