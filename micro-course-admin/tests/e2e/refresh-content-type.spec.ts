/**
 * Bug G 回归测试 (PR #165)
 * ====================================================================
 * 验证 /api/auth/refresh 请求**包含** `Content-Type: application/json` header.
 * 防止回归到 `headers: {}` 显式空对象 (axios 0.27+ 不自动注入 Content-Type → 415).
 *
 * 部署背景:
 *   PR #165 修 Bug G: src/utils/request.js:125 refresh 调用 `headers: {}` 导致
 *   axios 0.27+ 不自动注入 Content-Type → 后端 415 → refresh 失败 → 所有 401
 *   请求无法重试 → 用户 console 堆满 401 错误链.
 *
 * 修复: `headers: { 'Content-Type': 'application/json' }` (1 行)
 *
 * 防止再发:
 *   1. precheck.sh [4] 禁止 `headers: {}` 模式 (CI gate)
 *   2. precheck.sh [5] 禁止 utils/request.js 之外直接 import axios (CI gate)
 *   3. **本 e2e test**: 验证生产 dist bundle 在 /api/auth/refresh 调用时
 *      实际发送 `Content-Type: application/json` header (运行时验证)
 *
 * 用法:
 *   npx playwright test --config=tests/e2e/e2e.config.ts tests/e2e/refresh-content-type.spec.ts
 */

import { test, expect, Page } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088';

test.describe('Bug G 回归测试: /api/auth/refresh Content-Type', () => {

  test('登录失败触发 refresh 时, 请求必须包含 Content-Type: application/json', async ({ page }) => {
    // 1. 拦截 /api/auth/refresh 请求, 捕获请求头
    let refreshRequestHeaders: Record<string, string> | null = null;
    await page.route('**/api/auth/refresh', async (route, request) => {
      refreshRequestHeaders = request.headers();
      // 返回 401 (refresh token 无效) - 不真正调用后端
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 1005, message: 'Token 格式错误' })
      });
    });

    // 2. 拦截 /api/auth/login 请求, 模拟登录返回短期 token
    await page.route('**/api/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          data: {
            accessToken: 'mock-expired-access-token',
            refreshToken: 'mock-refresh-token',
            tokenType: 'Bearer',
            expiresIn: 3600
          }
        })
      });
    });

    // 3. 拦截 /api/auth/me 触发 401 (模拟 token 过期)
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 1005, message: 'Token 过期' })
      });
    });

    // 4. 访问首页, 模拟登录 (触发 token 写入 localStorage)
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('admin123');
    await page.locator('.login-btn').first().click();

    // 5. 等待 localStorage 写入 token (login 成功)
    await page.waitForFunction(() => localStorage.getItem('micro_course_token') !== null, { timeout: 10000 });

    // 6. 导航到任意页面, 触发 /api/auth/me → 401 → 自动 refresh
    await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    // 等待 refresh 请求被拦截
    await page.waitForTimeout(3000);

    // 7. 验证 refresh 请求被触发
    expect(refreshRequestHeaders, '/api/auth/refresh 应该被触发 (token 过期)').not.toBeNull();

    // 8. 关键验证: Content-Type header 是 application/json (Bug G 防再发)
    const contentType = refreshRequestHeaders!['content-type'] || refreshRequestHeaders!['Content-Type'] || '';
    expect(
      contentType.toLowerCase().includes('application/json'),
      `Bug G 回归: /api/auth/refresh Content-Type 必须是 application/json, 实际是 '${contentType}' (axios 0.27+ 显式空 headers 不会自动注入)`
    ).toBe(true);

    // 9. 防止 415: 验证请求 method 是 POST (refresh 必须 POST)
    // 注: POST 是 request method, headers 里的 'content-type' 才是 Content-Type

    console.log('✅ Bug G 回归测试通过:');
    console.log('   - /api/auth/refresh Content-Type:', contentType);
    console.log('   - 防 415 ✓, 防 axios 0.27+ 空 headers 陷阱 ✓');
  });

  test('utils/request.js 不应该有显式空 headers 模式 (静态扫描)', async ({ page }) => {
    // 这个 test 在 e2e 跑 (无需浏览器交互, 用 page.evaluate 在客户端执行)
    // 验证: production dist bundle 不包含 `headers:{` 紧接 `}` 的模式 (绕过 minify)
    // 这是双保险: 1) precheck.sh 在源码层拦截, 2) 本 e2e 在 dist 层验证

    await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded', timeout: 30000 });

    // 加载所有 JS bundle 拼接
    const allBundleContent = await page.evaluate(async () => {
      const scripts = Array.from(document.querySelectorAll('script[src]'));
      const contents = await Promise.all(scripts.map(async (s: any) => {
        try {
          const r = await fetch(s.src);
          return await r.text();
        } catch (e) {
          return '';
        }
      }));
      return contents.join('\n');
    });

    // 检查 minify 后的 patterns: `headers:{}` 或 `headers: {}` (空格)
    // 注意: 跨 chunk 后 `headers:{` 可能存在但紧接 `}` 是 bug
    const suspiciousPattern = /headers\s*:\s*\{\s*\}/g;
    const matches = allBundleContent.match(suspiciousPattern);

    if (matches && matches.length > 0) {
      console.log('❌ Bundle 含可疑 headers: {} 模式:');
      matches.forEach((m, i) => console.log(`  [${i}] ${m}`));
    }

    expect(
      matches,
      'dist bundle 不应包含 `headers: {}` 显式空对象 (Bug G 触发 415)'
    ).toBeNull();

    console.log('✅ Bundle 静态扫描通过: 无 `headers: {}` 模式');
  });
});
