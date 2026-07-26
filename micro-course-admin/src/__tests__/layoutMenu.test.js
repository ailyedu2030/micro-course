/**
 * Layout.vue menu regression tests for FIX-BUG-003:
 * - Role not initialized → empty menu (no ADMIN flash)
 * - TEACHER role → TEACHER menu shown
 * - ADMIN role → ADMIN menu shown
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { nextTick } from 'vue'

/* ========== Module-level mocks ========== */

vi.mock('@/utils/auth', () => ({
  isAuthenticated: vi.fn(() => true),
  removeToken: vi.fn(),
  getToken: vi.fn(() => 'mock-token'),
}))

const mockUserStore = vi.hoisted(() => ({
  role: '',
  token: 'mock-token',
  userInfo: null,
  refreshToken: 'mock-refresh',
  realName: '',
  username: 'test',
  getInfo: vi.fn(),
  refreshAccessToken: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => mockUserStore,
}))

vi.mock('@/store/notification', () => ({
  useNotificationStore: () => ({
    unreadCount: 0,
    startPolling: vi.fn(),
    stopPolling: vi.fn(),
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve(true)) },
    ElInput: { template: '<input />' },
  }
})

vi.mock('nprogress', () => ({
  default: { start: vi.fn(), done: vi.fn(), configure: vi.fn() },
  start: vi.fn(),
  done: vi.fn(),
  configure: vi.fn(),
}))

// NOTE: @element-plus/icons-vue is NOT mocked here because element-plus
// internal code imports many icons. We use importOriginal for element-plus
// which resolves all dependencies naturally.

// Provide localStorage mock for Layout.vue's initTheme()
const store = {}
vi.stubGlobal('localStorage', {
  getItem: (key) => store[key] ?? null,
  setItem: (key, val) => { store[key] = String(val) },
  removeItem: (key) => { delete store[key] },
  clear: () => { Object.keys(store).forEach(k => delete store[k]) },
})

/* ========== Imports ========== */

import Layout from '@/components/Layout.vue'
import { menuConfig } from '@/config/menuConfig'

/* ========== Test helpers ========== */

// Replicates the fixed computed property logic from Layout.vue
function getCurrentMenu(role) {
  if (!role) return []
  return menuConfig[role] || []
}

const testRouter = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div>home</div>' } },
    { path: '/teacher/dashboard', component: { template: '<div>dashboard</div>' } },
    { path: '/admin/dashboard', component: { template: '<div>admin</div>' } },
  ],
})

const globalPlugins = { plugins: [testRouter] }

const stubs = {
  'el-container':    { template: '<div class="el-container"><slot /></div>' },
  'el-aside':        { template: '<div class="el-aside"><slot /></div>' },
  'el-menu':         { template: '<div class="el-menu"><slot /></div>' },
  'el-sub-menu':     { template: '<div class="el-sub-menu"><slot name="title" /><slot /></div>' },
  'el-menu-item':    { template: '<div class="el-menu-item"><slot /><slot name="title" /></div>' },
  'el-header':       { template: '<div class="el-header"><slot /></div>' },
  'el-main':         { template: '<div class="el-main"><slot /></div>' },
  'el-icon':         { template: '<i class="el-icon"><slot /></i>' },
  'el-breadcrumb':   { template: '<div class="el-breadcrumb"><slot /></div>' },
  'el-breadcrumb-item': { template: '<span class="el-breadcrumb-item"><slot /></span>' },
  'el-dropdown':     { template: '<div class="el-dropdown"><slot /></div>' },
  'el-dropdown-menu':{ template: '<div class="el-dropdown-menu"><slot /></div>' },
  'el-dropdown-item':{ template: '<div class="el-dropdown-item"><slot /></div>' },
  'el-avatar':       { template: '<div class="el-avatar"><slot /></div>' },
  'el-tag':          { template: '<span class="el-tag"><slot /></span>' },
  'el-tooltip':      { template: '<div class="el-tooltip"><slot /></div>' },
  'el-badge':        { template: '<div class="el-badge"><slot /></div>' },
  'router-link':     { template: '<a class="router-link"><slot /></a>' },
  'router-view':     { template: '<div class="router-view" />' },
}

/* ================================================================
 * Part 1: Menu config data integrity
 * ================================================================ */

