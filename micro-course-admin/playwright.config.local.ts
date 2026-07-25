/**
 * Playwright 本地开发配置 (多浏览器 + 多 Viewport 矩阵)
 * ======================================================
 *
 * 与 AGENTS.md §浏览器验证默认规则 对齐:
 *   - ego-browser: 交互式主审查工具（本地走查/截图/手工点选）
 *   - Playwright:  可重复回归/CI 门禁（本配置）
 *
 * 用法:
 *   npx playwright test --config=playwright.config.local.ts
 *   npx playwright test --config=playwright.config.local.ts --project=chromium-desktop
 *   npx playwright test --config=playwright.config.local.ts --grep @a11y
 *
 * 环境变量:
 *   BASE_URL  - 测试目标（默认 http://localhost:8088，拒绝生产 host）
 *   AUTH_USER - 教师用户名（默认 teacher1）
 *   AUTH_PASS - 密码（默认 password123）
 *
 * 生产保护:
 *   启动时检查 BASE_URL，拒绝 100.74.122.13 / microcourse.ailyedu.cn
 */

import { defineConfig, devices } from '@playwright/test';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088';

// ---- 生产环境阻断 ----
const PRODUCTION_HOSTS = ['100.74.122.13', 'microcourse.ailyedu.cn', 'microcourse.ailyun.cn'];
try {
  const u = new URL(BASE_URL);
  for (const host of PRODUCTION_HOSTS) {
    if (u.hostname === host || u.hostname.endsWith('.' + host)) {
      console.error(`\n🚨 [FATAL] 检测到生产目标: ${BASE_URL}`);
      console.error('   本配置仅允许 localhost/127.0.0.1/0.0.0.0');
      process.exit(1);
    }
  }
} catch {
  console.error(`\n🚨 [FATAL] 无法解析 BASE_URL: ${BASE_URL}`);
  process.exit(1);
}

const AUTH_USER = process.env.AUTH_USER || 'teacher1';
const AUTH_PASS = process.env.AUTH_PASS || 'password123';

export default defineConfig({
  testDir: './e2e',
  testIgnore: ['**/*.test.mjs', '**/*.test.js', '**/*.test.ts'],
  fullyParallel: false,           // 教师页面需串行（共享登录态）
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : 1, // 单 worker 保证登录态
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['json', { outputFolder: 'test-results', outputFile: 'results.json' }],
    ['list'],
  ],

  // 全局共享环境变量
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    extraHTTPHeaders: {
      'Accept-Language': 'zh-CN,zh;q=0.9',
    },
  },

  // 多浏览器 + 多 Viewport 矩阵
  // 为避免笛卡尔积失控，分两层：
  //   Layer 1: 三浏览器桌面 viewport（回归覆盖）
  //   Layer 2: 仅 Chromium 做平板 + 移动端（响应式覆盖）
  projects: [
    // ==================== Layer 1: 桌面端多浏览器 ====================
    {
      name: 'chromium-desktop',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        launchOptions: {
          args: ['--disable-dev-shm-usage'],
        },
      },
    },
    {
      name: 'firefox-desktop',
      use: {
        ...devices['Desktop Firefox'],
        viewport: { width: 1440, height: 900 },
      },
    },
    {
      name: 'webkit-desktop',
      use: {
        ...devices['Desktop Safari'],
        viewport: { width: 1440, height: 900 },
      },
      metadata: {
        browserLabel: 'Safari兼容代理',
        note: '⚠ Playwright WebKit ≈ Safari 渲染引擎，非真实 Safari 浏览器。真实 Safari 行为差异（字体渲染、表单控件等）需在 macOS Safari 上手工验证（参见 ego-browser 交互式走查）。',
      },
    },
    // ==================== Layer 2: 响应式（仅 Chromium） ====================
    {
      name: 'chromium-tablet',
      use: {
        ...devices['iPad Pro 11'],
        // 覆盖为横屏 810×1080 更接近实际教学场景
        viewport: { width: 810, height: 1080 },
      },
    },
    {
      name: 'chromium-mobile',
      use: {
        ...devices['iPhone 15 Pro Max'],
      },
    },
  ],
});
