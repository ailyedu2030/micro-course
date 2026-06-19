<!--
  教师端 - 我的教学班
  路由路径: /teacher/teaching-classes
  Author: jackie
-->
<template>
  <div class="teacher-teaching-classes">
    <el-row :gutter="16">
      <!-- 左侧：课程列表 -->
      <el-col :span="8">
        <el-card class="course-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">我的课程</span>
            </div>
          </template>
          <div v-if="loadingCourses" class="loading-wrap">
            <el-skeleton :rows="6" animated />
          </div>
          <el-empty v-else-if="courseOptions.length === 0" description="暂无课程" :image-size="80" />
          <div v-else class="course-list">
            <div
              v-for="course in courseOptions"
              :key="course.id"
              class="course-item"
              :class="{ 'is-active': selectedCourseId === course.id }"
              @click="handleSelectCourse(course)"
            >
              <div class="course-title">{{ course.title }}</div>
              <div class="course-info">{{ course.code || '' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：教学班列表 -->
      <el-col :span="16">
        <el-card class="class-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ selectedCourse ? selectedCourse.title + ' - 教学班' : '请选择课程' }}</span>
            </div>
          </template>

          <div v-if="!selectedCourseId" class="empty-tip">
            <el-empty description="请从左侧选择课程" :image-size="100" />
          </div>

          <div v-else-if="loadingClasses" class="loading-wrap">
            <el-skeleton :rows="6" animated />
          </div>

          <div v-else-if="groupedClasses.length === 0" class="empty-tip">
            <el-empty description="该课程暂无教学班" :image-size="100" />
          </div>

          <div v-else class="class-groups">
            <div v-for="group in groupedClasses" :key="group.semester" class="class-group">
              <div class="group-header">
                <el-tag type="primary" size="small">{{ group.semester }}</el-tag>
                <span class="group-count">共 {{ group.list.length }} 个班</span>
              </div>
              <div class="group-classes">
                <div
                  v-for="cls in group.list"
                  :key="cls.id"
                  class="class-item"
                  :class="{ 'is-expanded': expandedClassId === cls.id }"
                >
                  <div class="class-summary" role="button" tabindex="0" :aria-label="`展开班级详情 ${cls.name}`" :aria-expanded="expandedClassId === cls.id" @click="handleExpandClass(cls)" @keydown.enter="handleExpandClass(cls)" @keydown.space.prevent="handleExpandClass(cls)">
                    <div class="class-info">
                      <span class="class-name">{{ cls.name }}</span>
                      <el-tag :type="getStatusType(cls.status)" size="small" class="status-tag">
                        {{ getStatusText(cls.status) }}
                      </el-tag>
                    </div>
                    <div class="class-meta">
                      <span>容量 {{ cls.currentStudents || 0 }}/{{ cls.maxStudents }}</span>
                      <span class="expand-icon">
                        <el-icon><ArrowRight v-if="expandedClassId !== cls.id" /><ArrowDown v-else /></el-icon>
                      </span>
                    </div>
                  </div>

                  <!-- 展开：学生管理 -->
                  <div v-if="expandedClassId === cls.id" class="class-detail">
                    <div class="detail-actions">
                      <el-button type="primary" size="small" v-if="userRole !== 'ACADEMIC'" @click="handleAddStudent(cls)" aria-label="刷新"><el-icon><Plus /></el-icon>添加学生
                      </el-button>
                      <el-button size="small" @click="handleRefreshStudents(cls)" aria-label="操作"><el-icon><RefreshRight /></el-icon>刷新
                      </el-button>
                    </div>

                    <el-table
                      v-loading="studentLoading[cls.id]" :aria-busy="studentLoading[cls.id]"
                      :data="studentData[cls.id] || []"
                      stripe
                      border
                      size="small"
                      class="student-table"
                    >
                      <el-table-column type="index" label="序号" width="60" align="center" />
                      <el-table-column prop="username" label="学号" width="120" show-overflow-tooltip />
                      <el-table-column prop="realName" label="姓名" width="100" show-overflow-tooltip />
                      <el-table-column prop="status" label="状态" width="100" align="center">
                        <template #default="{ row }">
                          <el-tag :type="getStudentStatusType(row.status)" size="small">
                            {{ getStudentStatusText(row.status) }}
                          </el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="enrolledAt" label="加入时间" width="160">
                        <template #default="{ row }">
                          <span class="text-secondary">{{ formatDate(row.enrolledAt) }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="操作" width="140" fixed="right">
                        <template #default="{ row }">
                          <el-button type="primary" link size="small" @click="handleChangeStatus(cls, row)">
                            修改状态
                          </el-button>
                          <el-popconfirm title="确定移除该学生？" @confirm="handleRemoveStudent(cls, row)">
                            <template #reference>
                              <el-button type="danger" link size="small">移除</el-button>
                            </template>
                          </el-popconfirm>
                        </template>
                      </el-table-column>
                    </el-table>

                    <div v-if="!studentLoading[cls.id] && (studentData[cls.id] || []).length === 0" class="no-students">
                      暂无学生
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 添加学生弹窗 -->
    <el-dialog v-model="addStudentVisible" title="添加学生" width="500px" destroy-on-close :close-on-press-escape="true">
      <el-form :model="addStudentForm" label-width="80px">
        <el-form-item label="学生ID">
          <el-input v-model="addStudentForm.userId" placeholder="请输入学生ID" clearable />
        </el-form-item>
        <el-form-item label="学生姓名">
          <el-input v-model="addStudentForm.realName" placeholder="请输入学生姓名搜索" clearable @keyup.enter="handleSearchStudent" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addStudentVisible = false">取消</el-button>
        <el-button type="primary" :loading="addingStudent" @click="confirmAddStudent">添加</el-button>
      </template>
    </el-dialog>

    <!-- 修改状态弹窗 -->
    <el-dialog v-model="changeStatusVisible" title="修改学生状态" width="400px" destroy-on-close :close-on-press-escape="true">
      <el-form label-width="80px">
        <el-form-item label="当前学生">
          <el-input :model-value="currentStudentItem?.realName || ''" disabled />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="changeStatusForm.status" class="full-width">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
            <el-option label="休学" value="SUSPENDED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeStatusVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingStatus" @click="confirmChangeStatus">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, RefreshRight, ArrowRight, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getTeachingClasses,
  getTeachingClassStudents,
  addStudentToClass,
  removeStudentFromClass,
  updateStudentStatus,
  getCourses
} from '@/api/teaching-class'
import { getUsers } from '@/api/user'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

// 加载状态
const loadingCourses = ref(false)
const loadingClasses = ref(false)
const studentLoading = reactive({})
const addingStudent = ref(false)
const changingStatus = ref(false)

// 数据
const courseOptions = ref([])
const selectedCourseId = ref(null)
const selectedCourse = computed(() => courseOptions.value.find(c => c.id === selectedCourseId.value))

const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(100)

const expandedClassId = ref(null)
const studentData = reactive({})

// 状态映射
const statusMap = {
  0: { text: '待开课', type: 'info' },
  1: { text: '进行中', type: 'success' },
  2: { text: '已结课', type: 'warning' }
}

const studentStatusMap = {
  ACTIVE: { text: '正常', type: 'success' },
  DISABLED: { text: '禁用', type: 'danger' },
  SUSPENDED: { text: '休学', type: 'warning' }
}

function getStatusText(status) {
  return statusMap[status]?.text || '未知'
}

function getStatusType(status) {
  return statusMap[status]?.type || 'info'
}

function getStudentStatusText(status) {
  return studentStatusMap[status]?.text || status || '未知'
}

function getStudentStatusType(status) {
  return studentStatusMap[status]?.type || 'info'
}

function formatDate(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 按学期分组
const groupedClasses = computed(() => {
  const groups = {}
  tableData.value.forEach(cls => {
    const sem = cls.semester || '未知学期'
    if (!groups[sem]) groups[sem] = { semester: sem, list: [] }
    groups[sem].list.push(cls)
  })
  return Object.values(groups).sort((a, b) => b.semester.localeCompare(a.semester))
})

// 获取课程列表
async function fetchCourses() {
  loadingCourses.value = true
  try {
    const teacherId = userStore.userInfo?.id
    const { data } = await getCourses({ size: 1000, teacherId })
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
  } finally {
    loadingCourses.value = false
  }
}

// 选择课程
async function handleSelectCourse(course) {
  selectedCourseId.value = course.id
  page.value = 1
  await fetchClasses()
}

// 获取教学班列表
async function fetchClasses() {
  if (!selectedCourseId.value) return
  loadingClasses.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: selectedCourseId.value
    }
    const { data } = await getTeachingClasses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取教学班列表失败')
  } finally {
    loadingClasses.value = false
  }
}

// 展开教学班
async function handleExpandClass(cls) {
  if (expandedClassId.value === cls.id) {
    expandedClassId.value = null
    return
  }
  expandedClassId.value = cls.id
  await fetchStudents(cls)
}

// 获取学生列表
async function fetchStudents(cls) {
  if (!cls.id) return
  studentLoading[cls.id] = true
  try {
    const { data } = await getTeachingClassStudents(cls.id)
    studentData[cls.id] = Array.isArray(data) ? data : (data.items || [])
  } catch {
    ElMessage.error(`获取班级学生列表失败`)
    studentData[cls.id] = []
  } finally {
    studentLoading[cls.id] = false
  }
}

// 刷新学生列表
async function handleRefreshStudents(cls) {
  await fetchStudents(cls)
}

// 添加学生弹窗
const addStudentVisible = ref(false)
const addStudentForm = reactive({
  userId: '',
  realName: ''
})
const currentClassForAdd = ref(null)

function handleAddStudent(cls) {
  currentClassForAdd.value = cls
  addStudentForm.userId = ''
  addStudentForm.realName = ''
  addStudentVisible.value = true
}

async function handleSearchStudent() {
  if (!addStudentForm.realName || addStudentForm.realName.trim().length < 2) {
    ElMessage.warning('请输入至少2个字符进行搜索')
    return
  }
  // 搜索学生，角色 STUDENT，姓名包含 realName
  try {
    const { data } = await getUsers({
      keyword: addStudentForm.realName.trim(),
      role: 'STUDENT',
      size: 20,
      status: 1
    })
    const items = data.items || []
    if (items.length === 0) {
      ElMessage.info('未找到匹配的学生')
      return
    }
    // 如果只找到一个，直接填入 userId
    if (items.length === 1) {
      addStudentForm.userId = items[0].id
      ElMessage.success(`已找到学生：${items[0].realName}`)
    } else {
      // 多个结果时显示选项列表（用 ElMessage 提示）
      ElMessage.info(`找到 ${items.length} 名学生，请选择`)
      // 填入第一个匹配的 userId 作为默认
      addStudentForm.userId = items[0].id
    }
  } catch {
    ElMessage.error('搜索学生失败')
  }
}

async function confirmAddStudent() {
  if (!addStudentForm.userId && !addStudentForm.realName) {
    ElMessage.warning('请输入学生ID或姓名')
    return
  }
  if (!currentClassForAdd.value) return
  addingStudent.value = true
  try {
    await addStudentToClass(currentClassForAdd.value.id, addStudentForm.userId)
    ElMessage.success('添加成功')
    addStudentVisible.value = false
    await fetchStudents(currentClassForAdd.value)
  } catch {
    ElMessage.error('添加失败')
  } finally {
    addingStudent.value = false
  }
}

// 移除学生
async function handleRemoveStudent(cls, student) {
  try {
    await removeStudentFromClass(cls.id, student.id)
    ElMessage.success('移除成功')
    await fetchStudents(cls)
  } catch {
    ElMessage.error('移除失败')
  }
}

// 修改状态弹窗
const changeStatusVisible = ref(false)
const changeStatusForm = reactive({ status: 'ACTIVE' })
const currentStudentItem = ref(null)
const currentClassForStatus = ref(null)

function handleChangeStatus(cls, student) {
  currentClassForStatus.value = cls
  currentStudentItem.value = student
  changeStatusForm.status = student.status || 'ACTIVE'
  changeStatusVisible.value = true
}

async function confirmChangeStatus() {
  if (!currentClassForStatus.value || !currentStudentItem.value) return
  changingStatus.value = true
  try {
    await updateStudentStatus(currentClassForStatus.value.id, currentStudentItem.value.id, changeStatusForm.status)
    ElMessage.success('状态修改成功')
    changeStatusVisible.value = false
    await fetchStudents(currentClassForStatus.value)
  } catch {
    ElMessage.error('状态修改失败')
  } finally {
    changingStatus.value = false
  }
}

onMounted(() => {
  fetchCourses()
  window.addEventListener('resize', handleResize)
})

function handleResize() {
  // 通知子组件重算高度（el-card 高度自适应）
  const cards = document.querySelectorAll('.course-card, .class-card')
  cards.forEach(card => {
    card.style.height = 'calc(100vh - 120px)'
  })
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.teacher-teaching-classes {
  padding: 24px;
  background: #F5F6FA;
  min-height: calc(100vh - 120px);
}

/* 课程卡片 & 教学班卡片 */
.course-card,
.class-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  transition: box-shadow 200ms ease, transform 200ms ease;
  height: calc(100vh - 168px);
  overflow-y: auto;
}

.course-card:hover,
.class-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  transform: translateY(-1px);
}

