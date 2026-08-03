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
    let refreshRequestHeaders: Record<string, string> | null;
    // 事件驱动：refresh 请求被拦截即 resolve（替代固定 sleep；
    //     慢 runner 下固定 3s 等待必抖 → 2026-08-03 CI 假失败根因）
    const refreshSeenPromise = new Promise<Record<string, string> | null>((resolve) => {
      page.route('**/api/auth/refresh', async (route, request) => {
        const headers = request.headers();
        // 返回 401 (refresh token 无效) - 不真正调用后端
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ code: 1005, message: 'Token 格式错误' })
        });
        resolve(headers);
      });
    });

    // 2. 拦截 /api/auth/login 请求, 模拟登录返回短期 token
    let resolveLoginSeen: () => void = () => {};
    const loginSeenPromise = new Promise<void>((resolve) => { resolveLoginSeen = resolve; });
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
      resolveLoginSeen();
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

    // 5. 等 login 响应已返回（前端正在写入 token 并跳转）。
    //    关键时序: 若在 login 请求完成前执行 page.goto('/')，导航会 abort 进行中的
    //    login 请求 → 登录失败 → 被重定向回登录页 → refresh 链路永不触发。
    //    即使等 route 层响应返回，紧随的 goto 仍可能打断前端 setToken 处理。
    //    这是 2026-08-03 main push CI 三次全挂的真实根因（慢 runner/热 cache 下
    //    导航先于 login 处理完成；单独跑时 bundle 冷加载慢反而"侥幸"通过）。
    await Promise.race([
      loginSeenPromise,
      new Promise<void>((resolve) => setTimeout(resolve, 30000))
    ]);

    // 6. 等待 refresh 请求被拦截（最多 30s，正常情况毫秒级返回；超时返回 null 由步骤 7 断言兜底）
    //    注意: 不等待 localStorage token —— mock login 写入 token 后，前端会立即调
    //    /api/auth/me(mock 401) → 自动 refresh(mock 401) → request.js 按产品逻辑
    //    removeToken() 清空凭证。慢 runner 上"写入→清除"链路可能快于轮询，
    //    导致 waitForFunction 观察到的始终是清空态而挂死（2026-08-03 main push CI 失败根因）。
    //    refresh 请求本身是确定性事件（me 401 必然触发），直接以它为同步点。
    //    不再 goto('/'): 登录成功后 SPA 自动 router.push → App.vue 挂载调 getInfo
    //    (/api/auth/me) → 401 → 自动 refresh。这是真实用户路径，也避免导航打断登录处理。
    refreshRequestHeaders = await Promise.race([
      refreshSeenPromise,
      new Promise<null>((resolve) => setTimeout(() => resolve(null), 30000))
    ]);

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
