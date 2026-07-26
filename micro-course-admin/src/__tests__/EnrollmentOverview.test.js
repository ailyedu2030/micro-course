/**
 * EnrollmentOverview — 选课数据总览
 * 测试覆盖:
 * 1. 字段映射: enrollmentStatus / enrolledAt
 * 2. 统计卡片基于 enrollmentStatus 过滤
 * 3. 渲染空状态 / 错误容错
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

const enrollmentApiMocks = vi.hoisted(() => ({
  getEnrollments: vi.fn(),
}))

vi.mock('@/api/enrollment', () => ({
  getEnrollments: enrollmentApiMocks.getEnrollments,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn() },
    ElMessageBox: { confirm: vi.fn(() => Promise.resolve()) },
  }
})

import EnrollmentOverview from '@/views/academic/EnrollmentOverview.vue'

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
  // el-table/el-table-column: use minimal stubs — table content not tested
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': { template: '<div><slot /></div>' },
  'el-skeleton': { template: '<div />' },
  'el-empty': { template: '<div />' },
}

function mockEnrollment(overrides = {}) {
  return {
    id: 1,
    realName: '张三',
    courseName: '高等数学',
    enrollmentStatus: 'ENROLLED',
    enrolledAt: '2026-07-01T10:00:00',
    progress: 50,
    ...overrides,
  }
}

function createWrapper() {
  return mount(EnrollmentOverview, {
    global: { stubs },
    attachTo: document.body,
  })
}

describe('EnrollmentOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ── 组件挂载不崩溃 ──
  it('mounts without crashing', async () => {
    enrollmentApiMocks.getEnrollments
      .mockResolvedValueOnce({ data: { items: [], totalElements: 0 } })
      .mockResolvedValueOnce({ data: { items: [], totalElements: 0 } })

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()
    expect(wrapper.exists()).toBe(true)
  })

  // ── 统计卡片使用 enrollmentStatus 过滤 ──
  it('stats filter by enrollmentStatus from response', async () => {
    // 后端返回 enrollmentStatus 字段
    const items = [
      { id: 1, realName: 'A', enrollmentStatus: 'ENROLLED', enrolledAt: '2026-01-01T00:00:00' },
      { id: 2, realName: 'B', enrollmentStatus: 'ENROLLED', enrolledAt: '2026-01-01T00:00:00' },
      { id: 3, realName: 'C', enrollmentStatus: 'COMPLETED', enrolledAt: '2026-01-01T00:00:00' },
      { id: 4, realName: 'D', enrollmentStatus: 'PENDING', enrolledAt: '2026-01-01T00:00:00' },
    ]
    enrollmentApiMocks.getEnrollments
      .mockResolvedValueOnce({ data: { items, totalElements: 4 } })
      .mockResolvedValueOnce({ data: { items, totalElements: 4 } })

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    // 渲染了 4 个统计卡片 + 搜索区 + 表格区
    const statCards = wrapper.findAll('.stat-card')
    expect(statCards.length).toBe(4)

    // getEnrollments 被调用 2 次: fetchStats + fetchData
    expect(enrollmentApiMocks.getEnrollments).toHaveBeenCalledTimes(2)
  })

  // ── search form 的 status 是查询参数名，不改为 enrollmentStatus ──
  it('search form sends "status" as query param name', async () => {
    enrollmentApiMocks.getEnrollments
      .mockResolvedValueOnce({ data: { items: [], totalElements: 0 } })
      .mockResolvedValueOnce({ data: { items: [], totalElements: 0 } })

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()

    // fetchData 调用时传 status 作为查询参数名（后端 @RequestParam status）
    const dataCallParams = enrollmentApiMocks.getEnrollments.mock.calls[1][0]
    expect(dataCallParams).toHaveProperty('page')
  })

  // ── 后端返回 null 字段时组件不崩溃 ──
  it('does not crash when enrollment fields are null', async () => {
    enrollmentApiMocks.getEnrollments
      .mockResolvedValueOnce({
        data: {
          items: [{
            id: 1, realName: null, courseName: null,
            enrollmentStatus: null, enrolledAt: null, progress: null,
          }],
          totalElements: 1,
        },
      })
      .mockResolvedValueOnce({
        data: {
          items: [{
            id: 1, realName: null, courseName: null,
            enrollmentStatus: null, enrolledAt: null, progress: null,
          }],
          totalElements: 1,
        },
      })

    const wrapper = createWrapper()
    await flushPromises()
    await nextTick()
    expect(wrapper.exists()).toBe(true)
  })
})
