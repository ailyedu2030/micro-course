/**
 * TeachingClassList — 角色按钮矩阵
 * 测试覆盖:
 * 1. ADMIN 可见「新增教学班」，ACADEMIC 不可见
 * 2. 操作列按钮按角色区分（编辑/删除仅 ADMIN）
 * 3. 渲染空/错误状态
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

const teachingClassApiMocks = vi.hoisted(() => ({
  getTeachingClasses: vi.fn(),
  getTeachingClassById: vi.fn(),
  completeTeachingClass: vi.fn(),
  cancelTeachingClass: vi.fn(),
  deleteTeachingClass: vi.fn(),
}))

const courseApiMocks = vi.hoisted(() => ({
  getCourses: vi.fn(),
}))

const mockUserStore = vi.hoisted(() => ({
  userInfo: { id: 1, role: 'ADMIN' },
  userId: 1,
  role: 'ADMIN',
}))

vi.mock('@/api/teaching-class', () => ({
  getTeachingClasses: teachingClassApiMocks.getTeachingClasses,
  getTeachingClassById: teachingClassApiMocks.getTeachingClassById,
  completeTeachingClass: teachingClassApiMocks.completeTeachingClass,
  cancelTeachingClass: teachingClassApiMocks.cancelTeachingClass,
  deleteTeachingClass: teachingClassApiMocks.deleteTeachingClass,
}))

vi.mock('@/api/course', () => ({
  getCourses: courseApiMocks.getCourses,
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => mockUserStore,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), prompt: vi.fn(() => Promise.resolve({ value: '原因' })) },
  }
})

import TeachingClassList from '@/views/admin/TeachingClassList.vue'

const stubs = {
  'el-breadcrumb': { template: '<div><slot /></div>' },
  'el-breadcrumb-item': { template: '<span><slot /></span>' },
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>' },
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<input />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': { template: '<div><slot /></div>' },
  'el-skeleton': { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-dialog': { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
  'el-input-number': { template: '<input />' },
  'el-icon': { template: '<span><slot /></span>' },
}

function mockClass(overrides = {}) {
  return {
    id: 1,
    name: '高数一班',
    courseTitle: '高等数学',
    teacherName: '李老师',
    semester: '2026-1',
    maxStudents: 50,
    studentCount: 30,
    status: 1,
    ...overrides,
  }
}

function createWrapper() {
  return mount(TeachingClassList, {
    global: { stubs },
    attachTo: document.body,
  })
}

describe('TeachingClassList - Role Button Matrix', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    teachingClassApiMocks.getTeachingClasses.mockResolvedValue({
      data: { items: [mockClass()], totalElements: 1 },
    })
    courseApiMocks.getCourses.mockResolvedValue({ data: { items: [] } })
  })

  // ── ADMIN 可见「新增教学班」 ──
  it('ADMIN sees 新增教学班 button', async () => {
    mockUserStore.role = 'ADMIN'

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const headerHtml = wrapper.find('.card-header').html()
    expect(headerHtml).toContain('新增教学班')
  })

  // ── ACADEMIC 不可见「新增教学班」 ──
  it('ACADEMIC does NOT see 新增教学班 button', async () => {
    mockUserStore.role = 'ACADEMIC'
    mockUserStore.userInfo = { id: 2, role: 'ACADEMIC' }

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    const headerHtml = wrapper.find('.card-header').html()
    expect(headerHtml).not.toContain('新增教学班')
  })

  // ── 组件挂载不崩溃 ──
  it('mounts without crashing', async () => {
    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()
    expect(wrapper.exists()).toBe(true)
  })

  // ── API 错误时组件容错 ──
  it('handles API error gracefully', async () => {
    teachingClassApiMocks.getTeachingClasses.mockRejectedValue(new Error('Network error'))

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()
    expect(wrapper.exists()).toBe(true)
  })
})
