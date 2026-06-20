<!--
  个人中心
  路由路径: /student/profile
  Phase 2
  Author: jackie
-->
<template>
  <div class="profile-view">
    <!-- PC 端布局 -->
    <template v-if="!isMobile">
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
              <el-upload
                class="avatar-uploader"
                :show-file-list="false"
                :auto-upload="false"
                accept="image/jpeg,image/png,image/webp"
                :on-change="handleAvatarChange"
              >
                <el-avatar :size="80" :src="avatarPreview || userStore.userInfo?.avatar" />
              </el-upload>
              <div class="avatar-tip">支持 JPG、PNG、WebP 格式，建议 200×200 像素，≤2MB</div>
              <div class="avatar-actions">
                <el-button
                  v-if="avatarPreview"
                  type="primary"
                  size="small"
                  :loading="avatarLoading"
                  @click="handleSaveAvatar"
                >
                  保存头像
                </el-button>
                <el-button
                  v-if="avatarPreview"
                  size="small"
                  @click="handleCancelAvatar"
                >
                  取消
                </el-button>
              </div>
            </div>
          </el-card>

          <!-- 账号信息 -->
          <el-card class="profile-card student-stat-card" shadow="never">
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

      <template v-if="isStudent">
        <!-- 成就 -->
        <el-card class="profile-card achievement-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>我的成就</span>
              <el-button type="primary" link @click="$router.push('/student/achievements')">
                查看全部 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>
          <div v-loading="badgeLoading" :aria-busy="badgeLoading" class="badge-grid">
            <div
              v-for="badge in allBadges"
              :key="badge.badgeType"
              class="badge-item student-card-item"
              :class="{ 'badge-locked': !badge.earnedAt }"
            >
              <div class="badge-icon">
                <el-icon v-if="badge.earnedAt" color="var(--role-primary)" :size="32"><Star /></el-icon>
                <el-icon v-else color="var(--el-text-color-placeholder)" :size="32"><Lock /></el-icon>
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

          <div class="wrong-table-wrapper">
            <el-table v-loading="wrongLoading" :aria-busy="wrongLoading" :data="wrongQuestions" stripe border max-height="400" class="data-table wrong-questions-table">
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
          </div>
          <el-empty v-if="!wrongLoading && wrongQuestions.length === 0" description="暂无错题记录，继续保持！" />
        </el-card>

        <!-- 我的证书 -->
        <el-card class="profile-card certificate-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>我的证书</span>
            </div>
          </template>

          <div v-loading="certLoading" :aria-busy="certLoading" class="cert-grid">
            <div
              v-for="cert in certificates"
              :key="cert.id"
              class="cert-item"
            >
              <div class="cert-icon">
                <el-icon color="var(--role-primary)" :size="40"><Medal /></el-icon>
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
      </template>
    </template>

    <!-- 移动端布局 -->
    <template v-else>
      <h2 class="page-title page-title--mobile">个人中心</h2>

      <!-- 用户信息卡片 -->
      <el-card class="profile-card user-info-card" shadow="never">
        <div class="user-info-content">
          <el-upload
            class="avatar-uploader-mobile"
            :show-file-list="false"
            :auto-upload="false"
            accept="image/jpeg,image/png,image/webp"
            :on-change="handleAvatarChange"
          >
            <el-avatar :size="60" :src="avatarPreview || userStore.userInfo?.avatar" />
          </el-upload>
          <div class="user-info-text">
            <div class="user-info-name">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</div>
            <div class="user-info-role">{{ userStore.userInfo?.role }}</div>
          </div>
        </div>
        <div v-if="avatarPreview" class="avatar-actions-mobile">
          <el-button type="primary" size="small" :loading="avatarLoading" @click="handleSaveAvatar">保存头像</el-button>
          <el-button size="small" @click="handleCancelAvatar">取消</el-button>
        </div>
      </el-card>

      <!-- 基本资料 -->
      <el-card class="profile-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>基本资料</span>
          </div>
        </template>
        <el-form :model="profileForm" :rules="profileRules" ref="profileFormRef" label-width="70px" size="small">
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
            <el-button type="primary" @click="handleUpdateProfile" :loading="profileLoading" size="default">保存修改</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 账号信息 -->
      <el-card class="profile-card student-stat-card" shadow="never">
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
            <span class="info-label">注册时间</span>
            <span class="info-value">{{ userStore.userInfo?.createdAt }}</span>
          </div>
        </div>
      </el-card>

      <!-- 修改密码 -->
      <el-card class="profile-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>修改密码</span>
          </div>
        </template>
        <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="80px" size="small">
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
            <el-button type="primary" @click="handleChangePassword" :loading="passwordLoading" size="default">修改密码</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <template v-if="isStudent">
        <!-- 成就 -->
        <el-card class="profile-card achievement-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>我的成就</span>
            </div>
          </template>
          <div v-loading="badgeLoading" :aria-busy="badgeLoading" class="badge-grid badge-grid--mobile">
            <div
              v-for="badge in allBadges"
              :key="badge.badgeType"
              class="badge-item student-card-item"
              :class="{ 'badge-locked': !badge.earnedAt }"
            >
              <div class="badge-icon">
                <el-icon v-if="badge.earnedAt" color="var(--role-primary)" :size="28"><Star /></el-icon>
                <el-icon v-else color="var(--el-text-color-placeholder)" :size="28"><Lock /></el-icon>
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
            <el-select v-model="selectedCourseId" placeholder="选择课程筛选" clearable @change="fetchWrongQuestions" class="course-select">
              <el-option
                v-for="course in myCourses"
                :key="course.courseId"
                :label="course.courseTitle"
                :value="course.courseId"
              />
            </el-select>
          </div>

          <div class="wrong-table-wrapper">
            <el-table v-loading="wrongLoading" :aria-busy="wrongLoading" :data="wrongQuestions" stripe border max-height="300" class="data-table wrong-questions-table">
              <el-table-column prop="questionContent" label="错题内容" min-width="150">
                <template #default="{ row }">
                  <span>{{ row.questionContent || row.content }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="courseTitle" label="课程" width="100" show-overflow-tooltip />
              <el-table-column prop="correctAnswer" label="答案" width="70" align="center">
                <template #default="{ row }">
                  <el-tag type="success" size="small">{{ row.correctAnswer }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button
                    v-if="row.chapterId"
                    type="primary"
                    size="small"
                    text
                    @click="handleReviewVideo(row)"
                  >
                    重温
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-empty v-if="!wrongLoading && wrongQuestions.length === 0" description="暂无错题记录，继续保持！" />
        </el-card>

        <!-- 我的证书 -->
        <el-card class="profile-card certificate-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>我的证书</span>
            </div>
          </template>

          <div v-loading="certLoading" :aria-busy="certLoading" class="cert-list--mobile">
            <div
              v-for="cert in certificates"
              :key="cert.id"
              class="cert-item cert-item--mobile"
            >
              <div class="cert-icon">
                <el-icon color="var(--role-primary)" :size="32"><Medal /></el-icon>
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
                  查看
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-if="!certLoading && certificates.length === 0" description="暂无证书记录，完成课程学习后可获得" :image-size="60" />
        </el-card>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { updateProfile, changePassword, uploadAvatar } from '../../api/auth'
import { getMyEnrollments } from '../../api/enrollment'
import { getMyBadges } from '../../api/badge'
import { getMyWrongQuestions } from '../../api/wrong-question'
import { getMyCertificates, downloadCertificate } from '../../api/certificate'
import { ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()

const userStore = useUserStore()
const isStudent = computed(() => userStore.role === 'STUDENT')

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
    { min: 8, message: '密码长度不能少于8位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d)/, message: '密码需包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const profileLoading = ref(false)
const passwordLoading = ref(false)

// 头像上传
const avatarPreview = ref('')
const avatarLoading = ref(false)
const avatarFile = ref(null)
const avatarMaxSize = 2 * 1024 * 1024 // 2MB

const handleAvatarChange = (uploadFile) => {
  const file = uploadFile?.raw
  if (!file) return
  if (file.size > avatarMaxSize) {
    ElMessage.error('图片大小不能超过 2MB')
    return
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    ElMessage.error('只支持 JPG、PNG、WebP 格式')
    return
  }
  avatarFile.value = file
  const reader = new FileReader()
  reader.onload = (e) => {
    avatarPreview.value = e.target.result
  }
  reader.readAsDataURL(file)
}

/**
 * Canvas 压缩头像到 200×200 JPEG
 */
const compressAvatar = (file) => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = 200
      canvas.height = 200
      const ctx = canvas.getContext('2d')
      // 居中裁剪
      const size = Math.min(img.width, img.height)
      const sx = (img.width - size) / 2
      const sy = (img.height - size) / 2
      ctx.drawImage(img, sx, sy, size, size, 0, 0, 200, 200)
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(new File([blob], 'avatar.jpg', { type: 'image/jpeg' }))
          } else {
            reject(new Error('图片压缩失败'))
          }
        },
        'image/jpeg',
        0.8
      )
    }
    img.onerror = () => reject(new Error('图片加载失败'))
    img.src = URL.createObjectURL(file)
  })
}