.course-card :deep(.el-card__header),
.class-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

/* 课程列表 */
.course-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.course-item {
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid #F1F5F9;
  border-left: 3px solid transparent;
  cursor: pointer;
  transition: all 200ms ease;
}

.course-item:hover {
  background: #F8FAFC;
  border-left-color: #4F46E5;
}

.course-item.is-active {
  background: #EEF2FF;
  border-left-color: #4F46E5;
}

.course-title {
  font-size: 14px;
  font-weight: 500;
  color: #1E293B;
  margin-bottom: 4px;
}

.course-info {
  font-size: 13px;
  color: #475569;
}

/* 教学班列表 */
.empty-tip {
  padding: 24px 0;
}

.class-groups {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.class-group {
  border: 1px solid #F1F5F9;
  border-radius: 8px;
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background-color: #F8FAFC;
  border-bottom: 1px solid #F1F5F9;
}

.group-count {
  font-size: 13px;
  color: #475569;
  margin-left: auto;
}

.group-classes {
  display: flex;
  flex-direction: column;
}

.class-item {
  border-bottom: 1px solid #F1F5F9;
}

.class-item:last-child {
  border-bottom: none;
}

.class-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 200ms ease;
}

.class-summary:hover {
  background-color: #F1F5F9;
}

.class-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.class-name {
  font-size: 14px;
  font-weight: 500;
  color: #1E293B;
}

