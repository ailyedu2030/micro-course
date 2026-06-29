import js from '@eslint/js'
import vue from 'eslint-plugin-vue'

const browserGlobals = {
  // Window & DOM
  window: 'readonly', document: 'readonly', navigator: 'readonly', location: 'readonly',
  history: 'readonly', localStorage: 'readonly', sessionStorage: 'readonly',
  fetch: 'readonly', Request: 'readonly', Response: 'readonly', Headers: 'readonly',
  FormData: 'readonly', Blob: 'readonly', File: 'readonly', FileReader: 'readonly',
  URL: 'readonly', URLSearchParams: 'readonly',
  HTMLElement: 'readonly', HTMLInputElement: 'readonly', HTMLVideoElement: 'readonly',
  HTMLCanvasElement: 'readonly', HTMLImageElement: 'readonly', HTMLAnchorElement: 'readonly',
  HTMLDivElement: 'readonly', HTMLFormElement: 'readonly', HTMLSelectElement: 'readonly',
  HTMLOptionElement: 'readonly', HTMLButtonElement: 'readonly', HTMLTextAreaElement: 'readonly',
  Element: 'readonly', Event: 'readonly', MouseEvent: 'readonly', KeyboardEvent: 'readonly',
  DragEvent: 'readonly', FocusEvent: 'readonly', CustomEvent: 'readonly',
  Node: 'readonly', DocumentFragment: 'readonly', DOMParser: 'readonly',
  Audio: 'readonly', AudioContext: 'readonly', MediaSource: 'readonly',
  ResizeObserver: 'readonly', MutationObserver: 'readonly', IntersectionObserver: 'readonly',
  XMLHttpRequest: 'readonly', WebSocket: 'readonly', Worker: 'readonly',
  setTimeout: 'readonly', clearTimeout: 'readonly', setInterval: 'readonly', clearInterval: 'readonly',
  requestAnimationFrame: 'readonly', cancelAnimationFrame: 'readonly',
  queueMicrotask: 'readonly', structuredClone: 'readonly', crypto: 'readonly',
  // Console
  console: 'readonly', alert: 'readonly', confirm: 'readonly', prompt: 'readonly',
  // Vue 3 macros
  defineProps: 'readonly', defineEmits: 'readonly', defineExpose: 'readonly',
  defineOptions: 'readonly', defineSlots: 'readonly', withDefaults: 'readonly',
  // 业务侧常用全局(Element Plus)
  ElMessage: 'readonly', ElMessageBox: 'readonly', ElNotification: 'readonly',
  ElLoading: 'readonly', getCurrentInstance: 'readonly',
  // Vite / Node
  process: 'readonly', import: 'readonly', global: 'readonly', Buffer: 'readonly',
  __dirname: 'readonly', __filename: 'readonly'
}

export default [
  js.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    languageOptions: {
      ecmaVersion: 2024,
      sourceType: 'module',
      globals: browserGlobals
    },
    rules: {
      'no-unused-vars': 'off',
      'no-empty': ['error', { allowEmptyCatch: true }],
      'no-useless-escape': 'off',
      'no-prototype-builtins': 'off',
      'no-async-promise-executor': 'off',
      'no-undef': 'off',

      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'off',
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/attributes-order': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/v-on-event-hyphenation': 'off',
      'vue/require-default-prop': 'off',
      'vue/no-mutating-props': 'warn',
      'vue/no-unused-vars': 'error',

      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'eqeqeq': ['error', 'always', { null: 'ignore' }],
      'prefer-const': 'warn',
      'no-var': 'error'
    }
  },
  {
    files: ['src/**/*.{js,vue}'],
    ignores: ['**/dist/**', '**/node_modules/**', 'e2e/**', 'coverage/**', '*.config.js']
  },
  {
    files: ['**/*.test.{js,vue}', '**/*.spec.{js,vue}', 'vite.config.js', 'e2e/**/*'],
    rules: {
      'no-console': 'off'
    }
  }
]
