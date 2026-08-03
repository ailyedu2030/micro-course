/**
 * E2E 流程测试专用 Playwright 配置
 * ======================================
 * 覆盖三个核心流程:
 *   1. 支付流程 (checkout.spec.ts)
 *   2. 选课流程 (enrollment.spec.ts)
 *   3. 课程 CRUD 流程 (course-crud.spec.ts)
 *
 * 用法:
 *   npx playwright test --config=tests/e2e/e2e.config.ts
 *   npx playwright test tests/e2e/checkout.spec.ts --config=tests/e2e/e2e.config.ts --reporter=list
 */

import { defineConfig } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088';

export default defineConfig({
  testDir: '.',
  testMatch: ['*.spec.ts'],
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  // CI retries=2：慢 runner 下首跑偶发超时（2026-08-03 Bug-G 假失败），
  // 1 次重试不足以覆盖，2 次在正常 3.5min 用例上成本可控
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  timeout: 120000,
  reporter: [
    ['list'],
  ],
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    extraHTTPHeaders: {
      'Accept-Language': 'zh-CN,zh;q=0.9',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: {
        browserName: 'chromium',
        viewport: { width: 1440, height: 900 },
        launchOptions: {
          // CI 上使用 Playwright 自动安装的浏览器（npx playwright install chromium）
          // 本地开发使用开发者本机安装的浏览器
          ...(process.env.CI ? {} : {
            executablePath: '/Users/jackie/Library/Caches/ms-playwright/chromium-1234/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing',
          }),
          args: ['--disable-gpu', '--no-sandbox', '--disable-dev-shm-usage'],
        },
      },
    },
  ],
});
