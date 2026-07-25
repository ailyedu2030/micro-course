/**
 * 教师可达页面 a11y 回归测试
 * ================================
 *
 * 验证 aria-label 修复: discussions label×3, questions label×1,
 * bundles label×1, profile 按钮名, CommentNode icon buttons
 *
 * 运行:
 *   npx vitest run src/__tests__/a11y-teacher-pages.test.js
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, shallowMount, flushPromises } from '@vue/test-utils'

// ============================================================
// —— DiscussionView mocks ——
// ============================================================
const discussionApiMocks = vi.hoisted(() => ({
  getPosts: vi.fn(() => Promise.resolve({ data: { items: [], totalElements: 0 } })),
  getPostById: vi.fn(() => Promise.resolve({ data: { id: 1, title: '测试帖', content: '内容', createdAt: '2026-07-25T10:00:00Z' } })),
  getComments: vi.fn(() => Promise.resolve({ data: [] })),
  createPost: vi.fn(() => Promise.resolve({ data: {} })),
  createComment: vi.fn(() => Promise.resolve({ data: {} })),
  getChapters: vi.fn(() => Promise.resolve({ data: { items: [] } })),
  getChapterById: vi.fn(() => Promise.resolve({ data: { id: 1, courseId: 1, title: '测试章节' } })),
  getCourses: vi.fn(() => Promise.resolve({ data: { items: [] } })),
}))

vi.mock('@/api/discussion', () => ({
  getPosts: discussionApiMocks.getPosts,
  getPostById: discussionApiMocks.getPostById,
  getComments: discussionApiMocks.getComments,
  createPost: discussionApiMocks.createPost,
  createComment: discussionApiMocks.createComment,
}))

vi.mock('@/api/chapter', () => ({
  getChapters: discussionApiMocks.getChapters,
  getChapterById: discussionApiMocks.getChapterById,
}))

vi.mock('@/api/course', () => ({
  getCourses: discussionApiMocks.getCourses,
}))

// ============================================================
// —— QuestionList mocks ——
// ============================================================
vi.mock('@/api/question', () => ({
  getQuestions: vi.fn(() => Promise.resolve({ data: { items: [], totalElements: 0 } })),
  createQuestion: vi.fn(() => Promise.resolve({ data: {} })),
  updateQuestion: vi.fn(() => Promise.resolve({ data: {} })),
  deleteQuestion: vi.fn(() => Promise.resolve({ data: {} })),
  batchImportQuestion: vi.fn(() => Promise.resolve({ data: { successCount: 0, failCount: 0, errors: [] } })),
  exportQuestions: vi.fn(() => { throw new Error('not implemented') }),
}))

vi.mock('@/api/course-category', () => ({
  getCategories: vi.fn(() => Promise.resolve({ data: { items: [] } })),
}))

// ============================================================
// —— BundleList mocks ——
// ============================================================
vi.mock('@/api/bundle', () => ({
  getBundles: vi.fn(() => Promise.resolve({ data: { items: [], totalElements: 0 } })),
  getBundleById: vi.fn(() => Promise.resolve({ data: { id: 1, title: '测试套件', items: [] } })),
  createBundle: vi.fn(() => Promise.resolve({ data: {} })),
  updateBundle: vi.fn(() => Promise.resolve({ data: {} })),
  publishBundle: vi.fn(() => Promise.resolve({ data: {} })),
  unpublishBundle: vi.fn(() => Promise.resolve({ data: {} })),
  addBundleCourse: vi.fn(() => Promise.resolve({ data: {} })),
  removeBundleCourse: vi.fn(() => Promise.resolve({ data: {} })),
  deleteBundle: vi.fn(() => Promise.resolve({ data: {} })),
}))

// ============================================================
// —— Profile mocks ——
// ============================================================
vi.mock('@/api/auth', () => ({
  uploadAvatar: vi.fn(() => Promise.resolve({ data: {} })),
  updateProfile: vi.fn(() => Promise.resolve({ data: {} })),
  changePassword: vi.fn(() => Promise.resolve({ data: {} })),
}))

// ============================================================
// —— Shared mocks ——
// ============================================================
vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    userInfo: {
      id: 1,
      username: 'teacher-a11y',
      realName: '测试教师',
      role: 'TEACHER',
      avatar: '/avatar.png',
      email: '',
      phone: '',
      gender: 'SECRET',
      createdAt: '2026-01-01',
    },
    role: 'TEACHER',
    userId: 1,
    getInfo: vi.fn(() => Promise.resolve()),
  }),
}))

const pushMock = vi.fn()
const replaceMock = vi.fn()
const useRouteMock = vi.fn(() => ({ params: {}, query: { chapterId: '1' } }))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: pushMock, replace: replaceMock }),
    useRoute: useRouteMock,
  }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()), alert: vi.fn() },
  }
})

vi.mock('@/utils/auth', () => ({
  removeToken: vi.fn(),
  removeRefreshToken: vi.fn(),
}))

vi.mock('@/utils/constants', () => ({
  PASSWORD_VALIDATORS: [],
}))

vi.mock('@/utils/format', () => ({
  formatDateTime: (d) => d || '',
}))

vi.mock('@/composables/useUrlPagination', () => ({
  useUrlPagination: () => ({
    bindToQuery: vi.fn(),
  }),
}))

// ============================================================
// —— CommentNode tests (mount, minimal stubs) ——
// ============================================================
describe('CommentNode.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const commentStubs = {
    'el-button': { template: '<button><slot /></button>' },
    'el-icon': { template: '<i><slot /></i>' },
    'el-tag': { template: '<span><slot /></span>' },
    'el-avatar': { template: '<span><slot /></span>' },
    'el-input': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
  }

  const makeComment = (overrides = {}) => ({
    id: 1,
    content: '测试评论',
    createdAt: '2026-07-25T10:00:00Z',
    likeCount: 3,
    ...overrides,
  })

  it('renders like button with aria-label="点赞"', async () => {
    const CommentNode = (await import('@/components/CommentNode.vue')).default
    const wrapper = mount(CommentNode, {
      props: { comment: makeComment(), depth: 0, replyLoading: false },
      global: { stubs: commentStubs },
    })
    await flushPromises()

    const btn = wrapper.find('button[aria-label="点赞"]')
    expect(btn.exists()).toBe(true)
  })

  it('renders reply button with aria-label="回复"', async () => {
    const CommentNode = (await import('@/components/CommentNode.vue')).default
    const wrapper = mount(CommentNode, {
      props: { comment: makeComment(), depth: 0, replyLoading: false },
      global: { stubs: commentStubs },
    })
    await flushPromises()

    const btn = wrapper.find('button[aria-label="回复"]')
    expect(btn.exists()).toBe(true)
  })

  it('renders collapse button with dynamic aria-label', async () => {
    const CommentNode = (await import('@/components/CommentNode.vue')).default
    const wrapper = mount(CommentNode, {
      props: {
        comment: makeComment({
          children: [{ id: 2, content: '子评论', createdAt: '2026-07-25T10:00:00Z' }],
        }),
        depth: 0,
        replyLoading: false,
      },
      global: { stubs: commentStubs },
    })
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const collapseBtn = buttons.find(
      b => b.attributes('aria-label') && /^(展开|收起)/.test(b.attributes('aria-label'))
    )
    expect(collapseBtn).toBeTruthy()
    expect(collapseBtn.attributes('aria-label')).toMatch(/^(展开|收起)/)
  })

  it('renders reply textarea with aria-label="回复内容"', async () => {
    const CommentNode = (await import('@/components/CommentNode.vue')).default
    const wrapper = mount(CommentNode, {
      props: { comment: makeComment(), depth: 0, replyLoading: false },
      global: { stubs: commentStubs },
    })
    await flushPromises()

    wrapper.vm.showReply = true
    await wrapper.vm.$nextTick()

    const replyInput = wrapper.find('input[aria-label="回复内容"]')
    expect(replyInput.exists()).toBe(true)
  })
})

// ============================================================
// —— Profile.vue (shallowMount avoids deep sub-component issues) ——
// ============================================================
describe('Profile.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders avatar upload wrapper with correct class', async () => {
    const Profile = (await import('@/views/student/Profile.vue')).default
    const wrapper = shallowMount(Profile, {
      global: {
        stubs: {
          'el-card': { template: '<div><slot /><slot name="header" /></div>' },
          'el-upload': { template: '<div class="el-upload" role="button"><slot /></div>' },
          'el-avatar': { template: '<img :alt="$attrs.alt" />' },
          'el-button': { template: '<button><slot /></button>' },
          'el-skeleton': { template: '<div />' },
          'el-result': { template: '<div><slot /><slot name="extra" /></div>' },
          'el-row': { template: '<div><slot /></div>' },
          'el-col': { template: '<div><slot /></div>' },
          UserInfoEditor: { template: '<div class="mock-editor" />' },
          PasswordEditor: { template: '<div class="mock-password" />' },
          AchievementBadges: { template: '<div class="mock-badges" />' },
          WrongQuestionsCard: { template: '<div class="mock-questions" />' },
          CertificatesCard: { template: '<div class="mock-certs" />' },
        },
      },
    })
    await flushPromises()

    const avatarUpload = wrapper.find('.avatar-uploader')
    expect(avatarUpload.exists()).toBe(true)
  })
})

// ============================================================
// —— DiscussionView.vue (shallowMount to avoid table slot issues) ——
// ============================================================
describe('DiscussionView.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders course el-select with aria-label="选择课程"', async () => {
    useRouteMock.mockReturnValue({ params: {}, query: {} })

    const DiscussionView = (await import('@/views/student/DiscussionView.vue')).default
    const wrapper = shallowMount(DiscussionView, {
      global: {
        stubs: {
          'el-select': { template: '<select :aria-label="$attrs[\'aria-label\']"><slot /></select>' },
          'el-option': { template: '<option><slot /></option>' },
          'el-card': { template: '<div><slot /><slot name="header" /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-table-column': { template: '<div />' },
          'el-pagination': { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-skeleton': { template: '<div />' },
          'el-input': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
          'el-checkbox': { template: '<label><slot /></label>' },
          'el-dialog': { template: '<div v-if="modelValue !== false"><slot /><slot name="footer" /></div>' },
          'el-link': { template: '<a><slot /></a>' },
          'el-divider': { template: '<hr />' },
          'el-avatar': { template: '<span><slot /></span>' },
          'el-tag': { template: '<span><slot /></span>' },
          CommentNode: { template: '<div class="mock-comment-node"><slot /></div>' },
        },
      },
    })
    await flushPromises()

    const selects = wrapper.findAll('select[aria-label="选择课程"]')
    expect(selects.length).toBeGreaterThanOrEqual(1)
  })

  it('renders chapter el-select with aria-label="选择章节"', async () => {
    useRouteMock.mockReturnValue({ params: {}, query: {} })

    const DiscussionView = (await import('@/views/student/DiscussionView.vue')).default
    const wrapper = shallowMount(DiscussionView, {
      global: {
        stubs: {
          'el-select': { template: '<select :aria-label="$attrs[\'aria-label\']"><slot /></select>' },
          'el-option': { template: '<option><slot /></option>' },
          'el-card': { template: '<div><slot /><slot name="header" /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-table-column': { template: '<div />' },
          'el-pagination': { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-skeleton': { template: '<div />' },
          'el-input': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
          'el-checkbox': { template: '<label><slot /></label>' },
          'el-dialog': { template: '<div v-if="modelValue !== false"><slot /><slot name="footer" /></div>' },
          'el-link': { template: '<a><slot /></a>' },
          'el-divider': { template: '<hr />' },
          'el-avatar': { template: '<span><slot /></span>' },
          'el-tag': { template: '<span><slot /></span>' },
          CommentNode: { template: '<div class="mock-comment-node"><slot /></div>' },
        },
      },
    })
    await flushPromises()

    const selects = wrapper.findAll('select[aria-label="选择章节"]')
    expect(selects.length).toBeGreaterThanOrEqual(1)
  })

  it('renders reply textarea with aria-label="回复内容"', async () => {
    const DiscussionView = (await import('@/views/student/DiscussionView.vue')).default
    const wrapper = shallowMount(DiscussionView, {
      global: {
        stubs: {
          'el-select': { template: '<select :aria-label="$attrs[\'aria-label\']"><slot /></select>' },
          'el-option': { template: '<option><slot /></option>' },
          'el-card': { template: '<div><slot /><slot name="header" /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-table-column': { template: '<div />' },
          'el-pagination': { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-skeleton': { template: '<div />' },
          'el-input': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
          'el-checkbox': { template: '<label><slot /></label>' },
          'el-dialog': { template: '<div v-if="modelValue !== false"><slot /><slot name="footer" /></div>' },
          'el-link': { template: '<a><slot /></a>' },
          'el-divider': { template: '<hr />' },
          'el-avatar': { template: '<span><slot /></span>' },
          'el-tag': { template: '<span><slot /></span>' },
          CommentNode: { template: '<div class="mock-comment-node"><slot /></div>' },
        },
      },
    })
    await flushPromises()

    // Open detail dialog
    wrapper.vm.currentPost = { id: 1, title: '测试标题', content: '测试内容', createdAt: '2026-07-25T10:00:00Z' }
    wrapper.vm.comments = []
    wrapper.vm.detailDialogVisible = true
    await wrapper.vm.$nextTick()

    const replyInput = wrapper.find('input[aria-label="回复内容"]')
    expect(replyInput.exists()).toBe(true)
  })
})

// ============================================================
// —— QuestionList.vue (shallowMount) ——
// ============================================================
describe('QuestionList.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders course el-select with aria-label="选择课程"', async () => {
    const QuestionList = (await import('@/views/courses/QuestionList.vue')).default
    const wrapper = shallowMount(QuestionList, {
      global: {
        stubs: {
          'el-select': { template: '<select :aria-label="$attrs[\'aria-label\']"><slot /></select>' },
          'el-option': { template: '<option><slot /></option>' },
          'el-card': { template: '<div><slot /><slot name="header" /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-table-column': { template: '<div />' },
          'el-pagination': { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-skeleton': { template: '<div />' },
          'el-input': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input-number': { template: '<input /><slot /></input>' },
          'el-radio-group': { template: '<div><slot /></div>' },
          'el-radio': { template: '<label><slot /></label>' },
          'el-checkbox': { template: '<label><slot /></label>' },
          'el-checkbox-group': { template: '<div><slot /></div>' },
          'el-switch': { template: '<div><slot /></div>' },
          'el-dialog': { template: '<div v-if="modelValue !== false"><slot /><slot name="footer" /></div>' },
          'el-breadcrumb': { template: '<div><slot /></div>' },
          'el-breadcrumb-item': { template: '<span><slot /></span>' },
          'el-upload': { template: '<div><slot /></div>' },
          QuestionPreview: { template: '<div class="mock-preview" />' },
        },
      },
    })
    await flushPromises()

    const courseSelect = wrapper.find('select[aria-label="选择课程"]')
    expect(courseSelect.exists()).toBe(true)
  })
})

// ============================================================
// —— BundleList.vue (shallowMount) ——
// ============================================================
describe('BundleList.vue accessibility', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders add-course el-select with aria-label="搜索并添加课程"', async () => {
    const BundleList = (await import('@/views/courses/BundleList.vue')).default
    const wrapper = shallowMount(BundleList, {
      global: {
        stubs: {
          'el-select': { template: '<select :aria-label="$attrs[\'aria-label\']"><slot /></select>' },
          'el-option': { template: '<option><slot /></option>' },
          'el-card': { template: '<div><slot /><slot name="header" /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-table-column': { template: '<div />' },
          'el-pagination': { template: '<div />' },
          'el-empty': { template: '<div />' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-input': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
          'el-input-number': { template: '<input :aria-label="$attrs[\'aria-label\']" /><slot /></input>' },
          'el-checkbox': { template: '<label><slot /></label>' },
          'el-form': { template: '<form><slot /></form>' },
          'el-form-item': { template: '<div><slot /></div>' },
          'el-dialog': { template: '<div v-if="modelValue !== false"><slot /><slot name="footer" /></div>' },
          'el-page-header': { template: '<div><slot /></div>' },
        },
      },
    })
    await flushPromises()

    // Open the sub-course dialog
    wrapper.vm.bundles = [{ id: 1, title: '测试套件', price: 0, status: 0, creatorId: 1 }]
    wrapper.vm.loading = false
    wrapper.vm.currentBundle = { id: 1, title: '测试套件' }
    wrapper.vm.bundleItems = []
    wrapper.vm.availableCourses = [{ id: 10, title: '测试课程' }]
    wrapper.vm.itemDialog = true
    await wrapper.vm.$nextTick()

    const courseSelect = wrapper.find('select[aria-label="搜索并添加课程"]')
    expect(courseSelect.exists()).toBe(true)
  })
})
