/**
 * 管理端 + 教务端 a11y 最小测试集
 * =========================================================
 * 覆盖 student/teacher-audit 之外的管理/教务角色关键页面 axe 扫描。
 * 运行: npx playwright test --config=playwright.config.local.ts --project=chromium-desktop e2e/admin-audit.spec.js
 *
 * 账号（隔离门禁环境种子）:
 *   - admin:    admin/admin123（gate 脚本将 admin 密码重置为 admin123）
 *   - academic: academic1/password123
 */

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadBaseline, filterBaselineViolations, formatViolationSummary } from './a11y-utils.js';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088';
const ADMIN_USER = process.env.ADMIN_USER || 'admin';
const ADMIN_PASS = process.env.ADMIN_PASS || 'admin123';
const ACADEMIC_USER = process.env.ACADEMIC_USER || 'academic1';
const ACADEMIC_PASS = process.env.ACADEMIC_PASS || 'password123';

const baseline = loadBaseline();

async function login(page, username, password) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 15000 });
  await page.waitForTimeout(2000);
  await page.fill('input[id="username"]', username);
  await page.fill('input[id="password"]', password);
  await page.keyboard.press('Enter');
  await page.waitForTimeout(3000);
}

async function runA11yGate(page, pageName, projectName, testInfo) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
    .analyze();
  const { remaining } = filterBaselineViolations(results.violations, baseline);
  const criticalSerious = remaining.filter(v => v.impact === 'critical' || v.impact === 'serious');
  if (criticalSerious.length > 0) {
    await testInfo.attach('axe-violations', {
      body: JSON.stringify(criticalSerious, null, 2),
      contentType: 'application/json',
    });
    console.log(formatViolationSummary(criticalSerious));
  }
  expect(criticalSerious.length, `axe critical/serious 违规数应为 0，实为 ${criticalSerious.length}`).toBe(0);
}

// =========================================================
// 1. 管理端关键页面 a11y
// =========================================================
test.describe('管理端 - 核心页面 a11y', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, ADMIN_USER, ADMIN_PASS);
  });

  for (const [name, path] of [
    ['数据看板', '/admin/dashboard'],
    ['用户管理', '/admin/users'],
    ['课程管理', '/courses'],
    ['系统设置', '/admin/settings'],
    ['操作日志', '/admin/logs'],
    ['分账配置', '/admin/platform-share-config'],
    ['轮播图管理', '/admin/banners'],
  ]) {
    test(`@a11y @smoke ${name} - axe 扫描`, async ({ page }, testInfo) => {
      await page.goto(`${BASE_URL}${path}`, { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      await runA11yGate(page, path, testInfo.project.name, testInfo);
    });
  }
});

// =========================================================
// 2. 教务端关键页面 a11y
// =========================================================
test.describe('教务端 - 核心页面 a11y', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, ACADEMIC_USER, ACADEMIC_PASS);
  });

  for (const [name, path] of [
    ['教务看板', '/academic/dashboard'],
    ['学习分析', '/academic/stats'],
    ['选课总览', '/academic/enrollments'],
    ['微专业审核', '/academic/micro-specialties/review'],
    ['申报审批', '/academic/micro-specialties/proposals'],
  ]) {
    test(`@a11y @smoke ${name} - axe 扫描`, async ({ page }, testInfo) => {
      await page.goto(`${BASE_URL}${path}`, { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      await runA11yGate(page, path, testInfo.project.name, testInfo);
    });
  }
});
