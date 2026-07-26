import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const userStoreMock = vi.hoisted(() => ({
  isLoggedIn: true,
  role: 'STUDENT',
  userInfo: { id: 1, realName: '测试学员', username: 'student', email: 'student@test.com', avatar: null, phone: '' },
  getInfo: vi.fn(() => Promise.resolve()),
}))

vi.mock('@/store/user', () => ({ useUserStore: () => userStoreMock }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ params: {}, query: {} }) }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }
})

import Profile from '@/views/student/Profile.vue'

describe('Profile.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('can be mounted', async () => {
    const wrapper = mount(Profile, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-skeleton': { template: '<div />' },
          teleport: true, transition: false,
        },
      },
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })
})
