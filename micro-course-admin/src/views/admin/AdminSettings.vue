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
          <p class="info-title">{{ $t('adminSettings.title') }}</p>
          <p class="info-desc">{{ $t('adminSettings.desc') }}</p>
        </div>
      </div>
    </el-card>

    <el-alert
      v-if="!isAdmin"
      type="info"
      :closable="false"
      show-icon
      class="readonly-tip"
      :title="$t('adminSettings.readonlyTip')"
    />

    <!-- 主体：左侧菜单 + 右侧表单 -->
    <div class="settings-layout" v-loading="loading" :element-loading-text="$t('adminSettings.loading')">
      <!-- 左侧菜单 -->
      <el-card class="menu-card" shadow="never">
        <el-menu
          :default-active="activeMenu"
          class="settings-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="system">
            <el-icon><Setting /></el-icon>
            <template #title>{{ $t('adminSettings.menuSystem') }}</template>
          </el-menu-item>
          <el-menu-item index="mail">
            <el-icon><Message /></el-icon>
            <template #title>{{ $t('adminSettings.menuMail') }}</template>
          </el-menu-item>
          <el-menu-item index="security">
            <el-icon><Lock /></el-icon>
            <template #title>{{ $t('adminSettings.menuSecurity') }}</template>
          </el-menu-item>
          <el-menu-item v-if="isAdmin" index="cas">
            <el-icon><Key /></el-icon>
            <template #title>{{ $t('adminSettings.menuCas') }}</template>
          </el-menu-item>
          <el-menu-item index="about">
            <el-icon><InfoFilled /></el-icon>
            <template #title>{{ $t('adminSettings.menuAbout') }}</template>
          </el-menu-item>
        </el-menu>
      </el-card>

      <!-- 右侧表单 -->
      <div class="settings-content">
        <!-- 系统参数 -->
        <el-card v-show="activeMenu === 'system'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ $t('adminSettings.menuSystem') }}</span>
              <el-button v-if="isAdmin" type="primary" size="small" :loading="saving" @click="handleSave('system')" :aria-label="$t('adminSettings.saveAria')">
