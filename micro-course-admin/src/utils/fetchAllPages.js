/**
 * 分页拉全量工具（R11 后端 size 上限收敛后的"全量"标准做法）。
 *
 * 背景：后端分页端点均校验 size ≤ 上限（100/200/10000），前端历史遗留
 * size=999/1000 直传会触发 400（如 SectionController max=200 曾致章节课时
 * 加载失败）。本工具以 pageSize（默认 200，恒在后端上限内）循环翻页，
 * 直到收齐 totalElements；同时兼容"后端忽略分页、直接返回全量"的实现
 * （首页即返回全部，一次循环即结束）。
 *
 * @param {(params: object) => Promise<{data: {items?: Array, totalElements?: number|string}}>} fetcher
 * @param {object} params 除 page/size 外的固定查询参数
 * @param {number} pageSize 每页大小，默认 200
 * @returns {Promise<Array>} 全量数据
 */
export async function fetchAllPages(fetcher, params = {}, pageSize = 200) {
  const collected = []
  let page = 0
  for (;;) {
    const { data } = await fetcher({ ...params, page, size: pageSize })
    const items = Array.isArray(data?.items) ? data.items : []
    collected.push(...items)
    const total = Number(data?.totalElements ?? collected.length)
    if (collected.length >= total || items.length === 0) break
    page += 1
  }
  return collected
}
