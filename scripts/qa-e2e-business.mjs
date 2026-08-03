// 微课平台 · 核心业务 E2E 流验证 (server-side API + browser-side UI 校验)
// 输出: /Users/jackie/微课平台/.qa-results/qa-e2e-<scenario>-<ts>.json

const BASE = process.env.QA_API_BASE || 'http://localhost:8089'
const UI = process.env.QA_UI_BASE || 'http://localhost:8088'

async function api(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = 'Bearer ' + token
  const r = await fetch(BASE + '/api' + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  let data = null
  try { data = await r.json() } catch (e) {}
  return { status: r.status, code: data?.code, message: data?.message, data: data?.data }
}

async function login(user, pass) {
  const r = await api('POST', '/auth/login', null, { username: user, password: pass })
  if (r.code !== 200) throw new Error(`login ${user} failed: ${r.code} ${r.message}`)
  // 获取 userId
  const me = await api('GET', '/auth/me', r.data.accessToken)
  return { token: r.data.accessToken, refresh: r.data.refreshToken, role: r.data.role, userId: me.data?.id }
}

const results = []

function record(name, ok, details) {
  results.push({ name, ok, details })
  console.log((ok ? '✓ ' : '✗ ') + name + (details ? ' ' + JSON.stringify(details).slice(0, 200) : ''))
}

// ────────────────────────────────────────────────────────────
// B01 课程生命周期: 教师新建 → 提交 → 教务审核 → 发布 → 学生选课
// ────────────────────────────────────────────────────────────
async function e2eB01_CourseLifecycle() {
  console.log('\n[B01] 课程生命周期 E2E')
  let teacher, academic, student
  try {
    teacher = await login('teacher1', 'password123')
    academic = await login('academic1', 'password123')
    student = await login('student1', 'password123')

    // 1. 教师新建课程 (DRAFT)
    const uniqueTitle = 'E2E测试课_' + Date.now()
    const created = await api('POST', '/courses', teacher.token, {
      title: uniqueTitle, categoryId: 1, learningMode: 'online-self-study', price: 0, isFree: true, courseType: 'VIDEO'
    })
    record('B01.1-教师-创建课程-DRAFT', created.code === 200 && created.data?.id > 0, { id: created.data?.id, code: created.code })

    const courseId = created.data?.id
    if (!courseId) throw new Error('no course id')

    // 2. 提交审核 - 应缺前置条件（封面/章节/视频）
    const submitted = await api('POST', `/courses/${courseId}/submit`, teacher.token)
    record('B01.2-教师-提交审核-期望前置条件校验', submitted.code === 9005 && submitted.message?.includes('封面'), submitted)

    // 3. 跳过"创建章节/视频/封面"前置 — 直接尝试 approve, 应 PENDING→APPROVED 失败
    const approvedDirectly = await api('POST', `/courses/${courseId}/approve`, academic.token)
    record('B01.3-教务-跳过提交直接审核-期望6005', approvedDirectly.code === 6005, approvedDirectly)

    // 4. 测试 publish 状态机
    const published = await api('POST', `/courses/${courseId}/publish`, teacher.token)
    record('B01.4-教师-发布-DRAFT→期望6005', published.code === 6005, published)

    // 5. 学生选课（未发布课程）期望 6007
    const enrolled = await api('POST', '/enrollments', student.token, { courseId })
    record('B01.5-学生-选课-期望6007', enrolled.code === 6007, enrolled)

    // 6. 重复选课仍期望 6007
    const dup = await api('POST', '/enrollments', student.token, { courseId })
    record('B01.6-学生-重复选课-期望6007', dup.code === 6007, dup)

    // 7. 学生退课（不存在选课记录）
    const drop = await api('DELETE', `/enrollments/999999`, student.token)
    record('B01.7-学生-退课-不存在ID-期望非200', drop.code === 404 || drop.code === 8005 || drop.code >= 400, drop)

    // 8. 不存在课程选课
    const fakeEnroll = await api('POST', '/enrollments', student.token, { courseId: 9999999 })
    record('B01.8-学生-不存在课程选课-期望6001', fakeEnroll.code === 6001 || fakeEnroll.code === 404, fakeEnroll)
  } catch (e) {
    record('B01-异常', false, { error: String(e).slice(0, 200) })
  }
}

// ────────────────────────────────────────────────────────────
// B02 微专业生命周期: 教务建 → 教师申报(草稿)→ 提交 → 教务审批 → 开课 → 学生报名
// ────────────────────────────────────────────────────────────
async function e2eB02_MicroSpecialtyLifecycle() {
  console.log('\n[B02] 微专业生命周期 E2E')
  let academic, teacher, student
  try {
    academic = await login('academic1', 'password123')
    teacher = await login('teacher1', 'password123')
    student = await login('student1', 'password123')

    // 教务创建微专业
    const msCode = 'MS_E2E_' + Date.now()
    const created = await api('POST', '/micro-specialties', academic.token, {
      code: msCode,
      title: 'E2E微专业_' + Date.now(),
      offerDepartmentId: 1,
      leadTeacherId: teacher.userId,
      semester: '2026-Fall',
      totalCredits: 10,
      totalHours: 64,
      maxStudents: 50,
      description: 'E2E测试'
    })
    record('B02.1-教务-创建微专业-DRAFT', created.code === 200 && created.data?.id > 0, { id: created.data?.id, code: created.code })

    const msId = created.data?.id
    if (!msId) throw new Error('no micro-specialty id')

    // 教师申报表 (前置: teacher1 需绑学院 — 测试环境通常未绑, 期望 9005 业务校验)
    const draft = await api('POST', '/storage-applications/init', teacher.token, { microSpecialtyId: msId })
    record('B02.2-教师-申报表-期望前置校验(学院未绑)或200', draft.code === 9005 || draft.code === 200, draft)

    // 微专业详情/列表查询 (用已公开的 P0_TEST_MS, DRAFT 状态不对学生可见)
    const detail = await api('GET', '/micro-specialties/1', student.token)
    record('B02.3-微专业详情-公开', detail.code === 200, { code: detail.code })

    const list = await api('GET', `/micro-specialties/${msId}/teachers`, student.token)
    record('B02.4-微专业团队', list.code === 200, { count: list.data?.length || 0 })

    const stats = await api('GET', `/micro-specialties/${msId}/stats`, academic.token)
    record('B02.5-微专业-统计', stats.code === 200, stats)

    // 教务审批 — 当前状态是 DRAFT, 不能审批 → 17003
    const approve = await api('POST', `/micro-specialties/${msId}/approve`, academic.token)
    record('B02.6-教务-审批-DRAFT-期望17003', approve.code === 17003, approve)

    // 教师开课 (教师不是 LEAD) → 17002
    const open = await api('POST', `/micro-specialties/${msId}/open`, teacher.token)
    record('B02.7-教师-非LEAD开课-期望17002', open.code === 17002, open)

    // 学生报名 (未招生期) → 17013
    const apply = await api('POST', '/micro-specialty-enrollments/apply', student.token, { microSpecialtyId: msId })
    record('B02.8-学生-未招生期报名-期望17013', apply.code === 17013, apply)
  } catch (e) {
    record('B02-异常', false, { error: String(e).slice(0, 200) })
  }
}

// ────────────────────────────────────────────────────────────
// B03 课程状态机非法转换: PENDING → APPROVED → DRAFT (非法), APPROVED → DRAFT (非法)
// ────────────────────────────────────────────────────────────
async function e2eB03_StateMachineInvariants() {
  console.log('\n[B03] 状态机非法转换')
  let teacher, academic
  try {
    teacher = await login('teacher1', 'password123')
    academic = await login('academic1', 'password123')

    // 创建 DRAFT
    const created = await api('POST', '/courses', teacher.token, {
      title: 'E2E状态机_' + Date.now(), categoryId: 1, learningMode: 'online-self-study', price: 0, isFree: true, courseType: 'VIDEO'
    })
    const courseId = created.data?.id
    record('B03.1-创建-DRAFT', created.code === 200 && courseId > 0, created)

    // 1) DRAFT → publish (非法) — 应 6005 不允许的状态转换
    const pub = await api('POST', `/courses/${courseId}/publish`, teacher.token)
    record('B03.2-DRAFT→PUBLISHED-期望6005', pub.code === 6005 || pub.code === 400, pub)

    // 2) DRAFT → approve (跨状态非法)
    const approve = await api('POST', `/courses/${courseId}/approve`, academic.token)
    record('B03.3-DRAFT→APPROVED-期望6005', approve.code === 6005, approve)

    // 3) 教师自审 → @PreAuthorize 拦截返回 403 (无权限, 而非 9010 自审批)
    const selfApprove = await api('POST', `/courses/${courseId}/approve`, teacher.token)
    record('B03.4-教师自审-期望403-NO_PERMISSION', selfApprove.code === 403 || selfApprove.code === 10003, selfApprove)

    // 4) reject 原因 < 10 字符 — 期望 400/9005 业务校验
    const badReject = await api('POST', `/courses/${courseId}/reject`, academic.token, { reason: '太短' })
    record('B03.5-reject-原因<10-期望400或9005', badReject.code === 400 || badReject.code === 9005, badReject)

    // 5) 不存在课程的状态变更 — 期望 404
    const ghost = await api('POST', '/courses/9999999/submit', teacher.token)
    record('B03.6-不存在课程-submit-期望404', ghost.code === 404 || ghost.code === 6001, ghost)

    // 6) 不存在课程-approve
    const ghostApprove = await api('POST', '/courses/9999999/approve', academic.token)
    record('B03.7-不存在课程-approve-期望404', ghostApprove.code === 404 || ghostApprove.code === 6001, ghostApprove)
  } catch (e) {
    record('B03-异常', false, { error: String(e).slice(0, 200) })
  }
}

// ────────────────────────────────────────────────────────────
// B04 选课并发 + 错误状态
// ────────────────────────────────────────────────────────────
async function e2eB04_EnrollmentConcurrency() {
  console.log('\n[B04] 选课并发与状态机')
  let teacher, academic, student1, student2
  try {
    teacher = await login('teacher1', 'password123')
    academic = await login('academic1', 'password123')
    student1 = await login('student1', 'password123')

    // 创建课程 (仅 DRAFT, 因为发布需前置条件)
    const created = await api('POST', '/courses', teacher.token, {
      title: 'E2E并发_' + Date.now(), categoryId: 1, learningMode: 'online-self-study', price: 0, isFree: true, courseType: 'VIDEO'
    })
    const courseId = created.data?.id
    record('B04.1-创建课程', created.code === 200 && courseId > 0, created)

    // 并发选课 5 次同一未发布课程 — 全部应 6007 课程未发布
    const results = await Promise.all(Array(5).fill(0).map(() => api('POST', '/enrollments', student1.token, { courseId })))
    const notPublished = results.filter(r => r.code === 6007).length
    record('B04.2-并发选未发布课程-全部6007', notPublished === 5, { notPublished, codes: results.map(r => r.code) })

    // 重复选课 — 仍 6007
    const dup = await api('POST', '/enrollments', student1.token, { courseId })
    record('B04.3-重复选课-期望6007', dup.code === 6007, dup)

    // 退课（不存在选课记录）
    const drop = await api('DELETE', `/enrollments/999999`, student1.token)
    record('B04.4-不存在选课-退课-期望8001', drop.code === 8001 || drop.code === 404, drop)

    // 不存在的课程ID
    const fake = await api('POST', '/enrollments', student1.token, { courseId: 9999999 })
    record('B04.5-不存在课程-期望6001', fake.code === 6001 || fake.code === 404, fake)
  } catch (e) {
    record('B04-异常', false, { error: String(e).slice(0, 200) })
  }
}

// ────────────────────────────────────────────────────────────
// B05 权限端点矩阵 — ADMIN 拒绝学生 token, 学生 token 拒绝管理员
// ────────────────────────────────────────────────────────────
async function e2eB05_PermissionMatrix() {
  console.log('\n[B05] 权限矩阵')
  let teacher, student
  try {
    teacher = await login('teacher1', 'password123')
    student = await login('student1', 'password123')

    // 学生访问教师端
    const stuCourses = await api('GET', '/courses/pending-review', student.token)
    record('B05.1-学生-pending-review-期望403', stuCourses.code === 10003 || stuCourses.code === 403, stuCourses)

    // 学生访问管理员端
    const stuAdmin = await api('GET', '/operation-logs', student.token)
    record('B05.2-学生-operation-logs-期望403', stuAdmin.code === 10003 || stuAdmin.code === 403, stuAdmin)

    // 教师访问管理员独有
    const teachAdmin = await api('GET', '/admin/platform-share-config', teacher.token)
    record('B05.3-教师-platform-share-config-期望403', teachAdmin.code === 10003 || teachAdmin.code === 403, teachAdmin)

    // 无 token
    const noToken = await api('GET', '/courses', null)
    record('B05.4-无token-courses-期望401', noToken.code === 1005 || noToken.status === 401, noToken)

    // 伪造 token
    const fakeToken = 'eyJhbGciOiJIUzM4NCJ9.invalid.token'
    const badToken = await api('GET', '/courses', fakeToken)
    record('B05.5-伪造token-期望401', badToken.code === 1005 || badToken.status === 401, badToken)
  } catch (e) {
    record('B05-异常', false, { error: String(e).slice(0, 200) })
  }
}

// ────────────────────────────────────────────────────────────
// B06 打卡、徽章、证书链路
// ────────────────────────────────────────────────────────────
async function e2eB06_LearningChain() {
  console.log('\n[B06] 学习链路')
  let student
  try {
    student = await login('student1', 'password123')

    // 打卡 (业务设计: 当天首次 200; 同日重复幂等返回原记录 200)
    const checkin = await api('POST', '/check-ins', student.token)
    record('B06.1-打卡-期望200', checkin.code === 200, checkin)

    // 同日重复打卡应幂等 (返回 200 同 id) — 业务允许幂等打卡
    const dup = await api('POST', '/check-ins', student.token)
    record('B06.2-同日重复打卡-期望幂等200', dup.code === 200 && dup.data?.id === checkin.data?.id, dup)

    // 连续天数
    const streak = await api('GET', '/check-ins/streak', student.token)
    record('B06.3-连续天数-期望200', streak.code === 200 && (streak.data >= 1 || streak.data?.streak >= 1), streak)

    // 徽章定义
    const defs = await api('GET', '/badges/definitions', student.token)
    record('B06.4-徽章定义', defs.code === 200, { count: Array.isArray(defs.data) ? defs.data.length : (defs.data?.items?.length || 0) })

    // 我的徽章
    const mine = await api('GET', '/badges/my', student.token)
    record('B06.5-我的徽章', mine.code === 200, mine)

    // 我的证书
    const certs = await api('GET', '/certificates/my', student.token)
    record('B06.6-我的证书', certs.code === 200, certs)

    // 服务端时间
    const ts = await api('GET', '/server-time', null)
    record('B06.7-服务端时间', ts.code === 200, ts)
  } catch (e) {
    record('B06-异常', false, { error: String(e).slice(0, 200) })
  }
}

await e2eB01_CourseLifecycle()
await e2eB02_MicroSpecialtyLifecycle()
await e2eB03_StateMachineInvariants()
await e2eB04_EnrollmentConcurrency()
await e2eB05_PermissionMatrix()
await e2eB06_LearningChain()

const summary = {
  ts: new Date().toISOString(),
  total: results.length,
  failed: results.filter(r => !r.ok).length,
  results
}
const fs = await import('node:fs/promises')
const ts = Date.now()
const out = `/Users/jackie/微课平台/.qa-results/qa-e2e-business-${ts}.json`
await fs.mkdir('/Users/jackie/微课平台/.qa-results', { recursive: true })
await fs.writeFile(out, JSON.stringify(summary, null, 2))
console.log('SAVED ' + out)
console.log('SUMMARY e2e failed=' + summary.failed + '/' + summary.total)
