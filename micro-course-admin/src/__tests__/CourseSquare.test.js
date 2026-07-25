import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const apiMocks = vi.hoisted(() => ({
  getCourses: vi.fn(() => Promise.resolve({ data: { items: [], totalElements: 0 } })),
  getCategories: vi.fn(() => Promise.resolve({ data: { items: [] } })),
  getBundles: vi.fn(() => Promise.resolve({ data: { items: [] } })),
  getActiveBanners: vi.fn(() => Promise.resolve({ data: [] })),
  getSquareData: vi.fn(() => Promise.resolve({ data: {} })),
  getDepartments: vi.fn(() => Promise.resolve({ data: { items: [] } })),
}))

vi.mock('@/api/course', () => ({ getCourses: apiMocks.getCourses }))
vi.mock('@/api/course-category', () => ({ getCategories: apiMocks.getCategories }))
vi.mock('@/api/bundle', () => ({ getBundles: apiMocks.getBundles }))
vi.mock('@/api/bannerPublic', () => ({ getActiveBanners: apiMocks.getActiveBanners }))
vi.mock('@/api/microSpecialty', () => ({ getSquareData: apiMocks.getSquareData, getMicroSpecialtyList: vi.fn() }))
vi.mock('@/api/department', () => ({ getDepartments: apiMocks.getDepartments }))
vi.mock('@/store/user', () => ({ useUserStore: () => ({ role: 'STUDENT' }) }))
vi.mock('@/store/plugins', () => ({ usePluginStore: () => ({ getCourseCardConfig: vi.fn() }) }))
vi.mock('@/utils/coverHelper', () => ({ getDefaultCover: vi.fn(() => 'cover.svg') }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRouter: () => ({ push: vi.fn(), back: vi.fn() }), useRoute: () => ({ params: {}, query: {} }) }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() } }
})

import CourseSquare from '@/views/student/CourseSquare.vue'

describe('CourseSquare.vue', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('can be mounted', async () => {
    const wrapper = mount(CourseSquare, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-row': { template: '<div><slot /></div>' },
          'el-col': { template: '<div><slot /></div>' },
          'el-input': { template: '<input />' },
          'el-select': { template: '<div><slot /></div>' },
          'el-option': { template: '<div />' },
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio-button': { template: '<label><slot /></label>' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-carousel': { template: '<div><slot /></div>' },
          'el-carousel-item': { template: '<div />' },
          'el-skeleton': { template: '<div><slot /></div>' },
          'el-empty': { template: '<div><slot /></div>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-result': { template: '<div><slot /></div>' },
          'el-dialog': { template: '<div><slot /></div>' },
          'el-pagination': { template: '<div />' },
          teleport: true, transition: false,
        },
      },
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('calls API on mount', async () => {
    mount(CourseSquare, {
      global: {
        stubs: {
          'el-button': { template: '<button><slot /></button>' },
          'el-card': { template: '<div><slot /></div>' },
          'el-row': { template: '<div><slot /></div>' },
          'el-col': { template: '<div><slot /></div>' },
          'el-input': { template: '<input />' },
          'el-select': { template: '<div><slot /></div>' },
          'el-option': { template: '<div />' },
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio-button': { template: '<label><slot /></label>' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-carousel': { template: '<div><slot /></div>' },
          'el-carousel-item': { template: '<div />' },
          'el-skeleton': { template: '<div><slot /></div>' },
          'el-empty': { template: '<div><slot /></div>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-result': { template: '<div><slot /></div>' },
          'el-dialog': { template: '<div><slot /></div>' },
          'el-pagination': { template: '<div />' },
          teleport: true, transition: false,
        },
      },
    })
    await flushPromises()
    expect(apiMocks.getCourses).toHaveBeenCalled()
    expect(apiMocks.getCategories).toHaveBeenCalled()
    expect(apiMocks.getDepartments).toHaveBeenCalled()
    expect(apiMocks.getBundles).toHaveBeenCalled()
    expect(apiMocks.getActiveBanners).toHaveBeenCalled()
  })
})
