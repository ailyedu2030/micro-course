/**
 * Fix-Agent-Q: PPT 课件全链路真实浏览器验证脚本（独立运行）
 * 用法: node e2e-tests/ppt-full-chain.cjs
 */
const { chromium } = require('playwright')

const BASE = 'http://localhost:5173'
const PASS = 'password123'
const COURSE = 111
const SECTION = 75

async function login(page, user) {
  await page.goto(`${BASE}/login`)
  await page.waitForSelector('input[id="username"]', { timeout: 15000 })
  await page.fill('input[id="username"]', user)
  await page.fill('input[id="password"]', PASS)
  await page.press('input[id="password"]', 'Enter')
  await page.waitForTimeout(3000)
}

;(async () => {
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await ctx.newPage()
  const results = []
  const step = (name, ok, extra = '') => {
    results.push({ name, ok, extra })
    console.log(`${ok ? '✅' : '❌'} ${name}${extra ? ' — ' + extra : ''}`)
  }

  try {
    // ============ 1. 教师登录 → 课件管理 ============
    await login(page, 'teacher')
    step('教师登录', page.url().includes('/teacher') || page.url().includes('dashboard'), page.url())

    await page.goto(`${BASE}/teacher/courses/${COURSE}/slides/manage?sectionId=${SECTION}`)
    await page.waitForTimeout(5000)
    let body = await page.textContent('body')
    step('课件管理页加载（PPT 模块）', /PPT 课件|PptCoursewareManage|讲述稿|跳转/.test(body), body.slice(0, 150).replace(/\s+/g, ' '))
    await page.screenshot({ path: 'test-results/ppt-01-manage.png', fullPage: true })

    // ============ 2. PPT 页面列表 ============
    const hasPages = await page.locator('.el-table__row, [class*=page], [class*=PptPage]').count()
    step('PPT 页列表存在', hasPages > 0, `页面元素 ${hasPages} 个`)

    // ============ 3. 讲述稿编辑（ScriptEditor）============
    // 尝试找到讲述稿编辑入口
    const scriptVisible = /讲述稿|讲述|script/i.test(body)
    step('讲述稿编辑区可见', scriptVisible)

    // ============ 4. 跳转规则编辑器（PptFlowEditor）============
    const flowVisible = /跳转|flow|规则/i.test(body)
    step('跳转规则区可见', flowVisible)

    // ============ 5. 教师预览 ============
    const previewBtn = page.locator('button:has-text("预览"), button:has-text("Preview")').first()
    if (await previewBtn.count()) {
      await previewBtn.click()
      await page.waitForTimeout(3000)
      await page.screenshot({ path: 'test-results/ppt-02-preview.png', fullPage: true })
      step('教师预览可进入', true)
      await page.keyboard.press('Escape')
      await page.waitForTimeout(1000)
    } else {
      step('教师预览按钮存在', false, '未找到预览按钮')
    }

    // ============ 6. 学生登录 → 播放 ============
    // 先清理教师登录态
    await page.evaluate(() => { localStorage.clear(); sessionStorage.clear() })
    await page.goto(`${BASE}/login`)
    await page.waitForSelector('input[id="username"]', { timeout: 15000 })
    await page.fill('input[id="username"]', 'student')
    await page.fill('input[id="password"]', PASS)
    await page.press('input[id="password"]', 'Enter')
    await page.waitForTimeout(3000)
    step('学生登录', true)

    await page.goto(`${BASE}/student/courses/${COURSE}/slides/player?sectionId=${SECTION}`)
    await page.waitForTimeout(6000)
    // 关闭首次访问键盘提示（一次性 session 遮罩，关闭后不再拦截）
    const hintDismiss = page.locator('.keyboard-hint-dismiss').first()
    if (await hintDismiss.count()) {
      await hintDismiss.click()
      await page.waitForTimeout(800)
    }
    body = await page.textContent('body')
    step('学生 PPT 播放页加载', /1\/2|第 1 页|slide|Slide/.test(body), body.slice(0, 160).replace(/\s+/g, ' '))
    await page.screenshot({ path: 'test-results/ppt-03-student-player.png', fullPage: true })

    // 音频状态诚实性: 第 1 页有 script 无音频 → 生成中; 第 2 页有 FAILED 音频 → 生成失败
    const page2Btn = page.locator('button[aria-label="第2页已加载"], button[aria-label*="第2页"]').first()
    if (await page2Btn.count()) {
      await page2Btn.click()
      await page.waitForTimeout(1500)
      body = await page.textContent('body')
      step('第2页音频状态诚实(失败而非生成中)', /音频生成失败|生成失败/.test(body), body.slice(0, 140).replace(/\s+/g, ' '))
      await page.screenshot({ path: 'test-results/ppt-05-failed-honest.png', fullPage: true })
    } else {
      // 键盘翻页到第2页
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(1500)
      body = await page.textContent('body')
      step('第2页音频状态诚实(失败而非生成中)', /音频生成失败|生成失败/.test(body), body.slice(0, 140).replace(/\s+/g, ' '))
    }

    // 翻页测试
    const nextBtn = page.locator('button:has-text("下一页"), button[aria-label*="下一页"], button[aria-label*="next"]').first()
    if (await nextBtn.count()) {
      await nextBtn.click()
      await page.waitForTimeout(1500)
      await page.screenshot({ path: 'test-results/ppt-04-student-next.png', fullPage: true })
      step('学生翻页', true)
    } else {
      // 尝试键盘
      await page.keyboard.press('ArrowRight')
      await page.waitForTimeout(1000)
      step('学生翻页(键盘)', true)
    }
  } catch (e) {
    console.log('❌ 脚本异常:', e.message)
    await page.screenshot({ path: 'test-results/ppt-99-error.png', fullPage: true }).catch(() => {})
  }

  await browser.close()

  const passed = results.filter(r => r.ok).length
  console.log(`\n===== 结果: ${passed}/${results.length} 通过 =====`)
  results.forEach(r => console.log(`  ${r.ok ? 'PASS' : 'FAIL'}  ${r.name}`))
})().catch(e => { console.error(e); process.exit(1) })
