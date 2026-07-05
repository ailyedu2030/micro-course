<!--
  教师端 - 我的教学班
  路由路径: /teacher/teaching-classes
  Author: jackie
-->
<template>
  <div class="teacher-teaching-classes">
    <el-row :gutter="16">
      <!-- 左侧：课程列表 -->
      <el-col :xs="24" :sm="24" :md="8">
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
      <el-col :xs="24" :sm="24" :md="16">
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
                  style="cursor:pointer"
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
                      <el-button type="primary" size="small" v-if="userRole !== 'ACADEMIC'" @click="handleAddStudent(cls)" aria-label="添加学生">
<el-icon><Plus /></el-icon>添加学生
                      </el-button>
                      <el-button size="small" @click="handleRefreshStudents(cls)" aria-label="刷新">
<el-icon><RefreshRight /></el-icon>刷新
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
                      <el-table-column prop="studentNo" label="学号" width="120" show-overflow-tooltip />
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
                          <el-button type="danger" link size="small" @click="handleRemoveStudent(cls, row)">移除</el-button>
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
    <el-dialog v-model="addStudentVisible" title="添加学生" width="560px" destroy-on-close :close-on-press-escape="true">
      <el-form :model="addStudentForm" label-width="80px">
        <el-form-item label="搜索学生">
          <el-input v-model="addStudentForm.realName" placeholder="输入姓名或学号，按回车搜索" clearable @keyup.enter="handleSearchStudent">
            <template #append>
              <el-button @click="handleSearchStudent">搜索</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="searchResults.length > 0" label="搜索结果">
          <el-table :data="searchResults" size="small" highlight-current-row max-height="240" @current-change="handleSelectStudent" border>
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column prop="departmentName" label="院系" show-overflow-tooltip />
          </el-table>
        </el-form-item>
        <el-form-item v-if="addStudentForm.userId" label="已选学生">
          <el-tag type="success" closable @close="addStudentForm.userId = null">
            {{ selectedStudentLabel }}
          </el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addStudentVisible = false">取消</el-button>
        <el-button type="primary" :loading="addingStudent" :disabled="!addStudentForm.userId" @click="confirmAddStudent">添加</el-button>
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
        <el-button type="primary" :loading="changingStatus" :disabled="changingStatus" @click="confirmChangeStatus">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight, ArrowRight, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getTeachingClasses,
  getTeachingClassStudents,
  addStudentToClass,
  removeStudentFromClass,
  updateStudentStatus
} from '@/api/teaching-class'
import { getUsers } from '@/api/user'
import { getCourses } from '@/api/course'

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
  0: { text: '已停开', type: 'info' },
  1: { text: '开课中', type: 'success' },
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
    if (!teacherId) {
      ElMessage.error('无法获取当前用户信息')
      return
    }
    const { data } = await getCourses({ size: 100, teacherId })
    courseOptions.value = data.items || []
  } catch (error) {
    console.error('[TeacherTeachingClasses] 获取课程列表失败', error)
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
  } catch (error) {
    console.error('[TeacherTeachingClasses] 获取教学班列表失败', error)
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
async function fetchStudents(cls, force = false) {
  if (!cls.id) return
  // P1-6: 有缓存数据则直接使用，避免重复请求
  if (!force && studentData[cls.id] && studentData[cls.id].length > 0) return
  studentLoading[cls.id] = true
  try {
    const { data } = await getTeachingClassStudents(cls.id)
    studentData[cls.id] = Array.isArray(data) ? data : (data.items || [])
  } catch (error) {
    console.error('[TeacherTeachingClasses] 获取班级学生列表失败', error)
    ElMessage.error(`获取班级学生列表失败`)
    studentData[cls.id] = []
  } finally {
    studentLoading[cls.id] = false
  }
}

// 刷新学生列表
async function handleRefreshStudents(cls) {
  await fetchStudents(cls, true)
}

// 添加学生弹窗
const addStudentVisible = ref(false)
const addStudentForm = reactive({
  userId: null,
  realName: ''
})
const currentClassForAdd = ref(null)
const searchResults = ref([])
const selectedStudentLabel = computed(() => {
  if (!addStudentForm.userId) return ''
  const found = searchResults.value.find(s => s.id === addStudentForm.userId)
  return found ? `${found.realName}（${found.studentNo || ''}）` : `ID: ${addStudentForm.userId}`
})

function handleAddStudent(cls) {
  currentClassForAdd.value = cls
  addStudentForm.userId = null
  addStudentForm.realName = ''
  searchResults.value = []
  addStudentVisible.value = true
}

async function handleSearchStudent() {
  if (!addStudentForm.realName || addStudentForm.realName.trim().length < 2) {
    ElMessage.warning('请输入至少2个字符进行搜索')
    return
  }
  try {
    const { data } = await getUsers({
      keyword: addStudentForm.realName.trim(),
      role: 'STUDENT',
      size: 20,
      status: 1
    })
    const items = data.items || []
    searchResults.value = items
    if (items.length === 0) {
      ElMessage.info('未找到匹配的学生')
      addStudentForm.userId = null
      return
    }
    if (items.length === 1) {
      addStudentForm.userId = items[0].id
      ElMessage.success(`已找到学生：${items[0].realName}`)
    } else {
      ElMessage.info(`找到 ${items.length} 名学生，请在列表中选择`)
      addStudentForm.userId = null
    }
  } catch (error) {
    console.error('[TeacherTeachingClasses] 搜索学生失败', error)
    ElMessage.error('搜索学生失败')
  }
}

function handleSelectStudent(row) {
  if (row) {
    addStudentForm.userId = row.id
  }
}

async function confirmAddStudent() {
  if (!addStudentForm.userId) {
    ElMessage.warning('请先搜索并选择学生')
    return
  }
  if (!currentClassForAdd.value) return
  addingStudent.value = true
  try {
    await addStudentToClass(currentClassForAdd.value.id, addStudentForm.userId)
    ElMessage.success('添加成功')
    addStudentVisible.value = false
    // 清除缓存以便刷新
    delete studentData[currentClassForAdd.value.id]
    await fetchStudents(currentClassForAdd.value)
  } catch (error) {
    console.error('[TeacherTeachingClasses] 添加学生失败', error)
    ElMessage.error('添加失败')
  } finally {
    addingStudent.value = false
  }
}

// 移除学生
async function handleRemoveStudent(cls, student) {
  try {
    await ElMessageBox.confirm('确定移除该学生？', '提示', { type: 'warning' })
    await removeStudentFromClass(cls.id, student.id)
    ElMessage.success('移除成功')
    // 清除缓存以便刷新
    delete studentData[cls.id]
    await fetchStudents(cls)
  } catch (e) {
    if (e !== 'cancel') {
      console.error('[TeacherTeachingClasses] 移除学生失败', e)
      ElMessage.error('移除失败')
    }
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
    // 清除缓存以便刷新
    delete studentData[currentClassForStatus.value.id]
    await fetchStudents(currentClassForStatus.value)
  } catch (error) {
    console.error('[TeacherTeachingClasses] 修改学生状态失败', error)
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
  // CSS handles responsive layout via calc(100dvh - 168px)
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.teacher-teaching-classes {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

/* 课程卡片 & 教学班卡片 */
.course-card,
.class-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
  height: calc(100dvh - 180px);
  min-height: 400px;
  overflow-y: auto;
}

.course-card:hover,
.class-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
  transform: translateY(-2px);
}

.course-card :deep(.el-card__header),
.class-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

/* 课程列表 */
.course-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.course-item {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid transparent;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out);
}

.course-item:hover {
  background: var(--el-fill-color-light);
  border-left-color: var(--role-primary);
}

.course-item.is-active {
  background: var(--role-primary-light-9);
  border-left-color: var(--role-primary);
  border-color: var(--role-primary-light-7);
}

.course-title {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-1);
  letter-spacing: var(--tracking-tight);
}

