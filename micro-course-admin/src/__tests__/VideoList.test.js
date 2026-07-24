/* eslint-disable vue/one-component-per-file */
import { defineComponent } from 'vue'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const routeState = vi.hoisted(() => ({
  params: { courseId: '12' },
  query: {},
}))

const videoApiMocks = vi.hoisted(() => ({
  getVideos: vi.fn(() => Promise.resolve({ data: { items: [], totalElements: 0 } })),
  createVideo: vi.fn(),
  updateVideo: vi.fn(),
  deleteVideo: vi.fn(),
  uploadVideoCover: vi.fn(),
  uploadVideo: vi.fn(() => Promise.resolve({ data: { id: 1 } })),
  retryVideoTranscode: vi.fn(),
}))

const courseApiMocks = vi.hoisted(() => ({
  getCourses: vi.fn(() => Promise.resolve({ data: { items: [{ id: 12, title: '批量上传课程' }] } })),
  getCourseById: vi.fn(() => Promise.resolve({ data: { id: 12, title: '批量上传课程' } })),
}))

const chapterApiMocks = vi.hoisted(() => ({
  getChapters: vi.fn(() => Promise.resolve({ data: { items: [{ id: 8, title: '第一章' }] } })),
  getChapterById: vi.fn(() => Promise.resolve({ data: { id: 8, title: '第一章' } })),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => routeState,
  }
})

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    role: 'TEACHER',
    userId: 3,
  }),
}))

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'token'),
}))

vi.mock('@/api/video', () => ({
  getVideos: videoApiMocks.getVideos,
  createVideo: videoApiMocks.createVideo,
  updateVideo: videoApiMocks.updateVideo,
  deleteVideo: videoApiMocks.deleteVideo,
  uploadVideoCover: videoApiMocks.uploadVideoCover,
  uploadVideo: videoApiMocks.uploadVideo,
  retryVideoTranscode: videoApiMocks.retryVideoTranscode,
}))

vi.mock('@/api/course', () => ({
  getCourses: courseApiMocks.getCourses,
  getCourseById: courseApiMocks.getCourseById,
}))

vi.mock('@/api/chapter', () => ({
  getChapters: chapterApiMocks.getChapters,
  getChapterById: chapterApiMocks.getChapterById,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn(), alert: vi.fn() },
  }
})

import VideoList from '@/views/courses/VideoList.vue'

const UploadStub = defineComponent({
  name: 'ElUploadStub',
  props: {
    onChange: Function,
    onRemove: Function,
    onExceed: Function,
    accept: String,
    multiple: Boolean,
    limit: Number,
    drag: Boolean,
    autoUpload: Boolean,
  },
  template: '<div class="el-upload-stub"><slot /><slot name="tip" /></div>',
})

const FormStub = defineComponent({
  name: 'ElFormStub',
  props: {
    model: Object,
    rules: Object,
  },
  setup(props, { expose }) {
    expose({
      validate: async (callback) => {
        const valid = Object.entries(props.rules || {}).every(([field, fieldRules]) => {
          return !(fieldRules || []).some((rule) => {
            if (!rule.required) {
              return false
            }
            const value = props.model?.[field]
            return value === '' || value === null || value === undefined
          })
        })

        callback(valid)
      },
      resetFields: vi.fn(),
    })
    return {}
  },
  template: '<form><slot /></form>',
})

const DialogStub = defineComponent({
  name: 'ElDialogStub',
  props: {
    modelValue: Boolean,
    title: String,
  },
  template: '<div v-if="modelValue" class="el-dialog-stub"><slot /><slot name="footer" /></div>',
})

