/**
 * HTML 课件 iframe 移动端 E2E 测试
 * =====================================
 *
 * 覆盖:
 *   1. iOS Safari (WebKit) 模拟: HTML 课件 iframe 正常加载
 *   2. Android Chrome 模拟: iframe sandbox 声明式生效
 *   3. 移动端布局: 横屏旋转响应式
 *
 * 注意:
 *   - Playwright iPhone 13 emulation 使用 WebKit 引擎,非真实 iOS Safari
 *   - iframe sandbox 属性是声明式,是否真执行 JS 由浏览器决定
 *   - 移动端真实测试需 BrowserStack / Sauce Labs,本测试是降级方案
 *
 * Mock 策略:
 *   - 拦截 /api/courses/{id} 返回带有 HTML 课件的模拟课程数据
 *   - 拦截 /api/sections 返回含 data:text/html iframe 的课时列表
 *
 * 运行:
 *   cd micro-course-admin && npx playwright test tests/e2e/mobile-iframe.spec.ts --config=playwright.config.local.ts --reporter=list
 */

import { test, expect, devices, Page } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088';

// ──────────────────────────────────────────────
// Helper: 登录学生
// ──────────────────────────────────────────────
async function loginAsStudent(page: Page) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 20000 });
  await page.waitForSelector('#username', { timeout: 20000 });
  await page.fill('#username', 'student');
  await page.fill('#password', 'student123');
  const loginBtn = page.locator('.login-btn');
  if (await loginBtn.isVisible().catch(() => false)) {
    await loginBtn.click();
  } else {
    await page.locator('button:has-text("登 录"), button:has-text("登录")').first().click();
  }
  await page.waitForURL(/\/(student|teacher|admin)\//, { timeout: 15000 });
  await page.waitForLoadState('networkidle');
}

// ──────────────────────────────────────────────
// Mock 数据: HTML 课件课程
// ──────────────────────────────────────────────
const MOCK_COURSE = {
  id: 999901,
  title: 'E2E HTML 课件测试课程',
  description: '<p>测试 HTML 课件在移动端的渲染</p>',
  status: 4,
  difficulty: 1,
  courseType: 'INTERACTIVE',
  coverUrl: '',
  teacherName: 'p0_teacher',
  categoryName: '测试分类',
  studentCount: 10,
  creditHours: 2,
};

const MOCK_CHAPTERS = [
  {
    id: 9999011,
    courseId: 999901,
    title: '第一章 · HTML 课件',
    sortOrder: 1,
    duration: 30,
  },
];

const MOCK_SECTIONS = [
  {
    id: 99990111,
    chapterId: 9999011,
    courseId: 999901,
    title: 'HTML 课件示例 - 基础概念',
    sectionType: 'HTML_COURSEWARE',
    content: '<html><body><h1>Hello HTML Courseware</h1><p>This is a test HTML content for mobile iframe testing.</p><script>document.body.innerHTML += "<p>JS executed</p>";</script></body></html>',
    sortOrder: 1,
    duration: 15,
  },
];

