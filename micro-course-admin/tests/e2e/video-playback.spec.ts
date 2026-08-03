/**
 * 视频播放全链路回归 E2E（2026-08-03 新增）
 * =========================================================
 * 背景：2026-08-03 两大 P0 修复（#179）
 *   P0-1 转码 hlsUrl 路径不匹配（/api/videos/stream → /api/video-stream）
 *   P0-2 LearningView 挂载即视频骨架屏卡死（watch 缺 immediate）
 * 随后发现 P1-C：HLS 播放入口（VideoPlayer / 课程详情预览）未携带播放签名
 *   （流端点 P1I-014 强制 sign，hls.js 仅带 Authorization 头）→ m3u8/ts 403。
 *
 * 本用例把整条链路固化为 CI 门禁：
 *   教师建课 → 封面 → 章节 → 课时(VIDEO) → 真实 mp4 上传 → FFmpeg 转码
 *   → 提交审核 → 教务审批 → 发布 → 学生选课 → 学生浏览器真实播放推进
 *   → HLS VideoPlayer 页（hls.js + X-Video-Sign 头）真实播放推进
 *
 * 环境变量（默认值对齐 CI p0-seed.sql）：
 *   BASE_URL       前端地址      默认 http://localhost:8088
 *   API_BASE_URL   后端地址      默认 http://localhost:8080（CI）
 *   VIDEO_TEACHER_USER/PASS     默认 p0_teacher / student123
 *   VIDEO_ADMIN_USER/PASS       默认 admin / admin123
 *   VIDEO_STUDENT_USER/PASS     默认 student / student123
 *
 * 运行（本地，需先起 local dev 栈）：
 *   cd micro-course-admin
 *   BASE_URL=http://localhost:8088 API_BASE_URL=http://localhost:8089 \
 *   VIDEO_TEACHER_USER=teacher1 VIDEO_TEACHER_PASS=password123 \
 *   VIDEO_STUDENT_USER=student1 VIDEO_STUDENT_PASS=password123 \
 *   VIDEO_ADMIN_USER=admin VIDEO_ADMIN_PASS=admin123 \
 *   npx playwright test tests/e2e/video-playback.spec.ts --config=tests/e2e/e2e.config.ts --reporter=list
 */