const stubs = {
  'el-card': { template: '<div><slot name="header" /><slot /></div>' },
  'el-button': { emits: ['click'], props: { disabled: Boolean }, template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>' },
  'el-form': FormStub,
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-table': { template: '<div><slot /><slot name="empty" /></div>' },
  'el-table-column': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-dialog': DialogStub,
  'el-upload': UploadStub,
  'el-image': { template: '<img />' },
  'el-progress': { props: ['percentage'], template: '<div class="progress-stub">{{ percentage }}</div>' },
  'el-empty': { props: ['description'], template: '<div class="empty-stub">{{ description }}</div>' },
  'el-link': { template: '<a><slot /></a>' },
  'el-breadcrumb': { template: '<nav><slot /></nav>' },
  'el-breadcrumb-item': { template: '<span><slot /></span>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-input': { props: { modelValue: String, disabled: Boolean, placeholder: String }, template: '<input :value="modelValue" :disabled="disabled" :placeholder="placeholder" />' },
  'el-input-number': { props: { modelValue: Number, disabled: Boolean }, template: '<input type="number" :value="modelValue" :disabled="disabled" />' },
  'router-link': { template: '<a><slot /></a>' },
}

describe('VideoList.vue batch upload queue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.params = { courseId: '12' }
    routeState.query = {}
  })

  it('shows a queue summary after multiple files are selected in the create dialog', async () => {
    const wrapper = mount(VideoList, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
      },
    })

    await flushPromises()

    const createButton = wrapper.findAll('button').find((node) => node.text().includes('新增视频'))
    expect(createButton).toBeTruthy()

    await createButton.trigger('click')
    await flushPromises()

    const upload = wrapper.findComponent(UploadStub)
    expect(upload.exists()).toBe(true)

    const files = [
      { name: '第一讲.mp4', raw: { name: '第一讲.mp4' } },
      { name: '第二讲.mp4', raw: { name: '第二讲.mp4' } },
    ]

    await upload.props('onChange')(files[0], files)
    await flushPromises()

    expect(wrapper.text()).toContain('第一讲.mp4')
    expect(wrapper.text()).toContain('第二讲.mp4')
    expect(wrapper.text()).toContain('2')
  })

  it('restores the remaining file title after removing extra files from a batch', async () => {
    const wrapper = mount(VideoList, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
      },
    })

    await flushPromises()

    const createButton = wrapper.findAll('button').find((node) => node.text().includes('新增视频'))
    await createButton.trigger('click')
    await flushPromises()

    const upload = wrapper.findComponent(UploadStub)
    const files = [
      { name: '第一讲.mp4', raw: { name: '第一讲.mp4' } },
      { name: '第二讲.mp4', raw: { name: '第二讲.mp4' } },
    ]

    await upload.props('onChange')(files[0], files)
    await flushPromises()

    const titleInput = wrapper.find('input[placeholder]')
    expect(titleInput.attributes('disabled')).toBeDefined()
    expect(titleInput.element.value).toBe('')

    await upload.props('onRemove')(files[1], [files[0]])
    await flushPromises()

    expect(wrapper.text()).toContain('第一讲.mp4')
    expect(wrapper.text()).not.toContain('第二讲.mp4')
    expect(titleInput.attributes('disabled')).toBeUndefined()
    expect(titleInput.element.value).toBe('第一讲')
  })

  it('allows batch upload submission without requiring a manual title on the generic route', async () => {
    routeState.params = {}

    const wrapper = mount(VideoList, {
      global: {
        stubs,
        directives: {
          loading: () => {},
        },
      },
    })

    await flushPromises()

    const createButton = wrapper.findAll('button').find((node) => node.text().includes('新增视频'))
    await createButton.trigger('click')
    await flushPromises()

    wrapper.vm.formData.courseId = 12
    wrapper.vm.formData.chapterId = 8

    const upload = wrapper.findComponent(UploadStub)
    const files = [
      { name: '第一讲.mp4', raw: { name: '第一讲.mp4' } },
      { name: '第二讲.mp4', raw: { name: '第二讲.mp4' } },
    ]

    await upload.props('onChange')(files[0], files)
    await flushPromises()

    const confirmButton = wrapper.findAll('button').find((node) => node.text().includes('确定'))
    await confirmButton.trigger('click')
    await flushPromises()

    expect(videoApiMocks.uploadVideo).toHaveBeenCalledTimes(2)
  })
})