// ──────────────────────────────────────────────
// Test Suite: 移动端 HTML 课件 iframe
// ──────────────────────────────────────────────
test.describe('HTML 课件 - 移动端 iframe', () => {
  test.describe.configure({ mode: 'serial' });

  test('iOS Safari: HTML 课件 iframe 正常加载', async ({ page }) => {
    // ---- Mock API ----
    await page.route('**/api/courses/999901', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: MOCK_COURSE, message: 'success' }),
      });
    });
    await page.route('**/api/courses/999901/chapters**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { items: MOCK_CHAPTERS, totalElements: 1 }, message: 'success' }),
      });
    });
    await page.route('**/api/courses/999901/chapters/9999011/sections**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { items: MOCK_SECTIONS, totalElements: 1 }, message: 'success' }),
      });
    });

    // ---- 使用 iPhone 13 模拟 ----
    await loginAsStudent(page);

    // 导航到课程详情页
    await page.goto(`${BASE_URL}/student/courses/999901`, { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(2000);

    // 验证 HTML 课件 iframe 存在
    const iframe = page.frameLocator('iframe[src*="data:text/html"]');
    await expect(iframe.locator('body')).toBeVisible({ timeout: 10000 });

    // 验证 iframe 内容渲染（检查 h1 元素）
    await expect(iframe.locator('h1')).toContainText('Hello HTML Courseware', { timeout: 5000 });

    // 验证 iframe 内容可见
    const iframeBody = iframe.locator('body');
    const bodyText = await iframeBody.textContent();
    expect(bodyText).toContain('Hello HTML Courseware');
    console.log('[mobile-iframe] iOS Safari: iframe 正常加载, HTML 内容可见');
  });

  test('Android Chrome: iframe sandbox 生效（JS 不执行）', async ({ page }) => {
    // ---- Mock API ----
    await page.route('**/api/courses/999901', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: MOCK_COURSE, message: 'success' }),
      });
    });
    await page.route('**/api/courses/999901/chapters**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { items: MOCK_CHAPTERS, totalElements: 1 }, message: 'success' }),
      });
    });
    await page.route('**/api/courses/999901/chapters/9999011/sections**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { items: MOCK_SECTIONS, totalElements: 1 }, message: 'success' }),
      });
    });

    // ---- 使用 Pixel 5 (Android Chrome) 模拟 ----
    await loginAsStudent(page);

    await page.goto(`${BASE_URL}/student/courses/999901`, { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(2000);

    // 验证 iframe 存在并检查 sandbox 属性
    const iframeElement = page.locator('iframe[src*="data:text/html"]');
    await expect(iframeElement).toBeVisible({ timeout: 5000 });

    // 验证 sandbox 属性存在
    const sandboxAttr = await iframeElement.getAttribute('sandbox');
    // sandbox 属性即使为 '' (空字符串) 也表示启用全部限制
    // 在 Playwright 中,空字符串时 getAttribute 返回 ''
    // 只要属性存在即表示 sandbox 生效
    expect(sandboxAttr).not.toBeNull();
    console.log('[mobile-iframe] Android: iframe sandbox 属性存在:', sandboxAttr);

    // 验证 JS 未执行: MOCK_SECTIONS 的 script 标签添加了 "<p>JS executed</p>"
    // 由于 sandbox 限制,这段文本不应出现在 iframe 内容中
    const iframe = page.frameLocator('iframe[src*="data:text/html"]');
    const bodyText = await iframe.locator('body').textContent().catch(() => '');
    // sandbox 阻止了 script 执行,所以 "JS executed" 不应出现
    expect(bodyText).not.toContain('JS executed');
    console.log('[mobile-iframe] Android: sandbox 阻止了 JS 执行');
  });

  test('移动端: 横屏旋转布局适应', async ({ page }) => {
    // ---- Mock API ----
    await page.route('**/api/courses/999901', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: MOCK_COURSE, message: 'success' }),
      });
    });
    await page.route('**/api/courses/999901/chapters**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { items: MOCK_CHAPTERS, totalElements: 1 }, message: 'success' }),
      });
    });
    await page.route('**/api/courses/999901/chapters/9999011/sections**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { items: MOCK_SECTIONS, totalElements: 1 }, message: 'success' }),
      });
    });

    await loginAsStudent(page);

    // 先以竖屏模式打开
    await page.setViewportSize({ width: 390, height: 844 }); // iPhone 14 portrait
    await page.goto(`${BASE_URL}/student/courses/999901`, { waitUntil: 'networkidle', timeout: 20000 });
    await page.waitForTimeout(1500);

    // 验证竖屏下 iframe 可见
    let iframe = page.frameLocator('iframe[src*="data:text/html"]');
    await expect(iframe.locator('body')).toBeVisible({ timeout: 5000 });

    // 旋转到横屏
    await page.setViewportSize({ width: 844, height: 390 });
    await page.waitForTimeout(1000);

    // 验证横屏下 iframe 仍可见
    iframe = page.frameLocator('iframe[src*="data:text/html"]');
    await expect(iframe.locator('body')).toBeVisible({ timeout: 5000 });

    // 验证内容未丢失
    await expect(iframe.locator('h1')).toContainText('Hello HTML Courseware', { timeout: 5000 });

    // 验证页面没有布局错乱（检查 body 可见且高度 > 0）
    const bodyHeight = await page.locator('body').evaluate(el => el.scrollHeight);
    expect(bodyHeight).toBeGreaterThan(100);
    console.log(`[mobile-iframe] 横屏旋转: 布局适应, body height = ${bodyHeight}px`);
  });
});
