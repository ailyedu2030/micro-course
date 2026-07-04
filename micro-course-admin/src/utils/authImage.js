import request from '@/utils/request'

const urlCache = new Map()
const CACHE_TTL = 5 * 60 * 1000

export async function loadAuthImage(url) {
  return loadAuthResource(url)
}

export async function loadAuthResource(url) {
  const cached = urlCache.get(url)
  if (cached && cached.blobUrl && (Date.now() - cached.time < CACHE_TTL)) {
    return cached.blobUrl
  }
  if (cached && cached.blobUrl) {
    URL.revokeObjectURL(cached.blobUrl)
  }
  try {
    const res = await request({
      method: 'GET',
      url,
      responseType: 'blob',
      _suppressErrorToast: true
    })
    const blobUrl = URL.createObjectURL(res.data)
    urlCache.set(url, { blobUrl, time: Date.now() })
    return blobUrl
  } catch {
    urlCache.delete(url)
    return ''
  }
}

export function clearImageCache() {
  for (const [, cached] of urlCache) {
    if (cached.blobUrl) URL.revokeObjectURL(cached.blobUrl)
  }
  urlCache.clear()
}

export function removeCacheEntry(url) {
  const cached = urlCache.get(url)
  if (cached && cached.blobUrl) URL.revokeObjectURL(cached.blobUrl)
  urlCache.delete(url)
}
