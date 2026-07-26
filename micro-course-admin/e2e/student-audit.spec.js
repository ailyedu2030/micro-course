/**
 * 学生端 a11y + 回归最小测试集 (v1)
 * ==============================
 *
 * 职责对齐 (AGENTS.md §浏览器验证默认规则):
 *   - ego-browser: 真实用户式交互走查/截图/手工点选（主审查工具）
 *   - Playwright:  可重复回归、CI 门禁、批量自动化断言（本文件）
 *
 * a11y 门禁规则:
 *   - axe critical/serious 违规（非基线豁免）→ 测试 FAIL（P0/严重）
 *   - axe moderate/minor 违规 → 记录警告，不阻断
 *   - 基线豁免: 仅 Element Plus 上游无法控制的项
 *
 * 运行:
 *   npx playwright test e2e/student-audit.spec.js
 *   node ../scripts/audit-student-pages.mjs        # 全量批量审计脚本
 */

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loadBaseline, filterBaselineViolations } from './a11y-utils.js';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8088';
const AUTH_USER = process.env.AUTH_USER || 'student';
const AUTH_PASS = process.env.AUTH_PASS || 'student123';

const baseline = loadBaseline();

async function loginAsStudent(page) {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
  await page.waitForSelector('#username', { timeout: 10000 });
  await page.fill('#username', AUTH_USER);
  await page.fill('#password', AUTH_PASS);
  const loginBtn = page.locator('.login-btn');
  const loginBtnAlt = page.locator('button:has-text("登 录"), button:has-text("登录")');
  if (await loginBtn.isVisible().catch(() => false)) {
    await loginBtn.click();
  } else {
    await loginBtnAlt.first().click();
  }
  await page.waitForURL('**/student/**', { timeout: 15000 });
  await page.waitForLoadState('networkidle');
}

async function runA11yGate(page, pageName, projectName, testInfo) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'])
    .analyze();
  const { remaining, removed } = filterBaselineViolations(results.violations, baseline);

  const rawCount = results.violations.length;
  const exemptCount = removed.length;
  console.log(`\n[axe] ${projectName} ${pageName}: ${rawCount} 违规 (基线豁免 ${exemptCount}, 实计 ${remaining.length})`);

  for (const r of removed) {
    console.log(`   ↩ 豁免: ${r.violation.id}[${r.violation.impact}] — ${r.reason}`);
  }

  const criticalSerious = remaining.filter(v => v.impact === 'critical' || v.impact === 'serious');
  if (criticalSerious.length > 0) {
    await testInfo.attach('axe-violations', {
      body: JSON.stringify(criticalSerious, null, 2),
      contentType: 'application/json',
    });
  }

  await page.screenshot({
    path: `e2e/screenshots/student-${pageName.replace(/\//g, '_')}-${projectName}.png`,
    fullPage: true,
  });

  expect(criticalSerious.length, `axe critical/serious 违规数应为 0，实为 ${criticalSerious.length}`).toBe(0);
}

// ============================================================
// 学生端核心页面测试
// ============================================================
const STUDENT_ROUTES = [
  '/student/courses',
  '/student/my-courses',
  '/student/learning',
  '/student/learning-stats',
  '/student/notifications',
  '/student/profile',
  '/student/favorites',
  '/student/discussions',
  '/student/exams',
  '/student/settings',
  '/student/achievements',
  '/student/orders',
  '/student/reviews',
  '/student/bundles',
  '/student/my-micro-specialties',
];

test.describe('学生端 a11y + 回归测试', () => {
  let sharedPage;

  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      locale: 'zh-CN',
    });
    sharedPage = await context.newPage();
    await loginAsStudent(sharedPage);
  });

  for (const route of STUDENT_ROUTES) {
    test(`@a11y @smoke ${route}`, async ({}, testInfo) => {
      test.setTimeout(30000);
      await sharedPage.goto(`${BASE_URL}${route}`, { waitUntil: 'networkidle', timeout: 25000 });
      await sharedPage.waitForTimeout(1000);
      await runA11yGate(sharedPage, route, 'chromium', testInfo);
    });
  }

  test.describe('学生端动态路由', () => {
    let courseId;
    let chapterId;

    test.beforeAll(async () => {
      // 预取课程和章节 ID
      try {
        const courseRes = await sharedPage.evaluate(async () => {
          const res = await fetch('/api/enrollments/my');
          const json = await res.json();
          return json?.data?.[0]?.courseId || null;
        });
        courseId = courseRes;
        if (courseId) {
          const chRes = await sharedPage.evaluate(async (cid) => {
            const res = await fetch(`/api/chapters?courseId=${cid}&size=1`);
            const json = await res.json();
            return json?.data?.items?.[0]?.id || null;
          }, courseId);
          chapterId = chRes;
        }
      } catch {
        // 静默失败 — 跳过后台动态路由测试
      }
    });

    if (courseId) {
      test(`@a11y /student/courses/${courseId} (课程详情)`, async ({}, testInfo) => {
        test.setTimeout(30000);
        await sharedPage.goto(`${BASE_URL}/student/courses/${courseId}`, { waitUntil: 'networkidle', timeout: 25000 });
        await sharedPage.waitForTimeout(1000);
        await runA11yGate(sharedPage, `/student/courses/${courseId}`, 'chromium', testInfo);
      });
    }

    if (chapterId) {
      test(`@a11y /student/chapters/${chapterId}/exercises (章节练习)`, async ({}, testInfo) => {
        test.setTimeout(30000);
        await sharedPage.goto(`${BASE_URL}/student/chapters/${chapterId}/exercises`, { waitUntil: 'networkidle', timeout: 25000 });
        await sharedPage.waitForTimeout(1000);
        await runA11yGate(sharedPage, `/student/chapters/${chapterId}/exercises`, 'chromium', testInfo);
      });
    }
  });
});
