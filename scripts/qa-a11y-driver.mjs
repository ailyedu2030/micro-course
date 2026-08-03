// 微课平台 · a11y 专项 (基于 ego-browser + axe-core CDN)
// 输出: /Users/jackie/微课平台/.qa-results/qa-a11y-<role>-<ts>.json

;(async () => {
const BASE = process.env.QA_BASE_URL || 'http://localhost:8088'
const ROLE = (process.argv[2] || 'ADMIN').toUpperCase()

const ROLES = {
  ADMIN:    { user: 'admin',      pass: 'admin123',     home: '/admin/dashboard' },
  ACADEMIC: { user: 'academic1',  pass: 'password123',  home: '/academic/dashboard' },
  TEACHER:  { user: 'teacher1',   pass: 'password123',  home: '/teacher/dashboard' },
  STUDENT:  { user: 'student1',   pass: 'password123',  home: '/student/courses' }
}

// axe-core 从本地仓库已有的 axe.min.js? 优先尝试 CDN → 退化注入自定义扫描脚本
const AXE = `https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.10.0/axe.min.js`

const _t = await useOrCreateTaskSpace('mcqa-a11y-' + ROLE + '-' + Date.now())
async function _b() { await useOrCreateTaskSpace(_t.id) }
await _b()
await cdp('Page.addScriptToEvaluateOnNewDocument', { source: 'window.__qa = { a11y: [], console: [], errors: [], network: [] }' })
// 先 navigate 到 login 页（同源）后再 fetch
await _b()
await gotoAndWait(`${BASE}/login`, { timeout: 25, settle: 1 })
await wait(2)
const loginResp = await js(`(async () => {
  try {
    localStorage.clear(); sessionStorage.clear();
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: '${ROLES[ROLE].user}', password: '${ROLES[ROLE].pass}' }) });
    const j = await r.json();
    if (j.code === 200 && j.data && j.data.accessToken) {
      localStorage.setItem('micro_course_token', j.data.accessToken);
      localStorage.setItem('micro_course_refresh_token', j.data.refreshToken || '');
      return { ok: true, tokenLen: j.data.accessToken.length };
    }
    return { ok: false, code: j.code, message: j.message };
  } catch (e) { return { ok: false, error: String(e) } }
})()`)
cliLog('server login ' + ROLE + ' ' + JSON.stringify(loginResp))
if (!loginResp.ok) { process.exit(2) }

await _b()
await gotoAndWait(BASE + ROLES[ROLE].home, { timeout: 25, settle: 1 })
await wait(2)

// 关键页面列表（按角色）
const PAGES = {
  ADMIN: [
    '/admin/dashboard','/admin/users','/admin/logs','/admin/settings',
    '/admin/banners','/admin/teaching-classes','/admin/system-health',
    '/admin/reports','/departments','/courses','/users/create','/chapters','/videos',
    '/enrollments','/favorites','/questions','/exercises','/discussions'
  ],
  ACADEMIC: [
    '/academic/dashboard','/academic/stats','/academic/enrollments',
    '/academic/micro-specialties/review','/academic/micro-specialties/proposals',
    '/academic/micro-specialties/featured','/academic/micro-specialties/gold',
    '/courses/review','/enrollments','/reviews','/admin/users','/departments','/classes'
  ],
  TEACHER: [
    '/teacher/dashboard','/teacher/courses','/teacher/videos','/teacher/exercises',
    '/teacher/questions','/teacher/students','/teacher/grades',
    '/teacher/teaching-classes','/teacher/profile','/teacher/slides','/teacher/exams',
    '/teacher/offline-list','/teacher/micro-specialties','/teacher/micro-specialties/proposals',
    '/teacher/micro-specialties/my-proposals','/teacher/micro-specialties/invites',
    '/courses','/chapters','/discussions','/notifications'
  ],
  STUDENT: [
    '/student/courses','/student/bundles','/student/my-courses',
    '/student/learning','/student/learning-stats','/student/notifications',
    '/student/exams','/student/profile','/student/report','/student/favorites',
    '/student/orders','/student/checkout','/student/reviews','/student/settings',
    '/student/achievements','/student/discussions','/micro-specialties'
  ]
}

const TARGETS = PAGES[ROLE]
const results = []

for (const p of TARGETS) {
  try {
    await _b()
    await js('window.__qa = { a11y: [], console: [], errors: [], network: [] }')
    await gotoAndWait(BASE + p, { timeout: 20, settle: 1 })
    await wait(2)
    // 1) 注入 axe-core (CDN 失败时退化为本地化简易扫描)
    let axeOk = false
    try {
      await js(`(async () => {
        if (window.axe) { window.__qa.axeReady = true; return }
        await new Promise((res, rej) => {
          const s = document.createElement('script');
          s.src = '${AXE}';
          s.onload = res; s.onerror = rej;
          document.head.appendChild(s);
        })
      })()`)
      const r = await js(`(async () => {
        if (!window.axe) return { error: 'axe not loaded' }
        try {
          const r = await window.axe.run(document, { resultTypes: ['violations'] })
          return { violations: r.violations.map(v => ({ id: v.id, impact: v.impact, nodes: v.nodes.length, help: v.help, helpUrl: v.helpUrl, targets: v.nodes.slice(0, 3).map(n => n.target) })) }
        } catch (e) {
          return { error: String(e) }
        }
      })()`)
      if (r && r.violations) {
        results.push({ path: p, axe: 'CDN', violations: r.violations })
        axeOk = true
      } else if (r && r.error) {
        results.push({ path: p, axe: 'CDN', error: r.error })
      }
    } catch (e) {
      // CDN 失败退化
    }

    // 2) 触摸目标 ≥44x44 检测
    const targets44 = await js(`(() => {
      const targets = [...document.querySelectorAll('button, a, [role="button"], input[type="checkbox"], input[type="radio"], .el-tabs__item')];
      const tooSmall = [];
      for (const el of targets) {
        if (el.offsetParent === null) continue;
        const r = el.getBoundingClientRect();
        if (r.width === 0 && r.height === 0) continue;
        if (r.width < 44 || r.height < 44) {
          tooSmall.push({ tag: el.tagName.toLowerCase(), text: (el.innerText || el.placeholder || '').slice(0, 30), w: Math.round(r.width), h: Math.round(r.height), cls: el.className.slice(0, 50) });
        }
      }
      return tooSmall;
    })()`)
    if (targets44.length > 0) results.push({ path: p, touchTargets: targets44 })

    // 3) 缺 alt 图像
    const imgMissingAlt = await js(`(() => {
      const imgs = [...document.querySelectorAll('img')];
      return imgs.filter(i => !i.alt && !i.getAttribute('aria-label') && i.offsetParent !== null).map(i => ({ src: i.src.slice(-60), w: i.naturalWidth }));
    })()`)
    if (imgMissingAlt.length) results.push({ path: p, imagesMissingAlt: imgMissingAlt })

    // 4) 缺 aria-label 的 icon-only button
    const iconOnlyBtn = await js(`(() => {
      const btns = [...document.querySelectorAll('button')];
      const viol = [];
      for (const b of btns) {
        if (b.offsetParent === null) continue;
        const txt = b.innerText.trim();
        if (txt === '' && !b.getAttribute('aria-label') && !b.querySelector('[aria-hidden="true"]')) {
          viol.push({ html: b.outerHTML.slice(0, 120) });
        }
      }
      return viol;
    })()`)
    if (iconOnlyBtn.length) results.push({ path: p, iconOnlyButtons: iconOnlyBtn })

    // 5) form input 缺 label/aria-label
    const inputMissingLabel = await js(`(() => {
      const ins = [...document.querySelectorAll('input:not([type=hidden]), textarea, select')];
      const viol = [];
      for (const i of ins) {
        if (i.offsetParent === null) continue;
        const id = i.id;
        const hasLabel = id && document.querySelector('label[for="' + id + '"]');
        const hasAria = i.getAttribute('aria-label') || i.getAttribute('aria-labelledby');
        const placeholder = i.placeholder;
        if (!hasLabel && !hasAria && !placeholder) viol.push({ name: i.name, type: i.type });
      }
      return viol;
    })()`)
    if (inputMissingLabel.length) results.push({ path: p, inputsMissingLabel: inputMissingLabel })

    // 6) heading hierarchy
    const headings = await js(`(() => {
      const hs = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')].map(h => +h.tagName.slice(1));
      const skips = [];
      for (let i = 1; i < hs.length; i++) if (hs[i] - hs[i-1] > 1) skips.push({ from: hs[i-1], to: hs[i] });
      return { counts: hs.reduce((a,l)=>{a[l]=(a[l]||0)+1;return a}, {}), skips };
    })()`)
    if (headings.skips.length) results.push({ path: p, headingSkips: headings.skips })

    // 7) console / JS error
    const errs = await js('window.__qa.errors || []')
    const cerrs = (await js('window.__qa.console || []')).filter(c => c.level === 'error')
    if (errs.length || cerrs.length) results.push({ path: p, jsErrors: errs.map(e => e.message), consoleErrors: cerrs.map(c => c.args.join(' ')) })

    cliLog(`${(results.filter(r => r.path === p).length === 0) ? '✓' : '⚠'} ${ROLE} ${p} axe=${axeOk?'Y':'N'}`)
  } catch (e) {
    results.push({ path: p, error: String(e).slice(0, 240) })
    cliLog('✗ ' + ROLE + ' ' + p + ' EX ' + String(e).slice(0, 100))
  }
}

const fs = await import('node:fs/promises')
const ts = Date.now()
const out = `/Users/jackie/微课平台/.qa-results/qa-a11y-${ROLE.toLowerCase()}-${ts}.json`
await fs.mkdir('/Users/jackie/微课平台/.qa-results', { recursive: true })
const summary = {
  role: ROLE, base: BASE, ts: new Date().toISOString(),
  pages: TARGETS.length,
  pagesWithIssues: new Set(results.map(r => r.path)).size,
  totalIssues: results.length,
  issues: results
}
await fs.writeFile(out, JSON.stringify(summary, null, 2))
cliLog('SAVED ' + out)
cliLog(`A11Y ${ROLE} pages=${summary.pages} withIssues=${summary.pagesWithIssues} total=${summary.totalIssues}`)

})().catch(e => { cliLog('FATAL ' + (e.stack || String(e))); process.exit(1) })