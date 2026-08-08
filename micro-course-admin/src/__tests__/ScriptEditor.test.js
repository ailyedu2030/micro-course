import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// P0-A 回归测试：新上传 PPT 页（无 active script）也能输入 + AI 生成 + 保存创建 v1。

// API mocks —— 以别名路径 mock，与组件内相对导入解析到同一模块
const apiMocks = vi.hoisted(() => ({
  getActivePptScript: vi.fn(),
  listPptScriptHistory: vi.fn(),
  savePptScript: vi.fn(),
  getActiveHtmlSegment: vi.fn(),
  saveHtmlSegmentScript: vi.fn(),
  generatePptScriptAi: vi.fn(),
  generateHtmlSegmentScriptAi: vi.fn(),
}))

vi.mock('@/plugins/interactive/api/pptCourseware', () => ({
  getActivePptScript: apiMocks.getActivePptScript,
  listPptScriptHistory: apiMocks.listPptScriptHistory,
  savePptScript: apiMocks.savePptScript,
}))

vi.mock('@/plugins/interactive/api/htmlCourseware', () => ({
  getActiveHtmlSegment: apiMocks.getActiveHtmlSegment,
  saveHtmlSegmentScript: apiMocks.saveHtmlSegmentScript,
}))

vi.mock('@/plugins/interactive/api/queryCourseware', () => ({
  generatePptScriptAi: apiMocks.generatePptScriptAi,
  generateHtmlSegmentScriptAi: apiMocks.generateHtmlSegmentScriptAi,
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ userInfo: { id: 7 }, userId: 7 }),
}))

// Element Plus 桩：ElButton/ElInput 渲染真实原生元素便于触发与断言 disabled
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
    ElInput: {
      name: 'ElInput',
      props: ['modelValue', 'disabled', 'type', 'rows', 'placeholder'],
      emits: ['update:modelValue', 'input'],
      template: '<textarea :disabled="disabled" :placeholder="placeholder" :rows="rows" :value="modelValue" class="el-input-stub" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    },
    ElTag: { name: 'ElTag', template: '<span class="el-tag-stub"><slot /></span>' },
    ElAlert: { name: 'ElAlert', props: ['title', 'type', 'closable'], template: '<div class="el-alert-stub"><slot /></div>' },
    ElDropdown: { name: 'ElDropdown', template: '<div class="el-dropdown-stub"><slot /></div>' },
    ElDropdownMenu: { name: 'ElDropdownMenu', template: '<div><slot /></div>' },
    ElDropdownItem: { name: 'ElDropdownItem', template: '<div><slot /></div>' },
    ElIcon: { name: 'ElIcon', template: '<i class="el-icon-stub"><slot /></i>' },
  }
})

vi.mock('@element-plus/icons-vue', async (importOriginal) => {
  const original = await importOriginal()
  const IconStub = { template: '<i />' }
  const mocked = { default: IconStub }
  for (const key of Object.keys(original)) mocked[key] = IconStub
  return mocked
})

import ScriptEditor from '@/plugins/interactive/components/ScriptEditor.vue'

function mountPpt(props = {}) {
  return mount(ScriptEditor, {
    props: {
      courseId: 1,
      pageType: 'PPT',
      pageId: 42,
      currentScriptId: null,
      ...props,
    },
  })
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('.el-button-stub').find(b => b.text().includes(text))
}

async function typeScript(wrapper, text) {
  const ta = wrapper.find('textarea')
  await ta.setValue(text)
  return ta
}

describe('ScriptEditor.vue · P0-A 新 PPT 页讲述稿管线', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getActivePptScript.mockResolvedValue({ data: null })
    apiMocks.listPptScriptHistory.mockResolvedValue({ data: [] })
    apiMocks.savePptScript.mockResolvedValue({ data: 100 })
    apiMocks.generatePptScriptAi.mockResolvedValue({ data: { scriptText: 'AI 生成的讲述稿' } })
    apiMocks.getActiveHtmlSegment.mockResolvedValue({ data: null })
  })

  it('新 PPT 页（无 currentScriptId）：textarea 可输入、保存按钮可点、保存走创建 v1', async () => {
    const wrapper = mountPpt()
    await flushPromises()

    // P0-A 核心断言 1: textarea 不再被永久 disabled
    expect(wrapper.find('textarea').attributes('disabled')).toBeUndefined()

    // 初始空稿 → 保存按钮 disabled
    expect(buttonByText(wrapper, '保存').attributes('disabled')).toBeDefined()

    // 输入内容后保存按钮可用
    await typeScript(wrapper, '这是新 PPT 的第一份讲述稿')
    await flushPromises()
    expect(buttonByText(wrapper, '保存').attributes('disabled')).toBeUndefined()

    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    // 保存调用 savePptScript（无 scriptId → 后端自动创建 v1）
    expect(apiMocks.savePptScript).toHaveBeenCalledTimes(1)
    const [courseId, pageId, payload] = apiMocks.savePptScript.mock.calls[0]
    expect(courseId).toBe(1)
    expect(pageId).toBe(42)
    expect(payload.scriptText).toBe('这是新 PPT 的第一份讲述稿')
    expect(payload.createdBy).toBe(7) // 审计追踪: 从用户 store 取真实用户
  })

  it('AI 生成 → 应用预览 → 保存成功（真实返回 + 落库）', async () => {
    const wrapper = mountPpt()
    await flushPromises()

    await buttonByText(wrapper, 'AI 生成讲述稿').trigger('click')
    await flushPromises()

    // 预览出现
    const preview = wrapper.find('.se-ai-preview')
    expect(preview.exists()).toBe(true)
    expect(preview.text()).toContain('AI 生成的讲述稿')

    // 应用预览 → 内容写入 textarea
    await buttonByText(wrapper, '应用此版本').trigger('click')
    await flushPromises()
    expect(wrapper.find('textarea').element.value).toBe('AI 生成的讲述稿')

    // 保存
    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()
    expect(apiMocks.savePptScript).toHaveBeenCalledTimes(1)
    expect(apiMocks.savePptScript.mock.calls[0][2].scriptText).toBe('AI 生成的讲述稿')
  })

  it('旧 PPT 页（有 currentScriptId）：加载 active 脚本并可更新保存', async () => {
    apiMocks.getActivePptScript.mockResolvedValue({
      data: { id: 5, scriptText: '旧版讲述稿', scriptVersion: 2, voice: 'v1', ttsModel: 'm1', isActive: true },
    })
    apiMocks.listPptScriptHistory.mockResolvedValue({
      data: [{ id: 5, scriptText: '旧版讲述稿', scriptVersion: 2, voice: 'v1', ttsModel: 'm1', isActive: true }],
    })

    const wrapper = mountPpt({ currentScriptId: 5 })
    await flushPromises()

    // 加载 active 脚本内容
    expect(wrapper.find('textarea').element.value).toBe('旧版讲述稿')

    // 修改后保存 → 更新（携带旧 voice/ttsModel 保持音色设置）
    await typeScript(wrapper, '更新后的讲述稿')
    await buttonByText(wrapper, '保存').trigger('click')
    await flushPromises()

    expect(apiMocks.savePptScript).toHaveBeenCalledTimes(1)
    const payload = apiMocks.savePptScript.mock.calls[0][2]
    expect(payload.scriptText).toBe('更新后的讲述稿')
    expect(payload.voice).toBe('v1')
    expect(payload.ttsModel).toBe('m1')
  })
})