<el-icon><Check /></el-icon>{{ $t('adminSettings.save') }}
              </el-button>
            </div>
          </template>
          <el-form ref="systemFormRef" :model="systemForm" :rules="systemFormRules" label-width="140px" class="settings-form">
            <el-form-item :label="$t('adminSettings.platformName')" prop="platformName">
              <el-input v-model="systemForm.platformName" :placeholder="$t('adminSettings.platformNamePlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.logoUrl')">
              <el-input v-model="systemForm.logoUrl" :placeholder="$t('adminSettings.logoUrlPlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.systemVersion')">
              <el-input :model-value="APP_VERSION" disabled />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.maxUploadSize')">
              <el-input-number
                v-model="systemForm.max_video_size_mb"
                :min="1"
                :max="500"
                controls-position="right"
              />
              <span class="form-unit">{{ $t('adminSettings.unitMb') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.sessionTimeout')">
              <el-input-number
                v-model="systemForm.sessionTimeout"
                :min="5"
                :max="1440"
                controls-position="right"
              />
              <span class="form-unit">{{ $t('adminSettings.unitMinutes') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.allowRegistration')">
              <el-switch v-model="systemForm.allowRegistration" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.maintenanceMode')">
              <el-switch v-model="systemForm.maintenanceMode" />
              <span class="form-hint">{{ $t('adminSettings.maintenanceHint') }}</span>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 邮件配置 -->
        <el-card v-show="activeMenu === 'mail'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ $t('adminSettings.menuMail') }}</span>
              <el-button v-if="isAdmin" type="primary" size="small" :loading="saving" @click="handleSave('mail')" :aria-label="$t('adminSettings.saveAria')">
<el-icon><Check /></el-icon>{{ $t('adminSettings.save') }}
              </el-button>
            </div>
          </template>
          <el-form ref="mailFormRef" :model="mailForm" :rules="mailFormRules" label-width="140px" class="settings-form">
            <el-form-item :label="$t('adminSettings.smtpHost')" prop="smtpHost">
              <el-input v-model="mailForm.smtpHost" placeholder="smtp.example.com" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.smtpPort')">
              <el-input-number
                v-model="mailForm.smtpPort"
                :min="1"
                :max="65535"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.username')">
              <el-input v-model="mailForm.smtpUsername" placeholder="your@email.com" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.password')">
              <el-input
                v-model="mailForm.smtpPassword"
                type="password"
                show-password
                :placeholder="$t('adminSettings.passwordPlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.senderName')">
              <el-input v-model="mailForm.fromName" :placeholder="$t('adminSettings.senderNamePlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.enableSsl')">
              <el-switch v-model="mailForm.useSsl" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.enableTls')">
              <el-switch v-model="mailForm.useTls" />
            </el-form-item>
            <el-form-item>
              <el-button @click="handleTestMail">{{ $t('adminSettings.sendTestMail') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 安全设置 -->
        <el-card v-show="activeMenu === 'security'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ $t('adminSettings.menuSecurity') }}</span>
              <el-button v-if="isAdmin" type="primary" size="small" :loading="saving" @click="handleSave('security')" :aria-label="$t('adminSettings.saveAria')">
<el-icon><Check /></el-icon>{{ $t('adminSettings.save') }}
              </el-button>
            </div>
          </template>
          <el-form ref="securityFormRef" :model="securityForm" :rules="securityFormRules" label-width="140px" class="settings-form">
            <el-form-item :label="$t('adminSettings.minPasswordLength')">
              <el-input-number
                v-model="securityForm.minPasswordLength"
                :min="6"
                :max="32"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.requireNumber')">
              <el-switch v-model="securityForm.requireNumber" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.requireSpecialChar')">
              <el-switch v-model="securityForm.requireSpecialChar" />
            </el-form-item>
            <el-form-item :label="$t('adminSettings.lockOnFailure')">
              <el-switch v-model="securityForm.lockOnFailure" />
            </el-form-item>
            <el-form-item v-if="securityForm.lockOnFailure" :label="$t('adminSettings.maxFailAttempts')">
              <el-input-number
                v-model="securityForm.maxFailAttempts"
                :min="3"
                :max="10"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item v-if="securityForm.lockOnFailure" :label="$t('adminSettings.lockDuration')">
              <el-input-number
                v-model="securityForm.lockDuration"
                :min="5"
                :max="1440"
                controls-position="right"
              />
              <span class="form-unit">{{ $t('adminSettings.unitMinutes') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.require2FA')">
              <el-switch v-model="securityForm.require2FA" />
              <span class="form-hint">{{ $t('adminSettings.require2FAHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.tokenExpiry')">
              <el-input-number
                v-model="securityForm.tokenExpiry"
                :min="30"
                :max="86400"
                controls-position="right"
              />
              <span class="form-unit">{{ $t('adminSettings.unitMinutes') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.refreshTokenExpiry')">
              <el-input-number
                v-model="securityForm.refreshTokenExpiry"
                :min="1"
                :max="30"
                controls-position="right"
              />
              <span class="form-unit">{{ $t('adminSettings.unitDays') }}</span>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- CAS 配置 -->
        <el-card v-show="activeMenu === 'cas'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ $t('adminSettings.casTitle') }}</span>
              <el-button v-if="isAdmin" type="primary" size="small" :loading="saving" @click="handleSave('cas')" :aria-label="$t('adminSettings.save')">
<el-icon><Check /></el-icon>{{ $t('adminSettings.save') }}
              </el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--space-4)">
            <template #title>
              {{ $t('adminSettings.casDesc') }}
            </template>
          </el-alert>
          <el-alert
            v-if="casLoadFailed"
            type="warning"
            :title="$t('adminSettings.casLoadFailed')"
            :closable="false"
            show-icon
            style="margin-bottom: var(--space-4)"
          />
          <el-form ref="casFormRef" :model="casForm" :rules="casFormRules" label-width="140px" class="settings-form">
            <el-form-item :label="$t('adminSettings.enableCas')">
              <el-switch v-model="casForm.enabled" />
              <span class="form-hint">{{ $t('adminSettings.enableCasHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.casServerUrl')" prop="serverUrl">
              <el-input v-model="casForm.serverUrl" placeholder="https://cas.example.edu.cn" />
              <span class="form-hint">{{ $t('adminSettings.casServerUrlHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.casServiceUrl')">
              <el-input v-model="casForm.serviceUrl" placeholder="https://micro-course.example.edu.cn/cas/validate" />
              <span class="form-hint">{{ $t('adminSettings.casServiceUrlHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.casVersion')">
              <el-select v-model="casForm.version" :placeholder="$t('adminSettings.pleaseSelect')" class="full-width">
                <el-option label="CAS 2.0" value="2.0" />
                <el-option label="CAS 3.0" value="3.0" />
                <el-option label="SAML 2.0" value="saml2" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.adminAccount')">
              <el-input v-model="casForm.adminUsername" :placeholder="$t('adminSettings.adminAccountPlaceholder')" />
              <span class="form-hint">{{ $t('adminSettings.adminAccountHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.superAdmins')">
              <el-input
                v-model="casForm.superAdmins"
                type="textarea"
                :rows="2"
                :placeholder="$t('adminSettings.superAdminsPlaceholder')"
              />
              <span class="form-hint">{{ $t('adminSettings.superAdminsHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.validateSsl')">
              <el-switch v-model="casForm.validateSsl" />
              <span class="form-hint">{{ $t('adminSettings.validateSslHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('adminSettings.testConnection')">
              <el-button @click="handleTestCas">{{ $t('adminSettings.testCasBtn') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 关于系统 -->
        <el-card v-show="activeMenu === 'about'" class="settings-card shadow-hover" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">{{ $t('adminSettings.menuAbout') }}</span>
            </div>
          </template>
          <el-descriptions :column="1" border class="about-descriptions">
            <el-descriptions-item :label="$t('adminSettings.aboutSystemName')">{{ $t('adminSettings.aboutPlatformName') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutVersion')">{{ APP_VERSION }}</el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutTechStack')">
              Spring Boot 3.2 + Vue 3.4 + Element Plus 2.5
            </el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutDatabase')">PostgreSQL 17.5</el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutCache')">Redis 7</el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutTeam')">{{ $t('adminSettings.aboutTeamName') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutLicense')">MIT License</el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutDocs')">
              <a href="#" class="about-link">https://docs.example.com</a>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('adminSettings.aboutFeedback')">
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
// 版本号从环境变量动态读取
const APP_VERSION = import.meta.env.VITE_APP_VERSION || '1.0.0'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  InfoFilled, Setting, Message, Lock, Check, Key
} from '@element-plus/icons-vue'
import { getSettings, updateSettings, getCasConfig, updateCasConfig, sendTestEmail } from '@/api/admin-settings'

const { t } = useI18n()
const userStore = useUserStore()
// CAS 配置含解密后的管理员账号等敏感字段，后端仅 ADMIN 可读；
// 非管理员只读视图不加载、不展示 CAS 菜单，避免无权限 403 与敏感信息暴露
const isAdmin = computed(() => userStore.role === 'ADMIN')

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
  max_video_size_mb: 100,
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
const casLoadFailed = ref(false)
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
  platformName: [{ required: true, message: t('adminSettings.platformNameRequired'), trigger: ['blur', 'change'] }]
}

const mailFormRules = {
  smtpHost: [{ required: true, message: t('adminSettings.smtpHostRequired'), trigger: ['blur', 'change'] }]
}

const securityFormRules = {
  minPasswordLength: [
    { required: true, message: t('adminSettings.minPasswordLengthRequired'), trigger: 'blur' },
    { type: 'number', min: 6, max: 32, message: t('adminSettings.minPasswordLengthRange'), trigger: 'blur' }
  ],
  tokenExpiry: [
    { type: 'number', min: 30, max: 86400, message: t('adminSettings.tokenExpiryRange'), trigger: 'blur' }
  ],
  refreshTokenExpiry: [
    { type: 'number', min: 1, max: 30, message: t('adminSettings.refreshTokenExpiryRange'), trigger: 'blur' }
  ],
  maxFailAttempts: [
    { type: 'number', min: 3, max: 10, message: t('adminSettings.maxFailAttemptsRange'), trigger: 'blur' }
  ],
  lockDuration: [
    { type: 'number', min: 5, max: 1440, message: t('adminSettings.lockDurationRange'), trigger: 'blur' }
  ]
}

const casFormRules = {
  serverUrl: [{ required: true, message: t('adminSettings.casServerUrlRequired'), trigger: ['blur', 'change'] }]
}

// 获取设置列表
async function fetchSettings() {
  function castValue(setting) {
    const raw = setting.settingValue
    const vt = setting.valueType
    if (vt === 'BOOLEAN') return raw === 'true'
    if (vt === 'NUMBER') { const n = Number(raw); return Number.isNaN(n) ? raw : n }
    return raw
  }

  loading.value = true
  try {
    const res = await getSettings()
    const items = res.data?.items || res.data || []
    // 填充到各表单（根据后端 valueType 做类型转换）
    items.forEach(item => {
      const val = castValue(item)
      // P1C: 兼容旧 key maxUploadSize → 映射到 max_video_size_mb
      if (item.settingKey === 'maxUploadSize') {
        systemForm.max_video_size_mb = val
      } else if (item.settingKey in systemForm) {
        systemForm[item.settingKey] = val
      }
      if (item.settingKey in mailForm) {
        // P2-024: 敏感字段如 smtpPassword 不回显实际值，只显示占位符
        if (item.settingKey === 'smtpPassword') {
          mailForm[item.settingKey] = val ? '******' : ''
        } else {
          mailForm[item.settingKey] = val
        }
      }
      if (item.settingKey in securityForm) securityForm[item.settingKey] = val
    })
    // CAS 设置从后端 API 加载（仅管理员）
    if (isAdmin.value) {
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
        casLoadFailed.value = true
        ElMessage.warning(t('adminSettings.casLoadFailedMsg'))
      }
    }
  } catch (e) {
    console.warn('[AdminSettings] fetchSettings failed', e)
    ElMessage.error(t('adminSettings.settingsLoadFailed'))
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
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
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
  saving.value = true
  if (currentFormRef?.value) {
    try {
      const valid = await currentFormRef.value.validate()
      if (!valid) {
        saving.value = false
        return
      }
    } catch {
      saving.value = false
      return
    }
  }

  try {
    if (menu === 'cas') {
      const casPayload = {
        ...casForm,
        superAdmins: casForm.superAdmins ? casForm.superAdmins.split(',').map(s => s.trim()).filter(Boolean) : []
      }
      await updateCasConfig(casPayload)
      ElMessage.success(t('adminSettings.casSaved'))
      saving.value = false
      return
    }

    let formData
    let keys
    if (menu === 'system') {
      formData = systemForm
      // P2-023: 排除 version 字段，防止系统版本作为可写配置项被更新覆盖
      keys = Object.keys(systemForm).filter(k => k !== 'version')
    } else if (menu === 'mail') {
      formData = mailForm
      // P2-024: 密码未修改时排除 smtpPassword，避免覆盖服务端实际值
      keys = Object.keys(mailForm).filter(k => !(k === 'smtpPassword' && mailForm.smtpPassword === '******'))
    } else if (menu === 'security') {
      formData = securityForm
      keys = Object.keys(securityForm)
    }

    const updates = keys.map(key => ({
      settingKey: key,
      settingValue: String(formData[key])
    }))

    await updateSettings(updates)
    ElMessage.success(t('common.success'))
  } catch (e) {
    console.warn('[AdminSettings] save failed', e)
    ElMessage.error(t('adminSettings.saveFailed'))
  } finally {
    saving.value = false
  }
}

// 测试 CAS 连接
async function handleTestCas() {
  if (!casForm.serverUrl) {
    ElMessage.warning(t('adminSettings.casUrlRequired'))
    return
  }
  // ⚠️ P1C-055: 当前为模拟测试，后端暂无实际测试端点，请手动验证
  ElMessage.warning({
    message: t('adminSettings.casMockTest'),
    duration: 5000
  })
  setTimeout(() => {
    ElMessage({
      type: 'warning',
      message: t('adminSettings.casMockEnd'),
      duration: 6000
    })
  }, 1000)
}

// 测试邮件
async function handleTestMail() {
  if (!mailForm.smtpHost || !mailForm.smtpUsername) {
    ElMessage.warning(t('adminSettings.mailConfigRequired'))
    return
  }
  // B10.5 修复：真实 SMTP 发送（后端按已保存配置自测）
  try {
    await sendTestEmail()
    ElMessage.success(t('adminSettings.mailTestSent'))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('adminSettings.mailSendFailed'))
  }
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
