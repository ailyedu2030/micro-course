import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// Mock the slide API
vi.mock('@/plugins/interactive/api/slide', () => ({
  getSlidePages: vi.fn(() => Promise.resolve({ data: [] })),
}))

// Mock auth image utility
vi.mock('@/utils/authImage', () => ({
  loadAuthResource: vi.fn(() => Promise.resolve(null)),
  clearImageCache: vi.fn(),
}))

// Mock learning progress API
vi.mock('@/api/learning-progress', () => ({
  getLearningProgress: vi.fn(() => Promise.resolve({ data: [] })),
  createLearningProgress: vi.fn(() => Promise.resolve({ data: { id: 1 } })),
  updateLearningProgress: vi.fn(() => Promise.resolve({ data: {} })),
}))

// Mock vue-router
vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { courseId: '100' },
    query: { chapterId: '42', lessonId: '5' },
  }),
}))

// Mock element-plus
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  const MockComp = { template: '<span><slot /></span>', props: { size: { type: [Number, String], default: undefined } } }
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn(), alert: vi.fn() },
    ElIcon: MockComp,
    ElButton: MockComp,
    ElUpload: { template: '<div><slot /></div>' },
    ElInput: { template: '<textarea><slot /></textarea>' },
    ElCheckbox: { template: '<span><slot /></span>' },
    ElTag: { template: '<span><slot /></span>' },
    ElProgress: { template: '<div><slot /></div>' },
    ElEmpty: { template: '<div><slot /></div>' },
    ElDialog: { template: '<div><slot /></div>' },
    ElBreadcrumb: { template: '<div><slot /></div>' },
    ElBreadcrumbItem: { template: '<span><slot /></span>' },
    ElDropdown: { template: '<div><slot /></div>' },
    ElDropdownMenu: { template: '<div><slot /></div>' },
    ElDropdownItem: { template: '<div><slot /></div>' },
    ElResult: { template: '<div><slot /></div>' },
  }
})

// Vue components from element-plus are resolved by unplugin-vue-components
// We need to provide stubs for them in tests
const elementPlusStubs = {
  'el-icon': { template: '<i><slot /></i>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-upload': { template: '<div><slot /></div>' },
  'el-progress': { template: '<div><slot /></div>' },
  'el-empty': { template: '<div><slot /></div>' },
  'el-dialog': { template: '<div><slot /></div>' },
  'el-breadcrumb': { template: '<div><slot /></div>' },
  'el-breadcrumb-item': { template: '<span><slot /></span>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-dropdown': { template: '<div><slot /></div>' },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { template: '<div><slot /></div>' },
  'el-input': { template: '<textarea><slot /></textarea>' },
  'el-checkbox': { template: '<span><slot /></span>' },
  'el-result': { template: '<div><slot /></div>' },
}

// We'll import and test the component after mocking
import SlidePlayer from '@/views/student/SlidePlayer.vue'

describe('SlidePlayer.vue iframe branch', () => {
  // 3.1.1 (tasks.md 3.5.1): contentType='HTML_DIRECT' → render iframe
  it('renders iframe when current page has contentType=HTML_DIRECT', async () => {
    const wrapper = mount(SlidePlayer, {
      global: {
        stubs: {
          ...elementPlusStubs,
          'router-link': { template: '<a><slot /></a>' },
          transition: { template: '<div><slot /></div>' },
          'transition-group': { template: '<div><slot /></div>' },
        },
      },
    })

    // Simulate pages loaded with HTML content
    await wrapper.vm.$nextTick()
    wrapper.vm.pages = [
      {
        pageNumber: 1,
        contentType: 'HTML_DIRECT',
        htmlContent: '<h1>Hello Test</h1>',
        narrationScript: 'Test narration',
      },
    ]
    wrapper.vm.current = 0
    wrapper.vm.pageLoading = false
    await wrapper.vm.$nextTick()

    // The iframe should render
    const iframe = wrapper.find('iframe')
    expect(iframe.exists()).toBe(true)
    // sandbox allow-scripts（无 allow-same-origin）：浏览器原生隔离
    // 安全论证见 docs/HTML课件播放能力增强要求.md
    expect(iframe.attributes('sandbox')).toBe('allow-scripts')
    expect(iframe.attributes('srcdoc')).toBe('<h1>Hello Test</h1>')
  })

  // 3.1.1 (reverse): content_type=PPT_RENDERED → render img (not iframe)
  it('renders image when content_type is PPT_RENDERED (not HTML_DIRECT)', async () => {
    const wrapper = mount(SlidePlayer, {
      global: {
        stubs: {
          ...elementPlusStubs,
          'router-link': { template: '<a><slot /></a>' },
          transition: { template: '<div><slot /></div>' },
        },
      },
    })

    await wrapper.vm.$nextTick()
    wrapper.vm.pages = [
      {
        pageNumber: 1,
        contentType: 'PPT_RENDERED',
        narrationScript: null,
      },
    ]
    wrapper.vm.current = 0
    wrapper.vm.pageLoading = false
    await wrapper.vm.$nextTick()

    // Should not render iframe
    const iframe = wrapper.find('iframe')
    expect(iframe.exists()).toBe(false)
  })

  // 3.5.2: HTML 课时自动播放不受影响
  it('HTML_DIRECT pages still trigger audio ended → goTo(next)', async () => {
    const wrapper = mount(SlidePlayer, {
      global: {
        stubs: {
          ...elementPlusStubs,
          'router-link': { template: '<a><slot /></a>' },
          transition: { template: '<div><slot /></div>' },
        },
      },
    })

    await wrapper.vm.$nextTick()
    wrapper.vm.pages = [
      {
        pageNumber: 1,
        contentType: 'HTML_DIRECT',
        htmlContent: '<h1>Page 1</h1>',
        narrationAudioUrl: '/audio/1',
      },
      {
        pageNumber: 2,
        contentType: 'HTML_DIRECT',
        htmlContent: '<h1>Page 2</h1>',
        narrationAudioUrl: '/audio/2',
      },
    ]
    wrapper.vm.current = 0
    wrapper.vm.pageLoading = false
    wrapper.vm.autoMode = true
    await wrapper.vm.$nextTick()

    // Simulate audio ended — should trigger goTo(1)
    wrapper.vm.onAudioEnded()
    await wrapper.vm.$nextTick()
    await nextTick()

    expect(wrapper.vm.current).toBe(1)
  })
})
