import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/exercise', () => ({
  getExercises: vi.fn(() => Promise.resolve({ data: [] })),
  getExerciseById: vi.fn(() => Promise.resolve({ data: {} })),
  submitExerciseRecord: vi.fn(() => Promise.resolve({ data: {} })),
}))

vi.mock('@/api/question', () => ({
  getQuestionById: vi.fn(() => Promise.resolve({ data: {} })),
}))

vi.mock('@/api/exercise-record', () => ({
  getMyAttemptCount: vi.fn(() => Promise.resolve({ data: 0 })),
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    userInfo: { id: 1, realName: '测试学员' },
  }),
}))

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ params: { chapterId: '88' }, query: {} }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(), alert: vi.fn() },
  }
})

import ExerciseTake from '@/views/student/ExerciseTake.vue'

const stubs = {
  teleport: true,
  transition: false,
  'el-card': { template: '<div><slot /><slot name="header" /></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-progress': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio': { template: '<label><slot /></label>' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { template: '<label><slot /></label>' },
  'el-input': { template: '<textarea><slot /></textarea>' },
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-skeleton': { template: '<div />' },
  'el-empty': { template: '<div />' },
  'el-icon': { template: '<i><slot /></i>' },
}

describe('ExerciseTake.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the mobile answer sheet trigger as a button', async () => {
    const wrapper = mount(ExerciseTake, {
      global: {
        stubs,
      },
    })

    await flushPromises()
    wrapper.vm.exerciseStarted = true
    wrapper.vm.isMobile = true
    await wrapper.vm.$nextTick()

    expect(wrapper.find('button.answer-sheet-fab').exists()).toBe(true)
  })

  it('closes the answer sheet with Escape for keyboard users', async () => {
    const wrapper = mount(ExerciseTake, {
      attachTo: document.body,
      global: {
        stubs,
      },
    })

    await flushPromises()
    wrapper.vm.exerciseStarted = true
    wrapper.vm.isMobile = true
    wrapper.vm.questions = [{ id: 1 }]
    wrapper.vm.questionIds = [1]
    wrapper.vm.sheetVisible = true
    await wrapper.vm.$nextTick()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.sheetVisible).toBe(false)
    wrapper.unmount()
  })
})
