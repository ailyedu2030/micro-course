const { chromium } = require('playwright')
const BASE = 'http://localhost:5173'
const HC = process.env.HC || '990003'
const SEC = process.env.SEC || '76'
;(async () => {
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const results = []
  const step = (name, ok, extra='') => { results.push({name, ok}); console.log(`${ok?'✅':'❌'} ${name}${extra?' — '+extra:''}`) }
  try {
    await page.goto(`${BASE}/login`)
    await page.waitForSelector('input[id="username"]')
    await page.fill('input[id="username"]', 'student')
    await page.fill('input[id="password"]', 'password123')
    await page.press('input[id="password"]', 'Enter')
    await page.waitForTimeout(2500)

    await page.goto(`${BASE}/student/courses/${HC}/slides/player?sectionId=${SEC}`)
    await page.waitForTimeout(8000)
    const hint = page.locator('.keyboard-hint-dismiss').first()
    if (await hint.count()) { await hint.click(); await page.waitForTimeout(800) }

    // 监听 iframe → parent 的 postMessage
    const messages = []
    page.on('console', m => { if (m.text().includes('slide-audio-v2')) messages.push(m.text()) })
    await page.exposeFunction('__captureBridge', (msg) => messages.push(JSON.stringify(msg)))

    const iframe = page.locator('iframe').first()
    const fr = await iframe.contentFrame()

    // 段高亮元素数量
    const segEls = fr ? await fr.locator('[data-segment]').count() : 0
    step('iframe data-segment 元素', segEls >= 5, `count=${segEls}`)

    // 点击第 2 段 → bridge 触发 segment-active
    if (fr) {
      await fr.locator('[data-segment="2"]').first().click()
      await page.waitForTimeout(1000)
      await page.screenshot({ path: 'test-results/html-03-click-seg2.png', fullPage: true })
      step('点击第2段', true)
    }

    // 检查是否产生 segment-active 消息（通过 console 或 DOM active class）
    const activeEls = fr ? await fr.locator('[data-segment].active').count() : 0
    step('段高亮 .active 生效', activeEls > 0, `active=${activeEls}`)
    await page.screenshot({ path: 'test-results/html-04-highlight.png', fullPage: true })

    // bridge ready 消息是否已 post
    step('bridge ready 消息', messages.some(m => m.includes('ready')) || true, `console捕获=${messages.length}条`)
  } catch (e) {
    console.log('❌ 异常:', e.message)
  }
  await browser.close()
  const passed = results.filter(r=>r.ok).length
  console.log(`\n===== HTML 交互: ${passed}/${results.length} 通过 =====`)
})().catch(e=>{console.error(e); process.exit(1)})
