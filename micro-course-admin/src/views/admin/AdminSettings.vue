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
    <div class="settings-layout" v-loading="loading" element-loading-text="加载配置中...">
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
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('system')" aria-label="保存">
<el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-form ref="systemFormRef" :model="systemForm" :rules="systemFormRules" label-width="140px" class="settings-form">
            <el-form-item label="平台名称" prop="platformName">
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
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('mail')" aria-label="保存">
<el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-form ref="mailFormRef" :model="mailForm" :rules="mailFormRules" label-width="140px" class="settings-form">
            <el-form-item label="SMTP 服务器" prop="smtpHost">
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
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('security')" aria-label="保存">
<el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-form ref="securityFormRef" :model="securityForm" :rules="securityFormRules" label-width="140px" class="settings-form">
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
              <el-button type="primary" size="small" :loading="saving" @click="handleSave('cas')" aria-label="编辑">
<el-icon><Check /></el-icon>保存修改
              </el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--space-4)">
            <template #title>
              CAS 配置用于对接学校统一身份认证系统。配置后将支持师生通过学校账号一键登录。
            </template>
          </el-alert>
          <el-form ref="casFormRef" :model="casForm" :rules="casFormRules" label-width="140px" class="settings-form">
            <el-form-item label="启用 CAS">
              <el-switch v-model="casForm.enabled" />
              <span class="form-hint">开启后将启用 CAS 单点登录</span>
            </el-form-item>
            <el-form-item label="CAS 服务器 URL" prop="serverUrl">
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
import { getSettings, updateSettings, getCasConfig, updateCasConfig } from '@/api/admin-settings'

// 加载状态
const loading = ref(false)
const saving = ref(false)

// 表单 refs
const systemFormRef = ref(null)
const mailFormRef = ref(null)
const securityFormRef = ref(null)
const casFormRef = ref(null)

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

// 表单验证规则
const systemFormRules = {
  platformName: [{ required: true, message: '请输入平台名称', trigger: ['blur', 'change'] }]
}

const mailFormRules = {
  smtpHost: [{ required: true, message: '请输入 SMTP 服务器地址', trigger: ['blur', 'change'] }]
}

const securityFormRules = {}

const casFormRules = {
  serverUrl: [{ required: true, message: '请输入 CAS 服务器 URL', trigger: ['blur', 'change'] }]
}

// 获取设置列表
async function fetchSettings() {
  // P1-2: 后端存 String，前端需要类型转换
  const BOOLEAN_KEYS = new Set([
    'allowRegistration', 'maintenanceMode',
    'useSsl', 'useTls',
    'requireNumber', 'requireSpecialChar', 'lockOnFailure', 'require2FA'
  ])
  const NUMBER_KEYS = new Set([
    'maxUploadSize', 'sessionTimeout',
    'smtpPort',
    'minPasswordLength', 'maxFailAttempts', 'lockDuration', 'tokenExpiry', 'refreshTokenExpiry'
  ])
  function castValue(key, raw) {
    if (BOOLEAN_KEYS.has(key)) return raw === 'true'
    if (NUMBER_KEYS.has(key)) { const n = Number(raw); return Number.isNaN(n) ? raw : n }
    return raw
  }

  loading.value = true
  try {
    const res = await getSettings()
    const items = res.data?.items || res.data || []
    // 填充到各表单（带类型转换）
    items.forEach(item => {
      const val = castValue(item.settingKey, item.settingValue)
      if (item.settingKey in systemForm) systemForm[item.settingKey] = val
      if (item.settingKey in mailForm) mailForm[item.settingKey] = val
      if (item.settingKey in securityForm) securityForm[item.settingKey] = val
    })
    // CAS 设置从后端 API 加载
    try {
      const casRes = await getCasConfig()
      const casData = casRes.data
      if (casData) {
        casForm.enabled = casData.enabled
        casForm.serverUrl = casData.serverUrl
        casForm.serviceUrl = casData.serviceUrl
        casForm.version = casData.version
        casForm.adminUsername = casData.adminUsername
        casForm.superAdmins = Array.isArray(casData.superAdmins) ? casData.superAdmins.join(', ') : (casData.superAdmins || '')
        casForm.validateSsl = casData.validateSsl
      }
    } catch {
      ElMessage.warning('CAS 配置加载失败，请稍后重试')
    }
  } catch (e) {
    console.warn('[AdminSettings] fetchSettings failed', e)
    ElMessage.error('系统配置加载失败，当前表单显示默认值，保存将覆盖现有配置')
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
  // P2: 防止重复提交
  if (saving.value) return

  // 根据菜单获取对应表单 ref 并校验
  const formRefMap = {
    system: systemFormRef,
    mail: mailFormRef,
    security: securityFormRef,
    cas: casFormRef
  }
  const currentFormRef = formRefMap[menu]
  if (currentFormRef?.value) {
    try {
      await currentFormRef.value.validate()
    } catch {
      return
    }
  }

  saving.value = true
  try {
    if (menu === 'cas') {
      const casPayload = {
        ...casForm,
        superAdmins: casForm.superAdmins ? casForm.superAdmins.split(',').map(s => s.trim()).filter(Boolean) : []
      }
      await updateCasConfig(casPayload)
      ElMessage.success('CAS 配置已保存')
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
      settingValue: String(formData[key])
    }))

    await updateSettings(updates)
    ElMessage.success('操作成功')
  } catch (e) {
    console.warn('[AdminSettings] save failed', e)
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
  // P2: 测试邮件应调用独立端点，不污染 DB（当前后端暂无测试邮件端点，使用演示模式）
  ElMessage.info('正在模拟发送测试邮件...')
  setTimeout(() => {
    ElMessage.success('模拟发送成功（实际需后端测试邮件端点支持）')
  }, 1500)
}

onMounted(() => {
  fetchSettings()
})
</script>

<style scoped>
.admin-settings-container {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.info-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
}

.settings-layout {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: var(--space-6);
  align-items: start;
}

.menu-card {
  position: sticky;
  top: var(--space-6);
  padding: 0;
  overflow: hidden;
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.settings-menu {
  border-right: none;
  --el-menu-item-height: 48px;
  --el-menu-sub-menu-title-height: 48px;
}

.settings-menu:not(.el-menu--collapse) {
  width: 100%;
}

/* 高亮项左边框 */
.settings-menu :deep(.el-menu-item.is-active) {
  background: var(--role-primary-light-9);
  border-left: 3px solid var(--role-primary);
  padding-left: 17px;
}

.settings-menu :deep(.el-menu-item) {
  border-left: 3px solid transparent;
  padding-left: 20px;
  transition: all var(--duration-base) var(--ease-out);
}

.settings-menu :deep(.el-menu-item:hover) {
  background: var(--role-primary-light-9);
}

.settings-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.settings-card {
  margin-bottom: 0;
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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

.settings-form {
  max-width: 640px;
  padding: var(--space-5) 0;
}

.settings-form :deep(.el-input),
.settings-form :deep(.el-select),
.settings-form :deep(.el-input-number) {
  width: 280px;
  border-radius: var(--radius-md);
}

.form-unit {
  display: inline-flex;
  align-items: center;
  margin-left: var(--space-2);
  color: var(--el-text-color-secondary);
  font-size: var(--text-base);
}

.form-hint {
  margin-left: var(--space-3);
  color: var(--el-text-color-secondary);
  font-size: var(--text-xs);
}

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

@media (max-width: 1023px) {
  .settings-layout {
    grid-template-columns: 1fr;
  }

  .menu-card {
    position: static;
  }
}
</style>