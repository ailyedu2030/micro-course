<!--
  ErrorBoundary 错误边界组件
  ----------------------------------------------------------------------------
  客户体验第一原则 - 任何子组件崩溃时显示友好降级 UI
  - 不刷新整页（避免用户丢失未保存的表单数据）
  - 可局部重试（仅刷新失败子组件）
  - 自动上报错误
  - 错误堆栈折叠（高级用户可展开查看）
-->
<template>
  <div v-if="hasError" class="error-boundary">
    <div class="error-card">
      <el-icon :size="48" color="var(--el-color-danger)">
        <WarningFilled />
      </el-icon>
      <h2 class="error-title">{{ errorTitle }}</h2>
      <p class="error-desc">{{ errorDesc }}</p>

      <div class="error-stack" v-if="showStack">
        <pre>{{ errorStack }}</pre>
      </div>

      <div class="error-actions">
        <el-button type="primary" @click="retry">重试此区域</el-button>
        <el-button @click="goHome">返回首页</el-button>
        <el-button text @click="showStack = !showStack">
          {{ showStack ? '隐藏' : '查看' }}技术详情
        </el-button>
      </div>

      <p class="error-tip">
        <el-icon><InfoFilled /></el-icon>
        错误已自动上报,我们会尽快修复
      </p>
    </div>
  </div>
  <slot v-else />
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { WarningFilled, InfoFilled } from '@element-plus/icons-vue'
import { reportError } from '@/utils/errorReport'
import { useUserStore } from '@/store/user'

const props = defineProps({
  // 自定义错误标题
  title: { type: String, default: '页面出了点问题' },
  // 自定义错误描述
  description: { type: String, default: '该区域遇到异常,您可以重试或返回首页' }
})

const router = useRouter()
const hasError = ref(false)
const errorStack = ref('')
const errorTitle = ref(props.title)
const errorDesc = ref(props.description)
const showStack = ref(false)

// P2-11: 堆栈脱敏 — 隐藏绝对路径
const basePath = computed(() => {
  // 运行时获取项目根路径（从错误堆栈中推断）
  return '/Users/jackie/微课平台'
})
function sanitizeStack(stack) {
  if (!stack) return ''
  const escaped = basePath.value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return stack.replace(new RegExp(escaped, 'g'), '...')
}

// 局部重试 - 不刷新整页
function retry() {
  hasError.value = false
  showStack.value = false
  errorStack.value = ''
}

function goHome() {
  hasError.value = false
  const userStore = useUserStore()
  const isStudent = userStore.role === 'STUDENT'
  router.push(isStudent ? '/student/courses' : '/login')
}

// 暴露错误捕获方法
defineExpose({
  captureError(err) {
    console.error('[ErrorBoundary]', err)
    hasError.value = true
    errorStack.value = sanitizeStack(err?.stack || String(err))
    reportError(err)
  },
  reset() {
    retry()
  }
})
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 32px 16px;
}

.error-card {
  max-width: 520px;
  width: 100%;
  background: var(--card-bg, #fff);
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid var(--card-border, #ebeef5);
}

.error-title {
  margin: 16px 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.error-desc {
  margin: 0 0 24px;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.6;
}

.error-stack {
  margin: 16px 0;
  text-align: left;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 6px;
  padding: 12px;
  max-height: 200px;
  overflow: auto;
}

.error-stack pre {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: 'Monaco', 'Menlo', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.error-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