const handleSaveAvatar = async () => {
  if (!avatarFile.value) {
    ElMessage.warning('请先选择头像')
    return
  }
  avatarLoading.value = true
  try {
    const compressed = await compressAvatar(avatarFile.value)
    await uploadAvatar(compressed)
    await userStore.getInfo()
    avatarPreview.value = ''
    avatarFile.value = null
    ElMessage.success('头像更新成功')
  } catch (e) {
    const msg = e?.message || '头像更新失败'
    if (msg.includes('格式')) {
      ElMessage.error('图片格式不支持，请使用 JPG/PNG/WebP')
    } else if (msg.includes('大小') || msg.includes('2MB') || msg.includes('2 MB')) {
      ElMessage.error('图片过大，请使用不超过 2MB 的图片')
    } else {
      // 拦截器已展示后端错误，避免重复
    }
  } finally {
    avatarLoading.value = false
  }
}

const handleCancelAvatar = () => {
  avatarPreview.value = ''
  avatarFile.value = null
}

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

// 响应式布局
const isMobile = ref(window.innerWidth <= 768)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

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
  } catch (e) {
    console.warn('[Profile] 获取成就徽章失败:', e)
    earnedBadges.value = []
  } finally {
    badgeLoading.value = false
  }
}

const handleUpdateProfile = async () => {
  try {
    await profileFormRef.value.validate()
  } catch {
    return
  }
  profileLoading.value = true
  try {
    await updateProfile(profileForm.value)
    ElMessage.success('资料更新成功')
    await userStore.getInfo()
  } catch {
    // 拦截器已展示后端具体错误，此处不重复提示
  } finally {
    profileLoading.value = false
  }
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

const fetchMyEnrollments = async () => {
  try {
    const userId = userStore.userInfo?.id
    if (!userId) return
    const res = await getMyEnrollments({ userId })
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
    wrongQuestions.value = res.data || []
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
    const blob = new Blob([res.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch {
    ElMessage.error('查看证书失败')
  }
}

onMounted(async () => {
  checkMobile()
  window.addEventListener('resize', checkMobile)

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
  // 并行加载所有数据（P2 优化）
  await Promise.all([
    fetchWrongQuestions(),
    fetchMyEnrollments(),
    fetchCertificates(),
    fetchBadges()
  ])
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
/* === Profile View === */
.profile-view {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
}

/* === Page Title === */
.page-title {
  margin: 0 0 var(--space-5) 0;
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.page-title--mobile {
  font-size: var(--text-lg);
  margin: 0 0 var(--space-4) 0;
}

/* === Cards === */
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

/* === Avatar Section === */
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.avatar-tip {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  text-align: center;
}

.avatar-uploader :deep(.el-upload) {
  cursor: pointer;
  border: 2px dashed var(--el-border-color);
  border-radius: var(--radius-pill);
  padding: 2px;
  transition: border-color var(--duration-base) var(--ease-out);
}

.avatar-uploader :deep(.el-upload:hover) {
  border-color: var(--role-primary);
}

.avatar-uploader-mobile :deep(.el-upload) {
  cursor: pointer;
}

.avatar-actions {
  display: flex;
  gap: var(--space-2);
  justify-content: center;
}

.avatar-actions-mobile {
  display: flex;
  gap: var(--space-2);
  justify-content: center;
  margin-top: var(--space-3);
}

/* === Info List === */
.info-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.info-item {
  display: flex;
  justify-content: space-between;
  font-size: var(--text-sm);
}

.info-label {
  color: var(--el-text-color-secondary);
}

.info-value {
  color: var(--el-text-color-primary);
}

/* === Student Stat Card === */
.student-stat-card {
  background: linear-gradient(135deg, var(--role-primary-light-9) 0%, var(--role-primary-light-7) 100%);
  border: 1px solid rgba(99, 102, 241, 0.08);
}

.student-stat-card .info-item {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.7);
  transition: background var(--duration-base) ease;
}

.student-stat-card .info-item:hover {
  background: rgba(255, 255, 255, 0.95);
}

/* === Badge Grid === */
.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.badge-grid--mobile {
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.badge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--role-primary-light);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}

.badge-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

/* === Student Card Item (badge refinement) === */
.student-card-item {
  border: 1px solid rgba(99, 102, 241, 0.1);
  backdrop-filter: blur(4px);
}

.student-card-item:not(.badge-locked) {
  background: linear-gradient(135deg, var(--role-primary-light) 0%, var(--role-primary-light-9) 100%);
}

.badge-item.badge-locked {
  background: var(--el-fill-color-light);
  opacity: 0.7;
}

.badge-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.badge-name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  text-align: center;
}

.badge-date {
  font-size: var(--text-xs);
  color: var(--role-primary);
  text-align: center;
}

.badge-tip {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  text-align: center;
}

/* === Certificate Grid === */
.cert-grid {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.cert-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}

.cert-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.cert-item--mobile {
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-3);
}

.cert-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--role-primary-light) 0%, var(--role-primary) 100%);
  border-radius: var(--radius-circle);
}

