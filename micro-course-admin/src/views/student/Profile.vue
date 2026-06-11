<!--
  个人中心
  路由路径: /student/profile
  Phase 2
  Author: jackie
-->
<template>
  <div class="profile-view">
    <h2 class="page-title">个人中心</h2>

    <el-row :gutter="20">
      <el-col :span="16">
        <!-- 资料编辑 -->
        <el-card class="profile-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>基本资料</span>
            </div>
          </template>
          <el-form :model="profileForm" :rules="profileRules" ref="profileFormRef" label-width="80px">
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
              <el-button type="primary" @click="handleUpdateProfile" :loading="profileLoading">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 密码修改 -->
        <el-card class="profile-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>修改密码</span>
            </div>
          </template>
          <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
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
              <el-button type="primary" @click="handleChangePassword" :loading="passwordLoading">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="8">
        <!-- 头像 -->
        <el-card class="profile-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>头像设置</span>
            </div>
          </template>
          <div class="avatar-section">
            <el-avatar :size="80" :src="userStore.userInfo?.avatar" />
            <div class="avatar-tip">支持 JPG、PNG格式，建议200x200 像素</div>
          </div>
        </el-card>

        <!-- 账号信息 -->
        <el-card class="profile-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>账号信息</span>
            </div>
          </template>
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">用户ID</span>
              <span class="info-value">{{ userStore.userInfo?.id }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">角色</span>
              <span class="info-value">{{ userStore.userInfo?.role }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">注册时间</span>
              <span class="info-value">{{ userStore.userInfo?.createdAt }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 成就 -->
    <el-card class="profile-card achievement-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的成就</span>
        </div>
      </template>
      <div v-loading="badgeLoading" class="badge-grid">
        <div
          v-for="badge in allBadges"
          :key="badge.badgeType"
          class="badge-item"
          :class="{ 'badge-locked': !badge.earnedAt }"
        >
          <div class="badge-icon">
            <el-icon v-if="badge.earnedAt" color="#67c23a" :size="32"><Star /></el-icon>
            <el-icon v-else color="#c0c4cc" :size="32"><Lock /></el-icon>
          </div>
          <div class="badge-name">{{ badge.badgeName }}</div>
          <div v-if="badge.earnedAt" class="badge-date">{{ badge.earnedAt }}</div>
          <div v-else class="badge-tip">未解锁</div>
        </div>
      </div>
      <el-empty v-if="!badgeLoading && allBadges.length === 0" description="暂无成就数据" :image-size="60" />
    </el-card>

    <!-- 错题集 -->
    <el-card class="profile-card wrong-questions-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的错题</span>
        </div>
      </template>

      <div class="wrong-toolbar">
        <el-select v-model="selectedCourseId" placeholder="选择课程筛选" clearable @change="fetchWrongQuestions">
          <el-option
            v-for="course in myCourses"
            :key="course.courseId"
            :label="course.courseTitle"
            :value="course.courseId"
          />
        </el-select>
      </div>

      <el-table v-loading="wrongLoading" :data="wrongQuestions" stripe border max-height="400">
        <el-table-column prop="questionContent" label="错题内容" min-width="200">
          <template #default="{ row }">
            <span>{{ row.questionContent || row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseTitle" label="所属课程" width="150" />
        <el-table-column prop="chapterTitle" label="所属章节" width="150" />
        <el-table-column prop="wrongCount" label="错误次数" width="100" align="center" />
        <el-table-column prop="correctAnswer" label="正确答案" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">{{ row.correctAnswer }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="入库时间" width="170" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.chapterId"
              type="primary"
              size="small"
              text
              @click="handleReviewVideo(row)"
            >
              重温视频
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!wrongLoading && wrongQuestions.length === 0" description="暂无错题记录，继续保持！" />
    </el-card>

    <!-- 我的证书 -->
    <el-card class="profile-card certificate-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的证书</span>
        </div>
      </template>

      <div v-loading="certLoading" class="cert-grid">
        <div
          v-for="cert in certificates"
          :key="cert.id"
          class="cert-item"
        >
          <div class="cert-icon">
            <el-icon color="#c9a227" :size="40"><Medal /></el-icon>
          </div>
          <div class="cert-info">
            <div class="cert-title">{{ cert.courseTitle }}</div>
            <div class="cert-meta">
              <span class="cert-date">颁发于 {{ formatDate(cert.issuedAt) }}</span>
              <span class="cert-code">{{ cert.certCode }}</span>
            </div>
          </div>
          <div class="cert-actions">
            <el-button type="primary" size="small" @click="handleViewCertificate(cert)">
              查看证书
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!certLoading && certificates.length === 0" description="暂无证书记录，完成课程学习后可获得" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { updateProfile, changePassword } from '../../api/auth'
import { getMyEnrollments } from '../../api/enrollment'
import { getMyBadges } from '../../api/badge'
import { getMyWrongQuestions } from '../../api/wrong-question'
import { getMyCertificates, downloadCertificate } from '../../api/certificate'

const router = useRouter()

const userStore = useUserStore()

const profileFormRef = ref(null)
const passwordFormRef = ref(null)

const profileForm = ref({
  realName: '',
  email: '',
  phone: '',
  gender: ''
})

const profileRules = {
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

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
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const profileLoading = ref(false)
const passwordLoading = ref(false)

// 错题集
const wrongQuestions = ref([])
const wrongLoading = ref(false)
const myCourses = ref([])
const selectedCourseId = ref('')

// 证书
const certificates = ref([])
const certLoading = ref(false)

// 成就徽章
const badgeLoading = ref(false)
const earnedBadges = ref([])

const allBadges = computed(() => {
  const badgeTypes = [
    { badgeType: 'FIRST_COURSE', badgeName: '初识课程' },
    { badgeType: 'ALL_COURSES', badgeName: '学满全部' },
    { badgeType: 'SEVEN_DAY_STREAK', badgeName: '连续打卡' }
  ]
  return badgeTypes.map(b => {
    const earned = earnedBadges.value.find(e => e.badgeType === b.badgeType)
    return earned ? { ...b, ...earned } : { ...b, earnedAt: null }
  })
})

const fetchBadges = async () => {
  badgeLoading.value = true
  try {
    const res = await getMyBadges()
    earnedBadges.value = res.data || []
  } catch {
    earnedBadges.value = []
  } finally {
    badgeLoading.value = false
  }
}

const handleUpdateProfile = async () => {
  profileLoading.value = true
  try {
    await updateProfile(profileForm.value)
    ElMessage.success('资料更新成功')
    await userStore.getInfo()
  } catch {
    ElMessage.error('资料更新失败')
  } finally {
    profileLoading.value = false
  }
}

const handleChangePassword = async () => {
  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }
  passwordLoading.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功')
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    passwordFormRef.value?.resetFields()
  } catch {
    ElMessage.error('密码修改失败')
  } finally {
    passwordLoading.value = false
  }
}

const fetchMyEnrollments = async () => {
  try {
    const userId = userStore.userInfo?.id
    if (!userId) return
    const res = await getMyEnrollments(userId)
    myCourses.value = res.data || []
  } catch {
    // silent
  }
}

const fetchWrongQuestions = async () => {
  wrongLoading.value = true
  try {
    const params = {}
    if (selectedCourseId.value) params.courseId = selectedCourseId.value
    const res = await getMyWrongQuestions(params)
    wrongQuestions.value = res.data?.items || res.data || []
  } catch {
    ElMessage.error('获取错题记录失败')
  } finally {
    wrongLoading.value = false
  }
}

const handleReviewVideo = (row) => {
  if (!row.chapterId) return
  const query = { chapterId: row.chapterId }
  if (row.watchPosition) {
    query.timestamp = row.watchPosition
  }
  router.push({ path: `/student/courses/${row.courseId}`, query })
}

const fetchCertificates = async () => {
  certLoading.value = true
  try {
    const res = await getMyCertificates()
    certificates.value = res.data || []
  } catch {
    ElMessage.error('获取证书记录失败')
  } finally {
    certLoading.value = false
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}年${month}月${day}日`
}

const handleViewCertificate = async (cert) => {
  try {
    const res = await downloadCertificate(cert.id)
    const html = res.data
    const newWindow = window.open('', '_blank')
    if (newWindow) {
      newWindow.document.write(html)
      newWindow.document.close()
    } else {
      ElMessage.error('弹出窗口被拦截，请允许弹出窗口')
    }
  } catch {
    ElMessage.error('查看证书失败')
  }
}

onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.getInfo()
  }
  // 初始化表单
  const info = userStore.userInfo
  if (info) {
    profileForm.value = {
      realName: info.realName || '',
      email: info.email || '',
      phone: info.phone || '',
      gender: info.gender || 'SECRET'
    }
  }
  // 加载错题集
  fetchWrongQuestions()
  fetchMyEnrollments()
  // 加载证书
  fetchCertificates()
  // 加载成就徽章
  fetchBadges()
})
</script>

<style scoped>
.profile-view {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-title {
  margin: 0 0 20px 0;
  font-size: 20px;
  color: #303133;
}

.profile-card {
  margin-bottom: 20px;
}

.card-header {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.avatar-tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
}

.info-label {
  color: #909399;
}

.info-value {
  color: #303133;
}

.wrong-toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
}

.wrong-questions-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.cert-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cert-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.cert-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fef0e6 0%, #fff7e6 100%);
  border-radius: 50%;
}

.cert-info {
  flex: 1;
  min-width: 0;
}

.cert-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.cert-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cert-date {
  font-size: 13px;
  color: #909399;
}

.cert-code {
  font-size: 12px;
  color: #c0c4cc;
  font-family: "Courier New", monospace;
}

.cert-actions {
  flex-shrink: 0;
}

.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.badge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  transition: all 0.3s ease;
}

.badge-item.badge-locked {
  background: #f0f0f0;
  opacity: 0.6;
}

.badge-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.badge-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  text-align: center;
}

.badge-date {
  font-size: 12px;
  color: #67c23a;
  text-align: center;
}

.badge-tip {
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
}

.achievement-card :deep(.el-card__header) {
  padding: 12px 20px;
}

@media (max-width: 768px) {
  .badge-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>