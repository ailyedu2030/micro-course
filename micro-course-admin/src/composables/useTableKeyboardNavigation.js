import { nextTick, onBeforeUnmount, watch } from 'vue'

export function useTableKeyboardNavigation(options) {
  const {
    tableRef,
    tableData,
    onActivate,
    getAriaLabel
  } = options

  let tbodyEl = null
  let keydownHandler = null

  function bindKeyboardListener(nextTbody) {
    if (!nextTbody || nextTbody === tbodyEl) {
      return
    }
    cleanupTableKeyboard()
    keydownHandler = (event) => {
      const rowElement = event.target.closest('tr')
      if (!rowElement) {
        return
      }
      if (event.key !== 'Enter' && event.key !== ' ') {
        return
      }
      const rowIndex = Array.from(nextTbody.querySelectorAll('tr')).indexOf(rowElement)
      const row = tableData.value?.[rowIndex]
      if (row) {
        event.preventDefault()
        onActivate?.(row)
      }
    }
    nextTbody.addEventListener('keydown', keydownHandler)
    tbodyEl = nextTbody
  }

  function enhanceRows() {
    const rootElement = tableRef.value?.$el || tableRef.value
    const nextTbody = rootElement?.querySelector?.('tbody')
    if (!nextTbody) {
      return
    }
    bindKeyboardListener(nextTbody)
    nextTbody.querySelectorAll('tr').forEach((rowElement, index) => {
      const row = tableData.value?.[index]
      rowElement.setAttribute('tabindex', '0')
      rowElement.setAttribute('role', 'button')
      rowElement.setAttribute('aria-label', getAriaLabel?.(row, index) || '')
    })
  }

  function refreshTableKeyboard() {
    return nextTick(enhanceRows)
  }

  function cleanupTableKeyboard() {
    if (tbodyEl && keydownHandler) {
      tbodyEl.removeEventListener('keydown', keydownHandler)
    }
    tbodyEl = null
    keydownHandler = null
  }

  watch(tableData, () => {
    refreshTableKeyboard()
  }, { deep: true })

  onBeforeUnmount(() => {
    cleanupTableKeyboard()
  })

  return {
    refreshTableKeyboard,
    cleanupTableKeyboard
  }
}
