import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// P0-D 回归测试：批量 AI 生成必须真实落库（调 batch-ai-generate 端点）+ 成功/失败统计反馈。

const apiMocks = vi.hoisted(() => ({
  getTtsOptions: vi.fn(),
  batchGeneratePptScripts: vi.fn(),
  deletePptPage: vi.fn(),
  generatePptAudio: vi.fn(),
  downloadOriginalSlide: vi.fn(),
  deleteCourseware: vi.fn(),
}))

vi.mock('@/plugins/interactive/api/queryCourseware', () => ({
  getTtsOptions: apiMocks.getTtsOptions,
  evaluateFlow: vi.fn(),
  getAudioStreamUrl: vi.fn(),
  resolveAudioToken: vi.fn(),
  getCoursewareTree: vi.fn(),
}))

vi.mock('@/plugins/interactive/api/pptCourseware', () => ({
  batchGeneratePptScripts: apiMocks.batchGeneratePptScripts,
  deletePptPage: apiMocks.deletePptPage,
  generatePptAudio: apiMocks.generatePptAudio,
}))

vi.mock('@/plugins/interactive/api/slide', () => ({
  downloadOriginalSlide: apiMocks.downloadOriginalSlide,
  deleteCourseware: apiMocks.deleteCourseware,
}))

vi.mock('@/plugins/interactive/composables/useCoursewareUpload', () => ({
  useCoursewareUpload: () => ({
    uploading: { value: false },
    uploadProgress: { value: 0 },
    renderPending: { value: false },
    handleUpload: vi.fn(),
    startRenderPolling: vi.fn(),
    stopRenderPolling: vi.fn(),
  }),
}))

// 子面板组件桩：本测试仅关注批量 AI 生成（handleBatchAI）行为（工厂内联，避免 hoist 问题）
vi.mock('@/plugins/interactive/components/PptPageEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/plugins/interactive/components/ScriptEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/plugins/interactive/components/AudioManager.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/plugins/interactive/components/PptFlowEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/plugins/interactive/components/SlidePreview.vue', () => ({ default: { template: '<div />' } }))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
    ElButton: {
      name: 'ElButton',
      props: ['disabled', 'loading', 'type', 'plain', 'size', 'icon'],
      emits: ['click'],
      template: '<button type="button" :disabled="disabled" class="el-button-stub" @click="$emit(\'click\', $event)"><slot /></button>',
    },
    ElCheckbox: {
      name: 'ElCheckbox',
      props: ['modelValue'],
      template: '<input type="checkbox" class="el-checkbox-stub" :checked="modelValue" />',
    },
    ElRadioButton: {
      name: 'ElRadioButton',
      props: ['value'],
      template: '<label class="el-radio-button-stub"><slot /></label>',
    },
    ElRadioGroup: { name: 'ElRadioGroup', template: '<div class="el-radio-group-stub"><slot /></div>' },
    ElTag: { name: 'ElTag', props: ['type', 'size', 'effect'], template: '<span class="el-tag-stub"><slot /></span>' },
    ElTabs: { name: 'ElTabs', template: '<div><slot /></div>' },
    ElTabPane: { name: 'ElTabPane', props: ['name', 'label'], template: '<div><slot /></div>' },
    ElDialog: { name: 'ElDialog', props: ['modelValue'], template: '<div><slot /></div>' },
    ElUpload: { name: 'ElUpload', template: '<div class="el-upload-stub"><slot /></div>' },
    ElPopconfirm: { name: 'ElPopconfirm', template: '<div><slot name="reference" /></div>' },
    ElEmpty: { name: 'ElEmpty', template: '<div><slot /></div>' },
    ElIcon: { name: 'ElIcon', template: '<i class="el-icon-stub"><slot /></i>' },
    ElAlert: { name: 'ElAlert', template: '<div><slot /></div>' },
    ElBreadcrumb: { name: 'ElBreadcrumb', template: '<div><slot /></div>' },
    ElBreadcrumbItem: { name: 'ElBreadcrumbItem', template: '<div><slot /></div>' },
  }
})

vi.mock('@element-plus/icons-vue', async (importOriginal) => {
  const original = await importOriginal()
  const IconStub = { template: '<i />' }
  const mocked = { default: IconStub }
  for (const key of Object.keys(original)) mocked[key] = IconStub
  return mocked
})

import { ElMessage } from 'element-plus'
import PptCoursewareManage from '@/plugins/interactive/components/PptCoursewareManage.vue'

