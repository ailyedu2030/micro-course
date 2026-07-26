import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const mockStore = vi.hoisted(() => ({
  role: 'TEACHER',
  userId: 42,
  userInfo: null,
  getInfo: vi.fn(async () => null),
}))

const courseApiMocks = vi.hoisted(() => ({
  getCourses: vi.fn(async () => ({ data: { items: [] } })),
}))

const departmentApiMocks = vi.hoisted(() => ({
  getDepartments: vi.fn(async () => ({ data: { items: [] } })),
}))

const gradeApiMocks = vi.hoisted(() => ({
  getGrades: vi.fn(async () => ({ data: { items: [], totalElements: 0 } })),
  submitGrade: vi.fn(async () => ({ data: {} })),
}))

const teachingClassApiMocks = vi.hoisted(() => ({
  getTeachingClasses: vi.fn(async () => ({ data: { items: [], totalElements: 0 } })),
  getTeachingClassStudents: vi.fn(async () => ({ data: { items: [] } })),
  addStudentToClass: vi.fn(async () => ({ data: {} })),
  removeStudentFromClass: vi.fn(async () => ({ data: {} })),
  updateStudentStatus: vi.fn(async () => ({ data: {} })),
}))

const userApiMocks = vi.hoisted(() => ({
  getUsers: vi.fn(async () => ({ data: { items: [] } })),
}))

const chapterApiMocks = vi.hoisted(() => ({
  getChapters: vi.fn(async () => ({ data: { items: [] } })),
}))

const slideApiMocks = vi.hoisted(() => ({
  getSlides: vi.fn(async () => ({ data: [] })),
  listSlides: vi.fn(async () => ({ data: [] })),
  getSlidePages: vi.fn(async () => ({ data: [] })),
  deleteSlide: vi.fn(async () => ({ data: {} })),
  deleteSlideById: vi.fn(async () => ({ data: {} })),
  updateSlideName: vi.fn(async () => ({ data: {} })),
  uploadSlide: vi.fn(async () => ({ data: {} })),
}))

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}))

const routeState = vi.hoisted(() => ({
  query: {},
}))

const elementPlusMocks = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => mockStore,
}))

vi.mock('@/api/course', () => ({
  getCourses: courseApiMocks.getCourses,
}))

vi.mock('@/api/department', () => ({
  getDepartments: departmentApiMocks.getDepartments,
}))

vi.mock('@/api/grade', () => ({
  getGrades: gradeApiMocks.getGrades,
  submitGrade: gradeApiMocks.submitGrade,
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

vi.mock('@/api/chapter', () => ({
  getChapters: chapterApiMocks.getChapters,
}))

vi.mock('@/plugins/interactive/api/slide', () => ({
  getSlides: slideApiMocks.getSlides,
  listSlides: slideApiMocks.listSlides,
  getSlidePages: slideApiMocks.getSlidePages,
  deleteSlide: slideApiMocks.deleteSlide,
  deleteSlideById: slideApiMocks.deleteSlideById,
  updateSlideName: slideApiMocks.updateSlideName,
  uploadSlide: slideApiMocks.uploadSlide,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => routerMocks,
}))

vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
  })),
}))

vi.mock('echarts/charts', () => ({
  BarChart: {},
}))

vi.mock('echarts/components', () => ({
  GridComponent: {},
  TooltipComponent: {},
}))

vi.mock('echarts/renderers', () => ({
  CanvasRenderer: {},
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: elementPlusMocks,
    ElMessageBox: {
      confirm: vi.fn(async () => true),
    },
    ElInput: { template: '<input />' },
  }
})

import StudentGrades from '@/views/teacher/StudentGrades.vue'
import TeacherTeachingClasses from '@/views/teacher/TeacherTeachingClasses.vue'
import TeacherSlideOverview from '@/views/teacher/TeacherSlideOverview.vue'

