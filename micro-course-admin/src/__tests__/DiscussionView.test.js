import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const apiMocks = vi.hoisted(() => ({
  getPosts: vi.fn(() => Promise.resolve({ data: { items: [], totalElements: 0 } })),
  getChapterById: vi.fn(() => Promise.resolve({ data: { courseId: 1 } })),
}))

vi.mock('@/api/discussion', () => ({ getPosts: apiMocks.getPosts }))
vi.mock('@/api/chapter', () => ({ getChapterById: apiMocks.getChapterById }))

vi.mock('@/store/user', () => ({ useUserStore: () => ({ userId: 7, role: 'STUDENT' }) }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), useRoute: () => ({ params: {}, query: { chapterId: '20001' } }) }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }
})

vi.mock('@/composables/useUrlPagination', () => ({ useUrlPagination: () => ({ bindToQuery: vi.fn() }) }))

import DiscussionView from '@/views/student/DiscussionView.vue'

describe('DiscussionView.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('can be mounted with chapterId', async () => {
    const wrapper = mount(DiscussionView, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-table': { template: '<div><slot /></div>' },
          'el-table-column': { template: '<div />' },
          'el-empty': { template: '<div><slot /></div>' },
          'el-pagination': { template: '<div />' },
          'el-select': { template: '<div><slot /></div>' },
          'el-option': { template: '<div />' },
          'el-dialog': { template: '<div><slot /></div>' },
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-input': { template: '<input />' },
          'el-checkbox': { template: '<label><input type="checkbox" /><slot /></label>' },
          'el-icon': { template: '<i><slot /></i>' },
          teleport: true, transition: false,
        },
      },
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
    expect(apiMocks.getChapterById).toHaveBeenCalled()
  })
})