.status-tag {
  transform: scale(0.85);
}

.class-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #475569;
}

.expand-icon {
  display: flex;
  align-items: center;
}

/* 展开详情 */
.class-detail {
  padding: 16px;
  background-color: #F8FAFC;
  border-top: 1px solid #F1F5F9;
}

.detail-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.detail-actions :deep(.el-button) {
  border-radius: 8px;
}

.student-table {
  width: 100%;
}

.student-table :deep(.el-table__header-wrapper th) {
  background: #F8FAFC;
}

.student-table :deep(.el-table__row:hover > td) {
  background: #F1F5F9;
}

.no-students {
  padding: 16px;
  text-align: center;
  color: #475569;
}

.loading-wrap {
  padding: 16px;
}

.full-width {
  width: 100%;
}

.text-secondary {
  color: #475569;
  font-size: 13px;
}

/* 弹窗样式 */
.teacher-teaching-classes :deep(.el-dialog) {
  border-radius: 12px;
}

.teacher-teaching-classes :deep(.el-button) {
  border-radius: 8px;
}

.teacher-teaching-classes :deep(.el-button--primary) {
  border-radius: 8px;
  transition: transform 200ms ease, box-shadow 200ms ease;
}

.teacher-teaching-classes :deep(.el-button--primary:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(79, 70, 229, 0.3);
}
</style>