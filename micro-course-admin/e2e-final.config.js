/**
 * 最终兜底 E2E 测试配置 (真实浏览器 chromium)
 * 目标: http://localhost:5173 (本地 dev 前端, 代理到 :8080 后端)
 */
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e-tests',
  testMatch: /phase13-final-coupon\.spec\.js$/,
  timeout: 120000,
  expect: { timeout: 15000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['line'], ['html', { outputFolder: 'e2e-tests/report', open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    launchOptions: { args: ['--disable-dev-shm-usage'] },
  },
  projects: [{ name: 'chromium-e2e', use: { viewport: { width: 1440, height: 900 } } }],
});
