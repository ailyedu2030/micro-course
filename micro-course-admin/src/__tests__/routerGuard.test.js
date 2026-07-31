/**
 * Router guard regression tests for FIX-BUG-003 + P1-C 权限/UX 修复:
 * 1. requiresLead `_readonly` infinite loop prevention
 * 2. Layout role flash prevention
 * 3. Token refresh 不跳过角色/权限校验
 * 4. 404 toast 同步显示 + 正确重定向
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

/* ========== Module-level mocks (hoisted before all imports) ========== */

const authState = vi.hoisted(() => ({ authenticated: true }))

vi.mock('@/utils/auth', () => ({
  isAuthenticated: vi.fn(() => authState.authenticated),
  removeToken: vi.fn(() => { authState.authenticated = false }),
  getToken: vi.fn(() => authState.authenticated ? 'mock-token' : null),
}))

const mockUserStore = vi.hoisted(() => ({
  role: '',
  token: 'mock-token',
  userInfo: null,
  refreshToken: 'mock-refresh',
  getInfo: vi.fn(),
  refreshAccessToken: vi.fn(),
  logout: vi.fn().mockImplementation(function() {
    this.token = ''
    this.refreshToken = ''
    this.role = ''
    this.userInfo = null
  }),
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => mockUserStore,
}))

const mockGetMyRole = vi.hoisted(() => vi.fn())

vi.mock('@/api/microSpecialty', () => ({
  getMyRole: mockGetMyRole,
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() },
  ElMessageBox: { confirm: vi.fn(() => Promise.resolve(true)) },
}))

vi.mock('nprogress', () => ({
  default: { start: vi.fn(), done: vi.fn(), configure: vi.fn() },
  start: vi.fn(),
  done: vi.fn(),
  configure: vi.fn(),
}))

/* ========== Imports (mocks are set up, so these use mocked deps) ========== */

import router, { getRoleHomePage } from '@/router/index'
import { isAuthenticated } from '@/utils/auth'

/* ================================================================
 * Part 1: getRoleHomePage — pure function
 * ================================================================ */

describe('getRoleHomePage', () => {
  it.each([
    ['STUDENT', '/student/courses'],
    ['TEACHER', '/teacher/dashboard'],
    ['ACADEMIC', '/academic/dashboard'],
    ['ADMIN', '/admin/dashboard'],
    ['', '/login'],
  ])('returns correct home for role=%s', (role, expected) => {
    expect(getRoleHomePage(role)).toBe(expected)
  })

  it('returns /login for undefined role', () => {
    expect(getRoleHomePage(undefined)).toBe('/login')
  })

  it('returns /login for unknown role', () => {
    expect(getRoleHomePage('UNKNOWN')).toBe('/login')
  })
})

/* ================================================================
 * Part 2: Router beforeEach — requiresLead guard
 * ================================================================ */

