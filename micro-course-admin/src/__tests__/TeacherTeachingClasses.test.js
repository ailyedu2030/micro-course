import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

// ============================================================
// Mock setup — vi.hoisted ensures hoisting above imports
// ============================================================
const courseApiMocks = vi.hoisted(() => ({
  getCourses: vi.fn(),
}))

const teachingClassApiMocks = vi.hoisted(() => ({
  getTeachingClasses: vi.fn(),
  getTeachingClassStudents: vi.fn(),
  addStudentToClass: vi.fn(),
  removeStudentFromClass: vi.fn(),
  updateStudentStatus: vi.fn(),
}))

const userApiMocks = vi.hoisted(() => ({
  getUsers: vi.fn(),
}))

vi.mock('@/api/course', () => ({
  getCourses: courseApiMocks.getCourses,
}))

vi.mock('@/api/teaching-class', () => ({
  getTeachingClasses: teachingClassApiMocks.getTeachingClasses,
  getTeachingClassStudents: teachingClassApiMocks.getTeachingClassStudents,
  addStudentToClass: teachingClassApiMocks.addStudentToClass,
  removeStudentFromClass: teachingClassApiMocks.removeStudentFromClass,
  updateStudentStatus: teachingClassApiMocks.updateStudentStatus,
}))

vi.mock('@/api/user', () => ({
  getUsers: userApiMocks.getUsers,
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    userInfo: { id: 1, role: 'TEACHER' },
    userId: 1,
    role: 'TEACHER',
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import TeacherTeachingClasses from '@/views/teacher/TeacherTeachingClasses.vue'

// ============================================================
// Helpers
// ============================================================
const stubs = {
  'el-icon': { template: '<span><slot /></span>' },
}

function createWrapper() {
  return mount(TeacherTeachingClasses, {
    global: { stubs },
    attachTo: document.body,
  })
}

/** Build a CourseVO-like object */
function mockCourse(overrides = {}) {
  return {
    id: 1,
    title: '测试课程',
    teacherId: 1,
    ...overrides,
  }
}

/** Build a TeachingClassVO-like object (studentCount, NOT currentStudents) */
function mockTeachingClass(overrides = {}) {
  return {
    id: 10,
    courseId: 1,
    teacherId: 1,
    name: '周一 1-2 节',
    maxStudents: 30,
    studentCount: 15,
    semester: '2025-2026-1',
    status: 1,
    ...overrides,
  }
}

// ============================================================
// Tests
// ============================================================
describe('TeacherTeachingClasses.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ——— Loading states ———
  it('shows skeleton while courses are loading', async () => {
    courseApiMocks.getCourses.mockReturnValue(new Promise(() => {})) // never resolves
    const wrapper = createWrapper()
    await nextTick()
    expect(wrapper.find('.loading-wrap').exists()).toBe(true)
  })

  it('shows skeleton while classes are loading after selecting a course', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockReturnValue(new Promise(() => {}))
    const wrapper = createWrapper()
    await flushPromises()
    // Click first course
    await wrapper.find('.course-item').trigger('click')
    await nextTick()
    expect(wrapper.find('.loading-wrap').exists()).toBe(true)
  })

  // ——— Empty states ———
  it('shows empty state when no courses', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [], totalElements: 0 } })
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无课程')
  })

  it('shows empty state when no classes for selected course', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({ data: { items: [], totalElements: 0 } })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('该课程暂无教学班')
  })

  // ——— Error states ———
  it('shows courses error state with retry button when course API fails', async () => {
    courseApiMocks.getCourses.mockRejectedValue(new Error('Network error'))
    const wrapper = createWrapper()
    await flushPromises()
    expect(wrapper.text()).toContain('课程加载失败')
    const retryBtn = wrapper.find('.error-state .el-button')
    // Re-mock to succeed and click retry
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    await retryBtn.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('课程加载失败')
  })

  it('shows classes error state with retry button when class API fails', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockRejectedValue(new Error('Server error'))
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('教学班加载失败')
    const retryBtn = wrapper.find('.error-state .el-button')
    // Re-mock to succeed and click retry
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: { items: [mockTeachingClass()], totalElements: 1 },
    })
    await retryBtn.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('教学班加载失败')
  })

  // ——— Capacity display (core bug fix) ———
  it('displays capacity as "studentCount/maxStudents" when both present', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass({ studentCount: 15, maxStudents: 30 })],
        totalElements: 1,
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    const classMeta = wrapper.find('.class-meta')
    expect(classMeta.text()).toContain('容量 15/30')
  })

  it('displays "0/maxStudents" when studentCount is 0', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass({ studentCount: 0, maxStudents: 30 })],
        totalElements: 1,
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    expect(wrapper.find('.class-meta').text()).toContain('容量 0/30')
  })

  it('displays "N 人" when maxStudents is null (unlimited capacity)', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass({ studentCount: 5, maxStudents: null })],
        totalElements: 1,
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    expect(wrapper.find('.class-meta').text()).toContain('容量 5 人')
  })

  it('displays "N 人" when maxStudents is 0 (unlimited capacity)', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass({ studentCount: 8, maxStudents: 0 })],
        totalElements: 1,
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    expect(wrapper.find('.class-meta').text()).toContain('容量 8 人')
  })

  it('displays "0 人" when both studentCount and maxStudents are null', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass({ studentCount: null, maxStudents: null })],
        totalElements: 1,
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    expect(wrapper.find('.class-meta').text()).toContain('容量 0 人')
  })

  it('displays full capacity when studentCount equals maxStudents', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass({ studentCount: 30, maxStudents: 30 })],
        totalElements: 1,
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    expect(wrapper.find('.class-meta').text()).toContain('容量 30/30')
  })

  // ——— Semester grouping ———
  it('groups classes by semester', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [
          mockTeachingClass({ id: 10, name: '周一班', semester: '2025-2026-1' }),
          mockTeachingClass({ id: 11, name: '周三班', semester: '2025-2026-1' }),
          mockTeachingClass({ id: 12, name: '周二班', semester: '2025-2026-2' }),
        ],
        totalElements: 3,
      },
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    const groupHeaders = wrapper.findAll('.group-header')
    expect(groupHeaders.length).toBe(2)
    expect(groupHeaders[0].text()).toContain('2025-2026-2')
    expect(groupHeaders[1].text()).toContain('2025-2026-1')
  })

  // ——— Expand / collapse ———
  it('expands a class to show student list', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass()],
        totalElements: 1,
      },
    })
    teachingClassApiMocks.getTeachingClassStudents.mockResolvedValue({
      data: [
        { id: 100, studentNo: '2024001', realName: '张三', status: 'ENROLLED', enrolledAt: '2025-09-01T08:00:00' },
      ],
    })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    await wrapper.find('.class-summary').trigger('click')
    await flushPromises()
    expect(wrapper.find('.student-table').exists()).toBe(true)
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('2024001')
  })

  it('collapses when clicking expanded class again', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: {
        items: [mockTeachingClass()],
        totalElements: 1,
      },
    })
    teachingClassApiMocks.getTeachingClassStudents.mockResolvedValue({ data: [] })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    await wrapper.find('.class-summary').trigger('click') // expand
    await flushPromises()
    await wrapper.find('.class-summary').trigger('click') // collapse
    await nextTick()
    expect(wrapper.find('.student-table').exists()).toBe(false)
  })

  // ——— Keyboard accessibility ———
  it('responds to keyboard Enter on course item', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({ data: { items: [], totalElements: 0 } })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('keydown.enter')
    await flushPromises()
    expect(teachingClassApiMocks.getTeachingClasses).toHaveBeenCalled()
  })

  it('responds to keyboard Enter on class summary', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: { items: [mockTeachingClass()], totalElements: 1 },
    })
    teachingClassApiMocks.getTeachingClassStudents.mockResolvedValue({ data: [] })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    await wrapper.find('.class-summary').trigger('keydown.enter')
    await flushPromises()
    expect(wrapper.find('.class-detail').exists()).toBe(true)
  })

  it('responds to keyboard Space on class summary', async () => {
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [mockCourse()], totalElements: 1 } })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: { items: [mockTeachingClass()], totalElements: 1 },
    })
    teachingClassApiMocks.getTeachingClassStudents.mockResolvedValue({ data: [] })
    const wrapper = createWrapper()
    await flushPromises()
    await wrapper.find('.course-item').trigger('click')
    await flushPromises()
    await wrapper.find('.class-summary').trigger('keydown.space')
    await flushPromises()
    expect(wrapper.find('.class-detail').exists()).toBe(true)
  })
})
