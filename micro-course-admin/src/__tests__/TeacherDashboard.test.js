import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const teacherApiMocks = vi.hoisted(() => ({
  getStats: vi.fn(() => Promise.resolve({ data: { courseCount: 3 } })),
  getStudentActivity: vi.fn(() => Promise.resolve({ data: [] })),
  getPendingTasks: vi.fn(() => Promise.resolve({ data: [] })),
  getNotifications: vi.fn(() => Promise.resolve({ data: [] })),
  getMyCourses: vi.fn(() => Promise.resolve({ data: { items: [] } })),
  getTeacherRevenue: vi.fn(() => Promise.resolve({ data: { totalRevenue: 128 } })),
}))

const teacherRatingMocks = vi.hoisted(() => ({
  getMyRating: vi.fn(() => Promise.resolve({ data: { tier: 'GOLD', ratingScore: 82, tierRate: 25 } })),
  getMyTierHistory: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/api/teacher', () => ({
  getStats: teacherApiMocks.getStats,
  getStudentActivity: teacherApiMocks.getStudentActivity,
  getPendingTasks: teacherApiMocks.getPendingTasks,
  getNotifications: teacherApiMocks.getNotifications,
  getMyCourses: teacherApiMocks.getMyCourses,
  getTeacherRevenue: teacherApiMocks.getTeacherRevenue,
}))

vi.mock('@/api/teacher-rating', () => ({
  getMyRating: teacherRatingMocks.getMyRating,
  getMyTierHistory: teacherRatingMocks.getMyTierHistory,
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    userInfo: {
      realName: '测试老师',
      username: 'teacher-demo',
    },
  }),
}))

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
  })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn() },
  }
})

import TeacherDashboard from '@/views/teacher/TeacherDashboard.vue'

const stubs = {
  'router-link': { props: ['to'], template: '<a :data-to="to"><slot /></a>' },
  'el-row': { template: '<div><slot /></div>' },
  'el-col': { template: '<div><slot /></div>' },
  'el-skeleton': { template: '<div><slot name="default" /></div>' },
  'el-skeleton-item': { template: '<div />' },
  'el-divider': { template: '<hr />' },
  'el-collapse': { template: '<div><slot /></div>' },
  'el-collapse-item': { template: '<div><slot /></div>' },
  'el-rate': { template: '<div />' },
  'el-icon': { template: '<i><slot /></i>' },
}

describe('TeacherDashboard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.runOnlyPendingTimers()
    vi.useRealTimers()
  })

  it('loads rating and revenue during initial mount', async () => {
    const wrapper = mount(TeacherDashboard, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
        mocks: {
          $router: {
            push: vi.fn(),
          },
        },
      },
    })

    await flushPromises()

    expect(teacherApiMocks.getStats).toHaveBeenCalledTimes(1)
    expect(teacherApiMocks.getStudentActivity).toHaveBeenCalledWith(7)
    expect(teacherApiMocks.getPendingTasks).toHaveBeenCalledWith(5)
    expect(teacherApiMocks.getNotifications).toHaveBeenCalledWith(5)
    expect(teacherApiMocks.getMyCourses).toHaveBeenCalledTimes(1)
    expect(teacherRatingMocks.getMyRating).toHaveBeenCalledTimes(1)
    expect(teacherRatingMocks.getMyTierHistory).toHaveBeenCalledTimes(1)
    expect(teacherApiMocks.getTeacherRevenue).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('exposes teacher quick actions for grades and discussion routes', async () => {
    const wrapper = mount(TeacherDashboard, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
        mocks: {
          $router: {
            push: vi.fn(),
          },
        },
      },
    })

    await flushPromises()

    const quickLinks = wrapper.findAll('.quick-action-card').map(node => ({
      text: node.text(),
      to: node.attributes('data-to'),
    }))

    expect(quickLinks).toEqual(expect.arrayContaining([
      expect.objectContaining({ text: expect.stringContaining('成绩明细'), to: '/teacher/grades' }),
      expect.objectContaining({ text: expect.stringContaining('学员提问'), to: '/teacher/discussions' }),
    ]))

    wrapper.unmount()
  })

  it('makes course cards keyboard accessible', async () => {
    teacherApiMocks.getMyCourses.mockResolvedValueOnce({
      data: {
        items: [
          { id: 7, title: '键盘可达课程', studentCount: 12, rating: 4.5, cover: null },
        ],
      },
    })

    const push = vi.fn()
    const wrapper = mount(TeacherDashboard, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
        mocks: {
          $router: { push },
        },
      },
    })

    await flushPromises()

    const card = wrapper.find('.course-card-item')
    expect(card.exists()).toBe(true)
    expect(card.attributes('role')).toBe('button')
    expect(card.attributes('tabindex')).toBe('0')
    expect(card.attributes('aria-label')).toContain('键盘可达课程')

    await card.trigger('keydown.enter')
    expect(push).toHaveBeenCalledWith('/teacher/courses/7')

    await card.trigger('keydown.space')
    expect(push).toHaveBeenCalledWith('/teacher/courses/7')

    wrapper.unmount()
  })

  it('renders the course cover image when the teacher course has a cover URL', async () => {
    teacherApiMocks.getMyCourses.mockResolvedValueOnce({
      data: {
        items: [
          { id: 11, title: '有封面的课程', studentCount: 18, rating: 4.8, cover: '/api/files/covers/course-11.jpg' },
        ],
      },
    })

    const wrapper = mount(TeacherDashboard, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
        mocks: {
          $router: {
            push: vi.fn(),
          },
        },
      },
    })

    await flushPromises()

    const image = wrapper.find('.course-cover-img')
    expect(image.exists()).toBe(true)
    expect(image.attributes('src')).toBe('/api/files/covers/course-11.jpg')
    expect(image.attributes('alt')).toBe('有封面的课程')

    wrapper.unmount()
  })
})
