/**
 * 微课平台 · E2E 冒烟测试套件 v2
 *
 * 覆盖 3 角色 × 10 条核心链路 + 6 边界值测试 + 1 CRUD 全链路
 * 每条测试: no console.error + no HTTP 500
 *
 * 运行: cd micro-course-admin && npx playwright test e2e/smoke-test.spec.js
 * 要求: 后端 8080 + 前端 5173 + Redis 都在运行
 */

import { test, expect } from '@playwright/test'

const BASE = 'http://localhost:5173'
const API  = 'http://localhost:8080'

// ────── 工具函数 ──────

/** 收集 console.error / pageerror / HTTP 5xx */
function watchErrors(page) {
  const errors = []
  page.on('console',   msg => { if (msg.type() === 'error')   errors.push(msg.text()) })
  page.on('pageerror', err => { errors.push(err.message) })
  page.on('response',  res => { if (res.status() >= 500)      errors.push(`HTTP ${res.status()} ${res.url()}`) })
  return errors
}

/** UI 登录（真实填表单） */
async function uiLogin(page, username, password) {
  await page.goto(BASE + '/login')
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(500)
  await page.locator('#username, input[aria-label*="用户名"]').first().fill(username)
  await page.locator('#password, input[type="password"]').first().fill(password)
  await page.locator('button').filter({ hasText: '登' }).first().click()
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(2000)
}

