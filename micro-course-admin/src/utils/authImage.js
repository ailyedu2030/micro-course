import request from '@/utils/request'

const urlCache = new Map()

export async function loadAuthImage(url) {
  if (urlCache.has(url)) {
    const cached = urlCache.get(url)
    if (cached.blobUrl) {
      URL.revokeObjectURL(cached.blobUrl)
    }
  }
  try {
    const res = await request({
      method: 'GET',
      url,
      responseType: 'blob'
    })
    const blobUrl = URL.createObjectURL(res.data)
    urlCache.set(url, { blobUrl, time: Date.now() })
    return blobUrl
  } catch {
    return ''
  }
}

export function clearImageCache() {
  for (const [, cached] of urlCache) {
    if (cached.blobUrl) URL.revokeObjectURL(cached.blobUrl)
  }
  urlCache.clear()
}