.course-info {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

/* 教学班列表 */
.empty-tip {
  padding: var(--space-6) 0;
}

.class-groups {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.class-group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background-color: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.group-count {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-left: auto;
}

.group-classes {
  display: flex;
  flex-direction: column;
}

.class-item {
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.class-item:last-child {
  border-bottom: none;
}

.class-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  cursor: pointer;
  transition: background-color var(--duration-base) var(--ease-out);
}

.class-summary:hover {
  background-color: var(--role-primary-light-9);
}

.class-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.class-name {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-tight);
}

.status-tag {
  transform: scale(0.85);
}

.class-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.expand-icon {
  display: flex;
  align-items: center;
}

/* 展开详情 */
.class-detail {
  padding: var(--space-4);
  background-color: var(--el-fill-color-light);
  border-top: 1px solid var(--el-border-color-lighter);
}

.detail-actions {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.detail-actions :deep(.el-button) {
  border-radius: var(--radius-md);
}

.student-table {
  width: 100%;
}



.student-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9);
}

.no-students {
  padding: var(--space-4);
  text-align: center;
  color: var(--el-text-color-secondary);
}

.loading-wrap {
  padding: var(--space-4);
}

.full-width {
  width: 100%;
}

.text-secondary {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

/* 弹窗样式 */
.teacher-teaching-classes :deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

.teacher-teaching-classes :deep(.el-button) {
  border-radius: var(--radius-md);
}

.teacher-teaching-classes :deep(.el-button--primary) {
  border-radius: var(--radius-md);
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.teacher-teaching-classes :deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: var(--shadow-primary);
}

@media (max-width: 768px) {
  .teacher-teaching-classes {
    padding: var(--space-4);
  }

  .course-card,
  .class-card {
    height: auto;
    min-height: 400px;
  }
}
</style>