describe('router beforeEach - requiresLead', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    mockUserStore.role = 'TEACHER'
    mockUserStore.token = 'mock-token'
    mockUserStore.userInfo = { id: 42, role: 'TEACHER', realName: 'Test' }
    authState.authenticated = true
    // Navigate to a known safe landing page first
    await router.push('/teacher/dashboard')
    await router.isReady()
  })

  // ── Test 1: Infinite loop guard ──
  it('skips getMyRole entirely when _readonly=1 is already in query (BROKEN LOOP)', async () => {
    mockGetMyRole.mockRejectedValue(new Error('API failed'))

    await router.push({
      path: '/teacher/micro-specialties/123/manage',
      query: { _readonly: '1' },
    })

    // getMyRole must NOT be called — the readonly check at the top of the
    // requiresLead block short-circuits before reaching the API call
    expect(mockGetMyRole).not.toHaveBeenCalled()

    // Navigation must settle on the intended readonly page, not redirect
    expect(router.currentRoute.value.path).toBe('/teacher/micro-specialties/123/manage')
    expect(router.currentRoute.value.query._readonly).toBe('1')
  })

  // ── Test 2: getMyRole fail → readonly redirect (only ONE call) ──
  it('calls getMyRole at most once when API keeps rejecting, then lands on readonly', async () => {
    mockGetMyRole.mockRejectedValue(new Error('API timeout'))

    await router.push({
      path: '/teacher/micro-specialties/123/manage',
      query: {},
    })

    // Guard: first attempt calls getMyRole → reject → redirect to _readonly=1
    // Guard: second attempt sees _readonly=1 → next() directly
    // Expected: getMyRole called exactly ONCE (not in a loop)
    expect(mockGetMyRole).toHaveBeenCalledTimes(1)
    expect(mockGetMyRole).toHaveBeenCalledWith('123')

    // Final route must be the readonly page
    expect(router.currentRoute.value.path).toBe('/teacher/micro-specialties/123/manage')
    expect(router.currentRoute.value.query._readonly).toBe('1')
  })

  // ── Test 3: Non-LEAD role → redirect to teacher list ──
  it('redirects TEACHER with non-LEAD role to /teacher/micro-specialties', async () => {
    mockGetMyRole.mockResolvedValue({ code: 200, data: { role: 'MEMBER' } })

    await router.push({
      path: '/teacher/micro-specialties/123/manage',
      query: {},
    })

    expect(mockGetMyRole).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/teacher/micro-specialties')
  })

  // ── Test 4: Non-TEACHER role → direct redirect (no getMyRole call) ──
  it('redirects STUDENT to home without calling getMyRole', async () => {
    mockGetMyRole.mockResolvedValue({ code: 200, data: { role: 'LEAD' } })
    mockUserStore.role = 'STUDENT'

    await router.push({
      path: '/teacher/micro-specialties/123/manage',
      query: {},
    })

    expect(mockGetMyRole).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/student/courses')
  })

  // ── Test 5: TEACHER + LEAD → allowed entry ──
  it('allows TEACHER with LEAD role to navigate to the page', async () => {
    mockGetMyRole.mockResolvedValue({ code: 200, data: { role: 'LEAD' } })

    await router.push({
      path: '/teacher/micro-specialties/123/manage',
      query: {},
    })

    expect(mockGetMyRole).toHaveBeenCalledTimes(1)
    expect(mockGetMyRole).toHaveBeenCalledWith('123')
    expect(router.currentRoute.value.path).toBe('/teacher/micro-specialties/123/manage')
  })
})

/* ================================================================
 * Part 3: Token refresh → 不跳过角色校验
 * ================================================================
 * 根因: refreshAccessToken 成功后 `return` 裸退出 beforeEach,
 *      导致 STAFF_ONLY_PATHS / roles / requiresLead 全部跳过。
 * 修复: 改用 refreshed flag，成功后不 return，继续执行后续校验。
 * ================================================================ */

