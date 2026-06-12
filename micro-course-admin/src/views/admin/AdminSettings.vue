<!--
  管理员 - 系统设置
  /admin/settings
  Author: jackie
-->
<template>
  <div class="admin-settings-container">
    <!-- 顶部说明卡片 -->
    <el-card class="info-card shadow-hover" shadow="never">
      <div class="info-content">
        <el-icon :size="20" class="info-icon"><InfoFilled /></el-icon>
        <div>
          <p class="info-title">系统设置管理</p>
          <p class="info-desc">管理平台运行时配置参数，修改后即时生效。</p>
        </div>
      </div>
    </el-card>

    <!-- 主体：左侧菜单 + 右侧表单 -->
    <div class="settings-layout">
      <!-- 左侧菜单 -->
      <el-card class="menu-card" shadow="never">
        <el-menu
          :default-active="activeMenu"
          class="settings-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="system">
            <el-icon><Setting /></el-icon>
            <template #title>系统参数</template>
          </el-menu-item>
          <el-menu-item index="mail">
            <el-icon><Message /></el-icon>
            <template #title>邮件配置</template>
          </el-menu-item>
          <el-menu-item index="security">
            <el-icon><Lock /></el-icon>
            <template #title>安全设置</template>
          </el-menu-item>
          <el-menu-item index="cas">
            <el-icon><Key /></el-icon>
            <template #title>CAS 配置</template>
          </el-menu-item>
          <el-menu-item index="about">
            <el-icon><InfoFilled /></el-icon>
            <template #title>关于系统</template>
          </el-menu-item>
        </el-menu>
      </el-card>

      <!-- 右侧表单 -->
      <div class="settings-content">
        <!-- 系统参数 -->
        <el-card v-show="activeMenu === 'system'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">系统参数</span>
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('system')">
                <el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-form :model="systemForm" label-width="140px" class="settings-form">
            <el-form-item label="平台名称">
              <el-input v-model="systemForm.platformName" placeholder="请输入平台名称" />
            </el-form-item>
            <el-form-item label="平台 Logo URL">
              <el-input v-model="systemForm.logoUrl" placeholder="请输入 Logo 地址" />
            </el-form-item>
            <el-form-item label="系统版本">
              <el-input :model-value="APP_VERSION" disabled />
            </el-form-item>
            <el-form-item label="文件上传大小限制">
              <el-input-number
                v-model="systemForm.maxUploadSize"
                :min="1"
                :max="500"
                controls-position="right"
              />
              <span class="form-unit">MB</span>
            </el-form-item>
            <el-form-item label="会话超时时间">
              <el-input-number
                v-model="systemForm.sessionTimeout"
                :min="5"
                :max="1440"
                controls-position="right"
              />
              <span class="form-unit">分钟</span>
            </el-form-item>
            <el-form-item label="启用注册">
              <el-switch v-model="systemForm.allowRegistration" />
            </el-form-item>
            <el-form-item label="维护模式">
              <el-switch v-model="systemForm.maintenanceMode" />
              <span class="form-hint">开启后普通用户将无法登录</span>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 邮件配置 -->
        <el-card v-show="activeMenu === 'mail'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">邮件配置</span>
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('mail')">
                <el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-form :model="mailForm" label-width="140px" class="settings-form">
            <el-form-item label="SMTP 服务器">
              <el-input v-model="mailForm.smtpHost" placeholder="smtp.example.com" />
            </el-form-item>
            <el-form-item label="SMTP 端口">
              <el-input-number
                v-model="mailForm.smtpPort"
                :min="1"
                :max="65535"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="mailForm.smtpUsername" placeholder="your@email.com" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="mailForm.smtpPassword"
                type="password"
                show-password
                placeholder="请输入密码"
              />
            </el-form-item>
            <el-form-item label="发件人昵称">
              <el-input v-model="mailForm.fromName" placeholder="微课平台" />
            </el-form-item>
            <el-form-item label="启用 SSL">
              <el-switch v-model="mailForm.useSsl" />
            </el-form-item>
            <el-form-item label="启用 TLS">
              <el-switch v-model="mailForm.useTls" />
            </el-form-item>
            <el-form-item>
              <el-button @click="handleTestMail">发送测试邮件</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 安全设置 -->
        <el-card v-show="activeMenu === 'security'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">安全设置</span>
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('security')">
                <el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-form :model="securityForm" label-width="140px" class="settings-form">
            <el-form-item label="密码最小长度">
              <el-input-number
                v-model="securityForm.minPasswordLength"
                :min="6"
                :max="32"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="密码必须包含数字">
              <el-switch v-model="securityForm.requireNumber" />
            </el-form-item>
            <el-form-item label="密码必须包含特殊字符">
              <el-switch v-model="securityForm.requireSpecialChar" />
            </el-form-item>
            <el-form-item label="登录失败锁定">
              <el-switch v-model="securityForm.lockOnFailure" />
            </el-form-item>
            <el-form-item v-if="securityForm.lockOnFailure" label="失败次数上限">
              <el-input-number
                v-model="securityForm.maxFailAttempts"
                :min="3"
                :max="10"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item v-if="securityForm.lockOnFailure" label="锁定时长">
              <el-input-number
                v-model="securityForm.lockDuration"
                :min="5"
                :max="1440"
                controls-position="right"
              />
              <span class="form-unit">分钟</span>
            </el-form-item>
            <el-form-item label="双因素认证">
              <el-switch v-model="securityForm.require2FA" />
              <span class="form-hint">开启后管理员登录需验证手机验证码</span>
            </el-form-item>
            <el-form-item label="Token 有效期">
              <el-input-number
                v-model="securityForm.tokenExpiry"
                :min="30"
                :max="86400"
                controls-position="right"
              />
              <span class="form-unit">分钟</span>
            </el-form-item>
            <el-form-item label="刷新 Token 有效期">
              <el-input-number
                v-model="securityForm.refreshTokenExpiry"
                :min="1"
                :max="30"
                controls-position="right"
              />
              <span class="form-unit">天</span>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- CAS 配置 -->
        <el-card v-show="activeMenu === 'cas'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">CAS 统一身份认证配置</span>
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('cas')">
                <el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--space-4)">
            <template #title>
              CAS 配置用于对接学校统一身份认证系统。配置后将支持师生通过学校账号一键登录。
            </template>
          </el-alert>
          <el-form :model="casForm" label-width="140px" class="settings-form">
            <el-form-item label="启用 CAS">
              <el-switch v-model="casForm.enabled" />
              <span class="form-hint">开启后将启用 CAS 单点登录</span>
            </el-form-item>
            <el-form-item label="CAS 服务器 URL">
              <el-input v-model="casForm.serverUrl" placeholder="https://cas.example.edu.cn" />
              <span class="form-hint">学校 CAS 服务器地址</span>
            </el-form-item>
            <el-form-item label="CAS Service URL">
              <el-input v-model="casForm.serviceUrl" placeholder="https://micro-course.example.edu.cn/cas/validate" />
              <span class="form-hint">本系统用于 CAS 回调的 URL</span>
            </el-form-item>
            <el-form-item label="CAS 版本">
              <el-select v-model="casForm.version" placeholder="请选择" class="full-width">
                <el-option label="CAS 2.0" value="2.0" />
                <el-option label="CAS 3.0" value="3.0" />
                <el-option label="SAML 2.0" value="saml2" />
              </el-select>
            </el-form-item>
            <el-form-item label="管理员账号">
              <el-input v-model="casForm.adminUsername" placeholder="管理员 CAS 用户名" />
              <span class="form-hint">拥有管理员权限的 CAS 账号</span>
            </el-form-item>
            <el-form-item label="超级管理员列表">
              <el-input
                v-model="casForm.superAdmins"
                type="textarea"
                :rows="2"
                placeholder="多个账号用逗号分隔，如: zhangsan,lisi"
              />
              <span class="form-hint">CAS 账号列表，列表内用户将自动获得管理员权限</span>
            </el-form-item>
            <el-form-item label="启用 SSL 校验">
              <el-switch v-model="casForm.validateSsl" />
              <span class="form-hint">是否验证 CAS 服务器 SSL 证书</span>
            </el-form-item>
            <el-form-item label="测试连接">
              <el-button @click="handleTestCas">测试 CAS 连接</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 关于系统 -->
        <el-card v-show="activeMenu === 'about'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">关于系统</span>
            </div>
          </template>
          <el-descriptions :column="1" border class="about-descriptions">
            <el-descriptions-item label="系统名称">微课管理平台</el-descriptions-item>
            <el-descriptions-item label="当前版本">{{ APP_VERSION }}</el-descriptions-item>
            <el-descriptions-item label="技术栈">
              Spring Boot 3.2 + Vue 3.4 + Element Plus 2.5
            </el-descriptions-item>
            <el-descriptions-item label="数据库">PostgreSQL 17.5</el-descriptions-item>
            <el-descriptions-item label="缓存">Redis 7</el-descriptions-item>
            <el-descriptions-item label="开发团队">微课平台开发组</el-descriptions-item>
            <el-descriptions-item label="许可证">MIT License</el-descriptions-item>
            <el-descriptions-item label="官方文档">
              <a href="#" class="about-link">https://docs.example.com</a>
            </el-descriptions-item>
            <el-descriptions-item label="问题反馈">
              <a href="#" class="about-link">https://github.com/example/micro-course/issues</a>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 管理员 - 系统设置
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted } from 'vue'
// 版本号从环境变量动态读取
const APP_VERSION = import.meta.env.VITE_APP_VERSION || '1.0.0'
import { ElMessage } from 'element-plus'
import {
  InfoFilled, Setting, Message, Lock, Check, Key
} from '@element-plus/icons-vue'
import { getSettings, updateSettings } from '@/api/admin-settings'

