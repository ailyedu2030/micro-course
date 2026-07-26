/**
 * StudentGrades — 成绩批改 UX 修复
 * 测试覆盖:
 * 1. confirmGrade 双击保护（savingGrade 状态锁）
 * 2. enrollmentId 缺失时不发请求
 * 3. currentStudent 不存在时不发请求
 * 4. validation 失败后 savingGrade 复位
 * 5. 正常提交流程
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

const gradeApiMocks = vi.hoisted(() => ({
  getGrades: vi.fn(),
  submitGrade: vi.fn(),
}))

const courseApiMocks = vi.hoisted(() => ({
  getCourses: vi.fn(),
}))

const deptApiMocks = vi.hoisted(() => ({
  getDepartments: vi.fn(),
}))

const mockUserStore = vi.hoisted(() => ({
  userInfo: { id: 1, role: 'TEACHER' },
  userId: 1,
  role: 'TEACHER',
}))

vi.mock('@/api/grade', () => ({
  getGrades: gradeApiMocks.getGrades,
  submitGrade: gradeApiMocks.submitGrade,
}))

vi.mock('@/api/course', () => ({
  getCourses: courseApiMocks.getCourses,
}))

vi.mock('@/api/department', () => ({
  getDepartments: deptApiMocks.getDepartments,
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => mockUserStore,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import StudentGrades from '@/views/teacher/StudentGrades.vue'

// Mock echarts
vi.mock('echarts/core', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn(),
  })),
  use: vi.fn(),
}))

vi.mock('echarts/charts', () => ({ BarChart: vi.fn() }))
vi.mock('echarts/components', () => ({ GridComponent: vi.fn(), TooltipComponent: vi.fn() }))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: vi.fn() }))

const stubs = {
  'el-breadcrumb': { template: '<div><slot /></div>' },
  'el-breadcrumb-item': { template: '<span><slot /></span>' },
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-input-number': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div><slot :row="row" /></div>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': { template: '<div><slot /></div>' },
  'el-skeleton': { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-dialog': { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-icon': { template: '<span><slot /></span>' },
}

function mockStudent(overrides = {}) {
  return {
    id: 1,
    realName: '张三',
    courseName: '高等数学',
    score: null,
    comment: '',
    gradedAt: null,
    enrollmentId: 42,
    ...overrides,
  }
}

function createWrapper() {
  return mount(StudentGrades, {
    global: { stubs },
    attachTo: document.body,
  })
}

describe('StudentGrades - confirmGrade UX', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [] } })
    deptApiMocks.getDepartments.mockResolvedValue({ data: { items: [] } })
    gradeApiMocks.getGrades.mockResolvedValue({ data: { items: [], totalElements: 0 } })
    mockUserStore.role = 'TEACHER'
    mockUserStore.userInfo = { id: 1, role: 'TEACHER' }
  })

  // ── 双击保护: savingGrade=true 时第二次调用直接 return ──
  it('prevents double submission when savingGrade is true', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const vm = wrapper.vm

    // 先设置一个带 enrollmentId 的 currentStudent
    vm.currentStudent = mockStudent()
    vm.gradeForm.score = 85
    vm.gradeForm.comment = '很好'

    // 模拟 gradeFormRef.validate 成功
    vm.gradeFormRef = { validate: vi.fn(() => Promise.resolve(true)) }

    // 设置 savingGrade=true 模拟正在提交
    vm.savingGrade = true

    // 确保 submitGrade 不会被调用
    await vm.confirmGrade()
    expect(gradeApiMocks.submitGrade).not.toHaveBeenCalled()
  })

  // ── enrollmentId 缺失时不发请求 ──
  it('does not submit when enrollmentId is null', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const vm = wrapper.vm

    vm.currentStudent = mockStudent({ enrollmentId: null })
    vm.gradeForm.score = 85
    vm.gradeFormRef = { validate: vi.fn(() => Promise.resolve(true)) }

    await vm.confirmGrade()
    // 保存结束后 savingGrade 应复位
    expect(vm.savingGrade).toBe(false)
    expect(gradeApiMocks.submitGrade).not.toHaveBeenCalled()
  })

  // ── currentStudent 为 null 时不发请求 ──
  it('does not submit when currentStudent is null', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const vm = wrapper.vm

    vm.currentStudent = null
    vm.gradeForm.score = 85
    vm.gradeFormRef = { validate: vi.fn(() => Promise.resolve(true)) }

    await vm.confirmGrade()
    expect(vm.savingGrade).toBe(false)
    expect(gradeApiMocks.submitGrade).not.toHaveBeenCalled()
  })

  // ── validation 失败后 savingGrade 复位 ──
  it('resets savingGrade after validation failure', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const vm = wrapper.vm

    vm.currentStudent = mockStudent()
    vm.gradeForm.score = null
    // validate 返回 false (校验失败)
    vm.gradeFormRef = { validate: vi.fn(() => Promise.resolve(false)) }

    await vm.confirmGrade()
    // 校验失败后 savingGrade 应被 finally 复位
    expect(vm.savingGrade).toBe(false)
    expect(gradeApiMocks.submitGrade).not.toHaveBeenCalled()
  })

  // ── validate catch 场景（Promise reject）──
  it('resets savingGrade when validate rejects', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const vm = wrapper.vm

    vm.currentStudent = mockStudent()
    vm.gradeForm.score = 99
    vm.gradeFormRef = { validate: vi.fn(() => Promise.reject(new Error('validation error'))) }

    await vm.confirmGrade()
    expect(vm.savingGrade).toBe(false)
    expect(gradeApiMocks.submitGrade).not.toHaveBeenCalled()
  })

  // ── 正常提交成功 ──
  it('submits grade successfully with valid data', async () => {
    gradeApiMocks.submitGrade.mockResolvedValue({ code: 200, data: {} })

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const vm = wrapper.vm

    vm.currentStudent = mockStudent({ enrollmentId: 42 })
    vm.gradeForm.score = 88
    vm.gradeForm.comment = '表现优秀'
    vm.gradeFormRef = { validate: vi.fn(() => Promise.resolve(true)) }

    await vm.confirmGrade()
    expect(gradeApiMocks.submitGrade).toHaveBeenCalledWith({
      enrollmentId: 42,
      score: 88,
      comment: '表现优秀',
    })
    expect(vm.savingGrade).toBe(false)
  })

  // ── 提交失败后 savingGrade 复位 ──
  it('resets savingGrade after submit failure', async () => {
    gradeApiMocks.submitGrade.mockRejectedValue(new Error('Network error'))

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const vm = wrapper.vm

    vm.currentStudent = mockStudent({ enrollmentId: 42 })
    vm.gradeForm.score = 75
    vm.gradeForm.comment = ''
    vm.gradeFormRef = { validate: vi.fn(() => Promise.resolve(true)) }

    await vm.confirmGrade()
    expect(vm.savingGrade).toBe(false)
    expect(gradeApiMocks.submitGrade).toHaveBeenCalled()
  })
})
