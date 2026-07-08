import { test, expect } from '@playwright/test'

test.describe('教师 API Key 管理 E2E', () => {

  const login = async (page) => {
    await page.goto('/login')
    if (page.url().includes('/dashboard')) return
    await page.waitForSelector('.login-box', { timeout: 15000 })
    await page.fill('#username', 'p0_teacher')
    await page.fill('input[type="password"]', 'student123')
    await page.locator('.el-button').filter({ hasText: '录' }).click()
    await page.waitForURL('**/teacher/dashboard', { timeout: 15000 })
  }

  test('1. 教师菜单位置和 API Key 完整流程', async ({ page }) => {
    // 1. 登录 → 检查菜单
    await login(page)
    const body1 = await page.textContent('body')
    expect(body1).toContain('个人设置')
    expect(body1).toContain('个人资料 / API Key')

    // 2. 导航到个人设置页 → 检查 API Key 组件
    await page.goto('/teacher/profile')
    await page.waitForTimeout(2000)
    const body2 = await page.textContent('body')
    expect(body2).toContain('API Key')
    expect(body2).toContain('第三方系统集成')

    // 3. 生成 Key
    await page.locator('.el-button').filter({ hasText: '生成' }).first().click()
    await page.waitForTimeout(1000)
    await page.locator('.el-message-box .el-button--primary').click()
    await page.waitForTimeout(2000)

    const keyInput = page.locator('.api-key-input input')
    await expect(keyInput).toBeVisible({ timeout: 5000 })
    const keyVal = await keyInput.inputValue()
    expect(keyVal.length).toBe(64)
    console.log(`  Key: ${keyVal.substring(0, 8)}... (${keyVal.length} chars)`)

    // 4. 关闭提示
    await page.locator('button').filter({ hasText: '我已保存' }).click()
    await page.waitForTimeout(500)

    // 5. 撤销 Key
    await page.locator('.el-button').filter({ hasText: '撤销' }).click()
    await page.waitForTimeout(1000)
    await page.locator('.el-message-box .el-button--primary').click()
    await page.waitForTimeout(1500)

    await expect(page.getByText('未生成')).toBeVisible({ timeout: 5000 })
    console.log('  撤销成功')
  })
})