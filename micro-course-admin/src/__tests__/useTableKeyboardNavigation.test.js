import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { useTableKeyboardNavigation } from '@/composables/useTableKeyboardNavigation'

function createHarness(onActivate) {
  return defineComponent({
    setup() {
      const tableRoot = ref(null)
      const rows = ref([
        { id: 1, label: '第一行' },
        { id: 2, label: '第二行' }
      ])
      const { refreshTableKeyboard } = useTableKeyboardNavigation({
        tableRef: tableRoot,
        tableData: rows,
        onActivate,
        getAriaLabel: (row) => `选择 ${row?.label || ''}`
      })

      const replaceRows = async (nextRows) => {
        rows.value = nextRows
        await nextTick()
        await nextTick()
      }

      return {
        tableRoot,
        rows,
        replaceRows,
        refreshTableKeyboard
      }
    },
    template: `
      <div ref="tableRoot">
        <table>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>{{ row.label }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    `
  })
}

describe('useTableKeyboardNavigation', () => {
  it('adds focus semantics and activates rows with Enter/Space', async () => {
    const onActivate = vi.fn()
    const wrapper = mount(createHarness(onActivate))

    await wrapper.vm.refreshTableKeyboard()
    await nextTick()

    const rows = wrapper.findAll('tr')
    expect(rows[0].attributes('tabindex')).toBe('0')
    expect(rows[0].attributes('role')).toBe('button')
    expect(rows[0].attributes('aria-label')).toBe('选择 第一行')

    await rows[1].trigger('keydown', { key: 'Enter' })
    await rows[0].trigger('keydown', { key: ' ' })

    expect(onActivate).toHaveBeenNthCalledWith(1, { id: 2, label: '第二行' })
    expect(onActivate).toHaveBeenNthCalledWith(2, { id: 1, label: '第一行' })
  })

  it('rebinds row metadata after data changes and removes listeners on unmount', async () => {
    const onActivate = vi.fn()
    const wrapper = mount(createHarness(onActivate))

    await wrapper.vm.refreshTableKeyboard()
    await wrapper.vm.replaceRows([{ id: 3, label: '新行' }])

    const row = wrapper.find('tr')
    expect(row.attributes('aria-label')).toBe('选择 新行')

    await row.trigger('keydown', { key: 'Enter' })
    expect(onActivate).toHaveBeenCalledWith({ id: 3, label: '新行' })

    const detachedRow = row.element
    wrapper.unmount()
    detachedRow.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))

    expect(onActivate).toHaveBeenCalledTimes(1)
  })
})
