// 微课平台 · 浏览器巡检驱动（基于 ego-browser）
// 运行: ego-browser nodejs scripts/qa-browser-driver.mjs <role>
// 输出: /Users/jackie/微课平台/.qa-results/qa-<role>-<ts>.json

;(async () => {
const BASE = process.env.QA_BASE_URL || 'http://localhost:8088'
const ROLE = (process.argv[2] || 'STUDENT').toUpperCase()

const ROLES = {
  ADMIN:    { user: 'admin',      pass: 'admin123',     home: '/admin/dashboard' },
  ACADEMIC: { user: 'academic1',  pass: 'password123',  home: '/academic/dashboard' },
  TEACHER:  { user: 'teacher1',   pass: 'password123',  home: '/teacher/dashboard' },
  STUDENT:  { user: 'student1',   pass: 'password123',  home: '/student/courses' }
}

const PAGES = {
  ADMIN: [
    '/admin/dashboard','/admin/users','/admin/logs','/admin/settings',
    '/admin/platform-share-config','/admin/teacher-ratings','/admin/revenue',
    '/admin/banners','/admin/teaching-classes','/admin/system-health','/admin/reports',
    '/departments','/majors','/classes','/users','/users/create',
    '/courses','/courses/create','/courses/review','/course-categories','/tags',
    '/chapters','/videos','/enrollments','/favorites','/questions','/exercises',
    '/discussions','/notifications','/reviews','/bundles',
    '/academic/dashboard','/academic/stats','/academic/enrollments',
    '/teacher/dashboard','/teacher/students','/teacher/grades',
    '/teacher/teaching-classes','/teacher/profile','/teacher/slides',
    '/teacher/exams','/teacher/offline-list',
    '/micro-specialties','/student/micro-specialties','/profile'
  ],
  ACADEMIC: [
    '/admin/dashboard','/admin/users','/admin/logs','/admin/settings',
    '/admin/banners','/admin/teaching-classes','/admin/revenue','/admin/reports',
    '/departments','/majors','/classes','/users',
    '/courses','/courses/create','/courses/review','/course-categories','/tags',
    '/chapters','/videos','/enrollments','/favorites','/questions','/exercises',
    '/discussions','/notifications','/reviews','/bundles',
    '/academic/dashboard','/academic/stats','/academic/enrollments',
    '/academic/micro-specialties/review','/academic/micro-specialties/proposals',
    '/academic/micro-specialties/featured','/academic/micro-specialties/cross-dept',
    '/academic/micro-specialties/class-import','/academic/micro-specialties/gold',
    '/academic/micro-specialties/storage-review',
    '/teacher/dashboard','/teacher/students','/teacher/grades',
    '/teacher/offline-list',
    '/micro-specialties','/profile'
  ],
  TEACHER: [
    '/teacher/dashboard','/teacher/courses','/teacher/videos','/teacher/exercises',
    '/teacher/discussions','/teacher/favorites','/teacher/questions',
    '/teacher/students','/teacher/grades','/teacher/teaching-classes',
    '/teacher/profile','/teacher/slides','/teacher/exams','/teacher/offline-list',
    '/teacher/micro-specialties','/teacher/micro-specialties/invites',
    '/teacher/micro-specialties/proposals','/teacher/micro-specialties/my-proposals',
    '/courses','/courses/create','/chapters','/videos','/questions','/exercises',
    '/discussions','/notifications','/bundles','/profile'
  ],
  STUDENT: [
    '/student/courses','/student/bundles','/student/my-courses',
    '/student/training','/student/learning','/student/learning-stats',
    '/student/notifications','/student/exams','/student/profile',
    '/student/report','/student/favorites','/student/orders',
    '/student/checkout','/student/reviews','/student/settings',
    '/student/achievements','/student/discussions',
    '/student/micro-specialties','/micro-specialties','/profile'
  ]
}

const INSTRUMENT = `(() => {
  if (window.__qa) return;
  window.__qa = { network: [], console: [], errors: [] };
  for (const level of ['log','info','warn','error']) {
    const o = console[level].bind(console);
    console[level] = (...args) => { window.__qa.console.push({ level, args: args.map(v => String(v)) }); o(...args); };
  }
  addEventListener('error', e => window.__qa.errors.push({ type:'error', message:e.message, source:e.filename, line:e.lineno, column:e.colno }));
  addEventListener('unhandledrejection', e => window.__qa.errors.push({ type:'unhandledrejection', message:String(e.reason) }));
  const _open = XMLHttpRequest.prototype.open;
  const _send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) { this.__meta = { m, u }; return _open.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function (b) {
    this.addEventListener('loadend', () => window.__qa.network.push({ ...this.__meta, status: this.status, body: typeof b === 'string' ? b : (b ? '[non-string]' : null) }));
    return _send.apply(this, arguments);
  };
})()`

// 健壮登录流：清理 ego-browser 继承的登录态 → 强制登出 → 表单登录 → 校验 token
const task = await useOrCreateTaskSpace('mcqa-route-' + ROLE + '-' + Date.now())
await wait(2)
await cdp('Page.addScriptToEvaluateOnNewDocument', { source: INSTRUMENT })
await cdp('Network.enable')
await cdp('Page.enable')
await cdp('Log.enable')

await gotoAndWait(`${BASE}/login`, { timeout: 30, settle: 1 })
await wait(2)
let url = await js('location.href')
let inputs = await js("[...document.querySelectorAll('input')].map(i => ({type:i.type, ph:i.placeholder}))")
if (!inputs.length) {
  if (String(url).startsWith(BASE)) {
    await js("(async () => { try { const t = localStorage.getItem('micro_course_token'); if (t) await fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + t } }) } catch(e){} ; try { localStorage.clear(); sessionStorage.clear() } catch(e){} })()")
  }
  await wait(1)
  await gotoAndWait(`${BASE}/login`, { timeout: 30, settle: 1 })
  await wait(2)
  url = await js('location.href')
  inputs = await js("[...document.querySelectorAll('input')].map(i => ({type:i.type, ph:i.placeholder}))")
}
// 登录 (server-side API + 直接 goto home)
let loggedIn = false
for (let attempt = 1; attempt <= 3 && !loggedIn; attempt++) {
  await useOrCreateTaskSpace(task.id)
  // 通过 CDP 直连 backend API 拿 token (绕过 SPA 路由)
  let apiResp = null
  try {
    apiResp = await cdp('Network.enable') // 确保 Network 开启
    apiResp = await fetch('http://localhost:8089/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: ROLES[ROLE].user, password: ROLES[ROLE].pass }) })
    const j = await apiResp.json()
    if (j.code === 200 && j.data && j.data.accessToken) {
      // navigate to login 先清空 localStorage 再写入
      await gotoAndWait(`${BASE}/login`, { timeout: 30, settle: 1 })
      await wait(2)
      await js(`(() => { try { localStorage.clear(); sessionStorage.clear() } catch(e){} })()`)
      await wait(1)
      const ok = await js(`(() => { try { localStorage.setItem('micro_course_token', '${j.data.accessToken}'); localStorage.setItem('micro_course_refresh_token', '${j.data.refreshToken || ''}'); return true } catch(e) { return false } })()`)
      if (ok) {
        loggedIn = true
        await gotoAndWait(BASE + ROLES[ROLE].home, { timeout: 25, settle: 1 })
        await wait(2)
        break
      }
    } else {
      cliLog('login attempt ' + attempt + ' api failed: ' + JSON.stringify(j))
    }
  } catch (e) {
    cliLog('login attempt ' + attempt + ' error: ' + String(e).slice(0, 200))
  }
  await wait(2)
}
if (!loggedIn) throw new Error('登录失败: 3 次尝试均未获得 token')

const results = []
for (const p of PAGES[ROLE]) {
  try {
    await js('window.__qa.network = []; window.__qa.console = []; window.__qa.errors = [];')
    let httpStatus = 0
    try {
      const resp = await gotoAndWait(`${BASE}${p}`, { timeout: 25, settle: 1 })
      httpStatus = resp?.status || 0
    } catch (e) {
      results.push({ path: p, ok: false, reason: 'navigation: ' + String(e).slice(0, 200) })
      cliLog(`✗ ${ROLE} ${p} [navigation]`)
      continue
    }
    await wait(1)
    const qa = await js('window.__qa || { network: [], console: [], errors: [] }')
    const api = (qa.network || []).filter(x => x.u && x.u.includes('/api/')).map(x => ({ method: x.m, url: x.u, status: x.status }))
    const err = (qa.errors || []).map(e => ({ type: e.type, message: String(e.message).slice(0, 240) }))
    const cerr = (qa.console || []).filter(c => c.level === 'error').map(c => c.args.join(' ').slice(0, 240))
    const cw = (qa.console || []).filter(c => c.level === 'warn').map(c => c.args.join(' ').slice(0, 240))
    const fails = []
    for (const a of api) if (a.status >= 400) fails.push(`API ${a.status} ${a.method} ${a.url}`)
    for (const e of err) fails.push(`JS ${e.type}: ${e.message}`)
    for (const c of cerr) fails.push(`console.error: ${c}`)
    results.push({ path: p, httpStatus, api, err, cerr, cw, ok: fails.length === 0, fails })
    cliLog(`${fails.length === 0 ? '✓' : '✗'} ${ROLE} ${p} http=${httpStatus} api=${api.length} jsErr=${err.length} cErr=${cerr.length}`)
  } catch (e) {
    results.push({ path: p, ok: false, reason: String(e).slice(0, 240) })
    cliLog(`✗ ${ROLE} ${p} [outer ${String(e).slice(0,60)}]`)
  }
}

const summary = { role: ROLE, base: BASE, ts: new Date().toISOString(), total: results.length, failed: results.filter(r => !r.ok).length, results }
const fs = await import('node:fs/promises')
const ts = Date.now()
const out = `/Users/jackie/微课平台/.qa-results/qa-${ROLE.toLowerCase()}-${ts}.json`
await fs.mkdir('/Users/jackie/微课平台/.qa-results', { recursive: true })
await fs.writeFile(out, JSON.stringify(summary, null, 2))
cliLog('SAVED ' + out)
cliLog(`SUMMARY: ${summary.failed}/${summary.total} failed`)
await completeTaskSpace(task.id, { keep: false })

})().catch(e => { cliLog('FATAL ' + (e.stack || String(e))); process.exit(1) })