describe('router beforeEach - token refresh continues to role checks', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    authState.authenticated = true
    mockUserStore.role = ''
    mockUserStore.token = 'mock-token'
    mockUserStore.userInfo = null
    mockUserStore.refreshToken = 'mock-refresh'
    mockUserStore.getInfo.mockReset()
    mockUserStore.refreshAccessToken.mockReset()
    // 导航到公开路由（requiresAuth: false），避免触发 getInfo
    await router.push('/micro-specialties')
    await router.isReady()
  })

  // ── Test 1: refresh 后 STUDENT 仍被 STAFF_ONLY_PATHS 拦住 ──
  it('STUDENT hitting staff path still gets blocked after token refresh', async () => {
    // getInfo: 第一次失败 → 触发 refresh；第二次成功 → 角色为 STUDENT
    mockUserStore.getInfo
      .mockRejectedValueOnce(new Error('token expired'))
      .mockImplementationOnce(() => {
        mockUserStore.userInfo = { id: 1, role: 'STUDENT', realName: 'Test' }
        mockUserStore.role = 'STUDENT'
        return Promise.resolve({ id: 1, role: 'STUDENT', realName: 'Test' })
      })
    mockUserStore.refreshAccessToken.mockResolvedValueOnce('new-token')

    // 路由 push 到一个只允许 STAFF 的路由 /courses
    await router.push({ path: '/courses' })

    // STUDENT 应该被 STAFF_ONLY_PATHS 拦住 → 重定向到 /student/courses
    expect(mockUserStore.refreshAccessToken).toHaveBeenCalledTimes(1)
    // getInfo 被调用 3 次: 1次在 beforeEach 导航到 /micro-specialties，2次在测试内(1失败+1成功)
    expect(mockUserStore.getInfo).toHaveBeenCalledTimes(3)
    expect(router.currentRoute.value.path).toBe('/student/courses')
  })

  // ── Test 2: refresh 后 roles 校验仍工作 ──
  it('STUDENT accessing TEACHER-only route gets blocked after token refresh', async () => {
    mockUserStore.getInfo
      .mockRejectedValueOnce(new Error('token expired'))
      .mockImplementationOnce(() => {
        mockUserStore.userInfo = { id: 1, role: 'STUDENT', realName: 'Test' }
        mockUserStore.role = 'STUDENT'
        return Promise.resolve({ id: 1, role: 'STUDENT', realName: 'Test' })
      })
    mockUserStore.refreshAccessToken.mockResolvedValueOnce('new-token')

    await router.push({ path: '/teacher/dashboard' })

    // STUDENT 访问 TEACHER 路由 → meta.roles 检查 → 重定向
    expect(mockUserStore.refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).not.toBe('/teacher/dashboard')
    expect(router.currentRoute.value.path).toBe('/student/courses')
  })

  // ── Test 3: refresh 后 requiresLead 校验仍工作 ──
  it('TEACHER (non-LEAD) still redirected after token refresh for requiresLead route', async () => {
    // 模拟: 首次 getInfo 需要 refresh
    mockUserStore.getInfo
      .mockRejectedValueOnce(new Error('token expired'))
      .mockImplementationOnce(() => {
        mockUserStore.userInfo = { id: 42, role: 'TEACHER', realName: 'Test' }
        mockUserStore.role = 'TEACHER'
        return Promise.resolve({ id: 42, role: 'TEACHER', realName: 'Test' })
      })
    mockUserStore.refreshAccessToken.mockResolvedValueOnce('new-token')
    mockGetMyRole.mockResolvedValue({ code: 200, data: { role: 'MEMBER' } })

    await router.push({ path: '/teacher/micro-specialties/123/manage', query: {} })

    // refresh 后应该继续执行 requiresLead 检查 → getMyRole → MEMBER → 重定向
    expect(mockUserStore.refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(mockGetMyRole).toHaveBeenCalledWith('123')
    expect(router.currentRoute.value.path).toBe('/teacher/micro-specialties')
  })
})

/* ================================================================
 * Part 4: 404 toast 同步显示 + 角色重定向
 * ================================================================
 * 根因: beforeEnter 中用动态 import('element-plus') 异步加载 ElMessage，
 *       toast 在 next() 后才显示，导致时序错乱。
 * 修复: 使用顶层同步 import 的 ElMessage，在 next() 前调用。
 * ================================================================ */

describe('404 route - beforeEnter redirect', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    mockUserStore.role = 'TEACHER'
    mockUserStore.userInfo = { id: 42, role: 'TEACHER', realName: 'Test' }
    authState.authenticated = true
    await router.push('/teacher/dashboard')
    await router.isReady()
  })

  it('redirects to role home with synchronous ElMessage.warning', async () => {
    const { ElMessage } = await import('element-plus')

    await router.push({ path: '/nonexistent/page' })

    // 必须有 warning toast（同步调用，不是动态 import）
    expect(ElMessage.warning).toHaveBeenCalled()
    const callArg = ElMessage.warning.mock.calls[0][0]
    expect(callArg.message).toContain('/nonexistent/page')
    // 不包含"已为您跳转到首页"（修复前文案），修复后为"正在跳转..."
    expect(callArg.message).toContain('正在跳转')
    expect(callArg.duration).toBe(2500)

    // 重定向到 TEACHER 的首页
    expect(router.currentRoute.value.path).toBe('/teacher/dashboard')
  })

  it('shows NotFound.vue (no redirect) when userStore.role is empty', async () => {
    mockUserStore.role = ''
    mockUserStore.userInfo = null

    const { ElMessage } = await import('element-plus')
    ElMessage.warning.mockClear()

    await router.push({ path: '/some/unknown/path' })

    // role 为空 → beforeEnter 不 redirect → 显示 NotFound.vue 组件
    expect(ElMessage.warning).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/some/unknown/path')
    expect(router.currentRoute.value.name).toBe('NotFound')
  })
})

/* ================================================================
 * Part 5: Token refresh 网络瞬断 — 保留 token、不登出
 * ================================================================
 * 场景: getInfo 失败 → refreshAccessToken 成功 → getInfo 再失败(网络异常)
 * 期望: 不清除有效 token、不跳转登录、显示"网络异常，请重试"、导航中止
 * ================================================================ */