.cert-info {
  flex: 1;
  min-width: 0;
}

.cert-title {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-1);
}

.cert-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cert-date {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.cert-code {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  font-family: "Courier New", monospace;
}

.cert-actions {
  flex-shrink: 0;
}

.cert-actions .el-button {
  cursor: pointer;
}

/* === Wrong Questions Table === */
.wrong-toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  gap: var(--space-3);
}

.course-select {
  width: 200px;
}

.wrong-table-wrapper {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.wrong-questions-table {
  border-radius: var(--radius-md);
}

.wrong-questions-table .el-button {
  cursor: pointer;
}

.wrong-questions-card :deep(.el-card__header) {
  padding: 12px var(--space-5);
}

/* === User Info Card (Mobile) === */
.user-info-card {
  margin-bottom: var(--space-4);
}

.user-info-content {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.user-info-text {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.user-info-name {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.user-info-role {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

/* === Mobile Cert List === */
.cert-list--mobile {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.cert-list--mobile .cert-item {
  flex-direction: row;
  align-items: center;
}

.cert-list--mobile .cert-icon {
  width: 40px;
  height: 40px;
}

.cert-list--mobile .cert-actions {
  width: auto;
}

.cert-list--mobile .cert-actions .el-button {
  width: auto;
}

/* === All Buttons Cursor === */
:deep(.el-button) {
  cursor: pointer;
}

/* === Achievement Card === */
.achievement-card :deep(.el-card__header) {
  padding: 12px var(--space-5);
}

/* === Mobile Responsive === */
@media (max-width: 768px) {
  .profile-view {
    padding: var(--space-3);
  }

  .profile-card {
    margin-bottom: var(--space-4);
  }

  .wrong-toolbar {
    flex-direction: column;
    gap: var(--space-2);
  }

  .wrong-toolbar .el-select,
  .course-select {
    width: 100%;
  }

  .cert-item--mobile {
    flex-direction: column;
    align-items: flex-start;
  }

  .cert-list--mobile .cert-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .cert-actions {
    width: 100%;
  }

  .cert-actions .el-button {
    width: 100%;
  }
}
</style>