const stubs = {
  'el-card': { template: '<div><slot /><slot name="header" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-empty': { props: ['description'], template: '<div class="empty-stub">{{ description }}</div>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-pagination': { template: '<div />' },
  'el-row': { template: '<div><slot /></div>' },
  'el-col': { template: '<div><slot /></div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-skeleton': { template: '<div><slot /></div>' },
  'el-icon': { template: '<i><slot /></i>' },
  'router-link': { template: '<a><slot /></a>' },
  'el-upload': { template: '<div><slot /><slot name="tip" /></div>' },
  Edit: { template: '<i />' },
}

describe('teacher identity consistency', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.query = {}
    mockStore.role = 'TEACHER'
    mockStore.userId = 42
    mockStore.userInfo = null
    mockStore.getInfo.mockResolvedValue(null)
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [] } })
    gradeApiMocks.getGrades.mockResolvedValue({ data: { items: [], totalElements: 0 } })
  })

  it('uses store userId when StudentGrades loads teacher courses', async () => {
    mount(StudentGrades, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(courseApiMocks.getCourses).toHaveBeenCalled()
    expect(courseApiMocks.getCourses).toHaveBeenCalledWith(expect.objectContaining({
      teacherId: 42,
    }))
  })

  it('does not fetch grades until a course is selected', async () => {
    mount(StudentGrades, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(gradeApiMocks.getGrades).not.toHaveBeenCalled()
  })

  it('fetches grades automatically when route carries a courseId', async () => {
    routeState.query = { courseId: '99' }

    mount(StudentGrades, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(gradeApiMocks.getGrades).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 99,
    }))
  })

  it('computes StudentGrades stats from the full course dataset instead of current page only', async () => {
    routeState.query = { courseId: '99' }
    gradeApiMocks.getGrades
      .mockResolvedValueOnce({
        data: {
          items: [
            {
              id: 1,
              realName: '张三',
              courseName: '测试课程',
              score: 100,
              comment: '',
              gradedAt: null,
              enrollmentId: 11,
            },
          ],
          totalElements: 2,
        },
      })
      .mockResolvedValueOnce({
        data: {
          items: [
            {
              id: 1,
              realName: '张三',
              courseName: '测试课程',
              score: 100,
              comment: '',
              gradedAt: null,
              enrollmentId: 11,
            },
            {
              id: 2,
              realName: '李四',
              courseName: '测试课程',
              score: 50,
              comment: '',
              gradedAt: null,
              enrollmentId: 12,
            },
          ],
          totalElements: 2,
        },
      })

    const wrapper = mount(StudentGrades, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(gradeApiMocks.getGrades).toHaveBeenCalledTimes(2)
    expect(gradeApiMocks.getGrades).toHaveBeenNthCalledWith(2, expect.objectContaining({
      courseId: 99,
      page: 0,
      size: 2,
    }))
    expect(wrapper.text()).toContain('75.0')
    expect(wrapper.text()).toContain('50%')
    expect(wrapper.text()).toContain('100.0')
    expect(wrapper.text()).toContain('2 / 2')
  })

  it('uses store userId when TeacherTeachingClasses loads teacher courses', async () => {
    mount(TeacherTeachingClasses, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(elementPlusMocks.error).not.toHaveBeenCalledWith('无法获取当前用户信息')
    expect(courseApiMocks.getCourses).toHaveBeenCalledWith(expect.objectContaining({
      teacherId: 42,
    }))
  })

  it('uses store userId without forcing getInfo in TeacherSlideOverview', async () => {
    mount(TeacherSlideOverview, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(mockStore.getInfo).not.toHaveBeenCalled()
    expect(courseApiMocks.getCourses).toHaveBeenCalledWith(expect.objectContaining({
      teacherId: 42,
    }))
  })

  it('shows neutral slide empty message before a course is selected', async () => {
    courseApiMocks.getCourses.mockResolvedValue({
      data: {
        items: [{ id: 1, title: '测试课程' }],
      },
    })

    const wrapper = mount(TeacherSlideOverview, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('当前还没有课件，可直接上传或按课程筛选。')
    expect(wrapper.text()).not.toContain('已选课程尚未上传课件')
  })

  it('shows an error and stops loading slide data when teacher identity is unavailable', async () => {
    mockStore.userId = null
    mockStore.getInfo.mockRejectedValueOnce(new Error('auth failed'))

    mount(TeacherSlideOverview, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    expect(mockStore.getInfo).toHaveBeenCalledTimes(1)
    expect(elementPlusMocks.error).toHaveBeenCalledWith('无法获取当前教师信息')
    expect(courseApiMocks.getCourses).not.toHaveBeenCalled()
  })

  it('adds keyboard accessibility to teaching class course items', async () => {
    courseApiMocks.getCourses.mockResolvedValueOnce({
      data: {
        items: [{ id: 100, title: '键盘课程', code: 'C100' }],
      },
    })
    teachingClassApiMocks.getTeachingClasses.mockResolvedValueOnce({
      data: {
        items: [],
        totalElements: 0,
      },
    })

    const wrapper = mount(TeacherTeachingClasses, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    const courseItem = wrapper.find('.course-item')
    expect(courseItem.attributes('role')).toBe('button')
    expect(courseItem.attributes('tabindex')).toBe('0')
    expect(courseItem.attributes('aria-label')).toContain('键盘课程')

    await courseItem.trigger('keydown.enter')
    await flushPromises()

    expect(teachingClassApiMocks.getTeachingClasses).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 100,
    }))
  })

  it('uses a read-only view title for ACADEMIC users even when the grade is pending', async () => {
    mockStore.role = 'ACADEMIC'
    routeState.query = { courseId: '99' }
    courseApiMocks.getCourses.mockResolvedValue({
      data: {
        items: [{ id: 99, title: '测试课程' }],
      },
    })
    gradeApiMocks.getGrades.mockResolvedValue({
      data: {
        items: [
          {
            id: 1,
            realName: '张三',
            courseName: '测试课程',
            score: null,
            comment: '',
            gradedAt: null,
            enrollmentId: 11,
          },
        ],
        totalElements: 1,
      },
    })

    const wrapper = mount(StudentGrades, {
      global: {
        stubs,
      },
    })

    await flushPromises()
    wrapper.vm.handleGrade(wrapper.vm.tableData[0])
    await flushPromises()

    expect(wrapper.vm.gradeDialogTitle).toBe('查看成绩')
    expect(wrapper.vm.isReadOnlyGradeView).toBe(true)
    expect(wrapper.text()).not.toContain('提交成绩')
    expect(wrapper.vm.userStore.role).toBe('ACADEMIC')
  })

  it('reloads teacher courses when the department filter is cleared', async () => {
    mockStore.role = 'ACADEMIC'
    courseApiMocks.getCourses
      .mockResolvedValueOnce({
        data: {
          items: [{ id: 1, title: '院系课程 A' }],
        },
      })
      .mockResolvedValueOnce({
        data: {
          items: [{ id: 11, title: '院系课程 B' }],
        },
      })
      .mockResolvedValueOnce({
        data: {
          items: [{ id: 2, title: '全部课程 B' }],
        },
      })

    const wrapper = mount(StudentGrades, {
      global: {
        stubs,
      },
    })

    await flushPromises()

    wrapper.vm.searchForm.departmentId = 7
    wrapper.vm.handleDeptChange()
    await flushPromises()

    expect(courseApiMocks.getCourses).toHaveBeenLastCalledWith(expect.objectContaining({
      offerDepartmentId: 7,
    }))

    wrapper.vm.searchForm.departmentId = ''
    wrapper.vm.handleDeptChange()
    await flushPromises()

    expect(courseApiMocks.getCourses).toHaveBeenLastCalledWith(expect.not.objectContaining({
      offerDepartmentId: expect.anything(),
    }))
    expect(wrapper.vm.courseOptions).toEqual([{ id: 2, title: '全部课程 B' }])
  })
})
