/**
 * BundleList.vue 权限按钮矩阵测试
 * ================================
 *
 * 验证 row-level 权限（canManage / canDelete / userRole）：
 *   TEACHER 自有: 新增套件 ✓ 编辑/上下架 ✓  删除 ✗
 *   TEACHER 他人: 新增套件 ✓ 编辑/上下架 ✗  删除 ✗
 *   ADMIN:        新增套件 ✓ 编辑/上下架 ✓  删除 ✓
 *   ACADEMIC:     新增套件 ✗ 编辑/上下架 ✗  删除 ✗
 *
 * 运行:
 *   npx vitest run src/__tests__/BundleList.permissions.test.js
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'

// ============================================================
// —— 动态 store mock（vi.hoisted 确保在 vi.mock 前执行） ——
// ============================================================
const storeMock = vi.hoisted(() => {
  let role = 'TEACHER'
  let userId = 1
  return {
    useUserStore: () => ({
      userInfo: { id: userId, username: 'test', role },
      role,
      userId,
      getInfo: vi.fn(),
    }),
    setRole(r, uid) { role = r; userId = uid ?? 1 },
  }
})

vi.mock('@/store/user', () => ({
  useUserStore: storeMock.useUserStore,
}))

// ============================================================
// —— API mocks ——
// ============================================================
vi.mock('@/api/bundle', () => ({
  getBundles: vi.fn(() => Promise.resolve({ data: { items: [], totalElements: 0 } })),
  getBundleById: vi.fn(() => Promise.resolve({ data: { id: 1, title: '测试套件', items: [] } })),
  createBundle: vi.fn(() => Promise.resolve({ data: {} })),
  updateBundle: vi.fn(() => Promise.resolve({ data: {} })),
  publishBundle: vi.fn(() => Promise.resolve({ data: {} })),
  unpublishBundle: vi.fn(() => Promise.resolve({ data: {} })),
  addBundleCourse: vi.fn(() => Promise.resolve({ data: {} })),
  removeBundleCourse: vi.fn(() => Promise.resolve({ data: {} })),
  deleteBundle: vi.fn(() => Promise.resolve({ data: {} })),
}))

vi.mock('@/api/course', () => ({
  getCourses: vi.fn(() => Promise.resolve({ data: { items: [] } })),
}))

// ============================================================
// —— Element Plus 组件保留，工具函数 mock ——
// ============================================================
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn() },
  }
})

// ============================================================
// —— 路由 mock ——
// ============================================================
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
    useRoute: () => ({ params: {}, query: {} }),
  }
})

// ============================================================
// —— 工具 mock ——
// ============================================================
vi.mock('@/utils/auth', () => ({
  removeToken: vi.fn(),
  removeRefreshToken: vi.fn(),
}))

vi.mock('@/utils/format', () => ({
  formatDateTime: (d) => d || '',
}))

// ============================================================
// —— Shared stubs for shallowMount ——
// ============================================================
const baseStubs = {
  'el-page-header': { template: '<div><slot /></div>' },
  'el-card': { template: '<div><slot /><slot name="header" /></div>' },
  'el-button': { template: '<button :aria-label="$attrs[\'aria-label\']"><slot /></button>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-input': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
  'el-input-number': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
  'el-checkbox': { template: '<label><slot /></label>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-dialog': { template: '<div v-if="modelValue !== false"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
  'el-select': { template: '<select :aria-label="$attrs[\'aria-label\']"><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
}

// ============================================================
// —— Helpers ——
// ============================================================
function createWrapper(role, userId = 1) {
  storeMock.setRole(role, userId)
  return shallowMount(BundleList, {
    global: { stubs: baseStubs },
  })
}

let BundleList

// ============================================================
// —— Tests ——
// ============================================================
describe('BundleList.vue 权限按钮矩阵', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    BundleList = (await import('@/views/courses/BundleList.vue')).default
  })

  // ── 逻辑层测试（不依赖 table slot 渲染） ──

  describe('canManage 行级权限函数', () => {
    it('TEACHER 可管理自有套件', () => {
      const wrapper = createWrapper('TEACHER', 1)
      expect(wrapper.vm.canManage({ creatorId: 1 })).toBe(true)
    })

    it('TEACHER 不可管理他人套件', () => {
      const wrapper = createWrapper('TEACHER', 1)
      expect(wrapper.vm.canManage({ creatorId: 2 })).toBe(false)
    })

    it('ADMIN 可管理所有套件', () => {
      const wrapper = createWrapper('ADMIN')
      expect(wrapper.vm.canManage({ creatorId: 1 })).toBe(true)
      expect(wrapper.vm.canManage({ creatorId: 999 })).toBe(true)
    })

    it('ACADEMIC 不可管理任何套件', () => {
      const wrapper = createWrapper('ACADEMIC')
      expect(wrapper.vm.canManage({ creatorId: 1 })).toBe(false)
      expect(wrapper.vm.canManage({ creatorId: 999 })).toBe(false)
    })
  })

  describe('canDelete 删除权限', () => {
    it('TEACHER 不可删除', () => {
      const wrapper = createWrapper('TEACHER')
      expect(wrapper.vm.canDelete).toBe(false)
    })

    it('ADMIN 可删除', () => {
      const wrapper = createWrapper('ADMIN')
      expect(wrapper.vm.canDelete).toBe(true)
    })

    it('ACADEMIC 不可删除', () => {
      const wrapper = createWrapper('ACADEMIC')
      expect(wrapper.vm.canDelete).toBe(false)
    })
  })

  describe('userRole computed', () => {
    it('返回正确的 userStore.role', () => {
      const wrapper = createWrapper('TEACHER')
      expect(wrapper.vm.userRole).toBe('TEACHER')
    })

    it('ADMIN', () => {
      const wrapper = createWrapper('ADMIN')
      expect(wrapper.vm.userRole).toBe('ADMIN')
    })

    it('ACADEMIC', () => {
      const wrapper = createWrapper('ACADEMIC')
      expect(wrapper.vm.userRole).toBe('ACADEMIC')
    })
  })

  // ── 模板渲染测试（header 按钮在 table 外，shallowMount 可捕捉） ──

  describe('「新增套件」header 按钮可见性', () => {
    it('TEACHER 可见', async () => {
      const wrapper = createWrapper('TEACHER')
      await flushPromises()
      expect(wrapper.find('[aria-label="新增套件"]').exists()).toBe(true)
    })

    it('ADMIN 可见', async () => {
      const wrapper = createWrapper('ADMIN')
      await flushPromises()
      expect(wrapper.find('[aria-label="新增套件"]').exists()).toBe(true)
    })

    it('ACADEMIC 不可见', async () => {
      const wrapper = createWrapper('ACADEMIC')
      await flushPromises()
      expect(wrapper.find('[aria-label="新增套件"]').exists()).toBe(false)
    })
  })

  // ── 确保无 Vue 模板编译 warning（userRole 未定义导致） ──
  describe('Vue warning 检查', () => {
    it('所有角色下均无 userRole 未定义 warning', async () => {
      // 捕获 console.warn
      const warns = []
      const origWarn = console.warn
      console.warn = (...args) => {
        if (typeof args[0] === 'string' && args[0].includes('userRole')) {
          warns.push(args.join(' '))
        }
        origWarn.call(console, ...args)
      }

      try {
        for (const role of ['TEACHER', 'ADMIN', 'ACADEMIC']) {
          const wrapper = createWrapper(role)
          await flushPromises()
          wrapper.unmount()
        }
        expect(warns).toHaveLength(0)
      } finally {
        console.warn = origWarn
      }
    })
  })
})
