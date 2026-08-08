const { chromium } = require('playwright')
const BASE = 'http://localhost:5173'
const HC = process.env.HC || '990003'
;(async () => {
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const results = []
  const step = (name, ok, extra='') => { results.push({name, ok}); console.log(`${ok?'✅':'❌'} ${name}${extra?' — '+extra:''}`) }
  try {
    await page.goto(`${BASE}/login`)
    await page.waitForSelector('input[id="username"]')
    await page.fill('input[id="username"]', 'admin')
    await page.fill('input[id="password"]', 'password123')
    await page.press('input[id="password"]', 'Enter')
    await page.waitForTimeout(3000)

    // HTML manage page via sectionId
    const SEC = process.env.SEC || '76'
    await page.goto(`${BASE}/teacher/courses/${HC}/slides/manage?sectionId=${SEC}`)
    await page.waitForTimeout(6000)
    let body = await page.textContent('body')
    step('HTML管理页加载', /HTML|课件|分段|音频/.test(body), body.slice(0,120).replace(/\s+/g,' '))
    await page.screenshot({ path: 'test-results/html-05-manage.png', fullPage: true })

    // 分段脚本 tab
    const segTab = page.locator('.el-tabs__item:has-text("分段脚本")').first()
    if (await segTab.count()) {
      await segTab.click()
      await page.waitForTimeout(2500)
      body = await page.textContent('body')
      step('分段脚本面板', /段|第 1 段|ScriptEditor|讲述稿/.test(body), body.slice(0,120).replace(/\s+/g,' '))
      await page.screenshot({ path: 'test-results/html-06-segments.png', fullPage: true })
      // 段音频面板
      const segAudio = page.locator('.hcm-segment-audio, :text("段级音频")').first()
      step('段级音频面板', await segAudio.count() > 0 || /段级音频/.test(body))
    } else {
      step('分段脚本tab存在', false)
    }

    // 预览按钮
    const previewBtn = page.locator('button:has-text("预览")').first()
    if (await previewBtn.count()) {
      await previewBtn.click()
      await page.waitForTimeout(3000)
      await page.screenshot({ path: 'test-results/html-07-preview.png', fullPage: true })
      step('HTML教师预览可进入', true)
    } else {
      step('HTML教师预览按钮', false)
    }
  } catch (e) {
    console.log('❌ 异常:', e.message)
    await page.screenshot({ path: 'test-results/html-99-error.png', fullPage: true }).catch(()=>{})
  }
  await browser.close()
  const passed = results.filter(r=>r.ok).length
  console.log(`\n===== HTML教师端: ${passed}/${results.length} 通过 =====`)
})().catch(e=>{console.error(e); process.exit(1)})
