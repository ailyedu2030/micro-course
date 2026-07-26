import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const progressApiMocks = vi.hoisted(() => ({
  getStudyDays: vi.fn(() => Promise.resolve({ data: 7 })),
  getTotalTime: vi.fn(() => Promise.resolve({ data: 3600 })),
  getLearningProgress: vi.fn(() => Promise.resolve({ data: [] })),
  getServerTime: vi.fn(() => Promise.resolve({ data: '2026-07-24T10:00:00Z' })),
}))

const enrollmentApiMocks = vi.hoisted(() => ({
  getMyEnrollments: vi.fn(() => Promise.resolve({
    data: [
      {
        courseId: 201,
        courseTitle: '继续学习课程',
        courseCover: '/cover.png',
        progress: 60,
        currentChapter: 2,
        courseType: 'VIDEO',
        completed: false,
      },
    ],
  })),
}))

vi.mock('@/components/learning-center/AccuracyTrendChart.vue', () => ({
  default: { template: '<div class="mock-trend-chart" />' },
}))

vi.mock('@/api/learning-progress', () => ({
  getStudyDays: progressApiMocks.getStudyDays,
  getTotalTime: progressApiMocks.getTotalTime,
  getLearningProgress: progressApiMocks.getLearningProgress,
  getServerTime: progressApiMocks.getServerTime,
}))

vi.mock('@/api/enrollment', () => ({
  getMyEnrollments: enrollmentApiMocks.getMyEnrollments,
}))

vi.mock('@/api/badge', () => ({
  getMyBadges: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/api/course', () => ({
  getCourses: vi.fn(() => Promise.resolve({ data: { items: [] } })),
}))

vi.mock('@/api/certificate', () => ({
  getMyCertificates: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/api/checkin', () => ({
  getMyCheckIns: vi.fn(() => Promise.resolve({ data: [] })),
  createCheckIn: vi.fn(() => Promise.resolve({ data: {} })),
  getCheckInStreak: vi.fn(() => Promise.resolve({ data: 3 })),
}))

vi.mock('@/api/exercise-record', () => ({
  getAccuracyTrend: vi.fn(() => Promise.resolve({ data: [] })),
}))

vi.mock('@/utils/enrollmentFilters', () => ({
  filterActiveLearningEnrollments: (items) => items,
  filterCourseCollectionEnrollments: (items) => items,
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    userInfo: { realName: '测试学员', username: 'student-a11y' },
  }),
}))

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  }
})

import LearningCenter from '@/views/student/LearningCenter.vue'

const stubs = {
  'el-card': { template: '<div><slot /><slot name="header" /></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-progress': { template: '<div />' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-tooltip': { template: '<div><slot /></div>' },
  'el-result': { template: '<div><slot /><slot name="extra" /></div>' },
  'el-skeleton': { template: '<div />' },
  'router-link': { template: '<a><slot /></a>' },
}

describe('LearningCenter.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the continue-learning card as a keyboard-accessible button', async () => {
    const wrapper = mount(LearningCenter, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    const continueButton = wrapper.find('button.continue-learning')
    expect(continueButton.exists()).toBe(true)
    expect(continueButton.attributes('aria-label')).toContain('继续学习')
  })
})