describe('menuConfig structure', () => {
  it('has ADMIN, ACADEMIC, and TEACHER configs', () => {
    expect(menuConfig.ADMIN).toBeInstanceOf(Array)
    expect(menuConfig.ACADEMIC).toBeInstanceOf(Array)
    expect(menuConfig.TEACHER).toBeInstanceOf(Array)
  })

  it('does NOT have a config for empty string', () => {
    expect(menuConfig['']).toBeUndefined()
  })

  it('ADMIN has system management group', () => {
    const groups = menuConfig.ADMIN.map(g => g.group)
    expect(groups).toContain('系统管理')
  })
})

/* ================================================================
 * Part 2: currentMenu logic (mirrors Layout.vue computed)
 * ================================================================ */

describe('Layout.vue currentMenu logic', () => {
  it('returns empty array when role is empty (no ADMIN flash)', () => {
    expect(getCurrentMenu('')).toEqual([])
  })

  it('returns empty array when role is nullish', () => {
    expect(getCurrentMenu(null)).toEqual([])
    expect(getCurrentMenu(undefined)).toEqual([])
  })

  it('returns TEACHER menu for TEACHER role', () => {
    const menu = getCurrentMenu('TEACHER')
    expect(menu).toBe(menuConfig.TEACHER)
    expect(menu.length).toBeGreaterThan(0)
    const groups = menu.map(g => g.group)
    expect(groups).toContain('教学看板')
    expect(groups).toContain('微专业管理')
  })

  it('returns ADMIN menu for ADMIN role', () => {
    const menu = getCurrentMenu('ADMIN')
    expect(menu).toBe(menuConfig.ADMIN)
    expect(menu.length).toBeGreaterThan(0)
  })

  it('returns empty array for unknown role instead of ADMIN fallback', () => {
    // This was the old bug: menuConfig['UNKNOWN'] || menuConfig.ADMIN
    const menu = getCurrentMenu('UNKNOWN')
    expect(menu).toEqual([])
  })

  it('returns empty array for STUDENT (student uses different layout)', () => {
    const menu = getCurrentMenu('STUDENT')
    expect(menu).toEqual([])
  })
})

/* ================================================================
 * Part 3: Layout.vue integration (mount with mocked store)
 * ================================================================ */

describe('Layout.vue rendered menu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mountLayout(role) {
    mockUserStore.role = role
    mockUserStore.userInfo = role ? { id: 1, role, realName: 'Test' } : null
    // Ensure large viewport so Layout doesn't collapse sidebar in tests
    window.innerWidth = 1440
    window.innerHeight = 900
    return mount(Layout, { global: { ...globalPlugins, stubs } })
  }

  // ── Integration test 1: empty role ──
  it('renders zero sub-menu groups when role is empty (no ADMIN flash)', async () => {
    const wrapper = mountLayout('')
    await nextTick()

    // With the fix, currentMenu returns [] → no sub-menu stubs rendered
    const subMenus = wrapper.findAll('.el-sub-menu')
    expect(subMenus.length).toBe(0)

    // ADMIN-specific text must not appear
    expect(wrapper.text()).not.toContain('数据看板')
    expect(wrapper.text()).not.toContain('基础数据')
    expect(wrapper.text()).not.toContain('系统管理')
  })

  // ── Integration test 2: TEACHER role ──
  it('renders TEACHER-specific menu groups when role is TEACHER', async () => {
    const wrapper = mountLayout('TEACHER')
    await nextTick()

    const subMenus = wrapper.findAll('.el-sub-menu')
    expect(subMenus.length).toBeGreaterThan(0)

    // TEACHER groups: 教学看板, 课程管理, 题库管理, 学员管理, 微专业管理, 个人设置
    expect(wrapper.text()).toContain('教学看板')
    expect(wrapper.text()).toContain('课程管理')
    expect(wrapper.text()).toContain('微专业管理')
  })

  // ── Integration test 3: ADMIN role ──
  it('renders ADMIN-specific menu groups when role is ADMIN', async () => {
    const wrapper = mountLayout('ADMIN')
    await nextTick()

    const subMenus = wrapper.findAll('.el-sub-menu')
    expect(subMenus.length).toBeGreaterThan(0)

    expect(wrapper.text()).toContain('数据看板')
    expect(wrapper.text()).toContain('系统管理')
  })

  // ── Integration test 4: TEACHER does NOT see ADMIN-only items ──
  it('does NOT show ADMIN-only items for TEACHER role', async () => {
    const wrapper = mountLayout('TEACHER')
    await nextTick()

    // TEACHER should NOT see "系统设置" (ADMIN/ACADEMIC only)
    expect(wrapper.text()).not.toContain('系统设置')
    // TEACHER should not see admin platform-share-config or operation logs
    expect(wrapper.text()).not.toContain('平台分账')
    expect(wrapper.text()).not.toContain('操作日志')
  })
})