// 加载状态
const loading = ref(false)
const saving = ref(false)

// 当前激活菜单
const activeMenu = ref('system')

// 系统参数表单
const systemForm = reactive({
  platformName: '微课管理平台',
  logoUrl: '',
  version: APP_VERSION,
  maxUploadSize: 100,
  sessionTimeout: 120,
  allowRegistration: true,
  maintenanceMode: false
})

// 邮件配置表单
const mailForm = reactive({
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  fromName: '微课平台',
  useSsl: false,
  useTls: true
})

// 安全设置表单
const securityForm = reactive({
  minPasswordLength: 8,
  requireNumber: true,
  requireSpecialChar: false,
  lockOnFailure: true,
  maxFailAttempts: 5,
  lockDuration: 30,
  require2FA: false,
  tokenExpiry: 480,
  refreshTokenExpiry: 7
})

// CAS 配置表单 (localStorage mock)
const casForm = reactive({
  enabled: false,
  serverUrl: '',
  serviceUrl: '',
  version: '3.0',
  adminUsername: '',
  superAdmins: '',
  validateSsl: true
})

// 获取设置列表
async function fetchSettings() {
  loading.value = true
  try {
    const res = await getSettings()
    const items = res.data?.items || res.data || []
    // 填充到各表单
    items.forEach(item => {
      if (item.settingKey in systemForm) {
        systemForm[item.settingKey] = item.settingValue
      }
      if (item.settingKey in mailForm) {
        mailForm[item.settingKey] = item.settingValue
      }
      if (item.settingKey in securityForm) {
        securityForm[item.settingKey] = item.settingValue
      }
    })
    // CAS 设置从 localStorage 加载 (mock)
    const storedCas = localStorage.getItem('cas_settings')
    if (storedCas) {
      try {
        const parsed = JSON.parse(storedCas)
        Object.assign(casForm, parsed)
      } catch (e) { console.warn('配置解析失败:', e); }
    }
  } catch {
    // 不报错，用默认值
  } finally {
    loading.value = false
  }
}