// 确保 localStorage API 在 happy-dom 中可用
function ensureLocalStorage() {
  if (typeof localStorage === 'undefined' || !localStorage.removeItem) {
    const store = {}
    vi.stubGlobal('localStorage', {
      getItem: vi.fn((key) => store[key] || null),
      setItem: vi.fn((key, val) => { store[key] = String(val) }),
      removeItem: vi.fn((key) => { delete store[key] }),
      clear: vi.fn(() => { Object.keys(store).forEach(k => delete store[k]) }),
      get length() { return Object.keys(store).length },
      key: vi.fn((i) => Object.keys(store)[i] || null),
    })
  }
}

describe('router beforeEach - token refresh + network glitch', () => {
  beforeEach(async () => {
    ensureLocalStorage()
    vi.clearAllMocks()
    authState.authenticated = true
    mockUserStore.role = ''
    mockUserStore.token = 'mock-token'
    mockUserStore.userInfo = null
    mockUserStore.refreshToken = 'mock-refresh'
    mockUserStore.getInfo.mockReset()
    mockUserStore.refreshAccessToken.mockReset()
    await router.push('/micro-specialties')
    await router.isReady()
  })

  // ── Test 1: refresh 成功 → getInfo 网络失败 → 保留 token, next(false) ──
  it('keeps token and aborts navigation when getInfo fails after successful refresh', async () => {
    mockUserStore.getInfo
      .mockRejectedValueOnce(new Error('Network Error')) // 第一次 getInfo 失败
      .mockRejectedValueOnce(new Error('Network Error')) // 第二次 getInfo 也失败(网络瞬断)
    mockUserStore.refreshAccessToken.mockResolvedValueOnce('new-token')

    await router.push({ path: '/teacher/dashboard' })

    // refresh 被调用了一次
    expect(mockUserStore.refreshAccessToken).toHaveBeenCalledTimes(1)
    // token 应该被保留（未被清除）
    expect(mockUserStore.token).toBe('mock-token')
    // 路由应停留在当前页（导航被 next(false) 中止）
    expect(router.currentRoute.value.path).toBe('/micro-specialties')
  })

  // ── Test 2: 401 场景 → refresh 失败 → 清除 token 并跳转登录 ──
  it('clears token and redirects to login when refresh fails (401)', async () => {
    mockUserStore.getInfo.mockRejectedValueOnce(new Error('401 Unauthorized'))
    // 使用 mockImplementationOnce 模拟 refreshAccessToken 在 401 后清除 store token 的行为
    mockUserStore.refreshAccessToken.mockImplementationOnce(() => {
      mockUserStore.token = ''
      mockUserStore.refreshToken = ''
      return Promise.resolve(null)
    })

    await router.push({ path: '/teacher/dashboard' })

    expect(mockUserStore.refreshAccessToken).toHaveBeenCalledTimes(1)
    // 登录态应清除
    expect(router.currentRoute.value.path).toBe('/login')
  })

  // ── Test 3: refresh 网络中断 → token 保留，导航中止 ──
  it('keeps token and aborts navigation when refresh fails with network error', async () => {
    mockUserStore.getInfo.mockRejectedValueOnce(new Error('Network Error'))
    // 模拟 refreshAccessToken 网络失败：返回 null 但保留 store token
    mockUserStore.refreshAccessToken.mockImplementationOnce(() => {
      return Promise.resolve(null) // token 不变（保留 'mock-token'）
    })

    await router.push({ path: '/teacher/dashboard' })

    expect(mockUserStore.refreshAccessToken).toHaveBeenCalledTimes(1)
    // token 应被保留（未被清除）
    expect(mockUserStore.token).toBe('mock-token')
    // 导航被 next(false) 中止，不跳登录
    expect(router.currentRoute.value.path).toBe('/micro-specialties')
  })

  // ── Test 4: refresh_token 不存在 → 直接清除并跳转登录 ──
  it('redirects to login when no refresh token available', async () => {
    mockUserStore.refreshToken = ''

    mockUserStore.getInfo.mockRejectedValueOnce(new Error('401 Unauthorized'))

    await router.push({ path: '/teacher/dashboard' })

    // refreshAccessToken 不应被调用（因为没有 refresh token）
    expect(mockUserStore.refreshAccessToken).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