const TWO_PAGE_TREE = {
  type: 'PPT',
  sectionId: 9,
  narrationStatus: 'PENDING',
  audioReadyCount: 0,
  pages: [
    { pageId: 1, pageNumber: 1, narrationStatus: 'PENDING' },
    { pageId: 2, pageNumber: 2, narrationStatus: 'PENDING' },
  ],
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('.el-button-stub').find(b => b.text().includes(text))
}

describe('PptCoursewareManage.vue · P0-D 批量 AI 生成真实落库', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getTtsOptions.mockResolvedValue({ data: { defaultVoice: 'female-shaonv', defaultModel: 'speech-2.8-hd' } })
  })

  it('调用 batch-ai-generate 端点，并按成功/失败统计反馈 + 刷新树', async () => {
    apiMocks.batchGeneratePptScripts.mockResolvedValue({
      // 后端契约: R<List<BatchPptScriptResult>> → res.data 即结果数组（勿再包一层 results）
      data: [
        { pageId: 1, success: true, scriptId: 11, error: null },
        { pageId: 2, success: false, scriptId: null, error: 'LLM 不可用' },
      ],
    })

    const wrapper = mount(PptCoursewareManage, {
      props: { courseId: 5, sectionId: 9, tree: TWO_PAGE_TREE },
    })
    await flushPromises()

    // 进入批量模式
    await buttonByText(wrapper, '批量操作').trigger('click')
    await flushPromises()
    expect(buttonByText(wrapper, '批量 AI 生成').exists()).toBe(true)

    // 勾选第 1、2 页（点击 checkbox 触发 toggleBatchSelect）
    const checks = wrapper.findAll('.el-checkbox-stub')
    await checks[0].trigger('click')
    await checks[1].trigger('click')

    // 执行批量 AI 生成
    await buttonByText(wrapper, '批量 AI 生成').trigger('click')
    await flushPromises()

    // P0-D 核心断言: 调新端点并传入选中的 pageIds
    expect(apiMocks.batchGeneratePptScripts).toHaveBeenCalledTimes(1)
    expect(apiMocks.batchGeneratePptScripts.mock.calls[0][0]).toBe(5)
    expect(apiMocks.batchGeneratePptScripts.mock.calls[0][1]).toEqual([1, 2])

    // UI 反馈: 成功 1 页 / 失败 1 页 + 失败页错误透传
    expect(ElMessage.warning).toHaveBeenCalledTimes(1)
    const msg = ElMessage.warning.mock.calls[0][0]
    expect(msg).toContain('成功 1 页')
    expect(msg).toContain('失败 1 页')
    expect(msg).toContain('第2页: LLM 不可用')

    // 成功后刷新课件树（activeScript 更新 → 讲述稿/音频 tab 立即可用）
    expect(wrapper.emitted('changed')).toBeTruthy()
  })

  it('全部成功时 success 提示，不出现失败统计', async () => {
    apiMocks.batchGeneratePptScripts.mockResolvedValue({
      data: [{ pageId: 1, success: true, scriptId: 11, error: null }],
    })

    const wrapper = mount(PptCoursewareManage, {
      props: { courseId: 5, sectionId: 9, tree: TWO_PAGE_TREE },
    })
    await flushPromises()

    await buttonByText(wrapper, '批量操作').trigger('click')
    await flushPromises()
    await wrapper.findAll('.el-checkbox-stub')[0].trigger('click')
    await buttonByText(wrapper, '批量 AI 生成').trigger('click')
    await flushPromises()

    expect(ElMessage.success).toHaveBeenCalledTimes(1)
    expect(ElMessage.success.mock.calls[0][0]).toContain('成功 1 页')
    expect(ElMessage.warning).not.toHaveBeenCalled()
    expect(wrapper.emitted('changed')).toBeTruthy()
  })

  it('后端整体异常时 error 反馈，不误报成功', async () => {
    apiMocks.batchGeneratePptScripts.mockRejectedValue({
      response: { data: { message: '批量生成服务不可用' } },
    })

    const wrapper = mount(PptCoursewareManage, {
      props: { courseId: 5, sectionId: 9, tree: TWO_PAGE_TREE },
    })
    await flushPromises()

    await buttonByText(wrapper, '批量操作').trigger('click')
    await flushPromises()
    await wrapper.findAll('.el-checkbox-stub')[0].trigger('click')
    await buttonByText(wrapper, '批量 AI 生成').trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalledTimes(1)
    expect(ElMessage.error.mock.calls[0][0]).toContain('批量生成服务不可用')
    expect(ElMessage.success).not.toHaveBeenCalled()
  })
})
