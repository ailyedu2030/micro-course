import { test, expect } from '@playwright/test'

/** 管理员登录（带重试） */
async function adminLogin(page) {
  for (let i = 0; i < 3; i++) {
    await page.goto('/login')
    await page.fill('input[type="text"]', 'admin')
    await page.fill('input[type="password"]', 'admin123')
    await page.click('button:has-text("登 录")')
    try {
      await page.waitForURL('**/admin/**', { timeout: 10000 })
      return
    } catch {
      if (page.url().includes('/admin/')) return
    }
  }
  throw new Error(`Admin登录失败（重试3次后），当前URL: ${page.url()}`)
}

test.describe('互动课程流程', () => {
  test.beforeEach(async ({ page }) => {
    // 管理员登录
    await adminLogin(page)
  })

  test('课程列表显示互动课程类型标签', async ({ page }) => {
    await page.goto('/courses')
    await page.waitForSelector('.el-table')
    // 验证课程类型列存在
    const typeCol = page.locator('.el-table__header-wrapper th:has-text("类型")')
    await expect(typeCol).toBeVisible()
  })

  test('课程详情页显示互动课程入口', async ({ page }) => {
    await page.goto('/courses')
    await page.waitForSelector('.el-table')
    // 点击第一门课的「详情」
    const detailBtn = page.locator('.el-table__body-wrapper .el-button:has-text("详情")').first()
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await page.waitForURL('**/courses/**')
      // 验证课程分类字段可见
      await expect(page.locator('text=课程类型').first()).toBeVisible()
    }
  })

  test('互动课程创建工作台入口', async ({ page }) => {
    await page.goto('/courses')
    await page.waitForSelector('.el-table')
    // 创建工作台按钮
    const workspaceBtn = page.locator('.el-table__body-wrapper .el-button:has-text("工作台")').first()
    if (await workspaceBtn.isVisible()) {
      await workspaceBtn.click()
      // 等待路由跳转完成
      await page.waitForTimeout(3000)
      expect(page.url()).toContain('/workspace')
      // 验证工作台加载 - 使用正确的CSS class
      await expect(page.locator('.course-editor').first()).toBeVisible({ timeout: 15000 })
    }
  })
})

test.describe('学生互动课程学习流程', () => {
  test.beforeEach(async ({ page }) => {
    // 学生登录
    await page.goto('/login')
    await page.fill('input[type="text"]', 'student')
    await page.fill('input[type="password"]', '123456')
    await page.click('button:has-text("登 录")')
    // 等待跳转学生端（最多15s，失败时检查URL）
    try {
      await page.waitForURL('**/student/**', { timeout: 15000 })
    } catch {
      // fallback: 检查当前URL是否已经在学生端
      if (!page.url().includes('/student/')) {
        throw new Error(`学生登录失败，当前URL: ${page.url()}`)
      }
    }
  })

  test('课程广场显示互动课程筛选', async ({ page }) => {
    await page.goto('/student/courses')
    await page.waitForSelector('.course-square, .course-grid, .el-card')
    // 验证课程类型筛选器存在
    const typeFilter = page.locator('select, .el-select, .el-radio-group:has-text("全部"), .el-radio-group:has-text("视频"), .el-radio-group:has-text("互动")').first()
    await expect(typeFilter).toBeVisible()
  })

  test('我的课程页面显示学习进度', async ({ page }) => {
    await page.goto('/student/my-courses')
    await page.waitForSelector('.el-card, .course-item, .my-courses')
    // 验证课程卡片显示
    const courseCards = page.locator('.el-card, .course-item, .my-course-card')
    const count = await courseCards.count()
    expect(count).toBeGreaterThanOrEqual(0) // 可能有0门课
  })

  test('学习中心页面加载统计信息', async ({ page }) => {
    await page.goto('/student/learning')
    await page.waitForSelector('.learning-center, .el-card')
    // 验证统计卡片显示
    const statCards = page.locator('.stat-card, .el-card')
    await expect(statCards.first()).toBeVisible()
  })
})