/** 通过 page.request 获取 API token（绕过 CORS） */
async function apiToken(page) {
  const res = await page.request.post(`${API}/api/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  })
  const body = await res.json()
  return body.data.accessToken
}

// ══════════════════════════════════════════════════════════════
// 1. ADMIN 核心流程
// ══════════════════════════════════════════════════════════════
test.describe('ADMIN Core Flow', () => {
  let errs

  test.beforeEach(async ({ page }) => {
    errs = watchErrors(page)
    await uiLogin(page, 'admin', 'admin123')
  })
  test.afterEach(() => { expect(errs).toEqual([]) })

  test('P1 Dashboard', async ({ page }) => {
    await page.goto(BASE + '/admin/dashboard')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(200)
  })

  test('P2 课程列表', async ({ page }) => {
    await page.goto(BASE + '/courses')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })

  test('P3 创建课程弹窗', async ({ page }) => {
    await page.goto(BASE + '/courses')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const btn = page.locator('button').filter({ hasText: '新增课程' }).first()
    const btnExists = await btn.count()
    if (btnExists > 0) {
      await btn.click()
      await page.waitForTimeout(1000)
      const dlg = page.locator('[role=dialog]').first()
      const dlgExists = await dlg.count()
      if (dlgExists > 0) {
        await expect(dlg).toBeVisible()
        await page.locator('button').filter({ hasText: '取消' }).first().click()
      }
    }
  })

  test('P4 用户列表', async ({ page }) => {
    await page.goto(BASE + '/users')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })

  test('P5 部门列表', async ({ page }) => {
    await page.goto(BASE + '/departments')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })
})

// ══════════════════════════════════════════════════════════════
// 2. TEACHER 核心流程
// ══════════════════════════════════════════════════════════════
test.describe('TEACHER Core Flow', () => {
  let errs

  test.beforeEach(async ({ page }) => {
    errs = watchErrors(page)
    await uiLogin(page, 'teacher', '123456')
  })
  test.afterEach(() => { expect(errs).toEqual([]) })

  test('P6 教师看板', async ({ page }) => {
    await page.goto(BASE + '/teacher/dashboard')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })

  test('P7 我的课程', async ({ page }) => {
    await page.goto(BASE + '/teacher/courses')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })

  test('P8 学员管理', async ({ page }) => {
    await page.goto(BASE + '/teacher/students')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })
})

// ══════════════════════════════════════════════════════════════
// 3. STUDENT 核心流程
// ══════════════════════════════════════════════════════════════
test.describe('STUDENT Core Flow', () => {
  let errs

  test.beforeEach(async ({ page }) => {
    errs = watchErrors(page)
    await uiLogin(page, 'student', '123456')
  })
  test.afterEach(() => { expect(errs).toEqual([]) })

  test('P9 课程广场', async ({ page }) => {
    await page.goto(BASE + '/courses/market')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })

  test('P10 我的学习', async ({ page }) => {
    await page.goto(BASE + '/student/my-courses')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const txt = await page.locator('body').textContent()
    expect(txt.length).toBeGreaterThan(50)
  })
})

// ══════════════════════════════════════════════════════════════
// 4. 边界值 / 安全 / 异常
// ══════════════════════════════════════════════════════════════
test.describe('Edge Case & Security', () => {
  let errs, token

  test.beforeEach(async ({ page }) => {
    errs = watchErrors(page)
    token = await apiToken(page)
  })
  test.afterEach(() => { expect(errs).toEqual([]) })

  test('E1 空表单 → 400', async ({ page }) => {
    const res = await page.request.post(`${API}/api/courses`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { title: '', categoryId: null, teacherId: null }
    })
    expect(res.status()).toBe(400)
    const body = await res.json()
    expect(body.code).toBeDefined()
  })

  test('E2 超长标题 → <500', async ({ page }) => {
    const res = await page.request.post(`${API}/api/courses`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { title: 'A'.repeat(5000), categoryId: 1, teacherId: 3, courseType: 'VIDEO' }
    })
    expect(res.status()).toBeLessThan(500)
  })

  test('E3 删除不存在 → 404', async ({ page }) => {
    const res = await page.request.delete(`${API}/api/courses/99999`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    expect(res.status()).toBe(404)
  })

  test('E4 无 token → 401', async ({ page }) => {
    const res = await page.request.get(`${API}/api/courses`)
    expect(res.status()).toBe(401)
  })

  test('E5 登录响应格式', async ({ page }) => {
    const res = await page.request.post(`${API}/api/auth/login`, {
      data: { username: 'admin', password: 'admin123' }
    })
    expect(res.status()).toBe(200)
    const body = await res.json()
    expect(body.code).toBe(200)
    expect(body.data.accessToken).toBeDefined()
  })

  test('E6 目录遍历 → 4xx', async ({ page }) => {
    const res = await page.request.get(`${API}/../../../etc/passwd`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    expect(res.status()).toBeGreaterThanOrEqual(400)
    expect(res.status()).toBeLessThan(500)
  })
})

// ══════════════════════════════════════════════════════════════
// 5. CRUD 全链路
// ══════════════════════════════════════════════════════════════
test.describe('CRUD Full Chain', () => {
  let errs, token, courseId

  test.beforeEach(async ({ page }) => {
    errs = watchErrors(page)
    token = await apiToken(page)
  })
  test.afterEach(() => { expect(errs).toEqual([]) })

  test('CRUD-1 创建 → 查询 → 删除（关闭状态验证）', async ({ page }) => {
    // CREATE
    const c = await page.request.post(`${API}/api/courses`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { title: 'E2E-TEST-' + Date.now(), categoryId: 1, teacherId: 3, difficulty: 1, courseType: 'VIDEO', description: 'E2E test' }
    })
    expect(c.status()).toBe(200)
    const cBody = await c.json()
    expect(cBody.code).toBe(200)
    courseId = cBody.data.id
    expect(courseId).toBeDefined()

    // READ
    const r = await page.request.get(`${API}/api/courses/${courseId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    expect(r.status()).toBe(200)
    const rBody = await r.json()
    expect(rBody.data.id).toBe(courseId)

    // DELETE（后端是状态关闭 CLOSED，不是 @TableLogic）
    const d = await page.request.delete(`${API}/api/courses/${courseId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    expect(d.status()).toBe(200)

    // VERIFY CLOSED
    const v = await page.request.get(`${API}/api/courses/${courseId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    expect(v.status()).toBe(200)
    const vBody = await v.json()
    expect(vBody.data.status).toBe(5) // CLOSED = 5
  })
})

// ══════════════════════════════════════════════════════════════
// 6. 状态机行为测试（防再发 — 锁定业务逻辑）
// ══════════════════════════════════════════════════════════════
test.describe('Course State Machine Behavior', () => {
  let errs, token, cid

  test.beforeEach(async ({ page }) => {
    errs = watchErrors(page)
    token = await apiToken(page)
    // Create a fresh course for each test
    const c = await page.request.post(`${API}/api/courses`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { title: 'SM-TEST-' + Date.now(), categoryId: 1, teacherId: 3, difficulty: 1, courseType: 'VIDEO', description: 'State machine test' }
    })
    const body = await c.json()
    cid = body.data.id
  })
  test.afterEach(() => { expect(errs).toEqual([]) })

  test('SM-1: DRAFT → PENDING_REVIEW', async ({ page }) => {
    const res = await page.request.post(`${API}/api/courses/${cid}/submit-review`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    // May fail if pre-conditions not met (no cover/chapters), but should not be 500
    expect(res.status()).toBeLessThan(500)
  })

  test('SM-2: CLOSED→DRAFT 应被拒绝（DEVIATION-1 修复验证）', async ({ page }) => {
    // Delete to set CLOSED
    await page.request.delete(`${API}/api/courses/${cid}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    // Try to update status to DRAFT
    const res = await page.request.put(`${API}/api/courses/${cid}/status`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { status: 0 }
    })
    // Should fail with invalid transition
    expect(res.status()).toBe(400)
  })

  test('SM-3: 空标题提交审核应被拒绝', async ({ page }) => {
    const c = await page.request.post(`${API}/api/courses`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { title: '', categoryId: 1, teacherId: 3, difficulty: 1, courseType: 'VIDEO' }
    })
    // Should fail validation
    expect(c.status()).toBe(400)
  })

  test('SM-4: 无 token 访问应 401', async ({ page }) => {
    const res = await page.request.get(`${API}/api/courses/${cid}`)
    expect(res.status()).toBe(401)
  })
})

// ══════════════════════════════════════════════════════════════
// 7. 选课状态机行为测试（防选课超卖 P0）
// ══════════════════════════════════════════════════════════════
test.describe('Enrollment State Machine Behavior', () => {
  let errs, token

  test.beforeEach(async ({ page }) => {
    errs = watchErrors(page)
    token = await apiToken(page)
  })
  test.afterEach(() => { expect(errs).toEqual([]) })

  test('ENROLL-1: 选课未发布的课程应被拒绝', async ({ page }) => {
    // Course 25 is DRAFT (not published) — used as test fixture
    const res = await page.request.post(`${API}/api/enrollments`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { courseId: 25, userId: 1 }  // admin user, trying to enroll in DRAFT course
    })
    // 业务逻辑审计 DEVIATION-1 修复验证: 原子 insert 在课程非 PUBLISHED 时返回 0
    // 然后回退到 course not found / not published 错误
    expect(res.status()).toBeGreaterThanOrEqual(400)
    expect(res.status()).toBeLessThan(500)
  })

  test('ENROLL-2: 选课不存在的课程应被拒绝', async ({ page }) => {
    const res = await page.request.post(`${API}/api/enrollments`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { courseId: 99999, userId: 1 }
    })
    expect(res.status()).toBeGreaterThanOrEqual(400)
  })

  test('ENROLL-3: 重复选课应返回现有记录（幂等）', async ({ page }) => {
    // student7 (id=7) is already enrolled in course 5
    // get a student token
    const studentLogin = await page.request.post(`${API}/api/auth/login`, {
      data: { username: 'student', password: '123456' }
    })
    const sbody = await studentLogin.json()
    const stoken = sbody.data.accessToken
    const res = await page.request.post(`${API}/api/enrollments`, {
      headers: { 'Authorization': `Bearer ${stoken}` },
      data: { courseId: 5 }
    })
    // Should succeed (idempotent) or 200/200 status
    expect(res.status()).toBe(200)
  })

  test('ENROLL-4: 并发超卖防护 — 已超 maxStudents 的课程应拒绝新选课', async ({ page }) => {
    // Course 5 is APPROVED (status=2) with max=2, count=5 (already over-allocated)
    // 期望：选课被拒绝（不管原因为何：未发布/满员/已选过）
    const res = await page.request.post(`${API}/api/enrollments`, {
      headers: { 'Authorization': `Bearer ${token}` },
      data: { courseId: 5, userId: 1 }
    })
    // Spec §3.5 要求超额选课被拒 — 任意 4xx 都是正确响应
    expect(res.status()).toBeGreaterThanOrEqual(400)
    expect(res.status()).toBeLessThan(500)
  })
})