import { test, expect, Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088'
const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080'

const TEACHER = {
  username: process.env.VIDEO_TEACHER_USER || 'p0_teacher',
  password: process.env.VIDEO_TEACHER_PASS || 'student123'
}
const ADMIN = {
  username: process.env.VIDEO_ADMIN_USER || 'admin',
  password: process.env.VIDEO_ADMIN_PASS || 'admin123'
}
const STUDENT = {
  username: process.env.VIDEO_STUDENT_USER || 'student',
  password: process.env.VIDEO_STUDENT_PASS || 'student123'
}

interface ApiResponse<T = any> {
  code: number
  message?: string
  data?: T
}

async function api<T = any>(method: string, path: string, token: string, body?: unknown): Promise<ApiResponse<T>> {
  const res = await fetch(API_BASE + '/api' + path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  const json = (await res.json()) as ApiResponse<T>
  if (!res.ok && json.code !== 200 && json.code !== 302) {
    throw new Error(`${method} ${path} → HTTP ${res.status} ${JSON.stringify(json)}`)
  }
  return json
}

async function login(username: string, password: string): Promise<string> {
  const res = await api('POST', '/auth/login', '', { username, password })
  expect(res.code, `登录失败: ${res.message}`).toBe(200)
  return res.data!.accessToken
}

async function loginAs(page: Page, username: string, password: string): Promise<void> {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForSelector('#username', { timeout: 60000 })
  await page.fill('#username', username)
  await page.fill('#password', password)
  const loginBtn = page.locator('.login-btn')
  if (await loginBtn.isVisible().catch(() => false)) {
    await loginBtn.click()
  } else {
    await page.locator('button:has-text("登 录"), button:has-text("登录")').first().click()
  }
  await page.waitForURL(/\/(student|teacher|admin)\//, { timeout: 20000 })
}

// MD5 秒传去重会命中历史残留视频（同内容 mp4），导致新课程"无视频"。
// 每次运行前清扫同名残留课程（先退课再删课，级联清理视频/章节），保证去重表干净。
async function sweepLeftoverCourses(adminToken: string): Promise<void> {
  const prefix = 'E2E视频回归课程_'
  try {
    const list = await api<any>('GET', `/courses?title=${encodeURIComponent(prefix)}&size=100`, adminToken)
    const courses = list.data?.items || []
    for (const course of courses) {
      if (!course?.id || typeof course.id !== 'number') continue
      try {
        const enrolls = await api<any>('GET', `/enrollments/course/${course.id}?size=100`, adminToken)
        for (const e of enrolls.data?.items || []) {
          if (e?.id) {
            await api('DELETE', `/enrollments/${e.id}`, adminToken)
          }
        }
      } catch { /* 无选课或列表异常均可忽略 */ }
      try { await api('DELETE', `/courses/${course.id}`, adminToken) } catch { /* 已删除/关闭态忽略 */ }
    }
  } catch { /* 清扫失败不阻断用例（retry 仍可通过唯一内容规避） */ }
}

async function cleanupCourse(adminToken: string, courseId: number): Promise<void> {
  try {
    const enrolls = await api<any>('GET', `/enrollments/course/${courseId}?size=100`, adminToken)
    for (const e of enrolls.data?.items || []) {
      if (e?.id) {
        await api('DELETE', `/enrollments/${e.id}`, adminToken)
      }
    }
  } catch { /* 无选课 */ }
  try { await api('DELETE', `/courses/${courseId}`, adminToken) } catch { /* 已删除 */ }
}

test.describe('视频播放全链路回归 E2E', () => {
  test.describe.configure({ mode: 'serial' })

  test('上传→转码→发布→选课→学生真实播放推进（mp4 + HLS 双链路）', async ({ page }) => {
    test.setTimeout(300000)

    // ── 0. 登录三角色 ─────────────────────────────────────────
    const teacherToken = await login(TEACHER.username, TEACHER.password)
    const adminToken = await login(ADMIN.username, ADMIN.password)
    const studentToken = await login(STUDENT.username, STUDENT.password)

    const suffix = Date.now()
    let courseId = 0
    let chapterId: number
    let videoId: number
    const fixturePath = join(process.cwd(), 'tests', 'e2e', 'fixtures', 'qa-tiny.mp4')

    try {
      // 清扫历史残留，避免 MD5 秒传去重命中旧视频
      await sweepLeftoverCourses(adminToken)

      // ── 1. 教师建课 + 封面 ─────────────────────────────────
      const created = await api('POST', '/courses', teacherToken, {
        title: `E2E视频回归课程_${suffix}`,
        categoryId: 1,
        learningMode: 'online-self-study',
        price: 0,
        isFree: true,
        courseType: 'VIDEO'
      })
      expect(created.code, `建课失败: ${created.message}`).toBe(200)
      courseId = created.data!.id
      test.info().annotations.push({ type: 'created-course', description: String(courseId) })

      const cover = await api('PUT', `/courses/${courseId}`, teacherToken, {
        coverUrl: '/api/files/covers/qa-cover.jpg',
        summary: 'E2E 视频播放回归课程'
      })
      expect(cover.code, `设置封面失败: ${cover.message}`).toBe(200)

      // ── 2. 章节 + 课时(VIDEO) ──────────────────────────────
      const chapter = await api('POST', '/chapters', teacherToken, {
        title: 'E2E第一章',
        courseId,
        description: '视频回归测试章节',
        sortOrder: 1,
        duration: 60
      })
      expect(chapter.code, `建章节失败: ${chapter.message}`).toBe(200)
      chapterId = chapter.data!.id

      const section = await api('POST', `/courses/${courseId}/chapters/${chapterId}/sections`, teacherToken, {
        title: 'E2E课时1',
        sectionType: 'VIDEO',
        sortOrder: 1
      })
      expect(section.code, `建课时失败: ${section.message}`).toBe(200)

      // ── 3. 真实 mp4 上传 + 等待 FFmpeg 转码 ────────────────
      const mp4Buffer = await readFile(fixturePath)
      const form = new FormData()
      form.append('file', new Blob([mp4Buffer], { type: 'video/mp4' }), 'qa-tiny.mp4')
      form.append('courseId', String(courseId))
      form.append('chapterId', String(chapterId))

      const uploadRes = await fetch(API_BASE + '/api/videos/upload', {
        method: 'POST',
        headers: { Authorization: `Bearer ${teacherToken}` },
        body: form
      })
      const uploadJson = (await uploadRes.json()) as ApiResponse<any>
      expect(uploadJson.code, `上传失败: ${uploadJson.message}`).toBe(200)
      videoId = uploadJson.data!.id

      // 轮询转码状态 → COMPLETED(2)
      let status = 0
      let statusBody: ApiResponse<any> | null = null
      const deadline = Date.now() + 120000
      while (Date.now() < deadline) {
        statusBody = await api('GET', `/videos/${videoId}/status`, teacherToken)
        status = statusBody.data?.status ?? 0
        if (status === 2) break
        if (status === 3) {
          throw new Error(`转码失败: ${statusBody.data?.errorMessage}`)
        }
        await new Promise((r) => setTimeout(r, 2000))
      }
      expect(status, `转码超时(120s), 最终状态: ${statusBody?.data?.status}`).toBe(2)

      // P0-1 回归断言：hlsUrl 必须为标准流路径
      const videoDetail = await api('GET', `/videos/${videoId}`, teacherToken)
      const hlsUrl = videoDetail.data?.hlsUrl
      expect(hlsUrl).toBeTruthy()
      expect(hlsUrl, 'hlsUrl 必须为 /api/video-stream/ 标准路径').toMatch(/^\/api\/video-stream\//)

      // ── 4. 提交审核 → 审批 → 发布 ──────────────────────────
      const submitted = await api('POST', `/courses/${courseId}/submit`, teacherToken)
      expect(submitted.code, `提交审核失败: ${submitted.message}`).toBe(200)
      const approved = await api('POST', `/courses/${courseId}/approve`, adminToken)
      expect(approved.code, `审批失败: ${approved.message}`).toBe(200)
      const published = await api('POST', `/courses/${courseId}/publish`, adminToken)
      expect(published.code, `发布失败: ${published.message}`).toBe(200)

      // ── 5. 学生选课 + 签名 + 流端点（P0-1 + P1-C 双通道）────
      const enrolled = await api('POST', '/enrollments', studentToken, {
        courseId,
        sourceChannel: 'SEARCH'
      })
      expect(enrolled.code, `选课失败: ${enrolled.message}`).toBe(200)

      const signRes = await api('GET', `/videos/${videoId}/sign`, studentToken)
      expect(signRes.code, `获取签名失败: ${signRes.message}`).toBe(200)
      const sign = signRes.data

      // /play 302 → hlsUrl（标准路径）
      const playRes = await fetch(API_BASE + `/api/videos/${videoId}/play?sign=${encodeURIComponent(sign)}`, {
        headers: { Authorization: `Bearer ${studentToken}` },
        redirect: 'manual'
      })
      expect(playRes.status).toBe(302)
      const location = playRes.headers.get('location') || ''
      expect(location).toMatch(/^\/api\/video-stream\//)

      // query sign 通道：manifest 200 + m3u8 内容
      const m3u8ViaQuery = await fetch(API_BASE + location + `?sign=${encodeURIComponent(sign)}`, {
        headers: { Authorization: `Bearer ${studentToken}` }
      })
      expect(m3u8ViaQuery.status).toBe(200)
      expect(await m3u8ViaQuery.text()).toContain('#EXTM3U')

      // X-Video-Sign 请求头通道：manifest 200（hls.js 分片同机制）
      const m3u8ViaHeader = await fetch(API_BASE + location, {
        headers: {
          Authorization: `Bearer ${studentToken}`,
          'X-Video-Sign': sign
        }
      })
      expect(m3u8ViaHeader.status).toBe(200)
      expect(await m3u8ViaHeader.text()).toContain('#EXTM3U')

      // ── 6. 学生浏览器：学习视图 mp4 真实播放推进（P0-2）────
      const pageErrors: string[] = []
      page.on('pageerror', (e) => pageErrors.push(String(e)))
      await loginAs(page, STUDENT.username, STUDENT.password)
      await page.goto(`${BASE_URL}/student/learning?courseId=${courseId}`, { waitUntil: 'domcontentloaded' })

      // P0-2 回归断言：<video> 渲染而非骨架屏卡死
      await page.waitForSelector('video.video-player', { timeout: 30000 })
      await page.waitForFunction(() => {
        const v = document.querySelector('video.video-player') as HTMLVideoElement | null
        return v && v.currentSrc && v.currentSrc.length > 0
      }, undefined, { timeout: 30000 })
      await page.waitForTimeout(1500)
      const skeletonStuck = await page.locator('.video-skeleton').isVisible().catch(() => false)
      expect(skeletonStuck, '骨架屏不应卡死').toBe(false)

      // 触发播放并断言 currentTime 推进（标准 Chromium 可播放 mp4）
      await page.evaluate(() => {
        const v = document.querySelector('video.video-player') as HTMLVideoElement
        v.muted = true
        v.play().catch(() => {})
      })
      await expect
        .poll(() => page.evaluate(() => {
          const v = document.querySelector('video.video-player') as HTMLVideoElement
          return v ? v.currentTime : 0
        }), { timeout: 60000, message: '学习视图视频应真实播放推进' })
        .toBeGreaterThan(0)

      // ── 7. 学生浏览器：VideoPlayer HLS 页（hls.js + 签名头）──
      await page.goto(`${BASE_URL}/student/courses/${courseId}/play/${videoId}`, { waitUntil: 'domcontentloaded' })
      await page.waitForSelector('video', { timeout: 30000 })
      await page.waitForTimeout(4000) // 等 hls.js 拉 manifest + 首分片
      await page.evaluate(() => {
        const v = document.querySelector('video') as HTMLVideoElement
        v.muted = true
        v.play().catch(() => {})
      })
      await expect
        .poll(() => page.evaluate(() => {
          const v = document.querySelector('video') as HTMLVideoElement
          return v ? v.currentTime : 0
        }), { timeout: 120000, message: 'VideoPlayer HLS 页应通过签名通道真实播放推进' })
        .toBeGreaterThan(0)

      // 无未捕获页面异常
      expect(pageErrors, `页面异常: ${pageErrors.join('; ')}`).toEqual([])
    } finally {
      // ── 8. 清理：删除课程（级联视频/章节/课时）+ 临时文件 ──
      if (courseId) {
        try {
          const adminToken2 = await login(ADMIN.username, ADMIN.password)
          await cleanupCourse(adminToken2, courseId)
        } catch (e) {
          console.warn('[video-playback] 课程清理失败(不影响用例判定):', e)
        }
      }
    }
  })
})
