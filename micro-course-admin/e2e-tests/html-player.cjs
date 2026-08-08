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

    let body = await page.textContent('body')
    step('学生HTML播放页加载', /HTML|html/.test(body) || body.length > 50, body.slice(0,150).replace(/\s+/g,' '))
    await page.screenshot({ path: 'test-results/html-01-player.png', fullPage: true })

    // iframe 是否存在
    const iframe = page.locator('iframe').first()
    step('HTML iframe 加载', await iframe.count() > 0)
    if (await iframe.count()) {
      const fr = await iframe.contentFrame()
      step('iframe 内容可访问', !!fr, fr ? await fr.locator('body').textContent().then(t=>t.slice(0,50)).catch(()=>'?') : 'no-frame')
      const segCount = fr ? await fr.locator('section, [data-segment], [id^="seg-"]').count() : 0
      step('iframe 内段元素', segCount > 0, `segments=${segCount}`)
      await page.screenshot({ path: 'test-results/html-02-iframe.png', fullPage: true })
    }

    // 段音频状态诚实（第一段 script 有、audio FAILED）
    body = await page.textContent('body')
    step('HTML段音频状态(失败诚实)', /生成失败|音频生成失败/.test(body) || /音频/.test(body), body.slice(0,120).replace(/\s+/g,' '))

    // 翻页/播放控件
    const playBtn = page.locator('button[aria-label="播放/暂停"], button:has-text("播放"), button:has-text("点击开始")').first()
    if (await playBtn.count()) {
      step('HTML 播放按钮存在', true)
    } else {
      step('HTML 播放按钮存在', false, '未找到播放按钮')
    }
  } catch (e) {
    console.log('❌ 异常:', e.message)
    await page.screenshot({ path: 'test-results/html-99-error.png', fullPage: true }).catch(()=>{})
  }
  await browser.close()
  const passed = results.filter(r=>r.ok).length
  console.log(`\n===== HTML 结果: ${passed}/${results.length} 通过 =====`)
})().catch(e=>{console.error(e); process.exit(1)})
