// 微课平台 · 响应式巡检 (基于 ego-browser)
// 输出: /Users/jackie/微课平台/.qa-results/qa-responsive-<role>-<viewport>-<ts>.json

;(async () => {
const BASE = process.env.QA_BASE_URL || 'http://localhost:8088'
const ROLE = (process.argv[2] || 'STUDENT').toUpperCase()
const VIEWPORT = process.argv[3] || 'desktop'  // desktop | tablet | mobile
const VIEWPORTS = {
  desktop: { width: 1440, height: 900 },
  tablet:  { width: 1024, height: 768 },
  mobile:  { width: 390,  height: 844 }
}
const vp = VIEWPORTS[VIEWPORT] || VIEWPORTS.desktop

const ROLES = {
  ADMIN:    { user: 'admin',      pass: 'admin123',     home: '/admin/dashboard' },
  ACADEMIC: { user: 'academic1',  pass: 'password123',  home: '/academic/dashboard' },
  TEACHER:  { user: 'teacher1',   pass: 'password123',  home: '/teacher/dashboard' },
  STUDENT:  { user: 'student1',   pass: 'password123',  home: '/student/courses' }
}

const PAGES = {
  ADMIN:    ['/admin/dashboard','/admin/users','/admin/logs','/courses','/enrollments','/departments'],
  ACADEMIC: ['/academic/dashboard','/courses/review','/enrollments','/academic/micro-specialties/review'],
  TEACHER:  ['/teacher/dashboard','/teacher/courses','/teacher/students','/teacher/slides','/teacher/micro-specialties/proposals'],
  STUDENT:  ['/student/courses','/student/my-courses','/student/learning','/student/profile','/student/notifications','/student/checkout','/student/courses/1','/student/orders','/student/favorites']
}

const _t = await useOrCreateTaskSpace('mcqa-resp-' + ROLE + '-' + Date.now())
async function _b() { await useOrCreateTaskSpace(_t.id) }
await _b()
await cdp('Page.addScriptToEvaluateOnNewDocument', { source: 'window.__qa = { network: [], console: [], errors: [], layout: {} }' })
await _b()
// 设置视口尺寸 (mobile emulation via DeviceMetricsOverride)
await cdp('Emulation.setDeviceMetricsOverride', {
  width: vp.width, height: vp.height, deviceScaleFactor: 1, mobile: VIEWPORT === 'mobile'
})
// 改写 window.innerWidth / innerHeight / matchMedia (因为 setDeviceMetricsOverride 不影响 JS 报告值)
await cdp('Page.addScriptToEvaluateOnNewDocument', {
  source: `(() => {
    const _w = ${vp.width}, _h = ${vp.height};
    Object.defineProperty(window, 'innerWidth', { get: () => _w, configurable: true });
    Object.defineProperty(window, 'innerHeight', { get: () => _h, configurable: true });
    window.matchMedia = window.matchMedia || ((q) => ({
      matches: q.includes('${VIEWPORT === 'mobile' ? 'max-width: 768px' : VIEWPORT === 'tablet' ? 'max-width: 1024px' : 'min-width: 1025px'}'),
      addEventListener: () => {},
      removeEventListener: () => {}
    }));
  })()`
})
// 强制登录 (server-side)
await gotoAndWait(`${BASE}/login`, { timeout: 30, settle: 1 })
await wait(2)
const loginResp = await js(`(async () => {
  try {
    localStorage.clear(); sessionStorage.clear();
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: '${ROLES[ROLE].user}', password: '${ROLES[ROLE].pass}' }) });
    const j = await r.json();
    if (j.code === 200 && j.data && j.data.accessToken) {
      localStorage.setItem('micro_course_token', j.data.accessToken);
      localStorage.setItem('micro_course_refresh_token', j.data.refreshToken || '');
      return { ok: true };
    }
    return { ok: false, code: j.code };
  } catch (e) { return { ok: false, error: String(e) } }
})()`)
if (!loginResp.ok) { cliLog('LOGIN FAILED ' + ROLE + ' ' + JSON.stringify(loginResp)); process.exit(2) }

const results = []
for (const p of PAGES[ROLE]) {
  try {
    await _b()
    await js('window.__qa = { network: [], console: [], errors: [], layout: {} }')
    await gotoAndWait(BASE + p, { timeout: 25, settle: 1 })
    await wait(2.5)
    const layout = await js(`(() => {
      const main = document.querySelector('main, .layout-main, .el-main');
      const mainR = main ? main.getBoundingClientRect() : null;
      const scrollX = document.documentElement.scrollWidth - document.documentElement.clientWidth;
      const overflows = [];
      document.querySelectorAll('body *').forEach(el => {
        if (el.scrollWidth > el.clientWidth + 5 && el.clientWidth > 0) {
          const cs = getComputedStyle(el);
          if (cs.overflowX === 'visible' && el.tagName !== 'PRE' && el.tagName !== 'CODE') {
            // 排除已被父级 overflow:auto/scroll 包含的子元素
            let p = el.parentElement;
            let parentScrollable = false;
            while (p) {
              const pcs = getComputedStyle(p);
              if (pcs.overflowX === 'auto' || pcs.overflowX === 'scroll') { parentScrollable = true; break; }
              p = p.parentElement;
            }
            if (!parentScrollable) {
              overflows.push({ tag: el.tagName.toLowerCase(), cls: (el.className||'').slice(0, 40), w: el.clientWidth, sw: el.scrollWidth });
            }
          }
        }
      });
      const navTab = document.querySelector('.nav-tab, .el-tabs__item');
      const tabR = navTab ? navTab.getBoundingClientRect() : null;
      const cta = [...document.querySelectorAll('.el-button--primary, .bottom-cta')].find(b => {
        const r = b.getBoundingClientRect();
        return r.bottom > window.innerHeight - 10 && r.bottom <= window.innerHeight + 60;
      });
      return {
        url: location.href,
        viewport: { w: window.innerWidth, h: window.innerHeight },
        bodyW: document.body.scrollWidth,
        clientW: document.documentElement.clientWidth,
        horizontalOverflow: scrollX,
        main: mainR ? { w: Math.round(mainR.width), h: Math.round(mainR.height), top: Math.round(mainR.top) } : null,
        navTab: tabR ? { w: Math.round(tabR.width), h: Math.round(tabR.height) } : null,
        bottomCtaVisible: cta ? { y: Math.round(cta.getBoundingClientRect().bottom) } : null,
        overflowCount: overflows.length,
        firstOverflows: overflows.slice(0, 3)
      };
    })()`)
    const qa = await js('window.__qa || { network: [], console: [], errors: [] }')
    const api = (qa.network || []).filter(x => x.u && x.u.includes('/api/')).map(x => ({ method: x.m, url: x.u, status: x.status }))
    const err = (qa.errors || []).map(e => ({ type: e.type, message: String(e.message).slice(0, 200) }))
    const cerr = (qa.console || []).filter(c => c.level === 'error').map(c => c.args.join(' ').slice(0, 200))
    const fails = api.filter(a => a.status >= 400).map(a => `API ${a.status} ${a.method} ${a.url}`)
      .concat(err.map(e => `JS ${e.type}: ${e.message}`), cerr.map(c => `console.error: ${c}`))
    const issues = []
    if (layout.horizontalOverflow > 5) issues.push(`horizontal overflow ${layout.horizontalOverflow}px`)
    if (layout.overflowCount > 0) issues.push(`overflow elements ${layout.overflowCount}`)
    if (layout.navTab && layout.navTab.h > 0 && layout.navTab.h < 44) issues.push(`navTab h=${layout.navTab.h}`)
    results.push({ path: p, ok: fails.length === 0 && issues.length === 0, layout, api4xx5xx: api.filter(a => a.status >= 400).length, jsErr: err.length, cErr: cerr.length, issues, fails: fails.slice(0, 5) })
    cliLog((fails.length === 0 && issues.length === 0 ? '✓ ' : '⚠ ') + ROLE + ' ' + VIEWPORT + ' ' + p + (issues.length ? ' issues=' + JSON.stringify(issues) : ''))
  } catch (e) {
    results.push({ path: p, ok: false, error: String(e).slice(0, 200) })
    cliLog('✗ ' + ROLE + ' ' + p + ' EX ' + String(e).slice(0, 100))
  }
}

const fs = await import('node:fs/promises')
const ts = Date.now()
const out = `/Users/jackie/微课平台/.qa-results/qa-responsive-${ROLE.toLowerCase()}-${VIEWPORT}-${ts}.json`
await fs.mkdir('/Users/jackie/微课平台/.qa-results', { recursive: true })
const summary = {
  viewport: VIEWPORT, viewportSize: vp, role: ROLE, base: BASE, ts: new Date().toISOString(),
  total: results.length,
  failed: results.filter(r => !r.ok).length,
  results
}
await fs.writeFile(out, JSON.stringify(summary, null, 2))
cliLog('SAVED ' + out)
cliLog(`RESPONSIVE ${ROLE} ${VIEWPORT} ${vp.w}x${vp.h} failed=${summary.failed}/${summary.total}`)

})().catch(e => { cliLog('FATAL ' + (e.stack || String(e))); process.exit(1) })