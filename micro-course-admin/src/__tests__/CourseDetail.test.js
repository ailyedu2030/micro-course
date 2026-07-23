import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const courseApiMocks = vi.hoisted(() => ({
  getCourseById: vi.fn(() => Promise.resolve({
    data: {
      id: 101,
      title: '无障碍课程',
      courseType: 'VIDEO',
      chapters: [],
      teacherId: 9,
      price: 0,
      isFree: true,
    },
  })),
  getMyCoursePrice: vi.fn(() => Promise.resolve({ data: { free: true, finalPrice: 0 } })),
}))

const userApiMocks = vi.hoisted(() => ({
  getPublicProfile: vi.fn(() => Promise.resolve({ data: { realName: '测试教师' } })),
}))

const reviewApiMocks = vi.hoisted(() => ({
  getReviews: vi.fn(() => Promise.resolve({ data: [] })),
  createReview: vi.fn(() => Promise.resolve({ data: {} })),
}))

const enrollmentApiMocks = vi.hoisted(() => ({
  enroll: vi.fn(() => Promise.resolve({ data: {} })),
  getMyEnrollments: vi.fn(() => Promise.resolve({ data: [] })),
  getCourseRanking: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/api/course', () => ({
  getCourseById: courseApiMocks.getCourseById,
  getMyCoursePrice: courseApiMocks.getMyCoursePrice,
}))

vi.mock('@/api/user', () => ({
  getPublicProfile: userApiMocks.getPublicProfile,
}))

vi.mock('@/api/video', () => ({
  getVideos: vi.fn(() => Promise.resolve({ data: { items: [] } })),
}))

vi.mock('@/api/enrollment', () => ({
  enroll: enrollmentApiMocks.enroll,
  getMyEnrollments: enrollmentApiMocks.getMyEnrollments,
  getCourseRanking: enrollmentApiMocks.getCourseRanking,
}))

vi.mock('@/store/cart', () => ({
  useCartStore: () => ({ addItem: vi.fn() }),
}))

vi.mock('@/api/order', () => ({
  createOrder: vi.fn(() => Promise.resolve({ data: { status: 'PAID' } })),
  payOrder: vi.fn(() => Promise.resolve({ data: {} })),
}))

vi.mock('@/utils/coverHelper', () => ({
  getDefaultCover: vi.fn(() => '/default-cover.png'),
}))

vi.mock('@/api/course-review', () => ({
  createReview: reviewApiMocks.createReview,
  getReviews: reviewApiMocks.getReviews,
}))

vi.mock('@/api/review', () => ({
  createReport: vi.fn(() => Promise.resolve({ data: {} })),
}))

vi.mock('@/api/learning-progress', () => ({
  getLearningProgress: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/plugins/interactive/api/slide', () => ({
  getSlidePages: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    isLoggedIn: true,
    role: 'STUDENT',
    userInfo: { id: 1, realName: '测试学员' },
  }),
}))

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'token'),
}))

vi.mock('hls.js', () => ({
  default: {
    isSupported: () => false,
  },
}))

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ params: { id: '101' }, query: {} }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(), alert: vi.fn() },
  }
})

import CourseDetail from '@/views/student/CourseDetail.vue'

const stubs = {
  'router-link': { template: '<a><slot /></a>' },
  'el-empty': { template: '<div><slot /></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-avatar': { template: '<div><slot /></div>' },
  'el-rate': { template: '<div />' },
  'el-dialog': { template: '<div><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<textarea><slot /></textarea>' },
  'el-progress': { template: '<div />' },
  'el-collapse': { template: '<div><slot /></div>' },
  'el-collapse-item': { template: '<div><slot name="title" /><slot /></div>' },
  'el-icon': { template: '<i><slot /></i>' },
}

describe('CourseDetail.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the preview CTA as a keyboard-accessible button', async () => {
    const wrapper = mount(CourseDetail, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
      },
    })

    await flushPromises()

    const previewButton = wrapper.find('button.hero-preview-trigger')
    expect(previewButton.exists()).toBe(true)
    expect(previewButton.attributes('aria-label')).toContain('预览')
  })
})
