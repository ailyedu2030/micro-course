// 微课平台 · 浏览器深度交互驱动 v2 (基于 ego-browser)
// ego-browser 的 task space 在每次 heredoc 独立 — 直接用 fresh name
// 输出: /Users/jackie/微课平台/.qa-results/qa-{phase}-{role}-{ts}.json

;(async () => {

const BASE = process.env.QA_BASE_URL || 'http://localhost:8088'
const ROLE = (process.argv[2] || 'ADMIN').toUpperCase()
const PHASE = (process.argv[3] || 'interaction').toLowerCase()

const ROLES = {
  ADMIN:    { user: 'admin',      pass: 'admin123',     home: '/admin/dashboard' },
  ACADEMIC: { user: 'academic1',  pass: 'password123',  home: '/academic/dashboard' },
  TEACHER:  { user: 'teacher1',   pass: 'password123',  home: '/teacher/dashboard' },
  STUDENT:  { user: 'student1',   pass: 'password123',  home: '/student/courses' }
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

// 1) 在 fresh task space 中：先 server-side 清掉 storage（删除 token），再 navigate
await useOrCreateTaskSpace('mcqa-' + ROLE + '-' + PHASE + '-' + Date.now())
await cdp('Page.addScriptToEvaluateOnNewDocument', { source: INSTRUMENT })

// 2) 直接在当前 tab 打开 login（不关 tab，避免任务空间随 tab 全关而销毁）
await useOrCreateTaskSpace('mcqa-' + ROLE + '-' + PHASE)
await gotoAndWait(`${BASE}/login`, { timeout: 30, settle: 1 })
await wait(2)
let url = await js("location.href")
let hasLoginForm = await js("!!document.querySelector('#username') && !!document.querySelector('#password')")
let loggedIn = false
for (let attempt = 1; attempt <= 3 && !loggedIn; attempt++) {
  if (!hasLoginForm) {
    await js("(async () => { try { const t = localStorage.getItem('micro_course_token'); if (t) await fetch('/api/auth/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + t } }) } catch(e){} ; try { localStorage.clear(); sessionStorage.clear() } catch(e){} })()")
    await wait(1)
    await gotoAndWait(`${BASE}/login`, { timeout: 30, settle: 1 })
    await wait(2)
  }
  await waitForElement('#username', { timeout: 20 }).catch(() => {})
  hasLoginForm = await js("!!document.querySelector('#username') && !!document.querySelector('#password')")
  if (!hasLoginForm) {
    url = await js('location.href')
    cliLog('LOGIN retry url=' + url)
    continue
  }
  await fillInput('#username', ROLES[ROLE].user)
  await fillInput('#password', ROLES[ROLE].pass)
  await click('.login-btn', { label: 'login submit' })
  await wait(3)
  const tokenNow = await js("localStorage.getItem('micro_course_token')")
  if (tokenNow) { loggedIn = true; break }
  await js("try { localStorage.clear(); sessionStorage.clear() } catch(e){}")
  await gotoAndWait(`${BASE}/login`, { timeout: 30, settle: 1 })
  await wait(2)
}
if (!loggedIn) { cliLog('LOGIN FAILED for ' + ROLE + ' — no token after 3 attempts, url=' + url); process.exit(2) }
cliLog('LOGIN OK ' + ROLE)

// 4) 跳到角色首页
await gotoAndWait(BASE + ROLES[ROLE].home, { timeout: 25, settle: 1 })
await wait(2)
cliLog('ROLE HOME ' + await js('location.href'))

// ────────────────────────────────────────────────────────────
// 工具
// ────────────────────────────────────────────────────────────
async function captureErrors() {
  return js('window.__qa || { network: [], console: [], errors: [] }')
}
async function resetCounters() {
  await js('(() => { window.__qa = window.__qa || { network: [], console: [], errors: [] }; window.__qa.network = []; window.__qa.console = []; window.__qa.errors = []; })()')
}
async function safeFill(placeholder, val) {
  const sel = `input[placeholder="${placeholder}"]`
  if (!(await js(`!!document.querySelector('${sel.replace(/'/g, "\\'")}')`))) return false
  await fillInput(sel, val)
  return true
}

async function runTask(name, fn) {
  try {
    await useOrCreateTaskSpace('mcqa-' + ROLE + '-' + PHASE)
    await resetCounters()
    await fn()
    await wait(1.5)
    const qa = await captureErrors()
    const api = (qa.network || []).filter(x => x.u && x.u.includes('/api/')).map(x => ({ method: x.m, url: x.u, status: x.status }))
    const err = (qa.errors || []).map(e => ({ type: e.type, message: String(e.message).slice(0, 240) }))
    const cerr = (qa.console || []).filter(c => c.level === 'error').map(c => c.args.join(' ').slice(0, 240))
    const fails = api.filter(a => a.status >= 400).map(a => `API ${a.status} ${a.method} ${a.url}`)
      .concat(err.map(e => `JS ${e.type}: ${e.message}`), cerr.map(c => `console.error: ${c}`))
    results.push({ name, ok: fails.length === 0, api4xx5xx: api.filter(a => a.status >= 400).length, jsErr: err.length, cErr: cerr.length, fails: fails.slice(0, 8) })
    cliLog((fails.length === 0 ? '✓ ' : '✗ ') + name + (fails.length ? ' fails=' + fails.length : ''))
    if (fails.length) for (const f of fails.slice(0, 3)) cliLog('    ' + f)
  } catch (e) {
    results.push({ name, ok: false, error: String(e).slice(0, 240) })
    cliLog('✗ ' + name + ' EX ' + String(e).slice(0, 160))
  }
}

const results = []

// ──── 工具：点击文字按钮 ────
async function clickByText(re) {
  return js(`(() => {
    const b = [...document.querySelectorAll('button')].find(b => ${re});
    if (b) { b.click(); return true; }
    return false;
  })()`)
}

// ──── ADMIN 套件 ────
async function adminSuite() {
  await runTask('D01-ADMIN-courses-filter', async () => {
    await gotoAndWait(`${BASE}/courses`, { timeout: 25, settle: 1 })
    await wait(1)
    await safeFill('搜索', '基础')
    await clickByText('/搜\\s*索/')
    await wait(1)
    await clickByText('/重\\s*置/')
    await wait(1)
  })
  await runTask('D07-ADMIN-courses-review', async () => {
    await gotoAndWait(`${BASE}/courses/review`, { timeout: 25, settle: 1 })
    await wait(1)
    await js(`(() => { const t = [...document.querySelectorAll('.el-tabs__item')][0]; if (t) t.click() })()`)
    await wait(1)
  })
  await runTask('D09-ADMIN-banners', async () => {
    await gotoAndWait(`${BASE}/admin/banners`, { timeout: 25, settle: 1 })
    await wait(1)
    await clickByText('/新\\s*增/')
    await wait(1)
  })
  await runTask('D10-ADMIN-teaching-classes', async () => {
    await gotoAndWait(`${BASE}/admin/teaching-classes`, { timeout: 25, settle: 1 })
    await wait(1)
    await clickByText('/新\\s*增/')
    await wait(1)
  })
  await runTask('D11-ADMIN-operation-logs', async () => {
    await gotoAndWait(`${BASE}/admin/logs`, { timeout: 25, settle: 1 })
    await wait(1)
    await clickByText('/查\\s*询/')
    await wait(1)
  })
  await runTask('D12-ADMIN-settings-tabs', async () => {
    await gotoAndWait(`${BASE}/admin/settings`, { timeout: 25, settle: 1 })
    await wait(1)
    for (const label of ['邮件', '安全', 'CAS', '关于']) {
      await js(`(() => { const it = [...document.querySelectorAll('.el-menu-item')].find(m => m.innerText.includes('${label}')); if (it) it.click() })()`)
      await wait(1)
    }
  })
  await runTask('D15-ADMIN-teacher-ratings', async () => {
    await gotoAndWait(`${BASE}/admin/teacher-ratings`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D16-ADMIN-platform-share-config', async () => {
    await gotoAndWait(`${BASE}/admin/platform-share-config`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D17-ADMIN-reports', async () => {
    await gotoAndWait(`${BASE}/admin/reports`, { timeout: 25, settle: 1 })
    await wait(1)
  })
}

// ──── ACADEMIC 套件 ────
async function academicSuite() {
  await runTask('D19-ACADEMIC-courses-edit', async () => {
    await gotoAndWait(`${BASE}/courses`, { timeout: 25, settle: 1 })
    await wait(1)
    const opened = await js(`(() => {
      const row = document.querySelector('.el-table__row');
      if (!row) return false;
      const btn = [...row.querySelectorAll('button')].find(b => /编\\s*辑/.test(b.innerText));
      if (!btn) return false;
      btn.click();
      return true;
    })()`)
    await wait(2)
  })
  await runTask('D20-ACADEMIC-ms-proposals', async () => {
    await gotoAndWait(`${BASE}/academic/micro-specialties/proposals`, { timeout: 25, settle: 1 })
    await wait(1)
    await js(`(() => { const tabs = [...document.querySelectorAll('.el-tabs__item')]; if (tabs[1]) tabs[1].click() })()`)
    await wait(1)
  })
  await runTask('D21-ACADEMIC-enrollments', async () => {
    await gotoAndWait(`${BASE}/enrollments`, { timeout: 25, settle: 1 })
    await wait(1)
    await js(`(() => {
      const row = document.querySelector('.el-table__row');
      if (!row) return false;
      const btn = [...row.querySelectorAll('button')].find(b => /通\\s*过/.test(b.innerText));
      if (!btn) return false;
      btn.click();
      return true;
    })()`)
    await wait(1)
  })
  await runTask('D22-ACADEMIC-stats', async () => {
    await gotoAndWait(`${BASE}/academic/stats`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D23-ACADEMIC-class-import', async () => {
    await gotoAndWait(`${BASE}/academic/micro-specialties/class-import`, { timeout: 25, settle: 1 })
    await wait(1)
  })
}

// ──── TEACHER 套件 ────
async function teacherSuite() {
  await runTask('D13-TEACHER-proposal-step', async () => {
    await gotoAndWait(`${BASE}/teacher/micro-specialties/proposals`, { timeout: 25, settle: 1 })
    await wait(2)
    for (let i = 0; i < 4; i++) {
      await clickByText('/下\\s*一\\s*步/')
      await wait(1)
    }
  })
  await runTask('D14-TEACHER-ms-manage', async () => {
    await gotoAndWait(`${BASE}/teacher/micro-specialties`, { timeout: 25, settle: 1 })
    await wait(1)
    for (const label of ['我参与', '邀请']) {
      await js(`(() => { const t = [...document.querySelectorAll('.el-tabs__item')].find(t => t.innerText.includes('${label}')); if (t) t.click() })()`)
      await wait(1)
    }
  })
  await runTask('D15-TEACHER-ms-team', async () => {
    await gotoAndWait(`${BASE}/teacher/micro-specialties`, { timeout: 25, settle: 1 })
    await wait(1)
    await js(`(() => {
      const row = document.querySelector('.el-table__row');
      if (!row) return false;
      const btn = [...row.querySelectorAll('button')].find(b => /团\\s*队/.test(b.innerText) || /管\\s*理/.test(b.innerText));
      if (!btn) return false;
      btn.click();
      return true;
    })()`)
    await wait(2)
  })
  await runTask('D03-TEACHER-videos', async () => {
    await gotoAndWait(`${BASE}/teacher/videos`, { timeout: 25, settle: 1 })
    await wait(1)
    await clickByText('/新\\s*增/')
    await wait(1)
  })
  await runTask('D04-TEACHER-questions', async () => {
    await gotoAndWait(`${BASE}/teacher/questions`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D05-TEACHER-exercises', async () => {
    await gotoAndWait(`${BASE}/teacher/exercises`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D06-TEACHER-students', async () => {
    await gotoAndWait(`${BASE}/teacher/students`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D08-TEACHER-teaching-classes', async () => {
    await gotoAndWait(`${BASE}/teacher/teaching-classes`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D09-TEACHER-dashboard', async () => {
    await gotoAndWait(`${BASE}/teacher/dashboard`, { timeout: 25, settle: 1 })
    await wait(2)
  })
  await runTask('D10-TEACHER-exams', async () => {
    await gotoAndWait(`${BASE}/teacher/exams`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D11-TEACHER-offline-list', async () => {
    await gotoAndWait(`${BASE}/teacher/offline-list`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D12-TEACHER-slides', async () => {
    await gotoAndWait(`${BASE}/teacher/slides`, { timeout: 25, settle: 1 })
    await wait(1)
  })
}

// ──── STUDENT 套件 ────
async function studentSuite() {
  await runTask('D16-STUDENT-course-square', async () => {
    await gotoAndWait(`${BASE}/student/courses`, { timeout: 25, settle: 1 })
    await wait(2)
    await safeFill('搜索', '机器学习')
    await wait(1)
    await clickByText('/重\\s*置/')
    await wait(1)
  })
  await runTask('D17-STUDENT-course-detail', async () => {
    await gotoAndWait(`${BASE}/student/courses`, { timeout: 25, settle: 1 })
    await wait(2)
    await js(`(() => { const card = document.querySelector('.course-card, .el-card'); if (card) card.click() })()`)
    await wait(2)
    await js(`(() => { const t = [...document.querySelectorAll('.el-tabs__item')].find(t => t.innerText.includes('评价')); if (t) t.click() })()`)
    await wait(1)
  })
  await runTask('D18-STUDENT-my-courses', async () => {
    await gotoAndWait(`${BASE}/student/my-courses`, { timeout: 25, settle: 1 })
    await wait(2)
    for (const label of ['已完成', '收藏']) {
      await js(`(() => { const t = [...document.querySelectorAll('.el-tabs__item')].find(t => t.innerText.includes('${label}')); if (t) t.click() })()`)
      await wait(1)
    }
  })
  await runTask('D19-STUDENT-learning', async () => {
    await gotoAndWait(`${BASE}/student/learning`, { timeout: 25, settle: 1 })
    await wait(2)
  })
  await runTask('D20-STUDENT-learning-stats', async () => {
    await gotoAndWait(`${BASE}/student/learning-stats`, { timeout: 25, settle: 1 })
    await wait(2)
    await clickByText('/打\\s*卡|签\\s*到/')
    await wait(1)
  })
  await runTask('D25-STUDENT-notifications', async () => {
    await gotoAndWait(`${BASE}/student/notifications`, { timeout: 25, settle: 1 })
    await wait(2)
    await clickByText('/全部已读/')
    await wait(1)
  })
  await runTask('D26-STUDENT-checkout', async () => {
    await gotoAndWait(`${BASE}/student/checkout`, { timeout: 25, settle: 1 })
    await wait(2)
  })
  await runTask('D27-STUDENT-orders', async () => {
    await gotoAndWait(`${BASE}/student/orders`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D28-STUDENT-settings', async () => {
    await gotoAndWait(`${BASE}/student/settings`, { timeout: 25, settle: 1 })
    await wait(1)
  })
  await runTask('D29-STUDENT-profile', async () => {
    await gotoAndWait(`${BASE}/student/profile`, { timeout: 25, settle: 1 })
    await wait(1)
  })
}

if (ROLE === 'ADMIN') await adminSuite()
else if (ROLE === 'ACADEMIC') await academicSuite()
else if (ROLE === 'TEACHER') await teacherSuite()
else if (ROLE === 'STUDENT') await studentSuite()

const finalReport = {
  phase: PHASE, role: ROLE, base: BASE, ts: new Date().toISOString(),
  total: results.length,
  failed: results.filter(r => !r.ok).length,
  results
}
const fs = await import('node:fs/promises')
const ts = Date.now()
const out = `/Users/jackie/微课平台/.qa-results/qa-${PHASE}-${ROLE.toLowerCase()}-${ts}.json`
await fs.mkdir('/Users/jackie/微课平台/.qa-results', { recursive: true })
await fs.writeFile(out, JSON.stringify(finalReport, null, 2))
cliLog('SAVED ' + out)
cliLog('SUMMARY ' + PHASE + ' ' + ROLE + ' failed=' + finalReport.failed + '/' + finalReport.total)

})().catch(e => { cliLog('FATAL ' + (e.stack || String(e))); process.exit(1) })