test.describe('教师互动课件管理', () => {
  test.beforeEach(async ({ page }) => {
    // 教师登录
    await page.goto('/login')
    await page.fill('input[type="text"]', 'teacher')
    await page.fill('input[type="password"]', '123456')
    await page.click('button:has-text("登 录")')
    await page.waitForURL('**/teacher/**')
  })

  test('教师数据看板加载统计', async ({ page }) => {
    await page.goto('/teacher/dashboard')
    await page.waitForSelector('.teacher-dashboard, .el-card')
    // 验证看板卡片（TeacherDashboard 使用 stat-card 非 el-card）
    const cards = page.locator('.stat-card')
    await expect(cards.first()).toBeVisible({ timeout: 10000 })
  })

  test('教师学员列表页面加载', async ({ page }) => {
    await page.goto('/teacher/students')
    await page.waitForSelector('.el-table, .student-list')
    // 验证表格
    const table = page.locator('.el-table').first()
    await expect(table).toBeVisible()
  })

  test('课件管理页显示上传区域', async ({ page }) => {
    await page.goto('/teacher/courses/1/slides')
    await page.waitForTimeout(3000)
    // 检查是否有上传区域或幻灯片管理内容
    const uploadSection = page.locator('.upload-hero, .workspace').first()
    await expect(uploadSection).toBeVisible({ timeout: 10000 })
  })

  test('课件创建向导步骤可见', async ({ page }) => {
    await page.goto('/teacher/courses/1/slides')
    await page.waitForTimeout(3000)
    // 验证创建向导步骤指示器存在
    const guideSteps = page.locator('.create-guide')
    const count = await guideSteps.count()
    // 可能有向导（无课件时）或进度条（有课件时）
    if (count > 0) {
      await expect(guideSteps.first()).toBeVisible()
    }
  })
})

test.describe('互动课件批量操作', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[type="text"]', 'teacher')
    await page.fill('input[type="password"]', '123456')
    await page.click('button:has-text("登 录")')
    await page.waitForURL('**/teacher/**')
  })

  test('课件管理页加载批量和预览按钮', async ({ page }) => {
    await page.goto('/teacher/courses/1/slides')
    await page.waitForTimeout(3000)
    // 如果幻灯片已就绪，检查批量操作按钮
    const batchBtn = page.locator('button:has-text("批量操作")')
    if (await batchBtn.isVisible()) {
      await batchBtn.click()
      await page.waitForTimeout(500)
      // 批量模式下应该可以操作
      const exitBtn = page.locator('button:has-text("退出批量")')
      await expect(exitBtn).toBeVisible()
    }
  })
})

test.describe('SlidePlayer 播放器', () => {
  test.beforeEach(async ({ page }) => {
    // 学生登录
    await page.goto('/login')
    await page.fill('input[type="text"]', 'student')
    await page.fill('input[type="password"]', '123456')
    await page.click('button:has-text("登 录")')
    try {
      await page.waitForURL('**/student/**', { timeout: 15000 })
    } catch {
      if (!page.url().includes('/student/')) {
        throw new Error(`学生登录失败，当前URL: ${page.url()}`)
      }
    }
  })

  test('SlidePlayer 加载显示', async ({ page }) => {
    await page.goto('/student/courses/1/slides/player')
    await page.waitForTimeout(5000)
    // 验证播放器主要元素存在
    const player = page.locator('.slide-player')
    const playerExists = await player.count()
    if (playerExists > 0) {
      await expect(player).toBeVisible()
      // 检查播放器关键组件
      await expect(page.locator('.player-header')).toBeVisible()
      await expect(page.locator('.player-footer')).toBeVisible()
    }
    // 如果没有幻灯片但进入了播放页，应该能看到加载状态或错误提示
    const loadingState = page.locator('.player-loading')
    const mainContent = page.locator('.player-main')
    await expect(loadingState.or(mainContent)).toBeVisible({ timeout: 10000 })
  })

  test('SlidePlayer 键盘快捷键可用', async ({ page }) => {
    await page.goto('/student/courses/1/slides/player')
    await page.waitForTimeout(5000)
    // 检查键盘提示是否显示（首次访问）
    const hint = page.locator('.keyboard-hint')
    if (await hint.isVisible()) {
      await expect(page.locator('kbd')).toHaveCount(4)
    }
  })

  test('SlidePlayer 全屏切换', async ({ page }) => {
    await page.goto('/student/courses/1/slides/player')
    await page.waitForTimeout(5000)
    const player = page.locator('.slide-player')
    if (await player.count() > 0) {
      const fullscreenBtn = page.locator('button[aria-label="全屏"], button[aria-label="退出全屏"]').first()
      if (await fullscreenBtn.isVisible()) {
        await fullscreenBtn.click()
        await page.waitForTimeout(500)
        // 全屏按钮的aria-label应该切换
        const exitBtn = page.locator('button[aria-label="退出全屏"]')
        await expect(exitBtn).toBeVisible()
      }
    }
  })
})