// 选择菜单
function handleMenuSelect(index) {
  activeMenu.value = index
}

// 保存修改
async function handleSave(menu) {
  saving.value = true
  try {
    if (menu === 'cas') {
      // TODO: 后端暂无 /admin/settings/cas 接口，先保留 localStorage mock
      // 提示用户此为演示功能
      localStorage.setItem('cas_settings', JSON.stringify(casForm))
      ElMessage.warning('CAS 配置已本地保存（演示功能，正式环境请配置后端 API）')
      saving.value = false
      return
    }

    let formData
    let keys
    if (menu === 'system') {
      formData = systemForm
      keys = Object.keys(systemForm)
    } else if (menu === 'mail') {
      formData = mailForm
      keys = Object.keys(mailForm)
    } else if (menu === 'security') {
      formData = securityForm
      keys = Object.keys(securityForm)
    }

    const updates = keys.map(key => ({
      settingKey: key,
      settingValue: formData[key]
    }))

    await updateSettings(updates)
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

// 测试 CAS 连接
async function handleTestCas() {
  if (!casForm.serverUrl) {
    ElMessage.warning('请先填写 CAS 服务器 URL')
    return
  }
  ElMessage.warning('CAS 连接测试需要后端支持，当前为演示模式')
  // 模拟测试结果
  setTimeout(() => {
    ElMessage.success('模拟测试成功（实际需后端支持）')
  }, 1500)
}

// 测试邮件
async function handleTestMail() {
  if (!mailForm.smtpHost || !mailForm.smtpUsername) {
    ElMessage.warning('请先填写完整的邮件配置')
    return
  }
  try {
    await updateSettings([{ settingKey: 'test_mail', settingValue: 'true' }])
    ElMessage.success('测试邮件已发送，请检查收件箱')
  } catch {
    ElMessage.error('发送失败，请检查配置')
  }
}

onMounted(() => {
  fetchSettings()
})
</script>

<style scoped>
.admin-settings-container {
  padding: var(--space-4);
  background: var(--el-bg-color-page);
}

/* 说明卡片 */
.info-card {
  margin-bottom: var(--space-4);
}

.info-content {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.info-icon {
  color: var(--role-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.info-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.info-desc {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

/* 布局 */
.settings-layout {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: var(--space-4);
  align-items: start;
}

/* 左侧菜单 */
.menu-card {
  position: sticky;
  top: var(--space-4);
  padding: 0;
  overflow: hidden;
}

.settings-menu {
  border-right: none;
  --el-menu-item-height: 48px;
  --el-menu-sub-menu-title-height: 48px;
}

.settings-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 右侧表单 */
.settings-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* 设置卡片 */
.settings-card {
  margin-bottom: 0;
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
}

/* 表单 */
.settings-form {
  max-width: 640px;
}

.settings-form .el-input,
.settings-form .el-select,
.settings-form .el-input-number {
  width: 280px;
}

.form-unit {
  margin-left: var(--space-2);
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

.form-hint {
  margin-left: var(--space-3);
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
}

/* 关于描述 */
.about-descriptions {
  max-width: 480px;
}

.about-link {
  color: var(--role-primary);
  text-decoration: none;
}

.about-link:hover {
  text-decoration: underline;
}

@media (max-width: 1023px) {
  .settings-layout {
    grid-template-columns: 1fr;
  }

  .menu-card {
    position: static;
  }
}
</style>