import { describe, expect, it, vi } from 'vitest'

import { fetchAllPages } from '@/utils/fetchAllPages'

const asPage = (items, totalElements, page, size) => ({
  data: { items, totalElements, page, size }
})

describe('fetchAllPages', () => {
  it('单页拉取：totalElements 等于首页条数时只请求一次', async () => {
    const fetcher = vi.fn().mockResolvedValue(asPage([{ id: 1 }, { id: 2 }], 2, 0, 200))
    const result = await fetchAllPages(fetcher, { chapterId: 1 })
    expect(result).toHaveLength(2)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(fetcher).toHaveBeenCalledWith({ chapterId: 1, page: 0, size: 200 })
  })

  it('多页拉取：总条数超过 pageSize 时循环翻页直至收齐', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(asPage(Array.from({ length: 200 }, (_, i) => ({ id: i })), 250, 0, 200))
      .mockResolvedValueOnce(asPage(Array.from({ length: 50 }, (_, i) => ({ id: 200 + i })), 250, 1, 200))
    const result = await fetchAllPages(fetcher, {}, 200)
    expect(result).toHaveLength(250)
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(fetcher.mock.calls[1][0]).toEqual({ page: 1, size: 200 })
  })

  it('兼容后端忽略分页返回全量：首页即收齐时只请求一次', async () => {
    const fetcher = vi.fn().mockResolvedValue(asPage(Array.from({ length: 250 }, (_, i) => ({ id: i })), 250, 0, 200))
    const result = await fetchAllPages(fetcher, {})
    expect(result).toHaveLength(250)
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('空结果：items 为空时立即返回空数组', async () => {
    const fetcher = vi.fn().mockResolvedValue(asPage([], 0, 0, 200))
    const result = await fetchAllPages(fetcher, {})
    expect(result).toEqual([])
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('请求失败时向上抛出，由调用方处理', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('400 Bad Request'))
    await expect(fetchAllPages(fetcher, {})).rejects.toThrow('400 Bad Request')
  })
})
